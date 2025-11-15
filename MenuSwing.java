package ashes;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MenuSwing extends JPanel {
    private static final int PANEL_WIDTH = 900;
    private static final int PANEL_HEIGHT = 700;
    private RoguelikeChessAppSwing app;
    private CardLayout menuLayout;
    private JPanel menuContainer;

    public MenuSwing(RoguelikeChessAppSwing app) {
        this.app = app;
        initializeUI();
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
    }

    private void initializeUI() {
        menuLayout = new CardLayout();
        menuContainer = new JPanel(menuLayout);

        menuContainer.add(createMainMenu(), "MAIN");
        menuContainer.add(createSettingsMenu(), "SETTINGS");
        menuContainer.add(createCreditsMenu(), "CREDITS");

        setLayout(new BorderLayout());
        add(menuContainer, BorderLayout.CENTER);

        menuLayout.show(menuContainer, "MAIN");
    }

    private JPanel createMainMenu() {
        JPanel mainMenu = new JPanel(new GridBagLayout());
        mainMenu.setBackground(new Color(20, 10, 5));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 50, 10, 50);

        JLabel titleLabel = new JLabel("ASHES OF THE BOARD", SwingConstants.CENTER);
        titleLabel.setForeground(Color.RED);
        titleLabel.setFont(new Font("Serif", Font.BOLD, 48));
        gbc.insets = new Insets(50, 50, 30, 50);
        mainMenu.add(titleLabel, gbc);

        JLabel subtitleLabel = new JLabel("Roguelike Chess", SwingConstants.CENTER);
        subtitleLabel.setForeground(Color.ORANGE);
        subtitleLabel.setFont(new Font("Serif", Font.ITALIC, 24));
        gbc.insets = new Insets(0, 50, 50, 50);
        mainMenu.add(subtitleLabel, gbc);

        gbc.insets = new Insets(10, 50, 10, 50);

        JButton newGameButton = createMenuButton("New Game");
        newGameButton.addActionListener(e -> app.startMatch());
        mainMenu.add(newGameButton, gbc);

        JButton highScoresButton = createMenuButton("High Scores");
        highScoresButton.addActionListener(e -> showHighScores());
        mainMenu.add(highScoresButton, gbc);

        JButton settingsButton = createMenuButton("Settings");
        settingsButton.addActionListener(e -> menuLayout.show(menuContainer, "SETTINGS"));
        mainMenu.add(settingsButton, gbc);

        JButton creditsButton = createMenuButton("Credits");
        creditsButton.addActionListener(e -> menuLayout.show(menuContainer, "CREDITS"));
        mainMenu.add(creditsButton, gbc);

        JButton exitButton = createMenuButton("Exit");
        exitButton.addActionListener(e -> confirmExit());
        mainMenu.add(exitButton, gbc);

        return mainMenu;
    }

    private JPanel createSettingsMenu() {
        JPanel settings = new JPanel(new GridBagLayout());
        settings.setBackground(new Color(20, 10, 5));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(20, 50, 20, 50);

        JLabel title = new JLabel("Settings", SwingConstants.CENTER);
        title.setForeground(Color.CYAN);
        title.setFont(new Font("Serif", Font.BOLD, 36));
        settings.add(title, gbc);

        JCheckBox soundToggle = new JCheckBox("Enable Sound");
        soundToggle.setSelected(AudioPlayer.isSoundEnabled());
        soundToggle.setForeground(Color.WHITE);
        soundToggle.setOpaque(false);
        soundToggle.addActionListener(e -> AudioPlayer.setSoundEnabled(soundToggle.isSelected()));
        settings.add(soundToggle, gbc);

        JButton backButton = createMenuButton("Back to Main Menu");
        backButton.addActionListener(e -> menuLayout.show(menuContainer, "MAIN"));
        settings.add(backButton, gbc);

        return settings;
    }

    private JPanel createCreditsMenu() {
        JPanel credits = new JPanel(new GridBagLayout());
        credits.setBackground(new Color(20, 10, 5));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.insets = new Insets(20, 50, 20, 50);

        JLabel title = new JLabel("Credits", SwingConstants.CENTER);
        title.setForeground(Color.CYAN);
        title.setFont(new Font("Serif", Font.BOLD, 36));
        credits.add(title, gbc);

        JTextArea creditText = new JTextArea(
                "Game Design & Programming:\n" +
                "Maddox and Sambar Boy\n\n"
            );
        creditText.setEditable(false);
        creditText.setForeground(Color.LIGHT_GRAY);
        creditText.setBackground(new Color(20, 10, 5));
        creditText.setFont(new Font("Monospaced", Font.PLAIN, 16));
        creditText.setLineWrap(true);
        creditText.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(creditText);
        scroll.setPreferredSize(new Dimension(600, 400));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        credits.add(scroll, gbc);

        JButton backButton = createMenuButton("Back");
        backButton.addActionListener(e -> menuLayout.show(menuContainer, "MAIN"));
        credits.add(backButton, gbc);

        return credits;
    }

    private JButton createMenuButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Serif", Font.BOLD, 20));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(80, 40, 20));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(150, 75, 0), 2),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)
            ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    button.setBackground(new Color(120, 60, 30));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    button.setBackground(new Color(80, 40, 20));
                }
            });

        return button;
    }

    private void startNewGame() {
        app.startMatch();
    }

    private void showHighScores() {

        JOptionPane.showMessageDialog(
            this,
            "High Scores feature coming soon!\n\n" +
            "Check back after a few rounds.",
            "Leaderboard",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void confirmExit() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to exit Ashes of the Board?",
                "Exit Game",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
        if (confirm == JOptionPane.YES_OPTION) {
            System.exit(0);
        }
    }

    public void showMenu() {
        menuLayout.show(menuContainer, "MAIN");
    }
}