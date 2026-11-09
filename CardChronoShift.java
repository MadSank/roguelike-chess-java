import java.util.*;

/**
 * BONUS GLOBAL CARD: Every 5 moves (counting both sides), all non-King pieces shift one
 * square to the right, wrapping from the h-file to the a-file. A piece that would land on
 * a King is destroyed. Kings never move.
 * Cost: 25 gold
 */
public class CardChronoShift extends Card {
    private static final long serialVersionUID = 2L;
    static final String MOVES = "chronoShift.moves";
    static final int PERIOD = 5;

    public CardChronoShift() {
        super("Chrono Shift",
              "Every 5 moves, all non-King pieces shift 1 square right. A piece that would land on a King is destroyed.",
              25, true);
    }

    @Override
    public void onMovePerformed(GameState state, Move move) {
        if (state.incrementCounter(MOVES) % PERIOD != 0) return;

        Piece[][] next = new Piece[8][8];
        int[] newIndex = new int[64];
        Arrays.fill(newIndex, -1);

        // Kings stay put.
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (state.board[r][c] instanceof King) {
                    next[r][c] = state.board[r][c];
                    newIndex[r * 8 + c] = r * 8 + c;
                }
            }
        }
        // Everything else moves one file right. All pieces move at once, so the only
        // square a piece can collide with is one held by a King.
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p == null || p instanceof King) continue;
                int nc = (c + 1) % 8;
                if (next[r][nc] == null) {
                    next[r][nc] = p;
                    newIndex[r * 8 + c] = r * 8 + nc;
                }
                // else: lands on a King and is destroyed
            }
        }
        for (int r = 0; r < 8; r++) {
            state.board[r] = next[r];
        }

        // Card state that is tied to squares moves with its piece.
        Map<Integer, Integer> stuns = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : state.stunnedPieces.entrySet()) {
            int to = newIndex[e.getKey()];
            if (to >= 0) stuns.put(to, e.getValue());
        }
        state.stunnedPieces = stuns;
        if (state.vaultingPieceSquare >= 0) {
            state.vaultingPieceSquare = newIndex[state.vaultingPieceSquare];
        }
    }
}
