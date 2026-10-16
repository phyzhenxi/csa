package csa.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Best-effort loader for Stone Age client map binaries.
 *
 * <p>Two on-disk layouts observed in {@code 嘟嘟石器客户端}:</p>
 * <ul>
 *   <li><b>Floor maps</b> ({@code map/*.dat}, also referenced as {@code *.MAP}):
 *       {@code uint32le width}, {@code uint32le height}, then
 *       {@code width*height} tiles of 6 bytes each
 *       ({@code 3 x uint16le}: graphic, object/extra, flags).
 *       Verified: 25x25→3758, 50x50→15008, 800x800→3840008 bytes.</li>
 *   <li><b>Battle maps</b> ({@code data/battleMap/*.map}):
 *       4-byte magic {@code "SAB "}, then 400 big-endian {@code uint16}
 *       tile ids (fixed 804-byte files → best-effort 20x20 grid).</li>
 * </ul>
 *
 * <p><b>Walk flags</b> (inferred from several {@code map/*.dat} samples):
 * the third {@code uint16le} of each floor tile is the walk attribute.
 * {@code 0} = walkable open floor; any other value = blocked
 * (walls, objects, void). Void tiles encode as {@code 0xC000} on every
 * word (graphic 0 with both high flag bits) and are also blocked via the
 * graphic-0 void check. Plain graphic {@code 0} with flag {@code 0} is
 * valid open floor (indoor room tiles and outdoor ground) and is
 * <em>not</em> blocked.</p>
 */
public final class MapLoader {

    /**
     * Decoded map grid. {@code tiles[y*width + x]} is the walk-block byte:
     * {@code 0} = walkable, non-zero = blocked.
     */
    public static final class MapData {
        public final int width;
        public final int height;
        public final byte[] tiles;

        public MapData(int width, int height, byte[] tiles) {
            this.width = width;
            this.height = height;
            this.tiles = tiles;
        }

        /** Walk-block byte: 0 = walkable, non-zero = blocked. OOB returns -1. */
        public int tileAt(int x, int y) {
            if (x < 0 || y < 0 || x >= width || y >= height) return -1;
            return tiles[y * width + x] & 0xff;
        }
    }

    /**
     * Load a floor {@code *.dat}/{@code *.MAP} or battle {@code *.map} file.
     *
     * @throws IOException on I/O error or unrecognized layout
     */
    public static MapData load(Path path) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        return parse(path.getFileName().toString(), raw);
    }

    public static MapData load(String path) throws IOException {
        return load(Path.of(path));
    }

    /** Parse already-read bytes; name is only used for error messages. */
    public static MapData parse(String name, byte[] raw) throws IOException {
        if (raw == null || raw.length < 8) {
            throw new IOException("map too small: " + name + " (" + (raw == null ? 0 : raw.length) + " bytes)");
        }
        if (raw.length >= 4 && raw[0] == 'S' && raw[1] == 'A' && raw[2] == 'B' && raw[3] == ' ') {
            return parseBattle(name, raw);
        }
        return parseFloor(name, raw);
    }

    private static MapData parseFloor(String name, byte[] raw) throws IOException {
        int w = u32le(raw, 0);
        int h = u32le(raw, 4);
        if (w <= 0 || h <= 0 || w > 4096 || h > 4096) {
            throw new IOException("bad floor size " + w + "x" + h + " in " + name);
        }
        long need = 8L + (long) w * h * 6L;
        if (raw.length < need) {
            throw new IOException("truncated floor map " + name + ": need " + need + " have " + raw.length);
        }
        byte[] tiles = new byte[w * h];
        for (int i = 0; i < tiles.length; i++) {
            int off = 8 + i * 6;
            int graphic = u16le(raw, off);       // word1
            int flags = u16le(raw, off + 4);     // word3 = walk attribute
            // Inferred rule: flags==0 → walkable. Void graphic (0xC000) also blocks.
            boolean voidTile = (graphic & 0xC000) == 0xC000;
            tiles[i] = (byte) ((flags == 0 && !voidTile) ? 0 : 1);
        }
        return new MapData(w, h, tiles);
    }

    private static MapData parseBattle(String name, byte[] raw) throws IOException {
        int n = (raw.length - 4) / 2;
        if (n <= 0) {
            throw new IOException("empty battle map " + name);
        }
        // 400 cells → 20x20 is the only square grid; other sizes fall back to 1-row.
        int w = 20;
        int h = n / 20;
        if (n % 20 != 0) {
            w = n;
            h = 1;
        }
        // TODO: battle maps have no stored w/h; 20x20 is best-effort from 400 cells.
        // Battle tiles are all open (no walk-flag field in the SAB layout).
        byte[] tiles = new byte[n]; // 0 = walkable
        return new MapData(w, h, tiles);
    }

    /**
     * Walkability check against a loaded map.
     *
     * <p>Floor maps: walkable iff tile walk-flag word (3rd uint16le) is 0
     * and the cell is not void ({@code graphic & 0xC000 == 0xC000}).
     * Out of bounds is blocked. Battle maps are fully open.</p>
     */
    public static boolean isWalkable(MapData map, int x, int y) {
        if (map == null) return true;
        if (x < 0 || y < 0 || x >= map.width || y >= map.height) return false;
        return map.tileAt(x, y) == 0;
    }

    private static int u16le(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }

    private static int u32le(byte[] b, int off) {
        return (b[off] & 0xff)
                | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16)
                | ((b[off + 3] & 0xff) << 24);
    }

    private MapLoader() {}
}
