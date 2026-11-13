import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * The pixel sprites, all drawn in code and shaded by Shade (lamp-lit, purple shadows, warm rims):
 * the masked characters (upper body, as if behind a table or counter) and the chess pieces.
 *
 * The players are prisoners forced to play, in makeshift masks: RAG, TAPE, BOX, BAG, PILLOW (opponents)
 * and PLATE (you). The guards around the table wear real masks: GAS, PLAGUE, SMILE, HOCKEY, WELDING;
 * the dealers too (BAUTA, FENCING, BALACLAVA). SACK is no longer used.
 */
public final class PixelArt {
    private PixelArt() {}

    public enum Mask { GAS, PLAGUE, HOCKEY, SACK, WELDING, BAUTA, FENCING, BALACLAVA, RAG, TAPE, BOX, BAG, PILLOW, PLATE, SMILE }

    /** One person: mask, robe colour, whether the hood is up, gloves, and a voice (blip pitch). */
    public static final class Person {
        public final String name;
        public final Mask mask;
        public final Color robe;
        public final boolean hood, gloves;
        public final int voice;
        /** Standing with arms folded (guards) instead of sitting with hands on the table. */
        public final boolean standing;

        public Person(String name, Mask mask, Color robe, boolean hood, boolean gloves, int voice) {
            this(name, mask, robe, hood, gloves, voice, false);
        }

        public Person(String name, Mask mask, Color robe, boolean hood, boolean gloves, int voice, boolean standing) {
            this.name = name;
            this.mask = mask;
            this.robe = robe;
            this.hood = hood;
            this.gloves = gloves;
            this.voice = voice;
            this.standing = standing;
        }
    }

    /** The other prisoners you are made to play, one per level, in masks made from whatever they had. */
    public static final Person[] OPPONENTS = {
        new Person("The Rag", Mask.RAG, new Color(92, 84, 70), false, false, 180),
        new Person("The Tape", Mask.TAPE, new Color(70, 74, 90), true, false, 300),
        new Person("The Box", Mask.BOX, new Color(110, 96, 80), false, false, 240),
        new Person("The Bag", Mask.BAG, new Color(66, 78, 96), false, false, 140),
        new Person("The Pillow", Mask.PILLOW, new Color(96, 70, 70), false, false, 110),
    };
    /** You: a paper plate with two holes, tied on with string. */
    public static final Person PLAYER = new Person("You", Mask.PLATE, new Color(80, 74, 66), true, false, 220);
    /** The masked men standing round the table. The White Smile is the one who walks in from the skywalk. */
    public static final Person[] GUARDS = {
        new Person("The White Smile", Mask.SMILE, new Color(44, 42, 50), false, true, 200, true),
        new Person("Gas Guard", Mask.GAS, new Color(52, 50, 46), true, true, 160, true),
        new Person("Beak Guard", Mask.PLAGUE, new Color(48, 40, 44), false, true, 280, true),
        new Person("Goalie Guard", Mask.HOCKEY, new Color(50, 52, 58), true, true, 230, true),
        new Person("Welder Guard", Mask.WELDING, new Color(56, 50, 48), false, true, 120, true),
    };
    public static final Person CARD_DEALER = new Person("The Card Dealer", Mask.BAUTA, new Color(84, 62, 54), true, false, 360);
    public static final Person PIECE_DEALER = new Person("The Piece Dealer", Mask.FENCING, new Color(122, 98, 70), false, true, 200);
    public static final Person RESELLER = new Person("The Reseller", Mask.BALACLAVA, new Color(98, 64, 118), true, false, 260);

    public static final int SPRITE_W = 150, SPRITE_H = 140;

    // Ramps used by everyone.
    private static final Color[] SKIN = Shade.ramp(new Color(214, 170, 138), 5);
    private static final Color[] GLOVE = Shade.ramp(new Color(58, 48, 52), 4);
    private static final Color[] VOID = Shade.ramp(new Color(20, 14, 24), 3);
    private static final Color[] BRASS = Shade.ramp(new Color(196, 148, 62), 5);
    private static final Color[] GLASS = Shade.ramp(new Color(30, 52, 56), 4);
    private static final Color[] METAL = Shade.ramp(new Color(140, 140, 146), 5);
    private static final Color[] BONE = Shade.ramp(new Color(226, 214, 186), 5);
    private static final Color[] LEATHER = Shade.ramp(new Color(104, 70, 50), 5);
    private static final Color[] RUBBER = Shade.ramp(new Color(92, 98, 80), 5);
    private static final Color[] BURLAP = Shade.ramp(new Color(176, 138, 92), 5);
    private static final Color[] ROPE = Shade.ramp(new Color(128, 94, 56), 4);
    private static final Color[] FELT = Shade.ramp(new Color(46, 40, 50), 4);
    private static final Color[] RED = Shade.ramp(new Color(140, 26, 32), 4);
    private static final Color[] STEEL_DARK = Shade.ramp(new Color(62, 60, 64), 5);
    private static final Color[] WOOL = Shade.ramp(new Color(34, 30, 38), 4);
    private static final Color[] CLOTH = Shade.ramp(new Color(220, 210, 190), 4);

