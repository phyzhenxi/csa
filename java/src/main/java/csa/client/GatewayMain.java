package csa.client;

import csa.db.DbAuth;
import csa.gateway.LssGateway;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Boot LssGateway with optional MariaDB auth.
 * Env: CSA_DB_HOST / CSA_DB_PORT / CSA_DB_USER / CSA_DB_PASS / CSA_DB_NAME
 *      CSA_DB_DISABLE=1  to skip DB
 */
public final class GatewayMain {

    /** gmsv LS2MAP + mapset.txt samples (mapset walk table + floor grids). */
    private static final String DEFAULT_MAP_DIR = "E:\\REA\\csa\\csa\\gmsv\\data\\map";

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 1234;
        LssGateway gw = new LssGateway();
        wireMapWorld(gw);
        if (!"1".equals(System.getenv("CSA_DB_DISABLE"))) {
            String host = env("CSA_DB_HOST", "192.168.2.100");
            int dbPort = Integer.parseInt(env("CSA_DB_PORT", "3306"));
            String user = env("CSA_DB_USER", "csa");
            String pass = env("CSA_DB_PASS", "");
            String db = env("CSA_DB_NAME", "csa");
            try {
                DbAuth auth = new DbAuth(host, dbPort, user, pass, db);
                gw.setDbAuth(auth);
                gw.setDbCharacters(new csa.db.DbCharacters(host, dbPort, user, pass, db));
                gw.setSaveStore(new csa.db.CharSaveStore());
                gw.setBattleService(new csa.battle.BattleService(42L));
                csa.game.SkillItemService skills = new csa.game.SkillItemService(42L);
                skills.seedItem(1001, 5);
                skills.seedSkill(7, 3);
                gw.setSkillItemService(skills);
                gw.setTradeFamilyWindowService(new csa.game.TradeFamilyWindowService());
                gw.setSocialMiscService(new csa.game.SocialMiscService());
                gw.setActionSystemService(new csa.game.ActionSystemService());
                gw.setEventSocialService(new csa.game.EventSocialService());
                gw.setDbAuth(auth);
                System.out.println("DbAuth enabled: " + user + "@" + host + "/" + db);
            } catch (Exception e) {
                System.out.println("DbAuth DISABLED (connect fail): " + e.getMessage());
            }
        } else {
            System.out.println("DbAuth disabled by CSA_DB_DISABLE=1");
        }
        try (ServerSocket ss = new ServerSocket()) {
            ss.bind(new InetSocketAddress("127.0.0.1", port));
            System.out.println("LssGateway listening on 127.0.0.1:" + port);
            gw.serve(ss);
        }
    }

    /**
     * Load mapset.txt + numeric-named LS2MAP files from the gmsv map dir
     * (if present) and attach the world to the gateway for spawn / W checks.
     */
    static void wireMapWorld(LssGateway gw) {
        csa.map.MapWorld world = new csa.map.MapWorld();
        Path dir = Path.of(DEFAULT_MAP_DIR);
        try {
            if (Files.isDirectory(dir)) {
                int loaded = world.loadFrom(dir);
                System.out.println("MapWorld: loaded " + loaded + " LS2MAP from " + dir
                        + " mapset=" + (world.mapset() == null ? 0 : world.mapset().size())
                        + " spawn=" + world.spawn());
            } else {
                System.out.println("MapWorld: map dir not present " + dir + " (default spawn only)");
            }
        } catch (Exception e) {
            System.out.println("MapWorld load failed: " + e.getMessage());
        }
        gw.setMapWorld(world);
    }

    private static String env(String k, String d) {
        String v = System.getenv(k);
        return v == null || v.isEmpty() ? d : v;
    }
}