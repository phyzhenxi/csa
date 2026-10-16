package csa.game;

import java.util.HashSet;
import java.util.Set;

/**
 * EV / MU / KN / PR handlers (gmsvjt recv sites).
 * EV @ 0x8091180  MU @ 0x808FC90  KN @ 0x808F980  PR @ 0x8090040
 */
public final class EventSocialService {

    public static final String TOKEN_EV_OK = "EV ok";
    public static final String TOKEN_EV_ERR = "EV ERR";
    public static final String TOKEN_EV_EVENT = "EV ERR_EVENT";
    public static final String TOKEN_MU_OK = "MU ok";
    public static final String TOKEN_KN_OK = "KN ok";
    public static final String TOKEN_KN_ERR = "KN ERR";
    public static final String TOKEN_KN_ERR_NAME = "KN ERR_NAME";
    public static final String TOKEN_PR_OK = "PR ok";
    public static final String TOKEN_PR_ERR_ZONE = "PR ERR_ZONE";

    public static final int PET_NAME_MAX = 0x10;
    /** SELECT_SUMMON_TASK equivalent — rejected event. */
    public static final int EVENT_SUMMON = 9;
    public static final int NO_ZONE = 0;

    private int lastSeqno = -1;
    private int lastObjindex = -1;
    private String lastData = "";
    private int unitX, unitY, unitDir, unitFlags;
    private String petName = "";
    private boolean partyZoneOk = true;
    private final Set<Integer> completedEvents = new HashSet<>();

    public String eventTrigger(int seqno, int objindex, String data) {
        if (seqno < 0 || objindex < 0) {
            return TOKEN_EV_ERR;
        }
        if (seqno == EVENT_SUMMON) {
            return TOKEN_EV_EVENT;
        }
        lastSeqno = seqno;
        lastObjindex = objindex;
        lastData = data == null ? "" : data;
        completedEvents.add(seqno);
        return TOKEN_EV_OK;
    }

    public String moveUnit(int x, int y, int dir, int flags) {
        unitX = x;
        unitY = y;
        unitDir = dir;
        unitFlags = flags;
        return TOKEN_MU_OK;
    }

    /** KN: pet name; len<=16, reject control/space/illegal chars (checkStringErr). */
    public String renamePet(String name) {
        if (name == null || name.isEmpty()) {
            return TOKEN_KN_ERR;
        }
        if (name.length() > PET_NAME_MAX) {
            return TOKEN_KN_ERR_NAME;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c <= 0x20 || c == '\'' || c == ';' || c == 0x7F) {
                return TOKEN_KN_ERR_NAME;
            }
        }
        petName = name;
        return TOKEN_KN_OK;
    }

    public String partyRecruit(int x, int y, int zone) {
        if (zone == NO_ZONE || !partyZoneOk) {
            return TOKEN_PR_ERR_ZONE;
        }
        unitX = x;
        unitY = y;
        return TOKEN_PR_OK;
    }

    public void seedPartyZoneOk(boolean ok) {
        this.partyZoneOk = ok;
    }

    public void seedPetName(String name) {
        this.petName = name == null ? "" : name;
    }

    public int lastSeqno() { return lastSeqno; }
    public int lastObjindex() { return lastObjindex; }
    public String lastData() { return lastData; }
    public int unitX() { return unitX; }
    public int unitY() { return unitY; }
    public int unitDir() { return unitDir; }
    public int unitFlags() { return unitFlags; }
    public String petName() { return petName; }
    public boolean isEventDone(int seq) { return completedEvents.contains(seq); }

    public static void main(String[] args) {
        int fail = 0;
        EventSocialService s = new EventSocialService();
        fail += ok("ev_ok", TOKEN_EV_OK.equals(s.eventTrigger(1, 10, "d")));
        fail += ok("ev_neg", TOKEN_EV_ERR.equals(s.eventTrigger(-1, 1, "d")));
        fail += ok("ev_summon", TOKEN_EV_EVENT.equals(s.eventTrigger(EVENT_SUMMON, 1, "d")));
        fail += ok("ev_done", s.isEventDone(1));
        fail += ok("mu", TOKEN_MU_OK.equals(s.moveUnit(3, 4, 1, 2)));
        fail += eq("mu_x", 3, s.unitX());
        fail += ok("kn_ok", TOKEN_KN_OK.equals(s.renamePet("Rex")));
        fail += ok("kn_empty", TOKEN_KN_ERR.equals(s.renamePet("")));
        fail += ok("kn_long", TOKEN_KN_ERR_NAME.equals(s.renamePet("X".repeat(PET_NAME_MAX + 1))));
        fail += ok("kn_space", TOKEN_KN_ERR_NAME.equals(s.renamePet("a b")));
        fail += eq("kn_name", "Rex", s.petName());
        s.seedPartyZoneOk(true);
        fail += ok("pr_ok", TOKEN_PR_OK.equals(s.partyRecruit(5, 6, 1)));
        fail += ok("pr_zone", TOKEN_PR_ERR_ZONE.equals(s.partyRecruit(5, 6, NO_ZONE)));
        s.seedPartyZoneOk(false);
        fail += ok("pr_block", TOKEN_PR_ERR_ZONE.equals(s.partyRecruit(5, 6, 1)));
        System.out.println(fail == 0 ? "EVENT SOCIAL PASS" : "EVENT SOCIAL FAIL " + fail);
        if (fail != 0) System.exit(1);
    }

    static int ok(String n, boolean c) {
        System.out.println((c ? "ok   " : "FAIL ") + n);
        return c ? 0 : 1;
    }

    static int eq(String n, Object a, Object b) {
        boolean c = String.valueOf(a).equals(String.valueOf(b));
        System.out.println((c ? "ok   " : "FAIL ") + n + " expect=" + a + " actual=" + b);
        return c ? 0 : 1;
    }
}
