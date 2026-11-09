import java.util.*;

/**
 * BONUS LOCAL CARD: Every 3rd capture you make, spawn a random piece
 * (Knight/Bishop/Rook) on a random empty square on your back rank.
 * Cost: 30 gold
 */
public class CardSoulHarvest extends Card {
    private static final long serialVersionUID = 2L;
    static final String CAPTURES = "soulHarvest.captures";

    public CardSoulHarvest() {
        super("Soul Harvest",
              "Every 3rd capture you make, spawn a random piece on your back rank.",
              30, false);
    }

    @Override
    public void onCapture(GameState state, Move move, Piece captured, Piece capturer) {
        if (capturer == null || !capturer.isWhite()) return; // player only
        int captureCount = state.incrementCounter(CAPTURES);
        if (captureCount % 3 != 0) return;

        List<Integer> emptyCols = new ArrayList<>();
        for (int c = 0; c < 8; c++) {
            if (state.board[7][c] == null) emptyCols.add(c);
        }
        if (emptyCols.isEmpty()) return;

        // Deterministic spawn
        int seed = captureCount * 17 + state.moveHistory.size();
        int col = emptyCols.get(Math.abs(seed) % emptyCols.size());
        Piece spawned;
        switch (Math.abs(seed * 31) % 3) {
            case 0: spawned = new Knight(true); break;
            case 1: spawned = new Bishop(true); break;
            default: spawned = new Rook(true); break;
        }
        state.board[7][col] = spawned;
    }
}
