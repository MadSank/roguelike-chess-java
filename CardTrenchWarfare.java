import java.util.*;

/**
 * GLOBAL CARD: No pieces (except Kings) can move or attack backward, toward their own side.
 * White backward = toward row 7. Black backward = toward row 0.
 * Cost: 10 gold
 */
public class CardTrenchWarfare extends Card {
    private static final long serialVersionUID = 2L;

    public CardTrenchWarfare() {
        super("Trench Warfare",
              "No pieces (except Kings) can move or attack backward toward their starting side.",
              10, true);
    }

    private static boolean isBackward(Piece piece, int fromRow, int toRow) {
        if (piece instanceof King) return false;
        return piece.isWhite() ? toRow > fromRow : toRow < fromRow;
    }

    @Override
    public List<Move> modifyMoveGeneration(List<Move> baseMoves, Piece piece,
            int row, int col, ChessEngine engine, GameState state) {
        baseMoves.removeIf(m -> isBackward(piece, m.fromRow, m.toRow));
        return baseMoves;
    }

    @Override
    public boolean blocksAttack(Piece piece, int fromRow, int fromCol,
            int toRow, int toCol, GameState state) {
        return isBackward(piece, fromRow, toRow);
    }
}
