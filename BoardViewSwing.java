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
    private static final int SQUARE_SIZE = 80;
    private static final int BOARD_SIZE = 8 * SQUARE_SIZE;
    /** How long a "+N gold" pop-up stays up, in milliseconds. */
    private static final int TOAST_MS = 2200;

    private final RoguelikeChessAppSwing app;
    private int selectedRow = -1, selectedCol = -1;
    private List<Move> legalMoves = new ArrayList<>();

    /** Floating messages (bonus pop-ups): text and the time it appeared. */
    private final List<Object[]> toasts = new ArrayList<>();
    private final Timer toastTimer;

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
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int row = e.getY() / SQUARE_SIZE;
                int col = e.getX() / SQUARE_SIZE;
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

        drawBoard(g);
        drawMinefields(g, state);
        drawStunnedOverlay(g, state);
        drawPieces(g, state);
        drawHighlights(g, state);
        drawVignette(g);
        drawStatusBar(g, state);
        drawToasts(g);
    }

    private void drawBoard(Graphics2D g) {
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
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        FontMetrics fm = g.getFontMetrics();
        for (int idx : state.minefieldSquares) {
            int r = idx / 8, c = idx % 8;
            g.setColor(MINE_COLOR);
            g.fillRect(c * SQUARE_SIZE, r * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
            g.setColor(new Color(200, 50, 50, 120));
            g.drawString("☠", c * SQUARE_SIZE + (SQUARE_SIZE - fm.stringWidth("☠")) / 2,
                         r * SQUARE_SIZE + SQUARE_SIZE / 2 + 7);
        }
    }

    private void drawStunnedOverlay(Graphics2D g, GameState state) {
        if (state.stunnedPieces == null || state.stunnedPieces.isEmpty()) return;
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        for (int idx : state.stunnedPieces.keySet()) {
            int r = idx / 8, c = idx % 8;
            g.setColor(STUN_COLOR);
            g.fillRect(c * SQUARE_SIZE, r * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
            g.setColor(new Color(150, 150, 255, 180));
            g.drawString("⛓", c * SQUARE_SIZE + 3, r * SQUARE_SIZE + 16);
        }
    }

    private void drawPieces(Graphics2D g, GameState state) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Piece piece = state.board[row][col];
                if (piece == null) continue;
                g.setColor(new Color(0, 0, 0, 60));
                g.fillOval(col * SQUARE_SIZE + 14, row * SQUARE_SIZE + 16, SQUARE_SIZE - 20, SQUARE_SIZE - 20);
                drawPiece(g, piece, row, col);
            }
        }
    }

    private void drawPiece(Graphics2D g, Piece piece, int row, int col) {
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

    private void drawHighlights(Graphics2D g, GameState state) {
        if (selectedRow != -1) {
            g.setColor(new Color(SELECT_GLOW.getRed(), SELECT_GLOW.getGreen(), SELECT_GLOW.getBlue(), 80));
            g.fillRect(selectedCol * SQUARE_SIZE, selectedRow * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
            g.setColor(SELECT_GLOW);
            g.setStroke(new BasicStroke(3));
            g.drawRect(selectedCol * SQUARE_SIZE + 1, selectedRow * SQUARE_SIZE + 1, SQUARE_SIZE - 2, SQUARE_SIZE - 2);
            g.setStroke(new BasicStroke(1));
        }
        for (Move move : legalMoves) {
            if (state.board[move.toRow][move.toCol] == null && !move.isSniperShot) {
                g.setColor(LEGAL_DOT);
                int cx = move.toCol * SQUARE_SIZE + SQUARE_SIZE / 2;
                int cy = move.toRow * SQUARE_SIZE + SQUARE_SIZE / 2;
                g.fillOval(cx - 8, cy - 8, 16, 16);
            } else {
                int tx = move.isSniperShot ? move.sniperTargetCol : move.toCol;
                int ty = move.isSniperShot ? move.sniperTargetRow : move.toRow;
                g.setColor(CAPTURE_RING);
                g.setStroke(new BasicStroke(3));
                g.drawOval(tx * SQUARE_SIZE + 4, ty * SQUARE_SIZE + 4, SQUARE_SIZE - 8, SQUARE_SIZE - 8);
                g.setStroke(new BasicStroke(1));
                if (move.isSniperShot) {
                    g.setFont(new Font("SansSerif", Font.BOLD, 10));
                    g.setColor(new Color(230, 57, 70));
                    g.drawString("⊕", tx * SQUARE_SIZE + 5, ty * SQUARE_SIZE + 15);
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
        int y = BOARD_SIZE + 2;
        g.setColor(INFO_BG);
        g.fillRect(0, y, BOARD_SIZE, 58);
        g.setColor(new Color(40, 20, 30));
        g.drawLine(0, y, BOARD_SIZE, y);

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
            int x = (BOARD_SIZE - fm.stringWidth(text)) / 2;
            int y = BOARD_SIZE / 2 - 40 + i * 30 - (int) (age * 40); // drift upwards
            g.setColor(new Color(0, 0, 0, alpha / 2));
            g.fillRoundRect(x - 12, y - fm.getAscent(), fm.stringWidth(text) + 24, fm.getHeight() + 4, 12, 12);
            g.setColor(new Color(GOLD_COLOR.getRed(), GOLD_COLOR.getGreen(), GOLD_COLOR.getBlue(), alpha));
            g.drawString(text, x, y);
            i++;
        }
    }
}
