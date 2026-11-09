import java.util.*;

/**
 * AI tests: card blindness, real-rules legality, time limit, difficulty curve, either colour.
 *
 * Run: java TestAI
 */
public class TestAI {

    public static void main(String[] args) {
        System.out.println("== TestAI ==");
        TestSupport.quiet(() -> {});
        cardBlindness();
        legalUnderRealRules();
        timeLimit();
        difficultyCurve();
        playsEitherColour();
        TestSupport.finish("TestAI");
    }

    static void cardBlindness() {
        System.out.println("-- the AI plans with plain chess");
        GameState s = TestSupport.fromFen("3q3k/8/8/8/3R4/8/8/K7 b - - 0");
        s.cardManager.addCard(new CardMinefield());
        s.minefieldSquares.add(4 * 8 + 3); // the rook stands on a mine
        Move m = new AIPlayer(new ChessEngine(s), 2).chooseMove(s);
        TestSupport.checkEquals("d8d4", m == null ? null : m.toUCI(), "AI grabs the rook, not knowing about the mine");
        ChessEngine e = new ChessEngine(s);
        e.applyMove(m);
        TestSupport.checkEquals(null, s.board[4][3], "...and its queen is blown up");
    }

    static void legalUnderRealRules() {
        System.out.println("-- the AI's move is always legal under the real rules");
        // In check from the queen; plain chess says Rxa8, but Trench Warfare forbids moving backward.
        GameState s = TestSupport.fromFen("Q6k/8/8/8/8/8/r7/7K b - - 0");
        s.cardManager.addCard(new CardTrenchWarfare());
        Move m = new AIPlayer(new ChessEngine(s), 3).chooseMove(s);
        TestSupport.check(m != null && !m.toUCI().equals("a2a8"), "AI doesn't play the forbidden Rxa8 (played " + m + ")");
        TestSupport.check(m != null && new ChessEngine(s).isMoveLegal(m), "AI's move is legal for the real game");
    }

    static void timeLimit() {
        System.out.println("-- time limit");
        GameState s = TestSupport.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b - - 0");
        Difficulty d = new Difficulty(0, 8, 300, 2, true, 0);
        long t0 = System.nanoTime();
        Move m = new AIPlayer(d, new Random(1)).chooseMove(s);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        TestSupport.check(m != null && ms < 1500, "depth-8 search with a 300 ms limit returns in time (" + ms + " ms)");
    }

    static void difficultyCurve() {
        System.out.println("-- difficulty table");
        boolean ok = true;
        Difficulty prev = Difficulty.forLevel(1);
        for (int level = 2; level <= 20; level++) {
            Difficulty d = Difficulty.forLevel(level);
            if (d.searchDepth < prev.searchDepth || d.timeLimitMs < prev.timeLimitMs
                    || d.evalTier < prev.evalTier || d.blunderPercent > prev.blunderPercent) ok = false;
            prev = d;
        }
        TestSupport.check(ok, "every level is at least as strong as the one before");
        TestSupport.check(Difficulty.forLevel(1).blunderPercent > 0 && Difficulty.forLevel(10).blunderPercent == 0,
            "early levels blunder sometimes, later levels never");
    }

    static void playsEitherColour() {
        System.out.println("-- either colour");
        GameState s = TestSupport.fromFen("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0");
        Move m = new AIPlayer(new ChessEngine(s), 2).chooseMove(s);
        TestSupport.checkEquals("a1a8", m == null ? null : m.toUCI(), "as White it finds Ra8 mate");
    }
}
