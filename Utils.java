package ashes;

import java.io.*;
public class Utils {
    public static int calculateEarnings(GameState state) {
        int baseGold = state.capturesMade * 5 + state.specialMovesUsed * 3 + state.piecesLeftStanding * 2;
        int moveBonus = 0;
        if (state.movesToEnd <= 10) {
            moveBonus = 50;
        } else if (state.movesToEnd <= 20) {
            moveBonus = 30;
        } else if (state.movesToEnd <= 30) {
            moveBonus = 15;
        } else if (state.movesToEnd <= 50) {
            moveBonus = 5;
        }

        int roundBonus = state.roundNumber * 10;
        int totalEarnings = baseGold + moveBonus + roundBonus;
        System.out.println("Earnings breakdown: base=" + baseGold +
            ", move=" + moveBonus +
            ", round=" + roundBonus +
            ", total=" + totalEarnings);
        return Math.max(10, totalEarnings);
    }

    public static String formatGold(int gold) {
        return String.format("%,d", gold);
    }

    public static String toAlgebraic(int row, int col) {
        char file = (char) ('a' + col);
        int rank = 8 - row;
        return "" + file + rank;
    }

    public static int[] fromAlgebraic(String alg) {
        if (alg == null || alg.length() < 2) return null;
        alg = alg.trim().toLowerCase();
        char file = alg.charAt(0);
        char rank = alg.charAt(1);
        if (file < 'a' || file > 'h') return null;
        if (rank < '1' || rank > '8') return null;
        int col = file - 'a';
        int row = 8 - (rank - '0');
        return new int[] { row, col };
    }

    public static String getResourcePath(String relativePath) {
        return "resources/" + relativePath;
    }

    public static boolean resourceExists(String relativePath) {
        try {
            File f = new File(getResourcePath(relativePath));
            return f.exists();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean saveGame(GameState state, String filename) {
        try {
            FileOutputStream fos = new FileOutputStream(filename);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(state);
            oos.close();
            fos.close();
            return true;
        } catch (Exception e) {
            System.err.println("Save failed: " + e.getMessage());
            return false;
        }
    }

    public static GameState loadGame(String filename) {
        try {
            FileInputStream fis = new FileInputStream(filename);
            ObjectInputStream ois = new ObjectInputStream(fis);
            GameState state = (GameState) ois.readObject();
            ois.close();
            fis.close();
            return state;
        } catch (Exception e) {
            System.err.println("Load failed: " + e.getMessage());
            return null;
        }
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static String formatMoves(int ply) {
        int fullMoves = (ply + 1) / 2;
        return fullMoves + " move" + (fullMoves == 1 ? "" : "s");
    }

    public static String getPieceSymbol(Piece piece) {
        if (piece == null) return " ";
        String[] whiteSymbols = {"♔", "♕", "♖", "♗", "♘", "♙"};
        String[] blackSymbols = {"♚", "♛", "♜", "♝", "♞", "♟"};
        String[] symbols = piece.isWhite() ? whiteSymbols : blackSymbols;
        if (piece instanceof King) return symbols[0];
        if (piece instanceof Queen) return symbols[1];
        if (piece instanceof Rook) return symbols[2];
        if (piece instanceof Bishop) return symbols[3];
        if (piece instanceof Knight) return symbols[4];
        if (piece instanceof Pawn) return symbols[5];
        return "?";
    }

    public static int chebyshevDistance(int r1, int c1, int r2, int c2) {
        return Math.max(Math.abs(r2 - r1), Math.abs(c2 - c1));
    }

    public static int manhattanDistance(int r1, int c1, int r2, int c2) {
        return Math.abs(r2 - r1) + Math.abs(c2 - c1);
    }

    public static boolean inBounds(int row, int col) {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }

    public static Piece[][] copyBoard(Piece[][] original) {
        Piece[][] copy = new Piece[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (original[i][j] != null) {
                    copy[i][j] = original[i][j].clone();
                }
            }
        }
        return copy;
    }

    public static int countPieces(GameState state, boolean white) {
        int count = 0;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                Piece piece = state.board[i][j];
                if (piece != null && piece.isWhite() == white) {
                    count++;
                }
            }
        }
        return count;
    }

    public static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}