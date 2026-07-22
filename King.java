 

import java.util.*;
public class King extends Piece
{
    private boolean hasMoved;
    public King(boolean white)
    {
        super(white, "King", 20);
        this.hasMoved = false;
    }

    public boolean hasMoved() 
    { 
        return hasMoved;
    }

    public void setHasMoved(boolean moved)
    { 
        this.hasMoved = moved;
    }
    private static final int[][] ADJACENT = {
            {-1, -1}, {-1, 0}, {-1, 1},
            {0, -1},           {0, 1},
            {1, -1},  {1, 0},  {1, 1}
        };
    @Override
    public List<Move> generateLegalMoves(int row, int col, ChessEngine engine) {
        List<Move> moves = new ArrayList<>();
        GameState state = engine.getGameState();
        // Standard adjacent moves
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
        // Castling disabled for Roguelike Chess960 variant
        return moves; // REMOVED: applyGlobalModifiers call
    }

    @Override
    public King clone() {
        King k = (King) super.clone();
        k.hasMoved = this.hasMoved;
        return k;
    }
}
