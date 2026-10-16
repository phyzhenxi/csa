package csa.map;

/**
 * W / W2 walk validation and position apply (lssproto_W_recv @ 0x8093400,
 * lssproto_W2_recv @ 0x8092EF0; args x,y,dir as base62 ints on the wire).
 *
 * <pre>
 * W_recv(fd, x, y, dir):
 *   gate WorkInt(char, 0x47)==0     // not in battle
 *   gate WorkInt(char, 0x137)==-1   // not street-vendoring
 *   validate step  // server-side walk rules reconstructed here
 *   CHAR_setInt(x=5, y=6) + dir
 * W2_recv: same args, 1292B extended frame (GC gcgc path); same walk rules.
 * </pre>
 *
 * Session fields live in {@code csa.model.Domain.Character} /
 * {@code LssGateway.Session} ({@code x, y, dir[, floor]}).
 *
 * Movement rule implemented here:
 * <ul>
 *   <li>allow same cell (turn-in-place) and unit step of 1 in 8 directions
 *       (Chebyshev distance &le; 1);</li>
 *   <li>reject jumps larger than 2 tiles (Chebyshev distance &gt; 2);</li>
 *   <li>distance exactly 2 is not a legal unit step → {@link #ERR_ILLEGAL_STEP}.</li>
 * </ul>
 */
public final class WalkService {

    /** Move accepted. */
    public static final int OK = 0;
    /** Not a same-cell or 8-direction unit step (e.g. distance 2). */
    public static final int ERR_ILLEGAL_STEP = 1;
    /** Jump further than 2 tiles (Chebyshev distance &gt; 2). */
    public static final int ERR_JUMP_TOO_FAR = 2;
    /** Map file says the destination cell is blocked. */
    public static final int ERR_BLOCKED = 3;

    /** Position + facing after a walk. dir is the W/W2 dir argument (typically 0–7). */
    public static final class Pos {
        public final int x, y, dir;

        public Pos(int x, int y, int dir) {
            this.x = x;
            this.y = y;
            this.dir = dir;
        }

        @Override
        public String toString() {
            return "Pos{x=" + x + ",y=" + y + ",dir=" + dir + "}";
        }
    }

    /**
     * Validate a single walk step.
     *
     * @param fromX current X
     * @param fromY current Y
     * @param toX   target X (W/W2 x)
     * @param toY   target Y (W/W2 y)
     * @param dir   facing recorded with the move (W/W2 dir); not geometry-checked
     * @return {@link #OK} or an ERR_* code
     */
    public static int validateMove(int fromX, int fromY, int toX, int toY, int dir) {
        int dx = toX - fromX;
        int dy = toY - fromY;
        int chebyshev = Math.max(Math.abs(dx), Math.abs(dy));
        if (chebyshev > 2) {
            return ERR_JUMP_TOO_FAR;
        }
        if (chebyshev > 1) {
            return ERR_ILLEGAL_STEP;
        }
        // same cell or 8-direction unit step
        return OK;
    }

    /**
     * Validate and apply a walk, returning the new position.
     * Does not mutate session state; caller writes x/y/dir onto Session / Domain.
     *
     * @return new {@link Pos} when valid, or null when {@link #validateMove} rejects
     */
    public static Pos applyMove(int fromX, int fromY, int toX, int toY, int dir) {
        if (validateMove(fromX, fromY, toX, toY, dir) != OK) {
            return null;
        }
        return new Pos(toX, toY, dir);
    }

    /** floor id → loaded map tiles (via {@link MapLoader}). */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, MapLoader.MapData> MAPS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Register map tiles for a floor (loaded with {@link MapLoader#load}).
     * Passing {@code null} unregisters.
     */
    public static void registerMap(int floor, MapLoader.MapData map) {
        if (map == null) {
            MAPS.remove(floor);
        } else {
            MAPS.put(floor, map);
        }
    }

    /**
     * Map-file walkability. Default open when no map is registered for the floor.
     *
     * <p>Uses {@link MapLoader#isWalkable} against registered tiles
     * (floor {@code map/*.dat} / battle {@code data/battleMap/*.map}).
     * TODO: flip blocked cells to false and return {@link #ERR_BLOCKED}
     * from validateMove / applyMove once real walk flags are decoded.</p>
     */
    public static boolean isWalkable(int floor, int x, int y) {
        MapLoader.MapData map = MAPS.get(floor);
        if (map == null) {
            return true;
        }
        return MapLoader.isWalkable(map, x, y);
    }

    private WalkService() {}
}
