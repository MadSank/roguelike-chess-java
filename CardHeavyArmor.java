
import java.util.*;

/**
 * GLOBAL CARD: Capturing requires two consecutive attacks.
 * First attack just "stuns" the piece for one turn (it can't move).
 * Second attack on a stunned piece captures normally.
 * Cost: 25 gold
 */
public class CardHeavyArmor extends Card {
    private static final long serialVersionUID = 1L;

    public CardHeavyArmor() {
        super("Heavy Armor",
              "First attack stuns the target for 1 turn. Second attack captures normally.",
              25, true);
    }

    @Override
    public boolean isCaptureImmune(GameState state, int row, int col, Move attackMove) {
        if (state.stunnedPieces == null) return false;
        int key = row * 8 + col;
        // If already stunned, allow capture
        if (state.stunnedPieces.containsKey(key)) {
            return false;
        }
        // Not stunned: block capture and stun instead
        Piece target = state.board[row][col];
        if (target == null) return false;
        if (target instanceof King) return false; // can't stun kings

        state.stunnedPieces.put(key, 1); // stunned for 1 turn
        return true; // immune this time
    }

    @Override
    public List<Move> modifyMoveGeneration(List<Move> baseMoves, Piece piece,
            int row, int col, ChessEngine engine, GameState state) {
        if (state.stunnedPieces == null) return baseMoves;
        int key = row * 8 + col;
        if (state.stunnedPieces.containsKey(key)) {
            // Stunned piece can't move
            baseMoves.clear();
            return baseMoves;
        }
        return baseMoves;
    }

    @Override
    public void onMovePerformed(GameState state, Move move) {
        if (state.stunnedPieces == null) return;
        // Decrement stun counters and remove expired stuns
        Iterator<Map.Entry<Integer, Integer>> it = state.stunnedPieces.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                it.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }
}
