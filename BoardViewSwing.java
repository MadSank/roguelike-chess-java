
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BoardViewSwing extends JPanel {
    private static final int SQUARE_SIZE = 80;
    private static final int BOARD_SIZE = 8 * SQUARE_SIZE;
    private RoguelikeChessAppSwing app;
    private ChessEngine engine;
    private int selectedRow = -1, selectedCol = -1;
    private List<Move> legalMoves;

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

    public BoardViewSwing(RoguelikeChessAppSwing app, ChessEngine engine) {
        this.app = app;
        this.engine = engine;
        this.legalMoves = new ArrayList<>();
        setupMouseHandlers();
        setPreferredSize(new Dimension(BOARD_SIZE + 10, BOARD_SIZE + 60));
        setBackground(BG);
    }

    private void setupMouseHandlers() {
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int row = e.getY() / SQUARE_SIZE;
                int col = e.getX() / SQUARE_SIZE;
                if (!Square.insideBoard(row, col)) return;
                handleBoardClick(row, col);
            }
        });
    }

    private void handleBoardClick(int row, int col) {
        GameState state = engine.getGameState();
        Piece clickedPiece = state.board[row][col];
        if (selectedRow == -1) {
            if (clickedPiece != null && clickedPiece.isWhite() == state.whiteToMove) {
                selectedRow = row; selectedCol = col;
                legalMoves = engine.getLegalMovesFromSquare(row, col);
                repaint();
            }
        } else {
            for (Move move : legalMoves) {
                if (move.toRow == row && move.toCol == col) {
                    app.onPlayerMove(move);
                    clearSelection();
                    return;
                }
            }
            if (clickedPiece != null && clickedPiece.isWhite() == state.whiteToMove) {
                selectedRow = row; selectedCol = col;
                legalMoves = engine.getLegalMovesFromSquare(row, col);
                repaint();
            } else { clearSelection(); }
        }
    }

    private void clearSelection() {
        selectedRow = -1; selectedCol = -1;
        legalMoves.clear(); repaint();
    }

    public void refresh() { clearSelection(); repaint(); }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        drawBoard(g);
        drawMinefields(g);
        drawStunnedOverlay(g);
        drawPieces(g);
        drawHighlights(g);
        drawVignette(g);
        drawStatusBar(g);
    }

    private void drawBoard(Graphics2D g) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Color sq = (row + col) % 2 == 0 ? LIGHT_SQ : DARK_SQ;
                g.setColor(sq);
                g.fillRect(col * SQUARE_SIZE, row * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
                // Subtle grid lines
                g.setColor(new Color(255, 255, 255, 8));
                g.drawRect(col * SQUARE_SIZE, row * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
            }
        }
    }

    private void drawMinefields(Graphics2D g) {
        GameState state = engine.getGameState();
        if (state.minefieldSquares == null || state.minefieldSquares.isEmpty()) return;
        for (int idx : state.minefieldSquares) {
            int r = idx / 8, c = idx % 8;
            g.setColor(MINE_COLOR);
            g.fillRect(c * SQUARE_SIZE, r * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
            // Skull icon
            g.setColor(new Color(200, 50, 50, 120));
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            FontMetrics fm = g.getFontMetrics();
            g.drawString("☠", c * SQUARE_SIZE + (SQUARE_SIZE - fm.stringWidth("☠"))/2,
                         r * SQUARE_SIZE + SQUARE_SIZE/2 + 7);
        }
    }

    private void drawStunnedOverlay(Graphics2D g) {
        GameState state = engine.getGameState();
        if (state.stunnedPieces == null || state.stunnedPieces.isEmpty()) return;
        for (int idx : state.stunnedPieces.keySet()) {
            int r = idx / 8, c = idx % 8;
            g.setColor(STUN_COLOR);
            g.fillRect(c * SQUARE_SIZE, r * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
            // Chain icon
            g.setColor(new Color(150, 150, 255, 180));
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.drawString("⛓", c * SQUARE_SIZE + 3, r * SQUARE_SIZE + 16);
        }
    }

    private void drawPieces(Graphics2D g) {
        GameState state = engine.getGameState();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Piece piece = state.board[row][col];
                if (piece != null) {
                    // Drop shadow
                    g.setColor(new Color(0, 0, 0, 60));
                    int sx = col * SQUARE_SIZE + 4, sy = row * SQUARE_SIZE + 4;
                    g.fillOval(sx + 10, sy + 12, SQUARE_SIZE - 20, SQUARE_SIZE - 20);
                    drawPiece(g, piece, row, col);
                }
            }
        }
    }

    private void drawPiece(Graphics2D g, Piece piece, int row, int col) {
        Image img = ResourceLoader.loadPieceImage(piece.getName(), piece.isWhite());
        if (img != null) {
            g.drawImage(img, col * SQUARE_SIZE, row * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE, null);
        } else {
            g.setColor(piece.isWhite() ? new Color(220, 215, 230) : new Color(30, 25, 40));
            g.setFont(new Font("Serif", Font.BOLD, 32));
            String symbol = Utils.getPieceSymbol(piece);
            FontMetrics fm = g.getFontMetrics();
            int x = col * SQUARE_SIZE + (SQUARE_SIZE - fm.stringWidth(symbol)) / 2;
            int y = row * SQUARE_SIZE + (SQUARE_SIZE + fm.getAscent()) / 2 - 4;
            // Text outline for contrast
            if (piece.isWhite()) {
                g.setColor(new Color(0, 0, 0, 80));
                g.drawString(symbol, x+1, y+1);
            }
            g.setColor(piece.isWhite() ? new Color(230, 225, 240) : new Color(50, 40, 65));
            g.drawString(symbol, x, y);
        }
    }

    private void drawHighlights(Graphics2D g) {
        if (selectedRow != -1 && selectedCol != -1) {
            // Crimson selection glow
            g.setColor(new Color(SELECT_GLOW.getRed(), SELECT_GLOW.getGreen(), SELECT_GLOW.getBlue(), 80));
            g.fillRect(selectedCol * SQUARE_SIZE, selectedRow * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
            g.setColor(SELECT_GLOW);
            g.setStroke(new BasicStroke(3));
            g.drawRect(selectedCol * SQUARE_SIZE + 1, selectedRow * SQUARE_SIZE + 1, SQUARE_SIZE - 2, SQUARE_SIZE - 2);
            g.setStroke(new BasicStroke(1));
        }

        for (Move move : legalMoves) {
            if (engine.getGameState().board[move.toRow][move.toCol] == null && !move.isSniperShot) {
                g.setColor(LEGAL_DOT);
                int cx = move.toCol * SQUARE_SIZE + SQUARE_SIZE/2;
                int cy = move.toRow * SQUARE_SIZE + SQUARE_SIZE/2;
                g.fillOval(cx - 8, cy - 8, 16, 16);
            } else {
                // Capture/sniper ring
                g.setColor(CAPTURE_RING);
                g.setStroke(new BasicStroke(3));
                int tx = move.isSniperShot ? move.sniperTargetCol : move.toCol;
                int ty = move.isSniperShot ? move.sniperTargetRow : move.toRow;
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
        int w = BOARD_SIZE, h = BOARD_SIZE;
        RadialGradientPaint vig = new RadialGradientPaint(
            new Point2D.Float(w/2f, h/2f), w * 0.65f,
            new float[]{0f, 0.7f, 1f},
            new Color[]{new Color(0,0,0,0), new Color(0,0,0,30), new Color(0,0,0,100)});
        g.setPaint(vig);
        g.fillRect(0, 0, w, h);
    }

    private void drawStatusBar(Graphics2D g) {
        GameState s = engine.getGameState();
        int y = BOARD_SIZE + 2;
        g.setColor(INFO_BG);
        g.fillRect(0, y, BOARD_SIZE, 58);
        g.setColor(new Color(40, 20, 30));
        g.drawLine(0, y, BOARD_SIZE, y);

        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(GOLD_COLOR);
        g.drawString("Gold: " + s.goldPlayer, 15, y + 20);
        g.setColor(CRIMSON);
        g.drawString("Round " + s.roundNumber, 150, y + 20);
        g.setColor(TEXT_DIM);
        g.drawString("Depth " + s.aiDepth, 280, y + 20);

        // Active card count
        int cardCount = s.cardManager != null ? s.cardManager.getActiveCards().size() : 0;
        g.setColor(new Color(100, 80, 150));
        g.drawString("Cards: " + cardCount, 400, y + 20);

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(TEXT_DIM);
        g.drawString(s.whiteToMove ? "Your turn" : "AI thinking...", 15, y + 42);
        if (s.gameOver) {
            g.setColor(CRIMSON);
            g.setFont(new Font("Serif", Font.BOLD, 14));
            g.drawString(s.gameResult, 150, y + 42);
        }
    }
}
