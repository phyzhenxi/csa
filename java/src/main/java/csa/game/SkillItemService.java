package csa.game;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import csa.item.ItemPetCodec;
import csa.net.cmd.Commands;

/**
 * Skill / item command service for the PS / SKUP / PI / I subset.
 *
 * <p>Wire context (see {@code csa.net.lssp.LssProtocolCodec}):
 * integer args are base62 ints, strings are escaped; frame is
 * {@code msgId SP command SP args "\n"}.
 *
 * <ul>
 *   <li>{@code I} / {@code PI} use item at inventory index
 *       &rarr; {@code "I ok"} / {@code "I ERR"}</li>
 *   <li>{@code PS} use skill at index against target
 *       &rarr; {@code "PS ok"} + damage-ish int (see {@link #lastDamage()})</li>
 *   <li>{@code SKUP} raise skill level &rarr; {@code "SKUP ok"}</li>
 * </ul>
 *
 * <p>State is in-memory and seedable for tests:
 * inventory {@code Map<Integer,Integer>} itemIndex &rarr; count,
 * skill levels {@code Map<Integer,Integer>} skillIndex &rarr; level.
 */
public final class SkillItemService {

    /** Item use accepted (count decremented). */
    public static final String ITEM_OK = "I ok";
    /** Item missing or count already zero. */
    public static final String ITEM_ERR = "I ERR";
    /** Skill use accepted; damage is the damage-ish int from {@link #lastDamage()}. */
    public static final String SKILL_OK = "PS ok";
    /** Skill index unknown or level &lt;= 0. */
    public static final String SKILL_ERR = "PS ERR";
    /** Skill level raised. */
    public static final String SKILLUP_OK = "SKUP ok";

    /** itemIndex &rarr; remaining count. */
    private final Map<Integer, Integer> inventory = new LinkedHashMap<>();
    /** skillIndex &rarr; level. */
    private final Map<Integer, Integer> skillLevels = new LinkedHashMap<>();

    private final Random random;
    private int lastDamage;

    public SkillItemService() {
        this(0L);
    }

    /** Seedable RNG so self-tests can pin damage. */
    public SkillItemService(long seed) {
        this.random = new Random(seed);
    }

    // ---------------------------------------------------------------- seed

    /** Seed (or top up) inventory count for tests / persistence load. */
    public void seedItem(int itemIndex, int count) {
        if (count <= 0) {
            inventory.remove(itemIndex);
        } else {
            inventory.put(itemIndex, count);
        }
    }

    /** Seed skill level for tests / persistence load. */
    public void seedSkill(int skillIndex, int level) {
        skillLevels.put(skillIndex, level);
    }

    public int itemCount(int itemIndex) {
        Integer n = inventory.get(itemIndex);
        return n == null ? 0 : n;
    }

    public int skillLevel(int skillIndex) {
        Integer lv = skillLevels.get(skillIndex);
        return lv == null ? 0 : lv;
    }

    /** Damage dealt by the most recent {@link #useSkill}. */
    public int lastDamage() {
        return lastDamage;
    }

    // ---------------------------------------------------------------- I / PI

    /**
     * Consume one item at {@code itemIndex}.
     *
     * @return {@link #ITEM_OK} when count was &gt; 0 (then count--),
     *         {@link #ITEM_ERR} when the slot is missing or empty
     */
    public String useItem(int itemIndex) {
        Integer n = inventory.get(itemIndex);
        if (n == null || n <= 0) {
            lastDamage = 0;
            return ITEM_ERR;
        }
        int left = n - 1;
        if (left <= 0) {
            inventory.remove(itemIndex);
        } else {
            inventory.put(itemIndex, left);
        }
        lastDamage = 0;
        return ITEM_OK;
    }

    // ---------------------------------------------------------------- PS

    /**
     * Use skill {@code skillIndex} against {@code target}.
     * Damage is a small level-scaled int (skeleton of the BATTLE path).
     *
     * @return {@link #SKILL_OK} when the skill exists at level &gt; 0,
     *         {@link #SKILL_ERR} otherwise; damage via {@link #lastDamage()}
     */
    public String useSkill(int skillIndex, int target) {
        int lv = skillLevel(skillIndex);
        if (lv <= 0) {
            lastDamage = 0;
            return SKILL_ERR;
        }
        // damage-ish: level-scaled, target folded in, seeded window
        int base = lv * 10 + (Math.floorMod(target, 7));
        int window = random.nextInt(Math.max(1, lv * 3));
        lastDamage = base + window;
        return SKILL_OK;
    }

    /**
     * Damage-ish int for the given skill/target pair without mutating inventory.
     * Companion to {@link #useSkill}; token form of a hit is still {@code "PS ok"}.
     */
    public int skillDamage(int skillIndex, int target) {
        int lv = skillLevel(skillIndex);
        if (lv <= 0) {
            return 0;
        }
        return lv * 10 + Math.floorMod(target, 7) + random.nextInt(Math.max(1, lv * 3));
    }

    // ---------------------------------------------------------------- SKUP

    /**
     * Raise skill level by 1 (creates the skill at 1 if absent).
     *
     * @return {@link #SKILLUP_OK}
     */
    public String skillUp(int skillIndex) {
        int lv = skillLevel(skillIndex);
        skillLevels.put(skillIndex, lv + 1);
        return SKILLUP_OK;
    }

    // ---------------------------------------------------------------- wire

    /**
     * Encode one skill slot the way {@code SKILL_makeStringFromSkillData} stores it
     * ({@code "lv=%d|id=%d|"}).
     */
    public String encodeSkillString(int skillIndex) {
        return ItemPetCodec.encodeSkill(skillLevel(skillIndex), skillIndex);
    }

    /** Command name constants used on the wire for these actions. */
    public static final String CMD_USE_ITEM = "I";
    public static final String CMD_USE_ITEM_ALT = "PI";
    public static final String CMD_USE_SKILL = Commands.PS;
    public static final String CMD_SKILL_UP = "SKUP";
}
