import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * The shading engine behind the pixel art: turns a flat shape into a lit, rounded-looking pixel
 * form, the way a pixel artist shades by hand.
 *
 *  - the shape is treated as a soft dome (its height rises from the edge inward over `round` pixels),
 *  - lit by the lamp (from above, slightly in front and to the left) with a little ambient light,
 *  - the light is mapped onto a short colour ramp whose shadows lean cool and highlights lean warm,
 *  - neighbouring tones are blended with ordered (Bayer) dithering,
 *  - and the shape gets a dark outline in its own darkest colour.
 *
 * Use: Shade.part(img, shape, ramp, round) paints onto a sprite image. Pure Java2D.
 */
public final class Shade {
    private Shade() {}

    private static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};
    /** Where the light comes from: left-ish, from above, toward the viewer. */
    private static final double LX = -0.35, LY = -0.75, LZ = 0.56;

    /** Deep shadows are pulled toward this purple, highlights toward this warm lamp orange (the two-light look). */
    public static final Color SHADOW_TINT = new Color(46, 26, 74), LIGHT_TINT = new Color(255, 170, 96);

    /** A colour ramp from deep shadow to highlight around a base colour: shadows purple, lights lamp-orange. */
    public static Color[] ramp(Color base, int n) {
        Color[] r = new Color[n];
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 0.5 : i / (double) (n - 1);              // 0 = shadow, 1 = light
            Color c;
            if (t < 0.5) {
                double k = (0.5 - t) * 2;                                 // how deep in shadow
                c = mix(scale(base, 1 - 0.55 * k), SHADOW_TINT, 0.55 * k);
            } else {
                double k = (t - 0.5) * 2;                                 // how far into the light
                c = mix(scale(base, 1 + 0.25 * k), LIGHT_TINT, 0.38 * k);
            }
            r[i] = c;
        }
        return r;
    }

    public static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private static Color scale(Color c, double k) {
        return new Color(Math.min(255, (int) (c.getRed() * k)), Math.min(255, (int) (c.getGreen() * k)), Math.min(255, (int) (c.getBlue() * k)));
    }

    /** Paints a shaded shape. round: how many pixels the edge takes to curve over; 0 = flat (lit evenly). */
    public static void part(BufferedImage img, Shape shape, Color[] ramp, double round) {
        part(img, shape, ramp, round, false, 0.2);
    }

    public static void part(BufferedImage img, Shape shape, Color[] ramp, double round, boolean outline, double ambient) {
        int w = img.getWidth(), h = img.getHeight();
        Rectangle b = shape.getBounds().intersection(new Rectangle(-2, -2, w + 4, h + 4));
        if (b.isEmpty()) return;
        int x0 = Math.max(0, b.x - 1), y0 = Math.max(0, b.y - 1);
        int x1 = Math.min(w, b.x + b.width + 2), y1 = Math.min(h, b.y + b.height + 2);
        int bw = x1 - x0, bh = y1 - y0;
        if (bw <= 0 || bh <= 0) return;
        // Rasterise the shape into a mask (hard pixels).
        BufferedImage m = new BufferedImage(bw, bh, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D mg = m.createGraphics();
        mg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        mg.setColor(Color.WHITE);
        mg.translate(-x0, -y0);
        mg.fill(shape);
        mg.dispose();
        boolean[] in = new boolean[bw * bh];
        byte[] data = ((java.awt.image.DataBufferByte) m.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < in.length; i++) in[i] = (data[i] & 0xff) > 127;

        // Distance from the edge (chamfer 3-4), then a dome-shaped height.
        int big = 1 << 20;
        int[] d = new int[bw * bh];
        for (int i = 0; i < d.length; i++) d[i] = in[i] ? big : 0;
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                int i = y * bw + x;
                if (d[i] == 0) continue;
                int v = d[i];
                if (x > 0) v = Math.min(v, d[i - 1] + 3);
                if (y > 0) v = Math.min(v, d[i - bw] + 3);
                if (x > 0 && y > 0) v = Math.min(v, d[i - bw - 1] + 4);
                if (x < bw - 1 && y > 0) v = Math.min(v, d[i - bw + 1] + 4);
                if (x == 0 || y == 0) v = Math.min(v, 3);
                d[i] = v;
            }
        }
        for (int y = bh - 1; y >= 0; y--) {
            for (int x = bw - 1; x >= 0; x--) {
                int i = y * bw + x;
                if (d[i] == 0) continue;
                int v = d[i];
                if (x < bw - 1) v = Math.min(v, d[i + 1] + 3);
                if (y < bh - 1) v = Math.min(v, d[i + bw] + 3);
                if (x < bw - 1 && y < bh - 1) v = Math.min(v, d[i + bw + 1] + 4);
                if (x > 0 && y < bh - 1) v = Math.min(v, d[i + bw - 1] + 4);
                if (x == bw - 1 || y == bh - 1) v = Math.min(v, 3);
                d[i] = v;
            }
        }
        double r = Math.max(0.5, round);
        double[] hgt = new double[bw * bh];
        for (int i = 0; i < d.length; i++) {
            if (!in[i]) continue;
            double e = Math.min(1.0, d[i] / 3.0 / r);
            hgt[i] = round <= 0 ? 1 : Math.sqrt(Math.max(0, 1 - (1 - e) * (1 - e)));
        }

        // Light each pixel and pick a tone, dithering between neighbours.
        int n = ramp.length;
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                int i = y * bw + x;
                if (!in[i]) continue;
                double gx = 0, gy = 0;
                if (round > 0) {
                    double hl = x > 0 && in[i - 1] ? hgt[i - 1] : 0, hr = x < bw - 1 && in[i + 1] ? hgt[i + 1] : 0;
                    double hu = y > 0 && in[i - bw] ? hgt[i - bw] : 0, hd = y < bh - 1 && in[i + bw] ? hgt[i + bw] : 0;
                    gx = (hr - hl) * r * 0.5;
                    gy = (hd - hu) * r * 0.5;
                }
                double nl = Math.sqrt(gx * gx + gy * gy + 1);
                double lam = (-gx * LX - gy * LY + LZ) / nl;
                double fall = 1.0 - 0.18 * (y + y0 - b.y) / Math.max(1.0, b.height);   // the lamp is above
                double v = (ambient + (1 - ambient) * Math.max(0, lam)) * fall;
                double idx = v * (n - 1) + (BAYER[(y + y0) & 3][(x + x0) & 3] / 16.0 - 0.47);
                int k = (int) Math.max(0, Math.min(n - 1, Math.round(idx)));
                Color c = ramp[k];
                // A warm rim where the surface turns away to the right: the lamp's glow catching the edge.
                double rim = (gx * 0.9 - gy * 0.2) / nl;
                if (round > 0 && rim > 0.62 && hgt[i] < 0.75) c = mix(ramp[n - 1], LIGHT_TINT, 0.35);
                img.setRGB(x + x0, y + y0, c.getRGB());
            }
        }
        if (!outline) return;
        int ink = mix(ramp[0], SHADOW_TINT, 0.6).darker().getRGB();
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                int i = y * bw + x;
                if (in[i]) continue;
                boolean edge = (x > 0 && in[i - 1]) || (x < bw - 1 && in[i + 1]) || (y > 0 && in[i - bw]) || (y < bh - 1 && in[i + bw]);
                if (edge) img.setRGB(x + x0, y + y0, ink | 0xff000000);
            }
        }
    }

    /** A thick shaded stroke along a path (hoses, ropes, beaks' ridges). */
    public static void stroke(BufferedImage img, Shape path, float width, Color[] ramp) {
        Shape s = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(path);
        part(img, s, ramp, width / 2.0);
    }

    /** The same shape moved by (dx, dy). */
    public static Shape move(Shape s, double dx, double dy) {
        return AffineTransform.getTranslateInstance(dx, dy).createTransformedShape(s);
    }

    /** A single hard pixel. */
    public static void px(BufferedImage img, int x, int y, Color c) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, c.getRGB());
    }

    public static void rect(BufferedImage img, int x, int y, int w, int h, Color c) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) px(img, xx, yy, c);
    }
}
