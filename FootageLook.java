import java.awt.image.BufferedImage;

/**
 * The "found footage" camera look applied to every rendered frame as it is loaded:
 *
 *  - lens fringing (chromatic aberration): red and blue are pulled apart toward the edges of
 *    the picture, as a cheap wide lens does
 *  - a harsher grade: more contrast, slightly drained colour, cold blue in the shadows
 *
 * It runs once per decoded frame (on the decoder thread), so drawing costs nothing extra.
 * ScenePlayer adds the moving parts on top: scanlines, grain and glitches.
 */
public final class FootageLook {
    /** Turn the whole look off here (frames are then shown exactly as rendered). */
    public static final boolean ENABLED = true;

    /** How far red/blue are pushed apart at the picture's edge, as a fraction of its half-size. */
    private static final double FRINGE = 0.0055;
    private static final int[] CURVE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            double x = i / 255.0;
            double s = x * x * (3 - 2 * x);          // an S-curve: darker darks, brighter brights
            double y = x + (s - x) * 0.55;
            CURVE[i] = (int) Math.round(Math.max(0, Math.min(1, y)) * 255);
        }
    }

    private FootageLook() {}

    /** Returns a graded copy of src (TYPE_INT_RGB). */
    public static BufferedImage apply(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        // Read the pixels through a throwaway int image (fast for JPEGs, which decode as bytes).
        BufferedImage tmp = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D tg = tmp.createGraphics();
        tg.drawImage(src, 0, 0, null);
        tg.dispose();
        int[] in = ((java.awt.image.DataBufferInt) tmp.getRaster().getDataBuffer()).getData();
        int[] out = new int[in.length];
        double cx = (w - 1) / 2.0, cy = (h - 1) / 2.0;
        // Where red and blue are sampled from for each column and row
        // (red nearer the centre, blue farther out).
        int[] rx = new int[w], bx = new int[w], ry = new int[h], by = new int[h];
        for (int x = 0; x < w; x++) {
            rx[x] = clamp((int) Math.round(cx + (x - cx) * (1 - FRINGE)), w);
            bx[x] = clamp((int) Math.round(cx + (x - cx) * (1 + FRINGE)), w);
        }
        for (int y = 0; y < h; y++) {
            ry[y] = clamp((int) Math.round(cy + (y - cy) * (1 - FRINGE)), h) * w;
            by[y] = clamp((int) Math.round(cy + (y - cy) * (1 + FRINGE)), h) * w;
        }
        for (int y = 0; y < h; y++) {
            int row = y * w, rrow = ry[y], brow = by[y];
            for (int x = 0; x < w; x++) {
                int r = (in[rrow + rx[x]] >> 16) & 255;
                int g = (in[row + x] >> 8) & 255;
                int b = in[brow + bx[x]] & 255;
                // Drain the colour a little, then add contrast.
                int lum = (r * 77 + g * 150 + b * 29) >> 8;
                r = CURVE[lum + (((r - lum) * 205) >> 8)];
                g = CURVE[lum + (((g - lum) * 205) >> 8)];
                b = CURVE[lum + (((b - lum) * 205) >> 8)];
                // Cold shadows: the darker the pixel, the more blue it leans.
                int shade = 255 - lum;
                r -= (shade * 10) >> 8;
                b += (shade * 14) >> 8;
                out[row + x] = ((r < 0 ? 0 : r) << 16) | (g << 8) | (b > 255 ? 255 : b);
            }
        }
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        dst.setRGB(0, 0, w, h, out, 0, w);
        return dst;
    }

    private static int clamp(int v, int size) {
        return v < 0 ? 0 : v >= size ? size - 1 : v;
    }

    private static int clamp8(int v) {
        return v < 0 ? 0 : v > 255 ? 255 : v;
    }
}
