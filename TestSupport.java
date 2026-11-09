import java.util.*;

/**
 * Shared helpers for the headless test classes (Test*.java).
 * Plain Java, no JUnit, so the project still builds with "javac *.java" and opens in BlueJ.
 */
public class TestSupport {
    private static int passed = 0;
    private static int failed = 0;
    private static final List<String> failures = new ArrayList<>();

    /**
     * Builds a GameState from a FEN string (piece placement, side to move, castling ignored,
     * en passant square, optional halfmove clock). No cards are active.
     */
    public static GameState fromFen(String fen) {
        GameState s = new GameState();
        String[] parts = fen.trim().split("\\s+");
        String[] rows = parts[0].split("/");
        for (int r = 0; r < 8; r++) {
            int c = 0;
            for (char ch : rows[r].toCharArray()) {
                if (Character.isDigit(ch)) {
                    c += ch - '0';
                    continue;
                }
                boolean white = Character.isUpperCase(ch);
                Piece p;
                switch (Character.toLowerCase(ch)) {
                    case 'p': p = new Pawn(white); break;
                    case 'n': p = new Knight(white); break;
                    case 'b': p = new Bishop(white); break;
                    case 'r': p = new Rook(white); break;
                    case 'q': p = new Queen(white); break;
                    case 'k': p = new King(white); break;
                    default: throw new IllegalArgumentException("Bad FEN piece: " + ch);
                }
                s.board[r][c++] = p;
            }
        }
        s.whiteToMove = parts.length < 2 || parts[1].equals("w");
        if (parts.length > 3 && !parts[3].equals("-")) {
            s.enPassantTarget = Square.fromAlgebraic(parts[3]);
        }
        if (parts.length > 4) {
            s.halfmoveClock = Integer.parseInt(parts[4]);
        }
        return s;
    }

    /** Finds the legal move with the given UCI text (e.g. "e2e4", "a7a8n"), or null. */
    public static Move findMove(ChessEngine engine, String uci) {
        GameState s = engine.getGameState();
        for (Move m : engine.getAllLegalMoves(s.whiteToMove)) {
            if (m.toUCI().equals(uci)) return m;
        }
        return null;
    }

    /** Plays a sequence of UCI moves; fails the check and stops if one is not legal. */
    public static boolean play(ChessEngine engine, String... ucis) {
        for (String uci : ucis) {
            Move m = findMove(engine, uci);
            if (m == null || !engine.applyMove(m)) {
                check(false, "move " + uci + " should be legal");
                return false;
            }
        }
        return true;
    }

    public static void check(boolean condition, String description) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + description);
        } else {
            failed++;
            failures.add(description);
            System.out.println("  FAIL  " + description);
        }
    }

    public static void checkEquals(Object expected, Object actual, String description) {
        boolean ok = Objects.equals(expected, actual);
        check(ok, description + (ok ? "" : "  (expected " + expected + ", got " + actual + ")"));
    }

    /** Prints a summary and exits with status 1 if anything failed. */
    public static void finish(String suite) {
        System.out.println();
        System.out.println(suite + ": " + passed + " passed, " + failed + " failed");
        for (String f : failures) System.out.println("  failed: " + f);
        if (failed > 0) System.exit(1);
    }

    /** Silences the game's debug printing while a test runs. */
    public static void quiet(Runnable r) {
        java.io.PrintStream out = System.out;
        System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
        try {
            r.run();
        } finally {
            System.setOut(out);
        }
    }
}
