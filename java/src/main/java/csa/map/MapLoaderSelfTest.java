package csa.map;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Self-test: load a real client map when present, else a synthetic 8x8 grid.
 * Prints {@code MAP LOADER PASS} on success.
 * Walk-flag section prints {@code WALKFLAG PASS} when 1.dat cell checks pass.
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

        // 1) real floor map + walk-flag cell pattern (1.dat)
        MapLoader.MapData floor = loadFirst(REAL_FLOOR);
        if (floor != null) {
            System.out.println("floor map w=" + floor.width + " h=" + floor.height);
            fail += ok("floor_w", floor.width > 0);
            fail += ok("floor_h", floor.height > 0);
            fail += ok("floor_tiles_len", floor.tiles.length == floor.width * floor.height);
            fail += ok("floor_oob_blocked", !MapLoader.isWalkable(floor, floor.width, 0));
            // walk flag: tiles==0 means walkable
            fail += ok("floor_isWalkable_fn", MapLoader.isWalkable(floor, 0, 0) == (floor.tileAt(0, 0) == 0));
        } else {
            System.out.println("note: no real floor map, using synthetic 8x8");
            fail += synthetic();
        }

        // 2) WALKFLAG: assert specific 1.dat cells (word3==0 open, else blocked)
        fail += walkFlag1Dat();

        // 3) real battle map (optional)
        MapLoader.MapData battle = loadFirst(REAL_BATTLE);
        if (battle != null) {
            System.out.println("battle map w=" + battle.width + " h=" + battle.height
                    + " tiles=" + battle.tiles.length);
            fail += ok("battle_w", battle.width > 0);
            fail += ok("battle_tiles", battle.tiles.length > 0);
            fail += ok("battle_oob_blocked", !MapLoader.isWalkable(battle, -1, 0));
        }

        // 4) synthetic 8x8 always runs (deterministic walkability)
        fail += synthetic();

        // 5) WalkService.registerMap integration
        // tiles: 0 = walkable, non-zero = blocked
        byte[] t = new byte[8 * 8];
        t[0 * 8 + 0] = 1; // (0,0) blocked
        // (3,3) stays 0 → walkable
        MapLoader.MapData m = new MapLoader.MapData(8, 8, t);
        WalkService.registerMap(99, m);
        fail += ok("ws_unregistered_open", WalkService.isWalkable(12345, 0, 0));
        fail += ok("ws_registered_blocked", !WalkService.isWalkable(99, 0, 0));
        fail += ok("ws_registered_open", WalkService.isWalkable(99, 3, 3));
        fail += ok("ws_registered_oob", !WalkService.isWalkable(99, 8, 0));

        System.out.println(fail == 0 ? "MAP LOADER PASS" : "MAP LOADER FAIL " + fail);
        if (fail != 0) System.exit(1);
    }

    /**
     * Walk-flag self-test against real {@code map/1.dat} (25x25).
     * Derived rule: 3rd tile word == 0 → walkable; else blocked (walls/objects/void).
     * Void cells (0xC000 on all words) are blocked. Graphic 0 with flag 0 is open floor.
     */
    private static int walkFlag1Dat() throws Exception {
        Path p = Path.of("E:\\REA\\csa\\\u561f\u561f\u77f3\u5668\u5ba2\u6237\u7aef\\map\\1.dat");
        if (!Files.exists(p)) {
            System.out.println("note: 1.dat missing, skip WALKFLAG cell asserts");
            return 0;
        }
        MapLoader.MapData m = MapLoader.load(p);
        int fail = 0;
        // (x,y, expectedWalkable) — derived from word3 on 1.dat
        // open floors (word3==0): terrain, room interiors (graphic 0), doors
        fail += ok("wf_open_0_0", MapLoader.isWalkable(m, 0, 0));           // w3=0
        fail += ok("wf_open_8_0", MapLoader.isWalkable(m, 8, 0));           // w3=0
        fail += ok("wf_open_5_8", MapLoader.isWalkable(m, 5, 8));           // w3=0, graphic 4000
        fail += ok("wf_open_10_9", MapLoader.isWalkable(m, 10, 9));         // w3=0
        fail += ok("wf_open_2_10", MapLoader.isWalkable(m, 2, 10));         // w3=0, graphic 0 (room floor)
        fail += ok("wf_open_3_12", MapLoader.isWalkable(m, 3, 12));         // w3=0, graphic 0
        fail += ok("wf_open_4_12", MapLoader.isWalkable(m, 4, 12));         // w3=0, graphic 0
        fail += ok("wf_open_13_16", MapLoader.isWalkable(m, 13, 16));       // w3=0 (door)
        fail += ok("wf_open_24_0", MapLoader.isWalkable(m, 24, 0));         // w3=0
        // blocked: objects, walls, void
        fail += ok("wf_block_9_0", !MapLoader.isWalkable(m, 9, 0));         // w3=4007 object
        fail += ok("wf_block_0_1", !MapLoader.isWalkable(m, 0, 1));         // w3=4000 object
        fail += ok("wf_block_0_17", !MapLoader.isWalkable(m, 0, 17));       // w3=0xC000 void
        fail += ok("wf_block_0_24", !MapLoader.isWalkable(m, 0, 24));       // w3=0xC000 void
        System.out.println(fail == 0 ? "WALKFLAG PASS" : "WALKFLAG FAIL " + fail);
        return fail;
    }

    private static int synthetic() {
        int fail = 0;
        // 8x8 floor-style synthetic: header + 6-byte tiles
        byte[] raw = new byte[8 + 8 * 8 * 6];
        raw[0] = 8; // w
        raw[4] = 8; // h
        // tile (0,0): word3 stays 0 → walkable
        // tile (1,0): word3 = 1 → blocked (set byte at off 8+6+4)
        raw[8 + 6 + 4] = 1;
        try {
            MapLoader.MapData m = MapLoader.parse("synthetic", raw);
            fail += eq("syn_w", 8, m.width);
            fail += eq("syn_h", 8, m.height);
            fail += ok("syn_open", MapLoader.isWalkable(m, 0, 0));          // flag 0
            fail += ok("syn_blocked", !MapLoader.isWalkable(m, 1, 0));      // flag 1
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
