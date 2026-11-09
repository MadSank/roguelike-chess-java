/**
 * How strong the AI plays at each level (DESIGN.md: difficulty comes from a table, not raw depth).
 */
public final class Difficulty {
    public final int level;
    /** Plies the AI searches. */
    public final int searchDepth;

    private Difficulty(int level, int searchDepth) {
        this.level = level;
        this.searchDepth = searchDepth;
    }

    public static Difficulty forLevel(int level) {
        int depth;
        if (level <= 2) depth = 2;
        else if (level <= 4) depth = 3;
        else if (level <= 8) depth = 4;
        else depth = 5;
        return new Difficulty(level, depth);
    }
}
