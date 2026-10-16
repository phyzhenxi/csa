package csa.map;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Self-test: load a real gmsv LS2MAP + mapset.txt and assert dimensions
 * plus a few walk flags derived from mapset col3.
 *
 * <p>Prints {@code SERVER MAP PASS} on success.</p>
 */
public final class ServerMapSelfTest {

    private static final String GMSV_MAP_DIR = "E:\\REA\\csa\\csa\\gmsv\\data\\map";

    private static final String[] REAL_MAPS = {
            GMSV_MAP_DIR + "\\1998",
            GMSV_MAP_DIR + "\\1997",
            GMSV_MAP_DIR + "\\extra\\104",
    };

    private static final String[] REAL_MAPSET = {
            GMSV_MAP_DIR + "\\mapset.txt",
            "E:\\REA\\csa\\csa\\gmsv\\data\\mapset.txt",
    };

    public static void main(String[] args) throws Exception {
        int fail = 0;

        Path mapsetPath = firstExisting(REAL_MAPSET);
        Path map1998 = Path.of(REAL_MAPS[0]);
        Path map1997 = Path.of(REAL_MAPS[1]);
        Path map104 = Path.of(REAL_MAPS[2]);

        if (mapsetPath == null || !Files.isRegularFile(map1998)) {
            System.out.println("note: gmsv map samples not found, running synthetic LS2MAP checks");
            fail += synthetic();
            System.out.println(fail == 0 ? "SERVER MAP PASS" : "SERVER MAP FAIL " + fail);
            if (fail != 0) System.exit(1);
            return;
        }

        // --- mapset table ---
        MapsetWalkTable table = MapsetWalkTable.load(mapsetPath);
        System.out.println("mapset " + mapsetPath + " entries=" + table.size());
        fail += ok("mapset_size", table.size() > 10000);
        fail += eq("mapset_0_blocked", MapsetWalkTable.BLOCKED, table.walkAttr(0));
        fail += eq("mapset_4_special", MapsetWalkTable.SPECIAL, table.walkAttr(4));
        fail += eq("mapset_195_open", MapsetWalkTable.OPEN, table.walkAttr(195));
        fail += eq("mapset_13607_special", MapsetWalkTable.SPECIAL, table.walkAttr(13607));
        fail += ok("mapset_unknown_neg", table.walkAttr(999_999) < 0);
        fail += ok("mapset_isWalkable_195", table.isWalkable(195));
        fail += ok("mapset_notWalkable_0", !table.isWalkable(0));

        // --- LS2MAP 1998: 180x180, open at (0,0), blocked at (112,8), special at (41,129) ---
        ServerMapLoader.ServerMap m1998 = ServerMapLoader.load(map1998);
        System.out.println("map1998 " + m1998);
        fail += eq("m1998_id", 1998, m1998.mapId);
        fail += eq("m1998_w", 180, m1998.width);
        fail += eq("m1998_h", 180, m1998.height);
        fail += eq("m1998_cells", 180 * 180, m1998.tile.length);
        fail += eq("m1998_obj_len", 180 * 180, m1998.obj.length);
        fail += eq("m1998_tile_0_0", 195, m1998.tileAt(0, 0));
        fail += ok("m1998_open_0_0", ServerMapLoader.isWalkable(m1998, table, 0, 0));
        fail += eq("m1998_attr_0_0", MapsetWalkTable.OPEN,
                ServerMapLoader.cellWalkAttr(m1998, table, 0, 0));
        fail += ok("m1998_blocked_112_8", !ServerMapLoader.isWalkable(m1998, table, 112, 8));
        fail += eq("m1998_attr_112_8", MapsetWalkTable.BLOCKED,
                ServerMapLoader.cellWalkAttr(m1998, table, 112, 8));
        fail += eq("m1998_attr_41_129", MapsetWalkTable.SPECIAL,
                ServerMapLoader.cellWalkAttr(m1998, table, 41, 129));
        fail += ok("m1998_special_not_open", !ServerMapLoader.isWalkable(m1998, table, 41, 129));
        fail += ok("m1998_oob", !ServerMapLoader.isWalkable(m1998, table, 180, 0));
        fail += ok("m1998_oob_neg", !ServerMapLoader.isWalkable(m1998, table, -1, 0));

        // --- LS2MAP 1997: 50x50, void (0,0) blocked, (37,26) open ---
        ServerMapLoader.ServerMap m1997 = ServerMapLoader.load(map1997);
        System.out.println("map1997 " + m1997);
        fail += eq("m1997_id", 1997, m1997.mapId);
        fail += eq("m1997_w", 50, m1997.width);
        fail += eq("m1997_h", 50, m1997.height);
        fail += ok("m1997_blocked_void_0_0", !ServerMapLoader.isWalkable(m1997, table, 0, 0));
        fail += ok("m1997_open_37_26", ServerMapLoader.isWalkable(m1997, table, 37, 26));
        fail += eq("m1997_tile_37_26", 2, m1997.tileAt(37, 26));

        // --- LS2MAP 104: special cell uses mapset attr 2 ---
        if (Files.isRegularFile(map104)) {
            ServerMapLoader.ServerMap m104 = ServerMapLoader.load(map104);
            System.out.println("map104 " + m104);
            fail += eq("m104_id", 104, m104.mapId);
            fail += eq("m104_w", 80, m104.width);
            fail += eq("m104_h", 80, m104.height);
            fail += eq("m104_attr_48_63", MapsetWalkTable.SPECIAL,
                    ServerMapLoader.cellWalkAttr(m104, table, 48, 63));
            fail += ok("m104_blocked_0_0", !ServerMapLoader.isWalkable(m104, table, 0, 0));
            fail += ok("m104_open_62_44", ServerMapLoader.isWalkable(m104, table, 62, 44));
        }

        // coverage: walk attrs partition the grid
        int open = 0, blocked = 0, special = 0;
        for (int y = 0; y < m1998.height; y++) {
            for (int x = 0; x < m1998.width; x++) {
                int a = ServerMapLoader.cellWalkAttr(m1998, table, x, y);
                if (a == MapsetWalkTable.OPEN) open++;
                else if (a == MapsetWalkTable.BLOCKED) blocked++;
                else special++;
            }
        }
        System.out.println("m1998 walk partition open=" + open + " blocked=" + blocked + " special=" + special);
        fail += ok("m1998_has_open", open > 0);
        fail += ok("m1998_has_blocked", blocked > 0);
        fail += ok("m1998_partition_sum", open + blocked + special == m1998.cellCount());

        System.out.println(fail == 0 ? "SERVER MAP PASS" : "SERVER MAP FAIL " + fail);
        if (fail != 0) System.exit(1);
    }

