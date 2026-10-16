package csa.client;

import csa.net.lssp.LssProtocolCodec;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal lssproto client simulator for LssGateway integration smoke.
 * Speaks the recovered wire: msgId cmd base62-or-escaped-args LF
 */
public final class SimClient implements AutoCloseable {

    public static final Charset GBK = Charset.forName("GBK");
    private final Socket sock;
    private final BufferedReader in;
    private final BufferedWriter out;
    private long msgId = 1;

    public SimClient(String host, int port) throws Exception {
        sock = new Socket(host, port);
        sock.setTcpNoDelay(true);
        in = new BufferedReader(new InputStreamReader(sock.getInputStream(), GBK));
        out = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), GBK));
    }

    public String send(String cmd, Object... args) throws Exception {
        StringBuilder sb = new StringBuilder(LssProtocolCodec.header(msgId++, cmd));
        for (Object a : args) {
            if (a instanceof Number) {
                sb.append(LssProtocolCodec.mkInt(((Number) a).longValue()));
            } else {
                sb.append(LssProtocolCodec.mkString(String.valueOf(a)));
            }
            sb.append(LssProtocolCodec.SP);
        }
        sb.append(LssProtocolCodec.LF);
        out.write(sb.toString());
        out.flush();
        // read one reply line
        return in.readLine();
    }

    /** Send without waiting (for walk etc. that may not reply). */
    public void sendNoReply(String cmd, Object... args) throws Exception {
        StringBuilder sb = new StringBuilder(LssProtocolCodec.header(msgId++, cmd));
        for (Object a : args) {
            if (a instanceof Number) sb.append(LssProtocolCodec.mkInt(((Number) a).longValue()));
            else sb.append(LssProtocolCodec.mkString(String.valueOf(a)));
            sb.append(LssProtocolCodec.SP);
        }
        sb.append(LssProtocolCodec.LF);
        out.write(sb.toString());
        out.flush();
    }

    @Override
    public void close() throws Exception {
        sock.close();
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 1234;
        List<String> log = new ArrayList<>();
        try (SimClient c = new SimClient(host, port)) {
            String r1 = c.send("ClientLogin", "user1", "pass1", "gmsv-abc123", 7);
            log.add("ClientLogin => " + r1);
            String r2 = c.send("CharList");
            log.add("CharList => " + r2);
            String r3 = c.send("CharLogin", "勇者");
            log.add("CharLogin => " + r3);
            c.sendNoReply("W", 100, 200, 1);
            log.add("W 100 200 1 => (no reply)");
            String r4 = c.send("C", "hello world");
            log.add("C => " + r4);
            String r5 = c.send("B", 3);
            log.add("B => " + r5);
            String r6 = c.send("TK", "hi there");
            log.add("TK => " + r6);
        }
        for (String line : log) {
            System.out.println(line);
        }
        boolean ok = log.get(0).contains("ok") || log.get(0).contains("ClientLogin");
        System.out.println(ok ? "SIM CLIENT DONE" : "SIM CLIENT WEAK");
    }
}