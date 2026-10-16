package csa.map;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Walk-attribute table from gmsv {@code data/map/mapset.txt}.
 *
 * <p>File format: whitespace-separated rows, blank lines ignored.</p>
 * <pre>
 *   col0  graphic id (tile / object id used by LS2MAP cells)
 *   col1  unused here (type-ish counter, mostly 1)
 *   col2  unused here (type-ish counter, mostly 1)
 *   col3  walk attribute: 0 = open, 1 = blocked, 2 = special
 *   col4  unused (almost always 0)
 *   col5  unused (always -1)
 *   …     further zero columns
 * </pre>
 *
 * <p>Despite the historical note "map id → walkable", column 0 is the
 * <b>tile/object graphic id</b> referenced by every LS2MAP cell — not the
 * map/floor id. Verified: every tile and object id sampled from server maps
 * resolves in this table (100% hit on 1997/1998/61000/104/1090/17002).</p>
 *
 * <p>Walk counts on the shipped file: 0=11082, 1=6283, 2=87.</p>
 */
public final class MapsetWalkTable {

    /** Open floor / non-blocking object. */
    public static final int OPEN = 0;
    /** Blocked. */
    public static final int BLOCKED = 1;
    /** Special (event / door / etc.); not freely walkable via {@link #isWalkable}. */
    public static final int SPECIAL = 2;

    private final Map<Integer, Integer> walkByGraphic;

    private MapsetWalkTable(Map<Integer, Integer> walkByGraphic) {
        this.walkByGraphic = walkByGraphic;
    }

    /**
     * Load mapset.txt.
     *
     * @throws IOException on I/O error or a row with fewer than 4 columns
     */
    public static MapsetWalkTable load(Path path) throws IOException {
        return parse(Files.readAllLines(path, StandardCharsets.ISO_8859_1));
    }

    public static MapsetWalkTable load(String path) throws IOException {
        return load(Path.of(path));
    }

    /** Parse already-read lines. Later rows win on duplicate ids. */
    public static MapsetWalkTable parse(Iterable<String> lines) throws IOException {
        Map<Integer, Integer> map = new HashMap<>();
        int lineNo = 0;
        for (String line : lines) {
            lineNo++;
            String s = line.trim();
            if (s.isEmpty()) {
                continue;
            }
            String[] parts = s.split("\\s+");
            if (parts.length < 4) {
                throw new IOException("mapset line " + lineNo + " needs >=4 cols: " + s);
            }
            int id;
            int walk;
            try {
                id = Integer.parseInt(parts[0]);
                walk = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                throw new IOException("mapset line " + lineNo + " bad int: " + s, e);
            }
            if (walk < 0 || walk > 2) {
                throw new IOException("mapset line " + lineNo + " walk out of range 0..2: " + walk);
            }
            map.put(id, walk);
        }
        return new MapsetWalkTable(map);
    }

    /**
     * Walk attribute for a tile/object graphic id.
     *
     * @return {@link #OPEN}/{@link #BLOCKED}/{@link #SPECIAL}, or -1 if unknown
     */
    public int walkAttr(int graphicId) {
        Integer v = walkByGraphic.get(graphicId);
        return v == null ? -1 : v;
    }

    /** True only when the graphic is known and {@link #OPEN}. */
    public boolean isWalkable(int graphicId) {
        return walkAttr(graphicId) == OPEN;
    }

    public boolean contains(int graphicId) {
        return walkByGraphic.containsKey(graphicId);
    }

    /** Number of graphic ids in the table. */
    public int size() {
        return walkByGraphic.size();
    }

    /** Unmodifiable view: graphic id → walk attr. */
    public Map<Integer, Integer> asMap() {
        return java.util.Collections.unmodifiableMap(walkByGraphic);
    }

    @Override
    public String toString() {
        return "MapsetWalkTable{size=" + walkByGraphic.size() + "}";
    }
}
