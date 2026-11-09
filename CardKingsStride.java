import java.util.*;

/**
 * LOCAL CARD: Your King may move two squares in a straight line or diagonal, if the square
 * in between is empty. It may capture on the landing square.
 * Cost: 20 gold
 */
public class CardKingsStride extends Card {
    private static final long serialVersionUID = 1L;
    private static final int[][] DIRS = {
        {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}
    };

    public CardKingsStride() {
        super("King's Stride", "Your King may move two squares in any direction if the path is clear.", 20, false);
    }

    @Override
    public List<Move> modifyMoveGeneration(List<Move> baseMoves, Piece piece,
            int row, int col, ChessEngine engine, GameState state) {
        if (!(piece instanceof King) || !appliesTo(piece.isWhite())) return baseMoves;
        for (int[] d : DIRS) {
            int mr = row + d[0], mc = col + d[1];
            int tr = row + 2 * d[0], tc = col + 2 * d[1];
            if (!Square.insideBoard(tr, tc) || state.board[mr][mc] != null) continue;
            Piece target = state.board[tr][tc];
            if (target == null) {
                baseMoves.add(new Move(row, col, tr, tc));
            } else if (target.isWhite() != piece.isWhite()) {
                Move m = new Move(row, col, tr, tc);
                m.isCapture = true;
                baseMoves.add(m);
            }
        }
        return baseMoves;
    }
}
