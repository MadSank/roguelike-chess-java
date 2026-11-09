import java.util.*;

/**
 * The opponent. Plays whichever side is to move (in the game that's always Black).
 *
 * Search: negamax with alpha-beta, iterative deepening inside a time limit, captures-first
 * move ordering and (from mid levels) a capture-only quiescence search.
 *
 * Card blindness (DESIGN.md): the AI only plays moves that are legal under the real rules,
 * but it plans with plain chess. It doesn't know what the player's cards do (extra moves,
 * mines, stuns, coin flips...), so card effects catch it out.
 */
public class AIPlayer {
    private final Difficulty difficulty;
    private final Random rng;

    private static final int MATE = 10_000_000;
    private static final int INFINITY = 1_000_000_000;
    private static final int MAX_QUIESCENCE_PLIES = 6;

    private long deadline;
    private long nodes;
    private int depthReached;

    /** Thrown to stop a search that ran out of time. */
    private static final class Timeout extends RuntimeException {
        Timeout() {
            super(null, null, false, false);
        }
    }

    public AIPlayer(Difficulty difficulty, Random rng) {
        this.difficulty = difficulty;
        this.rng = rng;
    }

    /** Fixed depth, full strength, no time limit (tests and simulations). */
    public AIPlayer(ChessEngine engine, int searchDepth) {
        this(Difficulty.fixedDepth(Math.max(1, searchDepth)), new Random(0));
    }

    public int getSearchDepth() {
        return difficulty.searchDepth;
    }

    /** Positions searched by the last chooseMove call. */
    public long getNodes() {
        return nodes;
    }

    /** Deepest search the last chooseMove call finished (0 if it blundered on purpose). */
    public int getDepthReached() {
        return depthReached;
    }

    /** Picks a move for the side to move, or null if it has no legal move. */
    public Move chooseMove(GameState state) {
        nodes = 0;
        depthReached = 0;
        List<Move> rootMoves = new ChessEngine(state).getAllLegalMoves(state.whiteToMove);
        if (rootMoves.isEmpty()) return null;
        if (difficulty.blunderPercent > 0 && rng.nextInt(100) < difficulty.blunderPercent) {
            return rootMoves.get(rng.nextInt(rootMoves.size()));
        }

        GameState belief = plainChessView(state);
        ChessEngine beliefEngine = new ChessEngine(belief);
        orderMoves(belief, rootMoves);
        deadline = difficulty.timeLimitMs > 0 ? System.nanoTime() + difficulty.timeLimitMs * 1_000_000L : Long.MAX_VALUE;

        Move best = rootMoves.get(0);
        for (int depth = 1; depth <= difficulty.searchDepth; depth++) {
            try {
                int alpha = -INFINITY;
                Move depthBest = null;
                for (Move m : rootMoves) {
                    int score = -negamax(beliefEngine.simulateMove(belief, m), depth - 1, -INFINITY, -alpha, 1);
                    if (depthBest == null || score > alpha) {
                        alpha = score;
                        depthBest = m;
                    }
                }
                best = depthBest;
                depthReached = depth;
                rootMoves.remove(best);
                rootMoves.add(0, best); // search the best move first next iteration
                if (alpha >= MATE - 100) break; // forced mate found
            } catch (Timeout t) {
                break; // keep the best move from the last finished depth
            }
        }
        return best;
    }

    /**
     * What the AI believes the position is: the same board, but no cards and none of their
     * markers (mines, stuns...). It plans as if this were an ordinary game of chess.
     */
    static GameState plainChessView(GameState real) {
        GameState b = real.copy();
        b.cardManager = new CardManager();
        b.stunnedPieces.clear();
        b.minefieldSquares.clear();
        b.cardCounters.clear();
        b.vaultingPieceSquare = -1;
        return b;
    }

    private int negamax(GameState state, int depth, int alpha, int beta, int ply) {
        checkTime();
        if (ChessEngine.drawReason(state) != null) return 0;
        ChessEngine engine = new ChessEngine(state);
        List<Move> moves = engine.getAllLegalMoves(state.whiteToMove);
        if (moves.isEmpty()) {
            return engine.isKingInCheck(state, state.whiteToMove) ? -(MATE - ply) : 0;
        }
        if (depth <= 0) {
            return difficulty.quiescence
                ? quiescence(state, engine, alpha, beta, ply, MAX_QUIESCENCE_PLIES)
                : evaluateForSideToMove(state, engine);
        }
        orderMoves(state, moves);
        int best = -INFINITY;
        for (Move m : moves) {
            int score = -negamax(engine.simulateMove(state, m), depth - 1, -beta, -alpha, ply + 1);
            if (score > best) best = score;
            if (score > alpha) alpha = score;
            if (alpha >= beta) break;
        }
        return best;
    }

