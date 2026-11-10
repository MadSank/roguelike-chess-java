import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * Plays pre-rendered clips behind a Swing panel, with a film look on top (grain, a vignette
 * and a flickering lamp).
 *
 * The camera works like Buckshot Roulette's: a few fixed views around the player's seat
 * (card dealer, table, piece dealer, reseller) with rendered turns between neighbours.
 *
 * While the camera rests on a view, its idle loop plays (<view>_idle). A reaction
 * (<view>_<name>, e.g. card_yes) plays once over it; a mood swaps the loop for another one
 * of the same length (e.g. opponent1_think).
 *
 * Smoothness: clips are rendered at 24 frames per second, but the screen is redrawn about
 * 60 times per second. Playback follows the real clock (so it never slows down or speeds up),
 * each redraw blends the two rendered frames around the current instant, and every cut between
 * clips (into a reaction, back to the loop, into a camera turn) is a short cross-fade.
 */
public class ScenePlayer {
    /** The views around the seat, in turning order (turning right moves forward). */
    public static final String[] RING = {"card", "table", "piece", "resell"};
    /** Screen redraws per second while something is moving. */
    private static final int REFRESH_MS = 16;
    /** Length of the cross-fade when cutting between clips, in seconds. */
    private static final double FADE_SECONDS = 0.28;

    private final JComponent host;
    private final javax.swing.Timer timer;
    private final Random rng = new Random();

    private String view = "table";
    /** Clips still to play, each with its direction. */
    private final Deque<Object[]> queue = new ArrayDeque<>();
    private SceneClip playing;
    private boolean reverse;
    /** Position in the playing clip, in frames (fractional: between two rendered frames). */
    private double pos;
    private Runnable onArrive;
    private long lastTick, lastNanos;

    /** Position in the current view's idle loop (if it has one), in frames. */
    private double idlePos;
    /** The loop playing now (e.g. "idle" or "think") and the one asked for. */
    private String loop = "idle", wantedLoop = "idle";
    /** A one-off reaction playing over the idle (null = none), and the position in it. */
    private SceneClip reaction;
    private double reactionPos;

    /** Cross-fade: the last picture of the clip being left, and when the fade began. */
    private BufferedImage fadeFrom, lastShown;
    private long fadeStart;

    private float flicker = 1f;
    private final BufferedImage[] grain = new BufferedImage[4];
    private final BufferedImage[] fineGrain = new BufferedImage[4];
    /** True when the frames being shown are high resolution (realistic renders). */
    private boolean hd;
    /** Where this player last painted inside its host (null = the whole host). */
    private Rectangle area;

    public ScenePlayer(JComponent host) {
        this.host = host;
        for (int i = 0; i < grain.length; i++) grain[i] = makeGrain(320, 240, new Random(i), 18, 22);
        for (int i = 0; i < fineGrain.length; i++) fineGrain[i] = makeGrain(960, 540, new Random(100 + i), 8, 10);
        lastNanos = System.nanoTime();
        timer = new javax.swing.Timer(REFRESH_MS, e -> tick());
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
        return playing == null ? -1 : (int) pos;
    }

    /** Starts a cross-fade from whatever is on screen now. */
    private void fade() {
        if (lastShown == null) return;
        fadeFrom = lastShown;
        fadeStart = System.nanoTime();
    }

    /**
     * Plays the current view's reaction clip once (e.g. "yes" plays card_yes), then returns to
     * the loop. Ignored while the camera is turning or if the clip wasn't rendered.
     */
    public void react(String name) {
        if (playing != null) return;
        SceneClip c = SceneClip.load(view + "_" + name);
        if (c == null) return;
        fade();
        reaction = c;
        reactionPos = 0;
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
        fadeFrom = null;
        if (!name.equals(view)) {
            idlePos = 0;
            lastShown = null;
        }
        view = name;
        repaintHost();
    }

    /** Plays a one-off clip (e.g. the intro), then holds on the given view. */
    public void playClip(String clip, String endView, Runnable then) {
        playClips(new String[]{clip}, endView, then);
    }

    /** Plays several clips back to back (e.g. a cutscene in parts), then holds on the given view. */
    public void playClips(String[] clips, String endView, Runnable then) {
        queue.clear();
        fadeFrom = null;
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
        fade(); // the dealer was mid-breath: ease from that picture into the turn
        startNext(target);
    }

    private void startNext(String fallbackView) {
        Object[] next = queue.poll();
        if (next == null) {
            playing = null;
            view = fallbackView;
            idlePos = 0; // a turn ends on the view's first idle frame
            loop = wantedLoop = "idle";
            Runnable r = onArrive;
            onArrive = null;
            repaintHost();
            if (r != null) r.run();
            return;
        }
        reaction = null;
        playing = (SceneClip) next[0];
        reverse = (Boolean) next[1];
        view = (String) next[2];
        pos = 0;
        prefetch();
    }

    /** Asks the clip to decode the frames coming up next (in the playing direction). */
    private void prefetch() {
        if (playing == null) return;
        int i = reverse ? playing.frameCount() - 1 - (int) pos : (int) pos;
        playing.prefetch(i, reverse ? -1 : 1);
    }

