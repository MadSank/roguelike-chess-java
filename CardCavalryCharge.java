
import java.util.*;

/**
 * LOCAL CARD: Knights can move to any empty square within Chebyshev distance 2,
 * plus their normal L-shape moves.
 * Cost: 20 gold
 */
public class CardCavalryCharge extends Card {
    private static final long serialVersionUID = 1L;

    public CardCavalryCharge() {
        super("Cavalry Charge",
              "Knights can move to any empty square within 2-square radius, plus normal L-shape.",
              20, false);
    }

    @Override
    public List<Move> modifyMoveGeneration(List<Move> baseMoves, Piece piece,
            int row, int col, ChessEngine engine, GameState state) {
        if (!(piece instanceof Knight)) return baseMoves;
        if (!appliesTo(piece.isWhite())) return baseMoves;

        // Collect existing target squares to avoid duplicates
        // Use bit-packed int set: targetRow * 8 + targetCol
        long existing = 0L;
        for (int i = 0, n = baseMoves.size(); i < n; i++) {
            Move m = baseMoves.get(i);
            existing |= (1L << (m.toRow * 8 + m.toCol));
        }

        // Scan 5x5 box centered on knight (Chebyshev distance <= 2)
        for (int dr = -2; dr <= 2; dr++) {
            for (int dc = -2; dc <= 2; dc++) {
                if (dr == 0 && dc == 0) continue;
                int tr = row + dr;
                int tc = col + dc;
                if (!Square.insideBoard(tr, tc)) continue;

                int idx = tr * 8 + tc;
                if ((existing & (1L << idx)) != 0) continue; // already have this move

                Piece target = state.board[tr][tc];
                if (target == null) {
                    baseMoves.add(new Move(row, col, tr, tc));
                } else if (target.isWhite() != piece.isWhite()) {
                    Move m = new Move(row, col, tr, tc);
                    m.isCapture = true;
                    baseMoves.add(m);
                }
            }
        }
        return baseMoves;
    }

    @Override
    public boolean canPieceAttackSquare(Piece piece, int fromRow, int fromCol,
            int toRow, int toCol, GameState state) {
        if (!(piece instanceof Knight)) return false;
        if (!appliesTo(piece.isWhite())) return false;
        int dr = Math.abs(toRow - fromRow);
        int dc = Math.abs(toCol - fromCol);
        return Math.max(dr, dc) <= 2 && (dr + dc) > 0;
    }
}
