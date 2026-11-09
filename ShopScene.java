import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The shop between rounds. Drawing and click areas come from the same code: every button
 * registers its rectangle while it is painted, so the two can't drift apart.
 */
public class ShopScene extends JPanel {
    private final RoguelikeChessAppSwing app;
    private boolean showingDraft = false;
    private String status = "";

    /** Buttons registered during the last paint, in drawing order. */
    private final List<Button> buttons = new ArrayList<>();
    private Point mouse = new Point(-1, -1);

    private static final Color BG_DARK = new Color(10, 8, 12);
    private static final Color CRIMSON = new Color(180, 30, 40);
    private static final Color CRIMSON_DIM = new Color(120, 20, 30);
    private static final Color GOLD_COLOR = new Color(212, 175, 55);
    private static final Color GOOD = new Color(90, 190, 110);
    private static final Color TEXT_DIM = new Color(140, 130, 150);
    private static final Color CARD_BG = new Color(25, 20, 32);
    private static final Color CARD_BORDER = new Color(80, 30, 40);
    private static final Color BTN_BG = new Color(40, 20, 25);
    private static final Color BTN_HOVER = new Color(70, 30, 40);
    private static final Color BTN_OFF = new Color(30, 28, 32);

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
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { handleClick(e.getPoint()); }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { mouse = e.getPoint(); repaint(); }
        });
    }

    public void refresh() {
        showingDraft = false;
        status = "";
        repaint();
    }

    private RunController run() { return app.getRun(); }

    private ShopManager shop() { return run().getShop(); }

    /** Runs a shop action and shows its result as the status line. */
    private void act(ShopManager.Result r, String success) {
        status = r == ShopManager.Result.OK ? success : r.message;
        if (r == ShopManager.Result.OK) AudioPlayer.playMoveSound();
    }

    private void handleClick(Point p) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Button b = buttons.get(i);
            if (b.area.contains(p)) {
                if (b.enabled) b.action.run();
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

        g.setPaint(new GradientPaint(0, 0, BG_DARK, 0, h, new Color(15, 10, 20)));
        g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, h / 2f), Math.max(w, h) * 0.7f,
            new float[]{0f, 0.6f, 1f},
            new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 60), new Color(0, 0, 0, 180)}));
        g.fillRect(0, 0, w, h);

        int level = run().getLevel();
        g.setFont(new Font("Serif", Font.BOLD, 34));
        g.setColor(CRIMSON);
        centered(g, "— THE ARMORY —", (w - 200) / 2, 42);

        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(GOLD_COLOR);
        g.drawString("Gold: " + shop().getGold(), 30, 40);

        drawRequirement(g, level);
        drawPieceShop(g, 30, 112);
        drawCardShop(g, 30, 290, level);
        drawOwnedCards(g, w - 200, 60, h - 150);

        g.setFont(new Font("SansSerif", Font.ITALIC, 13));
        g.setColor(TEXT_DIM);
        g.drawString(status, 30, h - 88);

        boolean ready = shop().meetsRequirement(level);
        int btnW = 280, btnX = (w - 200) / 2 - btnW / 2, btnY = h - 70;
        button(g, ready ? "START LEVEL " + level : "NEED MORE MATERIAL", btnX, btnY, btnW, 46, ready, CRIMSON,
            new Font("Serif", Font.BOLD, 16), () -> {
                if (!app.onShoppingComplete()) status = "Your army doesn't meet the requirement yet.";
            });
        button(g, "Give up", btnX + btnW + 20, btnY + 10, 90, 28, true, new Color(90, 90, 90),
            new Font("SansSerif", Font.PLAIN, 12), app::onAbandonRun);

        if (showingDraft) drawDraftOverlay(g, w, h);
    }

    private void drawRequirement(Graphics2D g, int level) {
        ShopManager shop = shop();
        int have = shop.armyMaterial(), need = Economy.requirement(level);
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.setColor(have >= need ? GOOD : CRIMSON);
        String hint = have >= need ? "  ✔"
            : shop.canStillReach(level) ? "  (need " + (need - have) + " more)"
            : "  (can't afford it: sell cards or give up)";
        g.drawString(String.format("Level %d needs %d material. Your army: %d%s", level, need, have, hint), 30, 72);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(TEXT_DIM);
        g.drawString(String.format("Pawns %d / %d allowed  ·  AI depth %d  ·  Best level beaten: %d",
            shop.count("Pawn"), shop.pawnCap(), Difficulty.forLevel(level).searchDepth,
            run().getHighestLevelBeaten()), 30, 92);
    }

    private void drawPieceShop(Graphics2D g, int x, int y) {
        g.setFont(new Font("Serif", Font.BOLD, 18));
        g.setColor(CRIMSON_DIM);
        g.drawString("PIECES", x, y);
        int cardW = 118, cardH = 150, gap = 12;
        for (int i = 0; i < Economy.PIECE_TYPES.length; i++) {
            String type = Economy.PIECE_TYPES[i];
            int cx = x + i * (cardW + gap), cy = y + 10;
            int price = Economy.piecePrice(type);
            int owned = shop().count(type);
            g.setColor(CARD_BG);
            g.fillRoundRect(cx, cy, cardW, cardH, 8, 8);
            g.setColor(CARD_BORDER);
            g.drawRoundRect(cx, cy, cardW, cardH, 8, 8);

            g.setColor(Color.WHITE);
            g.setFont(new Font("Serif", Font.BOLD, 15));
            centered(g, type, cx + cardW / 2, cy + 22);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(TEXT_DIM);
            centered(g, Economy.material(createSample(type)) + " material", cx + cardW / 2, cy + 40);
            g.setColor(GOLD_COLOR);
            centered(g, price + " gold", cx + cardW / 2, cy + 57);
            g.setColor(TEXT_DIM);
            centered(g, "Owned: " + owned, cx + cardW / 2, cy + 74);

            button(g, "BUY", cx + 14, cy + 86, cardW - 28, 24, shop().getGold() >= price, CRIMSON,
                new Font("SansSerif", Font.BOLD, 11), () -> act(shop().buyPiece(type), "Bought a " + type + "."));
            button(g, "SELL +" + Economy.sellPrice(price), cx + 14, cy + 116, cardW - 28, 24, owned > 0,
                new Color(120, 100, 60), new Font("SansSerif", Font.BOLD, 11),
                () -> act(shop().sellPiece(type), "Sold a " + type + "."));
        }
    }

    private void drawCardShop(Graphics2D g, int x, int y, int level) {
        g.setFont(new Font("Serif", Font.BOLD, 18));
        g.setColor(CRIMSON_DIM);
        g.drawString("CARDS", x, y);
        List<Card> faceUp = shop().getFaceUpCards();
        int cardW = 150, cardH = 190, gap = 12, cy = y + 10;
        for (int i = 0; i < faceUp.size(); i++) {
            Card card = faceUp.get(i);
            int cx = x + i * (cardW + gap);
            int price = Economy.faceUpCardPrice(card);
            drawCardPanel(g, card, cx, cy, cardW, cardH);
            g.setColor(GOLD_COLOR);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            centered(g, price + " gold", cx + cardW / 2, cy + cardH - 36);
            button(g, "BUY", cx + 20, cy + cardH - 28, cardW - 40, 22, shop().getGold() >= price, CRIMSON,
                new Font("SansSerif", Font.BOLD, 11), () -> act(shop().buyFaceUpCard(card), "Bought " + card.getName() + "."));
        }

        int packX = x + faceUp.size() * (cardW + gap);
        g.setColor(new Color(20, 15, 35));
        g.fillRoundRect(packX, cy, cardW, cardH, 10, 10);
        g.setStroke(new BasicStroke(2));
        g.setColor(new Color(100, 50, 150));
        g.drawRoundRect(packX, cy, cardW, cardH, 10, 10);
        g.setStroke(new BasicStroke(1));
        g.setColor(new Color(150, 100, 200));
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 16));
        centered(g, "MYSTERY", packX + cardW / 2, cy + 50);
        centered(g, "PACK", packX + cardW / 2, cy + 72);
        int packPrice = Economy.mysteryPackPrice(level);
        g.setColor(GOLD_COLOR);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        centered(g, packPrice + " gold", packX + cardW / 2, cy + 110);
        g.setColor(TEXT_DIM);
        g.setFont(new Font("SansSerif", Font.ITALIC, 10));
        centered(g, "Draft 1 of 3 cards", packX + cardW / 2, cy + 128);
        boolean canOpen = !shop().isMysteryPackOpened() && !shop().getMysteryPackCards().isEmpty()
            && shop().getGold() >= packPrice;
        button(g, shop().isMysteryPackOpened() ? "OPENED" : "OPEN", packX + 20, cy + cardH - 28, cardW - 40, 22,
            canOpen, new Color(100, 50, 150), new Font("SansSerif", Font.BOLD, 11), () -> {
                ShopManager.Result r = shop().openMysteryPack(level);
                act(r, "Pick one card.");
                if (r == ShopManager.Result.OK) showingDraft = true;
            });
    }

    private void drawOwnedCards(Graphics2D g, int sx, int sy, int sh) {
        int sideW = 190;
        g.setColor(new Color(15, 12, 20, 200));
        g.fillRoundRect(sx, sy, sideW, sh, 8, 8);
        g.setColor(CARD_BORDER);
        g.drawRoundRect(sx, sy, sideW, sh, 8, 8);
        g.setColor(CRIMSON_DIM);
        g.setFont(new Font("Serif", Font.BOLD, 14));
        centered(g, "STASH  (hand " + run().getHandSize() + ")", sx + sideW / 2, sy + 20);

        List<Card> owned = shop().getCards().getActiveCards();
        if (owned.isEmpty()) {
            g.setColor(TEXT_DIM);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString("No cards yet", sx + 50, sy + 48);
        }
        for (int i = 0; i < owned.size() && i < 16; i++) {
            Card c = new ArrayList<>(owned).get(i);
            int ry = sy + 36 + i * 26;
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(c.isGlobal() ? new Color(180, 140, 40) : new Color(80, 160, 220));
            g.drawString(c.getName(), sx + 8, ry + 14);
            button(g, "+" + Economy.cardSellPrice(c), sx + sideW - 46, ry, 38, 20, true, new Color(120, 100, 60),
                new Font("SansSerif", Font.BOLD, 10), () -> act(shop().sellCard(c), "Sold " + c.getName() + "."));
        }
    }

    private void drawCardPanel(Graphics2D g, Card card, int x, int y, int w, int h) {
        g.setColor(CARD_BG);
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(card.isGlobal() ? new Color(80, 60, 30) : CARD_BORDER);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(x, y, w, h, 10, 10);
        g.setStroke(new BasicStroke(1));
        g.setColor(card.isGlobal() ? new Color(180, 140, 40) : new Color(40, 120, 180));
        g.setFont(new Font("SansSerif", Font.BOLD, 9));
        centered(g, card.getRarity() + " · " + (card.isGlobal() ? "GLOBAL" : "LOCAL"), x + w / 2, y + 16);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Serif", Font.BOLD, 13));
        centered(g, card.getName(), x + w / 2, y + 36);
        g.setColor(TEXT_DIM);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        wrapped(g, card.getDescription(), x + 8, y + 52, w - 16, 12);
    }

    private void drawDraftOverlay(Graphics2D g, int w, int h) {
        buttons.clear(); // only the overlay is clickable
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(150, 100, 200));
        g.setFont(new Font("Serif", Font.BOLD, 28));
        centered(g, "— CHOOSE YOUR CARD —", w / 2, 80);
        g.setColor(TEXT_DIM);
        g.setFont(new Font("SansSerif", Font.ITALIC, 14));
        centered(g, "Pick 1 card to keep, or skip", w / 2, 110);

        List<Card> draft = new ArrayList<>(shop().getMysteryPackCards());
        int cardW = 180, cardH = 210, gap = 20;
        int startX = (w - (draft.size() * cardW + Math.max(0, draft.size() - 1) * gap)) / 2;
        for (int i = 0; i < draft.size(); i++) {
            Card card = draft.get(i);
            int cx = startX + i * (cardW + gap);
            drawCardPanel(g, card, cx, 140, cardW, cardH);
            button(g, "TAKE", cx + 20, 140 + cardH - 30, cardW - 40, 24, true, new Color(100, 50, 150),
                new Font("SansSerif", Font.BOLD, 11), () -> {
                    act(shop().draftMysteryCard(card), "Took " + card.getName() + ".");
                    showingDraft = false;
                });
        }
        button(g, "SKIP", w / 2 - 60, 380, 120, 34, true, new Color(80, 80, 80),
            new Font("Serif", Font.BOLD, 14), () -> {
                showingDraft = false;
                status = "Skipped the pack.";
            });
    }

    // ─── DRAWING HELPERS ────────────────────────────────────────

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
