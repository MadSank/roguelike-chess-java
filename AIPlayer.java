import java.util.*;

/**
 * The opponent. Always plays Black. Minimax with alpha-beta pruning; scores are from
 * Black's point of view (positive = good for the AI).
 *
 * Search uses ChessEngine.simulateMove, the same move code the real game uses.
 */
public class AIPlayer {
    private final int searchDepth;

    private static final int PAWN_VALUE = 100;
    private static final int KNIGHT_VALUE = 320;
    private static final int BISHOP_VALUE = 330;
    private static final int ROOK_VALUE = 500;
    private static final int QUEEN_VALUE = 900;
    private static final int KING_VALUE = 20000;

    /** Score for delivering mate; mates found sooner score higher. */
    private static final int MATE = 10_000_000;
    private static final int INFINITY = 1_000_000_000;
    private static final int DRAW = 0;

    public AIPlayer(ChessEngine engine, int searchDepth) {
        this.searchDepth = Math.max(1, searchDepth);
    }

    public int getSearchDepth() {
        return searchDepth;
    }

    /** Picks Black's move in the given position, or null if Black has no legal move. */
    public Move chooseMove(GameState state) {
        ChessEngine engine = new ChessEngine(state);
        List<Move> legalMoves = engine.getAllLegalMoves(false);
        if (legalMoves.isEmpty()) {
            return null;
        }
        Move bestMove = legalMoves.get(0);
        int bestScore = -INFINITY;
        int alpha = -INFINITY;
        int beta = INFINITY;
        for (Move move : legalMoves) {
            GameState next = engine.simulateMove(state, move);
            int score = minimax(next, searchDepth - 1, alpha, beta, 1);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            alpha = Math.max(alpha, score);
        }
        return bestMove;
    }

    /**
     * @param ply distance from the root, used to prefer faster mates and slower losses
     */
    private int minimax(GameState state, int depth, int alpha, int beta, int ply) {
        if (ChessEngine.drawReason(state) != null) {
            return DRAW;
        }
        boolean blackToMove = !state.whiteToMove;
        ChessEngine engine = new ChessEngine(state);
        List<Move> moves = engine.getAllLegalMoves(state.whiteToMove);

        if (moves.isEmpty()) {
            if (engine.isKingInCheck(state, state.whiteToMove)) {
                // Side to move is checkmated.
                return blackToMove ? -(MATE - ply) : (MATE - ply);
            }
            return DRAW; // stalemate
        }
        if (depth <= 0) {
            return evaluatePosition(state, engine, moves.size());
        }

        if (blackToMove) {
            int best = -INFINITY;
            for (Move move : moves) {
                int eval = minimax(engine.simulateMove(state, move), depth - 1, alpha, beta, ply + 1);
                best = Math.max(best, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;
            }
            return best;
        } else {
            int best = INFINITY;
            for (Move move : moves) {
                int eval = minimax(engine.simulateMove(state, move), depth - 1, alpha, beta, ply + 1);
                best = Math.min(best, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;
            }
            return best;
        }
    }

    /**
     * Static evaluation of a non-terminal position.
     * @param sideToMoveMobility number of legal moves for the side to move (already computed)
     */
    private int evaluatePosition(GameState state, ChessEngine engine, int sideToMoveMobility) {
        int score = 0;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = state.board[r][c];
                if (piece != null) {
                    int value = getPieceValue(piece);

                    // Card-aware: stunned pieces are worth less
                    if (state.stunnedPieces != null && state.stunnedPieces.containsKey(r * 8 + c)) {
                        value = value * 60 / 100;
                    }

                    // Card-aware: pieces near minefields lose value
                    if (state.minefieldSquares != null && !state.minefieldSquares.isEmpty()) {
                        for (int mineIdx : state.minefieldSquares) {
                            int mr = mineIdx / 8, mc = mineIdx % 8;
                            if (Math.abs(r - mr) <= 1 && Math.abs(c - mc) <= 1) {
                                value = value * 85 / 100;
                                break;
                            }
                        }
                    }

                    score += piece.isWhite() ? -value : value;
                }
            }
        }

        // Mobility: pseudo-legal move counts (cheap, and the same measure for both sides).
        int whiteMobility = engine.countPseudoLegalMoves(true);
        int blackMobility = engine.countPseudoLegalMoves(false);
        score += (blackMobility - whiteMobility) * 10;

        score += evaluateCenterControl(state);

        // Card-aware: phalanx bonus for adjacent pawns
        if (state.cardManager != null && state.cardManager.hasCard(CardPawnPhalanx.class)) {
            score += evaluatePhalanxBonus(state);
        }

        return score;
    }

    private int evaluatePhalanxBonus(GameState state) {
        int bonus = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 7; c++) {
                Piece p1 = state.board[r][c];
                Piece p2 = state.board[r][c + 1];
                if (p1 instanceof Pawn && p2 instanceof Pawn && p1.isWhite() == p2.isWhite()) {
                    bonus += p1.isWhite() ? -25 : 25;
                }
            }
        }
        return bonus;
    }

    private int getPieceValue(Piece piece) {
        if (piece instanceof Pawn) return PAWN_VALUE;
        if (piece instanceof Knight) return KNIGHT_VALUE;
        if (piece instanceof Bishop) return BISHOP_VALUE;
        if (piece instanceof Rook) return ROOK_VALUE;
        if (piece instanceof Queen) return QUEEN_VALUE;
        if (piece instanceof King) return KING_VALUE;
        return 0;
    }

    private int evaluateCenterControl(GameState state) {
        int score = 0;
        int[] center = {3, 4};
        for (int r : center) {
            for (int c : center) {
                Piece piece = state.board[r][c];
                if (piece != null) {
                    score += piece.isWhite() ? -20 : 20;
                }
            }
        }
        return score;
    }
}
