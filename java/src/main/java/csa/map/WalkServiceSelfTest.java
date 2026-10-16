package csa.map;

public final class WalkServiceSelfTest {
    public static void main(String[] args) {
        int fail = 0;

        // legal unit step (east)
        fail += eq("step_e", WalkService.OK,
                WalkService.validateMove(10, 10, 11, 10, 2));

        // diagonal unit step ok
        fail += eq("step_diag", WalkService.OK,
                WalkService.validateMove(10, 10, 11, 11, 3));

        // same cell (turn in place) ok
        fail += eq("step_same", WalkService.OK,
                WalkService.validateMove(10, 10, 10, 10, 0));

        // jump 10 tiles rejected
        fail += eq("jump10", WalkService.ERR_JUMP_TOO_FAR,
                WalkService.validateMove(10, 10, 20, 10, 2));

        // applyMove returns new position and records dir
        WalkService.Pos p = WalkService.applyMove(10, 10, 11, 10, 2);
        fail += ok("apply_nonnull", p != null);
        if (p != null) {
            fail += eq("apply_x", 11, p.x);
            fail += eq("apply_y", 10, p.y);
            fail += eq("apply_dir", 2, p.dir);
        }

        // invalid move yields null
        fail += ok("apply_jump_null", WalkService.applyMove(10, 10, 20, 10, 2) == null);

        // map stub defaults to walkable
        fail += ok("map_stub", WalkService.isWalkable(0, 10, 10));

        System.out.println(fail == 0 ? "WALK SERVICE PASS" : "WALK SERVICE FAIL " + fail);
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
