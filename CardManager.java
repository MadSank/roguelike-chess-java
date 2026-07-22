
import java.io.Serializable;
import java.util.*;

/**
 * Tracks active cards and exposes query methods for the engine.
 * All hot-path methods are designed for zero allocation and minimal branching.
 */
public class CardManager implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<Card> activeCards = new ArrayList<>();
    private transient List<Card> cardPool; // lazily initialized

    // ─── ACTIVE CARD MANAGEMENT ──────────────────────────────────

    public List<Card> getActiveCards() {
        return Collections.unmodifiableList(activeCards);
    }

    public void addCard(Card card) {
        activeCards.add(card);
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

    public void resetForNewRun() {
        activeCards.clear();
    }

    // ─── HOT-PATH ENGINE QUERIES ────────────────────────────────

    /**
     * Apply all active card move modifiers to a piece's base moves.
     * Called inside move generation (HOT PATH).
     */
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

    /**
     * Check if piece at (row,col) is immune to capture by the given move.
     */
    public boolean isCaptureImmune(GameState state, int row, int col, Move move) {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            if (activeCards.get(i).isCaptureImmune(state, row, col, move)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if any card grants extra attack capability.
     */
    public boolean canAttackSquare(Piece piece, int fr, int fc, int tr, int tc, GameState state) {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            Card card = activeCards.get(i);
            if (card.appliesTo(piece.isWhite()) &&
                card.canPieceAttackSquare(piece, fr, fc, tr, tc, state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolve capture side-effects (gold, destruction, spawning).
     */
    public void resolveCapture(GameState state, Move move, Piece captured, Piece capturer) {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            activeCards.get(i).onCapture(state, move, captured, capturer);
        }
    }

    /**
     * Fire onMovePerformed for all active cards.
     */
    public void onMovePerformed(GameState state, Move move) {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            activeCards.get(i).onMovePerformed(state, move);
        }
    }

    /**
     * Fire onGameStart for all active cards.
     */
    public void onGameStart(GameState state) {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            activeCards.get(i).onGameStart(state);
        }
    }

    /**
     * Fire onKingInCheck — returns true if any card handled it.
     */
    public boolean onKingInCheck(GameState state, boolean whiteKing, ChessEngine engine) {
        for (int i = 0, n = activeCards.size(); i < n; i++) {
            if (activeCards.get(i).onKingInCheck(state, whiteKing, engine)) {
                return true;
            }
        }
        return false;
    }

    // ─── ANTI-CHECKMATE SAFEGUARD ───────────────────────────────

    /**
     * Validates that applying a card won't cause immediate checkmate.
     * Clones state, applies card's onGameStart, checks for legal moves.
     * Returns true if safe.
     */
    public boolean validateCardApplication(Card card, GameState state) {
        GameState testState = state.copy();
        if (testState.cardManager == null) {
            testState.cardManager = this.copy();
        }
        card.onGameStart(testState);

        ChessEngine testEngine = new ChessEngine(testState);
        List<Move> whiteMoves = testEngine.getAllLegalMoves(true);
        List<Move> blackMoves = testEngine.getAllLegalMoves(false);

        if (whiteMoves.isEmpty() && testEngine.isKingInCheck(testState, true)) {
            System.out.println("SAFEGUARD: Card " + card.getName() + " causes white checkmate — BLOCKED");
            return false;
        }
        if (blackMoves.isEmpty() && testEngine.isKingInCheck(testState, false)) {
            System.out.println("SAFEGUARD: Card " + card.getName() + " causes black checkmate — BLOCKED");
            return false;
        }
        return true;
    }

    // ─── SHOP OFFERING ──────────────────────────────────────────

    /**
     * Generate 3 face-up cards for the shop (premium priced).
     * Cards already owned are excluded.
     */
    public List<Card> generateFaceUpOffering(int roundNumber, Random rng) {
        List<Card> pool = getCardPool();
        List<Card> available = new ArrayList<>();
        for (Card c : pool) {
            if (!activeCards.contains(c)) {
                available.add(c);
            }
        }
        Collections.shuffle(available, rng);
        int count = Math.min(3, available.size());
        return new ArrayList<>(available.subList(0, count));
    }

    /**
     * Generate 3 mystery pack cards (one of which the player can pick).
     * Discounted cost. Cards already owned are excluded.
     */
    public List<Card> generateMysteryPack(int roundNumber, Random rng) {
        List<Card> pool = getCardPool();
        List<Card> available = new ArrayList<>();
        for (Card c : pool) {
            if (!activeCards.contains(c)) {
                available.add(c);
            }
        }
        Collections.shuffle(available, rng);
        // Exclude cards that are in the current face-up offering by shifting further
        int count = Math.min(3, available.size());
        // Take from the end of the shuffled list to avoid overlap with face-up
        int start = Math.max(0, available.size() - count);
        return new ArrayList<>(available.subList(start, available.size()));
    }

    /**
     * Mystery pack cost (discounted).
     */
    public int getMysteryPackCost(int roundNumber) {
        return 8 + roundNumber * 2;
    }

    private List<Card> getCardPool() {
        if (cardPool == null) {
            cardPool = new ArrayList<>();
            // Local cards
            cardPool.add(new CardCavalryCharge());
            cardPool.add(new CardPawnPhalanx());
            cardPool.add(new CardSniperBishops());
            cardPool.add(new CardGoldenEdge());
            cardPool.add(new CardNecromancerGambit());
            cardPool.add(new CardVaultingMajors());
            cardPool.add(new CardPhantomKing());
            cardPool.add(new CardSoulHarvest());
            // Global cards
            cardPool.add(new CardGlassCannons());
            cardPool.add(new CardTrenchWarfare());
            cardPool.add(new CardBloodMoon());
            cardPool.add(new CardMinefield());
            cardPool.add(new CardHeavyArmor());
            cardPool.add(new CardChronoShift());
        }
        return cardPool;
    }

    public CardManager copy() {
        CardManager cm = new CardManager();
        for (Card c : activeCards) {
            cm.activeCards.add(c); // cards are stateless singletons mostly
        }
        return cm;
    }
}
