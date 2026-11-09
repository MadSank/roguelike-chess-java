import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * The opening cutscene: the bathroom, the corridor, the room, and being sat at the table.
 * Click or press any key to skip.
 */
public class IntroScene extends JPanel {
    private final ScenePlayer player;
    private Runnable onDone;

    public IntroScene() {
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

    /** Plays the intro, then runs done (also when skipped). */
    public void start(Runnable done) {
        onDone = done;
        requestFocusInWindow();
        player.playClip("intro", "table", this::finish);
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
        g.setFont(new Font("Serif", Font.ITALIC, 13));
        g.setColor(new Color(200, 190, 180, 120));
        String hint = "click to skip";
        g.drawString(hint, getWidth() - g.getFontMetrics().stringWidth(hint) - 16, getHeight() - 16);
    }
}
