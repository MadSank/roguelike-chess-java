import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Plays a short pre-rendered cutscene full screen (e.g. the opponent swap after a win:
 * the lamp dies on the beaten opponent and comes back on the next one).
 * Click or press any key to skip.
 */
public class CutScene extends JPanel {
    private final ScenePlayer player;
    private Runnable onDone;

    public CutScene() {
        setBackground(Color.BLACK);
        setFocusable(true);
        player = new ScenePlayer(this);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { finish(); }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { finish(); }
        });
    }

    /** Plays the clips back to back, then runs done (also when skipped). */
    public void play(String[] clips, Runnable done) {
        onDone = done;
        requestFocusInWindow();
        player.playClips(clips, "table", this::finish);
    }

    private void finish() {
        Runnable r = onDone;
        onDone = null;
        player.setView("table");
        if (r != null) r.run();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        player.paint(g, getWidth(), getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("Serif", Font.ITALIC, 13));
        g.setColor(new Color(200, 190, 180, 120));
        String hint = "click to skip";
        g.drawString(hint, getWidth() - g.getFontMetrics().stringWidth(hint) - 16, getHeight() - 16);
    }
}
