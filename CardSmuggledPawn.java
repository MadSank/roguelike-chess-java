/**
 * LOCAL CARD: A pawn slipped onto the board for you: one extra pawn appears on your second
 * rank (the empty square nearest the centre; the third rank if the second is full).
 * Cost: 10 gold
 */
public class CardSmuggledPawn extends Card {
    private static final long serialVersionUID = 1L;

    private static final int[] CENTRE_FIRST = {3, 4, 2, 5, 1, 6, 0, 7};

    public CardSmuggledPawn() {
        super("Smuggled Pawn", "One extra pawn is slipped onto your second rank.", 10, false);
    }

    @Override
    public void onPlay(GameState state) {
        for (int row : new int[]{6, 5}) {
            for (int col : CENTRE_FIRST) {
                if (state.board[row][col] == null) {
                    state.board[row][col] = new Pawn(true);
                    return;
                }
            }
        }
    }
}
