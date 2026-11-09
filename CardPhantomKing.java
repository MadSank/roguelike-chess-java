import java.util.*;

/**
 * BONUS LOCAL CARD: Once per game, when your King is put in check,
 * it teleports to a safe empty square.
 * Cost: 40 gold
 */
public class CardPhantomKing extends Card {
    private static final long serialVersionUID = 2L;
    static final String USED = "phantomKing.used";

    public CardPhantomKing() {
        super("Phantom King",
              "Once per game: when your King is checked, it teleports to a random safe square.",
              40, false);
    }

    @Override
    public boolean reactsToCheck() {
        return true;
    }

    @Override
    public boolean onKingInCheck(GameState state, boolean whiteKing, ChessEngine engine) {
        if (!whiteKing) return false; // only the player's king
        if (state.getCounter(USED) > 0) return false;

        int kr = -1, kc = -1;
        for (int r = 0; r < 8 && kr == -1; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p instanceof King && p.isWhite()) {
                    kr = r;
                    kc = c;
                    break;
                }
            }
        }
        if (kr == -1) return false;

        // Safe squares: empty and not attacked once the king stands there.
        Piece king = state.board[kr][kc];
        List<int[]> safeSquares = new ArrayList<>();
        state.board[kr][kc] = null;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (state.board[r][c] != null || (r == kr && c == kc)) continue;
                state.board[r][c] = king;
                boolean safe = !engine.isSquareAttacked(state, r, c, false);
                state.board[r][c] = null;
                if (safe) safeSquares.add(new int[]{r, c});
            }
        }
        state.board[kr][kc] = king;
        if (safeSquares.isEmpty()) return false;

        // Deterministic pick so the same position always gives the same result.
        int[] dest = safeSquares.get((state.moveHistory.size() * 13 + 7) % safeSquares.size());
        state.board[dest[0]][dest[1]] = king;
        state.board[kr][kc] = null;
        state.setCounter(USED, 1);
        return true;
    }
}
