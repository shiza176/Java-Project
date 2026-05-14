import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class PaintFightGame extends JPanel implements ActionListener, KeyListener {

    // ─── Constants ─────────────────────────────────────────────────────────────
    static final int WIDTH       = 900;
    static final int HEIGHT      = 620;
    static final int FPS         = 60;
    static final int ROUND_SECS  = 30;
    static final int ROUND_TICKS = ROUND_SECS * FPS;

    // Grid
    static final int TILE     = 20;
    static final int COLS     = WIDTH / TILE;
    static final int ROWS     = (HEIGHT - 80) / TILE;
    static final int GRID_TOP = 80;

    // Player
    static final int   P_SIZE      = 22;
    static final float P_SPEED     = 3.5f;
    static final int   PAINT_RADIUS = 18;  // radius of smooth paint circle trail

    // Boosters
    static final int   BOOST_SIZE       = 24;
    static final int   BOOST_INTERVAL   = 2 * FPS;   // new booster every 8 seconds
    static final int   SPEED_BOOST_TICKS= 4 * FPS;   // speed boost lasts 4 seconds
    static final float SPEED_BOOST_MULT = 2.0f;       // 2× speed
    static final int   SPLASH_BOOST_RADIUS = 80;      // splash paint radius

    // Tile ownership
    static final int EMPTY = 0, P1 = 1, P2 = 2;

    // ─── Colors ────────────────────────────────────────────────────────────────
    static final Color COL_BG       = Color.WHITE;
    static final Color COL_EMPTY    = new Color(0xF0, 0xF0, 0xF0);   // very light grey
    static final Color COL_P1       = new Color(0xFF, 0x4D, 0x6D);
    static final Color COL_P2       = new Color(0x00, 0xC2, 0xFF);
    static final Color COL_P1_PAINT = new Color(0xFF, 0x4D, 0x6D);   // fully opaque — no alpha
    static final Color COL_P2_PAINT = new Color(0x00, 0xC2, 0xFF);   // fully opaque — no alpha
    static final Color COL_HUD_BG   = new Color(0x1A, 0x1A, 0x2E);
    static final Color COL_TIMER_LOW= new Color(0xFF, 0x4A, 0x4A);

    // ─── Game State ────────────────────────────────────────────────────────────
    enum State { COUNTDOWN, PLAYING, GAME_OVER }
    State gameState = State.COUNTDOWN;

    // ─── Countdown ─────────────────────────────────────────────────────────────
    static final int COUNTDOWN_SECONDS = 3;
    int countdownTicks = COUNTDOWN_SECONDS * FPS;

    // ─── Paint Canvas (pixel-based, no grid) ───────────────────────────────────
    BufferedImage paintCanvas;   // each pixel stores 0=empty, P1 color, P2 color
    Graphics2D    canvasG;

    // For score counting — sample canvas periodically
    int p1pixels, p2pixels, totalPixels;
    float p1x, p1y, p2x, p2y;
    boolean p1up, p1down, p1left, p1right;
    boolean p2up, p2down, p2left, p2right;

    // ─── Timer ─────────────────────────────────────────────────────────────────
    int ticksLeft;

    // ─── Misc ──────────────────────────────────────────────────────────────────
    Timer  swingTimer;
    long   frameCount    = 0;
    int    splatSoundTimer = 0;  // throttle movement splat sound
    String resultMsg  = "";

    // ─── Hub callback ──────────────────────────────────────────────────────────
    GameOverListener onGameOver = null;
    Runnable onBack = null;

    // ─── Paint splat particles ─────────────────────────────────────────────────
    static class Splat {
        float x, y, vx, vy, life;
        Color col;
        Splat(float x, float y, Color c) {
            this.x = x; this.y = y; col = c;
            double a = Math.random() * Math.PI * 2;
            float spd = 1f + (float) Math.random() * 3f;
            vx = (float) Math.cos(a) * spd;
            vy = (float) Math.sin(a) * spd;
            life = 18f + (float) Math.random() * 12f;
        }
        void update() { x += vx; y += vy; vy += 0.15f; life--; }
        boolean dead() { return life <= 0; }
    }
    java.util.List<Splat> splats = new java.util.ArrayList<>();

    // ─── Boosters ──────────────────────────────────────────────────────────────
    enum BoostType { SPLASH, SPEED }

    static class Booster {
        float x, y;
        BoostType type;
        Booster(float x, float y, BoostType t) { this.x = x; this.y = y; this.type = t; }
    }

    java.util.List<Booster> boosters = new java.util.ArrayList<>();
    int  boosterSpawnTimer = BOOST_INTERVAL;
    int  p1SpeedTicks = 0,  p2SpeedTicks = 0;
    boolean nextBoosterIsSpeed = false;  // toggles each spawn

    // ══════════════════════════════════════════════════════════════════════════
    public PaintFightGame() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(COL_BG);
        setFocusable(true);
        addKeyListener(this);
        initGame();

        swingTimer = new Timer(1000 / FPS, this);
        swingTimer.start();
    }

    // ──────────────────────────────────────────────────────────────────────────
    private void initGame() {
        // Create pixel paint canvas
        int canvasH = ROWS * TILE;
        paintCanvas = new BufferedImage(WIDTH, canvasH, BufferedImage.TYPE_INT_ARGB);
        canvasG = paintCanvas.createGraphics();
        canvasG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        canvasG.setColor(new Color(0, 0, 0, 0));
        canvasG.fillRect(0, 0, WIDTH, canvasH);

        totalPixels = WIDTH * canvasH;
        p1pixels = 0; p2pixels = 0;
        ticksLeft = ROUND_TICKS;
        resultMsg = "";
        splats.clear();
        boosters.clear();
        boosterSpawnTimer = BOOST_INTERVAL;
        p1SpeedTicks = 0; p2SpeedTicks = 0;
        nextBoosterIsSpeed = false;

        p1x = TILE * 2;
        p1y = GRID_TOP + TILE * 2;
        p2x = WIDTH - TILE * 2 - P_SIZE;
        p2y = GRID_TOP + ROWS * TILE - TILE * 2 - P_SIZE;

        countdownTicks = COUNTDOWN_SECONDS * FPS;
        gameState = State.COUNTDOWN;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Game Loop
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameState == State.COUNTDOWN) {
            countdownTicks--;
            if (countdownTicks <= 0) gameState = State.PLAYING;
        } else if (gameState == State.PLAYING) {
            movePlayers();
            paintTiles();
            updateSplats();
            updateBoosters();
            if (p1SpeedTicks > 0) p1SpeedTicks--;
            if (p2SpeedTicks > 0) p2SpeedTicks--;
            if (frameCount % 15 == 0) countPixels();
            ticksLeft--;
            if (ticksLeft <= 0) endGame();
        }
        frameCount++;
        repaint();
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Movement
    // ──────────────────────────────────────────────────────────────────────────
    private void movePlayers() {
        float spd1 = p1SpeedTicks > 0 ? P_SPEED * SPEED_BOOST_MULT : P_SPEED;
        float spd2 = p2SpeedTicks > 0 ? P_SPEED * SPEED_BOOST_MULT : P_SPEED;
        float dx1 = 0, dy1 = 0, dx2 = 0, dy2 = 0;
        if (p1up)    dy1 -= spd1;
        if (p1down)  dy1 += spd1;
        if (p1left)  dx1 -= spd1;
        if (p1right) dx1 += spd1;
        if (p2up)    dy2 -= spd2;
        if (p2down)  dy2 += spd2;
        if (p2left)  dx2 -= spd2;
        if (p2right) dx2 += spd2;

        if (dx1 != 0 && dy1 != 0) { dx1 *= 0.707f; dy1 *= 0.707f; }
        if (dx2 != 0 && dy2 != 0) { dx2 *= 0.707f; dy2 *= 0.707f; }

        p1x = clampX(p1x + dx1);
        p1y = clampY(p1y + dy1);
        p2x = clampX(p2x + dx2);
        p2y = clampY(p2y + dy2);

        // Play splat sound periodically while any player is moving
        boolean anyMoving = (dx1 != 0 || dy1 != 0 || dx2 != 0 || dy2 != 0);
        if (anyMoving) {
            splatSoundTimer--;
            if (splatSoundTimer <= 0) {
                playSplatSound();
                splatSoundTimer = 12; // play roughly every 12 frames (~5x/sec)
            }
        } else {
            splatSoundTimer = 0; // reset so next movement starts a splat immediately
        }
    }

    private float clampX(float x) { return Math.max(0, Math.min(COLS * TILE - P_SIZE, x)); }
    private float clampY(float y) { return Math.max(GRID_TOP, Math.min(GRID_TOP + ROWS * TILE - P_SIZE, y)); }

    // ──────────────────────────────────────────────────────────────────────────
    //  Paint tiles
    // ──────────────────────────────────────────────────────────────────────────
    private void paintTiles() {
        // Paint smooth circles onto the canvas at player center positions
        paintCircle(p1x + P_SIZE / 2f, p1y + P_SIZE / 2f, COL_P1_PAINT, P1);
        paintCircle(p2x + P_SIZE / 2f, p2y + P_SIZE / 2f, COL_P2_PAINT, P2);
    }

    private void paintCircle(float wx, float wy, Color col, int owner) {
        // Draw filled circle on canvas at player position
        float cy = wy - GRID_TOP;
        canvasG.setColor(col);
        canvasG.fillOval((int)(wx - PAINT_RADIUS), (int)(cy - PAINT_RADIUS),
                          PAINT_RADIUS * 2, PAINT_RADIUS * 2);
        // Spawn splats occasionally
        if (Math.random() < 0.15) {
            Color sc = (owner == P1) ? COL_P1 : COL_P2;
            splats.add(new Splat(wx, wy, sc));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Booster Logic
    // ──────────────────────────────────────────────────────────────────────────
    private void updateBoosters() {
        // Spawn a new booster periodically — alternate types
        boosterSpawnTimer--;
        if (boosterSpawnTimer <= 0) {
            BoostType type = nextBoosterIsSpeed ? BoostType.SPEED : BoostType.SPLASH;
            nextBoosterIsSpeed = !nextBoosterIsSpeed;
            float bx = 80 + (float)(Math.random() * (WIDTH - 160));
            float by = GRID_TOP + 40 + (float)(Math.random() * (ROWS * TILE - 80));
            boosters.add(new Booster(bx, by, type));
            boosterSpawnTimer = BOOST_INTERVAL;
            return; // BUG FIX: skip collision check this frame so the booster
                    // is not instantly picked up the moment it spawns
        }

        // Check pickup — use circular distance, not rectangular box
        java.util.Iterator<Booster> it = boosters.iterator();
        while (it.hasNext()) {
            Booster b = it.next();
            float p1cx = p1x + P_SIZE / 2f, p1cy = p1y + P_SIZE / 2f;
            float p2cx = p2x + P_SIZE / 2f, p2cy = p2y + P_SIZE / 2f;
            float pickup = (P_SIZE + BOOST_SIZE) / 2f;

            boolean p1hit = Math.hypot(p1cx - b.x, p1cy - b.y) < pickup;
            boolean p2hit = Math.hypot(p2cx - b.x, p2cy - b.y) < pickup;

            if (p1hit || p2hit) {
                float px  = p1hit ? p1cx : p2cx;
                float py  = p1hit ? p1cy : p2cy;
                Color col = p1hit ? COL_P1_PAINT : COL_P2_PAINT;
                Color sCol= p1hit ? COL_P1 : COL_P2;

                if (b.type == BoostType.SPLASH) {
                    canvasG.setColor(col);
                    float cy = py - GRID_TOP;
                    canvasG.fillOval((int)(px - SPLASH_BOOST_RADIUS),
                                     (int)(cy - SPLASH_BOOST_RADIUS),
                                     SPLASH_BOOST_RADIUS * 2, SPLASH_BOOST_RADIUS * 2);
                    for (int i = 0; i < 20; i++) splats.add(new Splat(px, py, sCol));
                    playSplashSound();   // plays splash_boost.wav
                } else {
                    if (p1hit) p1SpeedTicks = SPEED_BOOST_TICKS;
                    else       p2SpeedTicks = SPEED_BOOST_TICKS;
                    for (int i = 0; i < 10; i++) splats.add(new Splat(px, py, sCol));
                    playSpeedSound();    // plays speed_boost.wav
                }
                it.remove();
            }
        }
    }

    int sampleTick = 0;

    private void countPixels() {
        p1pixels = 0; p2pixels = 0;
        int canvasH = ROWS * TILE;
        for (int y = 0; y < canvasH; y += 2) {
            for (int x = 0; x < WIDTH; x += 2) {
                int px = paintCanvas.getRGB(x, y);
                int a = (px >> 24) & 0xFF;
                if (a < 30) continue;  // skip transparent/empty
                int r = (px >> 16) & 0xFF;
                int b =  px        & 0xFF;
                // P1 is red-dominant, P2 is blue-dominant
                if (r > b && r > 100) p1pixels++;
                else if (b > r && b > 100) p2pixels++;
            }
        }
        // Scale back (sampled every 2nd pixel in both axes)
        p1pixels *= 4;
        p2pixels *= 4;
    }

    private void updateSplats() {
        splats.removeIf(Splat::dead);
        for (Splat s : splats) s.update();
    }

    private void endGame() {
        countPixels();
        if (p1pixels > p2pixels)      resultMsg = "Player 1 Wins! 🎨";
        else if (p2pixels > p1pixels) resultMsg = "Player 2 Wins! 🎨";
        else                          resultMsg = "It's a TIE! 🤝";
        gameState = State.GAME_OVER;
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

        drawGrid(g);
        drawSplats(g);
        drawBoosters(g);
        drawPlayers(g);
        drawHUD(g);

        if (gameState == State.COUNTDOWN) drawCountdown(g);
        if (gameState == State.GAME_OVER) drawGameOver(g);
    }

    // ── Grid — draw smooth pixel canvas ──────────────────────────────────────
    private void drawGrid(Graphics2D g) {
        // White base
        g.setColor(COL_BG);
        g.fillRect(0, GRID_TOP, WIDTH, ROWS * TILE);
        // Draw smooth paint canvas on top
        if (paintCanvas != null)
            g.drawImage(paintCanvas, 0, GRID_TOP, null);
    }

    // ── Splats ────────────────────────────────────────────────────────────────
    private void drawSplats(Graphics2D g) {
        for (Splat s : splats) {
            int alpha = Math.max(0, Math.min(255, (int)(255 * (s.life / 30f))));
            g.setColor(new Color(s.col.getRed(), s.col.getGreen(), s.col.getBlue(), alpha));
            int sz = 3 + (int)(s.life / 8f);
            g.fillOval((int) s.x - sz/2, (int) s.y - sz/2, sz, sz);
        }
    }

    // ── Boosters ──────────────────────────────────────────────────────────────
    private void drawBoosters(Graphics2D g) {
        for (Booster b : boosters) {
            int bx = (int) b.x - BOOST_SIZE / 2;
            int by = (int) b.y - BOOST_SIZE / 2;

            // Pulsing glow
            float pulse = 0.5f + 0.5f * (float) Math.sin(frameCount * 0.15);
            int glowA = (int)(60 + 80 * pulse);

            if (b.type == BoostType.SPLASH) {
                // Purple splash booster
                Color col = new Color(0xAA, 0x00, 0xFF);
                g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), glowA));
                g.fillOval(bx - 10, by - 10, BOOST_SIZE + 20, BOOST_SIZE + 20);
                g.setColor(col);
                g.fillOval(bx, by, BOOST_SIZE, BOOST_SIZE);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2f));
                g.drawOval(bx, by, BOOST_SIZE, BOOST_SIZE);
                // Paint splash icon — 4 small dots radiating out
                g.setColor(Color.WHITE);
                int cx = bx + BOOST_SIZE/2, cy = by + BOOST_SIZE/2;
                g.fillOval(cx - 3, cy - 3, 6, 6);
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2;
                    int dx = (int)(Math.cos(a) * 9), dy = (int)(Math.sin(a) * 9);
                    g.fillOval(cx + dx - 2, cy + dy - 2, 5, 5);
                }
            } else {
                // Orange speed booster
                Color col = new Color(0xFF, 0xA5, 0x00);
                g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), glowA));
                g.fillOval(bx - 10, by - 10, BOOST_SIZE + 20, BOOST_SIZE + 20);
                g.setColor(col);
                g.fillOval(bx, by, BOOST_SIZE, BOOST_SIZE);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2f));
                g.drawOval(bx, by, BOOST_SIZE, BOOST_SIZE);
                // Lightning bolt shape
                g.setFont(new Font("Arial Black", Font.BOLD, 14));
                FontMetrics fm = g.getFontMetrics();
                String icon = "⚡";
                g.drawString(icon, bx + (BOOST_SIZE - fm.stringWidth(icon)) / 2, by + BOOST_SIZE - 5);
            }
        }
    }

    // ── Players ───────────────────────────────────────────────────────────────
    private void drawPlayers(Graphics2D g) {
        drawPlayer(g, (int) p1x, (int) p1y, COL_P1, "P1");
        drawPlayer(g, (int) p2x, (int) p2y, COL_P2, "P2");
    }

    private void drawPlayer(Graphics2D g, int x, int y, Color col, String label) {
        g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 60));
        g.fillOval(x - 6, y - 6, P_SIZE + 12, P_SIZE + 12);

        g.setColor(new Color(0, 0, 0, 80));
        g.fillOval(x + 3, y + 4, P_SIZE, P_SIZE);

        GradientPaint gp = new GradientPaint(x, y, col.brighter(), x + P_SIZE, y + P_SIZE, col.darker());
        g.setPaint(gp);
        g.fillOval(x, y, P_SIZE, P_SIZE);

        g.setColor(new Color(255, 255, 255, 100));
        g.fillOval(x + 3, y + 2, P_SIZE / 3, P_SIZE / 3);

        g.setColor(col.darker());
        g.setStroke(new BasicStroke(2f));
        g.drawOval(x, y, P_SIZE, P_SIZE);

        g.setFont(new Font("Arial Black", Font.BOLD, 10));
        g.setColor(new Color(30, 30, 30));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, x + (P_SIZE - fm.stringWidth(label)) / 2, y - 3);
    }

    // ── HUD ──────────────────────────────────────────────────────────────────
    private void drawHUD(Graphics2D g) {
        g.setColor(COL_HUD_BG);
        g.fillRect(0, 0, WIDTH, GRID_TOP);
        g.setColor(new Color(255, 255, 255, 20));
        g.fillRect(0, GRID_TOP - 2, WIDTH, 2);

        countPixels();
        float p1pct = (float) p1pixels / totalPixels;
        float p2pct = (float) p2pixels / totalPixels;

        int barW = 300, barH = 28, barY = 26;
        drawCoverageBar(g, 20, barY, barW, barH, p1pct, COL_P1, "P1  WASD", true);
        drawCoverageBar(g, WIDTH - 20 - barW, barY, barW, barH, p2pct, COL_P2, "P2  ← → ↑ ↓", false);

        // Speed boost indicators
        if (p1SpeedTicks > 0) {
            g.setFont(new Font("Arial Black", Font.BOLD, 11));
            g.setColor(new Color(0xFF, 0xA5, 0x00));
            g.drawString("⚡ SPEED " + (p1SpeedTicks / FPS + 1) + "s", 28, 64);
        }
        if (p2SpeedTicks > 0) {
            g.setFont(new Font("Arial Black", Font.BOLD, 11));
            g.setColor(new Color(0xFF, 0xA5, 0x00));
            g.drawString("⚡ SPEED " + (p2SpeedTicks / FPS + 1) + "s", WIDTH - 20 - barW, 64);
        }

        int secsLeft = (ticksLeft + FPS - 1) / FPS;
        boolean lowTime = secsLeft <= 10;

        if (lowTime) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(frameCount * 0.25);
            g.setColor(new Color(255, 60, 60, (int)(40 + 60 * pulse)));
        } else {
            g.setColor(new Color(255, 255, 255, 15));
        }
        g.fillRoundRect(WIDTH / 2 - 45, 8, 90, 56, 14, 14);

        g.setFont(new Font("Arial Black", Font.BOLD, 32));
        FontMetrics fm = g.getFontMetrics();
        String timeStr = String.format("%02d", gameState == State.COUNTDOWN ? ROUND_SECS : secsLeft);
        g.setColor(lowTime && gameState == State.PLAYING ? COL_TIMER_LOW : Color.WHITE);
        g.drawString(timeStr, WIDTH / 2 - fm.stringWidth(timeStr) / 2, 48);

        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(new Color(255, 255, 255, 140));
        String secLabel = "SECONDS";
        fm = g.getFontMetrics();
        g.drawString(secLabel, WIDTH / 2 - fm.stringWidth(secLabel) / 2, 62);

        // Back hint
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(new Color(255, 255, 255, 80));
        g.drawString("ESC = Menu", WIDTH - 72, 14);
    }

    private void drawCoverageBar(Graphics2D g, int x, int y, int w, int h,
                                  float pct, Color col, String label, boolean leftAlign) {
        g.setFont(new Font("Arial Black", Font.BOLD, 13));
        g.setColor(col);
        g.drawString(label, x, y - 6);

        g.setColor(new Color(255, 255, 255, 20));
        g.fillRoundRect(x, y, w, h, 10, 10);

        int fillW = (int)(w * pct);
        if (fillW > 0) {
            GradientPaint gp = new GradientPaint(x, y, col.brighter(), x + fillW, y, col);
            g.setPaint(gp);
            g.fillRoundRect(x, y, fillW, h, 10, 10);
            g.setColor(new Color(255, 255, 255, 40));
            g.fillRoundRect(x, y, fillW, h / 2, 10, 10);
        }

        g.setColor(new Color(255, 255, 255, 50));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x, y, w, h, 10, 10);

        g.setFont(new Font("Arial Black", Font.BOLD, 14));
        String pctStr = (int)(pct * 100) + "%";
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Color.WHITE);
        if (leftAlign) g.drawString(pctStr, x + w + 8, y + h - 5);
        else           g.drawString(pctStr, x - fm.stringWidth(pctStr) - 8, y + h - 5);
    }

    // ── Countdown 3-2-1 GO! ───────────────────────────────────────────────────
    private void drawCountdown(Graphics2D g) {
        // Light overlay over the white field
        g.setColor(new Color(255, 255, 255, 160));
        g.fillRect(0, GRID_TOP, WIDTH, ROWS * TILE);

        int secondsLeft = (countdownTicks + FPS - 1) / FPS;
        String label = secondsLeft > 0 ? String.valueOf(secondsLeft) : "GO!";

        float tickInSecond = countdownTicks % FPS;
        float pulse = 1.0f + 0.45f * (tickInSecond / FPS);
        int fontSize = (int)(140 * pulse);

        g.setFont(new Font("Arial Black", Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();

        Color numCol = secondsLeft == 3 ? COL_P1
                     : secondsLeft == 2 ? new Color(0xFF, 0xA5, 0x00)
                     :                    COL_P2;

        // Shadow
        g.setColor(new Color(0, 0, 0, 60));
        g.drawString(label, (WIDTH - fm.stringWidth(label)) / 2 + 5, HEIGHT / 2 + 55);
        // Main
        g.setColor(numCol);
        g.drawString(label, (WIDTH - fm.stringWidth(label)) / 2, HEIGHT / 2 + 50);

        // Controls hint
        g.setFont(new Font("Arial Black", Font.BOLD, 14));
        g.setColor(new Color(80, 80, 80, 200));
        String hint = "P1: WASD     P2: Arrow Keys";
        fm = g.getFontMetrics();
        g.drawString(hint, (WIDTH - fm.stringWidth(hint)) / 2, HEIGHT - 20);
    }

    // ── Game Over ─────────────────────────────────────────────────────────────
    private void drawGameOver(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 175));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        countPixels();
        float p1pct = (float) p1pixels / totalPixels * 100f;
        float p2pct = (float) p2pixels / totalPixels * 100f;

        int bx = WIDTH / 2 - 280, by = HEIGHT / 2 - 160;
        g.setColor(new Color(15, 15, 30, 230));
        g.fillRoundRect(bx, by, 560, 320, 24, 24);
        Color winCol = resultMsg.contains("1") ? COL_P1 : resultMsg.contains("2") ? COL_P2 : Color.WHITE;
        g.setColor(winCol);
        g.setStroke(new BasicStroke(2.5f));
        g.drawRoundRect(bx, by, 560, 320, 24, 24);

        g.setFont(new Font("Arial Black", Font.BOLD, 44));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(0, 0, 0, 150));
        g.drawString(resultMsg, (WIDTH - fm.stringWidth(resultMsg)) / 2 + 3, by + 68);
        g.setColor(winCol);
        g.drawString(resultMsg, (WIDTH - fm.stringWidth(resultMsg)) / 2, by + 65);

        int barY = by + 90;
        drawResultBar(g, bx + 30, barY,      500, 36, p1pct / 100f, COL_P1, "Player 1");
        drawResultBar(g, bx + 30, barY + 60, 500, 36, p2pct / 100f, COL_P2, "Player 2");

        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        g.setColor(new Color(220, 220, 220));
        String stats = String.format("P1: %.1f%%  |  P2: %.1f%%  |  Unpainted: %.1f%%",
            p1pct, p2pct, 100f - p1pct - p2pct);
        fm = g.getFontMetrics();
        g.drawString(stats, (WIDTH - fm.stringWidth(stats)) / 2, by + 210);

        g.setFont(new Font("Arial Black", Font.BOLD, 20));
        String restart = "ENTER — Play Again     ESC — Main Menu";
        fm = g.getFontMetrics();
        int alpha = 160 + (int)(95 * Math.sin(System.currentTimeMillis() / 400.0));
        g.setColor(new Color(255, 255, 255, Math.min(255, alpha)));
        g.drawString(restart, (WIDTH - fm.stringWidth(restart)) / 2, by + 265);
    }

    private void drawResultBar(Graphics2D g, int x, int y, int w, int h,
                                float pct, Color col, String label) {
        g.setFont(new Font("Arial Black", Font.BOLD, 13));
        g.setColor(col);
        g.drawString(label, x, y - 5);

        g.setColor(new Color(255, 255, 255, 20));
        g.fillRoundRect(x, y, w, h, 10, 10);

        int fillW = (int)(w * pct);
        if (fillW > 0) {
            GradientPaint gp = new GradientPaint(x, y, col.brighter(), x + fillW, y, col);
            g.setPaint(gp);
            g.fillRoundRect(x, y, fillW, h, 10, 10);
            g.setColor(new Color(255, 255, 255, 50));
            g.fillRoundRect(x, y, fillW, h / 2, 10, 10);
        }

        g.setFont(new Font("Arial Black", Font.BOLD, 16));
        g.setColor(Color.WHITE);
        String pctStr = String.format("%.1f%%", pct * 100);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(pctStr, x + w / 2 - fm.stringWidth(pctStr) / 2, y + h - 9);

        g.setColor(new Color(255, 255, 255, 50));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x, y, w, h, 10, 10);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Key Events
    // ══════════════════════════════════════════════════════════════════════════
    @Override public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W:     p1up    = true; break;
            case KeyEvent.VK_S:     p1down  = true; break;
            case KeyEvent.VK_A:     p1left  = true; break;
            case KeyEvent.VK_D:     p1right = true; break;
            case KeyEvent.VK_UP:    p2up    = true; break;
            case KeyEvent.VK_DOWN:  p2down  = true; break;
            case KeyEvent.VK_LEFT:  p2left  = true; break;
            case KeyEvent.VK_RIGHT: p2right = true; break;
            case KeyEvent.VK_ENTER:
                if (gameState == State.GAME_OVER) initGame();
                break;
            case KeyEvent.VK_ESCAPE:
                if (onBack != null) { swingTimer.stop(); onBack.run(); }
                break;
        }
    }

    @Override public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W:     p1up    = false; break;
            case KeyEvent.VK_S:     p1down  = false; break;
            case KeyEvent.VK_A:     p1left  = false; break;
            case KeyEvent.VK_D:     p1right = false; break;
            case KeyEvent.VK_UP:    p2up    = false; break;
            case KeyEvent.VK_DOWN:  p2down  = false; break;
            case KeyEvent.VK_LEFT:  p2left  = false; break;
            case KeyEvent.VK_RIGHT: p2right = false; break;
        }
    }

    @Override public void keyTyped(KeyEvent e) {}

    // ══════════════════════════════════════════════════════════════════════════
    //  Sound — plays YOUR OWN WAV files for each booster
    //
    //  SETUP (3 steps):
    //  1. Find two WAV sound files you like (MP3 won't work — must be WAV)
    //  2. Rename them:  splash_boost.wav   and   speed_boost.wav
    //  3. Put them in the SAME FOLDER as PaintFightGame.java
    //
    //  That's it! Sounds play automatically when a booster is picked up.
    //  If a file is missing the game runs normally — no crash, no error.
    //
    //  Want different filenames? Change the two lines below:
    // ══════════════════════════════════════════════════════════════════════════

    private static final String SPLASH_SOUND = "splash_boost.wav";  // purple booster
    private static final String SPEED_SOUND  = "speed_boost.wav";   // orange booster

    /** Called when purple SPLASH booster is picked up. */
    private void playSplashSound() { playWav(SPLASH_SOUND); }

    /** Called when orange SPEED booster is picked up. */
    private void playSpeedSound()  { playWav(SPEED_SOUND);  }

    /**
     * SPLAT! — short wet paint-hit sound, generated in code (no WAV file needed).
     * Plays while players are moving to give a satisfying painting feel.
     * Pitch and noise are randomised slightly each call so it never sounds repetitive.
     */
    private void playSplatSound() {
        new Thread(() -> {
            try {
                int   sr      = 44100;
                float dur     = 0.055f + (float)(Math.random() * 0.03f); // 55–85 ms
                int   samples = (int)(sr * dur);
                byte[]  buf   = new byte[samples];

                // Randomise pitch a little each time so repeated hits sound natural
                double basePitch = 180 + Math.random() * 120;  // 180–300 Hz

                for (int i = 0; i < samples; i++) {
                    double t = (double) i / sr;
                    double p = (double) i / samples;  // 0 → 1 progress

                    // Descending tone (wet-drop feel): pitch falls fast
                    double freq = basePitch * (1.0 - p * 0.7);

                    // Sine body
                    double tone = Math.sin(2 * Math.PI * freq * t);

                    // Noise burst — heavy at start, gone by halfway
                    double noise = (Math.random() * 2 - 1) * Math.max(0, 1.0 - p * 2.2);

                    // Sub-bass thump at the very start (the "SPLAT" body)
                    double thump = Math.sin(2 * Math.PI * 55 * t) * Math.exp(-t * 38);

                    // Envelope: instant attack, fast exponential decay
                    double env = Math.exp(-p * 9.0);

                    double sample = (tone * 0.35 + noise * 0.45 + thump * 0.9) * env;
                    sample = Math.max(-1.0, Math.min(1.0, sample * 0.85));
                    buf[i] = (byte)(sample * 127);
                }

                // Play via SourceDataLine — lightweight, no Clip overhead
                javax.sound.sampled.AudioFormat fmt =
                    new javax.sound.sampled.AudioFormat(sr, 8, 1, true, false);
                javax.sound.sampled.DataLine.Info info =
                    new javax.sound.sampled.DataLine.Info(
                        javax.sound.sampled.SourceDataLine.class, fmt);
                if (!javax.sound.sampled.AudioSystem.isLineSupported(info)) return;
                javax.sound.sampled.SourceDataLine line =
                    (javax.sound.sampled.SourceDataLine)
                        javax.sound.sampled.AudioSystem.getLine(info);
                line.open(fmt, buf.length);
                line.start();
                line.write(buf, 0, buf.length);
                line.drain();
                line.close();

            } catch (Exception ex) { /* silent fail */ }
        }).start();
    }

    /**
     * Loads and plays a WAV file in a background thread.
     * Returns instantly — the game loop is never blocked or slowed down.
     * Completely silent on any error (file missing, wrong format, etc.)
     */
    private void playWav(String filename) {
        new Thread(() -> {
            try {
                java.io.File f = new java.io.File(filename);
                if (!f.exists()) return;  // file not found — skip silently

                javax.sound.sampled.AudioInputStream raw =
                    javax.sound.sampled.AudioSystem.getAudioInputStream(f);

                // Convert to 16-bit PCM signed — works on all systems
                javax.sound.sampled.AudioFormat base = raw.getFormat();
                javax.sound.sampled.AudioFormat playFmt = new javax.sound.sampled.AudioFormat(
                    javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                    base.getSampleRate(), 16,
                    base.getChannels(),
                    base.getChannels() * 2,
                    base.getSampleRate(), false);

                javax.sound.sampled.AudioInputStream pcm =
                    javax.sound.sampled.AudioSystem.getAudioInputStream(playFmt, raw);

                javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
                clip.open(pcm);

                // Auto-release resources the moment sound finishes
                clip.addLineListener(ev -> {
                    if (ev.getType() == javax.sound.sampled.LineEvent.Type.STOP)
                        clip.close();
                });

                clip.start();  // non-blocking — returns immediately

            } catch (Exception ex) { /* any error: wrong format, device busy, etc — skip */ }
        }).start();
    }

}
// import javax.swing.*;
// import java.awt.*;
// import java.awt.event.*;
// import java.awt.image.BufferedImage;
// import javax.sound.sampled.*;
// import java.io.File;

