import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The shop between rounds, split across the three dealers around the player's seat:
 *   - piece dealer (right): buy pieces
 *   - card dealer (left): buy cards and the mystery pack
 *   - reseller (behind, right): sell pieces and cards back for half
 * The camera turns between them using the pre-rendered clips (ScenePlayer). Without the
 * rendered scenes the same panels show over a plain background.
 *
 * Drawing and click areas come from the same code: every button registers its rectangle
 * while it is painted.
 */
public class ShopScene extends JPanel {
    private static final String[] DEALERS = {"card", "piece", "resell"};
    private static final String[] DEALER_TITLES = {"THE CARD DEALER", "THE PIECE DEALER", "THE RESELLER"};

    private final RoguelikeChessAppSwing app;
    private final ScenePlayer scene;
    private int dealer = 1; // start at the piece dealer: the requirement comes first
    private boolean showingDraft = false;
    private String status = "";

    private final List<Button> buttons = new ArrayList<>();
    private Point mouse = new Point(-1, -1);

    private static final Color BG_DARK = new Color(10, 8, 12);
    private static final Color PANEL = new Color(12, 9, 14, 215);
    private static final Color CRIMSON = new Color(180, 30, 40);
    private static final Color CRIMSON_DIM = new Color(150, 40, 45);
    private static final Color GOLD_COLOR = new Color(212, 175, 55);
    private static final Color GOOD = new Color(90, 190, 110);
    private static final Color TEXT_DIM = new Color(160, 150, 165);
    private static final Color CARD_BG = new Color(25, 20, 32);
    private static final Color CARD_BORDER = new Color(80, 30, 40);
    private static final Color BTN_BG = new Color(40, 20, 25);
    private static final Color BTN_HOVER = new Color(70, 30, 40);
    private static final Color BTN_OFF = new Color(30, 28, 32);
    private static final Color SELL = new Color(150, 120, 60);

    private static class Button {
        final Rectangle area;
        final boolean enabled;
        final Runnable action;

        Button(Rectangle area, boolean enabled, Runnable action) {
            this.area = area;
            this.enabled = enabled;
            this.action = action;
        }
    }

