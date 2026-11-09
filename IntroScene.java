import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * The opening cutscene: the bathroom, the corridor, the room, and being sat at the table.
 * It fades in from black (the strip light flickers on in the render) and the title appears
 * over the cracked mirror. Click or press any key to skip.
 */
public class IntroScene extends JPanel {
    private final ScenePlayer player;
    /** Intro frames (24 per second): the fade from black, and when the title shows. */
    private static final int FADE_FRAMES = 20;
    private static final int TITLE_IN = 14, TITLE_FULL = 26, TITLE_OUT = 50, TITLE_GONE = 64;
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
        int f = player.getClipFrame();
        if (f >= 0) {
            drawTitle(g, f);
            if (f < FADE_FRAMES) {
                g.setColor(new Color(0, 0, 0, 255 - 255 * f / FADE_FRAMES));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("Serif", Font.ITALIC, 13));
        g.setColor(new Color(200, 190, 180, 120));
        String hint = "click to skip";
        g.drawString(hint, getWidth() - g.getFontMetrics().stringWidth(hint) - 16, getHeight() - 16);
    }

    /** "ASHES / of the board", fading in and out over the mirror shot. */
    private void drawTitle(Graphics2D g, int f) {
        float a;
        if (f < TITLE_IN || f >= TITLE_GONE) return;
        else if (f < TITLE_FULL) a = (f - TITLE_IN) / (float) (TITLE_FULL - TITLE_IN);
        else if (f < TITLE_OUT) a = 1f;
        else a = 1f - (f - TITLE_OUT) / (float) (TITLE_GONE - TITLE_OUT);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int cx = getWidth() / 2, cy = getHeight() / 2;
        Font big = new Font("Serif", Font.BOLD, 72);
        String title = "A S H E S";
        g.setFont(big);
        int tw = g.getFontMetrics().stringWidth(title);
        g.setColor(new Color(0, 0, 0, (int) (150 * a)));          // soft shadow so it reads on the tiles
        g.drawString(title, cx - tw / 2 + 3, cy + 3);
        g.setColor(new Color(225, 215, 200, (int) (235 * a)));
        g.drawString(title, cx - tw / 2, cy);
        g.setFont(new Font("Serif", Font.ITALIC, 20));
        String sub = "of the board";
        int sw = g.getFontMetrics().stringWidth(sub);
        g.setColor(new Color(180, 40, 50, (int) (220 * a)));
        g.drawString(sub, cx - sw / 2, cy + 36);
    }
}
