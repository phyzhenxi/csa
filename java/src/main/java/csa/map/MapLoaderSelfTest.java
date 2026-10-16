package csa.map;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Self-test: load a real client map when present, else a synthetic 8x8 grid.
 * Prints {@code MAP LOADER PASS} on success.
 */
public final class MapLoaderSelfTest {

    private static final String[] REAL_FLOOR = {
            "E:\\REA\\csa\\\u561f\u561f\u77f3\u5668\u5ba2\u6237\u7aef\\map\\1.dat",
            "E:\\REA\\csa\\\u561f\u561f\u77f3\u5668\u5ba2\u6237\u7aef\\map\\10001.dat",
    };
    private static final String[] REAL_BATTLE = {
            "E:\\REA\\csa\\\u561f\u561f\u77f3\u5668\u5ba2\u6237\u7aef\\data\\battleMap\\battle00.map",
    };

    public static void main(String[] args) throws Exception {
        int fail = 0;

        // 1) real floor map
        MapLoader.MapData floor = loadFirst(REAL_FLOOR);
        if (floor != null) {
            System.out.println("floor map w=" + floor.width + " h=" + floor.height);
            fail += ok("floor_w", floor.width > 0);
            fail += ok("floor_h", floor.height > 0);
            fail += ok("floor_tiles_len", floor.tiles.length == floor.width * floor.height);
            // (0,0) of 1.dat is empty tile 0 → blocked under best-effort rule
            fail += ok("floor_isWalkable_fn", MapLoader.isWalkable(floor, 0, 0) == (floor.tileAt(0, 0) != 0));
            fail += ok("floor_oob_blocked", !MapLoader.isWalkable(floor, floor.width, 0));
        } else {
            System.out.println("note: no real floor map, using synthetic 8x8");
            fail += synthetic();
        }

        // 2) real battle map (optional)
        MapLoader.MapData battle = loadFirst(REAL_BATTLE);
        if (battle != null) {
            System.out.println("battle map w=" + battle.width + " h=" + battle.height
                    + " tiles=" + battle.tiles.length);
            fail += ok("battle_w", battle.width > 0);
            fail += ok("battle_tiles", battle.tiles.length > 0);
            fail += ok("battle_oob_blocked", !MapLoader.isWalkable(battle, -1, 0));
        }

        // 3) synthetic 8x8 always runs (deterministic walkability)
        fail += synthetic();

        // 4) WalkService.registerMap integration
        byte[] t = new byte[8 * 8];
        t[3 * 8 + 3] = 1; // (3,3) open
        // leave (0,0) as 0 → blocked
        MapLoader.MapData m = new MapLoader.MapData(8, 8, t);
        WalkService.registerMap(99, m);
        fail += ok("ws_unregistered_open", WalkService.isWalkable(12345, 0, 0));
        fail += ok("ws_registered_blocked", !WalkService.isWalkable(99, 0, 0));
        fail += ok("ws_registered_open", WalkService.isWalkable(99, 3, 3));
        fail += ok("ws_registered_oob", !WalkService.isWalkable(99, 8, 0));

        System.out.println(fail == 0 ? "MAP LOADER PASS" : "MAP LOADER FAIL " + fail);
        if (fail != 0) System.exit(1);
    }

    private static int synthetic() {
        int fail = 0;
        // 8x8 floor-style synthetic: header + 6-byte tiles
        byte[] raw = new byte[8 + 8 * 8 * 6];
        raw[0] = 8; // w
        raw[4] = 8; // h
        // tile (0,0) stays 0 → blocked; tile (1,0) = 5 → open
        raw[8 + 6] = 5;
        try {
            MapLoader.MapData m = MapLoader.parse("synthetic", raw);
            fail += eq("syn_w", 8, m.width);
            fail += eq("syn_h", 8, m.height);
            fail += ok("syn_blocked", !MapLoader.isWalkable(m, 0, 0));
            fail += ok("syn_open", MapLoader.isWalkable(m, 1, 0));
            fail += ok("syn_oob", !MapLoader.isWalkable(m, -1, 0));
        } catch (Exception e) {
            System.out.println("FAIL synthetic parse: " + e);
            fail++;
        }
        return fail;
    }

    private static MapLoader.MapData loadFirst(String[] paths) {
        for (String p : paths) {
            try {
                if (Files.exists(Path.of(p))) {
                    return MapLoader.load(p);
                }
            } catch (Exception e) {
                System.out.println("skip " + p + ": " + e);
            }
        }
        return null;
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
