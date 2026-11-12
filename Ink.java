import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * The drawing kit for the "dark storybook" look: everything in the game is drawn with these.
 *
 *  - a small palette (charcoal, bone, dried red, lamp ochre, cold shadow)
 *  - ink outlines that wobble like a hand-drawn line and "boil" (redraw slightly differently) a few
 *    times a second, so still pictures feel alive
 *  - hatching for shade, a soft pool of lamp light, and paper grain over the whole picture
 *
 * Pure Java2D: no images are loaded, so it runs anywhere BlueJ does.
 */
public final class Ink {
    private Ink() {}

    // ─── Palette ────────────────────────────────────────────────
    public static final Color INK = new Color(22, 18, 20);
    public static final Color CHARCOAL = new Color(38, 33, 35);
    public static final Color SOOT = new Color(58, 51, 52);
    public static final Color ASH = new Color(92, 84, 82);
    public static final Color BONE = new Color(222, 210, 186);
    public static final Color BONE_SHADE = new Color(170, 156, 134);
    public static final Color PAPER = new Color(236, 226, 204);
    public static final Color BLOOD = new Color(122, 30, 30);
    public static final Color RUST = new Color(150, 72, 44);
    public static final Color OCHRE = new Color(224, 168, 90);
    public static final Color LAMP = new Color(255, 214, 150);
    public static final Color WOOD = new Color(96, 56, 36);
    public static final Color WOOD_DARK = new Color(62, 36, 24);
    public static final Color LEATHER = new Color(74, 50, 36);
    public static final Color BRASS = new Color(176, 132, 60);
    public static final Color BURLAP = new Color(160, 128, 86);
    public static final Color SHADOW = new Color(30, 36, 46);
    public static final Color GLASS = new Color(40, 70, 58);

    /** Current "boil" frame: outlines change shape slightly when this changes (about 8 times a second). */
    public static int boil() {
        return (int) (System.currentTimeMillis() / 125) % 3;
    }

