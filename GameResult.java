/**
 * Outcome of a game. The player is always White, the AI always Black.
 */
public enum GameResult {
    ONGOING,
    WHITE_WINS,
    BLACK_WINS,
    DRAW;

    public boolean isOver() {
        return this != ONGOING;
    }
}
