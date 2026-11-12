import java.awt.*;
import java.awt.geom.*;

/**
 * The cellar seen from your seat, in the storybook style (see Ink): a brick wall, the opponent's
 * high-backed chair, the hanging lamp and its cone of light, the round table, and the board in
 * perspective with its pieces.
 *
 * The board is a real perspective projection, so cellAt() can turn a mouse click back into a square.
 * Rows and columns follow GameState: row 0 is the far (Black) back rank, column 0 is file a (your left).
 */
public class Cellar {
    // The board as a flat square on the table, eight units wide, seen by a camera at the player's eyes.
    private static final double EYE_H = 8.6, EYE_BACK = 5.4;      // camera height above, and distance before, the board
    private static final double LOOK_Z = 4.3;                     // the point on the board the camera looks at
    private double focal, cx, cy;
    private double fy, fz, uy, uz;                                // camera forward and up (x is always across)
    private int w, h;

    /** Lays out the projection for a picture of w x h, with the board's centre at (bx, by) on screen, bw wide at its middle. */
    public void layout(int w, int h, double bx, double by, double bw) {
        this.w = w;
        this.h = h;
        double dy = -EYE_H, dz = LOOK_Z + EYE_BACK, len = Math.hypot(dy, dz);
        fy = dy / len;
        fz = dz / len;
        uy = fz;                                                    // up is forward turned 90 degrees
        uz = -fy;
        cx = bx;
        cy = by;
        // Choose the focal length so the board is bw pixels wide across its middle row.
        double depth = depthOf(0, 4);
        focal = bw / 8.0 * depth;
        // ...and shift so the board's centre lands at (bx, by).
        cy = by - (project(0, 4).getY() - cy);
    }

    private double depthOf(double x, double z) {
        double py = -EYE_H, pz = z + EYE_BACK;
        return py * fy + pz * fz;
    }

    /** A point on the board plane (x across from -4 to 4, z from 0 at your edge to 8 at his) on screen. */
    public Point2D project(double x, double z) {
        double py = -EYE_H, pz = z + EYE_BACK;
        double d = py * fy + pz * fz;
        double up = py * uy + pz * uz;
        return new Point2D.Double(cx + focal * x / d, cy - focal * up / d);
    }

    /** How much bigger things are drawn at board point z than at the middle (for piece sizes). */
    public double scaleAt(double z) {
        return depthOf(0, 4) / depthOf(0, z);
    }

    /** The centre of a square on screen. */
    public Point2D square(int row, int col) {
        return project(col - 3.5, 7.5 - row);
    }

    /** The square under a screen point, or null. */
    public int[] cellAt(double sx, double sy) {
        // Walk the ray through the pixel down to the board plane.
        double rx = (sx - cx) / focal, ry = -(sy - cy) / focal;
        // Direction in world (y, z) from the camera: forward + ry * up; x component rx.
        double dirY = fy + ry * uy, dirZ = fz + ry * uz;
        if (dirY >= 0) return null;
        double t = EYE_H / -dirY;
        double z = -EYE_BACK + dirZ * t, x = rx * t;
        int col = (int) Math.floor(x + 4), row = 7 - (int) Math.floor(z);
        if (col < 0 || col > 7 || row < 0 || row > 7 || z < 0) return null;
        return new int[]{row, col};
    }

    /** The outline of a square on screen (for highlights). */
    public Shape squareShape(int row, int col, double inset) {
        double x0 = col - 4 + inset, x1 = col - 3 - inset, z0 = 7 - row + inset, z1 = 8 - row - inset;
        Path2D p = new Path2D.Double();
        Point2D a = project(x0, z0), b = project(x1, z0), c = project(x1, z1), d = project(x0, z1);
        p.moveTo(a.getX(), a.getY());
        p.lineTo(b.getX(), b.getY());
        p.lineTo(c.getX(), c.getY());
        p.lineTo(d.getX(), d.getY());
        p.closePath();
        return p;
    }

    // ─── The room ───────────────────────────────────────────────
    /** Wall, floor, the chair behind him and the hanging lamp, with the lamp's light. lampY: the bulb's height. */
    public void room(Graphics2D g, double t, double flicker) {
        room(g, t, flicker, true);
    }

