/**
 * LOCAL CARD: The guards bet on the game: when the enemy takes one of your pieces, you are paid
 * twice its value in gold.
 * Cost: 15 gold
 */
public class CardBloodMoney extends Card {
    private static final long serialVersionUID = 1L;

    public CardBloodMoney() {
        super("Blood Money", "When the enemy takes one of your pieces, you're paid twice its worth in gold.", 15, false);
    }

    @Override
    public void onCapture(GameState state, Move move, Piece captured, Piece capturer) {
        if (captured == null || capturer == null) return;
        if (captured.isWhite() && !capturer.isWhite() && !(captured instanceof King)) state.goldPlayer += 2 * captured.getValue();
    }
}
