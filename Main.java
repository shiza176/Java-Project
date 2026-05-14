import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

interface GameOverListener {
    void onGameOver(int winnerId);
}

public class Main {

    static JFrame frame;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                frame = new JFrame("Mini Games");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setResizable(false);
                showMenu();
            }
        });
    }

    static void showMenu() {
        frame.setContentPane(new MenuPanel());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    static void launchGame(int which) {
        JPanel game;

        if (which == 0) {
            BeachBallGame g = new BeachBallGame();
            g.onGameOver = new GameOverListener() {
                public void onGameOver(int w) {
                    SwingUtilities.invokeLater(new Runnable() { public void run() { showMenu(); } });
                }
            };
            g.onBack = new Runnable() { public void run() {
                SwingUtilities.invokeLater(new Runnable() { public void run() { showMenu(); } });
            }};
            game = g;
        } else if (which == 1) {
            KingYardGame g = new KingYardGame();
            g.onGameOver = new GameOverListener() {
                public void onGameOver(int w) {
                    SwingUtilities.invokeLater(new Runnable() { public void run() { showMenu(); } });
                }
            };
            g.onBack = new Runnable() { public void run() {
                SwingUtilities.invokeLater(new Runnable() { public void run() { showMenu(); } });
            }};
            game = g;
        } else {
            PaintFightGame g = new PaintFightGame();
            g.onGameOver = new GameOverListener() {
                public void onGameOver(int w) {
                    SwingUtilities.invokeLater(new Runnable() { public void run() { showMenu(); } });
                }
            };
            g.onBack = new Runnable() { public void run() {
                SwingUtilities.invokeLater(new Runnable() { public void run() { showMenu(); } });
            }};
            game = g;
        }

        frame.setContentPane(game);
        frame.pack();
        frame.setLocationRelativeTo(null);
        game.requestFocusInWindow();
    }
}

class MenuPanel extends JPanel {

    static final int W = 700, H = 480;

    static final String[] NAMES  = { "Beach Ball", "King Yard", "Paint Fight" };
    static final String[] EMOJIS = { "Beach Ball", "King Yard", "Paint Fight" };
    static final String[] DESCS  = {
        "P1: W / S     P2:  Up / Down     First to 5 points wins",
        "P1: WASD     P2: Arrow Keys     Hold the crown to win",
        "P1: WASD     P2: Arrow Keys     Most paint in 30 seconds"
    };
    static final Color[] COLORS = {
        new Color(0x1A, 0x8C, 0xFF),
        new Color(0xFF, 0xD7, 0x00),
        new Color(0xFF, 0x4D, 0x6D)
    };

    MenuPanel() {
        setPreferredSize(new Dimension(W, H));
        setLayout(null);
        setBackground(new Color(0x12, 0x12, 0x28));

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            JButton btn = createGameButton(idx);
            btn.setBounds(50, 130 + i * 100, W - 100, 78);
            btn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    Main.launchGame(idx);
                }
            });
            add(btn);
        }
    }

    private JButton createGameButton(final int idx) {
        final Color col = COLORS[idx];
        final String name = NAMES[idx];
        final String desc = DESCS[idx];

        JButton btn = new JButton() {
            public void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                boolean hover = getModel().isRollover();

                // Background
                g.setColor(hover ? new Color(35, 35, 70) : new Color(22, 22, 48));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);

                // Left accent bar
                g.setColor(col);
                g.fillRect(0, 12, 6, getHeight() - 24);
                g.fillRoundRect(0, 8, 6, getHeight() - 16, 6, 6);

                // Game name
                g.setFont(new Font("Arial Black", Font.BOLD, 19));
                g.setColor(Color.WHITE);
                g.drawString(name, 26, 32);

                // Description
                g.setFont(new Font("SansSerif", Font.PLAIN, 13));
                g.setColor(new Color(160, 160, 200));
                g.drawString(desc, 26, 56);

                // Play arrow
                g.setFont(new Font("SansSerif", Font.BOLD, 16));
                g.setColor(hover ? col : new Color(100, 100, 140));
                g.drawString("PLAY  >", getWidth() - 82, 44);

                // Border
                g.setColor(hover ? col : new Color(50, 50, 90));
                g.setStroke(new BasicStroke(1.5f));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            }
        };

        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Title
        g.setFont(new Font("Arial Black", Font.BOLD, 36));
        FontMetrics fm = g.getFontMetrics();
        String title = "Mini Games";
        g.setColor(Color.WHITE);
        g.drawString(title, (W - fm.stringWidth(title)) / 2, 75);

        // Subtitle
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        fm = g.getFontMetrics();
        String sub = "Select a game to play";
        g.setColor(new Color(140, 140, 180));
        g.drawString(sub, (W - fm.stringWidth(sub)) / 2, 102);
    }
}