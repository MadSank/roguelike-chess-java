package ashes;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class ShopScene extends JPanel {
    private final RoguelikeChessAppSwing app;
    private final ChessEngine engine;
    private final ShopManager shop;
    private final ScoreManager score;

    private JLabel goldLabel;
    private JPanel countPanel;

    public ShopScene(RoguelikeChessAppSwing app, ChessEngine engine,
    ShopManager shop, ScoreManager score) {
        this.app = app; this.engine = engine; this.shop = shop; this.score = score;
        setLayout(new BorderLayout());
        setBackground(new Color(20, 10, 5));
        buildUI();
    }

    private void buildUI() {

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        goldLabel = new JLabel();
        goldLabel.setForeground(Color.YELLOW);
        goldLabel.setFont(new Font("Serif", Font.BOLD, 22));
        top.add(goldLabel, BorderLayout.WEST);

        countPanel = new JPanel(new GridLayout(0, 1, 5, 2));
        countPanel.setOpaque(false);
        countPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 20));
        top.add(countPanel, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        String[] items = {"Pawn", "Knight", "Bishop", "Rook", "Queen"};
        for (int i = 0; i < items.length; i++) {
            String it = items[i];
            JButton b = new JButton(it + " (" + shop.getPrices().get(it) + " gold)");
            b.setFont(new Font("Serif", Font.BOLD, 18));
            b.addActionListener(e -> buy(it));
            gbc.gridx = i % 3; gbc.gridy = i / 3;
            center.add(b, gbc);
        }

        JButton cont = new JButton("Continue to Next Round");
        cont.setFont(new Font("Serif", Font.BOLD, 20));
        cont.addActionListener(e -> app.onShoppingComplete());
        gbc.gridx = 0; gbc.gridy = 99; gbc.gridwidth = 3;
        center.add(cont, gbc);

        add(center, BorderLayout.CENTER);
    }

    private void buy(String type) {
        boolean ok = shop.purchase(engine, type);
        if (ok) AudioPlayer.playMoveSound();
        refresh();
    }

    public void refresh() {
        GameState s = engine.getGameState();
        goldLabel.setText("Gold: " + s.goldPlayer);

        countPanel.removeAll();
        Map<String, Integer> cnt = shop.getPieceCounts();
        for (Map.Entry<String, Integer> e : cnt.entrySet()) {
            JLabel l = new JLabel(e.getKey() + ": " + e.getValue());
            l.setForeground(Color.LIGHT_GRAY);
            l.setFont(new Font("Monospaced", Font.PLAIN, 16));
            countPanel.add(l);
        }
        countPanel.revalidate();
        countPanel.repaint();
    }
}