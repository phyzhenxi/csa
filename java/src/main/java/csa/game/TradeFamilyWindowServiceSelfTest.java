package csa.game;

/**
 * Deterministic self-test for TradeFamilyWindowService.
 * Run: javac + java (no deps). Expects print of TRADE FAMILY WINDOW PASS.
 */
public final class TradeFamilyWindowServiceSelfTest {
    public static void main(String[] args) {
        int fail = 0;
        TradeFamilyWindowService svc = new TradeFamilyWindowService();

        // ---- TD trade ----
        fail += eq("trade_ok", TradeFamilyWindowService.TRADE_OK,
                svc.tradeRequest("alice", "bob", 3));
        fail += eq("trade_self", TradeFamilyWindowService.TRADE_ERR,
                svc.tradeRequest("alice", "alice", 3));
        fail += eq("trade_blank", TradeFamilyWindowService.TRADE_ERR,
                svc.tradeRequest("alice", "", 3));
        fail += eq("trade_neg_item", TradeFamilyWindowService.TRADE_ERR,
                svc.tradeRequest("alice", "bob", -1));

        // trade while street-vendoring -> ERR_STREET_VENDOR
        svc.setStreetVendor(true);
        fail += ok("vendor_flag", svc.isStreetVendor());
        fail += eq("trade_vendor", TradeFamilyWindowService.TRADE_ERR_STREET_VENDOR,
                svc.tradeRequest("alice", "bob", 3));
        svc.setStreetVendor(false);
        fail += eq("trade_after_vendor", TradeFamilyWindowService.TRADE_OK,
                svc.tradeRequest("alice", "bob", 3));

        // ---- FM family ----
        fail += eq("fm_join", TradeFamilyWindowService.FAMILY_OK,
                svc.familyJoin("龙之家族"));
        fail += eq("fm_name", "龙之家族", svc.familyName());
        fail += eq("fm_join_blank", TradeFamilyWindowService.FAMILY_ERR,
                svc.familyJoin("  "));
        fail += eq("fm_leave", TradeFamilyWindowService.FAMILY_OK,
                svc.familyLeave());
        fail += ok("fm_cleared", svc.familyName() == null);
        fail += eq("fm_leave_again", TradeFamilyWindowService.FAMILY_ERR,
                svc.familyLeave());

        // ---- WN window ----
        // good seq (matches seeded expected)
        svc.seedExpectedWindow(5);
        fail += eq("wn_good_seq", TradeFamilyWindowService.WINDOW_OK,
                svc.windowSelect(5, 10, 0, "hello"));
        fail += ok("wn_last", svc.lastWindow() != null);
        if (svc.lastWindow() != null) {
            fail += eq("wn_last_seq", 5, svc.lastWindow().seqno);
            fail += eq("wn_last_obj", 10, svc.lastWindow().objindex);
            fail += eq("wn_last_data", "hello", svc.lastWindow().data);
        }

        // bad window: wrong seqno
        fail += eq("wn_bad_seq", TradeFamilyWindowService.WINDOW_ERR_BAD_WINDOW,
                svc.windowSelect(9, 10, 0, "hello"));

        // bad window: illegal select
        fail += eq("wn_bad_select", TradeFamilyWindowService.WINDOW_ERR_BAD_WINDOW,
                svc.windowSelect(5, 10, -1, "x"));

        // bad window: data over 0x800
        String big = "x".repeat(TradeFamilyWindowService.WN_MAX_DATA + 1);
        fail += eq("wn_data_over", TradeFamilyWindowService.WINDOW_ERR_BAD_WINDOW,
                svc.windowSelect(5, 10, 0, big));

        // summon task -> ERR_EVENT
        fail += eq("wn_event", TradeFamilyWindowService.WINDOW_ERR_EVENT,
                svc.windowSelect(5, 10, TradeFamilyWindowService.SELECT_SUMMON_TASK, ""));

        // any seq accepted when no expected is seeded
        svc.seedExpectedWindow(-1);
        fail += eq("wn_any_seq", TradeFamilyWindowService.WINDOW_OK,
                svc.windowSelect(42, 7, 1, "ok"));

        // seedable last window
        svc.seedLastWindow(1, 2, 3, "seeded");
        fail += eq("wn_seeded", 1, svc.lastWindow().seqno);

        System.out.println(fail == 0 ? "TRADE FAMILY WINDOW PASS" : "TRADE FAMILY WINDOW FAIL " + fail);
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
