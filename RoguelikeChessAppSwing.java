package ashes;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class RoguelikeChessAppSwing extends JFrame {

    private static final int WINDOW_WIDTH = 900;
    private static final int WINDOW_HEIGHT = 700;
    private static final int MAX_AI_DEPTH = 16;

    private ChessEngine engine;
    private AIPlayer aiPlayer;
    private ShopManager shopManager;
    private Chess960Setup chess960;
    private ScoreManager scoreManager;
    private CardLayout sceneLayout;
    private JPanel sceneContainer;
    private BoardViewSwing boardView;
    private MenuSwing menuScene;
    private ShopScene shopScene;
    private GamePhase currentPhase;

    public enum GamePhase { MENU, PLAYING, SHOPPING, GAME_OVER }

    public RoguelikeChessAppSwing() {
        super("Ashes of the Board - Roguelike Chess");
        initializeGame();
        initializeUI();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setLocationRelativeTo(null);
        setResizable(false);
    }

    private void initializeGame() {
        engine = new ChessEngine();
        chess960 = new Chess960Setup();
        scoreManager = new ScoreManager();
        shopManager = new ShopManager();
        currentPhase = GamePhase.MENU;
    }

    private void initializeUI() {
        sceneLayout = new CardLayout();
        sceneContainer = new JPanel(sceneLayout);

        menuScene = new MenuSwing(this);
        boardView = new BoardViewSwing(this, engine);
        shopScene = new ShopScene(this, engine, shopManager, scoreManager);

        sceneContainer.add(menuScene, "MENU");
        sceneContainer.add(boardView, "BOARD");
        sceneContainer.add(shopScene, "SHOP");

        add(sceneContainer);
        sceneLayout.show(sceneContainer, "MENU");
    }

    public void startNewGameSession() {
        String name = JOptionPane.showInputDialog(this,
                "Enter your name for the leaderboard:", "New Game",
                JOptionPane.QUESTION_MESSAGE);
        if (name == null || name.trim().isEmpty()) name = "Anonymous";
        scoreManager.setPlayerName(name.trim());
        scoreManager.resetSession();

        engine.reset();
        GameState gs = engine.getGameState();
        gs.aiDepth = 2;
        gs.goldPlayer = 0;
        gs.roundNumber = 1;

        List<Piece> starting = shopManager.collectPurchasedPieces();

        chess960.setupCustomChess960Board(gs, starting);

        aiPlayer = new AIPlayer(engine, gs.aiDepth);
        currentPhase = GamePhase.PLAYING;
        sceneLayout.show(sceneContainer, "BOARD");
        boardView.refresh();
        AudioPlayer.playMusic("battle.wav", true);
    }

    public void onShoppingComplete() {
        GameState old = engine.getGameState();

        List<Piece> pieces = shopManager.collectPurchasedPieces();

        System.out.println("=== PIECES FOR NEXT ROUND ===");
        System.out.println("Total pieces: " + pieces.size());
        for (Piece p : pieces) {
            System.out.println("  - " + p.getName());
        }

        GameState fresh = new GameState();
        fresh.goldPlayer = shopManager.getGoldFromPreviousGame();
        fresh.roundNumber = old.roundNumber + 1;
        fresh.aiDepth = Math.min(old.aiDepth + 1, MAX_AI_DEPTH);

        fresh.movesToEnd = 0;
        fresh.capturesMade = 0;
        fresh.specialMovesUsed = 0;
        fresh.piecesLeftStanding = 0;
        fresh.moveHistory.clear();

        fresh.gameOver = false;
        fresh.gameResult = "";
        fresh.whiteToMove = true;

        chess960.setupCustomChess960Board(fresh, pieces);

        System.out.println("=== PRE-ENGINE DEBUG ===");
        System.out.println("Game over flag: " + fresh.gameOver);
        System.out.println("White to move: " + fresh.whiteToMove);
        System.out.println("Pieces passed to Chess960: " + pieces.size());
        debugBoardState(fresh);

        engine = new ChessEngine(fresh);

        verifyPlayableState();

        System.out.println("=== POST-ENGINE DEBUG ===");
        GameState current = engine.getGameState();
        System.out.println("Game over flag: " + current.gameOver);
        System.out.println("White to move: " + current.whiteToMove);
        List<Move> whiteMoves = engine.getAllLegalMoves(true);
        List<Move> blackMoves = engine.getAllLegalMoves(false);
        System.out.println("White legal moves: " + whiteMoves.size());
        System.out.println("Black legal moves: " + blackMoves.size());

        sceneContainer.remove(boardView);
        boardView = new BoardViewSwing(this, engine);
        sceneContainer.add(boardView, "BOARD");

        aiPlayer = new AIPlayer(engine, fresh.aiDepth);

        currentPhase = GamePhase.PLAYING;

        sceneLayout.show(sceneContainer, "BOARD");
        boardView.refresh();

        String warn = fresh.aiDepth > old.aiDepth
            ? String.format("%nWarning: AI depth %d → %d", old.aiDepth, fresh.aiDepth)
            : "";
        JOptionPane.showMessageDialog(this,
            String.format("Round %d%nGold: %d | Score: %,d%s",
                fresh.roundNumber, fresh.goldPlayer,
                scoreManager.getCurrentScore(), warn),
            "New Round", JOptionPane.INFORMATION_MESSAGE);

        AudioPlayer.playMusic("battle.wav", true);
    }

    private void debugBoardState(GameState state) {
        boolean whiteKing = false;
        boolean blackKing = false;
        int whitePieces = 0;
        int blackPieces = 0;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.board[r][c];
                if (p != null) {
                    if (p.isWhite()) {
                        whitePieces++;
                        if (p instanceof King) whiteKing = true;
                    } else {
                        blackPieces++;
                        if (p instanceof King) blackKing = true;
                    }
                }
            }
        }

        System.out.println("White King: " + whiteKing + " | White Pieces: " + whitePieces);
        System.out.println("Black King: " + blackKing + " | Black Pieces: " + blackPieces);
    }

    private void verifyPlayableState() {
        GameState state = engine.getGameState();

        state.gameOver = false;
        state.gameResult = "";
        state.whiteToMove = true;

        boolean whiteKingExists = false;
        boolean blackKingExists = false;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = state.board[r][c];
                if (piece instanceof King) {
                    if (piece.isWhite()) whiteKingExists = true;
                    else blackKingExists = true;
                }
            }
        }

        if (!whiteKingExists || !blackKingExists) {
            System.err.println("CRITICAL ERROR: Missing king(s)!");
            System.err.println("White King: " + whiteKingExists);
            System.err.println("Black King: " + blackKingExists);

            if (!whiteKingExists) {
                for (int c = 0; c < 8; c++) {
                    if (state.board[7][c] == null) {
                        state.board[7][c] = new King(true);
                        System.err.println("Added white king at (7," + c + ")");
                        break;
                    }
                }
            }

            if (!blackKingExists) {
                for (int c = 0; c < 8; c++) {
                    if (state.board[0][c] == null) {
                        state.board[0][c] = new King(false);
                        System.err.println("Added black king at (0," + c + ")");
                        break;
                    }
                }
            }
        }

        state.piecesLeftStanding = countPiecesOnBoard(state);
    }

    public void onPlayerMove(Move move) {
        if (currentPhase != GamePhase.PLAYING) return;
        if (!engine.applyMove(move)) {
            JOptionPane.showMessageDialog(this, "Illegal move!");
            return;
        }
        AudioPlayer.playMoveSound();
        if (move.isCapture) AudioPlayer.playCaptureSound();
        boardView.refresh();

        if (engine.getGameState().gameOver) { 
            handleGameOver(); 
            return; 
        }

        SwingUtilities.invokeLater(this::performAIMove);
    }

    private void performAIMove() {
        Move m = aiPlayer.chooseMove(engine.getGameState());
        if (m == null) { 
            handleGameOver(); 
            return; 
        }
        engine.applyMove(m);
        AudioPlayer.playMoveSound();
        boardView.refresh();
        if (engine.getGameState().gameOver) handleGameOver();
    }

    private void handleGameOver() {
        GameState s = engine.getGameState();
        if (s.gameResult.contains("White")) {
            int earn = calculateRoundEarnings(s);
            s.goldPlayer += earn;
            scoreManager.addMatchScore(earn, s.aiDepth, s.goldPlayer);

            JOptionPane.showMessageDialog(this,
                String.format("Victory!%nGold Earned: %d%nTotal Gold: %d%nScore: %,d%nDepth %d",
                    earn, s.goldPlayer, scoreManager.getCurrentScore(), s.aiDepth),
                "Round Complete", JOptionPane.INFORMATION_MESSAGE);
            transitionToShop();
        } else {
            AudioPlayer.playCheckmateSound();
            long finalScore = scoreManager.getCurrentScore();
            boolean high = scoreManager.isHighScore();
            JOptionPane.showMessageDialog(this,
                String.format("Defeat!%nFinal Score: %,d%nRounds: %d%nDepth %d%n%s",
                    finalScore, s.roundNumber, s.aiDepth,
                    high ? "NEW HIGH SCORE!" : ""),
                "Game Over", JOptionPane.ERROR_MESSAGE);
            if (high && finalScore > 0) {
                scoreManager.submitScore(s.roundNumber);
                JOptionPane.showMessageDialog(this,
                    "Score saved to leaderboard!", "High Score",
                    JOptionPane.INFORMATION_MESSAGE);
            }
            currentPhase = GamePhase.GAME_OVER;
            sceneLayout.show(sceneContainer, "MENU");
            menuScene.showMenu();
        }
    }

    private int calculateRoundEarnings(GameState s) {
        int base   = s.capturesMade * 5;
        int alive  = s.piecesLeftStanding * 2;
        int spec   = s.specialMovesUsed * 3;
        int fast   = s.movesToEnd <= 10 ? 50 : s.movesToEnd <= 20 ? 30 :
            s.movesToEnd <= 30 ? 15 : s.movesToEnd <= 50 ? 5 : 0;
        int round  = s.roundNumber * 10;
        return Math.max(10, base + alive + spec + fast + round);
    }

    private void transitionToShop() {
        currentPhase = GamePhase.SHOPPING;

        GameState state = engine.getGameState();
        shopManager.saveGoldForNextGame(state.goldPlayer);
        shopManager.loadSurvivingPieces(state);

        sceneLayout.show(sceneContainer, "SHOP");
        shopScene.refresh();
    }

    private int countPiecesOnBoard(GameState gstate) {
        int count = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (gstate.board[r][c] != null) count++;
            }
        }
        return count;
    }

    public void startMatch() {
        if (currentPhase == GamePhase.MENU) startNewGameSession();
        else {
            currentPhase = GamePhase.PLAYING;
            sceneLayout.show(sceneContainer, "BOARD");
            boardView.refresh();
            AudioPlayer.playMusic("battle.wav", true);
        }
    }

    public ChessEngine getEngine() { return engine; }

    public GamePhase getCurrentPhase() { return currentPhase; }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
                    ResourceLoader.preloadPieceImages();
                    RoguelikeChessAppSwing app = new RoguelikeChessAppSwing();
                    app.setVisible(true);
                    AudioPlayer.playMusic("intro.wav", false);
            });
    }
}