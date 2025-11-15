package ashes;

import java.io.Serializable;
import java.util.Objects;

public final class Square implements Comparable<Square>, Serializable {
    private static final long serialVersionUID = 1L;
    public final int row;
    public final int col;

    public Square(int row, int col) {
        if (!insideBoard(row, col)) {
            throw new IllegalArgumentException("Square coordinates out of bounds: row=" + row + " col=" + col);
        }
        this.row = row;
        this.col = col;
    }

    public static Square fromAlgebraic(String alg) {
        if (alg == null) return null;
        alg = alg.trim().toLowerCase();
        if (alg.length() < 2 || alg.length() > 3) return null;
        char file = alg.charAt(0);
        char rank = alg.charAt(1);
        if (file < 'a' || file > 'h') return null;
        if (rank < '1' || rank > '8') return null;
        int col = file - 'a';
        int rankNum = rank - '1' + 1;
        int row = 8 - rankNum;
        return new Square(row, col);
    }

    public String toAlgebraic() {
        char file = (char) ('a' + col);
        int rank = 8 - row;
        return "" + file + rank;
    }

    public static boolean insideBoard(int row, int col) {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }

    public int manhattanDistance(Square other) {
        return Math.abs(this.row - other.row) + Math.abs(this.col - other.col);
    }

    public int chebyshevDistance(Square other) {
        return Math.max(Math.abs(this.row - other.row), Math.abs(this.col - other.col));
    }

    public int fileDelta(Square other) {
        return other.col - this.col;
    }

    public int rankDelta(Square other) {
        return (8 - other.row) - (8 - this.row);
    }

    @Override
    public int compareTo(Square o) {
        if (o == null) throw new NullPointerException();
        if (this.row != o.row) return Integer.compare(this.row, o.row);
        return Integer.compare(this.col, o.col);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Square)) return false;
        Square square = (Square) o;
        return row == square.row && col == square.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col);
    }

    @Override
    public String toString() {
        return "Square{" + toAlgebraic() + " r=" + row + " c=" + col + "}";
    }

    public static Square a1() { return new Square(7, 0); }

    public static Square h8() { return new Square(0, 7); }

    public static Square[] fromUCI(String uci) {
        if (uci == null || uci.length() < 4) return null;

        try {
            String from = uci.substring(0, 2);
            String to = uci.substring(2, 4);

            Square fromSquare = fromAlgebraic(from);
            Square toSquare = fromAlgebraic(to);

            if (fromSquare == null || toSquare == null) return null;

            return new Square[]{fromSquare, toSquare};
        } catch (Exception e) {
            return null;
        }
    }
}