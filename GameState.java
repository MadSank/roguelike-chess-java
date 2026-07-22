

import java.io.Serializable;
import java.util.*;

/**
 * Complete game state snapshot - serializable for save/load
 */
public class GameState implements Serializable {
    private static final long serialVersionUID = 1L;
    public Piece[][] board;
    public boolean whiteToMove;
    public int goldPlayer;
    public int pawnResource;
    
    public int movesToEnd;
    public int capturesMade;
    public int specialMovesUsed;
    public int piecesLeftStanding;
    
    public int aiDepth;
    public Square bannedSquare;
    public Square enPassantTarget;
    
    public String lastMoveUCI;
    public boolean gameOver;
    public String gameResult;
    public int roundNumber;
    public List<String> moveHistory;
    public boolean whiteKingMoved;
    public boolean whiteKingsideRookMoved;
    public boolean whiteQueensideRookMoved;
    public boolean blackKingMoved;
    public boolean blackKingsideRookMoved;
    public boolean blackQueensideRookMoved;

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
    
    public GameState() {
        board = new Piece[8][8];
        whiteToMove = true;
        goldPlayer = 0;
        pawnResource = 2;
        movesToEnd = 0;
        capturesMade = 0;
        specialMovesUsed = 0;
        piecesLeftStanding = 0;
        aiDepth = 2;
        bannedSquare = null;
        enPassantTarget = null;
        
        lastMoveUCI = "";
        gameOver = false;
        gameResult = "";
        roundNumber = 1;
        moveHistory = new ArrayList<>();
        whiteKingMoved = false;
        whiteKingsideRookMoved = false;
        whiteQueensideRookMoved = false;
        blackKingMoved = false;
        blackKingsideRookMoved = false;
        blackQueensideRookMoved = false;

        // Card system
        cardManager = new CardManager();
        stunnedPieces = new HashMap<>();
        minefieldSquares = new HashSet<>();
        necromancerUsed = false;
        vaultingPieceSquare = -1;
        totalMoveCount = 0;
    }
    
    public void setGameState(GameState other) {
        
        this.board = new Piece[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = other.board[r][c];
                this.board[r][c] = (p != null) ? p.clone() : null;
            }
        }
        
        this.whiteToMove = other.whiteToMove;
        this.goldPlayer = other.goldPlayer;
        this.pawnResource = other.pawnResource;
        this.roundNumber = other.roundNumber;
        this.aiDepth = other.aiDepth;
        this.gameOver = other.gameOver;
        this.gameResult = other.gameResult;
        this.capturesMade = other.capturesMade;
        this.specialMovesUsed = other.specialMovesUsed;
        this.piecesLeftStanding = other.piecesLeftStanding;
        this.movesToEnd = other.movesToEnd;
        this.bannedSquare = other.bannedSquare;
        this.enPassantTarget = other.enPassantTarget;
        this.lastMoveUCI = other.lastMoveUCI;
        
        this.whiteKingMoved = other.whiteKingMoved;
        this.whiteKingsideRookMoved = other.whiteKingsideRookMoved;
        this.whiteQueensideRookMoved = other.whiteQueensideRookMoved;
        this.blackKingMoved = other.blackKingMoved;
        this.blackKingsideRookMoved = other.blackKingsideRookMoved;
        this.blackQueensideRookMoved = other.blackQueensideRookMoved;
        
        this.moveHistory = new ArrayList<>(other.moveHistory);

        // Card system
        this.cardManager = (other.cardManager != null) ? other.cardManager.copy() : new CardManager();
        this.stunnedPieces = new HashMap<>(other.stunnedPieces != null ? other.stunnedPieces : Collections.emptyMap());
        this.minefieldSquares = new HashSet<>(other.minefieldSquares != null ? other.minefieldSquares : Collections.emptySet());
        this.necromancerUsed = other.necromancerUsed;
        this.vaultingPieceSquare = other.vaultingPieceSquare;
        this.totalMoveCount = other.totalMoveCount;
    }

    public GameState copy() {
        GameState copy = new GameState();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] != null) {
                    copy.board[r][c] = board[r][c].clone();
                }
            }
        }
        copy.whiteToMove = whiteToMove;
        copy.goldPlayer = goldPlayer;
        copy.pawnResource = pawnResource;
        copy.movesToEnd = movesToEnd;
        copy.capturesMade = capturesMade;
        copy.specialMovesUsed = specialMovesUsed;
        copy.piecesLeftStanding = piecesLeftStanding;
        copy.bannedSquare = bannedSquare;
        copy.enPassantTarget = enPassantTarget;
        copy.gameOver = gameOver;
        copy.gameResult = gameResult;
        copy.roundNumber = roundNumber;
        
        copy.lastMoveUCI = lastMoveUCI;
        copy.aiDepth = this.aiDepth;
        copy.whiteKingMoved = whiteKingMoved;
        copy.whiteKingsideRookMoved = whiteKingsideRookMoved;
        copy.whiteQueensideRookMoved = whiteQueensideRookMoved;
        copy.blackKingMoved = blackKingMoved;
        copy.blackKingsideRookMoved = blackKingsideRookMoved;
        copy.blackQueensideRookMoved = blackQueensideRookMoved;
        
        copy.moveHistory = new ArrayList<>(moveHistory);

        // Card system deep copy
        copy.cardManager = (cardManager != null) ? cardManager.copy() : new CardManager();
        copy.stunnedPieces = new HashMap<>(stunnedPieces != null ? stunnedPieces : Collections.emptyMap());
        copy.minefieldSquares = new HashSet<>(minefieldSquares != null ? minefieldSquares : Collections.emptySet());
        copy.necromancerUsed = necromancerUsed;
        copy.vaultingPieceSquare = vaultingPieceSquare;
        copy.totalMoveCount = totalMoveCount;

        return copy;
    }

    public void initializeStartingPosition() {
        board[7][4] = new King(true);
        board[6][3] = new Pawn(true);
        board[6][4] = new Pawn(true);
        board[0][4] = new King(false);
        board[1][3] = new Pawn(false);
        board[1][4] = new Pawn(false);
    }
}
