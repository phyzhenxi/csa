package csa.battle;

/**
 * Deterministic self-test for BattleService.
 * Run: javac + java (no deps). Expects print of BATTLE SERVICE PASS.
 */
public final class BattleServiceSelfTest {
    public static void main(String[] args) {
        int fail = 0;
        long seed = 42L;

        // attack: damage must be > 0
        BattleService atkSvc = new BattleService(seed);
        BattleService.BattleResult atk = atkSvc.onAction(BattleService.ACTION_ATTACK);
        fail += ok("attack_dmg_pos", atk.damage > 0);
        fail += eq("attack_token", "B 1", atk.replyToken);

        // defend: same seed → halves damage, must be < attack
        BattleService defSvc = new BattleService(seed);
        BattleService.BattleResult def = defSvc.onAction(BattleService.ACTION_DEFEND);
        fail += ok("defend_dmg_lt_atk", def.damage < atk.damage);
        fail += eq("defend_token", "B 3", def.replyToken);
        fail += ok("defend_half_approx", def.damage == atk.damage / 2);

        // skill: same seed path, higher attr ratio → damage > 0 and token B 2
        BattleService skillSvc = new BattleService(seed);
        BattleService.BattleResult skill = skillSvc.onAction(BattleService.ACTION_SKILL);
        fail += ok("skill_dmg_pos", skill.damage > 0);
        fail += eq("skill_token", "B 2", skill.replyToken);

        // unknown action → error token, zero damage
        BattleService unkSvc = new BattleService(seed);
        BattleService.BattleResult unk = unkSvc.onAction(99);
        fail += eq("unknown_token", BattleService.TOKEN_ERROR, unk.replyToken);
        fail += eq("unknown_dmg", 0, unk.damage);

        // deterministic: same seed → identical attack damage
        BattleService again = new BattleService(seed);
        BattleService.BattleResult atk2 = again.onAction(BattleService.ACTION_ATTACK);
        fail += eq("seed_determinism", atk.damage, atk2.damage);

        System.out.println(fail == 0 ? "BATTLE SERVICE PASS" : "BATTLE SERVICE FAIL " + fail);
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
