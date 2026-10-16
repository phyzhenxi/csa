package csa.map;

import csa.gateway.LssGateway;
import csa.net.login.LoginHandshake;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Self-test: MapWorld + gateway login/walk wiring against real gmsv
 * mapset.txt + LS2MAP samples (synthetic fallback when absent).
 *
 * <p>Prints {@code MAPWORLD PASS} on success.</p>
 */
public final class MapWorldSelfTest {

    private static final String GMSV_MAP_DIR = "E:\\REA\\csa\\csa\\gmsv\\data\\map";

    public static void main(String[] args) throws Exception {
        int fail = 0;

        Path mapDir = Path.of(GMSV_MAP_DIR);
        Path mapsetPath = mapDir.resolve("mapset.txt");
        Path map1997 = mapDir.resolve("1997");
        Path map1998 = mapDir.resolve("1998");

        MapWorld world = new MapWorld();
        if (Files.isRegularFile(mapsetPath) && Files.isRegularFile(map1997)) {
            System.out.println("using real gmsv maps at " + mapDir);
            int loaded = world.loadFrom(mapDir);
            System.out.println("loadFrom maps=" + loaded + " mapset=" + world.mapset().size()
                    + " floors=" + world.floors().size());
            fail += ok("has_1997", world.hasMap(1997));
            fail += ok("has_mapset", world.mapset() != null && world.mapset().size() > 10000);

            // known tiles from ServerMapSelfTest
            fail += ok("tile_1997_void_blocked", !world.isWalkable(1997, 0, 0));
            fail += ok("tile_1997_open_37_26", world.isWalkable(1997, 37, 26));
            if (Files.isRegularFile(map1998) && world.hasMap(1998)) {
                fail += ok("tile_1998_open_0_0", world.isWalkable(1998, 0, 0));
                fail += ok("tile_1998_blocked_112_8", !world.isWalkable(1998, 112, 8));
            }
        } else {
            System.out.println("note: gmsv map samples not found, running synthetic MapWorld checks");
            fail += synthetic(world);
        }

        // --- spawn is walkable ---
        MapWorld.Spawn sp = world.findSpawn(world.hasMap(1997) ? 1997 : world.spawn().floor);
        fail += ok("findSpawn_nonnull", sp != null);
        if (sp != null) {
            fail += ok("findSpawn_walkable", world.isWalkable(sp.floor, sp.x, sp.y));
            System.out.println("findSpawn " + sp);
        }

        MapWorld.Spawn def = world.spawn();
        fail += ok("spawn_nonnull", def != null);
        if (def != null) {
            fail += ok("spawn_walkable", world.isWalkable(def.floor, def.x, def.y));
            int expectFloor = world.hasMap(MapWorld.DEFAULT_FLOOR_PREFERRED)
                    ? MapWorld.DEFAULT_FLOOR_PREFERRED
                    : def.floor;
            fail += eq("spawn_floor", expectFloor, def.floor);
            System.out.println("default spawn " + def);
        }

        // unknown floor is permissive (geometry-only)
        fail += ok("unknown_floor_open", world.isWalkable(999_999, 5, 5));
        fail += ok("findSpawn_unknown_null", world.findSpawn(999_999) == null);

        // --- gateway: CharLogin spawns, W rejects blocked tiles ---
        fail += gateway(world, def);

        System.out.println(fail == 0 ? "MAPWORLD PASS" : "MAPWORLD FAIL " + fail);
        if (fail != 0) System.exit(1);
    }

