package csa.battle;

public final class DamageCalculatorSelfTest {
    public static void main(String[] args) {
        int fail = 0;

        // base attack with ride pet
        DamageCalculator.Fighter a = new DamageCalculator.Fighter();
        a.ridePetIndex = 1;
        a.work0x16 = 1000;
        a.work0x171 = 200;      // lo16 = 200
        a.work0x536 = 10;       // pet equip
        fail += eq("baseAtk", 1000f - 200f + 1.0f, DamageCalculator.baseAttack(a)); // 10*10/100=1

        // base defense * 0.7
        DamageCalculator.Fighter d = new DamageCalculator.Fighter();
        d.work0x16 = 400;
        fail += eq("baseDef", 400f * 0.7f, DamageCalculator.baseDefense(a, d));

        // ratio branches
        fail += ok("hi_branch", DamageCalculator.ratioDamage(1000, 100) > DamageCalculator.ratioDamage(200, 100));
        float hi = DamageCalculator.ratioDamage(1000, 100);
        float lo = DamageCalculator.ratioDamage(50, 100);
        fail += ok("hi_pos", hi > 0);
        fail += ok("lo_diff", lo != hi);

        // resists
        float r = DamageCalculator.applyResists(1000f, new int[] {0, 0, 0, 0});
        fail += eq("resist0", 1000f, r);
        r = DamageCalculator.applyResists(1000f, new int[] {100, 0, 0, 0});
        fail += eq("resist100", 500f, r); // 1 - 100/200 = 0.5

        // random
        fail += eq("rand_mid", 100f, DamageCalculator.applyRandom(100f, 0.5));
        fail += eq("rand_hi", (float) (100 * (1 + 0.25 * 0.3)), DamageCalculator.applyRandom(100f, 0.75));

        // critical
        fail += eq("crit0", 100, DamageCalculator.criticalDamage(100f, 0, 50, 10));
        // 100 + 0*... = 100
        fail += eq("crit", (int) Math.floor(100 + 10 * 40 / 5 * 0.5), DamageCalculator.criticalDamage(100f, 10, 40, 5));

        // full pipeline deterministic
        DamageCalculator.Fighter at = new DamageCalculator.Fighter();
        at.ridePetIndex = -1;
        at.work0x16 = 500;
        DamageCalculator.Fighter de = new DamageCalculator.Fighter();
        de.work0x16 = 300;
        de.resistHi16 = new int[] {0, 0, 0, 0};
        de.critWork = 0;
        int dmg = DamageCalculator.calc(at, de, 1.0, 0.5);
        fail += ok("full_nonneg", dmg >= 0);

        System.out.println(fail == 0 ? "DAMAGE CALC ALL PASS" : "DAMAGE CALC FAIL " + fail);
        if (fail != 0) System.exit(1);
    }

    static int eq(String n, Object a, Object b) {
        boolean ok = String.valueOf(a).equals(String.valueOf(b));
        System.out.println((ok ? "ok   " : "FAIL ") + n + " expect=" + a + " actual=" + b);
        return ok ? 0 : 1;
    }
    static int ok(String n, boolean c) {
        System.out.println((c ? "ok   " : "FAIL ") + n);
        return c ? 0 : 1;
    }
}