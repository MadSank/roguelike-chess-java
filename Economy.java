import java.util.*;

/**
 * Economy rules from DESIGN.md: material values, the level requirement, the pawn cap and
 * round payouts. All numbers live here so balance changes happen in one place.
 */
public final class Economy {
    private Economy() {}

    // ─── MATERIAL ───────────────────────────────────────────────

    /** Pawn 1, Knight 3, Bishop 3, Rook 5, Queen 9, King 0. */
    public static int material(Piece p) {
        if (p instanceof Pawn) return 1;
        if (p instanceof Knight || p instanceof Bishop) return 3;
        if (p instanceof Rook) return 5;
        if (p instanceof Queen) return 9;
        return 0;
    }

    public static int material(GameState s, boolean white) {
        int total = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = s.board[r][c];
                if (p != null && p.isWhite() == white) total += material(p);
            }
        }
        return total;
    }

    public static int material(Collection<Piece> pieces) {
        int total = 0;
        for (Piece p : pieces) total += material(p);
        return total;
    }

    // ─── ARMY LIMITS ────────────────────────────────────────────

    /** Draft requirement table: points of material needed to play level 1, 2, ... (last value repeats). */
    private static final int[] REQUIREMENT = {2, 4, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36, 39};

    public static int requirement(int level) {
        int i = Math.max(1, level) - 1;
        return REQUIREMENT[Math.min(i, REQUIREMENT.length - 1)];
    }

    /** max pawns = min(8, 2 for the King + 1 per Knight/Bishop/Rook + 2 per Queen). */
    public static int pawnCap(int knightsBishopsRooks, int queens) {
        return Math.min(8, 2 + knightsBishopsRooks + 2 * queens);
    }

    /** Back rank has 8 squares and the King takes one. */
    public static final int MAX_NON_PAWNS = 7;
    public static final int MAX_PAWNS = 8;

    // ─── PRICES ─────────────────────────────────────────────────

    public static final String[] PIECE_TYPES = {"Pawn", "Knight", "Bishop", "Rook", "Queen"};

    /** Price of a piece type. Every piece costs 5 coins per point of material. */
    public static int piecePrice(String type) {
        switch (type) {
            case "Pawn":   return 5;
            case "Knight": return 15;
            case "Bishop": return 15;
            case "Rook":   return 25;
            case "Queen":  return 45;
            default: throw new IllegalArgumentException("Unknown piece type: " + type);
        }
    }

    /** Anything sold back returns half its price. */
    public static int sellPrice(int price) {
        return price / 2;
    }

    /** Face-up cards cost more than their base price; mystery packs are the cheap gamble. */
    public static int faceUpCardPrice(Card card) {
        return card.getCost() * 3 / 2;
    }

    public static int mysteryPackPrice(int level) {
        return 8 + level * 2;
    }

    public static int cardSellPrice(Card card) {
        return sellPrice(card.getCost());
    }

    // ─── PAYOUTS ────────────────────────────────────────────────

    /** Coins per point of your own material still on the board at the end of a win. */
    public static final int COINS_PER_OWN_POINT = 2;
    /** Coins per point of enemy material defeated (captured, or destroyed by your cards). */
    public static final int COINS_PER_DEFEATED_POINT = 3;
    /** A draw pays this percentage of the win formula. */
    public static final int DRAW_PERCENT = 30;
    /** Mating within this many of your own moves earns the fast-mate bonus. */
    public static final int FAST_MATE_MOVES = 15;
    public static final int FAST_MATE_BONUS = 10;

    public static int levelBase(int level) {
        return 10 + 2 * level;
    }

    /** Breakdown of a round's payout, for the end-of-round screen. */
    public static class Payout {
        public final int ownMaterial;
        public final int defeatedMaterial;
        public final int bonusCoins;
        public final int levelBase;
        public final int total;

        Payout(int ownMaterial, int defeatedMaterial, int bonusCoins, int levelBase, int total) {
            this.ownMaterial = ownMaterial;
            this.defeatedMaterial = defeatedMaterial;
            this.bonusCoins = bonusCoins;
            this.levelBase = levelBase;
            this.total = total;
        }
    }

    public static Payout payout(GameResult result, int ownMaterialLeft, int enemyMaterialDefeated,
                                int bonusCoins, int level) {
        int base = levelBase(level);
        int win = COINS_PER_OWN_POINT * ownMaterialLeft
                + COINS_PER_DEFEATED_POINT * enemyMaterialDefeated
                + bonusCoins + base;
        int total;
        switch (result) {
            case WHITE_WINS: total = win; break;
            case DRAW:       total = Math.max(1, win * DRAW_PERCENT / 100); break;
            default:         total = 0;
        }
        return new Payout(ownMaterialLeft, enemyMaterialDefeated, bonusCoins, base, total);
    }
}
