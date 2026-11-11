import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the game rules without a window, for the 3D front end (the Godot project in godot/).
 *
 * It reads one command per line from standard input and answers each with one line of JSON on
 * standard output: the whole state of the run after that command. All rules stay here in Java
 * (RunController, ChessEngine, AIPlayer, ShopManager); the front end only shows and asks.
 *
 * Commands:
 *   new NAME            start a run             state            just report
 *   move N              play legal move N       ai               let the opponent think and move
 *   play N              play hand card N        finish           end the finished round (payout)
 *   buy TYPE / sell TYPE                        buycard N / sellcard N
 *   openpack / draft N                          sit N,N,..       take those stash cards and start the level
 *   abandon             end the run             menu             back to the menu
 *   quit
 * An answer always has "ok"; when a command is refused, "error" says why.
 */
public class GameServer {
    private final RunController run = new RunController();
    private List<Move> legal = new ArrayList<>();
    private RunController.RoundSummary summary;
    private Move lastAiMove;
    private List<String> notes = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        System.setOut(System.err); // anything the rules print goes to the log, not into the JSON stream
        GameServer server = new GameServer();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.equals("quit")) break;
            String error;
            try {
                error = server.handle(line);
            } catch (Exception e) {
                e.printStackTrace();
                error = "Internal error: " + e;
            }
            out.println(server.state(error));
        }
    }

    /** Carries out one command. Returns null, or why it was refused. */
    String handle(String line) {
        String[] p = line.split(" ", 2);
        String cmd = p[0], arg = p.length > 1 ? p[1].trim() : "";
        notes = new ArrayList<>();
        lastAiMove = null;
        ShopManager shop = run.getShop();
        switch (cmd) {
            case "state":
                return null;
            case "new":
                summary = null;
                run.startNewRun(arg.isEmpty() ? "Anonymous" : arg);
                return null;
            case "move": {
                int i = number(arg);
                if (i < 0 || i >= legal.size()) return "No such move.";
                List<MoveBonuses.Bonus> bonuses = run.playerMove(legal.get(i));
                if (bonuses == null) return "Illegal move.";
                for (MoveBonuses.Bonus b : bonuses) notes.add(b.toString() + " gold");
                notes.addAll(run.takeEvents());
                return null;
            }
            case "ai": {
                if (run.isRoundOver() || !run.isAiTurn()) return "It isn't the opponent's turn.";
                Move m = run.computeAiMove(run.getRoundState().copy());
                if (!run.applyAiMove(m)) return "The opponent found no move.";
                lastAiMove = m;
                notes.addAll(run.takeEvents());
                return null;
            }
            case "play": {
                int i = number(arg);
                List<Card> hand = run.getHand();
                if (i < 0 || i >= hand.size()) return "No such card.";
                String error = run.playCard(hand.get(i));
                if (error == null) notes.add(hand.get(i).getName() + " played");
                notes.addAll(run.takeEvents());
                return error;
            }
            case "finish":
                if (!run.isRoundOver()) return "The round isn't over.";
                summary = run.finishRound();
                return null;
            case "buy":
                return refusal(shop.buyPiece(arg));
            case "sell":
                return refusal(shop.sellPiece(arg));
            case "buycard": {
                int i = number(arg);
                List<Card> offer = shop.getFaceUpCards();
                return i < 0 || i >= offer.size() ? "No such card." : refusal(shop.buyFaceUpCard(offer.get(i)));
            }
            case "openpack":
                return refusal(shop.openMysteryPack(run.getLevel()));
            case "draft": {
                int i = number(arg);
                List<Card> pack = shop.getMysteryPackCards();
                return i < 0 || i >= pack.size() ? "No such card." : refusal(shop.draftMysteryCard(pack.get(i)));
            }
            case "sellcard": {
                int i = number(arg);
                List<Card> stash = shop.getCards().getActiveCards();
                return i < 0 || i >= stash.size() ? "No such card." : refusal(shop.sellCard(stash.get(i)));
            }
            case "sit": {
                if (!shop.meetsRequirement(run.getLevel())) return "You need more material for this level.";
                List<Card> stash = shop.getCards().getActiveCards();
                List<Card> chosen = new ArrayList<>();
                for (String s : arg.split(",")) {
                    if (s.trim().isEmpty()) continue;
                    int i = number(s.trim());
                    if (i < 0 || i >= stash.size()) return "No such card.";
                    chosen.add(stash.get(i));
                }
                if (!run.setHand(chosen)) return "Too many cards for your hand.";
                summary = null;
                return run.finishShopping() ? null : "The level could not start.";
            }
            case "abandon":
                run.abandonRun();
                return null;
            case "menu":
                run.returnToMenu();
                summary = null;
                return null;
            default:
                return "Unknown command: " + cmd;
        }
    }

    private static String refusal(ShopManager.Result r) {
        return r == ShopManager.Result.OK ? null : r.message;
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ─── JSON ───────────────────────────────────────────────────

    /** The whole run as one line of JSON. */
    String state(String error) {
        StringBuilder j = new StringBuilder(4096);
        RunController.Phase phase = run.getPhase();
        ShopManager shop = run.getShop();
        j.append("{\"ok\":").append(error == null);
        if (error != null) j.append(",\"error\":").append(q(error));
        j.append(",\"phase\":").append(q(phase.name()));
        j.append(",\"level\":").append(run.getLevel());
        j.append(",\"gold\":").append(run.getGold());
        j.append(",\"requirement\":").append(run.getRequirement());
        j.append(",\"opponent\":").append(run.getOpponent());
        j.append(",\"aiDepth\":").append(run.getAiDepth());
        j.append(",\"handSize\":").append(run.getHandSize());
        j.append(",\"bestLevel\":").append(run.getHighestLevelBeaten());
        j.append(",\"notes\":").append(strings(notes));

        legal = new ArrayList<>();
        if (phase == RunController.Phase.PLAYING && run.getEngine() != null) {
            GameState s = run.getRoundState();
            ChessEngine engine = run.getEngine();
            j.append(",\"board\":[");
            for (int r = 0; r < 8; r++) {
                j.append(r == 0 ? "[" : ",[");
                for (int c = 0; c < 8; c++) {
                    Piece piece = s.board[r][c];
                    if (c > 0) j.append(',');
                    j.append(piece == null ? "\"\"" : "\"" + (piece.isWhite() ? 'w' : 'b')
                        + Character.toUpperCase(GameState.pieceLetter(piece)) + "\"");
                }
                j.append(']');
            }
            j.append(']');
            j.append(",\"whiteToMove\":").append(s.whiteToMove);
            j.append(",\"roundOver\":").append(run.isRoundOver());
            j.append(",\"result\":").append(q(s.result == null ? "" : s.result.name()));
            j.append(",\"whiteInCheck\":").append(engine.isKingInCheck(s, true));
            j.append(",\"blackInCheck\":").append(engine.isKingInCheck(s, false));
            if (!run.isRoundOver() && s.whiteToMove) legal = engine.getAllLegalMoves(true);
            j.append(",\"legal\":[");
            for (int i = 0; i < legal.size(); i++) {
                if (i > 0) j.append(',');
                j.append(move(legal.get(i)));
            }
            j.append(']');
            if (lastAiMove != null) j.append(",\"aiMove\":").append(move(lastAiMove));
            j.append(",\"hand\":[");
            List<Card> hand = run.getHand();
            for (int i = 0; i < hand.size(); i++) {
                if (i > 0) j.append(',');
                j.append(card(hand.get(i), 0)).insert(j.length() - 1, ",\"played\":" + run.isPlayed(hand.get(i)));
            }
            j.append(']');
        }

        // The shop is reported in every phase (the army and stash are worth showing during play too).
        j.append(",\"shop\":{\"material\":").append(shop.armyMaterial());
        j.append(",\"meets\":").append(shop.meetsRequirement(run.getLevel()));
        j.append(",\"pawnCap\":").append(shop.pawnCap());
        j.append(",\"pieces\":[");
        for (int i = 0; i < Economy.PIECE_TYPES.length; i++) {
            String type = Economy.PIECE_TYPES[i];
            int price = Economy.piecePrice(type);
            if (i > 0) j.append(',');
            j.append("{\"type\":").append(q(type)).append(",\"owned\":").append(shop.count(type))
                .append(",\"price\":").append(price).append(",\"sell\":").append(Economy.sellPrice(price)).append('}');
        }
        j.append("],\"offer\":").append(cards(shop.getFaceUpCards(), 1));
        j.append(",\"pack\":").append(cards(shop.getMysteryPackCards(), 0));
        j.append(",\"packOpened\":").append(shop.isMysteryPackOpened());
        j.append(",\"packPrice\":").append(Economy.mysteryPackPrice(run.getLevel()));
        j.append(",\"stash\":").append(cards(shop.getCards().getActiveCards(), 2));
        j.append('}');

        if (summary != null) {
            RunController.RoundSummary s = summary;
            j.append(",\"summary\":{\"result\":").append(q(s.result.name()));
            j.append(",\"text\":").append(q(s.resultText == null ? "" : s.resultText));
            j.append(",\"level\":").append(s.level).append(",\"nextLevel\":").append(s.nextLevel);
            j.append(",\"goldAfter\":").append(s.goldAfter).append(",\"gameOver\":").append(s.gameOver);
            j.append(",\"madeLeaderboard\":").append(s.madeLeaderboard);
            j.append(",\"bestLevel\":").append(s.highestLevelBeaten);
            if (s.payout != null) {
                j.append(",\"own\":").append(s.payout.ownMaterial).append(",\"defeated\":").append(s.payout.defeatedMaterial);
                j.append(",\"bonus\":").append(s.payout.bonusCoins).append(",\"base\":").append(s.payout.levelBase);
                j.append(",\"interest\":").append(s.payout.interest).append(",\"total\":").append(s.payout.total);
            }
            j.append(",\"cardsUsedUp\":").append(strings(s.cardsUsedUp)).append('}');
        }
        return j.append('}').toString();
    }

    private static String move(Move m) {
        return "{\"fr\":" + m.fromRow + ",\"fc\":" + m.fromCol + ",\"tr\":" + m.toRow + ",\"tc\":" + m.toCol
            + ",\"capture\":" + m.isCapture + ",\"promo\":" + q(m.isPromotion && m.promotionType != null ? m.promotionType : "")
            + ",\"snipe\":" + m.isSniperShot + ",\"sr\":" + m.sniperTargetRow + ",\"sc\":" + m.sniperTargetCol
            + ",\"enPassant\":" + m.isEnPassant + "}";
    }

    /** price: 0 = none, 1 = the shop's face-up price, 2 = what the reseller pays. */
    private static String card(Card c, int price) {
        String s = "{\"name\":" + q(c.getName()) + ",\"text\":" + q(c.getDescription())
            + ",\"rarity\":" + q(c.getRarity().name()) + ",\"global\":" + c.isGlobal();
        if (price == 1) s += ",\"price\":" + Economy.faceUpCardPrice(c);
        if (price == 2) s += ",\"price\":" + Economy.cardSellPrice(c);
        return s + "}";
    }

    private static String cards(List<Card> list, int price) {
        StringBuilder j = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) j.append(',');
            j.append(card(list.get(i), price));
        }
        return j.append(']').toString();
    }

    private static String strings(List<String> list) {
        StringBuilder j = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) j.append(',');
            j.append(q(list.get(i)));
        }
        return j.append(']').toString();
    }

    private static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch == '\n') b.append("\\n");
            else if (ch < 0x20) b.append(' ');
            else b.append(ch);
        }
        return b.append('"').toString();
    }
}
