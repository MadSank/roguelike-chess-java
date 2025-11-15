package ashes;

import java.util.*;

public class ChessEngine {
    private GameState state;
    private Deque<HistoryEntry> history;
    private Random rng;
    private boolean isCheckingAttacks = false;

    public ChessEngine() {
        this(new GameState());
    }

    public ChessEngine(GameState initial) 
    {
        this.state = initial;
        this.history = new ArrayDeque<>();
        this.rng = new Random(12345);
    }

    public GameState getGameState() 
    {
        return state;
    }

    public void reset() 
    {
        this.state = new GameState();
        this.history.clear();
        this.isCheckingAttacks = false;
    }

    public boolean applyMove(Move move) {
        if (!isMoveLegal(move)) {
            return false;
        }
        HistoryEntry entry = makeHistoryEntry(move);
        history.push(entry);
        performMoveOnState(state, move);

        updateCastlingRights(move);

        updateEnPassantTarget(move);

        state.movesToEnd++;
        state.moveHistory.add(move.toUCI());

        if (move.isCastle || move.isEnPassant || move.isPromotion) {
            state.specialMovesUsed++;
        }

        state.whiteToMove = !state.whiteToMove;

        checkEndConditions();
        return true;
    }

    private void updateCastlingRights(Move move) {
        Piece piece = state.board[move.toRow][move.toCol];

        if (piece instanceof King) {
            if (piece.isWhite()) {
                state.whiteKingMoved = true;
                ((King) piece).setHasMoved(true);
            } else {
                state.blackKingMoved = true;
                ((King) piece).setHasMoved(true);
            }
        }

        if (piece instanceof Rook) {
            if (piece.isWhite()) {
                if (move.fromCol == 7) state.whiteKingsideRookMoved = true;
                if (move.fromCol == 0) state.whiteQueensideRookMoved = true;
            } else {
                if (move.fromCol == 7) state.blackKingsideRookMoved = true;
                if (move.fromCol == 0) state.blackQueensideRookMoved = true;
            }
        }

        if (move.isCapture) {
            if (move.toRow == 7) {
                if (move.toCol == 7) state.whiteKingsideRookMoved = true;
                if (move.toCol == 0) state.whiteQueensideRookMoved = true;
            } else if (move.toRow == 0) {
                if (move.toCol == 7) state.blackKingsideRookMoved = true;
                if (move.toCol == 0) state.blackQueensideRookMoved = true;
            }
        }
    }

    private void updateEnPassantTarget(Move move) {
        state.enPassantTarget = null;
        Piece piece = state.board[move.toRow][move.toCol];
        if (piece instanceof Pawn) {
            int moveDist = Math.abs(move.toRow - move.fromRow);
            if (moveDist == 2) {
                int targetRow = (move.fromRow + move.toRow) / 2;
                state.enPassantTarget = new Square(targetRow, move.toCol);
            }
        }
    }

    public boolean undo() {
        if (history.isEmpty()) return false;
        HistoryEntry entry = history.pop();
        restoreHistoryEntry(entry);
        return true;
    }

    public List<Move> getPseudoLegalMoves(int fromRow, int fromCol) {
        Piece piece = state.board[fromRow][fromCol];
        if (piece == null) return Collections.emptyList();
        return getSafeLegalMoves(piece, fromRow, fromCol);
    }

    public boolean isMoveLegal(Move move) {
        if (!coordinatesInBounds(move.fromRow, move.fromCol) ||
        !coordinatesInBounds(move.toRow, move.toCol)) {
            return false;
        }
        Piece piece = state.board[move.fromRow][move.fromCol];
        if (piece == null) return false;
        if (piece.isWhite() != state.whiteToMove) return false;
        List<Move> pseudoLegalMoves = getPseudoLegalMoves(move.fromRow, move.fromCol);
        boolean found = false;
        Move matchingMove = null;
        for (Move m : pseudoLegalMoves) {
            if (m.fromRow == move.fromRow && m.fromCol == move.fromCol &&
            m.toRow == move.toRow && m.toCol == move.toCol) {
                matchingMove = m;
                found = true;
                break;
            }
        }
        if (!found) return false;
        if (state.bannedSquare != null) {
            if (move.toRow == state.bannedSquare.row && move.toCol == state.bannedSquare.col) {
                return false;
            }
        }
        GameState simulated = simulateMove(state, matchingMove);
        boolean ourKingInCheck = isKingInCheck(simulated, state.whiteToMove);
        return !ourKingInCheck;
    }