// public class PaintFightGame extends JPanel implements ActionListener, KeyListener {

//     // ─── Constants ─────────────────────────────────────────────────────────────
//     static final int WIDTH       = 900;
//     static final int HEIGHT      = 620;
//     static final int FPS         = 60;
//     static final int ROUND_SECS  = 30;
//     static final int ROUND_TICKS = ROUND_SECS * FPS;

//     // Grid
//     static final int TILE     = 20;
//     static final int COLS     = WIDTH / TILE;
//     static final int ROWS     = (HEIGHT - 80) / TILE;
//     static final int GRID_TOP = 80;

//     // Player
//     static final int   P_SIZE      = 22;
//     static final float P_SPEED     = 3.5f;
//     static final int   PAINT_RADIUS = 18;  // radius of smooth paint circle trail

//     // Boosters
//     static final int   BOOST_SIZE       = 24;
//     static final int   BOOST_INTERVAL   = 8 * FPS;   // new booster every 8 seconds
//     static final int   SPEED_BOOST_TICKS= 4 * FPS;   // speed boost lasts 4 seconds
//     static final float SPEED_BOOST_MULT = 2.0f;       // 2× speed
//     static final int   SPLASH_BOOST_RADIUS = 80;      // splash paint radius

//     // Tile ownership
//     static final int EMPTY = 0, P1 = 1, P2 = 2;

