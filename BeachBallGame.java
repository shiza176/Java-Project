import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.sound.sampled.*;

public class BeachBallGame extends JPanel implements ActionListener, KeyListener {

    // ─── Window & Game Constants ───────────────────────────────────────────────
    static final int WIDTH       = 900;
    static final int HEIGHT      = 600;
    static final int WIN_SCORE   = 5;
    static final int FPS         = 60;

    // ─── Paddle Constants ──────────────────────────────────────────────────────
    static final int PADDLE_W      = 14;
    static final int PADDLE_H      = 90;
    static final int PADDLE_SPEED  = 18;
    static final int PADDLE_MARGIN = 30;

    // ─── Ball Constants ────────────────────────────────────────────────────────
    static final int    BALL_SIZE      = 28;
    static final double INIT_SPEED     = 6.5;
    static final double MAX_SPEED      = 15.0;
    static final double SPEED_INC      = 0.90;
    static final double TIME_SPEED_INC = 0.12;

    // ─── Colors ────────────────────────────────────────────────────────────────
    static final Color COL_SKY_TOP  = new Color(0x1A, 0x8C, 0xFF);
    static final Color COL_SKY_BTM  = new Color(0x87, 0xCE, 0xFF);
    static final Color COL_SAND     = new Color(0xF4, 0xD0, 0x6A);
    static final Color COL_SAND_DARK= new Color(0xE0, 0xB9, 0x4E);
    static final Color COL_WATER    = new Color(0x00, 0xB4, 0xD8, 180);
    static final Color COL_NET      = new Color(0xFF, 0xFF, 0xFF, 160);
    static final Color COL_P1       = new Color(0xFF, 0x6B, 0x35);
    static final Color COL_P2       = new Color(0x2E, 0xCC, 0x71);
    static final Color COL_SCORE_BG = new Color(0x00, 0x00, 0x00, 100);

    // ─── Game State ────────────────────────────────────────────────────────────
    enum State { COUNTDOWN, PLAYING, POINT_SCORED, GAME_OVER }
    State gameState = State.COUNTDOWN;

    // ─── Countdown ─────────────────────────────────────────────────────────────
    static final int COUNTDOWN_SECONDS = 3;
    int countdownTicks = COUNTDOWN_SECONDS * FPS;

    // ─── Paddles ───────────────────────────────────────────────────────────────
    double p1Y, p2Y;
    boolean p1Up, p1Down, p2Up, p2Down;

    // ─── Ball ──────────────────────────────────────────────────────────────────
    double ballX, ballY, ballVX, ballVY, ballSpeed;

    // ─── Scores ────────────────────────────────────────────────────────────────
    int score1, score2;

    // ─── Misc ──────────────────────────────────────────────────────────────────
    Timer  timer;
    int    pauseTicks = 0;
    String winnerName = "";
    int    tickCounter = 0;
    long   frameCount  = 0;

    // ─── Sound ─────────────────────────────────────────────────────────────────
    String  scoreSound   = "score.wav";
    boolean soundPlaying = false;

    // ─── Hub callback ──────────────────────────────────────────────────────────
    GameOverListener onGameOver = null;
    Runnable onBack = null;

    // ─── Background ────────────────────────────────────────────────────────────
    BufferedImage bgImage;

    // ══════════════════════════════════════════════════════════════════════════
    public BeachBallGame() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        addKeyListener(this);
        buildBackground();
        initGame();
        timer = new Timer(1000 / FPS, this);
        timer.start();
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Background
    // ──────────────────────────────────────────────────────────────────────────
    private void buildBackground() {
        bgImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bgImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint sky = new GradientPaint(0, 0, COL_SKY_TOP, 0, HEIGHT * 0.55f, COL_SKY_BTM);
        g.setPaint(sky);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Sun
        g.setColor(new Color(0xFF, 0xE0, 0x40, 220));
        g.fillOval(WIDTH / 2 - 38, 28, 76, 76);
        g.setColor(new Color(0xFF, 0xF0, 0x80, 80));
        g.setStroke(new BasicStroke(8));
        g.drawOval(WIDTH / 2 - 50, 16, 100, 100);

        // Water
        g.setColor(COL_WATER);
        g.fillRect(0, (int)(HEIGHT * 0.55), WIDTH, (int)(HEIGHT * 0.12));

        // Sand
        GradientPaint sand = new GradientPaint(0, (int)(HEIGHT * 0.62), COL_SAND, 0, HEIGHT, COL_SAND_DARK);
        g.setPaint(sand);
        g.fillRect(0, (int)(HEIGHT * 0.62), WIDTH, HEIGHT);

        // Wave lines
        g.setColor(new Color(0xFF, 0xFF, 0xFF, 60));
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int wx = 0; wx < WIDTH; wx += 80)
            g.drawArc(wx, (int)(HEIGHT * 0.56), 80, 20, 0, 180);

