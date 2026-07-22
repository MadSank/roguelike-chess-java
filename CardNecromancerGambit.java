
import java.util.*;

/**
 * LOCAL CARD: Once per game, if your Queen is captured, a random friendly
 * pawn instantly promotes to a Queen.
 * Cost: 30 gold
 */
public class CardNecromancerGambit extends Card {
    private static final long serialVersionUID = 1L;

    public CardNecromancerGambit() {
        super("Necromancer's Gambit",
              "Once per game: if your Queen is captured, a random pawn promotes to Queen.",
              30, false);
    }

    @Override
    public void onCapture(GameState state, Move move, Piece captured, Piece capturer) {
        if (captured == null) return;
        if (!(captured instanceof Queen)) return;
        if (!captured.isWhite()) return; // only triggers for player's queen
        if (state.necromancerUsed) return;

        // Find all friendly pawns
        List<int[]> pawnPositions = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p instanceof Pawn && p.isWhite()) {
                    pawnPositions.add(new int[]{r, c});
                }
            }
        }

        if (!pawnPositions.isEmpty()) {
            // Deterministic random based on move count for AI consistency
            int idx = (state.moveHistory.size() * 31 + 17) % pawnPositions.size();
            int[] pos = pawnPositions.get(idx);
            state.board[pos[0]][pos[1]] = new Queen(true);
            state.necromancerUsed = true;
            System.out.println("NECROMANCER: Pawn at (" + pos[0] + "," + pos[1] + ") promoted to Queen!");
        }
    }
}
