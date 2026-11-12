import javax.swing.*;

/**
 * Main window: one PixelGame panel (the whole game, drawn as pixel art) at twice the canvas size.
 * The run rules are in RunController; the screens are in PixelGame.
 */
public class RoguelikeChessAppSwing extends JFrame {

    public RoguelikeChessAppSwing() {
        super("Ashes of the Board");
        PixelGame game = new PixelGame();
        add(game);
        pack();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        game.requestFocusInWindow();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new RoguelikeChessAppSwing().setVisible(true);
            AudioPlayer.playMusic("intro.wav", false);
        });
    }
}
