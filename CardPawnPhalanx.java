
import java.util.*;

/**
 * LOCAL CARD: Friendly pawns are immune to capture if horizontally
 * adjacent to another friendly pawn.
 * Cost: 15 gold
 */
public class CardPawnPhalanx extends Card {
    private static final long serialVersionUID = 1L;

    public CardPawnPhalanx() {
        super("Pawn Phalanx",
              "Your pawns are immune to capture when horizontally adjacent to another friendly pawn.",
              15, false);
    }

    @Override
    public boolean isCaptureImmune(GameState state, int row, int col, Move attackMove) {
        Piece target = state.board[row][col];
        if (target == null) return false;
        if (!(target instanceof Pawn)) return false;
        if (!appliesTo(target.isWhite())) return false;

        // O(1) check: is there a friendly pawn to the left or right?
        if (col > 0) {
            Piece left = state.board[row][col - 1];
            if (left instanceof Pawn && left.isWhite() == target.isWhite()) {
                return true;
            }
        }
        if (col < 7) {
            Piece right = state.board[row][col + 1];
            if (right instanceof Pawn && right.isWhite() == target.isWhite()) {
                return true;
            }
        }
        return false;
    }
}
