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

    // ─── The Ashes look: charred wood, brass, candlelight ───────
    public static final Color WOOD = new Color(74, 44, 30);
    public static final Color WOOD_LIGHT = new Color(104, 64, 42);
    public static final Color WOOD_DARK = new Color(40, 24, 18);
    public static final Color CHAR = new Color(24, 18, 16);
    public static final Color EMBER = new Color(232, 120, 40);
    public static final Color CREAM = new Color(236, 222, 190);
    public static final Color ASH = new Color(140, 128, 116);
    public static final Color BRICK = new Color(70, 40, 34);
    public static final Color BRICK_DARK = new Color(44, 26, 24);
    public static final Color MORTAR = new Color(30, 22, 22);

    /** A charred wooden frame with brass rivets at the corners, dark inside: every text box and panel. */
    public static void frame(Graphics2D g, int x, int y, int w, int h) {
        rect(g, x, y, w, h, BLACK);
        rect(g, x + 1, y + 1, w - 2, h - 2, WOOD);
        for (int i = x + 3; i < x + w - 3; i += 7) {               // grain along the top and bottom
            dot(g, i, y + 2, WOOD_LIGHT);
            dot(g, i + 3, y + h - 3, WOOD_DARK);
        }
        rect(g, x + 1, y + 1, w - 2, 1, WOOD_LIGHT);
        rect(g, x + 4, y + 4, w - 8, h - 8, BLACK);
        rect(g, x + 5, y + 5, w - 10, h - 10, CHAR);
        for (int[] c : new int[][]{{x + 2, y + 2}, {x + w - 4, y + 2}, {x + 2, y + h - 4}, {x + w - 4, y + h - 4}}) {
            rect(g, c[0], c[1], 2, 2, GOLD);
            dot(g, c[0], c[1], YELLOW);
        }
    }

    /** A small brass name plate (who is speaking), sitting on the top edge of a frame. */
    public static void plate(Graphics2D g, String name, int x, int y) {
        int w = textWidth(name, 1) + 10;
        rect(g, x, y, w, 11, BLACK);
        rect(g, x + 1, y + 1, w - 2, 9, GOLD);
        rect(g, x + 1, y + 1, w - 2, 1, YELLOW);
        rect(g, x + 1, y + 9, w - 2, 1, new Color(150, 110, 40));
        text(g, name, x + 5, y + 2, WOOD_DARK, 1);
    }

    /** The cellar wall: rough bricks fading into the dark, lit by a pool of lamplight around (lx, ly). */
    public static void cellar(Graphics2D g, int lx, int ly, int radius, double flicker) {
        rect(g, 0, 0, W, H, BLACK);
        for (int row = 0; row * 8 < H; row++) {
            int off = (row % 2) * 9;
            for (int col = -1; col * 18 < W; col++) {
                int bx = col * 18 + off, by = row * 8;
                double d = Math.hypot(bx + 9 - lx, (by + 4 - ly) * 1.3) / radius;
                if (d > 1.15) continue;
                Color c = d < 0.55 ? BRICK : BRICK_DARK;
                rect(g, bx + 1, by + 1, 16, 6, c);
                if (d < 0.4) rect(g, bx + 1, by + 1, 16, 1, new Color(96, 56, 44));
                if (d > 0.8) {                                        // dither into the dark at the edge
                    g.setColor(BLACK);
                    for (int yy = by + 1; yy < by + 7; yy++) {
                        for (int xx = bx + 1 + (yy % 2); xx < bx + 17; xx += 2) g.fillRect(xx, yy, 1, 1);
                    }
                }
            }
        }
        // The light itself: amber specks, thicker near the lamp, so the light falls off in dither.
        g.setColor(new Color(255, 196, 120, (int) (70 * flicker)));
        for (int y = 0; y < H; y += 2) {
            for (int x = (y / 2) % 2; x < W; x += 2) {
                double d = Math.hypot(x - lx, (y - ly) * 1.4) / radius;
                int hash = (x * 73856093) ^ (y * 19349663);
                hash = (hash ^ (hash >>> 13)) * 1274126177;
                if (d < 0.75 && ((hash >>> 8) & 255) < (0.75 - d) * 300) g.fillRect(x, y, 1, 1);
            }
        }
    }

    // ─── Icons (8 x 8) ──────────────────────────────────────────
    private static void icon(Graphics2D g, String[] rows, int x, int y, Color c) {
        g.setColor(c);
        for (int r = 0; r < rows.length; r++) {
            for (int col = 0; col < rows[r].length(); col++) {
                if (rows[r].charAt(col) == '#') g.fillRect(x + col, y + r, 1, 1);
            }
        }
    }

    public static void coin(Graphics2D g, int x, int y) {
        icon(g, new String[]{"..####..", ".######.", "##.##.##", "##.##.##", "##.##.##", "##.##.##", ".######.", "..####.."}, x, y, GOLD);
        icon(g, new String[]{"..####..", ".#......", "#.......", "#.......", "#.......", "#.......", ".#......", "........"}, x, y, YELLOW);
    }

    public static void skull(Graphics2D g, int x, int y) {
        icon(g, new String[]{".######.", "########", "##.##.##", "#..##..#", "########", ".######.", ".#.##.#.", ".######."}, x, y, CREAM);
    }

    public static void flame(Graphics2D g, int x, int y, double t) {
        int f = ((int) (t * 8)) % 2;
        icon(g, f == 0 ? new String[]{"...#....", "..##....", "..###...", ".#####..", ".##.##..", "..###...", "..###..."}
                       : new String[]{"....#...", "...##...", "..###...", ".#####..", ".##.##..", "..###...", "..###..."}, x, y, EMBER);
        icon(g, new String[]{"........", "........", "........", "........", "...#....", "..###...", "........"}, x, y, YELLOW);
        rect(g, x + 2, y + 7, 3, 4, CREAM);                           // the candle under it
    }

    // ─── The choices: a fan of cards ─────────────────────────────
    /** Choices drawn as cards along the bottom; the chosen one rises with a candle flame over it. */
    public static void cards(Graphics2D g, String[] labels, int selected, double t) {
        int n = labels.length, cw = 62, gap = 10, total = n * cw + (n - 1) * gap, x0 = (W - total) / 2;
        for (int i = 0; i < n; i++) {
            int x = x0 + i * (cw + gap), y = 214 - (i == selected ? 3 : 0);
            rect(g, x, y, cw, 30, BLACK);
            rect(g, x + 1, y + 1, cw - 2, 28, i == selected ? CREAM : ASH);
            rect(g, x + 3, y + 3, cw - 6, 24, i == selected ? new Color(214, 196, 160) : new Color(112, 102, 94));
            dot(g, x + 3, y + 3, DRED);
            dot(g, x + cw - 4, y + 26, DRED);
            centred(g, labels[i], x + cw / 2 + (i == selected ? 4 : 0), y + 12, i == selected ? WOOD_DARK : CHAR, 1);
            if (i == selected) flame(g, x + 5, y + 9, t);                  // a candle on the chosen card
        }
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
