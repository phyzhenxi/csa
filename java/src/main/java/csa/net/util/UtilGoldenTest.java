package csa.net.util;

public final class UtilGoldenTest {
    // Golden vectors from scripts/util_golden.py (C reconstruction).
    // Row format (TAB-separated): op\tkey\tinput\texpected
    static final String[] VEC = {
        "mkString\tcary\t\t;",
        "mkString\tcary\ta\t{W;",
        "mkString\tcary\tab\t{eK;",
        "mkString\tcary\tabc\t{e4V;",
        "mkString\tcary\thello\t5qKY9SK;",
        "mkString\tcary\tHello World!\tbqKY9SKFqSqZ9maF;",
        "mkString\tcary\t测试\tFfyvnY;",
        "mkString\tcary\tid=1|\t6maMEFL;",
        "mkInt\tcary\t0\tYWnuYa;",
        "mkInt\tcary\t1\tYSnuYa;",
        "mkInt\tcary\t42\tYuluYa;",
        "mkInt\tcary\t-1\tZXovZ;",
        "mkInt\tcary\t2147483647\tZXoPZ;",
        "mkInt\tcary\t-2147483648\tYWnOYa;",
        "mkString\tuser1234567\thello\tpYXftAX;",
        "mkString\tuser1234567\tHello World!\tJYXftAXMYA1gtUnM;",
        "mkString\tuser1234567\t测试\tzN90VG;",
        "mkInt\tuser1234567\t0\tqoanqs;",
        "mkInt\tuser1234567\t42\tqAYnqs;",
        "mkInt\tuser1234567\t-1\trpbor;",
        "mkString\tabcd1234567\thello\t7pZtBRZ;",
        "mkString\tabcd1234567\tabc\t0dJq;",
        "mkInt\tabcd1234567\t42\tWvWZWb;",
        "mkInt\tabcd1234567\t-1\tXYZaX;",
    };

    public static void main(String[] args) {
        int fail = 0;
        for (String row : VEC) {
            String[] p = row.split("\t", -1);
            String op = p[0], key = p[1], input = p[2], expect = p[3];
            String actual;
            if (op.equals("mkString")) {
                actual = UtilCodec.mkString(input, key);
            } else {
                actual = UtilCodec.mkInt(Integer.parseInt(input), key);
            }
            boolean ok = expect.equals(actual);
            System.out.println((ok ? "ok   " : "FAIL ") + op + " key=" + key + " in=[" + input + "]"
                    + " expect=[" + expect + "] actual=[" + actual + "]");
            if (!ok) fail++;
        }
        System.out.println(fail == 0 ? "UTIL GOLDEN ALL PASS" : "UTIL GOLDEN FAIL " + fail);
        if (fail != 0) System.exit(1);
    }
}