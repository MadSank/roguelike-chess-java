import java.lang.reflect.*;
import java.util.*;

/**
 * Card tests: one scenario per card, plus checks that listing moves and AI search never
 * change the real game (cards keep no state of their own).
 *
 * Run: java TestCards
 */
public class TestCards {

    public static void main(String[] args) {
        System.out.println("== TestCards ==");
        TestSupport.quiet(() -> {});
        cardsHaveNoState();
        searchHasNoSideEffects();
        heavyArmor();
        chronoShift();
        phantomKing();
        glassCannons();
        soulHarvest();
        trenchWarfare();
        sniperBishops();
        necromancer();
        minefield();
        pawnPhalanx();
        goldenEdge();
        cavalryCharge();
        bloodMoon();
        vaultingMajors();
        catalog();
        pawnSurge();
        pawnRetreat();
        bishopShift();
        kingsStride();
        timeStop();
        ironPawns();
        smuggledPawn();
        bloodMoney();
        blindfold();
        TestSupport.finish("TestCards");
    }

    static GameState with(String fen, Card... cards) {
        GameState s = TestSupport.fromFen(fen);
        for (Card c : cards) s.cardManager.addCard(c);
        return s;
    }

    static Card[] allCards() {
        return CardCatalog.all().toArray(new Card[0]);
    }

    static void catalog() {
        System.out.println("-- catalog");
        Set<String> names = new HashSet<>();
        for (Card c : CardCatalog.all()) names.add(c.getName());
        TestSupport.checkEquals(23, names.size(), "23 distinct cards in the catalog");
        Map<CardCatalog.Rarity, Integer> seen = new EnumMap<>(CardCatalog.Rarity.class);
        Random rng = new Random(3);
        for (int i = 0; i < 2000; i++) {
            for (Card c : CardCatalog.drawOffer(3, 10, rng, Collections.emptySet())) seen.merge(c.getRarity(), 1, Integer::sum);
        }
        TestSupport.check(seen.getOrDefault(CardCatalog.Rarity.COMMON, 0) > seen.getOrDefault(CardCatalog.Rarity.RARE, 0),
            "commons are offered more often than rares " + seen);
        TestSupport.check(CardCatalog.shopWeight(CardCatalog.Rarity.RARE, 12) > CardCatalog.shopWeight(CardCatalog.Rarity.RARE, 1),
            "rares get more likely at higher levels");
    }

    static void pawnSurge() {
        System.out.println("-- Pawn Surge");
        GameState s = with("k7/8/8/8/8/4P3/8/K7 w - - 0", new CardPawnSurge());
        TestSupport.check(TestSupport.findMove(new ChessEngine(s), "e3e5") != null, "pawn off its start rank advances two");
        GameState p = with("k7/8/4P3/8/8/8/8/K7 w - - 0", new CardPawnSurge());
        TestSupport.check(TestSupport.findMove(new ChessEngine(p), "e6e8n") != null, "surging onto the last rank promotes");
    }

    static void pawnRetreat() {
        System.out.println("-- Pawn Retreat");
        GameState s = with("k7/8/8/4P3/8/8/8/K7 w - - 0", new CardPawnRetreat());
        TestSupport.check(TestSupport.findMove(new ChessEngine(s), "e5e4") != null, "pawn steps backward");
        GameState t = with("k7/8/8/8/8/8/4P3/K7 w - - 0", new CardPawnRetreat());
        TestSupport.checkEquals(null, TestSupport.findMove(new ChessEngine(t), "e2e1"), "never back onto the first rank");

        // Shuffling a pawn back and forth must still end in a repetition draw.
        GameState r = with("k7/8/8/4P3/8/8/8/K7 w - - 0", new CardPawnRetreat());
        ChessEngine re = new ChessEngine(r);
        TestSupport.play(re, "e5e4", "a8b8", "e4e5", "b8a8", "e5e4", "a8b8", "e4e5", "b8a8");
        TestSupport.checkEquals(GameResult.DRAW, r.result, "pawn shuffling with Pawn Retreat is a repetition draw");
    }

    static void bishopShift() {
        System.out.println("-- Bishop Shift");
        GameState s = with("k7/8/8/8/3B4/8/8/K7 w - - 0", new CardBishopShift());
        TestSupport.check(TestSupport.findMove(new ChessEngine(s), "d4e4") != null, "bishop steps sideways");
    }

    static void kingsStride() {
        System.out.println("-- King's Stride");
        GameState s = with("k7/8/8/8/8/8/8/4K3 w - - 0", new CardKingsStride());
        TestSupport.check(TestSupport.findMove(new ChessEngine(s), "e1e3") != null, "king moves two squares");
        GameState b = with("k7/8/8/8/8/8/4P3/4K3 w - - 0", new CardKingsStride());
        TestSupport.checkEquals(null, TestSupport.findMove(new ChessEngine(b), "e1e3"), "not through a piece");
    }

