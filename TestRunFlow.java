import java.io.File;
import java.util.*;

/**
 * Run-loop tests (DESIGN.md): economy numbers, shop rules, win / draw / loss outcomes,
 * game over, and good-move bonuses. Drives RunController with no UI.
 *
 * Run: java TestRunFlow
 */
public class TestRunFlow {

    public static void main(String[] args) throws Exception {
        System.out.println("== TestRunFlow ==");
        TestSupport.quiet(() -> {});
        economy();
        shopRules();
        gameOverCheck();
        winThenLossThenGameOver();
        draw();
        bonuses();
        handRules();
        timeStopFlow();
        TestSupport.finish("TestRunFlow");
    }

    static RunController newRun() throws Exception {
        File f = File.createTempFile("ashes-test-leaderboard", ".dat");
        f.delete();
        f.deleteOnExit();
        RunController run = new RunController(new ScoreManager(f), new Chess960Setup(7), new Random(7));
        run.startNewRun("Tester");
        return run;
    }

    static void economy() {
        System.out.println("-- economy");
        TestSupport.checkEquals(2, Economy.requirement(1), "level 1 needs 2 material");
        TestSupport.checkEquals(39, Economy.requirement(14), "level 14 needs a full board (39)");
        TestSupport.checkEquals(39, Economy.requirement(30), "requirement stays at 39 after level 14");
        TestSupport.checkEquals(2, Economy.pawnCap(0, 0), "King alone allows 2 pawns");
        TestSupport.checkEquals(4, Economy.pawnCap(2, 0), "two rooks allow 4 pawns");
        TestSupport.checkEquals(8, Economy.pawnCap(6, 1), "full board allows 8 pawns");
        Economy.Payout win = Economy.payout(GameResult.WHITE_WINS, 5, 4, 6, 3);
        TestSupport.checkEquals(2 * 5 + 3 * 4 + 6 + Economy.levelBase(3), win.total, "win pays own + defeated + bonuses + level base");
        Economy.Payout draw = Economy.payout(GameResult.DRAW, 5, 4, 6, 3);
        TestSupport.checkEquals(win.total * Economy.DRAW_PERCENT / 100, draw.total, "draw pays 30% of the win formula");
        TestSupport.checkEquals(0, Economy.payout(GameResult.BLACK_WINS, 5, 4, 6, 3).total, "loss pays nothing");
    }

    static void shopRules() {
        System.out.println("-- shop rules");
        ShopManager shop = new ShopManager();
        TestSupport.checkEquals(2, shop.armyMaterial(), "new run: King + 2 pawns");
        shop.setGold(200);
        TestSupport.checkEquals(ShopManager.Result.PAWN_CAP, shop.buyPiece("Pawn"), "3rd pawn blocked by the pawn cap");
        TestSupport.checkEquals(ShopManager.Result.OK, shop.buyPiece("Knight"), "knight bought");
        TestSupport.checkEquals(ShopManager.Result.OK, shop.buyPiece("Pawn"), "knight raises the cap: 3rd pawn allowed");
        TestSupport.checkEquals(200 - 15 - 5, shop.getGold(), "prices charged");
        shop.sellPiece("Knight");
        TestSupport.checkEquals(200 - 15 - 5 + 7, shop.getGold(), "selling refunds half (15 -> 7)");
        TestSupport.checkEquals(ShopManager.Result.NOT_OWNED, shop.sellPiece("Queen"), "can't sell what you don't own");

        ShopManager full = new ShopManager();
        full.setGold(1000);
        for (int i = 0; i < 7; i++) full.buyPiece("Knight");
        TestSupport.checkEquals(ShopManager.Result.NO_ROOM, full.buyPiece("Rook"), "back rank holds 7 pieces besides the King");

        ShopManager cards = new ShopManager();
        cards.setGold(100);
        cards.generateCardOfferings(1, new Random(1));
        Card offered = cards.getFaceUpCards().get(0);
        TestSupport.checkEquals(ShopManager.Result.OK, cards.buyFaceUpCard(offered), "face-up card bought");
        TestSupport.checkEquals(100 - Economy.faceUpCardPrice(offered), cards.getGold(), "face-up card price charged");
        cards.sellCard(offered);
        TestSupport.checkEquals(100 - Economy.faceUpCardPrice(offered) + offered.getCost() / 2, cards.getGold(),
            "selling a card refunds half its base cost");
        Set<String> names = new HashSet<>();
        cards.generateCardOfferings(2, new Random(2));
        for (Card c : cards.getFaceUpCards()) names.add(c.getName());
        boolean overlap = false;
        for (Card c : cards.getMysteryPackCards()) if (names.contains(c.getName())) overlap = true;
        TestSupport.check(!overlap, "mystery pack never repeats a face-up card");
    }