    // ─── Pose ───────────────────────────────────────────────────
    private static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** {body y, head x, head y, shake x}, in pixels. */
    private static int[] pose(double t, String reaction, double p) {
        int bodyY = (int) Math.round((1 + Math.sin(t * 2 * Math.PI / 3.6)) * 0.8);   // breathing
        int headX = (int) Math.round(Math.sin(t * 2 * Math.PI / 6.1) * 0.8), headY = 0, shake = 0;
        if (reaction != null) {
            double bell = Math.sin(Math.PI * p);
            double hold = smooth(0, 0.2, p) * (1 - smooth(0.8, 1, p));
            switch (reaction) {
                case "think": headY = (int) Math.round(3 * bell); headX += (int) Math.round(2 * bell); break;
                case "capture": headY = (int) Math.round(2 * hold); bodyY += (int) Math.round(2 * hold); break;
                case "hurt": headY = -(int) Math.round(5 * Math.sin(Math.PI * Math.sqrt(p))); shake = (int) Math.round(Math.sin(p * 40) * 3 * (1 - p)); break;
                case "check": headY = (int) Math.round(3 * hold); bodyY += (int) Math.round(2 * hold); break;
                case "win": headY = -(int) Math.round(3 * hold); shake = (int) Math.round(Math.sin(p * 60) * 2 * hold); break;
                case "lose": headY = (int) Math.round(8 * hold); bodyY += (int) Math.round(3 * hold); break;
                case "yes": headY = (int) Math.round(3 * bell * (0.5 - 0.5 * Math.cos(p * 4 * Math.PI))); break;
                case "no": headX += (int) Math.round(3 * bell * Math.sin(p * 6 * Math.PI)); break;
                default: break;
            }
        }
        return new int[]{bodyY, headX, headY, shake};
    }

    /** The person's upper body as a small image (SPRITE_W x SPRITE_H), hands resting at the bottom edge. */
    public static BufferedImage person(Person who, double t, String reaction, double p) {
        int[] o = pose(t, reaction, p);
        int beat = (int) Math.floor(t * 3);                             // eye glints change a few times a second
        String key = who.name + o[0] + "," + o[1] + "," + o[2] + "," + o[3] + "," + beat % 20;
        BufferedImage img = CACHE.get(key);
        if (img == null) {
            img = build(who, (beat % 20) / 3.0, o);
            CACHE.put(key, img);
        }
        return img;
    }

