package csa.net.cmd;

/**
 * P0-b command names and argument kinds for the high-frequency lssproto set.
 * Full table: data/command-signatures.json
 */
public final class Commands {
    public static final String W = "W";
    public static final String W2 = "W2";
    public static final String C = "C";
    public static final String B = "B";
    public static final String M = "M";
    public static final String S = "S";
    public static final String TK = "TK";
    public static final String TD = "TD";
    public static final String WN = "WN";
    public static final String PR = "PR";
    public static final String PS = "PS";
    public static final String FS = "FS";
    public static final String FM = "FM";
    public static final String MSG = "MSG";
    public static final String KN = "KN";
    public static final String EN = "EN";
    public static final String CLIENT_LOGIN = "ClientLogin";
    public static final String CHAR_LIST = "CharList";
    public static final String CHAR_LOGIN = "CharLogin";
    public static final String CREATE_NEW_CHAR = "CreateNewChar";

    public static final int WORK_BATTLE = 0x47;
    public static final int WORK_STREET_VENDOR = 0x137;
    public static final int INT_X = 5;
    public static final int INT_Y = 6;
    public static final int WN_MAX_DATA = 0x800;
    public static final int KN_MAX_NAME = 0x10;

    private Commands() {}

    public static boolean needsStringCheck(String cmd) {
        switch (cmd) {
            case C: case TK: case TD: case FM: case KN: case WN:
            case CLIENT_LOGIN: case CHAR_LOGIN: case CREATE_NEW_CHAR:
                return true;
            default:
                return false;
        }
    }
}