    /** Synthetic LS2MAP (2x2) + mapset when real samples are absent. */
    private static int synthetic() throws Exception {
        int fail = 0;
        byte[] raw = new byte[44 + 2 * 2 * 4];
        raw[0] = 'L'; raw[1] = 'S'; raw[2] = '2'; raw[3] = 'M'; raw[4] = 'A'; raw[5] = 'P';
        raw[6] = 0x00; raw[7] = 0x2A; // id 42
        // name "t|0"
        raw[8] = 't'; raw[9] = '|'; raw[10] = '0';
        raw[40] = 0; raw[41] = 2; // w=2
        raw[42] = 0; raw[43] = 2; // h=2
        // cells: (0,0) tile195 obj0; (1,0) tile0 obj0; (0,1) tile4 obj0; (1,1) tile195 obj0
        putCell(raw, 0, 195, 0);
        putCell(raw, 1, 0, 0);
        putCell(raw, 2, 4, 0);
        putCell(raw, 3, 195, 0);
        ServerMapLoader.ServerMap map = ServerMapLoader.parse("synth", raw);
        fail += eq("synth_id", 42, map.mapId);
        fail += eq("synth_w", 2, map.width);
        fail += eq("synth_h", 2, map.height);

        MapsetWalkTable table = MapsetWalkTable.parse(java.util.List.of(
                "0 1 1 1 0 -1",
                "4 1 1 2 0 -1",
                "195 1 1 0 0 -1"
        ));
        fail += ok("synth_open", ServerMapLoader.isWalkable(map, table, 0, 0));
        fail += ok("synth_blocked", !ServerMapLoader.isWalkable(map, table, 1, 0));
        fail += eq("synth_special", MapsetWalkTable.SPECIAL,
                ServerMapLoader.cellWalkAttr(map, table, 0, 1));
        fail += ok("synth_oob", !ServerMapLoader.isWalkable(map, table, 5, 5));
        return fail;
    }

    private static void putCell(byte[] raw, int index, int tile, int obj) {
        int off = 44 + index * 4;
        raw[off] = (byte) (tile >> 8);
        raw[off + 1] = (byte) tile;
        raw[off + 2] = (byte) (obj >> 8);
        raw[off + 3] = (byte) obj;
    }

    private static Path firstExisting(String[] paths) {
        for (String p : paths) {
            Path path = Path.of(p);
            if (Files.isRegularFile(path)) return path;
        }
        return null;
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

    private ServerMapSelfTest() {}
}
