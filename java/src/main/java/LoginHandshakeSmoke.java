import csa.net.login.LoginHandshake;
public class LoginHandshakeSmoke {
  public static void main(String[] a) {
    int fail = 0;
    fail += ok("mac", LoginHandshake.isValidMacToken("gmsv-abc123"));
    fail += ok("mac2_bad", !LoginHandshake.isValidMacToken("eth0"));
    fail += ok("empty_bad", LoginHandshake.isBadLoginString(""));
    fail += ok("semi_bad", LoginHandshake.isBadLoginString("a;b"));
    fail += ok("good", !LoginHandshake.isBadLoginString("player1"));
    fail += ok("key", "user1234567".equals(LoginHandshake.sessionKeyBuffer("user")));
    fail += ok("env", LoginHandshake.envelope(0x48, "XX").equals("&;95XX;#;"));
    System.out.println(fail == 0 ? "LOGIN SMOKE PASS" : "LOGIN SMOKE FAIL " + fail);
  }
  static int ok(String n, boolean c) { System.out.println((c?"ok ":"FAIL ")+n); return c?0:1; }
}