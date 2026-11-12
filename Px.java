import javax.sound.sampled.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * The pixel-art kit: a small canvas (320 x 240) drawn with hard pixels and blown up to the window,
 * a hand-made 5 x 7 pixel font, white-bordered text boxes, typewriter text with little voice blips.
 * Pure Java (Java2D and Java Sound), no image or sound files.
 */
public final class Px {
    private Px() {}

    public static final int W = 320, H = 240;

    // ─── Palette ────────────────────────────────────────────────
    public static final Color BLACK = new Color(0, 0, 0);
    public static final Color WHITE = new Color(255, 255, 255);
    public static final Color GREY = new Color(128, 128, 128);
    public static final Color DARK = new Color(48, 44, 52);
    public static final Color DIM = new Color(86, 80, 90);
    public static final Color BONE = new Color(232, 222, 196);
    public static final Color BONE2 = new Color(176, 162, 134);
    public static final Color RED = new Color(200, 30, 40);
    public static final Color DRED = new Color(110, 18, 24);
    public static final Color ORANGE = new Color(255, 128, 0);
    public static final Color YELLOW = new Color(255, 230, 0);
    public static final Color GOLD = new Color(212, 168, 60);
    public static final Color BROWN = new Color(110, 66, 40);
    public static final Color DBROWN = new Color(62, 36, 24);
    public static final Color LEATHER = new Color(88, 60, 42);
    public static final Color BURLAP = new Color(168, 132, 86);
    public static final Color BURLAP2 = new Color(122, 92, 58);
    public static final Color GREEN = new Color(60, 200, 110);
    public static final Color BLUE = new Color(70, 110, 200);
    public static final Color PURPLE = new Color(110, 60, 140);
    public static final Color STEEL = new Color(150, 150, 158);

    /** A fresh pixel canvas. */
    public static BufferedImage canvas() {
        return new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    }

