import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Main window. Only Swing lives here: the run rules are in RunController.
 */
public class RoguelikeChessAppSwing extends JFrame {

    private static final int WINDOW_WIDTH = 900;
    private static final int WINDOW_HEIGHT = 700;

    private final RunController run = new RunController();
    private final CardLayout sceneLayout = new CardLayout();
    private final JPanel sceneContainer = new JPanel(sceneLayout);
    private final MenuSwing menuScene;
    private final BoardViewSwing boardView;
    private final ShopScene shopScene;
    /** True while the AI is searching on a background thread. */
    private boolean aiThinking;

    public RoguelikeChessAppSwing() {
        super("Ashes of the Board - Roguelike Chess");
        menuScene = new MenuSwing(this);
        boardView = new BoardViewSwing(this);
        shopScene = new ShopScene(this);
        sceneContainer.add(menuScene, "MENU");
        sceneContainer.add(boardView, "BOARD");
        sceneContainer.add(shopScene, "SHOP");
        add(sceneContainer);
        sceneLayout.show(sceneContainer, "MENU");

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setLocationRelativeTo(null);
        setResizable(false);
    }

    public RunController getRun() { return run; }

    public boolean isAiThinking() { return aiThinking; }

    // ─── SCENES ─────────────────────────────────────────────────

    /** Menu "New Game" button. */
    public void startMatch() {
        String name = JOptionPane.showInputDialog(this,
                "Enter your name for the leaderboard:", "New Game",
                JOptionPane.QUESTION_MESSAGE);
        if (name == null) return; // cancelled
        if (name.trim().isEmpty()) name = "Anonymous";
        run.startNewRun(name.trim());
        showBoard();
    }

    public String getLeaderboardText() {
        return run.getScores().getLeaderboardText();
    }

    private void showBoard() {
        aiThinking = false;
        boardView.refresh();
        sceneLayout.show(sceneContainer, "BOARD");
        AudioPlayer.playMusic("battle.wav", true);
    }

    private void showShop() {
        shopScene.refresh();
        sceneLayout.show(sceneContainer, "SHOP");
    }

    private void showMenu() {
        run.returnToMenu();
        sceneLayout.show(sceneContainer, "MENU");
        menuScene.showMenu();
    }

    /** Shop "Continue" button. Returns false if the requirement isn't met yet. */
    public boolean onShoppingComplete() {
        if (!run.finishShopping()) return false;
        showBoard();
        return true;
    }

    /** Shop "Give up" button. */
    public void onAbandonRun() {
        int ok = JOptionPane.showConfirmDialog(this, "End this run and record it on the leaderboard?",
            "Give up", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        boolean board = run.abandonRun();
        JOptionPane.showMessageDialog(this, String.format("Run over.%nHighest level beaten: %d%s",
            run.getHighestLevelBeaten(), board ? "\nYou made the leaderboard!" : ""));
        showMenu();
    }

    // ─── PLAYING ────────────────────────────────────────────────

    public void onPlayerMove(Move move) {
        if (aiThinking) return;
        List<MoveBonuses.Bonus> bonuses = run.playerMove(move);
        if (bonuses == null) {
            boardView.showToast("Illegal move");
            return;
        }
        AudioPlayer.playMoveSound();
        if (move.isCapture) AudioPlayer.playCaptureSound();
        for (MoveBonuses.Bonus b : bonuses) boardView.showToast(b.toString() + " gold");
        boardView.refresh();

        if (run.isRoundOver()) {
            endRound();
        } else {
            performAIMove();
        }
    }

    /** Runs the AI search off the Swing thread so the window stays responsive. */
    private void performAIMove() {
        final GameState snapshot = run.getRoundState().copy();
        final ChessEngine roundEngine = run.getEngine();
        aiThinking = true;
        boardView.refresh();

        new SwingWorker<Move, Void>() {
            @Override
            protected Move doInBackground() {
                return run.computeAiMove(snapshot);
            }

            @Override
            protected void done() {
                aiThinking = false;
                if (roundEngine != run.getEngine()) return; // round changed meanwhile
                Move m;
                try {
                    m = get();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    m = null;
                }
                if (!run.applyAiMove(m)) {
                    System.err.println("AI produced no legal move: " + m);
                    boardView.refresh();
                    return;
                }
                AudioPlayer.playMoveSound();
                if (m.isCapture) AudioPlayer.playCaptureSound();
                boardView.refresh();
                if (run.isRoundOver()) endRound();
            }
        }.execute();
    }

    private void endRound() {
        RunController.RoundSummary s = run.finishRound();
        StringBuilder msg = new StringBuilder();
        String title;
        switch (s.result) {
            case WHITE_WINS: title = "Victory"; msg.append("You beat level ").append(s.level).append("!\n"); break;
            case DRAW:       title = "Draw";    msg.append(s.resultText).append("\nYou keep your pieces and replay level ").append(s.level).append(".\n"); break;
            default:         title = "Defeat";  msg.append("Checkmated. Only your King survives.\nRebuild your army and replay level ").append(s.level).append(".\n");
        }
        if (s.result != GameResult.BLACK_WINS) {
            msg.append(String.format("%nYour material left: %d x%d%nEnemy material defeated: %d x%d%nGood moves: %d%nLevel bonus: %d%n",
                s.payout.ownMaterial, Economy.COINS_PER_OWN_POINT,
                s.payout.defeatedMaterial, Economy.COINS_PER_DEFEATED_POINT,
                s.payout.bonusCoins, s.payout.levelBase));
            if (s.result == GameResult.DRAW) msg.append("Draws pay ").append(Economy.DRAW_PERCENT).append("%\n");
            msg.append("Gold earned: ").append(s.payout.total).append("\n");
        }
        msg.append("Gold: ").append(s.goldAfter);

        if (s.result == GameResult.BLACK_WINS) AudioPlayer.playCheckmateSound();
        JOptionPane.showMessageDialog(this, msg.toString(), title,
            s.result == GameResult.BLACK_WINS ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);

        if (s.gameOver) {
            JOptionPane.showMessageDialog(this, String.format(
                "You can't reach level %d's requirement of %d material, even selling everything.%n%n"
                    + "Run over. Highest level beaten: %d%s",
                s.nextLevel, Economy.requirement(s.nextLevel), s.highestLevelBeaten,
                s.madeLeaderboard ? "\nYou made the leaderboard!" : ""),
                "Game Over", JOptionPane.ERROR_MESSAGE);
            showMenu();
        } else {
            showShop();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ResourceLoader.preloadPieceImages();
            RoguelikeChessAppSwing app = new RoguelikeChessAppSwing();
            app.setVisible(true);
            AudioPlayer.playMusic("intro.wav", false);
        });
    }
}
