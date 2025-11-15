package ashes;

import java.util.*;

public class AIPlayer {
    private ChessEngine engine;
    private int searchDepth;
    private Random rng;

    private static final int PAWN_VALUE = 100;
    private static final int KNIGHT_VALUE = 320;
    private static final int BISHOP_VALUE = 330;
    private static final int ROOK_VALUE = 500;
    private static final int QUEEN_VALUE = 900;
    private static final int KING_VALUE = 20000;

    public AIPlayer(ChessEngine engine, int searchDepth) {
        this.engine = engine;
        this.searchDepth = searchDepth;
        this.rng = new Random();
    }

    public Move chooseMove(GameState state) {
        List<Move> legalMoves = getAllLegalMoves(state, false);
        if (legalMoves.isEmpty()) {
            return null;
        }
        Move bestMove = null;
        int bestScore = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;
        for (Move move : legalMoves) {
            GameState newState = simulateMove(state, move);
            int score = minimax(newState, searchDepth - 1, alpha, beta, false);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            alpha = Math.max(alpha, score);
        }
        return bestMove;
    }

    private int minimax(GameState state, int depth, int alpha, int beta, boolean maximizing) {
        if (depth == 0 || state.gameOver) {
            return evaluatePosition(state);
        }
        List<Move> moves = getAllLegalMoves(state, !maximizing);
        if (moves.isEmpty()) {
            return evaluatePosition(state);
        }
        if (maximizing) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                GameState newState = simulateMove(state, move);
                int eval = minimax(newState, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                GameState newState = simulateMove(state, move);
                int eval = minimax(newState, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;
            }
            return minEval;
        }
    }

    private int evaluatePosition(GameState state) {
        int score = 0;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = state.board[r][c];
                if (piece != null) {
                    int value = getPieceValue(piece);
                    score += piece.isWhite() ? -value : value;
                }
            }
        }

        int whiteMobility = getAllLegalMoves(state, true).size();
        int blackMobility = getAllLegalMoves(state, false).size();
        score += (blackMobility - whiteMobility) * 10;

        score += evaluateCenterControl(state);
        return score;
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
        int[] centerRows = {3, 4};
        int[] centerCols = {3, 4};
        for (int r : centerRows) {
            for (int c : centerCols) {
                Piece piece = state.board[r][c];
                if (piece != null) {
                    score += piece.isWhite() ? -20 : 20;
                }
            }
        }
        return score;
    }

    private List<Move> getAllLegalMoves(GameState state, boolean forWhite) {
        List<Move> moves = new ArrayList<>();

        ChessEngine tempEngine = new ChessEngine(state);

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = state.board[r][c];
                if (piece != null && piece.isWhite() == forWhite) {
                    List<Move> pieceMoves = piece.generateLegalMoves(r, c, tempEngine);
                    for (Move move : pieceMoves) {
                        if (tempEngine.isMoveLegal(move)) {
                            moves.add(move);
                        }
                    }
                }
            }
        }
        return moves;
    }

    private GameState simulateMove(GameState state, Move move) {
        GameState newState = state.copy();
        Piece piece = newState.board[move.fromRow][move.fromCol];
        Piece captured = newState.board[move.toRow][move.toCol];
        newState.board[move.toRow][move.toCol] = piece;
        newState.board[move.fromRow][move.fromCol] = null;
        if (captured != null) {
            move.isCapture = true;
        }
        newState.whiteToMove = !newState.whiteToMove;
        return newState;
    }
}