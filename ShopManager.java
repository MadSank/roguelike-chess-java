

import javax.swing.JOptionPane;
import java.util.*;

public class ShopManager {
    private final Map<String, Integer> prices = new HashMap<>();
    private final List<Piece> nextRound = new ArrayList<>();
    private int savedGold = 0;

    private static final int MAX_PAWNS          = 8;
    private static final int MAX_QUEENS         = 3;
    private static final int MAX_NON_PAWN_TOTAL = 7;
    private static final int FREE_PAWNS         = 2;

    // ─── CARD OFFERING STATE ────────────────────────────────────
    private List<Card> currentFaceUpCards = new ArrayList<>();
    private List<Card> currentMysteryPack = new ArrayList<>();
    private boolean mysteryPackPurchased = false;

    public ShopManager() {
        prices.put("Pawn",    5);
        prices.put("Knight", 15);
        prices.put("Bishop", 15);
        prices.put("Rook",   25);
        prices.put("Queen",  45);

        initializeFreePawns();
    }

    public void saveGoldForNextGame(int gold)   
    { 
        this.savedGold = gold;
    }

    public int  getGoldFromPreviousGame()       
    { 
        return savedGold;
    }

    private void initializeFreePawns() {
        nextRound.clear();
        for (int i = 0; i < FREE_PAWNS; i++) {
            nextRound.add(new Pawn(true));
        }
        System.out.println("Initialized with " + FREE_PAWNS + " free pawns");
    }

