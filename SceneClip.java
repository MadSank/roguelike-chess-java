import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/**
 * A pre-rendered clip: the frames in resources/scenes/<name>/ (made by tools/blender/build_scenes.py).
 *
 * Frames are streamed from disk, not all loaded at once: at 1280x720 a whole clip would take
 * hundreds of megabytes. A small cache keeps recent frames, and a background thread decodes
 * the frames coming up next. A missing clip loads as null and the game falls back to its plain look.
 */
public final class SceneClip {
    public static final int FPS = 24;
    private static final File ROOT = new File("resources" + File.separator + "scenes");
    private static final Map<String, SceneClip> CLIPS = new HashMap<>();
    /** Decoded frames kept in memory per clip (about 3.7 MB each at 720p). */
    private static final int CACHE_FRAMES = 40;
    private static final int PREFETCH_AHEAD = 10;

    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "scene-frame-decoder");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    public final String name;
    private final File[] files;
    private final Map<Integer, BufferedImage> cache = new LinkedHashMap<Integer, BufferedImage>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, BufferedImage> eldest) {
            return size() > CACHE_FRAMES;
        }
    };
    private final Set<Integer> queued = ConcurrentHashMap.newKeySet();

    private SceneClip(String name, File[] files) {
        this.name = name;
        this.files = files;
    }

    /** The clip, or null if its frames aren't there. Cheap: no frames are decoded yet. */
    public static synchronized SceneClip load(String name) {
        if (CLIPS.containsKey(name)) return CLIPS.get(name);
        File[] files = new File(ROOT, name).listFiles((d, f) -> {
            String lower = f.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".png");
        });
        SceneClip clip = null;
        if (files != null && files.length > 0) {
            Arrays.sort(files);
            clip = new SceneClip(name, files);
        }
        CLIPS.put(name, clip);
        return clip;
    }

    /** True if every named clip is available (the scene mode needs them all). */
    public static boolean available(String... names) {
        for (String n : names) if (load(n) == null) return false;
        return true;
    }

    public int frameCount() {
        return files.length;
    }

    /** Frame i (clamped), decoding it now if it isn't cached. Never returns null for a valid clip. */
    public BufferedImage frame(int i) {
        int idx = Math.max(0, Math.min(i, files.length - 1));
        synchronized (cache) {
            BufferedImage img = cache.get(idx);
            if (img != null) return img;
        }
        BufferedImage img = decode(idx);
        if (img != null) {
            synchronized (cache) {
                cache.put(idx, img);
            }
        }
        return img;
    }

    /** Starts decoding the frames after i in the playing direction (step = 1 or -1). */
    public void prefetch(int i, int step) {
        for (int k = 1; k <= PREFETCH_AHEAD; k++) {
            int idx = i + k * step;
            if (idx < 0 || idx >= files.length) break;
            synchronized (cache) {
                if (cache.containsKey(idx)) continue;
            }
            if (!queued.add(idx)) continue;
            final int target = idx;
            DECODER.execute(() -> {
                try {
                    BufferedImage img = decode(target);
                    if (img != null) {
                        synchronized (cache) {
                            cache.put(target, img);
                        }
                    }
                } finally {
                    queued.remove(target);
                }
            });
        }
    }

    private BufferedImage decode(int idx) {
        try {
            BufferedImage raw = ImageIO.read(files[idx]);
            if (raw == null) return null;
            if (FootageLook.ENABLED) return FootageLook.apply(raw); // lens fringing and grade, once per frame
            if (raw.getType() == BufferedImage.TYPE_INT_RGB) return raw;
            // JPEGs decode as 3-byte BGR, which is slow to scale; convert once here.
            BufferedImage fast = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = fast.createGraphics();
            g.drawImage(raw, 0, 0, null);
            g.dispose();
            return fast;
        } catch (Exception e) {
            System.err.println("Could not read " + files[idx] + ": " + e.getMessage());
            return null;
        }
    }
}
