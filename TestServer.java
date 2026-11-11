import java.util.Random;

/**
 * Checks GameServer (the rules as the 3D front end sees them): a whole run driven only by its
 * text commands, with every answer checked for shape. Plain class with a main, like the other tests.
 */
public class TestServer {
    public static void main(String[] args) throws Exception {
        System.out.println("== TestServer ==");
        TestSupport.quiet(() -> {});
        GameServer server = new GameServer();
        Random rng = new Random(7);

        String s = server.state(server.handle("state"));
        TestSupport.check(s.contains("\"phase\":\"MENU\""), "a fresh server is at the menu");
        TestSupport.check(balanced(s), "the menu answer is well-formed JSON");

        s = server.state(server.handle("new Tester"));
        TestSupport.check(s.contains("\"phase\":\"PLAYING\"") && s.contains("\"board\":[["), "new starts a round with a board");
        TestSupport.check(server.legalCount() > 0, "White has legal moves at the start");
        TestSupport.check(server.handle("move 999") != null, "an out-of-range move is refused");
        TestSupport.check(server.handle("ai") != null, "the opponent can't move on White's turn");

        // Play random legal moves until the round ends (mate, stalemate or a draw rule).
        int plies = 0;
        boolean wellFormed = true;
        while (!s.contains("\"roundOver\":true") && plies < 2000) {
            String error = s.contains("\"whiteToMove\":true")
                ? server.handle("move " + rng.nextInt(server.legalCount())) : server.handle("ai");
            s = server.state(error);
            wellFormed &= error == null && balanced(s);
            plies++;
        }
        TestSupport.check(s.contains("\"roundOver\":true"), "a round of random moves ends (" + plies + " plies)");
        TestSupport.check(wellFormed, "every answer during the round was accepted and well-formed");

        TestSupport.check(server.handle("move 0") != null, "no moves after the round is over");
        s = server.state(server.handle("finish"));
        TestSupport.check(s.contains("\"summary\":{\"result\":"), "finish reports a summary");
        TestSupport.check(balanced(s), "the summary answer is well-formed JSON");
        TestSupport.check(s.contains("\"phase\":\"SHOP\"") || s.contains("\"phase\":\"GAME_OVER\""), "after the round comes the shop (or game over)");

        if (s.contains("\"phase\":\"SHOP\"")) {
            TestSupport.check(server.handle("buy Dragon") != null, "buying an unknown piece is refused");
            server.handle("buy Pawn");                       // may be refused for gold: both are fine
            s = server.state(null);
            TestSupport.check(s.contains("\"offer\":[") && s.contains("\"stash\":["), "the shop lists its cards and your stash");
            if (s.contains("\"meets\":true")) {
                s = server.state(server.handle("sit "));
                TestSupport.check(s.contains("\"phase\":\"PLAYING\""), "sit starts the next round");
            } else {
                TestSupport.check(server.handle("sit ") != null, "sit is refused without enough material");
            }
        }
        s = server.state(server.handle("abandon"));
        s = server.state(server.handle("menu"));
        TestSupport.check(s.contains("\"phase\":\"MENU\""), "menu returns to the menu");
        TestSupport.finish("TestServer");
    }

    /** Brackets and quotes pair up (a cheap check that the hand-written JSON isn't broken). */
    private static boolean balanced(String json) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inString) {
                if (ch == '\\') i++;
                else if (ch == '"') inString = false;
            } else if (ch == '"') inString = true;
            else if (ch == '{' || ch == '[') depth++;
            else if (ch == '}' || ch == ']') depth--;
            if (depth < 0) return false;
        }
        return depth == 0 && !inString;
    }
}
