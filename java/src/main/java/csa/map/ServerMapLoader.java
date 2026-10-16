package csa.map;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loader for gmsv server map binaries ({@code data/map/**}, magic {@code LS2MAP}).
 *
 * <p>On-disk layout (big-endian throughout; verified on 1373/1391 samples —
 * the rest carry trailing bytes after the grid):</p>
 * <pre>
 *   0   6  magic "LS2MAP"
 *   6   2  u16be map id (matches numeric filename)
 *   8  32  GBK name, often suffixed with "|0", NUL-padded
 *  40   2  u16be width
 *  42   2  u16be height
 *  44   w*h * 4  cells: u16be tile graphic, u16be object graphic
 * </pre>
 *
 * <p>Walk attributes are <b>not</b> stored in this file. They come from
 * {@code data/map/mapset.txt} column 3 keyed by tile/object graphic id —
 * see {@link MapsetWalkTable}.</p>
 */
public final class ServerMapLoader {

    /** Fixed LS2MAP header size in bytes. */
    public static final int HEADER_SIZE = 44;

    /** Decoded server map grid. Cell index is {@code y * width + x}. */
    public static final class ServerMap {
        public final int mapId;
        public final String name;
        public final int width;
        public final int height;
        /** Floor graphic id per cell ({@code y*width+x}). */
        public final int[] tile;
        /** Object graphic id per cell ({@code y*width+x}); 0 = empty. */
        public final int[] obj;

        public ServerMap(int mapId, String name, int width, int height, int[] tile, int[] obj) {
            this.mapId = mapId;
            this.name = name;
            this.width = width;
            this.height = height;
            this.tile = tile;
            this.obj = obj;
        }

        public int cellCount() {
            return width * height;
        }

        /** Floor graphic at (x,y); OOB returns -1. */
        public int tileAt(int x, int y) {
            if (x < 0 || y < 0 || x >= width || y >= height) return -1;
            return tile[y * width + x];
        }

        /** Object graphic at (x,y); OOB returns -1. */
        public int objAt(int x, int y) {
            if (x < 0 || y < 0 || x >= width || y >= height) return -1;
            return obj[y * width + x];
        }

        @Override
        public String toString() {
            return "ServerMap{id=" + mapId + ", name=" + name + ", " + width + "x" + height + "}";
        }
    }

    /**
     * Load an LS2MAP file.
     *
     * @throws IOException on I/O error or unrecognized/truncated layout
     */
    public static ServerMap load(Path path) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        return parse(path.getFileName().toString(), raw);
    }

    public static ServerMap load(String path) throws IOException {
        return load(Path.of(path));
    }

    /** Parse already-read bytes; {@code name} is only used for error messages. */
    public static ServerMap parse(String name, byte[] raw) throws IOException {
        if (raw == null || raw.length < HEADER_SIZE) {
            throw new IOException("LS2MAP too small: " + name + " (" + (raw == null ? 0 : raw.length) + " bytes)");
        }
        if (raw[0] != 'L' || raw[1] != 'S' || raw[2] != '2' || raw[3] != 'M'
                || raw[4] != 'A' || raw[5] != 'P') {
            throw new IOException("not LS2MAP: " + name);
        }
        int mapId = u16be(raw, 6);
        String mapName = decodeName(raw, 8, 32);
        int w = u16be(raw, 40);
        int h = u16be(raw, 42);
        if (w <= 0 || h <= 0 || w > 2048 || h > 2048) {
            throw new IOException("bad LS2MAP size " + w + "x" + h + " in " + name);
        }
        long need = HEADER_SIZE + (long) w * h * 4L;
        if (raw.length < need) {
            throw new IOException("truncated LS2MAP " + name + ": need " + need + " have " + raw.length);
        }
        int n = w * h;
        int[] tile = new int[n];
        int[] obj = new int[n];
        for (int i = 0; i < n; i++) {
            int off = HEADER_SIZE + i * 4;
            tile[i] = u16be(raw, off);
            obj[i] = u16be(raw, off + 2);
        }
        return new ServerMap(mapId, mapName, w, h, tile, obj);
    }

    /**
     * Effective walk attribute for a cell using {@link MapsetWalkTable}.
     *
     * <p>Rules (from mapset.txt col3 + server maps):</p>
     * <ul>
     *   <li>out of bounds → {@link MapsetWalkTable#BLOCKED};</li>
     *   <li>tile graphic looked up in mapset (unknown → blocked);</li>
     *   <li>object graphic {@code 0} is empty and does not block, even though
     *       mapset id 0 itself is col3=1; any other object is looked up;</li>
     *   <li>cell is blocked if either layer is blocked; else special if either
     *       is special; else open.</li>
     * </ul>
     *
     * @return {@link MapsetWalkTable#OPEN}, {@link MapsetWalkTable#BLOCKED},
     *         or {@link MapsetWalkTable#SPECIAL}
     */
    public static int cellWalkAttr(ServerMap map, MapsetWalkTable table, int x, int y) {
        if (map == null || x < 0 || y < 0 || x >= map.width || y >= map.height) {
            return MapsetWalkTable.BLOCKED;
        }
        if (table == null) {
            return MapsetWalkTable.OPEN;
        }
        int tileId = map.tileAt(x, y);
        int objId = map.objAt(x, y);
        int tileAttr = table.walkAttr(tileId);
        if (tileAttr < 0) tileAttr = MapsetWalkTable.BLOCKED;
        int objAttr;
        if (objId == 0) {
            objAttr = MapsetWalkTable.OPEN; // empty object slot
        } else {
            objAttr = table.walkAttr(objId);
            if (objAttr < 0) objAttr = MapsetWalkTable.BLOCKED;
        }
        if (tileAttr == MapsetWalkTable.BLOCKED || objAttr == MapsetWalkTable.BLOCKED) {
            return MapsetWalkTable.BLOCKED;
        }
        if (tileAttr == MapsetWalkTable.SPECIAL || objAttr == MapsetWalkTable.SPECIAL) {
            return MapsetWalkTable.SPECIAL;
        }
        return MapsetWalkTable.OPEN;
    }

    /**
     * Walkability check: open floor only. Special (2) and blocked (1) are both
     * not freely walkable; inspect {@link #cellWalkAttr} to treat specials.
     */
    public static boolean isWalkable(ServerMap map, MapsetWalkTable table, int x, int y) {
        return cellWalkAttr(map, table, x, y) == MapsetWalkTable.OPEN;
    }

    /** GBK name field, stopping at NUL or a {@code "|NN"} suffix boundary. */
    private static String decodeName(byte[] raw, int off, int len) {
        int end = off;
        int max = Math.min(raw.length, off + len);
        while (end < max && raw[end] != 0) {
            end++;
        }
        // Keep the raw "|0"/"|2" tail out of the display name.
        String s = new String(raw, off, end - off, Charset.forName("GBK"));
        int bar = s.indexOf('|');
        return bar >= 0 ? s.substring(0, bar) : s;
    }

    private static int u16be(byte[] b, int off) {
        return ((b[off] & 0xff) << 8) | (b[off + 1] & 0xff);
    }

    private ServerMapLoader() {}
}
