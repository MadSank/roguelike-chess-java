import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Card faces, drawn in code: an aged card in a frame whose metal tells its rarity (iron, blued steel,
 * gold with gems), a dark window with an emblem for the card, and an engraved band below. No text on
 * the card itself: the name lives on the price tag and in the hover line.
 *
 * card(c, w, h) returns a face at any size; the design is drawn at 48 x 64 (the counter size) and at
 * 96 x 128 for close looks, and shrunk smoothly for the little cards held in your fingers.
 */
public final class CardArt {
    private CardArt() {}

    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    public static BufferedImage card(Card c, int w, int h) {
        return CACHE.computeIfAbsent(c.getName() + "@" + w + "x" + h, k -> {
            int scale = w >= 96 ? 2 : 1;
            BufferedImage base = face(c, scale);
            if (base.getWidth() == w && base.getHeight() == h) return base;
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(base, 0, 0, w, h, null);
            g.dispose();
            return out;
        });
    }

    private static Color[] metal(Card c) {
        switch (c.getRarity()) {
            case RARE: return Shade.ramp(new Color(196, 146, 56), 5);
            case UNCOMMON: return Shade.ramp(new Color(110, 132, 160), 5);
            default: return Shade.ramp(new Color(96, 92, 96), 5);
        }
    }