    public List<Move> getLegalMovesFromSquare(int r, int c) {
        List<Move> legalMoves = new ArrayList<>();
        List<Move> pseudoLegalMoves = getPseudoLegalMoves(r, c);
        for (Move move : pseudoLegalMoves) {
            if (isMoveLegal(move)) {
                legalMoves.add(move);
            }
        }
        return legalMoves;
    }

    private List<Move> getSafeLegalMoves(Piece piece, int row, int col) {
        try {
            return piece.generateLegalMoves(row, col, this);
        } catch (StackOverflowError e) {
            System.err.println("Stack overflow in move generation for " + piece.getName() + " at " + row + "," + col);
            return Collections.emptyList();
        }
    }

    boolean coordinatesInBounds(int r, int c) {
        return r >= 0 && r < 8 && c >= 0 && c < 8;
    }

    private void performMoveOnState(GameState gstate, Move move) {
        Piece moving = gstate.board[move.fromRow][move.fromCol];
        if (moving == null) {
            System.err.println("Error: No piece at move source " + move.fromRow + "," + move.fromCol);
            return;
        }

        Piece captured = gstate.board[move.toRow][move.toCol];
        if (move.isEnPassant) {
            int capRow = move.fromRow;
            int capCol = move.toCol;
            captured = gstate.board[capRow][capCol];
            gstate.board[capRow][capCol] = null;
        }
        if (move.isCastle) {
            if (move.toCol == 6) {
                Piece rook = gstate.board[move.toRow][7];
                gstate.board[move.toRow][5] = rook;
                gstate.board[move.toRow][7] = null;
            } else if (move.toCol == 2) {
                Piece rook = gstate.board[move.toRow][0];
                gstate.board[move.toRow][3] = rook;
                gstate.board[move.toRow][0] = null;
            }
        }
        gstate.board[move.toRow][move.toCol] = moving;
        gstate.board[move.fromRow][move.fromCol] = null;
        if (move.isPromotion) {
            Piece promoted = createPromotedPiece(move.promotionType, moving.isWhite());
            gstate.board[move.toRow][move.toCol] = promoted;
        }
        if (captured != null) {
            gstate.capturesMade++;
        }
        gstate.piecesLeftStanding = countPiecesOnBoard(gstate);
        gstate.lastMoveUCI = move.toUCI();
    }

    private Piece createPromotedPiece(String promoType, boolean white) {
        switch (promoType) {
            case "Q": return new Queen(white);
            case "R": return new Rook(white);
            case "B": return new Bishop(white);
            case "N": return new Knight(white);
            default: return new Queen(white);
        }
    }

    public GameState simulateMove(GameState fromState, Move move) {
        GameState newState = fromState.copy();
        performMoveOnState(newState, move);
        newState.whiteToMove = !newState.whiteToMove;
        return newState;
    }

