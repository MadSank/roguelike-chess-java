import java.util.*;

/**
 * Rules engine. One code path (applyToState) applies a move, both for the real game
 * (applyMove) and for the AI's search (simulateMove), so the two can never disagree.
 *
 * This variant has no castling (random back ranks).
 */
public class ChessEngine {
    private GameState state;
    /** Snapshots of the state before each applied move, for undo(). */
    private final Deque<GameState> history;

    public ChessEngine() {
        this(new GameState());
    }

    public ChessEngine(GameState initial) {
        this.state = initial;
        this.history = new ArrayDeque<>();
    }

    public GameState getGameState() {
        return state;
    }

    public void reset() {
        this.state = new GameState();
        this.history.clear();
    }

    // ─── APPLYING MOVES ─────────────────────────────────────────

    /**
     * Plays a move in the real game if it is legal for the side to move.
     * The move's flags (capture, en passant, promotion type...) are taken from the
     * engine's own generated move, so callers only need from/to (and promotion type).
     */
    public boolean applyMove(Move move) {
        if (state.gameOver) return false;
        if (!isOnBoard(move)) return false;
        Piece piece = state.board[move.fromRow][move.fromCol];
        if (piece == null || piece.isWhite() != state.whiteToMove) return false;

        Move legal = findLegalMove(move);
        if (legal == null) return false;

        history.push(state.copy());
        applyToState(state, legal);
        checkEndConditions();
        return true;
    }

    /** Takes back the last move played with applyMove. */
    public boolean undo() {
        if (history.isEmpty()) return false;
        state.copyFrom(history.pop());
        return true;
    }

    /** Returns a new state with the move applied. The original state is not changed. */
    public GameState simulateMove(GameState fromState, Move move) {
        GameState next = fromState.copy();
        applyToState(next, move);
        return next;
    }

    /**
     * Applies a move to a state: board changes, card hooks, en passant square,
     * counters, repetition history and side to move. Does not check legality
     * and does not decide whether the game has ended.
     */
    private void applyToState(GameState s, Move move) {
        if (s.positionHistory.isEmpty()) {
            s.positionHistory.add(s.positionKey()); // starting position counts for repetition
        }

        Piece mover = s.board[move.fromRow][move.fromCol];
        Piece captured;
        if (move.isSniperShot) {
            captured = s.board[move.sniperTargetRow][move.sniperTargetCol];
        } else if (move.isEnPassant) {
            captured = s.board[move.fromRow][move.toCol];
        } else {
            captured = s.board[move.toRow][move.toCol];
        }

        performMoveOnState(s, move);

        // Card: capture side-effects (Golden Edge, Glass Cannons, Necromancer, Soul Harvest)
        if (captured != null && s.cardManager != null) {
            Piece capturer = s.board[move.toRow][move.toCol];
            s.cardManager.resolveCapture(s, move, captured, capturer);
        }
        // Card: per-move triggers (Minefield, Chrono Shift, Heavy Armor stun, Vaulting tracking)
        if (s.cardManager != null) {
            s.cardManager.onMovePerformed(s, move);
        }

        s.enPassantTarget = null;
        if (!move.isSniperShot && mover instanceof Pawn && Math.abs(move.toRow - move.fromRow) == 2) {
            s.enPassantTarget = new Square((move.fromRow + move.toRow) / 2, move.toCol);
        }

        if (captured != null || mover instanceof Pawn) {
            s.halfmoveClock = 0;
        } else {
            s.halfmoveClock++;
        }

        s.movesToEnd++;
        s.totalMoveCount++;
        s.moveHistory.add(move.toUCI());
        s.lastMoveUCI = move.toUCI();
        if (move.isEnPassant || move.isPromotion) {
            s.specialMovesUsed++;
        }
        if (captured != null) {
            s.capturesMade++;
        }

        s.whiteToMove = !s.whiteToMove;
        s.piecesLeftStanding = Utils.countPiecesOnBoard(s);
        s.positionHistory.add(s.positionKey());
    }

    private void performMoveOnState(GameState s, Move move) {
        if (move.isSniperShot) {
            // Sniper shot: the bishop stays put, the target is removed
            s.board[move.sniperTargetRow][move.sniperTargetCol] = null;
            return;
        }
        Piece moving = s.board[move.fromRow][move.fromCol];
        if (move.isEnPassant) {
            s.board[move.fromRow][move.toCol] = null;
        }
        s.board[move.toRow][move.toCol] = moving;
        s.board[move.fromRow][move.fromCol] = null;
        if (move.isPromotion && moving != null) {
            s.board[move.toRow][move.toCol] = createPromotedPiece(move.promotionType, moving.isWhite());
        }
    }