    public void loadSurvivingPieces(GameState state) {
        System.out.println("=== LOADING SURVIVING PIECES ===");

        nextRound.clear();

        int survivingPawns = 0;
        List<Piece> survivors = new ArrayList<>();

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p != null && p.isWhite() && !(p instanceof King)) {
                    survivors.add(p.clone());
                    if (p instanceof Pawn) survivingPawns++;
                    System.out.println("  Survived: " + p.getName() + " at (" + r + "," + c + ")");
                }
            }
        }

        int pawnsNeeded = Math.max(0, FREE_PAWNS - survivingPawns);
        System.out.println("Surviving pawns: " + survivingPawns + ", need to add: " + pawnsNeeded);

        for (int i = 0; i < pawnsNeeded; i++) {
            nextRound.add(new Pawn(true));
            System.out.println("  Added FREE pawn #" + (i + 1));
        }

        for (Piece p : survivors) {
            if (canAddPiece(p)) {
                nextRound.add(p);
            } else {
                System.out.println("  CAPPED: " + p.getName() + " (reached maximum)");
            }
        }

        System.out.println("Total pieces loaded: " + nextRound.size());
    }

    /** Central cap-check – used both for surviving pieces and for purchases */
    private boolean canAddPiece(Piece p) {
        if (p instanceof Pawn)    return count(Pawn.class)    < MAX_PAWNS;
        if (p instanceof Queen)   return count(Queen.class)   < MAX_QUEENS;
        if (nonPawnCount() >= MAX_NON_PAWN_TOTAL) return false;
        return true;
    }

    public boolean purchase(ChessEngine engine, String type) {
        GameState s = engine.getGameState();
        int cost = prices.getOrDefault(type, 999);
        if (s.goldPlayer < cost) {
            System.out.println("Not enough gold for " + type + " (need " + cost + ", have " + s.goldPlayer + ")");
            return false;
        }

        int pawns = count(Pawn.class);
        if (!type.equals("Pawn")) {
            if (!unlockAllowed(pawns, type)) {
                JOptionPane.showMessageDialog(null,
                    "Need at least " + getRequiredPawns(type) + " pawns to unlock " + type + "!\nYou have: " + pawns,
                    "Shop", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }

        Piece dummy = create(type, true);
        if (!canAddPiece(dummy)) {
            JOptionPane.showMessageDialog(null,
                "Maximum limit reached for " + type + "!", "Shop",
                JOptionPane.WARNING_MESSAGE);
            return false;
        }

        s.goldPlayer -= cost;
        nextRound.add(dummy);
        System.out.println("Purchased: " + type + " (cost: " + cost + ", remaining gold: " + s.goldPlayer + ")");
        System.out.println("Current nextRound size: " + nextRound.size());
        return true;
    }

    // ─── CARD SHOP METHODS ──────────────────────────────────────

    /**
     * Generate the card offerings for this shop visit.
     */
    public void generateCardOfferings(CardManager cardManager, int roundNumber) {
        Random rng = new Random(roundNumber * 997L + System.nanoTime());
        currentFaceUpCards = cardManager.generateFaceUpOffering(roundNumber, rng);
        currentMysteryPack = cardManager.generateMysteryPack(roundNumber, new Random(rng.nextLong()));
        mysteryPackPurchased = false;
        System.out.println("Shop: Generated " + currentFaceUpCards.size() + " face-up cards, " +
                           currentMysteryPack.size() + " mystery pack cards");
    }

    public List<Card> getFaceUpCards() {
        return Collections.unmodifiableList(currentFaceUpCards);
    }

    public List<Card> getMysteryPackCards() {
        return Collections.unmodifiableList(currentMysteryPack);
    }

    public boolean isMysteryPackPurchased() {
        return mysteryPackPurchased;
    }

    /**
     * Purchase a face-up card at its full (premium) cost.
     */
    public boolean purchaseFaceUpCard(Card card, GameState state) {
        int premiumCost = (int)(card.getCost() * 1.5); // 50% premium for face-up
        if (state.goldPlayer < premiumCost) {
            System.out.println("Not enough gold for card " + card.getName() +
                " (need " + premiumCost + ", have " + state.goldPlayer + ")");
            return false;
        }

        // Anti-checkmate safeguard
        if (state.cardManager != null && !state.cardManager.validateCardApplication(card, state)) {
            System.out.println("Card " + card.getName() + " blocked by anti-checkmate safeguard!");
            return false;
        }

        state.goldPlayer -= premiumCost;
        state.cardManager.addCard(card);
        currentFaceUpCards.remove(card);
        System.out.println("Purchased face-up card: " + card.getName() + " for " + premiumCost + " gold");
        return true;
    }

    /**
     * Purchase the mystery pack.
     * Returns true if purchase succeeded. The draft UI is handled by ShopScene.
     */
    public boolean purchaseMysteryPack(GameState state, int roundNumber) {
        if (mysteryPackPurchased) return false;
        int cost = state.cardManager.getMysteryPackCost(roundNumber);
        if (state.goldPlayer < cost) {
            System.out.println("Not enough gold for mystery pack (need " + cost +
                ", have " + state.goldPlayer + ")");
            return false;
        }
        state.goldPlayer -= cost;
        mysteryPackPurchased = true;
        System.out.println("Purchased mystery pack for " + cost + " gold");
        return true;
    }

    /**
     * Player picks a card from the mystery pack draft.
     */
    public boolean draftMysteryCard(Card card, GameState state) {
        if (!mysteryPackPurchased) return false;
        if (!currentMysteryPack.contains(card)) return false;

        // Anti-checkmate safeguard
        if (state.cardManager != null && !state.cardManager.validateCardApplication(card, state)) {
            System.out.println("Draft card " + card.getName() + " blocked by safeguard!");
            return false;
        }

        state.cardManager.addCard(card);
        currentMysteryPack.clear();
        System.out.println("Drafted card from mystery pack: " + card.getName());
        return true;
    }

    public int getFaceUpCardCost(Card card) {
        return (int)(card.getCost() * 1.5);
    }

    // ─── EXISTING METHODS ───────────────────────────────────────

    private boolean unlockAllowed(int pawns, String type) {
        int required = getRequiredPawns(type);
        return pawns >= required;
    }

    private int getRequiredPawns(String type) {
        return switch (type) {
            case "Knight", "Bishop", "Queen" -> 4;
            case "Rook"                     -> 5;
            default                         -> 0;
        };
    }

    private Piece create(String type, boolean white) {
        return switch (type) {
            case "Pawn"    -> new Pawn(white);
            case "Knight"  -> new Knight(white);
            case "Bishop"  -> new Bishop(white);
            case "Rook"    -> new Rook(white);
            case "Queen"   -> new Queen(white);
            default        -> null;
        };
    }

    /** 
     * Returns all pieces for next round
     */
    public List<Piece> collectPurchasedPieces() {
        System.out.println("=== COLLECTING PIECES ===");
        System.out.println("Pieces to collect: " + nextRound.size());

        List<Piece> result = new ArrayList<>(nextRound);

        return result;
    }

    public Map<String, Integer> getPieceCounts() {
        Map<String, Integer> m = new HashMap<>();
        m.put("Pawn",    count(Pawn.class));
        m.put("Knight",  count(Knight.class));
        m.put("Bishop",  count(Bishop.class));
        m.put("Rook",    count(Rook.class));
        m.put("Queen",   count(Queen.class));
        return m;
    }

    public Map<String, Integer> getPrices() 
    { 
        return new HashMap<>(prices);
    }

    private <T extends Piece> int count(Class<T> cls) 
    {
        int c = 0;
        for (Piece p : nextRound) 
            if (cls.isInstance(p))
                c++;
        return c;
    }

    private int nonPawnCount()
    { 
        return nextRound.size() - count(Pawn.class);
    }
}
