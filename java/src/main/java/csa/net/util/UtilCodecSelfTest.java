package csa.net.util;

public final class UtilCodecSelfTest {
    public static void main(String[] args) {
        int fail = 0;
        fail += eq("alpha_len", 64, UtilCodec.ALPHABET.length());
        fail += eq("default_key", "cary", UtilCodec.DEFAULT_KEY);
        fail += eq("key_empty", "cary", UtilCodec.keyBuffer(""));
        fail += eq("key_cdkey", "user1234567", UtilCodec.keyBuffer("user"));

        // swapInt "3142": dest[0]=src[2], dest[1]=src[0], dest[2]=src[3], dest[3]=src[1]
        int v = 0x11223344;
        int s = UtilCodec.swapInt(v, "3142");
        // src bytes LE: 44 33 22 11 → dest: 22 44 11 33 → 0x33114422
        fail += eq("swap", 0x33114422, s);

        String enc = UtilCodec.encodeShl(new byte[] {'a','b','c'}, "cary");
        fail += ok("enc_len", enc.length() == 4);
        fail += ok("enc_alpha", allInAlphabet(enc));

        String enc2 = UtilCodec.encodeShl("hello".getBytes(UtilCodec.GBK), "user1234567");
        fail += ok("enc2_alpha", allInAlphabet(enc2));

        String mk = UtilCodec.mkString("ab", "cary");
        fail += ok("mk_semi", mk.endsWith(";"));
        fail += ok("mk_alpha", allInAlphabet(mk.substring(0, mk.length()-1)));

        String mi = UtilCodec.mkInt(42, "cary");
        fail += ok("mi_semi", mi.endsWith(";"));
        fail += ok("mi_alpha", allInAlphabet(mi.substring(0, mi.length()-1)));

        String env = UtilCodec.envelope(0x48, "XX");
        fail += eq("env", "&;95XX;#;", env);

        System.out.println(fail == 0 ? "UTIL CODEC ALL PASS" : "UTIL CODEC FAIL " + fail);
        if (fail != 0) System.exit(1);
    }

    static boolean allInAlphabet(String s) {
        for (char c : s.toCharArray()) {
            if (UtilCodec.ALPHABET.indexOf(c) < 0) return false;
        }
        return !s.isEmpty();
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