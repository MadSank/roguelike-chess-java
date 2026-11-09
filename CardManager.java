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

    /** Every card in the game. */
    public static List<Card> allCards() {
        List<Card> pool = new ArrayList<>();
        // Local cards (help only the player)
        pool.add(new CardCavalryCharge());
        pool.add(new CardPawnPhalanx());
        pool.add(new CardSniperBishops());
        pool.add(new CardGoldenEdge());
        pool.add(new CardNecromancerGambit());
        pool.add(new CardVaultingMajors());
        pool.add(new CardPhantomKing());
        pool.add(new CardSoulHarvest());
        // Global cards (change the rules for both sides)
        pool.add(new CardGlassCannons());
        pool.add(new CardTrenchWarfare());
        pool.add(new CardBloodMoon());
        pool.add(new CardMinefield());
        pool.add(new CardHeavyArmor());
        pool.add(new CardChronoShift());
        return pool;
    }

    /** Cards not owned yet, i.e. the ones the shop may offer. */
    public List<Card> availableCards() {
        List<Card> available = new ArrayList<>();
        for (Card c : allCards()) {
            if (!activeCards.contains(c)) available.add(c);
        }
        return available;
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

    public void onGameStart(GameState state) {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            activeCards.get(i).onGameStart(state);
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
