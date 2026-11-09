import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/**
 * A pre-rendered clip: the frames in resources/scenes/<name>/ (made by tools/blender/build_scenes.py).
 * Clips are loaded once and cached. A missing clip loads as null, and the game falls back
 * to its plain look.
 */
public final class SceneClip {
    public static final int FPS = 24;
    private static final File ROOT = new File("resources" + File.separator + "scenes");
    private static final Map<String, SceneClip> CACHE = new HashMap<>();

    public final String name;
    private final List<BufferedImage> frames;

    private SceneClip(String name, List<BufferedImage> frames) {
        this.name = name;
        this.frames = frames;
    }

    /** The clip, or null if its frames aren't there. */
    public static synchronized SceneClip load(String name) {
        if (CACHE.containsKey(name)) return CACHE.get(name);
        SceneClip clip = null;
        File dir = new File(ROOT, name);
        File[] files = dir.listFiles((d, f) -> f.toLowerCase().endsWith(".jpg") || f.toLowerCase().endsWith(".png"));
        if (files != null && files.length > 0) {
            Arrays.sort(files);
            List<BufferedImage> frames = new ArrayList<>();
            for (File f : files) {
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) frames.add(img);
                } catch (Exception e) {
                    System.err.println("Could not read " + f + ": " + e.getMessage());
                }
            }
            if (!frames.isEmpty()) clip = new SceneClip(name, frames);
        }
        CACHE.put(name, clip);
        return clip;
    }

    /** True if every named clip is available (the scene mode needs them all). */
    public static boolean available(String... names) {
        for (String n : names) if (load(n) == null) return false;
        return true;
    }

    public int frameCount() {
        return frames.size();
    }

    public BufferedImage frame(int i) {
        return frames.get(Math.max(0, Math.min(i, frames.size() - 1)));
    }
}