    /** Keeps resolving captures so the search doesn't stop in the middle of an exchange. */
    private int quiescence(GameState state, ChessEngine engine, int alpha, int beta, int ply, int pliesLeft) {
        checkTime();
        int standPat = evaluateForSideToMove(state, engine);
        if (pliesLeft == 0 || standPat >= beta) return standPat;
        if (standPat > alpha) alpha = standPat;

        List<Move> captures = new ArrayList<>();
        for (Move m : engine.getAllLegalMoves(state.whiteToMove)) {
            if (m.isCapture || m.isPromotion) captures.add(m);
        }
        orderMoves(state, captures);
        for (Move m : captures) {
            GameState next = engine.simulateMove(state, m);
            int score = -quiescence(next, new ChessEngine(next), -beta, -alpha, ply + 1, pliesLeft - 1);
            if (score >= beta) return score;
            if (score > alpha) alpha = score;
        }
        return alpha;
    }

    private void checkTime() {
        nodes++;
        if ((nodes & 63) == 0 && System.nanoTime() > deadline) throw new Timeout();
    }

    // ─── MOVE ORDERING ──────────────────────────────────────────

    /** Promotions and captures first (most valuable victim, cheapest attacker), then the rest. */
    private void orderMoves(GameState s, List<Move> moves) {
        Map<Move, Integer> key = new HashMap<>();
        for (Move m : moves) key.put(m, orderScore(s, m));
        moves.sort((a, b) -> key.get(b) - key.get(a));
    }

    private int orderScore(GameState s, Move m) {
        int score = 0;
        if (m.isPromotion) score += "Q".equals(m.promotionType) ? 9000 : 1000;
        if (m.isCapture) {
            Piece victim = m.isEnPassant ? s.board[m.fromRow][m.toCol]
                : m.isSniperShot ? s.board[m.sniperTargetRow][m.sniperTargetCol] : s.board[m.toRow][m.toCol];
            Piece attacker = s.board[m.fromRow][m.fromCol];
            score += 10 * pieceValue(victim) - pieceValue(attacker) / 10 + 100;
        }
        return score;
    }

    // ─── EVALUATION ─────────────────────────────────────────────

    private int evaluateForSideToMove(GameState s, ChessEngine engine) {
        int blackScore = evaluate(s, engine);
        return s.whiteToMove ? -blackScore : blackScore;
    }

    /** Static evaluation from Black's point of view (positive = good for Black). */
    private int evaluate(GameState s, ChessEngine engine) {
        int score = 0;
        int totalMaterial = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = s.board[r][c];
                if (p != null && !(p instanceof King)) totalMaterial += Economy.material(p);
            }
        }
        boolean endgame = totalMaterial <= 20;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = s.board[r][c];
                if (p == null) continue;
                int value = pieceValue(p);
                if (difficulty.evalTier >= 2) value += placement(p, r, c, endgame);
                score += p.isWhite() ? -value : value;
            }
        }

        if (difficulty.evalTier >= 1) {
            score += (engine.countPseudoLegalMoves(false) - engine.countPseudoLegalMoves(true)) * 10;
            for (int r = 3; r <= 4; r++) {
                for (int c = 3; c <= 4; c++) {
                    Piece p = s.board[r][c];
                    if (p != null) score += p.isWhite() ? -20 : 20;
                }
            }
        }
        return score;
    }

    static int pieceValue(Piece p) {
        if (p instanceof Pawn) return 100;
        if (p instanceof Knight) return 320;
        if (p instanceof Bishop) return 330;
        if (p instanceof Rook) return 500;
        if (p instanceof Queen) return 900;
        if (p instanceof King) return 20000;
        return 0;
    }

    /** Simple piece placement bonuses (tier 2). Written for White and mirrored for Black. */
    private static int placement(Piece p, int row, int col, boolean endgame) {
        int r = p.isWhite() ? row : 7 - row; // r = 7 is the piece's own back rank
        int centre = (int) ((3.5 - Math.abs(r - 3.5)) + (3.5 - Math.abs(col - 3.5))); // 0..6
        if (p instanceof Pawn) return (6 - r) * 10 + (col >= 2 && col <= 5 ? 5 : 0);
        if (p instanceof Knight) return centre * 8 - 15;
        if (p instanceof Bishop) return centre * 5;
        if (p instanceof Rook) return r == 1 ? 25 : 0;      // rook on the 7th rank
        if (p instanceof Queen) return centre * 2;
        if (p instanceof King) return endgame ? centre * 10 : (7 - r) * -15; // shelter early, centralise late
        return 0;
    }
}
