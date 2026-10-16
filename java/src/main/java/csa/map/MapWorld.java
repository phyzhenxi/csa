package csa.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World registry: floor id → LS2MAP grid ({@link ServerMapLoader}) plus the
 * shared graphic walk table ({@link MapsetWalkTable}).
 *
 * <p>Used by the gateway after CharLogin (spawn) and on every W step
 * (destination walkability). Walk rules:</p>
 * <ul>
 *   <li>unknown floor / no map registered → permissive {@code OPEN}
 *       (geometry-only validation still applies);</li>
 *   <li>registered map → {@link ServerMapLoader#cellWalkAttr};</li>
 *   <li>{@code findSpawn} returns an OPEN tile near the map centre whose
 *       four cardinal neighbours are also OPEN when possible.</li>
 * </ul>
 */
public final class MapWorld {

    /** Preferred default floor when that map is registered. */
    public static final int DEFAULT_FLOOR_PREFERRED = 1997;
    /** Fallback default floor when 1997 is absent. */
    public static final int DEFAULT_FLOOR = 101;

    /** Resolved spawn point (floor + tile + facing). */
    public static final class Spawn {
        public final int floor, x, y, dir;

        public Spawn(int floor, int x, int y, int dir) {
            this.floor = floor;
            this.x = x;
            this.y = y;
            this.dir = dir;
        }

        @Override
        public String toString() {
            return "Spawn{floor=" + floor + ",x=" + x + ",y=" + y + ",dir=" + dir + "}";
        }
    }

    private final ConcurrentHashMap<Integer, ServerMapLoader.ServerMap> maps = new ConcurrentHashMap<>();
    private volatile MapsetWalkTable mapset;

    /** Install (or clear with {@code null}) the mapset walk table. */
    public void registerMapset(MapsetWalkTable table) {
        this.mapset = table;
    }

    public MapsetWalkTable mapset() {
        return mapset;
    }

    /** Register a server map under {@code floor}. {@code null} unregisters. */
    public void registerMap(int floor, ServerMapLoader.ServerMap map) {
        if (map == null) {
            maps.remove(floor);
        } else {
            maps.put(floor, map);
        }
    }

    public boolean hasMap(int floor) {
        return maps.containsKey(floor);
    }

    public ServerMapLoader.ServerMap map(int floor) {
        return maps.get(floor);
    }

    /** Floor ids with a registered map, ascending. */
    public List<Integer> floors() {
        List<Integer> list = new ArrayList<>(maps.keySet());
        Collections.sort(list);
        return list;
    }

    /**
     * Walk attribute at (floor,x,y). Unregistered floor →
     * {@link MapsetWalkTable#OPEN}; otherwise
     * {@link ServerMapLoader#cellWalkAttr}.
     */
    public int cellWalkAttr(int floor, int x, int y) {
        ServerMapLoader.ServerMap map = maps.get(floor);
        if (map == null) {
            return MapsetWalkTable.OPEN;
        }
        return ServerMapLoader.cellWalkAttr(map, mapset, x, y);
    }

    /** True only for an open (attr 0) in-bounds cell on a registered map. */
    public boolean isWalkable(int floor, int x, int y) {
        return cellWalkAttr(floor, x, y) == MapsetWalkTable.OPEN;
    }

    /**
     * Safe open spawn tile on {@code floor}: OPEN cell nearest the map
     * centre, preferring cells whose four cardinal neighbours are also OPEN.
     *
     * @return spawn, or {@code null} when the floor has no map or no open tile
     */
    public Spawn findSpawn(int floor) {
        ServerMapLoader.ServerMap map = maps.get(floor);
        if (map == null) {
            return null;
        }
        MapsetWalkTable table = mapset;
        int cx = map.width / 2;
        int cy = map.height / 2;
        Spawn best = null;
        int bestRank = Integer.MAX_VALUE;
        int bestDist = Integer.MAX_VALUE;
        for (int y = 0; y < map.height; y++) {
            for (int x = 0; x < map.width; x++) {
                if (ServerMapLoader.cellWalkAttr(map, table, x, y) != MapsetWalkTable.OPEN) {
                    continue;
                }
                boolean safe = isOpenCell(map, table, x - 1, y)
                        && isOpenCell(map, table, x + 1, y)
                        && isOpenCell(map, table, x, y - 1)
                        && isOpenCell(map, table, x, y + 1);
                int dist = Math.max(Math.abs(x - cx), Math.abs(y - cy));
                int rank = safe ? 0 : 1;
                if (rank < bestRank || (rank == bestRank && dist < bestDist)) {
                    bestRank = rank;
                    bestDist = dist;
                    best = new Spawn(floor, x, y, 0);
                }
            }
        }
        return best;
    }

    /**
     * Default spawn for CharLogin: floor {@link #DEFAULT_FLOOR_PREFERRED}
     * (1997) if registered, else {@link #DEFAULT_FLOOR} (101), else the
     * lowest registered floor, else a no-map fallback at (101,0,0).
     */
    public Spawn spawn() {
        Spawn s = spawnOn(DEFAULT_FLOOR_PREFERRED);
        if (s != null) {
            return s;
        }
        s = spawnOn(DEFAULT_FLOOR);
        if (s != null) {
            return s;
        }
        for (int floor : floors()) {
            s = findSpawn(floor);
            if (s != null) {
                return s;
            }
        }
        return new Spawn(DEFAULT_FLOOR, 0, 0, 0);
    }

    private Spawn spawnOn(int floor) {
        return findSpawn(floor);
    }

    private static boolean isOpenCell(ServerMapLoader.ServerMap map, MapsetWalkTable table, int x, int y) {
        return ServerMapLoader.cellWalkAttr(map, table, x, y) == MapsetWalkTable.OPEN;
    }

    /**
     * Load {@code mapset.txt} (if present) and every numeric-named LS2MAP
     * under {@code dir}, including subfolders such as {@code extra/}.
     * Floor id is the LS2MAP {@code mapId} field. Unparseable files are skipped.
     *
     * @return number of maps registered
     */
    public int loadFrom(Path dir) throws IOException {
        if (dir == null) {
            return 0;
        }
        Path mapsetPath = dir.resolve("mapset.txt");
        if (Files.isRegularFile(mapsetPath)) {
            registerMapset(MapsetWalkTable.load(mapsetPath));
        }
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int loaded = 0;
        try (var walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                String name = p.getFileName().toString();
                if (!isNumericName(name)) {
                    continue;
                }
                try {
                    ServerMapLoader.ServerMap map = ServerMapLoader.load(p);
                    registerMap(map.mapId, map);
                    loaded++;
                } catch (IOException | RuntimeException skipped) {
                    // not an LS2MAP / truncated — ignore
                }
            }
        }
        return loaded;
    }

    public int loadFrom(String dir) throws IOException {
        return loadFrom(Path.of(dir));
    }

    private static boolean isNumericName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "MapWorld{maps=" + maps.size() + ", mapset=" + (mapset == null ? "none" : mapset.size())
                + ", spawn=" + spawn() + "}";
    }
}
