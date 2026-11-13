import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The opening scene, timed to the "opening" song in Music (96 beats a minute; one footstep a beat):
 *
 *   beats   0-16   seen from the same distance as the walk: under a torn awning in hard rain, the guard
 *                  tries to light a cigarette. Two misses, a light the rain hisses out, two weak sparks, a
 *                  flame the rain kills. He lowers the lighter, his head drops, his shoulders shake. He snaps
 *                  it shut, flicks the soaked cigarette away over the rail, wipes his face and walks.
 *   beats  16-100  he walks out from under the awning and across a skywalk
 *                  through an overcrowded, rotting megacity (towers in haze, a shaft of light from above,
 *                  traffic, neon, beggars, rubbish heaps, fires in drums, rats, graffiti, a propaganda
 *                  screen, drizzle, a crowd far below). The credits are
 *                  neon signs on the buildings. At the far door he puts on his white smiling mask.
 *   beats 100-108  the game room: two prisoners at a chess table, masked guards around them in the dark;
 *                  he comes in and takes his place.
 *   beats 108-116  the camera turns to you, the prisoner in the paper-plate mask, and moves in.
 *
 * Everything is drawn in code on the Px canvas. draw(g, seconds) is all the game calls.
 */
public final class Opening {
    private Opening() {}

    public static final double SPB = 60.0 / Music.OPENING_BPM;
    public static final double LENGTH = Music.OPENING_BEATS * SPB;

