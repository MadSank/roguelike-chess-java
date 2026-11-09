 

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class ResourceLoader {

    private static final Map<String, BufferedImage> imageCache = new HashMap<>();

    public static BufferedImage loadImage(String filename) {
        if (imageCache.containsKey(filename)) {
            return imageCache.get(filename);
        }

        String[] paths = {
                "resources/images/" + filename,
                "assets/images/" + filename,
                "images/" + filename
            };

        BufferedImage img = null;

        for (String path : paths) {
            try {
                File file = new File(path);
                if (file.exists()) {
                    img = ImageIO.read(file);
                    if (img != null) break;
                }

                InputStream is = ResourceLoader.class.getClassLoader().getResourceAsStream(path);
                if (is != null) {
                    img = ImageIO.read(is);
                    is.close();
                    if (img != null) break;
                }
            } catch (Exception e) {
                //welp
            }
        }

        // Missing images stay null, so callers fall back to their own drawing
        // (the board draws Unicode chess symbols).
        imageCache.put(filename, img);
        return img;
    }

    public static BufferedImage loadPieceImage(String pieceName, boolean isWhite) {
        String color = isWhite ? "white" : "black";
        String filename = "piece_" + pieceName.toLowerCase() + "_" + color + ".png";
        return loadImage(filename);
    }

    public static BufferedImage loadUIImage(String name) {
        return loadImage(name + ".png");
    }

    public static BufferedImage scaleImage(BufferedImage original, int width, int height) {
        Image scaled = original.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = result.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        return result;
    }

    public static void clearCache() {
        imageCache.clear();
    }

    public static void preloadPieceImages() {
        String[] pieces = {"pawn", "knight", "bishop", "rook", "queen", "king"};
        String[] colors = {"white", "black"};

        for (String piece : pieces) {
            for (String color : colors) {
                loadPieceImage(piece, "white".equals(color));
            }
        }


    }
}