    /** As room(), with or without his chair (the dealers stand elsewhere in the room). */
    public void room(Graphics2D g, double t, double flicker, boolean chair) {
        Ink.smooth(g);
        g.setColor(new Color(18, 15, 17));
        g.fillRect(0, 0, w, h);
        // The back wall: rough bricks drawn as ink, fading into the dark.
        g.setPaint(new GradientPaint(0, 0, new Color(46, 38, 36), 0, (float) (h * 0.62), new Color(30, 25, 25)));
        g.fillRect(0, 0, w, (int) (h * 0.62));
        int brickH = Math.max(14, h / 30), brickW = brickH * 3;
        g.setStroke(new BasicStroke(1.4f));
        for (int row = 0; row * brickH < h * 0.62; row++) {
            int y = row * brickH;
            g.setColor(new Color(16, 12, 13, 150));
            g.draw(new Line2D.Double(0, y, w, y + Ink.noise(row * 0.7, 3) * 2));
            int off = (row % 2) * brickW / 2;
            for (int x = -off; x < w; x += brickW) {
                double jx = x + Ink.noise(row * 3.1 + x * 0.01, 5) * 4;
                g.draw(new Line2D.Double(jx, y, jx, y + brickH));
            }
        }
        g.setColor(new Color(24, 20, 21));
        g.fillRect(0, (int) (h * 0.62), w, h);                       // the floor, mostly lost in shadow

        if (!chair) return;
        // His high-backed chair, behind where he sits.
        double chairX = w / 2.0, chairTop = h * 0.08, chairW = w * 0.2, seatY = h * 0.6;
        Path2D back = new Path2D.Double();
        back.moveTo(chairX - chairW / 2, seatY);
        back.lineTo(chairX - chairW / 2, chairTop + chairW * 0.35);
        back.lineTo(chairX - chairW * 0.18, chairTop + chairW * 0.12);
        back.lineTo(chairX, chairTop);
        back.lineTo(chairX + chairW * 0.18, chairTop + chairW * 0.12);
        back.lineTo(chairX + chairW / 2, chairTop + chairW * 0.35);
        back.lineTo(chairX + chairW / 2, seatY);
        back.closePath();
        Ink.draw(g, back, Ink.shade(back, new Color(78, 46, 32), new Color(40, 24, 18)), 3.5f, 77);
        Shape arch = Ink.blob(chairX - chairW * 0.3, seatY - chairW * 0.2, chairX - chairW * 0.3, chairTop + chairW * 0.45,
            chairX, chairTop + chairW * 0.2, chairX + chairW * 0.3, chairTop + chairW * 0.45, chairX + chairW * 0.3, seatY - chairW * 0.2);
        Ink.draw(g, arch, new Color(30, 18, 14), 2.5f, 78);
        for (int side = -1; side <= 1; side += 2) {                   // finials
            double fx = chairX + side * chairW / 2;
            Ink.draw(g, Ink.oval(fx, chairTop + chairW * 0.32, chairW * 0.05, chairW * 0.07), Ink.WOOD, 2.5f, 79 + side);
        }
    }

    /** The lamp hanging over the table, its cone of light, and the darkness around. */
    public void lamp(Graphics2D g, double t, double flicker) {
        double lx = w / 2.0, ly = h * 0.055;
        Ink.line(g, lx, 0, lx, ly - h * 0.04, 2.5f, 90);               // the flex
        Path2D shade = new Path2D.Double();
        shade.moveTo(lx - w * 0.012, ly - h * 0.045);
        shade.lineTo(lx + w * 0.012, ly - h * 0.045);
        shade.lineTo(lx + w * 0.055, ly + h * 0.01);
        shade.lineTo(lx - w * 0.055, ly + h * 0.01);
        shade.closePath();
        Ink.draw(g, shade, Ink.shade(shade, new Color(70, 76, 66), new Color(34, 38, 32)), 3f, 91);
        Ink.glow(g, lx, ly + h * 0.015, w * 0.025, Ink.LAMP, (int) (255 * flicker));
        // The cone of light down onto the table.
        Path2D cone = new Path2D.Double();
        cone.moveTo(lx - w * 0.05, ly + h * 0.01);
        cone.lineTo(lx + w * 0.05, ly + h * 0.01);
        cone.lineTo(lx + w * 0.36, h * 0.95);
        cone.lineTo(lx - w * 0.36, h * 0.95);
        cone.closePath();
        g.setPaint(new GradientPaint((float) lx, (float) ly, new Color(255, 214, 150, (int) (40 * flicker)),
            (float) lx, (float) (h * 0.88), new Color(255, 214, 150, 0)));
        g.fill(cone);
    }

    /** The round table top in front of you. */
    public void table(Graphics2D g) {
        Point2D far = project(0, 9.3), near = project(0, -1.4);
        double rx = w * 0.46, top = far.getY(), bottom = Math.max(near.getY(), h * 1.05);
        Shape top2 = new Ellipse2D.Double(w / 2.0 - rx, top, rx * 2, (bottom - top) * 2);
        Ink.draw(g, top2, Ink.shade(top2, new Color(120, 72, 44), Ink.WOOD_DARK), 4f, 95);
        Shape old = g.getClip();
        g.clip(top2);
        for (int i = 0; i < 9; i++) {                                 // wood grain
            double y = top + (i + 0.5) * (bottom - top) / 9;
            Ink.line(g, Ink.curve(w * 0.05, y + 6, w * 0.3, y - 4, w * 0.6, y + 5, w * 0.95, y - 3), 1.3f, new Color(40, 22, 14, 120), 96 + i);
        }
        g.setClip(old);
    }