//     // ─── Colors ────────────────────────────────────────────────────────────────
//     static final Color COL_BG       = Color.WHITE;
//     static final Color COL_EMPTY    = new Color(0xF0, 0xF0, 0xF0);   // very light grey
//     static final Color COL_P1       = new Color(0xFF, 0x4D, 0x6D);
//     static final Color COL_P2       = new Color(0x00, 0xC2, 0xFF);
//     static final Color COL_P1_PAINT = new Color(0xFF, 0x4D, 0x6D);   // fully opaque — no alpha
//     static final Color COL_P2_PAINT = new Color(0x00, 0xC2, 0xFF);   // fully opaque — no alpha
//     static final Color COL_HUD_BG   = new Color(0x1A, 0x1A, 0x2E);
//     static final Color COL_TIMER_LOW= new Color(0xFF, 0x4A, 0x4A);

//     // ─── Game State ────────────────────────────────────────────────────────────
//     enum State { COUNTDOWN, PLAYING, GAME_OVER }
//     State gameState = State.COUNTDOWN;

//     // ─── Countdown ─────────────────────────────────────────────────────────────
//     static final int COUNTDOWN_SECONDS = 3;
//     int countdownTicks = COUNTDOWN_SECONDS * FPS;

//     // ─── Paint Canvas (pixel-based, no grid) ───────────────────────────────────
//     BufferedImage paintCanvas;   // each pixel stores 0=empty, P1 color, P2 color
//     Graphics2D    canvasG;

