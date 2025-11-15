package ashes;

import java.util.*;

public class Pawn extends Piece {
    public Pawn(boolean white) {
        super(white, "Pawn", 1);
    }

    @Override
    public List<Move> generateLegalMoves(int row, int col, ChessEngine engine) {
        List<Move> moves = new ArrayList<>();
        GameState state = engine.getGameState();
        int dir = white ? -1 : 1;

        int nextRow = row + dir;
        if (Square.insideBoard(nextRow, col) && state.board[nextRow][col] == null) {
            addPawnAdvance(moves, row, col, nextRow, col);

            if ((white && row == 6) || (!white && row == 1)) {
                int twoRow = row + 2 * dir;
                if (Square.insideBoard(twoRow, col) && state.board[twoRow][col] == null) {
                    moves.add(new Move(row, col, twoRow, col));
                }
            }
        }

        for (int dc : new int[]{-1, 1}) {
            int cr = row + dir;
            int cc = col + dc;
            if (!Square.insideBoard(cr, cc)) continue;
            Piece target = state.board[cr][cc];
            if (target != null && target.isWhite() != this.white) {
                Move m = new Move(row, col, cr, cc);
                m.isCapture = true;
                addPawnAdvance(moves, row, col, cr, cc, m);
            }
        }

        if (state.enPassantTarget != null) {
            int epRow = state.enPassantTarget.row;
            int epCol = state.enPassantTarget.col;
            if (Math.abs(epCol - col) == 1 && epRow == row + dir) {
                Move m = new Move(row, col, epRow, epCol);
                m.isEnPassant = true;
                m.isCapture = true;
                moves.add(m);
            }
        }

        return moves;
    }

    private void addPawnAdvance(List<Move> moves, int fromRow, int fromCol, int toRow, int toCol) {
        Move m = new Move(fromRow, fromCol, toRow, toCol);
        if ((white && toRow == 0) || (!white && toRow == 7)) {
            m.isPromotion = true;
            m.promotionType = "Q";
        }
        moves.add(m);
    }

    private void addPawnAdvance(List<Move> moves, int fromRow, int fromCol, int toRow, int toCol, Move m) {
        if ((white && toRow == 0) || (!white && toRow == 7)) {
            m.isPromotion = true;
            m.promotionType = "Q";
        }
        moves.add(m);
    }
}