    static void gameOverCheck() {
        System.out.println("-- can the run continue?");
        ShopManager shop = new ShopManager();
        shop.clearArmy();
        TestSupport.check(!shop.canStillReach(2), "no army, no gold: level 2 unreachable");
        shop.setGold(15);
        TestSupport.check(!shop.canStillReach(2), "15 gold buys at most 3 material: still short of 4");
        shop.setGold(20);
        TestSupport.check(shop.canStillReach(2), "20 gold buys a knight + pawn: reachable");
        shop.setGold(15);
        shop.getCards().addCard(new CardGoldenEdge()); // sells for 5
        TestSupport.check(shop.canStillReach(2), "card resale counts toward the budget");
    }

    static void forceResult(RunController run, GameResult r) {
        run.getRoundState().setResult(r, r.name());
    }

    static void winThenLossThenGameOver() throws Exception {
        System.out.println("-- win, then loss, then game over");
        RunController run = newRun();
        GameState s = run.getRoundState();
        TestSupport.checkEquals(RunController.Phase.PLAYING, run.getPhase(), "new run starts level 1 straight away");
        TestSupport.checkEquals(2, Economy.material(s, true), "white has 2 pawns of material");
        TestSupport.checkEquals(2, Economy.material(s, false), "black mirrors the army");

        // Win having taken one black pawn.
        outer:
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            if (s.board[r][c] instanceof Pawn && !s.board[r][c].isWhite()) { s.board[r][c] = null; break outer; }
        }
        forceResult(run, GameResult.WHITE_WINS);
        RunController.RoundSummary win = run.finishRound();
        int expected = 2 * 2 + 3 * 1 + Economy.FAST_MATE_BONUS + Economy.levelBase(1);
        TestSupport.checkEquals(expected, win.payout.total, "win payout: 2 own, 1 defeated, fast mate, level base");
        TestSupport.checkEquals(2, run.getLevel(), "win moves on to level 2");
        TestSupport.checkEquals(1, run.getHighestLevelBeaten(), "level 1 recorded as beaten");
        TestSupport.checkEquals(2, run.getShop().armyMaterial(), "survivors carry over");
        TestSupport.checkEquals(RunController.Phase.SHOP, run.getPhase(), "then the shop opens");

        TestSupport.check(!run.finishShopping(), "can't start level 2 (needs 4) with 2 material");
        TestSupport.checkEquals(ShopManager.Result.OK, run.getShop().buyPiece("Knight"), "buy a knight");
        TestSupport.check(run.finishShopping(), "with the knight (5 material) level 2 starts");

