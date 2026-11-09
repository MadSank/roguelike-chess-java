/**
 * LOCAL CARD: The AI loses its next turn. If your next move puts the AI in check, the skip
 * waits until a turn where it isn't in check.
 * Cost: 40 gold
 */
public class CardTimeStop extends Card {
    private static final long serialVersionUID = 1L;
    /** Counter set while a skipped turn is pending (read by RunController). */
    static final String PENDING = "timeStop.pending";

    public CardTimeStop() {
        super("Time Stop", "The AI skips its next turn: you move twice in a row.", 40, false);
    }

    @Override
    public void onPlay(GameState state) {
        state.setCounter(PENDING, 1);
    }
}
