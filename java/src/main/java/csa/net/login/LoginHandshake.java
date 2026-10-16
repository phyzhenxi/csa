package csa.net.login;

import java.nio.charset.Charset;
import java.util.regex.Pattern;

/**
 * P0-a login handshake contracts recovered from gmsvjt.
 *
 * Wire uses LssProtocolCodec for the outer frame; some responses are additionally
 * wrapped in the util_SendMesg envelope "&;%d%s;#;".
 *
 * MAC field is NOT a real MAC: it is a client version token "gmsv-...".
 */
public final class LoginHandshake {

    public static final Charset GBK = Charset.forName("GBK");

    /** freelogincheck.lua: first dash-segment must be "gmsv". */
    public static final Pattern MAC_TOKEN = Pattern.compile("^gmsv-[0-9A-Za-z-]+$");

    public static final String OK = "ok";
    public static final String ERR_EMPTY = "账号或密码不得为空。";
    public static final String ERR_CHARS = "异常字符的用户名";
    public static final String ERR_LOCKED_ACCOUNT =
            "您的游戏账号已被锁定，无法登录游戏，如有疑问请联系客服。";
    public static final String ERR_LOCKED_IP =
            "您的IP已被锁定，无法登录游戏，如有疑问请联系客服。";
    public static final String ERR_BAD_PASSWORD =
            "您的密码输入错误，请核对后登录。（注意大小写）";
    public static final String ERR_NOT_REGISTERED =
            "您的账号尚未注册，请登录主页，注册后登录游戏。";

    // connection states observed in CONNECT_setState
    public static final int STATE_NOT_LOGIN = 0;
    public static final int STATE_WHILE_LOGIN = 2;
    public static final int STATE_CHAR_LOGIN = 3;

    // util_SendMesg envelope
    public static final String ENVELOPE_FMT = "&;%d%s;#;";
    public static final int ENVELOPE_FUNC_ID_BIAS = 0x17;
    /** Alphabet used by util_mkstring / util_mkint (NOT lssproto base62). */
    public static final String UTIL_ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz{}";
    /** Suffix appended to cdkey in global key buffer 0x89A4840. */
    public static final String KEY_MAGIC = "1234567";

    private LoginHandshake() {}

    /**
     * checkStringErr @ 0x808D620 — returns true if string is rejected.
     * Rejects: &lt;=0x20, 0x27 ('), 0x3B (;), 0x7F, 0x80, 0xFF.
     */
    public static boolean isBadLoginString(String s) {
        if (s == null || s.isEmpty()) {
            return true;
        }
        byte[] raw = s.getBytes(GBK);
        for (byte b : raw) {
            int u = b & 0xFF;
            if (u <= 0x20 || u == 0x27 || u == 0x3B || u == 0x7F || u == 0x80 || u == 0xFF) {
                return true;
            }
        }
        return false;
    }

    /** freelogincheck.lua MAC/token gate. */
    public static boolean isValidMacToken(String mac) {
        return mac != null && MAC_TOKEN.matcher(mac).matches();
    }

    /** Key buffer content: cdkey + "1234567". */
    public static String sessionKeyBuffer(String cdkey) {
        return cdkey + KEY_MAGIC;
    }

    /** util_SendMesg outer frame. */
    public static String envelope(int code, String encodedBody) {
        return String.format(ENVELOPE_FMT, code + ENVELOPE_FUNC_ID_BIAS, encodedBody);
    }

    /**
     * util_mkint: NOT the 32-bit value, then encode with UTIL_ALPHABET (variable width),
     * terminator ';'. This is a simplified reconstruction of the integer arm; full
     * signedness/endian details are in docs/login-handshake.md §7.
     */
    public static String utilMkIntNot(int value) {
        int n = ~value;
        if (n == 0) {
            return "0;";
        }
        boolean neg = n < 0;
        long v = Math.abs((long) n);
        char[] buf = new char[32];
        int i = buf.length;
        while (v > 0) {
            buf[--i] = UTIL_ALPHABET.charAt((int) (v % UTIL_ALPHABET.length()));
            v /= UTIL_ALPHABET.length();
        }
        if (neg) {
            // C path uses 2's complement bit pattern; keep a marker
            buf[--i] = '{';
        }
        return new String(buf, i, buf.length - i) + ";";
    }

    public enum LoginState {
        NOT_LOGIN, WHILE_LOGIN, CHAR_LOGIN
    }
}
