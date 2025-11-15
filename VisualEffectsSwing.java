package ashes;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VisualEffectsSwing {
    public static void fadeIn(final JComponent component, final int durationMs) {
        component.setVisible(false);
        javax.swing.Timer timer = new javax.swing.Timer(50, null);
        final long startTime = System.currentTimeMillis();
        timer.addActionListener(new ActionListener() 
            {
                @Override
                public void actionPerformed(ActionEvent e) 
                {
                    long elapsed = System.currentTimeMillis() - startTime;
                    float progress = (float) elapsed / durationMs;
                    if (progress >= 1.0f) {
                        component.setVisible(true);
                        component.setOpaque(true);
                        ((javax.swing.Timer)e.getSource()).stop();
                    } else {
                        component.setVisible(true);
                        component.repaint();
                    }
                }
            });
        timer.start();
    }

    public static void tableFlipAnimation(final JComponent component, final Runnable onComplete) {
        javax.swing.Timer timer = new javax.swing.Timer(50, null);
        final long startTime = System.currentTimeMillis();
        final int duration = 900;
        timer.addActionListener(new ActionListener()
            {
                @Override
                public void actionPerformed(ActionEvent e) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    float progress = (float) elapsed / duration;
                    if (progress >= 1.0f) 
                    {
                        component.setVisible(false);
                        ((javax.swing.Timer)e.getSource()).stop();
                        if (onComplete != null)
                            onComplete.run();
                    } 
                    else
                        component.repaint();
                }
            });
        timer.start();
    }

    public static void screenShake(final JComponent component, final int intensity, final int durationMs) 
    {
        final Point originalLocation = component.getLocation();
        javax.swing.Timer timer = new javax.swing.Timer(50, null);
        final long startTime = System.currentTimeMillis();
        final Random random = new Random();
        timer.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    float progress = (float) elapsed / durationMs;
                    if (progress >= 1.0f) {
                        component.setLocation(originalLocation);
                        ((javax.swing.Timer)e.getSource()).stop();
                    } else {
                        int shakeX = random.nextInt(intensity * 2) - intensity;
                        int shakeY = random.nextInt(intensity * 2) - intensity;
                        component.setLocation(originalLocation.x + shakeX, originalLocation.y + shakeY);
                    }
                }
            });
        timer.start();
    }

    public static void pieceScatter(final JComponent container, final Point[] piecePositions) {
        javax.swing.Timer timer = new javax.swing.Timer(50, null);
        final long startTime = System.currentTimeMillis();
        final int duration = 2000;
        final Random random = new Random();
        final Point[] velocities = new Point[piecePositions.length];
        for (int i = 0; i < velocities.length; i++) {
            velocities[i] = new Point(
                random.nextInt(20) - 10,
                random.nextInt(20) - 10
            );
        }
        timer.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    float progress = (float) elapsed / duration;
                    if (progress >= 1.0f) {
                        ((javax.swing.Timer)e.getSource()).stop();
                    } else {
                        for (int i = 0; i < piecePositions.length; i++) {
                            if (piecePositions[i] != null) {
                                piecePositions[i].x += velocities[i].x;
                                piecePositions[i].y += velocities[i].y;
                                velocities[i].y += 1;
                            }
                        }
                        container.repaint();
                    }
                }
            });
        timer.start();
    }

    public static void goldBurnEffect(final Point location, final JComponent parent) {
        final ParticlePanel particlesPanel = new ParticlePanel(location);
        particlesPanel.setBounds(0, 0, parent.getWidth(), parent.getHeight());
        parent.add(particlesPanel);
        javax.swing.Timer timer = new javax.swing.Timer(50, null);
        final long startTime = System.currentTimeMillis();
        final int duration = 1000;
        timer.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    float progress = (float) elapsed / duration;
                    if (progress >= 1.0f) {
                        parent.remove(particlesPanel);
                        parent.repaint();
                        ((javax.swing.Timer)e.getSource()).stop();
                    } else {
                        particlesPanel.updateParticles();
                        particlesPanel.repaint();
                    }
                }
            });
        timer.start();
    }

    private static class ParticlePanel extends JPanel {
        private List<Point> particles;
        private List<Point> particleVelocities;
        private List<Color> particleColors;
        public ParticlePanel(Point location) {
            setOpaque(false);
            particles = new ArrayList<>();
            particleVelocities = new ArrayList<>();
            particleColors = new ArrayList<>();
            Random random = new Random();
            for (int i = 0; i < 20; i++) {
                particles.add(new Point(location));
                particleVelocities.add(new Point(
                        random.nextInt(10) - 5,
                        random.nextInt(10) - 15
                    ));
                particleColors.add(new Color(
                        255,
                        200 + random.nextInt(55),
                        random.nextInt(100)
                    ));
            }
        }

        public void updateParticles() {
            for (int i = 0; i < particles.size(); i++) {
                Point p = particles.get(i);
                Point v = particleVelocities.get(i);
                p.x += v.x;
                p.y += v.y;
                v.y += 1;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            for (int i = 0; i < particles.size(); i++) {
                Point p = particles.get(i);
                g.setColor(particleColors.get(i));
                g.fillOval(p.x, p.y, 4, 4);
            }
        }
    }
}