//     // For score counting — sample canvas periodically
//     int p1pixels, p2pixels, totalPixels;
//     float p1x, p1y, p2x, p2y;
//     boolean p1up, p1down, p1left, p1right;
//     boolean p2up, p2down, p2left, p2right;

//     // ─── Timer ─────────────────────────────────────────────────────────────────
//     int ticksLeft;

//     // ─── Misc ──────────────────────────────────────────────────────────────────
//     Timer  swingTimer;
//     long   frameCount    = 0;
//     int    splatSoundTimer = 0;  // throttle movement splat sound
//     String resultMsg  = "";

//     // ─── Hub callback ──────────────────────────────────────────────────────────
//     GameOverListener onGameOver = null;
//     Runnable onBack = null;

//     // ─── Paint splat particles ─────────────────────────────────────────────────
//     static class Splat {
//         float x, y, vx, vy, life;
//         Color col;
//         Splat(float x, float y, Color c) {
//             this.x = x; this.y = y; col = c;
//             double a = Math.random() * Math.PI * 2;
//             float spd = 1f + (float) Math.random() * 3f;
//             vx = (float) Math.cos(a) * spd;
//             vy = (float) Math.sin(a) * spd;
//             life = 18f + (float) Math.random() * 12f;
//         }
//         void update() { x += vx; y += vy; vy += 0.15f; life--; }
//         boolean dead() { return life <= 0; }
//     }
//     java.util.List<Splat> splats = new java.util.ArrayList<>();

