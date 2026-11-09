import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The chess board. Reads the current round from the RunController on every paint.
 */
public class BoardViewSwing extends JPanel {
    private static final int SQUARE_SIZE = 74; // plain board: board + status bar fit the 700 px window
    private static final int BOARD_SIZE = 8 * SQUARE_SIZE;
    /** The board area (realistic image or plain grid) is at most this wide. */
    private static final int BOARD_AREA_MAX_W = 600;
    private static final int STATUS_H = 58;

    /** The realistic board (null = draw the plain 2D board). */
    private final BoardArt art = BoardArt.get();
    // Current layout, set by layoutBoard(): board area size, a8's top-left corner and one square's size.
    private int areaW = BOARD_SIZE, areaH = BOARD_SIZE;
    private double gx0, gy0, gsw = SQUARE_SIZE, gsh = SQUARE_SIZE, artScale = 1;
    /** How long a "+N gold" pop-up stays up, in milliseconds. */
    private static final int TOAST_MS = 2200;

    private final RoguelikeChessAppSwing app;
    private int selectedRow = -1, selectedCol = -1;
    private List<Move> legalMoves = new ArrayList<>();

    /** Play buttons of the hand panel, registered while painting (same order as handCards). */
    private final List<Rectangle> handButtons = new ArrayList<>();
    private final List<Card> handCards = new ArrayList<>();

    /** Floating messages (bonus pop-ups): text and the time it appeared. */
    private final List<Object[]> toasts = new ArrayList<>();
    private final Timer toastTimer;
    /** The rendered room behind the board (falls back to plain colours if not rendered). */
    private final ScenePlayer scene;

    private static final Color DARK_SQ = new Color(26, 26, 46);
    private static final Color LIGHT_SQ = new Color(22, 33, 62);
    private static final Color SELECT_GLOW = new Color(230, 57, 70);
    private static final Color LEGAL_DOT = new Color(139, 0, 0, 140);
    private static final Color CAPTURE_RING = new Color(230, 57, 70, 160);
    private static final Color MINE_COLOR = new Color(200, 50, 50, 50);
    private static final Color STUN_COLOR = new Color(100, 100, 200, 100);
    private static final Color BG = new Color(10, 8, 15);
    private static final Color INFO_BG = new Color(15, 12, 22, 230);
    private static final Color GOLD_COLOR = new Color(212, 175, 55);
    private static final Color CRIMSON = new Color(180, 30, 40);
    private static final Color TEXT_DIM = new Color(120, 110, 130);

