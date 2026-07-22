
import java.io.Serializable;
import java.util.List;

/**
 * Abstract base class for all roguelike cards.
 * Cards use hook methods — override only the hooks you need.
 * All hooks have default no-op implementations for minimal overhead.
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
     * Called once when the game round starts (after board setup).
     * Use for one-time board mutations (Blood Moon, Minefield).
     */
    public void onGameStart(GameState state) {
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
