/**
 * GLOBAL CARD: Whenever any piece captures, there is a 50% chance
 * the capturing piece is also destroyed. Kings never shatter.
 * Cost: 15 gold
 *
 * The coin flip is deterministic (derived from the move and move count),
 * so the same position and move always give the same outcome.
 */
public class CardGlassCannons extends Card {
    private static final long serialVersionUID = 2L;

    public CardGlassCannons() {
        super("Glass Cannons",
              "50% chance the capturing piece is also destroyed after a capture (Kings are safe).",
              15, true);
    }

    @Override
    public void onCapture(GameState state, Move move, Piece captured, Piece capturer) {
        if (capturer == null || capturer instanceof King) return;
        int seed = move.fromRow * 512 + move.fromCol * 64 + move.toRow * 8 + move.toCol;
        seed ^= state.moveHistory.size() * 0x9E3779B9;
        boolean selfDestruct = ((seed >>> 16) & 1) == 0;
        if (selfDestruct) {
            // Sniper shots leave the shooter on its own square (toRow/toCol = fromRow/fromCol).
            state.board[move.toRow][move.toCol] = null;
        }
    }
}