    private Piece createPromotedPiece(String promoType, boolean white) {
        if (promoType == null) return new Queen(white);
        switch (promoType) {
            case "R": return new Rook(white);
            case "B": return new Bishop(white);
            case "N": return new Knight(white);
            default:  return new Queen(white);
        }
    }

    // ─── MOVE GENERATION AND LEGALITY ───────────────────────────

    /** Moves a piece could make before checking whether its own king is left in check. */
    public List<Move> getPseudoLegalMoves(int fromRow, int fromCol) {
        Piece piece = state.board[fromRow][fromCol];
        if (piece == null) return Collections.emptyList();
        List<Move> baseMoves = getSafeLegalMoves(piece, fromRow, fromCol);
        if (state.cardManager != null) {
            baseMoves = state.cardManager.applyMoveModifiers(baseMoves, piece, fromRow, fromCol, this, state);
        }
        return baseMoves;
    }

    /** True if the move is legal for the side to move. */
    public boolean isMoveLegal(Move move) {
        if (!isOnBoard(move)) return false;
        Piece piece = state.board[move.fromRow][move.fromCol];
        if (piece == null || piece.isWhite() != state.whiteToMove) return false;
        return findLegalMove(move) != null;
    }

    /** Legal moves of the piece on (r, c), whichever side it belongs to. */
    public List<Move> getLegalMovesFromSquare(int r, int c) {
        List<Move> legal = new ArrayList<>();
        Piece piece = state.board[r][c];
        if (piece == null) return legal;
        for (Move m : getPseudoLegalMoves(r, c)) {
            if (isLegalForMover(m, piece.isWhite())) legal.add(m);
        }
        return legal;
    }

