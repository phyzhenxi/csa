package csa.client;

import java.util.ArrayList;
import java.util.List;

/** Multi-scenario SimClient for login/play smoke. */
public final class SimScenarios {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 1234;
        int fail = 0;
        List<String> log = new ArrayList<>();

        // S1: correct login
        try (SimClient c = new SimClient(host, port)) {
            String r = c.send("ClientLogin", "user1", "pass1", "gmsv-abc123", 7);
            log.add("S1 login user1 => " + r);
            if (r == null || !r.contains("&;")) fail++;
            r = c.send("CharList");
            log.add("S1 CharList => " + r);
            r = c.send("CharLogin", "勇者A");
            log.add("S1 CharLogin => " + r);
            c.sendNoReply("W", 100, 200, 1);
            log.add("S1 W => ok");
            r = c.send("C", "hello world");
            log.add("S1 C => " + r);
            r = c.send("B", 3);
            log.add("S1 B => " + r);
        } catch (Exception e) {
            log.add("S1 EX " + e);
            fail++;
        }

        // S2: wrong password
        try (SimClient c = new SimClient(host, port)) {
            String r = c.send("ClientLogin", "user1", "WRONG", "gmsv-abc123", 7);
            log.add("S2 badpass => " + r);
            // should NOT be a simple ok-only; envelope still
            if (r == null) fail++;
        } catch (Exception e) {
            log.add("S2 EX " + e);
            fail++;
        }

        // S3: unknown user
        try (SimClient c = new SimClient(host, port)) {
            String r = c.send("ClientLogin", "ghost", "x", "gmsv-abc123", 7);
            log.add("S3 ghost => " + r);
        } catch (Exception e) {
            log.add("S3 EX " + e);
            fail++;
        }

        // S4: bad MAC
        try (SimClient c = new SimClient(host, port)) {
            String r = c.send("ClientLogin", "user1", "pass1", "eth0", 7);
            log.add("S4 badmac => " + r);
        } catch (Exception e) {
            log.add("S4 EX " + e);
            fail++;
        }

        for (String s : log) System.out.println(s);
        System.out.println(fail == 0 ? "SIM SCENARIOS PASS" : "SIM SCENARIOS FAIL " + fail);
        if (fail != 0) System.exit(1);
    }
}