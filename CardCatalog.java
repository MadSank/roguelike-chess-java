import java.util.*;
import java.util.function.Supplier;

/**
 * Every card in the game and its rarity. To add a card: write the class, add one line here.
 */
public final class CardCatalog {
    private CardCatalog() {}

    public enum Rarity {
        COMMON(60), UNCOMMON(30), RARE(10);

        /** Base shop weight; rarer cards get more likely at higher levels (see shopWeight). */
        public final int weight;

        Rarity(int weight) {
            this.weight = weight;
        }
    }

    private static final class Entry {
        final Supplier<Card> factory;
        final Rarity rarity;
        final String name;

        Entry(Supplier<Card> factory, Rarity rarity) {
            this.factory = factory;
            this.rarity = rarity;
            this.name = factory.get().getName();
        }
    }

    private static final List<Entry> ENTRIES = Arrays.asList(
        // Local cards (help only the player)
        new Entry(CardGoldenEdge::new,        Rarity.COMMON),
        new Entry(CardPawnPhalanx::new,       Rarity.COMMON),
        new Entry(CardCavalryCharge::new,     Rarity.COMMON),
        new Entry(CardPawnSurge::new,         Rarity.COMMON),
        new Entry(CardPawnRetreat::new,       Rarity.COMMON),
        new Entry(CardBishopShift::new,       Rarity.COMMON),
        new Entry(CardSniperBishops::new,     Rarity.UNCOMMON),
        new Entry(CardVaultingMajors::new,    Rarity.UNCOMMON),
        new Entry(CardKingsStride::new,       Rarity.UNCOMMON),
        new Entry(CardSoulHarvest::new,       Rarity.UNCOMMON),
        new Entry(CardNecromancerGambit::new, Rarity.UNCOMMON),
        new Entry(CardPhantomKing::new,       Rarity.RARE),
        new Entry(CardTimeStop::new,          Rarity.RARE),
        // Global cards (change the rules for both sides)
        new Entry(CardTrenchWarfare::new,     Rarity.COMMON),
        new Entry(CardGlassCannons::new,      Rarity.COMMON),
        new Entry(CardMinefield::new,         Rarity.UNCOMMON),
        new Entry(CardHeavyArmor::new,        Rarity.UNCOMMON),
        new Entry(CardBloodMoon::new,         Rarity.UNCOMMON),
        new Entry(CardChronoShift::new,       Rarity.RARE),
        new Entry(CardIronPawns::new,         Rarity.COMMON),
        new Entry(CardSmuggledPawn::new,      Rarity.COMMON),
        new Entry(CardBloodMoney::new,        Rarity.UNCOMMON),
        new Entry(CardBlindfold::new,         Rarity.UNCOMMON)
    );

    /** A fresh instance of every card. */
    public static List<Card> all() {
        List<Card> cards = new ArrayList<>();
        for (Entry e : ENTRIES) cards.add(e.factory.get());
        return cards;
    }

    public static Rarity rarityOf(Card card) {
        for (Entry e : ENTRIES) if (e.name.equals(card.getName())) return e.rarity;
        return Rarity.COMMON;
    }

    /** Shop weight at a level: commons fade a little and rares grow as levels rise. */
    public static int shopWeight(Rarity r, int level) {
        switch (r) {
            case COMMON:   return Math.max(30, r.weight - 2 * level);
            case UNCOMMON: return r.weight + level;
            default:       return r.weight + 2 * level;
        }
    }

    /** Picks up to count different cards, weighted by rarity for the level. */
    public static List<Card> drawOffer(int count, int level, Random rng, Set<String> exclude) {
        List<Card> pool = new ArrayList<>();
        for (Card c : all()) if (!exclude.contains(c.getName())) pool.add(c);
        List<Card> picked = new ArrayList<>();
        while (picked.size() < count && !pool.isEmpty()) {
            int total = 0;
            for (Card c : pool) total += shopWeight(c.getRarity(), level);
            int roll = rng.nextInt(total);
            for (Iterator<Card> it = pool.iterator(); it.hasNext(); ) {
                Card c = it.next();
                roll -= shopWeight(c.getRarity(), level);
                if (roll < 0) {
                    picked.add(c);
                    it.remove();
                    break;
                }
            }
        }
        return picked;
    }
}
