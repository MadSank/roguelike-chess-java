import java.util.*;

/**
 * Places the player's army on a random back-rank setup and mirrors it for the AI.
 * Pawns go on random files of the second rank; Rooks and Queens prefer files with a pawn
 * in front of them; the King goes in the middle of the free squares.
 * No piece is ever dropped: every piece in the army gets a square.
 */
public class Chess960Setup {
    private final Random rng;

    public Chess960Setup() {
        this.rng = new Random();
    }

    public Chess960Setup(long seed) {
        this.rng = new Random(seed);
    }

    /**
     * Clears the board, places White's King plus the given army, mirrors it for Black and
     * resets the per-game counters.
     */
    public void setupCustomChess960Board(GameState state, List<Piece> army) {
        for (int r = 0; r < 8; r++) {
            Arrays.fill(state.board[r], null);
        }
        placeWhite(state, army != null ? army : Collections.emptyList());
        mirrorForBlack(state);

        state.whiteToMove = true;
        state.setResult(GameResult.ONGOING, "");
        state.movesToEnd = 0;
        state.capturesMade = 0;
        state.specialMovesUsed = 0;
        state.enPassantTarget = null;
        state.halfmoveClock = 0;
        state.positionHistory.clear();
        state.moveHistory.clear();
        state.piecesLeftStanding = Utils.countPiecesOnBoard(state);
    }

    private void placeWhite(GameState state, List<Piece> army) {
        final int backRank = 7;
        final int pawnRank = 6;

        List<Piece> pawns = new ArrayList<>();
        List<Piece> heavy = new ArrayList<>();  // rooks and queens: like a pawn shield
        List<Piece> others = new ArrayList<>(); // knights and bishops
        for (Piece p : army) {
            if (p instanceof King) continue;
            if (p instanceof Pawn) pawns.add(p);
            else if (p instanceof Rook || p instanceof Queen) heavy.add(p);
            else others.add(p);
        }

        List<Integer> files = shuffledFiles();
        List<Integer> pawnFiles = new ArrayList<>();
        for (int i = 0; i < Math.min(pawns.size(), 8); i++) {
            int col = files.get(i);
            state.board[pawnRank][col] = pawns.get(i);
            pawnFiles.add(col);
        }

        List<Integer> freeBack = shuffledFiles();
        Collections.shuffle(pawnFiles, rng);

        // Rooks and queens behind pawns where possible, otherwise anywhere free.
        for (Piece p : heavy) {
            Integer col = null;
            for (Iterator<Integer> it = pawnFiles.iterator(); it.hasNext(); ) {
                Integer f = it.next();
                it.remove();
                if (freeBack.contains(f)) {
                    col = f;
                    break;
                }
            }
            if (col == null && !freeBack.isEmpty()) col = freeBack.get(0);
            if (col == null) break; // board full (army limits prevent this)
            state.board[backRank][col] = p;
            freeBack.remove(col);
        }

        // King in the middle of what's left.
        if (!freeBack.isEmpty()) {
            List<Integer> sorted = new ArrayList<>(freeBack);
            Collections.sort(sorted);
            Integer kingCol = sorted.get(sorted.size() / 2);
            state.board[backRank][kingCol] = new King(true);
            freeBack.remove(kingCol);
        }

        for (Piece p : others) {
            if (freeBack.isEmpty()) break;
            state.board[backRank][freeBack.remove(0)] = p;
        }
    }

    /** Black gets a mirror image of White's army. */
    private void mirrorForBlack(GameState state) {
        for (int row = 4; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Piece w = state.board[row][col];
                if (w != null && w.isWhite()) {
                    Piece b = w.clone();
                    b.setWhite(false);
                    state.board[7 - row][col] = b;
                }
            }
        }
    }

    private List<Integer> shuffledFiles() {
        List<Integer> files = new ArrayList<>();
        for (int i = 0; i < 8; i++) files.add(i);
        Collections.shuffle(files, rng);
        return files;
    }
}
