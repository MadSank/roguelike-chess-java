import java.util.*;

/**
 * The run loop from DESIGN.md, with no Swing in it:
 *   shop -> round -> result -> (next level | replay level) -> shop ... -> game over.
 *
 * The UI calls these methods and draws what they return; tests drive it directly.
 */
public class RunController {

    public enum Phase { MENU, PLAYING, SHOP, GAME_OVER }

    /** Everything the end-of-round screen needs. */
    public static class RoundSummary {
        public final GameResult result;
        public final String resultText;
        public final int level;
        public final Economy.Payout payout;
        public final List<MoveBonuses.Bonus> bonuses;
        public final int goldAfter;
        public final int nextLevel;
        public final boolean gameOver;
        public final boolean madeLeaderboard;
        public final int highestLevelBeaten;
        /** Names of cards that left the stash this round. */
        public final List<String> cardsUsedUp;

        RoundSummary(GameResult result, String resultText, int level, Economy.Payout payout,
                     List<MoveBonuses.Bonus> bonuses, int goldAfter, int nextLevel,
                     boolean gameOver, boolean madeLeaderboard, int highestLevelBeaten,
                     List<String> cardsUsedUp) {
            this.cardsUsedUp = cardsUsedUp;
            this.result = result;
            this.resultText = resultText;
            this.level = level;
            this.payout = payout;
            this.bonuses = bonuses;
            this.goldAfter = goldAfter;
            this.nextLevel = nextLevel;
            this.gameOver = gameOver;
            this.madeLeaderboard = madeLeaderboard;
            this.highestLevelBeaten = highestLevelBeaten;
        }
    }

    private final ShopManager shop = new ShopManager();
    private final ScoreManager scores;
    private final Chess960Setup setup;
    private final Random rng;

    private Phase phase = Phase.MENU;
    private int level = 1;
    private int highestLevelBeaten = 0;

    private ChessEngine engine = new ChessEngine(new GameState());
    private AIPlayer ai;
    private int blackMaterialAtStart;
    private final List<MoveBonuses.Bonus> roundBonuses = new ArrayList<>();

    /** Cards taken into the current (or next) round, chosen from the stash. */
    private final List<Card> hand = new ArrayList<>();
    /** Cards from the hand played this round (they are used up when the round ends). */
    private final List<Card> played = new ArrayList<>();
    /** Messages for the UI about things that just happened (e.g. "AI skips its turn"). */
    private final List<String> events = new ArrayList<>();

    public RunController(ScoreManager scores, Chess960Setup setup, Random rng) {
        this.scores = scores;
        this.setup = setup;
        this.rng = rng;
    }

    public RunController() {
        this(new ScoreManager(), new Chess960Setup(), new Random());
    }

    // ─── ACCESSORS ──────────────────────────────────────────────

    public Phase getPhase() { return phase; }
    public int getLevel() { return level; }
    public int getHighestLevelBeaten() { return highestLevelBeaten; }
    public ShopManager getShop() { return shop; }
    public ScoreManager getScores() { return scores; }
    public ChessEngine getEngine() { return engine; }
    public GameState getRoundState() { return engine.getGameState(); }
    public int getRequirement() { return Economy.requirement(level); }
    public int getAiDepth() { return Difficulty.forLevel(level).searchDepth; }

    /** Hand size: 5 cards, +1 every 3 levels (6 at level 4, 7 at level 7, ...). */
    public static int handSize(int level) {
        return 5 + (Math.max(1, level) - 1) / 3;
    }

    public int getHandSize() { return handSize(level); }

    /** How many different masked opponents there are; they take turns, one per level. */
    public static final int ROSTER_SIZE = 5;

    /** Which opponent (1..ROSTER_SIZE) sits across the table at a level: 1, 2, ... 5, then 1 again. */
    public static int opponentFor(int level) {
        return (Math.max(1, level) - 1) % ROSTER_SIZE + 1;
    }

    public int getOpponent() { return opponentFor(level); }

    public List<Card> getHand() { return Collections.unmodifiableList(hand); }

    public boolean isPlayed(Card card) { return containsSame(played, card); }

    /** Messages since the last call (and clears them). */
    public List<String> takeEvents() {
        List<String> e = new ArrayList<>(events);
        events.clear();
        return e;
    }

    // ─── HAND ───────────────────────────────────────────────────

