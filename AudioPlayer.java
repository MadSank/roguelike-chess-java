 

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

/**
 * Sound switches and the game's sound effects, played by the synth in Music.
 * (playMusic / playSound still play WAV files from assets/audio if any are added later.)
 */
public class AudioPlayer {

    private static boolean soundEnabled = true;
    private static Clip musicClip;

    public static boolean isSoundEnabled() {
        return soundEnabled;
    }

    public static void setSoundEnabled(boolean enabled) {
        soundEnabled = enabled;
        Music.setMuted(!enabled);
        if (!enabled && musicClip != null && musicClip.isRunning()) {
            musicClip.stop();
        }
    }

    public static void playMusic(String filename, boolean loop) {
        if (!soundEnabled) 
            return;

        stopMusic();

        String[] paths = {
                "assets/audio/" + filename,
                "resources/audio/" + filename,
                "audio/" + filename
            };

        for (String path : paths) {
            try {
                File audioFile = new File(path);
                if (!audioFile.exists()) 
                {
                    continue;
                }

                AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
                musicClip = AudioSystem.getClip();
                musicClip.open(audioStream);
                musicClip.start();

                if (loop) {
                    musicClip.loop(Clip.LOOP_CONTINUOUSLY);
                }
                return;
            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
                //welp
            }
        }
        System.err.println("Could not play music from any path: " + filename);
    }

    public static void stopMusic() {
        if (musicClip != null) {
            if (musicClip.isRunning()) {
                musicClip.stop();
            }
            musicClip.close();
            musicClip = null;
        }
    }

    public static void playMoveSound() {
        if (soundEnabled) Music.sfx("move");
    }

    public static void playCaptureSound() {
        if (soundEnabled) Music.sfx("capture");
    }

    public static void playCheckmateSound() {
        if (soundEnabled) Music.sting("mate");
    }

    public static void playSound(String filename) {
        if (!soundEnabled) return;

        String[] paths = {
                "assets/audio/" + filename,
                "resources/audio/" + filename,
                "audio/" + filename
            };

        for (String path : paths) {
            try {
                File soundFile = new File(path);
                if (!soundFile.exists()) {
                    continue;
                }

                AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);
                clip.start();

                clip.addLineListener(event -> {
                            if (event.getType() == LineEvent.Type.STOP) {
                                clip.close();
                            }
                    });
                return;
            } catch (Exception e) {
                //welp
            }
        }
        System.err.println("Could not play sound from any path: " + filename);
    }
}
