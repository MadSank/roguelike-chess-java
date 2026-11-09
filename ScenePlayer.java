import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * Plays pre-rendered clips behind a Swing panel, with a PS1-style film look on top:
 * chunky upscaled pixels, animated grain, a vignette and a flickering lamp.
 *
 * The camera works like Buckshot Roulette's: a few fixed views around the player's seat
 * (card dealer, table, piece dealer, reseller) with rendered turns between neighbours.
 *
 * While the camera rests on a view, its idle loop plays (<view>_idle). A reaction
 * (<view>_<name>, e.g. card_yes) cuts in from the loop and hands back to it at frame 0; both
 * are rendered from the same breathing cycle, so neither cut jumps. A mood swaps the loop
 * for another one (e.g. opponent_think) at the next loop boundary.
 */
public class ScenePlayer {
    /** The views around the seat, in turning order (turning right moves forward). */
    public static final String[] RING = {"card", "table", "piece", "resell"};

    private final JComponent host;
    private final javax.swing.Timer timer;
    private final Random rng = new Random();

    private String view = "table";
    /** Clips still to play, each with its direction. */
    private final Deque<Object[]> queue = new ArrayDeque<>();
    private SceneClip playing;
    private boolean reverse;
    private int frame;
    private Runnable onArrive;
    private long lastTick;

    /** Position in the current view's idle loop (if it has one). */
    private int idleFrame;
    /** The loop playing now (e.g. "idle" or "think") and the one wanted at the next boundary. */
    private String loop = "idle", wantedLoop = "idle";
    /** A one-off reaction playing over the idle (null = none). */
    private SceneClip reaction;
    private int reactionFrame;
    private float flicker = 1f;
    private final BufferedImage[] grain = new BufferedImage[4];
    private final BufferedImage[] fineGrain = new BufferedImage[4];
    /** True when the frames being shown are high resolution (realistic renders). */
    private boolean hd;

    public ScenePlayer(JComponent host) {
        this.host = host;
        for (int i = 0; i < grain.length; i++) grain[i] = makeGrain(320, 240, new Random(i), 18, 22);
        for (int i = 0; i < fineGrain.length; i++) fineGrain[i] = makeGrain(960, 540, new Random(100 + i), 8, 10);
        timer = new javax.swing.Timer(1000 / SceneClip.FPS, e -> tick());
        timer.start();
    }

    /** True when every clip the scene mode needs has been rendered. */
    public static boolean scenesAvailable() {
        List<String> names = new ArrayList<>(Arrays.asList(RING));
        names.add("intro");
        for (int i = 0; i < RING.length; i++) names.add(turnName(i));
        return SceneClip.available(names.toArray(new String[0]));
    }

    private static String turnName(int i) {
        return "turn_" + RING[i] + "_" + RING[(i + 1) % RING.length];
    }

    public String getView() {
        return view;
    }

    public boolean isMoving() {
        return playing != null;
    }

    /** The frame being shown of the clip that's playing (e.g. the intro), or -1 if none is. */
    public int getClipFrame() {
        return playing == null ? -1 : frame;
    }

    /**
     * Plays the current view's reaction clip once (e.g. "yes" plays card_yes), then returns to
     * the loop. Ignored while the camera is turning or if the clip wasn't rendered.
     */
    public void react(String name) {
        if (playing != null) return;
        SceneClip c = SceneClip.load(view + "_" + name);
        if (c == null) return;
        reaction = c;
        reactionFrame = 0;
        c.prefetch(0, 1);
    }

    /** Loops the current view's <view>_<name> clip instead of its idle (null = back to the idle). */
    public void setMood(String name) {
        wantedLoop = name == null ? "idle" : name;
    }

    /** The loop clip to play now (the mood if it was rendered, otherwise the idle). */
    private SceneClip loopClip() {
        SceneClip c = SceneClip.load(view + "_" + loop);
        return c != null ? c : SceneClip.load(view + "_idle");
    }

    /** Jumps straight to a view (no animation). */
    public void setView(String name) {
        queue.clear();
        playing = null;
        reaction = null;
        view = name;
        host.repaint();
    }

    /** Plays a one-off clip (e.g. the intro), then holds on the given view. */
    public void playClip(String clip, String endView, Runnable then) {
        queue.clear();
        SceneClip c = SceneClip.load(clip);
        if (c != null) queue.add(new Object[]{c, false, endView});
        onArrive = then;
        startNext(endView);
    }

    /** Plays several clips back to back (e.g. a cutscene in parts), then holds on the given view. */
    public void playClips(String[] clips, String endView, Runnable then) {
        queue.clear();
        for (String name : clips) {
            SceneClip c = SceneClip.load(name);
            if (c != null) queue.add(new Object[]{c, false, endView});
        }
        onArrive = then;
        startNext(endView);
    }

    /** Turns the camera to a view along the ring (shortest way), then runs then. */
    public void turnTo(String target, Runnable then) {
        int from = indexOf(view), to = indexOf(target);
        if (from < 0 || to < 0 || from == to) {
            setView(target);
            if (then != null) then.run();
            return;
        }
        queue.clear();
        int n = RING.length;
        int forward = (to - from + n) % n;
        int step = forward <= n / 2 ? 1 : -1;
        for (int i = from; i != to; i = (i + step + n) % n) {
            int next = (i + step + n) % n;
            int clipIndex = step > 0 ? i : next;           // turn_<a>_<b> goes a -> b
            SceneClip c = SceneClip.load(turnName(clipIndex));
            if (c != null) queue.add(new Object[]{c, step < 0, RING[next]});
        }
        onArrive = then;
        startNext(target);
    }

