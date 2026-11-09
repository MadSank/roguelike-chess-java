import java.io.*;
import java.util.*;

/**
 * Leaderboard: runs are ranked by the highest level beaten (DESIGN.md).
 * Saved in leaderboard.dat next to the game (not tracked by git).
 */
public class ScoreManager {
    private static final String LEADERBOARD_FILE = "leaderboard.dat";
    private static final int MAX_ENTRIES = 10;

    private List<ScoreEntry> entries = new ArrayList<>();
    private String playerName = "Anonymous";
    private final File file;

    public ScoreManager() {
        this(new File(LEADERBOARD_FILE));
    }

    /** For tests: use a different file. */
    public ScoreManager(File file) {
        this.file = file;
        load();
    }

    public static class ScoreEntry implements Serializable, Comparable<ScoreEntry> {
        private static final long serialVersionUID = 2L;
        private final String playerName;
        private final int highestLevelBeaten;
        private final Date date;

        public ScoreEntry(String playerName, int highestLevelBeaten) {
            this.playerName = playerName;
            this.highestLevelBeaten = highestLevelBeaten;
            this.date = new Date();
        }

        public String getPlayerName() { return playerName; }

        public int getHighestLevelBeaten() { return highestLevelBeaten; }

        public Date getDate() { return date; }

        @Override
        public int compareTo(ScoreEntry other) {
            return Integer.compare(other.highestLevelBeaten, this.highestLevelBeaten);
        }
    }

    public void setPlayerName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            this.playerName = name.trim();
        }
    }

    public String getPlayerName() {
        return playerName;
    }

    /** True if a run that beat this level would make the top 10. */
    public boolean qualifies(int highestLevelBeaten) {
        if (highestLevelBeaten <= 0) return false;
        if (entries.size() < MAX_ENTRIES) return true;
        return highestLevelBeaten > entries.get(entries.size() - 1).getHighestLevelBeaten();
    }

    /** Records a finished run. Returns true if it made the leaderboard. */
    public boolean submit(int highestLevelBeaten) {
        if (!qualifies(highestLevelBeaten)) return false;
        entries.add(new ScoreEntry(playerName, highestLevelBeaten));
        Collections.sort(entries);
        if (entries.size() > MAX_ENTRIES) {
            entries = new ArrayList<>(entries.subList(0, MAX_ENTRIES));
        }
        save();
        return true;
    }

    public List<ScoreEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public String getLeaderboardText() {
        if (entries.isEmpty()) {
            return "No runs recorded yet.\nBeat level 1 to get on the board!";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-5s %-14s %-12s %s%n", "Rank", "Player", "Best level", "Date"));
        for (int i = 0; i < entries.size(); i++) {
            ScoreEntry e = entries.get(i);
            sb.append(String.format("%-5d %-14s %-12d %tF%n", i + 1, e.getPlayerName(), e.getHighestLevelBeaten(), e.getDate()));
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!file.exists()) return;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            entries = new ArrayList<>((List<ScoreEntry>) in.readObject());
            Collections.sort(entries);
        } catch (Exception e) {
            System.err.println("Could not read leaderboard (" + e.getMessage() + "); starting a new one.");
            entries = new ArrayList<>();
        }
    }

    private void save() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(new ArrayList<>(entries));
        } catch (Exception e) {
            System.err.println("Could not save leaderboard: " + e.getMessage());
        }
    }
}
