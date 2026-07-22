
import java.util.*;

/**
 * BONUS LOCAL CARD: Every 3rd capture you make, spawn a random piece
 * (Knight/Bishop/Rook) on a random empty square on your back rank.
 * Cost: 30 gold
 */
public class CardSoulHarvest extends Card {
    private static final long serialVersionUID = 1L;
    private int captureCount = 0;

    public CardSoulHarvest() {
        super("Soul Harvest",
              "Every 3rd capture you make, spawn a random piece on your back rank.",
              30, false);
    }

    @Override
    public void onCapture(GameState state, Move move, Piece captured, Piece capturer) {
        if (capturer == null || !capturer.isWhite()) return; // player only

        captureCount++;
        if (captureCount % 3 != 0) return;

        // Find empty squares on white's back rank (row 7)
        List<Integer> emptyCols = new ArrayList<>();
        for (int c = 0; c < 8; c++) {
            if (state.board[7][c] == null) {
                emptyCols.add(c);
            }
        }
        if (emptyCols.isEmpty()) return;

        // Deterministic spawn
        int seed = captureCount * 17 + state.moveHistory.size();
        int colIdx = Math.abs(seed) % emptyCols.size();
        int col = emptyCols.get(colIdx);

        // Random piece type
        Piece spawned;
        int pieceType = Math.abs(seed * 31) % 3;
        switch (pieceType) {
            case 0: spawned = new Knight(true); break;
            case 1: spawned = new Bishop(true); break;
            default: spawned = new Rook(true); break;
        }

        state.board[7][col] = spawned;
        System.out.println("SOUL HARVEST: Spawned " + spawned.getName() + " at (7," + col + ")!");
    }
}