    private void startNext(String fallbackView) {
        Object[] next = queue.poll();
        if (next == null) {
            playing = null;
            view = fallbackView;
            Runnable r = onArrive;
            onArrive = null;
            host.repaint();
            if (r != null) r.run();
            return;
        }
        reaction = null;
        playing = (SceneClip) next[0];
        reverse = (Boolean) next[1];
        view = (String) next[2];
        frame = 0;
        prefetch();
    }

    /** Asks the clip to decode the frames coming up next (in the playing direction). */
    private void prefetch() {
        if (playing == null) return;
        int i = reverse ? playing.frameCount() - 1 - frame : frame;
        playing.prefetch(i, reverse ? -1 : 1);
    }

    private void tick() {
        if (!host.isShowing()) return;
        long now = System.currentTimeMillis();
        // The lamp flickers now and then.
        flicker = rng.nextInt(40) == 0 ? 0.8f + rng.nextFloat() * 0.1f : Math.min(1f, flicker + 0.05f);
        SceneClip idle = playing == null ? loopClip() : null;
        if (playing != null) {
            frame++;
            if (frame >= playing.frameCount()) startNext(view);
            else prefetch();
        } else if (reaction != null) {
            reactionFrame++;
            if (reactionFrame >= reaction.frameCount()) {
                // Back to the loop at its first frame (the reaction ends where the loop starts).
                reaction = null;
                idleFrame = 0;
                loop = wantedLoop;
            } else {
                reaction.prefetch(reactionFrame, 1);
            }
        } else if (idle != null) {
            // Resting on a view with an idle loop (a dealer breathing): keep it moving.
            idleFrame = (idleFrame + 1) % idle.frameCount();
            if (idleFrame == 0 && !loop.equals(wantedLoop)) {
                loop = wantedLoop;  // switch moods only at the loop boundary, so nothing jumps
                idle = loopClip();
            }
            if (idle != null) {
                idle.prefetch(idleFrame, 1);
                if (idleFrame + 10 >= idle.frameCount()) idle.prefetch(-1, 1); // wrap-around frames
            }
        }
        if (playing != null || reaction != null || idle != null || now - lastTick > 80) { // stills: animate grain at ~12 fps
            lastTick = now;
            host.repaint();
        }
    }

    /** The image to show right now, or null if no clip is available. */
    private BufferedImage currentImage() {
        if (playing != null) {
            int i = reverse ? playing.frameCount() - 1 - frame : frame;
            return playing.frame(i);
        }
        if (reaction != null) return reaction.frame(Math.min(reactionFrame, reaction.frameCount() - 1));
        SceneClip idle = loopClip();
        if (idle != null) return idle.frame(idleFrame % idle.frameCount());
        SceneClip still = SceneClip.load(view);
        return still == null ? null : still.frame(0);
    }

    /** Draws the scene into a rectangle (e.g. a small portrait), clipped to it. */
    public boolean paint(Graphics2D g, int x, int y, int w, int h) {
        Graphics2D g2 = (Graphics2D) g.create(x, y, w, h);
        try {
            return paint(g2, w, h);
        } finally {
            g2.dispose();
        }
    }

    /** True if the given clip of the current view was rendered (e.g. "idle"). */
    public boolean hasClip(String name) {
        return SceneClip.load(view + "_" + name) != null;
    }

    /** Draws the scene filling the area, plus the film look. Returns false if there is no scene. */
    public boolean paint(Graphics2D g, int w, int h) {
        BufferedImage img = currentImage();
        if (img == null) return false;
        Object oldHint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        // Cover the area, keeping the 4:3 aspect.
        // Realistic (HD) frames scale smoothly; low-resolution placeholder frames keep chunky pixels.
        hd = img.getWidth() >= 640;
        if (hd) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double scale = Math.max(w / (double) img.getWidth(), h / (double) img.getHeight());
        int dw = (int) Math.ceil(img.getWidth() * scale), dh = (int) Math.ceil(img.getHeight() * scale);
        g.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldHint == null
            ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : oldHint);
        drawFilm(g, w, h);
        return true;
    }

    /** Just the moving grain (for screens that must stay readable, like the board). */
    public void drawGrain(Graphics2D g, int w, int h) {
        Object oldHint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        if (hd) {
            // Fine, faint grain for realistic frames.
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(fineGrain[rng.nextInt(fineGrain.length)], 0, 0, w, h, null);
        } else {
            // Chunky grain for the low-resolution placeholder frames.
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(grain[rng.nextInt(grain.length)], 0, 0, w, h, null);
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldHint == null
            ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : oldHint);
    }

    /** Grain, vignette and flicker over whatever was drawn. */
    public void drawFilm(Graphics2D g, int w, int h) {
        drawGrain(g, w, h);
        int mid = hd ? 35 : 70, edge = hd ? 150 : 210; // realistic frames are already lit moodily
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, h / 2f), Math.max(w, h) * 0.7f,
            new float[]{0f, 0.6f, 1f},
            new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, mid), new Color(0, 0, 0, edge)}));
        g.fillRect(0, 0, w, h);
        if (flicker < 1f) {
            g.setColor(new Color(0, 0, 0, (int) ((1f - flicker) * 255)));
            g.fillRect(0, 0, w, h);
        }
    }

    /** Random speckle: about a third of pixels get a grey dot of alpha minAlpha..minAlpha+spread. */
    private static BufferedImage makeGrain(int w, int h, Random r, int minAlpha, int spread) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = r.nextInt(256);
                int a = r.nextInt(100) < 35 ? minAlpha + r.nextInt(spread) : 0;
                img.setRGB(x, y, (a << 24) | (v << 16) | (v << 8) | v);
            }
        }
        return img;
    }

    private static int indexOf(String v) {
        for (int i = 0; i < RING.length; i++) if (RING[i].equals(v)) return i;
        return -1;
    }
}
