import java.util.*;

/**
 * LOCAL CARD: Your bishops may step one square sideways onto an empty square,
 * switching square colour.
 * Cost: 12 gold
 */
public class CardBishopShift extends Card {
    private static final long serialVersionUID = 1L;

    public CardBishopShift() {
        super("Bishop Shift", "Your bishops may step one square sideways to change colour.", 12, false);
    }

    @Override
    public List<Move> modifyMoveGeneration(List<Move> baseMoves, Piece piece,
            int row, int col, ChessEngine engine, GameState state) {
        if (!(piece instanceof Bishop) || !appliesTo(piece.isWhite())) return baseMoves;
        for (int dc : new int[]{-1, 1}) {
            int c = col + dc;
            if (Square.insideBoard(row, c) && state.board[row][c] == null) {
                baseMoves.add(new Move(row, col, row, c));
            }
        }
        return baseMoves;
    }
}
