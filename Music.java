import javax.sound.sampled.*;
import java.util.*;

/**
 * The game's music and sound effects, composed as notes and synthesized here in pure Java (no sound files).
 *
 *  - Instruments are small synths: a reedy cellar organ, a plucked bass, a music box, a chiptune pulse
 *    lead, an accordion-like reed, a low bell, tremolo strings, and percussion (heartbeat, clock ticks, boom).
 *  - Each song is rendered once (in the background) to a buffer with a cellar reverb, made seamless for
 *    looping, then streamed through one audio line together with the sound effects.
 *  - Songs can have a second layer that fades in and out with the game: the table music grows a ticking
 *    clock and trembling strings while your king is in check (tension()).
 *
 * Songs: menu, table, shop, ambient, over. Stings (play once over the music): mate, win, lightsout.
 * Effects: move, capture, check, coin, deny, card, step1-3, drip, creak, chair, hiss, crack, sparks;
 * steps(...) walks; blip(pitch) is the typewriter voice. Drips and creaks play by themselves now and then.
 * Silent if the computer has no audio device.
 *   java Music <folder>   writes every song and sting as a WAV file there, to listen to.
 */
public final class Music {
    private Music() {}

    public static final int RATE = 44100;

    // ════════ Composition ════════════════════════════════════════
    enum Inst { ORGAN, BASS, BOX, LEAD, REED, BELL, STRING, HEART, TICK, TOCK, BOOM, STAB, FIZZ, KICK, JINGLE, CITY, RAIN, SAMPLE }

    private static final class Note {
        final Inst inst;
        final double beat, len, vel, freq;
        /** For SAMPLE notes: which sound effect to place here (a footstep, the tap, a door...). */
        final String sample;

        Note(Inst inst, double beat, double len, double vel, double freq) {
            this.inst = inst;
            this.beat = beat;
            this.len = len;
            this.vel = vel;
            this.freq = freq;
            this.sample = null;
        }

        Note(String sample, double beat, double vel) {
            this.inst = Inst.SAMPLE;
            this.beat = beat;
            this.len = 0.1;
            this.vel = vel;
            this.freq = 0;
            this.sample = sample;
        }
    }

    private static final class Song {
        final String name;
        final double bpm, beats, wind, wet;
        final boolean loop;
        /** For a one-off song: the beats whose loudness sets its level (so quiet parts stay quiet). */
        double normFrom = -1, normTo = -1;
        final List<List<Note>> layers = new ArrayList<>();

        Song(String name, double bpm, double beats, boolean loop, double wind, double wet) {
            this.name = name;
            this.bpm = bpm;
            this.beats = beats;
            this.loop = loop;
            this.wind = wind;
            this.wet = wet;
        }

        List<Note> layer(int i) {
            while (layers.size() <= i) layers.add(new ArrayList<>());
            return layers.get(i);
        }
    }

    private static double freq(String name) {
        int pc = "C D EF G A B".indexOf(Character.toUpperCase(name.charAt(0)));
        int i = 1;
        if (name.charAt(i) == '#') { pc++; i++; } else if (name.charAt(i) == 'b') { pc--; i++; }
        int octave = Integer.parseInt(name.substring(i));
        int midi = 12 * (octave + 1) + pc;
        return 440 * Math.pow(2, (midi - 69) / 12.0);
    }

    /**
     * Adds a line of notes: tokens like "D4/1" (note / beats), "-/2" (rest), "D3+F3+A3/4" (chord).
     * Returns the beat after the last token.
     */
    private static double seq(List<Note> out, Inst in, double vel, double beat, String s) {
        for (String tok : s.trim().split("\\s+")) {
            String[] p = tok.split("/");
            double len = Double.parseDouble(p[1]);
            if (!p[0].equals("-")) for (String n : p[0].split("\\+")) out.add(new Note(in, beat, len, vel, freq(n)));
            beat += len;
        }
        return beat;
    }

    private static void hit(List<Note> out, Inst in, double vel, double beat) {
        out.add(new Note(in, beat, 0.1, vel, 0));
    }

    private static final Map<String, Song> SONGS = new LinkedHashMap<>();

    /** The table's bass line: eighth notes, root root fifth root, a creeping half-step, root low-fifth root. */
    private static void ostinato(List<Note> out, double start, String[] roots, double vel) {
        for (int bar = 0; bar < roots.length; bar++) {
            double r = freq(roots[bar]);
            double[] steps = {1, 1, 1.5, 1, Math.pow(2, 1 / 12.0), 1, 0.75, 1};
            for (int i = 0; i < 8; i++) {
                double f = r * steps[i];
                if (bar % 4 == 3 && i >= 6) f = r * (i == 6 ? 1.5 : 2);                   // a little lift at the end of each phrase
                out.add(new Note(Inst.BASS, start + bar * 4 + i * 0.5, 0.42, (i % 4 == 0 ? 0.9 : 0.65) * vel, f));
            }
        }
    }

    private static final String[] TABLE_ROOTS = {"E2", "E2", "C2", "C2", "A1", "A1", "B1", "B1", "E2", "E2", "F2", "F2", "A1", "A1", "B1", "B1"};

    /** Beats in the opening (96 per minute): where each part of the scene starts. */
    public static final double OPENING_BPM = 96, OPENING_BEATS = 116;

