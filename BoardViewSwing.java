package ashes;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class BoardViewSwing extends JPanel {
    private static final int SQUARE_SIZE = 80;
    private static final int BOARD_SIZE = 8 * SQUARE_SIZE;
    private RoguelikeChessAppSwing app;
    private ChessEngine engine;
    private int selectedRow = -1;
    private int selectedCol = -1;
    private List<Move> legalMoves;
    public BoardViewSwing(RoguelikeChessAppSwing app, ChessEngine engine) {
        this.app = app;
        this.engine = engine;
        this.legalMoves = new ArrayList<>();
        setupMouseHandlers();
        setPreferredSize(new Dimension(BOARD_SIZE, BOARD_SIZE));
        setBackground(Color.DARK_GRAY);
    }

    private void setupMouseHandlers() {
        addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
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
                selectedRow = row;
                selectedCol = col;
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
                selectedRow = row;
                selectedCol = col;
                legalMoves = engine.getLegalMovesFromSquare(row, col);
                repaint();
            } else {
                clearSelection();
            }
        }
    }

    private void clearSelection() {
        selectedRow = -1;
        selectedCol = -1;
        legalMoves.clear();
        repaint();
    }

    public void refresh() {
        clearSelection();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        drawBoard(g);
        drawPieces(g);
        drawHighlights(g);
    }

    private void drawBoard(Graphics g) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Color squareColor = (row + col) % 2 == 0 ?
                        new Color(240, 217, 181) :
                    new Color(181, 136, 99);
                g.setColor(squareColor);
                g.fillRect(col * SQUARE_SIZE, row * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
            }
        }
    }

    private void drawPieces(Graphics g) {
        GameState state = engine.getGameState();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Piece piece = state.board[row][col];
                if (piece != null) {
                    drawPiece(g, piece, row, col);
                }
            }
        }
    }

    private void drawPiece(Graphics g, Piece piece, int row, int col) {
        Image img = ResourceLoader.loadPieceImage(piece.getName(), piece.isWhite());
        if (img != null) {
            g.drawImage(img,
                col * SQUARE_SIZE, row * SQUARE_SIZE,
                SQUARE_SIZE, SQUARE_SIZE, null);
        } else {
            g.setColor(piece.isWhite() ? Color.WHITE : Color.BLACK);
            g.setFont(new Font("Arial", Font.BOLD, 24));
            String symbol = Utils.getPieceSymbol(piece);
            FontMetrics fm = g.getFontMetrics();
            int x = col * SQUARE_SIZE + (SQUARE_SIZE - fm.stringWidth(symbol)) / 2;
            int y = row * SQUARE_SIZE + (SQUARE_SIZE + fm.getAscent()) / 2;
            g.drawString(symbol, x, y);
        }
    }

    private void drawHighlights(Graphics g) {
        if (selectedRow != -1 && selectedCol != -1) {
            g.setColor(new Color(0, 255, 0, 100));
            g.fillRect(selectedCol * SQUARE_SIZE, selectedRow * SQUARE_SIZE,
                SQUARE_SIZE, SQUARE_SIZE);
        }
        g.setColor(new Color(0, 255, 0, 70));
        for (Move move : legalMoves) {
            if (engine.getGameState().board[move.toRow][move.toCol] == null) {
                g.fillOval(move.toCol * SQUARE_SIZE + SQUARE_SIZE/4,
                    move.toRow * SQUARE_SIZE + SQUARE_SIZE/4,
                    SQUARE_SIZE/2, SQUARE_SIZE/2);
            } else {
                g.drawRect(move.toCol * SQUARE_SIZE + 2, move.toRow * SQUARE_SIZE + 2,
                    SQUARE_SIZE - 4, SQUARE_SIZE - 4);
            }
        }
    }
}