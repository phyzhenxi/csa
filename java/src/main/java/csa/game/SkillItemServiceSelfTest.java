package csa.game;

/**
 * Deterministic self-test for SkillItemService.
 * Run: javac + java (no deps). Expects print of SKILL ITEM PASS.
 */
public final class SkillItemServiceSelfTest {
    public static void main(String[] args) {
        int fail = 0;
        SkillItemService svc = new SkillItemService(42L);

        // add item
        svc.seedItem(1, 2);
        fail += eq("seed_count", 2, svc.itemCount(1));

        // use it (count--)
        fail += eq("use1", SkillItemService.ITEM_OK, svc.useItem(1));
        fail += eq("count_after_use1", 1, svc.itemCount(1));
        fail += eq("use2", SkillItemService.ITEM_OK, svc.useItem(1));
        fail += eq("count_after_use2", 0, svc.itemCount(1));

        // useSkill
        svc.seedSkill(7, 3);
        fail += eq("useSkill", SkillItemService.SKILL_OK, svc.useSkill(7, 99));
        fail += ok("useSkill_dmg_pos", svc.lastDamage() > 0);

        // skillUp level++
        fail += eq("skillUp", SkillItemService.SKILLUP_OK, svc.skillUp(7));
        fail += eq("level_after_up", 4, svc.skillLevel(7));

        // use missing item -> ERR
        fail += eq("use_missing", SkillItemService.ITEM_ERR, svc.useItem(2));
        fail += eq("use_empty", SkillItemService.ITEM_ERR, svc.useItem(1));

        // missing skill -> ERR
        fail += eq("useSkill_missing", SkillItemService.SKILL_ERR, svc.useSkill(9, 1));
        fail += eq("useSkill_missing_dmg", 0, svc.lastDamage());

        // skill encode string (lv=4|id=7|)
        fail += eq("skill_str", "lv=4|id=7|", svc.encodeSkillString(7));

        System.out.println(fail == 0 ? "SKILL ITEM PASS" : "SKILL ITEM FAIL " + fail);
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