    static {
        // ── The opening: a bathroom, the skywalk, the game room. His footsteps become the beat ──
        //  0-16 in hard rain he tries to light a cigarette and can't; he breaks down; grinds it out / 16-96 the walk, one step a beat:
        //  steps alone, then a kick under them (32), the table's bass and his keys (48), organ and
        //  music box (64), the melody (80) / 96 he stops at the door / 100-116 the room, heartbeat.
        Song op = new Song("opening", OPENING_BPM, OPENING_BEATS, false, 0, 0.12);
        op.normFrom = 80;
        op.normTo = 96;
        List<Note> ol = op.layer(0);
        ol.add(new Note(Inst.RAIN, 0, 8.4, 2.4, 0));
        ol.add(new Note(Inst.RAIN, 8.0, 8, 1.5, 0));                        // easing while he cries                         // hard rain; he can't get it lit
        ol.add(new Note("flick", 2.0, 1.7));                               // miss
        ol.add(new Note("flick", 2.9, 1.7));                               // miss
        ol.add(new Note("flick", 3.7, 1.7));                               // it catches
        ol.add(new Note("catch", 3.75, 1.5));
        ol.add(new Note("drag", 4.0, 1.2));                                // one breath of it
        ol.add(new Note("sizzle", 5.0, 1.7));                              // and the rain puts it out
        ol.add(new Note("flick", 6.0, 0.8));                               // a wet wheel, weak sparks
        ol.add(new Note("flick", 6.8, 0.7));
        ol.add(new Note("flick", 7.6, 1.5));
        ol.add(new Note("catch", 7.65, 1.0));
        ol.add(new Note("sizzle", 8.3, 1.0));                              // killed before it lights anything
        for (double sb : new double[]{9.0, 9.7, 10.4, 11.3, 11.9}) ol.add(new Note("sob", sb, 3.6));   // he breaks down
        ol.add(new Note("clack", 12.3, 1.7));                              // snaps the lighter shut
        ol.add(new Note("grind", 13.6, 1.7));                              // grinds the wet cigarette out
        ol.add(new Note("exhale", 14.7, 1.4));                             // a long breath, a sleeve across his face
        ol.add(new Note(Inst.RAIN, 15, 85, 0.35, 0));
        ol.add(new Note(Inst.CITY, 14, 90, 1.0, 0));
        for (int b = 17; b <= 95; b++) {
            double v = b < 48 ? 0.9 : b < 80 ? 0.9 - (b - 48) / 32.0 * 0.4 : 0.45;
            ol.add(new Note("step" + (1 + b % 3), b, v));
            if (b >= 32) ol.add(new Note(Inst.KICK, b, 0.1, b < 48 ? 0.15 + (b - 32) / 16.0 * 0.65 : 0.85, 0));
            if (b >= 48) ol.add(new Note(Inst.JINGLE, b + 0.5, 0.1, 0.6, 0));
        }
        seq(ol, Inst.BASS, 0.5, 40, "E1/2 -/2 E1/2 -/2");
        ostinato(ol, 48, Arrays.copyOf(TABLE_ROOTS, 12), 1);
        seq(ol, Inst.ORGAN, 0.55, 64, "E3+G3+B3/8 C3+E3+G3/8 A2+C3+E3/8 B2+D#3+F#3/8");
        seq(ol, Inst.BOX, 0.5, 64, "-/8 G5/2 F#5/2 E5/4 -/8 E5/2 D#5/2 B4/4");
        seq(ol, Inst.LEAD, 0.8, 80, "B4/2 G4/1 F#4/1 E4/3 -/1 F4/2 A4/1 C5/1 B4/3 -/1");
        ol.add(new Note(Inst.BOOM, 96, 0.1, 0.35, 0));                       // he stops: everything stops
        ol.add(new Note("latch", 98.5, 0.9));
        ol.add(new Note("door", 99, 0.8));
        for (double b : new double[]{100.5, 101.5, 102.5, 103.5}) ol.add(new Note("step2", b, 0.35));
        ol.add(new Note("shut", 104, 0.9));
        seq(ol, Inst.ORGAN, 0.45, 100, "E2+B2/16");
        for (int b = 100; b < 116; b += 4) {
            hit(ol, Inst.HEART, 0.7, b);
            hit(ol, Inst.HEART, 0.45, b + 0.4);
        }
        seq(ol, Inst.STRING, 0.5, 108, "F4+B4/8");
        seq(ol, Inst.BOX, 0.4, 108, "B5/4 G5/4");
        SONGS.put(op.name, op);

        // ── The cellar stairs (menu, name, intro): D minor, an organ breathing under a music box ──
        Song m = new Song("menu", 72, 32, true, 0.5, 0.32);
        seq(m.layer(0), Inst.ORGAN, 0.8, 0, "D3+F3+A3/4 Bb2+D3+F3/4 G2+Bb2+D3/4 A2+C#3+E3/4 D3+F3+A3/4 C3+F3+A3/4 Bb2+D3+G3/4 A2+C#3+E3+G3/4");
        seq(m.layer(0), Inst.ORGAN, 0.6, 0, "D2/4 Bb1/4 G1/4 A1/4 D2/4 C2/4 Bb1/4 A1/4");
        seq(m.layer(0), Inst.BOX, 0.9, 0, "A4/1 D5/1 F5/1 E5/1 D5/2 C5/1 Bb4/1 A4/1 G4/1 Bb4/1 D5/1 C#5/3 -/1 "
            + "A4/1 D5/1 F5/1 A5/1 G5/1 F5/1 E5/1 C5/1 D5/1 Bb4/1 G4/1 Bb4/1 A4/2 C#5/1 E5/1");
        seq(m.layer(0), Inst.BOX, 0.35, 16.5, "D4/4 -/4 Bb3/4 -/2.5");          // a faint echo, lower
        SONGS.put(m.name, m);

        // ── Across the table (a round): E minor with a creeping half-step, heartbeat; check adds a layer ──
        Song t = new Song("table", 96, 64, true, 0.35, 0.26);
        ostinato(t.layer(0), 0, TABLE_ROOTS, 1);
        for (int bar = 0; bar < 16; bar++) {
            hit(t.layer(0), Inst.KICK, 0.55, bar * 4 + 2);                                   // the footsteps from the opening, still walking
            hit(t.layer(0), Inst.HEART, 0.7, bar * 4);
            hit(t.layer(0), Inst.HEART, 0.45, bar * 4 + 0.4);
        }
        seq(t.layer(0), Inst.ORGAN, 0.55, 0, "E3+G3+B3/8 C3+E3+G3/8 A2+C3+E3/8 B2+D#3+F#3/8 E3+G3+B3/8 F2+A2+C3/8 A2+C3+E3/8 B2+D#3+F#3+A3/8");
        seq(t.layer(0), Inst.BOX, 0.5, 0, "-/8 G5/2 F#5/2 E5/4 -/8 E5/2 D#5/2 B4/4");
        seq(t.layer(0), Inst.LEAD, 0.8, 32, "B4/2 G4/1 F#4/1 E4/3 -/1 F4/2 A4/1 C5/1 B4/3 -/1 A4/1 C5/1 E5/2 D5/1 C5/1 B4/2 A4/1 B4/1 C5/1 D#5/1 F#4/4");
        for (int i = 0; i < 128; i++) hit(t.layer(1), i % 2 == 0 ? Inst.TICK : Inst.TOCK, 0.8, i * 0.5);   // the clock
        for (int bar = 0; bar < 16; bar++) {
            hit(t.layer(1), Inst.HEART, 0.6, bar * 4 + 2);                                      // the heart races
            hit(t.layer(1), Inst.HEART, 0.4, bar * 4 + 2.4);
        }
        seq(t.layer(1), Inst.STRING, 0.9, 0, "F4+B4/8 F4+B4/8 F4+Bb4/8 F#4+C5/8 F4+B4/8 F#4+B4/8 E4+Bb4/8 F4+B4/8");
        SONGS.put(t.name, t);

        // ── The three dealers (shop): a crooked little waltz in A minor ──
        Song s = new Song("shop", 132, 48, true, 0.2, 0.22);
        String[] chord = {"Am", "Am", "E7", "E7", "Am", "Am", "Dm", "Dm", "Am", "Am", "Dm", "E7", "Am", "F", "E7", "Am"};
        Map<String, String[]> v = new HashMap<>();
        v.put("Am", new String[]{"A2", "E2", "A3+C4+E4"});
        v.put("E7", new String[]{"E2", "B1", "G#3+B3+D4"});
        v.put("Dm", new String[]{"D2", "A1", "A3+D4+F4"});
        v.put("F", new String[]{"F2", "C2", "A3+C4+F4"});
        for (int bar = 0; bar < 16; bar++) {
            String[] c = v.get(chord[bar]);
            seq(s.layer(0), Inst.BASS, 0.85, bar * 3, (bar % 2 == 0 ? c[0] : c[1]) + "/0.9");
            seq(s.layer(0), Inst.REED, 0.55, bar * 3 + 1, c[2] + "/0.55 -/0.45 " + c[2] + "/0.55");
        }
        seq(s.layer(0), Inst.LEAD, 0.8, 0, "E4/1 A4/1 C5/1 B4/1.5 A4/0.5 G#4/1 B4/2 E4/1 D5/1 C5/1 B4/1 "
            + "C5/1 E5/1 A5/1 G#5/1.5 F5/0.5 E5/1 D5/2 F5/1 E5/1 D5/1 C5/1 "
            + "A4/2 C5/1 B4/1 A4/1 E4/1 F4/1 A4/1 D5/1 C5/1 B4/1 G#4/1 "
            + "A4/1 C5/1 E5/1 F5/1.5 E5/0.5 C5/1 B4/1 D5/1 G#4/1 A4/3");
        seq(s.layer(0), Inst.BOX, 0.4, 24, "A5/3 G#5/3 F5/3 E5/3 A5/3 A5/3 G#5/3 A5/3");
        SONGS.put(s.name, s);

        // ── Between things (summary, after the lamp comes back): wind and a low drone ──
        Song a = new Song("ambient", 60, 16, true, 0.8, 0.4);
        seq(a.layer(0), Inst.ORGAN, 0.5, 0, "D2+A2/16");
        seq(a.layer(0), Inst.BOX, 0.3, 0, "-/6 A5/2 -/5 F5/2 -/1");
        SONGS.put(a.name, a);

        // ── The lamp goes out (game over): a dirge with a tolling bell ──
        Song o = new Song("over", 56, 16, true, 0.6, 0.4);
        seq(o.layer(0), Inst.ORGAN, 0.7, 0, "D3+F3+A3/4 D3+G3+Bb3/4 C#3+E3+A3/4 D3+F3+A3/4");
        seq(o.layer(0), Inst.ORGAN, 0.5, 0, "D2/16");
        for (int i = 0; i < 4; i++) seq(o.layer(0), Inst.BELL, 0.8, i * 4, "D3/4");
        SONGS.put(o.name, o);

        // ── Stings ──
        Song mate = new Song("mate", 100, 6, false, 0, 0.35);                    // he lunges: a boom and a screech
        hit(mate.layer(0), Inst.BOOM, 1.0, 0);
        seq(mate.layer(0), Inst.STAB, 1.0, 0, "E5+F5+Bb5/3");
        seq(mate.layer(0), Inst.STAB, 0.6, 0, "E2+F2/3");
        SONGS.put(mate.name, mate);

        Song win = new Song("win", 90, 8, false, 0, 0.35);                      // you won: a minor arpeggio that turns major
        seq(win.layer(0), Inst.BOX, 0.9, 0, "D5/0.5 F5/0.5 A5/0.5 D6/2.5");
        seq(win.layer(0), Inst.ORGAN, 0.8, 1.5, "D3+F#3+A3+D4/4");
        seq(win.layer(0), Inst.BELL, 0.5, 1.5, "D4/4");
        SONGS.put(win.name, win);

        Song out = new Song("lightsout", 80, 5, false, 0, 0.4);                 // the bulb fizzles and a bell sinks
        hit(out.layer(0), Inst.FIZZ, 1.0, 0);
        seq(out.layer(0), Inst.BELL, 0.9, 0.3, "A2/4");
        hit(out.layer(0), Inst.BOOM, 0.4, 0.3);
        SONGS.put(out.name, out);
    }

