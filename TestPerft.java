import java.util.*;

/**
 * Move-generation tests ("perft"): counts every legal move sequence to a fixed depth and
 * compares with known-correct numbers. Castling is not part of this variant, so only
 * positions without castling rights are used.
 *
 * Two paths are checked:
 *   - engine path: ChessEngine.applyMove / undo (what the real game uses)
 *   - search path: ChessEngine.simulateMove on copies (what the AI uses)
 * Both must agree, otherwise the AI is searching with different rules than the game.
 *
 * Run: java TestPerft
 */
public class TestPerft {

    public static void main(String[] args) {
        System.out.println("== TestPerft ==");
        TestSupport.quiet(() -> {});
        String[][] cases = {
            // name, FEN, expected counts per depth
            {"start position", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0", "20,400,8902,197281"},
            {"CPW position 3 (pins, ep, checks)", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0", "14,191,2812,43238"},
            {"promotions", "n1n5/PPPk4/8/8/8/8/4Kppp/5N1N b - - 0", "24,496,9483"},
        };
        for (String[] c : cases) {
            String[] expected = c[2].split(",");
            for (int depth = 1; depth <= expected.length; depth++) {
                long want = Long.parseLong(expected[depth - 1]);
                final int d = depth;
                long[] got = new long[2];
                TestSupport.quiet(() -> {
                    got[0] = perftEngine(new ChessEngine(TestSupport.fromFen(c[1])), d);
                    got[1] = perftSearch(TestSupport.fromFen(c[1]), d);
                });
                TestSupport.checkEquals(want, got[0], c[0] + " depth " + depth + " (engine path)");
                TestSupport.checkEquals(want, got[1], c[0] + " depth " + depth + " (search path)");
            }
        }
        TestSupport.finish("TestPerft");
    }

    static long perftEngine(ChessEngine engine, int depth) {
        List<Move> moves = engine.getAllLegalMoves(engine.getGameState().whiteToMove);
        if (depth == 1) return moves.size();
        long n = 0;
        for (Move m : moves) {
            if (!engine.applyMove(m)) throw new IllegalStateException("generated move rejected: " + m);
            n += perftEngine(engine, depth - 1);
            engine.undo();
        }
        return n;
    }

    static long perftSearch(GameState state, int depth) {
        ChessEngine engine = new ChessEngine(state);
        List<Move> moves = engine.getAllLegalMoves(state.whiteToMove);
        if (depth == 1) return moves.size();
        long n = 0;
        for (Move m : moves) {
            n += perftSearch(engine.simulateMove(state, m), depth - 1);
        }
        return n;
    }
}