    /** Recently drawn poses, so a figure is shaded only when it actually changes. */
    private static final java.util.Map<String, BufferedImage> CACHE = new java.util.LinkedHashMap<String, BufferedImage>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, BufferedImage> e) { return size() > 160; }
    };

    private static BufferedImage build(Person who, double t, int[] o) {
        BufferedImage img = new BufferedImage(SPRITE_W, SPRITE_H, BufferedImage.TYPE_INT_ARGB);
        int sx = o[3], by = o[0], hx = o[1] + o[3], hy = o[2] + o[0];
        Color[] robe = Shade.ramp(who.robe, 5), robeDark = Shade.ramp(who.robe.darker(), 5);
        if (who.hood) {                                                 // a peaked hood, its opening deep in shadow
            Path2D hood = new Path2D.Double();
            hood.moveTo(38 + hx, 92 + hy);
            hood.curveTo(34 + hx, 50 + hy, 50 + hx, 14 + hy, 75 + hx, 2 + hy);
            hood.curveTo(100 + hx, 14 + hy, 116 + hx, 50 + hy, 112 + hx, 92 + hy);
            hood.closePath();
            Shade.part(img, hood, Shade.ramp(who.robe.darker(), 5), 16);
            Shade.part(img, new Ellipse2D.Double(48 + hx, 14 + hy, 54, 72), Shade.ramp(new Color(30, 20, 32), 3), 0, false, 0.6);
        }
        robe(img, robe, robeDark, sx, by);
        head(img, who, hx, hy, t);
        if (who.standing) {                                             // arms folded across the chest
            Color[] hand = who.gloves ? GLOVE : SKIN;
            Shade.stroke(img, new Line2D.Double(30 + sx, 116 + by, 98 + sx, 100 + by), 13, robeDark);   // right arm under
            Shade.part(img, new Ellipse2D.Double(94 + sx, 94 + by, 13, 11), hand, 4);
            Shade.stroke(img, new Line2D.Double(120 + sx, 116 + by, 52 + sx, 100 + by), 13, robe);       // left arm over
            Shade.part(img, new Ellipse2D.Double(43 + sx, 94 + by, 13, 11), hand, 4);
        } else {
            hands(img, who, sx, by);
        }
        return img;
    }

    private static void robe(BufferedImage img, Color[] robe, Color[] dark, int sx, int by) {
        Path2D body = new Path2D.Double();
        body.moveTo(4 + sx, 140);
        body.curveTo(8 + sx, 108, 16 + sx, 84 + by, 34 + sx, 74 + by);
        body.curveTo(50 + sx, 66 + by, 100 + sx, 66 + by, 116 + sx, 74 + by);
        body.curveTo(134 + sx, 84 + by, 142 + sx, 108, 146 + sx, 140);
        body.closePath();
        Shade.part(img, body, robe, 26);
        Shade.part(img, new Polygon(new int[]{60 + sx, 90 + sx, 75 + sx}, new int[]{70 + by, 70 + by, 112 + by}, 3), dark, 4);
        for (int[] f : new int[][]{{30, 96, 22, 138}, {50, 90, 46, 138}, {100, 90, 104, 138}, {120, 96, 128, 138}}) {
            Path2D fold = new Path2D.Double();
            fold.moveTo(f[0] + sx, f[1] + by);
            fold.quadTo((f[0] + f[2]) / 2.0 + sx + 3, (f[1] + f[3]) / 2.0, f[2] + sx, f[3]);
            Shade.stroke(img, fold, 2.5f, dark);
        }
        // Sleeves down to the table.
        for (int side = -1; side <= 1; side += 2) {
            double c = 75 + sx;
            Path2D sleeve = new Path2D.Double();
            sleeve.moveTo(c + side * 50, 80 + by);
            sleeve.curveTo(c + side * 64, 96 + by, c + side * 62, 118, c + side * 44, 134);
            sleeve.lineTo(c + side * 22, 132);
            sleeve.curveTo(c + side * 30, 116, c + side * 36, 98 + by, c + side * 36, 84 + by);
            sleeve.closePath();
            Shade.part(img, sleeve, dark, 12);
        }
    }

    private static void hands(BufferedImage img, Person who, int sx, int by) {
        Color[] skin = who.gloves ? GLOVE : SKIN;
        for (int side = -1; side <= 1; side += 2) {
            double cx = 75 + sx + side * 34;
            Shade.part(img, new Ellipse2D.Double(cx - 13, 124, 26, 11), skin, 5);
            for (int f = 0; f < 4; f++) {                              // long fingers splayed on the wood
                double fx = cx - 10 + f * 6 + side * 1;
                Shade.part(img, new RoundRectangle2D.Double(fx, 129, 4, 10 - Math.abs(f - 1.5), 4, 4), skin, 2);
            }
            Shade.part(img, new Ellipse2D.Double(cx + side * 12 - 3, 124, 7, 6), skin, 2);   // thumb
        }
    }

    /** A black eye hole with a glint that comes and goes, so something looks back. */
    private static void eye(BufferedImage img, Shape hole, int gx, int gy, double t) {
        Shade.part(img, hole, VOID, 0, false, 1);
        if (Math.sin(t * 2.3 + gx) > -0.6) {
            Shade.px(img, gx, gy, new Color(255, 236, 210));
            Shade.px(img, gx + 1, gy, new Color(180, 150, 140));
        }
    }

    private static void head(BufferedImage img, Person who, int hx, int hy, double t) {
        Shade.part(img, new Ellipse2D.Double(54 + hx, 22 + hy, 42, 56), VOID, 0, false, 1);   // the dark inside the hood
        if (!who.hood) Shade.part(img, new Rectangle2D.Double(66 + hx, 66 + hy, 18, 10), VOID, 0, false, 1);  // neck in shadow
        AffineTransform at = AffineTransform.getTranslateInstance(hx, hy);
        switch (who.mask) {
            case GAS: gas(img, at, hx, hy, t); break;
            case PLAGUE: plague(img, at, hx, hy, t); break;
            case HOCKEY: hockey(img, at, hx, hy, t); break;
            case SACK: sack(img, at, hx, hy, t); break;
            case WELDING: welding(img, at, hx, hy, t); break;
            case BAUTA: bauta(img, at, hx, hy, t); break;
            case FENCING: fencing(img, at, hx, hy, t); break;
            case BALACLAVA: balaclava(img, at, hx, hy, t); break;
            case RAG: rag(img, at, hx, hy, t); break;
            case TAPE: tape(img, at, hx, hy, t); break;
            case BOX: box(img, at, hx, hy, t); break;
            case BAG: bag(img, at, hx, hy, t); break;
            case PILLOW: pillow(img, at, hx, hy, t); break;
            case PLATE: plate(img, at, hx, hy, t); break;
            case SMILE: smile(img, at, hx, hy, t); break;
            default: break;
        }
    }

    private static Shape tr(AffineTransform at, Shape s) {
        return at.createTransformedShape(s);
    }

    // ─── The masks ──────────────────────────────────────────────
    private static void gas(BufferedImage img, AffineTransform at, int hx, int hy, double t) {
        Shade.part(img, tr(at, new Ellipse2D.Double(50, 18, 50, 60)), RUBBER, 16);
        Shade.part(img, tr(at, new Rectangle2D.Double(48, 38, 54, 4)), Shade.ramp(new Color(60, 50, 40), 3), 2);   // the strap
        for (int side = -1; side <= 1; side += 2) {
            double cx = 75 + side * 12;
            Shade.part(img, tr(at, new Ellipse2D.Double(cx - 10, 34, 20, 20)), BRASS, 5);
            Shade.part(img, tr(at, new Ellipse2D.Double(cx - 7, 37, 14, 14)), GLASS, 7);
            eye(img, tr(at, new Ellipse2D.Double(cx - 3, 42, 6, 5)), (int) cx - 2 + hx, 41 + hy, t);
            Shade.px(img, (int) cx - 5 + hx, 39 + hy, new Color(200, 230, 220));
        }
        Shade.part(img, tr(at, new Ellipse2D.Double(64, 56, 22, 20)), METAL, 8);    // the filter snout
        for (int i = 0; i < 4; i++) Shade.rect(img, 68 + i * 4 + hx, 60 + hy, 1, 12, new Color(60, 58, 64));
        Path2D hose = new Path2D.Double();
        hose.moveTo(78, 74);
        hose.curveTo(84, 92, 100, 96, 104, 120);
        Shade.stroke(img, tr(at, hose), 9, RUBBER);
        for (int i = 0; i < 7; i++) {
            double k = i / 7.0;
            int x = (int) (80 + 24 * k * k + 4 * k), y = (int) (78 + 40 * k);
            Shade.rect(img, x - 3 + hx, y + hy, 7, 1, new Color(48, 46, 40));
        }
    }

    private static void plague(BufferedImage img, AffineTransform at, int hx, int hy, double t) {
        Shade.part(img, tr(at, new Ellipse2D.Double(52, 22, 46, 56)), LEATHER, 14);
        for (int side = -1; side <= 1; side += 2) {
            double cx = 75 + side * 11;
            Shade.part(img, tr(at, new Ellipse2D.Double(cx - 7, 38, 14, 14)), BRASS, 4);
            Shade.part(img, tr(at, new Ellipse2D.Double(cx - 5, 40, 10, 10)), GLASS, 5);
            eye(img, tr(at, new Ellipse2D.Double(cx - 2, 43, 5, 4)), (int) cx - 1 + hx, 43 + hy, t);
        }
        Path2D beak = new Path2D.Double();                             // long, curving down past the chin
        beak.moveTo(66, 54);
        beak.curveTo(66, 74, 72, 96, 80, 112);
        beak.curveTo(84, 94, 86, 72, 84, 54);
        beak.closePath();
        Shade.part(img, tr(at, beak), Shade.ramp(new Color(130, 92, 64), 5), 7);
        Path2D ridge = new Path2D.Double();
        ridge.moveTo(75, 58);
        ridge.curveTo(76, 80, 78, 96, 80, 110);
        Shade.stroke(img, tr(at, ridge), 1.5f, LEATHER);
        Shade.part(img, tr(at, new RoundRectangle2D.Double(52, -2, 46, 26, 8, 8)), FELT, 8);   // the hat
        Shade.part(img, tr(at, new Rectangle2D.Double(52, 16, 46, 5)), RED, 2);
        Shade.part(img, tr(at, new Ellipse2D.Double(26, 18, 98, 13)), FELT, 4);
    }

    private static void hockey(BufferedImage img, AffineTransform at, int hx, int hy, double t) {
        Shade.part(img, tr(at, new Ellipse2D.Double(54, 20, 42, 58)), BONE, 16);
        for (int side = -1; side <= 1; side += 2) {
            Path2D hole = new Path2D.Double();
            double cx = 75 + side * 10;
            hole.moveTo(cx - 6, 42);
            hole.quadTo(cx, 36, cx + 6, 42);
            hole.quadTo(cx, 47, cx - 6, 42);
            eye(img, tr(at, hole), (int) cx - 1 + hx, 41 + hy, t);
        }
        Color dot = new Color(40, 26, 30);
        int[][] dots = {{75, 26}, {69, 29}, {81, 29}, {66, 54}, {72, 54}, {78, 54}, {84, 54}, {63, 60}, {69, 60}, {75, 60},
            {81, 60}, {87, 60}, {66, 66}, {72, 66}, {78, 66}, {84, 66}, {70, 72}, {75, 72}, {80, 72}, {75, 50}};
        for (int[] d : dots) {
            Shade.rect(img, d[0] + hx, d[1] + hy, 2, 2, dot);
            Shade.px(img, d[0] + 2 + hx, d[1] + 2 + hy, new Color(160, 146, 120));
        }
        Shade.rect(img, 60 + hx, 32 + hy, 4, 1, new Color(150, 136, 112));     // scuffs
        Shade.rect(img, 85 + hx, 58 + hy, 3, 1, new Color(150, 136, 112));
    }

    private static void sack(BufferedImage img, AffineTransform at, int hx, int hy, double t) {
        Path2D bag = new Path2D.Double();
        bag.moveTo(52, 62);
        bag.curveTo(48, 40, 52, 20, 56, 12);
        bag.lineTo(64, 18);
        bag.curveTo(70, 14, 80, 14, 86, 18);
        bag.lineTo(94, 11);
        bag.curveTo(100, 22, 102, 42, 98, 62);
        bag.curveTo(94, 76, 56, 76, 52, 62);
        bag.closePath();
        Shape b = tr(at, bag);
        Shade.part(img, b, BURLAP, 14);
        Rectangle r = b.getBounds();
        for (int y = r.y; y < r.y + r.height; y++) {                    // the weave
            for (int x = r.x; x < r.x + r.width; x++) {
                if (!b.contains(x + 0.5, y + 0.5)) continue;
                if ((x + y * 3) % 6 == 0) Shade.px(img, x, y, BURLAP[1]);
                else if ((x * 5 + y) % 9 == 0) Shade.px(img, x, y, BURLAP[4]);
            }
        }
        Path2D eyeL = new Path2D.Double();
        eyeL.moveTo(62, 38); eyeL.lineTo(68, 35); eyeL.lineTo(72, 39); eyeL.lineTo(70, 44); eyeL.lineTo(63, 43); eyeL.closePath();
        Path2D eyeR = new Path2D.Double();
        eyeR.moveTo(80, 37); eyeR.lineTo(86, 34); eyeR.lineTo(90, 38); eyeR.lineTo(88, 45); eyeR.lineTo(81, 43); eyeR.closePath();
        eye(img, tr(at, eyeL), 66 + hx, 38 + hy, t);
        eye(img, tr(at, eyeR), 84 + hx, 38 + hy, t + 1);
        Color thread = new Color(40, 24, 20);                          // a stitched mouth
        Shade.rect(img, 64 + hx, 58 + hy, 22, 1, thread);
        for (int x = 65; x <= 85; x += 4) {
            Shade.rect(img, x + hx, 55 + hy, 1, 7, thread);
            Shade.px(img, x + 1 + hx, 56 + hy, thread);
        }
        Path2D rope = new Path2D.Double();
        rope.moveTo(56, 72);
        rope.quadTo(75, 80, 94, 72);
        Shade.stroke(img, tr(at, rope), 6, ROPE);
        Path2D tail = new Path2D.Double();
        tail.moveTo(82, 76);
        tail.quadTo(86, 86, 82, 94);
        Shade.stroke(img, tr(at, tail), 4, ROPE);
    }

    private static void welding(BufferedImage img, AffineTransform at, int hx, int hy, double t) {
        Shade.part(img, tr(at, new RoundRectangle2D.Double(52, 14, 46, 66, 18, 18)), STEEL_DARK, 14);
        Shade.part(img, tr(at, new RoundRectangle2D.Double(58, 34, 34, 14, 3, 3)), Shade.ramp(new Color(24, 22, 26), 3), 2);
        Shade.part(img, tr(at, new Rectangle2D.Double(61, 37, 28, 8)), Shade.ramp(new Color(48, 110, 72), 4), 3);
        if (Math.sin(t * 1.7) > -0.3) Shade.rect(img, 63 + hx, 38 + hy, 6, 1, new Color(170, 240, 190));
        for (int[] r : new int[][]{{57, 20}, {93, 20}, {57, 72}, {93, 72}}) {
            Shade.rect(img, r[0] + hx, r[1] + hy, 2, 2, METAL[3]);
            Shade.px(img, r[0] + hx, r[1] + hy, METAL[4]);
        }
        Shade.rect(img, 54 + hx, 50 + hy, 42, 1, STEEL_DARK[0]);       // the seam of the shield
    }

    private static void bauta(BufferedImage img, AffineTransform at, int hx, int hy, double t) {
        Path2D m = new Path2D.Double();                                 // a blank brow over a jutting, square jaw
        m.moveTo(60, 22);
        m.curveTo(68, 16, 82, 16, 90, 22);
        m.curveTo(96, 34, 95, 46, 94, 54);
        m.lineTo(100, 80);
        m.lineTo(50, 80);
        m.lineTo(56, 54);
        m.curveTo(55, 46, 54, 34, 60, 22);
        m.closePath();
        Shade.part(img, tr(at, m), BONE, 12);
        Shade.rect(img, 57 + hx, 62 + hy, 36, 1, new Color(150, 132, 116));    // the jutting chin's crease
        Path2D nose = new Path2D.Double();
        nose.moveTo(75, 38);
        nose.lineTo(73, 52);
        nose.lineTo(77, 52);
        Shade.stroke(img, tr(at, nose), 1.5f, Shade.ramp(new Color(170, 150, 130), 3));
        for (int side = -1; side <= 1; side += 2) {
            double cx = 75 + side * 10;
            Path2D hole = new Path2D.Double();
            hole.moveTo(cx - 6, 38);
            hole.quadTo(cx, 33, cx + 6, 38);
            hole.quadTo(cx, 41, cx - 6, 38);
            eye(img, tr(at, hole), (int) cx - 1 + hx, 37 + hy, t);
        }
    }

    private static void fencing(BufferedImage img, AffineTransform at, int hx, int hy, double t) {
        Shade.part(img, tr(at, new RoundRectangle2D.Double(54, 70, 42, 20, 6, 6)), CLOTH, 6);   // the bib
        Shape dome = tr(at, new Ellipse2D.Double(53, 16, 44, 60));
        Shade.part(img, dome, STEEL_DARK, 18);
        Rectangle r = dome.getBounds();
        for (int y = r.y; y < r.y + r.height; y++) {                    // the steel mesh catches the light
            for (int x = r.x; x < r.x + r.width; x++) {
                if (!dome.contains(x + 0.5, y + 0.5)) continue;
                boolean wire = (x - hx) % 3 == 0 || (y - hy) % 3 == 0;
                int rgb = img.getRGB(x, y);
                if (!wire) Shade.px(img, x, y, Shade.mix(new Color(rgb), new Color(10, 8, 14), 0.75));
            }
        }
        if (Math.sin(t * 2.1) > -0.5) {
            Shade.px(img, 68 + hx, 43 + hy, new Color(255, 236, 210));
            Shade.px(img, 82 + hx, 43 + hy, new Color(255, 236, 210));
        }
    }

    private static void balaclava(BufferedImage img, AffineTransform at, int hx, int hy, double t) {
        Shape head = tr(at, new Ellipse2D.Double(52, 16, 46, 62));
        Shade.part(img, head, WOOL, 16);
        Rectangle r = head.getBounds();
        for (int y = r.y; y < r.y + r.height; y++) {                    // knitted ribs
            for (int x = r.x; x < r.x + r.width; x += 3) {
                if (head.contains(x + 0.5, y + 0.5) && (y % 2 == 0)) Shade.px(img, x, y, WOOL[0]);
            }
        }
        Shade.part(img, tr(at, new RoundRectangle2D.Double(59, 36, 32, 10, 6, 6)), SKIN, 3);   // the eye slit, skin showing
        Shade.part(img, tr(at, new Ellipse2D.Double(68, 60, 14, 7)), SKIN, 2);                  // the mouth hole
        eye(img, tr(at, new Ellipse2D.Double(62, 38, 8, 6)), 64 + hx, 39 + hy, t);
        eye(img, tr(at, new Ellipse2D.Double(80, 38, 8, 6)), 82 + hx, 39 + hy, t + 1);
        Shade.part(img, tr(at, new Ellipse2D.Double(71, 62, 8, 3)), Shade.ramp(new Color(60, 12, 16), 2), 0, false, 1);
    }

    // ─── The makeshift masks ─────────────────────────────────────
    private static final Color INK = new Color(26, 20, 24);

    /** A t-shirt wound round the head and knotted, with swimming goggles over it. */
    private static void rag(BufferedImage img, AffineTransform at, int hx, int hy, double t) {
        Color[] rag = Shade.ramp(new Color(116, 54, 48), 5);
        Shade.part(img, tr(at, new Ellipse2D.Double(51, 16, 48, 64)), rag, 14);
        for (int i = 0; i < 4; i++) {                                     // the turns of cloth
            Path2D fold = new Path2D.Double();
            fold.moveTo(52, 24 + i * 15);
            fold.quadTo(75, 30 + i * 15 + (i % 2) * 4, 98, 20 + i * 15);
            Shade.stroke(img, tr(at, fold), 1.6f, Shade.ramp(new Color(80, 34, 34), 3));
        }
        Path2D tail1 = new Path2D.Double();                               // the knot and its ends
        tail1.moveTo(98, 50);
        tail1.quadTo(110, 56, 112, 68);
        Shade.stroke(img, tr(at, tail1), 5, rag);
        Path2D tail2 = new Path2D.Double();
        tail2.moveTo(98, 52);
        tail2.quadTo(114, 50, 120, 58);
        Shade.stroke(img, tr(at, tail2), 4, rag);
        Shade.part(img, tr(at, new Ellipse2D.Double(94, 44, 10, 10)), rag, 4);
        Shade.part(img, tr(at, new Rectangle2D.Double(50, 39, 50, 4)), Shade.ramp(new Color(30, 30, 36), 3), 1);   // the strap
        for (int side = -1; side <= 1; side += 2) {
            double cx = 75 + side * 12;
            Shade.part(img, tr(at, new Ellipse2D.Double(cx - 9, 34, 18, 14)), Shade.ramp(new Color(36, 36, 44), 4), 3);
            Shade.part(img, tr(at, new Ellipse2D.Double(cx - 7, 36, 14, 10)), Shade.ramp(new Color(60, 100, 124), 4), 4);
            eye(img, tr(at, new Ellipse2D.Double(cx - 3, 39, 6, 4)), (int) cx - 1 + hx, 40 + hy, t);
            Shade.px(img, (int) cx - 5 + hx, 37 + hy, new Color(200, 230, 240));
        }
    }

    /** Grey duct tape wound round and round, a gap left for the eyes. */
    private static void tape(BufferedImage img, AffineTransform at, int hx, int hy, double t) {
        Shape head = tr(at, new Ellipse2D.Double(52, 16, 46, 64));
        Shade.part(img, head, VOID, 0, false, 1);
        for (int i = 0; i < 10; i++) {
            double y = 16 + i * 6.6;
            if (y > 35 && y < 46) continue;                                 // the gap for the eyes
            double ang = (i % 2 == 0 ? 0.1 : -0.12);
            Shape strip = AffineTransform.getRotateInstance(ang, 75, y + 3).createTransformedShape(new Rectangle2D.Double(46, y, 58, 7.5));
            Area a = new Area(at.createTransformedShape(strip));
            a.intersect(new Area(head));
            Shade.part(img, a, Shade.ramp(new Color(150 - (i % 3) * 14, 150 - (i % 3) * 14, 156 - (i % 3) * 12), 5), 3);
        }
        Shade.part(img, tr(at, new Polygon(new int[]{90, 99, 95}, new int[]{60, 58, 66}, 3)), Shade.ramp(new Color(176, 176, 182), 3), 1);  // a peeling end
        eye(img, tr(at, new Ellipse2D.Double(60, 39, 9, 5)), 63 + hx, 40 + hy, t);
        eye(img, tr(at, new Ellipse2D.Double(81, 39, 9, 5)), 84 + hx, 40 + hy, t + 1);
    }

    /** A cardboard box with the flaps up, holes cut for eyes and a smile drawn on in marker. */
    private static void box(BufferedImage img, AffineTransform at, int hx, int hy, double t) {
        Color[] card = Shade.ramp(new Color(178, 134, 86), 5), side = Shade.ramp(new Color(132, 96, 60), 5);
        Shade.part(img, tr(at, new Polygon(new int[]{98, 108, 108, 98}, new int[]{14, 8, 74, 80}, 4)), side, 2);
        Shade.part(img, tr(at, new Polygon(new int[]{48, 98, 90, 40}, new int[]{14, 14, 2, 6}, 4)), side, 2);
        Shade.part(img, tr(at, new Polygon(new int[]{98, 108, 118, 106}, new int[]{14, 8, 0, 4}, 4)), card, 2);
        Shade.part(img, tr(at, new Rectangle2D.Double(48, 14, 50, 66)), card, 3);
        Shade.part(img, tr(at, new Rectangle2D.Double(48, 21, 50, 6)), Shade.ramp(new Color(196, 176, 124), 3), 1);   // packing tape
        Path2D l = new Path2D.Double();
        l.moveTo(57, 40); l.lineTo(68, 38); l.lineTo(70, 46); l.lineTo(58, 47); l.closePath();
        Path2D r = new Path2D.Double();
        r.moveTo(79, 38); r.lineTo(90, 40); r.lineTo(89, 47); r.lineTo(80, 46); r.closePath();
        eye(img, tr(at, l), 62 + hx, 41 + hy, t);
        eye(img, tr(at, r), 84 + hx, 41 + hy, t + 1);
        for (int x = 56; x <= 90; x++) {                                   // the marker smile
            int y = 60 + (int) Math.round(5 * Math.sin((x - 56) / 34.0 * Math.PI));
            Shade.rect(img, x + hx, y + hy, 1, 2, INK);
        }
        for (int x = 62; x <= 84; x += 5) Shade.rect(img, x + hx, 61 + hy + (int) (4 * Math.sin((x - 56) / 34.0 * Math.PI)), 1, 4, INK);
        Color red = new Color(150, 40, 36);                                 // "this way up"
        Shade.rect(img, 91 + hx, 30 + hy, 1, 6, red);
        Shade.rect(img, 90 + hx, 31 + hy, 3, 1, red);
        Shade.px(img, 91 + hx, 29 + hy, red);
    }

    /** A paper grocery bag pulled down over the head, two holes torn in it. */
    private static void bag(BufferedImage img, AffineTransform at, int hx, int hy, double t) {
        Color[] paper = Shade.ramp(new Color(198, 166, 118), 5);
        Path2D b = new Path2D.Double();
        b.moveTo(50, 80); b.lineTo(47, 40); b.lineTo(49, 12); b.lineTo(101, 12); b.lineTo(103, 44); b.lineTo(100, 80); b.closePath();
        Shade.part(img, tr(at, b), paper, 5);
        for (int x : new int[]{56, 94}) {                                   // the folded sides
            Path2D crease = new Path2D.Double();
            crease.moveTo(x, 14);
            crease.lineTo(x + (x < 75 ? -2 : 2), 78);
            Shade.stroke(img, tr(at, crease), 1.2f, Shade.ramp(new Color(150, 120, 80), 3));
        }
        Shade.part(img, tr(at, new Rectangle2D.Double(49, 8, 52, 7)), Shade.ramp(new Color(180, 148, 102), 4), 2);
        Path2D rim = new Path2D.Double();
        rim.moveTo(50, 77);
        rim.quadTo(62, 74, 75, 78);
        rim.quadTo(88, 82, 100, 77);
        Shade.stroke(img, tr(at, rim), 5, paper);
        eye(img, tr(at, new Ellipse2D.Double(59, 37, 10, 9)), 63 + hx, 40 + hy, t);
        eye(img, tr(at, new Ellipse2D.Double(81, 38, 10, 8)), 85 + hx, 41 + hy, t + 1);
        for (double a = 0; a < 6.28; a += 0.5) {                            // torn edges
            Shade.px(img, (int) (64 + Math.cos(a) * 6) + hx, (int) (41 + Math.sin(a) * 5.5) + hy, paper[4]);
            Shade.px(img, (int) (86 + Math.cos(a) * 6) + hx, (int) (42 + Math.sin(a) * 5) + hy, paper[4]);
        }
        Shade.part(img, tr(at, new Ellipse2D.Double(84, 58, 10, 8)), Shade.ramp(new Color(160, 122, 74), 3), 3);   // a grease stain
    }

    /** A pillowcase tied at the neck with string, a face drawn on it in marker. */
    private static void pillow(BufferedImage img, AffineTransform at, int hx, int hy, double t) {
        Color[] cloth = Shade.ramp(new Color(204, 198, 184), 5);
        Shade.part(img, tr(at, new RoundRectangle2D.Double(48, 10, 54, 72, 22, 22)), cloth, 12);
        Shade.part(img, tr(at, new Ellipse2D.Double(56, 62, 18, 12)), Shade.ramp(new Color(186, 170, 120), 3), 4);   // an old stain
        for (int side = -1; side <= 1; side += 2) {
            int cx = 75 + side * 12;
            for (double a = 0; a < 6.28; a += 0.25) Shade.px(img, (int) Math.round(cx + Math.cos(a) * 7) + hx, (int) Math.round(40 + Math.sin(a) * 7) + hy, INK);
            eye(img, tr(at, new Ellipse2D.Double(cx - 3, 38, 6, 5)), cx - 1 + hx, 39 + hy, t);
        }
        for (int x = 56; x <= 94; x++) {                                   // a big drawn grin
            int y = 56 + (int) Math.round(8 * Math.sin((x - 56) / 38.0 * Math.PI));
            Shade.rect(img, x + hx, y + hy, 1, 2, INK);
            if (x % 5 == 0) Shade.rect(img, x + hx, y + hy - 3, 1, 3, INK);
        }
        Path2D tie = new Path2D.Double();
        tie.moveTo(50, 78);
        tie.quadTo(75, 84, 100, 78);
        Shade.stroke(img, tr(at, tie), 1.6f, ROPE);
        Shade.part(img, tr(at, new Ellipse2D.Double(92, 78, 8, 5)), ROPE, 2);
        Shade.part(img, tr(at, new Ellipse2D.Double(99, 79, 8, 5)), ROPE, 2);
    }

    /** Yours: a paper plate with two eye holes, tied on with string. */
    private static void plate(BufferedImage img, AffineTransform at, int hx, int hy, double t) {
        Shade.stroke(img, tr(at, new Line2D.Double(48, 44, 102, 44)), 1.4f, CLOTH);
        Shade.part(img, tr(at, new Ellipse2D.Double(49, 18, 52, 52)), Shade.ramp(new Color(234, 230, 216), 5), 5);
        Shade.part(img, tr(at, new Ellipse2D.Double(56, 25, 38, 38)), Shade.ramp(new Color(220, 214, 196), 4), 2);
        for (double a = 0; a < 6.28; a += 0.21) Shade.px(img, (int) Math.round(75 + Math.cos(a) * 23) + hx, (int) Math.round(44 + Math.sin(a) * 23) + hy, new Color(196, 188, 170));
        eye(img, tr(at, new Ellipse2D.Double(63, 38, 8, 6)), 66 + hx, 40 + hy, t);
        eye(img, tr(at, new Ellipse2D.Double(80, 38, 8, 6)), 83 + hx, 40 + hy, t + 1);
        Shade.rect(img, 70 + hx, 56 + hy, 10, 1, new Color(120, 112, 104));   // a pencil line for a mouth
    }

    /** A guard's mask: white, with a fixed wide smile, a curled moustache and a thin beard. */
    private static void smile(BufferedImage img, AffineTransform at, int hx, int hy, double t) {
        Path2D f = new Path2D.Double();
        f.moveTo(75, 16);
        f.curveTo(97, 16, 101, 40, 97, 56);
        f.curveTo(93, 72, 83, 82, 75, 84);
        f.curveTo(67, 82, 57, 72, 53, 56);
        f.curveTo(49, 40, 53, 16, 75, 16);
        f.closePath();
        Shade.part(img, tr(at, f), Shade.ramp(new Color(234, 226, 212), 5), 12);
        Color[] rose = Shade.ramp(new Color(214, 140, 134), 3);
        Shade.part(img, tr(at, new Ellipse2D.Double(56, 50, 11, 8)), rose, 3);
        Shade.part(img, tr(at, new Ellipse2D.Double(83, 50, 11, 8)), rose, 3);
        for (int side = -1; side <= 1; side += 2) {
            Path2D brow = new Path2D.Double();
            brow.moveTo(75 + side * 4, 34);
            brow.quadTo(75 + side * 10, 29, 75 + side * 18, 35);
            Shade.stroke(img, tr(at, brow), 1.3f, Shade.ramp(INK, 2));
            double cx = 75 + side * 11;
            Path2D e = new Path2D.Double();
            e.moveTo(cx - 6, 42);
            e.quadTo(cx, 38, cx + 6, 42);
            e.quadTo(cx, 44, cx - 6, 42);
            eye(img, tr(at, e), (int) cx - 1 + hx, 41 + hy, t);
            Path2D tash = new Path2D.Double();                              // the moustache, curling up
            tash.moveTo(75, 60);
            tash.quadTo(75 + side * 8, 58, 75 + side * 13, 60);
            tash.quadTo(75 + side * 17, 58, 75 + side * 17, 53);
            Shade.stroke(img, tr(at, tash), 2, Shade.ramp(INK, 2));
        }
        Path2D grin = new Path2D.Double();
        grin.moveTo(63, 64);
        grin.quadTo(75, 72, 87, 64);
        Shade.stroke(img, tr(at, grin), 1.2f, Shade.ramp(new Color(120, 70, 70), 2));
        Shade.part(img, tr(at, new Rectangle2D.Double(73.5, 70, 3, 11)), Shade.ramp(INK, 2), 1);   // the thin beard
    }

    // ─── Pieces ─────────────────────────────────────────────────
    private static final java.util.Map<String, BufferedImage> PIECES = new java.util.HashMap<>();

    /** A piece standing with the middle of its base at (x, y) on the canvas g draws to. */
    public static void piece(Graphics2D g, String code, int x, int y) {
        BufferedImage s = PIECES.computeIfAbsent(code, PixelArt::pieceSprite);
        g.drawImage(s, x - s.getWidth() / 2, y - s.getHeight(), null);
    }

    /** A turned wooden piece, 16 pixels wide and up to 26 tall, shaded like everything else. */
    private static BufferedImage pieceSprite(String code) {
        boolean white = code.charAt(0) == 'w';
        char kind = code.charAt(1);
        Color[] wood = Shade.ramp(white ? new Color(222, 200, 160) : new Color(64, 46, 50), 5);
        BufferedImage img = new BufferedImage(18, 28, BufferedImage.TYPE_INT_ARGB);
        int base = 27;
        Shade.part(img, new Ellipse2D.Double(2, base - 5, 14, 5), wood, 2, true, 0.2);                 // foot
        int top = kind == 'K' ? 2 : kind == 'Q' ? 4 : kind == 'B' ? 7 : kind == 'N' ? 7 : kind == 'R' ? 9 : 13;
        Shade.part(img, new RoundRectangle2D.Double(6, top + 6, 6, base - top - 9, 4, 4), wood, 3, true, 0.2);   // stem
        switch (kind) {
            case 'K':
                Shade.part(img, new Ellipse2D.Double(4, top + 5, 10, 7), wood, 3, true, 0.2);
                Shade.part(img, new Rectangle2D.Double(8, top - 1, 2, 7), wood, 1, true, 0.2);
                Shade.part(img, new Rectangle2D.Double(6, top + 1, 6, 2), wood, 1, true, 0.2);
                break;
            case 'Q':
                Shade.part(img, new Ellipse2D.Double(4, top + 3, 10, 8), wood, 3, true, 0.2);
                for (int i = 0; i < 4; i++) Shade.part(img, new Ellipse2D.Double(4 + i * 3, top, 3, 3), wood, 1, true, 0.2);
                break;
            case 'R':
                Shade.part(img, new Rectangle2D.Double(4, top, 10, 8), wood, 3, true, 0.2);
                break;
            case 'B':
                Shade.part(img, new Ellipse2D.Double(5, top, 8, 11), wood, 4, true, 0.2);
                break;
            case 'N':
                Path2D horse = new Path2D.Double();
                horse.moveTo(6, top + 12);
                horse.curveTo(2, top + 10, 3, top + 4, 7, top + 2);
                horse.lineTo(10, top - 1);
                horse.curveTo(15, top + 3, 14, top + 9, 12, top + 12);
                horse.closePath();
                Shade.part(img, horse, wood, 3, true, 0.2);
                break;
            default:
                Shade.part(img, new Ellipse2D.Double(5, top, 8, 8), wood, 4, true, 0.2);
                break;
        }
        if (kind == 'R') for (int i = 0; i < 3; i++) Shade.rect(img, 5 + i * 3, top, 2, 2, new Color(0, 0, 0, 0));
        if (kind == 'B') Shade.rect(img, 9, top + 3, 1, 3, wood[0]);
        if (kind == 'N') Shade.px(img, 8, top + 4, wood[0]);
        return img;
    }
}