    /**
     * Chooses the cards to take into the next round: at most the hand size, all from the stash.
     * @return false (hand unchanged) if the choice isn't allowed
     */
    public boolean setHand(List<Card> chosen) {
        if (chosen.size() > getHandSize()) return false;
        List<Card> stash = new ArrayList<>(shop.getCards().getActiveCards());
        for (Card c : chosen) {
            if (!removeSame(stash, c)) return false; // not in the stash (or picked twice)
        }
        hand.clear();
        hand.addAll(chosen);
        return true;
    }

    /** Drops hand cards that are no longer in the stash (sold) and fills an empty hand automatically. */
    private void validateHand() {
        List<Card> stash = new ArrayList<>(shop.getCards().getActiveCards());
        hand.removeIf(c -> !removeSame(stash, c));
        if (hand.isEmpty()) {
            List<Card> all = shop.getCards().getActiveCards();
            hand.addAll(all.subList(0, Math.min(getHandSize(), all.size())));
        }
    }

    /**
     * Plays a card from the hand. Doesn't use up the player's move; the card stays active
     * until the round ends.
     * @return null on success, otherwise why it can't be played
     */
    public String playCard(Card card) {
        GameState s = engine.getGameState();
        if (phase != Phase.PLAYING || s.gameOver) return "No round in progress.";
        if (!s.whiteToMove) return "Wait for your turn.";
        if (!containsSame(hand, card)) return "That card isn't in your hand.";
        if (containsSame(played, card)) return "Already played.";
        if (s.cardManager.isActive(card)) return card.getName() + " is already active.";
        s.cardManager.play(card, s);
        played.add(card);
        engine.refreshEndConditions();
        return null;
    }

    private static boolean containsSame(List<Card> list, Card card) {
        for (Card c : list) if (c == card) return true;
        return false;
    }

