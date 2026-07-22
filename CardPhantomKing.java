
import java.util.*;

/**
 * BONUS LOCAL CARD: Once per game, when your King is in check,
 * the King teleports to a random safe square.
 * Cost: 40 gold
 */
public class CardPhantomKing extends Card {
    private static final long serialVersionUID = 1L;
    private boolean phantomUsed = false;

    public CardPhantomKing() {
        super("Phantom King",
              "Once per game: when your King is checked, it teleports to a random safe square.",
              40, false);
    }

    @Override
    public boolean onKingInCheck(GameState state, boolean whiteKing, ChessEngine engine) {
        if (!whiteKing) return false; // only player's king
        if (phantomUsed) return false;

        // Find king position
        int kr = -1, kc = -1;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p instanceof King && p.isWhite() == whiteKing) {
                    kr = r; kc = c; break;
                }
            }
            if (kr != -1) break;
        }
        if (kr == -1) return false;

        // Find all safe squares (empty, not attacked by enemy)
        List<int[]> safeSquares = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (state.board[r][c] != null) continue;
                if (r == kr && c == kc) continue;
                // Temporarily place king here to check safety
                state.board[r][c] = state.board[kr][kc];
                state.board[kr][kc] = null;
                boolean safe = !engine.isSquareAttacked(state, r, c, !whiteKing);
                state.board[kr][kc] = state.board[r][c];
                state.board[r][c] = null;
                if (safe) {
                    safeSquares.add(new int[]{r, c});
                }
            }
        }

        if (!safeSquares.isEmpty()) {
            // Deterministic pick
            int idx = (state.moveHistory.size() * 13 + 7) % safeSquares.size();
            int[] dest = safeSquares.get(idx);
            Piece king = state.board[kr][kc];
            state.board[dest[0]][dest[1]] = king;
            state.board[kr][kc] = null;
            phantomUsed = true;
            System.out.println("PHANTOM KING: Teleported from (" + kr + "," + kc +
                ") to (" + dest[0] + "," + dest[1] + ")!");
            return true;
        }
        return false;
    }
}
