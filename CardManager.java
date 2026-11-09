import java.io.Serializable;
import java.util.*;

/**
 * Holds the active cards and exposes the hooks the engine calls.
 * Cards are stateless, so a CardManager copy can share the same card instances.
 */
public class CardManager implements Serializable {
    private static final long serialVersionUID = 2L;

    private final List<Card> activeCards = new ArrayList<>();

    // ─── ACTIVE CARD MANAGEMENT ──────────────────────────────────

    public List<Card> getActiveCards() {
        return Collections.unmodifiableList(activeCards);
    }

    public void addCard(Card card) {
        activeCards.add(card);
    }

    public boolean removeCard(Card card) {
        return activeCards.remove(card);
    }

    public boolean hasCard(Class<? extends Card> type) {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            if (type.isInstance(activeCards.get(i))) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public <T extends Card> T getCard(Class<T> type) {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            Card c = activeCards.get(i);
            if (type.isInstance(c)) return (T) c;
        }
        return null;
    }

    public CardManager copy() {
        CardManager cm = new CardManager();
        cm.activeCards.addAll(activeCards);
        return cm;
    }

    /** True if a card with the same name is already active. */
    public boolean isActive(Card card) {
        return activeCards.contains(card);
    }

    /** Plays a card: it becomes active and its onPlay effect fires. */
    public void play(Card card, GameState state) {
        activeCards.add(card);
        card.onPlay(state);
    }

    // ─── ENGINE HOOKS (hot path) ────────────────────────────────

    /** Apply all active card move modifiers to a piece's base moves. */
    public List<Move> applyMoveModifiers(List<Move> baseMoves, Piece piece,
            int row, int col, ChessEngine engine, GameState state) {
        List<Move> moves = baseMoves;
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            Card card = activeCards.get(i);
            if (card.appliesTo(piece.isWhite())) {
                moves = card.modifyMoveGeneration(moves, piece, row, col, engine, state);
            }
        }
        return moves;
    }

    /** True if the piece at (row,col) is immune to capture by the given move. */
    public boolean isCaptureImmune(GameState state, int row, int col, Move move) {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            if (activeCards.get(i).isCaptureImmune(state, row, col, move)) return true;
        }
        return false;
    }

    /** True if any card grants the piece an extra attack on (tr,tc). */
    public boolean canAttackSquare(Piece piece, int fr, int fc, int tr, int tc, GameState state) {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            Card card = activeCards.get(i);
            if (card.appliesTo(piece.isWhite()) && card.canPieceAttackSquare(piece, fr, fc, tr, tc, state)) {
                return true;
            }
        }
        return false;
    }

    /** True if some card cancels this capture (see Card.interceptCapture). */
    public boolean interceptCapture(GameState state, Move move, int targetRow, int targetCol) {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            if (activeCards.get(i).interceptCapture(state, move, targetRow, targetCol)) return true;
        }
        return false;
    }

    /** True if some card removes this attack for check detection (see Card.blocksAttack). */
    public boolean blocksAttack(Piece piece, int fr, int fc, int tr, int tc, GameState state) {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            if (activeCards.get(i).blocksAttack(piece, fr, fc, tr, tc, state)) return true;
        }
        return false;
    }

    /** True if any active card reacts to a king being put in check. */
    public boolean reactsToCheck() {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            if (activeCards.get(i).reactsToCheck()) return true;
        }
        return false;
    }

    /** Capture side-effects (gold, destruction, spawning). */
    public void resolveCapture(GameState state, Move move, Piece captured, Piece capturer) {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            activeCards.get(i).onCapture(state, move, captured, capturer);
        }
    }

    public void onMovePerformed(GameState state, Move move) {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            activeCards.get(i).onMovePerformed(state, move);
        }
    }

    /** Returns true if any card handled the check (e.g. Phantom King teleport). */
    public boolean onKingInCheck(GameState state, boolean whiteKing, ChessEngine engine) {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            if (activeCards.get(i).onKingInCheck(state, whiteKing, engine)) return true;
        }
        return false;
    }
}