//     // ─── Boosters ──────────────────────────────────────────────────────────────
//     enum BoostType { SPLASH, SPEED }

//     static class Booster {
//         float x, y;
//         BoostType type;
//         Booster(float x, float y, BoostType t) { this.x = x; this.y = y; this.type = t; }
//     }

//     java.util.List<Booster> boosters = new java.util.ArrayList<>();
//     int  boosterSpawnTimer = BOOST_INTERVAL;
//     int  p1SpeedTicks = 0,  p2SpeedTicks = 0;
//     boolean nextBoosterIsSpeed = false;  // toggles each spawn

//     // ══════════════════════════════════════════════════════════════════════════
//     public PaintFightGame() {
//         setPreferredSize(new Dimension(WIDTH, HEIGHT));
//         setBackground(COL_BG);
//         setFocusable(true);
//         addKeyListener(this);
//         initGame();

//         swingTimer = new Timer(1000 / FPS, this);
//         swingTimer.start();
//     }

//     // ──────────────────────────────────────────────────────────────────────────
//     private void initGame() {
//         // Create pixel paint canvas
//         int canvasH = ROWS * TILE;
//         paintCanvas = new BufferedImage(WIDTH, canvasH, BufferedImage.TYPE_INT_ARGB);
//         canvasG = paintCanvas.createGraphics();
//         canvasG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//         canvasG.setColor(new Color(0, 0, 0, 0));
//         canvasG.fillRect(0, 0, WIDTH, canvasH);

//         totalPixels = WIDTH * canvasH;
//         p1pixels = 0; p2pixels = 0;
//         ticksLeft = ROUND_TICKS;
//         resultMsg = "";
//         splats.clear();
//         boosters.clear();
//         boosterSpawnTimer = BOOST_INTERVAL;
//         p1SpeedTicks = 0; p2SpeedTicks = 0;
//         nextBoosterIsSpeed = false;

//         p1x = TILE * 2;
//         p1y = GRID_TOP + TILE * 2;
//         p2x = WIDTH - TILE * 2 - P_SIZE;
//         p2y = GRID_TOP + ROWS * TILE - TILE * 2 - P_SIZE;

//         countdownTicks = COUNTDOWN_SECONDS * FPS;
//         gameState = State.COUNTDOWN;
//     }

//     // ══════════════════════════════════════════════════════════════════════════
//     //  Game Loop
//     // ══════════════════════════════════════════════════════════════════════════
//     @Override
//     public void actionPerformed(ActionEvent e) {
//         if (gameState == State.COUNTDOWN) {
//             countdownTicks--;
//             if (countdownTicks <= 0) gameState = State.PLAYING;
//         } else if (gameState == State.PLAYING) {
//             movePlayers();
//             paintTiles();
//             updateSplats();
//             updateBoosters();
//             if (p1SpeedTicks > 0) p1SpeedTicks--;
//             if (p2SpeedTicks > 0) p2SpeedTicks--;
//             if (frameCount % 15 == 0) countPixels();
//             ticksLeft--;
//             if (ticksLeft <= 0) endGame();
//         }
//         frameCount++;
//         repaint();
//     }

//     // ──────────────────────────────────────────────────────────────────────────
//     //  Movement
//     // ──────────────────────────────────────────────────────────────────────────
//     private void movePlayers() {
//         float spd1 = p1SpeedTicks > 0 ? P_SPEED * SPEED_BOOST_MULT : P_SPEED;
//         float spd2 = p2SpeedTicks > 0 ? P_SPEED * SPEED_BOOST_MULT : P_SPEED;
//         float dx1 = 0, dy1 = 0, dx2 = 0, dy2 = 0;
//         if (p1up)    dy1 -= spd1;
//         if (p1down)  dy1 += spd1;
//         if (p1left)  dx1 -= spd1;
//         if (p1right) dx1 += spd1;
//         if (p2up)    dy2 -= spd2;
//         if (p2down)  dy2 += spd2;
//         if (p2left)  dx2 -= spd2;
//         if (p2right) dx2 += spd2;

//         if (dx1 != 0 && dy1 != 0) { dx1 *= 0.707f; dy1 *= 0.707f; }
//         if (dx2 != 0 && dy2 != 0) { dx2 *= 0.707f; dy2 *= 0.707f; }

//         p1x = clampX(p1x + dx1);
//         p1y = clampY(p1y + dy1);
//         p2x = clampX(p2x + dx2);
//         p2y = clampY(p2y + dy2);

//         // Play splat sound periodically while any player is moving
//         boolean anyMoving = (dx1 != 0 || dy1 != 0 || dx2 != 0 || dy2 != 0);
//         if (anyMoving) {
//             splatSoundTimer--;
//             if (splatSoundTimer <= 0) {
//                 playSplatSound();
//                 splatSoundTimer = 12; // play roughly every 12 frames (~5x/sec)
//             }
//         } else {
//             splatSoundTimer = 0; // reset so next movement starts a splat immediately
//         }
//     }

//     private float clampX(float x) { return Math.max(0, Math.min(COLS * TILE - P_SIZE, x)); }
//     private float clampY(float y) { return Math.max(GRID_TOP, Math.min(GRID_TOP + ROWS * TILE - P_SIZE, y)); }

//     // ──────────────────────────────────────────────────────────────────────────
//     //  Paint tiles
//     // ──────────────────────────────────────────────────────────────────────────
//     private void paintTiles() {
//         // Paint smooth circles onto the canvas at player center positions
//         paintCircle(p1x + P_SIZE / 2f, p1y + P_SIZE / 2f, COL_P1_PAINT, P1);
//         paintCircle(p2x + P_SIZE / 2f, p2y + P_SIZE / 2f, COL_P2_PAINT, P2);
//     }

//     private void paintCircle(float wx, float wy, Color col, int owner) {
//         // Draw filled circle on canvas at player position
//         float cy = wy - GRID_TOP;
//         canvasG.setColor(col);
//         canvasG.fillOval((int)(wx - PAINT_RADIUS), (int)(cy - PAINT_RADIUS),
//                           PAINT_RADIUS * 2, PAINT_RADIUS * 2);
//         // Spawn splats occasionally
//         if (Math.random() < 0.15) {
//             Color sc = (owner == P1) ? COL_P1 : COL_P2;
//             splats.add(new Splat(wx, wy, sc));
//         }
//     }

