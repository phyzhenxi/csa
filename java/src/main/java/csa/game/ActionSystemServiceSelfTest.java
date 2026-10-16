package csa.game;

/**
 * Deterministic self-test for ActionSystemService.
 * Run: javac + java (no deps). Expects print of ACTION SYSTEM PASS.
 */
public final class ActionSystemServiceSelfTest {
    public static void main(String[] args) {
        int fail = 0;
        ActionSystemService svc = new ActionSystemService();

        // ---- FS feature flags ----
        fail += eq("fs_ok", ActionSystemService.FS_OK,
                svc.setFeatureFlags(0));
        fail += eq("fs_zero_flags", 0, svc.featureFlags());
        fail += eq("fs_ok_bits", ActionSystemService.FS_OK,
                svc.setFeatureFlags(0x5A5A));
        fail += eq("fs_bits_recorded", 0x5A5A, svc.featureFlags());
        fail += eq("fs_neg_ok", ActionSystemService.FS_OK,
                svc.setFeatureFlags(-1));
        fail += eq("fs_neg_recorded", -1, svc.featureFlags());

        // seedable featureFlags
        svc.seedFeatureFlags(0x1234);
        fail += eq("fs_seeded", 0x1234, svc.featureFlags());

        // ---- EN place / drop ----
        fail += eq("en_ok", ActionSystemService.EN_OK, svc.placeObject(10, 20));
        fail += eq("en_zero", ActionSystemService.EN_OK, svc.placeObject(0, 0));
        fail += eq("en_neg", ActionSystemService.EN_OK, svc.placeObject(-1, -2));

        // mode==2 (WorkInt 0x46) rejects
        svc.seedMode(ActionSystemService.MODE_PLACE_REJECT);
        fail += eq("en_mode", ActionSystemService.MODE_PLACE_REJECT, svc.mode());
        fail += eq("en_err_mode", ActionSystemService.EN_ERR_MODE,
                svc.placeObject(10, 20));

        // other modes accept
        svc.seedMode(0);
        fail += eq("en_mode0_ok", ActionSystemService.EN_OK, svc.placeObject(1, 1));
        svc.seedMode(1);
        fail += eq("en_mode1_ok", ActionSystemService.EN_OK, svc.placeObject(1, 1));
        svc.seedMode(3);
        fail += eq("en_mode3_ok", ActionSystemService.EN_OK, svc.placeObject(1, 1));
        svc.seedMode(2);
        fail += eq("en_mode2_err", ActionSystemService.EN_ERR_MODE,
                svc.placeObject(0, 0));
        svc.seedMode(0);

        // ---- DP dump / parcel ----
        fail += eq("dp_ok", ActionSystemService.DP_OK,
                svc.dumpParcel(7, "parcelBox"));
        fail += ok("dp_last", svc.lastDump() != null);
        if (svc.lastDump() != null) {
            fail += eq("dp_last_item", 7, svc.lastDump().itemId);
            fail += eq("dp_last_dest", "parcelBox", svc.lastDump().dest);
        }

        fail += eq("dp_neg_item", ActionSystemService.DP_ERR,
                svc.dumpParcel(-1, "parcelBox"));
        fail += eq("dp_empty_dest", ActionSystemService.DP_ERR,
                svc.dumpParcel(7, ""));
        fail += eq("dp_blank_dest", ActionSystemService.DP_ERR,
                svc.dumpParcel(7, "  "));
        fail += eq("dp_null_dest", ActionSystemService.DP_ERR,
                svc.dumpParcel(7, null));
        fail += eq("dp_zero_item", ActionSystemService.DP_OK,
                svc.dumpParcel(0, "warehouse"));

        // seedable lastDump
        svc.seedLastDump(42, "seededDest");
        fail += eq("dp_seeded_item", 42, svc.lastDump().itemId);
        fail += eq("dp_seeded_dest", "seededDest", svc.lastDump().dest);

        // ---- DI item interact ----
        fail += eq("di_ok", ActionSystemService.DI_OK,
                svc.itemInteract(3, "use"));
        fail += ok("di_last", svc.lastInteract() != null);
        if (svc.lastInteract() != null) {
            fail += eq("di_last_slot", 3, svc.lastInteract().slot);
            fail += eq("di_last_action", "use", svc.lastInteract().action);
        }

        fail += eq("di_neg_slot", ActionSystemService.DI_ERR,
                svc.itemInteract(-1, "use"));
        fail += eq("di_empty_action", ActionSystemService.DI_ERR,
                svc.itemInteract(3, ""));
        fail += eq("di_blank_action", ActionSystemService.DI_ERR,
                svc.itemInteract(3, "  "));
        fail += eq("di_null_action", ActionSystemService.DI_ERR,
                svc.itemInteract(3, null));
        fail += eq("di_zero_slot", ActionSystemService.DI_OK,
                svc.itemInteract(0, "drop"));

        // seedable lastInteract
        svc.seedLastInteract(9, "seededAction");
        fail += eq("di_seeded_slot", 9, svc.lastInteract().slot);
        fail += eq("di_seeded_action", "seededAction", svc.lastInteract().action);

        System.out.println(fail == 0 ? "ACTION SYSTEM PASS" : "ACTION SYSTEM FAIL " + fail);
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
