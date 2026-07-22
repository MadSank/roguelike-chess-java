
import java.util.*;

/**
 * GLOBAL CARD: At the start of the game, 4 random empty squares are
 * permanently trapped. Any non-King piece landing on them is destroyed.
 * Cost: 20 gold
 */
public class CardMinefield extends Card {
    private static final long serialVersionUID = 1L;

    public CardMinefield() {
        super("Minefield",
              "4 random empty squares are trapped. Any non-King piece landing on them is destroyed.",
              20, true);
    }

    @Override
    public void onGameStart(GameState state) {
        if (state.minefieldSquares == null) {
            state.minefieldSquares = new HashSet<>();
        }
        state.minefieldSquares.clear();

        // Collect all empty squares (rows 2-5 for fairness — not on back ranks)
        List<Integer> emptyCandidates = new ArrayList<>();
        for (int r = 2; r <= 5; r++) {
            for (int c = 0; c < 8; c++) {
                if (state.board[r][c] == null) {
                    emptyCandidates.add(r * 8 + c);
                }
            }
        }

        // Deterministic shuffle based on round number
        Random rng = new Random(state.roundNumber * 42L + 7);
        Collections.shuffle(emptyCandidates, rng);

        int count = Math.min(4, emptyCandidates.size());
        for (int i = 0; i < count; i++) {
            state.minefieldSquares.add(emptyCandidates.get(i));
        }
        System.out.println("MINEFIELD: " + count + " squares trapped!");
    }

    @Override
    public void onMovePerformed(GameState state, Move move) {
        if (state.minefieldSquares == null) return;
        int landingIdx = move.toRow * 8 + move.toCol;
        if (state.minefieldSquares.contains(landingIdx)) {
            Piece landed = state.board[move.toRow][move.toCol];
            if (landed != null && !(landed instanceof King)) {
                state.board[move.toRow][move.toCol] = null;
                System.out.println("MINEFIELD: Piece destroyed at (" +
                    move.toRow + "," + move.toCol + ")!");
            }
        }
    }
}
