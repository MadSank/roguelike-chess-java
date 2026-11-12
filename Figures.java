import java.awt.*;
import java.awt.geom.*;

/**
 * The masked people of the cellar, drawn in the storybook style (see Ink).
 *
 * Everyone wears a hooded robe and a mask real people wear, covering the whole face:
 *   GAS (gas mask), PLAGUE (plague doctor: beak, goggles, hat), HOCKEY (goalie mask), SACK (burlap sack),
 *   WELDING (welder's helmet), BAUTA (Venetian), FENCING (fencing mask), BALACLAVA.
 * Behind every eye hole there is only black, and a glint.
 *
 * Animation is a plain function of time: breathing, a slow sway and head tilt, plus a reaction
 * (think, capture, hurt, check, win, lose for opponents; yes, no for dealers) given with its progress 0..1.
 */
public final class Figures {
    private Figures() {}

    public enum Mask { GAS, PLAGUE, HOCKEY, SACK, WELDING, BAUTA, FENCING, BALACLAVA }

    /** What makes one person look different from another. */
    public static final class Look {
        public final Mask mask;
        public final Color robe, hood;
        public final boolean gloves, hoodUp;
        public final int seed;

        public Look(Mask mask, Color robe, Color hood, boolean gloves, boolean hoodUp, int seed) {
            this.mask = mask;
            this.robe = robe;
            this.hood = hood;
            this.gloves = gloves;
            this.hoodUp = hoodUp;
            this.seed = seed;
        }
    }

    /** The five opponents (one per level, cycling) and the three dealers. */
    public static final Look[] OPPONENTS = {
        new Look(Mask.GAS, new Color(70, 54, 44), new Color(52, 40, 33), false, true, 1),
        new Look(Mask.PLAGUE, new Color(92, 30, 30), new Color(30, 24, 22), true, false, 2),
        new Look(Mask.HOCKEY, new Color(112, 92, 64), new Color(84, 68, 48), false, true, 3),
        new Look(Mask.SACK, new Color(48, 56, 70), new Color(40, 46, 58), false, false, 4),
        new Look(Mask.WELDING, new Color(40, 34, 38), new Color(30, 26, 28), true, false, 5),
    };
    public static final Look CARD_DEALER = new Look(Mask.BAUTA, new Color(54, 42, 36), new Color(42, 32, 28), false, true, 6);
    public static final Look PIECE_DEALER = new Look(Mask.FENCING, new Color(86, 70, 52), new Color(66, 54, 40), true, false, 7);
    public static final Look RESELLER = new Look(Mask.BALACLAVA, new Color(62, 44, 72), new Color(48, 34, 56), false, true, 8);

    /** The pose at a moment: everything the drawing needs, worked out from time and reaction. */
    private static final class Pose {
        double breath, sway, tilt, lean, headDrop, headBack, shake, armLift;
    }

