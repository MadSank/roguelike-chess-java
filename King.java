package ashes;

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
        // Castling - REMOVED: Loose Castling power-up logic
        addCastlingMoves(moves, row, col, state, engine);
        return moves; // REMOVED: applyGlobalModifiers call
    }

    private void addCastlingMoves(List<Move> moves, int row, int col, GameState state, ChessEngine engine)
    {
        boolean canCastle = !hasMoved;
        // REMOVED: Loose Castling power-up logic
        if (!canCastle) return;
        if (engine.isKingInCheck(state, this.white)) return;
        int homeRow = white ? 7 : 0;
        if (row != homeRow) return;
        if (canCastleKingside(state, homeRow, engine)) {
            Move castle = new Move(row, col, row, 6);
            castle.isCastle = true;
            moves.add(castle);
        }
        if (canCastleQueenside(state, homeRow, engine)) {
            Move castle = new Move(row, col, row, 2);
            castle.isCastle = true;
            moves.add(castle);
        }
    }

    private boolean canCastleKingside(GameState state, int row, ChessEngine engine) {
        Piece rook = state.board[row][7];
        if (!(rook instanceof Rook) || rook.isWhite() != this.white) return false;
        if (white && state.whiteKingsideRookMoved) return false;
        if (!white && state.blackKingsideRookMoved) return false;
        if (state.board[row][5] != null || state.board[row][6] != null) return false;
        if (engine.isSquareAttacked(state, row, 5, !this.white)) return false;
        if (engine.isSquareAttacked(state, row, 6, !this.white)) return false;
        return true;
    }

    private boolean canCastleQueenside(GameState state, int row, ChessEngine engine) {
        Piece rook = state.board[row][0];
        if (!(rook instanceof Rook) || rook.isWhite() != this.white) return false;
        if (white && state.whiteQueensideRookMoved) return false;
        if (!white && state.blackQueensideRookMoved) return false;
        if (state.board[row][1] != null ||
        state.board[row][2] != null ||
        state.board[row][3] != null) return false;
        if (engine.isSquareAttacked(state, row, 3, !this.white)) return false;
        if (engine.isSquareAttacked(state, row, 2, !this.white)) return false;
        return true;
    }

    @Override
    public King clone() {
        King k = (King) super.clone();
        k.hasMoved = this.hasMoved;
        return k;
    }
}