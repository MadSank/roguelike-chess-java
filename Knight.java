package ashes;
import java.util.*;

public class Knight extends Piece {
    public Knight(boolean white) {
        super(white, "Knight", 3);
    }
    private static final int[][] OFFSETS = {
            {-2, -1}, {-2, 1},
            {-1, -2}, {-1, 2},
            {1, -2}, {1, 2},
            {2, -1}, {2, 1}
        };
    @Override
    public List<Move> generateLegalMoves(int row, int col, ChessEngine engine) {
        List<Move> moves = new ArrayList<>();
        GameState state = engine.getGameState();
        
        for (int[] off : OFFSETS) {
            int tr = row + off[0];
            int tc = col + off[1];
            if (!Square.insideBoard(tr, tc)) 
                continue;
            
            Piece target = state.board[tr][tc];
            if (target == null || target.isWhite() != this.white) 
            {
                Move m = new Move(row, col, tr, tc);
                if (target != null) 
                    m.isCapture = true;
                moves.add(m);
            }
        }
        
        return moves;
    }
}