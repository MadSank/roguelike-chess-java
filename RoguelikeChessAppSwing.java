import javax.swing.*;
import java.awt.*;
import java.util.List;

public class RoguelikeChessAppSwing extends JFrame {

    private static final int WINDOW_WIDTH = 900;
    private static final int WINDOW_HEIGHT = 700;
    private static final int MAX_AI_DEPTH = 16;
    /** A draw pays this percentage of what a win would have paid. */
    private static final int DRAW_PAYOUT_PERCENT = 30;

    private ChessEngine engine;
    private AIPlayer aiPlayer;
    private ShopManager shopManager;
    private Chess960Setup chess960;
    private ScoreManager scoreManager;
    private CardLayout sceneLayout;
    private JPanel sceneContainer;
    private BoardViewSwing boardView;
    private MenuSwing menuScene;
    private ShopScene shopScene;
    private GamePhase currentPhase;
    /** True when the next round replays the current level (after a draw). */
    private boolean replayLevel;
    /** True while the AI is searching on a background thread. */
    private boolean aiThinking;

    public enum GamePhase { MENU, PLAYING, SHOPPING, GAME_OVER }

    public RoguelikeChessAppSwing() {
        super("Ashes of the Board - Roguelike Chess");
        initializeGame();
        initializeUI();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setLocationRelativeTo(null);
        setResizable(false);
    }

    private void initializeGame() {
        engine = new ChessEngine();
        chess960 = new Chess960Setup();
        scoreManager = new ScoreManager();
        shopManager = new ShopManager();
        currentPhase = GamePhase.MENU;
    }

    private void initializeUI() {
        sceneLayout = new CardLayout();
        sceneContainer = new JPanel(sceneLayout);

        menuScene = new MenuSwing(this);
        boardView = new BoardViewSwing(this, engine);
        shopScene = new ShopScene(this, engine, shopManager, scoreManager);

        sceneContainer.add(menuScene, "MENU");
        sceneContainer.add(boardView, "BOARD");
        sceneContainer.add(shopScene, "SHOP");

        add(sceneContainer);
        sceneLayout.show(sceneContainer, "MENU");
    }

    public void startNewGameSession() {
        String name = JOptionPane.showInputDialog(this,
                "Enter your name for the leaderboard:", "New Game",
                JOptionPane.QUESTION_MESSAGE);
        if (name == null || name.trim().isEmpty()) name = "Anonymous";
        scoreManager.setPlayerName(name.trim());
        scoreManager.resetSession();
        shopManager.resetForNewRun();
        replayLevel = false;

        GameState gs = new GameState();
        gs.aiDepth = 2;
        gs.goldPlayer = 0;
        gs.roundNumber = 1;
        gs.cardManager = new CardManager();

        List<Piece> starting = shopManager.collectPurchasedPieces();
        chess960.setupCustomChess960Board(gs, starting);
        gs.cardManager.onGameStart(gs);

        startRound(gs);
    }

