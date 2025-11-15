package ashes;

import java.util.*;

public class Chess960Setup {
    private Random rng;

    public Chess960Setup() {
        this.rng = new Random();
    }

    public Chess960Setup(long seed) {
        this.rng = new Random(seed);
    }

    public void setupCustomChess960Board(GameState state, List<Piece> purchasedPieces) {
        System.out.println("=== CHESS960 SETUP START ===");
        System.out.println("Purchased pieces received: " + purchasedPieces.size());
        for (Piece p : purchasedPieces) {
            System.out.println("  - " + p.getName());
        }

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                state.board[r][c] = null;
            }
        }

        List<Piece> whitePieces = new ArrayList<>();
        whitePieces.add(new King(true));

        if (purchasedPieces != null) {
            whitePieces.addAll(purchasedPieces);
        }

        System.out.println("Total white pieces (including king): " + whitePieces.size());

        setupSideWithPawnProtection(state, whitePieces, true);

        setupBlackSide(state);

        state.whiteToMove = true;
        state.gameOver = false;
        state.gameResult = "";
        state.movesToEnd = 0;
        state.capturesMade = 0;
        state.specialMovesUsed = 0;
        state.piecesLeftStanding = countPiecesOnBoard(state);
        state.enPassantTarget = null;

        state.whiteKingMoved = false;
        state.whiteKingsideRookMoved = false;
        state.whiteQueensideRookMoved = false;
        state.blackKingMoved = false;
        state.blackKingsideRookMoved = false;
        state.blackQueensideRookMoved = false;

        System.out.println("=== CHESS960 SETUP COMPLETE ===");
        System.out.println("Total pieces on board: " + state.piecesLeftStanding);
    }

    private void setupSideWithPawnProtection(GameState state, List<Piece> pieces, boolean white) {
        int backRank = white ? 7 : 0;
        int pawnRank = white ? 6 : 1;

        System.out.println("Setting up " + (white ? "white" : "black") + " side with " + pieces.size() + " pieces");

        List<Piece> pawns = new ArrayList<>();
        List<Piece> rooks = new ArrayList<>();
        List<Piece> queens = new ArrayList<>();
        List<Piece> otherPieces = new ArrayList<>();
        Piece king = null;

        for (Piece p : pieces) {
            if (p instanceof Pawn) pawns.add(p);
            else if (p instanceof Rook) rooks.add(p);
            else if (p instanceof Queen) queens.add(p);
            else if (p instanceof King) king = p;
            else otherPieces.add(p);
        }

        System.out.println("  Pawns: " + pawns.size());
        System.out.println("  Rooks: " + rooks.size());
        System.out.println("  Queens: " + queens.size());
        System.out.println("  Others: " + otherPieces.size());
        System.out.println("  King: " + (king != null ? "YES" : "NO"));

        Set<Integer> pawnColumns = new HashSet<>();

        List<Integer> availablePawnCols = new ArrayList<>();
        for (int i = 0; i < 8; i++) availablePawnCols.add(i);
        Collections.shuffle(availablePawnCols, rng);

        int pawnsToPlace = Math.min(pawns.size(), 8);
        for (int i = 0; i < pawnsToPlace; i++) {
            int col = availablePawnCols.get(i);
            state.board[pawnRank][col] = pawns.get(i);
            pawnColumns.add(col);
            System.out.println("  Placed pawn at " + (white ? "rank 2" : "rank 7") + ", file " + (char)('a' + col));
        }

        List<Integer> availableBackCols = new ArrayList<>();
        for (int i = 0; i < 8; i++) availableBackCols.add(i);
        Collections.shuffle(availableBackCols, rng);

        List<Integer> protectedCols = new ArrayList<>(pawnColumns);
        Collections.shuffle(protectedCols, rng);

        for (Piece rook : rooks) {
            if (!protectedCols.isEmpty()) {
                int col = protectedCols.remove(0);
                state.board[backRank][col] = rook;
                availableBackCols.remove(Integer.valueOf(col));
                System.out.println("  Placed rook at " + (white ? "rank 1" : "rank 8") + ", file " + (char)('a' + col));
            } else {
                System.out.println("  WARNING: No protected column for rook!");
            }
        }

        for (Piece queen : queens) {
            if (!protectedCols.isEmpty()) {
                int col = protectedCols.remove(0);
                state.board[backRank][col] = queen;
                availableBackCols.remove(Integer.valueOf(col));
                System.out.println("  Placed queen at " + (white ? "rank 1" : "rank 8") + ", file " + (char)('a' + col));
            } else {
                System.out.println("  WARNING: No protected column for queen!");
            }
        }

        if (king != null && availableBackCols.size() >= 1) {
            Collections.sort(availableBackCols);
            int kingPos = availableBackCols.get(availableBackCols.size() / 2);
            state.board[backRank][kingPos] = king;
            availableBackCols.remove(Integer.valueOf(kingPos));
            System.out.println("  Placed king at " + (white ? "rank 1" : "rank 8") + ", file " + (char)('a' + kingPos));
        }

        for (Piece piece : otherPieces) {
            if (!availableBackCols.isEmpty()) {
                int col = availableBackCols.remove(0);
                state.board[backRank][col] = piece;
                System.out.println("  Placed " + piece.getName() + " at " + (white ? "rank 1" : "rank 8") + ", file " + (char)('a' + col));
            }
        }
    }

    private void setupBlackSide(GameState state) {
        System.out.println("Setting up black side (mirroring white)");

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Piece whitePiece = state.board[row][col];
                if (whitePiece != null && whitePiece.isWhite()) {
                    int blackRow = 7 - row;
                    int blackCol = col;

                    if (state.board[blackRow][blackCol] == null) {
                        Piece blackPiece = createMirroredPiece(whitePiece);
                        state.board[blackRow][blackCol] = blackPiece;
                        System.out.println("  Mirrored " + blackPiece.getName() + " at rank " + (8 - blackRow) + ", file " + (char)('a' + blackCol));
                    }
                }
            }
        }

        boolean hasBlackKing = false;
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Piece p = state.board[row][col];
                if (p instanceof King && !p.isWhite()) {
                    hasBlackKing = true;
                    break;
                }
            }
        }

        if (!hasBlackKing) {
            System.out.println("  WARNING: Black king missing! Adding to back rank...");
            for (int col : new int[]{4, 3, 5, 2, 6}) {
                if (state.board[0][col] == null) {
                    state.board[0][col] = new King(false);
                    System.out.println("  Placed black king at rank 8, file " + (char)('a' + col));
                    break;
                }
            }
        }
    }

    private Piece createMirroredPiece(Piece whitePiece) {
        if (whitePiece instanceof Pawn) return new Pawn(false);
        if (whitePiece instanceof Knight) return new Knight(false);
        if (whitePiece instanceof Bishop) return new Bishop(false);
        if (whitePiece instanceof Rook) return new Rook(false);
        if (whitePiece instanceof Queen) return new Queen(false);
        if (whitePiece instanceof King) return new King(false);
        return null;
    }

    public String getPositionDescription(GameState state) {
        int whitePieces = 0;
        int blackPieces = 0;
        int whiteRooksQueens = 0;
        int whitePawns = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p != null) {
                    if (p.isWhite()) {
                        whitePieces++;
                        if (p instanceof Pawn) whitePawns++;
                        if (p instanceof Rook || p instanceof Queen) whiteRooksQueens++;
                    } else {
                        blackPieces++;
                    }
                }
            }
        }
        return String.format("Chess960 Setup: %d white, %d black pieces (%d pawns protecting %d rooks/queens)",
            whitePieces, blackPieces, whitePawns, whiteRooksQueens);
    }

    private int countPiecesOnBoard(GameState state) {
        int count = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (state.board[r][c] != null) count++;
            }
        }
        return count;
    }
}