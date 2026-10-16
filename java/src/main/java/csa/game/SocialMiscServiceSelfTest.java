package csa.game;

/**
 * Deterministic self-test for SocialMiscService.
 * Run: javac + java (no deps). Expects print of SOCIAL MISC PASS.
 */
public final class SocialMiscServiceSelfTest {
    public static void main(String[] args) {
        int fail = 0;
        SocialMiscService svc = new SocialMiscService();

        // ---- PMSG private message ----
        fail += eq("pmsg_ok", SocialMiscService.PMSG_OK,
                svc.privateMessage("alice", "hello"));
        fail += ok("pmsg_last", svc.lastPmsg() != null);
        if (svc.lastPmsg() != null) {
            fail += eq("pmsg_last_to", "alice", svc.lastPmsg().to);
            fail += eq("pmsg_last_text", "hello", svc.lastPmsg().text);
        }

        fail += eq("pmsg_empty_to", SocialMiscService.PMSG_ERR,
                svc.privateMessage("", "hello"));
        fail += eq("pmsg_blank_to", SocialMiscService.PMSG_ERR,
                svc.privateMessage("  ", "hello"));
        fail += eq("pmsg_empty_text", SocialMiscService.PMSG_ERR,
                svc.privateMessage("alice", ""));
        fail += eq("pmsg_null_text", SocialMiscService.PMSG_ERR,
                svc.privateMessage("alice", null));

        // text length > 200 -> ERR; exactly 200 -> ok
        String over = "x".repeat(SocialMiscService.PMSG_MAX_TEXT + 1);
        fail += eq("pmsg_too_long", SocialMiscService.PMSG_ERR,
                svc.privateMessage("alice", over));
        String max = "y".repeat(SocialMiscService.PMSG_MAX_TEXT);
        fail += eq("pmsg_max_len", SocialMiscService.PMSG_OK,
                svc.privateMessage("alice", max));

        // seedable last PMSG
        svc.seedLastPmsg("bob", "seeded");
        fail += eq("pmsg_seeded_to", "bob", svc.lastPmsg().to);
        fail += eq("pmsg_seeded_text", "seeded", svc.lastPmsg().text);

        // ---- STV street vendor ----
        fail += eq("stv_open", SocialMiscService.STV_OK,
                svc.streetVendorOpen("小店"));
        fail += ok("stv_flag", svc.isStreetVendor());
        fail += eq("stv_name", "小店", svc.vendorName());
        fail += eq("stv_open_again", SocialMiscService.STV_ERR,
                svc.streetVendorOpen("另一家"));
        fail += eq("stv_open_blank", SocialMiscService.STV_ERR,
                svc.streetVendorOpen("  "));

        fail += eq("stv_close", SocialMiscService.STV_OK, svc.streetVendorClose());
        fail += ok("stv_flag_off", !svc.isStreetVendor());
        fail += ok("stv_name_cleared", svc.vendorName() == null);
        fail += eq("stv_close_again", SocialMiscService.STV_ERR, svc.streetVendorClose());

        // seedable vendor flag
        svc.seedStreetVendor(true);
        fail += ok("stv_seeded", svc.isStreetVendor());
        fail += eq("stv_close_seeded", SocialMiscService.STV_OK, svc.streetVendorClose());
        svc.seedStreetVendor(false);
        fail += ok("stv_seeded_off", !svc.isStreetVendor());

        // ---- JD job daily ----
        fail += eq("jd_accept", SocialMiscService.JD_OK, svc.jobDailyAccept(10));
        fail += ok("jd_accepted", svc.isJobAccepted(10));
        fail += eq("jd_accept_again", SocialMiscService.JD_OK, svc.jobDailyAccept(10));
        fail += eq("jd_complete", SocialMiscService.JD_OK, svc.jobDailyComplete(10));
        fail += ok("jd_done", svc.isJobCompleted(10));
        fail += ok("jd_done_unaccepted", !svc.isJobAccepted(10));

        fail += eq("jd_complete_again", SocialMiscService.JD_ERR_DONE,
                svc.jobDailyComplete(10));
        fail += eq("jd_accept_done", SocialMiscService.JD_ERR_DONE,
                svc.jobDailyAccept(10));

        // complete without accept -> ERR
        fail += eq("jd_complete_never", SocialMiscService.JD_ERR,
                svc.jobDailyComplete(99));

        // negative jobId -> ERR
        fail += eq("jd_accept_neg", SocialMiscService.JD_ERR, svc.jobDailyAccept(-1));
        fail += eq("jd_complete_neg", SocialMiscService.JD_ERR, svc.jobDailyComplete(-1));

        // seedable completed job set
        svc.seedCompletedJob(55);
        fail += ok("jd_seeded_done", svc.isJobCompleted(55));
        fail += eq("jd_accept_seeded_done", SocialMiscService.JD_ERR_DONE,
                svc.jobDailyAccept(55));
        fail += eq("jd_seeded_done_set", true, svc.completedJobs().contains(55));

        System.out.println(fail == 0 ? "SOCIAL MISC PASS" : "SOCIAL MISC FAIL " + fail);
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
