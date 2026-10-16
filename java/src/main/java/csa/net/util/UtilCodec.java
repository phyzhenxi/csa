package csa.net.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Keyed base64-like codec used by util_SendMesg envelope (autil.c).
 *
 * Alphabet @ 0x82BE028:
 *   0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz{}
 *
 * Default key when buffer empty: "cary" (0x89A4840 := 0x79726163)
 * Login path key: cdkey + "1234567"
 *
 * util_swapint @ 0x8098CD0 — permute 4 bytes by format "3142" then NOT (in mkint)
 * util_256to64_shl @ 0x8098D50 — encode: out = alphabet[(bits - key[i]) & 0x3f]
 * util_256to64_shr @ 0x8098EE0 — decode-ish sibling: out = alphabet[(bits + key[i]) & 0x3f]
 *
 * Wire fragment from util_mkstring/util_mkint ends with ';'.
 */
public final class UtilCodec {

    public static final Charset GBK = Charset.forName("GBK");
    public static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz{}";
    public static final String DEFAULT_KEY = "cary";
    /** util_mkint byte-perm format string @ 0x82BE014. */
    public static final String INT_SWAP_FMT = "3142";

    private UtilCodec() {}

    /** Build key buffer content: cdkey ++ "1234567", or "cary" when empty. */
    public static String keyBuffer(String cdkey) {
        if (cdkey == null || cdkey.isEmpty()) return DEFAULT_KEY;
        return cdkey + "1234567";
    }

    /**
     * util_swapint: reorder 4 bytes of {@code value} using fmt chars as 1-based
     * source indices ( '3','1','4','2' - 0x31 ), i.e. dest[0]=src[2], dest[1]=src[0], ...
     */
    public static int swapInt(int value, String fmt) {
        byte[] src = new byte[] {
            (byte) (value), (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24)
        };
        byte[] dst = new byte[4];
        if (fmt == null || fmt.length() < 4) fmt = INT_SWAP_FMT;
        for (int i = 0; i < 4; i++) {
            int srcIdx = (fmt.charAt(i) & 0xFF) - 0x31;
            if (srcIdx >= 0 && srcIdx < 4) {
                dst[i] = src[srcIdx];
            }
        }
        return (dst[0] & 0xFF) | ((dst[1] & 0xFF) << 8) | ((dst[2] & 0xFF) << 16) | ((dst[3] & 0xFF) << 24);
    }

    /**
     * util_256to64_shl — 3 bytes → 4 chars, (bits - key) & 0x3f into alphabet.
     * Key index wraps to 0 when it hits NUL (key length).
     */
    public static String encodeShl(byte[] src, String key) {
        if (src == null || src.length == 0) return "";
        if (key == null || key.isEmpty()) key = DEFAULT_KEY;
        byte[] k = key.getBytes(StandardCharsets.ISO_8859_1);
        StringBuilder out = new StringBuilder((src.length + 2) / 3 * 4 + 2);
        int carry = 0;
        int ki = 0;
        for (int i = 0; i < src.length; i++) {
            int shift = (i % 3) * 2;
            int bits = ((src[i] & 0xFF) << shift) | carry;
            int kb = k[ki];
            ki++;
            if (ki >= k.length) ki = 0; // NUL wrap
            int idx = (bits - kb) & 0x3F;
            out.append(ALPHABET.charAt(idx));
            carry = bits >>> 6;
            if (i % 3 == 2) {
                kb = k[ki];
                ki++;
                if (ki >= k.length) ki = 0;
                idx = (carry - kb) & 0x3F;
                out.append(ALPHABET.charAt(idx));
                carry = 0;
                ki = 0; // boundary reset observed at i%3==2 path
            }
        }
        if (carry != 0) {
            int kb = k[ki];
            int idx = (carry - kb) & 0x3F;
            out.append(ALPHABET.charAt(idx));
        }
        return out.toString();
    }

    /** util_256to64_shr — same packing, (bits + key) & 0x3f. */
    public static String encodeShr(byte[] src, String key) {
        if (src == null || src.length == 0) return "";
        if (key == null || key.isEmpty()) key = DEFAULT_KEY;
        byte[] k = key.getBytes(StandardCharsets.ISO_8859_1);
        StringBuilder out = new StringBuilder((src.length + 2) / 3 * 4 + 2);
        int carry = 0;
        int ki = 0;
        for (int i = 0; i < src.length; i++) {
            int shift = (i % 3) * 2;
            int bits = ((src[i] & 0xFF) << shift) | carry;
            int kb = k[ki];
            ki++;
            if (ki >= k.length) ki = 0;
            int idx = (bits + kb) & 0x3F;
            out.append(ALPHABET.charAt(idx));
            carry = bits >>> 6;
            if (i % 3 == 2) {
                kb = k[ki];
                ki++;
                if (ki >= k.length) ki = 0;
                idx = (carry + kb) & 0x3F;
                out.append(ALPHABET.charAt(idx));
                carry = 0;
                ki = 0;
            }
        }
        if (carry != 0) {
            int kb = k[ki];
            int idx = (carry + kb) & 0x3F;
            out.append(ALPHABET.charAt(idx));
        }
        return out.toString();
    }

    /** util_mkstring: encodeShl(src) + ';'. */
    public static String mkString(String value, String key) {
        if (value == null) return ";";
        return encodeShl(value.getBytes(GBK), key) + ";";
    }

    /**
     * util_mkint: swapInt(v, "3142") then NOT, then encodeShr of the 4 bytes + ';'.
     * Mirrors util_mkint @ 0x8099300.
     */
    public static String mkInt(int value, String key) {
        int swapped = swapInt(value, INT_SWAP_FMT);
        int notv = ~swapped;
        byte[] raw = new byte[] {
            (byte) notv, (byte) (notv >> 8), (byte) (notv >> 16), (byte) (notv >> 24)
        };
        return encodeShr(raw, key) + ";";
    }

    /** util_SendMesg outer envelope: "&;%d%s;#;". */
    public static String envelope(int code, String body) {
        return String.format("&;%d%s;#;", code + 0x17, body);
    }
}
