
import java.util.*;

/**
 * LOCAL CARD: One random major piece (Rook/Queen) can jump over exactly
 * one piece in its path, landing on the square immediately after.
 * Cost: 20 gold
 */
public class CardVaultingMajors extends Card {
    private static final long serialVersionUID = 1L;

    private static final int[][] STRAIGHT_DIRS = {
        {-1, 0}, {1, 0}, {0, -1}, {0, 1}
    };
    private static final int[][] ALL_DIRS = {
        {-1, -1}, {-1, 0}, {-1, 1},
        {0, -1},           {0, 1},
        {1, -1},  {1, 0},  {1, 1}
    };

    public CardVaultingMajors() {
        super("Vaulting Majors",
              "One random Rook/Queen can jump over exactly one piece in its path.",
              20, false);
    }

    @Override
    public void onGameStart(GameState state) {
        // Pick a random friendly Rook or Queen to be the "vaulter"
        List<Integer> candidates = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p != null && p.isWhite() && (p instanceof Rook || p instanceof Queen)) {
                    candidates.add(r * 8 + c);
                }
            }
        }
        if (!candidates.isEmpty()) {
            int idx = (state.roundNumber * 7 + 3) % candidates.size();
            state.vaultingPieceSquare = candidates.get(idx);
        } else {
            state.vaultingPieceSquare = -1;
        }
    }

    @Override
    public List<Move> modifyMoveGeneration(List<Move> baseMoves, Piece piece,
            int row, int col, ChessEngine engine, GameState state) {
        if (!appliesTo(piece.isWhite())) return baseMoves;
        if (state.vaultingPieceSquare < 0) return baseMoves;
        if (row * 8 + col != state.vaultingPieceSquare) return baseMoves;
        if (!(piece instanceof Rook || piece instanceof Queen)) return baseMoves;

        int[][] dirs = (piece instanceof Rook) ? STRAIGHT_DIRS : ALL_DIRS;

        for (int[] dir : dirs) {
            int r = row + dir[0];
            int c = col + dir[1];
            boolean jumped = false;

            while (Square.insideBoard(r, c)) {
                Piece target = state.board[r][c];
                if (target != null) {
                    if (!jumped) {
                        // Jump over this piece
                        jumped = true;
                        r += dir[0];
                        c += dir[1];
                        if (!Square.insideBoard(r, c)) break;
                        Piece landing = state.board[r][c];
                        if (landing == null) {
                            Move vault = new Move(row, col, r, c);
                            baseMoves.add(vault);
                        } else if (landing.isWhite() != piece.isWhite()) {
                            Move vault = new Move(row, col, r, c);
                            vault.isCapture = true;
                            baseMoves.add(vault);
                        }
                        break; // must stop immediately after jump
                    } else {
                        break;
                    }
                }
                r += dir[0];
                c += dir[1];
            }
        }
        return baseMoves;
    }

    @Override
    public void onMovePerformed(GameState state, Move move) {
        // Track the vaulting piece as it moves
        if (state.vaultingPieceSquare >= 0) {
            int fromIdx = move.fromRow * 8 + move.fromCol;
            if (fromIdx == state.vaultingPieceSquare) {
                state.vaultingPieceSquare = move.toRow * 8 + move.toCol;
            }
        }
    }
}