    /** Login/walk through LssGateway with MapWorld wired. */
    private static int gateway(MapWorld world, MapWorld.Spawn def) {
        int fail = 0;
        LssGateway gw = new LssGateway();
        gw.setMapWorld(world);
        LssGateway.Session s = new LssGateway.Session(1);

        List<String> rLogin = gw.dispatch(s,
                LssGateway.line(1, "ClientLogin", "user1", "pass1", "gmsv-abc123", 7).trim());
        fail += ok("gw_login", rLogin.size() == 1 && s.state == LoginHandshake.LoginState.WHILE_LOGIN);
        gw.dispatch(s, LssGateway.line(2, "CharList").trim());
        List<String> rChar = gw.dispatch(s, LssGateway.line(3, "CharLogin", "hero").trim());
        fail += ok("gw_charlogin", rChar.size() == 1 && s.state == LoginHandshake.LoginState.CHAR_LOGIN);

        fail += eq("gw_floor", def.floor, s.floor);
        fail += eq("gw_x", def.x, s.x);
        fail += eq("gw_y", def.y, s.y);
        fail += ok("gw_spawn_walkable", world.isWalkable(s.floor, s.x, s.y));

        // locate an open cell next to a blocked cell on the spawn floor
        int floor = s.floor;
        ServerMapLoader.ServerMap map = world.map(floor);
        if (map == null) {
            fail += ok("gw_map_present", false);
            return fail;
        }
        int ox = -1, oy = -1, bx = -1, by = -1;
        int[][] deltas = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        outer:
        for (int y = 0; y < map.height; y++) {
            for (int x = 0; x < map.width; x++) {
                if (!world.isWalkable(floor, x, y)) {
                    continue;
                }
                for (int[] d : deltas) {
                    int xx = x + d[0];
                    int yy = y + d[1];
                    if (xx < 0 || yy < 0 || xx >= map.width || yy >= map.height) {
                        continue;
                    }
                    if (!world.isWalkable(floor, xx, yy)) {
                        ox = x;
                        oy = y;
                        bx = xx;
                        by = yy;
                        break outer;
                    }
                }
            }
        }
        fail += ok("gw_found_open_next_to_blocked", ox >= 0);
        if (ox < 0) {
            return fail;
        }

        // teleport beside the blocked cell, attempt to step onto it
        s.x = ox;
        s.y = oy;
        int dir = Integer.signum(bx - ox) + 2 * Integer.signum(by - oy);
        List<String> rBlocked = gw.dispatch(s,
                LssGateway.line(4, "W", bx, by, dir).trim());
        fail += eq("gw_blocked_no_reply", 0, rBlocked.size());
        fail += eq("gw_blocked_x_stays", ox, s.x);
        fail += eq("gw_blocked_y_stays", oy, s.y);

        // legal step onto an open neighbour must apply
        int nx = -1, ny = -1;
        for (int[] d : deltas) {
            int xx = ox + d[0];
            int yy = oy + d[1];
            if (xx < 0 || yy < 0 || xx >= map.width || yy >= map.height) {
                continue;
            }
            if (world.isWalkable(floor, xx, yy)
                    && WalkService.validateMove(ox, oy, xx, yy, 0) == WalkService.OK) {
                nx = xx;
                ny = yy;
                break;
            }
        }
        fail += ok("gw_found_open_step", nx >= 0);
        if (nx >= 0) {
            List<String> rOpen = gw.dispatch(s,
                    LssGateway.line(5, "W", nx, ny, 1).trim());
            fail += eq("gw_open_no_reply", 0, rOpen.size());
            fail += eq("gw_open_x", nx, s.x);
            fail += eq("gw_open_y", ny, s.y);
        }

        // jump too far still rejected by step validation
        s.x = ox;
        s.y = oy;
        gw.dispatch(s, LssGateway.line(6, "W", ox + 10, oy, 0).trim());
        fail += eq("gw_jump_x_stays", ox, s.x);
        fail += eq("gw_jump_y_stays", oy, s.y);

        return fail;
    }

    /** Synthetic 4x4 LS2MAP + mapset when real samples are absent. */
    private static int synthetic(MapWorld world) {
        int fail = 0;
        int w = 4;
        int h = 4;
        byte[] raw = new byte[44 + w * h * 4];
        raw[0] = 'L'; raw[1] = 'S'; raw[2] = '2'; raw[3] = 'M'; raw[4] = 'A'; raw[5] = 'P';
        raw[6] = 0x07; raw[7] = (byte) 0xCD; // id 1997
        raw[8] = 't'; raw[9] = '|'; raw[10] = '0';
        raw[40] = (byte) (w >> 8); raw[41] = (byte) w;
        raw[42] = (byte) (h >> 8); raw[43] = (byte) h;
        // centre 2x2 open (195), ring blocked (0), one special (4)
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int tile;
                if (x >= 1 && x <= 2 && y >= 1 && y <= 2) {
                    tile = 195;
                } else if (x == 0 && y == 0) {
                    tile = 4;
                } else {
                    tile = 0;
                }
                putCell(raw, y * w + x, tile, 0);
            }
        }
        try {
            ServerMapLoader.ServerMap map = ServerMapLoader.parse("synth", raw);
            world.registerMapset(MapsetWalkTable.parse(List.of(
                    "0 1 1 1 0 -1",
                    "4 1 1 2 0 -1",
                    "195 1 1 0 0 -1"
            )));
            world.registerMap(map.mapId, map);
            fail += eq("synth_id", 1997, map.mapId);
            fail += ok("synth_blocked_corner", !world.isWalkable(1997, 3, 0));
            fail += ok("synth_open_centre", world.isWalkable(1997, 1, 1));
            fail += ok("synth_special_not_open", !world.isWalkable(1997, 0, 0));
        } catch (Exception e) {
            System.out.println("synthetic setup failed: " + e);
            fail++;
        }
        return fail;
    }

    private static void putCell(byte[] raw, int index, int tile, int obj) {
        int off = 44 + index * 4;
        raw[off] = (byte) (tile >> 8);
        raw[off + 1] = (byte) tile;
        raw[off + 2] = (byte) (obj >> 8);
        raw[off + 3] = (byte) obj;
    }

    private static int ok(String label, boolean cond) {
        System.out.println((cond ? "  ok  " : "  FAIL") + " " + label);
        return cond ? 0 : 1;
    }

    private static int eq(String label, int expect, int actual) {
        boolean cond = expect == actual;
        System.out.println((cond ? "  ok  " : "  FAIL") + " " + label + " expect=" + expect + " actual=" + actual);
        return cond ? 0 : 1;
    }

    private MapWorldSelfTest() {}
}
