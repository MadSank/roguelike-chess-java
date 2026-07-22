
import java.util.*;

/**
 * GLOBAL CARD: No pieces (except the King) can move backward
 * toward their own starting side.
 * White backward = toRow > fromRow (moving toward row 7).
 * Black backward = toRow < fromRow (moving toward row 0).
 * Cost: 10 gold
 */
public class CardTrenchWarfare extends Card {
    private static final long serialVersionUID = 1L;

    public CardTrenchWarfare() {
        super("Trench Warfare",
              "No pieces (except Kings) can move backward toward their starting side.",
              10, true);
    }

    @Override
    public List<Move> modifyMoveGeneration(List<Move> baseMoves, Piece piece,
            int row, int col, ChessEngine engine, GameState state) {
        if (piece instanceof King) return baseMoves; // Kings exempt

        boolean isWhite = piece.isWhite();
        Iterator<Move> it = baseMoves.iterator();
        while (it.hasNext()) {
            Move m = it.next();
            if (isWhite && m.toRow > m.fromRow) {
                it.remove(); // White moving backward (toward row 7)
            } else if (!isWhite && m.toRow < m.fromRow) {
                it.remove(); // Black moving backward (toward row 0)
            }
        }
        return baseMoves;
    }
}