        int goldBefore = run.getShop().getGold();
        forceResult(run, GameResult.BLACK_WINS);
        RunController.RoundSummary loss = run.finishRound();
        TestSupport.checkEquals(0, loss.payout.total, "loss pays nothing");
        TestSupport.checkEquals(0, run.getShop().armyMaterial(), "loss leaves only the King");
        TestSupport.checkEquals(2, run.getLevel(), "loss replays the same level");
        TestSupport.checkEquals(goldBefore, run.getShop().getGold(), "gold kept after a loss");
        TestSupport.check(loss.gameOver, goldBefore + " gold can't rebuild 4 material, so the run is over");
        TestSupport.checkEquals(RunController.Phase.GAME_OVER, run.getPhase(), "phase is GAME_OVER");
        TestSupport.check(loss.madeLeaderboard && run.getScores().getEntries().get(0).getHighestLevelBeaten() == 1,
            "run recorded on the leaderboard with level 1");
    }

    static void draw() throws Exception {
        System.out.println("-- draw");
        RunController run = newRun();
        forceResult(run, GameResult.DRAW);
        RunController.RoundSummary d = run.finishRound();
        int win = 2 * 2 + 0 + Economy.levelBase(1);
        TestSupport.checkEquals(win * Economy.DRAW_PERCENT / 100, d.payout.total, "draw pays 30%, no fast-mate bonus");
        TestSupport.checkEquals(1, run.getLevel(), "draw replays the same level");
        TestSupport.checkEquals(2, run.getShop().armyMaterial(), "survivors kept after a draw");
        TestSupport.checkEquals(0, run.getHighestLevelBeaten(), "a draw doesn't count as beating the level");
    }

    static void bonuses() {
        System.out.println("-- good-move bonuses");
        checkBonus("k7/8/8/8/8/8/8/K6R w - - 0", "h1h8", "Check");
        checkBonus("k7/8/8/8/1q3r2/8/8/K3N3 w - - 0", "e1d3", "Fork");
        checkBonus("k7/8/8/8/8/3n4/4P3/K7 w - - 0", "e2d3", "Trade up");
        checkBonus("8/P6k/8/8/8/8/8/K7 w - - 0", "a7a8q", "Promotion");
        checkBonus("k7/8/8/3pP3/8/8/8/K7 w - d6 0", "e5d6", "En passant");

        GameState s = TestSupport.fromFen("k7/8/8/8/3n4/8/8/B6K w - - 0");
        s.cardManager.addCard(new CardSniperBishops());
        ChessEngine e = new ChessEngine(s);
        Move snipe = null;
        for (Move m : e.getLegalMovesFromSquare(7, 0)) if (m.isSniperShot) snipe = m;
        GameState after = e.simulateMove(s, snipe);
        TestSupport.check(names(MoveBonuses.detect(s, snipe, after)).contains("Card capture"), "sniper capture earns Card capture");

        GameState quiet = TestSupport.fromFen("k7/8/8/8/8/8/8/K6R w - - 0");
        ChessEngine qe = new ChessEngine(quiet);
        Move m = TestSupport.findMove(qe, "h1h2");
        TestSupport.check(MoveBonuses.detect(quiet, m, qe.simulateMove(quiet, m)).isEmpty(), "a quiet move earns nothing");
    }

    static void handRules() throws Exception {
        System.out.println("-- hand and card use");
        TestSupport.checkEquals(5, RunController.handSize(1), "hand holds 5 at level 1");
        TestSupport.checkEquals(6, RunController.handSize(4), "+1 at level 4");
        TestSupport.checkEquals(8, RunController.handSize(10), "8 at level 10");

        RunController run = newRun();
        forceResult(run, GameResult.DRAW);
        run.finishRound();
        Card edge = new CardGoldenEdge(), surge = new CardPawnSurge();
        run.getShop().getCards().addCard(edge);
        run.getShop().getCards().addCard(surge);
        TestSupport.check(!run.setHand(Arrays.asList(new CardTimeStop())), "can't take a card you don't own");
        TestSupport.check(run.setHand(Arrays.asList(edge, surge)), "hand chosen from the stash");
        TestSupport.check(run.finishShopping(), "round starts");
        TestSupport.check(run.getRoundState().cardManager.getActiveCards().isEmpty(), "no card is active until played");

        TestSupport.checkEquals(null, run.playCard(edge), "card played");
        TestSupport.check(run.getRoundState().whiteToMove, "playing a card doesn't use your move");
        TestSupport.check(run.getRoundState().cardManager.isActive(edge), "played card is active");
        TestSupport.check(run.playCard(edge) != null, "a card can't be played twice");

        forceResult(run, GameResult.DRAW);
        RunController.RoundSummary d = run.finishRound();
        List<Card> stash = run.getShop().getCards().getActiveCards();
        TestSupport.check(!stash.contains(edge) && stash.contains(surge), "draw: played card used up, unplayed card kept");
        TestSupport.checkEquals(Arrays.asList("Golden Edge"), d.cardsUsedUp, "summary lists the used-up card");

        run.setHand(Arrays.asList(surge));
        run.finishShopping();
        forceResult(run, GameResult.BLACK_WINS);
        run.finishRound();
        TestSupport.check(run.getShop().getCards().getActiveCards().isEmpty(), "loss: unplayed hand cards are lost too");
    }

    static void timeStopFlow() throws Exception {
        System.out.println("-- Time Stop in a round");
        RunController run = newRun();
        forceResult(run, GameResult.DRAW);
        run.finishRound();
        Card stop = new CardTimeStop();
        run.getShop().getCards().addCard(stop);
        run.setHand(Arrays.asList(stop));
        run.finishShopping();
        run.playCard(stop);
        ChessEngine e = run.getEngine();
        Move quiet = null;
        for (Move m : e.getAllLegalMoves(true)) {
            GameState after = e.simulateMove(e.getGameState(), m);
            if (!m.isCapture && !e.isKingInCheck(after, false)) { quiet = m; break; }
        }
        run.playerMove(quiet);
        TestSupport.check(run.getRoundState().whiteToMove, "after your move it's your turn again");
        TestSupport.check(run.takeEvents().contains("Time Stop: the AI skips its turn"), "the UI is told why");
    }

    static void checkBonus(String fen, String uci, String bonus) {
        GameState s = TestSupport.fromFen(fen);
        ChessEngine e = new ChessEngine(s);
        Move m = TestSupport.findMove(e, uci);
        if (m == null) {
            TestSupport.check(false, uci + " should be legal for the " + bonus + " test");
            return;
        }
        List<String> got = names(MoveBonuses.detect(s, m, e.simulateMove(s, m)));
        TestSupport.check(got.contains(bonus), uci + " earns " + bonus + " " + got);
    }

    static List<String> names(List<MoveBonuses.Bonus> bonuses) {
        List<String> n = new ArrayList<>();
        for (MoveBonuses.Bonus b : bonuses) n.add(b.name);
        return n;
    }
}
