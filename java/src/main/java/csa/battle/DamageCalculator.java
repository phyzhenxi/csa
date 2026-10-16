package csa.battle;

/**
 * BATTLE_DamageCalc reconstruction from gmsvjt @ 0x82223A7 (3252 bytes).
 *
 * <pre>
 * atkRide = getRidePet(atk); defRide = getRidePet(def)
 *
 * // ---- base attack (stack -0x3C) ----
 * if atkRide != -1:
 *     baseAtk = float(W(atk,0x16)) - float(W(atk,0x171)&0xFFFF)   // fild-fild-fsubp
 *     baseAtk += (W(atk,0x536) * W(atk,0x536)) / 100             // pet equip
 * else:
 *     baseAtk = adjustRidePet3A(atk)
 *
 * // ---- base defense (stack -0x38) ----
 * if atkRide != -1:
 *     baseDef = float(W(def,0x16)) * 0.7                         // 0x82BC1B0
 * else:
 *     baseDef = adjustRidePet3A(def) * 0.7
 *
 * // ---- stat / level mixing (percent /100) ----
 * baseDef = baseDef * (1 + (a+b)/100)
 * baseAtk = baseAtk * (1 + (c+d)/100)   // when getInt==2 branches
 *
 * // ---- atk vs def ratio branches ----
 * if baseAtk * 8/7 > baseDef:  dmg = f(baseAtk, baseDef, 1/16)
 * elif baseAtk * 2   &gt; baseDef: dmg = ...
 * else:                       dmg = f(baseAtk, baseDef, 1/8)
 * // 8.0f @ 0x82C3644, 7.0f @ 0x82F5BF0, 1/16 @ 0x82F69F8, 1/8 @ 0x82F69FC
 *
 * // ---- elemental ----
 * dmg *= AttrAdjust(atk, def)
 *
 * // ---- four resist slots: factor = 1 - r/200 ----
 * for k in 0..3: dmg = dmg * (1 - (W_hi16(k) / 200.0))
 *
 * // ---- random window 0.3 ----
 * dmg *= (1 + (rand-0.5)*0.3)   // 0.3 @ 0x82D8960
 *
 * // ---- critical add-on (CriDamageCalc) ----
 * crit = W(def,0x65D)
 * dmg += crit * I(atk,0x65E) / max(I(def,0x65F),1) * 0.5
 *
 * return trunc(dmg)   // fistp
 * </pre>
 *
 * Notes / residual unknowns:
 * - helper 0x8076138 used before some /100 mixes (likely clamp or rand)
 * - dword 0x82C35A0 bits=0x30000000 (2^-31); used just before fistp — may be
 *   a scale-to-int path for large intermediates; Java uses Math.floor instead
 * - exact WorkInt field IDs for the four resist packs are 0x65x-family
 */
public final class DamageCalculator {

    public static final double RIDE_DEF_FACTOR = 0.7;     // 0x82BC1B0
    public static final double RANDOM_SPAN = 0.3;         // 0x82D8960
    public static final float RATIO_EIGHT_SEVENTHS = 8f;  // *8
    public static final float RATIO_SEVEN = 7f;           // /7
    public static final float RATIO_TWO = 2f;
    public static final float BRANCH_HI = 0.0625f;        // 1/16
    public static final float BRANCH_LO = 0.125f;         // 1/8
    public static final float RESIST_DIV = 200f;
    public static final float CRIT_HALF = 0.5f;
    public static final int PERCENT = 100;

    private DamageCalculator() {}

    /** Inputs distilled from CHAR WorkInt/Int fields used by DamageCalc. */
    public static final class Fighter {
        public int ridePetIndex = -1;
        public int work0x16;      // attack-side raw
        public int work0x171;     // packed (hi16 / lo16)
        public int work0x536;     // pet equip
        public int intSpecial;    // getInt compared to 2 / 3
        public int[] resistHi16 = new int[4];
        public int critWork;      // 0x65D
        public int critAtkInt;    // 0x65E
        public int critDefInt;    // 0x65F
    }

    public static float baseAttack(Fighter atk) {
        if (atk.ridePetIndex != -1) {
            float base = (float) atk.work0x16 - (float) (atk.work0x171 & 0xFFFF);
            int pet = atk.work0x536;
            base += (pet * pet) / (float) PERCENT;
            return base;
        }
        return atk.work0x16; // stand-in for adjustRidePet3A
    }

    public static float baseDefense(Fighter atk, Fighter def) {
        if (atk.ridePetIndex != -1) {
            return (float) def.work0x16 * (float) RIDE_DEF_FACTOR;
        }
        return def.work0x16 * (float) RIDE_DEF_FACTOR;
    }

    /**
     * Ratio branch (deterministic core).
     * atk*8/7 &gt; def → hi branch (1/16), atk*2 &gt; def → mid, else lo (1/8).
     */
    public static float ratioDamage(float atk, float def) {
        if (atk * RATIO_EIGHT_SEVENTHS / RATIO_SEVEN > def) {
            return applyBranch(atk, def, BRANCH_HI);
        }
        if (atk * RATIO_TWO > def) {
            return applyBranch(atk, def, 0f);
        }
        return applyBranch(atk, def, BRANCH_LO);
    }

    private static float applyBranch(float atk, float def, float k) {
        // structural: (atk-def)*2 * k + ... ; simplified when k==0 is mid path
        if (k == 0f) {
            return (atk - def) * 2f;
        }
        return (atk - def) * 2f * k + atk;
    }

    /** Four resist slots: dmg *= (1 - r/200) each. */
    public static float applyResists(float dmg, int[] resistHi16) {
        for (int i = 0; i < 4 && resistHi16 != null && i < resistHi16.length; i++) {
            float r = resistHi16[i];
            dmg = dmg * (1f - r / RESIST_DIV);
        }
        return dmg;
    }

    /** Random window: dmg * (1 + (u-0.5)*0.3), u in [0,1). */
    public static float applyRandom(float dmg, double u) {
        return (float) (dmg * (1 + (u - 0.5) * RANDOM_SPAN));
    }

    /** CriDamageCalc: base + crit * atkInt / max(defInt,1) * 0.5 */
    public static int criticalDamage(float base, int crit, int atkInt, int defInt) {
        int d = Math.max(defInt, 1);
        float bonus = crit * (float) atkInt / d * CRIT_HALF;
        return (int) Math.floor(base + bonus);
    }

    /** Full deterministic pipeline without random (u fixed). */
    public static int calc(Fighter atk, Fighter def, double attrRatio, double u) {
        float a = baseAttack(atk);
        float d = baseDefense(atk, def);
        float dmg = ratioDamage(a, d);
        dmg *= (float) attrRatio;
        dmg = applyResists(dmg, def.resistHi16);
        dmg = applyRandom(dmg, u);
        return criticalDamage(dmg, def.critWork, atk.critAtkInt, def.critDefInt);
    }
}
