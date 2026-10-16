package csa.net.saac;

/**
 * saacproto command names (gmsv ↔ account server). Same frame as lssproto.
 * Full list: data/protocol-commands.json (73 commands).
 */
public final class SaacCommands {
    public static final String AC_SERVER_LOGIN = "ACServerLogin";
    public static final String AC_CHAR_LOGIN = "ACCharLogin";
    public static final String AC_CHAR_LIST = "ACCharList";
    public static final String AC_CHAR_LOAD = "ACCharLoad";
    public static final String AC_CHAR_SAVE = "ACCharSave";
    public static final String AC_CHAR_DELETE = "ACCharDelete";
    public static final String AC_CHAR_GET_POOL_ITEM = "ACCharGetPoolItem";
    public static final String AC_CHAR_GET_POOL_PET = "ACCharGetPoolPet";
    public static final String AC_CHAR_INSERT_POOL_ITEM = "ACCharInsertPoolItem";
    public static final String AC_CHAR_INSERT_POOL_PET = "ACCharInsertPoolPet";
    public static final String AC_ADD_FM = "ACAddFM";
    public static final String AC_JOIN_FM = "ACJoinFM";
    public static final String AC_LEAVE_FM = "ACLeaveFM";
    public static final String AC_FIX_FM_DATA = "ACFixFMData";
    public static final String AC_GET_FM_DATA = "ACGetFMData";
    public static final String AC_FM_CHAR_LOGIN = "ACFMCharLogin";
    public static final String AC_FM_CHAR_LOGOUT = "ACFMCharLogout";
    public static final String AC_LOCK = "ACLock";
    public static final String AC_KICK = "ACKick";
    public static final String AC_U_CHECK = "ACUCheck";
    public static final String DB_GET_ENTRY_STRING = "DBGetEntryString";
    public static final String DB_UPDATE_ENTRY_STRING = "DBUpdateEntryString";
    public static final String DB_DELETE_ENTRY_STRING = "DBDeleteEntryString";
    public static final String DB_GET_ENTRY_INT = "DBGetEntryInt";
    public static final String DB_UPDATE_ENTRY_INT = "DBUpdateEntryInt";
    public static final String BROADCAST = "Broadcast";
    public static final String MESSAGE = "Message";

    /** ACServerLogin first arg must equal this on recv. */
    public static final String LOGIN_SUCCESS_TOKEN = "successful";

    private SaacCommands() {}
}