    public ShopScene(RoguelikeChessAppSwing app) {
        this.app = app;
        setLayout(null);
        setBackground(BG_DARK);
        setFocusable(true);
        scene = new ScenePlayer(this);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { handleClick(e.getPoint()); }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { mouse = e.getPoint(); }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (showingDraft) return;
                if (e.getKeyCode() == KeyEvent.VK_LEFT) turn(-1);
                if (e.getKeyCode() == KeyEvent.VK_RIGHT) turn(1);
            }
        });
    }

    /** Called when the shop opens after a round: turn from the table to the piece dealer. */
    public void refresh() {
        showingDraft = false;
        status = "";
        dealer = 1;
        scene.setView("table");
        scene.turnTo(DEALERS[dealer], null);
        requestFocusInWindow();
        repaint();
    }

    private RunController run() { return app.getRun(); }

    private ShopManager shop() { return run().getShop(); }

    private void turn(int step) {
        if (scene.isMoving()) return;
        dealer = (dealer + step + DEALERS.length) % DEALERS.length;
        status = "";
        scene.turnTo(DEALERS[dealer], null);
    }

    private void startLevel() {
        if (!shop().meetsRequirement(run().getLevel())) {
            status = "Your army doesn't meet the requirement yet.";
            return;
        }
        // Turn back to the table, then sit down to play.
        scene.turnTo("table", () -> {
            if (!app.onShoppingComplete()) {
                status = "Not yet: pick your hand when you're ready.";
                scene.turnTo(DEALERS[dealer], null);
            }
        });
    }

    /** Runs a shop action and shows its result as the status line. */
    private void act(ShopManager.Result r, String success) {
        status = r == ShopManager.Result.OK ? success : r.message;
        if (r == ShopManager.Result.OK) AudioPlayer.playMoveSound();
        // The dealer you're facing nods at a deal or shakes his head at a refusal.
        scene.react(r == ShopManager.Result.OK ? "yes" : "no");
    }

    private void handleClick(Point p) {
        requestFocusInWindow();
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Button b = buttons.get(i);
            if (b.area.contains(p)) {
                if (b.enabled) {
                    b.action.run();
                } else if (!scene.isMoving() && !scene.getView().equals("table")) {
                    scene.react("no"); // can't afford it (or nothing to sell): the dealer shakes his head
                }
                repaint();
                return;
            }
        }
    }

    // ─── PAINTING ───────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        buttons.clear();
        int w = getWidth(), h = getHeight();

        if (!scene.paint(g, w, h)) {
            g.setPaint(new GradientPaint(0, 0, BG_DARK, 0, h, new Color(15, 10, 20)));
            g.fillRect(0, 0, w, h);
        }

        drawHeader(g, w);
        if (!scene.isMoving() && !scene.getView().equals("table")) {
            int panelY = 300, panelH = h - panelY - 96;
            g.setColor(PANEL);
            g.fillRoundRect(16, panelY, w - 32, panelH, 12, 12);
            g.setColor(CARD_BORDER);
            g.drawRoundRect(16, panelY, w - 32, panelH, 12, 12);
            g.setFont(new Font("Serif", Font.BOLD, 18));
            g.setColor(CRIMSON);
            g.drawString(DEALER_TITLES[dealer], 32, panelY + 26);
            switch (DEALERS[dealer]) {
                case "piece":  drawPieceDealer(g, 32, panelY + 38); break;
                case "card":   drawCardDealer(g, 32, panelY + 38, w); break;
                default:       drawReseller(g, 32, panelY + 38, w); break;
            }
        }
        drawFooter(g, w, h);
        if (showingDraft) drawDraftOverlay(g, w, h);
    }

    private void drawHeader(Graphics2D g, int w) {
        int level = run().getLevel();
        ShopManager shop = shop();
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, w, 64);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(GOLD_COLOR);
        g.drawString("Gold: " + shop.getGold(), 20, 28);
        int have = shop.armyMaterial(), need = Economy.requirement(level);
        String hint = have >= need ? "  ✔"
            : shop.canStillReach(level) ? "  (need " + (need - have) + " more)"
            : "  (can't afford it: sell cards or give up)";
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(have >= need ? GOOD : CRIMSON);
        g.drawString(String.format("Level %d needs %d material. Your army: %d%s", level, need, have, hint), 170, 26);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(TEXT_DIM);
        g.drawString(String.format("Pawns %d / %d allowed  ·  Hand %d cards  ·  AI depth %d  ·  Best level beaten: %d",
            shop.count("Pawn"), shop.pawnCap(), run().getHandSize(), Difficulty.forLevel(level).searchDepth,
            run().getHighestLevelBeaten()), 170, 46);
    }

    private void drawFooter(Graphics2D g, int w, int h) {
        g.setFont(new Font("SansSerif", Font.ITALIC, 13));
        g.setColor(new Color(220, 210, 200));
        g.drawString(status, 24, h - 80);

        boolean idle = !scene.isMoving();
        int by = h - 64;
        button(g, "◀", 24, by, 52, 44, idle, CRIMSON_DIM, new Font("SansSerif", Font.BOLD, 18), () -> turn(-1));
        button(g, "▶", 84, by, 52, 44, idle, CRIMSON_DIM, new Font("SansSerif", Font.BOLD, 18), () -> turn(1));
        boolean ready = shop().meetsRequirement(run().getLevel());
        int btnW = 280, btnX = (w - btnW) / 2;
        button(g, ready ? "SIT DOWN: LEVEL " + run().getLevel() : "NEED MORE MATERIAL", btnX, by, btnW, 44,
            ready && idle, CRIMSON, new Font("Serif", Font.BOLD, 16), this::startLevel);
        button(g, "Give up", w - 114, by + 8, 90, 28, idle, new Color(90, 90, 90),
            new Font("SansSerif", Font.PLAIN, 12), app::onAbandonRun);
    }

    private void drawPieceDealer(Graphics2D g, int x, int y) {
        int cardW = 150, cardH = 150, gap = 14;
        for (int i = 0; i < Economy.PIECE_TYPES.length; i++) {
            String type = Economy.PIECE_TYPES[i];
            int cx = x + i * (cardW + gap);
            int price = Economy.piecePrice(type);
            tile(g, cx, y, cardW, cardH);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Serif", Font.BOLD, 16));
            centered(g, Utils.getPieceSymbol(createSample(type)) + " " + type, cx + cardW / 2, y + 26);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(TEXT_DIM);
            centered(g, Economy.material(createSample(type)) + " material", cx + cardW / 2, y + 48);
            g.setColor(GOLD_COLOR);
            centered(g, price + " gold", cx + cardW / 2, y + 68);
            g.setColor(TEXT_DIM);
            centered(g, "Owned: " + shop().count(type), cx + cardW / 2, y + 88);
            button(g, "BUY", cx + 20, y + 108, cardW - 40, 28, shop().getGold() >= price, CRIMSON,
                new Font("SansSerif", Font.BOLD, 12), () -> act(shop().buyPiece(type), "Bought a " + type + "."));
        }
    }

    private void drawCardDealer(Graphics2D g, int x, int y, int w) {
        List<Card> faceUp = shop().getFaceUpCards();
        int cardW = 180, cardH = 200, gap = 16;
        for (int i = 0; i < faceUp.size(); i++) {
            Card card = faceUp.get(i);
            int cx = x + i * (cardW + gap);
            int price = Economy.faceUpCardPrice(card);
            drawCardPanel(g, card, cx, y, cardW, cardH);
            g.setColor(GOLD_COLOR);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            centered(g, price + " gold", cx + cardW / 2, y + cardH - 36);
            button(g, "BUY", cx + 24, y + cardH - 28, cardW - 48, 22, shop().getGold() >= price, CRIMSON,
                new Font("SansSerif", Font.BOLD, 11), () -> act(shop().buyFaceUpCard(card), "Bought " + card.getName() + "."));
        }
        int level = run().getLevel();
        int packX = x + 3 * (cardW + gap);
        g.setColor(new Color(20, 15, 35));
        g.fillRoundRect(packX, y, cardW, cardH, 10, 10);
        g.setStroke(new BasicStroke(2));
        g.setColor(new Color(100, 50, 150));
        g.drawRoundRect(packX, y, cardW, cardH, 10, 10);
        g.setStroke(new BasicStroke(1));
        g.setColor(new Color(170, 120, 220));
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 17));
        centered(g, "MYSTERY PACK", packX + cardW / 2, y + 60);
        int packPrice = Economy.mysteryPackPrice(level);
        g.setColor(GOLD_COLOR);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        centered(g, packPrice + " gold", packX + cardW / 2, y + 100);
        g.setColor(TEXT_DIM);
        g.setFont(new Font("SansSerif", Font.ITALIC, 11));
        centered(g, "Draft 1 of 3 hidden cards", packX + cardW / 2, y + 120);
        boolean canOpen = !shop().isMysteryPackOpened() && !shop().getMysteryPackCards().isEmpty()
            && shop().getGold() >= packPrice;
        button(g, shop().isMysteryPackOpened() ? "OPENED" : "OPEN", packX + 24, y + cardH - 28, cardW - 48, 22,
            canOpen, new Color(100, 50, 150), new Font("SansSerif", Font.BOLD, 11), () -> {
                ShopManager.Result r = shop().openMysteryPack(level);
                act(r, "Pick one card.");
                if (r == ShopManager.Result.OK) showingDraft = true;
            });
    }

    private void drawReseller(Graphics2D g, int x, int y, int w) {
        g.setFont(new Font("SansSerif", Font.ITALIC, 12));
        g.setColor(TEXT_DIM);
        g.drawString("Everything sells for half its price.", x + 200, y - 12);
        // Pieces
        int rowH = 30;
        for (int i = 0; i < Economy.PIECE_TYPES.length; i++) {
            String type = Economy.PIECE_TYPES[i];
            int owned = shop().count(type);
            int ry = y + i * rowH;
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.setColor(Color.WHITE);
            g.drawString(type + "  ×" + owned, x, ry + 18);
            int refund = Economy.sellPrice(Economy.piecePrice(type));
            button(g, "SELL +" + refund, x + 130, ry + 2, 90, 22, owned > 0, SELL,
                new Font("SansSerif", Font.BOLD, 11), () -> act(shop().sellPiece(type), "Sold a " + type + "."));
        }
        // Cards in the stash, two columns
        List<Card> owned = new ArrayList<>(shop().getCards().getActiveCards());
        int cx = x + 280;
        g.setFont(new Font("Serif", Font.BOLD, 14));
        g.setColor(CRIMSON_DIM);
        g.drawString("YOUR STASH", cx, y + 4);
        if (owned.isEmpty()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(TEXT_DIM);
            g.drawString("No cards.", cx, y + 26);
        }
        for (int i = 0; i < owned.size() && i < 14; i++) {
            Card c = owned.get(i);
            int col = i / 7, row = i % 7;
            int ox = cx + col * 270, oy = y + 12 + row * 24;
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(c.isGlobal() ? new Color(200, 160, 60) : new Color(110, 180, 230));
            g.drawString(c.getName(), ox, oy + 15);
            button(g, "SELL +" + Economy.cardSellPrice(c), ox + 170, oy, 80, 20, true, SELL,
                new Font("SansSerif", Font.BOLD, 10), () -> act(shop().sellCard(c), "Sold " + c.getName() + "."));
        }
    }

    private void drawCardPanel(Graphics2D g, Card card, int x, int y, int w, int h) {
        g.setColor(CARD_BG);
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(card.isGlobal() ? new Color(110, 80, 30) : CARD_BORDER);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(x, y, w, h, 10, 10);
        g.setStroke(new BasicStroke(1));
        g.setColor(card.isGlobal() ? new Color(200, 160, 60) : new Color(70, 150, 210));
        g.setFont(new Font("SansSerif", Font.BOLD, 9));
        centered(g, card.getRarity() + " · " + (card.isGlobal() ? "GLOBAL" : "LOCAL"), x + w / 2, y + 16);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Serif", Font.BOLD, 14));
        centered(g, card.getName(), x + w / 2, y + 38);
        g.setColor(TEXT_DIM);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        wrapped(g, card.getDescription(), x + 10, y + 58, w - 20, 13);
    }

    private void drawDraftOverlay(Graphics2D g, int w, int h) {
        buttons.clear(); // only the overlay is clickable
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(170, 120, 220));
        g.setFont(new Font("Serif", Font.BOLD, 28));
        centered(g, "— CHOOSE YOUR CARD —", w / 2, 80);
        g.setColor(TEXT_DIM);
        g.setFont(new Font("SansSerif", Font.ITALIC, 14));
        centered(g, "Pick 1 card to keep, or skip", w / 2, 110);

        List<Card> draft = new ArrayList<>(shop().getMysteryPackCards());
        int cardW = 190, cardH = 220, gap = 20;
        int startX = (w - (draft.size() * cardW + Math.max(0, draft.size() - 1) * gap)) / 2;
        for (int i = 0; i < draft.size(); i++) {
            Card card = draft.get(i);
            int cx = startX + i * (cardW + gap);
            drawCardPanel(g, card, cx, 140, cardW, cardH);
            button(g, "TAKE", cx + 24, 140 + cardH - 32, cardW - 48, 24, true, new Color(100, 50, 150),
                new Font("SansSerif", Font.BOLD, 11), () -> {
                    act(shop().draftMysteryCard(card), "Took " + card.getName() + ".");
                    showingDraft = false;
                });
        }
        button(g, "SKIP", w / 2 - 60, 400, 120, 34, true, new Color(80, 80, 80),
            new Font("Serif", Font.BOLD, 14), () -> {
                showingDraft = false;
                status = "Skipped the pack.";
            });
    }

    // ─── DRAWING HELPERS ────────────────────────────────────────

    private void tile(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(CARD_BG);
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(CARD_BORDER);
        g.drawRoundRect(x, y, w, h, 8, 8);
    }

    private void button(Graphics2D g, String text, int x, int y, int w, int h, boolean enabled,
                        Color accent, Font font, Runnable action) {
        Rectangle r = new Rectangle(x, y, w, h);
        boolean hover = enabled && r.contains(mouse);
        g.setColor(!enabled ? BTN_OFF : hover ? BTN_HOVER : BTN_BG);
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.setColor(enabled ? accent : new Color(60, 55, 65));
        g.setStroke(new BasicStroke(enabled ? 2 : 1));
        g.drawRoundRect(x, y, w, h, 6, 6);
        g.setStroke(new BasicStroke(1));
        g.setColor(enabled ? Color.WHITE : new Color(110, 105, 115));
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, x + (w - fm.stringWidth(text)) / 2, y + (h + fm.getAscent() - fm.getDescent()) / 2);
        buttons.add(new Button(r, enabled, action));
    }

    private static Piece createSample(String type) {
        switch (type) {
            case "Pawn": return new Pawn(true);
            case "Knight": return new Knight(true);
            case "Bishop": return new Bishop(true);
            case "Rook": return new Rook(true);
            default: return new Queen(true);
        }
    }

    private void centered(Graphics2D g, String s, int cx, int cy) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, cy);
    }

    private void wrapped(Graphics2D g, String text, int x, int y, int maxW, int lineH) {
        FontMetrics fm = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        int cy = y;
        for (String word : text.split(" ")) {
            if (line.length() > 0 && fm.stringWidth(line + " " + word) > maxW) {
                g.drawString(line.toString(), x, cy);
                line = new StringBuilder(word);
                cy += lineH;
            } else {
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
        }
        if (line.length() > 0) g.drawString(line.toString(), x, cy);
    }
}
