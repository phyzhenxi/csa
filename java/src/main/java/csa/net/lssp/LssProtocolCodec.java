package csa.net.lssp;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * lssproto / saacproto wire codec for 石器时代 CSA (gmsvjt 6.5-derived).
 *
 * Wire grammar (from gmsvjt disassembly):
 *   frame    := header SP args "\n"
 *   header   := msgId SP command SP
 *   msgId    := [0-9]+                // %u, per-connection counter
 *   command  := [A-Za-z0-9_]+         // e.g. Login, CharLogin, B
 *   args     := (token SP)*
 *   token    := escaped-string | base62-int
 *
 * Framing: LF (0x0A) terminated lines (lssproto_consumeLine).
 * Fields:  split on SP 0x20; tokens terminated at SP / CR / LF (lssproto_splitString).
 *
 * Integer encoding (mkstr_int / demkstr_int → cnv10to62 / parse base 62):
 *   alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
 *   0-9 → 0-9, a-z → 10-35, A-Z → 36-61
 *   negative: leading '-'
 *
 * String escaping (escapeString / descapeString):
 *   '\'  -> "\\"     (backslash doubled; following byte processed normally)
 *   ' '  -> "\\S"
 *   '\n' -> "\\n"
 *   '\r' -> "\\r"
 *   bytes >= 0x80: copied as a 2-byte pair (GBK/CJK lead+trail), not escaped
 *
 * CreateHeader format string in binary: "%u %s "  (0x82C0085)
 * mkstr tail separator: " "                         (0x82C063F)
 *
 * Handshake / dispatch note:
 *   ServerDispatchMessage recognizes a magic token
 *   "&;129;PtdT8oWQ4kSK}qYT7nVP3jPK9pYS6mUO2gPU8pXR5lTN;vvVemr;#;"
 *   and also treats ';' as an internal separator in some paths.
 *   Keep these literal if you reimplement login.
 */
public final class LssProtocolCodec {

    public static final Charset GBK = Charset.forName("GBK");
    /** cnv10to62 / demkstr alphabet (verified at 0x82BB4D0). */
    static final String B62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final char SP = ' ';
    public static final char LF = '\n';
    public static final String HANDSHAKE_MAGIC =
            "&;129;PtdT8oWQ4kSK}qYT7nVP3jPK9pYS6mUO2gPU8pXR5lTN;vvVemr;#;";

    private final AtomicLong messageIds = new AtomicLong(1);

    // ---------------------------------------------------------------- header

    /** CreateHeader: sprintf(buf, "%u %s ", msgId, command). */
    public String newHeader(String command) {
        return messageIds.getAndIncrement() + " " + command + " ";
    }

    /** CreateHeader with explicit id (for tests / replay). */
    public static String header(long msgId, String command) {
        return msgId + " " + command + " ";
    }

    // ---------------------------------------------------------------- encode

    /**
     * Encode a full line: header + mkstr(arg)* + LF.
     * Integer args are base62; all args are space-separated; string args escaped.
     */
    public String encode(String command, Object... args) {
        StringBuilder sb = new StringBuilder(newHeader(command));
        for (Object a : args) {
            if (a instanceof Integer || a instanceof Long || a instanceof Short || a instanceof Byte) {
                sb.append(mkInt(((Number) a).longValue()));
            } else if (a instanceof Boolean) {
                sb.append(mkInt(((Boolean) a) ? 1 : 0));
            } else {
                sb.append(mkString(String.valueOf(a)));
            }
            sb.append(SP);
        }
        sb.append(LF);
        return sb.toString();
    }

    /** mkstr_int: cnv10to62(v) + trailing SP is added by encode. */
    public static String mkInt(long v) {
        return cnv10to62(v);
    }

    /** mkstr_string: escapeString(s). */
    public static String mkString(String s) {
        return escape(s);
    }

    // ---------------------------------------------------------------- decode

    /** One parsed line: command + ordered raw tokens (still escaped / base62). */
    public static final class Frame {
        public final long msgId;
        public final String command;
        public final List<String> tokens;

        public Frame(long msgId, String command, List<String> tokens) {
            this.msgId = msgId;
            this.command = command;
            this.tokens = tokens;
        }
    }

