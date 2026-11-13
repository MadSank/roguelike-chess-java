import java.util.*;

/**
 * The shop and everything the player owns between rounds: army, coins and cards.
 * Pure game logic: no Swing. Every action returns a Result the UI turns into a message.
 */
public class ShopManager {

    public enum Result {
        OK("Done."),
        NOT_ENOUGH_GOLD("Not enough gold."),
        PAWN_CAP("Pawn limit reached: buy a Knight, Bishop, Rook or Queen first."),
        NO_ROOM("No room: the back rank holds at most 7 pieces besides the King."),
        NOT_OWNED("You don't own one of those."),
        ALREADY_OPENED("You already opened a pack this visit."),
        NOT_OFFERED("That card isn't on offer."),
        EMBARGO("Nobody here will deal with a thief."),
        CAN_AFFORD("You can pay for that."),
        CAUGHT("Caught stealing.");

        public final String message;

        Result(String message) {
            this.message = message;
        }
    }

    /** Your pieces (White, King not included). They start each round on the board for free. */
    private final List<Piece> army = new ArrayList<>();
    private int gold;
    /** Cards you own. */
    private CardManager cards = new CardManager();

    private List<Card> faceUpCards = new ArrayList<>();
    private List<Card> mysteryPack = new ArrayList<>();
    private boolean mysteryPackOpened;

    // ─── THEFT ──────────────────────────────────────────────────
    /** How much the dealers suspect you: +1 per theft, -0.5 for every visit since. Sets the chance of being caught. */
    private double heat;
    /** Visits (this one included) in which no dealer will deal with you, after being caught. */
    private int embargo;
    /** Visits left of worse deals once the embargo is over. */
    private int markup;
    /** Visits of worse deals that follow an embargo. */
    public static final int MARKUP_VISITS = 3;

    public ShopManager() {
        resetForNewRun();
    }

    /** A new run starts with King + 2 Pawns, no gold, no cards. */
    public void resetForNewRun() {
        army.clear();
        army.add(new Pawn(true));
        army.add(new Pawn(true));
        gold = 0;
        cards = new CardManager();
        faceUpCards = new ArrayList<>();
        mysteryPack = new ArrayList<>();
        mysteryPackOpened = false;
        heat = 0;
        embargo = 0;
        markup = 0;
    }

    // ─── GOLD AND ARMY ──────────────────────────────────────────

    public int getGold() { return gold; }

    public void setGold(int gold) { this.gold = Math.max(0, gold); }

    public CardManager getCards() { return cards; }

    /** Copies of the army's pieces, ready to place on a board. */
    public List<Piece> getArmy() {
        List<Piece> copy = new ArrayList<>();
        for (Piece p : army) copy.add(p.clone());
        return copy;
    }

