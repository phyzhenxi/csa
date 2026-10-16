package csa.net.login;

import csa.net.util.UtilCodec;

/**
 * Login-path response senders matching lssproto_*_send (util_SendMesg envelope).
 *
 * Shared encode path (all five functions):
 *   key = CONNECT_getCdkey() + "1234567"
 *   body = util_mkstring(...) [+ util_mkstring(...)]
 *   body += util_mkint(total_src_len)
 *   _util_SendMesg(file, line, fd, code, body)
 *   wire = "&;{code+0x17}{body};#;"
 *
 * Codes observed:
 *   ClientLogin   0x48 → funcId 95
 *   CharList      0x50 → funcId 103
 *   CharLogin     0x4E → funcId 101
 *   CreateNewChar 0x4A → funcId 97
 *   CharLogout    0x52 → funcId 105
 */
public final class LoginResponses {

    public static final int CODE_CLIENT_LOGIN = 0x48;
    public static final int CODE_CHAR_LIST = 0x50;
    public static final int CODE_CHAR_LOGIN = 0x4E;
    public static final int CODE_CREATE_NEW_CHAR = 0x4A;
    public static final int CODE_CHAR_LOGOUT = 0x52;

    private LoginResponses() {}

    /**
     * ClientLogin_send(fd, result):
     *   mkstring(result) + mkint(strlen(result))
     */
    public static String clientLogin(String result, String cdkey) {
        String key = UtilCodec.keyBuffer(cdkey);
        String body = UtilCodec.mkString(result == null ? "" : result, key);
        int srcLen = (result == null ? "" : result).getBytes(UtilCodec.GBK).length;
        body += UtilCodec.mkInt(srcLen, key);
        return UtilCodec.envelope(CODE_CLIENT_LOGIN, body);
    }

    /**
     * CharList_send(fd, a, b) / CharLogin_send / CreateNewChar_send / CharLogout_send:
     *   mkstring(a) + mkstring(b) + mkint(strlen(a)+strlen(b))
     */
    public static String twoString(int code, String a, String b, String cdkey) {
        String key = UtilCodec.keyBuffer(cdkey);
        String sa = a == null ? "" : a;
        String sb = b == null ? "" : b;
        String body = UtilCodec.mkString(sa, key) + UtilCodec.mkString(sb, key);
        int srcLen = sa.getBytes(UtilCodec.GBK).length + sb.getBytes(UtilCodec.GBK).length;
        body += UtilCodec.mkInt(srcLen, key);
        return UtilCodec.envelope(code, body);
    }

    public static String charList(String status, String data, String cdkey) {
        return twoString(CODE_CHAR_LIST, status, data, cdkey);
    }

    public static String charLogin(String status, String data, String cdkey) {
        return twoString(CODE_CHAR_LOGIN, status, data, cdkey);
    }

    public static String createNewChar(String status, String data, String cdkey) {
        return twoString(CODE_CREATE_NEW_CHAR, status, data, cdkey);
    }

    public static String charLogout(String status, String data, String cdkey) {
        return twoString(CODE_CHAR_LOGOUT, status, data, cdkey);
    }

    /**
     * Game-server selection list sent after successful ClientLogin.
     * Two entries, both local gateway (calibration point for real client).
     * Encoded as packed string: name|host|port;name|host|port
     */
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 1234;

    public static String serverList(String cdkey) {
        String a = "server1|" + DEFAULT_HOST + "|" + DEFAULT_PORT;
        String b = "server2|" + DEFAULT_HOST + "|" + DEFAULT_PORT;
        String packed = a + ";" + b;
        // reuse two-string envelope so client can parse a list payload
        return twoString(CODE_CHAR_LIST, String.valueOf(2), packed, cdkey);
    }

    /** Alternate: include server list inside ClientLogin result payload. */
    public static String clientLoginWithServers(String result, String cdkey) {
        String servers = "server1|" + DEFAULT_HOST + "|" + DEFAULT_PORT
                + ";server2|" + DEFAULT_HOST + "|" + DEFAULT_PORT;
        return clientLogin(result + "|" + servers, cdkey);
    }
}