    private void tick() {
        long nanos = System.nanoTime();
        double dt = Math.min(0.1, (nanos - lastNanos) / 1e9); // after a stall, don't jump ahead
        lastNanos = nanos;
        if (!host.isShowing()) return;
        double advance = dt * SceneClip.FPS;
        long now = System.currentTimeMillis();
        // The lamp flickers now and then.
        flicker = rng.nextInt(100) == 0 ? 0.8f + rng.nextFloat() * 0.1f : Math.min(1f, flicker + 0.02f);
        SceneClip idle = playing == null ? loopClip() : null;
        if (playing != null) {
            pos += advance;
            if (pos >= playing.frameCount() - 1) startNext(view);
            else prefetch();
        } else if (reaction != null) {
            reactionPos += advance;
            if (reactionPos >= reaction.frameCount() - 1) {
                // Back to the loop, at the point in the breathing cycle where the reaction ends.
                int played = reaction.frameCount();
                fade();
                reaction = null;
                loop = wantedLoop;
                SceneClip back = loopClip();
                idlePos = back == null ? 0 : played % back.frameCount();
            } else {
                reaction.prefetch((int) reactionPos, 1);
            }
        } else if (idle != null) {
            // Resting on a view with an idle loop (a dealer breathing): keep it moving.
            if (!loop.equals(wantedLoop)) {
                // A mood is the same breathing cycle with something added (e.g. a bowed head while
                // thinking), so switch right away at the same point in the cycle, with a cross-fade.
                fade();
                loop = wantedLoop;
                idle = loopClip();
            }
            idlePos += advance;
            if (idle != null && idlePos >= idle.frameCount()) idlePos %= idle.frameCount();
            if (idle != null) {
                idle.prefetch((int) idlePos, 1);
                if (idlePos + 10 >= idle.frameCount()) idle.prefetch(-1, 1); // wrap-around frames
            }
        }
        boolean fading = fadeFrom != null;
        if (playing != null || reaction != null || idle != null || fading || now - lastTick > 80) { // stills: grain at ~12 fps
            lastTick = now;
            repaintHost();
        }
    }

    /** Repaints only the part of the host this player draws in (the whole host if unknown). */
    private void repaintHost() {
        if (area != null) host.repaint(area);
        else host.repaint();
    }

    /** Draws the scene into a rectangle (e.g. a small portrait), clipped to it. */
    public boolean paint(Graphics2D g, int x, int y, int w, int h) {
        area = new Rectangle(x, y, w + 1, h + 1);
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
        // Which clip, and where in it (in frames; the fraction is the blend toward the next frame).
        SceneClip clip;
        double at;
        boolean loops = false;
        if (playing != null) {
            clip = playing;
            at = reverse ? playing.frameCount() - 1 - pos : pos;
        } else if (reaction != null) {
            clip = reaction;
            at = reactionPos;
        } else {
            clip = loopClip();
            at = idlePos;
            loops = true;
            if (clip == null) {
                clip = SceneClip.load(view);
                at = 0;
                loops = false;
            }
        }
        if (clip == null) return false;
        int n = clip.frameCount();
        at = Math.max(0, Math.min(at, loops ? n - 1e-6 : n - 1));
        int i0 = (int) Math.floor(at);
        int i1 = loops ? (i0 + 1) % n : Math.min(i0 + 1, n - 1);
        float blend = (float) (at - i0);
        BufferedImage a = clip.frame(i0);
        if (a == null) return false;

        Object oldHint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        Composite oldComposite = g.getComposite();
        // Realistic (HD) frames scale smoothly; low-resolution placeholder frames keep chunky pixels.
        hd = a.getWidth() >= 640 || w < a.getWidth();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hd
            ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        drawCover(g, a, w, h);
        if (hd && n > 1 && i1 != i0 && blend > 0.04f) {
            BufferedImage b = clip.frame(i1);
            if (b != null) {
                g.setComposite(AlphaComposite.SrcOver.derive(blend));
                drawCover(g, b, w, h);
            }
        }
        if (fadeFrom != null) {
            double t = (System.nanoTime() - fadeStart) / 1e9 / FADE_SECONDS;
            if (t >= 1 || fadeFrom == a) {
                fadeFrom = null;
            } else {
                double ease = t * t * (3 - 2 * t);
                g.setComposite(AlphaComposite.SrcOver.derive((float) (1 - ease)));
                drawCover(g, fadeFrom, w, h);
            }
        }
        lastShown = a;
        g.setComposite(oldComposite);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldHint == null
            ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : oldHint);
        drawFilm(g, w, h);
        return true;
    }

    /** Draws img covering the area, keeping its aspect (cropping what doesn't fit). */
    private static void drawCover(Graphics2D g, BufferedImage img, int w, int h) {
        double scale = Math.max(w / (double) img.getWidth(), h / (double) img.getHeight());
        int dw = (int) Math.ceil(img.getWidth() * scale), dh = (int) Math.ceil(img.getHeight() * scale);
        g.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
    }

    /** Just the moving grain (for screens that must stay readable, like the board). */
    public void drawGrain(Graphics2D g, int w, int h) {
        Object oldHint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        int pick = (int) ((System.currentTimeMillis() / 70) % grain.length); // film grain changes ~14 times a second
        if (hd) {
            // Fine, faint grain for realistic frames.
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(fineGrain[pick], 0, 0, w, h, null);
        } else {
            // Chunky grain for the low-resolution placeholder frames.
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(grain[pick], 0, 0, w, h, null);
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