    public boolean isKingInCheck(GameState gstate, boolean whiteKing) {
        int kr = -1, kc = -1;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = gstate.board[r][c];
                if (p instanceof King && p.isWhite() == whiteKing) {
                    kr = r; kc = c; break;
                }
            }
            if (kr != -1) break;
        }
        if (kr == -1) return true;
        return isSquareAttacked(gstate, kr, kc, !whiteKing);
    }

    public boolean isSquareAttacked(GameState gstate, int targetRow, int targetCol, boolean byWhite) {
        if (isCheckingAttacks) {
            return simpleAttackCheck(gstate, targetRow, targetCol, byWhite);
        }
        isCheckingAttacks = true;
        try {
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    Piece p = gstate.board[r][c];
                    if (p != null && p.isWhite() == byWhite) {
                        List<Move> moves = getSafeLegalMoves(p, r, c);
                        for (Move m : moves) {
                            if (m.toRow == targetRow && m.toCol == targetCol) {
                                Piece target = gstate.board[targetRow][targetCol];
                                if (target == null || target.isWhite() != byWhite) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            return false;
        } finally {
            isCheckingAttacks = false;
        }
    }

    private boolean simpleAttackCheck(GameState gstate, int targetRow, int targetCol, boolean byWhite) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = gstate.board[r][c];
                if (p != null && p.isWhite() == byWhite) {
                    if (canPieceAttackSquare(p, r, c, targetRow, targetCol, gstate)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean canPieceAttackSquare(Piece piece, int fromRow, int fromCol, int toRow, int toCol, GameState state) {
        int dr = Math.abs(toRow - fromRow);
        int dc = Math.abs(toCol - fromCol);
        if (piece instanceof Pawn) {
            int dir = piece.isWhite() ? -1 : 1;
            return (toRow == fromRow + dir) && (dc == 1);
        } else if (piece instanceof Knight) {
            return (dr == 2 && dc == 1) || (dr == 1 && dc == 2);
        } else if (piece instanceof Bishop) {
            return dr == dc && dr > 0 && isPathClear(state, fromRow, fromCol, toRow, toCol);
        } else if (piece instanceof Rook) {
            return (dr == 0 || dc == 0) && (dr + dc > 0) && isPathClear(state, fromRow, fromCol, toRow, toCol);
        } else if (piece instanceof Queen) {
            return ((dr == dc) || (dr == 0 || dc == 0)) && (dr + dc > 0) &&
            isPathClear(state, fromRow, fromCol, toRow, toCol);
        } else if (piece instanceof King) {
            return dr <= 1 && dc <= 1 && (dr + dc > 0);
        }
        return false;
    }

    private boolean isPathClear(GameState state, int fromRow, int fromCol, int toRow, int toCol) {
        int dr = Integer.compare(toRow - fromRow, 0);
        int dc = Integer.compare(toCol - fromCol, 0);
        int r = fromRow + dr;
        int c = fromCol + dc;
        while (r != toRow || c != toCol) {
            if (state.board[r][c] != null) return false;
            r += dr;
            c += dc;
        }
        return true;
    }

    private int countPiecesOnBoard(GameState gstate) {
        int count = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (gstate.board[r][c] != null) count++;
            }
        }
        return count;
    }

    private void checkEndConditions() {
        boolean sideToMove = state.whiteToMove;
        List<Move> legalMoves = getAllLegalMoves(sideToMove);
        if (legalMoves.isEmpty()) {
            if (isKingInCheck(state, sideToMove)) {
                state.gameOver = true;
                state.gameResult = sideToMove ? "0-1 (Black mates)" : "1-0 (White mates)";
            } else {
                state.gameOver = true;
                state.gameResult = "1/2-1/2 (Stalemate)";
            }
        } else {
            state.gameOver = false;
            state.gameResult = "";
        }
    }

    public List<Move> getAllLegalMoves(boolean forWhite) {
        List<Move> moves = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p != null && p.isWhite() == forWhite) {
                    List<Move> pieceMoves = getLegalMovesFromSquare(r, c);
                    moves.addAll(pieceMoves);
                }
            }
        }
        return moves;
    }

    public void applyMirroredSetupToAI() {
        for (int r = 0; r < 4; r++) {
            int mirror = 7 - r;
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p != null && p.isWhite()) {
                    if (state.board[mirror][c] == null) {
                        Piece clone = p.clone();
                        clone.setWhite(false);
                        state.board[mirror][c] = clone;
                    }
                }
            }
        }
    }

    private HistoryEntry makeHistoryEntry(Move move) {
        HistoryEntry h = new HistoryEntry();
        h.move = move.copy();
        h.captured = state.board[move.toRow][move.toCol];
        h.prevEnPassantTarget = state.enPassantTarget;
        h.prevWhiteToMove = state.whiteToMove;
        h.prevWhiteKingMoved = state.whiteKingMoved;
        h.prevWhiteKingsideRookMoved = state.whiteKingsideRookMoved;
        h.prevWhiteQueensideRookMoved = state.whiteQueensideRookMoved;
        h.prevBlackKingMoved = state.blackKingMoved;
        h.prevBlackKingsideRookMoved = state.blackKingsideRookMoved;
        h.prevBlackQueensideRookMoved = state.blackQueensideRookMoved;
        h.prevMovesToEnd = state.movesToEnd;
        h.prevCapturesMade = state.capturesMade;
        h.prevSpecialMovesUsed = state.specialMovesUsed;
        h.prevPiecesLeftStanding = state.piecesLeftStanding;
        h.prevLastMoveUCI = state.lastMoveUCI;
        h.prevGameOver = state.gameOver;
        h.prevGameResult = state.gameResult;
        return h;
    }

    private void restoreHistoryEntry(HistoryEntry entry) {
        Move move = entry.move;
        Piece moved = state.board[move.toRow][move.toCol];
        state.board[move.fromRow][move.fromCol] = moved;
        state.board[move.toRow][move.toCol] = entry.captured;
        if (move.isEnPassant) {
            int capRow = move.fromRow;
            int capCol = move.toCol;
            state.board[capRow][capCol] = entry.captured;
        }
        if (move.isCastle) {
            if (move.toCol == 6) {
                Piece rook = state.board[move.toRow][5];
                state.board[move.toRow][7] = rook;
                state.board[move.toRow][5] = null;
            } else if (move.toCol == 2) {
                Piece rook = state.board[move.toRow][3];
                state.board[move.toRow][0] = rook;
                state.board[move.toRow][3] = null;
            }
        }
        if (move.isPromotion) {
            Piece pawn = new Pawn(moved.isWhite());
            state.board[move.fromRow][move.fromCol] = pawn;
        }
        state.whiteToMove = entry.prevWhiteToMove;
        state.enPassantTarget = entry.prevEnPassantTarget;
        state.whiteKingMoved = entry.prevWhiteKingMoved;
        state.whiteKingsideRookMoved = entry.prevWhiteKingsideRookMoved;
        state.whiteQueensideRookMoved = entry.prevWhiteQueensideRookMoved;
        state.blackKingMoved = entry.prevBlackKingMoved;
        state.blackKingsideRookMoved = entry.prevBlackKingsideRookMoved;
        state.blackQueensideRookMoved = entry.prevBlackQueensideRookMoved;
        state.movesToEnd = entry.prevMovesToEnd;
        state.capturesMade = entry.prevCapturesMade;
        state.specialMovesUsed = entry.prevSpecialMovesUsed;
        state.piecesLeftStanding = entry.prevPiecesLeftStanding;
        state.lastMoveUCI = entry.prevLastMoveUCI;
        state.gameOver = entry.prevGameOver;
        state.gameResult = entry.prevGameResult;

        if (!state.moveHistory.isEmpty())
            state.moveHistory.remove(state.moveHistory.size() - 1);
    }

    private static class HistoryEntry {
        Move move;
        Piece captured;
        Boolean prevWhiteToMove;
        Square prevEnPassantTarget;
        boolean prevWhiteKingMoved;
        boolean prevWhiteKingsideRookMoved;
        boolean prevWhiteQueensideRookMoved;
        boolean prevBlackKingMoved;
        boolean prevBlackKingsideRookMoved;
        boolean prevBlackQueensideRookMoved;
        int prevMovesToEnd;
        int prevCapturesMade;
        int prevSpecialMovesUsed;
        int prevPiecesLeftStanding;
        String prevLastMoveUCI;
        boolean prevGameOver;
        String prevGameResult;
    }
}