package csa.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Minimal MariaDB auth against CSAlogin (192.168.2.100 / db csa).
 * Config loaded from config/db.local.json (gitignored) or env:
 *   CSA_DB_HOST, CSA_DB_PORT, CSA_DB_USER, CSA_DB_PASS, CSA_DB_NAME
 *
 * Column note: Lock is reserved in MariaDB — always backtick it.
 */
public final class DbAuth implements AutoCloseable {

    public static final class Account {
        public int id;
        public String name;
        public int lock;
        public String lockInfo = "";
    }

    private final Connection conn;

    public DbAuth(String host, int port, String user, String pass, String db) throws Exception {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        this.conn = DriverManager.getConnection(url, user, pass);
    }

    /** @return null on success, error message key on failure. */
    public String authenticate(String cdkey, String password) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, Name, PassWord, `Lock`, LockInfo FROM CSAlogin WHERE Name=?")) {
            ps.setString(1, cdkey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "not_registered";
                }
                String pw = rs.getString("PassWord");
                int lock = rs.getInt("Lock");
                if (pw == null || !pw.equals(password)) {
                    return "bad_password";
                }
                if (lock != 0) {
                    return "locked_account";
                }
                return null; // ok
            }
        } catch (Exception e) {
            return "db_error:" + e.getMessage();
        }
    }

    @Override
    public void close() throws Exception {
        conn.close();
    }

    public static void main(String[] args) throws Exception {
        String host = env("CSA_DB_HOST", "192.168.2.100");
        int port = Integer.parseInt(env("CSA_DB_PORT", "3306"));
        String user = env("CSA_DB_USER", "csa");
        String pass = env("CSA_DB_PASS", "");
        String db = env("CSA_DB_NAME", "csa");
        try (DbAuth auth = new DbAuth(host, port, user, pass, db)) {
            String r1 = auth.authenticate("user1", "pass1");
            String r2 = auth.authenticate("user1", "wrong");
            String r3 = auth.authenticate("nobody", "x");
            System.out.println("user1/pass1 => " + (r1 == null ? "OK" : r1));
            System.out.println("user1/wrong => " + r2);
            System.out.println("nobody      => " + r3);
            if (r1 != null) {
                System.exit(1);
            }
            System.out.println("DB AUTH PASS");
        }
    }

    private static String env(String k, String d) {
        String v = System.getenv(k);
        return v == null || v.isEmpty() ? d : v;
    }
}
