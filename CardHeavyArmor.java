import java.util.*;

/**
 * GLOBAL CARD: Capturing takes two hits.
 * The first attack on a piece stuns it instead of capturing: the attacker stays where it is
 * and the target can't move on its next turn. Attacking a stunned piece captures it normally.
 * Kings are never stunned.
 * Cost: 25 gold
 */
public class CardHeavyArmor extends Card {
    private static final long serialVersionUID = 2L;

    /**
     * Stun length in plies. Set when the hit lands and counted down after every move:
     * the hit's own move (3 -> 2), the victim's turn, during which it can't move (2 -> 1),
     * then the attacker's next turn, when a second attack captures (1 -> 0).
     */
    static final int STUN_PLIES = 3;

    public CardHeavyArmor() {
        super("Heavy Armor",
              "First attack stuns the target for a turn instead of capturing. Attacking a stunned piece captures it.",
              25, true);
    }

    @Override
    public boolean interceptCapture(GameState state, Move move, int targetRow, int targetCol) {
        Piece target = state.board[targetRow][targetCol];
        if (target == null || target instanceof King) return false;
        int key = targetRow * 8 + targetCol;
        if (state.stunnedPieces.containsKey(key)) {
            // Second hit: normal capture. Clear the stun so the capturer doesn't inherit it.
            state.stunnedPieces.remove(key);
            return false;
        }
        state.stunnedPieces.put(key, STUN_PLIES);
        return true;
    }

    @Override
    public List<Move> modifyMoveGeneration(List<Move> baseMoves, Piece piece,
            int row, int col, ChessEngine engine, GameState state) {
        if (state.stunnedPieces.containsKey(row * 8 + col)) {
            baseMoves.clear(); // stunned pieces can't move
        }
        return baseMoves;
    }

    @Override
    public void onMovePerformed(GameState state, Move move) {
        Iterator<Map.Entry<Integer, Integer>> it = state.stunnedPieces.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            int idx = entry.getKey();
            int remaining = entry.getValue() - 1;
            // Drop expired stuns, and stuns on squares whose piece is gone.
            if (remaining <= 0 || state.board[idx / 8][idx % 8] == null) {
                it.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }
}