    /** All legal moves for one side (it does not have to be that side's turn). */
    public List<Move> getAllLegalMoves(boolean forWhite) {
        List<Move> moves = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p != null && p.isWhite() == forWhite) {
                    moves.addAll(getLegalMovesFromSquare(r, c));
                }
            }
        }
        return moves;
    }

    /** Number of moves a side could make before the "own king in check" filter. Cheap; used by the AI's evaluation. */
    public int countPseudoLegalMoves(boolean forWhite) {
        int n = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p != null && p.isWhite() == forWhite) n += getPseudoLegalMoves(r, c).size();
            }
        }
        return n;
    }

    /** Finds the engine's own legal move matching the request (from/to, sniper target, promotion). */
    private Move findLegalMove(Move request) {
        Piece piece = state.board[request.fromRow][request.fromCol];
        if (piece == null) return null;
        for (Move m : getPseudoLegalMoves(request.fromRow, request.fromCol)) {
            if (m.toRow != request.toRow || m.toCol != request.toCol) continue;
            if (m.isSniperShot != request.isSniperShot) continue;
            if (m.isSniperShot && (m.sniperTargetRow != request.sniperTargetRow
                    || m.sniperTargetCol != request.sniperTargetCol)) continue;
            if (m.isPromotion && request.promotionType != null
                    && !request.promotionType.equals(m.promotionType)) continue;
            if (isLegalForMover(m, piece.isWhite())) return m;
        }
        return null;
    }

    private boolean isLegalForMover(Move move, boolean moverIsWhite) {
        // Kings are never captured: in normal chess this can't come up, and card
        // abilities (snipe, vault, cavalry...) are not allowed to target a King.
        Piece target;
        if (move.isSniperShot) {
            target = state.board[move.sniperTargetRow][move.sniperTargetCol];
        } else if (move.isEnPassant) {
            target = state.board[move.fromRow][move.toCol];
        } else {
            target = state.board[move.toRow][move.toCol];
        }
        if (target instanceof King) return false;

        // Card hook: is the target immune to this capture?
        if (move.isCapture && !move.isSniperShot && state.cardManager != null) {
            if (state.cardManager.isCaptureImmune(state, move.toRow, move.toCol, move)) {
                return false;
            }
        }

        // The move may not leave the mover's own king in check.
        GameState after = simulateMove(state, move);
        return !isKingInCheck(after, moverIsWhite);
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

    private boolean isOnBoard(Move m) {
        return coordinatesInBounds(m.fromRow, m.fromCol) && coordinatesInBounds(m.toRow, m.toCol);
    }

    // ─── CHECK DETECTION ────────────────────────────────────────

    public boolean isKingInCheck(GameState gstate, boolean whiteKing) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = gstate.board[r][c];
                if (p instanceof King && p.isWhite() == whiteKing) {
                    return isSquareAttacked(gstate, r, c, !whiteKing);
                }
            }
        }
        return true; // no king on the board counts as lost
    }

    public boolean isSquareAttacked(GameState gstate, int targetRow, int targetCol, boolean byWhite) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = gstate.board[r][c];
                if (p != null && p.isWhite() == byWhite
                        && canPieceAttackSquare(p, r, c, targetRow, targetCol, gstate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canPieceAttackSquare(Piece piece, int fromRow, int fromCol, int toRow, int toCol, GameState gs) {
        int dr = Math.abs(toRow - fromRow);
        int dc = Math.abs(toCol - fromCol);
        if (piece instanceof Pawn) {
            int dir = piece.isWhite() ? -1 : 1;
            return (toRow == fromRow + dir) && (dc == 1);
        } else if (piece instanceof Knight) {
            if ((dr == 2 && dc == 1) || (dr == 1 && dc == 2)) return true;
        } else if (piece instanceof Bishop) {
            if (dr == dc && dr > 0 && isPathClear(gs, fromRow, fromCol, toRow, toCol)) return true;
        } else if (piece instanceof Rook) {
            if ((dr == 0 || dc == 0) && (dr + dc > 0) && isPathClear(gs, fromRow, fromCol, toRow, toCol)) return true;
        } else if (piece instanceof Queen) {
            if (((dr == dc) || (dr == 0 || dc == 0)) && (dr + dc > 0)
                    && isPathClear(gs, fromRow, fromCol, toRow, toCol)) return true;
        } else if (piece instanceof King) {
            return dr <= 1 && dc <= 1 && (dr + dc > 0);
        }
        // Card hook: extended attacks (e.g. Cavalry Charge radius)
        if (gs.cardManager != null) {
            return gs.cardManager.canAttackSquare(piece, fromRow, fromCol, toRow, toCol, gs);
        }
        return false;
    }

    private boolean isPathClear(GameState gs, int fromRow, int fromCol, int toRow, int toCol) {
        int dr = Integer.compare(toRow - fromRow, 0);
        int dc = Integer.compare(toCol - fromCol, 0);
        int r = fromRow + dr;
        int c = fromCol + dc;
        while (r != toRow || c != toCol) {
            if (gs.board[r][c] != null) return false;
            r += dr;
            c += dc;
        }
        return true;
    }

    // ─── GAME END ───────────────────────────────────────────────

    /** Decides whether the game in the engine's state has ended, and how. */
    private void checkEndConditions() {
        boolean side = state.whiteToMove;
        if (getAllLegalMoves(side).isEmpty()) {
            if (isKingInCheck(state, side)) {
                if (side) state.setResult(GameResult.BLACK_WINS, "0-1 (Black mates)");
                else      state.setResult(GameResult.WHITE_WINS, "1-0 (White mates)");
            } else {
                state.setResult(GameResult.DRAW, "1/2-1/2 (Stalemate)");
            }
            return;
        }
        String draw = drawReason(state);
        if (draw != null) {
            state.setResult(GameResult.DRAW, "1/2-1/2 (" + draw + ")");
        } else {
            state.setResult(GameResult.ONGOING, "");
        }
    }

    /**
     * Draws that don't depend on the move list: 50-move rule, threefold repetition,
     * insufficient material. Returns the reason, or null if none applies.
     * (Stalemate is detected separately, because it needs move generation.)
     */
    public static String drawReason(GameState s) {
        if (s.halfmoveClock >= 100) return "50-move rule";
        if (isThreefoldRepetition(s)) return "threefold repetition";
        if (isInsufficientMaterial(s)) return "insufficient material";
        return null;
    }

    private static boolean isThreefoldRepetition(GameState s) {
        int n = s.positionHistory.size();
        if (n < 5) return false;
        String current = s.positionHistory.get(n - 1);
        int count = 0;
        // Only positions since the last capture or pawn move can repeat.
        int from = Math.max(0, n - 1 - s.halfmoveClock);
        for (int i = from; i < n; i++) {
            if (s.positionHistory.get(i).equals(current)) count++;
        }
        return count >= 3;
    }

    /** K v K, K+minor v K, or only bishops all on the same square colour. */
    private static boolean isInsufficientMaterial(GameState s) {
        int minors = 0;
        int knights = 0;
        int lightBishops = 0;
        int darkBishops = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = s.board[r][c];
                if (p == null || p instanceof King) continue;
                if (p instanceof Pawn || p instanceof Rook || p instanceof Queen) return false;
                minors++;
                if (p instanceof Knight) knights++;
                if (p instanceof Bishop) {
                    if ((r + c) % 2 == 0) lightBishops++;
                    else darkBishops++;
                }
            }
        }
        if (minors <= 1) return true;
        return knights == 0 && (lightBishops == 0 || darkBishops == 0);
    }
}
