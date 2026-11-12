import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * A look at the pixel style on its own.
 *   java PixelPreview                 opens a window: click to switch screen, any key for the next character
 *   java PixelPreview out.png S N     saves screen S (1 = facing him, 2 = the board, 3 = a dealer) with character N
 */
public class PixelPreview extends JPanel {
    private int screen = 0, who = 0;
    private final long start = System.currentTimeMillis();
    private Px.Talk talk = new Px.Talk(LINES[0], 180);

    static final String[] LINES = {
        "* The masked man drums his fingers on the table. \"Sit. Your king looks lonely.\"",
        "* The Doctor adjusts his beak. \"I have cured worse openings than yours.\"",
        "* The Goalie leans in. You can hear him breathing behind the holes.",
        "* The sack rustles. Something under it is smiling.",
        "* The Welder's glass flickers green. \"Let's burn through this.\"",
    };

    static final String[][] BOARD = {
        {"bR", "bN", "bB", "bQ", "bK", "bB", "bN", "bR"}, {"bP", "bP", "bP", "bP", "", "bP", "bP", "bP"},
        {"", "", "", "", "", "", "", ""}, {"", "", "", "", "bP", "", "", ""}, {"", "", "", "", "wP", "", "", ""},
        {"", "", "", "", "", "wN", "", ""}, {"wP", "wP", "wP", "wP", "", "wP", "wP", "wP"},
        {"wR", "wN", "wB", "wQ", "wK", "wB", "", "wR"},
    };

    static PixelArt.Person person(int who) {
        return who < 5 ? PixelArt.OPPONENTS[who] : new PixelArt.Person[]{PixelArt.CARD_DEALER, PixelArt.PIECE_DEALER, PixelArt.RESELLER}[who - 5];
    }

    /** The lamp above: a bulb and a faint dithered cone. */
    static void lamp(Graphics2D g, int cx, double t) {
        Px.rect(g, cx, 0, 1, 6, Px.DIM);
        Px.rect(g, cx - 5, 6, 11, 3, Px.DARK);
        boolean on = Math.sin(t * 9) > -0.95;
        Px.rect(g, cx - 1, 9, 3, 2, on ? Px.YELLOW : Px.GOLD);
        g.setColor(new Color(60, 50, 30));
        for (int y = 12; y < 140; y += 2) {
            int half = 6 + (y - 12) * 2 / 3;
            for (int x = cx - half + (y % 4 == 0 ? 0 : 1); x <= cx + half; x += 4) g.fillRect(x, y, 1, 1);
        }
    }

    static void table(Graphics2D g, int y) {
        Px.rect(g, 40, y, 240, 8, Px.BROWN);
        Px.rect(g, 40, y + 6, 240, 2, Px.DBROWN);
        Px.rect(g, 40, y - 1, 240, 1, Px.BLACK);
    }

    static void buttons(Graphics2D g, int selected, double t) {
        String[] labels = {"PLAY", "CARDS", "TALK", "YIELD"};
        for (int i = 0; i < 4; i++) {
            int x = 13 + i * 76, y = 212;
            Color c = i == selected ? Px.YELLOW : Px.ORANGE;
            Px.rect(g, x, y, 66, 22, c);
            Px.rect(g, x + 2, y + 2, 62, 18, Px.BLACK);
            int tx = x + 22;
            if (i == selected) PixelArt.piece(g, "wP", x + 11, y + 17 - (int) (t * 3) % 2);   // the cursor: a little pawn
            else tx = x + (66 - Px.textWidth(labels[i], 1)) / 2;
            Px.text(g, labels[i], tx, y + 8, c, 1);
        }
    }

