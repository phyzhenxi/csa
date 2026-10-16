package csa.game;

import csa.net.cmd.Commands;
import csa.net.lssp.LssProtocolCodec;

/**
 * TD / FM / WN command service (trade, family, window).
 *
 * <p>Wire context (see {@code csa.net.lssp.LssProtocolCodec}):
 * integer args are base62 ints, strings are escaped; frame is
 * {@code msgId SP command SP args "\n"}.
 *
 * <ul>
 *   <li>{@code TD} trade request &rarr; {@code "TD ok"} /
 *       {@code "TD ERR_STREET_VENDOR"} / {@code "TD ERR"}</li>
 *   <li>{@code FM} family join / leave &rarr; {@code "FM ok"} / {@code "FM ERR"}</li>
 *   <li>{@code WN} window select &rarr; {@code "WN ok"} /
 *       {@code "WN ERR_BAD_WINDOW"} / {@code "WN ERR_EVENT"}</li>
 * </ul>
 *
 * <p>Recv sites (gmsvjt): TD_recv @ 0x808EA00, FM_recv @ 0x808E940,
 * WN_recv @ 0x808F440. Binary error strings:
 * {@code 摆摊状态下不能交易}, {@code 改封包!! 非法的窗口号}, {@code 不接受召唤任务}.
 *
 * <p>State is in-memory and seedable for tests:
 * street-vendor flag (WorkInt 0x137), family name, expected/last window.
 */
public final class TradeFamilyWindowService {

    /** Trade accepted. */
    public static final String TRADE_OK = "TD ok";
    /** Trade refused while street-vendoring (binary: 摆摊状态下不能交易). */
    public static final String TRADE_ERR_STREET_VENDOR = "TD ERR_STREET_VENDOR";
    /** Trade refused: bad partner / player / item. */
    public static final String TRADE_ERR = "TD ERR";

    /** Family join / leave accepted. */
    public static final String FAMILY_OK = "FM ok";
    /** Family join / leave refused. */
    public static final String FAMILY_ERR = "FM ERR";

    /** Window select accepted. */
    public static final String WINDOW_OK = "WN ok";
    /** Illegal window number (binary: 改封包!! 非法的窗口号). */
    public static final String WINDOW_ERR_BAD_WINDOW = "WN ERR_BAD_WINDOW";
    /** Summon-task window rejected (binary: 不接受召唤任务). */
    public static final String WINDOW_ERR_EVENT = "WN ERR_EVENT";

    /** Binary string when trading while street-vendoring. */
    public static final String MSG_STREET_VENDOR = "摆摊状态下不能交易";
    /** Binary string for an illegal window number. */
    public static final String MSG_BAD_WINDOW = "改封包!! 非法的窗口号";
    /** Binary string when a summon task window is not accepted. */
    public static final String MSG_NO_SUMMON = "不接受召唤任务";

    /** WN data max length (0x800), from command-signatures.json. */
    public static final int WN_MAX_DATA = Commands.WN_MAX_DATA;

    /**
     * {@code select} value that requests a summon task.
     * Rejected with {@link #WINDOW_ERR_EVENT} / {@link #MSG_NO_SUMMON}.
     */
    public static final int SELECT_SUMMON_TASK = 9;

    /** Highest legal window {@code select} (inclusive). */
    public static final int SELECT_MAX = 255;

    /** Last (or seeded) window selection. */
    public static final class Window {
        public final int seqno;
        public final int objindex;
        public final int select;
        public final String data;

        public Window(int seqno, int objindex, int select, String data) {
            this.seqno = seqno;
            this.objindex = objindex;
            this.select = select;
            this.data = data == null ? "" : data;
        }

        @Override
        public String toString() {
            return "Window{seqno=" + seqno + ",objindex=" + objindex
                    + ",select=" + select + ",data=" + data + "}";
        }
    }

    private boolean streetVendor;
    private String familyName;
    private int expectedSeqno = -1;
    private Window lastWindow;

    // ---------------------------------------------------------------- seed

    /** Seed the street-vendor flag (WorkInt 0x137; true = vending). */
    public void setStreetVendor(boolean vending) {
        this.streetVendor = vending;
    }

    public boolean isStreetVendor() {
        return streetVendor;
    }

    /** Seed (or clear, with null/empty) the family name. */
    public void seedFamily(String name) {
        this.familyName = (name == null || name.isEmpty()) ? null : name;
    }

    /** Current family name, or null when not in a family. */
    public String familyName() {
        return familyName;
    }