    static void timeStop() {
        System.out.println("-- Time Stop");
        GameState s = TestSupport.fromFen("k7/8/8/8/8/8/8/K6R b - - 0"); // AI to move
        new CardTimeStop().onPlay(s);
        TestSupport.checkEquals(1, s.getCounter(CardTimeStop.PENDING), "playing it arms the skip");
        ChessEngine e = new ChessEngine(s);
        TestSupport.check(e.passTurn() && s.whiteToMove, "passing hands the move back to White");
    }

    static void cardsHaveNoState() {
        System.out.println("-- cards are stateless");
        for (Card c : allCards()) {
            List<String> mutable = new ArrayList<>();
            for (Class<?> k = c.getClass(); k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    int mod = f.getModifiers();
                    if (!Modifier.isStatic(mod) && !Modifier.isFinal(mod)) mutable.add(f.getName());
                }
            }
            TestSupport.check(mutable.isEmpty(), c.getName() + " has no mutable fields " + (mutable.isEmpty() ? "" : mutable));
        }
    }

    static String snapshot(GameState s) {
        return s.positionKey() + " stun=" + new TreeMap<>(s.stunnedPieces) + " ctr=" + new TreeMap<>(s.cardCounters)
            + " gold=" + s.goldPlayer + " mines=" + new TreeSet<>(s.minefieldSquares) + " vault=" + s.vaultingPieceSquare
            + " necro=" + s.necromancerUsed + " moves=" + s.moveHistory.size();
    }

    static void searchHasNoSideEffects() {
        System.out.println("-- listing moves and AI search leave the game untouched");
        Card[] cards = allCards();
        List<Card> noBloodMoon = new ArrayList<>();
        for (Card c : cards) if (!(c instanceof CardBloodMoon)) noBloodMoon.add(c);
        GameState s = with("r3k2r/pp1n1ppp/2p1pn2/3p4/1bPP4/2N1PN2/PP1B1PPP/R2QKB1R w - - 0",
            noBloodMoon.toArray(new Card[0]));
        s.minefieldSquares.add(4 * 8 + 0);
        s.vaultingPieceSquare = 7 * 8 + 0;
        String before = snapshot(s);
        ChessEngine e = new ChessEngine(s);
        e.getAllLegalMoves(true);
        e.getAllLegalMoves(false);
        TestSupport.checkEquals(before, snapshot(s), "listing every legal move changes nothing");
        new AIPlayer(e, 2).chooseMove(s);
        TestSupport.checkEquals(before, snapshot(s), "a depth-2 AI search changes nothing");
    }

    static void heavyArmor() {
        System.out.println("-- Heavy Armor");
        GameState s = with("k7/8/8/3n4/8/4N3/8/K7 w - - 0", new CardHeavyArmor());
        ChessEngine e = new ChessEngine(s);
        boolean offered = false;
        for (Move m : e.getLegalMovesFromSquare(5, 4)) if (m.toUCI().equals("e3d5")) offered = true;
        TestSupport.check(offered && s.stunnedPieces.isEmpty(), "attack is offered and selecting it stuns nothing");
        TestSupport.play(e, "e3d5");
        TestSupport.check(s.board[3][3] instanceof Knight && !s.board[3][3].isWhite() && s.board[5][4] instanceof Knight,
            "first hit: target stays, attacker stays put");
        TestSupport.check(s.stunnedPieces.containsKey(27), "first hit stuns the target");
        TestSupport.check(e.getLegalMovesFromSquare(3, 3).isEmpty(), "stunned piece can't move on its turn");
        TestSupport.play(e, "a8b8", "e3d5");
        TestSupport.check(s.board[3][3] instanceof Knight && s.board[3][3].isWhite(), "second hit captures");
        TestSupport.check(s.stunnedPieces.isEmpty(), "capturing clears the stun (capturer isn't stunned)");

        GameState t = with("k7/8/8/3n4/8/4N3/8/K7 w - - 0", new CardHeavyArmor());
        ChessEngine te = new ChessEngine(t);
        TestSupport.play(te, "e3d5", "a8b8", "a1b1");
        TestSupport.check(!te.getLegalMovesFromSquare(3, 3).isEmpty(), "stun wears off after the attacker's next turn");
    }

    static void chronoShift() {
        System.out.println("-- Chrono Shift");
        // Blocked h-pawns keep enough material on the board that the game isn't a draw.
        GameState s = with("k7/8/8/7p/7P/8/1N6/K7 w - - 0", new CardChronoShift());
        ChessEngine e = new ChessEngine(s);
        TestSupport.play(e, "a1a2", "a8b8", "a2a1", "b8a8");
        TestSupport.check(s.board[6][1] instanceof Knight, "no shift after 4 moves");
        TestSupport.play(e, "a1a2");
        TestSupport.check(s.board[6][2] instanceof Knight && s.board[6][1] == null, "5th move shifts pieces one file right");
        TestSupport.check(s.board[6][0] instanceof King, "kings don't shift");

        GameState a = with("k7/pp6/8/8/8/8/PP6/K7 b - - 0", new CardChronoShift());
        new AIPlayer(new ChessEngine(a), 2).chooseMove(a);
        TestSupport.checkEquals(0, a.getCounter(CardChronoShift.MOVES), "AI search doesn't advance the shift counter");
    }

    static void phantomKing() {
        System.out.println("-- Phantom King");
        GameState s = with("k3r3/8/8/8/8/8/8/3K4 b - - 0", new CardPhantomKing());
        ChessEngine e = new ChessEngine(s);
        TestSupport.play(e, "e8d8");
        TestSupport.check(!(s.board[7][3] instanceof King), "checked king teleports away");
        TestSupport.check(!e.isKingInCheck(s, true), "king lands on a safe square");
        TestSupport.checkEquals(1, s.getCounter(CardPhantomKing.USED), "teleport is used up");
    }

    static void glassCannons() {
        System.out.println("-- Glass Cannons");
        GameState s = with("k7/8/8/8/8/8/1p6/K7 w - - 0", new CardGlassCannons());
        ChessEngine e = new ChessEngine(s);
        TestSupport.play(e, "a1b2");
        TestSupport.check(s.board[6][1] instanceof King, "a capturing king never shatters");

        GameState r = with("k7/8/8/8/8/8/8/K2R3n w - - 0", new CardGlassCannons());
        ChessEngine re = new ChessEngine(r);
        Move cap = TestSupport.findMove(re, "d1h1");
        GameState predicted = re.simulateMove(r, cap);
        re.applyMove(cap);
        TestSupport.checkEquals(String.valueOf(predicted.board[7][7]), String.valueOf(r.board[7][7]),
            "outcome is deterministic (simulation matches the real move)");
    }

    static void soulHarvest() {
        System.out.println("-- Soul Harvest");
        GameState s = with("k7/8/8/8/8/8/8/K7 w - - 0", new CardSoulHarvest());
        Card c = s.cardManager.getActiveCards().get(0);
        Move m = new Move(4, 4, 3, 3);
        c.onCapture(s, m, new Pawn(false), new Rook(true));
        c.onCapture(s, m, new Pawn(false), new Rook(true));
        TestSupport.checkEquals(1, Utils.countPieces(s, true), "nothing spawns after 2 captures");
        c.onCapture(s, m, new Pawn(false), new Rook(true));
        TestSupport.checkEquals(2, Utils.countPieces(s, true), "3rd capture spawns a piece on the back rank");
    }

    static void trenchWarfare() {
        System.out.println("-- Trench Warfare");
        String fen = "k7/8/8/8/4K3/8/8/4r3 w - - 0"; // black rook e1 'attacks' e4 backwards
        GameState plain = TestSupport.fromFen(fen);
        TestSupport.check(new ChessEngine(plain).isKingInCheck(plain, true), "without the card the rook gives check");
        GameState s = with(fen, new CardTrenchWarfare());
        TestSupport.check(!new ChessEngine(s).isKingInCheck(s, true), "with the card a backward attack is no check");
        boolean back = false;
        for (Move m : new ChessEngine(s).getLegalMovesFromSquare(7, 4)) if (m.toRow < m.fromRow) back = true;
        TestSupport.check(!back, "black rook has no backward moves");
    }

    static void sniperBishops() {
        System.out.println("-- Sniper Bishops");
        GameState s = with("k7/8/8/8/3n4/8/8/B6K w - - 0", new CardSniperBishops());
        ChessEngine e = new ChessEngine(s);
        Move snipe = null;
        for (Move m : e.getLegalMovesFromSquare(7, 0)) if (m.isSniperShot) snipe = m;
        TestSupport.check(snipe != null && snipe.sniperTargetRow == 4 && snipe.sniperTargetCol == 3, "snipe offered at the knight");
        e.applyMove(snipe);
        TestSupport.check(s.board[4][3] == null && s.board[7][0] instanceof Bishop, "target removed, bishop stays");
    }

    static void necromancer() {
        System.out.println("-- Necromancer's Gambit");
        // Black king off the a-file: the new queen on a2 would otherwise check it, making the capture illegal.
        GameState s = with("4k3/8/8/8/8/8/P7/K2Q3r b - - 0", new CardNecromancerGambit());
        ChessEngine e = new ChessEngine(s);
        TestSupport.play(e, "h1d1");
        TestSupport.check(s.board[6][0] instanceof Queen && s.necromancerUsed, "losing the queen promotes a pawn");
    }

    static void minefield() {
        System.out.println("-- Minefield");
        GameState s = with("k7/8/8/8/8/8/8/KN6 w - - 0", new CardMinefield());
        s.minefieldSquares.add(5 * 8 + 2);
        ChessEngine e = new ChessEngine(s);
        TestSupport.play(e, "b1c3");
        TestSupport.checkEquals(null, s.board[5][2], "piece landing on a mine is destroyed");
    }

    static void pawnPhalanx() {
        System.out.println("-- Pawn Phalanx");
        GameState s = with("k7/8/5n2/8/3PP3/8/8/K7 b - - 0", new CardPawnPhalanx());
        TestSupport.checkEquals(null, TestSupport.findMove(new ChessEngine(s), "f6e4"), "paired pawn can't be captured");
        GameState t = with("k7/8/5n2/8/4P3/8/8/K7 b - - 0", new CardPawnPhalanx());
        TestSupport.check(TestSupport.findMove(new ChessEngine(t), "f6e4") != null, "lone pawn can be captured");
    }

    static void ironPawns() {
        System.out.println("-- Iron Pawns");
        GameState s = with("k7/8/8/3p4/4P3/8/8/K7 b - - 0", new CardIronPawns());
        TestSupport.check(TestSupport.findMove(new ChessEngine(s), "d5e4") == null, "an enemy pawn can't take your pawn");
        GameState q = with("k7/8/8/3q4/4P3/8/8/K7 b - - 0", new CardIronPawns());
        TestSupport.check(TestSupport.findMove(new ChessEngine(q), "d5e4") != null, "other pieces still can");
    }

    static void smuggledPawn() {
        System.out.println("-- Smuggled Pawn");
        GameState s = with("k7/8/8/8/8/8/PPP5/K7 w - - 0", new CardSmuggledPawn());
        TestSupport.quiet(() -> s.cardManager.getActiveCards().get(0).onPlay(s));
        TestSupport.check(s.board[6][3] instanceof Pawn && s.board[6][3].isWhite(), "a white pawn appears on the second rank, nearest the centre");
    }

    static void bloodMoney() {
        System.out.println("-- Blood Money");
        GameState s = with("k7/8/8/3n4/8/4N3/8/K7 b - - 0", new CardBloodMoney());
        TestSupport.play(new ChessEngine(s), "d5e3");
        TestSupport.checkEquals(6, s.goldPlayer, "losing a knight pays twice its value");
    }

    static void blindfold() {
        System.out.println("-- Blindfold");
        GameState s = with("k7/8/8/8/8/8/8/K7 w - - 0", new CardBlindfold());
        TestSupport.quiet(() -> s.cardManager.getActiveCards().get(0).onPlay(s));
        TestSupport.checkEquals(1, s.getCounter(CardBlindfold.KEY), "the AI is told to look one move less far");
        TestSupport.check(new AIPlayer(Difficulty.fixedDepth(1), new java.util.Random(1)).chooseMove(s) != null, "and still finds a move at depth one");
    }

    static void goldenEdge() {
        System.out.println("-- Golden Edge");
        GameState s = with("k7/8/8/3n4/8/4N3/8/K7 w - - 0", new CardGoldenEdge());
        TestSupport.play(new ChessEngine(s), "e3d5");
        TestSupport.checkEquals(3, s.goldPlayer, "capturing a knight pays 3 gold");
    }

    static void cavalryCharge() {
        System.out.println("-- Cavalry Charge");
        GameState s = with("k7/8/8/8/8/8/8/KN6 w - - 0", new CardCavalryCharge());
        TestSupport.checkEquals(10, new ChessEngine(s).getLegalMovesFromSquare(7, 1).size(),
            "knight reaches every empty square within 2");
    }

    static void bloodMoon() {
        System.out.println("-- Blood Moon");
        GameState s = with("k7/p7/8/8/8/8/P7/K7 w - - 0", new CardBloodMoon());
        TestSupport.quiet(() -> s.cardManager.getActiveCards().get(0).onPlay(s));
        TestSupport.check(s.board[6][0] instanceof Knight && s.board[1][0] instanceof Knight, "all pawns become knights");
    }

    static void vaultingMajors() {
        System.out.println("-- Vaulting Majors");
        GameState s = with("k7/8/8/8/8/8/P7/R6K w - - 0", new CardVaultingMajors());
        s.vaultingPieceSquare = 7 * 8;
        TestSupport.check(TestSupport.findMove(new ChessEngine(s), "a1a3") != null, "rook vaults over its own pawn");
    }
}
