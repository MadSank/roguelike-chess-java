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
        NOT_OFFERED("That card isn't on offer.");

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

    // ─── BUYING AND SELLING PIECES ──────────────────────────────

    public Result buyPiece(String type) {
        int price = Economy.piecePrice(type);
        if (gold < price) return Result.NOT_ENOUGH_GOLD;
        if (type.equals("Pawn")) {
            if (count("Pawn") >= pawnCap()) return Result.PAWN_CAP;
        } else if (nonPawnCount() >= Economy.MAX_NON_PAWNS) {
            return Result.NO_ROOM;
        }
        gold -= price;
        army.add(create(type));
        return Result.OK;
    }

    public Result sellPiece(String type) {
        for (Iterator<Piece> it = army.iterator(); it.hasNext(); ) {
            if (typeOf(it.next()).equals(type)) {
                it.remove();
                gold += Economy.sellPrice(Economy.piecePrice(type));
                return Result.OK;
            }
        }
        return Result.NOT_OWNED;
    }

    // ─── CARDS ──────────────────────────────────────────────────

    /** Rolls this shop visit's offers: 3 face-up cards and a separate 3-card mystery pack. */
    public void generateCardOfferings(int level, Random rng) {
        List<Card> available = cards.availableCards();
        Collections.shuffle(available, rng);
        int faceUp = Math.min(3, available.size());
        faceUpCards = new ArrayList<>(available.subList(0, faceUp));
        mysteryPack = new ArrayList<>(available.subList(faceUp, Math.min(faceUp + 3, available.size())));
        mysteryPackOpened = false;
    }

    public List<Card> getFaceUpCards() { return Collections.unmodifiableList(faceUpCards); }

    public List<Card> getMysteryPackCards() { return Collections.unmodifiableList(mysteryPack); }

    public boolean isMysteryPackOpened() { return mysteryPackOpened; }

    public Result buyFaceUpCard(Card card) {
        if (!faceUpCards.contains(card)) return Result.NOT_OFFERED;
        int price = Economy.faceUpCardPrice(card);
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
        int price = Economy.mysteryPackPrice(level);
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
        if (!cards.removeCard(card)) return Result.NOT_OWNED;
        gold += Economy.cardSellPrice(card);
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
        int budget = gold;
        for (Card c : cards.getActiveCards()) budget += Economy.cardSellPrice(c);
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
                    int cost = q * 45 + r * 25 + m * 15;
                    if (cost > budget) break;
                    int cap = Economy.pawnCap(minors + r + m, queens + q);
                    int extraPawns = Math.max(0, Math.min(cap, Economy.MAX_PAWNS) - pawns);
                    extraPawns = Math.min(extraPawns, (budget - cost) / Economy.piecePrice("Pawn"));
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
