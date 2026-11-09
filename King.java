import java.util.*;

/** King: one step in any direction. This variant has no castling. */
public class King extends Piece {
    private static final int[][] ADJACENT = {
        {-1, -1}, {-1, 0}, {-1, 1},
        {0, -1},           {0, 1},
        {1, -1},  {1, 0},  {1, 1}
    };

    public King(boolean white) {
        super(white, "King", 20);
    }

    @Override
    public List<Move> generateLegalMoves(int row, int col, ChessEngine engine) {
        List<Move> moves = new ArrayList<>();
        GameState state = engine.getGameState();
        for (int[] dir : ADJACENT) {
            int tr = row + dir[0];
            int tc = col + dir[1];
            if (!Square.insideBoard(tr, tc)) continue;
            Piece target = state.board[tr][tc];
            if (target == null || target.isWhite() != this.white) {
                Move m = new Move(row, col, tr, tc);
                if (target != null) m.isCapture = true;
                moves.add(m);
            }
        }
        return moves;
    }
}
