/**
 * LOCAL CARD: Your pawns can't be taken by enemy pawns (other pieces still can take them;
 * en passant still works).
 * Cost: 12 gold
 */
public class CardIronPawns extends Card {
    private static final long serialVersionUID = 1L;

    public CardIronPawns() {
        super("Iron Pawns", "Your pawns can't be taken by enemy pawns.", 12, false);
    }

    @Override
    public boolean isCaptureImmune(GameState state, int row, int col, Move attackMove) {
        Piece target = state.board[row][col];
        if (!(target instanceof Pawn) || !appliesTo(target.isWhite())) return false;
        Piece attacker = state.board[attackMove.fromRow][attackMove.fromCol];
        return attacker instanceof Pawn && attacker.isWhite() != target.isWhite();
    }
}
