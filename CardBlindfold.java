/**
 * LOCAL CARD: The opponent plays half-blind: for the rest of the round the AI looks one move
 * less far ahead (never less than one).
 * Cost: 20 gold
 */
public class CardBlindfold extends Card {
    private static final long serialVersionUID = 1L;

    /** Counter read by AIPlayer: how many plies to take off its search. */
    public static final String KEY = "blindfold";

    public CardBlindfold() {
        super("Blindfold",
              "Your opponent plays half-blind: for the rest of the round he looks one move less far ahead.",
              20, false);
    }

    @Override
    public void onPlay(GameState state) {
        state.setCounter(KEY, 1);
    }
}
