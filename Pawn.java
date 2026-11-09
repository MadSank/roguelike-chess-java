import java.util.*;

public class Pawn extends Piece {
    /** Promotion choices, queen first so it is the default pick. */
    private static final String[] PROMOTIONS = {"Q", "R", "B", "N"};

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
            addPawnMove(moves, row, col, nextRow, col, false);

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
                addPawnMove(moves, row, col, cr, cc, true);
            }
        }

        if (state.enPassantTarget != null) {
            int epRow = state.enPassantTarget.row;
            int epCol = state.enPassantTarget.col;
            Piece victim = Square.insideBoard(row, epCol) ? state.board[row][epCol] : null;
            if (Math.abs(epCol - col) == 1 && epRow == row + dir
                    && victim instanceof Pawn && victim.isWhite() != this.white) {
                Move m = new Move(row, col, epRow, epCol);
                m.isEnPassant = true;
                m.isCapture = true;
                moves.add(m);
            }
        }

        return moves;
    }

    /** Adds a pawn move, expanded into the four promotion choices on the last rank. */
    private void addPawnMove(List<Move> moves, int fromRow, int fromCol, int toRow, int toCol, boolean capture) {
        boolean lastRank = (white && toRow == 0) || (!white && toRow == 7);
        if (!lastRank) {
            Move m = new Move(fromRow, fromCol, toRow, toCol);
            m.isCapture = capture;
            moves.add(m);
            return;
        }
        for (String type : PROMOTIONS) {
            Move m = Move.promotion(fromRow, fromCol, toRow, toCol, type);
            m.isCapture = capture;
            moves.add(m);
        }
    }
}
