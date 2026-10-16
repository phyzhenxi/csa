package csa.game;

import csa.net.cmd.Commands;

/**
 * FS / EN / DP / DI command service (feature flags, place/drop, dump/parcel, item interact).
 *
 * <p>Wire context (see {@code csa.net.lssp.LssProtocolCodec}):
 * integer args are base62 ints, strings are escaped; frame is
 * {@code msgId SP command SP args "\n"}.
 *
 * <ul>
 *   <li>{@code FS} feature-flag bitfield &rarr; {@code "FS ok"} (records bitmask;
 *       each bit calls a setter in the binary)</li>
 *   <li>{@code EN} drop/place {@code (x,y)} &rarr; {@code "EN ok"} /
 *       {@code "EN ERR_MODE"} (rejected when WorkInt 0x46 == 2)</li>
 *   <li>{@code DP} dump / parcel &rarr; {@code "DP ok"} / {@code "DP ERR"}</li>
 *   <li>{@code DI} item interact (non-trade) &rarr; {@code "DI ok"} /
 *       {@code "DI ERR"}</li>
 * </ul>
 *
 * <p>Recv sites (gmsvjt): FS_recv @ 0x80902B0 (size 576),
 * EN_recv @ 0x80910B0, DP_recv @ 0x80926F0, DI_recv @ 0x80928F0.
 * Commands.{@code FS}/{@code EN} constants exist in
 * {@code csa.net.cmd.Commands}.
 *
 * <p>State is in-memory and seedable for tests:
 * featureFlags, mode (WorkInt 0x46), lastDump, lastInteract.
 */
public final class ActionSystemService {

    /** Feature flags accepted (bitmask recorded). */
    public static final String FS_OK = "FS ok";

    /** Place / drop accepted. */
    public static final String EN_OK = "EN ok";
    /** Place / drop refused: WorkInt 0x46 == {@link #MODE_PLACE_REJECT}. */
    public static final String EN_ERR_MODE = "EN ERR_MODE";

    /** Dump / parcel accepted (lastDump recorded). */
    public static final String DP_OK = "DP ok";
    /** Dump / parcel refused: bad itemId or blank dest. */
    public static final String DP_ERR = "DP ERR";

    /** Item interact accepted (lastInteract recorded). */
    public static final String DI_OK = "DI ok";
    /** Item interact refused: bad slot or blank action. */
    public static final String DI_ERR = "DI ERR";

    /**
     * WorkInt 0x46 value that rejects EN (drop/place).
     * From command-signatures: {@code work 0x46==2 时拒绝}.
     */
    public static final int MODE_PLACE_REJECT = 2;

    /** Last (or seeded) dump / parcel. */
    public static final class Dump {
        public final int itemId;
        public final String dest;

        public Dump(int itemId, String dest) {
            this.itemId = itemId;
            this.dest = dest == null ? "" : dest;
        }

        @Override
        public String toString() {
            return "Dump{itemId=" + itemId + ",dest=" + dest + "}";
        }
    }

    /** Last (or seeded) item interact. */
    public static final class Interact {
        public final int slot;
        public final String action;

        public Interact(int slot, String action) {
            this.slot = slot;
            this.action = action == null ? "" : action;
        }

        @Override
        public String toString() {
            return "Interact{slot=" + slot + ",action=" + action + "}";
        }
    }

    private int featureFlags;
    private int mode;
    private Dump lastDump;
    private Interact lastInteract;

    // ---------------------------------------------------------------- seed

    /** Seed the feature-flag bitmask (FS state). */
    public void seedFeatureFlags(int flags) {
        this.featureFlags = flags;
    }

    /** Current feature-flag bitmask. */
    public int featureFlags() {
        return featureFlags;
    }

    /** Seed WorkInt 0x46 mode (EN rejects when this equals {@link #MODE_PLACE_REJECT}). */
    public void seedMode(int mode) {
        this.mode = mode;
    }

    /** Current WorkInt 0x46 mode. */
    public int mode() {
        return mode;
    }

    /** Seed the recorded last dump / parcel (tests / persistence load). */
    public void seedLastDump(int itemId, String dest) {
        this.lastDump = new Dump(itemId, dest);
    }

    /** Most recent accepted dump / parcel, or null. */
    public Dump lastDump() {
        return lastDump;
    }

    /** Seed the recorded last item interact (tests / persistence load). */
    public void seedLastInteract(int slot, String action) {
        this.lastInteract = new Interact(slot, action);
    }

    /** Most recent accepted item interact, or null. */
    public Interact lastInteract() {
        return lastInteract;
    }

    // ---------------------------------------------------------------- FS

    /**
     * Apply the FS feature-flag bitfield. Each bit maps to a binary setter;
     * this layer records the full bitmask and always accepts.
     *
     * @return {@link #FS_OK} (records {@code flags})
     */
    public String setFeatureFlags(int flags) {
        featureFlags = flags;
        return FS_OK;
    }

    // ---------------------------------------------------------------- EN

    /**
     * Drop / place at {@code (x, y)} (EN_recv).
     *
     * @return {@link #EN_OK} on accept,
     *         {@link #EN_ERR_MODE} when {@link #mode()} is
     *         {@link #MODE_PLACE_REJECT} (WorkInt 0x46 == 2)
     */
    public String placeObject(int x, int y) {
        if (mode == MODE_PLACE_REJECT) {
            return EN_ERR_MODE;
        }
        return EN_OK;
    }

    // ---------------------------------------------------------------- DP

    /**
     * Dump / parcel item {@code itemId} to destination {@code dest} (DP_recv).
     *
     * @return {@link #DP_OK} on accept (records lastDump),
     *         {@link #DP_ERR} for a negative itemId or blank dest
     */
    public String dumpParcel(int itemId, String dest) {
        if (itemId < 0 || isBlank(dest)) {
            return DP_ERR;
        }
        lastDump = new Dump(itemId, dest);
        return DP_OK;
    }

    // ---------------------------------------------------------------- DI

    /**
     * Item interact in {@code slot} with {@code action} (DI_recv, non-trade).
     *
     * @return {@link #DI_OK} on accept (records lastInteract),
     *         {@link #DI_ERR} for a negative slot or blank action
     */
    public String itemInteract(int slot, String action) {
        if (slot < 0 || isBlank(action)) {
            return DI_ERR;
        }
        lastInteract = new Interact(slot, action);
        return DI_OK;
    }

    // ---------------------------------------------------------------- wire

    /** Command names used on the wire for these actions. */
    public static final String CMD_FS = Commands.FS;
    public static final String CMD_EN = Commands.EN;
    public static final String CMD_DP = "DP";
    public static final String CMD_DI = "DI";

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