    /** The board: its frame and squares. */
    public void board(Graphics2D g) {
        Path2D frame = new Path2D.Double();
        Point2D a = project(-4.45, -0.45), b = project(4.45, -0.45), c = project(4.45, 8.45), d = project(-4.45, 8.45);
        frame.moveTo(a.getX(), a.getY());
        frame.lineTo(b.getX(), b.getY());
        frame.lineTo(c.getX(), c.getY());
        frame.lineTo(d.getX(), d.getY());
        frame.closePath();
        Ink.draw(g, frame, new Color(52, 34, 26), 3.5f, 100);
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                boolean dark = (row + col) % 2 == 1;
                Shape sq = squareShape(row, col, 0);
                g.setColor(dark ? new Color(70, 44, 34) : new Color(206, 186, 150));
                g.fill(sq);
                if (dark) Ink.hatch(g, sq, 5, 45, 50, row * 8 + col);
            }
        }
        Ink.line(g, frame, 3f, Ink.INK, 101);
        Point2D in0 = project(-4, 0), in1 = project(4, 0), in2 = project(4, 8), in3 = project(-4, 8);
        Path2D inner = new Path2D.Double();
        inner.moveTo(in0.getX(), in0.getY());
        inner.lineTo(in1.getX(), in1.getY());
        inner.lineTo(in2.getX(), in2.getY());
        inner.lineTo(in3.getX(), in3.getY());
        inner.closePath();
        Ink.line(g, inner, 2.2f, Ink.INK, 102);
    }

    // ─── Pieces ─────────────────────────────────────────────────
    /** Draws every piece of a board (codes like "wK", "bP", "" for empty), back rows first. */
    public void pieces(Graphics2D g, String[][] board) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                String code = board[row][col];
                if (code != null && !code.isEmpty()) piece(g, code, row, col, 0);
            }
        }
    }

    /** One piece on a square; lift raises it (for a piece in mid-move), in board units. */
    public void piece(Graphics2D g, String code, int row, int col, double lift) {
        Point2D p = square(row, col);
        double s = focal / depthOf(0, 7.5 - row) * 0.5;               // half a square, at this depth
        piece(g, code, p.getX(), p.getY() - lift * s * 2, s, row * 8 + col);
    }

    /** A piece standing at (x, y) (its base), s pixels per half square. */
    public static void piece(Graphics2D g, String code, double x, double y, double s, int seed) {
        boolean white = code.charAt(0) == 'w';
        char kind = code.charAt(1);
        Color body = white ? Ink.BONE : new Color(46, 38, 40);
        Color shadeC = white ? Ink.BONE_SHADE : new Color(24, 20, 22);
        g.setColor(new Color(0, 0, 0, 90));                            // its shadow on the board
        g.fill(Ink.oval(x + s * 0.12, y + s * 0.06, s * 0.62, s * 0.22));
        Path2D shape = silhouette(kind, x, y, s);
        Ink.draw(g, shape, new GradientPaint((float) (x - s * 0.5), 0, body, (float) (x + s * 0.6), 0, shadeC), (float) Math.max(1.6, s * 0.09), seed);
        if (!white) {                                                  // a rim of lamp light on the dark pieces
            Shape old = g.getClip();
            g.clip(shape);
            g.setColor(new Color(255, 214, 150, 70));
            g.setStroke(new BasicStroke((float) (s * 0.16)));
            g.draw(new Line2D.Double(x - s * 0.35, y - s * 3, x - s * 0.35, y));
            g.setClip(old);
        }
    }

    /** The outline of a piece: a turned base and stem, and its head (cross, crown, battlement, mitre, horse, ball). */
    private static Path2D silhouette(char kind, double x, double y, double s) {
        double hgt = kind == 'K' ? 2.5 : kind == 'Q' ? 2.3 : kind == 'B' ? 2.0 : kind == 'N' ? 1.85 : kind == 'R' ? 1.6 : 1.25;
        double top = y - hgt * s;
        Path2D p = new Path2D.Double();
        double baseW = (kind == 'P' ? 0.5 : 0.62) * s, neck = (kind == 'P' ? 0.16 : 0.2) * s;
        p.moveTo(x - baseW, y);
        p.lineTo(x - baseW, y - 0.18 * s);
        p.curveTo(x - baseW * 0.8, y - 0.32 * s, x - neck * 1.5, y - 0.4 * s, x - neck, y - hgt * 0.45 * s);
        switch (kind) {
            case 'K': case 'Q': {
                p.curveTo(x - neck, top + 0.9 * s, x - 0.42 * s, top + 0.75 * s, x - 0.36 * s, top + 0.6 * s);
                if (kind == 'Q') {                                     // a crown of points
                    double[] xs = {-0.36, -0.22, -0.12, 0, 0.12, 0.22, 0.36};
                    for (int i = 0; i < xs.length; i++) p.lineTo(x + xs[i] * s, top + (i % 2 == 0 ? 0.25 : 0.5) * s);
                    p.lineTo(x + 0.36 * s, top + 0.6 * s);
                } else {                                               // a cross
                    p.lineTo(x - 0.3 * s, top + 0.45 * s);
                    p.lineTo(x - 0.07 * s, top + 0.45 * s);
                    p.lineTo(x - 0.07 * s, top + 0.3 * s);
                    p.lineTo(x - 0.2 * s, top + 0.3 * s);
                    p.lineTo(x - 0.2 * s, top + 0.16 * s);
                    p.lineTo(x - 0.07 * s, top + 0.16 * s);
                    p.lineTo(x - 0.07 * s, top);
                    p.lineTo(x + 0.07 * s, top);
                    p.lineTo(x + 0.07 * s, top + 0.16 * s);
                    p.lineTo(x + 0.2 * s, top + 0.16 * s);
                    p.lineTo(x + 0.2 * s, top + 0.3 * s);
                    p.lineTo(x + 0.07 * s, top + 0.3 * s);
                    p.lineTo(x + 0.07 * s, top + 0.45 * s);
                    p.lineTo(x + 0.3 * s, top + 0.45 * s);
                    p.lineTo(x + 0.36 * s, top + 0.6 * s);
                }
                p.curveTo(x + 0.42 * s, top + 0.75 * s, x + neck, top + 0.9 * s, x + neck, y - hgt * 0.45 * s);
                break;
            }
            case 'R': {
                p.lineTo(x - 0.4 * s, top + 0.35 * s);
                p.lineTo(x - 0.4 * s, top);
                p.lineTo(x - 0.24 * s, top);
                p.lineTo(x - 0.24 * s, top + 0.14 * s);
                p.lineTo(x - 0.08 * s, top + 0.14 * s);
                p.lineTo(x - 0.08 * s, top);
                p.lineTo(x + 0.08 * s, top);
                p.lineTo(x + 0.08 * s, top + 0.14 * s);
                p.lineTo(x + 0.24 * s, top + 0.14 * s);
                p.lineTo(x + 0.24 * s, top);
                p.lineTo(x + 0.4 * s, top);
                p.lineTo(x + 0.4 * s, top + 0.35 * s);
                p.lineTo(x + neck, y - hgt * 0.45 * s);
                break;
            }
            case 'B': {
                p.curveTo(x - 0.34 * s, top + 0.7 * s, x - 0.3 * s, top + 0.25 * s, x, top + 0.05 * s);
                p.curveTo(x + 0.3 * s, top + 0.25 * s, x + 0.34 * s, top + 0.7 * s, x + neck, y - hgt * 0.45 * s);
                break;
            }
            case 'N': {                                                // a horse's head, facing left
                p.lineTo(x - 0.3 * s, top + 0.95 * s);
                p.curveTo(x - 0.55 * s, top + 0.85 * s, x - 0.6 * s, top + 0.6 * s, x - 0.42 * s, top + 0.45 * s);
                p.curveTo(x - 0.2 * s, top + 0.25 * s, x - 0.1 * s, top + 0.05 * s, x + 0.08 * s, top);
                p.lineTo(x + 0.14 * s, top + 0.12 * s);
                p.curveTo(x + 0.45 * s, top + 0.3 * s, x + 0.42 * s, top + 0.9 * s, x + neck * 1.3, y - hgt * 0.45 * s);
                break;
            }
            default: {                                                 // pawn: a ball on a collar
                p.lineTo(x - 0.22 * s, top + 0.55 * s);
                p.curveTo(x - 0.34 * s, top + 0.4 * s, x - 0.3 * s, top, x, top);
                p.curveTo(x + 0.3 * s, top, x + 0.34 * s, top + 0.4 * s, x + 0.22 * s, top + 0.55 * s);
                p.lineTo(x + neck, y - hgt * 0.45 * s);
                break;
            }
        }
        p.curveTo(x + neck * 1.5, y - 0.4 * s, x + baseW * 0.8, y - 0.32 * s, x + baseW, y - 0.18 * s);
        p.lineTo(x + baseW, y);
        p.closePath();
        return p;
    }
}
