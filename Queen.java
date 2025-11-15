package ashes;

import java.util.*;

public class Queen extends Piece {

    public Queen(boolean white) {
        super(white, "Queen", 9);
    }

    private static final int[][] ALL_DIRECTIONS = {
            {-1, -1}, {-1, 0}, {-1, 1},
            {0, -1},           {0, 1},
            {1, -1},  {1, 0},  {1, 1}
    };

    @Override
    public List<Move> generateLegalMoves(int row, int col, ChessEngine engine) {
        List<Move> moves = new ArrayList<>();
        GameState state = engine.getGameState();

        for (int[] dir : ALL_DIRECTIONS) {
            int r = row + dir[0];
            int c = col + dir[1];

            for (int step = 1; step <= 8; step++, r += dir[0], c += dir[1]) {
                if (!Square.insideBoard(r, c)) break;

                Piece target = state.board[r][c];

                if (target == null) {
                    moves.add(new Move(row, col, r, c));
                } else {
                    if (target.isWhite() != this.white) {
                        Move m = new Move(row, col, r, c);
                        m.isCapture = true;
                        moves.add(m);
                    }
                    break;
                }
            }
        }

        return moves;
    }
}