
/**
 * GLOBAL CARD: All pawns currently on the board (both colors)
 * immediately become Knights. One-time mutation at purchase.
 * Future pawns bought in the shop remain pawns.
 * Cost: 35 gold
 */
public class CardBloodMoon extends Card {
    private static final long serialVersionUID = 1L;

    public CardBloodMoon() {
        super("Blood Moon",
              "All pawns on the board immediately become Knights. One-time mutation.",
              35, true);
    }

    @Override
    public void onGameStart(GameState state) {
        int transformed = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p instanceof Pawn) {
                    state.board[r][c] = new Knight(p.isWhite());
                    transformed++;
                }
            }
        }
        System.out.println("BLOOD MOON: " + transformed + " pawns transformed into Knights!");
    }
}