    /**
     * Parse one LF-terminated line (without LF). Mirrors splitString + header read.
     */
    public static Frame decodeLine(String line) {
        List<String> raw = splitSpaces(line);
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("empty frame");
        }
        long id = Long.parseLong(raw.get(0));
        if (raw.size() < 2) {
            throw new IllegalArgumentException("missing command");
        }
        String cmd = raw.get(1);
        return new Frame(id, cmd, raw.subList(2, raw.size()));
    }

    /** demkstr_int: base62 (optional '-'). */
    public static long demInt(String tok) {
        return parseBase62(tok);
    }

    /** demkstr_string: descapeString. */
    public static String demString(String tok) {
        return descape(tok);
    }

    // ---------------------------------------------------------------- base62

    /** lssproto_cnv10to62 — integer to base62 text. */
    public static String cnv10to62(long value) {
        if (value == 0) {
            return "0";
        }
        boolean neg = value < 0;
        // mirror C long path; use unsigned magnitude for negatives like C's non-absolute impl
        // C walks digits via repeated divmod 62 on the raw (possibly negative) value.
        // For wire compatibility use standard base62 of absolute value with '-'.
        long n = Math.abs(value);
        char[] buf = new char[32];
        int i = buf.length;
        while (n > 0) {
            buf[--i] = B62.charAt((int) (n % 62));
            n /= 62;
        }
        if (neg) {
            buf[--i] = '-';
        }
        return new String(buf, i, buf.length - i);
    }

    /** demkstr integer parser (0x80ABC90): optional '-', then 0-9 / a-z / A-Z, base 62. */
    public static long parseBase62(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int i = 0;
        boolean neg = false;
        if (s.charAt(0) == '-') {
            neg = true;
            i = 1;
        }
        long acc = 0;
        for (; i < s.length(); i++) {
            int d = b62Digit(s.charAt(i));
            if (d < 0) {
                throw new IllegalArgumentException("bad base62 digit: " + s.charAt(i));
            }
            acc = acc * 62 + d;
        }
        return neg ? -acc : acc;
    }

    static int b62Digit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 36;
        }
        return -1;
    }

    // ---------------------------------------------------------------- escape

    /**
     * lssproto_escapeString.
     * Operates on raw bytes; CJK (byte >= 0x80) copied as pairs.
     * Java API uses GBK so a CJK char becomes 2 bytes, matching the C pair logic.
     */
    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        byte[] in = s.getBytes(GBK);
        StringBuilder out = new StringBuilder(in.length + 8);
        for (int i = 0; i < in.length; i++) {
            int b = in[i] & 0xFF;
            if (b >= 0x80) {
                // 2-byte lead (and trail) copied verbatim (C path @ 0x80AB908)
                out.append((char) b);
                if (i + 1 < in.length) {
                    out.append((char) (in[++i] & 0xFF));
                }
            } else if (b == '\\') {
                out.append("\\\\");
            } else if (b == ' ') {
                out.append("\\S");
            } else if (b == '\n') {
                out.append("\\n");
            } else if (b == '\r') {
                out.append("\\r");
            } else {
                out.append((char) b);
            }
        }
        return out.toString();
    }

    /** lssproto_descapeString. */
    public static String descape(String s) {
        if (s == null) {
            return "";
        }
        byte[] in = s.getBytes(StandardCharsets.ISO_8859_1);
        StringBuilder out = new StringBuilder(in.length);
        for (int i = 0; i < in.length; i++) {
            int b = in[i] & 0xFF;
            if (b >= 0x80) {
                out.append((char) b);
                if (i + 1 < in.length) {
                    out.append((char) (in[++i] & 0xFF));
                }
            } else if (b == '\\') {
                if (i + 1 >= in.length) {
                    out.append('\\');
                    break;
                }
                int n = in[++i] & 0xFF;
                switch (n) {
                    case 'S':
                        out.append(' ');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    default:
                        // C @ 0x80ABA62: emit '\' and swallow the next byte entirely
                        // so "\\" -> "\", "\\A" -> "\"
                        out.append('\\');
                        break;
                }
            } else {
                out.append((char) b);
            }
        }
        return new String(out.toString().getBytes(StandardCharsets.ISO_8859_1), GBK);
    }

    // ---------------------------------------------------------------- split

    /** lssproto_splitString: tokens separated by SP; CR/LF also cut tokens. */
    static List<String> splitSpaces(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == SP) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else if (c == '\n' || c == '\r') {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                break;
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    // ---------------------------------------------------------------- util

    /**
     * Extract complete lines from a byte stream buffer (TCP reassembly).
     * Returns consumed count; leftover partial line stays in buffer.
     */
    public static int consumeLines(StringBuilder buffer, List<String> lines) {
        int consumed = 0;
        int idx;
        while ((idx = buffer.indexOf("\n")) >= 0) {
            lines.add(buffer.substring(0, idx));
            buffer.delete(0, idx + 1);
            consumed += idx + 1;
        }
        return consumed;
    }
}
