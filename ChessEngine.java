

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

        // Resolve capture with card hooks
        Piece captured = null;
        if (move.isSniperShot) {
            captured = state.board[move.sniperTargetRow][move.sniperTargetCol];
        } else if (move.isEnPassant) {
            captured = state.board[move.fromRow][move.toCol];
        } else {
            captured = state.board[move.toRow][move.toCol];
        }

        performMoveOnState(state, move);

        // Card: resolve capture side-effects (Golden Edge, Glass Cannons, Necromancer, Soul Harvest)
        if (move.isCapture && captured != null && state.cardManager != null) {
            Piece capturer = state.board[move.toRow][move.toCol];
            state.cardManager.resolveCapture(state, move, captured, capturer);
        }

        // Card: per-move triggers (Minefield, Chrono Shift, Heavy Armor stun decrement, Vaulting track)
        if (state.cardManager != null) {
            state.cardManager.onMovePerformed(state, move);
        }

        updateCastlingRights(move);
        updateEnPassantTarget(move);

        state.movesToEnd++;
        state.totalMoveCount++;
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
        // For sniper shots, piece stays in place
        if (move.isSniperShot) {
            piece = state.board[move.fromRow][move.fromCol];
        }
        if (piece == null) return;

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

        if (move.isCapture && !move.isSniperShot) {
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
        if (move.isSniperShot) return;
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
        List<Move> baseMoves = getSafeLegalMoves(piece, fromRow, fromCol);

        // Card hook: modify move generation
        if (state.cardManager != null) {
            baseMoves = state.cardManager.applyMoveModifiers(
                baseMoves, piece, fromRow, fromCol, this, state);
        }
        return baseMoves;
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
                // For sniper shots, also match target coordinates
                if (m.isSniperShot && move.isSniperShot) {
                    if (m.sniperTargetRow == move.sniperTargetRow &&
                        m.sniperTargetCol == move.sniperTargetCol) {
                        matchingMove = m;
                        found = true;
                        break;
                    }
                } else if (!m.isSniperShot && !move.isSniperShot) {
                    matchingMove = m;
                    found = true;
                    break;
                }
            }
        }
        if (!found) return false;
        if (state.bannedSquare != null && !move.isSniperShot) {
            if (move.toRow == state.bannedSquare.row && move.toCol == state.bannedSquare.col) {
                return false;
            }
        }

        // Card hook: check if target piece is immune to capture
        if (matchingMove.isCapture && !matchingMove.isSniperShot && state.cardManager != null) {
            if (state.cardManager.isCaptureImmune(state, move.toRow, move.toCol, matchingMove)) {
                return false;
            }
        }

        // Sniper shots don't move the piece, so check differently
        if (matchingMove.isSniperShot) {
            // Sniper doesn't move, so king can't be put in check by this
            return true;
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
        // Handle sniper shots: piece stays, target is removed
        if (move.isSniperShot) {
            Piece sniperTarget = gstate.board[move.sniperTargetRow][move.sniperTargetCol];
            gstate.board[move.sniperTargetRow][move.sniperTargetCol] = null;
            if (sniperTarget != null) {
                gstate.capturesMade++;
            }
            gstate.piecesLeftStanding = Utils.countPiecesOnBoard(gstate);
            gstate.lastMoveUCI = move.toUCI();
            return;
        }

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
        gstate.piecesLeftStanding = Utils.countPiecesOnBoard(gstate);
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

        // Card: resolve capture in simulation for accurate AI evaluation
        if (move.isCapture && newState.cardManager != null) {
            Piece captured = null;
            if (move.isSniperShot) {
                // Already handled in performMoveOnState
            } else if (move.isEnPassant) {
                // captured already removed
            } else {
                // captured already removed, but we need the original
                captured = fromState.board[move.toRow][move.toCol];
            }
            if (captured != null) {
                Piece capturer = newState.board[move.toRow][move.toCol];
                newState.cardManager.resolveCapture(newState, move, captured, capturer);
            }
        }

        // Card: per-move triggers in simulation
        if (newState.cardManager != null) {
            newState.cardManager.onMovePerformed(newState, move);
        }

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
        return simpleAttackCheck(gstate, targetRow, targetCol, byWhite);
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
            boolean normalAttack = (dr == 2 && dc == 1) || (dr == 1 && dc == 2);
            if (normalAttack) return true;
        } else if (piece instanceof Bishop) {
            if (dr == dc && dr > 0 && isPathClear(state, fromRow, fromCol, toRow, toCol)) return true;
        } else if (piece instanceof Rook) {
            if ((dr == 0 || dc == 0) && (dr + dc > 0) && isPathClear(state, fromRow, fromCol, toRow, toCol)) return true;
        } else if (piece instanceof Queen) {
            if (((dr == dc) || (dr == 0 || dc == 0)) && (dr + dc > 0) &&
            isPathClear(state, fromRow, fromCol, toRow, toCol)) return true;
        } else if (piece instanceof King) {
            return dr <= 1 && dc <= 1 && (dr + dc > 0);
        }

        // Card hook: extended attack checks (Cavalry Charge radius, etc.)
        if (state.cardManager != null) {
            return state.cardManager.canAttackSquare(piece, fromRow, fromCol, toRow, toCol, state);
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
        if (move.isSniperShot) {
            h.captured = state.board[move.sniperTargetRow][move.sniperTargetCol];
        } else if (move.isEnPassant) {
            h.captured = state.board[move.fromRow][move.toCol];
        } else {
            h.captured = state.board[move.toRow][move.toCol];
        }
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
        if (move.isSniperShot) {
            // Restore sniper target
            state.board[move.sniperTargetRow][move.sniperTargetCol] = entry.captured;
        } else {
            Piece moved = state.board[move.toRow][move.toCol];
            state.board[move.fromRow][move.fromCol] = moved;
            if (move.isEnPassant) {
                state.board[move.toRow][move.toCol] = null;
                int capRow = move.fromRow;
                int capCol = move.toCol;
                state.board[capRow][capCol] = entry.captured;
            } else {
                state.board[move.toRow][move.toCol] = entry.captured;
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
