package csa.gateway;

import csa.net.login.LoginHandshake;
import csa.net.login.LoginResponses;
import csa.net.lssp.LssProtocolCodec;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LSS TCP gateway skeleton (Netty-style pipeline stages, JDK-only).
 *
 * Pipeline per connection:
 *   1. LineDecoder   — read LF frames
 *   2. FrameDecoder  — LssProtocolCodec.decodeLine
 *   3. LoginHandler  — ClientLogin / CharList / CharLogin state machine
 *   4. CommandRouter — W / C / B / ...
 *   5. FrameEncoder  — LssProtocolCodec.encode + optional util_SendMesg envelope
 *
 * Replace stages 1–2/5 with Netty {@code DelimiterBasedFrameDecoder} +
 * {@code StringDecoder/Encoder} for production; domain handlers stay the same.
 */
public final class LssGateway {

    public static final Charset GBK = Charset.forName("GBK");
    private final AtomicLong ids = new AtomicLong(1);
    /** Optional DB authenticator (CSAlogin); null = accept any valid-format login. */
    private csa.db.DbAuth dbAuth;
    private csa.db.DbCharacters dbChars;

    /** Connection session state. */
    public static final class Session {
        public final int fd;
        public String cdkey = "";
        public String password = "";
        public String macToken = "";
        public int servId;
        public LoginHandshake.LoginState state = LoginHandshake.LoginState.NOT_LOGIN;
        public String charName = "";
        public int x, y, dir;

        public Session(int fd) { this.fd = fd; }
    }

    /** Domain handler SPI. */
    public interface Handler {
        /** @return response command args, or empty for no reply */
        List<Object> handle(Session s, LssProtocolCodec.Frame frame);
    }

    /** Handlers registered for post-login commands. */
    private final java.util.Map<String, Handler> handlers = new java.util.LinkedHashMap<>();

    public LssGateway() {
        handlers.put(csa.net.cmd.Commands.W, (s, f) -> {
            if (f.tokens.size() >= 3) {
                s.x = (int) LssProtocolCodec.demInt(f.tokens.get(0));
                s.y = (int) LssProtocolCodec.demInt(f.tokens.get(1));
                s.dir = (int) LssProtocolCodec.demInt(f.tokens.get(2));
            }
            return List.of(); // broadcast via game service in real impl
        });
        handlers.put(csa.net.cmd.Commands.C, (s, f) -> List.of("P", f.tokens.isEmpty() ? "" : LssProtocolCodec.demString(f.tokens.get(0))));
        handlers.put(csa.net.cmd.Commands.B, (s, f) -> {
            long action = f.tokens.isEmpty() ? 0 : LssProtocolCodec.demInt(f.tokens.get(0));
            return List.of("B", action);
        });
        handlers.put(csa.net.cmd.Commands.TK, (s, f) -> {
            String msg = f.tokens.isEmpty() ? "" : LssProtocolCodec.demString(f.tokens.get(0));
            if (LoginHandshake.isBadLoginString(msg)) {
                return List.of("MSG", "异常字符的用户名");
            }
            return List.of("P", msg);
        });
    }

    /**
     * Process one decoded client frame and return zero or more reply lines
     * (without trailing LF).
     */
    public List<String> dispatch(Session s, String rawLine) {
        LssProtocolCodec.Frame f;
        try {
            f = LssProtocolCodec.decodeLine(rawLine);
        } catch (RuntimeException e) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        switch (f.command) {
            case csa.net.cmd.Commands.CLIENT_LOGIN:
                out.add(login(s, f));
                break;
            case csa.net.cmd.Commands.CHAR_LIST:
                out.add(charList(s, f));
                break;
            case csa.net.cmd.Commands.CHAR_LOGIN:
                out.add(charLogin(s, f));
                break;
            default:
                if (s.state != LoginHandshake.LoginState.CHAR_LOGIN) {
                    out.add(encode(f.msgId, "MSG", "请不要进行非法登陆游戏！"));
                    break;
                }
                Handler h = handlers.get(f.command);
                if (h != null) {
                    List<Object> args = h.handle(s, f);
                    if (!args.isEmpty()) {
                        out.add(encode(f.msgId, String.valueOf(args.get(0)),
                                args.subList(1, args.size()).toArray()));
                    }
                }
                break;
        }
        return out;
    }

