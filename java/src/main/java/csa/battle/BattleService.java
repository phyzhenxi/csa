package csa.battle;

import java.util.Random;

/**
 * B-command action dispatcher on top of {@link DamageCalculator}.
 *
 * <p>B command carries one int action code:
 * <ul>
 *   <li>1 = attack</li>
 *   <li>2 = skill</li>
 *   <li>3 = defend (incoming damage halved)</li>
 *   <li>else = unknown → error reply token</li>
 * </ul>
 *
 * <p>Random window is drawn from an injectable {@link Random} so self-tests
 * can pin a seed and assert deterministic damage.
 */
public final class BattleService {

    public static final int ACTION_ATTACK = 1;
    public static final int ACTION_SKILL = 2;
    public static final int ACTION_DEFEND = 3;

    /** Echo token shape matches gateway "B <action>" reply. */
    public static final String TOKEN_PREFIX = "B";
    public static final String TOKEN_ERROR = "ERR";

    public static final double SKILL_ATTR_RATIO = 1.5;
    public static final double NORMAL_ATTR_RATIO = 1.0;
    public static final int CRIT_RATE_PERCENT = 10;

    /** Immutable outcome of one B action. */
    public static final class BattleResult {
        public final int damage;
        public final boolean critical;
        public final String replyToken;

        public BattleResult(int damage, boolean critical, String replyToken) {
            this.damage = damage;
            this.critical = critical;
            this.replyToken = replyToken;
        }
    }

    private final DamageCalculator.Fighter attacker;
    private final DamageCalculator.Fighter defender;
    private final Random random;

    /** Default fighters + seeded RNG (test-friendly). */
    public BattleService(long seed) {
        this(defaultAttacker(), defaultDefender(), new Random(seed));
    }

    public BattleService(DamageCalculator.Fighter attacker, DamageCalculator.Fighter defender, long seed) {
        this(attacker, defender, new Random(seed));
    }

    public BattleService(DamageCalculator.Fighter attacker, DamageCalculator.Fighter defender, Random random) {
        if (attacker == null || defender == null || random == null) {
            throw new IllegalArgumentException("attacker/defender/random required");
        }
        this.attacker = attacker;
        this.defender = defender;
        this.random = random;
    }

    /**
     * Dispatch one B action code and return damage / crit flag / reply token.
     */
    public BattleResult onAction(int action) {
        if (action != ACTION_ATTACK && action != ACTION_SKILL && action != ACTION_DEFEND) {
            return new BattleResult(0, false, TOKEN_ERROR);
        }

        double u = random.nextDouble();
        double attrRatio = (action == ACTION_SKILL) ? SKILL_ATTR_RATIO : NORMAL_ATTR_RATIO;
        int dmg = DamageCalculator.calc(attacker, defender, attrRatio, u);

        boolean critical = random.nextInt(100) < CRIT_RATE_PERCENT;
        if (action == ACTION_DEFEND) {
            dmg = dmg / 2;
        }

        return new BattleResult(dmg, critical, TOKEN_PREFIX + " " + action);
    }

    static DamageCalculator.Fighter defaultAttacker() {
        DamageCalculator.Fighter f = new DamageCalculator.Fighter();
        f.ridePetIndex = -1;
        f.work0x16 = 500;
        f.resistHi16 = new int[] {0, 0, 0, 0};
        f.critWork = 0;
        return f;
    }

    static DamageCalculator.Fighter defaultDefender() {
        DamageCalculator.Fighter f = new DamageCalculator.Fighter();
        f.ridePetIndex = -1;
        f.work0x16 = 300;
        f.resistHi16 = new int[] {0, 0, 0, 0};
        f.critWork = 0;
        f.critAtkInt = 0;
        f.critDefInt = 1;
        return f;
    }
}
