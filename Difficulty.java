/**
 * How strong the AI plays at each level (DESIGN.md: difficulty comes from a table, not raw depth).
 *
 * Each level sets:
 *  - searchDepth: most plies the AI looks ahead (iterative deepening stops earlier if time runs out)
 *  - timeLimitMs: thinking time per move
 *  - evalTier:    0 = material only, 1 = + mobility and centre, 2 = + piece placement tables
 *  - quiescence:  keeps searching captures past the depth limit, so it doesn't stop mid-exchange
 *  - blunderPercent: chance of playing a random legal move instead of thinking
 */
public final class Difficulty {
    public final int level;
    public final int searchDepth;
    public final long timeLimitMs;
    public final int evalTier;
    public final boolean quiescence;
    public final int blunderPercent;

    public Difficulty(int level, int searchDepth, long timeLimitMs, int evalTier,
                      boolean quiescence, int blunderPercent) {
        this.level = level;
        this.searchDepth = searchDepth;
        this.timeLimitMs = timeLimitMs;
        this.evalTier = evalTier;
        this.quiescence = quiescence;
        this.blunderPercent = blunderPercent;
    }

    //                  depth, time ms, tier, quiescence, blunder %
    private static final Object[][] TABLE = {
        /* L1  */ {1,  500, 0, false, 30},
        /* L2  */ {2,  500, 0, false, 20},
        /* L3  */ {2,  800, 1, false, 10},
        /* L4  */ {3, 1000, 1, false, 10},
        /* L5  */ {3, 1200, 1, true,   5},
        /* L6  */ {3, 1500, 1, true,   0},
        /* L7  */ {3, 1500, 2, true,   0},
        /* L8  */ {4, 2000, 2, true,   0},
        /* L9  */ {4, 2000, 2, true,   0},
        /* L10 */ {4, 2500, 2, true,   0},
        /* L11 */ {5, 3000, 2, true,   0},
        /* L12 */ {5, 3000, 2, true,   0},
        /* L13 */ {5, 3500, 2, true,   0},
        /* L14+ */{6, 4000, 2, true,   0},
    };

    public static Difficulty forLevel(int level) {
        int i = Math.min(Math.max(1, level), TABLE.length) - 1;
        Object[] row = TABLE[i];
        return new Difficulty(level, (Integer) row[0], ((Integer) row[1]).longValue(),
            (Integer) row[2], (Boolean) row[3], (Integer) row[4]);
    }

    /** Full-strength settings at a fixed depth with no time limit (used by tests and sims). */
    public static Difficulty fixedDepth(int depth) {
        return new Difficulty(0, depth, 0, 1, false, 0);
    }
}