    String login(Session s, LssProtocolCodec.Frame f) {
        if (s.state != LoginHandshake.LoginState.NOT_LOGIN) {
            return LoginResponses.clientLogin("the Client had Logined", s.cdkey);
        }
        if (f.tokens.size() < 3) {
            return LoginResponses.clientLogin(LoginHandshake.ERR_EMPTY, "");
        }
        String cdkey = LssProtocolCodec.demString(f.tokens.get(0));
        String pass = LssProtocolCodec.demString(f.tokens.get(1));
        String mac = LssProtocolCodec.demString(f.tokens.get(2));
        int servId = f.tokens.size() > 3 ? (int) LssProtocolCodec.demInt(f.tokens.get(3)) : 1;

        if (cdkey.isEmpty() || pass.isEmpty()) {
            return LoginResponses.clientLogin(LoginHandshake.ERR_EMPTY, cdkey);
        }
        if (LoginHandshake.isBadLoginString(cdkey) || LoginHandshake.isBadLoginString(pass)) {
            return LoginResponses.clientLogin(LoginHandshake.ERR_CHARS, cdkey);
        }
        if (!LoginHandshake.isValidMacToken(mac)) {
            return LoginResponses.clientLogin("由于你的版本过旧，请更新后再进入游戏！", cdkey);
        }
        if (dbAuth != null) {
            String err = dbAuth.authenticate(cdkey, pass);
            if (err != null) {
                String msg;
                switch (err) {
                    case "not_registered":
                        msg = LoginHandshake.ERR_NOT_REGISTERED;
                        break;
                    case "bad_password":
                        msg = LoginHandshake.ERR_BAD_PASSWORD;
                        break;
                    case "locked_account":
                        msg = LoginHandshake.ERR_LOCKED_ACCOUNT;
                        break;
                    default:
                        msg = err.startsWith("locked_ip") ? LoginHandshake.ERR_LOCKED_IP : err;
                        break;
                }
                return LoginResponses.clientLogin(msg, cdkey);
            }
        }
        s.cdkey = cdkey;
        s.password = pass;
        s.macToken = mac;
        s.servId = servId;
        s.state = LoginHandshake.LoginState.WHILE_LOGIN;
        return LoginResponses.clientLogin(LoginHandshake.OK, s.cdkey);
    }

    String charList(Session s, LssProtocolCodec.Frame f) {
        if (s.state == LoginHandshake.LoginState.NOT_LOGIN) {
            return LoginResponses.charList("failed", "请不要进行非法登陆游戏！", s.cdkey);
        }
        String packed = "";
        if (dbChars != null) {
            java.util.List<csa.db.DbCharacters.CharRow> rows = dbChars.listByCdkey(s.cdkey);
            packed = csa.db.DbCharacters.packList(rows);
        }
        return LoginResponses.charList(String.valueOf(packed.isEmpty() ? 0 : 1), packed, s.cdkey);
    }

    String charLogin(Session s, LssProtocolCodec.Frame f) {
        if (s.state != LoginHandshake.LoginState.WHILE_LOGIN) {
            return LoginResponses.charLogin("Already Logged in", "", s.cdkey);
        }
        if (f.tokens.isEmpty()) {
            return LoginResponses.charLogin("Can't access char have no name", "", s.cdkey);
        }
        String name = LssProtocolCodec.demString(f.tokens.get(0));
        if (name.isEmpty() || LoginHandshake.isBadLoginString(name)) {
            return LoginResponses.charLogin("failed", "", s.cdkey);
        }
        s.charName = name;
        s.state = LoginHandshake.LoginState.CHAR_LOGIN;
        return LoginResponses.charLogin(LoginHandshake.OK, name, s.cdkey);
    }

    /** Attach CSAlogin authenticator (optional). */
    public void setDbAuth(csa.db.DbAuth auth) {
        this.dbAuth = auth;
    }

    /** Attach CSAinfo character repository (optional). */
    public void setDbCharacters(csa.db.DbCharacters chars) {
        this.dbChars = chars;
    }

    String encode(long id, String cmd, Object... args) {
        StringBuilder sb = new StringBuilder(LssProtocolCodec.header(id, cmd));
        for (Object a : args) {
            if (a instanceof Number) {
                sb.append(LssProtocolCodec.mkInt(((Number) a).longValue()));
            } else {
                sb.append(LssProtocolCodec.mkString(String.valueOf(a)));
            }
            sb.append(LssProtocolCodec.SP);
        }
        return sb.toString(); // caller appends LF
    }

    /** Blocking socket accept loop for smoke / local integration. */
    public void serve(java.net.ServerSocket ss) throws IOException {
        while (!ss.isClosed()) {
            Socket sock = ss.accept();
            new Thread(() -> {
                try {
                    sock.setTcpNoDelay(true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), GBK));
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), GBK));
                    Session s = new Session((int) ids.getAndIncrement());
                    String line;
                    while ((line = in.readLine()) != null) {
                        for (String reply : dispatch(s, line)) {
                            out.write(reply);
                            out.write('\n');
                        }
                        out.flush();
                    }
                } catch (IOException ignored) {
                } finally {
                    try { sock.close(); } catch (IOException ignored) {}
                }
            }, "lss-fd-" + ids.get()).start();
        }
    }

    /** Encode helper for tests. */
    public static String line(long id, String cmd, Object... args) {
        StringBuilder sb = new StringBuilder(LssProtocolCodec.header(id, cmd));
        for (Object a : args) {
            if (a instanceof Number) sb.append(LssProtocolCodec.mkInt(((Number) a).longValue()));
            else sb.append(LssProtocolCodec.mkString(String.valueOf(a)));
            sb.append(LssProtocolCodec.SP);
        }
        sb.append(LssProtocolCodec.LF);
        return sb.toString();
    }
}
