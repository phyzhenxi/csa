package csa.game;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * PMSG / STREET_VENDOR / JOBDAILY command service (private mail, street vendor, daily job).
 *
 * <p>Wire context (see {@code csa.net.lssp.LssProtocolCodec}):
 * integer args are base62 ints, strings are escaped; frame is
 * {@code msgId SP command SP args "\n"}.
 *
 * <ul>
 *   <li>{@code PMSG} private message / mail-ish &rarr; {@code "PMSG ok"} /
 *       {@code "PMSG ERR"}</li>
 *   <li>{@code STREET_VENDOR} 摆摊 open / close &rarr; {@code "STV ok"} /
 *       {@code "STV ERR"}</li>
 *   <li>{@code JOBDAILY} daily job accept / complete &rarr; {@code "JD ok"} /
 *       {@code "JD ERR"} / {@code "JD ERR_DONE"}</li>
 * </ul>
 *
 * <p>Recv sites (gmsvjt): PMSG_recv @ 0x808ECD0, STREET_VENDOR_recv @ 0x808CF50,
 * JOBDAILY_recv @ 0x808CEC0. TK chat lives elsewhere and is not handled here.
 * Related binary error strings {@code 摆摊状态下不能交易} / {@code 账号锁定}
 * sit in TradeFamilyWindowService / LoginHandshake.
 *
 * <p>State is in-memory and seedable for tests:
 * street-vendor flag (WorkInt 0x137), last PMSG, completed job set.
 */
public final class SocialMiscService {

    /** Private message accepted (last PMSG recorded). */
    public static final String PMSG_OK = "PMSG ok";
    /** Private message refused: blank to/text or text longer than {@link #PMSG_MAX_TEXT}. */
    public static final String PMSG_ERR = "PMSG ERR";

    /** Street-vendor open / close accepted (flag updated). */
    public static final String STV_OK = "STV ok";
    /** Street-vendor open / close refused (blank name, double-open, or not open). */
    public static final String STV_ERR = "STV ERR";

    /** Daily job accept / complete accepted. */
    public static final String JD_OK = "JD ok";
    /** Daily job refused: bad jobId, or complete before accept. */
    public static final String JD_ERR = "JD ERR";
    /** Daily job refused: job already completed. */
    public static final String JD_ERR_DONE = "JD ERR_DONE";

    /** PMSG text max length (inclusive); longer text is rejected. */
    public static final int PMSG_MAX_TEXT = 200;

    /** Last (or seeded) private message. */
    public static final class Pmsg {
        public final String to;
        public final String text;

        public Pmsg(String to, String text) {
            this.to = to == null ? "" : to;
            this.text = text == null ? "" : text;
        }

        @Override
        public String toString() {
            return "Pmsg{to=" + to + ",text=" + text + "}";
        }
    }

    private boolean streetVendor;
    private String vendorName;
    private Pmsg lastPmsg;
    private final Set<Integer> acceptedJobs = new LinkedHashSet<>();
    private final Set<Integer> completedJobs = new LinkedHashSet<>();

    // ---------------------------------------------------------------- seed

    /** Seed the street-vendor flag (WorkInt 0x137; true = vending). */
    public void seedStreetVendor(boolean vending) {
        this.streetVendor = vending;
        if (!vending) {
            this.vendorName = null;
        }
    }

    public boolean isStreetVendor() {
        return streetVendor;
    }

    /** Current stall name, or null when not vending / not opened via {@link #streetVendorOpen}. */
    public String vendorName() {
        return vendorName;
    }

    /** Seed the recorded last private message (tests / persistence load). */
    public void seedLastPmsg(String to, String text) {
        this.lastPmsg = new Pmsg(to, text);
    }

    /** Most recent accepted private message, or null. */
    public Pmsg lastPmsg() {
        return lastPmsg;
    }

    /** Seed a job as already completed (tests / persistence load). */
    public void seedCompletedJob(int jobId) {
        completedJobs.add(jobId);
    }

    public boolean isJobCompleted(int jobId) {
        return completedJobs.contains(jobId);
    }

    public boolean isJobAccepted(int jobId) {
        return acceptedJobs.contains(jobId);
    }

    /** Unmodifiable view of completed job ids. */
    public Set<Integer> completedJobs() {
        return Collections.unmodifiableSet(completedJobs);
    }

    // ---------------------------------------------------------------- PMSG

    /**
     * Send a private message / mail to {@code to}.
     * Rejects a blank recipient, blank text, or text longer than
     * {@link #PMSG_MAX_TEXT} (200).
     *
     * @return {@link #PMSG_OK} on accept (records last PMSG),
     *         {@link #PMSG_ERR} otherwise
     */
    public String privateMessage(String to, String text) {
        if (isBlank(to) || isBlank(text)) {
            return PMSG_ERR;
        }
        if (text.length() > PMSG_MAX_TEXT) {
            return PMSG_ERR;
        }
        lastPmsg = new Pmsg(to, text);
        return PMSG_OK;
    }

    // ---------------------------------------------------------------- STV

    /**
     * Open a street-vendor stall named {@code name} and set the vendor flag.
     *
     * @return {@link #STV_OK} on accept,
     *         {@link #STV_ERR} for a blank name or when already vending
     */
    public String streetVendorOpen(String name) {
        if (isBlank(name) || streetVendor) {
            return STV_ERR;
        }
        streetVendor = true;
        vendorName = name;
        return STV_OK;
    }

    /**
     * Close the street-vendor stall and clear the vendor flag.
     *
     * @return {@link #STV_OK} when vending (then flag cleared),
     *         {@link #STV_ERR} when not vending
     */
    public String streetVendorClose() {
        if (!streetVendor) {
            return STV_ERR;
        }
        streetVendor = false;
        vendorName = null;
        return STV_OK;
    }

    // ---------------------------------------------------------------- JD

    /**
     * Accept daily job {@code jobId}.
     *
     * @return {@link #JD_OK} on accept (idempotent re-accept is ok),
     *         {@link #JD_ERR} for a negative jobId,
     *         {@link #JD_ERR_DONE} when the job is already completed
     */
    public String jobDailyAccept(int jobId) {
        if (jobId < 0) {
            return JD_ERR;
        }
        if (completedJobs.contains(jobId)) {
            return JD_ERR_DONE;
        }
        acceptedJobs.add(jobId);
        return JD_OK;
    }

    /**
     * Complete previously accepted daily job {@code jobId}.
     *
     * @return {@link #JD_OK} on complete,
     *         {@link #JD_ERR} for a negative jobId or a job never accepted,
     *         {@link #JD_ERR_DONE} when the job is already completed
     */
    public String jobDailyComplete(int jobId) {
        if (jobId < 0) {
            return JD_ERR;
        }
        if (completedJobs.contains(jobId)) {
            return JD_ERR_DONE;
        }
        if (!acceptedJobs.contains(jobId)) {
            return JD_ERR;
        }
        acceptedJobs.remove(jobId);
        completedJobs.add(jobId);
        return JD_OK;
    }

    // ---------------------------------------------------------------- wire

    /** Command names used on the wire for these actions. */
    public static final String CMD_PMSG = "PMSG";
    public static final String CMD_STREET_VENDOR = "STREET_VENDOR";
    public static final String CMD_JOBDAILY = "JOBDAILY";

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
