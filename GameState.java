import java.io.Serializable;
import java.util.*;

/**
 * Complete game state snapshot. The engine mutates it; the AI works on copies.
 */
public class GameState implements Serializable {
    private static final long serialVersionUID = 2L;

    public Piece[][] board;
    public boolean whiteToMove;
    public int goldPlayer;

    // Round statistics
    public int movesToEnd;
    public int capturesMade;
    public int specialMovesUsed;
    public int piecesLeftStanding;

    public int aiDepth;
    public Square enPassantTarget;

    public String lastMoveUCI;
    public int roundNumber;
    public List<String> moveHistory;

    // ─── GAME END ───────────────────────────────────────────────
    public GameResult result;
    /** Kept in sync with result; true once the game has ended. */
    public boolean gameOver;
    /** Human-readable result, e.g. "1-0 (White mates)" or "1/2-1/2 (50-move rule)". */
    public String gameResult;

    /** Plies since the last capture or pawn move (50-move rule draws at 100). */
    public int halfmoveClock;
    /** Position keys after every move, for threefold repetition. */
    public List<String> positionHistory;

    // ─── CARD SYSTEM STATE ──────────────────────────────────────
    public CardManager cardManager;

    /** Heavy Armor: stunned pieces. Key = row*8+col, Value = turns remaining. */
    public Map<Integer, Integer> stunnedPieces;

    /** Minefield: trapped square indices (row*8+col). */
    public Set<Integer> minefieldSquares;

    /** Necromancer's Gambit: one-time trigger flag. */
    public boolean necromancerUsed;

    /** Vaulting Majors: which piece can vault (row*8+col), -1 if none. */
    public int vaultingPieceSquare = -1;

    /** Total move counter for cards like Chrono Shift. */
    public int totalMoveCount;

    /**
     * Per-game counters and flags used by cards (e.g. "chronoShift.moves", "phantomKing.used").
     * Cards themselves hold no state, so copying a GameState copies all card state too and the
     * AI's search can never change the real game.
     */
    public Map<String, Integer> cardCounters;

    public GameState() {
        board = new Piece[8][8];
        whiteToMove = true;
        goldPlayer = 0;
        movesToEnd = 0;
        capturesMade = 0;
        specialMovesUsed = 0;
        piecesLeftStanding = 0;
        aiDepth = 2;
        enPassantTarget = null;
        lastMoveUCI = "";
        roundNumber = 1;
        moveHistory = new ArrayList<>();

        result = GameResult.ONGOING;
        gameOver = false;
        gameResult = "";
        halfmoveClock = 0;
        positionHistory = new ArrayList<>();

        cardManager = new CardManager();
        stunnedPieces = new HashMap<>();
        minefieldSquares = new HashSet<>();
        necromancerUsed = false;
        vaultingPieceSquare = -1;
        totalMoveCount = 0;
        cardCounters = new HashMap<>();
    }

    /** Deep copy: pieces, lists, maps and card state are all independent of the original. */
    public GameState copy() {
        GameState c = new GameState();
        c.copyFrom(this);
        return c;
    }

    /** Makes this state an independent deep copy of other. */
    public void copyFrom(GameState other) {
        board = new Piece[8][8];
        for (int r = 0; r < 8; r++) {
            for (int col = 0; col < 8; col++) {
                Piece p = other.board[r][col];
                board[r][col] = (p != null) ? p.clone() : null;
            }
        }
        whiteToMove = other.whiteToMove;
        goldPlayer = other.goldPlayer;
        movesToEnd = other.movesToEnd;
        capturesMade = other.capturesMade;
        specialMovesUsed = other.specialMovesUsed;
        piecesLeftStanding = other.piecesLeftStanding;
        aiDepth = other.aiDepth;
        enPassantTarget = other.enPassantTarget; // Square is immutable
        lastMoveUCI = other.lastMoveUCI;
        roundNumber = other.roundNumber;
        moveHistory = new ArrayList<>(other.moveHistory);

        result = other.result;
        gameOver = other.gameOver;
        gameResult = other.gameResult;
        halfmoveClock = other.halfmoveClock;
        positionHistory = new ArrayList<>(other.positionHistory);

        cardManager = (other.cardManager != null) ? other.cardManager.copy() : new CardManager();
        stunnedPieces = new HashMap<>(other.stunnedPieces != null ? other.stunnedPieces : Collections.emptyMap());
        minefieldSquares = new HashSet<>(other.minefieldSquares != null ? other.minefieldSquares : Collections.emptySet());
        necromancerUsed = other.necromancerUsed;
        vaultingPieceSquare = other.vaultingPieceSquare;
        totalMoveCount = other.totalMoveCount;
        cardCounters = new HashMap<>(other.cardCounters != null ? other.cardCounters : Collections.emptyMap());
    }

    /** Current value of a card counter (0 if never set). */
    public int getCounter(String key) {
        return cardCounters.getOrDefault(key, 0);
    }

    public void setCounter(String key, int value) {
        cardCounters.put(key, value);
    }

    /** Adds one to a card counter and returns the new value. */
    public int incrementCounter(String key) {
        int v = getCounter(key) + 1;
        cardCounters.put(key, v);
        return v;
    }

    /** Sets the result and keeps gameOver / gameResult in sync. */
    public void setResult(GameResult r, String text) {
        result = r;
        gameOver = r.isOver();
        gameResult = text;
    }

    /**
     * Identifies a position for repetition: piece placement, side to move and en passant square.
     */
    public String positionKey() {
        StringBuilder sb = new StringBuilder(70);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p == null) {
                    sb.append('.');
                } else {
                    char ch = pieceLetter(p);
                    sb.append(p.isWhite() ? ch : Character.toLowerCase(ch));
                }
            }
        }
        sb.append(whiteToMove ? 'w' : 'b');
        if (enPassantTarget != null) sb.append(enPassantTarget.row).append(enPassantTarget.col);
        return sb.toString();
    }

    /** FEN-style letter for a piece (N for Knight, so it doesn't clash with King). */
    public static char pieceLetter(Piece p) {
        if (p instanceof King) return 'K';
        if (p instanceof Queen) return 'Q';
        if (p instanceof Rook) return 'R';
        if (p instanceof Bishop) return 'B';
        if (p instanceof Knight) return 'N';
        return 'P';
    }
}