//     // ──────────────────────────────────────────────────────────────────────────
//     //  Booster Logic
//     // ──────────────────────────────────────────────────────────────────────────
//     private void updateBoosters() {
//         // Spawn a new booster periodically — alternate types
//         boosterSpawnTimer--;
//         if (boosterSpawnTimer <= 0) {
//             BoostType type = nextBoosterIsSpeed ? BoostType.SPEED : BoostType.SPLASH;
//             nextBoosterIsSpeed = !nextBoosterIsSpeed;
//             float bx = 80 + (float)(Math.random() * (WIDTH - 160));
//             float by = GRID_TOP + 40 + (float)(Math.random() * (ROWS * TILE - 80));
//             boosters.add(new Booster(bx, by, type));
//             boosterSpawnTimer = BOOST_INTERVAL;
//             return; // BUG FIX: skip collision check this frame so the booster
//                     // is not instantly picked up the moment it spawns
//         }

//         // Check pickup — use circular distance, not rectangular box
//         java.util.Iterator<Booster> it = boosters.iterator();
//         while (it.hasNext()) {
//             Booster b = it.next();
//             float p1cx = p1x + P_SIZE / 2f, p1cy = p1y + P_SIZE / 2f;
//             float p2cx = p2x + P_SIZE / 2f, p2cy = p2y + P_SIZE / 2f;
//             float pickup = (P_SIZE + BOOST_SIZE) / 2f;

//             boolean p1hit = Math.hypot(p1cx - b.x, p1cy - b.y) < pickup;
//             boolean p2hit = Math.hypot(p2cx - b.x, p2cy - b.y) < pickup;

//             if (p1hit || p2hit) {
//                 float px  = p1hit ? p1cx : p2cx;
//                 float py  = p1hit ? p1cy : p2cy;
//                 Color col = p1hit ? COL_P1_PAINT : COL_P2_PAINT;
//                 Color sCol= p1hit ? COL_P1 : COL_P2;

//                 if (b.type == BoostType.SPLASH) {
//                     canvasG.setColor(col);
//                     float cy = py - GRID_TOP;
//                     canvasG.fillOval((int)(px - SPLASH_BOOST_RADIUS),
//                                      (int)(cy - SPLASH_BOOST_RADIUS),
//                                      SPLASH_BOOST_RADIUS * 2, SPLASH_BOOST_RADIUS * 2);
//                     for (int i = 0; i < 20; i++) splats.add(new Splat(px, py, sCol));
//                     playSplashSound();   // plays splash_boost.wav
//                 } else {
//                     if (p1hit) p1SpeedTicks = SPEED_BOOST_TICKS;
//                     else       p2SpeedTicks = SPEED_BOOST_TICKS;
//                     for (int i = 0; i < 10; i++) splats.add(new Splat(px, py, sCol));
//                     playSpeedSound();    // plays speed_boost.wav
//                 }
//                 it.remove();
//             }
//         }
//     }

//     int sampleTick = 0;

//     private void countPixels() {
//         p1pixels = 0; p2pixels = 0;
//         int canvasH = ROWS * TILE;
//         for (int y = 0; y < canvasH; y += 2) {
//             for (int x = 0; x < WIDTH; x += 2) {
//                 int px = paintCanvas.getRGB(x, y);
//                 int a = (px >> 24) & 0xFF;
//                 if (a < 30) continue;  // skip transparent/empty
//                 int r = (px >> 16) & 0xFF;
//                 int b =  px        & 0xFF;
//                 // P1 is red-dominant, P2 is blue-dominant
//                 if (r > b && r > 100) p1pixels++;
//                 else if (b > r && b > 100) p2pixels++;
//             }
//         }
//         // Scale back (sampled every 2nd pixel in both axes)
//         p1pixels *= 4;
//         p2pixels *= 4;
//     }

//     private void updateSplats() {
//         splats.removeIf(Splat::dead);
//         for (Splat s : splats) s.update();
//     }

//     private void endGame() {
//         countPixels();
//         if (p1pixels > p2pixels)      resultMsg = "Player 1 Wins! 🎨";
//         else if (p2pixels > p1pixels) resultMsg = "Player 2 Wins! 🎨";
//         else                          resultMsg = "It's a TIE! 🤝";
//         gameState = State.GAME_OVER;
//     }

//     // ══════════════════════════════════════════════════════════════════════════
//     //  Rendering
//     // ══════════════════════════════════════════════════════════════════════════
//     @Override
//     protected void paintComponent(Graphics g0) {
//         super.paintComponent(g0);
//         Graphics2D g = (Graphics2D) g0;
//         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
//         g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

//         drawGrid(g);
//         drawSplats(g);
//         drawBoosters(g);
//         drawPlayers(g);
//         drawHUD(g);

//         if (gameState == State.COUNTDOWN) drawCountdown(g);
//         if (gameState == State.GAME_OVER) drawGameOver(g);
//     }

//     // ── Grid — draw smooth pixel canvas ──────────────────────────────────────
//     private void drawGrid(Graphics2D g) {
//         // White base
//         g.setColor(COL_BG);
//         g.fillRect(0, GRID_TOP, WIDTH, ROWS * TILE);
//         // Draw smooth paint canvas on top
//         if (paintCanvas != null)
//             g.drawImage(paintCanvas, 0, GRID_TOP, null);
//     }

//     // ── Splats ────────────────────────────────────────────────────────────────
//     private void drawSplats(Graphics2D g) {
//         for (Splat s : splats) {
//             int alpha = Math.max(0, Math.min(255, (int)(255 * (s.life / 30f))));
//             g.setColor(new Color(s.col.getRed(), s.col.getGreen(), s.col.getBlue(), alpha));
//             int sz = 3 + (int)(s.life / 8f);
//             g.fillOval((int) s.x - sz/2, (int) s.y - sz/2, sz, sz);
//         }
//     }

//     // ── Boosters ──────────────────────────────────────────────────────────────
//     private void drawBoosters(Graphics2D g) {
//         for (Booster b : boosters) {
//             int bx = (int) b.x - BOOST_SIZE / 2;
//             int by = (int) b.y - BOOST_SIZE / 2;

//             // Pulsing glow
//             float pulse = 0.5f + 0.5f * (float) Math.sin(frameCount * 0.15);
//             int glowA = (int)(60 + 80 * pulse);

//             if (b.type == BoostType.SPLASH) {
//                 // Purple splash booster
//                 Color col = new Color(0xAA, 0x00, 0xFF);
//                 g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), glowA));
//                 g.fillOval(bx - 10, by - 10, BOOST_SIZE + 20, BOOST_SIZE + 20);
//                 g.setColor(col);
//                 g.fillOval(bx, by, BOOST_SIZE, BOOST_SIZE);
//                 g.setColor(Color.WHITE);
//                 g.setStroke(new BasicStroke(2f));
//                 g.drawOval(bx, by, BOOST_SIZE, BOOST_SIZE);
//                 // Paint splash icon — 4 small dots radiating out
//                 g.setColor(Color.WHITE);
//                 int cx = bx + BOOST_SIZE/2, cy = by + BOOST_SIZE/2;
//                 g.fillOval(cx - 3, cy - 3, 6, 6);
//                 for (int i = 0; i < 4; i++) {
//                     double a = i * Math.PI / 2;
//                     int dx = (int)(Math.cos(a) * 9), dy = (int)(Math.sin(a) * 9);
//                     g.fillOval(cx + dx - 2, cy + dy - 2, 5, 5);
//                 }
//             } else {
//                 // Orange speed booster
//                 Color col = new Color(0xFF, 0xA5, 0x00);
//                 g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), glowA));
//                 g.fillOval(bx - 10, by - 10, BOOST_SIZE + 20, BOOST_SIZE + 20);
//                 g.setColor(col);
//                 g.fillOval(bx, by, BOOST_SIZE, BOOST_SIZE);
//                 g.setColor(Color.WHITE);
//                 g.setStroke(new BasicStroke(2f));
//                 g.drawOval(bx, by, BOOST_SIZE, BOOST_SIZE);
//                 // Lightning bolt shape
//                 g.setFont(new Font("Arial Black", Font.BOLD, 14));
//                 FontMetrics fm = g.getFontMetrics();
//                 String icon = "⚡";
//                 g.drawString(icon, bx + (BOOST_SIZE - fm.stringWidth(icon)) / 2, by + BOOST_SIZE - 5);
//             }
//         }
//     }

//     // ── Players ───────────────────────────────────────────────────────────────
//     private void drawPlayers(Graphics2D g) {
//         drawPlayer(g, (int) p1x, (int) p1y, COL_P1, "P1");
//         drawPlayer(g, (int) p2x, (int) p2y, COL_P2, "P2");
//     }

//     private void drawPlayer(Graphics2D g, int x, int y, Color col, String label) {
//         g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 60));
//         g.fillOval(x - 6, y - 6, P_SIZE + 12, P_SIZE + 12);

//         g.setColor(new Color(0, 0, 0, 80));
//         g.fillOval(x + 3, y + 4, P_SIZE, P_SIZE);

//         GradientPaint gp = new GradientPaint(x, y, col.brighter(), x + P_SIZE, y + P_SIZE, col.darker());
//         g.setPaint(gp);
//         g.fillOval(x, y, P_SIZE, P_SIZE);