    public BoardViewSwing(RoguelikeChessAppSwing app) {
        this.app = app;
        setPreferredSize(new Dimension(BOARD_SIZE + 10, BOARD_SIZE + 60));
        setBackground(BG);
        scene = new ScenePlayer(this);
        scene.setView("table");
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                for (int i = 0; i < handButtons.size(); i++) {
                    if (handButtons.get(i).contains(e.getPoint())) {
                        app.onPlayCard(handCards.get(i));
                        return;
                    }
                }
                layoutBoard();
                if (e.getX() < gx0 || e.getY() < gy0) return;
                int row = (int) ((e.getY() - gy0) / gsh);
                int col = (int) ((e.getX() - gx0) / gsw);
                if (Square.insideBoard(row, col)) handleBoardClick(row, col);
            }
        });
        toastTimer = new Timer(40, e -> {
            long now = System.currentTimeMillis();
            toasts.removeIf(t -> now - (Long) t[1] > TOAST_MS);
            if (toasts.isEmpty()) ((Timer) e.getSource()).stop();
            repaint();
        });
    }

    private ChessEngine engine() {
        return app.getRun().getEngine();
    }

    /** Shows a short message over the board that fades out on its own. */
    public void showToast(String text) {
        toasts.add(new Object[]{text, System.currentTimeMillis()});
        if (!toastTimer.isRunning()) toastTimer.start();
        repaint();
    }

    private void handleBoardClick(int row, int col) {
        if (app.isAiThinking()) return;
        GameState state = engine().getGameState();
        if (state.gameOver || !state.whiteToMove) return;
        Piece clickedPiece = state.board[row][col];

        if (selectedRow != -1) {
            // Normal moves land on the clicked square; sniper shots target it from a distance.
            List<Move> matching = new ArrayList<>();
            Move snipe = null;
            for (Move move : legalMoves) {
                if (move.isSniperShot) {
                    if (move.sniperTargetRow == row && move.sniperTargetCol == col) snipe = move;
                } else if (move.toRow == row && move.toCol == col) {
                    matching.add(move);
                }
            }
            if (!matching.isEmpty() || snipe != null) {
                Move chosen;
                if (snipe != null && !matching.isEmpty()) {
                    chosen = askSnipeOrMove(snipe, matching.get(0));
                } else if (snipe != null) {
                    chosen = snipe;
                } else if (matching.size() > 1 && matching.get(0).isPromotion) {
                    chosen = askPromotion(matching);
                } else {
                    chosen = matching.get(0);
                }
                clearSelection();
                if (chosen != null) app.onPlayerMove(chosen);
                return;
            }
        }
        if (clickedPiece != null && clickedPiece.isWhite()) {
            selectedRow = row;
            selectedCol = col;
            legalMoves = engine().getLegalMovesFromSquare(row, col);
            repaint();
        } else {
            clearSelection();
        }
    }

    /** Sniper Bishops: shoot from where you stand, or move onto the square and capture. */
    private Move askSnipeOrMove(Move snipe, Move capture) {
        String[] labels = {"Snipe (stay put)", "Move and capture"};
        int pick = JOptionPane.showOptionDialog(this, "How should the bishop take this piece?", "Sniper Bishops",
            JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, labels, labels[0]);
        if (pick < 0) return null;
        return pick == 0 ? snipe : capture;
    }

    /** Lets the player pick the promotion piece; returns null if the dialog is closed. */
    private Move askPromotion(List<Move> promotions) {
        String[] labels = {"Queen", "Rook", "Bishop", "Knight"};
        String[] codes = {"Q", "R", "B", "N"};
        int pick = JOptionPane.showOptionDialog(this, "Promote pawn to:", "Promotion",
            JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, labels, labels[0]);
        if (pick < 0) return null;
        for (Move m : promotions) {
            if (codes[pick].equals(m.promotionType)) return m;
        }
        return promotions.get(0);
    }

    private void clearSelection() {
        selectedRow = -1;
        selectedCol = -1;
        legalMoves = new ArrayList<>();
        repaint();
    }

    public void refresh() {
        clearSelection();
    }

    // ─── PAINTING ───────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        GameState state = engine().getGameState();

        layoutBoard();
        // The room behind everything (if the scenes were rendered), dimmed so the board reads.
        if (scene.paint(g, getWidth(), getHeight())) {
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        drawBoard(g);
        drawMinefields(g, state);
        drawStunnedOverlay(g, state);
        drawHighlights(g, state); // on the board, under the pieces
        drawPieces(g, state);
        if (art == null) drawVignette(g);
        drawStatusBar(g, state);
        drawHandPanel(g, state);
        if (SceneClip.load("table") != null) scene.drawGrain(g, getWidth(), getHeight());
        drawToasts(g);
    }

    /** Right-hand panel: the cards in hand, each with a Play button (or ACTIVE once played). */
    private void drawHandPanel(Graphics2D g, GameState state) {
        handButtons.clear();
        handCards.clear();
        RunController run = app.getRun();
        int x = areaW + 12, w = getWidth() - x - 10;
        if (w < 120) return;
        g.setColor(new Color(15, 12, 22, 190)); // see-through: the room shows behind the hand
        g.fillRoundRect(x, 6, w, getHeight() - 12, 10, 10);
        g.setColor(new Color(80, 30, 40));
        g.drawRoundRect(x, 6, w, getHeight() - 12, 10, 10);
        g.setFont(new Font("Serif", Font.BOLD, 15));
        g.setColor(CRIMSON);
        g.drawString("HAND", x + 10, 28);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(TEXT_DIM);
        g.drawString("Playing a card is free.", x + 10, 44);
        g.drawString("It lasts until the round ends.", x + 10, 56);

        List<Card> hand = run.getHand();
        if (hand.isEmpty()) {
            g.drawString("No cards in hand.", x + 10, 80);
            return;
        }
        boolean canPlay = state.whiteToMove && !state.gameOver && !app.isAiThinking();
        int cardH = Math.min(78, (getHeight() - 76) / hand.size());
        for (int i = 0; i < hand.size(); i++) {
            Card c = hand.get(i);
            int y = 66 + i * cardH;
            boolean played = run.isPlayed(c);
            g.setColor(played ? new Color(35, 28, 45) : new Color(25, 20, 32));
            g.fillRoundRect(x + 6, y, w - 12, cardH - 6, 8, 8);
            g.setColor(rarityColour(c));
            g.drawRoundRect(x + 6, y, w - 12, cardH - 6, 8, 8);
            g.setFont(new Font("Serif", Font.BOLD, 12));
            g.drawString(c.getName(), x + 12, y + 15);
            g.setFont(new Font("SansSerif", Font.PLAIN, 9));
            g.setColor(TEXT_DIM);
            wrap(g, c.getDescription(), x + 12, y + 28, w - 24, 10, 2);

            Rectangle btn = new Rectangle(x + w - 66, y + cardH - 26, 54, 16);
            if (played) {
                g.setColor(new Color(90, 190, 110));
                g.setFont(new Font("SansSerif", Font.BOLD, 10));
                g.drawString("ACTIVE", btn.x + 8, btn.y + 12);
            } else {
                g.setColor(canPlay ? new Color(70, 30, 40) : new Color(35, 32, 38));
                g.fillRoundRect(btn.x, btn.y, btn.width, btn.height, 6, 6);
                g.setColor(canPlay ? Color.WHITE : new Color(110, 105, 115));
                g.setFont(new Font("SansSerif", Font.BOLD, 10));
                g.drawString("PLAY", btn.x + 15, btn.y + 12);
                if (canPlay) {
                    handButtons.add(btn);
                    handCards.add(c);
                }
            }
        }
    }

    private static Color rarityColour(Card c) {
        switch (c.getRarity()) {
            case RARE: return new Color(220, 120, 255);
            case UNCOMMON: return new Color(90, 170, 240);
            default: return new Color(170, 160, 150);
        }
    }

    /** Word-wraps text, up to maxLines lines. */
    private static void wrap(Graphics2D g, String text, int x, int y, int maxW, int lineH, int maxLines) {
        FontMetrics fm = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        int lines = 0;
        for (String word : text.split(" ")) {
            if (line.length() > 0 && fm.stringWidth(line + " " + word) > maxW) {
                if (++lines == maxLines) {
                    g.drawString(line + "…", x, y);
                    return;
                }
                g.drawString(line.toString(), x, y);
                y += lineH;
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
        }
        if (line.length() > 0) g.drawString(line.toString(), x, y);
    }

    // ─── BOARD GEOMETRY ─────────────────────────────────────────

    /** Works out where the board and its squares are drawn for the current panel size. */
    private void layoutBoard() {
        if (art == null) {
            areaW = areaH = BOARD_SIZE;
            gx0 = gy0 = 0;
            gsw = gsh = SQUARE_SIZE;
            return;
        }
        int maxH = Math.max(200, getHeight() - STATUS_H - 4);
        artScale = Math.min(BOARD_AREA_MAX_W / (double) art.width, maxH / (double) art.height);
        areaW = (int) Math.round(art.width * artScale);
        areaH = (int) Math.round(art.height * artScale);
        gx0 = art.squareLeft * artScale;
        gy0 = art.squareTop * artScale;
        gsw = art.squareWidth * artScale;
        gsh = art.squareHeight * artScale;
    }

    private double sqX(int col) { return gx0 + col * gsw; }

    private double sqY(int row) { return gy0 + row * gsh; }

    /** The square as a rectangle, inset by a fraction of its size. */
    private Rectangle2D squareRect(int row, int col, double inset) {
        return new Rectangle2D.Double(sqX(col) + gsw * inset, sqY(row) + gsh * inset,
            gsw * (1 - 2 * inset), gsh * (1 - 2 * inset));
    }

    /** An ellipse lying flat on the square (looks right on the tilted realistic board too). */
    private Ellipse2D squareEllipse(int row, int col, double scale) {
        double w = gsw * scale, h = gsh * scale;
        return new Ellipse2D.Double(sqX(col) + (gsw - w) / 2, sqY(row) + (gsh - h) / 2, w, h);
    }

    // ─── BOARD DRAWING ──────────────────────────────────────────

    private void drawBoard(Graphics2D g) {
        if (art != null) {
            g.drawImage(art.board(artScale), 0, 0, null);
            // Let the table around the board fall off into the room's darkness.
            float cx = (float) (gx0 + 4 * gsw), cy = (float) (gy0 + 4 * gsh);
            g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), (float) (areaW * 0.62),
                new float[]{0f, 0.62f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 60), new Color(0, 0, 0, 215)}));
            g.fillRect(0, 0, areaW, areaH);
            return;
        }
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                g.setColor((row + col) % 2 == 0 ? LIGHT_SQ : DARK_SQ);
                g.fillRect(col * SQUARE_SIZE, row * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
                g.setColor(new Color(255, 255, 255, 8));
                g.drawRect(col * SQUARE_SIZE, row * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
            }
        }
    }

    private void drawMinefields(Graphics2D g, GameState state) {
        if (state.minefieldSquares == null || state.minefieldSquares.isEmpty()) return;
        g.setFont(new Font("SansSerif", Font.BOLD, (int) Math.max(14, gsh * 0.3)));
        FontMetrics fm = g.getFontMetrics();
        for (int idx : state.minefieldSquares) {
            int r = idx / 8, c = idx % 8;
            g.setColor(MINE_COLOR);
            g.fill(squareRect(r, c, 0.0));
            g.setColor(new Color(200, 50, 50, 150));
            g.drawString("☠", (int) (sqX(c) + (gsw - fm.stringWidth("☠")) / 2), (int) (sqY(r) + gsh / 2 + fm.getAscent() / 3));
        }
    }

    private void drawStunnedOverlay(Graphics2D g, GameState state) {
        if (state.stunnedPieces == null || state.stunnedPieces.isEmpty()) return;
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        for (int idx : state.stunnedPieces.keySet()) {
            int r = idx / 8, c = idx % 8;
            g.setColor(STUN_COLOR);
            g.fill(squareRect(r, c, 0.0));
            g.setColor(new Color(150, 150, 255, 200));
            g.drawString("⛓", (int) sqX(c) + 3, (int) sqY(r) + 16);
        }
    }

    /** Pieces from the back rank forward, so nearer pieces overlap the ones behind them. */
    private void drawPieces(Graphics2D g, GameState state) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Piece piece = state.board[row][col];
                if (piece == null) continue;
                if (art != null) {
                    Image sprite = art.sprite(piece, artScale);
                    double cx = sqX(col) + gsw / 2, cy = sqY(row) + gsh / 2;
                    g.drawImage(sprite, (int) Math.round(cx - art.anchorX * artScale),
                        (int) Math.round(cy - art.anchorY * artScale), null);
                } else {
                    g.setColor(new Color(0, 0, 0, 60));
                    g.fillOval(col * SQUARE_SIZE + 14, row * SQUARE_SIZE + 16, SQUARE_SIZE - 20, SQUARE_SIZE - 20);
                    drawPlainPiece(g, piece, row, col);
                }
            }
        }
    }

    private void drawPlainPiece(Graphics2D g, Piece piece, int row, int col) {
        Image img = ResourceLoader.loadPieceImage(piece.getName(), piece.isWhite());
        if (img != null) {
            g.drawImage(img, col * SQUARE_SIZE, row * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE, null);
            return;
        }
        g.setFont(new Font("Serif", Font.BOLD, 32));
        String symbol = Utils.getPieceSymbol(piece);
        FontMetrics fm = g.getFontMetrics();
        int x = col * SQUARE_SIZE + (SQUARE_SIZE - fm.stringWidth(symbol)) / 2;
        int y = row * SQUARE_SIZE + (SQUARE_SIZE + fm.getAscent()) / 2 - 4;
        if (piece.isWhite()) {
            g.setColor(new Color(0, 0, 0, 80));
            g.drawString(symbol, x + 1, y + 1);
        }
        g.setColor(piece.isWhite() ? new Color(230, 225, 240) : new Color(50, 40, 65));
        g.drawString(symbol, x, y);
    }

    /** Selection, move dots and capture rings, drawn flat on the board under the pieces. */
    private void drawHighlights(Graphics2D g, GameState state) {
        if (selectedRow != -1) {
            g.setColor(new Color(SELECT_GLOW.getRed(), SELECT_GLOW.getGreen(), SELECT_GLOW.getBlue(), 90));
            g.fill(squareRect(selectedRow, selectedCol, 0.0));
            g.setColor(SELECT_GLOW);
            g.setStroke(new BasicStroke(3));
            g.draw(squareRect(selectedRow, selectedCol, 0.02));
            g.setStroke(new BasicStroke(1));
        }
        for (Move move : legalMoves) {
            if (state.board[move.toRow][move.toCol] == null && !move.isSniperShot) {
                g.setColor(LEGAL_DOT);
                g.fill(squareEllipse(move.toRow, move.toCol, 0.24));
            } else {
                int tx = move.isSniperShot ? move.sniperTargetCol : move.toCol;
                int ty = move.isSniperShot ? move.sniperTargetRow : move.toRow;
                g.setColor(CAPTURE_RING);
                g.setStroke(new BasicStroke(3));
                g.draw(squareEllipse(ty, tx, 0.88));
                g.setStroke(new BasicStroke(1));
                if (move.isSniperShot) {
                    g.setFont(new Font("SansSerif", Font.BOLD, 12));
                    g.setColor(new Color(230, 57, 70));
                    g.drawString("⊕", (int) sqX(tx) + 4, (int) sqY(ty) + 14);
                }
            }
        }
    }

    private void drawVignette(Graphics2D g) {
        RadialGradientPaint vig = new RadialGradientPaint(
            new Point2D.Float(BOARD_SIZE / 2f, BOARD_SIZE / 2f), BOARD_SIZE * 0.65f,
            new float[]{0f, 0.7f, 1f},
            new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 30), new Color(0, 0, 0, 100)});
        g.setPaint(vig);
        g.fillRect(0, 0, BOARD_SIZE, BOARD_SIZE);
    }

    private void drawStatusBar(Graphics2D g, GameState s) {
        RunController run = app.getRun();
        int y = areaH + 2;
        g.setColor(INFO_BG);
        g.fillRect(0, y, areaW, STATUS_H);
        g.setColor(new Color(40, 20, 30));
        g.drawLine(0, y, areaW, y);

        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(GOLD_COLOR);
        g.drawString("Gold: " + run.getGold(), 15, y + 20);
        g.setColor(CRIMSON);
        g.drawString("Level " + run.getLevel(), 150, y + 20);
        g.setColor(TEXT_DIM);
        g.drawString("AI depth " + s.aiDepth, 260, y + 20);
        int cardCount = s.cardManager != null ? s.cardManager.getActiveCards().size() : 0;
        g.setColor(new Color(100, 80, 150));
        g.drawString("Cards: " + cardCount, 400, y + 20);

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(TEXT_DIM);
        String turn = s.gameOver ? "" : (app.isAiThinking() || !s.whiteToMove ? "AI thinking..." : "Your turn");
        g.drawString(turn, 15, y + 42);
        if (s.gameOver) {
            g.setColor(CRIMSON);
            g.setFont(new Font("Serif", Font.BOLD, 14));
            g.drawString(s.gameResult, 150, y + 42);
        }
    }

    private void drawToasts(Graphics2D g) {
        long now = System.currentTimeMillis();
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        FontMetrics fm = g.getFontMetrics();
        int i = 0;
        for (Object[] t : toasts) {
            String text = (String) t[0];
            float age = (now - (Long) t[1]) / (float) TOAST_MS;
            int alpha = (int) (255 * Math.max(0f, 1f - age));
            int x = (areaW - fm.stringWidth(text)) / 2;
            int y = areaH / 2 - 40 + i * 30 - (int) (age * 40); // drift upwards
            g.setColor(new Color(0, 0, 0, alpha / 2));
            g.fillRoundRect(x - 12, y - fm.getAscent(), fm.stringWidth(text) + 24, fm.getHeight() + 4, 12, 12);
            g.setColor(new Color(GOLD_COLOR.getRed(), GOLD_COLOR.getGreen(), GOLD_COLOR.getBlue(), alpha));
            g.drawString(text, x, y);
            i++;
        }
    }
}
