import java.util.*;

/**
 * LOCAL CARD: Your pawns may step one square backward onto an empty square
 * (never back onto your own first rank).
 * Cost: 10 gold
 */
public class CardPawnRetreat extends Card {
    private static final long serialVersionUID = 1L;

    public CardPawnRetreat() {
        super("Pawn Retreat", "Your pawns may step one square backward (not onto your first rank).", 10, false);
    }

    @Override
    public List<Move> modifyMoveGeneration(List<Move> baseMoves, Piece piece,
            int row, int col, ChessEngine engine, GameState state) {
        if (!(piece instanceof Pawn) || !appliesTo(piece.isWhite())) return baseMoves;
        int back = row + (piece.isWhite() ? 1 : -1);
        int firstRank = piece.isWhite() ? 7 : 0;
        if (back != firstRank && Square.insideBoard(back, col) && state.board[back][col] == null) {
            baseMoves.add(new Move(row, col, back, col));
        }
        return baseMoves;
    }
}