    private static boolean removeSame(List<Card> list, Card card) {
        for (Iterator<Card> it = list.iterator(); it.hasNext(); ) {
            if (it.next() == card) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    /** Gold shown during a round includes what cards like Golden Edge paid so far. */
    public int getGold() {
        return phase == Phase.PLAYING ? engine.getGameState().goldPlayer : shop.getGold();
    }

    // ─── RUN START ──────────────────────────────────────────────

    /** Starts a fresh run and its first round (King + 2 Pawns meets level 1). */
    public void startNewRun(String playerName) {
        scores.setPlayerName(playerName);
        shop.resetForNewRun();
        level = 1;
        highestLevelBeaten = 0;
        hand.clear();
        startRound();
    }

    // ─── ROUND ──────────────────────────────────────────────────

    /**
     * Starts the current level if the army meets its requirement.
     * @return false (and stays in the shop) if it doesn't
     */
    public boolean startRound() {
        if (!shop.meetsRequirement(level)) {
            phase = Phase.SHOP;
            return false;
        }
        GameState s = new GameState();
        s.roundNumber = level;
        s.aiDepth = getAiDepth();
        s.goldPlayer = shop.getGold();
        s.cardManager = new CardManager(); // cards become active only when played
        setup.setupCustomChess960Board(s, shop.getArmy());

        engine = new ChessEngine(s);
        ai = new AIPlayer(Difficulty.forLevel(level), rng);
        blackMaterialAtStart = Economy.material(s, false);
        roundBonuses.clear();
        played.clear();
        events.clear();
        validateHand();
        phase = Phase.PLAYING;
        return true;
    }

    /**
     * Plays the player's move.
     * @return the good-move bonuses it earned (possibly empty), or null if the move is illegal
     */
    public List<MoveBonuses.Bonus> playerMove(Move move) {
        if (phase != Phase.PLAYING || !engine.getGameState().whiteToMove) return null;
        GameState before = engine.getGameState().copy();
        if (!engine.applyMove(move)) return null;
        Move played = findPlayed(before, move);
        List<MoveBonuses.Bonus> earned = played == null
            ? Collections.emptyList()
            : MoveBonuses.detect(before, played, engine.getGameState());
        roundBonuses.addAll(earned);
        applyTimeStop();
        return earned;
    }

    /** Time Stop: the AI loses its turn, unless it is in check (then the skip waits). */
    private void applyTimeStop() {
        GameState s = engine.getGameState();
        if (s.gameOver || s.getCounter(CardTimeStop.PENDING) == 0) return;
        if (engine.isKingInCheck(s, false)) return;
        s.setCounter(CardTimeStop.PENDING, 0);
        engine.passTurn();
        events.add("Time Stop: the AI skips its turn");
    }

    /** The engine's own version of the move (with capture / en passant / sniper flags). */
    private Move findPlayed(GameState before, Move request) {
        ChessEngine e = new ChessEngine(before);
        for (Move m : e.getLegalMovesFromSquare(request.fromRow, request.fromCol)) {
            if (m.toRow != request.toRow || m.toCol != request.toCol) continue;
            if (m.isSniperShot != request.isSniperShot) continue;
            if (m.isSniperShot && (m.sniperTargetRow != request.sniperTargetRow
                    || m.sniperTargetCol != request.sniperTargetCol)) continue;
            if (m.isPromotion && request.promotionType != null && !request.promotionType.equals(m.promotionType)) continue;
            return m;
        }
        return null;
    }

    public boolean isAiTurn() {
        return phase == Phase.PLAYING && !engine.getGameState().whiteToMove && !engine.getGameState().gameOver;
    }

    /** Thread-safe: works on a snapshot, so the UI can call it from a background thread. */
    public Move computeAiMove(GameState snapshot) {
        return ai.chooseMove(snapshot);
    }

    public boolean applyAiMove(Move move) {
        return isAiTurn() && move != null && engine.applyMove(move);
    }

    public boolean isRoundOver() {
        return phase == Phase.PLAYING && engine.getGameState().gameOver;
    }

    /**
     * Settles a finished round: pays out, updates the army, moves to the next level or
     * replays this one, and ends the run if the player can't continue.
     */
    public RoundSummary finishRound() {
        GameState s = engine.getGameState();
        GameResult result = s.result;
        int playedLevel = level;

        List<MoveBonuses.Bonus> bonuses = new ArrayList<>(roundBonuses);
        if (result == GameResult.WHITE_WINS) {
            int whiteMoves = (s.movesToEnd + 1) / 2;
            if (whiteMoves <= Economy.FAST_MATE_MOVES) {
                bonuses.add(new MoveBonuses.Bonus("Fast mate", Economy.FAST_MATE_BONUS));
            }
        }
        int bonusCoins = 0;
        for (MoveBonuses.Bonus b : bonuses) bonusCoins += b.coins;

        int own = Economy.material(s, true);
        int defeated = Math.max(0, blackMaterialAtStart - Economy.material(s, false));
        Economy.Payout payout = Economy.payout(result, own, defeated, bonusCoins, playedLevel, s.goldPlayer);

        // s.goldPlayer already includes coins cards paid during the round.
        shop.setGold(s.goldPlayer + payout.total);

        switch (result) {
            case WHITE_WINS:
                shop.setArmyFromBoard(s);
                highestLevelBeaten = Math.max(highestLevelBeaten, playedLevel);
                level = playedLevel + 1;
                break;
            case DRAW:
                shop.setArmyFromBoard(s);
                break;
            default:
                shop.clearArmy(); // only the King remains
        }

        // Cards played are used up. After a loss, unplayed hand cards are lost too;
        // after a win or draw they stay in the stash.
        List<String> cardsUsedUp = new ArrayList<>();
        for (Card c : (result == GameResult.BLACK_WINS ? hand : played)) {
            if (shop.getCards().removeCard(c)) cardsUsedUp.add(c.getName());
        }
        hand.clear();
        played.clear();

        boolean gameOver = !shop.canStillReach(level);
        boolean madeBoard = false;
        if (gameOver) {
            phase = Phase.GAME_OVER;
            madeBoard = scores.submit(highestLevelBeaten);
        } else {
            phase = Phase.SHOP;
            shop.generateCardOfferings(level, rng);
        }
        return new RoundSummary(result, s.gameResult, playedLevel, payout, bonuses, shop.getGold(),
            level, gameOver, madeBoard, highestLevelBeaten, cardsUsedUp);
    }

    /** Leaves the shop. Returns false if the army doesn't meet the requirement yet. */
    public boolean finishShopping() {
        return startRound();
    }

    /** Ends the run from the shop (e.g. the player gives up). */
    public boolean abandonRun() {
        phase = Phase.GAME_OVER;
        return scores.submit(highestLevelBeaten);
    }

    public void returnToMenu() {
        phase = Phase.MENU;
    }
}
