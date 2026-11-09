
import java.io.Serializable;
import java.util.List;

/**
 * Abstract base class for all roguelike cards.
 * Cards use hook methods — override only the hooks you need.
 * All hooks have default no-op implementations for minimal overhead.
 *
 * Cards must not keep mutable fields: card instances are shared between the real game and
 * the AI's simulated positions. Keep per-game state in GameState (e.g. cardCounters).
 */
public abstract class Card implements Serializable {
    private static final long serialVersionUID = 1L;

    protected final String name;
    protected final String description;
    protected final int cost;
    protected final boolean global; // true = affects both sides

    public Card(String name, String description, int cost, boolean global) {
        this.name = name;
        this.description = description;
        this.cost = cost;
        this.global = global;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getCost() { return cost; }
    public boolean isGlobal() { return global; }
    public CardCatalog.Rarity getRarity() { return CardCatalog.rarityOf(this); }

    // ─── HOOK METHODS (override as needed) ───────────────────────

    /**
     * Modify/extend move generation for a piece.
     * Called per-piece during pseudo-legal move generation (HOT PATH).
     * Return the modified list. Default: return unmodified.
     */
    public List<Move> modifyMoveGeneration(List<Move> baseMoves, Piece piece,
            int row, int col, ChessEngine engine, GameState state) {
        return baseMoves;
    }

    /**
     * Called after a capture is resolved on the board.
     * Use for side-effects (gold gain, piece destruction, spawning).
     */
    public void onCapture(GameState state, Move move, Piece captured, Piece capturer) {
        // no-op
    }

    /**
     * Called once when the player plays the card from their hand during a round.
     * From then on the card is active until the round ends. Use for one-time effects
     * at the moment of play (Blood Moon transforms pawns, Minefield lays mines).
     */
    public void onPlay(GameState state) {
        // no-op
    }

    /**
     * Returns true if the piece at (row, col) is immune to capture by the given move.
     * Used for Pawn Phalanx, Heavy Armor stun.
     */
    public boolean isCaptureImmune(GameState state, int row, int col, Move attackMove) {
        return false;
    }

    /**
     * Extend attack detection for check/pin calculations.
     * Returns true if this card grants piece an attack on (tr,tc).
     * Default: false (no extra attacks).
     */
    public boolean canPieceAttackSquare(Piece piece, int fromRow, int fromCol,
            int toRow, int toCol, GameState state) {
        return false;
    }

    /**
     * Stops an attack from capturing ordinary chess-style (e.g. Heavy Armor turns the first
     * hit into a stun). Called before the board changes. Return true to cancel the capture:
     * the attacker then stays on its square and the target stays on the board.
     * (targetRow, targetCol) is where the attacked piece stands.
     */
    public boolean interceptCapture(GameState state, Move move, int targetRow, int targetCol) {
        return false;
    }

    /**
     * Removes an attack the piece would normally have, for check detection
     * (e.g. Trench Warfare: no attacking backwards). Default: never blocks.
     */
    public boolean blocksAttack(Piece piece, int fromRow, int fromCol,
            int toRow, int toCol, GameState state) {
        return false;
    }

    /** True if this card wants onKingInCheck calls (checking for check costs time). */
    public boolean reactsToCheck() {
        return false;
    }

    /**
     * Called after every move is performed on state.
     * Use for per-move triggers (Chrono Shift, Minefield, stun decrement).
     */
    public void onMovePerformed(GameState state, Move move) {
        // no-op
    }

    /**
     * Called when a king enters check. Return true if the card handled it
     * (e.g., Phantom King teleport).
     */
    public boolean onKingInCheck(GameState state, boolean whiteKing, ChessEngine engine) {
        return false;
    }

    /**
     * Whether this card applies to the given side.
     * Global cards apply to both; local cards apply only to the player (white).
     */
    public boolean appliesTo(boolean isWhite) {
        return global || isWhite;
    }

    @Override
    public String toString() {
        return name + (global ? " [GLOBAL]" : " [LOCAL]");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return name.equals(card.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
