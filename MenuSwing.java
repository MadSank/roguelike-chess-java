
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MenuSwing extends JPanel {
    private static final int PANEL_WIDTH = 900;
    private static final int PANEL_HEIGHT = 700;
    private RoguelikeChessAppSwing app;
    private String currentMenu = "MAIN";

    private static final Color BG = new Color(5, 3, 8);
    private static final Color CRIMSON = new Color(180, 30, 40);
    private static final Color CRIMSON_GLOW = new Color(230, 57, 70, 60);
    private static final Color BTN_BG = new Color(30, 18, 22);
    private static final Color BTN_BORDER = new Color(100, 40, 50);
    private static final Color BTN_HOVER = new Color(60, 30, 38);
    private static final Color TEXT_DIM = new Color(120, 100, 115);

    // Ember particles
    private final List<float[]> embers = new ArrayList<>();
    private final Random rng = new Random();
    private javax.swing.Timer animTimer;
    private int hoverButton = -1;

    private final String[] mainButtons = {"New Game", "High Scores", "Settings", "Credits", "Exit"};
    private static final int BTN_W = 280, BTN_H = 50, BTN_GAP = 14;

    public MenuSwing(RoguelikeChessAppSwing app) {
        this.app = app;
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setBackground(BG);
        initEmbers();
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { handleClick(e.getX(), e.getY()); }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int old = hoverButton;
                hoverButton = getButtonAt(e.getX(), e.getY());
                if (old != hoverButton) repaint();
            }
        });
        animTimer = new javax.swing.Timer(40, e -> { updateEmbers(); repaint(); });
        animTimer.start();
    }

    private void initEmbers() {
        for (int i = 0; i < 40; i++) {
            embers.add(new float[]{
                rng.nextFloat() * PANEL_WIDTH,
                rng.nextFloat() * PANEL_HEIGHT,
                (rng.nextFloat() - 0.5f) * 0.8f,
                -0.3f - rng.nextFloat() * 1.2f,
                0.5f + rng.nextFloat() * 0.5f,
                rng.nextFloat()
            }); // x, y, vx, vy, size, brightness
        }
    }

    private void updateEmbers() {
        for (float[] e : embers) {
            e[0] += e[2]; e[1] += e[3];
            e[5] += (rng.nextFloat() - 0.5f) * 0.05f;
            e[5] = Math.max(0.2f, Math.min(1f, e[5]));
            if (e[1] < -10) { e[1] = PANEL_HEIGHT + 10; e[0] = rng.nextFloat() * PANEL_WIDTH; }
            if (e[0] < -10) e[0] = PANEL_WIDTH + 10;
            if (e[0] > PANEL_WIDTH + 10) e[0] = -10;
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        int w = getWidth(), h = getHeight();

        g.setColor(BG); g.fillRect(0, 0, w, h);

        // Draw embers
        for (float[] e : embers) {
            int alpha = (int)(e[5] * 200);
            int r = 200 + (int)(e[5] * 55), gr = 80 + (int)(e[5] * 40), b = 20;
            g.setColor(new Color(Math.min(r, 255), Math.min(gr, 255), b, Math.min(alpha, 255)));
            int sz = (int)(e[4] * 3);
            g.fillOval((int)e[0], (int)e[1], sz, sz);
            g.setColor(new Color(Math.min(r, 255), Math.min(gr, 255), b, Math.min(alpha/3, 80)));
            g.fillOval((int)e[0] - 2, (int)e[1] - 2, sz + 4, sz + 4);
        }

        // Vignette
        RadialGradientPaint vig = new RadialGradientPaint(
            new Point2D.Float(w/2f, h/2f), w * 0.6f,
            new float[]{0f, 0.5f, 1f},
            new Color[]{new Color(0,0,0,0), new Color(0,0,0,80), new Color(0,0,0,220)});
        g.setPaint(vig); g.fillRect(0, 0, w, h);

        if (currentMenu.equals("MAIN")) paintMainMenu(g, w, h);
        else if (currentMenu.equals("SETTINGS")) paintSettings(g, w, h);
        else if (currentMenu.equals("CREDITS")) paintCredits(g, w, h);
    }

    private void paintMainMenu(Graphics2D g, int w, int h) {
        // Title with glow
        g.setColor(CRIMSON_GLOW);
        g.setFont(new Font("Serif", Font.BOLD, 56));
        FontMetrics fm = g.getFontMetrics();
        String title = "ASHES OF THE BOARD";
        int tx = (w - fm.stringWidth(title)) / 2;
        // Glow layers
        g.drawString(title, tx + 2, 142);
        g.setColor(CRIMSON);
        g.drawString(title, tx, 140);

        g.setColor(new Color(200, 120, 80));
        g.setFont(new Font("Serif", Font.ITALIC, 22));
        fm = g.getFontMetrics();
        String sub = "Roguelike Chess";
        g.drawString(sub, (w - fm.stringWidth(sub))/2, 180);

        // Buttons
        int startY = 240;
        for (int i = 0; i < mainButtons.length; i++) {
            int bx = (w - BTN_W) / 2, by = startY + i * (BTN_H + BTN_GAP);
            boolean hover = (hoverButton == i);

            // Button background
            GradientPaint grad = new GradientPaint(bx, by, hover ? BTN_HOVER : BTN_BG,
                bx, by + BTN_H, hover ? new Color(45, 22, 30) : new Color(20, 12, 16));
            g.setPaint(grad); g.fillRoundRect(bx, by, BTN_W, BTN_H, 8, 8);

            g.setColor(hover ? CRIMSON : BTN_BORDER);
            g.setStroke(new BasicStroke(hover ? 2 : 1));
            g.drawRoundRect(bx, by, BTN_W, BTN_H, 8, 8);
            g.setStroke(new BasicStroke(1));

            g.setColor(hover ? Color.WHITE : new Color(200, 190, 205));
            g.setFont(new Font("Serif", Font.BOLD, 20));
            fm = g.getFontMetrics();
            g.drawString(mainButtons[i], bx + (BTN_W - fm.stringWidth(mainButtons[i]))/2, by + 33);
        }
    }

    private void paintSettings(Graphics2D g, int w, int h) {
        g.setColor(CRIMSON); g.setFont(new Font("Serif", Font.BOLD, 36));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("Settings", (w - fm.stringWidth("Settings"))/2, 100);

        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        String snd = "Sound: " + (AudioPlayer.isSoundEnabled() ? "ON" : "OFF");
        g.drawString(snd, (w - g.getFontMetrics().stringWidth(snd))/2, 200);

        drawBackButton(g, w, h, "[Toggle Sound]", 250);
        drawBackButton(g, w, h, "Back to Main Menu", h - 100);
    }

    private void paintCredits(Graphics2D g, int w, int h) {
        g.setColor(CRIMSON); g.setFont(new Font("Serif", Font.BOLD, 36));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("Credits", (w - fm.stringWidth("Credits"))/2, 100);

        g.setColor(TEXT_DIM); g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        String[] lines = {"Game Design & Programming:", "Maddox and Sambar Boy", "", "Card System Architecture:", "Ashes Engine v2.0"};
        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], (w - g.getFontMetrics().stringWidth(lines[i]))/2, 180 + i*30);
        }
        drawBackButton(g, w, h, "Back", h - 100);
    }

    private void drawBackButton(Graphics2D g, int w, int h, String text, int y) {
        int bw = 250, bx = (w - bw)/2;
        g.setColor(BTN_BG); g.fillRoundRect(bx, y, bw, 44, 8, 8);
        g.setColor(BTN_BORDER); g.drawRoundRect(bx, y, bw, 44, 8, 8);
        g.setColor(Color.WHITE); g.setFont(new Font("Serif", Font.BOLD, 16));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, bx + (bw - fm.stringWidth(text))/2, y + 28);
    }

    private int getButtonAt(int mx, int my) {
        if (!currentMenu.equals("MAIN")) return -1;
        int startY = 240, w = getWidth();
        for (int i = 0; i < mainButtons.length; i++) {
            int bx = (w - BTN_W)/2, by = startY + i*(BTN_H + BTN_GAP);
            if (mx >= bx && mx <= bx+BTN_W && my >= by && my <= by+BTN_H) return i;
        }
        return -1;
    }

    private void handleClick(int mx, int my) {
        int w = getWidth(), h = getHeight();
        if (currentMenu.equals("MAIN")) {
            int btn = getButtonAt(mx, my);
            switch (btn) {
                case 0: app.startMatch(); break;
                case 1: JOptionPane.showMessageDialog(this, "High Scores coming soon!", "Leaderboard", JOptionPane.INFORMATION_MESSAGE); break;
                case 2: currentMenu = "SETTINGS"; repaint(); break;
                case 3: currentMenu = "CREDITS"; repaint(); break;
                case 4: if (JOptionPane.showConfirmDialog(this, "Exit?", "Exit", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) System.exit(0); break;
            }
        } else if (currentMenu.equals("SETTINGS")) {
            int bx = (w-250)/2;
            if (mx >= bx && mx <= bx+250 && my >= 250 && my <= 294) {
                AudioPlayer.setSoundEnabled(!AudioPlayer.isSoundEnabled()); repaint();
            }
            if (my >= h-100 && my <= h-56) { currentMenu = "MAIN"; repaint(); }
        } else if (currentMenu.equals("CREDITS")) {
            if (my >= h-100 && my <= h-56) { currentMenu = "MAIN"; repaint(); }
        }
    }

    public void showMenu() { currentMenu = "MAIN"; repaint(); }
}