    /**
     * Seed the seqno the next {@link #windowSelect} must carry.
     * Pass {@code -1} to accept any non-negative seqno.
     */
    public void seedExpectedWindow(int seqno) {
        this.expectedSeqno = seqno;
    }

    public int expectedWindowSeqno() {
        return expectedSeqno;
    }

    /** Seed the recorded last window (tests / persistence load). */
    public void seedLastWindow(int seqno, int objindex, int select, String data) {
        this.lastWindow = new Window(seqno, objindex, select, data);
    }

    /** Most recent accepted window selection, or null. */
    public Window lastWindow() {
        return lastWindow;
    }

    // ---------------------------------------------------------------- TD

    /**
     * Trade request: {@code player} offers {@code itemId} to {@code partner}.
     *
     * @return {@link #TRADE_OK} on accept,
     *         {@link #TRADE_ERR_STREET_VENDOR} while street-vendoring,
     *         {@link #TRADE_ERR} for blank names or a negative item id
     */
    public String tradeRequest(String player, String partner, int itemId) {
        if (streetVendor) {
            return TRADE_ERR_STREET_VENDOR;
        }
        if (isBlank(player) || isBlank(partner)) {
            return TRADE_ERR;
        }
        if (player.equals(partner)) {
            return TRADE_ERR;
        }
        if (itemId < 0) {
            return TRADE_ERR;
        }
        return TRADE_OK;
    }

    // ---------------------------------------------------------------- FM

    /**
     * Join family {@code name} (replaces any current family).
     *
     * @return {@link #FAMILY_OK} on accept, {@link #FAMILY_ERR} for a blank name
     */
    public String familyJoin(String name) {
        if (isBlank(name)) {
            return FAMILY_ERR;
        }
        familyName = name;
        return FAMILY_OK;
    }

    /**
     * Leave the current family.
     *
     * @return {@link #FAMILY_OK} when a family was set (then cleared),
     *         {@link #FAMILY_ERR} when not in a family
     */
    public String familyLeave() {
        if (familyName == null) {
            return FAMILY_ERR;
        }
        familyName = null;
        return FAMILY_OK;
    }

    // ---------------------------------------------------------------- WN

    /**
     * Window select (WN_recv: seqno, objindex, select, data).
     *
     * <p>Rejects data longer than {@link #WN_MAX_DATA} (0x800), a seqno that
     * does not match {@link #seedExpectedWindow} when one is set, a select
     * outside {@code [0,}{@link #SELECT_MAX}{@code ]}, and the summon-task
     * select ({@link #SELECT_SUMMON_TASK}).
     *
     * @return {@link #WINDOW_OK} on accept,
     *         {@link #WINDOW_ERR_BAD_WINDOW} for an illegal window number,
     *         {@link #WINDOW_ERR_EVENT} for the summon-task window
     */
    public String windowSelect(int seqno, int objindex, int select, String data) {
        String payload = data == null ? "" : data;
        if (payload.length() > WN_MAX_DATA) {
            return WINDOW_ERR_BAD_WINDOW;
        }
        if (seqno < 0 || select < 0 || select > SELECT_MAX) {
            return WINDOW_ERR_BAD_WINDOW;
        }
        if (expectedSeqno >= 0 && seqno != expectedSeqno) {
            return WINDOW_ERR_BAD_WINDOW;
        }
        if (select == SELECT_SUMMON_TASK) {
            return WINDOW_ERR_EVENT;
        }
        lastWindow = new Window(seqno, objindex, select, payload);
        return WINDOW_OK;
    }

    // ---------------------------------------------------------------- wire

    /**
     * Encode one WN argument list (base62 ints + escaped data) using
     * {@link LssProtocolCodec}, ready to append after the frame header.
     */
    public static String encodeWindowTokens(int seqno, int objindex, int select, String data) {
        String payload = data == null ? "" : data;
        return LssProtocolCodec.mkInt(seqno) + LssProtocolCodec.SP
                + LssProtocolCodec.mkInt(objindex) + LssProtocolCodec.SP
                + LssProtocolCodec.mkInt(select) + LssProtocolCodec.SP
                + LssProtocolCodec.mkString(payload);
    }

    /**
     * Encode the TD data token ({@code partner|itemId}) escaped for the wire.
     */
    public static String encodeTradeToken(String partner, int itemId) {
        String payload = (partner == null ? "" : partner) + "|" + itemId;
        return LssProtocolCodec.mkString(payload);
    }

    /** Command names used on the wire for these actions. */
    public static final String CMD_TRADE = Commands.TD;
    public static final String CMD_FAMILY = Commands.FM;
    public static final String CMD_WINDOW = Commands.WN;

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
