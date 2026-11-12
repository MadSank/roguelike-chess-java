import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The whole game on one pixel canvas (Px.W x Px.H), drawn fresh every frame:
 * menu, name, intro, facing him, the board, the round summary, the lights-out swap, the shop
 * (three dealers), choosing your hand, the checkmate lunge and game over.
 *
 * The rules live in RunController; this class only shows them and turns clicks and keys into calls.
 * Every clickable thing registers its rectangle while it is drawn, so drawing and clicking always agree.
 * Keys: left / right choose a card, Enter or Space picks it (or finishes the line being typed), Esc gives up.
 */
public class PixelGame extends JPanel {

    private enum Mode { MENU, SCORES, NAME, INTRO, FACE, BOARD, LUNGE, SUMMARY, SWAP, SHOP, DRAFT, HAND, CONFIRM, OVER }

    private final RunController run = new RunController();
    private final Random rng = new Random();
    private final long start = System.currentTimeMillis();
    private Mode mode = Mode.MENU;

    // ─── What is clickable this frame ───────────────────────────
    private static final class Hit {
        final Rectangle r;
        final Runnable a;

        Hit(Rectangle r, Runnable a) {
            this.r = r;
            this.a = a;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private Point mouse = new Point(-1, -1);
    private Point lastClick = new Point(-1, -1);
    private int choice;
    private Runnable[] cardActions;

    // ─── Talking ────────────────────────────────────────────────
    private String speaker;
    private String[] line = {"", ""};
    private Px.Talk talk;

    // ─── The person in front of you ─────────────────────────────
    private String reaction;
    private double reactStart, reactLen = 1.4;
    private boolean aiThinking, roundEnding;

    // ─── Board ──────────────────────────────────────────────────
    private static final int BX = 152, BY = 156, SW = 22, SH = 18;
    private int selRow = -1, selCol = -1;
    private List<Move> legal = new ArrayList<>();
    private List<Move> promotions;
    private Move snipeMove, plainMove;
    private final List<Object[]> toasts = new ArrayList<>();
    private Card hoverCard;

    // ─── Other screens ──────────────────────────────────────────
    private final StringBuilder name = new StringBuilder();
    private int introStep;
    private RunController.RoundSummary summary;
    private double swapStart, lungeStart;
    private int swapFrom, swapTo;
    private int dealer = 1;
    private List<Card> stash = new ArrayList<>();
    private boolean[] picked = new boolean[0];
    private String confirmText;
    private Runnable confirmYes;
    private Mode confirmBack;
    private boolean overMadeBoard;

    public PixelGame() {
        setPreferredSize(new Dimension(Px.W * 2, Px.H * 2));
        setBackground(Color.BLACK);
        setFocusable(true);
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Point p = Px.toCanvas(e.getX(), e.getY(), getWidth(), getHeight());
                if (p == null) return;
                lastClick = p;
                for (int i = hits.size() - 1; i >= 0; i--) {
                    if (hits.get(i).r.contains(p)) {
                        hits.get(i).a.run();
                        return;
                    }
                }
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                Point p = Px.toCanvas(e.getX(), e.getY(), getWidth(), getHeight());
                mouse = p == null ? new Point(-1, -1) : p;
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { key(e); }

            @Override public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (mode == Mode.NAME && name.length() < 12 && (Character.isLetterOrDigit(c) || c == ' ' || c == '-')) name.append(c);
            }
        });
        new Timer(33, e -> repaint()).start();
        Music.play("menu");
        say(null, new String[]{"A cellar, a lamp, a table. A game of chess for what's left of you.", ""}, 200);
    }

    private double now() {
        return (System.currentTimeMillis() - start) / 1000.0;
    }

    // ─── Small helpers ──────────────────────────────────────────
    private void say(String who, String[] l, int voice) {
        speaker = who;
        line = new String[]{l[0] == null ? "" : l[0], l.length > 1 && l[1] != null ? l[1] : ""};
        String text = line[0].isEmpty() ? "\"" + line[1] + "\"" : line[1].isEmpty() ? line[0] : line[0] + " \"" + line[1] + "\"";
        talk = new Px.Talk(text, voice);
    }

    private PixelArt.Person opponent() {
        return PixelArt.OPPONENTS[run.getOpponent() - 1];
    }

    private PixelArt.Person dealerPerson() {
        return dealer == 0 ? PixelArt.CARD_DEALER : dealer == 1 ? PixelArt.PIECE_DEALER : PixelArt.RESELLER;
    }

    private void sayOpp(String[] l) {
        say(opponent().name, l, opponent().voice);
    }

    private void react(String r, double len) {
        reaction = r;
        reactStart = now();
        reactLen = len;
    }

    private void after(int ms, Runnable r) {
        Timer t = new Timer(ms, e -> r.run());
        t.setRepeats(false);
        t.start();
    }

    private void toast(String text) {
        toasts.add(new Object[]{text, now()});
    }

    private void setMode(Mode m, int defaultChoice) {
        mode = m;
        choice = defaultChoice;
        cardActions = null;
        switch (m) {                                                  // each place has its own music
            case MENU: case SCORES: case NAME: case INTRO: Music.play("menu"); break;
            case FACE: case BOARD: Music.play("table"); break;
            case SHOP: case DRAFT: case HAND: Music.play("shop"); break;
            case SUMMARY: Music.play("ambient"); break;
            case OVER: Music.play("over"); break;
            case LUNGE: case SWAP: Music.play(null); break;
            default: break;
        }
    }

    private boolean hot(Rectangle r) {
        return r.contains(mouse);
    }

    private static String code(Piece p) {
        String n = p.getName();
        return (p.isWhite() ? "w" : "b") + (n.equals("Knight") ? "N" : n.substring(0, 1));
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + ".";
    }

    /** The person's current pose: a reaction while one is playing, thinking while the AI searches. */
    private BufferedImage figure(PixelArt.Person p, double t) {
        if (reaction != null) {
            double k = (now() - reactStart) / reactLen;
            if (k < 1) return PixelArt.person(p, t, reaction, k);
            reaction = null;
        }
        if (aiThinking) return PixelArt.person(p, t, "think", (t % 2.4) / 2.4);
        return PixelArt.person(p, t, null, 0);
    }

    // ─── Keys ───────────────────────────────────────────────────
    private void key(KeyEvent e) {
        int k = e.getKeyCode();
        if (mode == Mode.NAME && k == KeyEvent.VK_BACK_SPACE && name.length() > 0) {
            name.setLength(name.length() - 1);
            return;
        }
        if (k == KeyEvent.VK_LEFT && cardActions != null) choice = (choice + cardActions.length - 1) % cardActions.length;
        if (k == KeyEvent.VK_RIGHT && cardActions != null) choice = (choice + 1) % cardActions.length;
        if (k == KeyEvent.VK_ENTER || (k == KeyEvent.VK_SPACE && mode != Mode.NAME)) {
            if (talk != null && !talk.done() && mode != Mode.NAME) talk.skip();
            else if (mode == Mode.INTRO) nextIntro();
            else if (mode == Mode.SWAP) { if (now() - swapStart > 1.6) enterShop(); }
            else if (cardActions != null && choice < cardActions.length) cardActions[choice].run();
        }
        if (k == KeyEvent.VK_ESCAPE && (mode == Mode.BOARD || mode == Mode.FACE) && !roundEnding) askGiveUp();
    }

    // ─── Painting ───────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        BufferedImage img = Px.canvas();
        Graphics2D g = Px.pen(img);
        hits.clear();
        cardActions = null;
        double t = now();
        draw(g, mode, t);
        g.dispose();
        Px.present((Graphics2D) g0, img, getWidth(), getHeight());
    }

    private void draw(Graphics2D g, Mode m, double t) {
        switch (m) {
            case MENU: drawMenu(g, t); break;
            case SCORES: drawScores(g, t); break;
            case NAME: drawName(g, t); break;
            case INTRO: drawIntro(g, t); break;
            case FACE: drawFace(g, t); break;
            case BOARD: drawBoard(g, t); break;
            case LUNGE: drawLunge(g, t); break;
            case SUMMARY: drawSummary(g, t); break;
            case SWAP: drawSwap(g, t); break;
            case SHOP: drawShop(g, t); break;
            case DRAFT: drawDraft(g, t); break;
            case HAND: drawHand(g, t); break;
            case CONFIRM: drawConfirm(g, t); break;
            case OVER: drawOver(g, t); break;
            default: break;
        }
    }

    private double flicker(double t) {
        return 0.85 + 0.15 * Math.sin(t * 7) * Math.sin(t * 2.3);
    }

    /** Choices as a row of cards along the bottom (Px.cards), clickable and keyboard-driven. */
    private void choices(Graphics2D g, String[] labels, Runnable[] actions, double t) {
        int[] lay = Px.cardLayout(labels);
        int n = labels.length, x0 = lay[0], cw = lay[1], gap = lay[2];
        if (choice >= n) choice = n - 1;
        for (int i = 0; i < n; i++) {
            Rectangle r = new Rectangle(x0 + i * (cw + gap), Px.H - 39, cw, 33);
            if (hot(r)) choice = i;
            final int idx = i;
            hits.add(new Hit(r, () -> { choice = idx; actions[idx].run(); }));
        }
        Px.cards(g, labels, choice, t);
        cardActions = actions;
    }

    /** The framed text box: name plate, narration in ash grey, then the spoken line in cream. */
    private void speechBox(Graphics2D g, int y, int h) {
        Px.frame(g, 20, y, 440, h);
        if (speaker != null) Px.plate(g, speaker, 32, y - 6);
        if (talk == null) return;
        int shown = talk.shown(), lines = 0;
        if (!line[0].isEmpty()) lines = Px.wrap(g, line[0], 34, y + 13, 410, Px.ASH, shown);
        if (!line[1].isEmpty()) {
            int used = line[0].isEmpty() ? 0 : line[0].length() + 1;
            Px.wrap(g, "\"" + line[1] + "\"", 34, y + 13 + (lines > 0 ? lines * 10 + 4 : 0), 410, Px.CREAM, shown - used);
        }
        hits.add(new Hit(new Rectangle(20, y, 440, h), () -> talk.skip()));
    }

    private void status(Graphics2D g, int gold) {
        int have = run.getShop().armyMaterial(), need = run.getRequirement();
        Px.coin(g, 8, 8);
        Px.text(g, String.valueOf(gold), 19, 9, Px.YELLOW, 1);
        int x = 26 + Px.textWidth(String.valueOf(gold), 1);
        Px.skull(g, x, 8);
        Px.text(g, String.valueOf(run.getLevel()), x + 11, 9, Px.CREAM, 1);
        x += 20 + Px.textWidth(String.valueOf(run.getLevel()), 1);
        PixelArt.piece(g, "wP", x + 6, 22);
        Px.text(g, have + "/" + need, x + 15, 9, have >= need ? Px.CREAM : Px.EMBER, 1);
    }

    private static void lamp(Graphics2D g, int cx, double t) {
        PixelPreview.lamp(g, cx, t);
    }

    private static void table(Graphics2D g, int x, int y, int w, int h) {
        PixelPreview.table(g, x, y, w, h);
    }

    // ─── Menu, scores, name, intro ──────────────────────────────
    private void drawMenu(Graphics2D g, double t) {
        Px.cellar(g, 240, 110, 260, flicker(t));
        lamp(g, 240, t);
        Px.centred(g, "ASHES", 242, 62, Px.DRED, 6);
        Px.centred(g, "ASHES", 240, 60, Px.EMBER, 6);
        Px.centred(g, "of the board", 240, 112, Px.CREAM, 1);
        table(g, 90, 214, 300, 16);
        PixelArt.piece(g, "wK", 200, 222);
        PixelArt.piece(g, "bK", 280, 222);
        Px.flame(g, 237, 205, t);
        Px.centred(g, "Arrows and Enter, or the mouse.", 240, 300, Px.ASH, 1);
        choices(g, new String[]{"NEW RUN", "SCORES", AudioPlayer.isSoundEnabled() ? "SOUND ON" : "SOUND OFF", "LEAVE"}, new Runnable[]{
            () -> { name.setLength(0); setMode(Mode.NAME, 0); say(null, new String[]{"Before you go down, the cellar wants a name.", ""}, 200); },
            () -> setMode(Mode.SCORES, 0),
            () -> AudioPlayer.setSoundEnabled(!AudioPlayer.isSoundEnabled()),
            () -> System.exit(0)}, t);
    }

    private void drawScores(Graphics2D g, double t) {
        Px.cellar(g, 240, 110, 260, flicker(t));
        Px.frame(g, 40, 40, 400, 260);
        Px.plate(g, "Leaderboard: highest level beaten", 52, 34);
        String[] rows = run.getScores().getLeaderboardText().split("\\R");
        for (int i = 0; i < rows.length && i < 22; i++) Px.text(g, rows[i], 56, 56 + i * 11, i == 0 ? Px.EMBER : Px.CREAM, 1);
        choices(g, new String[]{"BACK"}, new Runnable[]{() -> setMode(Mode.MENU, 1)}, t);
    }

    private void drawName(Graphics2D g, double t) {
        Px.cellar(g, 240, 110, 260, flicker(t));
        lamp(g, 240, t);
        speechBox(g, 120, 50);
        Px.frame(g, 140, 190, 200, 34);
        String shown = name + ((int) (t * 2) % 2 == 0 ? "_" : " ");
        Px.text(g, shown, 154, 203, Px.CREAM, 1);
        Px.centred(g, "Type your name.", 240, 234, Px.ASH, 1);
        choices(g, new String[]{"GO DOWN", "BACK"}, new Runnable[]{this::beginRun, () -> setMode(Mode.MENU, 0)}, t);
    }

    private void beginRun() {
        String n = name.toString().trim();
        run.startNewRun(n.isEmpty() ? "Anonymous" : n);
        introStep = 0;
        setMode(Mode.INTRO, 0);
        say(null, new String[]{"Stone steps lead down into a cellar. The air tastes of smoke.", ""}, 160);
    }

    private void nextIntro() {
        if (introStep == 0) {
            introStep = 1;
            say(null, new String[]{"One lamp. One table. A chessboard. Someone is already sitting there, waiting for you.", ""}, 160);
        } else {
            startRoundScreen();
        }
    }

    private void drawIntro(Graphics2D g, double t) {
        Px.cellar(g, 240, 90, 300, flicker(t));
        if (introStep == 1) g.drawImage(PixelArt.person(opponent(), t, null, 0), (Px.W - PixelArt.SPRITE_W) / 2, 50, null);
        lamp(g, 240, t);
        table(g, 30, 186, 420, 18);
        speechBox(g, 222, 80);
        Px.centred(g, "Click or press Enter", 240, 340, Px.ASH, 1);
        hits.add(new Hit(new Rectangle(0, 0, Px.W, Px.H), () -> { if (talk.done()) nextIntro(); else talk.skip(); }));
    }

    // ─── Facing him ─────────────────────────────────────────────
    private void startRoundScreen() {
        aiThinking = false;
        roundEnding = false;
        reaction = null;
        clearSelection();
        toasts.clear();
        setMode(Mode.FACE, 0);
        sayOpp(Lines.greet(run.getOpponent() - 1));
        Music.tension(0);
    }

    private void drawFace(Graphics2D g, double t) {
        Px.cellar(g, 240, 90, 300, flicker(t));
        g.drawImage(figure(opponent(), t), (Px.W - PixelArt.SPRITE_W) / 2, 50, null);
        lamp(g, 240, t);
        table(g, 30, 186, 420, 18);
        speechBox(g, 222, 80);
        status(g, run.getGold());
        choices(g, new String[]{"PLAY", "CARDS", "TALK", "GIVE UP"}, new Runnable[]{
            () -> { setMode(Mode.BOARD, 0); say(opponent().name, new String[]{"He gestures at the board.", "Your move."}, opponent().voice); },
            () -> {
                List<Card> hand = run.getHand();
                StringBuilder sb = new StringBuilder();
                for (Card c : hand) sb.append(sb.length() == 0 ? "" : ", ").append(c.getName());
                say(null, new String[]{hand.isEmpty() ? "Your hand is empty." : "In your hand: " + sb + ". Play them from the board; they are free.", ""}, 200);
            },
            () -> { sayOpp(Lines.talk(run.getOpponent() - 1)); react("yes", 1.0); },
            this::askGiveUp}, t);
    }

    // ─── The board ──────────────────────────────────────────────
    private static BufferedImage boardImage;

    private static BufferedImage boardImage() {
        if (boardImage != null) return boardImage;
        BufferedImage b = new BufferedImage(SW * 8, SH * 8, BufferedImage.TYPE_INT_RGB);
        Color[] light = Shade.ramp(new Color(206, 178, 136), 5), dark = Shade.ramp(new Color(98, 58, 42), 5);
        int[][] bayer = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Color[] ramp = (r + c) % 2 == 1 ? dark : light;
                double d = Math.hypot(c - 3.5, r - 1.0) / 8.0;               // lamplight falls off down the board
                for (int yy = 0; yy < SH; yy++) {
                    for (int xx = 0; xx < SW; xx++) {
                        int x = c * SW + xx, y = r * SH + yy;
                        double v = 0.9 - d * 0.8 + (yy == 0 ? 0.1 : 0) + ((x * 7 + y * 3) % 11 == 0 ? -0.08 : 0);
                        int k = (int) Math.max(0, Math.min(4, Math.round(v * 4 + bayer[y & 3][x & 3] / 16.0 - 0.47)));
                        b.setRGB(x, y, ramp[k].getRGB());
                    }
                }
            }
        }
        boardImage = b;
        return b;
    }

    private void drawBoard(Graphics2D g, double t) {
        GameState s = run.getRoundState();
        Px.cellar(g, 240, 60, 290, flicker(t));
        g.drawImage(figure(opponent(), t), (Px.W - PixelArt.SPRITE_W) / 2, 8, null);
        lamp(g, 240, t);
        table(g, 20, 142, 440, 16);
        Px.rect(g, BX - 6, BY - 6, SW * 8 + 12, SH * 8 + 12, Px.BLACK);
        Px.rect(g, BX - 5, BY - 5, SW * 8 + 10, SH * 8 + 10, Px.WOOD);
        Px.rect(g, BX - 5, BY - 5, SW * 8 + 10, 1, Px.WOOD_LIGHT);
        g.drawImage(boardImage(), BX, BY, null);
        for (int idx : s.minefieldSquares) {                          // mines: a dull red cross
            int x = BX + (idx % 8) * SW + SW / 2, y = BY + (idx / 8) * SH + SH / 2;
            for (int i = -3; i <= 3; i++) {
                Px.dot(g, x + i, y + i, Px.DRED);
                Px.dot(g, x + i, y - i, Px.DRED);
            }
        }
        for (int idx : s.stunnedPieces.keySet()) {                    // stunned: cold blue corners
            int x = BX + (idx % 8) * SW, y = BY + (idx / 8) * SH;
            Color c = new Color(110, 140, 230);
            Px.rect(g, x + 1, y + 1, 3, 1, c);
            Px.rect(g, x + SW - 4, y + 1, 3, 1, c);
            Px.rect(g, x + 1, y + SH - 2, 3, 1, c);
            Px.rect(g, x + SW - 4, y + SH - 2, 3, 1, c);
        }
        if (selRow >= 0) outline(g, selRow, selCol, Px.EMBER);
        for (Move m : legal) {
            if (m.isSniperShot) {
                int x = BX + m.sniperTargetCol * SW + SW / 2, y = BY + m.sniperTargetRow * SH + SH / 2;
                Px.rect(g, x - 4, y, 9, 1, Px.RED);
                Px.rect(g, x, y - 4, 1, 9, Px.RED);
            } else if (s.board[m.toRow][m.toCol] != null) {
                corners(g, m.toRow, m.toCol, Px.RED);
            } else {
                int x = BX + m.toCol * SW + SW / 2 - 1, y = BY + m.toRow * SH + SH / 2 - 1;
                Px.rect(g, x - 1, y - 1, 4, 4, Px.CHAR);
                Px.rect(g, x, y, 2, 2, Px.EMBER);
            }
        }
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = s.board[r][c];
                if (p != null) PixelArt.piece(g, code(p), BX + c * SW + SW / 2, BY + r * SH + SH - 3);
            }
        }
        hits.add(new Hit(new Rectangle(BX, BY, SW * 8, SH * 8), () -> boardClick((lastClick.y - BY) / SH, (lastClick.x - BX) / SW)));

        // Left: the round.
        Px.frame(g, 8, 160, 136, 146);
        Px.text(g, "LEVEL " + run.getLevel(), 20, 172, Px.EMBER, 1);
        boolean yours = s.whiteToMove && !s.gameOver && !aiThinking;
        Px.text(g, s.gameOver ? "OVER" : yours ? "YOUR MOVE" : "HE THINKS", 20, 186, yours ? Px.CREAM : Px.ASH, 1);
        Px.text(g, "ACTIVE", 20, 204, Px.ASH, 1);
        List<Card> active = s.cardManager != null ? s.cardManager.getActiveCards() : new ArrayList<>();
        for (int i = 0; i < active.size() && i < 6; i++) Px.text(g, cut(active.get(i).getName(), 18), 20, 216 + i * 10, Px.CREAM, 1);
        if (active.isEmpty()) Px.text(g, "nothing", 20, 216, Px.DIM, 1);
        Px.text(g, "Esc: give up", 20, 290, Px.DIM, 1);

        // Right: the hand.
        hoverCard = null;
        Px.frame(g, 338, 160, 136, 146);
        Px.text(g, "HAND", 350, 172, Px.EMBER, 1);
        List<Card> hand = run.getHand();
        boolean canPlay = yours && !roundEnding;
        if (hand.isEmpty()) Px.text(g, "empty", 350, 188, Px.DIM, 1);
        for (int i = 0; i < hand.size() && i < 9; i++) {
            Card c = hand.get(i);
            Rectangle r = new Rectangle(344, 184 + i * 13, 124, 12);
            boolean played = run.isPlayed(c), over = hot(r);
            if (over) hoverCard = c;
            Color col = played ? new Color(120, 190, 120) : over && canPlay ? Px.EMBER : canPlay ? Px.CREAM : Px.ASH;
            Px.text(g, cut(c.getName(), 19), 350, r.y + 2, col, 1);
            if (!played && canPlay) hits.add(new Hit(r, () -> playCard(c)));
        }

        // Toasts rise over the board and fade.
        for (int i = 0; i < toasts.size(); i++) {
            double age = t - (Double) toasts.get(i)[1];
            if (age > 2.2) { toasts.remove(i--); continue; }
            int a = (int) (255 * Math.max(0, 1 - age / 2.2));
            String text = (String) toasts.get(i)[0];
            int y = 214 - (int) (age * 20) + i * 11;
            Px.rect(g, 240 - Px.textWidth(text, 1) / 2 - 3, y - 2, Px.textWidth(text, 1) + 6, 11, new Color(10, 6, 14, a * 3 / 4));
            Px.centred(g, text, 240, y, new Color(255, 214, 90, a), 1);
        }

        status(g, run.getGold());
        if (promotions != null) {
            choices(g, new String[]{"QUEEN", "ROOK", "BISHOP", "KNIGHT"}, new Runnable[]{
                () -> promote("Q"), () -> promote("R"), () -> promote("B"), () -> promote("N")}, t);
        } else if (snipeMove != null) {
            choices(g, new String[]{"SNIPE", "MOVE", "BACK"}, new Runnable[]{
                () -> { Move m = snipeMove; snipeMove = null; clearSelection(); playerMove(m); },
                () -> { Move m = plainMove; snipeMove = null; clearSelection(); playerMove(m); },
                () -> { snipeMove = null; clearSelection(); }}, t);
        } else if (hoverCard != null) {
            Px.frame(g, 20, 312, 440, 44);
            Px.plate(g, hoverCard.getName(), 32, 306);
            Px.wrap(g, hoverCard.getDescription(), 34, 324, 410, Px.CREAM, 999);
        } else {
            speechBox(g, 312, 44);
        }
    }

    private void outline(Graphics2D g, int r, int c, Color col) {
        int x = BX + c * SW, y = BY + r * SH;
        Px.rect(g, x, y, SW, 1, col);
        Px.rect(g, x, y + SH - 1, SW, 1, col);
        Px.rect(g, x, y, 1, SH, col);
        Px.rect(g, x + SW - 1, y, 1, SH, col);
    }

    private void corners(Graphics2D g, int r, int c, Color col) {
        int x = BX + c * SW, y = BY + r * SH;
        Px.rect(g, x, y, 5, 1, col);
        Px.rect(g, x, y, 1, 4, col);
        Px.rect(g, x + SW - 5, y, 5, 1, col);
        Px.rect(g, x + SW - 1, y, 1, 4, col);
        Px.rect(g, x, y + SH - 1, 5, 1, col);
        Px.rect(g, x, y + SH - 4, 1, 4, col);
        Px.rect(g, x + SW - 5, y + SH - 1, 5, 1, col);
        Px.rect(g, x + SW - 1, y + SH - 4, 1, 4, col);
    }

    private void clearSelection() {
        selRow = selCol = -1;
        legal = new ArrayList<>();
        promotions = null;
    }

    private void boardClick(int row, int col) {
        if (aiThinking || roundEnding || promotions != null || snipeMove != null) return;
        GameState s = run.getRoundState();
        if (s.gameOver || !s.whiteToMove || !Square.insideBoard(row, col)) return;
        if (selRow != -1) {
            List<Move> matching = new ArrayList<>();
            Move snipe = null;
            for (Move m : legal) {
                if (m.isSniperShot) {
                    if (m.sniperTargetRow == row && m.sniperTargetCol == col) snipe = m;
                } else if (m.toRow == row && m.toCol == col) {
                    matching.add(m);
                }
            }
            if (snipe != null && !matching.isEmpty()) {
                snipeMove = snipe;
                plainMove = matching.get(0);
                choice = 0;
                say(null, new String[]{"Sniper Bishops: shoot from where you stand, or move onto the square?", ""}, 200);
                return;
            }
            if (matching.size() > 1 && matching.get(0).isPromotion) {
                promotions = matching;
                choice = 0;
                return;
            }
            Move chosen = snipe != null ? snipe : matching.isEmpty() ? null : matching.get(0);
            if (chosen != null) {
                clearSelection();
                playerMove(chosen);
                return;
            }
        }
        Piece p = s.board[row][col];
        if (p != null && p.isWhite()) {
            selRow = row;
            selCol = col;
            legal = run.getEngine().getLegalMovesFromSquare(row, col);
        } else {
            clearSelection();
        }
    }

    private void promote(String type) {
        Move pick = promotions.get(0);
        for (Move m : promotions) if (type.equals(m.promotionType)) pick = m;
        clearSelection();
        playerMove(pick);
    }

    private void playCard(Card c) {
        if (aiThinking || roundEnding) return;
        String err = run.playCard(c);
        if (err != null) {
            toast(err);
            return;
        }
        Music.sfx("card");
        toast(c.getName() + " played");
        clearSelection();
        if (run.isRoundOver()) endRound();
    }

    private void playerMove(Move move) {
        if (aiThinking || roundEnding) return;
        List<MoveBonuses.Bonus> bonuses = run.playerMove(move);
        if (bonuses == null) {
            toast("Illegal move");
            return;
        }
        AudioPlayer.playMoveSound();
        if (move.isCapture) AudioPlayer.playCaptureSound();
        Music.tension(0);
        for (MoveBonuses.Bonus b : bonuses) toast(b + " gold");
        for (String e : run.takeEvents()) toast(e);
        if (move.isCapture && !run.isRoundOver()) {
            react("hurt", 1.2);
            if (rng.nextInt(3) > 0) sayOpp(Lines.hurt());
        }
        if (run.isRoundOver()) endRound();
        else if (run.isAiTurn()) aiMove();
    }

    /** The AI searches off the Swing thread so the lamp keeps flickering. */
    private void aiMove() {
        final GameState snapshot = run.getRoundState().copy();
        final ChessEngine roundEngine = run.getEngine();
        aiThinking = true;
        new SwingWorker<Move, Void>() {
            @Override protected Move doInBackground() {
                return run.computeAiMove(snapshot);
            }

            @Override protected void done() {
                aiThinking = false;
                if (roundEngine != run.getEngine()) return;
                Move m;
                try {
                    m = get();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    m = null;
                }
                if (!run.applyAiMove(m)) {
                    System.err.println("AI produced no legal move: " + m);
                    return;
                }
                AudioPlayer.playMoveSound();
                if (m.isCapture) AudioPlayer.playCaptureSound();
                if (!run.isRoundOver()) {
                    if (run.getEngine().isKingInCheck(run.getRoundState(), true)) {
                        Music.tension(1);
                        Music.sfx("check");
                        react("check", 1.4);
                        sayOpp(Lines.check());
                    } else if (m.isCapture) {
                        react("capture", 1.4);
                        sayOpp(Lines.capture());
                    }
                }
                if (run.isRoundOver()) endRound();
            }
        }.execute();
    }

    /** The round is over: he reacts, (if you were mated) lunges at you, then the summary. */
    private void endRound() {
        if (roundEnding) return;
        roundEnding = true;
        clearSelection();
        GameResult r = run.getRoundState().result;
        if (r == GameResult.BLACK_WINS) {
            react("win", 1.1);
            sayOpp(Lines.win());
            after(1300, () -> {
                setMode(Mode.LUNGE, 0);
                lungeStart = now();
                AudioPlayer.playCheckmateSound();
                after(1300, this::showSummary);
            });
        } else if (r == GameResult.WHITE_WINS) {
            react("lose", 2.0);
            sayOpp(Lines.lose());
            Music.play(null);
            Music.sting("win");
            after(2100, this::showSummary);
        } else {
            Music.play("ambient");
            sayOpp(Lines.draw());
            after(1700, this::showSummary);
        }
    }

    private void drawLunge(Graphics2D g, double t) {
        double k = Math.min(1, (t - lungeStart) / 0.9);
        Px.rect(g, 0, 0, Px.W, Px.H, Color.BLACK);
        if (k >= 1) return;
        int shake = (int) Math.round(Math.sin(t * 90) * 4 * k);
        Px.cellar(g, 240 + shake, 90, (int) (300 * (1 - k * 0.6)), 1);
        double scale = 1 + k * k * 4;
        BufferedImage p = PixelArt.person(opponent(), t, "check", 0.5);
        int w = (int) (PixelArt.SPRITE_W * scale), h = (int) (PixelArt.SPRITE_H * scale);
        g.drawImage(p, (Px.W - w) / 2 + shake, 50 - (int) (k * 120), w, h, null);
        Px.rect(g, 0, 0, Px.W, Px.H, new Color(120, 0, 10, (int) (150 * k)));
    }

    // ─── Summary, swap, game over ───────────────────────────────
    private void showSummary() {
        summary = run.finishRound();
        roundEnding = false;
        aiThinking = false;
        reaction = null;
        setMode(Mode.SUMMARY, 0);
        talk = null;
    }

    private void drawSummary(Graphics2D g, double t) {
        RunController.RoundSummary s = summary;
        PixelArt.Person p = PixelArt.OPPONENTS[RunController.opponentFor(s.level) - 1];
        Px.cellar(g, 240, 90, 300, flicker(t));
        String pose = s.result == GameResult.WHITE_WINS ? "lose" : s.result == GameResult.BLACK_WINS ? "win" : null;
        g.drawImage(PixelArt.person(p, t, pose, 0.5), (Px.W - PixelArt.SPRITE_W) / 2, 50, null);
        lamp(g, 240, t);
        table(g, 30, 186, 420, 18);
        Px.frame(g, 20, 214, 440, 102);
        String title = s.result == GameResult.WHITE_WINS ? "Victory" : s.result == GameResult.DRAW ? "Draw" : "Defeat";
        Px.plate(g, title, 32, 208);
        List<String> left = new ArrayList<>();
        if (s.result == GameResult.WHITE_WINS) left.add("You beat level " + s.level + ".");
        else if (s.result == GameResult.DRAW) { left.add(s.resultText + "."); left.add("You keep your pieces and replay level " + s.level + "."); }
        else { left.add("Checkmated. Only your king survives."); left.add("Rebuild your army and replay level " + s.level + "."); }
        left.add("Gold now: " + s.goldAfter);
        if (!s.cardsUsedUp.isEmpty()) left.add("Used up: " + String.join(", ", s.cardsUsedUp));
        int y = 228;
        for (int i = 0; i < left.size(); i++) y += 10 * Px.wrap(g, left.get(i), 34, y, 200, i == left.size() - 1 && !s.cardsUsedUp.isEmpty() ? Px.ASH : Px.CREAM, 999) + 2;
        if (s.result != GameResult.BLACK_WINS) {
            Economy.Payout pay = s.payout;
            String[][] rows = {
                {"Your material left", pay.ownMaterial + " x" + Economy.COINS_PER_OWN_POINT},
                {"Enemy material taken", pay.defeatedMaterial + " x" + (Economy.TENTHS_PER_DEFEATED_POINT / 10.0)},
                {"Good moves", String.valueOf(pay.bonusCoins)},
                {"Level bonus", String.valueOf(pay.levelBase)},
                {s.result == GameResult.DRAW ? "Draws pay " + Economy.DRAW_PERCENT + "%" : "", ""},
                {"Interest on savings", String.valueOf(pay.interest)},
                {"Gold earned", "+" + pay.total},
            };
            int ry = 228;
            for (String[] r : rows) {
                if (r[0].isEmpty()) continue;
                boolean last = r[0].equals("Gold earned");
                Px.text(g, r[0], 250, ry, last ? Px.YELLOW : Px.ASH, 1);
                Px.text(g, r[1], 446 - Px.textWidth(r[1], 1), ry, last ? Px.YELLOW : Px.CREAM, 1);
                ry += 11;
            }
        }
        choices(g, new String[]{"CONTINUE"}, new Runnable[]{this::afterSummary}, t);
    }

    private void afterSummary() {
        if (summary.gameOver) {
            overMadeBoard = summary.madeLeaderboard;
            setMode(Mode.OVER, 0);
            say(null, new String[]{"You can't reach level " + summary.nextLevel + "'s requirement of " + Economy.requirement(summary.nextLevel)
                + " material, even selling everything. The lamp goes out.", ""}, 140);
        } else if (summary.result == GameResult.WHITE_WINS) {
            swapFrom = RunController.opponentFor(summary.level) - 1;
            swapTo = RunController.opponentFor(summary.nextLevel) - 1;
            swapStart = now();
            setMode(Mode.SWAP, 0);
            Music.sting("lightsout");
            say(null, new String[]{"The lamp gutters out.", ""}, 140);
        } else {
            enterShop();
        }
    }

    /** After a win the lamp dies on the beaten opponent and comes back on the next one. */
    private void drawSwap(Graphics2D g, double t) {
        double k = t - swapStart;
        boolean second = k > 1.6;
        PixelArt.Person p = PixelArt.OPPONENTS[second ? swapTo : swapFrom];
        boolean dark = (k > 0.6 && k < 1.6) || (k < 0.6 && Math.sin(k * 47) > 0.2) || (k >= 1.6 && k < 2.2 && Math.sin(k * 41) > 0.1);
        if (dark) {
            Px.rect(g, 0, 0, Px.W, Px.H, new Color(4, 2, 6));
            Px.rect(g, 238, 20, 3, 2, new Color(60, 30, 20));               // the cooling filament
        } else {
            Px.cellar(g, 240, 90, 300, flicker(t));
            g.drawImage(PixelArt.person(p, t, null, 0), (Px.W - PixelArt.SPRITE_W) / 2, 50, null);
            lamp(g, 240, t);
            table(g, 30, 186, 420, 18);
        }
        if (k > 2.2) {
            Music.play("ambient");
            if (!p.name.equals(speaker)) say(p.name, new String[]{"Someone else is sitting in the chair now. Level " + run.getLevel() + ".", ""}, p.voice);
            speechBox(g, 222, 80);
            choices(g, new String[]{"TO THE DEALERS"}, new Runnable[]{this::enterShop}, t);
        } else if (k < 1.6) {
            Px.centred(g, "The lamp gutters out.", 240, 300, Px.ASH, 1);
        }
        hits.add(0, new Hit(new Rectangle(0, 0, Px.W, Px.H), () -> { if (now() - swapStart > 2.2) enterShop(); }));
    }

    private void drawOver(Graphics2D g, double t) {
        Px.rect(g, 0, 0, Px.W, Px.H, new Color(4, 2, 6));
        Px.centred(g, "THE LAMP GOES OUT", 240, 80, Px.EMBER, 2);
        Px.centred(g, "Highest level beaten: " + run.getHighestLevelBeaten(), 240, 120, Px.CREAM, 1);
        if (overMadeBoard) Px.centred(g, "You made the leaderboard.", 240, 136, Px.YELLOW, 1);
        speechBox(g, 222, 70);
        choices(g, new String[]{"UPSTAIRS"}, new Runnable[]{() -> { run.returnToMenu(); setMode(Mode.MENU, 0); talk = null; }}, t);
    }

    private void askGiveUp() {
        confirmBack = mode;
        confirmText = "Give up this run? It goes on the leaderboard as it stands.";
        confirmYes = () -> {
            overMadeBoard = run.abandonRun();
            setMode(Mode.OVER, 0);
            say(null, new String[]{"You push your chair back and climb the stairs. Behind you, the lamp goes out.", ""}, 140);
        };
        setMode(Mode.CONFIRM, 1);
    }

    private void drawConfirm(Graphics2D g, double t) {
        draw(g, confirmBack, t);
        hits.clear();
        cardActions = null;
        Px.rect(g, 0, 0, Px.W, Px.H, new Color(6, 3, 10, 180));
        Px.frame(g, 80, 130, 320, 60);
        Px.wrap(g, confirmText, 96, 146, 290, Px.CREAM, 999);
        choices(g, new String[]{"YES", "NO"}, new Runnable[]{confirmYes, () -> setMode(confirmBack, 0)}, t);
    }

    // ─── The shop ───────────────────────────────────────────────
    private ShopManager shop() {
        return run.getShop();
    }

    private void enterShop() {
        if (mode == Mode.SHOP) return;
        dealer = 1;
        reaction = null;
        setMode(Mode.SHOP, 2);
        say(dealerPerson().name, Lines.dealerGreet(dealer), dealerPerson().voice);
    }

    private void turn(int step) {
        dealer = (dealer + step + 3) % 3;
        reaction = null;
        say(dealerPerson().name, Lines.dealerGreet(dealer), dealerPerson().voice);
    }

    private void deal(ShopManager.Result r, String what) {
        if (r == ShopManager.Result.OK) {
            Music.sfx("coin");
            react("yes", 1.0);
            String[] y = Lines.yes();
            say(dealerPerson().name, new String[]{what, y[1]}, dealerPerson().voice);
        } else {
            Music.sfx("deny");
            react("no", 1.0);
            say(dealerPerson().name, new String[]{r.message, Lines.no()[1]}, dealerPerson().voice);
        }
    }

    private void sitDown() {
        int level = run.getLevel();
        if (!shop().meetsRequirement(level)) {
            react("no", 1.0);
            int need = Economy.requirement(level) - shop().armyMaterial();
            say(dealerPerson().name, new String[]{"Level " + level + " needs " + Economy.requirement(level) + " material. You have "
                + shop().armyMaterial() + ".", "Buy " + need + " more first."}, dealerPerson().voice);
            return;
        }
        stash = new ArrayList<>(shop().getCards().getActiveCards());
        if (stash.isEmpty()) {
            if (run.finishShopping()) startRoundScreen();
            return;
        }
        picked = new boolean[stash.size()];
        for (int i = 0; i < picked.length && i < run.getHandSize(); i++) picked[i] = true;
        setMode(Mode.HAND, 0);
    }

    private void drawShop(Graphics2D g, double t) {
        Px.cellar(g, 240, 70, 260, flicker(t));
        g.drawImage(figure(dealerPerson(), t), (Px.W - PixelArt.SPRITE_W) / 2, -8, null);
        table(g, 30, 128, 420, 18);
        Px.flame(g, 70, 116, t);
        Px.flame(g, 404, 116, t + 0.3);
        Px.frame(g, 20, 152, 440, 112);
        Px.plate(g, dealer == 0 ? "Cards" : dealer == 1 ? "Pieces" : "Sell for half", 32, 146);
        String info;
        if (dealer == 1) info = drawPieceWares(g);
        else if (dealer == 0) info = drawCardWares(g);
        else info = drawResale(g);
        if (info != null) Px.wrap(g, info, 34, 240, 412, Px.ASH, 999);
        speechBox(g, 274, 44);
        status(g, shop().getGold());
        boolean ready = shop().meetsRequirement(run.getLevel());
        choices(g, new String[]{"<", "TALK", ready ? "SIT DOWN" : "NEED " + (run.getRequirement() - shop().armyMaterial()), "GIVE UP", ">"}, new Runnable[]{
            () -> turn(-1),
            () -> say(dealerPerson().name, Lines.dealerTalk(dealer), dealerPerson().voice),
            this::sitDown,
            this::askGiveUp,
            () -> turn(1)}, t);
    }

    private static Piece sample(String type) {
        switch (type) {
            case "Pawn": return new Pawn(true);
            case "Knight": return new Knight(true);
            case "Bishop": return new Bishop(true);
            case "Rook": return new Rook(true);
            default: return new Queen(true);
        }
    }

    /** Five pieces standing on the counter; click to buy. Returns the line about the one under the mouse. */
    private String drawPieceWares(Graphics2D g) {
        String info = "Click a piece to buy it. Pawns allowed: " + shop().count("Pawn") + " of " + shop().pawnCap() + ".";
        for (int i = 0; i < Economy.PIECE_TYPES.length; i++) {
            String type = Economy.PIECE_TYPES[i];
            int price = Economy.piecePrice(type);
            Rectangle r = new Rectangle(34 + i * 84, 160, 76, 74);
            boolean over = hot(r), afford = shop().getGold() >= price;
            Px.rect(g, r.x, r.y, r.width, r.height, over ? new Color(60, 34, 26) : new Color(30, 22, 22));
            if (over) {
                Px.rect(g, r.x, r.y, r.width, 1, Px.EMBER);
                Px.rect(g, r.x, r.y + r.height - 1, r.width, 1, Px.EMBER);
            }
            PixelArt.piece(g, "w" + (type.equals("Knight") ? "N" : type.substring(0, 1)), r.x + 38, r.y + 34);
            Px.centred(g, type, r.x + 38, r.y + 40, Px.CREAM, 1);
            Px.centred(g, price + " gold", r.x + 38, r.y + 51, afford ? Px.YELLOW : Px.DIM, 1);
            Px.centred(g, "own " + shop().count(type), r.x + 38, r.y + 62, Px.ASH, 1);
            if (over) info = type + ": " + Economy.material(sample(type)) + " material for " + price + " gold.";
            hits.add(new Hit(r, () -> deal(shop().buyPiece(type), "He sets a " + type.toLowerCase() + " in front of you.")));
        }
        return info;
    }

    private static Color rarity(Card c) {
        switch (c.getRarity()) {
            case RARE: return new Color(200, 120, 230);
            case UNCOMMON: return new Color(110, 170, 230);
            default: return Px.ASH;
        }
    }

    /** A small card tile: name, rarity, price. */
    private void cardTile(Graphics2D g, Rectangle r, String title, Color accent, String price, boolean over, boolean dim) {
        cardTile(g, r, title, null, accent, price, over, dim);
    }

    private void cardTile(Graphics2D g, Rectangle r, String title, String text, Color accent, String price, boolean over, boolean dim) {
        Px.rect(g, r.x, r.y, r.width, r.height, Px.BLACK);
        Px.rect(g, r.x + 1, r.y + 1, r.width - 2, r.height - 2, over ? Px.CREAM : dim ? Px.DIM : Px.ASH);
        Px.rect(g, r.x + 3, r.y + 3, r.width - 6, r.height - 6, over ? new Color(214, 196, 160) : dim ? new Color(60, 54, 58) : new Color(112, 102, 94));
        Px.rect(g, r.x + 3, r.y + 3, r.width - 6, 2, accent);
        int lines = Px.wrap(g, title, r.x + 7, r.y + 10, r.width - 12, dim ? Px.CHAR : Px.WOOD_DARK, 999);
        if (text != null) Px.wrap(g, text, r.x + 7, r.y + 16 + lines * 10, r.width - 12, Px.CHAR, 999);
        Px.text(g, price, r.x + 7, r.y + r.height - 13, dim ? Px.CHAR : Px.DRED, 1);
    }

    private String drawCardWares(Graphics2D g) {
        String info = "Hover a card to read it, click to buy.";
        List<Card> faceUp = shop().getFaceUpCards();
        for (int i = 0; i < faceUp.size() && i < 3; i++) {
            Card c = faceUp.get(i);
            int price = Economy.faceUpCardPrice(c);
            Rectangle r = new Rectangle(34 + i * 104, 158, 96, 76);
            boolean over = hot(r);
            cardTile(g, r, c.getName(), rarity(c), price + " gold", over, shop().getGold() < price);
            if (over) info = c.getName() + " (" + c.getRarity() + (c.isGlobal() ? ", global" : "") + "): " + c.getDescription();
            hits.add(new Hit(r, () -> deal(shop().buyFaceUpCard(c), "He slides " + c.getName() + " across the counter.")));
        }
        int level = run.getLevel(), packPrice = Economy.mysteryPackPrice(level);
        Rectangle r = new Rectangle(34 + 3 * 104, 158, 96, 76);
        boolean opened = shop().isMysteryPackOpened() || shop().getMysteryPackCards().isEmpty();
        boolean over = hot(r);
        cardTile(g, r, opened ? "Pack opened" : "Mystery pack", new Color(150, 90, 200), opened ? "" : packPrice + " gold", over && !opened, opened || shop().getGold() < packPrice);
        if (over) info = "Mystery pack: three hidden cards; keep one of them.";
        hits.add(new Hit(r, () -> {
            ShopManager.Result res = shop().openMysteryPack(level);
            deal(res, "He tears the pack open.");
            if (res == ShopManager.Result.OK) setMode(Mode.DRAFT, 3);
        }));
        return info;
    }

    private String drawResale(Graphics2D g) {
        String info = "Everything sells for half. Click to sell.";
        for (int i = 0; i < Economy.PIECE_TYPES.length; i++) {
            String type = Economy.PIECE_TYPES[i];
            int owned = shop().count(type), refund = Economy.sellPrice(Economy.piecePrice(type));
            Rectangle r = new Rectangle(30, 160 + i * 12, 130, 11);
            boolean over = hot(r) && owned > 0;
            Px.text(g, type + " x" + owned, 34, r.y + 2, owned == 0 ? Px.DIM : over ? Px.EMBER : Px.CREAM, 1);
            Px.text(g, "+" + refund, 156 - Px.textWidth("+" + refund, 1), r.y + 2, owned == 0 ? Px.DIM : Px.YELLOW, 1);
            if (owned > 0) hits.add(new Hit(r, () -> deal(shop().sellPiece(type), "He pockets the " + type.toLowerCase() + ".")));
        }
        List<Card> owned = new ArrayList<>(shop().getCards().getActiveCards());
        if (owned.isEmpty()) Px.text(g, "No cards to sell.", 178, 162, Px.DIM, 1);
        for (int i = 0; i < owned.size() && i < 12; i++) {
            Card c = owned.get(i);
            int x = 174 + (i / 6) * 140, y = 160 + (i % 6) * 12;
            Rectangle r = new Rectangle(x, y, 134, 11);
            boolean over = hot(r);
            String price = "+" + Economy.cardSellPrice(c);
            Px.text(g, cut(c.getName(), 16), x + 4, y + 2, over ? Px.EMBER : rarity(c), 1);
            Px.text(g, price, x + 132 - Px.textWidth(price, 1), y + 2, Px.YELLOW, 1);
            if (over) info = c.getName() + ": " + c.getDescription();
            hits.add(new Hit(r, () -> deal(shop().sellCard(c), "He tucks " + c.getName() + " into his coat.")));
        }
        return info;
    }

    private void drawDraft(Graphics2D g, double t) {
        drawShop(g, t);
        hits.clear();
        cardActions = null;
        Px.rect(g, 0, 0, Px.W, Px.H, new Color(6, 3, 10, 200));
        Px.centred(g, "KEEP ONE", 240, 40, Px.EMBER, 2);
        List<Card> draft = new ArrayList<>(shop().getMysteryPackCards());
        String info = "Hover to read. Click one to keep it.";
        int n = draft.size(), w = 130, gap = 12, x0 = (Px.W - (n * w + (n - 1) * gap)) / 2;
        for (int i = 0; i < n; i++) {
            Card c = draft.get(i);
            Rectangle r = new Rectangle(x0 + i * (w + gap), 66, w, 146);
            boolean over = hot(r);
            cardTile(g, r, c.getName(), c.getDescription(), rarity(c), c.getRarity().toString(), over, false);
            if (over) info = c.getName() + ": " + c.getDescription();
            hits.add(new Hit(r, () -> { deal(shop().draftMysteryCard(c), "You keep " + c.getName() + "."); setMode(Mode.SHOP, 2); }));
        }
        Px.frame(g, 20, 220, 440, 60);
        Px.wrap(g, info, 34, 234, 410, Px.CREAM, 999);
        choices(g, new String[]{"SKIP"}, new Runnable[]{() -> { setMode(Mode.SHOP, 2); say(dealerPerson().name, new String[]{"You push the pack back.", "Suit yourself."}, dealerPerson().voice); }}, t);
    }

    // ─── Pictures of each screen, for checking the look ─────────
    /** java PixelGame out.png SCREEN  (menu, name, face, board, summary, swap, shop0, shop1, shop2, draft, hand, over, confirm) */
    public static void main(String[] args) throws Exception {
        PixelGame p = new PixelGame();
        p.run.startNewRun("Tester");
        p.run.getShop().setGold(40);
        String s = args[1];
        double t = 0.4;
        switch (s) {
            case "menu": break;
            case "name": p.name.append("Madhu"); p.setMode(Mode.NAME, 0); p.say(null, new String[]{"Before you go down, the cellar wants a name.", ""}, 200); break;
            case "face": p.startRoundScreen(); break;
            case "board": {
                p.startRoundScreen();
                p.setMode(Mode.BOARD, 0);
                GameState st = p.run.getRoundState();
                outer:
                for (int r = 7; r >= 0; r--) for (int c = 0; c < 8; c++) {
                    Piece pc = st.board[r][c];
                    if (pc != null && pc.isWhite() && pc.getName().equals("Pawn")) { p.boardClick(r, c); break outer; }
                }
                p.sayOpp(Lines.check());
                p.toast("Fork +3 gold");
                break;
            }
            case "summary": {
                Economy.Payout pay = Economy.payout(GameResult.WHITE_WINS, 4, 9, 3, 1, 10);
                p.summary = new RunController.RoundSummary(GameResult.WHITE_WINS, "White wins", 1, pay, new ArrayList<>(), 31, 2,
                    false, false, 1, java.util.Arrays.asList("Pawn Surge"));
                p.setMode(Mode.SUMMARY, 0);
                break;
            }
            case "swap": p.swapFrom = 0; p.swapTo = 1; p.setMode(Mode.SWAP, 0); p.swapStart = 0; break;
            case "shop0": case "shop1": case "shop2": case "draft": {
                p.run.getShop().generateCardOfferings(1, new Random(3));
                p.mode = Mode.CONFIRM;
                p.enterShop();
                p.dealer = s.equals("draft") ? 0 : s.charAt(4) - '0';
                p.say(p.dealerPerson().name, Lines.dealerGreet(p.dealer), 0);
                if (s.equals("shop2")) p.shop().buyFaceUpCard(p.shop().getFaceUpCards().get(0));
                if (s.equals("draft")) { p.shop().setGold(99); p.shop().openMysteryPack(1); p.setMode(Mode.DRAFT, 0); }
                p.mouse = s.equals("shop1") ? new Point(200, 190) : s.equals("shop0") ? new Point(80, 190) : new Point(-1, -1);
                break;
            }
            case "hand": {
                p.run.getShop().generateCardOfferings(1, new Random(3));
                p.shop().setGold(99);
                for (Card c : new ArrayList<>(p.shop().getFaceUpCards())) p.shop().buyFaceUpCard(c);
                p.stash = new ArrayList<>(p.shop().getCards().getActiveCards());
                p.picked = new boolean[p.stash.size()];
                p.picked[0] = true;
                p.setMode(Mode.HAND, 0);
                p.mouse = new Point(100, 82);
                break;
            }
            case "over": p.setMode(Mode.OVER, 0); p.say(null, new String[]{"You push your chair back and climb the stairs. Behind you, the lamp goes out.", ""}, 0); break;
            case "confirm": p.startRoundScreen(); p.askGiveUp(); break;
            default: break;
        }
        if (p.talk != null) p.talk.skip();
        BufferedImage img = Px.canvas();
        Graphics2D g = Px.pen(img);
        p.draw(g, p.mode, s.equals("swap") ? 3.4 : t);
        g.dispose();
        BufferedImage big = new BufferedImage(Px.W * 2, Px.H * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D bg = big.createGraphics();
        Px.present(bg, img, big.getWidth(), big.getHeight());
        bg.dispose();
        javax.imageio.ImageIO.write(big, "png", new java.io.File(args[0]));
        System.exit(0);
    }

    // ─── Choosing the hand ──────────────────────────────────────
    private void drawHand(Graphics2D g, double t) {
        Px.cellar(g, 240, 90, 300, flicker(t));
        g.drawImage(PixelArt.person(opponent(), t, null, 0), (Px.W - PixelArt.SPRITE_W) / 2, 50, null);
        lamp(g, 240, t);
        table(g, 30, 186, 420, 18);
        Px.rect(g, 0, 0, Px.W, Px.H, new Color(6, 3, 10, 120));
        int max = run.getHandSize(), count = 0;
        for (boolean b : picked) if (b) count++;
        Px.frame(g, 50, 30, 380, 270);
        Px.plate(g, "Choose your hand: " + count + " of " + max, 62, 24);
        Px.wrap(g, "Cards you play are used up. If you lose, your whole hand is lost.", 66, 44, 350, Px.ASH, 999);
        String info = "";
        for (int i = 0; i < stash.size() && i < 16; i++) {
            Card c = stash.get(i);
            Rectangle r = new Rectangle(62, 68 + i * 12, 356, 11);
            boolean over = hot(r);
            if (picked[i]) Px.rect(g, 66, r.y + 1, 7, 7, Px.EMBER);
            else { Px.rect(g, 66, r.y + 1, 7, 7, Px.DIM); Px.rect(g, 67, r.y + 2, 5, 5, Px.CHAR); }
            Px.text(g, c.getName(), 80, r.y + 1, over ? Px.EMBER : picked[i] ? Px.CREAM : Px.ASH, 1);
            Px.text(g, c.getRarity().toString(), 412 - Px.textWidth(c.getRarity().toString(), 1), r.y + 1, rarity(c), 1);
            if (over) info = c.getDescription();
            final int idx = i;
            final int chosen = count;
            hits.add(new Hit(r, () -> { if (picked[idx] || chosen < max) picked[idx] = !picked[idx]; }));
        }
        Px.wrap(g, info, 66, 268, 350, Px.CREAM, 999);
        choices(g, new String[]{"SIT DOWN", "BACK"}, new Runnable[]{() -> {
            List<Card> hand = new ArrayList<>();
            for (int i = 0; i < stash.size(); i++) if (picked[i]) hand.add(stash.get(i));
            if (run.setHand(hand) && run.finishShopping()) startRoundScreen();
        }, () -> setMode(Mode.SHOP, 2)}, t);
    }
}
