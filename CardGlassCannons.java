
/**
 * GLOBAL CARD: Whenever any piece captures, there is a 50% chance
 * the capturing piece is also destroyed.
 * Cost: 15 gold
 * 
 * Uses deterministic RNG for AI consistency — same position+move
 * always produces the same outcome in minimax.
 */
public class CardGlassCannons extends Card {
    private static final long serialVersionUID = 1L;

    public CardGlassCannons() {
        super("Glass Cannons",
              "50% chance the capturing piece is also destroyed after a capture.",
              15, true);
    }

    @Override
    public void onCapture(GameState state, Move move, Piece captured, Piece capturer) {
        if (capturer == null) return;
        // Deterministic RNG: based on move coordinates and move count
        int seed = move.fromRow * 512 + move.fromCol * 64 + move.toRow * 8 + move.toCol;
        seed ^= state.moveHistory.size() * 0x9E3779B9;
        boolean selfDestruct = ((seed >>> 16) & 1) == 0; // 50% chance

        if (selfDestruct) {
            // Destroy the capturer (piece that just moved to the capture square)
            state.board[move.toRow][move.toCol] = null;
            System.out.println("GLASS CANNONS: Capturer destroyed at (" +
                move.toRow + "," + move.toCol + ")!");
        }
    }
}
