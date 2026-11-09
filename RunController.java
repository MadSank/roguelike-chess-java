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

        RoundSummary(GameResult result, String resultText, int level, Economy.Payout payout,
                     List<MoveBonuses.Bonus> bonuses, int goldAfter, int nextLevel,
                     boolean gameOver, boolean madeLeaderboard, int highestLevelBeaten) {
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
        s.cardManager = shop.getCards().copy();
        setup.setupCustomChess960Board(s, shop.getArmy());
        s.cardManager.onGameStart(s);

        engine = new ChessEngine(s);
        ai = new AIPlayer(engine, s.aiDepth);
        blackMaterialAtStart = Economy.material(s, false);
        roundBonuses.clear();
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
        return earned;
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
        Economy.Payout payout = Economy.payout(result, own, defeated, bonusCoins, playedLevel);

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
            level, gameOver, madeBoard, highestLevelBeaten);
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
