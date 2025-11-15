package ashes;

import java.io.*;
import java.util.*;

public class ScoreManager {
    private static final String HIGH_SCORES_FILE = "highscores.dat";
    private List<ScoreEntry> highScores;
    private int currentScore;
    private String playerName;
    private static final double BASE_MULTIPLIER = 1.5;
    public ScoreManager() {
        this.highScores = new ArrayList<>();
        this.currentScore = 0;
        this.playerName = "Anonymous";
        loadHighScores();
    }

    public static class ScoreEntry implements Serializable, Comparable<ScoreEntry> {
        private static final long serialVersionUID = 1L;
        private String playerName;
        private int score;
        private Date date;
        private int roundsSurvived;
        public ScoreEntry(String playerName, int score, int roundsSurvived) {
            this.playerName = playerName;
            this.score = score;
            this.date = new Date();
            this.roundsSurvived = roundsSurvived;
        }

        public String getPlayerName() { return playerName; }

        public int getScore() { return score; }

        public Date getDate() { return date; }

        public int getRoundsSurvived() { return roundsSurvived; }

        @Override
        public int compareTo(ScoreEntry other) {
            return Integer.compare(other.score, this.score);
        }

        @Override
        public String toString() {
            return String.format("%-15s %10d %3d rounds %tF", playerName, score, roundsSurvived, date);
        }
    }

    public void setPlayerName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            this.playerName = name.trim();
        }
    }

    public void addMatchScore(int goldEarned, int aiDepth, int currentGold) {
        double depthMultiplier = Math.pow(BASE_MULTIPLIER, aiDepth);
        int matchScore = (int)(goldEarned * depthMultiplier);
        this.currentScore += matchScore;
    }

    public int getCurrentScore() {
        return currentScore;
    }

    public void resetSession() {
        this.currentScore = 0;
    }

    public boolean isHighScore() {
        if (highScores.size() < 10) return true;
        return currentScore > highScores.get(highScores.size() - 1).getScore();
    }

    public void submitScore(int roundsSurvived) {
        if (currentScore > 0) {
            ScoreEntry newEntry = new ScoreEntry(playerName, currentScore, roundsSurvived);
            highScores.add(newEntry);
            Collections.sort(highScores);
            if (highScores.size() > 10) {
                highScores = new ArrayList<>(highScores.subList(0, 10));
            }
            saveHighScores();
        }
    }

    public String getHighScoresTable() {
        if (highScores.isEmpty()) {
            return "No high scores yet!\nBe the first to set a record!";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Rank  Player          Score     Rounds  Date\n");
        sb.append("----  ------------  ----------  ------  ----------\n");
        for (int i = 0; i < highScores.size(); i++) {
            ScoreEntry entry = highScores.get(i);
            sb.append(String.format("%2d.   %-12s %10d   %3d     %tF%n",
                    i + 1, entry.getPlayerName(), entry.getScore(),
                    entry.getRoundsSurvived(), entry.getDate()));
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void loadHighScores() {
        try {
            File file = new File(HIGH_SCORES_FILE);
            if (!file.exists()) {
                return; // No high scores file yet
            }
            FileInputStream fis = new FileInputStream(file);
            ObjectInputStream ois = new ObjectInputStream(fis);
            highScores = (List<ScoreEntry>) ois.readObject();
            ois.close();
            fis.close();
            Collections.sort(highScores);
        } catch (Exception e) {
            System.err.println("Error loading high scores: " + e.getMessage());
            highScores = new ArrayList<>(); // Reset if corrupted
        }
    }

    private void saveHighScores() {
        try {
            FileOutputStream fos = new FileOutputStream(HIGH_SCORES_FILE);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(highScores);
            oos.close();
            fos.close();
        } catch (Exception e) {
            System.err.println("Error saving high scores: " + e.getMessage());
        }
    }

    public int getMinimumHighScore() {
        if (highScores.isEmpty()) return 0;
        return highScores.get(highScores.size() - 1).getScore();
    }

    public int getHighestScore() {
        if (highScores.isEmpty()) return 0;
        return highScores.get(0).getScore();
    }

    private int getRoundsSurvived() {
        return 1;
    }
}