import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * A look at the storybook art on its own: the table scene with an opponent, live.
 *   java ArtPreview              opens a window (click to change opponent, 1-8 for dealers / masks)
 *   java ArtPreview out.png N    saves a picture of opponent N (1-5) or dealer N (6-8) and quits
 */
public class ArtPreview extends JPanel {
    private int who = 0;
    private final long start = System.currentTimeMillis();
    private final Cellar cellar = new Cellar();

    static final String[][] START = {
        {"bR", "bN", "bB", "bQ", "bK", "bB", "bN", "bR"},
        {"bP", "bP", "bP", "bP", "bP", "bP", "bP", "bP"},
        {"", "", "", "", "", "", "", ""}, {"", "", "", "", "", "", "", ""},
        {"", "", "", "", "wP", "", "", ""}, {"", "", "", "", "", "", "", ""},
        {"wP", "wP", "wP", "wP", "", "wP", "wP", "wP"},
        {"wR", "wN", "wB", "wQ", "wK", "wB", "wN", "wR"},
    };

    public static void paintScene(Graphics2D g, int w, int h, int who, double t, Cellar cellar) {
        Ink.smooth(g);
        cellar.layout(w, h, w / 2.0, h * 0.8, w * 0.34);
        double flicker = 0.92 + 0.08 * Math.sin(t * 13) * Math.sin(t * 3.1);
        cellar.room(g, t, flicker, who < 5);
        if (who < 5) {
            Ink.glow(g, w / 2.0, h * 0.38, h * 0.42, Ink.LAMP, 40);                   // the lamp's warmth on him
            Figures.seated(g, Figures.OPPONENTS[who], w / 2.0, h * 0.645, h * 0.15, t, null, 0);
        } else {
            Figures.Look[] dealers = {Figures.CARD_DEALER, Figures.PIECE_DEALER, Figures.RESELLER};
            Ink.glow(g, w / 2.0, h * 0.35, h * 0.5, Ink.OCHRE, 45);
            Figures.standing(g, dealers[who - 5], w / 2.0, h * 1.0, h * 0.11, t, null, 0, who == 5);
        }
        if (who < 5) {
            cellar.table(g);
            cellar.board(g);
            cellar.pieces(g, START);
        }
        if (who < 5) cellar.lamp(g, t, flicker);
        Ink.darkness(g, w, h, w / 2.0, h * 0.5, Math.max(w, h) * 0.7, 205);
        Ink.paper(g, w, h);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        paintScene((Graphics2D) g0, getWidth(), getHeight(), who, (System.currentTimeMillis() - start) / 1000.0, cellar);
    }

    public static void main(String[] args) throws Exception {
        if (args.length >= 2) {
            int w = 1600, h = 900;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            paintScene(g, w, h, Integer.parseInt(args[1]) - 1, 1.3, new Cellar());
            g.dispose();
            ImageIO.write(img, "png", new File(args[0]));
            System.out.println("saved " + args[0]);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Ashes - storybook art");
            ArtPreview p = new ArtPreview();
            p.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) { p.who = (p.who + 1) % 8; }
            });
            f.add(p);
            f.setSize(1280, 760);
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);
            new Timer(33, e -> p.repaint()).start();
        });
    }
}