    // ════════ Synthesis ══════════════════════════════════════════
    /** The same hash the opening's pictures use, so sounds can follow what flickers on screen. */
    private static int hash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7fffffff;
    }

    private static double blep(double t, double dt) {
        if (t < dt) { t /= dt; return t + t - t * t - 1; }
        if (t > 1 - dt) { t = (t - 1) / dt; return t * t + t + t + 1; }
        return 0;
    }

    private static double saw(double ph, double dt) {
        return 2 * ph - 1 - blep(ph, dt);
    }

    private static double pulse(double ph, double dt, double duty) {
        double v = ph < duty ? 1 : -1;
        return v + blep(ph, dt) - blep((ph - duty + 1) % 1, dt);
    }

    /** A gentle state-variable low-pass (or band-pass) filter. */
    private static final class Filter {
        double low, band;

        double low(double in, double fc) {
            double f = 2 * Math.sin(Math.PI * Math.min(fc, RATE / 6.5) / RATE);
            low += f * band;
            double high = in - low - 1.1 * band;
            band += f * high;
            return low;
        }

        double band(double in, double fc, double q) {
            double f = 2 * Math.sin(Math.PI * Math.min(fc, RATE / 6.5) / RATE);
            low += f * band;
            double high = in - low - q * band;
            band += f * high;
            return band;
        }
    }

    private static double adsr(double t, double dur, double a, double d, double s, double r) {
        double v = t < a ? t / a : t < a + d ? 1 - (1 - s) * (t - a) / d : s;
        if (t > dur) v *= Math.max(0, 1 - (t - dur) / r);
        return v;
    }

    private static final Random NOISE = new Random(7);

    /** Renders one note into buf starting at sample `at`. */
    private static void render(float[] buf, int at, Note n, double secPerBeat) {
        double dur = n.len * secPerBeat, f = n.freq, vel = n.vel;
        double len;
        switch (n.inst) {
            case ORGAN: len = dur + 1.2; break;
            case BASS: len = dur + 0.12; break;
            case BOX: len = 2.4; break;
            case LEAD: len = dur + 0.2; break;
            case REED: len = dur + 0.1; break;
            case BELL: len = 6; break;
            case STRING: len = dur + 0.5; break;
            case HEART: len = 0.35; break;
            case TICK: case TOCK: len = 0.04; break;
            case BOOM: len = 3; break;
            case STAB: len = dur + 1.5; break;
            case FIZZ: len = 0.7; break;
            case KICK: len = 0.3; break;
            case JINGLE: len = 0.15; break;
            case CITY: len = dur + 1.5; break;
            case RAIN: len = dur + 1.5; break;
            default: len = dur;
        }
        int count = (int) (len * RATE);
        double[] ph = new double[4];
        if (n.inst == Inst.SAMPLE) {                                         // a whole sound effect, placed on the beat
            float[] b = makeSfx(n.sample);
            for (int i = 0; i < b.length && at + i < buf.length; i++) if (at + i >= 0) buf[at + i] += (float) (b[i] * vel);
            return;
        }
        Filter flt = new Filter(), flt2 = new Filter(), flt3 = new Filter();
        double dt = f / RATE;
        for (int i = 0; i < count && at + i < buf.length; i++) {
            if (at + i < 0) continue;
            double t = (double) i / RATE, x = 0;
            switch (n.inst) {
                case ORGAN: {
                    double det[] = {0.996, 1.0, 1.004};
                    for (int k = 0; k < 3; k++) {
                        x += saw(ph[k], dt * det[k]);
                        ph[k] = (ph[k] + dt * det[k]) % 1;
                    }
                    x += 0.4 * Math.sin(2 * Math.PI * ph[3]);
                    ph[3] = (ph[3] + dt / 2) % 1;
                    x = flt.low(x, 950 + 300 * Math.sin(t * 0.9)) * (1 + 0.07 * Math.sin(2 * Math.PI * 4.5 * t));
                    x *= 0.09 * vel * adsr(t, dur, 0.5, 0.3, 0.9, 1.2);
                    break;
                }
                case BASS: {
                    x = saw(ph[0], dt) + 0.7 * Math.sin(2 * Math.PI * ph[1]);
                    ph[0] = (ph[0] + dt) % 1;
                    ph[1] = (ph[1] + dt) % 1;
                    x = flt.low(x, 220 + 1300 * Math.exp(-t / 0.07));
                    x *= 0.3 * vel * adsr(t, dur, 0.004, 0.25, 0.55, 0.12);
                    break;
                }
                case BOX: {
                    double w = 2 * Math.PI * f * t;
                    x = Math.sin(w) * Math.exp(-t / 1.1) + 0.35 * Math.sin(2 * w) * Math.exp(-t / 0.5)
                        + 0.18 * Math.sin(4.16 * w) * Math.exp(-t / 0.22) + 0.08 * Math.sin(6.8 * w) * Math.exp(-t / 0.07);
                    x *= 0.22 * vel * Math.min(1, t / 0.002);
                    break;
                }
                case LEAD: {
                    double vib = 1 + 0.006 * Math.sin(2 * Math.PI * 5.2 * t) * Math.min(1, Math.max(0, (t - 0.25) / 0.3));
                    x = pulse(ph[0], dt * vib, 0.3);
                    ph[0] = (ph[0] + dt * vib) % 1;
                    x = flt.low(x, 2400);
                    x *= 0.075 * vel * adsr(t, dur, 0.01, 0.15, 0.7, 0.18);
                    break;
                }
                case REED: {
                    x = pulse(ph[0], dt * 0.9965, 0.45) + pulse(ph[1], dt * 1.0035, 0.45);
                    ph[0] = (ph[0] + dt * 0.9965) % 1;
                    ph[1] = (ph[1] + dt * 1.0035) % 1;
                    x = flt.low(x, 1900) * (1 + 0.18 * Math.sin(2 * Math.PI * 6.5 * t));
                    x *= 0.045 * vel * adsr(t, dur, 0.03, 0.1, 0.85, 0.08);
                    break;
                }
                case BELL: {
                    double[] ratio = {0.5, 1, 1.19, 2, 2.76, 4.07}, amp = {0.5, 1, 0.4, 0.55, 0.3, 0.15}, dec = {6, 4, 3, 2, 1, 0.5};
                    for (int k = 0; k < ratio.length; k++) x += amp[k] * Math.sin(2 * Math.PI * f * ratio[k] * t) * Math.exp(-t / dec[k]);
                    x *= 0.13 * vel * Math.min(1, t / 0.003);
                    break;
                }
                case STRING: {
                    x = saw(ph[0], dt * 0.997) + saw(ph[1], dt * 1.003);
                    ph[0] = (ph[0] + dt * 0.997) % 1;
                    ph[1] = (ph[1] + dt * 1.003) % 1;
                    x = flt.low(x, 1500) * (0.5 + 0.5 * Math.abs(Math.sin(2 * Math.PI * 6 * t)));
                    x *= 0.13 * vel * adsr(t, dur, 0.25, 0.1, 1, 0.4);
                    break;
                }
                case HEART: {
                    double fr = 48 + 60 * Math.exp(-t / 0.03);
                    ph[0] = (ph[0] + fr / RATE) % 1;
                    x = Math.sin(2 * Math.PI * ph[0]) * Math.exp(-t / 0.11) * 0.32 * vel;
                    break;
                }
                case TICK: case TOCK: {
                    double noise = NOISE.nextDouble() * 2 - 1;
                    x = flt.band(noise, n.inst == Inst.TICK ? 3600 : 2300, 0.25) * Math.exp(-t / 0.006) * 0.6 * vel;
                    break;
                }
                case BOOM: {
                    double fr = 36 + 55 * Math.exp(-t / 0.1);
                    ph[0] = (ph[0] + fr / RATE) % 1;
                    double noise = NOISE.nextDouble() * 2 - 1;
                    x = (Math.sin(2 * Math.PI * ph[0]) * Math.exp(-t / 0.9) + 0.5 * flt.low(noise, 180) * Math.exp(-t / 0.35)) * 0.6 * vel;
                    break;
                }
                case STAB: {
                    x = saw(ph[0], dt) + saw(ph[1], dt * 1.012);
                    ph[0] = (ph[0] + dt) % 1;
                    ph[1] = (ph[1] + dt * 1.012) % 1;
                    x = flt.low(x, 3200 * Math.exp(-t / 1.5) + 300) * (0.6 + 0.4 * Math.sin(2 * Math.PI * 11 * t));
                    x *= 0.07 * vel * Math.min(1, t / 0.004) * Math.exp(-t / 1.2);
                    break;
                }
                case KICK: {
                    double fr = 50 + 110 * Math.exp(-t / 0.025);
                    ph[0] = (ph[0] + fr / RATE) % 1;
                    x = (Math.sin(2 * Math.PI * ph[0]) * Math.exp(-t / 0.16) + (t < 0.004 ? (NOISE.nextDouble() * 2 - 1) * 0.4 : 0)) * 0.55 * vel;
                    break;
                }
                case JINGLE: {                                               // keys on a belt
                    double[] fr = {3150, 4420, 5930, 7300};
                    for (int k = 0; k < fr.length; k++) x += Math.sin(2 * Math.PI * fr[k] * t + k) * Math.exp(-t / (0.035 + 0.01 * k));
                    x *= 0.03 * vel;
                    break;
                }
                case CITY: {                                                 // the city: rumble, traffic, craft passing, a siren far off
                    double fadeIn = Math.min(1, t / 1.5), fadeOut = t > dur ? Math.max(0, 1 - (t - dur) / 1.5) : 1;
                    double noise = NOISE.nextDouble() * 2 - 1;
                    ph[0] = ph[0] * 0.995 + noise * 0.05;
                    double rumble = flt.low(ph[0], 110) * 2.2;
                    double traffic = flt2.low(noise, 500) * (0.25 + 0.15 * Math.sin(t * 0.37) + 0.1 * Math.sin(t * 1.13));
                    double cyc = (t % 5.5) / 5.5, pass = Math.exp(-Math.pow((cyc - 0.5) / 0.12, 2));
                    double whoosh = flt3.band(noise, 400 + 1600 * cyc, 0.5) * pass * 0.5;
                    double st = t % 23, siren = st < 6 ? Math.sin(2 * Math.PI * (st % 1.2 < 0.6 ? 660 : 820) * t) * 0.03 * Math.sin(Math.PI * st / 6) : 0;
                    x = (rumble + traffic + whoosh + siren) * 0.5 * vel * fadeIn * fadeOut;
                    break;
                }
                case RAIN: {                                                 // rain: a hiss, and drops hitting things close by
                    double fadeIn = Math.min(1, t / 1.0), fadeOut = t > dur ? Math.max(0, 1 - (t - dur) / 1.5) : 1;
                    double noise = NOISE.nextDouble() * 2 - 1;
                    double hiss = flt.band(noise, 5200, 1.3) * 0.5 + flt2.low(noise, 700) * 0.25;
                    if (NOISE.nextDouble() < 0.0006) ph[0] = 0.6;                // a drop
                    ph[0] *= 0.993;
                    double drop = flt3.band(noise * ph[0], 2600, 0.5) * 2.5;
                    x = (hiss + drop) * 0.12 * vel * fadeIn * fadeOut;
                    break;
                }
                case FIZZ: {
                    double noise = NOISE.nextDouble() * 2 - 1;
                    double buzz = ((int) (t * 120) % 2 == 0 ? 1 : -1) * 0.3;
                    boolean on = Math.sin(t * 90) * Math.sin(t * 31) > -0.2;
                    x = (flt.band(noise, 5000, 0.6) + flt2.low(buzz, 900)) * (on ? 1 : 0.1) * Math.exp(-t / 0.25) * 0.3 * vel;
                    break;
                }
                default: break;
            }
            buf[at + i] += (float) x;
        }
    }

    /** Wind in the cellar: brown noise breathing through a slow filter, made to loop seamlessly over `len` samples. */
    private static float[] wind(int len, double amount) {
        int fade = RATE / 2;
        double[] w = new double[len + fade];
        double b = 0;
        Filter flt = new Filter();
        Random r = new Random(11);
        for (int i = 0; i < w.length; i++) {
            double t = (double) i / RATE;
            b = b * 0.985 + (r.nextDouble() - 0.5) * 0.12;
            w[i] = flt.low(b, 260 + 160 * Math.sin(2 * Math.PI * t / 7.3) + 110 * Math.sin(2 * Math.PI * t / 3.1)) * (0.7 + 0.3 * Math.sin(2 * Math.PI * t / 9.7));
        }
        float[] out = new float[len];
        for (int i = 0; i < len; i++) {
            double v = i < fade ? w[i] * i / fade + w[len + i] * (1 - (double) i / fade) : w[i];
            out[i] = (float) (Math.tanh(v * 4) * amount * 0.12);
        }
        return out;
    }

    /** A small cellar reverb (four damped combs and two all-passes, after Freeverb). */
    private static void reverb(float[] x, double wet) {
        int[] cd = {1557, 1617, 1491, 1422};
        int[] ad = {556, 441};
        float[][] cb = new float[4][];
        float[] cs = new float[4];
        int[] ci = new int[4];
        for (int k = 0; k < 4; k++) cb[k] = new float[cd[k]];
        float[][] ab = {new float[ad[0]], new float[ad[1]]};
        int[] ai = new int[2];
        double fb = 0.82, damp = 0.2;
        for (int i = 0; i < x.length; i++) {
            double in = x[i] * 0.25, out = 0;
            for (int k = 0; k < 4; k++) {
                float y = cb[k][ci[k]];
                cs[k] = (float) (y * (1 - damp) + cs[k] * damp);
                cb[k][ci[k]] = (float) (in + cs[k] * fb);
                ci[k] = (ci[k] + 1) % cd[k];
                out += y;
            }
            for (int k = 0; k < 2; k++) {
                float bo = ab[k][ai[k]];
                double y = -out + bo;
                ab[k][ai[k]] = (float) (out + bo * 0.5);
                ai[k] = (ai[k] + 1) % ad[k];
                out = y;
            }
            x[i] = (float) (x[i] * (1 - wet * 0.4) + out * wet);
        }
    }

    /** How loud songs sit (RMS, about -23 dBFS) and how high a sting peaks. */
    private static final double MUSIC_RMS = 0.07, STING_PEAK = 0.55;

    /** Renders a song: one buffer per layer. Looping songs are rendered twice and the second pass kept, so tails wrap round. */
    private static float[][] render(Song s) {
        double spb = 60.0 / s.bpm;
        int len = (int) Math.round(s.beats * spb * RATE);
        int tail = 7 * RATE;
        float[][] out = new float[s.layers.size()][];
        float[] w = s.wind > 0 ? wind(len, s.wind) : null;
        for (int l = 0; l < s.layers.size(); l++) {
            int copies = s.loop ? 2 : 1;
            float[] buf = new float[len * copies + tail];
            for (int c = 0; c < copies; c++) {
                for (Note n : s.layers.get(l)) render(buf, c * len + (int) Math.round(n.beat * spb * RATE), n, spb);
                if (w != null && l == 0) for (int i = 0; i < len; i++) buf[c * len + i] += w[i];
            }
            reverb(buf, s.wet);
            out[l] = s.loop ? Arrays.copyOfRange(buf, len, 2 * len) : trim(buf);
        }
        // Level it: songs to a quiet, even loudness under the dialogue; stings to a fixed peak.
        double sum = 0, peak = 0;
        for (float v : out[0]) { sum += v * v; peak = Math.max(peak, Math.abs(v)); }
        double k = s.loop ? MUSIC_RMS / Math.max(1e-6, Math.sqrt(sum / out[0].length)) : STING_PEAK / Math.max(1e-6, peak);
        if (s.normTo > 0) {
            double spb2 = 60.0 / s.bpm, win = 0;
            int a = (int) (s.normFrom * spb2 * RATE), b = Math.min(out[0].length, (int) (s.normTo * spb2 * RATE));
            for (int i = a; i < b; i++) win += out[0][i] * out[0][i];
            k = MUSIC_RMS / Math.max(1e-6, Math.sqrt(win / Math.max(1, b - a)));
        }
        for (float[] layer : out) for (int i = 0; i < layer.length; i++) layer[i] *= k;
        return out;
    }

    private static float[] trim(float[] b) {
        int end = b.length;
        while (end > 1 && Math.abs(b[end - 1]) < 1e-4) end--;
        return Arrays.copyOf(b, end);
    }

    // ════════ Sound effects ══════════════════════════════════════
    private static final Map<String, float[]> SFX = new HashMap<>();

    private static float[] makeSfx(String name) {
        float[] b = new float[10 * RATE];
        Filter f = new Filter(), f2 = new Filter();
        Random r = new Random(name.hashCode());
        int n;
        switch (name) {
            case "move": case "capture": {                                   // a wooden piece set down on wood
                boolean heavy = name.equals("capture");
                n = (int) (RATE * (heavy ? 0.35 : 0.12));
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE;
                    double knock = f.band(r.nextDouble() * 2 - 1, heavy ? 650 : 950, 0.5) * Math.exp(-t / 0.012);
                    double body = Math.sin(2 * Math.PI * (heavy ? 120 : 190) * t) * Math.exp(-t / (heavy ? 0.06 : 0.035));
                    double x = 0.5 * knock + 0.35 * body;
                    if (heavy && t > 0.07) {                                   // the taken piece knocks over
                        double t2 = t - 0.07;
                        x += 0.4 * f2.band(r.nextDouble() * 2 - 1, 800, 0.5) * Math.exp(-t2 / 0.01) + 0.25 * Math.sin(2 * Math.PI * 160 * t2) * Math.exp(-t2 / 0.04);
                    }
                    b[i] = (float) (x * 0.8);
                }
                break;
            }
            case "check": {
                Note bell = new Note(Inst.BELL, 0, 1, 0.9, freq("E4"));
                float[] tmp = new float[3 * RATE];
                render(tmp, 0, bell, 1);
                reverb(tmp, 0.3);
                return tmp;
            }
            case "coin": {
                n = (int) (RATE * 0.25);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, fr = t < 0.06 ? 988 : 1319;
                    double tt = t < 0.06 ? t : t - 0.06;
                    b[i] = (float) ((Math.sin(2 * Math.PI * fr * t) + 0.3 * Math.sin(2 * Math.PI * fr * 2.7 * t)) * Math.exp(-tt / 0.07) * 0.22);
                }
                break;
            }
            case "deny": {
                n = (int) (RATE * 0.22);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE;
                    double x = pulse((110 * t) % 1, 110.0 / RATE, 0.5) + pulse((116 * t) % 1, 116.0 / RATE, 0.5);
                    b[i] = (float) (f.low(x, 700) * 0.09 * Math.min(1, (0.22 - t) / 0.03));
                }
                break;
            }
            case "card": {                                                  // paper sliding
                n = (int) (RATE * 0.22);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE;
                    b[i] = (float) (f.band(r.nextDouble() * 2 - 1, 2000 + 18000 * t, 0.7) * Math.sin(Math.PI * t / 0.22) * 0.35);
                }
                break;
            }
            case "step1": case "step2": case "step3": {                       // a boot on a stone step
                double pitch = 0.85 + 0.15 * (name.charAt(4) - '1');
                n = (int) (RATE * 0.18);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, noise = r.nextDouble() * 2 - 1;
                    double heel = f.low(noise, 420 * pitch) * Math.exp(-t / 0.03) * 1.6;
                    double grit = f2.band(noise, 2600 * pitch, 0.9) * Math.exp(-t / 0.05) * 0.25;
                    double thud = Math.sin(2 * Math.PI * 75 * pitch * t) * Math.exp(-t / 0.05) * 0.5;
                    b[i] = (float) ((heel + grit + thud) * 0.6);
                }
                break;
            }
            case "drip": {                                                  // water falling into a puddle somewhere
                n = (int) (RATE * 0.2);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE;
                    b[i] = (float) (Math.sin(2 * Math.PI * (900 + 1300 * Math.exp(-t / 0.012)) * t) * Math.exp(-t / 0.05) * 0.18);
                }
                break;
            }
            case "creak": {                                                 // old wood shifting under weight
                n = (int) (RATE * 0.8);
                double ph = 0;
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE;
                    ph += (38 + 14 * Math.sin(t * 9) + 6 * Math.sin(t * 23)) / RATE;
                    double click = (ph % 1) < 0.08 ? 1 : 0;
                    double env = Math.sin(Math.PI * t / 0.8) * (0.6 + 0.4 * Math.sin(t * 31));
                    b[i] = (float) (f.band(click, 700 + 300 * Math.sin(t * 4), 0.3) * env * 0.5);
                }
                break;
            }
            case "chair": {                                                 // a chair dragged on stone, then set down
                n = (int) (RATE * 0.75);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, noise = r.nextDouble() * 2 - 1, x;
                    if (t < 0.5) x = f.band(noise, 420 + 350 * t + 80 * Math.sin(t * 70), 0.25) * Math.sin(Math.PI * t / 0.5) * (0.6 + 0.4 * Math.sin(t * 95)) * 0.5;
                    else x = (f2.low(noise, 300) * 1.5 + Math.sin(2 * Math.PI * 90 * t)) * Math.exp(-(t - 0.5) / 0.04) * 0.4;
                    b[i] = (float) x;
                }
                break;
            }
            case "hiss": {                                                  // gas
                n = (int) (RATE * 1.6);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE;
                    b[i] = (float) (f.band(r.nextDouble() * 2 - 1, 4500, 1.4) * Math.min(1, t / 0.25) * Math.exp(-Math.max(0, t - 0.6) / 0.4) * 0.25);
                }
                break;
            }
            case "crack": {                                                 // something hard splitting
                n = (int) (RATE * 0.5);
                double[] at = {0, 0.012, 0.02, 0.041, 0.06, 0.09, 0.13};
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, noise = r.nextDouble() * 2 - 1, x = 0;
                    for (double a : at) if (t >= a) x += Math.exp(-(t - a) / 0.004);
                    x = f.band(noise * x, 3000, 0.8) + Math.sin(2 * Math.PI * 70 * t) * Math.exp(-t / 0.1) * 0.6;
                    b[i] = (float) (x * 0.5);
                }
                break;
            }
            case "sparks": {                                                // a welding arc spitting
                n = (int) (RATE * 1.6);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, density = 0.004 * Math.exp(-t / 0.6);
                    double imp = r.nextDouble() < density ? (r.nextDouble() * 2 - 1) * 3 : 0;
                    double arc = (r.nextDouble() * 2 - 1) * 0.15 * Math.exp(-t / 0.3);
                    b[i] = (float) (f.band(imp + arc, 5000, 0.7) * 0.3);
                }
                break;
            }
            case "flick": {                                                 // a lighter wheel: a scrape, sparks, a tick of metal
                n = (int) (RATE * 0.35);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, noise = r.nextDouble() * 2 - 1;
                    double scrape = t < 0.07 ? f.band(noise, 3500, 0.7) * (0.6 + 0.4 * Math.sin(t * 2 * Math.PI * 180)) : 0;
                    double spark = r.nextDouble() < 0.02 * Math.exp(-t / 0.08) ? (r.nextDouble() * 2 - 1) * 2 : 0;
                    double tick = Math.sin(2 * Math.PI * 4200 * t) * Math.exp(-t / 0.008);
                    b[i] = (float) ((scrape * 0.5 + f2.band(spark, 6500, 0.7) * 0.6 + tick * 0.15) * 0.7);
                }
                break;
            }
            case "catch": {                                                 // the flame catching: a soft breathy whoosh
                n = (int) (RATE * 0.8);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, noise = r.nextDouble() * 2 - 1;
                    double env = Math.min(1, t / 0.03) * Math.exp(-t / 0.3) * (1 + 0.3 * Math.sin(t * 2 * Math.PI * 9));
                    b[i] = (float) (f.low(noise, 900 + 700 * Math.min(1, t / 0.1)) * env * 0.9);
                }
                break;
            }
            case "clack": {                                                 // the lid snapping shut
                n = (int) (RATE * 0.15);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, x = 0;
                    for (double a : new double[]{0, 0.028}) if (t >= a) x += (Math.sin(2 * Math.PI * 2800 * (t - a)) + 0.6 * Math.sin(2 * Math.PI * 5100 * (t - a))) * Math.exp(-(t - a) / 0.012);
                    x += Math.sin(2 * Math.PI * 300 * t) * Math.exp(-t / 0.02) * 0.6;
                    b[i] = (float) (x * 0.22);
                }
                break;
            }
            case "sob": {                                                   // a breath that catches and shakes: crying, held in
                n = (int) (RATE * 0.55);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, noise = r.nextDouble() * 2 - 1;
                    double env = Math.min(1, t / 0.04) * Math.exp(-t / 0.22);
                    double shake = 0.55 + 0.45 * Math.signum(Math.sin(2 * Math.PI * 11 * t));      // the breath stutters
                    double air = f.band(noise, 1300 - 500 * Math.min(1, t / 0.4), 0.5);
                    double chest = Math.sin(2 * Math.PI * (150 - 30 * t) * t) * 0.12 * Math.exp(-t / 0.12);
                    b[i] = (float) ((air * 0.4 * shake + chest) * env);
                }
                break;
            }
            case "drag": {                                                  // drawing on the cigarette: breath and the paper crackling
                n = (int) (RATE * 1.1);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, noise = r.nextDouble() * 2 - 1, env = Math.sin(Math.PI * t / 1.1);
                    double breath = f.band(noise, 900, 0.5) * env * 0.25;
                    double crackle = r.nextDouble() < 0.006 * env ? (r.nextDouble() * 2 - 1) * 1.5 : 0;
                    b[i] = (float) (breath + f2.band(crackle, 5000, 0.6) * 0.5);
                }
                break;
            }
            case "exhale": {                                                // smoke breathed out, falling away
                n = (int) (RATE * 1.5);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, noise = r.nextDouble() * 2 - 1;
                    double env = Math.min(1, t / 0.12) * Math.exp(-t / 0.55);
                    b[i] = (float) ((f.band(noise, 1400 - 700 * Math.min(1, t / 1.2), 0.6) * 0.5 + f2.low(noise, 300) * 0.3) * env * 0.6);
                }
                break;
            }
            case "sizzle": {                                                // the butt hitting a puddle
                n = (int) (RATE * 0.5);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, noise = r.nextDouble() * 2 - 1;
                    double pop = r.nextDouble() < 0.01 * Math.exp(-t / 0.1) ? 2 : 0;
                    b[i] = (float) ((f.band(noise, 6000, 1.2) * 0.5 * Math.exp(-t / 0.15) + f2.band(pop * noise, 3500, 0.6)) * 0.45);
                }
                break;
            }
            case "grind": {                                                 // a boot coming down and twisting on wet grit
                n = (int) (RATE * 0.8);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, noise = r.nextDouble() * 2 - 1;
                    double thud = (Math.sin(2 * Math.PI * 80 * t) + f2.low(noise, 250)) * Math.exp(-t / 0.05) * 0.6;
                    double grains = t > 0.08 && t < 0.7 ? f.band(noise, 1800, 0.5) * (0.5 + 0.5 * Math.signum(Math.sin(t * 2 * Math.PI * 38))) * Math.sin(Math.PI * (t - 0.08) / 0.62) : 0;
                    b[i] = (float) ((thud + grains * 0.5) * 0.6);
                }
                break;
            }
            case "dryer": {                                                 // a hand dryer: motor spins up, air roars, hands move in it
                n = (int) (RATE * 5.8);
                double ph = 0, ph2 = 0;
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, noise = r.nextDouble() * 2 - 1;
                    double spin = t < 0.6 ? t / 0.6 : t > 5.0 ? Math.max(0, (5.8 - t) / 0.8) : 1;
                    double hands = t > 1.0 && t < 4.8 ? 1 + 0.4 * Math.sin(t * 2.6 + Math.sin(t * 1.1) * 2) : 1;
                    double air = (f.low(noise, 200 + 1700 * spin) * 1.2 - f2.low(noise, 150) * 0.5) * hands;
                    ph = (ph + 95 * spin / RATE) % 1;
                    ph2 = (ph2 + 1150 * spin / RATE) % 1;
                    double motor = Math.sin(2 * Math.PI * ph) * 0.6 + Math.sin(4 * Math.PI * ph) * 0.3 + Math.sin(6 * Math.PI * ph + 0.5) * 0.15;
                    double whine = Math.sin(2 * Math.PI * ph2) * 0.05;
                    b[i] = (float) ((air * 0.55 * Math.pow(spin, 1.5) + motor * 0.12 * spin + whine * spin) * 0.6);
                }
                break;
            }
            case "hum": {                                                   // a fluorescent tube; crackles when the picture flickers
                n = (int) (RATE * 9.4);
                double ph = 0;
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, beat = t / (60.0 / OPENING_BPM), noise = r.nextDouble() * 2 - 1;
                    ph = (ph + 100.0 / RATE) % 1;
                    double buzz = f.low((ph < 0.5 ? 0.3 : -0.3) + Math.sin(4 * Math.PI * ph) * 0.2, 900);
                    boolean dip = hash((int) (beat * 6), 7) % 11 == 0 || (beat > 10.4 && beat < 10.6);
                    double crackle = dip ? f2.band(noise, 3000, 0.6) * 0.8 : 0;
                    b[i] = (float) ((buzz * (dip ? 0.4 : 1) + crackle) * 0.05 * Math.min(1, (9.4 - t) / 0.4));
                }
                break;
            }
            case "water": {                                                 // a tap running
                n = RATE * 6;
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, noise = r.nextDouble() * 2 - 1;
                    double bubbles = 0.6 + 0.4 * Math.sin(t * 37 + Math.sin(t * 13) * 3);
                    double w = f.band(noise, 1800 + 600 * Math.sin(t * 5.3), 0.4) * bubbles + f2.low(noise, 400) * 0.5;
                    b[i] = (float) (w * Math.min(1, t / 0.15) * Math.min(1, (6 - t) / 0.1) * 0.35);
                }
                break;
            }
            case "scrub": {                                                 // soapy hands
                n = RATE * 5;
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, env = Math.pow(Math.max(0, Math.sin(2 * Math.PI * 3.5 * t)), 2) * (0.7 + 0.3 * Math.sin(t * 1.7));
                    b[i] = (float) (f.band(r.nextDouble() * 2 - 1, 2500, 0.8) * env * Math.min(1, (5 - t) / 0.3) * 0.25);
                }
                break;
            }
            case "squeak": {                                                // the tap turned off
                n = (int) (RATE * 0.5);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, x = 0;
                    if (t < 0.35) x = Math.sin(2 * Math.PI * (1100 + 1100 * t) * t + Math.sin(t * 250) * 0.6) * Math.sin(Math.PI * t / 0.35) * 0.1;
                    else x = Math.sin(2 * Math.PI * 300 * (t - 0.35)) * Math.exp(-(t - 0.35) / 0.05) * 0.3;
                    b[i] = (float) x;
                }
                break;
            }
            case "towel": {                                                 // paper towels pulled
                n = (int) (RATE * 0.8);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, x = 0;
                    for (double a : new double[]{0, 0.25, 0.5}) if (t >= a) x += Math.exp(-(t - a) / 0.06);
                    b[i] = (float) (f.band((r.nextDouble() * 2 - 1) * (r.nextDouble() < 0.3 ? 1 : 0.3), 4000, 1) * x * 0.4);
                }
                break;
            }
            case "latch": {
                n = (int) (RATE * 0.2);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE, x = 0;
                    for (double a : new double[]{0, 0.09}) if (t >= a) x += f.band(r.nextDouble() * 2 - 1, 3000, 0.6) * Math.exp(-(t - a) / 0.003) + Math.sin(2 * Math.PI * 1200 * (t - a)) * Math.exp(-(t - a) / 0.01) * 0.3;
                    b[i] = (float) (x * 0.5);
                }
                break;
            }
            case "door": {                                                  // a heavy door, hinges complaining, air moving
                n = (int) (RATE * 1.4);
                double ph = 0;
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE;
                    ph += (26 + 10 * Math.sin(t * 6) + 4 * Math.sin(t * 17)) / RATE;
                    double creak = f.band((ph % 1) < 0.1 ? 1 : 0, 380 + 120 * Math.sin(t * 3), 0.3) * Math.sin(Math.PI * Math.min(1, t / 0.9)) * 0.7;
                    double air = f2.low(r.nextDouble() * 2 - 1, 800) * Math.exp(-Math.pow((t - 0.8) / 0.3, 2)) * 0.6;
                    b[i] = (float) ((creak + air) * 0.6);
                }
                break;
            }
            case "shut": {                                                  // the door shuts behind him
                n = (int) (RATE * 0.6);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE;
                    double x = Math.sin(2 * Math.PI * 60 * t) * Math.exp(-t / 0.3) + f.low(r.nextDouble() * 2 - 1, 300) * Math.exp(-t / 0.08) * 1.5;
                    if (t > 0.05) x += f2.band(r.nextDouble() * 2 - 1, 2500, 0.6) * Math.exp(-(t - 0.05) / 0.004);
                    b[i] = (float) (x * 0.5);
                }
                break;
            }
            default: n = 1;
        }
        if (name.equals("dryer") || name.equals("door") || name.equals("shut") || name.equals("latch") || name.equals("squeak") || name.equals("towel")) {
            float[] wet = Arrays.copyOf(b, n + RATE);                        // a small tiled room
            reverb(wet, 0.3);
            return trim(wet);
        }
        if (name.startsWith("step") || name.equals("drip") || name.equals("creak") || name.equals("chair") || name.equals("crack")) {
            float[] wet = Arrays.copyOf(b, n + RATE);                        // these happen in the cellar: let it answer
            reverb(wet, 0.45);
            return trim(wet);
        }
        return Arrays.copyOf(b, n);
    }

    private static float[] blipSound(int pitch) {
        int n = RATE * 28 / 1000;
        float[] b = new float[n];
        double dt = (double) pitch / RATE;
        for (int i = 0; i < n; i++) b[i] = (float) (pulse((i * dt) % 1, dt, 0.5) * 0.06 * (1 - (double) i / n));
        return b;
    }

    // ════════ Playing ════════════════════════════════════════════
    private static final class Playing {
        final String name;
        final float[][] layers;
        final boolean loop;
        int pos;
        double gain, target;
        /** For a one-off song: what starts the moment it reaches `endAt` (its last beat), while its echo dies away. */
        String next;
        int endAt = Integer.MAX_VALUE;
        final double[] layerGain, layerTarget;

        Playing(String name, float[][] layers, boolean loop, double gain, double target) {
            this.name = name;
            this.layers = layers;
            this.loop = loop;
            this.gain = gain;
            this.target = target;
            layerGain = new double[layers.length];
            layerTarget = new double[layers.length];
            layerGain[0] = layerTarget[0] = 1;
        }
    }

    private static final Map<String, float[][]> RENDERED = new HashMap<>();
    private static final List<Playing> MUSIC = new ArrayList<>();
    private static final List<Playing> STINGS = new ArrayList<>();
    /** A sound effect waiting to play or playing: starts at sample `at` of the output clock. */
    private static final class Fx {
        final float[] b;
        final float gain;
        final long at;
        int pos;

        Fx(float[] b, float gain, long at) {
            this.b = b;
            this.gain = gain;
            this.at = at;
        }
    }

    private static final List<Fx> EFFECTS = new ArrayList<>();
    private static long clock, nextAmbient = 4L * RATE;
    private static boolean ambientOn = true;
    private static final Random AMBIENT = new Random();
    private static String wanted;
    private static double tension;
    private static boolean started, broken;
    private static volatile boolean muted;

    /** Opens the sound line and starts rendering every song in the background. Safe to call twice. */
    public static synchronized void start() {
        if (started) return;
        started = true;
        Thread renderer = new Thread(() -> {
            for (Song s : SONGS.values()) {
                float[][] r = render(s);
                synchronized (Music.class) {
                    RENDERED.put(s.name, r);
                    if (s.name.equals(wanted) && current() == null) begin(s.name);
                }
            }
        }, "music-render");
        renderer.setDaemon(true);
        renderer.setPriority(Thread.MIN_PRIORITY);
        renderer.start();
        Thread player = new Thread(Music::stream, "music-out");
        player.setDaemon(true);
        player.start();
    }

    private static Playing current() {
        for (Playing p : MUSIC) if (p.target > 0) return p;
        return null;
    }

    private static String chainFrom, chainTo;

    private static void begin(String name) {
        Song song = SONGS.get(name);
        boolean loop = song == null || song.loop;
        Playing p = new Playing(name, RENDERED.get(name), loop, loop ? 0 : 1, 1);
        if (!loop) p.endAt = (int) (song.beats * 60 / song.bpm * RATE);
        if (name.equals(chainFrom)) p.next = chainTo;
        p.layerTarget[0] = 1;
        if (p.layers.length > 1) p.layerTarget[1] = p.layerGain[1] = tension;
        MUSIC.add(p);
    }

    /** Crossfades to a song (null = fade to silence). Ignored if that song is already playing. */
    public static synchronized void play(String name) {
        start();
        Playing cur = current();
        if (cur != null && cur.name.equals(name)) return;
        wanted = name;
        for (Playing p : MUSIC) p.target = 0;
        if (name != null && RENDERED.containsKey(name)) begin(name);
    }

    /** Plays a one-off song (the opening), then goes straight into `next` on its last beat. */
    public static synchronized void playThen(String name, String next) {
        chainFrom = name;
        chainTo = next;
        play(name);
    }

    /** Seconds into a playing song as heard (allowing for the sound card's buffer), or -1 if it isn't playing. */
    public static synchronized double position(String name) {
        for (Playing p : MUSIC) if (p.name.equals(name)) return Math.max(0, p.pos - 4096) / (double) RATE;
        return -1;
    }

    /** True if there is no sound device, so pictures must keep their own time. */
    public static boolean silent() {
        return broken;
    }

    /** How tense the table music is: 0 calm, 1 your king is in check (fades the second layer in). */
    public static synchronized void tension(double t) {
        tension = t;
        for (Playing p : MUSIC) if (p.layers.length > 1) p.layerTarget[1] = t;
    }

    /** A short piece played once over the music, which ducks under it. */
    public static synchronized void sting(String name) {
        start();
        float[][] r = RENDERED.get(name);
        if (r == null) {
            Song s = SONGS.get(name);
            if (s == null) return;
            r = render(s);
            RENDERED.put(name, r);
        }
        STINGS.add(new Playing(name, r, false, 1, 1));
    }

    public static synchronized void sfx(String name) {
        later(name, 0, 1);
    }

    /** An effect `delay` seconds from now at a volume (footsteps going away get quieter). */
    public static synchronized void later(String name, double delay, double gain) {
        start();
        float[] b = SFX.computeIfAbsent(name, Music::makeSfx);
        EFFECTS.add(new Fx(b, (float) gain, clock + (long) (delay * RATE)));
    }

    /** Footsteps: n steps, `gap` seconds apart, fading from gain g0 to g1 (going away or coming closer). */
    public static synchronized void steps(int n, double delay, double gap, double g0, double g1) {
        for (int i = 0; i < n; i++) {
            double k = n == 1 ? 0 : (double) i / (n - 1);
            later("step" + (1 + i % 3), delay + i * gap + (i % 2) * 0.03, g0 + (g1 - g0) * k);
        }
    }

    /** The cellar's own sounds now and then (drips, creaks). On by default. */
    public static synchronized void ambient(boolean on) {
        ambientOn = on;
    }

    private static final Map<Integer, float[]> BLIPS = new HashMap<>();

    /** The typewriter voice: a tiny square-wave blip at a character's pitch. */
    public static synchronized void blip(int pitch) {
        start();
        if (EFFECTS.size() > 16) return;
        EFFECTS.add(new Fx(BLIPS.computeIfAbsent(pitch, Music::blipSound), 1, clock));
    }

    public static void setMuted(boolean m) {
        muted = m;
    }

    private static double duck = 1;

    /** The audio thread: mixes music, stings and effects in small blocks and writes them out. */
    private static void stream() {
        SourceDataLine line;
        try {
            AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
            line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, 4096 * 2);
            line.start();
        } catch (Exception e) {
            broken = true;
            return;
        }
        int block = 512;
        float[] mix = new float[block];
        byte[] out = new byte[block * 2];
        double fadeStep = 1.0 / (1.4 * RATE / block), layerStep = 1.0 / (2.0 * RATE / block);
        while (true) {
            Arrays.fill(mix, 0);
            synchronized (Music.class) {
                double duckTarget = STINGS.isEmpty() ? 1 : 0.3;
                duck += (duckTarget - duck) * 0.08;
                List<String> chained = new ArrayList<>();
                for (Iterator<Playing> it = MUSIC.iterator(); it.hasNext(); ) {
                    Playing p = it.next();
                    double g0 = p.gain;
                    p.gain = p.target > p.gain ? Math.min(p.target, p.gain + fadeStep) : Math.max(p.target, p.gain - fadeStep);
                    double[] l0 = p.layerGain.clone();
                    for (int l = 0; l < p.layers.length; l++) {
                        double tg = p.layerTarget[l];
                        p.layerGain[l] = tg > p.layerGain[l] ? Math.min(tg, p.layerGain[l] + layerStep) : Math.max(tg, p.layerGain[l] - layerStep);
                    }
                    int len = p.layers[0].length;
                    for (int i = 0; i < block; i++) {
                        double k = (double) i / block, g = (g0 + (p.gain - g0) * k) * duck, v = 0;
                        for (int l = 0; l < p.layers.length; l++) {
                            double lg = l0[l] + (p.layerGain[l] - l0[l]) * k;
                            if (lg > 0) v += p.layers[l][p.pos % p.layers[l].length] * lg;
                        }
                        if (p.loop || p.pos < len) mix[i] += (float) (v * g);
                        if (p.loop) p.pos = (p.pos + 1) % len;
                        else p.pos++;
                        if (p.pos == p.endAt && p.next != null && p.target > 0) {
                            chained.add(p.next);
                            p.target = 0.0001;                                 // let the opening's echo ring out under the next song
                        }
                    }
                    if ((p.target == 0 && p.gain <= 0) || (!p.loop && p.pos >= len)) it.remove();
                }
                for (String name : chained) {
                    Playing q = new Playing(name, RENDERED.get(name), true, 1, 1);
                    if (q.layers.length > 1) q.layerTarget[1] = q.layerGain[1] = tension;
                    MUSIC.add(q);
                }
                for (Iterator<Playing> it = STINGS.iterator(); it.hasNext(); ) {
                    Playing p = it.next();
                    float[] b = p.layers[0];
                    for (int i = 0; i < block && p.pos < b.length; i++) mix[i] += b[p.pos++];
                    if (p.pos >= b.length) it.remove();
                }
                if (ambientOn && clock >= nextAmbient) {                      // a drip or a creak, somewhere in the dark
                    String what = AMBIENT.nextInt(3) == 0 ? "creak" : "drip";
                    EFFECTS.add(new Fx(SFX.computeIfAbsent(what, Music::makeSfx), (float) (0.25 + 0.35 * AMBIENT.nextDouble()), clock));
                    nextAmbient = clock + (long) ((4 + AMBIENT.nextDouble() * 8) * RATE);
                }
                for (int e = EFFECTS.size() - 1; e >= 0; e--) {
                    Fx fx = EFFECTS.get(e);
                    int from = (int) Math.max(0, Math.min(block, fx.at - clock));
                    for (int i = from; i < block && fx.pos < fx.b.length; i++) mix[i] += fx.b[fx.pos++] * fx.gain;
                    if (fx.pos >= fx.b.length) EFFECTS.remove(e);
                }
                clock += block;
            }
            for (int i = 0; i < block; i++) {
                double x = muted ? 0 : Math.tanh(mix[i] * 1.1);
                int s = (int) (x * 30000);
                out[2 * i] = (byte) s;
                out[2 * i + 1] = (byte) (s >> 8);
            }
            line.write(out, 0, out.length);
        }
    }

    // ════════ Writing WAVs to listen to ══════════════════════════
    public static void main(String[] args) throws Exception {
        java.io.File dir = new java.io.File(args.length > 0 ? args[0] : "music_preview");
        dir.mkdirs();
        for (Song s : SONGS.values()) {
            long t0 = System.currentTimeMillis();
            float[][] r = render(s);
            int reps = s.loop ? (s.name.equals("table") ? 1 : 2) : 1;
            int len = r[0].length;
            float[] all = new float[len * reps * (s.name.equals("table") ? 2 : 1)];
            for (int i = 0; i < all.length; i++) {
                double v = r[0][i % len];
                if (s.name.equals("table") && i >= len && r.length > 1) v += r[1][i % len] * Math.min(1, (i - len) / (2.0 * RATE));   // second pass: in check
                all[i] = (float) v;
            }
            write(new java.io.File(dir, s.name + ".wav"), all);
            System.out.println(s.name + ": " + String.format("%.1f", all.length / (double) RATE) + " s, rendered in " + (System.currentTimeMillis() - t0) + " ms");
        }
        for (String e : new String[]{"flick", "catch", "clack", "drag", "exhale", "sizzle", "grind", "sob", "latch", "door", "shut", "move", "capture", "check", "coin", "deny", "card", "step1", "step2", "step3", "drip", "creak", "chair", "hiss", "crack", "sparks"}) write(new java.io.File(dir, "sfx_" + e + ".wav"), makeSfx(e));
    }

    private static void write(java.io.File f, float[] x) throws Exception {
        byte[] b = new byte[x.length * 2];
        for (int i = 0; i < x.length; i++) {
            int s = (int) (Math.tanh(x[i] * 1.1) * 30000);
            b[2 * i] = (byte) s;
            b[2 * i + 1] = (byte) (s >> 8);
        }
        AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
        AudioSystem.write(new AudioInputStream(new java.io.ByteArrayInputStream(b), fmt, x.length), AudioFileFormat.Type.WAVE, f);
    }
}
