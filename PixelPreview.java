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
    private Px.Talk talk = new Px.Talk(LINES[0][0] + " " + LINES[0][1], 180);

    /** What you see (narration, dim) and what he says (speech, bright), per opponent. */
    static final String[][] LINES = {
        {"He drums his fingers on the table.", "Sit. Your king looks lonely."},
        {"The beak tilts toward your pieces.", "I have cured worse openings than yours."},
        {"He leans in. You can hear him breathing behind the holes.", "Your move. Don't keep me waiting."},
        {"The sack rustles. Something under it is smiling.", "Play. I'll be gentle. At first."},
        {"Green light flickers behind the glass.", "Let's burn through this."},
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

    /** The lamp above: flex, shade and bulb. */
    static void lamp(Graphics2D g, int cx, double t) {
        Px.rect(g, cx, 0, 1, 6, Px.DIM);
        Px.rect(g, cx - 6, 6, 13, 1, Px.BLACK);
        Px.rect(g, cx - 5, 6, 11, 3, new Color(60, 66, 56));
        Px.rect(g, cx - 7, 9, 15, 1, Px.BLACK);
        boolean on = Math.sin(t * 9) > -0.95;
        Px.rect(g, cx - 1, 10, 3, 2, on ? Px.YELLOW : Px.GOLD);
    }

    static void table(Graphics2D g, int y) {
        Px.rect(g, 30, y - 1, 260, 1, Px.BLACK);
        Px.rect(g, 30, y, 260, 7, Px.WOOD_LIGHT);
        Px.rect(g, 30, y, 260, 1, new Color(140, 90, 60));
        Px.rect(g, 30, y + 5, 260, 2, Px.WOOD_DARK);
        for (int x = 36; x < 290; x += 23) Px.rect(g, x, y + 2, 6, 1, Px.WOOD);
    }

    /** Coin and gold, skull and level, pawn and army, in the top-left corner. */
    static void status(Graphics2D g) {
        Px.coin(g, 6, 5);
        Px.text(g, "64", 17, 6, Px.YELLOW, 1);
        Px.skull(g, 36, 5);
        Px.text(g, "3", 47, 6, Px.CREAM, 1);
        PixelArt.piece(g, "wP", 62, 13);
        Px.text(g, "4/6", 70, 6, Px.CREAM, 1);
    }

    /** A framed text box with a name plate: narration in ash grey, then his words in cream. */
    static void speech(Graphics2D g, String name, String narration, String said, int y, int h, int shown) {
        Px.frame(g, 14, y, 292, h);
        Px.plate(g, name, 24, y - 6);
        int lines = Px.wrap(g, narration, 26, y + 11, 268, Px.ASH, shown);
        Px.wrap(g, "\"" + said + "\"", 26, y + 13 + lines * 10, 268, Px.CREAM, shown - narration.length() - 1);
    }

    public static BufferedImage render(int screen, int who, double t, Px.Talk talk) {
        BufferedImage img = Px.canvas();
        Graphics2D g = Px.pen(img);
        PixelArt.Person p = person(who);
        double flicker = 0.85 + 0.15 * Math.sin(t * 7) * Math.sin(t * 2.3);
        String[] line = LINES[Math.min(who, 4)];
        if (screen == 0) {                                          // facing him: big, with his words and the choices
            Px.cellar(g, 160, 70, 190, flicker);
            lamp(g, 160, t);
            g.drawImage(PixelArt.person(p, t, null, 0), 80, 2, 160, 144, null);
            table(g, 140);
            speech(g, p.name, line[0], line[1], 156, 50, talk.shown());
            status(g);
            Px.cards(g, new String[]{"PLAY", "CARDS", "TALK", "LEAVE"}, 0, t);
        } else if (screen == 1) {                                   // the board on the table, him above it
            Px.cellar(g, 160, 40, 170, flicker);
            lamp(g, 160, t);
            g.drawImage(PixelArt.person(p, t, "think", (t % 4) / 4), 120, 6, 80, 72, null);
            table(g, 76);
            Px.rect(g, 94, 82, 132, 112, Px.BLACK);                     // the board in its wooden frame
            Px.rect(g, 95, 83, 130, 110, Px.WOOD);
            Px.rect(g, 95, 83, 130, 1, Px.WOOD_LIGHT);
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    boolean dark = (r + c) % 2 == 1;
                    Px.rect(g, 100 + c * 15, 86 + r * 13, 15, 13, dark ? new Color(92, 56, 40) : new Color(206, 184, 144));
                }
            }
            Px.rect(g, 100 + 4 * 15, 86 + 6 * 13, 15, 13, new Color(236, 160, 70));   // a chosen square
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (!BOARD[r][c].isEmpty()) PixelArt.piece(g, BOARD[r][c], 100 + c * 15 + 8, 86 + r * 13 + 12);
                }
            }
            Px.frame(g, 234, 82, 80, 70);                               // your hand
            Px.text(g, "HAND", 242, 90, Px.EMBER, 1);
            Px.text(g, "Pawn", 242, 104, Px.CREAM, 1);
            Px.text(g, " Surge", 242, 114, Px.CREAM, 1);
            Px.text(g, "Heavy", 242, 128, Px.CREAM, 1);
            Px.text(g, " Armor", 242, 138, Px.CREAM, 1);
            status(g);
            Px.frame(g, 14, 202, 292, 34);
            Px.plate(g, p.name, 24, 196);
            Px.text(g, "He is thinking...", 26, 214, Px.ASH, 1);
        } else {                                                    // a dealer behind his counter, by candlelight
            Px.cellar(g, 160, 90, 170, flicker);
            g.drawImage(PixelArt.person(p, t, null, 0), 80, 8, 160, 144, null);
            Px.rect(g, 30, 146, 260, 1, Px.BLACK);
            Px.rect(g, 30, 147, 260, 10, Px.WOOD_DARK);
            Px.rect(g, 30, 147, 260, 1, Px.WOOD);
            Px.flame(g, 52, 132, t);
            Px.flame(g, 262, 132, t + 0.3);
            speech(g, p.name, "He spreads his wares across the counter.", "Everything has a price. Even you.", 162, 46, talk.shown());
            Px.text(g, "<", 6, 100, Px.CREAM, 2);
            Px.text(g, ">", 304, 100, Px.CREAM, 2);
            status(g);
            Px.cards(g, new String[]{"BUY", "SELL", "TALK", "BACK"}, 0, t);
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
            Px.Talk done = new Px.Talk(LINES[Math.min(who, 4)][1], 0) {
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
                    String[] l = LINES[Math.min(p.who, 4)];
                    p.talk = new Px.Talk(l[0] + " " + l[1], person(p.who).voice);
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