    // ─── Noise ──────────────────────────────────────────────────
    private static double hash(int x, int seed) {
        long h = x * 374761393L + seed * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xffff) / 65535.0 * 2 - 1;
    }

    /** Smooth value noise in -1..1. */
    public static double noise(double x, int seed) {
        int i = (int) Math.floor(x);
        double f = x - i, u = f * f * (3 - 2 * f);
        return hash(i, seed) * (1 - u) + hash(i + 1, seed) * u;
    }

    // ─── Wobbly lines ───────────────────────────────────────────
    /**
     * The shape redrawn as if by hand: each point pushed sideways a little by smooth noise.
     * wobble: how far, in pixels. seed: makes each shape wobble its own way.
     */
    public static Shape wobble(Shape s, double wobble, int seed) {
        if (wobble <= 0) return s;
        Path2D.Double out = new Path2D.Double();
        PathIterator it = new FlatteningPathIterator(s.getPathIterator(null), 0.8, 8);
        double[] c = new double[6];
        double dist = 0, lx = 0, ly = 0;
        int frame = boil() * 7919 + seed * 31;
        while (!it.isDone()) {
            int type = it.currentSegment(c);
            if (type == PathIterator.SEG_CLOSE) {
                out.closePath();
            } else {
                double x = c[0], y = c[1];
                if (type == PathIterator.SEG_LINETO) dist += Math.hypot(x - lx, y - ly);
                double n = dist / 26.0;
                double ox = noise(n, frame) * wobble, oy = noise(n + 57.3, frame + 1) * wobble;
                if (type == PathIterator.SEG_MOVETO) out.moveTo(x + ox, y + oy);
                else out.lineTo(x + ox, y + oy);
                lx = x;
                ly = y;
            }
            it.next();
        }
        return out;
    }

    /** Fills a shape and outlines it in ink (both wobbled the same way). */
    public static void draw(Graphics2D g, Shape s, Paint fill, float line, int seed) {
        Shape w = wobble(s, line * 0.55, seed);
        if (fill != null) {
            g.setPaint(fill);
            g.fill(w);
        }
        if (line > 0) {
            g.setColor(INK);
            g.setStroke(new BasicStroke(line, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(w);
        }
    }

    /** Just an ink line (open or closed shape), wobbled. */
    public static void line(Graphics2D g, Shape s, float width, Color colour, int seed) {
        g.setColor(colour);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(wobble(s, width * 0.5, seed));
    }

    public static void line(Graphics2D g, double x1, double y1, double x2, double y2, float width, int seed) {
        line(g, new Line2D.Double(x1, y1, x2, y2), width, INK, seed);
    }

    // ─── Shading ────────────────────────────────────────────────
    /** Diagonal hatching inside a shape: spacing in pixels, angle in degrees, alpha 0..255. */
    public static void hatch(Graphics2D g, Shape area, double spacing, double angle, int alpha, int seed) {
        Shape old = g.getClip();
        g.clip(area);
        Rectangle2D b = area.getBounds2D();
        double r = Math.hypot(b.getWidth(), b.getHeight());
        double cx = b.getCenterX(), cy = b.getCenterY();
        double a = Math.toRadians(angle), dx = Math.cos(a), dy = Math.sin(a);
        g.setColor(new Color(INK.getRed(), INK.getGreen(), INK.getBlue(), alpha));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Random rng = new Random(seed);
        for (double t = -r; t < r; t += spacing) {
            double px = cx - dy * t, py = cy + dx * t;
            double jitter = rng.nextDouble() * spacing * 0.3;
            g.draw(new Line2D.Double(px - dx * r + jitter, py - dy * r, px + dx * r, py + dy * r + jitter));
        }
        g.setClip(old);
    }

    /** A vertical gradient paint from top colour to bottom colour over the shape's bounds. */
    public static Paint shade(Shape s, Color top, Color bottom) {
        Rectangle2D b = s.getBounds2D();
        return new GradientPaint((float) b.getX(), (float) b.getY(), top, (float) b.getX(), (float) b.getMaxY(), bottom);
    }

    /** A soft round glow (for the lamp, candles, the stove). */
    public static void glow(Graphics2D g, double x, double y, double radius, Color colour, int alpha) {
        Color c0 = new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), Math.min(255, alpha));
        Color c1 = new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 0);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), (float) radius, new float[]{0f, 1f}, new Color[]{c0, c1}));
        g.fill(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
    }

    /** Darkness everywhere except a pool of light around (x, y). */
    public static void darkness(Graphics2D g, int w, int h, double x, double y, double radius, int edgeAlpha) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), (float) radius, new float[]{0f, 0.45f, 1f},
            new Color[]{new Color(0, 0, 0, 0), new Color(8, 6, 10, edgeAlpha / 2), new Color(8, 6, 10, edgeAlpha)}));
        g.fillRect(0, 0, w, h);
    }

    // ─── Paper ──────────────────────────────────────────────────
    private static BufferedImage grain;

    /** Paper grain and a faint vignette over the whole picture. */
    public static void paper(Graphics2D g, int w, int h) {
        if (grain == null) {
            int s = 256;
            grain = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Random rng = new Random(11);
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    int v = rng.nextInt(60);
                    int a = rng.nextInt(100) < 45 ? 14 + rng.nextInt(16) : 0;
                    grain.setRGB(x, y, (a << 24) | ((180 + v) << 16) | ((165 + v) << 8) | (140 + v));
                }
            }
        }
        Paint old = g.getPaint();
        int shift = boil() * 37;
        g.setPaint(new TexturePaint(grain, new Rectangle2D.Double(shift, shift, 256, 256)));
        g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w / 2.0, h / 2.0), Math.max(w, h) * 0.72f,
            new float[]{0.55f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 150)}));
        g.fillRect(0, 0, w, h);
        g.setPaint(old);
    }

    /** Turns on smooth drawing. */
    public static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    // ─── Small helpers for building shapes ──────────────────────
    public static Ellipse2D oval(double cx, double cy, double rx, double ry) {
        return new Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2);
    }

    /** A closed smooth shape through the points (a Catmull-Rom loop). */
    public static Path2D blob(double... xy) {
        int n = xy.length / 2;
        Path2D.Double p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 0; i < n; i++) {
            double x0 = xy[((i - 1 + n) % n) * 2], y0 = xy[((i - 1 + n) % n) * 2 + 1];
            double x1 = xy[i * 2], y1 = xy[i * 2 + 1];
            double x2 = xy[((i + 1) % n) * 2], y2 = xy[((i + 1) % n) * 2 + 1];
            double x3 = xy[((i + 2) % n) * 2], y3 = xy[((i + 2) % n) * 2 + 1];
            p.curveTo(x1 + (x2 - x0) / 6, y1 + (y2 - y0) / 6, x2 - (x3 - x1) / 6, y2 - (y3 - y1) / 6, x2, y2);
        }
        p.closePath();
        return p;
    }

    /** An open smooth curve through the points. */
    public static Path2D curve(double... xy) {
        int n = xy.length / 2;
        Path2D.Double p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 0; i < n - 1; i++) {
            double x0 = xy[Math.max(0, i - 1) * 2], y0 = xy[Math.max(0, i - 1) * 2 + 1];
            double x1 = xy[i * 2], y1 = xy[i * 2 + 1];
            double x2 = xy[(i + 1) * 2], y2 = xy[(i + 1) * 2 + 1];
            double x3 = xy[Math.min(n - 1, i + 2) * 2], y3 = xy[Math.min(n - 1, i + 2) * 2 + 1];
            p.curveTo(x1 + (x2 - x0) / 6, y1 + (y2 - y0) / 6, x2 - (x3 - x1) / 6, y2 - (y3 - y1) / 6, x2, y2);
        }
        return p;
    }
}