    /** Graphics for drawing hard pixels (no smoothing). */
    public static Graphics2D pen(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /** Blows the canvas up to fill (w, h) with whole-number pixels, centred, black around it. Returns the scale. */
    public static int present(Graphics2D g, BufferedImage canvas, int w, int h) {
        int scale = Math.max(1, Math.min(w / W, h / H));
        int dw = W * scale, dh = H * scale;
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(canvas, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        return scale;
    }

    /** Turns a window point into a canvas pixel (or null outside it). */
    public static Point toCanvas(int x, int y, int w, int h) {
        int scale = Math.max(1, Math.min(w / W, h / H));
        int ox = (w - W * scale) / 2, oy = (h - H * scale) / 2;
        int cx = (x - ox) / scale, cy = (y - oy) / scale;
        return cx < 0 || cy < 0 || cx >= W || cy >= H ? null : new Point(cx, cy);
    }

    public static void rect(Graphics2D g, int x, int y, int w, int h, Color c) {
        g.setColor(c);
        g.fillRect(x, y, w, h);
    }

    public static void dot(Graphics2D g, int x, int y, Color c) {
        g.setColor(c);
        g.fillRect(x, y, 1, 1);
    }

    /** A white-bordered black box (3 pixels of white), the frame of every text and menu. */
    public static void box(Graphics2D g, int x, int y, int w, int h) {
        rect(g, x, y, w, h, WHITE);
        rect(g, x + 3, y + 3, w - 6, h - 6, BLACK);
    }

    // ─── The font: 5 x 7 glyphs (g, j, p, q, y and the comma hang one row lower), from '#' and '.' ───
    private static final Map<Character, String[]> GLYPHS = new HashMap<>();

    private static void glyph(char c, String rows) {
        GLYPHS.put(c, rows.split(","));
    }

    static {
        glyph('A', ".###.,#...#,#...#,#####,#...#,#...#,#...#");
        glyph('B', "####.,#...#,#...#,####.,#...#,#...#,####.");
        glyph('C', ".###.,#...#,#....,#....,#....,#...#,.###.");
        glyph('D', "####.,#...#,#...#,#...#,#...#,#...#,####.");
        glyph('E', "#####,#....,#....,####.,#....,#....,#####");
        glyph('F', "#####,#....,#....,####.,#....,#....,#....");
        glyph('G', ".###.,#...#,#....,#.###,#...#,#...#,.####");
        glyph('H', "#...#,#...#,#...#,#####,#...#,#...#,#...#");
        glyph('I', ".###.,..#..,..#..,..#..,..#..,..#..,.###.");
        glyph('J', "..###,...#.,...#.,...#.,#..#.,#..#.,.##..");
        glyph('K', "#...#,#..#.,#.#..,##...,#.#..,#..#.,#...#");
        glyph('L', "#....,#....,#....,#....,#....,#....,#####");
        glyph('M', "#...#,##.##,#.#.#,#.#.#,#...#,#...#,#...#");
        glyph('N', "#...#,##..#,#.#.#,#..##,#...#,#...#,#...#");
        glyph('O', ".###.,#...#,#...#,#...#,#...#,#...#,.###.");
        glyph('P', "####.,#...#,#...#,####.,#....,#....,#....");
        glyph('Q', ".###.,#...#,#...#,#...#,#.#.#,#..#.,.##.#");
        glyph('R', "####.,#...#,#...#,####.,#.#..,#..#.,#...#");
        glyph('S', ".####,#....,#....,.###.,....#,....#,####.");
        glyph('T', "#####,..#..,..#..,..#..,..#..,..#..,..#..");
        glyph('U', "#...#,#...#,#...#,#...#,#...#,#...#,.###.");
        glyph('V', "#...#,#...#,#...#,#...#,#...#,.#.#.,..#..");
        glyph('W', "#...#,#...#,#...#,#.#.#,#.#.#,#.#.#,.#.#.");
        glyph('X', "#...#,#...#,.#.#.,..#..,.#.#.,#...#,#...#");
        glyph('Y', "#...#,#...#,.#.#.,..#..,..#..,..#..,..#..");
        glyph('Z', "#####,....#,...#.,..#..,.#...,#....,#####");
        glyph('a', ".....,.....,.###.,....#,.####,#...#,.####");
        glyph('b', "#....,#....,####.,#...#,#...#,#...#,####.");
        glyph('c', ".....,.....,.###.,#....,#....,#....,.###.");
        glyph('d', "....#,....#,.####,#...#,#...#,#...#,.####");
        glyph('e', ".....,.....,.###.,#...#,#####,#....,.###.");
        glyph('f', "..##.,.#...,.#...,###..,.#...,.#...,.#...");
        glyph('g', ".....,.....,.####,#...#,#...#,.####,....#,.###.");
        glyph('h', "#....,#....,####.,#...#,#...#,#...#,#...#");
        glyph('i', "..#..,.....,.##..,..#..,..#..,..#..,.###.");
        glyph('j', "...#.,.....,..##.,...#.,...#.,...#.,#..#.,.##..");
        glyph('k', "#....,#....,#..#.,#.#..,##...,#.#..,#..#.");
        glyph('l', ".##..,..#..,..#..,..#..,..#..,..#..,.###.");
        glyph('m', ".....,.....,##.#.,#.#.#,#.#.#,#.#.#,#...#");
        glyph('n', ".....,.....,####.,#...#,#...#,#...#,#...#");
        glyph('o', ".....,.....,.###.,#...#,#...#,#...#,.###.");
        glyph('p', ".....,.....,####.,#...#,#...#,####.,#....,#....");
        glyph('q', ".....,.....,.####,#...#,#...#,.####,....#,....#");
        glyph('r', ".....,.....,#.##.,##..#,#....,#....,#....");
        glyph('s', ".....,.....,.####,#....,.###.,....#,####.");
        glyph('t', ".#...,.#...,###..,.#...,.#...,.#..#,..##.");
        glyph('u', ".....,.....,#...#,#...#,#...#,#..##,.##.#");
        glyph('v', ".....,.....,#...#,#...#,#...#,.#.#.,..#..");
        glyph('w', ".....,.....,#...#,#...#,#.#.#,#.#.#,.#.#.");
        glyph('x', ".....,.....,#...#,.#.#.,..#..,.#.#.,#...#");
        glyph('y', ".....,.....,#...#,#...#,#...#,.####,....#,.###.");
        glyph('z', ".....,.....,#####,...#.,..#..,.#...,#####");
        glyph('0', ".###.,#...#,#..##,#.#.#,##..#,#...#,.###.");
        glyph('1', "..#..,.##..,..#..,..#..,..#..,..#..,.###.");
        glyph('2', ".###.,#...#,....#,...#.,..#..,.#...,#####");
        glyph('3', "####.,....#,....#,.###.,....#,....#,####.");
        glyph('4', "...#.,..##.,.#.#.,#..#.,#####,...#.,...#.");
        glyph('5', "#####,#....,####.,....#,....#,#...#,.###.");
        glyph('6', ".###.,#....,#....,####.,#...#,#...#,.###.");
        glyph('7', "#####,....#,...#.,..#..,.#...,.#...,.#...");
        glyph('8', ".###.,#...#,#...#,.###.,#...#,#...#,.###.");
        glyph('9', ".###.,#...#,#...#,.####,....#,....#,.###.");
        glyph('.', ".....,.....,.....,.....,.....,.##..,.##..");
        glyph(',', ".....,.....,.....,.....,.....,.##..,..#..,.#...");
        glyph('!', "..#..,..#..,..#..,..#..,..#..,.....,..#..");
        glyph('?', ".###.,#...#,....#,...#.,..#..,.....,..#..");
        glyph('\'', "..#..,..#..,.#...,.....,.....,.....,.....");
        glyph('"', ".#.#.,.#.#.,.....,.....,.....,.....,.....");
        glyph(':', ".....,.##..,.##..,.....,.##..,.##..,.....");
        glyph(';', ".....,.##..,.##..,.....,.##..,..#..,.#...");
        glyph('-', ".....,.....,.....,#####,.....,.....,.....");
        glyph('+', ".....,..#..,..#..,#####,..#..,..#..,.....");
        glyph('*', ".....,#.#.#,.###.,#####,.###.,#.#.#,.....");
        glyph('/', "....#,....#,...#.,..#..,.#...,#....,#....");
        glyph('(', "...#.,..#..,.#...,.#...,.#...,..#..,...#.");
        glyph(')', ".#...,..#..,...#.,...#.,...#.,..#..,.#...");
        glyph('%', "##..#,##..#,...#.,..#..,.#...,#..##,#..##");
        glyph('<', "...#.,..#..,.#...,#....,.#...,..#..,...#.");
        glyph('>', ".#...,..#..,...#.,....#,...#.,..#..,.#...");
        glyph('=', ".....,.....,#####,.....,#####,.....,.....");
        glyph('#', ".#.#.,#####,.#.#.,.#.#.,.#.#.,#####,.#.#.");
        glyph('_', ".....,.....,.....,.....,.....,.....,#####");
        glyph('[', ".###.,.#...,.#...,.#...,.#...,.#...,.###.");
        glyph(']', ".###.,...#.,...#.,...#.,...#.,...#.,.###.");
        glyph(' ', ".....,.....,.....,.....,.....,.....,.....");
    }

    /** Width in pixels of text in the pixel font at a scale (1 = 6 pixels per letter). */
    public static int textWidth(String s, int scale) {
        return s.length() * 6 * scale - scale;
    }

    /** Draws text in the pixel font, top-left at (x, y). scale 1 = 5 x 7 letters. */
    public static void text(Graphics2D g, String s, int x, int y, Color c, int scale) {
        g.setColor(c);
        int cx = x;
        for (char ch : s.toCharArray()) {
            String[] rows = GLYPHS.getOrDefault(ch, GLYPHS.getOrDefault(Character.toUpperCase(ch), GLYPHS.get('?')));
            for (int r = 0; r < rows.length; r++) {      // 7 rows; letters with a tail (g, p, y...) have an 8th
                for (int col = 0; col < 5; col++) {
                    if (rows[r].charAt(col) == '#') g.fillRect(cx + col * scale, y + r * scale, scale, scale);
                }
            }
            cx += 6 * scale;
        }
    }

    public static void centred(Graphics2D g, String s, int cx, int y, Color c, int scale) {
        text(g, s, cx - textWidth(s, scale) / 2, y, c, scale);
    }

    /** Word-wrapped text inside a width; returns the number of lines drawn. Only the first `shown` letters appear. */
    public static int wrap(Graphics2D g, String s, int x, int y, int width, Color c, int shown) {
        int perLine = Math.max(1, (width + 1) / 6);
        int line = 0, used = 0;
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            if (cur.length() > 0 && cur.length() + 1 + word.length() > perLine) {
                used = drawPart(g, cur.toString(), x, y + line * 10, c, shown, used);
                line++;
                cur = new StringBuilder();
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(word);
        }
        if (cur.length() > 0) {
            drawPart(g, cur.toString(), x, y + line * 10, c, shown, used);
            line++;
        }
        return line;
    }

    private static int drawPart(Graphics2D g, String s, int x, int y, Color c, int shown, int used) {
        int left = shown - used;
        if (left > 0) text(g, s.substring(0, Math.min(s.length(), left)), x, y, c, 1);
        return used + s.length() + 1;
    }

    // ─── Typewriter and voice ───────────────────────────────────
    /** A line of dialogue that types itself out, a letter at a time, each letter with a blip. */
    public static class Talk {
        public final String text;
        public final int pitch;
        private final long start;
        private int lastBlip = -1;
        private static final int MS_PER_LETTER = 32;

        public Talk(String text, int pitch) {
            this.text = text;
            this.pitch = pitch;
            this.start = System.currentTimeMillis();
        }

        /** How many letters are showing now (and blips for new ones). */
        public int shown() {
            int n = (int) Math.min(text.length(), (System.currentTimeMillis() - start) / MS_PER_LETTER);
            if (n > lastBlip && n > 0) {
                char ch = text.charAt(n - 1);
                if (Character.isLetterOrDigit(ch) && n % 2 == 0) blip(pitch);
                lastBlip = n;
            }
            return n;
        }

        public boolean done() {
            return shown() >= text.length();
        }
    }

    private static SourceDataLine line;
    private static boolean soundBroken;

    /** A tiny square-wave blip (a character's voice), played without waiting. */
    public static synchronized void blip(int pitch) {
        if (soundBroken) return;
        try {
            if (line == null) {
                AudioFormat f = new AudioFormat(22050, 8, 1, true, false);
                line = AudioSystem.getSourceDataLine(f);
                line.open(f, 4096);
                line.start();
            }
            int n = 22050 * 28 / 1000;
            byte[] buf = new byte[n];
            double period = 22050.0 / pitch;
            for (int i = 0; i < n; i++) {
                double env = 1.0 - (double) i / n;
                buf[i] = (byte) (((i % period) < period / 2 ? 18 : -18) * env);
            }
            if (line.available() >= n) line.write(buf, 0, n);
        } catch (Exception e) {
            soundBroken = true;                              // no audio device: stay quiet
        }
    }
}