    private static Pose pose(double t, String reaction, double p, int seed) {
        Pose o = new Pose();
        double ph = seed * 1.7;
        o.breath = Math.sin(t * 2 * Math.PI / 4.0 + ph);
        o.sway = Math.sin(t * 2 * Math.PI / 7.0 + ph) * 0.035 + Math.sin(t * 2 * Math.PI / 2.9 + ph * 2) * 0.008;
        o.tilt = Math.sin(t * 2 * Math.PI / 5.3 + ph) * 0.05 + Math.sin(t * 2 * Math.PI / 1.9 + ph) * 0.012;
        if (reaction == null) return o;
        double bell = Math.sin(Math.PI * p);                          // 0 -> 1 -> 0
        double hold = smooth(0, 0.2, p) * (1 - smooth(0.8, 1, p));    // rises fast, holds, settles
        double snap = Math.sin(Math.PI * Math.sqrt(p));               // a flinch, peaking early
        switch (reaction) {
            case "think": o.headDrop = 0.14 * bell; o.tilt += 0.16 * bell; break;
            case "capture": o.lean = 0.06 * hold; o.tilt += 0.18 * hold; break;
            case "hurt": o.lean = -0.06 * snap; o.headBack = 0.16 * snap; o.shake = 0.03 * snap * Math.sin(p * 40); break;
            case "check": o.lean = 0.09 * hold; o.tilt -= 0.2 * hold; break;
            case "win": o.lean = -0.04 * hold; o.headBack = 0.2 * hold; o.shake = 0.025 * hold * Math.sin(p * 55); break;
            case "lose": o.lean = 0.05 * hold; o.headDrop = 0.38 * hold; o.tilt -= 0.1 * hold; break;
            case "yes": o.headDrop = 0.16 * bell * (0.5 - 0.5 * Math.cos(p * 4 * Math.PI)); break;
            case "no": o.tilt += 0.22 * bell * Math.sin(p * 6 * Math.PI); break;
            case "lunge": o.lean = 0.5 * smooth(0, 0.35, p); o.armLift = smooth(0.05, 0.4, p); o.shake = 0.02 * Math.sin(p * 70); break;
            default: break;
        }
        return o;
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    // ─── Seated at the table ─────────────────────────────────────
    /**
     * An opponent seated behind the table, facing you. (cx, tableY): the middle of the table's far edge,
     * where his forearms rest. s: size (about one head's height, in pixels).
     */
    public static void seated(Graphics2D g, Look look, double cx, double tableY, double s, double t, String reaction, double p) {
        Pose o = pose(t, reaction, p, look.seed);
        AffineTransform saved = g.getTransform();
        // Leaning in (or back) is a scale about the point where he meets the table.
        double k = 1 + o.lean;
        g.translate(cx + o.sway * s + o.shake * s, tableY);
        g.scale(k, k);
        double rise = -0.035 * s * (1 + o.breath) / 2;               // breathing lifts the shoulders

        // The robe: shoulders down to the table, folds as ink lines.
        double sh = -1.95 * s + rise;
        Path2D robe = Ink.blob(-1.55 * s, 0.2 * s, -1.42 * s, sh + 0.55 * s, -0.95 * s, sh + 0.05 * s, -0.3 * s, sh - 0.1 * s,
            0.3 * s, sh - 0.1 * s, 0.95 * s, sh + 0.05 * s, 1.42 * s, sh + 0.55 * s, 1.55 * s, 0.2 * s);
        Ink.draw(g, robe, Ink.shade(robe, look.robe, look.robe.darker()), (float) (0.06 * s), look.seed);
        Ink.hatch(g, robe, 0.13 * s, 70, 40, look.seed);
        for (int i = -2; i <= 2; i++) {
            if (i == 0) continue;
            double x = i * 0.42 * s;
            Ink.line(g, Ink.curve(x, sh + 0.35 * s, x * 1.08, sh + 1.1 * s, x * 1.12, 0.1 * s), (float) (0.025 * s),
                new Color(0, 0, 0, 120), look.seed + i);
        }

        // Arms down to the hands on the table.
        for (int side = -1; side <= 1; side += 2) {
            double lift = o.armLift;
            double hx = side * (0.72 + 0.25 * lift) * s, hy = 0.12 * s - lift * 0.9 * s;
            Path2D sleeve = Ink.blob(side * 1.25 * s, sh + 0.45 * s, side * 1.5 * s, sh + 1.2 * s, hx + side * 0.32 * s, hy - 0.15 * s,
                hx - side * 0.12 * s, hy - 0.22 * s, side * 0.98 * s, sh + 0.9 * s);
            Ink.draw(g, sleeve, look.robe.darker(), (float) (0.055 * s), look.seed + 10 + side);
            hand(g, look, hx, hy + 0.02 * s, s * (1 + 0.3 * lift), side, look.seed + 20 + side);
        }

        // Neck, hood and head with its mask.
        double hy = sh - 0.62 * s + o.headDrop * s - o.headBack * 0.3 * s;
        g.translate(0, hy);
        g.rotate(o.tilt);
        g.scale(1, 1 - o.headBack * 0.25);
        head(g, look, s);
        g.setTransform(saved);
    }

    private static void hand(Graphics2D g, Look look, double x, double y, double s, int side, int seed) {
        Color skin = look.gloves ? new Color(34, 28, 26) : Ink.BONE;
        Path2D palm = Ink.blob(x - 0.2 * s, y - 0.1 * s, x + 0.2 * s, y - 0.12 * s, x + 0.24 * s, y + 0.06 * s,
            x - 0.24 * s, y + 0.07 * s);
        Ink.draw(g, palm, skin, (float) (0.04 * s), seed);
        for (int f = 0; f < 4; f++) {                                    // long fingers, splayed on the wood
            double fx = x + (-0.17 + f * 0.11) * s;
            Path2D finger = Ink.curve(fx, y + 0.03 * s, fx + (f - 1.5) * 0.02 * s, y + 0.2 * s, fx + (f - 1.5) * 0.035 * s, y + 0.3 * s);
            Ink.line(g, finger, (float) (0.085 * s), Ink.INK, seed + f);
            Ink.line(g, finger, (float) (0.05 * s), skin, seed + f);
        }
        if (!look.gloves) Ink.hatch(g, palm, 0.06 * s, 30, 35, seed);
    }

    /** The head with hood and mask, centred at the origin; s: about one head's height. */
    public static void head(Graphics2D g, Look look, double s) {
        double r = 0.42 * s;                                              // the head's half-width
        if (look.hoodUp) {                                                // the hood behind and around the head
            Path2D hood = Ink.blob(-0.78 * s, 0.75 * s, -0.75 * s, -0.25 * s, -0.4 * s, -0.78 * s, 0, -0.9 * s,
                0.4 * s, -0.78 * s, 0.75 * s, -0.25 * s, 0.78 * s, 0.75 * s);
            Ink.draw(g, hood, Ink.shade(hood, look.hood.brighter(), look.hood.darker()), (float) (0.055 * s), look.seed + 30);
            Ink.draw(g, Ink.oval(0, 0.05 * s, 0.56 * s, 0.68 * s), Ink.INK, 0, 0);   // the dark inside of the hood
        } else {
            Ink.draw(g, new Rectangle2D.Double(-0.17 * s, 0.25 * s, 0.34 * s, 0.5 * s), Ink.CHARCOAL, (float) (0.04 * s), look.seed + 31);
        }
        switch (look.mask) {
            case GAS: gas(g, s, r, look.seed); break;
            case PLAGUE: plague(g, s, r, look.seed); break;
            case HOCKEY: hockey(g, s, r, look.seed); break;
            case SACK: sack(g, s, r, look.seed); break;
            case WELDING: welding(g, s, r, look.seed); break;
            case BAUTA: bauta(g, s, r, look.seed); break;
            case FENCING: fencing(g, s, r, look.seed); break;
            case BALACLAVA: balaclava(g, s, r, look.seed); break;
            default: break;
        }
    }

    /** A black eye hole with a faint glint, so something is looking back. */
    private static void eye(Graphics2D g, Shape hole, double gx, double gy, double size) {
        Ink.draw(g, hole, Ink.INK, 0, 0);
        int glint = 120 + (int) (60 * Math.sin(System.currentTimeMillis() / 900.0));
        g.setColor(new Color(240, 228, 200, Math.max(0, glint)));
        g.fill(Ink.oval(gx, gy, size, size));
    }

    // ─── The masks ──────────────────────────────────────────────
    private static void gas(Graphics2D g, double s, double r, int seed) {
        Path2D shell = Ink.blob(-0.5 * s, 0.1 * s, -0.46 * s, -0.42 * s, 0, -0.62 * s, 0.46 * s, -0.42 * s, 0.5 * s, 0.1 * s,
            0.3 * s, 0.52 * s, 0, 0.6 * s, -0.3 * s, 0.52 * s);
        Ink.draw(g, shell, Ink.shade(shell, new Color(96, 88, 72), new Color(52, 48, 40)), (float) (0.06 * s), seed);
        Ink.hatch(g, shell, 0.07 * s, 120, 45, seed);
        for (int side = -1; side <= 1; side += 2) {                       // round goggles, brass rims
            double ex = side * 0.21 * s, ey = -0.1 * s;
            Ink.draw(g, Ink.oval(ex, ey, 0.17 * s, 0.17 * s), Ink.BRASS, (float) (0.05 * s), seed + side);
            Shape lens = Ink.oval(ex, ey, 0.12 * s, 0.12 * s);
            eye(g, lens, ex - 0.06 * s, ey - 0.06 * s, 0.035 * s);
        }
        Shape can = Ink.oval(0, 0.33 * s, 0.17 * s, 0.15 * s);            // the filter snout
        Ink.draw(g, can, new Color(70, 66, 58), (float) (0.05 * s), seed + 5);
        for (int i = -2; i <= 2; i++) Ink.line(g, i * 0.06 * s, 0.22 * s, i * 0.065 * s, 0.45 * s, (float) (0.025 * s), seed + 9 + i);
        Path2D hose = Ink.curve(0, 0.47 * s, 0.05 * s, 0.75 * s, 0.25 * s, 1.0 * s, 0.35 * s, 1.35 * s);
        Ink.line(g, hose, (float) (0.17 * s), Ink.INK, seed + 3);
        Ink.line(g, hose, (float) (0.12 * s), new Color(64, 60, 52), seed + 3);
        for (int i = 0; i < 6; i++) {                                    // the ribs of the hose
            double t = i / 6.0;
            double x = 0.05 * s + 0.3 * s * t, y = 0.55 * s + 0.75 * s * t;
            Ink.line(g, x - 0.07 * s, y, x + 0.07 * s, y + 0.02 * s, (float) (0.02 * s), seed + 40 + i);
        }
    }

    private static void plague(Graphics2D g, double s, double r, int seed) {
        Path2D hood = Ink.blob(-0.52 * s, 0.6 * s, -0.5 * s, -0.25 * s, 0, -0.55 * s, 0.5 * s, -0.25 * s, 0.52 * s, 0.6 * s);
        Ink.draw(g, hood, Ink.shade(hood, Ink.LEATHER, new Color(36, 24, 18)), (float) (0.055 * s), seed);
        for (int side = -1; side <= 1; side += 2) {
            double ex = side * 0.2 * s, ey = -0.08 * s;
            Ink.draw(g, Ink.oval(ex, ey, 0.13 * s, 0.13 * s), Ink.BRASS, (float) (0.045 * s), seed + side);
            eye(g, Ink.oval(ex, ey, 0.09 * s, 0.09 * s), ex - 0.04 * s, ey - 0.045 * s, 0.03 * s);
        }
        // The beak: long, curving down past the chin.
        Path2D beak = new Path2D.Double();
        beak.moveTo(-0.17 * s, 0.05 * s);
        beak.curveTo(-0.15 * s, 0.4 * s, -0.05 * s, 0.85 * s, 0.07 * s, 1.18 * s);
        beak.curveTo(0.12 * s, 0.8 * s, 0.2 * s, 0.4 * s, 0.17 * s, 0.05 * s);
        beak.closePath();
        Ink.draw(g, beak, Ink.shade(beak, new Color(98, 70, 50), new Color(40, 28, 20)), (float) (0.05 * s), seed + 4);
        Ink.line(g, Ink.curve(0, 0.1 * s, 0.03 * s, 0.6 * s, 0.07 * s, 1.1 * s), (float) (0.02 * s), Ink.INK, seed + 6);
        Ink.hatch(g, beak, 0.05 * s, 60, 60, seed + 7);
        // The hat: a wide flat brim and a low crown.
        Shape crown = new RoundRectangle2D.Double(-0.36 * s, -0.95 * s, 0.72 * s, 0.42 * s, 0.1 * s, 0.1 * s);
        Ink.draw(g, crown, Ink.CHARCOAL, (float) (0.055 * s), seed + 8);
        Ink.draw(g, new Rectangle2D.Double(-0.36 * s, -0.63 * s, 0.72 * s, 0.08 * s), Ink.BLOOD.darker(), (float) (0.03 * s), seed + 10);
        Ink.draw(g, Ink.oval(0, -0.53 * s, 0.95 * s, 0.12 * s), Ink.INK, (float) (0.05 * s), seed + 9);
    }

    private static void hockey(Graphics2D g, double s, double r, int seed) {
        Shape shell = Ink.blob(-0.4 * s, -0.1 * s, -0.32 * s, -0.5 * s, 0, -0.6 * s, 0.32 * s, -0.5 * s, 0.4 * s, -0.1 * s,
            0.3 * s, 0.45 * s, 0, 0.6 * s, -0.3 * s, 0.45 * s);
        Ink.draw(g, shell, Ink.shade(shell, Ink.PAPER, Ink.BONE_SHADE), (float) (0.055 * s), seed);
        for (int side = -1; side <= 1; side += 2) {
            Path2D hole = Ink.blob(side * 0.08 * s, -0.12 * s, side * 0.2 * s, -0.2 * s, side * 0.3 * s, -0.12 * s, side * 0.19 * s, -0.04 * s);
            eye(g, hole, side * 0.19 * s - 0.02 * s, -0.15 * s, 0.03 * s);
        }
        double[][] dots = {{0, -0.42}, {-0.1, -0.35}, {0.1, -0.35}, {-0.16, 0.12}, {0.16, 0.12}, {-0.22, 0.25}, {0.22, 0.25},
            {-0.09, 0.24}, {0.09, 0.24}, {0, 0.33}, {-0.15, 0.37}, {0.15, 0.37}, {-0.06, 0.45}, {0.06, 0.45}, {0, 0.12}};
        for (double[] d : dots) Ink.draw(g, Ink.oval(d[0] * s, d[1] * s, 0.03 * s, 0.03 * s), Ink.INK, 0, 0);
        for (int i = 0; i < 5; i++) {                                     // scratches
            double x = (-0.25 + 0.12 * i) * s, y = (-0.3 + (i % 3) * 0.2) * s;
            Ink.line(g, x, y, x + 0.08 * s, y + 0.05 * s, (float) (0.012 * s), seed + 20 + i);
        }
    }

    private static void sack(Graphics2D g, double s, double r, int seed) {
        Path2D bag = Ink.blob(-0.55 * s, 0.4 * s, -0.6 * s, -0.2 * s, -0.48 * s, -0.7 * s, -0.25 * s, -0.62 * s, 0.05 * s, -0.75 * s,
            0.3 * s, -0.65 * s, 0.52 * s, -0.72 * s, 0.6 * s, -0.15 * s, 0.55 * s, 0.4 * s, 0.25 * s, 0.62 * s, -0.22 * s, 0.62 * s);
        Ink.draw(g, bag, Ink.shade(bag, Ink.BURLAP, new Color(104, 80, 52)), (float) (0.06 * s), seed);
        Ink.hatch(g, bag, 0.045 * s, 0, 40, seed);
        Ink.hatch(g, bag, 0.045 * s, 90, 30, seed + 1);
        // Ragged eye holes and a stitched mouth.
        for (int side = -1; side <= 1; side += 2) {
            Path2D hole = Ink.blob(side * 0.12 * s, -0.1 * s, side * 0.2 * s, -0.22 * s, side * 0.32 * s, -0.12 * s,
                side * 0.28 * s, -0.02 * s, side * 0.18 * s, 0.0);
            eye(g, hole, side * 0.21 * s - 0.02 * s, -0.12 * s, 0.03 * s);
        }
        Ink.line(g, Ink.curve(-0.22 * s, 0.25 * s, 0, 0.3 * s, 0.22 * s, 0.24 * s), (float) (0.025 * s), Ink.INK, seed + 3);
        for (int i = -3; i <= 3; i++) {
            double x = i * 0.065 * s, y = 0.27 * s + 0.02 * s * Math.abs(i) / 3.0;
            Ink.line(g, x - 0.025 * s, y - 0.05 * s, x + 0.025 * s, y + 0.05 * s, (float) (0.02 * s), seed + 10 + i);
        }
        // The rope at the neck.
        Path2D rope = Ink.curve(-0.42 * s, 0.6 * s, 0, 0.7 * s, 0.42 * s, 0.6 * s);
        Ink.line(g, rope, (float) (0.11 * s), Ink.INK, seed + 5);
        Ink.line(g, rope, (float) (0.07 * s), new Color(120, 92, 54), seed + 5);
        Ink.line(g, Ink.curve(0.1 * s, 0.68 * s, 0.14 * s, 0.85 * s, 0.08 * s, 1.0 * s), (float) (0.06 * s), new Color(120, 92, 54), seed + 6);
    }

    private static void welding(Graphics2D g, double s, double r, int seed) {
        Shape shield = new RoundRectangle2D.Double(-0.46 * s, -0.66 * s, 0.92 * s, 1.3 * s, 0.4 * s, 0.4 * s);
        Ink.draw(g, shield, Ink.shade(shield, new Color(70, 66, 64), new Color(30, 28, 28)), (float) (0.06 * s), seed);
        Ink.hatch(g, shield, 0.08 * s, 45, 50, seed);
        Shape frame = new Rectangle2D.Double(-0.3 * s, -0.22 * s, 0.6 * s, 0.24 * s);
        Ink.draw(g, frame, Ink.INK, (float) (0.05 * s), seed + 1);
        Shape glass = new Rectangle2D.Double(-0.24 * s, -0.17 * s, 0.48 * s, 0.14 * s);
        Ink.draw(g, glass, Ink.GLASS, 0, 0);
        int glint = 70 + (int) (50 * Math.sin(System.currentTimeMillis() / 700.0));
        g.setColor(new Color(150, 220, 170, Math.max(0, glint)));
        g.fill(new Rectangle2D.Double(-0.2 * s, -0.15 * s, 0.1 * s, 0.03 * s));
        for (int side = -1; side <= 1; side += 2) {                       // rivets and the head band
            Ink.draw(g, Ink.oval(side * 0.4 * s, -0.4 * s, 0.035 * s, 0.035 * s), Ink.ASH, (float) (0.02 * s), seed + 3 + side);
            Ink.draw(g, Ink.oval(side * 0.4 * s, 0.35 * s, 0.035 * s, 0.035 * s), Ink.ASH, (float) (0.02 * s), seed + 5 + side);
        }
        Ink.line(g, Ink.curve(-0.3 * s, 0.3 * s, 0, 0.36 * s, 0.3 * s, 0.3 * s), (float) (0.02 * s), Ink.INK, seed + 8);
    }

    private static void bauta(Graphics2D g, double s, double r, int seed) {
        Path2D m = new Path2D.Double();                                   // a blank brow over a jutting, square jaw
        m.moveTo(-0.32 * s, -0.55 * s);
        m.curveTo(-0.15 * s, -0.66 * s, 0.15 * s, -0.66 * s, 0.32 * s, -0.55 * s);
        m.curveTo(0.42 * s, -0.2 * s, 0.4 * s, 0.05 * s, 0.38 * s, 0.15 * s);
        m.lineTo(0.48 * s, 0.6 * s);
        m.lineTo(-0.48 * s, 0.6 * s);
        m.lineTo(-0.38 * s, 0.15 * s);
        m.curveTo(-0.4 * s, 0.05 * s, -0.42 * s, -0.2 * s, -0.32 * s, -0.55 * s);
        m.closePath();
        Ink.draw(g, m, Ink.shade(m, Ink.PAPER, Ink.BONE_SHADE), (float) (0.055 * s), seed);
        Ink.line(g, Ink.curve(-0.06 * s, -0.25 * s, -0.05 * s, 0.05 * s, 0, 0.15 * s, 0.06 * s, 0.07 * s), (float) (0.025 * s), Ink.INK, seed + 2);
        Ink.line(g, -0.38 * s, 0.15 * s, 0.38 * s, 0.15 * s, (float) (0.02 * s), seed + 3);   // the ledge of the jaw
        Ink.hatch(g, new Rectangle2D.Double(-0.48 * s, 0.17 * s, 0.96 * s, 0.43 * s), 0.05 * s, 70, 45, seed + 4);
        for (int side = -1; side <= 1; side += 2) {
            Path2D hole = Ink.blob(side * 0.07 * s, -0.2 * s, side * 0.17 * s, -0.27 * s, side * 0.28 * s, -0.21 * s, side * 0.17 * s, -0.15 * s);
            eye(g, hole, side * 0.17 * s - 0.02 * s, -0.23 * s, 0.028 * s);
        }
    }

    private static void fencing(Graphics2D g, double s, double r, int seed) {
        Shape bib = Ink.blob(-0.45 * s, 0.45 * s, 0.45 * s, 0.45 * s, 0.52 * s, 0.85 * s, -0.52 * s, 0.85 * s);
        Ink.draw(g, bib, Ink.PAPER, (float) (0.05 * s), seed + 1);
        Shape dome = Ink.oval(0, -0.05 * s, 0.44 * s, 0.6 * s);
        Ink.draw(g, dome, Ink.shade(dome, new Color(64, 62, 62), new Color(18, 18, 20)), (float) (0.06 * s), seed);
        Shape old = g.getClip();
        g.clip(dome);
        g.setColor(new Color(150, 150, 150, 90));
        g.setStroke(new BasicStroke((float) (0.012 * s)));
        for (double d = -0.6; d <= 0.6; d += 0.05) {                       // the steel mesh
            g.draw(new Line2D.Double(d * s, -0.7 * s, d * s, 0.6 * s));
            g.draw(new Line2D.Double(-0.5 * s, d * s, 0.5 * s, d * s));
        }
        g.setClip(old);
        for (int side = -1; side <= 1; side += 2) {                       // just a glint where the eyes are
            int glint = 110 + (int) (60 * Math.sin(System.currentTimeMillis() / 800.0 + side));
            g.setColor(new Color(240, 228, 200, Math.max(0, glint)));
            g.fill(Ink.oval(side * 0.15 * s, -0.12 * s, 0.03 * s, 0.022 * s));
        }
        Ink.line(g, Ink.curve(-0.42 * s, 0.35 * s, 0, 0.48 * s, 0.42 * s, 0.35 * s), (float) (0.06 * s), Ink.PAPER, seed + 2);
    }

    private static void balaclava(Graphics2D g, double s, double r, int seed) {
        Shape headShape = Ink.blob(-0.42 * s, 0.45 * s, -0.45 * s, -0.2 * s, -0.3 * s, -0.58 * s, 0, -0.66 * s,
            0.3 * s, -0.58 * s, 0.45 * s, -0.2 * s, 0.42 * s, 0.45 * s, 0, 0.6 * s);
        Ink.draw(g, headShape, Ink.shade(headShape, new Color(44, 40, 44), new Color(18, 16, 18)), (float) (0.055 * s), seed);
        Ink.hatch(g, headShape, 0.035 * s, 90, 55, seed);                // knitted ribs
        for (int side = -1; side <= 1; side += 2) {
            Path2D hole = Ink.blob(side * 0.06 * s, -0.12 * s, side * 0.18 * s, -0.19 * s, side * 0.3 * s, -0.12 * s, side * 0.18 * s, -0.06 * s);
            Ink.draw(g, hole, new Color(150, 128, 110), (float) (0.025 * s), seed + side);
            eye(g, Ink.oval(side * 0.18 * s, -0.125 * s, 0.07 * s, 0.045 * s), side * 0.18 * s - 0.02 * s, -0.14 * s, 0.025 * s);
        }
        Shape mouth = Ink.oval(0, 0.26 * s, 0.12 * s, 0.06 * s);
        Ink.draw(g, mouth, new Color(150, 128, 110), (float) (0.025 * s), seed + 4);
        Ink.draw(g, Ink.oval(0, 0.26 * s, 0.08 * s, 0.035 * s), new Color(40, 10, 12), 0, 0);
    }

    // ─── Standing (the dealers) ─────────────────────────────────
    /** A dealer standing, seen from your seat. (cx, footY): where he stands; s: about one head's height. */
    public static void standing(Graphics2D g, Look look, double cx, double footY, double s, double t, String reaction, double p, boolean offering) {
        Pose o = pose(t, reaction, p, look.seed);
        AffineTransform saved = g.getTransform();
        g.translate(cx + o.sway * s, footY);
        double rise = -0.035 * s * (1 + o.breath) / 2;
        double sh = -6.1 * s + rise;
        Path2D robe = Ink.blob(-1.6 * s, 0, -1.25 * s, -2.5 * s, -1.35 * s, sh + 0.6 * s, -0.8 * s, sh, 0.8 * s, sh,
            1.35 * s, sh + 0.6 * s, 1.25 * s, -2.5 * s, 1.6 * s, 0);
        Ink.draw(g, robe, Ink.shade(robe, look.robe, look.robe.darker().darker()), (float) (0.06 * s), look.seed);
        Ink.hatch(g, robe, 0.14 * s, 75, 45, look.seed);
        for (int i = -2; i <= 2; i++) {
            if (i == 0) continue;
            Ink.line(g, Ink.curve(i * 0.4 * s, sh + 0.5 * s, i * 0.5 * s, -2.5 * s, i * 0.65 * s, -0.1 * s), (float) (0.025 * s),
                new Color(0, 0, 0, 110), look.seed + i);
        }
        // Arms: hands clasped in front, or (the card dealer) one held out with a fan of cards.
        double handY = sh + 2.3 * s;
        if (offering) {
            Path2D sleeve = Ink.blob(-1.1 * s, sh + 0.5 * s, -1.4 * s, sh + 1.3 * s, -1.9 * s, handY - 0.1 * s, -1.6 * s, handY - 0.4 * s, -0.8 * s, sh + 1.1 * s);
            Ink.draw(g, sleeve, look.robe.darker(), (float) (0.055 * s), look.seed + 11);
            for (int c = 0; c < 4; c++) {
                AffineTransform before = g.getTransform();
                g.translate(-2.0 * s, handY - 0.2 * s);
                g.rotate(-0.5 + c * 0.25);
                Ink.draw(g, new RoundRectangle2D.Double(-0.12 * s, -0.55 * s, 0.24 * s, 0.4 * s, 0.05 * s, 0.05 * s), Ink.PAPER, (float) (0.03 * s), look.seed + 50 + c);
                g.setTransform(before);
            }
            hand(g, look, -1.95 * s, handY - 0.15 * s, s, -1, look.seed + 21);
        }
        for (int side = offering ? 1 : -1; side <= 1; side += 2) {
            Path2D sleeve = Ink.blob(side * 1.15 * s, sh + 0.5 * s, side * 1.35 * s, sh + 1.5 * s, side * 0.35 * s, handY + 0.1 * s,
                side * 0.15 * s, handY - 0.2 * s, side * 0.85 * s, sh + 1.2 * s);
            Ink.draw(g, sleeve, look.robe.darker(), (float) (0.055 * s), look.seed + 10 + side);
            hand(g, look, side * 0.18 * s, handY, s, side, look.seed + 20 + side);
        }
        double hy = sh - 0.62 * s + o.headDrop * s;
        g.translate(0, hy);
        g.rotate(o.tilt);
        head(g, look, s);
        g.setTransform(saved);
    }
}
