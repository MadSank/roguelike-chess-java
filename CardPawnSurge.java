import java.util.*;

/**
 * LOCAL CARD: Your pawns may advance two squares from any rank (if both squares are empty).
 * Cost: 10 gold
 */
public class CardPawnSurge extends Card {
    private static final long serialVersionUID = 1L;

    public CardPawnSurge() {
        super("Pawn Surge", "Your pawns may advance two squares from any rank.", 10, false);
    }

    @Override
    public List<Move> modifyMoveGeneration(List<Move> baseMoves, Piece piece,
            int row, int col, ChessEngine engine, GameState state) {
        if (!(piece instanceof Pawn) || !appliesTo(piece.isWhite())) return baseMoves;
        boolean white = piece.isWhite();
        int dir = white ? -1 : 1;
        int startRow = white ? 6 : 1;
        if (row == startRow) return baseMoves; // the normal double step already covers it
        int one = row + dir, two = row + 2 * dir;
        if (!Square.insideBoard(two, col) || state.board[one][col] != null || state.board[two][col] != null) {
            return baseMoves;
        }
        boolean lastRank = (white && two == 0) || (!white && two == 7);
        if (lastRank) {
            for (String t : new String[]{"Q", "R", "B", "N"}) baseMoves.add(Move.promotion(row, col, two, col, t));
        } else {
            baseMoves.add(new Move(row, col, two, col));
        }
        return baseMoves;
    }
}
