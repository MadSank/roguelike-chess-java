
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class ShopScene extends JPanel {
    private final RoguelikeChessAppSwing app;
    private final ShopManager shop;
    private final ScoreManager score;
    private boolean showingDraft = false;
    private List<Card> draftCards = new ArrayList<>();

    private static final Color BG_DARK = new Color(10, 8, 12);
    private static final Color PANEL_BG = new Color(18, 14, 22, 220);
    private static final Color CRIMSON = new Color(180, 30, 40);
    private static final Color CRIMSON_DIM = new Color(120, 20, 30);
    private static final Color GOLD_COLOR = new Color(212, 175, 55);
    private static final Color TEXT_DIM = new Color(140, 130, 150);
    private static final Color CARD_BG = new Color(25, 20, 32);
    private static final Color CARD_BORDER = new Color(80, 30, 40);
    private static final Color BTN_BG = new Color(40, 20, 25);
    private static final Color BTN_HOVER = new Color(70, 30, 40);

    public ShopScene(RoguelikeChessAppSwing app, ChessEngine engine,
            ShopManager shop, ScoreManager score) {
        this.app = app; this.shop = shop; this.score = score;
        setLayout(null);
        setBackground(BG_DARK);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { handleClick(e.getX(), e.getY()); }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { repaint(); }
        });
    }

    private ChessEngine getEngine() { return app.getEngine(); }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        int w = getWidth(), h = getHeight();

        // Background gradient
        GradientPaint bgGrad = new GradientPaint(0, 0, BG_DARK, 0, h, new Color(15, 10, 20));
        g.setPaint(bgGrad);
        g.fillRect(0, 0, w, h);

        // Vignette
        RadialGradientPaint vig = new RadialGradientPaint(
            new Point2D.Float(w/2f, h/2f), Math.max(w, h) * 0.7f,
            new float[]{0f, 0.6f, 1f},
            new Color[]{new Color(0,0,0,0), new Color(0,0,0,60), new Color(0,0,0,180)});
        g.setPaint(vig);
        g.fillRect(0, 0, w, h);

        GameState s = getEngine().getGameState();

        // Title
        g.setFont(new Font("Serif", Font.BOLD, 36));
        g.setColor(CRIMSON);
        drawCenteredString(g, "— THE ARMORY —", w/2, 45);

        // Gold & Round
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(GOLD_COLOR);
        g.drawString("⬡ Gold: " + s.goldPlayer, 30, 40);
        g.setColor(TEXT_DIM);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.drawString("Round " + s.roundNumber + " | Depth " + s.aiDepth, w - 200, 40);

        // Active cards sidebar
        drawActiveCards(g, w, h, s);

        // Piece shop section
        int pieceY = 80;
        g.setFont(new Font("Serif", Font.BOLD, 20));
        g.setColor(CRIMSON_DIM);
        g.drawString("PIECES", 40, pieceY);
        drawPieceShop(g, 40, pieceY + 10, w - 220);

        // Card shop section
        int cardY = 260;
        g.setFont(new Font("Serif", Font.BOLD, 20));
        g.setColor(CRIMSON_DIM);
        g.drawString("CARDS", 40, cardY);
        drawCardShop(g, 40, cardY + 10, w - 220, s);

        // Continue button
        int btnW = 260, btnH = 48;
        int btnX = (w - 220) / 2 - btnW / 2 + 20, btnY = h - 70;
        drawButton(g, "CONTINUE TO NEXT ROUND", btnX, btnY, btnW, btnH, CRIMSON);

        // Draft overlay
        if (showingDraft) drawDraftOverlay(g, w, h, s);
    }

    private void drawPieceShop(Graphics2D g, int x, int y, int maxW) {
        String[] items = {"Pawn", "Knight", "Bishop", "Rook", "Queen"};
        Map<String, Integer> prices = shop.getPrices();
        Map<String, Integer> counts = shop.getPieceCounts();
        int cardW = 110, cardH = 100, gap = 12;

        for (int i = 0; i < items.length; i++) {
            String it = items[i];
            int cx = x + i * (cardW + gap), cy = y;
            g.setColor(CARD_BG);
            g.fillRoundRect(cx, cy, cardW, cardH, 8, 8);
            g.setColor(CARD_BORDER);
            g.drawRoundRect(cx, cy, cardW, cardH, 8, 8);

            g.setColor(Color.WHITE);
            g.setFont(new Font("Serif", Font.BOLD, 14));
            drawCenteredString(g, it, cx + cardW/2, cy + 25);
            g.setColor(GOLD_COLOR);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            drawCenteredString(g, prices.get(it) + " gold", cx + cardW/2, cy + 45);
            g.setColor(TEXT_DIM);
            drawCenteredString(g, "Owned: " + counts.getOrDefault(it, 0), cx + cardW/2, cy + 62);

            // Buy button
            g.setColor(BTN_BG);
            g.fillRoundRect(cx + 15, cy + 72, cardW - 30, 22, 6, 6);
            g.setColor(CRIMSON);
            g.drawRoundRect(cx + 15, cy + 72, cardW - 30, 22, 6, 6);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            drawCenteredString(g, "BUY", cx + cardW/2, cy + 87);
        }
    }

    private void drawCardShop(Graphics2D g, int x, int y, int maxW, GameState state) {
        List<Card> faceUp = shop.getFaceUpCards();
        int cardW = 170, cardH = 180, gap = 15;

        // Face-up cards
        for (int i = 0; i < faceUp.size(); i++) {
            Card card = faceUp.get(i);
            int cx = x + i * (cardW + gap), cy = y;
            drawCardPanel(g, card, cx, cy, cardW, cardH, shop.getFaceUpCardCost(card), false);
        }

        // Mystery pack
        int packX = x + faceUp.size() * (cardW + gap) + 10;
        int packH = 180;
        g.setColor(new Color(20, 15, 35));
        g.fillRoundRect(packX, y, cardW, packH, 10, 10);

        // Gradient border for mystery
        g.setStroke(new BasicStroke(2));
        g.setColor(new Color(100, 50, 150));
        g.drawRoundRect(packX, y, cardW, packH, 10, 10);
        g.setStroke(new BasicStroke(1));

        g.setColor(new Color(150, 100, 200));
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 16));
        drawCenteredString(g, "MYSTERY", packX + cardW/2, y + 50);
        drawCenteredString(g, "BOOSTER", packX + cardW/2, y + 72);
        drawCenteredString(g, "PACK", packX + cardW/2, y + 94);

        int packCost = state.cardManager.getMysteryPackCost(state.roundNumber);
        g.setColor(GOLD_COLOR);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        drawCenteredString(g, packCost + " gold", packX + cardW/2, y + 120);
        g.setColor(TEXT_DIM);
        g.setFont(new Font("SansSerif", Font.ITALIC, 10));
        drawCenteredString(g, "Draft 1 of 3 cards", packX + cardW/2, y + 138);

        if (!shop.isMysteryPackPurchased()) {
            g.setColor(BTN_BG); g.fillRoundRect(packX+25, y+150, cardW-50, 24, 6, 6);
            g.setColor(new Color(100, 50, 150)); g.drawRoundRect(packX+25, y+150, cardW-50, 24, 6, 6);
            g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 11));
            drawCenteredString(g, "OPEN", packX + cardW/2, y + 166);
        } else {
            g.setColor(TEXT_DIM); g.setFont(new Font("SansSerif", Font.ITALIC, 11));
            drawCenteredString(g, "OPENED", packX + cardW/2, y + 166);
        }
    }

    private void drawCardPanel(Graphics2D g, Card card, int x, int y, int w, int h, int cost, boolean isDraft) {
        g.setColor(CARD_BG);
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(card.isGlobal() ? new Color(80, 60, 30) : CARD_BORDER);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(x, y, w, h, 10, 10);
        g.setStroke(new BasicStroke(1));

        // Type badge
        g.setColor(card.isGlobal() ? new Color(180, 140, 40) : new Color(40, 120, 180));
        g.setFont(new Font("SansSerif", Font.BOLD, 9));
        drawCenteredString(g, card.isGlobal() ? "GLOBAL" : "LOCAL", x + w/2, y + 16);

        // Name
        g.setColor(Color.WHITE);
        g.setFont(new Font("Serif", Font.BOLD, 13));
        drawCenteredString(g, card.getName(), x + w/2, y + 38);

        // Description (wrapped)
        g.setColor(TEXT_DIM);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        drawWrappedText(g, card.getDescription(), x + 8, y + 52, w - 16, 12);

        // Cost
        g.setColor(GOLD_COLOR);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        drawCenteredString(g, cost + " gold", x + w/2, y + h - 35);

        // Buy button
        g.setColor(BTN_BG); g.fillRoundRect(x+20, y+h-28, w-40, 22, 6, 6);
        g.setColor(CRIMSON); g.drawRoundRect(x+20, y+h-28, w-40, 22, 6, 6);
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 11));
        drawCenteredString(g, isDraft ? "DRAFT" : "BUY", x + w/2, y + h - 13);
    }

    private void drawActiveCards(Graphics2D g, int w, int h, GameState s) {
        int sideW = 180, sx = w - sideW - 10, sy = 60;
        g.setColor(new Color(15, 12, 20, 200));
        g.fillRoundRect(sx, sy, sideW, h - 140, 8, 8);
        g.setColor(CARD_BORDER); g.drawRoundRect(sx, sy, sideW, h - 140, 8, 8);

        g.setColor(CRIMSON_DIM); g.setFont(new Font("Serif", Font.BOLD, 14));
        drawCenteredString(g, "ACTIVE CARDS", sx + sideW/2, sy + 20);

        List<Card> active = s.cardManager.getActiveCards();
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        for (int i = 0; i < active.size() && i < 15; i++) {
            Card c = active.get(i);
            g.setColor(c.isGlobal() ? new Color(180, 140, 40) : new Color(80, 160, 220));
            g.drawString("• " + c.getName(), sx + 10, sy + 42 + i * 18);
        }
        if (active.isEmpty()) {
            g.setColor(TEXT_DIM);
            g.drawString("No cards yet", sx + 30, sy + 50);
        }
    }

    private void drawDraftOverlay(Graphics2D g, int w, int h, GameState state) {
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, w, h);

        g.setColor(new Color(100, 50, 150));
        g.setFont(new Font("Serif", Font.BOLD, 28));
        drawCenteredString(g, "— CHOOSE YOUR CARD —", w/2, 80);
        g.setColor(TEXT_DIM); g.setFont(new Font("SansSerif", Font.ITALIC, 14));
        drawCenteredString(g, "Pick 1 card to keep, or close to skip", w/2, 110);

        int cardW = 180, cardH = 200, gap = 20;
        int totalW = draftCards.size() * cardW + (draftCards.size()-1) * gap;
        int startX = (w - totalW) / 2;
        for (int i = 0; i < draftCards.size(); i++) {
            drawCardPanel(g, draftCards.get(i), startX + i*(cardW+gap), 140, cardW, cardH, 0, true);
        }

        // Skip button
        drawButton(g, "SKIP", w/2 - 60, 360, 120, 36, new Color(80, 80, 80));
    }

    private void drawButton(Graphics2D g, String text, int x, int y, int w, int h, Color accent) {
        g.setColor(BTN_BG); g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(accent); g.setStroke(new BasicStroke(2));
        g.drawRoundRect(x, y, w, h, 8, 8); g.setStroke(new BasicStroke(1));
        g.setColor(Color.WHITE); g.setFont(new Font("Serif", Font.BOLD, 16));
        drawCenteredString(g, text, x + w/2, y + h/2 + 6);
    }

    private void handleClick(int mx, int my) {
        GameState s = getEngine().getGameState();
        int w = getWidth(), h = getHeight();

        if (showingDraft) {
            int cardW = 180, cardH = 200, gap = 20;
            int totalW = draftCards.size() * cardW + (draftCards.size()-1) * gap;
            int startX = (w - totalW) / 2;
            for (int i = 0; i < draftCards.size(); i++) {
                int cx = startX + i*(cardW+gap), cy = 140;
                if (mx >= cx+20 && mx <= cx+cardW-20 && my >= cy+cardH-28 && my <= cy+cardH-6) {
                    if (shop.draftMysteryCard(draftCards.get(i), s)) {
                        AudioPlayer.playMoveSound();
                        showingDraft = false;
                    }
                    repaint(); return;
                }
            }
            // Skip button
            if (mx >= w/2-60 && mx <= w/2+60 && my >= 360 && my <= 396) {
                showingDraft = false; repaint(); return;
            }
            return;
        }

        // Piece buy buttons
        String[] items = {"Pawn", "Knight", "Bishop", "Rook", "Queen"};
        int cardW2 = 110, gap2 = 12;
        for (int i = 0; i < items.length; i++) {
            int cx = 40 + i * (cardW2 + gap2), cy = 90;
            if (mx >= cx+15 && mx <= cx+cardW2-15 && my >= cy+72 && my <= cy+94) {
                if (shop.purchase(getEngine(), items[i])) AudioPlayer.playMoveSound();
                repaint(); return;
            }
        }

        // Face-up card buy buttons
        List<Card> faceUp = shop.getFaceUpCards();
        int cardW3 = 170, cardH3 = 180, gap3 = 15;
        for (int i = 0; i < faceUp.size(); i++) {
            int cx = 40 + i * (cardW3 + gap3), cy = 270;
            if (mx >= cx+20 && mx <= cx+cardW3-20 && my >= cy+cardH3-28 && my <= cy+cardH3-6) {
                if (shop.purchaseFaceUpCard(faceUp.get(i), s)) AudioPlayer.playMoveSound();
                repaint(); return;
            }
        }

        // Mystery pack button
        int packX = 40 + faceUp.size() * (cardW3 + gap3) + 10;
        if (mx >= packX+25 && mx <= packX+cardW3-25 && my >= 420 && my <= 444) {
            if (!shop.isMysteryPackPurchased() && shop.purchaseMysteryPack(s, s.roundNumber)) {
                AudioPlayer.playMoveSound();
                draftCards = new ArrayList<>(shop.getMysteryPackCards());
                showingDraft = true;
            }
            repaint(); return;
        }

        // Continue button
        int btnW = 260, btnH = 48;
        int btnX = (w - 220) / 2 - btnW / 2 + 20, btnY = h - 70;
        if (mx >= btnX && mx <= btnX+btnW && my >= btnY && my <= btnY+btnH) {
            app.onShoppingComplete();
        }
    }

    private void drawCenteredString(Graphics2D g, String s, int cx, int cy) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s)/2, cy);
    }

    private void drawWrappedText(Graphics2D g, String text, int x, int y, int maxW, int lineH) {
        FontMetrics fm = g.getFontMetrics();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int cy = y;
        for (String word : words) {
            if (fm.stringWidth(line + " " + word) > maxW && line.length() > 0) {
                g.drawString(line.toString(), x, cy);
                line = new StringBuilder(word);
                cy += lineH;
            } else {
                if (line.length() > 0) line.append(" ");
                line.append(word);
            }
        }
        if (line.length() > 0) g.drawString(line.toString(), x, cy);
    }

    public void refresh() { repaint(); }
}
