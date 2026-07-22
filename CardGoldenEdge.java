
/**
 * LOCAL CARD: Every capture yields gold equal to the captured piece's
 * standard point value (P=1, N=3, B=3, R=5, Q=9).
 * Cost: 10 gold
 */
public class CardGoldenEdge extends Card {
    private static final long serialVersionUID = 1L;

    public CardGoldenEdge() {
        super("Golden Edge",
              "Every capture yields gold equal to the captured piece's point value.",
              10, false);
    }

    @Override
    public void onCapture(GameState state, Move move, Piece captured, Piece capturer) {
        if (capturer == null || captured == null) return;
        // Only player (white) earns gold
        if (capturer.isWhite()) {
            state.goldPlayer += captured.getValue();
        }
    }
}