    public static BufferedImage render(int screen, int who, double t, Px.Talk talk) {
        BufferedImage img = Px.canvas();
        Graphics2D g = Px.pen(img);
        PixelArt.Person p = person(who);
        if (screen == 0) {                                          // facing him: big, with his line and the four choices
            lamp(g, 160, t);
            g.drawImage(PixelArt.person(p, t, null, 0), 80, 0, 160, 144, null);
            table(g, 138);
            Px.box(g, 16, 150, 288, 52);
            Px.wrap(g, talk.text, 26, 159, 270, Px.WHITE, talk.shown());
            Px.text(g, "LV 3   GOLD 64   ARMY 4/6", 18, 203, Px.WHITE, 1);
            buttons(g, 0, t);
        } else if (screen == 1) {                                   // the board: him smaller above it
            lamp(g, 160, t);
            g.drawImage(PixelArt.person(p, t, "think", (t % 4) / 4), 120, 2, 80, 72, null);
            table(g, 72);
            Px.box(g, 94, 80, 132, 120);
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    boolean dark = (r + c) % 2 == 1;
                    Px.rect(g, 100 + c * 15, 86 + r * 13, 15, 13, dark ? Px.DBROWN : Px.BONE2);
                }
            }
            Px.rect(g, 100 + 4 * 15, 86 + 6 * 13, 15, 13, Px.GOLD);           // a selected square
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (!BOARD[r][c].isEmpty()) PixelArt.piece(g, BOARD[r][c], 100 + c * 15 + 8, 86 + r * 13 + 12);
                }
            }
            Px.box(g, 16, 204, 288, 32);
            Px.text(g, "* He is thinking...", 26, 216, Px.WHITE, 1);
            Px.text(g, "HAND", 238, 84, Px.ORANGE, 1);
            Px.text(g, "Pawn Surge", 238, 96, Px.WHITE, 1);
            Px.text(g, "Heavy Armor", 238, 108, Px.WHITE, 1);
            Px.text(g, "LV 3", 20, 84, Px.WHITE, 1);
            Px.text(g, "GOLD 64", 20, 96, Px.YELLOW, 1);
        } else {                                                    // a dealer behind his counter
            g.drawImage(PixelArt.person(p, t, null, 0), 80, 10, 160, 144, null);
            Px.rect(g, 40, 148, 240, 10, Px.DBROWN);
            Px.rect(g, 40, 147, 240, 1, Px.BLACK);
            Px.box(g, 16, 160, 288, 46);
            Px.wrap(g, "* " + p.name + " spreads his wares. \"Everything has a price. Even you.\"", 26, 169, 270, Px.WHITE, talk.shown());
            Px.text(g, "<", 6, 108, Px.WHITE, 2);
            Px.text(g, ">", 304, 108, Px.WHITE, 2);
            buttons(g, 1, t);
        }
        g.dispose();
        return img;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        double t = (System.currentTimeMillis() - start) / 1000.0;
        Px.present((Graphics2D) g0, render(screen, who, t, talk), getWidth(), getHeight());
    }

    public static void main(String[] args) throws Exception {
        if (args.length >= 3) {
            int screen = Integer.parseInt(args[1]) - 1, who = Integer.parseInt(args[2]) - 1;
            Px.Talk done = new Px.Talk(LINES[Math.min(who, 4)], 0) {
                @Override public int shown() { return 999; }
            };
            BufferedImage small = render(screen, who, 0.4, done);
            BufferedImage big = new BufferedImage(Px.W * 3, Px.H * 3, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = big.createGraphics();
            Px.present(g, small, big.getWidth(), big.getHeight());
            g.dispose();
            ImageIO.write(big, "png", new File(args[0]));
            System.out.println("saved " + args[0]);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Ashes - pixel style");
            PixelPreview p = new PixelPreview();
            p.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) { p.screen = (p.screen + 1) % 3; }
            });
            f.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override public void keyPressed(java.awt.event.KeyEvent e) {
                    p.who = (p.who + 1) % 8;
                    p.talk = new Px.Talk(LINES[Math.min(p.who, 4)], person(p.who).voice);
                }
            });
            f.add(p);
            f.setSize(Px.W * 3 + 16, Px.H * 3 + 39);
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);
            new Timer(33, e -> p.repaint()).start();
        });
    }
}