    public void onShoppingComplete() {
        GameState old = engine.getGameState();
        List<Piece> pieces = shopManager.collectPurchasedPieces();

        GameState fresh = new GameState();
        fresh.goldPlayer = shopManager.getGoldFromPreviousGame();
        if (replayLevel) {
            fresh.roundNumber = old.roundNumber;
            fresh.aiDepth = old.aiDepth;
        } else {
            fresh.roundNumber = old.roundNumber + 1;
            fresh.aiDepth = Math.min(old.aiDepth + 1, MAX_AI_DEPTH);
        }

        // Carry cards forward (persist between rounds)
        fresh.cardManager = old.cardManager != null ? old.cardManager.copy() : new CardManager();

        chess960.setupCustomChess960Board(fresh, pieces);
        fresh.cardManager.onGameStart(fresh);
        ensureBothKings(fresh);

        startRound(fresh);

        String message;
        if (replayLevel) {
            message = String.format("Replaying Round %d%nGold: %d | Score: %,d%nActive Cards: %d",
                fresh.roundNumber, fresh.goldPlayer, scoreManager.getCurrentScore(),
                fresh.cardManager.getActiveCards().size());
        } else {
            message = String.format("Round %d%nGold: %d | Score: %,d%nActive Cards: %d%nAI depth %d -> %d",
                fresh.roundNumber, fresh.goldPlayer, scoreManager.getCurrentScore(),
                fresh.cardManager.getActiveCards().size(), old.aiDepth, fresh.aiDepth);
        }
        replayLevel = false;
        JOptionPane.showMessageDialog(this, message, "New Round", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Shows the board for a new round played from the given state. */
    private void startRound(GameState state) {
        engine = new ChessEngine(state);
        aiPlayer = new AIPlayer(engine, state.aiDepth);
        aiThinking = false;

        sceneContainer.remove(boardView);
        boardView = new BoardViewSwing(this, engine);
        sceneContainer.add(boardView, "BOARD");

        currentPhase = GamePhase.PLAYING;
        sceneLayout.show(sceneContainer, "BOARD");
        boardView.refresh();
        AudioPlayer.playMusic("battle.wav", true);
    }

    /** Safety net: a round can't start without both kings. */
    private void ensureBothKings(GameState state) {
        boolean whiteKing = false;
        boolean blackKing = false;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p instanceof King) {
                    if (p.isWhite()) whiteKing = true;
                    else blackKing = true;
                }
            }
        }
        for (int c = 0; c < 8 && !whiteKing; c++) {
            if (state.board[7][c] == null) {
                state.board[7][c] = new King(true);
                whiteKing = true;
                System.err.println("Missing white king: added at (7," + c + ")");
            }
        }
        for (int c = 0; c < 8 && !blackKing; c++) {
            if (state.board[0][c] == null) {
                state.board[0][c] = new King(false);
                blackKing = true;
                System.err.println("Missing black king: added at (0," + c + ")");
            }
        }
        state.whiteToMove = true;
        state.setResult(GameResult.ONGOING, "");
        state.piecesLeftStanding = Utils.countPiecesOnBoard(state);
    }

    public boolean isAiThinking() {
        return aiThinking;
    }

    public void onPlayerMove(Move move) {
        if (currentPhase != GamePhase.PLAYING || aiThinking) return;
        if (!engine.applyMove(move)) {
            JOptionPane.showMessageDialog(this, "Illegal move!");
            return;
        }
        AudioPlayer.playMoveSound();
        if (move.isCapture) AudioPlayer.playCaptureSound();
        boardView.refresh();

        if (engine.getGameState().gameOver) {
            handleGameOver();
            return;
        }
        performAIMove();
    }

    /** Runs the AI search off the Swing thread so the window stays responsive. */
    private void performAIMove() {
        final ChessEngine roundEngine = engine;
        final GameState snapshot = roundEngine.getGameState().copy();
        final AIPlayer ai = aiPlayer;
        aiThinking = true;
        boardView.refresh();

        new SwingWorker<Move, Void>() {
            @Override
            protected Move doInBackground() {
                return ai.chooseMove(snapshot);
            }

            @Override
            protected void done() {
                aiThinking = false;
                if (roundEngine != engine || currentPhase != GamePhase.PLAYING) return; // round changed meanwhile
                Move m;
                try {
                    m = get();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    m = null;
                }
                if (m == null || !engine.applyMove(m)) {
                    System.err.println("AI produced no legal move: " + m);
                    boardView.refresh();
                    return;
                }
                AudioPlayer.playMoveSound();
                if (m.isCapture) AudioPlayer.playCaptureSound();
                boardView.refresh();
                if (engine.getGameState().gameOver) handleGameOver();
            }
        }.execute();
    }

    private void handleGameOver() {
        GameState s = engine.getGameState();
        switch (s.result) {
            case WHITE_WINS: {
                int earn = calculateRoundEarnings(s);
                s.goldPlayer += earn;
                scoreManager.addMatchScore(earn, s.aiDepth, s.goldPlayer);
                JOptionPane.showMessageDialog(this,
                    String.format("Victory!%nGold Earned: %d%nTotal Gold: %d%nScore: %,d%nDepth %d%nCards: %d",
                        earn, s.goldPlayer, scoreManager.getCurrentScore(), s.aiDepth,
                        s.cardManager.getActiveCards().size()),
                    "Round Complete", JOptionPane.INFORMATION_MESSAGE);
                replayLevel = false;
                transitionToShop();
                break;
            }
            case DRAW: {
                int earn = Math.max(1, calculateRoundEarnings(s) * DRAW_PAYOUT_PERCENT / 100);
                s.goldPlayer += earn;
                scoreManager.addMatchScore(earn, s.aiDepth, s.goldPlayer);
                JOptionPane.showMessageDialog(this,
                    String.format("Draw: %s%nGold Earned: %d%nTotal Gold: %d%nYou will replay Round %d.",
                        s.gameResult, earn, s.goldPlayer, s.roundNumber),
                    "Round Drawn", JOptionPane.INFORMATION_MESSAGE);
                replayLevel = true;
                transitionToShop();
                break;
            }
            default:
                handleDefeat(s);
        }
    }

    private void handleDefeat(GameState s) {
        AudioPlayer.playCheckmateSound();
        long finalScore = scoreManager.getCurrentScore();
        boolean high = scoreManager.isHighScore();

        if (s.cardManager != null) {
            s.cardManager.resetForNewRun();
        }

        JOptionPane.showMessageDialog(this,
            String.format("Defeat!%nFinal Score: %,d%nRounds: %d%nDepth %d%n%s",
                finalScore, s.roundNumber, s.aiDepth,
                high ? "NEW HIGH SCORE!" : ""),
            "Game Over", JOptionPane.ERROR_MESSAGE);
        if (high && finalScore > 0) {
            scoreManager.submitScore(s.roundNumber);
            JOptionPane.showMessageDialog(this,
                "Score saved to leaderboard!", "High Score",
                JOptionPane.INFORMATION_MESSAGE);
        }
        currentPhase = GamePhase.GAME_OVER;
        sceneLayout.show(sceneContainer, "MENU");
        menuScene.showMenu();
    }

    private int calculateRoundEarnings(GameState s) {
        int base   = s.capturesMade * 5;
        int alive  = s.piecesLeftStanding * 2;
        int spec   = s.specialMovesUsed * 3;
        int fast   = s.movesToEnd <= 10 ? 50 : s.movesToEnd <= 20 ? 30 :
            s.movesToEnd <= 30 ? 15 : s.movesToEnd <= 50 ? 5 : 0;
        int round  = s.roundNumber * 10;
        return Math.max(10, base + alive + spec + fast + round);
    }

    private void transitionToShop() {
        currentPhase = GamePhase.SHOPPING;

        GameState state = engine.getGameState();
        shopManager.saveGoldForNextGame(state.goldPlayer);
        shopManager.loadSurvivingPieces(state);
        shopManager.generateCardOfferings(state.cardManager, state.roundNumber);

        sceneLayout.show(sceneContainer, "SHOP");
        shopScene.refresh();
    }

    /** Called by the menu's "New Game" button. */
    public void startMatch() {
        if (currentPhase == GamePhase.MENU || currentPhase == GamePhase.GAME_OVER) {
            startNewGameSession();
        } else {
            currentPhase = GamePhase.PLAYING;
            sceneLayout.show(sceneContainer, "BOARD");
            boardView.refresh();
            AudioPlayer.playMusic("battle.wav", true);
        }
    }

    public ChessEngine getEngine() { return engine; }

    public GamePhase getCurrentPhase() { return currentPhase; }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ResourceLoader.preloadPieceImages();
            RoguelikeChessAppSwing app = new RoguelikeChessAppSwing();
            app.setVisible(true);
            AudioPlayer.playMusic("intro.wav", false);
        });
    }
}