//         g.setColor(new Color(255, 255, 255, 100));
//         g.fillOval(x + 3, y + 2, P_SIZE / 3, P_SIZE / 3);

//         g.setColor(col.darker());
//         g.setStroke(new BasicStroke(2f));
//         g.drawOval(x, y, P_SIZE, P_SIZE);

//         g.setFont(new Font("Arial Black", Font.BOLD, 10));
//         g.setColor(new Color(30, 30, 30));
//         FontMetrics fm = g.getFontMetrics();
//         g.drawString(label, x + (P_SIZE - fm.stringWidth(label)) / 2, y - 3);
//     }

//     // ── HUD ──────────────────────────────────────────────────────────────────
//     private void drawHUD(Graphics2D g) {
//         g.setColor(COL_HUD_BG);
//         g.fillRect(0, 0, WIDTH, GRID_TOP);
//         g.setColor(new Color(255, 255, 255, 20));
//         g.fillRect(0, GRID_TOP - 2, WIDTH, 2);

//         countPixels();
//         float p1pct = (float) p1pixels / totalPixels;
//         float p2pct = (float) p2pixels / totalPixels;

//         int barW = 300, barH = 28, barY = 26;
//         drawCoverageBar(g, 20, barY, barW, barH, p1pct, COL_P1, "P1  WASD", true);
//         drawCoverageBar(g, WIDTH - 20 - barW, barY, barW, barH, p2pct, COL_P2, "P2  ← → ↑ ↓", false);

//         // Speed boost indicators
//         if (p1SpeedTicks > 0) {
//             g.setFont(new Font("Arial Black", Font.BOLD, 11));
//             g.setColor(new Color(0xFF, 0xA5, 0x00));
//             g.drawString("⚡ SPEED " + (p1SpeedTicks / FPS + 1) + "s", 28, 64);
//         }
//         if (p2SpeedTicks > 0) {
//             g.setFont(new Font("Arial Black", Font.BOLD, 11));
//             g.setColor(new Color(0xFF, 0xA5, 0x00));
//             g.drawString("⚡ SPEED " + (p2SpeedTicks / FPS + 1) + "s", WIDTH - 20 - barW, 64);
//         }

//         int secsLeft = (ticksLeft + FPS - 1) / FPS;
//         boolean lowTime = secsLeft <= 10;

//         if (lowTime) {
//             float pulse = 0.5f + 0.5f * (float) Math.sin(frameCount * 0.25);
//             g.setColor(new Color(255, 60, 60, (int)(40 + 60 * pulse)));
//         } else {
//             g.setColor(new Color(255, 255, 255, 15));
//         }
//         g.fillRoundRect(WIDTH / 2 - 45, 8, 90, 56, 14, 14);

//         g.setFont(new Font("Arial Black", Font.BOLD, 32));
//         FontMetrics fm = g.getFontMetrics();
//         String timeStr = String.format("%02d", gameState == State.COUNTDOWN ? ROUND_SECS : secsLeft);
//         g.setColor(lowTime && gameState == State.PLAYING ? COL_TIMER_LOW : Color.WHITE);
//         g.drawString(timeStr, WIDTH / 2 - fm.stringWidth(timeStr) / 2, 48);

//         g.setFont(new Font("SansSerif", Font.BOLD, 10));
//         g.setColor(new Color(255, 255, 255, 140));
//         String secLabel = "SECONDS";
//         fm = g.getFontMetrics();
//         g.drawString(secLabel, WIDTH / 2 - fm.stringWidth(secLabel) / 2, 62);

//         // Back hint
//         g.setFont(new Font("SansSerif", Font.PLAIN, 10));
//         g.setColor(new Color(255, 255, 255, 80));
//         g.drawString("ESC = Menu", WIDTH - 72, 14);
//     }

//     private void drawCoverageBar(Graphics2D g, int x, int y, int w, int h,
//                                   float pct, Color col, String label, boolean leftAlign) {
//         g.setFont(new Font("Arial Black", Font.BOLD, 13));
//         g.setColor(col);
//         g.drawString(label, x, y - 6);

//         g.setColor(new Color(255, 255, 255, 20));
//         g.fillRoundRect(x, y, w, h, 10, 10);

//         int fillW = (int)(w * pct);
//         if (fillW > 0) {
//             GradientPaint gp = new GradientPaint(x, y, col.brighter(), x + fillW, y, col);
//             g.setPaint(gp);
//             g.fillRoundRect(x, y, fillW, h, 10, 10);
//             g.setColor(new Color(255, 255, 255, 40));
//             g.fillRoundRect(x, y, fillW, h / 2, 10, 10);
//         }

//         g.setColor(new Color(255, 255, 255, 50));
//         g.setStroke(new BasicStroke(1f));
//         g.drawRoundRect(x, y, w, h, 10, 10);

//         g.setFont(new Font("Arial Black", Font.BOLD, 14));
//         String pctStr = (int)(pct * 100) + "%";
//         FontMetrics fm = g.getFontMetrics();
//         g.setColor(Color.WHITE);
//         if (leftAlign) g.drawString(pctStr, x + w + 8, y + h - 5);
//         else           g.drawString(pctStr, x - fm.stringWidth(pctStr) - 8, y + h - 5);
//     }

//     // ── Countdown 3-2-1 GO! ───────────────────────────────────────────────────
//     private void drawCountdown(Graphics2D g) {
//         // Light overlay over the white field
//         g.setColor(new Color(255, 255, 255, 160));
//         g.fillRect(0, GRID_TOP, WIDTH, ROWS * TILE);

//         int secondsLeft = (countdownTicks + FPS - 1) / FPS;
//         String label = secondsLeft > 0 ? String.valueOf(secondsLeft) : "GO!";

//         float tickInSecond = countdownTicks % FPS;
//         float pulse = 1.0f + 0.45f * (tickInSecond / FPS);
//         int fontSize = (int)(140 * pulse);

//         g.setFont(new Font("Arial Black", Font.BOLD, fontSize));
//         FontMetrics fm = g.getFontMetrics();

//         Color numCol = secondsLeft == 3 ? COL_P1
//                      : secondsLeft == 2 ? new Color(0xFF, 0xA5, 0x00)
//                      :                    COL_P2;

//         // Shadow
//         g.setColor(new Color(0, 0, 0, 60));
//         g.drawString(label, (WIDTH - fm.stringWidth(label)) / 2 + 5, HEIGHT / 2 + 55);
//         // Main
//         g.setColor(numCol);
//         g.drawString(label, (WIDTH - fm.stringWidth(label)) / 2, HEIGHT / 2 + 50);

//         // Controls hint
//         g.setFont(new Font("Arial Black", Font.BOLD, 14));
//         g.setColor(new Color(80, 80, 80, 200));
//         String hint = "P1: WASD     P2: Arrow Keys";
//         fm = g.getFontMetrics();
//         g.drawString(hint, (WIDTH - fm.stringWidth(hint)) / 2, HEIGHT - 20);
//     }

//     // ── Game Over ─────────────────────────────────────────────────────────────
//     private void drawGameOver(Graphics2D g) {
//         g.setColor(new Color(0, 0, 0, 175));
//         g.fillRect(0, 0, WIDTH, HEIGHT);

//         countPixels();
//         float p1pct = (float) p1pixels / totalPixels * 100f;
//         float p2pct = (float) p2pixels / totalPixels * 100f;

//         int bx = WIDTH / 2 - 280, by = HEIGHT / 2 - 160;
//         g.setColor(new Color(15, 15, 30, 230));
//         g.fillRoundRect(bx, by, 560, 320, 24, 24);
//         Color winCol = resultMsg.contains("1") ? COL_P1 : resultMsg.contains("2") ? COL_P2 : Color.WHITE;
//         g.setColor(winCol);
//         g.setStroke(new BasicStroke(2.5f));
//         g.drawRoundRect(bx, by, 560, 320, 24, 24);

//         g.setFont(new Font("Arial Black", Font.BOLD, 44));
//         FontMetrics fm = g.getFontMetrics();
//         g.setColor(new Color(0, 0, 0, 150));
//         g.drawString(resultMsg, (WIDTH - fm.stringWidth(resultMsg)) / 2 + 3, by + 68);
//         g.setColor(winCol);
//         g.drawString(resultMsg, (WIDTH - fm.stringWidth(resultMsg)) / 2, by + 65);

//         int barY = by + 90;
//         drawResultBar(g, bx + 30, barY,      500, 36, p1pct / 100f, COL_P1, "Player 1");
//         drawResultBar(g, bx + 30, barY + 60, 500, 36, p2pct / 100f, COL_P2, "Player 2");

//         g.setFont(new Font("SansSerif", Font.BOLD, 17));
//         g.setColor(new Color(220, 220, 220));
//         String stats = String.format("P1: %.1f%%  |  P2: %.1f%%  |  Unpainted: %.1f%%",
//             p1pct, p2pct, 100f - p1pct - p2pct);
//         fm = g.getFontMetrics();
//         g.drawString(stats, (WIDTH - fm.stringWidth(stats)) / 2, by + 210);

