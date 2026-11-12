import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * The pixel sprites, all drawn in code: the eight masked characters (upper body, as if behind a table
 * or counter), the chess pieces, and the props. Each character is drawn into a small image at true
 * pixel size, then blown up whole, so every pixel stays the same size.
 *
 * Masks (all covering the whole face): GAS, PLAGUE, HOCKEY, SACK, WELDING (opponents);
 * BAUTA, FENCING, BALACLAVA (dealers).
 */
public final class PixelArt {
    private PixelArt() {}

    public enum Mask { GAS, PLAGUE, HOCKEY, SACK, WELDING, BAUTA, FENCING, BALACLAVA }

    /** One person: mask, robe colour, whether the hood is up, gloves, and a voice (blip pitch). */
    public static final class Person {
        public final String name;
        public final Mask mask;
        public final Color robe;
        public final boolean hood, gloves;
        public final int voice;

        public Person(String name, Mask mask, Color robe, boolean hood, boolean gloves, int voice) {
            this.name = name;
            this.mask = mask;
            this.robe = robe;
            this.hood = hood;
            this.gloves = gloves;
            this.voice = voice;
        }
    }

    public static final Person[] OPPONENTS = {
        new Person("The Gasman", Mask.GAS, new Color(92, 72, 56), true, false, 180),
        new Person("The Doctor", Mask.PLAGUE, new Color(120, 34, 36), false, true, 300),
        new Person("The Goalie", Mask.HOCKEY, new Color(128, 106, 72), true, false, 240),
        new Person("The Sack", Mask.SACK, new Color(60, 70, 92), false, false, 140),
        new Person("The Welder", Mask.WELDING, new Color(58, 50, 56), false, true, 110),
    };
    public static final Person CARD_DEALER = new Person("The Card Dealer", Mask.BAUTA, new Color(70, 54, 46), true, false, 360);
    public static final Person PIECE_DEALER = new Person("The Piece Dealer", Mask.FENCING, new Color(112, 92, 66), false, true, 200);
    public static final Person RESELLER = new Person("The Reseller", Mask.BALACLAVA, new Color(86, 58, 100), true, false, 260);

    public static final int SPRITE_W = 80, SPRITE_H = 72;

