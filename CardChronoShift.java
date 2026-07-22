
/**
 * BONUS GLOBAL CARD: Every 5 moves, all pieces on the board shift one
 * square to the right (wrapping around). Pieces that would collide are
 * destroyed. Kings are exempt from shifting.
 * Cost: 25 gold
 */
public class CardChronoShift extends Card {
    private static final long serialVersionUID = 1L;
    private int moveCounter = 0;

    public CardChronoShift() {
        super("Chrono Shift",
              "Every 5 moves, all non-King pieces shift 1 square right. Collisions destroy both.",
              25, true);
    }

    @Override
    public void onMovePerformed(GameState state, Move move) {
        moveCounter++;
        if (moveCounter % 5 != 0) return;

        // Execute the shift: scan right-to-left to avoid overwrites
        Piece[][] newBoard = new Piece[8][8];
        boolean[][] occupied = new boolean[8][8];

        // First pass: place kings (they don't shift)
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p instanceof King) {
                    newBoard[r][c] = p;
                    occupied[r][c] = true;
                }
            }
        }

        // Second pass: shift non-kings to the right
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p == null || p instanceof King) continue;

                int newCol = (c + 1) % 8; // wrap around

                if (occupied[r][newCol]) {
                    // Collision — destroy the incoming piece AND the existing one
                    // (unless existing is a King)
                    if (!(newBoard[r][newCol] instanceof King)) {
                        newBoard[r][newCol] = null;
                        occupied[r][newCol] = false;
                    }
                    // incoming piece is destroyed regardless
                } else {
                    newBoard[r][newCol] = p;
                    occupied[r][newCol] = true;
                }
            }
        }

        // Apply
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                state.board[r][c] = newBoard[r][c];
            }
        }
        System.out.println("CHRONO SHIFT: Board shifted right!");
    }
}
