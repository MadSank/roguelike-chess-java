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
    private final IntroScene introScene;
    private final CutScene cutScene;
    /** True while the AI is searching on a background thread. */
    private boolean aiThinking;
    /** True while the opponent reacts to the end of a round, before the summary shows. */
    private boolean roundEnding;
    /** How long the opponent's win/lose reaction plays before the round summary, in ms. */
    private static final int END_REACTION_MS = 1700;

    public RoguelikeChessAppSwing() {
        super("Ashes of the Board - Roguelike Chess");
        menuScene = new MenuSwing(this);
        boardView = new BoardViewSwing(this);
        shopScene = new ShopScene(this);
        introScene = new IntroScene();
        cutScene = new CutScene();
        sceneContainer.add(introScene, "INTRO");
        sceneContainer.add(cutScene, "CUT");
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
        if (ScenePlayer.scenesAvailable()) {
            sceneLayout.show(sceneContainer, "INTRO");
            AudioPlayer.playMusic("battle.wav", true);
            introScene.start(this::showBoard);
        } else {
            showBoard();
        }
    }

    public String getLeaderboardText() {
        return run.getScores().getLeaderboardText();
    }

    private void showBoard() {
        aiThinking = false;
        roundEnding = false;
        boardView.setOpponentThinking(false);
        boardView.setOpponent(run.getOpponent());
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

    /**
     * Shop "Sit down" button. Returns true if the round started; false if the requirement
     * isn't met or the player cancelled the hand picker (they stay in the shop).
     */
    public boolean onShoppingComplete() {
        if (!run.getShop().meetsRequirement(run.getLevel())) return false;
        if (!chooseHand()) return false;
        if (!run.finishShopping()) return false;
        showBoard();
        return true;
    }

    /** Lets the player pick which stash cards to take into the round. Returns false if cancelled. */
    private boolean chooseHand() {
        List<Card> stash = run.getShop().getCards().getActiveCards();
        if (stash.isEmpty()) return true;
        int max = run.getHandSize();
        JPanel panel = new JPanel(new GridLayout(0, 1, 2, 2));
        panel.add(new JLabel("Take up to " + max + " cards into level " + run.getLevel()
            + ". Cards you play are used up; if you lose, your whole hand is lost."));
        List<JCheckBox> boxes = new java.util.ArrayList<>();
        for (int i = 0; i < stash.size(); i++) {
            Card c = stash.get(i);
            JCheckBox box = new JCheckBox(c.getName() + "  [" + c.getRarity() + (c.isGlobal() ? ", global" : "") + "]  "
                + c.getDescription(), i < max);
            box.addActionListener(e -> {
                long checked = boxes.stream().filter(JCheckBox::isSelected).count();
                if (checked > max) box.setSelected(false);
            });
            boxes.add(box);
            panel.add(box);
        }
        while (true) {
            int ok = JOptionPane.showConfirmDialog(this, new JScrollPane(panel), "Choose your hand",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return false;
            List<Card> chosen = new java.util.ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) if (boxes.get(i).isSelected()) chosen.add(stash.get(i));
            if (run.setHand(chosen)) return true;
            JOptionPane.showMessageDialog(this, "Pick at most " + max + " cards.");
        }
    }

    /** Hand panel "Play" button. Playing a card doesn't use your move. */
    public void onPlayCard(Card card) {
        if (aiThinking) return;
        String error = run.playCard(card);
        if (error != null) {
            boardView.showToast(error);
            return;
        }
        AudioPlayer.playMoveSound();
        boardView.showToast(card.getName() + " played");
        boardView.refresh();
        if (run.isRoundOver()) endRound();
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
        for (String e : run.takeEvents()) boardView.showToast(e);
        boardView.refresh();
        if (move.isCapture && !run.isRoundOver()) boardView.opponentReact("hurt");

        if (run.isRoundOver()) {
            endRound();
        } else if (run.isAiTurn()) {
            performAIMove();
        } // otherwise (Time Stop) it's the player's turn again
    }

    /** Runs the AI search off the Swing thread so the window stays responsive. */
    private void performAIMove() {
        final GameState snapshot = run.getRoundState().copy();
        final ChessEngine roundEngine = run.getEngine();
        aiThinking = true;
        boardView.setOpponentThinking(true);
        boardView.refresh();

        new SwingWorker<Move, Void>() {
            @Override
            protected Move doInBackground() {
                return run.computeAiMove(snapshot);
            }

            @Override
            protected void done() {
                aiThinking = false;
                boardView.setOpponentThinking(false);
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
                if (!run.isRoundOver()) {
                    GameState st = run.getRoundState();
                    if (run.getEngine().isKingInCheck(st, true)) boardView.opponentReact("check");
                    else if (m.isCapture) boardView.opponentReact("capture");
                }
                if (run.isRoundOver()) endRound();
            }
        }.execute();
    }

    /** The round is over: the opponent reacts (if rendered), then the summary shows. */
    private void endRound() {
        if (roundEnding) return;
        GameResult result = run.getRoundState().result;
        if (!boardView.hasOpponentPortrait() || (result != GameResult.WHITE_WINS && result != GameResult.BLACK_WINS)) {
            showRoundSummary();
            return;
        }
        // Checkmated: the opponent lunges across the table at you (if that clip was rendered).
        String scare = "scare_" + run.getOpponent();
        if (result == GameResult.BLACK_WINS && SceneClip.load(scare) != null) {
            roundEnding = true;
            AudioPlayer.playCheckmateSound();
            sceneLayout.show(sceneContainer, "CUT");
            cutScene.play(new String[]{scare}, () -> {
                roundEnding = false;
                sceneLayout.show(sceneContainer, "BOARD");
                showRoundSummary();
            }, 0.5);
            return;
        }
        roundEnding = true;
        boardView.opponentReact(result == GameResult.BLACK_WINS ? "win" : "lose");
        javax.swing.Timer t = new javax.swing.Timer(END_REACTION_MS, e -> {
            roundEnding = false;
            showRoundSummary();
        });
        t.setRepeats(false);
        t.start();
    }

    private void showRoundSummary() {
        RunController.RoundSummary s = run.finishRound();
        StringBuilder msg = new StringBuilder();
        String title;
        switch (s.result) {
            case WHITE_WINS: title = "Victory"; msg.append("You beat level ").append(s.level).append("!\n"); break;
            case DRAW:       title = "Draw";    msg.append(s.resultText).append("\nYou keep your pieces and replay level ").append(s.level).append(".\n"); break;
            default:         title = "Defeat";  msg.append("Checkmated. Only your King survives.\nRebuild your army and replay level ").append(s.level).append(".\n");
        }
        if (s.result != GameResult.BLACK_WINS) {
            msg.append(String.format("%nYour material left: %d x%d%nEnemy material defeated: %d x%.1f%nGood moves: %d%nLevel bonus: %d%n",
                s.payout.ownMaterial, Economy.COINS_PER_OWN_POINT,
                s.payout.defeatedMaterial, Economy.TENTHS_PER_DEFEATED_POINT / 10.0,
                s.payout.bonusCoins, s.payout.levelBase));
            if (s.result == GameResult.DRAW) msg.append("Draws pay ").append(Economy.DRAW_PERCENT).append("%\n");
            msg.append("Interest on savings: ").append(s.payout.interest).append("\n");
            msg.append("Gold earned: ").append(s.payout.total).append("\n");
        }
        msg.append("Gold: ").append(s.goldAfter);
        if (!s.cardsUsedUp.isEmpty()) {
            msg.append("\nCards used up: ").append(String.join(", ", s.cardsUsedUp));
        }

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
        } else if (s.result == GameResult.WHITE_WINS) {
            swapOpponents(s.level, s.nextLevel);
        } else {
            showShop();
        }
    }

    /**
     * After a win: the lamp dies on the beaten opponent and comes back on the next one
     * (pre-rendered "swap_out_<n>" and "swap_in_<n>"), then the shop. Skipped if not rendered.
     */
    private void swapOpponents(int beatenLevel, int nextLevel) {
        String[] clips = {"swap_out_" + RunController.opponentFor(beatenLevel),
                          "swap_in_" + RunController.opponentFor(nextLevel)};
        if (!SceneClip.available(clips)) {
            showShop();
            return;
        }
        sceneLayout.show(sceneContainer, "CUT");
        cutScene.play(clips, this::showShop);
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
