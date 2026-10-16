package csa.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * CSAinfo character list access for CharList / CharLogin.
 * Table columns are reserved-word safe (backtick `time`).
 */
public final class DbCharacters implements AutoCloseable {

    public static final class CharRow {
        public String cdkey = "";
        public int saveIndex;
        public String name = "";
        public String famename = "";
        public int level;
        public int trans;
        public int alive;
        public int cameo;
        public int fame;
        public int titleLv;
        public int vip;
    }

    private final Connection conn;

    public DbCharacters(String host, int port, String user, String pass, String db) throws Exception {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        this.conn = DriverManagerHolder.connect(url, user, pass);
    }

    public DbCharacters(Connection shared) {
        this.conn = shared;
    }

    /** Characters owned by cdkey, ordered by saveindex. */
    public List<CharRow> listByCdkey(String cdkey) {
        List<CharRow> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT cdkey, saveindex, name, famename, level, trans, alive, cameo, fame, TITLE_LV, vip "
                        + "FROM CSAinfo WHERE cdkey=? ORDER BY saveindex")) {
            ps.setString(1, cdkey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CharRow r = new CharRow();
                    r.cdkey = rs.getString("cdkey");
                    r.saveIndex = rs.getInt("saveindex");
                    r.name = rs.getString("name");
                    r.famename = rs.getString("famename");
                    r.level = rs.getInt("level");
                    r.trans = rs.getInt("trans");
                    r.alive = rs.getInt("alive");
                    r.cameo = rs.getInt("cameo");
                    r.fame = rs.getInt("fame");
                    r.titleLv = rs.getInt("TITLE_LV");
                    r.vip = rs.getInt("vip");
                    out.add(r);
                }
            }
        } catch (Exception e) {
            // empty list on error
        }
        return out;
    }

    /** Compact summary token for CharList_send (stable, space-free). */
    public static String toSummary(CharRow r) {
        // name|level|trans|vip|fame|titleLv  — keep ASCII-safe separators
        return r.name + "|" + r.level + "|" + r.trans + "|" + r.vip + "|" + r.fame + "|" + r.titleLv;
    }

    /** Pack N characters as newline-free joined list (single string arg). */
    public static String packList(List<CharRow> rows) {
        if (rows.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sb.append(";");
            sb.append(toSummary(rows.get(i)));
        }
        return sb.toString();
    }

    @Override
    public void close() throws Exception {
        conn.close();
    }

    /** Tiny holder so we can share one Connection with DbAuth if desired. */
    static final class DriverManagerHolder {
        static Connection connect(String url, String user, String pass) throws Exception {
            return java.sql.DriverManager.getConnection(url, user, pass);
        }
    }

    public static void main(String[] args) throws Exception {
        String host = env("CSA_DB_HOST", "192.168.2.100");
        int port = Integer.parseInt(env("CSA_DB_PORT", "3306"));
        String user = env("CSA_DB_USER", "csa");
        String pass = env("CSA_DB_PASS", "");
        String db = env("CSA_DB_NAME", "csa");
        try (DbCharacters chars = new DbCharacters(host, port, user, pass, db)) {
            List<CharRow> rows = chars.listByCdkey("user1");
            System.out.println("user1 chars: " + rows.size());
            for (CharRow r : rows) {
                System.out.println("  " + toSummary(r));
            }
            System.out.println("packed=" + packList(rows));
            if (rows.isEmpty()) {
                System.exit(1);
            }
            System.out.println("DB CHARS PASS");
        }
    }

    private static String env(String k, String d) {
        String v = System.getenv(k);
        return v == null || v.isEmpty() ? d : v;
    }
}
