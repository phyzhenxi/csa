package csa.gateway;

import csa.net.lssp.LssProtocolCodec;

import java.util.List;

public final class LssGatewaySelfTest {
    public static void main(String[] args) {
        int fail = 0;

        LssGateway gw = new LssGateway();
        LssGateway.Session s = new LssGateway.Session(1);

        // 1. login ok with valid mac token
        String login = LssGateway.line(1, "ClientLogin", "user1", "pass1", "gmsv-abc123", 7);
        List<String> r = gw.dispatch(s, login.trim());
        fail += eq("login_replies", 1, r.size());
        fail += ok("login_env", r.get(0).startsWith("&;") && r.get(0).contains(";#;"));
        fail += ok("state_while", s.state == csa.net.login.LoginHandshake.LoginState.WHILE_LOGIN);

        // 2. bad mac
        LssGateway.Session s2 = new LssGateway.Session(2);
        List<String> r2 = gw.dispatch(s2, LssGateway.line(1, "ClientLogin", "u", "p", "eth0", 1).trim());
        fail += ok("bad_mac", r2.get(0).startsWith("&;") && r2.get(0).contains(";#;"));

        // 3. empty password
        LssGateway.Session s3 = new LssGateway.Session(3);
        List<String> r3 = gw.dispatch(s3, LssGateway.line(1, "ClientLogin", "u", "", "gmsv-x", 1).trim());
        fail += ok("empty", r3.get(0).startsWith("&;") && s3.state == csa.net.login.LoginHandshake.LoginState.NOT_LOGIN);

        // 4. char list after login
        List<String> rl = gw.dispatch(s, LssGateway.line(2, "CharList").trim());
        fail += eq("charlist", 1, rl.size());

        // 5. char login
        List<String> rc = gw.dispatch(s, LssGateway.line(3, "CharLogin", "勇者").trim());
        fail += ok("charlogin_env", rc.get(0).startsWith("&;") && rc.get(0).contains(";#;"));
        fail += ok("state_char", s.state == csa.net.login.LoginHandshake.LoginState.CHAR_LOGIN);

        // 6. walk after in-game
        List<String> rw = gw.dispatch(s, LssGateway.line(4, "W", 100, 200, 1).trim());
        fail += eq("walk_no_reply", 0, rw.size());
        fail += eq("walk_x", 100, s.x);
        fail += eq("walk_y", 200, s.y);

        // 7. chat
        List<String> rt = gw.dispatch(s, LssGateway.line(5, "C", "hello world").trim());
        fail += eq("chat_replies", 1, rt.size());
        fail += ok("chat_echo", rt.get(0).contains("hello") || rt.get(0).contains("hello\\S"));

        // 8. battle
        List<String> rb = gw.dispatch(s, LssGateway.line(6, "B", 3).trim());
        fail += eq("battle_replies", 1, rb.size());
        fail += ok("battle_echo", rb.get(0).startsWith("6 B ") || rb.get(0).contains(" B "));

        // 9. command before login rejected
        LssGateway.Session s4 = new LssGateway.Session(4);
        List<String> r4 = gw.dispatch(s4, LssGateway.line(9, "W", 1, 2, 3).trim());
        fail += ok("prelogin_reject", r4.size() == 1 && r4.get(0).contains("MSG"));

        System.out.println(fail == 0 ? "GATEWAY ALL PASS" : "GATEWAY FAIL " + fail);
        if (fail != 0) System.exit(1);
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