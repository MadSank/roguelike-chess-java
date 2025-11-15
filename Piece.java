package ashes;

import java.io.Serializable;
import java.util.*;

public abstract class Piece implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;

    protected boolean white;
    protected String name;
    protected int baseValue;
    
    public Piece(boolean white, String name, int baseValue) {
        this.white = white;
        this.name = name;
        this.baseValue = baseValue;
    }

    public boolean isWhite() { return white; }

    public void setWhite(boolean w) { this.white = w; }

    public String getName() { return name; }

    public int getValue() { return baseValue; }

    public abstract List<Move> generateLegalMoves(int row, int col, ChessEngine engine);

    @Override
    public String toString() {
        return (white ? "W" : "B") + name.charAt(0);
    }

    @Override
    public Piece clone() {
        try {
            Piece p = (Piece) super.clone();
            return p;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    protected boolean tryAddMove(List<Move> moves, GameState state, int fromRow, int fromCol, int toRow, int toCol) {
        if (!Square.insideBoard(toRow, toCol)) return false;
        Piece target = state.board[toRow][toCol];
        if (target == null) {
            moves.add(new Move(fromRow, fromCol, toRow, toCol));
            return true;
        } else if (target.isWhite() != this.white) {
            Move m = new Move(fromRow, fromCol, toRow, toCol);
            m.isCapture = true;
            moves.add(m);
            return false;
        } else {
            return false;
        }
    }

    protected void addSlidingMoves(List<Move> moves, GameState state, int fromRow, int fromCol, int[][] directions) {
        for (int[] dir : directions) {
            int r = fromRow + dir[0];
            int c = fromCol + dir[1];
            while (Square.insideBoard(r, c)) {
                if (!tryAddMove(moves, state, fromRow, fromCol, r, c)) break;
                r += dir[0];
                c += dir[1];
            }
        }
    }

    protected void addJumpMoves(List<Move> moves, GameState state, int fromRow, int fromCol, int[][] offsets) {
        for (int[] off : offsets) {
            int r = fromRow + off[0];
            int c = fromCol + off[1];
            if (!Square.insideBoard(r, c)) continue;
            Piece target = state.board[r][c];
            if (target == null || target.isWhite() != this.white) {
                Move m = new Move(fromRow, fromCol, r, c);
                if (target != null) m.isCapture = true;
                moves.add(m);
            }
        }
    }
    
    
    protected boolean isFriendlyAt(GameState state, int r, int c) {
        if (!Square.insideBoard(r, c)) return false;
        Piece p = state.board[r][c];
        return (p != null && p.isWhite() == this.white);
    }

    protected boolean isEnemyAt(GameState state, int r, int c) {
        if (!Square.insideBoard(r, c)) return false;
        Piece p = state.board[r][c];
        return (p != null && p.isWhite() != this.white);
    }
}