        // Net
        g.setColor(COL_NET);
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1f, new float[]{8, 10}, 0));
        g.drawLine(WIDTH / 2, 0, WIDTH / 2, HEIGHT);

        g.dispose();
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Init / Reset
    // ──────────────────────────────────────────────────────────────────────────
    private void initGame() {
        score1 = 0; score2 = 0;
        resetPaddles();
        resetBall(1);
        countdownTicks = COUNTDOWN_SECONDS * FPS;
        gameState = State.COUNTDOWN;
    }

    private void startAfterCountdown() {
        gameState = State.PLAYING;
    }

    private void resetPaddles() {
        p1Y = (HEIGHT - PADDLE_H) / 2.0;
        p2Y = (HEIGHT - PADDLE_H) / 2.0;
    }

    private void resetBall(int serveDir) {
        ballX = WIDTH  / 2.0 - BALL_SIZE / 2.0;
        ballY = HEIGHT / 2.0 - BALL_SIZE / 2.0;
        ballSpeed = INIT_SPEED;
        tickCounter = 0;
        double angle = Math.toRadians(30 + Math.random() * 30);
        ballVX = ballSpeed * Math.cos(angle) * serveDir;
        ballVY = ballSpeed * Math.sin(angle) * (Math.random() < 0.5 ? 1 : -1);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Game Loop
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameState == State.COUNTDOWN) {
            countdownTicks--;
            if (countdownTicks <= 0) startAfterCountdown();
        } else if (gameState == State.PLAYING) {
            updatePaddles();
            updateBall();
            applyTimeAcceleration();
        } else if (gameState == State.POINT_SCORED) {
            // Only resume once sound has finished playing
            if (!soundPlaying) gameState = State.PLAYING;
        }
        frameCount++;
        repaint();
    }

    private void applyTimeAcceleration() {
        tickCounter++;
        if (tickCounter % FPS == 0 && ballSpeed < MAX_SPEED) {
            ballSpeed = Math.min(ballSpeed + TIME_SPEED_INC, MAX_SPEED);
            double mag = Math.sqrt(ballVX * ballVX + ballVY * ballVY);
            if (mag > 0) { ballVX = (ballVX / mag) * ballSpeed; ballVY = (ballVY / mag) * ballSpeed; }
        }
    }

    private void updatePaddles() {
        if (p1Up)   p1Y -= PADDLE_SPEED;
        if (p1Down) p1Y += PADDLE_SPEED;
        if (p2Up)   p2Y -= PADDLE_SPEED;
        if (p2Down) p2Y += PADDLE_SPEED;
        p1Y = Math.max(0, Math.min(HEIGHT - PADDLE_H, p1Y));
        p2Y = Math.max(0, Math.min(HEIGHT - PADDLE_H, p2Y));
    }

    private void updateBall() {
        ballX += ballVX;
        ballY += ballVY;

        if (ballY <= 0)                 { ballY = 0;               ballVY =  Math.abs(ballVY); }
        if (ballY + BALL_SIZE >= HEIGHT){ ballY = HEIGHT - BALL_SIZE; ballVY = -Math.abs(ballVY); }

        int p1X = PADDLE_MARGIN;
        if (ballVX < 0 && ballX <= p1X + PADDLE_W && ballX + BALL_SIZE >= p1X
         && ballY + BALL_SIZE >= p1Y && ballY <= p1Y + PADDLE_H) {
            ballX = p1X + PADDLE_W;
            reflectBall(p1Y);
        }

        int p2X = WIDTH - PADDLE_MARGIN - PADDLE_W;
        if (ballVX > 0 && ballX + BALL_SIZE >= p2X && ballX <= p2X + PADDLE_W
         && ballY + BALL_SIZE >= p2Y && ballY <= p2Y + PADDLE_H) {
            ballX = p2X - BALL_SIZE;
            reflectBall(p2Y);
        }

        if (ballX + BALL_SIZE < 0) { score2++; checkWin(2); }
        else if (ballX > WIDTH)    { score1++; checkWin(1); }
    }

    private void reflectBall(double paddleY) {
        double norm  = ((ballY + BALL_SIZE / 2.0) - (paddleY + PADDLE_H / 2.0)) / (PADDLE_H / 2.0);
        double angle = norm * Math.toRadians(60);
        ballSpeed = Math.min(ballSpeed + SPEED_INC, MAX_SPEED);
        ballVX = (ballVX < 0 ? 1 : -1) * ballSpeed * Math.cos(angle);
        ballVY = ballSpeed * Math.sin(angle);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Sound
    // ──────────────────────────────────────────────────────────────────────────
    private void playSound() {
        soundPlaying = true;
        new Thread(() -> {
            try {
                AudioInputStream ais = AudioSystem.getAudioInputStream(new File(scoreSound));
                AudioFormat fmt = ais.getFormat();
                AudioFormat target = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    fmt.getSampleRate(), 16,
                    fmt.getChannels(), fmt.getChannels() * 2,
                    fmt.getSampleRate(), false);
                AudioInputStream converted = AudioSystem.getAudioInputStream(target, ais);
                Clip clip = AudioSystem.getClip();
                clip.open(converted);
                clip.addLineListener(ev -> {
                    if (ev.getType() == LineEvent.Type.STOP) {
                        clip.close();
                        soundPlaying = false;  // signal game to resume
                    }
                });
                clip.start();
            } catch (Exception ex) {
                System.out.println("Sound error: " + ex.getMessage());
                soundPlaying = false;  // resume even if sound fails
            }
        }).start();
    }

    private void checkWin(int scorer) {
        playSound();
        if (scorer == 1 && score1 >= WIN_SCORE) {
            winnerName = "Player 1"; gameState = State.GAME_OVER;
        } else if (scorer == 2 && score2 >= WIN_SCORE) {
            winnerName = "Player 2"; gameState = State.GAME_OVER;
        } else {
            resetBall(scorer == 1 ? -1 : 1);
            pauseTicks = FPS;
            gameState  = State.POINT_SCORED;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Rendering
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.drawImage(bgImage, 0, 0, null);

        // Always draw paddles + ball so players see them during countdown
        drawPaddle(g, PADDLE_MARGIN, (int) p1Y, COL_P1);
        drawPaddle(g, WIDTH - PADDLE_MARGIN - PADDLE_W, (int) p2Y, COL_P2);
        drawBall(g);

        if (gameState == State.COUNTDOWN) {
            drawCountdown(g);
            return;
        }

        drawScoreboard(g);
        drawSpeedBar(g);

        if (gameState == State.POINT_SCORED) drawPointFlash(g);
        if (gameState == State.GAME_OVER)    drawGameOver(g);
    }

    // ── Countdown 3-2-1 GO! ───────────────────────────────────────────────────
    private void drawCountdown(Graphics2D g) {
        // Semi-dark overlay
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        int secondsLeft = (countdownTicks + FPS - 1) / FPS;
        String label = secondsLeft > 0 ? String.valueOf(secondsLeft) : "GO!";

        // Pulse — grows as second ticks down
        float tickInSecond = countdownTicks % FPS;
        float pulse = 1.0f + 0.45f * (tickInSecond / FPS);
        int fontSize = (int)(130 * pulse);

        g.setFont(new Font("Arial Black", Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();

        Color numCol = secondsLeft == 3 ? new Color(0xFF, 0x6B, 0x35)
                     : secondsLeft == 2 ? new Color(0xFF, 0xD7, 0x00)
                     :                    new Color(0x2E, 0xCC, 0x71);

        // Shadow
        g.setColor(new Color(0, 0, 0, 180));
        g.drawString(label, (WIDTH - fm.stringWidth(label)) / 2 + 5, HEIGHT / 2 + 50);
        // Main
        g.setColor(numCol);
        g.drawString(label, (WIDTH - fm.stringWidth(label)) / 2, HEIGHT / 2 + 45);

        // Controls hint at bottom
        g.setFont(new Font("Arial Black", Font.BOLD, 15));
        g.setColor(new Color(255, 255, 255, 170));
        String hint = "P1: W / S     P2: ↑ / ↓";
        fm = g.getFontMetrics();
        g.drawString(hint, (WIDTH - fm.stringWidth(hint)) / 2, HEIGHT - 35);
    }

    // ── Speed Bar ─────────────────────────────────────────────────────────────
    private void drawSpeedBar(Graphics2D g) {
        int barW = 120, barH = 8;
        int bx = WIDTH / 2 - barW / 2, by = 72;
        double ratio = Math.max(0, Math.min(1, (ballSpeed - INIT_SPEED) / (MAX_SPEED - INIT_SPEED)));

        g.setColor(new Color(0, 0, 0, 80));
        g.fillRoundRect(bx, by, barW, barH, 6, 6);

        int r = (int)(255 * ratio), gv = (int)(255 * (1 - ratio));
        g.setColor(new Color(r, gv, 30, 200));
        g.fillRoundRect(bx, by, (int)(barW * ratio), barH, 6, 6);

        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(new Color(255, 255, 255, 160));
        g.drawString("SPEED", bx + barW + 6, by + 8);

        // Back hint
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(new Color(255, 255, 255, 80));
        g.drawString("ESC = Menu", WIDTH - 75, 15);
    }

    // ── Paddle ────────────────────────────────────────────────────────────────
    private void drawPaddle(Graphics2D g, int x, int y, Color col) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRoundRect(x + 4, y + 4, PADDLE_W, PADDLE_H, 10, 10);
        GradientPaint gp = new GradientPaint(x, y, col.brighter(), x + PADDLE_W, y, col.darker());
        g.setPaint(gp);
        g.fillRoundRect(x, y, PADDLE_W, PADDLE_H, 10, 10);
        g.setColor(new Color(255, 255, 255, 80));
        g.fillRoundRect(x + 2, y + 4, PADDLE_W / 2, PADDLE_H / 3, 6, 6);
    }

    // ── Ball ─────────────────────────────────────────────────────────────────
    private void drawBall(Graphics2D g) {
        int bx = (int) ballX, by = (int) ballY;
        g.setColor(new Color(0, 0, 0, 50));
        g.fillOval(bx + 4, by + 6, BALL_SIZE, BALL_SIZE);

        Color[] stripes = {
            new Color(0xFF, 0x4D, 0x4D), Color.WHITE,
            new Color(0x4D, 0x9F, 0xFF), new Color(0xFF, 0xD7, 0x00)
        };
        int segAngle = 360 / stripes.length;
        for (int i = 0; i < stripes.length; i++) {
            g.setColor(stripes[i]);
            g.fillArc(bx, by, BALL_SIZE, BALL_SIZE, i * segAngle, segAngle);
        }
        g.setColor(new Color(255, 255, 255, 120));
        g.fillOval(bx + 5, by + 3, BALL_SIZE / 3, BALL_SIZE / 4);
        g.setColor(new Color(0, 0, 0, 60));
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(bx, by, BALL_SIZE, BALL_SIZE);
    }

    // ── Scoreboard ───────────────────────────────────────────────────────────
    private void drawScoreboard(Graphics2D g) {
        g.setColor(COL_SCORE_BG);
        g.fillRoundRect(WIDTH / 2 - 110, 12, 220, 54, 30, 30);

        g.setFont(new Font("Arial Black", Font.BOLD, 34));
        g.setColor(COL_P1);
        String s1 = String.valueOf(score1);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s1, WIDTH / 2 - 70 - fm.stringWidth(s1) / 2, 52);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial Black", Font.BOLD, 28));
        g.drawString(":", WIDTH / 2 - 8, 50);

        g.setFont(new Font("Arial Black", Font.BOLD, 34));
        g.setColor(COL_P2);
        g.drawString(String.valueOf(score2), WIDTH / 2 + 48, 52);

        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(new Color(COL_P1.getRed(), COL_P1.getGreen(), COL_P1.getBlue(), 200));
        g.drawString("P1  W S", PADDLE_MARGIN, HEIGHT - 10);
        g.setColor(new Color(COL_P2.getRed(), COL_P2.getGreen(), COL_P2.getBlue(), 200));
        String p2label = "↑ ↓  P2";
        g.drawString(p2label, WIDTH - PADDLE_MARGIN - fm.stringWidth(p2label) - 20, HEIGHT - 10);
    }

    // ── Point Flash ──────────────────────────────────────────────────────────
    private void drawPointFlash(Graphics2D g) {
        g.setColor(new Color(255, 255, 255, 60));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setFont(new Font("Arial Black", Font.BOLD, 52));
        String msg = score1 > score2 ? "P1 scores! 🎯" : "P2 scores! 🎯";
        FontMetrics fm = g.getFontMetrics();
        int tx = (WIDTH - fm.stringWidth(msg)) / 2;
        g.setColor(new Color(0, 0, 0, 120));
        g.drawString(msg, tx + 3, HEIGHT / 2 + 3);
        g.setColor(Color.WHITE);
        g.drawString(msg, tx, HEIGHT / 2);
    }

    // ── Game Over ────────────────────────────────────────────────────────────
    private void drawGameOver(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        g.setFont(new Font("Arial Black", Font.BOLD, 56));
        FontMetrics fm = g.getFontMetrics();
        String msg = winnerName + " Wins! 🏆";
        Color wCol = winnerName.contains("1") ? COL_P1 : COL_P2;
        g.setColor(new Color(0, 0, 0, 180));
        g.drawString(msg, (WIDTH - fm.stringWidth(msg)) / 2 + 4, HEIGHT / 2 - 30 + 4);
        g.setColor(wCol);
        g.drawString(msg, (WIDTH - fm.stringWidth(msg)) / 2, HEIGHT / 2 - 30);

        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        g.setColor(Color.WHITE);
        String scores = "Final: " + score1 + " — " + score2;
        fm = g.getFontMetrics();
        g.drawString(scores, (WIDTH - fm.stringWidth(scores)) / 2, HEIGHT / 2 + 30);

        g.setFont(new Font("Arial Black", Font.BOLD, 22));
        String restart = "ENTER — Play Again   |   ESC — Main Menu";
        fm = g.getFontMetrics();
        int alpha = 160 + (int)(95 * Math.sin(System.currentTimeMillis() / 400.0));
        g.setColor(new Color(255, 255, 255, Math.min(255, alpha)));
        g.drawString(restart, (WIDTH - fm.stringWidth(restart)) / 2, HEIGHT / 2 + 100);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Key Events
    // ══════════════════════════════════════════════════════════════════════════
    @Override public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W:     p1Up   = true; break;
            case KeyEvent.VK_S:     p1Down = true; break;
            case KeyEvent.VK_UP:    p2Up   = true; break;
            case KeyEvent.VK_DOWN:  p2Down = true; break;
            case KeyEvent.VK_ENTER:
                if (gameState == State.GAME_OVER) initGame();
                break;
            case KeyEvent.VK_ESCAPE:
                if (gameState == State.GAME_OVER) {
                    if (onBack != null) { timer.stop(); onBack.run(); }
                } else {
                    if (onBack != null) { timer.stop(); onBack.run(); }
                }
                break;
        }
    }

    @Override public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W:     p1Up   = false; break;
            case KeyEvent.VK_S:     p1Down = false; break;
            case KeyEvent.VK_UP:    p2Up   = false; break;
            case KeyEvent.VK_DOWN:  p2Down = false; break;
        }
    }

    @Override public void keyTyped(KeyEvent e) {}
}