    private static final int W = Px.W, H = Px.H;
    private static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};

    // ─── Where he is ────────────────────────────────────────────
    private static final int FLOOR = 282;                 // where feet stand on the skywalk
    private static final double START_X = 64, STRIDE = 30;
    private static final int DOOR_X = 2450, DOOR_W = 34, CAM_END = 2167;

    private static double walkerX(double b) {
        if (b < 16.5) return START_X;
        double x = START_X + (Math.min(b, 95.5) - 16.5) * STRIDE;
        if (b > 99) x += Math.min(1, b - 99) * 36;          // in through the door
        return x;
    }

    private static int cam(double b) {
        return (int) Math.max(0, Math.min(CAM_END, walkerX(b) - 200));
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static int hash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7fffffff;
    }

    // ─── The whole thing ────────────────────────────────────────
    public static void draw(Graphics2D g, double t) {
        double b = t / SPB;
        if (b < 100) outside(g, b, t);
        else if (b < 108.5) room(g, b, t);
        else if (b < 109.1) {                                       // a quick pan across the table to you
            double k = smooth(108.5, 109.1, b);
            BufferedImage a = Px.canvas(), c = Px.canvas();
            Graphics2D ga = Px.pen(a), gc = Px.pen(c);
            room(ga, b, t);
            player(gc, b, t);
            ga.dispose();
            gc.dispose();
            g.drawImage(a, (int) (-k * W), 0, null);
            g.drawImage(c, (int) ((1 - k) * W), 0, null);
        } else player(g, b, t);

        double black = 0;                                           // fades
        if (b < 1.2) black = 1 - smooth(0, 1.2, b);
        if (b > 99.3 && b < 100) black = smooth(99.3, 100, b);
        if (b >= 100 && b < 101.2) black = 1 - smooth(100, 101.2, b);
        if (b > 115.2) black = smooth(115.2, 116, b);
        if (black > 0) Px.rect(g, 0, 0, W, H, new Color(0, 0, 0, (int) (255 * Math.min(1, black))));
        if (b < 10) Px.text(g, "any key to skip", W - 98, H - 12, new Color(110, 110, 104), 1);
    }

    // ═══ 2. Outside: the city and the skywalk ═══════════════════
    private static final double F_FAR = 0.06, F_MID = 0.18, F_NEAR = 0.45, F_STREET = 0.7;
    private static BufferedImage sky, far, mid, near, rays, street;

    private static final Color[] HAZE = {new Color(14, 14, 12), new Color(30, 30, 26), new Color(50, 50, 42), new Color(76, 74, 60),
        new Color(106, 102, 82), new Color(142, 136, 108), new Color(184, 176, 142), new Color(222, 212, 176), new Color(244, 236, 206)};

    private static Color haze(double v, int x, int y) {
        int k = (int) Math.round(v * (HAZE.length - 1) + BAYER[y & 3][x & 3] / 16.0 - 0.47);
        return HAZE[Math.max(0, Math.min(HAZE.length - 1, k))];
    }

    private static void buildCity() {
        if (sky != null) return;
        sky = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double shaft = Math.exp(-Math.pow((x - 250) / (70 + y * 0.45), 2)) * (1 - y / 420.0);
                double v = 0.26 - 0.16 * y / H + 0.72 * shaft;
                double ring = 64 - 54 * Math.exp(-Math.pow((x - 250) / 120.0, 2));     // the great opening far above
                if (y < ring) v = 0.12 + (ring - y < 3 ? 0.3 : 0);
                sky.setRGB(x, y, haze(v, x, y).getRGB());
            }
        }
        far = layer(F_FAR, new Random(1), 30, 18, 52, 30, 190, 0.62, 0.018);
        mid = layer(F_MID, new Random(2), 24, 30, 84, 10, 150, 0.34, 0.07);
        near = layer(F_NEAR, new Random(3), 16, 50, 130, -40, 90, 0.06, 0.07);
        cables(mid, new Random(4), 10, new Color(40, 38, 32));
        cables(near, new Random(5), 14, new Color(12, 10, 10));
        rays = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 300; y++) {
            for (int x = 0; x < W; x++) {
                double ang = Math.atan2(x - 250, y + 40);
                double beam = Math.sin(ang * 23 + 1.3) * 0.6 + Math.sin(ang * 9 - 0.4) * 0.4;
                if (beam > 0.62 && BAYER[y & 3][x & 3] < 7) rays.setRGB(x, y, new Color(240, 230, 196, (int) (34 * (1 - y / 300.0))).getRGB());
            }
        }
        street = new BufferedImage(W + (int) (F_STREET * CAM_END) + 20, H, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(6);
        Color[] awnings = {new Color(200, 90, 50), new Color(170, 40, 40), new Color(60, 160, 140), new Color(210, 160, 60)};
        for (int y = 296; y < H; y++) for (int x = 0; x < street.getWidth(); x++) street.setRGB(x, y, haze(0.08 + (y > 340 ? 0.06 : 0), x, y).getRGB());
        for (int x = 0; x < street.getWidth(); x += 30 + r.nextInt(40)) {
            int w = 14 + r.nextInt(18);
            Color c = awnings[r.nextInt(awnings.length)];
            for (int yy = 324; yy < 328; yy++) for (int xx = x; xx < x + w && xx < street.getWidth(); xx++) street.setRGB(xx, yy, c.getRGB());
            for (int yy = 328; yy < 346; yy++) {
                for (int xx = x - 4; xx < x + w + 4 && xx < street.getWidth(); xx++) {
                    if (xx < 0) continue;
                    double glow = 1 - Math.abs(xx - x - w / 2.0) / (w / 2.0 + 4);
                    if (glow > 0 && BAYER[yy & 3][xx & 3] < glow * 10) street.setRGB(xx, yy, Shade.mix(c, new Color(30, 20, 18), 0.55).getRGB());
                }
            }
        }
    }

    /** A layer of towers seen through the haze: further layers paler, nearer ones nearly black. */
    private static BufferedImage layer(double f, Random r, int count, int wMin, int wMax, int topMin, int topMax, double hz, double windows) {
        int lw = W + (int) (f * CAM_END) + 20;
        BufferedImage img = new BufferedImage(lw, H, BufferedImage.TYPE_INT_ARGB);
        Color fog = new Color(118, 112, 92), dark = new Color(26, 25, 22);
        Color[] warm = {new Color(230, 196, 130), new Color(170, 200, 190), new Color(240, 150, 90)};
        for (int i = 0; i < count; i++) {
            int x = r.nextInt(lw), w = wMin + r.nextInt(wMax - wMin), top = topMin + r.nextInt(topMax - topMin);
            int tier = 14 + r.nextInt(30);
            boolean spire = r.nextInt(3) == 0;
            for (int y = Math.max(0, top - (spire ? 40 : 0)); y < H; y++) {
                int inset = y < top ? w / 2 - 1 : y < top + tier / 2 ? w * 2 / 5 : y < top + tier ? w / 5 : 0;
                for (int xx = x + inset; xx < x + w - inset && xx < lw; xx++) {
                    if (xx < 0) continue;
                    double v = 0.0;
                    int lx = xx - x;
                    if (lx - inset < 2) v += 0.25;                                   // the side facing the light
                    if (lx % 6 == 0) v -= 0.06;
                    double depthFog = hz + (y > 220 ? -0.15 * (y - 220) / 140.0 : 0);
                    Color c = Shade.mix(Shade.mix(dark, fog, Math.max(0, depthFog)), new Color(220, 210, 176), Math.max(0, v) * (0.5 + hz));
                    if (v < 0) c = Shade.mix(c, Color.BLACK, -v);
                    if (y >= top + 3 && (lx % 3 == 1) && ((y - top) % 4 == 2) && xx > x + inset + 1 && xx < x + w - inset - 2) {
                        int floor = (y - top) / 4, block = (xx - x) / 9;                // windows light up a floor at a time
                        boolean lit = hash(block * 31 + i, floor) % 1000 < windows * 3000 && hash(xx, y) % 4 != 0;
                        c = lit ? Shade.mix(warm[hash(i, floor / 6) % warm.length], fog, hz * 0.8) : Shade.mix(c, fog, 0.12);
                    }
                    img.setRGB(xx, y, c.getRGB());
                }
            }
        }
        return img;
    }

    private static void cables(BufferedImage img, Random r, int n, Color c) {
        for (int i = 0; i < n; i++) {
            int x1 = r.nextInt(img.getWidth()), x2 = x1 + 120 + r.nextInt(300), y1 = 40 + r.nextInt(180), y2 = 30 + r.nextInt(200);
            double sag = 20 + r.nextInt(50);
            for (int x = x1; x <= x2 && x < img.getWidth(); x++) {
                double k = (double) (x - x1) / (x2 - x1);
                int y = (int) (y1 + (y2 - y1) * k + sag * 4 * k * (1 - k));
                if (y >= 0 && y < img.getHeight()) img.setRGB(x, y, c.getRGB());
            }
        }
    }

    // Neon signs: {layer factor, x, y, w, h, colour index, seed}
    private static final Color[] NEON = {new Color(240, 86, 60), new Color(90, 230, 160), new Color(250, 170, 60), new Color(230, 90, 120)};
    private static final double[][] SIGNS = {
        {F_MID, 120, 150, 10, 46, 0, 1}, {F_MID, 380, 120, 12, 60, 2, 2}, {F_MID, 700, 170, 10, 40, 1, 3},
        {F_NEAR, 260, 170, 16, 70, 0, 4}, {F_NEAR, 560, 120, 14, 90, 1, 5}, {F_NEAR, 760, 200, 30, 14, 2, 6},
        {F_NEAR, 1240, 140, 16, 80, 3, 7}, {F_NEAR, 1380, 210, 40, 16, 0, 8}, {F_NEAR, 1190, 230, 12, 30, 1, 9},
    };

    private static void glyphSign(Graphics2D g, int x, int y, int w, int h, Color c, int seed, double t) {
        boolean on = hash(seed, (int) (t * 7)) % 23 != 0;
        Px.rect(g, x - 2, y - 2, w + 4, h + 4, new Color(c.getRed(), c.getGreen(), c.getBlue(), on ? 40 : 10));
        Px.rect(g, x, y, w, h, new Color(14, 10, 12));
        Color lit = on ? c : Shade.mix(c, new Color(30, 20, 20), 0.75);
        for (int gy = y + 2; gy + 3 <= y + h - 1; gy += 5) {
            for (int gx = x + 2; gx + 3 <= x + w - 1; gx += 5) {
                int bits = hash(gx * 7 + seed, gy);
                for (int k = 0; k < 9; k++) if ((bits >> k & 1) == 1) Px.dot(g, gx + k % 3, gy + k / 3, lit);
            }
        }
    }

    /** Neon words with a soft glow: how the credits hang on the buildings. */
    private static void neonText(Graphics2D g, String s, int x, int y, Color c, int scale, boolean on) {
        Color glow = new Color(c.getRed(), c.getGreen(), c.getBlue(), on ? 60 : 0);
        Color body = on ? c : Shade.mix(c, new Color(24, 18, 18), 0.8);
        if (on) for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-2, 0}, {2, 0}}) Px.text(g, s, x + d[0], y + d[1], glow, scale);
        Px.text(g, s, x, y, body, scale);
    }

    private static void outside(Graphics2D g, double b, double t) {
        buildCity();
        int cam = cam(b);
        g.drawImage(sky, 0, 0, null);
        g.drawImage(far, -(int) (cam * F_FAR), 0, null);
        traffic(g, cam, t, 0.05, 0.14);
        g.drawImage(mid, -(int) (cam * F_MID), 0, null);
        for (double[] s : SIGNS) if (s[0] == F_MID) glyphSign(g, (int) (s[1] - cam * s[0]), (int) s[2], (int) s[3], (int) s[4], NEON[(int) s[5]], (int) s[6], t);
        traffic(g, cam, t, 0.14, 0.4);
        g.drawImage(near, -(int) (cam * F_NEAR), 0, null);
        for (double[] s : SIGNS) if (s[0] == F_NEAR) glyphSign(g, (int) (s[1] - cam * s[0]), (int) s[2], (int) s[3], (int) s[4], NEON[(int) s[5]], (int) s[6], t);
        int mx = (int) (910 - cam * F_NEAR), sx2 = (int) (1068 - cam * F_NEAR);        // the credits
        Px.rect(g, mx - 6, 110, 92, 34, new Color(14, 10, 12));
        neonText(g, "A GAME BY", mx, 114, new Color(200, 190, 170), 1, hash((int) (t * 5), 11) % 17 != 0);
        neonText(g, "MADDOX", mx, 126, new Color(240, 90, 110), 2, hash((int) (t * 6), 12) % 19 != 0);
        Px.rect(g, sx2 - 6, 166, 136, 26, new Color(14, 10, 12));
        neonText(g, "SAMBAR BOY", sx2, 172, new Color(110, 230, 190), 2, hash((int) (t * 6), 13) % 21 != 0);
        boolean ashes = b > 84 && (b > 86 || hash((int) (b * 12), 5) % 3 != 0);           // the title flickers on
        int ax = (int) (612 - cam * F_MID);
        Px.rect(g, ax - 6, 52, Px.textWidth("ASHES", 4) + 12, 46, new Color(16, 12, 12));
        neonText(g, "ASHES", ax, 58, new Color(255, 140, 60), 4, ashes);
        neonText(g, "OF THE BOARD", ax + 30, 90, new Color(240, 200, 150), 1, ashes);
        int px = (int) (760 - cam * F_NEAR);                                            // a screen telling everyone what to do
        if (px > -90 && px < W) {
            Px.rect(g, px - 3, 26, 86, 50, new Color(10, 10, 12));
            String[] words = {"WORK.", "OBEY.", "PLAY.", "THE HOUSE", "ALWAYS WINS"};
            int k = (int) (t / 1.6) % 4;
            boolean glitch = hash((int) (t * 10), 21) % 9 == 0;
            Color c = glitch ? new Color(90, 230, 220) : new Color(230, 70, 60);
            if (k < 3) Px.centred(g, words[k], px + 40, 40, c, 2);
            else { Px.centred(g, words[3], px + 40, 36, c, 1); Px.centred(g, words[4], px + 40, 50, c, 1); }
            for (int y = 28; y < 74; y += 2) Px.rect(g, px - 1, y, 82, 1, new Color(0, 0, 0, 90));
            if (glitch) Px.rect(g, px - 1, 30 + hash((int) (t * 10), 3) % 40, 82, 3, new Color(200, 255, 250, 120));
        }
        g.drawImage(rays, 0, 0, null);
        for (int i = 0; i < 40; i++) {                                                 // dust drifting in the shaft of light
            int x = Math.floorMod((int) (hash(i, 1) % W + t * 3 - cam * 0.3), W), y = (int) ((hash(i, 2) % 300 + t * (5 + i % 5)) % 300);
            if (Math.abs(x - 250) < 90 + y * 0.3) Px.dot(g, x, y, new Color(236, 226, 194, 150));
        }
        g.drawImage(street, -(int) (cam * F_STREET), 0, null);
        for (int i = 0; i < 110; i++) {                                                // the crowd far below
            double v = (i % 2 == 0 ? 1 : -1) * (6 + i % 7);
            int x = Math.floorMod((int) (hash(i, 7) % 900 + v * t - cam * F_STREET), W + 20) - 10, y = 334 + (i % 5) * 4;
            int bob = (int) (Math.sin(t * 6 + i) * 0.6);
            Px.rect(g, x, y + bob, 2, 5, new Color(8, 8, 8));
            Px.rect(g, x, y - 2 + bob, 2, 2, new Color(14, 12, 12));
        }
        skywalk(g, b, t, cam);
        foreground(g, b, t, cam);
        int drops = b < 16 ? 220 : 90;                                                 // hard rain at first, then a filthy drizzle
        for (int i = 0; i < drops; i++) {                                              // rain
            int x = Math.floorMod((int) (hash(i, 31) % (W + 60) - t * 40 - (i % 3) * 9), W + 60) - 30;
            int y = (int) ((hash(i, 32) % H + t * (180 + i % 5 * 30)) % H);
            int len = b < 16 ? 10 : 5;
            for (int k = 0; k < len; k++) Px.dot(g, x - k / 3, y + k, new Color(170, 176, 160, b < 16 ? 100 : 50));
        }
    }

    /** Flying traffic in lanes at some depth range: dark shapes with a red tail light and a white head light. */
    private static void traffic(Graphics2D g, int cam, double t, double fMin, double fMax) {
        Random r = new Random(9);
        for (int i = 0; i < 34; i++) {
            double f = 0.05 + r.nextDouble() * 0.35;
            int y = 60 + r.nextInt(200), size = 1 + r.nextInt(3);
            double v = (r.nextBoolean() ? 1 : -1) * (12 + r.nextInt(40)) * (0.5 + f);
            int x0 = r.nextInt(2000);
            if (f < fMin || f >= fMax) continue;
            int x = Math.floorMod((int) (x0 + v * t - cam * f), W + 80) - 40;
            Color body = Shade.mix(new Color(24, 22, 20), new Color(118, 112, 92), 0.7 - f * 1.5);
            Px.rect(g, x, y, size * 6, size * 2, body);
            Px.rect(g, x + 1, y - 1, size * 4, 1, body);
            Px.dot(g, v > 0 ? x : x + size * 6 - 1, y + size, new Color(230, 40, 30));
            Px.dot(g, v > 0 ? x + size * 6 - 1 : x, y + size - 1, new Color(250, 246, 220));
        }
        if (fMin < 0.1) {                                                               // a great barge, slow and low
            int x = Math.floorMod((int) (60 + 9 * t - cam * 0.1), W + 200) - 120;
            Px.rect(g, x, 128, 84, 12, new Color(60, 58, 48));
            Px.rect(g, x + 10, 124, 60, 4, new Color(70, 68, 56));
            Px.rect(g, x, 140, 84, 2, new Color(34, 32, 28));
            for (int k = 0; k < 6; k++) Px.dot(g, x + 8 + k * 13, 134, hash(k, (int) (t * 2)) % 3 == 0 ? new Color(250, 120, 80) : new Color(240, 220, 170));
        }
    }

    // ─── The skywalk and what is on it ──────────────────────────
    private static final Map<String, BufferedImage> SPRITES = new HashMap<>();

    private static BufferedImage sprite(String key, java.util.function.Supplier<BufferedImage> make) {
        return SPRITES.computeIfAbsent(key, k -> make.get());
    }

    /** Beggar poses: 0 sitting with a cup held out, 1 lying under a blanket, 2 sitting with a sign, 3 a mother and child. */
    private static final int[][] BEGGARS = {{300, 0}, {420, 2}, {760, 1}, {980, 0}, {1110, 3}, {1420, 2}, {1600, 0}, {1840, 1}, {2060, 2}, {2250, 0}, {2370, 1}};
    private static final String[] SIGNS_TEXT = {"HUNGRY", "ANYTHING", "NO HOME", "HELP"};
    private static final int[] FIRES = {560, 1700};
    private static final int[] HEAPS = {200, 470, 690, 1030, 1180, 1350, 1560, 1780, 1990, 2190, 2330};

    private static BufferedImage beggar(int kind, int seed, boolean reaching) {
        return sprite("beg" + kind + "," + seed + "," + reaching, () -> {
            BufferedImage img = new BufferedImage(40, 34, BufferedImage.TYPE_INT_ARGB);
            Color[] rag = Shade.ramp(new Color(58 + seed % 3 * 8, 50 + seed % 2 * 6, 44), 4), dark = Shade.ramp(new Color(36, 32, 32), 3);
            Color[] skin = Shade.ramp(new Color(150, 116, 96), 3);
            if (kind == 1) {                                                          // lying down, a blanket over everything
                Shade.part(img, new RoundRectangle2D.Double(2, 22, 36, 11, 8, 8), rag, 3);
                Shade.part(img, new Ellipse2D.Double(30, 20, 9, 8), dark, 2);
                for (int x = 4; x < 34; x += 5) Shade.rect(img, x, 24 + x % 3, 3, 1, rag[0]);
                return img;
            }
            Shade.part(img, new Ellipse2D.Double(6, 14, 24, 20), rag, 4);             // sitting hunched, knees up
            Shade.part(img, new Ellipse2D.Double(11, 3, 12, 13), dark, 3);            // hooded head, bowed
            Shade.part(img, new Ellipse2D.Double(18, 7, 4, 5), Shade.ramp(new Color(14, 10, 12), 2), 0, false, 1);
            if (kind == 0) {                                                          // an arm out, a tin cup
                int reach = reaching ? 9 : 4;
                Shade.stroke(img, new Line2D.Double(20, 20, 24 + reach, 22), 3, rag);
                Shade.part(img, new Ellipse2D.Double(23 + reach, 20, 4, 4), skin, 1);
                Shade.part(img, new Rectangle2D.Double(26 + reach, 18, 4, 5), Shade.ramp(new Color(120, 120, 126), 3), 1);
            } else if (kind == 2) {                                                   // a cardboard sign
                Shade.part(img, new Rectangle2D.Double(14, 18, 24, 12), Shade.ramp(new Color(168, 136, 92), 3), 1);
            } else {                                                                  // a child against her
                Shade.part(img, new Ellipse2D.Double(26, 20, 10, 13), Shade.ramp(new Color(70, 56, 60), 3), 3);
                Shade.part(img, new Ellipse2D.Double(27, 13, 8, 8), dark, 2);
            }
            return img;
        });
    }

    /** Close to the camera, below and in front of the rail: rubbish mounds, a fence, pipes, someone asleep. */
    private static void foreground(Graphics2D g, double b, double t, int cam) {
        double f = 1.35;
        int off = (int) (cam * f);
        for (int i = 0; i < 16; i++) {
            int wx = i * 230 + hash(i, 40) % 90, x = wx - off;
            if (x < -200 || x > W + 40) continue;
            switch (i % 4) {
                case 0: {                                                                 // a mound of rubbish
                    for (int k = 0; k < 9; k++) {
                        int bw = 26 + hash(i, k) % 20, bh = 18 + hash(k, i) % 14;
                        g.setColor(k % 3 == 0 ? new Color(16, 16, 20) : k % 3 == 1 ? new Color(24, 26, 22) : new Color(32, 24, 20));
                        g.fillRoundRect(x + k * 16 - 30, 300 + (k % 3) * 8, bw, bh, 9, 9);
                        Px.rect(g, x + k * 16 - 26, 302 + (k % 3) * 8, 6, 1, new Color(70, 70, 84));
                    }
                    Px.rect(g, x + 20, 296, 4, 10, new Color(150, 40, 40));               // a crushed can on top
                    break;
                }
                case 1: {                                                                 // chain-link fence, torn
                    for (int y = 296; y < H; y++) for (int k = 0; k < 120; k++) {
                        if (((k + y) % 6 == 0 || (k - y + 600) % 6 == 0) && !(k > 70 && k < 90 && y > 320)) Px.dot(g, x + k, y, new Color(40, 42, 40));
                    }
                    Px.rect(g, x, 294, 120, 2, new Color(56, 56, 52));
                    Px.rect(g, x, 294, 3, H - 294, new Color(30, 30, 30));
                    Px.rect(g, x + 117, 294, 3, H - 294, new Color(30, 30, 30));
                    break;
                }
                case 2: {                                                                 // pipes, leaking
                    Px.rect(g, x - 40, 318, 200, 7, new Color(34, 30, 28));
                    Px.rect(g, x - 40, 318, 200, 1, new Color(86, 70, 56));
                    Px.rect(g, x - 40, 330, 200, 5, new Color(26, 24, 24));
                    for (int k = 0; k < 3; k++) {
                        int dy = (int) ((t * 60 + k * 17) % 40);
                        Px.dot(g, x + 30 + k * 50, 326 + dy, new Color(120, 140, 130));
                    }
                    break;
                }
                default: {                                                                // someone asleep on a ledge, close by
                    Px.rect(g, x - 20, 326, 110, 6, new Color(30, 28, 26));
                    g.setColor(new Color(44, 38, 36));
                    g.fillRoundRect(x, 304, 70, 22, 14, 14);
                    g.setColor(new Color(30, 26, 28));
                    g.fillOval(x + 58, 300, 18, 16);
                    Px.rect(g, x + 8, 310, 50, 1, new Color(60, 52, 48));
                    break;
                }
            }
        }
    }

    /** Everything that makes the skywalk a place nobody should live. */
    private static void filth(Graphics2D g, double b, double t, int cam) {
        // graffiti on the glass of the back rail and on the booth
        String[] tags = {"NO WAY OUT", "WHO WINS?", "THEY WATCH", "ROT", "FREE US", "PLAY OR PAY"};
        Color[] spray = {new Color(200, 60, 90), new Color(90, 200, 120), new Color(230, 200, 70), new Color(120, 140, 230), new Color(220, 120, 50)};
        for (int i = 0; i < 18; i++) {
            int wx = 140 + i * 131 + hash(i, 1) % 40, x = wx - cam;
            if (x < -80 || x > W) continue;
            Color c = Shade.mix(spray[i % spray.length], new Color(40, 36, 30), 0.35);
            if (i % 3 == 0) Px.text(g, tags[i / 3 % tags.length], x, 250 + hash(i, 2) % 4, c, i % 2 == 0 ? 2 : 1);
            else for (int k = 0; k < 26; k++) Px.dot(g, x + k, 256 + (int) (Math.sin(k * 0.7 + i) * 4), c);   // scrawls
        }
        // litter on every stretch of floor
        Color[] junk = {new Color(150, 40, 40), new Color(200, 196, 180), new Color(60, 120, 70), new Color(120, 120, 126), new Color(210, 170, 60), new Color(90, 70, 50)};
        for (int x = 0; x < W; x++) {
            int wx = x + cam;
            int h = hash(wx, 77);
            if (h % 5 != 0) continue;
            int y = 274 + h / 7 % 13;
            Color c = junk[h / 97 % junk.length];
            switch (h / 13 % 4) {
                case 0: Px.rect(g, x, y, 2, 1, c); break;                                          // wrappers
                case 1: Px.rect(g, x, y - 2, 2, 3, c); Px.dot(g, x, y - 2, Color.WHITE); break;    // cans
                case 2: Px.rect(g, x, y, 3, 2, Shade.mix(c, new Color(200, 196, 180), 0.6)); break; // paper
                default: Px.dot(g, x, y, new Color(20, 18, 16)); break;                            // stains
            }
        }
        // heaps of rubbish bags, flies over them
        for (int i = 0; i < HEAPS.length; i++) {
            int x = HEAPS[i] - cam;
            if (x < -40 || x > W) continue;
            for (int k = 0; k < 6 + i % 3; k++) {
                int bw = 14 + hash(i, k) % 9, bh = 11 + hash(k, i) % 8, bx = x + k * 9 - (k % 2) * 4, by = 275 - bh - (k % 3) * 5;
                g.setColor(k % 3 == 0 ? new Color(18, 18, 22) : k % 3 == 1 ? new Color(30, 34, 30) : new Color(40, 30, 26));
                g.fillRoundRect(bx, by, bw, bh, 5, 5);
                Px.dot(g, bx + 2, by + 2, new Color(90, 96, 104));
            }
            Px.rect(g, x - 4, 271, 10, 2, new Color(60, 44, 30));                               // something spilled
            for (int f = 0; f < 5; f++) {
                double a = t * (5 + f) + f * 1.7;
                Px.dot(g, x + 14 + (int) (Math.cos(a) * (6 + f)), 254 + (int) (Math.sin(a * 1.3) * 5), new Color(12, 12, 12));
            }
        }
        // fires in oil drums, people standing round them
        for (int fx : FIRES) {
            int x = fx - cam;
            if (x < -60 || x > W + 60) continue;
            g.setColor(new Color(255, 140, 50, 26 + (int) (10 * Math.sin(t * 13))));
            g.fillOval(x - 46, 196, 106, 90);
            for (int k = 0; k < 3; k++) {                                                        // people warming their hands
                BufferedImage w = shadowed(-1, k + 1);
                int px = x - 26 + k * 24 + (k == 1 ? 6 : 0);
                if (k == 1) g.drawImage(w, px, FLOOR - 6 - 88, null);
                else g.drawImage(w, k == 0 ? px : px + w.getWidth(), FLOOR - 6 - 88, k == 0 ? w.getWidth() : -w.getWidth(), w.getHeight(), null);
            }
            Px.rect(g, x, 254, 15, 20, new Color(86, 44, 30));
            Px.rect(g, x, 258, 15, 1, new Color(50, 26, 20));
            Px.rect(g, x, 266, 15, 1, new Color(50, 26, 20));
            Px.rect(g, x + 1, 254, 2, 20, new Color(130, 70, 40));
            for (int k = 0; k < 14; k++) {                                                       // the flames
                int fh = 4 + hash(k, (int) (t * 14)) % 9;
                Px.rect(g, x + 1 + k, 254 - fh, 1, fh, k % 3 == 0 ? new Color(255, 230, 140) : new Color(250, 130, 40));
            }
            for (int k = 0; k < 10; k++) {                                                       // smoke going up
                double age = (t * 0.6 + k * 0.1) % 1;
                int sx = x + 6 + (int) (Math.sin(t + k) * 4 + age * 10), sy = 240 - (int) (age * 90);
                g.setColor(new Color(70, 66, 58, (int) (90 * (1 - age))));
                g.fillOval(sx - 3 - (int) (age * 6), sy, 6 + (int) (age * 12), 5 + (int) (age * 8));
            }
        }
        // beggars against the rail; the ones with cups reach out as he passes
        double wxNow = walkerX(b);
        for (int i = 0; i < BEGGARS.length; i++) {
            int x = BEGGARS[i][0] - cam, kind = BEGGARS[i][1];
            if (x < -50 || x > W) continue;
            boolean reaching = kind == 0 && Math.abs(wxNow - BEGGARS[i][0]) < 60 && b > 16.5 && b < 96;
            g.drawImage(beggar(kind, i, reaching), x, 242, null);
            if (kind == 2) Px.text(g, SIGNS_TEXT[i % SIGNS_TEXT.length].substring(0, Math.min(3, SIGNS_TEXT[i % SIGNS_TEXT.length].length())), x + 16, 263, new Color(30, 22, 18), 1);
        }
        // rats
        for (int i = 0; i < 4; i++) {
            double run = (t * 70 + i * 400) % 900;
            int x = (int) (i % 2 == 0 ? run : 900 - run) - 200 + (i * 37) % 120, y = 286 - i % 2 * 3;
            if (x < -10 || x > W) continue;
            boolean right = i % 2 == 0;
            Px.rect(g, x, y, 6, 3, new Color(30, 26, 24));
            Px.rect(g, right ? x + 5 : x - 1, y, 2, 2, new Color(36, 30, 28));
            Px.rect(g, right ? x - 4 : x + 6, y + 2, 4, 1, new Color(70, 50, 50));
        }
        // a broken machine and a puddle
        int vx = 640 - cam;
        if (vx > -40 && vx < W) {
            Px.rect(g, vx, 218, 26, 54, new Color(30, 34, 36));
            Px.rect(g, vx + 4, 224, 18, 10, new Color(20, 40, 30));
            if (hash((int) (t * 9), 2) % 4 != 0) for (int k = 0; k < 4; k++) Px.rect(g, vx + 4 + hash(k, (int) (t * 9)) % 10, 225 + k * 2, 6, 1, new Color(90, 240, 150));
            Px.rect(g, vx + 4, 240, 18, 20, new Color(40, 44, 46));
            for (int k = 0; k < 8; k++) Px.dot(g, vx + 6 + hash(k, 5) % 14, 242 + hash(k, 6) % 16, new Color(110, 116, 120));   // smashed glass
        }
    }

    private static BufferedImage huddled(int seed) {
        return sprite("huddle" + seed, () -> {
            BufferedImage img = new BufferedImage(30, 26, BufferedImage.TYPE_INT_ARGB);
            Color[] rag = Shade.ramp(new Color(56 + seed * 6, 50, 46), 4);
            Shade.part(img, new Ellipse2D.Double(2, 8, 26, 18), rag, 6);
            Shade.part(img, new Ellipse2D.Double(8, 1, 12, 13), Shade.ramp(new Color(46, 42, 44), 3), 4);
            if (seed % 2 == 0) Shade.part(img, new Rectangle2D.Double(17, 12, 10, 8), Shade.ramp(new Color(170, 140, 96), 3), 1);   // a cardboard sign
            return img;
        });
    }

    private static void skywalk(Graphics2D g, double b, double t, int cam) {
        // underside and supports
        Px.rect(g, 0, 296, W, 4, new Color(22, 20, 20));
        for (int wx = -(cam % 90); wx < W; wx += 90) Px.dot(g, wx + 40, 298, hash(wx + cam, 1) % 2 == 0 ? new Color(220, 50, 40) : new Color(90, 160, 230));
        for (int s = Math.floorDiv(cam, 240) * 240; s < cam + W + 60; s += 240) {
            int x = s - cam;
            for (int k = 0; k < 64; k++) Px.rect(g, x + k, 300 + k, 3, 1, new Color(18, 16, 16));
        }
        // floor
        for (int y = 272; y < 290; y++) {
            for (int x = 0; x < W; x++) {
                int wx = x + cam;
                double v = 0.2 + (y - 272) * 0.006 + (wx % 6 == 0 ? -0.06 : 0) + ((y - 272) % 6 == 0 ? -0.04 : 0);
                Px.dot(g, x, y, haze(v, x, y));
            }
        }
        Px.rect(g, 0, 272, W, 1, new Color(110, 104, 86));
        Px.rect(g, 0, 290, W, 6, new Color(60, 56, 48));
        Px.rect(g, 0, 290, W, 1, new Color(140, 132, 106));
        // back rail
        Px.rect(g, 0, 248, W, 1, new Color(96, 92, 76));
        for (int wx = Math.floorDiv(cam, 48) * 48; wx < cam + W; wx += 48) Px.rect(g, wx - cam, 248, 2, 24, new Color(34, 32, 30));

        // where he was standing: an awning over a shuttered doorway, a drainpipe, the crushed cigarette
        int bx = -cam;
        if (bx > -110) {
            for (int y = 150; y < 272; y++) for (int x = 0; x < 100; x++) Px.dot(g, bx + x, y, haze(0.16 + (x < 3 ? 0.1 : 0) + ((hash(x, y) & 15) == 0 ? -0.06 : 0), x, y));
            for (int y = 196; y < 272; y += 3) Px.rect(g, bx + 30, y, 44, 2, new Color(56, 54, 50));   // a rusted shutter
            Px.rect(g, bx + 30, 196, 44, 1, new Color(90, 70, 50));
            Px.rect(g, bx + 10, 180, 84, 6, new Color(70, 40, 34));                                // the awning, torn
            for (int x = 10; x < 94; x += 7) Px.rect(g, bx + x, 186, 4, 3 + hash(x, 2) % 5, new Color(70, 40, 34));
            boolean lamp = hash((int) (t * 5), 8) % 5 != 0;
            Px.rect(g, bx + 46, 188, 8, 3, lamp ? new Color(250, 220, 160) : new Color(80, 70, 60));
            if (lamp) {
                g.setColor(new Color(250, 210, 140, 28));
                g.fillOval(bx + 10, 186, 80, 96);
            }
            Px.rect(g, bx + 94, 150, 3, 122, new Color(40, 44, 44));                               // a drainpipe, overflowing
            for (int k = 0; k < 4; k++) Px.dot(g, bx + 95, 272 + (int) ((t * 50 + k * 4) % 12), new Color(150, 170, 170));
        }
        // filth: litter everywhere, rubbish heaps with flies, graffiti, beggars, a fire in a barrel, rats
        filth(g, b, t, cam);
        for (int k = 0; k < 3; k++) {
            int x = 1150 + k * 11 - cam;
            Px.rect(g, x, 260 - k % 2 * 3, 12, 12 + k % 2 * 3, new Color(20, 20, 24));
            Px.dot(g, x + 3, 262, new Color(80, 80, 96));
        }
        for (int x = 1290; x < 1336; x++) {
            int sx = x - cam;
            if (sx < 0 || sx >= W) continue;
            for (int y = 279; y < 284; y++) if ((x + y) % 3 != 0) Px.dot(g, sx, y, hash(x / 4, (int) (t * 3)) % 2 == 0 ? new Color(150, 60, 90) : new Color(60, 140, 130));
        }
        int dx = (int) (1720 + 40 * Math.sin(t * 0.4)) - cam, dy = (int) (204 + 4 * Math.sin(t * 2));
        if (dx > -40 && dx < W + 40) {
            int sweep = (int) (Math.sin(t * 1.3) * 26);
            for (int y = dy + 6; y < 282; y++) {
                int half = (y - dy) / 3;
                for (int x = dx + 6 + sweep * (y - dy) / 80 - half; x < dx + 6 + sweep * (y - dy) / 80 + half; x++) {
                    if (((x + y) & 1) == 0 && BAYER[y & 3][x & 3] < 5) Px.dot(g, x, y, new Color(200, 40, 40, 90));
                }
            }
            Px.rect(g, dx, dy, 13, 5, new Color(30, 30, 34));
            Px.rect(g, dx - 3, dy - 1, 19, 1, new Color(60, 60, 66));
            Px.dot(g, dx + 6, dy + 4, new Color(255, 50, 40));
        }
        // the game room's wall and door at the far end
        int fx = 2420 - cam;
        if (fx < W) {
            for (int y = 110; y < 272; y++) for (int x = Math.max(0, fx); x < W; x++) Px.dot(g, x, y, haze(0.13 + ((hash(x + cam, y) & 31) == 0 ? 0.05 : 0), x, y));
            int d = DOOR_X - cam;
            double open = smooth(99, 99.6, b);
            Px.rect(g, d - 3, 182, DOOR_W + 6, 90, new Color(18, 16, 16));
            Px.rect(g, d, 186, DOOR_W, 86, new Color(70, 40, 30));                                 // warm inside
            Px.rect(g, d + (int) (DOOR_W * open), 186, DOOR_W - (int) (DOOR_W * open), 86, new Color(62, 64, 68));
            for (int k = 0; k < 4; k++) Px.dot(g, d + 4 + (int) (DOOR_W * open), 192 + k * 20, new Color(120, 120, 126));
            boolean lamp = hash((int) (t * 3), 4) % 7 != 0;
            Px.rect(g, d + 12, 172, 10, 5, lamp ? new Color(240, 40, 30) : new Color(90, 20, 20));
            if (lamp) {
                g.setColor(new Color(240, 40, 30, 30));
                g.fillOval(d - 4, 160, 42, 30);
            }
            Px.rect(g, d + DOOR_W + 20, 150, 10, 6, new Color(40, 40, 44));                         // a camera watching
            Px.dot(g, d + DOOR_W + 20, 153, new Color(255, 60, 40));
        }
        // people walking the other way, then him
        for (int i = 0; i < PASSERS.length; i++) {
            double meet = PASSERS[i];
            double x0 = walkerX(meet) + 30 * meet * SPB;
            int x = (int) (x0 - 30 * t) - cam;
            if (x < -50 || x > W + 10) continue;
            int frame = (int) (((t * 0.8 + i * 0.37) % 1) * 12);
            BufferedImage s = shadowed(frame, i);
            g.drawImage(s, x + s.getWidth(), FLOOR - 4 - 88, -s.getWidth(), s.getHeight(), null);
        }
        if (b < 99.9) {
            boolean handUp = (b > 1.6 && b < 4.9) || (b > 5.6 && b < 8.5) || (b > 12.4 && b < 12.7) || (b > 14.6 && b < 15.3);   // lighting it; flicking it away; wiping his face
            boolean bowed = b > 8.6 && b < 12.4;                                               // the head drops
            int stage = b < 16 ? (handUp ? 4 : bowed ? 5 : 0) : b < 97 ? 0 : b < 97.4 ? 1 : b < 97.8 ? 2 : 3;
            boolean walking = (b >= 16.5 && b <= 95.5) || b > 99;
            double p = ((b - 17) / 2 + 0.25) % 1;
            if (p < 0) p += 1;
            int frame = walking ? (int) (p * 12) % 12 : -1;
            int shake = b > 8.8 && b < 12.4 && near(b, SOBS, 0.3) && Math.sin(t * 36) > 0 ? 1 : 0;   // his shoulders shake
            g.drawImage(walker(frame, stage), (int) walkerX(b) - cam - 21, FLOOR - 88 + shake, null);
            if (b < 16) smoking(g, b, t, cam);
        }
        // front rail and glass, in front of everything on the walkway
        Px.rect(g, 0, 266, W, 1, new Color(150, 142, 112));
        Px.rect(g, 0, 267, W, 1, new Color(30, 28, 26));
        for (int wx = Math.floorDiv(cam, 48) * 48 + 24; wx < cam + W + 48; wx += 48) {
            int x = wx - cam, panel = Math.floorDiv(wx, 48);
            if (panel % 7 != 3) {
                g.setColor(new Color(190, 210, 200, 22));
                g.fillRect(x + 3, 268, 45, 22);
                for (int k = 0; k < 14; k++) Px.dot(g, x + 10 + k, 287 - k, new Color(230, 240, 236, 60));
                if (panel % 5 == 1) for (int k = 0; k < 12; k++) Px.dot(g, x + 20 + (hash(panel, k) % 5) - 2 + k, 270 + k + (k % 3), new Color(230, 236, 230, 140));
            }
            Px.rect(g, x, 266, 3, 24, new Color(40, 38, 34));
            Px.rect(g, x, 266, 1, 24, new Color(110, 104, 86));
        }
    }

    /** The cigarette that won't stay lit, small in the wide view: sparks, a flame the rain puts out, steam. */
    private static void smoking(Graphics2D g, double b, double t, int cam) {
        int mx = (int) walkerX(b) - cam + 11, my = FLOOR - 75;
        for (int i = 0; i < FLICKS.length; i++) {
            double f = FLICKS[i];
            if (b > f && b < f + 0.15) {
                boolean weak = i == 3 || i == 4;                                       // a wet wheel
                for (int k = 0; k < (weak ? 2 : 6); k++) Px.dot(g, mx + 2 + hash(k, (int) (b * 30)) % 7 - 3, my + 2 - hash(k, 4) % 6, new Color(255, 230, 140));
                g.setColor(new Color(255, 200, 120, weak ? 16 : 40));
                g.fillOval(mx - 12, my - 12, 26, 26);
            }
        }
        boolean flame1 = b > 3.75 && b < 4.6, flame2 = b > 7.65 && b < 8.3 && Math.sin(t * 50) > -0.5;   // the second gutters
        if (flame1 || flame2) {
            double k = flame2 ? 0.6 : 1;
            g.setColor(new Color(255, 160, 80, (int) ((34 + 8 * Math.sin(t * 30)) * k)));
            g.fillOval(mx - 34, my - 30, 70, 64);
            g.setColor(new Color(255, 170, 90, (int) (50 * k)));
            g.fillOval(mx - 12, my - 10, 26, 24);
            Px.rect(g, mx + 2, my - 2, 1, 3, new Color(255, 240, 180));
            Px.rect(g, mx + 1, my, 3, 1, new Color(250, 140, 50));
        }
        boolean inMouth = b > 2 && b < 12.55;
        int droop = b > 8.6 ? 2 : 0;                                                       // it hangs from his lip
        if (inMouth) {
            Px.rect(g, mx, my + 1 + droop / 2, 2, 1, new Color(196, 188, 170));
            Px.rect(g, mx + 2, my + 1 + droop, 2, 1, new Color(170, 164, 150));
            if (b > 4.0 && b < 5.0) {                                                      // lit, for one breath
                Px.dot(g, mx + 4, my + 1 + droop, new Color(255, 140, 60));
                g.setColor(new Color(255, 120, 50, 40));
                g.fillOval(mx - 2, my - 5, 12, 12);
            }
        }
        if (b > 5.0 && b < 6.2) {                                                          // the rain hits it: a puff of steam
            double age = b - 5.0;
            for (int k = 0; k < 6; k++) Px.dot(g, mx + 4 + k % 3 - 1 + (int) (age * 3), my - (int) (age * 8) - k, new Color(190, 190, 196, (int) (160 * (1 - age / 1.2))));
        }
        if (b > 8.3 && b < 9.4) {                                                          // a thread of smoke off the dead wick
            double age = b - 8.3;
            for (int k = 0; k < 5; k++) Px.dot(g, mx + 2 + (int) (Math.sin(age * 5 + k) * 1.5), my - 3 - k * 2 - (int) (age * 6), new Color(170, 170, 178, (int) (120 * (1 - age / 1.1))));
        }
        if (b >= 12.6 && b < 15) {                                                         // flicked away: out over the rail, down into the drop
            double s2 = (b - 12.6) * SPB;
            int x = mx + 4 + (int) (s2 * 70), y = (int) (my - 2 - s2 * 60 + s2 * s2 * 140);
            if (y < H) {
                boolean spin = ((int) (b * 12)) % 2 == 0;
                Px.rect(g, x, y, spin ? 3 : 1, spin ? 1 : 2, new Color(186, 180, 166));
            }
        }
    }

    private static final double[] FLICKS = {2.0, 2.9, 3.7, 6.0, 6.8, 7.6};      // 3.7 and 7.6 catch
    private static final double[] SOBS = {9.0, 9.7, 10.4, 11.3, 11.9};

    private static boolean near(double b, double[] at, double w) {
        for (double a : at) if (Math.abs(b - a) < w) return true;
        return false;
    }

    private static final double[] PASSERS = {21, 26, 31, 35, 40, 44, 50, 55, 59, 64, 69, 74, 79, 84, 88, 92};

    // ─── The guard, walking ─────────────────────────────────────
    /** One frame of him walking (frame 0-11 of a two-step stride, or -1 standing); stage 0-3 is his mask going on. */
    static BufferedImage walker(int frame, int stage) {
        return sprite("walk" + frame + "," + stage, () -> buildWalker(frame < 0 ? 0 : frame / 12.0, frame >= 0, stage, false));
    }

    private static BufferedImage shadowed(int frame, int who) {
        return sprite("pass" + frame + "," + who % 3, () -> {
            BufferedImage src = buildWalker(Math.max(0, frame) / 12.0, frame >= 0, -1, true);
            BufferedImage d = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Color tint = who % 3 == 0 ? new Color(16, 12, 20) : who % 3 == 1 ? new Color(22, 18, 16) : new Color(12, 16, 18);
            for (int y = 0; y < src.getHeight(); y++) {
                for (int x = 0; x < src.getWidth(); x++) {
                    int c = src.getRGB(x, y);
                    if ((c >>> 24) != 0) d.setRGB(x, y, Shade.mix(new Color(c), tint, 0.72).getRGB());
                }
            }
            return d;
        });
    }

    private static double[] limb(double hx, double hy, double a, double bend, double l1, double l2) {
        double kx = hx + Math.sin(a) * l1, ky = hy + Math.cos(a) * l1;
        return new double[]{kx, ky, kx + Math.sin(a - bend) * l2, ky + Math.cos(a - bend) * l2};
    }

    private static BufferedImage buildWalker(double p, boolean walking, int stage, boolean plain) {
        BufferedImage img = new BufferedImage(44, 92, BufferedImage.TYPE_INT_ARGB);
        Color[] coat = Shade.ramp(new Color(60, 56, 66), 5), coatFar = Shade.ramp(new Color(40, 37, 46), 5);
        Color[] pants = Shade.ramp(new Color(42, 40, 46), 4), pantsFar = Shade.ramp(new Color(28, 26, 32), 4);
        Color[] boot = Shade.ramp(new Color(32, 28, 30), 3), glove = Shade.ramp(new Color(36, 32, 36), 3);
        double s = Math.sin(2 * Math.PI * p), c = Math.cos(2 * Math.PI * p);
        double aL = walking ? 0.38 * s : 0.05, aR = walking ? -0.38 * s : -0.05;
        double bendL = walking ? 0.8 * Math.max(0, c) : 0, bendR = walking ? 0.8 * Math.max(0, -c) : 0;
        double[] l = limb(0, 0, aL, bendL, 19, 19), r = limb(0, 0, aR, bendR, 19, 19);
        double hipY = 86 - Math.max(l[3], r[3]), hipX = 21;
        double sy = hipY - 28;
        double armNear = walking ? -0.35 * s : 0.08, armFar = walking ? 0.35 * s : -0.06;

        double[] fa = limb(hipX, sy, armFar, -0.35, 13, 12);                           // far arm
        Shade.stroke(img, path(hipX, sy, fa[0], fa[1], fa[2], fa[3]), 5, coatFar);
        Shade.part(img, new Ellipse2D.Double(fa[2] - 2.5, fa[3] - 2, 5, 5), glove, 2);
        for (int k = 0; k < 2; k++) {                                                   // legs: far then near
            double[] g = k == 0 ? r : l;
            Shade.stroke(img, path(hipX, hipY, hipX + g[0], hipY + g[1], hipX + g[2], hipY + g[3]), 7, k == 0 ? pantsFar : pants);
            Shade.part(img, new RoundRectangle2D.Double(hipX + g[2] - 4, hipY + g[3] - 2, 11, 5, 3, 3), boot, 2);
        }
        double hem = walking ? 2 * Math.sin(2 * Math.PI * p + 1) : 0;
        Polygon body = new Polygon(new int[]{17, 27, 30, 32 + (int) hem, 9 + (int) hem, 12, 14},
            new int[]{(int) sy - 2, (int) sy - 1, (int) sy + 8, (int) hipY + 16, (int) hipY + 16, (int) sy + 8, (int) sy}, 7);
        Shade.part(img, body, coat, 6);
        Shade.stroke(img, new Line2D.Double(13, hipY - 5, 30, hipY - 5), 1.2f, Shade.ramp(new Color(24, 22, 26), 2));   // belt
        double hy = sy - (stage == 5 ? 5 : 10);
        Path2D hood = new Path2D.Double();
        hood.moveTo(14, hy + 9);
        hood.curveTo(11, hy - 2, 16, hy - 9, 23, hy - 9);
        hood.curveTo(30, hy - 9, 32, hy - 2, 31, hy + 6);
        hood.lineTo(26, hy + 10);
        hood.closePath();
        Shade.part(img, hood, coat, 5);
        Shade.part(img, new Ellipse2D.Double(26, hy - 5, 6, 12), Shade.ramp(new Color(14, 10, 16), 2), 0, false, 1);   // face in shadow
        if (stage == 3) {                                                               // the mask on: white, smiling
            Shade.part(img, new Ellipse2D.Double(27, hy - 6, 6, 13), Shade.ramp(new Color(232, 224, 212), 4), 2);
            Shade.px(img, 31, (int) hy - 2, new Color(20, 14, 18));
            Shade.px(img, 32, (int) hy + 3, new Color(20, 14, 18));
            Shade.px(img, 31, (int) hy + 4, new Color(20, 14, 18));
        }
        if ((stage == 0 || stage == 4 || stage == 5) && !plain) {                                     // the mask hanging from his belt
            Shade.part(img, new Ellipse2D.Double(25, hipY - 4, 6, 8), Shade.ramp(new Color(232, 224, 212), 4), 2);
            Shade.px(img, 28, (int) hipY - 1, new Color(20, 14, 18));
            Shade.px(img, 29, (int) hipY + 2, new Color(140, 60, 60));
        }
        double hx, hyy;                                                                  // the near arm, or the arm lifting the mask
        if (stage == 1) { hx = 31; hyy = sy + 6; } else if (stage == 2) { hx = 32; hyy = hy + 2; } else if (stage == 4) { hx = 30; hyy = hy + 4; } else { hx = -1; hyy = -1; }
        if (hx < 0) {
            double[] na = limb(hipX, sy, armNear, -0.35, 13, 12);
            Shade.stroke(img, path(hipX, sy, na[0], na[1], na[2], na[3]), 5.5f, coat);
            Shade.part(img, new Ellipse2D.Double(na[2] - 2.5, na[3] - 2, 5, 5), glove, 2);
            if (stage == 5) Shade.rect(img, (int) na[2], (int) na[3] - 3, 2, 3, new Color(196, 160, 70));   // the lighter in his hand
        } else {
            Shade.stroke(img, path(hipX, sy, hipX + 6, sy + 12, hx, hyy), 5.5f, coat);
            if (stage != 4) Shade.part(img, new Ellipse2D.Double(25 + (stage == 2 ? 2 : 0), hyy - 6, 6, 12), Shade.ramp(new Color(232, 224, 212), 4), 2);
            Shade.part(img, new Ellipse2D.Double(hx - 3, hyy - 2, 5, 5), glove, 2);
        }
        return img;
    }

    private static Path2D path(double x1, double y1, double x2, double y2, double x3, double y3) {
        Path2D p = new Path2D.Double();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        p.lineTo(x3, y3);
        return p;
    }

    // ═══ 3. The game room ═══════════════════════════════════════
    private static final Map<BufferedImage, BufferedImage> DARK = new java.util.LinkedHashMap<BufferedImage, BufferedImage>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<BufferedImage, BufferedImage> e) { return size() > 80; }
    };

    /** A figure standing back from the lamp: mostly shadow, the eyes still catching the light. */
    private static BufferedImage dark(BufferedImage src, double amount) {
        return DARK.computeIfAbsent(src, k -> {
            BufferedImage d = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < src.getHeight(); y++) {
                for (int x = 0; x < src.getWidth(); x++) {
                    int argb = src.getRGB(x, y);
                    if ((argb >>> 24) == 0) continue;
                    Color c = new Color(argb);
                    boolean glint = c.getRed() > 235 && c.getGreen() > 210;
                    d.setRGB(x, y, glint ? argb : Shade.mix(c, new Color(12, 7, 18), amount - 0.12 * (1 - y / (double) src.getHeight())).getRGB());
                }
            }
            return d;
        });
    }

    private static BufferedImage darkLess(BufferedImage src) {
        BufferedImage d = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                if ((argb >>> 24) != 0) d.setRGB(x, y, Shade.mix(new Color(argb), new Color(12, 7, 18), 0.45).getRGB());
            }
        }
        return d;
    }

    private static void room(Graphics2D g, double b, double t) {
        double flick = 0.85 + 0.15 * Math.sin(t * 7) * Math.sin(t * 2.3);
        Px.cellar(g, 240, 90, 300, flick);
        // the door at the back, where he comes in
        double open = b < 103 ? 1 : 1 - smooth(103, 104, b);
        Px.rect(g, 316, 70, 46, 128, new Color(14, 10, 12));
        if (open > 0) {
            int w = (int) (42 * open);
            Px.rect(g, 318, 72, w, 124, new Color(196, 168, 120));
            if (b < 103) {                                                              // his outline in the doorway
                int x = 318 + (int) Math.min(w - 14, (b - 100) * 6);
                Px.rect(g, x + 2, 92, 10, 11, new Color(20, 16, 18));
                Px.rect(g, x, 103, 14, 48, new Color(20, 16, 18));
                Px.rect(g, x + 2, 151, 4, 45, new Color(20, 16, 18));
                Px.rect(g, x + 8, 151, 4, 45, new Color(20, 16, 18));
                Px.rect(g, x + 9, 94, 3, 6, new Color(220, 210, 196));
            }
        }
        // the ring of guards, in the dark
        PixelArt.Person[] G = PixelArt.GUARDS;
        g.drawImage(dark(PixelArt.person(G[1], t + 1.3, null, 0), 0.7), -40, 28, null);
        g.drawImage(dark(PixelArt.person(G[2], t + 2.1, null, 0), 0.7), 50, 20, null);
        g.drawImage(dark(PixelArt.person(G[4], t + 0.6, null, 0), 0.7), 370, 28, null);
        if (b > 104.6) g.drawImage(dark(PixelArt.person(G[0], t, null, 0), 0.6), 290, 22, null);   // he takes his place
        g.drawImage(PixelArt.person(PixelArt.OPPONENTS[0], t, null, 0), (W - PixelArt.SPRITE_W) / 2, 60, null);
        PixelPreview.lamp(g, 240, t);
        PixelPreview.table(g, 30, 196, 420, 18);
        PixelArt.piece(g, "bK", 200, 204);
        PixelArt.piece(g, "wK", 280, 204);
        // you, from behind: a hood, shoulders, the string of your mask
        BufferedImage back = sprite("back", () -> {
            BufferedImage img = new BufferedImage(W, 160, BufferedImage.TYPE_INT_ARGB);
            Color[] ramp = Shade.ramp(new Color(40, 36, 42), 5);
            Shade.part(img, new Ellipse2D.Double(40, 78, 400, 200), ramp, 34);      // shoulders
            Path2D hood = new Path2D.Double();
            hood.moveTo(166, 120);
            hood.curveTo(160, 40, 196, 6, 240, 4);
            hood.curveTo(284, 6, 320, 40, 314, 120);
            hood.closePath();
            Shade.part(img, hood, ramp, 26);
            Path2D seam = new Path2D.Double();                                          // the hood's seam down the back
            seam.moveTo(240, 8);
            seam.quadTo(238, 60, 240, 118);
            Shade.stroke(img, seam, 1.5f, Shade.ramp(new Color(24, 20, 26), 2));
            for (int x = 0; x < W; x++) {                                               // the lamp catches the top edge
                for (int y = 0; y < 160; y++) {
                    if ((img.getRGB(x, y) >>> 24) != 0) {
                        img.setRGB(x, y, new Color(200, 140, 90).getRGB());
                        break;
                    }
                }
            }
            return img;
        });
        g.drawImage(back, 0, 232, null);
    }

    /** You, seen from across the table: the paper plate, the guards behind you; the camera moves in. */
    private static void player(Graphics2D g, double b, double t) {
        BufferedImage scene = Px.canvas();
        Graphics2D s = Px.pen(scene);
        double flick = 0.85 + 0.15 * Math.sin(t * 7) * Math.sin(t * 2.3);
        Px.cellar(s, 240, 90, 300, flick);
        PixelArt.Person[] G = PixelArt.GUARDS;
        s.drawImage(dark(PixelArt.person(G[3], t + 0.9, null, 0), 0.7), -30, 30, null);
        s.drawImage(dark(PixelArt.person(G[1], t + 1.7, null, 0), 0.7), 360, 30, null);
        s.drawImage(darkLess(PixelArt.person(G[0], t, null, 0)), 268, 14, null);       // the smiling one, right behind you
        s.drawImage(PixelArt.person(PixelArt.PLAYER, t, b > 112 ? "think" : null, b > 112 ? Math.min(1, (b - 112) / 3) : 0), (W - PixelArt.SPRITE_W) / 2, 50, null);
        PixelPreview.lamp(s, 240, t);
        PixelPreview.table(s, 30, 186, 420, 18);
        s.dispose();
        double z = 1 + smooth(108.8, 114.5, b) * 1.6;
        double cx = 240, cy = 180 + (95 - 180) * smooth(108.8, 114.5, b);
        double vw = W / z, vh = H / z;
        int x0 = (int) Math.max(0, Math.min(W - vw, cx - vw / 2)), y0 = (int) Math.max(0, Math.min(H - vh, cy - vh / 2));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(scene, 0, 0, W, H, x0, y0, x0 + (int) vw, y0 + (int) vh, null);
    }
}
