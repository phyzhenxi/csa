package csa.net.lssp;

/**
 * Golden vectors recovered from gmsvjt (ELF32) disassembly.
 * Run with: javac + java (no deps). Use to validate the codec against the C behavior.
 */
public final class LssProtocolCodecSelfTest {

    public static void main(String[] args) {
        int fail = 0;
        fail += eq("b62 0", "0", LssProtocolCodec.cnv10to62(0));
        fail += eq("b62 61", "Z", LssProtocolCodec.cnv10to62(61));
        fail += eq("b62 62", "10", LssProtocolCodec.cnv10to62(62));
        fail += eq("b62 42", "G", LssProtocolCodec.cnv10to62(42));   // 36+6
        fail += eq("b62 86", "1o", LssProtocolCodec.cnv10to62(86)); // 1*62+24
        fail += eq("b62 123", "1Z", LssProtocolCodec.cnv10to62(123)); // 1*62+61
        fail += eq("parse 1Z", 123L, LssProtocolCodec.parseBase62("1Z"));
        fail += eq("parse neg", -5L, LssProtocolCodec.parseBase62("-5"));

        fail += eq("esc space", "\\S", LssProtocolCodec.escape(" "));
        fail += eq("esc nl", "\\n", LssProtocolCodec.escape("\n"));
        fail += eq("esc cr", "\\r", LssProtocolCodec.escape("\r"));
        fail += eq("esc bs", "\\\\", LssProtocolCodec.escape("\\"));
        fail += eq("esc mix", "a\\Sb\\nc", LssProtocolCodec.escape("a b\nc"));

        fail += eq("unesc space", " ", LssProtocolCodec.descape("\\S"));
        fail += eq("unesc nl", "\n", LssProtocolCodec.descape("\\n"));
        fail += eq("unesc bs", "\\", LssProtocolCodec.descape("\\\\"));
        fail += eq("unesc mix", "a b\nc", LssProtocolCodec.descape("a\\Sb\\nc"));

        String line = LssProtocolCodec.header(7, "Login") + LssProtocolCodec.mkString("hello world") + " "
                + LssProtocolCodec.mkInt(42) + " \n";
        // expected: "7 Login hello\\Sworld G \n"  (42 -> base62 "G")
        fail += eq("frame", "7 Login hello\\Sworld G \n", line);

        LssProtocolCodec.Frame f = LssProtocolCodec.decodeLine("7 Login hello\\Sworld G ");
        fail += eq("frame id", 7L, f.msgId);
        fail += eq("frame cmd", "Login", f.command);
        fail += eq("frame argc", 2, f.tokens.size());
        fail += eq("tok0", "hello\\Sworld", f.tokens.get(0));
        fail += eq("tok1", "G", f.tokens.get(1));
        fail += eq("dem tok0", "hello world", LssProtocolCodec.demString(f.tokens.get(0)));
        fail += eq("dem tok1", 42L, LssProtocolCodec.demInt(f.tokens.get(1)));

        if (fail == 0) {
            System.out.println("ALL PASS");
        } else {
            System.out.println("FAILED: " + fail);
            System.exit(1);
        }
    }

    static int eq(String name, Object expect, Object actual) {
        if (String.valueOf(expect).equals(String.valueOf(actual))) {
            System.out.println("ok   " + name + " = " + actual);
            return 0;
        }
        System.out.println("FAIL " + name + " expect=" + expect + " actual=" + actual);
        return 1;
    }
}