    /** Replaces the army with the White pieces (except the King) left on a board. */
    public void setArmyFromBoard(GameState state) {
        army.clear();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p != null && p.isWhite() && !(p instanceof King)) army.add(p.clone());
            }
        }
    }

    /** After a loss only the King remains. */
    public void clearArmy() {
        army.clear();
    }

    public int armyMaterial() {
        return Economy.material(army);
    }

    public int count(String type) {
        int n = 0;
        for (Piece p : army) if (typeOf(p).equals(type)) n++;
        return n;
    }

    public int pawnCap() {
        return Economy.pawnCap(count("Knight") + count("Bishop") + count("Rook"), count("Queen"));
    }

    private int nonPawnCount() {
        return army.size() - count("Pawn");
    }

    public boolean meetsRequirement(int level) {
        return armyMaterial() >= Economy.requirement(level);
    }

    // ─── THEFT, EMBARGO, WORSE DEALS ────────────────────────────

    /** The chance of being caught on the next theft: 15%, +20 points per theft not yet cooled off, at most 85%. */
    public double catchChance() {
        return Math.min(0.85, 0.15 + 0.2 * heat);
    }

    /** True while the dealers refuse you (the visit you were caught in, and the next). */
    public boolean isEmbargoed() { return embargo > 0; }

    /** True while prices are worse after an embargo. */
    public boolean hasMarkup() { return markup > 0; }

    public int markupVisitsLeft() { return markup; }

    /** A new shop visit begins (after a round): suspicion cools, an embargo runs down into worse deals. */
    public void startVisit() {
        heat = Math.max(0, heat - 0.5);
        if (embargo > 0) {
            embargo--;
            if (embargo == 0) markup = MARKUP_VISITS;
        } else if (markup > 0) {
            markup--;
        }
    }

    /** What something costs you now (half again during worse deals). */
    public int price(int base) {
        return markup > 0 ? (base * 3 + 1) / 2 : base;
    }

    /** What the reseller pays you now (a quarter less during worse deals). */
    public int refund(int base) {
        return markup > 0 ? base * 3 / 4 : base;
    }

    public int piecePrice(String type) { return price(Economy.piecePrice(type)); }

    public int cardPrice(Card card) { return price(Economy.faceUpCardPrice(card)); }

    public int packPrice(int level) { return price(Economy.mysteryPackPrice(level)); }

    public int pieceRefund(String type) { return refund(Economy.sellPrice(Economy.piecePrice(type))); }

    public int cardRefund(Card card) { return refund(Economy.cardSellPrice(card)); }

    /** The piece could join the army (pawn limit, back-rank room). */
    private Result roomFor(String type) {
        if (type.equals("Pawn")) {
            if (count("Pawn") >= pawnCap()) return Result.PAWN_CAP;
        } else if (nonPawnCount() >= Economy.MAX_NON_PAWNS) {
            return Result.NO_ROOM;
        }
        return Result.OK;
    }

    /** One theft attempt: caught if roll (0..1) falls under the catch chance. Suspicion rises either way. */
    private boolean caught(double roll) {
        boolean caught = roll < catchChance();
        heat += 1;
        if (caught) embargo = 2;                 // the rest of this visit, and the next one
        return caught;
    }

    /** Takes a piece without paying. Only when you can't afford it. roll: a random number 0..1. */
    public Result stealPiece(String type, double roll) {
        if (embargo > 0) return Result.EMBARGO;
        if (gold >= piecePrice(type)) return Result.CAN_AFFORD;
        Result room = roomFor(type);
        if (room != Result.OK) return room;
        if (caught(roll)) return Result.CAUGHT;
        army.add(create(type));
        return Result.OK;
    }

    /** Takes a face-up card without paying. Only when you can't afford it. */
    public Result stealCard(Card card, double roll) {
        if (embargo > 0) return Result.EMBARGO;
        if (!faceUpCards.contains(card)) return Result.NOT_OFFERED;
        if (gold >= cardPrice(card)) return Result.CAN_AFFORD;
        if (caught(roll)) return Result.CAUGHT;
        cards.addCard(card);
        faceUpCards.remove(card);
        return Result.OK;
    }

    // ─── BUYING AND SELLING PIECES ──────────────────────────────

    public Result buyPiece(String type) {
        if (embargo > 0) return Result.EMBARGO;
        int price = piecePrice(type);
        if (gold < price) return Result.NOT_ENOUGH_GOLD;
        Result room = roomFor(type);
        if (room != Result.OK) return room;
        gold -= price;
        army.add(create(type));
        return Result.OK;
    }

    public Result sellPiece(String type) {
        if (embargo > 0) return Result.EMBARGO;
        for (Iterator<Piece> it = army.iterator(); it.hasNext(); ) {
            if (typeOf(it.next()).equals(type)) {
                it.remove();
                gold += pieceRefund(type);
                return Result.OK;
            }
        }
        return Result.NOT_OWNED;
    }

    // ─── CARDS ──────────────────────────────────────────────────

    /**
     * Rolls this shop visit's offers: 3 face-up cards and a 3-card mystery pack with no card
     * in common, weighted by rarity for the level. Cards are consumables, so owning a card
     * doesn't stop it being offered again.
     */
    public void generateCardOfferings(int level, Random rng) {
        faceUpCards = CardCatalog.drawOffer(3, level, rng, Collections.emptySet());
        Set<String> shown = new HashSet<>();
        for (Card c : faceUpCards) shown.add(c.getName());
        mysteryPack = CardCatalog.drawOffer(3, level, rng, shown);
        mysteryPackOpened = false;
    }

    public List<Card> getFaceUpCards() { return Collections.unmodifiableList(faceUpCards); }

    public List<Card> getMysteryPackCards() { return Collections.unmodifiableList(mysteryPack); }

    public boolean isMysteryPackOpened() { return mysteryPackOpened; }

    public Result buyFaceUpCard(Card card) {
        if (embargo > 0) return Result.EMBARGO;
        if (!faceUpCards.contains(card)) return Result.NOT_OFFERED;
        int price = cardPrice(card);
        if (gold < price) return Result.NOT_ENOUGH_GOLD;
        gold -= price;
        cards.addCard(card);
        faceUpCards.remove(card);
        return Result.OK;
    }

    /** Pays for the mystery pack; the player then drafts one of its cards. */
    public Result openMysteryPack(int level) {
        if (mysteryPackOpened) return Result.ALREADY_OPENED;
        if (mysteryPack.isEmpty()) return Result.NOT_OFFERED;
        if (embargo > 0) return Result.EMBARGO;
        int price = packPrice(level);
        if (gold < price) return Result.NOT_ENOUGH_GOLD;
        gold -= price;
        mysteryPackOpened = true;
        return Result.OK;
    }

    public Result draftMysteryCard(Card card) {
        if (!mysteryPackOpened || !mysteryPack.contains(card)) return Result.NOT_OFFERED;
        cards.addCard(card);
        mysteryPack.clear();
        return Result.OK;
    }

    public Result sellCard(Card card) {
        if (embargo > 0) return Result.EMBARGO;
        if (!cards.removeCard(card)) return Result.NOT_OWNED;
        gold += cardRefund(card);
        return Result.OK;
    }

    // ─── GAME OVER CHECK ────────────────────────────────────────

    /**
     * True if the player can still reach the level's requirement: by buying pieces with their
     * gold, plus whatever they'd get for selling all their cards. Selling pieces never helps,
     * since it loses more material than the refund buys back.
     */
    public boolean canStillReach(int level) {
        int needed = Economy.requirement(level) - armyMaterial();
        if (needed <= 0) return true;
        if (embargo > 0) return true;            // nobody will sell to you, so the guards make you play as you are
        int budget = gold;
        for (Card c : cards.getActiveCards()) budget += cardRefund(c);
        return maxAffordableMaterial(budget) >= needed;
    }

    /** Most material that can be added with the budget, respecting the pawn cap and back-rank room. */
    int maxAffordableMaterial(int budget) {
        int minors = count("Knight") + count("Bishop") + count("Rook");
        int queens = count("Queen");
        int pawns = count("Pawn");
        int room = Economy.MAX_NON_PAWNS - nonPawnCount();
        int best = 0;
        for (int q = 0; q <= room; q++) {
            for (int r = 0; q + r <= room; r++) {
                for (int m = 0; q + r + m <= room; m++) { // knights/bishops
                    int cost = q * piecePrice("Queen") + r * piecePrice("Rook") + m * piecePrice("Knight");
                    if (cost > budget) break;
                    int cap = Economy.pawnCap(minors + r + m, queens + q);
                    int extraPawns = Math.max(0, Math.min(cap, Economy.MAX_PAWNS) - pawns);
                    extraPawns = Math.min(extraPawns, (budget - cost) / piecePrice("Pawn"));
                    best = Math.max(best, q * 9 + r * 5 + m * 3 + extraPawns);
                }
            }
        }
        return best;
    }

    // ─── HELPERS ────────────────────────────────────────────────

    public static String typeOf(Piece p) {
        if (p instanceof Pawn) return "Pawn";
        if (p instanceof Knight) return "Knight";
        if (p instanceof Bishop) return "Bishop";
        if (p instanceof Rook) return "Rook";
        if (p instanceof Queen) return "Queen";
        return "King";
    }

    private static Piece create(String type) {
        switch (type) {
            case "Pawn":   return new Pawn(true);
            case "Knight": return new Knight(true);
            case "Bishop": return new Bishop(true);
            case "Rook":   return new Rook(true);
            case "Queen":  return new Queen(true);
            default: throw new IllegalArgumentException("Unknown piece type: " + type);
        }
    }
}
