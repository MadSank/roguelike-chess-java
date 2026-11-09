import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The realistic board rendered by tools/blender/build_board.py: an empty board image, one sprite
 * per piece, and board.properties saying where the squares are and where each sprite's base sits.
 *
 * The render camera is orthographic and only tilted, so every square is the same axis-aligned
 * rectangle and a sprite fits any square. Loads as null when the files aren't there; the board
 * view then draws its plain 2D board.
 */
public final class BoardArt {
    private static final File DIR = new File("resources" + File.separator + "board");
    private static BoardArt cached;
    private static boolean tried;

    public final int width, height;
    /** Square geometry in board.png pixels: top-left of a8, and one square's size. */
    public final double squareLeft, squareTop, squareWidth, squareHeight;
    /** Where a piece's base centre sits inside its sprite image. */
    public final double anchorX, anchorY;
    private final BufferedImage board;
    private final Map<String, BufferedImage> sprites = new HashMap<>();

    // Images pre-scaled for the current display size (rebuilt when the scale changes).
    private double scaledFor = -1;
    private BufferedImage boardScaled;
    private final Map<String, BufferedImage> spritesScaled = new HashMap<>();

    private BoardArt(Properties p, BufferedImage board) throws Exception {
        width = Integer.parseInt(p.getProperty("width").trim());
        height = Integer.parseInt(p.getProperty("height").trim());
        squareLeft = num(p, "squareLeft");
        squareTop = num(p, "squareTop");
        squareWidth = num(p, "squareWidth");
        squareHeight = num(p, "squareHeight");
        anchorX = num(p, "anchorX");
        anchorY = num(p, "anchorY");
        this.board = board;
        for (String kind : new String[]{"pawn", "knight", "bishop", "rook", "queen", "king"}) {
            for (String colour : new String[]{"white", "black"}) {
                BufferedImage s = ImageIO.read(new File(DIR, "piece_" + kind + "_" + colour + ".png"));
                if (s == null) throw new IllegalStateException("missing sprite " + kind + " " + colour);
                sprites.put(kind + "_" + colour, s);
            }
        }
    }

    /** The board art, or null if it hasn't been rendered. */
    public static synchronized BoardArt get() {
        if (tried) return cached;
        tried = true;
        File props = new File(DIR, "board.properties");
        File img = new File(DIR, "board.png");
        if (!props.exists() || !img.exists()) return null;
        try (FileReader r = new FileReader(props)) {
            Properties p = new Properties();
            p.load(r);
            cached = new BoardArt(p, ImageIO.read(img));
        } catch (Exception e) {
            System.err.println("Board art not usable (" + e.getMessage() + "); using the plain board.");
            cached = null;
        }
        return cached;
    }

    private static double num(Properties p, String key) {
        return Double.parseDouble(p.getProperty(key).trim());
    }

    /** The board image at the given scale (cached). */
    public BufferedImage board(double scale) {
        rescale(scale);
        return boardScaled;
    }

    /** The piece's sprite at the given scale (cached). */
    public BufferedImage sprite(Piece piece, double scale) {
        rescale(scale);
        String key = ShopManager.typeOf(piece).toLowerCase() + "_" + (piece.isWhite() ? "white" : "black");
        return spritesScaled.get(key);
    }

    private void rescale(double scale) {
        if (Math.abs(scale - scaledFor) < 1e-6) return;
        scaledFor = scale;
        boardScaled = scaled(board, scale);
        spritesScaled.clear();
        for (Map.Entry<String, BufferedImage> e : sprites.entrySet()) {
            spritesScaled.put(e.getKey(), scaled(e.getValue(), scale));
        }
    }

    private static BufferedImage scaled(BufferedImage src, double scale) {
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