//         g.setFont(new Font("Arial Black", Font.BOLD, 20));
//         String restart = "ENTER — Play Again     ESC — Main Menu";
//         fm = g.getFontMetrics();
//         int alpha = 160 + (int)(95 * Math.sin(System.currentTimeMillis() / 400.0));
//         g.setColor(new Color(255, 255, 255, Math.min(255, alpha)));
//         g.drawString(restart, (WIDTH - fm.stringWidth(restart)) / 2, by + 265);
//     }

//     private void drawResultBar(Graphics2D g, int x, int y, int w, int h,
//                                 float pct, Color col, String label) {
//         g.setFont(new Font("Arial Black", Font.BOLD, 13));
//         g.setColor(col);
//         g.drawString(label, x, y - 5);

//         g.setColor(new Color(255, 255, 255, 20));
//         g.fillRoundRect(x, y, w, h, 10, 10);

//         int fillW = (int)(w * pct);
//         if (fillW > 0) {
//             GradientPaint gp = new GradientPaint(x, y, col.brighter(), x + fillW, y, col);
//             g.setPaint(gp);
//             g.fillRoundRect(x, y, fillW, h, 10, 10);
//             g.setColor(new Color(255, 255, 255, 50));
//             g.fillRoundRect(x, y, fillW, h / 2, 10, 10);
//         }

//         g.setFont(new Font("Arial Black", Font.BOLD, 16));
//         g.setColor(Color.WHITE);
//         String pctStr = String.format("%.1f%%", pct * 100);
//         FontMetrics fm = g.getFontMetrics();
//         g.drawString(pctStr, x + w / 2 - fm.stringWidth(pctStr) / 2, y + h - 9);

//         g.setColor(new Color(255, 255, 255, 50));
//         g.setStroke(new BasicStroke(1f));
//         g.drawRoundRect(x, y, w, h, 10, 10);
//     }

//     // ══════════════════════════════════════════════════════════════════════════
//     //  Key Events
//     // ══════════════════════════════════════════════════════════════════════════
//     @Override public void keyPressed(KeyEvent e) {
//         switch (e.getKeyCode()) {
//             case KeyEvent.VK_W:     p1up    = true; break;
//             case KeyEvent.VK_S:     p1down  = true; break;
//             case KeyEvent.VK_A:     p1left  = true; break;
//             case KeyEvent.VK_D:     p1right = true; break;
//             case KeyEvent.VK_UP:    p2up    = true; break;
//             case KeyEvent.VK_DOWN:  p2down  = true; break;
//             case KeyEvent.VK_LEFT:  p2left  = true; break;
//             case KeyEvent.VK_RIGHT: p2right = true; break;
//             case KeyEvent.VK_ENTER:
//                 if (gameState == State.GAME_OVER) initGame();
//                 break;
//             case KeyEvent.VK_ESCAPE:
//                 if (onBack != null) { swingTimer.stop(); onBack.run(); }
//                 break;
//         }
//     }

//     @Override public void keyReleased(KeyEvent e) {
//         switch (e.getKeyCode()) {
//             case KeyEvent.VK_W:     p1up    = false; break;
//             case KeyEvent.VK_S:     p1down  = false; break;
//             case KeyEvent.VK_A:     p1left  = false; break;
//             case KeyEvent.VK_D:     p1right = false; break;
//             case KeyEvent.VK_UP:    p2up    = false; break;
//             case KeyEvent.VK_DOWN:  p2down  = false; break;
//             case KeyEvent.VK_LEFT:  p2left  = false; break;
//             case KeyEvent.VK_RIGHT: p2right = false; break;
//         }
//     }

//     @Override public void keyTyped(KeyEvent e) {}

//     // ══════════════════════════════════════════════════════════════════════════
//     //  Sound — plays YOUR OWN WAV files for each booster
//     //
//     //  SETUP (3 steps):
//     //  1. Find two WAV sound files you like (MP3 won't work — must be WAV)
//     //  2. Rename them:  splash_boost.wav   and   speed_boost.wav
//     //  3. Put them in the SAME FOLDER as PaintFightGame.java
//     //
//     //  That's it! Sounds play automatically when a booster is picked up.
//     //  If a file is missing the game runs normally — no crash, no error.
//     //
//     //  Want different filenames? Change the two lines below:
//     // ══════════════════════════════════════════════════════════════════════════

//     private static final String SPLASH_SOUND = "splash_boost.wav";  // purple booster
//     private static final String SPEED_SOUND  = "speed_boost.wav";   // orange booster

//     /** Called when purple SPLASH booster is picked up. */
//     private void playSplashSound() { playWav(SPLASH_SOUND); }

//     /** Called when orange SPEED booster is picked up. */
//     private void playSpeedSound()  { playWav(SPEED_SOUND);  }

//     /**
//      * SPLAT! — short wet paint-hit sound, generated in code (no WAV file needed).
//      * Plays while players are moving to give a satisfying painting feel.
//      * Pitch and noise are randomised slightly each call so it never sounds repetitive.
//      */
//     private void playSplatSound() {
//         new Thread(() -> {
//             try {
//                 int   sr      = 44100;
//                 float dur     = 0.055f + (float)(Math.random() * 0.03f); // 55–85 ms
//                 int   samples = (int)(sr * dur);
//                 byte[]  buf   = new byte[samples];

//                 // Randomise pitch a little each time so repeated hits sound natural
//                 double basePitch = 180 + Math.random() * 120;  // 180–300 Hz

//                 for (int i = 0; i < samples; i++) {
//                     double t = (double) i / sr;
//                     double p = (double) i / samples;  // 0 → 1 progress

//                     // Descending tone (wet-drop feel): pitch falls fast
//                     double freq = basePitch * (1.0 - p * 0.7);

//                     // Sine body
//                     double tone = Math.sin(2 * Math.PI * freq * t);

//                     // Noise burst — heavy at start, gone by halfway
//                     double noise = (Math.random() * 2 - 1) * Math.max(0, 1.0 - p * 2.2);

//                     // Sub-bass thump at the very start (the "SPLAT" body)
//                     double thump = Math.sin(2 * Math.PI * 55 * t) * Math.exp(-t * 38);

//                     // Envelope: instant attack, fast exponential decay
//                     double env = Math.exp(-p * 9.0);

//                     double sample = (tone * 0.35 + noise * 0.45 + thump * 0.9) * env;
//                     sample = Math.max(-1.0, Math.min(1.0, sample * 0.85));
//                     buf[i] = (byte)(sample * 127);
//                 }

//                 // Play via SourceDataLine — lightweight, no Clip overhead
//                 javax.sound.sampled.AudioFormat fmt =
//                     new javax.sound.sampled.AudioFormat(sr, 8, 1, true, false);
//                 javax.sound.sampled.DataLine.Info info =
//                     new javax.sound.sampled.DataLine.Info(
//                         javax.sound.sampled.SourceDataLine.class, fmt);
//                 if (!javax.sound.sampled.AudioSystem.isLineSupported(info)) return;
//                 javax.sound.sampled.SourceDataLine line =
//                     (javax.sound.sampled.SourceDataLine)
//                         javax.sound.sampled.AudioSystem.getLine(info);
//                 line.open(fmt, buf.length);
//                 line.start();
//                 line.write(buf, 0, buf.length);
//                 line.drain();
//                 line.close();

//             } catch (Exception ex) { /* silent fail */ }
//         }).start();
//     }

//     /**
//      * Loads and plays a WAV file in a background thread.
//      * Returns instantly — the game loop is never blocked or slowed down.
//      * Completely silent on any error (file missing, wrong format, etc.)
//      */
//     private void playWav(String filename) {
//         new Thread(() -> {
//             try {
//                 java.io.File f = new java.io.File(filename);
//                 if (!f.exists()) return;  // file not found — skip silently

//                 javax.sound.sampled.AudioInputStream raw =
//                     javax.sound.sampled.AudioSystem.getAudioInputStream(f);

//                 // Convert to 16-bit PCM signed — works on all systems
//                 javax.sound.sampled.AudioFormat base = raw.getFormat();
//                 javax.sound.sampled.AudioFormat playFmt = new javax.sound.sampled.AudioFormat(
//                     javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
//                     base.getSampleRate(), 16,
//                     base.getChannels(),
//                     base.getChannels() * 2,
//                     base.getSampleRate(), false);

//                 javax.sound.sampled.AudioInputStream pcm =
//                     javax.sound.sampled.AudioSystem.getAudioInputStream(playFmt, raw);

//                 javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
//                 clip.open(pcm);

//                 // Auto-release resources the moment sound finishes
//                 clip.addLineListener(ev -> {
//                     if (ev.getType() == javax.sound.sampled.LineEvent.Type.STOP)
//                         clip.close();
//                 });

//                 clip.start();  // non-blocking — returns immediately

//             } catch (Exception ex) { /* any error: wrong format, device busy, etc — skip */ }
//         }).start();
//     }

// }