import java.util.Random;

/**
 * Stealing from the dealers: catch chance, embargo, worse deals, and being dragged to the table.
 * Run: java TestTheft
 */
public class TestTheft {
    private static int passed, failed;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
        if (ok) passed++; else failed++;
    }

    public static void main(String[] args) {
        ShopManager shop = new ShopManager();
        shop.setGold(0);
        check("the first theft is caught 15% of the time", Math.abs(shop.catchChance() - 0.15) < 1e-9);
        check("you can't steal what you can afford", afford(shop) == ShopManager.Result.CAN_AFFORD);

        int before = shop.count("Knight");
        check("a lucky theft takes the piece", shop.stealPiece("Knight", 0.99) == ShopManager.Result.OK);
        check("...and costs nothing", shop.getGold() == 0 && shop.count("Knight") == before + 1);
        check("each theft makes the next riskier (35%)", Math.abs(shop.catchChance() - 0.35) < 1e-9);
        shop.startVisit();
        check("time since the last theft lowers the risk (25% a visit later)", Math.abs(shop.catchChance() - 0.25) < 1e-9);

        shop.generateCardOfferings(1, new Random(3));
        Card card = shop.getFaceUpCards().get(0);
        int owned = shop.getCards().getActiveCards().size();
        check("a card theft that's caught takes nothing", shop.stealCard(card, 0.0) == ShopManager.Result.CAUGHT
            && shop.getCards().getActiveCards().size() == owned && shop.getFaceUpCards().contains(card));
        check("caught: the dealers refuse you for the rest of the visit", shop.isEmbargoed()
            && shop.buyPiece("Pawn") == ShopManager.Result.EMBARGO && shop.sellPiece("Pawn") == ShopManager.Result.EMBARGO
            && shop.stealPiece("Pawn", 0.99) == ShopManager.Result.EMBARGO);
        check("while refused, you can't fall out of the run for lack of material", shop.canStillReach(30));
        shop.startVisit();
        check("...and the whole next visit", shop.isEmbargoed());
        shop.startVisit();
        check("then they deal again", !shop.isEmbargoed());
        check("but prices are half again (Knight 15 -> 23)", shop.hasMarkup() && shop.piecePrice("Knight") == 23);
        check("and the reseller pays a quarter less", shop.pieceRefund("Rook") == Economy.sellPrice(Economy.piecePrice("Rook")) * 3 / 4);
        for (int i = 0; i < ShopManager.MARKUP_VISITS; i++) shop.startVisit();
        check("after three visits of worse deals, prices are back to normal", !shop.hasMarkup() && shop.piecePrice("Knight") == Economy.piecePrice("Knight"));

        // Dragged to the table: an embargo lets the round start below the requirement.
        RunController run = new RunController(new ScoreManager(new java.io.File(System.getProperty("java.io.tmpdir"), "ashes_theft_scores.dat")), new Chess960Setup(), new Random(1));
        run.startNewRun("Thief");
        run.getShop().clearArmy();
        run.getShop().setGold(0);
        run.getShop().stealPiece("Queen", 0.0);
        check("embargoed and under-armed, you are still made to play", run.finishShopping());

        ShopManager fresh = new ShopManager();
        fresh.setGold(0);
        fresh.stealPiece("Pawn", 0.99);
        fresh.resetForNewRun();
        check("a new run forgets your thefts", Math.abs(fresh.catchChance() - 0.15) < 1e-9 && !fresh.isEmbargoed());

        System.out.println();
        System.out.println("TestTheft: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static ShopManager.Result afford(ShopManager shop) {
        shop.setGold(100);
        ShopManager.Result r = shop.stealPiece("Pawn", 0.99);
        shop.setGold(0);
        return r;
    }
}