    // ─── Pose ───────────────────────────────────────────────────
    private static int[] pose(double t, String reaction, double p) {
        int bodyY = ((int) (t * 1.5)) % 2;                        // breathing: the body rises a pixel
        int headX = 0, headY = 0, shake = 0;
        if (reaction != null) {
            double bell = Math.sin(Math.PI * p);
            double hold = smooth(0, 0.2, p) * (1 - smooth(0.8, 1, p));
            switch (reaction) {
                case "think": headY = (int) Math.round(2 * bell); headX = (int) Math.round(1 * bell); break;
                case "capture": headY = (int) Math.round(1 * hold); bodyY += (int) Math.round(1 * hold); break;
                case "hurt": headY = -(int) Math.round(3 * Math.sin(Math.PI * Math.sqrt(p))); shake = (int) Math.round(Math.sin(p * 40) * 2 * (1 - p)); break;
                case "check": headY = (int) Math.round(2 * hold); bodyY += (int) Math.round(1 * hold); break;
                case "win": headY = -(int) Math.round(2 * hold); shake = (int) Math.round(Math.sin(p * 60) * hold); break;
                case "lose": headY = (int) Math.round(5 * hold); bodyY += (int) Math.round(2 * hold); break;
                case "yes": headY = (int) Math.round(2 * bell * (0.5 - 0.5 * Math.cos(p * 4 * Math.PI))); break;
                case "no": headX = (int) Math.round(2 * bell * Math.sin(p * 6 * Math.PI)); break;
                default: break;
            }
        }
        return new int[]{bodyY, headX, headY, shake};
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** The person's upper body as a small image (SPRITE_W x SPRITE_H), hands resting at the bottom edge. */
    public static BufferedImage person(Person who, double t, String reaction, double p) {
        BufferedImage img = new BufferedImage(SPRITE_W, SPRITE_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Px.pen(img);
        int[] o = pose(t, reaction, p);
        int by = o[0], hx = o[1] + o[3], hy = o[2] + o[0];
        g.translate(o[3], 0);
        robe(g, who, by);
        g.translate(hx - o[3], hy);
        head(g, who, t);
        g.dispose();
        return img;
    }

    private static void outlined(Graphics2D g, Polygon p, Color fill) {
        g.setColor(fill);
        g.fillPolygon(p);
        g.setColor(Px.BLACK);
        g.drawPolygon(p);
    }

    private static void outlinedOval(Graphics2D g, int x, int y, int w, int h, Color fill) {
        g.setColor(fill);
        g.fillOval(x, y, w, h);
        g.setColor(Px.BLACK);
        g.drawOval(x, y, w, h);
    }

    private static void robe(Graphics2D g, Person who, int by) {
        Color dark = who.robe.darker(), darker = dark.darker();
        Polygon body = new Polygon(new int[]{5, 14, 26, 54, 66, 75}, new int[]{71, 42 + by, 35 + by, 35 + by, 42 + by, 71}, 6);
        outlined(g, body, who.robe);
        g.setColor(dark);                                          // folds
        for (int x : new int[]{24, 33, 47, 56}) g.drawLine(x, 44 + by, x + (x < 40 ? -3 : 3), 70);
        g.setColor(darker);
        g.fillRect(36, 38 + by, 8, 33);                              // the shadow under the chin
        // Arms down to the hands on the table edge.
        Polygon armL = new Polygon(new int[]{14, 24, 30, 18}, new int[]{44 + by, 46 + by, 67, 67}, 4);
        Polygon armR = new Polygon(new int[]{66, 56, 50, 62}, new int[]{44 + by, 46 + by, 67, 67}, 4);
        outlined(g, armL, dark);
        outlined(g, armR, dark);
        Color skin = who.gloves ? new Color(36, 30, 30) : Px.BONE;
        for (int hx : new int[]{17, 51}) {
            g.setColor(Px.BLACK);
            g.fillRect(hx - 1, 65, 14, 7);
            g.setColor(skin);
            g.fillRect(hx, 66, 12, 4);
            for (int f = 0; f < 4; f++) g.fillRect(hx + 1 + f * 3, 70, 2, 2);   // fingers
            g.setColor(Px.BLACK);
            for (int f = 0; f < 3; f++) g.fillRect(hx + 3 + f * 3, 70, 1, 2);
        }
    }

    /** A black eye hole with a white glint that comes and goes, so something looks back. */
    private static void eye(Graphics2D g, int x, int y, int w, int h, double t) {
        g.setColor(Px.BLACK);
        g.fillRect(x, y, w, h);
        if (Math.sin(t * 2.3 + x) > -0.6) {
            g.setColor(Px.WHITE);
            g.fillRect(x + (w > 2 ? 1 : 0), y, 1, 1);
        }
    }

    private static void head(Graphics2D g, Person who, double t) {
        if (who.hood) {
            outlinedOval(g, 23, 4, 34, 40, who.robe.darker());
            g.setColor(Px.BLACK);
            g.fillOval(27, 9, 26, 32);
        } else {
            g.setColor(Px.BLACK);
            g.fillRect(36, 33, 8, 4);                                // the neck in shadow
        }
        switch (who.mask) {
            case GAS: gas(g, t); break;
            case PLAGUE: plague(g, t); break;
            case HOCKEY: hockey(g, t); break;
            case SACK: sack(g, t); break;
            case WELDING: welding(g, t); break;
            case BAUTA: bauta(g, t); break;
            case FENCING: fencing(g, t); break;
            case BALACLAVA: balaclava(g, t); break;
            default: break;
        }
    }

    private static void gas(Graphics2D g, double t) {
        outlinedOval(g, 28, 9, 24, 28, new Color(96, 98, 80));
        g.setColor(new Color(70, 72, 58));
        g.fillRect(29, 27, 22, 8);
        for (int side = -1; side <= 1; side += 2) {
            int cx = 40 + side * 6;
            outlinedOval(g, cx - 4, 17, 8, 8, Px.GOLD);
            g.setColor(Px.BLACK);
            g.fillOval(cx - 2, 19, 5, 5);
            eye(g, cx - 1, 20, 2, 2, t);
        }
        outlinedOval(g, 36, 27, 8, 8, Px.STEEL);                    // the filter snout
        g.setColor(Px.DARK);
        for (int i = 0; i < 3; i++) g.fillRect(38 + i * 2, 29, 1, 5);
        g.setColor(Px.BLACK);                                        // the hose
        g.fillRect(41, 35, 4, 4);
        g.fillRect(43, 38, 4, 5);
        g.fillRect(45, 42, 4, 6);
        g.setColor(new Color(80, 82, 70));
        g.fillRect(42, 36, 2, 2);
        g.fillRect(44, 39, 2, 3);
        g.fillRect(46, 43, 2, 4);
    }

    private static void plague(Graphics2D g, double t) {
        outlinedOval(g, 29, 12, 22, 26, Px.LEATHER);
        for (int side = -1; side <= 1; side += 2) {
            int cx = 40 + side * 5;
            outlinedOval(g, cx - 3, 18, 6, 6, Px.GOLD);
            eye(g, cx - 1, 20, 2, 2, t);
        }
        Polygon beak = new Polygon(new int[]{36, 44, 42, 40}, new int[]{25, 25, 40, 46}, 4);
        outlined(g, beak, new Color(120, 86, 60));
        g.setColor(new Color(80, 56, 40));
        g.fillPolygon(new int[]{40, 44, 42, 40}, new int[]{25, 25, 40, 46}, 4);
        Color felt = new Color(40, 36, 42), edge = new Color(78, 72, 80);
        g.setColor(Px.BLACK);                                        // the hat: crown, band, brim, outlined
        g.fillRect(30, 1, 20, 11);
        g.fillRect(19, 10, 42, 4);
        g.setColor(felt);
        g.fillRect(31, 2, 18, 9);
        g.fillRect(20, 11, 40, 2);
        g.setColor(edge);
        g.fillRect(31, 2, 18, 1);
        g.fillRect(20, 11, 40, 1);
        g.setColor(Px.DRED);
        g.fillRect(31, 8, 18, 2);
    }

    private static void hockey(Graphics2D g, double t) {
        outlinedOval(g, 30, 10, 20, 27, Px.BONE);
        g.setColor(Px.BONE2);
        g.fillRect(31, 28, 18, 6);
        eye(g, 33, 19, 4, 3, t);
        eye(g, 43, 19, 4, 3, t);
        g.setColor(Px.BLACK);
        int[][] dots = {{40, 13}, {37, 15}, {43, 15}, {36, 26}, {40, 26}, {44, 26}, {34, 29}, {38, 29}, {42, 29}, {46, 29},
            {36, 32}, {40, 32}, {44, 32}, {40, 35}};
        for (int[] d : dots) g.fillRect(d[0], d[1], 1, 1);
        g.setColor(Px.BONE2);                                        // scuffs
        g.fillRect(33, 14, 2, 1);
        g.fillRect(45, 24, 2, 1);
    }

    private static void sack(Graphics2D g, double t) {
        Polygon bag = new Polygon(new int[]{27, 29, 33, 40, 47, 51, 53, 51, 46, 34, 29},
            new int[]{30, 8, 5, 8, 5, 8, 30, 38, 40, 40, 38}, 11);
        outlined(g, bag, Px.BURLAP);
        g.setColor(Px.BURLAP2);                                      // weave
        for (int y = 10; y < 38; y += 3) {
            for (int x = 30 + (y % 2); x < 51; x += 4) g.fillRect(x, y, 1, 1);
        }
        eye(g, 33, 18, 4, 4, t);
        eye(g, 43, 17, 4, 5, t);
        g.setColor(Px.BLACK);
        g.fillRect(34, 29, 12, 1);                                   // stitched mouth
        for (int x = 35; x <= 45; x += 2) g.fillRect(x, 28, 1, 3);
        g.setColor(new Color(90, 64, 34));                           // the rope at the neck
        g.fillRect(30, 37, 20, 3);
        g.setColor(Px.BLACK);
        g.drawRect(30, 37, 20, 3);
        g.setColor(new Color(90, 64, 34));
        g.fillRect(42, 40, 2, 5);
    }

    private static void welding(Graphics2D g, double t) {
        g.setColor(new Color(64, 62, 62));
        g.fillRoundRect(29, 8, 22, 31, 8, 8);
        g.setColor(Px.BLACK);
        g.drawRoundRect(29, 8, 22, 31, 8, 8);
        g.setColor(new Color(44, 42, 42));
        g.fillRect(30, 30, 20, 7);
        g.setColor(Px.BLACK);
        g.fillRect(32, 17, 16, 6);
        g.setColor(new Color(40, 90, 60));
        g.fillRect(33, 18, 14, 4);
        if (Math.sin(t * 1.7) > -0.3) {
            g.setColor(new Color(150, 230, 170));
            g.fillRect(34, 18, 3, 1);
        }
        g.setColor(Px.STEEL);
        for (int[] r : new int[][]{{31, 11}, {48, 11}, {31, 35}, {48, 35}}) g.fillRect(r[0], r[1], 1, 1);
    }

    private static void bauta(Graphics2D g, double t) {
        Polygon m = new Polygon(new int[]{32, 36, 44, 48, 49, 52, 28, 31}, new int[]{11, 8, 8, 11, 25, 38, 38, 25}, 8);
        outlined(g, m, Px.BONE);
        g.setColor(Px.BONE2);
        g.fillRect(30, 27, 21, 10);                                  // the jutting jaw in shade
        g.setColor(Px.BLACK);
        g.drawLine(30, 26, 50, 26);
        g.fillRect(40, 17, 1, 7);                                    // the nose ridge
        eye(g, 34, 17, 4, 2, t);
        eye(g, 43, 17, 4, 2, t);
    }

    private static void fencing(Graphics2D g, double t) {
        g.setColor(Px.BONE);
        g.fillRect(31, 34, 18, 8);                                   // the bib
        g.setColor(Px.BLACK);
        g.drawRect(31, 34, 18, 8);
        outlinedOval(g, 30, 8, 20, 28, new Color(40, 40, 44));
        g.setColor(new Color(110, 110, 116));                        // the mesh
        for (int y = 10; y < 35; y += 2) {
            for (int x = 31 + (y % 4 == 0 ? 0 : 1); x < 50; x += 2) {
                double dx = (x - 40) / 10.0, dy = (y - 22) / 14.0;
                if (dx * dx + dy * dy < 0.9) g.fillRect(x, y, 1, 1);
            }
        }
        if (Math.sin(t * 2.1) > -0.5) {
            g.setColor(Px.WHITE);
            g.fillRect(36, 19, 1, 1);
            g.fillRect(44, 19, 1, 1);
        }
    }

    private static void balaclava(Graphics2D g, double t) {
        outlinedOval(g, 29, 8, 22, 30, new Color(30, 28, 32));
        g.setColor(new Color(46, 42, 48));                           // knitted ribs
        for (int x = 31; x < 50; x += 2) g.drawLine(x, 11, x, 35);
        g.setColor(new Color(150, 126, 108));
        g.fillRect(32, 18, 16, 4);                                   // the eye slit, skin showing
        g.fillRect(36, 29, 8, 3);                                    // the mouth hole
        eye(g, 34, 19, 3, 2, t);
        eye(g, 43, 19, 3, 2, t);
        g.setColor(new Color(40, 8, 10));
        g.fillRect(37, 30, 6, 1);
    }

    // ─── Pieces ─────────────────────────────────────────────────
    private static final String[][] PIECES = {
        {"K", "....#.....", "...###....", "....#.....", "..#####...", "..#####...", "...###....", "...###....", "...###....",
            "..#####...", ".#######..", ".#######.."},
        {"Q", ".#.#.#....", ".#####....", "..###.....", "..###.....", "...#......", "..###.....", "..###.....", "..###.....",
            ".#####....", "#######...", "#######..."},
        {"R", ".#.#.#....", ".#####....", ".#####....", "..###.....", "..###.....", "..###.....", "..###.....", ".#####....",
            "#######...", "#######..."},
        {"B", "...#......", "..###.....", "..#.#.....", "..###.....", "...#......", "..###.....", "..###.....", ".#####....",
            "#######...", "#######..."},
        {"N", "..##......", ".####.....", "######....", "##.###....", "...###....", "..####....", "..###.....", ".#####....",
            "#######...", "#######..."},
        {"P", "..##......", ".####.....", ".####.....", "..##......", "..##......", ".####.....", "######....", "######...."},
    };

    /** A piece standing with its base's middle at (x, y). */
    public static void piece(Graphics2D g, String code, int x, int y) {
        boolean white = code.charAt(0) == 'w';
        String kind = code.substring(1, 2);
        for (String[] p : PIECES) {
            if (!p[0].equals(kind)) continue;
            int rows = p.length - 1, cols = 0;
            for (int r = 1; r <= rows; r++) cols = Math.max(cols, p[r].lastIndexOf('#') + 1);
            int left = x - cols / 2, top = y - rows;
            Color fill = white ? Px.BONE : new Color(56, 48, 54), shade = white ? Px.BONE2 : new Color(30, 26, 30);
            // Outline first: every empty pixel next to a filled one.
            g.setColor(Px.BLACK);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (filled(p, r, c)) g.fillRect(left + c - 1, top + r - 1, 3, 3);
                }
            }
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (!filled(p, r, c)) continue;
                    g.setColor(c >= cols * 0.6 ? shade : fill);
                    g.fillRect(left + c, top + r, 1, 1);
                }
            }
            if (!white) {                                            // a glint of lamplight on the dark pieces
                g.setColor(new Color(150, 120, 90));
                for (int r = 1; r < rows - 2; r++) if (filled(p, r, 1)) g.fillRect(left + 1, top + r, 1, 1);
            }
            return;
        }
    }

    private static boolean filled(String[] p, int r, int c) {
        String row = p[r + 1];
        return c >= 0 && c < row.length() && row.charAt(c) == '#';
    }
}
