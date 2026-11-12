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

    /** The lamp above: a cord, a shaded tin cone and a bare bulb. */
    static void lamp(Graphics2D g, int cx, double t) {
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB);
        Shade.rect(img, 19, 0, 2, 12, new Color(30, 24, 30));
        java.awt.geom.Path2D cone = new java.awt.geom.Path2D.Double();
        cone.moveTo(14, 10); cone.lineTo(26, 10); cone.lineTo(36, 22); cone.lineTo(4, 22); cone.closePath();
        Shade.part(img, cone, Shade.ramp(new Color(70, 80, 64), 5), 5);
        boolean on = Math.sin(t * 9) > -0.95;
        Shade.part(img, new java.awt.geom.Ellipse2D.Double(15, 19, 10, 8), Shade.ramp(on ? new Color(255, 226, 150) : new Color(200, 150, 80), 3), 0);
        Shade.rect(img, 18, 21, 3, 2, on ? Color.WHITE : Px.CREAM);
        g.drawImage(img, cx - 20, 0, null);
    }

    private static final java.util.Map<String, BufferedImage> TABLES = new java.util.HashMap<>();

    /** A plank table edge across the scene, its top lit by the lamp, its front falling into dark. */
    static void table(Graphics2D g, int x, int y, int w, int h) {
        BufferedImage img = TABLES.computeIfAbsent(x + "," + w + "," + h, k -> {
            BufferedImage t = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Color[] top = Shade.ramp(new Color(122, 76, 48), 5), front = Shade.ramp(new Color(66, 40, 30), 4);
            int th = h * 2 / 3;
            Shade.part(t, new Rectangle(0, 0, w, th), top, 3);
            Shade.part(t, new Rectangle(0, th, w, h - th), front, 2);
            Shade.rect(t, 0, th, w, 1, new Color(30, 18, 20));
            for (int px = 0; px < w; px++) {                                  // grain and plank joints
                if (px % 61 == 0) Shade.rect(t, px, 0, 1, th, top[0]);
                for (int py = 1; py < th; py++) {
                    int n = (px * 31 + py * 977) % 53;
                    if (n == 0 || (py == (px / 9 * 3) % th && px % 5 != 0)) Shade.px(t, px, py, top[1]);
                }
            }
            return t;
        });
        g.drawImage(img, x, y, null);
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
        Px.frame(g, 20, y, 440, h);
        Px.plate(g, name, 32, y - 6);
        int lines = Px.wrap(g, narration, 34, y + 13, 410, Px.ASH, shown);
        Px.wrap(g, "\"" + said + "\"", 34, y + 17 + lines * 10, 410, Px.CREAM, shown - narration.length() - 1);
    }

    public static BufferedImage render(int screen, int who, double t, Px.Talk talk) {
        BufferedImage img = Px.canvas();
        Graphics2D g = Px.pen(img);
        PixelArt.Person p = person(who);
        double flicker = 0.85 + 0.15 * Math.sin(t * 7) * Math.sin(t * 2.3);
        String[] line = LINES[Math.min(who, 4)];
        int sx = (Px.W - PixelArt.SPRITE_W) / 2;
        if (screen == 0) {                                          // facing him, with his words and the choices
            Px.cellar(g, 240, 90, 300, flicker);
            g.drawImage(PixelArt.person(p, t, null, 0), sx, 50, null);
            lamp(g, 240, t);
            table(g, 30, 186, 420, 18);
            speech(g, p.name, line[0], line[1], 222, 80, talk.shown());
            status(g);
            Px.cards(g, new String[]{"PLAY", "CARDS", "TALK", "LEAVE"}, 0, t);
        } else if (screen == 1) {                                   // the board on the table, him above it
            Px.cellar(g, 240, 60, 290, flicker);
            g.drawImage(PixelArt.person(p, t, "think", (t % 4) / 4), sx, 8, null);
            lamp(g, 240, t);
            table(g, 20, 142, 440, 16);
            int bx = 152, by = 156, sw = 22, sh = 18;
            Px.rect(g, bx - 6, by - 6, sw * 8 + 12, sh * 8 + 12, Px.BLACK);          // the board in its frame
            Px.rect(g, bx - 5, by - 5, sw * 8 + 10, sh * 8 + 10, Px.WOOD);
            Px.rect(g, bx - 5, by - 5, sw * 8 + 10, 1, Px.WOOD_LIGHT);
            Color[] light = Shade.ramp(new Color(206, 178, 136), 5), dark = Shade.ramp(new Color(98, 58, 42), 5);
            int[][] bayer = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    Color[] ramp = (r + c) % 2 == 1 ? dark : light;
                    double d = Math.hypot(c - 3.5, r - 1.0) / 8.0;              // lamplight falls off down the board
                    for (int yy = 0; yy < sh; yy++) {
                        for (int xx = 0; xx < sw; xx++) {
                            int X = bx + c * sw + xx, Y = by + r * sh + yy;
                            double v = 0.9 - d * 0.8 + (yy == 0 ? 0.1 : 0) + ((X * 7 + Y * 3) % 11 == 0 ? -0.08 : 0);
                            int k = (int) Math.max(0, Math.min(4, Math.round(v * 4 + bayer[Y & 3][X & 3] / 16.0 - 0.47)));
                            Px.dot(g, X, Y, ramp[k]);
                        }
                    }
                }
            }
            Px.rect(g, bx + 4 * sw, by + 6 * sh, sw, 1, Px.EMBER);              // a chosen square, edged in ember
            Px.rect(g, bx + 4 * sw, by + 7 * sh - 1, sw, 1, Px.EMBER);
            Px.rect(g, bx + 4 * sw, by + 6 * sh, 1, sh, Px.EMBER);
            Px.rect(g, bx + 5 * sw - 1, by + 6 * sh, 1, sh, Px.EMBER);
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (!BOARD[r][c].isEmpty()) PixelArt.piece(g, BOARD[r][c], bx + c * sw + sw / 2, by + r * sh + sh - 3);
                }
            }
            Px.frame(g, 350, 160, 116, 84);                                    // your hand
            Px.text(g, "HAND", 362, 170, Px.EMBER, 1);
            Px.text(g, "Pawn Surge", 362, 186, Px.CREAM, 1);
            Px.text(g, "Heavy Armor", 362, 200, Px.CREAM, 1);
            Px.text(g, "Lights Out", 362, 214, Px.ASH, 1);
            status(g);
            Px.frame(g, 20, 312, 440, 42);
            Px.plate(g, p.name, 32, 306);
            Px.text(g, "He is thinking...", 34, 328, Px.ASH, 1);
        } else {                                                    // a dealer behind his counter, by candlelight
            Px.cellar(g, 240, 120, 270, flicker);
            g.drawImage(PixelArt.person(p, t, null, 0), sx, 52, null);
            table(g, 40, 188, 400, 20);
            Px.flame(g, 80, 176, t);
            Px.flame(g, 392, 176, t + 0.3);
            speech(g, p.name, "He spreads his wares across the counter.", "Everything has a price. Even you.", 224, 78, talk.shown());
            Px.text(g, "<", 10, 140, Px.CREAM, 2);
            Px.text(g, ">", 460, 140, Px.CREAM, 2);
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
            BufferedImage big = new BufferedImage(Px.W * 2, Px.H * 2, BufferedImage.TYPE_INT_RGB);
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
            f.setSize(Px.W * 2 + 16, Px.H * 2 + 39);
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);
            new Timer(33, e -> p.repaint()).start();
        });
    }
}
