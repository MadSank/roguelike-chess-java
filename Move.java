

import java.io.Serializable;
import java.util.Objects;

public class Move implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int fromRow;
    public final int fromCol;
    public final int toRow;
    public final int toCol;

    public boolean isCapture;
    public boolean isCastle;
    public boolean isEnPassant;
    public boolean isPromotion;
    public String promotionType;

    // ─── Sniper Bishops extension ────────────────────────────────
    public boolean isSniperShot;
    public int sniperTargetRow = -1;
    public int sniperTargetCol = -1;

    public Move(int fromRow, int fromCol, int toRow, int toCol) {
        this.fromRow = fromRow;
        this.fromCol = fromCol;
        this.toRow = toRow;
        this.toCol = toCol;
        this.isCapture = false;
        this.isCastle = false;
        this.isEnPassant = false;
        this.isPromotion = false;
        this.promotionType = null;
        this.isSniperShot = false;
    }

    public static Move promotion(int fromRow, int fromCol, int toRow, int toCol, String type) {
        Move m = new Move(fromRow, fromCol, toRow, toCol);
        m.isPromotion = true;
        m.promotionType = type;
        return m;
    }

    public Move copy() {
        Move m = new Move(fromRow, fromCol, toRow, toCol);
        m.isCapture = isCapture;
        m.isCastle = isCastle;
        m.isEnPassant = isEnPassant;
        m.isPromotion = isPromotion;
        m.promotionType = promotionType;
        m.isSniperShot = isSniperShot;
        m.sniperTargetRow = sniperTargetRow;
        m.sniperTargetCol = sniperTargetCol;
        return m;
    }

    public boolean equalsIgnoringMeta(Move other) {
        if (other == null) return false;
        return this.fromRow == other.fromRow &&
        this.fromCol == other.fromCol &&
        this.toRow == other.toRow &&
        this.toCol == other.toCol;
    }

    public void copyMetaFrom(Move other) {
        if (other == null) return;
        this.isCapture = other.isCapture;
        this.isCastle = other.isCastle;
        this.isEnPassant = other.isEnPassant;
        this.isPromotion = other.isPromotion;
        this.promotionType = other.promotionType;
        this.isSniperShot = other.isSniperShot;
        this.sniperTargetRow = other.sniperTargetRow;
        this.sniperTargetCol = other.sniperTargetCol;
    }

    public String toUCI() {
        if (isSniperShot) {
            return "snipe:" + squareName(fromRow, fromCol) + "x" +
                   squareName(sniperTargetRow, sniperTargetCol);
        }
        String base = squareName(fromRow, fromCol) + squareName(toRow, toCol);
        if (isPromotion && promotionType != null) {
            base += promotionType.toLowerCase();
        }
        return base;
    }

    public static Move fromUCI(String uci) {
        if (uci == null) return null;
        uci = uci.trim().toLowerCase();
        if (uci.length() < 4) return null;
        Square[] s = Square.fromUCI(uci);
        if (s == null) return null;
        Move m = new Move(s[0].row, s[0].col, s[1].row, s[1].col);
        if (uci.length() == 5) {
            char promo = uci.charAt(4);
            if ("qrbn".indexOf(promo) >= 0) {
                m.isPromotion = true;
                m.promotionType = String.valueOf(Character.toUpperCase(promo));
            }
        }
        return m;
    }

    private static String squareName(int row, int col) {
        char file = (char) ('a' + col);
        int rank = 8 - row;
        return "" + file + rank;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(toUCI());
        if (isCapture) sb.append("x");
        if (isCastle) sb.append(" (castle)");
        if (isEnPassant) sb.append(" (ep)");
        if (isSniperShot) sb.append(" (snipe)");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Move)) return false;
        Move other = (Move) obj;
        return fromRow == other.fromRow &&
        fromCol == other.fromCol &&
        toRow == other.toRow &&
        toCol == other.toCol &&
        isSniperShot == other.isSniperShot &&
        sniperTargetRow == other.sniperTargetRow &&
        sniperTargetCol == other.sniperTargetCol &&
        Objects.equals(promotionType, other.promotionType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromRow, fromCol, toRow, toCol, promotionType,
                           isSniperShot, sniperTargetRow, sniperTargetCol);
    }
}
