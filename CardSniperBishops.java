
import java.util.*;

/**
 * LOCAL CARD: Bishops can capture an enemy piece in their line of sight
 * without moving to its square (a "sniper shot"). Counts as a turn.
 * Cost: 25 gold
 */
public class CardSniperBishops extends Card {
    private static final long serialVersionUID = 1L;

    private static final int[][] DIAGONALS = {
        {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
    };

    public CardSniperBishops() {
        super("Sniper Bishops",
              "Bishops can capture an enemy in their line of sight without moving to its square.",
              25, false);
    }

    @Override
    public List<Move> modifyMoveGeneration(List<Move> baseMoves, Piece piece,
            int row, int col, ChessEngine engine, GameState state) {
        if (!(piece instanceof Bishop)) return baseMoves;
        if (!appliesTo(piece.isWhite())) return baseMoves;

        // Scan diagonals — for each enemy found in line of sight, add a sniper move
        for (int[] dir : DIAGONALS) {
            int r = row + dir[0];
            int c = col + dir[1];
            while (Square.insideBoard(r, c)) {
                Piece target = state.board[r][c];
                if (target != null) {
                    if (target.isWhite() != piece.isWhite()) {
                        // Sniper shot: bishop stays, target is removed
                        Move snipe = new Move(row, col, row, col);
                        snipe.isCapture = true;
                        snipe.isSniperShot = true;
                        snipe.sniperTargetRow = r;
                        snipe.sniperTargetCol = c;
                        baseMoves.add(snipe);
                    }
                    break; // blocked by this piece
                }
                r += dir[0];
                c += dir[1];
            }
        }
        return baseMoves;
    }
}
