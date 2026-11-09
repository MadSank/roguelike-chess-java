/**
 * Small board helpers shared by the engine and the UI.
 */
public final class Utils {
    private Utils() {}

    /** Unicode chess symbol, used when no piece images are available. */
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
        return symbols[5];
    }

    /** Number of pieces of one colour on the board. */
    public static int countPieces(GameState state, boolean white) {
        int count = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p != null && p.isWhite() == white) count++;
            }
        }
        return count;
    }

    /** Number of pieces of both colours on the board. */
    public static int countPiecesOnBoard(GameState state) {
        return countPieces(state, true) + countPieces(state, false);
    }
}
