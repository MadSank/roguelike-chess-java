import java.util.*;

/**
 * Rules and AI sanity tests: game end (mate, stalemate, draws), promotion choice,
 * en passant bookkeeping, the "cards never capture a King" rule, and whether the AI
 * finds and avoids mate.
 *
 * Run: java TestRules
 */
public class TestRules {

    public static void main(String[] args) {
        System.out.println("== TestRules ==");
        TestSupport.quiet(() -> {});
        gameEnd();
        promotion();
        enPassant();
        noKingCaptures();
        aiMate();
        TestSupport.finish("TestRules");
    }

    static void gameEnd() {
        System.out.println("-- game end");
        ChessEngine e = new ChessEngine(TestSupport.fromFen("r6k/1p6/2N5/8/8/8/5PPP/6K1 b - - 0"));
        TestSupport.play(e, "a8a1");
        TestSupport.checkEquals(GameResult.BLACK_WINS, e.getGameState().result, "back-rank mate gives BLACK_WINS");

        e = new ChessEngine(TestSupport.fromFen("7k/8/5QK1/8/8/8/8/8 w - - 0"));
        TestSupport.play(e, "f6f7");
        TestSupport.checkEquals(GameResult.DRAW, e.getGameState().result, "stalemate is a DRAW");

        e = new ChessEngine(TestSupport.fromFen("k7/8/8/8/8/8/8/KR6 w - - 99"));
        TestSupport.play(e, "b1b2");
        TestSupport.checkEquals(GameResult.DRAW, e.getGameState().result, "50-move rule is a DRAW");

        e = new ChessEngine(TestSupport.fromFen("k7/8/8/8/8/8/1n6/K7 w - - 0"));
        TestSupport.play(e, "a1b2");
        TestSupport.checkEquals(GameResult.DRAW, e.getGameState().result, "K v K (insufficient material) is a DRAW");

        e = new ChessEngine(TestSupport.fromFen("k6n/8/8/8/8/8/8/K6N w - - 0"));
        TestSupport.play(e, "h1g3", "h8g6", "g3h1", "g6h8", "h1g3", "h8g6", "g3h1");
        TestSupport.checkEquals(GameResult.ONGOING, e.getGameState().result, "position seen twice is not yet a draw");
        TestSupport.play(e, "g6h8");
        TestSupport.checkEquals(GameResult.DRAW, e.getGameState().result, "threefold repetition is a DRAW");

        TestSupport.check(!e.applyMove(new Move(7, 7, 5, 6)), "no moves accepted after the game is over");
    }

    static void promotion() {
        System.out.println("-- promotion");
        ChessEngine e = new ChessEngine(TestSupport.fromFen("8/P6k/8/8/8/8/8/K7 w - - 0"));
        List<String> promos = new ArrayList<>();
        for (Move m : e.getLegalMovesFromSquare(1, 0)) promos.add(m.toUCI());
        TestSupport.checkEquals(Arrays.asList("a7a8q", "a7a8r", "a7a8b", "a7a8n"), promos, "all four promotion choices offered");
        TestSupport.play(e, "a7a8n");
        TestSupport.check(e.getGameState().board[0][0] instanceof Knight, "under-promotion to a knight is applied");
    }

    static void enPassant() {
        System.out.println("-- en passant");
        GameState s = TestSupport.fromFen("k7/p7/8/8/4P3/8/3PN3/K7 b - e3 0");
        ChessEngine e = new ChessEngine(s);
        GameState after = e.simulateMove(s, new Move(1, 0, 2, 0));
        TestSupport.checkEquals(null, after.enPassantTarget, "en passant square is cleared after the next move in search");

        ChessEngine e2 = new ChessEngine(TestSupport.fromFen("k7/8/8/3pP3/8/8/8/K7 w - d6 0"));
        TestSupport.play(e2, "e5d6");
        TestSupport.checkEquals(null, e2.getGameState().board[3][3], "en passant removes the captured pawn");
    }

    static void noKingCaptures() {
        System.out.println("-- card abilities never capture a King");
        GameState s = TestSupport.fromFen("7k/8/8/8/8/8/8/B6K w - - 0");
        s.cardManager.addCard(new CardSniperBishops());
        ChessEngine e = new ChessEngine(s);
        boolean kingShot = false;
        for (Move m : e.getAllLegalMoves(true)) {
            if (m.isSniperShot && s.board[m.sniperTargetRow][m.sniperTargetCol] instanceof King) kingShot = true;
        }
        TestSupport.check(!kingShot, "Sniper Bishops cannot shoot the king");

        GameState v = TestSupport.fromFen("8/8/8/k7/p7/8/8/R6K w - - 0");
        v.cardManager.addCard(new CardVaultingMajors());
        v.vaultingPieceSquare = 7 * 8;
        ChessEngine ve = new ChessEngine(v);
        boolean vaultsKing = false;
        for (Move m : ve.getLegalMovesFromSquare(7, 0)) {
            if (v.board[m.toRow][m.toCol] instanceof King) vaultsKing = true;
        }
        TestSupport.check(!vaultsKing, "Vaulting Majors cannot vault onto the king");
    }

    static void aiMate() {
        System.out.println("-- AI and mate");
        String mateInOne = "r6k/1p6/2N5/8/8/8/5PPP/6K1 b - - 0"; // Ra1# beats taking the knight
        for (int d = 1; d <= 3; d++) {
            GameState s = TestSupport.fromFen(mateInOne);
            Move m = new AIPlayer(new ChessEngine(s), d).chooseMove(s);
            TestSupport.checkEquals("a8a1", m == null ? null : m.toUCI(), "AI depth " + d + " finds mate in 1");
        }

        String threatened = "6k1/5ppp/8/8/8/2n5/8/R5K1 b - - 0"; // white threatens Ra8#
        for (int d = 2; d <= 3; d++) {
            GameState s = TestSupport.fromFen(threatened);
            ChessEngine e = new ChessEngine(s);
            Move m = new AIPlayer(e, d).chooseMove(s);
            e.applyMove(m);
            boolean whiteCanMate = false;
            for (Move w : e.getAllLegalMoves(true)) {
                ChessEngine probe = new ChessEngine(e.getGameState().copy());
                probe.applyMove(w);
                if (probe.getGameState().result == GameResult.WHITE_WINS) whiteCanMate = true;
            }
            TestSupport.check(!whiteCanMate, "AI depth " + d + " stops a mate-in-1 threat (played " + m + ")");
        }
    }
}