    private static int hash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7fffffff;
    }

    /** The whole face at 48 x 64 times scale. */
    private static BufferedImage face(Card c, int s) {
        int W = 48 * s, H = 64 * s;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Px.pen(img);
        Color[] m = metal(c);
        Px.rect(g, 0, 0, W, H, new Color(14, 10, 12));
        Shade.part(img, new RoundRectangle2D.Double(1, 1, W - 2, H - 2, 4 * s, 4 * s), m, 2 * s);            // the frame
        Color paper = new Color(198, 182, 146);
        Px.rect(g, 4 * s, 4 * s, W - 8 * s, H - 8 * s, paper);
        for (int y = 4 * s; y < H - 4 * s; y++) for (int x = 4 * s; x < W - 4 * s; x++) {                     // age spots
            int h = hash(x / s, y / s + c.getName().length());
            if (h % 37 == 0) Px.dot(g, x, y, new Color(170, 150, 112));
        }
        Rectangle win = new Rectangle(6 * s, 6 * s, 36 * s, 34 * s);
        Px.rect(g, win.x - s, win.y - s, win.width + 2 * s, win.height + 2 * s, m[0]);
        window(img, g, win, c, s);
        // the engraved band: a gem for the rarity, scrollwork either side
        int gy = 47 * s;
        for (int x = 8 * s; x < W - 8 * s; x++) {
            double k = (x - 8.0 * s) / (W - 16.0 * s);
            int y = gy + (int) Math.round(Math.sin(k * Math.PI * 4) * 2 * s);
            if (Math.abs(x - W / 2) > 5 * s) Px.rect(g, x, y, 1, s, new Color(110, 90, 66));
        }
        Color gem = c.getRarity() == CardCatalog.Rarity.RARE ? new Color(200, 40, 60) : c.getRarity() == CardCatalog.Rarity.UNCOMMON ? new Color(70, 140, 220) : new Color(130, 130, 120);
        Shade.part(img, new Ellipse2D.Double(W / 2.0 - 4 * s, gy - 4 * s, 8 * s, 8 * s), Shade.ramp(gem, 4), 2 * s);
        Px.rect(g, W / 2 - s, gy - 3 * s, s, s, new Color(255, 255, 240));
        if (c.isGlobal()) for (int i = -1; i <= 1; i += 2) {                                                 // two eyes: it touches both sides
            Shade.part(img, new Ellipse2D.Double(W / 2.0 + i * 14 * s - 3 * s, 55 * s, 6 * s, 3 * s), Shade.ramp(new Color(60, 40, 40), 2), 0, false, 1);
            Px.rect(g, W / 2 + i * 14 * s, 56 * s, s, s, new Color(220, 200, 160));
        } else Px.rect(g, W / 2 - 6 * s, 56 * s, 12 * s, s, new Color(110, 90, 66));
        for (int[] k : new int[][]{{2, 2}, {W / s - 4, 2}, {2, H / s - 4}, {W / s - 4, H / s - 4}}) {        // rivets, gems on rare cards
            Color rv = c.getRarity() == CardCatalog.Rarity.RARE ? gem : m[4];
            Px.rect(g, k[0] * s, k[1] * s, 2 * s, 2 * s, rv);
        }
        g.dispose();
        return img;
    }

    // ─── The window and its emblem ──────────────────────────────
    /** Night behind the emblem, lit from below like everything else in the room. */
    private static void window(BufferedImage img, Graphics2D g, Rectangle r, Card c, int s) {
        int[][] bayer = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};
        Color[] night = {new Color(14, 12, 22), new Color(26, 20, 34), new Color(44, 30, 42), new Color(70, 42, 44)};
        for (int y = r.y; y < r.y + r.height; y++) for (int x = r.x; x < r.x + r.width; x++) {
            double v = Math.max(0, 1 - Math.hypot((x - r.getCenterX()) / r.width, (y - r.y - r.height * 0.85) / r.height) * 1.4);
            int k = (int) Math.max(0, Math.min(3, Math.round(v * 3 + bayer[y & 3][x & 3] / 16.0 - 0.47)));
            img.setRGB(x, y, night[k].getRGB());
        }
        Graphics2D a = (Graphics2D) g.create();
        a.translate(r.x, r.y);
        a.scale(s, s);
        a.clip(new Rectangle(0, 0, 36, 34));
        emblem(a, img, r, c.getName(), s);
        a.dispose();
    }

    // helpers in the 36 x 34 emblem space (scaled with s onto the image)
    private static Shape at(Rectangle r, int s, Shape sh) {
        AffineTransform t = new AffineTransform();
        t.translate(r.x, r.y);
        t.scale(s, s);
        return t.createTransformedShape(sh);
    }

    private static void part(BufferedImage img, Rectangle r, int s, Shape sh, Color base, double round) {
        Shade.part(img, at(r, s, sh), Shade.ramp(base, 5), round * s);
    }

    private static void line(BufferedImage img, Rectangle r, int s, double x1, double y1, double x2, double y2, float w, Color c) {
        Shade.stroke(img, at(r, s, new Line2D.Double(x1, y1, x2, y2)), w * s, Shade.ramp(c, 3));
    }

    private static final Color BONE = new Color(222, 208, 176), INK = new Color(30, 22, 26), GOLD = new Color(220, 170, 60);

    /** A chess piece silhouette standing with its base centre at (x, y), h tall, in a colour. */
    private static void piece(BufferedImage img, Rectangle r, int s, char kind, double x, double y, double h, Color col) {
        double w = h * 0.5;
        part(img, r, s, new Ellipse2D.Double(x - w * 0.6, y - h * 0.14, w * 1.2, h * 0.16), col, 1);
        part(img, r, s, new RoundRectangle2D.Double(x - w * 0.22, y - h * 0.7, w * 0.44, h * 0.62, 2, 2), col, 1);
        switch (kind) {
            case 'K':
                part(img, r, s, new Ellipse2D.Double(x - w * 0.42, y - h * 0.82, w * 0.84, h * 0.24), col, 1);
                part(img, r, s, new Rectangle2D.Double(x - w * 0.08, y - h * 1.02, w * 0.16, h * 0.24), col, 0.5);
                part(img, r, s, new Rectangle2D.Double(x - w * 0.24, y - h * 0.96, w * 0.48, h * 0.08), col, 0.5);
                break;
            case 'Q':
                part(img, r, s, new Ellipse2D.Double(x - w * 0.42, y - h * 0.86, w * 0.84, h * 0.26), col, 1);
                for (int i = 0; i < 4; i++) part(img, r, s, new Ellipse2D.Double(x - w * 0.45 + i * w * 0.27, y - h * 0.98, w * 0.18, h * 0.12), col, 0.5);
                break;
            case 'R':
                part(img, r, s, new Rectangle2D.Double(x - w * 0.42, y - h * 0.92, w * 0.84, h * 0.3), col, 1);
                for (int i = 0; i < 3; i++) part(img, r, s, new Rectangle2D.Double(x - w * 0.42 + i * w * 0.32, y - h * 1.0, w * 0.2, h * 0.1), col, 0.5);
                break;
            case 'B':
                part(img, r, s, new Ellipse2D.Double(x - w * 0.34, y - h * 0.98, w * 0.68, h * 0.42), col, 1.5);
                line(img, r, s, x + w * 0.05, y - h * 0.9, x + w * 0.18, y - h * 0.74, 0.7f, INK);
                break;
            case 'N': {                                                      // a horse's head, facing left
                Path2D p = new Path2D.Double();
                p.moveTo(x + w * 0.4, y - h * 0.36);
                p.curveTo(x + w * 0.5, y - h * 0.7, x + w * 0.35, y - h * 0.96, x + w * 0.06, y - h * 1.06);   // the mane up to the ears
                p.lineTo(x - w * 0.02, y - h * 1.16);
                p.lineTo(x - w * 0.14, y - h * 1.04);
                p.curveTo(x - w * 0.4, y - h * 0.98, x - w * 0.62, y - h * 0.86, x - w * 0.7, y - h * 0.74);   // forehead to the muzzle
                p.lineTo(x - w * 0.66, y - h * 0.64);
                p.curveTo(x - w * 0.46, y - h * 0.62, x - w * 0.24, y - h * 0.68, x - w * 0.1, y - h * 0.66);  // the jaw
                p.curveTo(x - w * 0.22, y - h * 0.56, x - w * 0.3, y - h * 0.46, x - w * 0.3, y - h * 0.36);   // the throat
                p.closePath();
                part(img, r, s, p, col, 1.5);
                part(img, r, s, new Ellipse2D.Double(x - w * 0.24, y - h * 0.92, w * 0.12, h * 0.06), INK, 0);   // the eye
                break;
            }
            default:
                part(img, r, s, new Ellipse2D.Double(x - w * 0.32, y - h * 0.94, w * 0.64, h * 0.34), col, 1.5);
        }
    }

    private static void arrow(BufferedImage img, Rectangle r, int s, double x, double y, double dx, double dy, Color c) {
        line(img, r, s, x, y, x + dx, y + dy, 1.6f, c);
        double a = Math.atan2(dy, dx);
        Path2D head = new Path2D.Double();
        head.moveTo(x + dx + Math.cos(a) * 3, y + dy + Math.sin(a) * 3);
        head.lineTo(x + dx + Math.cos(a + 2.4) * 4, y + dy + Math.sin(a + 2.4) * 4);
        head.lineTo(x + dx + Math.cos(a - 2.4) * 4, y + dy + Math.sin(a - 2.4) * 4);
        head.closePath();
        part(img, r, s, head, c, 0.5);
    }

    private static void emblem(Graphics2D a, BufferedImage img, Rectangle r, String name, int s) {
        Color red = new Color(170, 30, 36), pale = new Color(170, 220, 230), green = new Color(90, 200, 120);
        switch (name) {
            case "Golden Edge":                                               // a coin with a blade's edge and a glint
                part(img, r, s, new Ellipse2D.Double(8, 6, 20, 20), GOLD, 4);
                part(img, r, s, new Ellipse2D.Double(12, 10, 12, 12), new Color(180, 130, 40), 2);
                piece(img, r, s, 'P', 18, 20, 9, GOLD);
                line(img, r, s, 4, 30, 32, 26, 1.4f, new Color(210, 210, 220));
                line(img, r, s, 26, 5, 30, 9, 0.8f, Color.WHITE);
                line(img, r, s, 30, 5, 26, 9, 0.8f, Color.WHITE);
                break;
            case "Pawn Phalanx":                                              // three pawns shoulder to shoulder behind shields
                for (int i = 0; i < 3; i++) {
                    piece(img, r, s, 'P', 9 + i * 9, 26, 16, BONE);
                    part(img, r, s, new RoundRectangle2D.Double(5 + i * 9, 20, 8, 10, 3, 3), new Color(120, 120, 130), 1.5);
                }
                line(img, r, s, 3, 31, 33, 31, 1f, new Color(90, 70, 50));
                break;
            case "Cavalry Charge":                                            // a knight at the gallop
                piece(img, r, s, 'N', 22, 30, 24, BONE);
                for (int i = 0; i < 4; i++) line(img, r, s, 2, 10 + i * 5, 10 - i, 10 + i * 5, 1f, new Color(200, 180, 140));
                break;
            case "Pawn Surge":
                piece(img, r, s, 'P', 18, 32, 14, BONE);
                arrow(img, r, s, 18, 16, 0, -7, GOLD);
                arrow(img, r, s, 18, 10, 0, -5, GOLD);
                break;
            case "Pawn Retreat":
                piece(img, r, s, 'P', 18, 22, 14, BONE);
                arrow(img, r, s, 18, 24, 0, 6, pale);
                break;
            case "Bishop Shift":                                              // one step aside, onto the other colour
                part(img, r, s, new Rectangle2D.Double(3, 20, 15, 12), new Color(70, 50, 40), 0);
                part(img, r, s, new Rectangle2D.Double(18, 20, 15, 12), new Color(190, 170, 130), 0);
                piece(img, r, s, 'B', 18, 26, 20, BONE);
                arrow(img, r, s, 10, 9, -6, 0, GOLD);
                arrow(img, r, s, 26, 9, 6, 0, GOLD);
                break;
            case "Sniper Bishops":                                            // in the crosshairs
                piece(img, r, s, 'B', 18, 30, 22, BONE);
                Shade.stroke(img, at(r, s, new Ellipse2D.Double(8, 6, 20, 20)), 1.2f * s, Shade.ramp(red, 3));
                line(img, r, s, 18, 2, 18, 30, 0.8f, red);
                line(img, r, s, 4, 16, 32, 16, 0.8f, red);
                break;
            case "Vaulting Majors":                                           // a rook leaping a pawn
                piece(img, r, s, 'P', 18, 32, 11, new Color(120, 110, 100));
                piece(img, r, s, 'R', 26, 18, 14, BONE);
                Shade.stroke(img, at(r, s, new Arc2D.Double(4, 6, 24, 30, 20, 140, Arc2D.OPEN)), 1f * s, Shade.ramp(GOLD, 3));
                break;
            case "King's Stride":                                             // a king and two long steps
                piece(img, r, s, 'K', 12, 32, 22, GOLD);
                for (int i = 0; i < 2; i++) part(img, r, s, new Ellipse2D.Double(20 + i * 7, 24 - i * 8, 4, 6), new Color(140, 120, 90), 0.5);
                break;
            case "Soul Harvest":                                              // a scythe and a soul drawn off a piece
                line(img, r, s, 10, 32, 24, 4, 1.5f, new Color(110, 80, 50));
                part(img, r, s, new Arc2D.Double(12, 0, 20, 14, 0, 180, Arc2D.CHORD), new Color(200, 200, 210), 1);
                for (int i = 0; i < 5; i++) part(img, r, s, new Ellipse2D.Double(24 - i * 2, 28 - i * 4, 4 + i, 3 + i), pale, 0.5);
                break;
            case "Necromancer's Gambit":                                        // a skull, green light rising out of a grave
                part(img, r, s, new Rectangle2D.Double(4, 26, 28, 8), new Color(50, 40, 34), 0.5);
                for (int i = 0; i < 6; i++) line(img, r, s, 8 + i * 4, 28, 9 + i * 4, 12 + (i % 2) * 4, 0.8f, green);
                part(img, r, s, new Ellipse2D.Double(11, 6, 14, 13), BONE, 3);
                part(img, r, s, new Rectangle2D.Double(14, 16, 8, 5), BONE, 1);
                part(img, r, s, new Ellipse2D.Double(13, 10, 4, 4), INK, 0);
                part(img, r, s, new Ellipse2D.Double(19, 10, 4, 4), INK, 0);
                break;
            case "Phantom King": {                                            // a king you can see through
                piece(img, r, s, 'K', 18, 32, 26, pale);
                for (int y = r.y; y < r.y + r.height; y += 2) for (int x = r.x; x < r.x + r.width; x++) {
                    int c = img.getRGB(x, y);
                    if (new Color(c).getBlue() > 150 && ((x + y) & 3) == 0) img.setRGB(x, y, new Color(26, 20, 34).getRGB());
                }
                break;
            }
            case "Time Stop":                                                 // an hourglass with the sand frozen mid-fall
                part(img, r, s, new Rectangle2D.Double(9, 3, 18, 3), new Color(110, 80, 50), 0.5);
                part(img, r, s, new Rectangle2D.Double(9, 29, 18, 3), new Color(110, 80, 50), 0.5);
                Path2D glass = new Path2D.Double();
                glass.moveTo(11, 6); glass.lineTo(25, 6); glass.lineTo(19, 17); glass.lineTo(25, 29); glass.lineTo(11, 29); glass.lineTo(17, 17); glass.closePath();
                part(img, r, s, glass, new Color(150, 170, 180), 1);
                part(img, r, s, new Polygon(new int[]{13, 23, 18}, new int[]{8, 8, 15}, 3), GOLD, 0.5);
                for (int i = 0; i < 3; i++) part(img, r, s, new Ellipse2D.Double(17.4, 17 + i * 3, 1.4, 1.4), GOLD, 0);
                part(img, r, s, new Polygon(new int[]{13, 23, 18}, new int[]{29, 29, 25}, 3), GOLD, 0.5);
                break;
            case "Trench Warfare":                                            // pawns dug in behind sandbags
                for (int i = 0; i < 3; i++) piece(img, r, s, 'P', 8 + i * 10, 22, 12, BONE);
                for (int i = 0; i < 5; i++) part(img, r, s, new Ellipse2D.Double(2 + i * 7, 20, 9, 6), new Color(140, 120, 80), 2);
                part(img, r, s, new Rectangle2D.Double(0, 26, 36, 8), new Color(60, 44, 32), 0.5);
                break;
            case "Glass Cannons":                                             // a piece of glass, cracking
                piece(img, r, s, 'Q', 18, 32, 26, new Color(170, 210, 220));
                line(img, r, s, 14, 10, 20, 18, 0.7f, Color.WHITE);
                line(img, r, s, 20, 18, 16, 24, 0.7f, Color.WHITE);
                line(img, r, s, 20, 18, 24, 22, 0.7f, Color.WHITE);
                break;
            case "Minefield":                                                 // a spiked mine on the squares, a skull above
                for (int i = 0; i < 4; i++) part(img, r, s, new Rectangle2D.Double(2 + i * 8, 24, 8, 8), (i % 2 == 0) ? new Color(70, 50, 40) : new Color(170, 150, 110), 0);
                part(img, r, s, new Ellipse2D.Double(13, 18, 10, 8), new Color(60, 60, 64), 2);
                for (int i = 0; i < 5; i++) line(img, r, s, 18, 22, 18 + Math.cos(i * 1.26 - 1.6) * 7, 22 + Math.sin(i * 1.26 - 1.6) * 6, 0.8f, new Color(90, 90, 96));
                part(img, r, s, new Ellipse2D.Double(13, 3, 10, 9), BONE, 2);
                part(img, r, s, new Ellipse2D.Double(14.5, 6, 3, 3), INK, 0);
                part(img, r, s, new Ellipse2D.Double(18.5, 6, 3, 3), INK, 0);
                break;
            case "Heavy Armor":                                               // a battered shield
                Path2D sh = new Path2D.Double();
                sh.moveTo(8, 4); sh.lineTo(28, 4); sh.lineTo(28, 16); sh.curveTo(28, 26, 18, 32, 18, 32); sh.curveTo(18, 32, 8, 26, 8, 16); sh.closePath();
                part(img, r, s, sh, new Color(130, 130, 140), 3);
                line(img, r, s, 18, 5, 18, 30, 1f, new Color(90, 90, 100));
                line(img, r, s, 9, 13, 27, 13, 1f, new Color(90, 90, 100));
                for (int i = 0; i < 4; i++) part(img, r, s, new Ellipse2D.Double(10 + (i % 2) * 14, 6 + (i / 2) * 14, 2, 2), new Color(200, 200, 210), 0);
                break;
            case "Blood Moon":                                                // a red moon over black hills
                part(img, r, s, new Ellipse2D.Double(9, 3, 18, 18), new Color(190, 40, 40), 4);
                part(img, r, s, new Ellipse2D.Double(14, 7, 4, 3), new Color(150, 30, 30), 0.5);
                Path2D hills = new Path2D.Double();
                hills.moveTo(0, 34); hills.lineTo(0, 24); hills.curveTo(8, 18, 14, 26, 20, 22); hills.curveTo(26, 18, 32, 24, 36, 22); hills.lineTo(36, 34); hills.closePath();
                part(img, r, s, hills, new Color(16, 12, 16), 0);
                break;
            case "Chrono Shift":                                              // a clock face with the hands turning back
                part(img, r, s, new Ellipse2D.Double(6, 4, 24, 24), new Color(200, 186, 150), 3);
                for (int i = 0; i < 12; i++) part(img, r, s, new Ellipse2D.Double(17.4 + Math.cos(i * Math.PI / 6) * 9, 15.4 + Math.sin(i * Math.PI / 6) * 9, 1.2, 1.2), INK, 0);
                line(img, r, s, 18, 16, 18, 9, 1f, INK);
                line(img, r, s, 18, 16, 23, 18, 1f, INK);
                Shade.stroke(img, at(r, s, new Arc2D.Double(3, 1, 30, 30, 200, 110, Arc2D.OPEN)), 1.2f * s, Shade.ramp(GOLD, 3));
                arrow(img, r, s, 7, 22, -1, -4, GOLD);
                break;
            default:
                piece(img, r, s, 'K', 18, 32, 24, BONE);
        }
    }
}
