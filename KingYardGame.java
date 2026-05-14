import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.io.*;
import javax.sound.sampled.*;

public class KingYardGame extends JPanel implements ActionListener, KeyListener {

    // ─── Constants ─────────────────────────────────────────────────────────────
    static final int WIDTH        = 900;
    static final int HEIGHT       = 620;
    static final int FPS          = 60;
    static final int WIN_SCORE    = 1800;    // ~30 seconds of holding at 60fps

    // Player
    static final int   P_SIZE     = 28;
    static final float P_SPEED    = 3.8f;
    static final int   STEAL_DIST = P_SIZE + 4;

    // Crown
    static final int   C_SIZE     = 22;

    // Booster
    static final float BOOST_SPEED      = P_SPEED * 2.0f;
    static final int   BOOST_DURATION   = 3 * FPS;
    static final int   BOOST_INTERVAL   = 9 * FPS;
    static final int   BOOST_SIZE       = 22;

    // Mud
    static final float MUD_SPEED        = P_SPEED * 0.35f;  // 35% of normal speed
    static final int   MUD_COUNT        = 3;                 // number of mud patches
    static final int   MUD_W            = 80;
    static final int   MUD_H            = 55;

    // Doc2 mechanics
    static final long  STUN_MS        = 1000;   // stun duration in milliseconds
    static final long  STEAL_COOLDOWN = 400;    // ms between steals
    static final int   BOUNCE_FRAMES  = 14;     // frames the bounce lasts
    static final float BOUNCE_POWER   = 4.5f;   // pixels/frame during bounce

    // ─── Colors ────────────────────────────────────────────────────────────────
    static final Color COL_BG        = new Color(0x33, 0x69, 0x1E);
    static final Color COL_GRASS     = new Color(0x4C, 0xAF, 0x50);  // bright yard green
    static final Color COL_GRID      = new Color(0x0F, 0x3B, 0x52, 80);
    static final Color COL_WALL      = new Color(0x8D, 0x6E, 0x63);  // wooden fence brown
    static final Color COL_WALL_TOP  = new Color(0xBC, 0x98, 0x85);  // fence highlight
    static final Color COL_P1        = new Color(0xFF, 0x6B, 0x6B);
    static final Color COL_P2        = new Color(0x4E, 0xCD, 0xC4);
    static final Color COL_CROWN     = new Color(0xFF, 0xD7, 0x00);
    static final Color COL_TIMER_BG  = new Color(0, 0, 0, 140);
    static final Color COL_STUN      = new Color(0xFF, 0xFF, 0x00, 180);

    // ─── Game State ────────────────────────────────────────────────────────────
    enum State { COUNTDOWN, PLAYING, GAME_OVER }
    State gameState = State.COUNTDOWN;

    // ─── Players ───────────────────────────────────────────────────────────────
    float p1x, p1y, p2x, p2y;
    boolean p1up, p1down, p1left, p1right;
    boolean p2up, p2down, p2left, p2right;

    // ─── Stun System (from Doc2) ───────────────────────────────────────────────
    boolean p1Stunned = false, p2Stunned = false;
    long p1StunEnd = 0, p2StunEnd = 0;

    // ─── Steal Cooldown (from Doc2) ────────────────────────────────────────────
    long lastStealTime = 0;

    // ─── Contact Tracking — steal only fires on FRESH touch, not sustained overlap ──
    boolean playersWereTouching = false;

    // ─── Bounce System (from Doc2) ─────────────────────────────────────────────
    float p1BounceVX = 0, p1BounceVY = 0;
    float p2BounceVX = 0, p2BounceVY = 0;
    int   p1BounceFrames = 0, p2BounceFrames = 0;

    // ─── Crown ─────────────────────────────────────────────────────────────────
    float crownX, crownY;
    int   crownHolder = 0;

    // ─── Booster ───────────────────────────────────────────────────────────────
    boolean boosterActive  = false;
    float   boosterX, boosterY;
    int     boosterSpawnTimer = BOOST_INTERVAL;
    int     p1BoostTicks  = 0;
    int     p2BoostTicks  = 0;

    // ─── Mud Patches ───────────────────────────────────────────────────────────
    int[]   mudX = new int[MUD_COUNT];
    int[]   mudY = new int[MUD_COUNT];

    // ─── Score System (replaces timer win) ─────────────────────────────────────
    int p1Score = 0;
    int p2Score = 0;

    // ─── Countdown ─────────────────────────────────────────────────────────────
    int countdownTicks = 0;
    static final int COUNTDOWN_SECONDS = 3;
    List<Rectangle> walls = new ArrayList<>();

    // ─── Misc ──────────────────────────────────────────────────────────────────
    Timer  timer;
    String winnerName  = "";
    long   frameCount  = 0;
    BufferedImage bgCache;

    // ─── Hub callback ──────────────────────────────────────────────────────────
    GameOverListener onGameOver = null;
    Runnable onBack = null;

    // ─── WAV Music ─────────────────────────────────────────────────────────────
    Clip  musicClip = null;
    String musicFile = "crown_music.wav";  // ← put your WAV filename here

    // ══════════════════════════════════════════════════════════════════════════
    public KingYardGame() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        addKeyListener(this);
        buildWalls();
        buildBackground();
        resetGame();
        timer = new Timer(1000 / FPS, this);
        timer.start();
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Arena walls
    // ──────────────────────────────────────────────────────────────────────────
    private void buildWalls() {
        int T = 20;
        walls.add(new Rectangle(0,           0,          WIDTH, T));
        walls.add(new Rectangle(0,           HEIGHT - T, WIDTH, T));
        walls.add(new Rectangle(0,           0,          T,     HEIGHT));
        walls.add(new Rectangle(WIDTH - T,   0,          T,     HEIGHT));

        int bw = 80, bh = 20;
        walls.add(new Rectangle(120, 120, bw, bh));
        walls.add(new Rectangle(120, 120, bh, bw));
        walls.add(new Rectangle(WIDTH - 120 - bw, 120, bw, bh));
        walls.add(new Rectangle(WIDTH - 120 - bh, 120, bh, bw));
        walls.add(new Rectangle(120, HEIGHT - 120 - bh, bw, bh));
        walls.add(new Rectangle(120, HEIGHT - 120 - bw, bh, bw));
        walls.add(new Rectangle(WIDTH - 120 - bw, HEIGHT - 120 - bh, bw, bh));
        walls.add(new Rectangle(WIDTH - 120 - bh, HEIGHT - 120 - bw, bh, bw));
        walls.add(new Rectangle(WIDTH / 2 - 60, 160,          120, 18));
        walls.add(new Rectangle(WIDTH / 2 - 60, HEIGHT - 178, 120, 18));
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Pre-render static background
    // ──────────────────────────────────────────────────────────────────────────
    private void buildBackground() {
        bgCache = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bgCache.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ── Base grass ───────────────────────────────────────────────────────
        g.setColor(new Color(0x4C, 0xAF, 0x50));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Slightly darker grass patches for texture variation
        g.setColor(new Color(0x43, 0xA0, 0x47, 120));
        int[] patchX = {60,220,500,700,100,400,750,300,600,150,650,350};
        int[] patchY = {80,200,60,150,400,450,380,300,500,530,270,120};
        int[] patchW = {90,70,110,80,100,90,70,80,100,75,85,65};
        int[] patchH = {50,40,60,45,55,50,40,45,55,42,48,38};
        for (int i = 0; i < patchX.length; i++)
            g.fillOval(patchX[i], patchY[i], patchW[i], patchH[i]);

        // ── Dirt path — diagonal from top-right to bottom-left ───────────────
        g.setColor(new Color(0xA1, 0x78, 0x58, 160));
        g.setStroke(new BasicStroke(38f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(WIDTH - 80, 60, 80, HEIGHT - 80);
        g.setColor(new Color(0xBC, 0x98, 0x75, 80));
        g.setStroke(new BasicStroke(22f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(WIDTH - 80, 60, 80, HEIGHT - 80);

        // ── Outer border — thick hedge/fence ring ────────────────────────────
        g.setStroke(new BasicStroke(18f));
        g.setColor(new Color(0x2E, 0x7D, 0x32));
        g.drawRect(9, 9, WIDTH - 18, HEIGHT - 18);
        // inner fence line
        g.setColor(new Color(0x1B, 0x5E, 0x20));
        g.setStroke(new BasicStroke(4f));
        g.drawRect(18, 18, WIDTH - 36, HEIGHT - 36);

        // Fence post marks along border
        g.setColor(new Color(0x33, 0x69, 0x1E));
        for (int x = 30; x < WIDTH - 20; x += 40) {
            g.fillRect(x, 4,  6, 22);
            g.fillRect(x, HEIGHT - 26, 6, 22);
        }
        for (int y = 30; y < HEIGHT - 20; y += 40) {
            g.fillRect(4,  y, 22, 6);
            g.fillRect(WIDTH - 26, y, 22, 6);
        }

        // ── Trees (top-down circles with shadow) ─────────────────────────────
        int[][] trees = {
            {200, 80},  {720, 70},  {820, 220}, {760, 480},
            {170, 470}, {440, 520}, {60, 280},
        };
        for (int[] t : trees) {
            // shadow
            g.setColor(new Color(0, 0, 0, 40));
            g.fillOval(t[0] + 6, t[1] + 8, 46, 46);
            // dark outer foliage
            g.setColor(new Color(0x2E, 0x7D, 0x32));
            g.fillOval(t[0], t[1], 46, 46);
            // bright inner foliage
            g.setColor(new Color(0x66, 0xBB, 0x6A));
            g.fillOval(t[0] + 6, t[1] + 4, 32, 32);
            // highlight
            g.setColor(new Color(0xA5, 0xD6, 0xA7, 160));
            g.fillOval(t[0] + 10, t[1] + 7, 14, 12);
        }

        // ── Small decorative flowers ─────────────────────────────────────────
        int[][] flowers = {
            {310, 130, 0xFFD700}, {560, 90, 0xFF6B6B},  {670, 310, 0xFF8C00},
            {220, 350, 0xFF6B6B}, {480, 410, 0xFFD700}, {700, 540, 0xFF6B6B},
            {370, 540, 0xFFFFFF}, {130, 200, 0xFF8C00},
        };
        for (int[] f : flowers) {
            Color fc = new Color(f[2]);
            // petals
            g.setColor(fc);
            g.fillOval(f[0]-4, f[1]-8, 8, 8);
            g.fillOval(f[0]-4, f[1]+2, 8, 8);
            g.fillOval(f[0]-8, f[1]-3, 8, 8);
            g.fillOval(f[0]+1, f[1]-3, 8, 8);
            // center
            g.setColor(new Color(0xFF, 0xE0, 0x82));
            g.fillOval(f[0]-3, f[1]-3, 7, 7);
        }

        // ── Centre crown zone marker ─────────────────────────────────────────
        g.setColor(new Color(0xFF, 0xD7, 0x00, 30));
        g.fillOval(WIDTH / 2 - 55, HEIGHT / 2 - 55, 110, 110);
        g.setColor(new Color(0xFF, 0xD7, 0x00, 70));
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    0, new float[]{8, 6}, 0));
        g.drawOval(WIDTH / 2 - 55, HEIGHT / 2 - 55, 110, 110);

        // ── Fence/wall obstacles ─────────────────────────────────────────────
        g.setStroke(new BasicStroke(1f));
        for (Rectangle w : walls) {
            // shadow
            g.setColor(new Color(0, 0, 0, 55));
            g.fillRoundRect(w.x + 3, w.y + 4, w.width, w.height, 4, 4);
            // body
            g.setColor(COL_WALL);
            g.fillRoundRect(w.x, w.y, w.width, w.height, 4, 4);
            // top highlight
            g.setColor(COL_WALL_TOP);
            g.fillRoundRect(w.x, w.y, w.width, Math.min(5, w.height), 4, 4);
            // plank lines on longer walls
            g.setColor(new Color(0x6D, 0x4C, 0x41, 120));
            g.setStroke(new BasicStroke(1f));
            if (w.width > w.height) {
                for (int x = w.x + 16; x < w.x + w.width - 4; x += 16)
                    g.drawLine(x, w.y + 2, x, w.y + w.height - 2);
            } else {
                for (int y = w.y + 16; y < w.y + w.height - 4; y += 16)
                    g.drawLine(w.x + 2, y, w.x + w.width - 2, y);
            }
        }

        g.dispose();
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Reset / Start
    // ──────────────────────────────────────────────────────────────────────────
    private void resetGame() {
        p1x = 80;  p1y = HEIGHT / 2f - P_SIZE / 2f;
        p2x = WIDTH - 80 - P_SIZE; p2y = HEIGHT / 2f - P_SIZE / 2f;
        crownX = WIDTH / 2f - C_SIZE / 2f;
        crownY = HEIGHT / 2f - C_SIZE / 2f;
        crownHolder = 0;
        p1Score = 0; p2Score = 0;
        p1Stunned = false; p2Stunned = false;
        p1BounceFrames = 0; p2BounceFrames = 0;
        p1BoostTicks = 0;  p2BoostTicks = 0;
        boosterActive = false;
        boosterSpawnTimer = BOOST_INTERVAL;
        placeMud();
        lastStealTime = 0;
        playersWereTouching = false;
        stopMusic();
        winnerName = "";
        countdownTicks = COUNTDOWN_SECONDS * FPS;
        gameState = State.COUNTDOWN;
    }

    private void startGame() {
        p1x = 80;  p1y = HEIGHT / 2f - P_SIZE / 2f;
        p2x = WIDTH - 80 - P_SIZE; p2y = HEIGHT / 2f - P_SIZE / 2f;
        crownX = WIDTH / 2f - C_SIZE / 2f;
        crownY = HEIGHT / 2f - C_SIZE / 2f;
        crownHolder = 0;
        p1Score = 0; p2Score = 0;
        p1Stunned = false; p2Stunned = false;
        p1BounceFrames = 0; p2BounceFrames = 0;
        p1BoostTicks = 0;  p2BoostTicks = 0;
        boosterActive = false;
        boosterSpawnTimer = BOOST_INTERVAL;
        placeMud();
        lastStealTime = 0;
        playersWereTouching = false;
        stopMusic();
        winnerName = "";
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
            updateStuns();
            movePlayer1();
            movePlayer2();
            applyBounce();
            pushApart();
            updateCrown();
            updateBooster();
            updateScore();
            checkWin();
        }
        frameCount++;
        repaint();
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Stun tick update
    // ──────────────────────────────────────────────────────────────────────────
    private void updateStuns() {
        long now = System.currentTimeMillis();
        if (p1Stunned && now > p1StunEnd) p1Stunned = false;
        if (p2Stunned && now > p2StunEnd) p2Stunned = false;
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Movement with wall collision (stunned players can't move)
    // ──────────────────────────────────────────────────────────────────────────
    private void movePlayer1() {
        if (p1Stunned) return;
        float spd = p1BoostTicks > 0 ? BOOST_SPEED
                  : isInMud(p1x, p1y)  ? MUD_SPEED
                  : P_SPEED;
        float dx = 0, dy = 0;
        if (p1up)    dy -= spd;
        if (p1down)  dy += spd;
        if (p1left)  dx -= spd;
        if (p1right) dx += spd;
        if (dx != 0 && dy != 0) { dx *= 0.707f; dy *= 0.707f; }
        p1x = clampX(resolveX(p1x, p1y, dx));
        p1y = clampY(resolveY(p1x, p1y, dy));
    }

    private void movePlayer2() {
        if (p2Stunned) return;
        float spd = p2BoostTicks > 0 ? BOOST_SPEED
                  : isInMud(p2x, p2y)  ? MUD_SPEED
                  : P_SPEED;
        float dx = 0, dy = 0;
        if (p2up)    dy -= spd;
        if (p2down)  dy += spd;
        if (p2left)  dx -= spd;
        if (p2right) dx += spd;
        if (dx != 0 && dy != 0) { dx *= 0.707f; dy *= 0.707f; }
        p2x = clampX(resolveX(p2x, p2y, dx));
        p2y = clampY(resolveY(p2x, p2y, dy));
    }
    

    // ── Smooth Bounce Animation (from Doc2) ───────────────────────────────────
    private void applyBounce() {
        if (p1BounceFrames > 0) {
            p1x = clampX(p1x + p1BounceVX);
            p1y = clampY(p1y + p1BounceVY);
            p1BounceFrames--;
        }
        if (p2BounceFrames > 0) {
            p2x = clampX(p2x + p2BounceVX);
            p2y = clampY(p2y + p2BounceVY);
            p2BounceFrames--;
        }
    }

    // ── Push-apart overlap resolution (from Doc2) ─────────────────────────────
    private void pushApart() {
        Rectangle r1 = new Rectangle((int) p1x, (int) p1y, P_SIZE, P_SIZE);
        Rectangle r2 = new Rectangle((int) p2x, (int) p2y, P_SIZE, P_SIZE);
        if (r1.intersects(r2)) {
            int overlapX = Math.min(r1.x + r1.width, r2.x + r2.width) - Math.max(r1.x, r2.x);
            int overlapY = Math.min(r1.y + r1.height, r2.y + r2.height) - Math.max(r1.y, r2.y);
            if (overlapX < overlapY) {
                int half = overlapX / 2;
                if (p1x < p2x) { p1x -= half; p2x += half; }
                else            { p1x += half; p2x -= half; }
            } else {
                int half = overlapY / 2;
                if (p1y < p2y) { p1y -= half; p2y += half; }
                else            { p1y += half; p2y -= half; }
            }
        }
    }

    private float resolveX(float px, float py, float dx) {
        float nx = px + dx;
        Rectangle r = new Rectangle((int) nx, (int) py, P_SIZE, P_SIZE);
        for (Rectangle w : walls) if (w.intersects(r)) return px;
        return nx;
    }

    private float resolveY(float px, float py, float dy) {
        float ny = py + dy;
        Rectangle r = new Rectangle((int) px, (int) ny, P_SIZE, P_SIZE);
        for (Rectangle w : walls) if (w.intersects(r)) return py;
        return ny;
    }

    private float clampX(float x) { return Math.max(20, Math.min(WIDTH  - P_SIZE - 20, x)); }
    private float clampY(float y) { return Math.max(20, Math.min(HEIGHT - P_SIZE - 20, y)); }

    // ──────────────────────────────────────────────────────────────────────────
    //  Bounce helper
    // ──────────────────────────────────────────────────────────────────────────
    // dx/dy should point FROM attacker TO loser (loser's escape direction)
    private void triggerBounce(int loser, float dx, float dy) {
        float len = Math.max(0.01f, (float) Math.sqrt(dx * dx + dy * dy));
        float nx = dx / len;
        float ny = dy / len;

        if (loser == 1) {
            p1BounceVX = nx * BOUNCE_POWER;
            p1BounceVY = ny * BOUNCE_POWER;
            p1BounceFrames = BOUNCE_FRAMES;
        } else {
            p2BounceVX = nx * BOUNCE_POWER;
            p2BounceVY = ny * BOUNCE_POWER;
            p2BounceFrames = BOUNCE_FRAMES;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  WAV Music helpers  (no external library needed)
    // ──────────────────────────────────────────────────────────────────────────
    private void playMusic() {
        stopMusic();
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(new File(musicFile));
            AudioFormat original = ais.getFormat();

            // Java Clip only supports 8-bit or 16-bit PCM — convert if needed
            AudioFormat target = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                original.getSampleRate(),
                16,                          // force 16-bit
                original.getChannels(),
                original.getChannels() * 2,  // frame size = channels * 2 bytes
                original.getSampleRate(),
                false                        // little-endian
            );

            AudioInputStream converted = AudioSystem.getAudioInputStream(target, ais);
            musicClip = AudioSystem.getClip();
            musicClip.open(converted);
            musicClip.start();  // play once, no loop
        } catch (Exception e) {
            System.out.println("Could not play music: " + e.getMessage());
        }
    }

    private void stopMusic() {
        if (musicClip != null) {
            musicClip.stop();
            musicClip.close();
            musicClip = null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Crown Logic  — entry-based steal (only on FRESH contact, not sustained)
    // ──────────────────────────────────────────────────────────────────────────
    private void updateCrown() {
        long now = System.currentTimeMillis();

        // Detect whether players are touching THIS frame
        boolean touchingNow = dist(p1x, p1y, p2x, p2y) < STEAL_DIST;
        // A steal attempt is only valid if this is a NEW collision (weren't touching last frame)
        boolean freshContact = touchingNow && !playersWereTouching;

        if (crownHolder == 0) {
            // Free crown — either player can walk into it
            if (dist(p1x, p1y, crownX, crownY) < STEAL_DIST + 10) {
                crownHolder = 1;
                playMusic();
                lastStealTime = now;   // short grace period so P2 can't insta-steal
                playersWereTouching = touchingNow;
                return;
            } else if (dist(p2x, p2y, crownX, crownY) < STEAL_DIST + 10) {
                crownHolder = 2;
                playMusic();
                lastStealTime = now;
                playersWereTouching = touchingNow;
                return;
            }
        } else if (crownHolder == 1) {
            // Crown follows P1
            crownX = p1x + P_SIZE / 2f - C_SIZE / 2f;
            crownY = p1y - C_SIZE - 4;

            // P2 steals only on a FRESH touch + cooldown elapsed
            if (freshContact && now - lastStealTime > STEAL_COOLDOWN) {
                crownHolder = 2;
                playMusic();
                p1Stunned = true;
                p1StunEnd = now + STUN_MS;
                float dx = p1x - p2x, dy = p1y - p2y;
                triggerBounce(1, dx, dy);
                lastStealTime = now;
            }
        } else {
            // Crown follows P2
            crownX = p2x + P_SIZE / 2f - C_SIZE / 2f;
            crownY = p2y - C_SIZE - 4;

            // P1 steals only on a FRESH touch + cooldown elapsed
            if (freshContact && now - lastStealTime > STEAL_COOLDOWN) {
                crownHolder = 1;
                playMusic();
                p2Stunned = true;
                p2StunEnd = now + STUN_MS;
                float dx = p2x - p1x, dy = p2y - p1y;
                triggerBounce(2, dx, dy);
                lastStealTime = now;
            }
        }

        // Update touch state for next frame
        playersWereTouching = touchingNow;
    }

    private float dist(float ax, float ay, float bx, float by) {
        float dx = (ax + P_SIZE / 2f) - (bx + P_SIZE / 2f);
        float dy = (ay + P_SIZE / 2f) - (by + P_SIZE / 2f);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Score + Win (replaces hold-timer win)
    // ──────────────────────────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────────────────────────
    //  Booster Logic — spawns every 9s, gives 3s speed boost to picker
    // ──────────────────────────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────────────────────────
    //  Mud Patch helpers
    // ──────────────────────────────────────────────────────────────────────────
    private void placeMud() {
        int MIN_DIST = 200;  // minimum pixel distance between any two mud patches
        int placed = 0;
        int totalAttempts = 0;

        while (placed < MUD_COUNT && totalAttempts < 500) {
            totalAttempts++;
            int mx = 80 + (int)(Math.random() * (WIDTH  - 160 - MUD_W));
            int my = 80 + (int)(Math.random() * (HEIGHT - 160 - MUD_H));

            // Avoid player spawn zones and centre crown
            boolean tooCloseP1     = mx < 180 && my > HEIGHT/2 - 80 && my < HEIGHT/2 + 80;
            boolean tooCloseP2     = mx > WIDTH - 180 && my > HEIGHT/2 - 80 && my < HEIGHT/2 + 80;
            boolean tooCloseCenter = Math.abs(mx + MUD_W/2 - WIDTH/2) < 80 && Math.abs(my + MUD_H/2 - HEIGHT/2) < 60;

            // Avoid walls
            boolean hitsWall = false;
            Rectangle mr = new Rectangle(mx, my, MUD_W, MUD_H);
            for (Rectangle w : walls) if (w.intersects(mr)) { hitsWall = true; break; }

            // Enforce minimum distance from already-placed patches
            boolean tooClose = false;
            for (int j = 0; j < placed; j++) {
                float dx = (mx + MUD_W/2f) - (mudX[j] + MUD_W/2f);
                float dy = (my + MUD_H/2f) - (mudY[j] + MUD_H/2f);
                if (Math.sqrt(dx*dx + dy*dy) < MIN_DIST) { tooClose = true; break; }
            }

            if (!tooCloseP1 && !tooCloseP2 && !tooCloseCenter && !hitsWall && !tooClose) {
                mudX[placed] = mx;
                mudY[placed] = my;
                placed++;
            }
        }
    }

    private boolean isInMud(float px, float py) {
        for (int i = 0; i < MUD_COUNT; i++) {
            if (px + P_SIZE > mudX[i] && px < mudX[i] + MUD_W
             && py + P_SIZE > mudY[i] && py < mudY[i] + MUD_H) return true;
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Booster Logic — spawns every 9s, near crown holder if possible
    // ──────────────────────────────────────────────────────────────────────────
    private void updateBooster() {
        if (p1BoostTicks > 0) p1BoostTicks--;
        if (p2BoostTicks > 0) p2BoostTicks--;

        if (!boosterActive) {
            boosterSpawnTimer--;
            if (boosterSpawnTimer <= 0) {
                // Spawn near crown holder; random if crown is free
                float targetX = crownHolder == 1 ? p1x : crownHolder == 2 ? p2x : WIDTH / 2f;
                float targetY = crownHolder == 1 ? p1y : crownHolder == 2 ? p2y : HEIGHT / 2f;
                float angle   = (float)(Math.random() * Math.PI * 2);
                float dist    = 80 + (float)(Math.random() * 80);
                boosterX = Math.max(40, Math.min(WIDTH  - 60, targetX + (float)Math.cos(angle) * dist));
                boosterY = Math.max(40, Math.min(HEIGHT - 60, targetY + (float)Math.sin(angle) * dist));
                boosterActive = true;
                boosterSpawnTimer = BOOST_INTERVAL;
            }
        } else {
            if (Math.abs((p1x + P_SIZE/2f) - (boosterX + BOOST_SIZE/2f)) < (P_SIZE + BOOST_SIZE) / 2f
             && Math.abs((p1y + P_SIZE/2f) - (boosterY + BOOST_SIZE/2f)) < (P_SIZE + BOOST_SIZE) / 2f) {
                p1BoostTicks = BOOST_DURATION; boosterActive = false;
            } else if (Math.abs((p2x + P_SIZE/2f) - (boosterX + BOOST_SIZE/2f)) < (P_SIZE + BOOST_SIZE) / 2f
                    && Math.abs((p2y + P_SIZE/2f) - (boosterY + BOOST_SIZE/2f)) < (P_SIZE + BOOST_SIZE) / 2f) {
                p2BoostTicks = BOOST_DURATION; boosterActive = false;
            }
        }
    }

    private void updateScore() {
        if (crownHolder == 1) p1Score++;
        if (crownHolder == 2) p2Score++;
    }

    private void checkWin() {
        if (p1Score >= WIN_SCORE) {
            winnerName = "Player 1"; stopMusic(); gameState = State.GAME_OVER;
        }
        if (p2Score >= WIN_SCORE) {
            winnerName = "Player 2"; stopMusic(); gameState = State.GAME_OVER;
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

        g.drawImage(bgCache, 0, 0, null);

        if (gameState == State.COUNTDOWN) { drawCountdown(g); return; }

        drawMud(g);
        drawPlayers(g);
        if (crownHolder == 0) drawFreeCrown(g);
        if (boosterActive)    drawBooster(g);
        drawHUD(g);
        if (gameState == State.GAME_OVER) drawGameOver(g);
    }

    // ── Players ───────────────────────────────────────────────────────────────
    private void drawPlayers(Graphics2D g) {
        drawPlayer(g, (int) p1x, (int) p1y, COL_P1, "P1", crownHolder == 1, p1Stunned);
        drawPlayer(g, (int) p2x, (int) p2y, COL_P2, "P2", crownHolder == 2, p2Stunned);
    }

    private void drawPlayer(Graphics2D g, int x, int y, Color col, String label, boolean hasKing, boolean stunned) {
        // Stun flash ring (yellow, from Doc2)
        if (stunned) {
            float flash = 0.5f + 0.5f * (float) Math.sin(frameCount * 0.4);
            int alpha = (int)(120 + 100 * flash);
            g.setColor(new Color(COL_STUN.getRed(), COL_STUN.getGreen(), COL_STUN.getBlue(), alpha));
            g.fillOval(x - 10, y - 10, P_SIZE + 20, P_SIZE + 20);
            // "STUNNED" text
            g.setFont(new Font("Arial Black", Font.BOLD, 9));
            g.setColor(new Color(255, 255, 0, 200));
            g.drawString("STUNNED", x - 2, y - 14);
        }

        // Glow when king
        if (hasKing) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(frameCount * 0.15);
            int glow = (int)(60 + 60 * pulse);
            g.setColor(new Color(COL_CROWN.getRed(), COL_CROWN.getGreen(), COL_CROWN.getBlue(), glow));
            g.fillOval(x - 10, y - 10, P_SIZE + 20, P_SIZE + 20);
        }

        // Shadow
        g.setColor(new Color(0, 0, 0, 80));
        g.fillOval(x + 3, y + 5, P_SIZE, P_SIZE);

        // Body — grey-tinted when stunned
        Color bodyCol = stunned ? col.darker().darker() : col;
        GradientPaint gp = new GradientPaint(x, y, bodyCol.brighter(), x, y + P_SIZE, bodyCol.darker());
        g.setPaint(gp);
        g.fillOval(x, y, P_SIZE, P_SIZE);

        // Outline
        g.setColor(bodyCol.darker());
        g.setStroke(new BasicStroke(2f));
        g.drawOval(x, y, P_SIZE, P_SIZE);

        // Face
        g.setColor(new Color(255, 255, 255, 200));
        g.fillOval(x + 7,  y + 8,  6, 6);
        g.fillOval(x + 15, y + 8,  6, 6);
        g.setColor(new Color(0, 0, 0, 160));
        g.fillOval(x + 9,  y + 10, 3, 3);
        g.fillOval(x + 17, y + 10, 3, 3);
        g.setColor(new Color(255, 255, 255, 180));
        g.setStroke(new BasicStroke(1.5f));
        // sad face when stunned, smile when normal
        if (stunned) g.drawArc(x + 7, y + 17, 14, 8, 20, 140);
        else         g.drawArc(x + 7, y + 14, 14, 8, 200, 140);

        if (hasKing) drawCrownOnPlayer(g, x, y);

        g.setFont(new Font("Arial Black", Font.BOLD, 10));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, x + (P_SIZE - fm.stringWidth(label)) / 2, y + P_SIZE + 14);
    }

    private void drawCrownOnPlayer(Graphics2D g, int px, int py) {
        int cx = px + P_SIZE / 2 - C_SIZE / 2;
        int cy = py - C_SIZE - 2;
        drawCrown(g, cx, cy, 1.0f);
    }

    private void drawFreeCrown(Graphics2D g) {
        float bob = (float) Math.sin(frameCount * 0.08) * 4f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(frameCount * 0.1);
        int alpha = (int)(80 + 80 * pulse);
        g.setColor(new Color(255, 215, 0, alpha));
        int cr = (int) crownX, ct = (int)(crownY + bob);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(cr - 14, ct - 14, C_SIZE + 28, C_SIZE + 28);
        drawCrown(g, cr, ct, 1.2f);
    }

    private void drawCrown(Graphics2D g, int cx, int cy, float scale) {
        int w = (int)(C_SIZE * scale);
        int h = (int)(C_SIZE * 0.75 * scale);

        g.setColor(new Color(0, 0, 0, 80));
        g.fillOval(cx - 2 + 3, cy + h - 3, w + 4, 6);

        int[] bx = { cx, cx + w, cx + w - 4, cx + 4 };
        int[] by = { cy + h / 2, cy + h / 2, cy + h, cy + h };
        g.setColor(new Color(0xD4, 0xA0, 0x17));
        g.fillPolygon(bx, by, 4);

        int[] px = { cx, cx + w/4, cx + w/2, cx + 3*w/4, cx + w };
        int[] py = { cy + h/2, cy, cy + h/4, cy, cy + h/2 };
        g.setColor(COL_CROWN);
        g.fillPolygon(px, py, 5);

        g.setColor(new Color(0xB8, 0x86, 0x0B));
        g.setStroke(new BasicStroke(1.5f));
        g.drawPolygon(px, py, 5);
        g.drawPolygon(bx, by, 4);

        g.setColor(new Color(0xFF, 0x4D, 0x4D));
        g.fillOval(cx + w/2 - 3, cy + h/4 - 2, 6, 6);
        g.setColor(new Color(0x4D, 0x9F, 0xFF));
        g.fillOval(cx + 2, cy + h/2 - 3, 5, 5);
        g.fillOval(cx + w - 7, cy + h/2 - 3, 5, 5);
    }

    // ── Mud patches on field ──────────────────────────────────────────────────
    private void drawMud(Graphics2D g) {
        for (int i = 0; i < MUD_COUNT; i++) {
            int mx = mudX[i], my = mudY[i];

            // Dark brown muddy base
            g.setColor(new Color(0x5D, 0x40, 0x37, 200));
            g.fillOval(mx, my, MUD_W, MUD_H);

            // Lighter splodge in middle for texture
            g.setColor(new Color(0x78, 0x55, 0x40, 160));
            g.fillOval(mx + 10, my + 8, MUD_W - 20, MUD_H - 16);

            // Mud puddle shine
            g.setColor(new Color(0x90, 0x6A, 0x50, 100));
            g.fillOval(mx + 18, my + 10, 20, 10);

            // Dashed border
            g.setColor(new Color(0x3E, 0x2A, 0x20, 180));
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        0, new float[]{6, 4}, 0));
            g.drawOval(mx, my, MUD_W, MUD_H);
            g.setStroke(new BasicStroke(1f));
        }
    }

    // ── Booster pickup on field ───────────────────────────────────────────────
    private void drawBooster(Graphics2D g) {
        int bx = (int) boosterX, by = (int) boosterY;
        float bob = (float) Math.sin(frameCount * 0.1) * 3f;
        by += (int) bob;
        float pulse = 0.5f + 0.5f * (float) Math.sin(frameCount * 0.12);
        int alpha = (int)(80 + 80 * pulse);

        // Glow ring
        g.setColor(new Color(0xFF, 0xA5, 0x00, alpha));
        g.fillOval(bx - 8, by - 8, BOOST_SIZE + 16, BOOST_SIZE + 16);

        // Orange circle body
        g.setColor(new Color(0xFF, 0xA5, 0x00));
        g.fillOval(bx, by, BOOST_SIZE, BOOST_SIZE);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(bx, by, BOOST_SIZE, BOOST_SIZE);

        // Lightning bolt icon
        g.setFont(new Font("Arial Black", Font.BOLD, 13));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        String icon = "Z";
        g.drawString(icon, bx + (BOOST_SIZE - fm.stringWidth(icon)) / 2, by + BOOST_SIZE - 5);
    }

    // ── HUD — score bars instead of hold-timer ────────────────────────────────
    private void drawHUD(Graphics2D g) {
        drawScoreBar(g, 30, 30, p1Score, COL_P1, "P1  WASD");
        drawScoreBar(g, WIDTH - 230, 30, p2Score, COL_P2, "P2  ← → ↑ ↓");

        // Boost indicators
        if (p1BoostTicks > 0) {
            g.setFont(new Font("Arial Black", Font.BOLD, 12));
            g.setColor(new Color(0xFF, 0xA5, 0x00));
            g.drawString("⚡ BOOST " + (p1BoostTicks / FPS + 1) + "s", 38, 72);
        }
        if (p2BoostTicks > 0) {
            g.setFont(new Font("Arial Black", Font.BOLD, 12));
            g.setColor(new Color(0xFF, 0xA5, 0x00));
            g.drawString("⚡ BOOST " + (p2BoostTicks / FPS + 1) + "s", WIDTH - 222, 72);
        }

        String status;
        Color sc;
        if (crownHolder == 0)       { status = "👑  FREE!"; sc = COL_CROWN; }
        else if (crownHolder == 1)  { status = "👑  P1 is KING!"; sc = COL_P1; }
        else                        { status = "👑  P2 is KING!"; sc = COL_P2; }

        g.setFont(new Font("Arial Black", Font.BOLD, 16));
        FontMetrics fm = g.getFontMetrics();
        int sw = fm.stringWidth(status);
        g.setColor(COL_TIMER_BG);
        g.fillRoundRect(WIDTH / 2 - sw / 2 - 14, 14, sw + 28, 32, 16, 16);
        g.setColor(sc);
        g.drawString(status, WIDTH / 2 - sw / 2, 36);

        // Back hint
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(new Color(255, 255, 255, 80));
        g.drawString("ESC = Menu", WIDTH - 75, 15);
    }

    private void drawScoreBar(Graphics2D g, int x, int y, int score, Color col, String label) {
        int barW = 200, barH = 22;
        float pct = Math.min(1f, (float) score / WIN_SCORE);

        g.setColor(COL_TIMER_BG);
        g.fillRoundRect(x, y, barW, barH + 30, 12, 12);

        g.setColor(new Color(255, 255, 255, 30));
        g.fillRoundRect(x + 8, y + 30, barW - 16, barH - 6, 8, 8);

        GradientPaint gp = new GradientPaint(x, y, col.brighter(), x + (int)((barW - 16) * pct), y, col);
        g.setPaint(gp);
        g.fillRoundRect(x + 8, y + 30, (int)((barW - 16) * pct), barH - 6, 8, 8);

        g.setFont(new Font("Arial Black", Font.BOLD, 13));
        g.setColor(col);
        g.drawString(label, x + 8, y + 18);

        // Show score as seconds equivalent
        int secsHeld = score / FPS;
        int secsNeeded = WIN_SCORE / FPS;
        String scoreStr = secsHeld + " / " + secsNeeded + "s";
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(scoreStr, x + barW - fm.stringWidth(scoreStr) - 10, y + 18);
    }

    // ── Countdown 3-2-1 ──────────────────────────────────────────────────────
    private void drawCountdown(Graphics2D g) {
        // Still draw players in background so players can see starting positions
        drawPlayers(g);

        // Dark overlay
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        int secondsLeft = (countdownTicks + FPS - 1) / FPS;  // ceiling division
        String label = secondsLeft > 0 ? String.valueOf(secondsLeft) : "GO!";

        // Pulse scale — number grows as second ticks down
        float tickInSecond = (countdownTicks % FPS);
        float pulse = 1.0f + 0.5f * (tickInSecond / FPS);  // 1.5 → 1.0 during each second

        int fontSize = (int)(120 * pulse);
        g.setFont(new Font("Arial Black", Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();

        // Pick color per number
        Color numCol = secondsLeft == 3 ? new Color(0xFF,0x6B,0x6B)
                     : secondsLeft == 2 ? new Color(0xFF,0xD7,0x00)
                     :                    new Color(0x4E,0xCD,0xC4);

        // Shadow
        g.setColor(new Color(0, 0, 0, 180));
        g.drawString(label, (WIDTH - fm.stringWidth(label)) / 2 + 5, HEIGHT / 2 + 45);
        // Main
        g.setColor(numCol);
        g.drawString(label, (WIDTH - fm.stringWidth(label)) / 2, HEIGHT / 2 + 40);

        // Small hint at bottom
        g.setFont(new Font("Arial Black", Font.BOLD, 16));
        g.setColor(new Color(255, 255, 255, 160));
        String hint = "P1: WASD     P2: Arrow Keys";
        fm = g.getFontMetrics();
        g.drawString(hint, (WIDTH - fm.stringWidth(hint)) / 2, HEIGHT - 40);
    }

    // ── Game Over ────────────────────────────────────────────────────────────
    private void drawGameOver(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        drawCrown(g, WIDTH / 2 - 36, HEIGHT / 2 - 160, 3.0f);

        g.setFont(new Font("Arial Black", Font.BOLD, 58));
        FontMetrics fm = g.getFontMetrics();
        String msg = winnerName + " is KING! 👑";
        Color wCol = winnerName.contains("1") ? COL_P1 : COL_P2;

        g.setColor(new Color(0, 0, 0, 150));
        g.drawString(msg, (WIDTH - fm.stringWidth(msg)) / 2 + 4, HEIGHT / 2 + 4);
        g.setColor(wCol);
        g.drawString(msg, (WIDTH - fm.stringWidth(msg)) / 2, HEIGHT / 2);

        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(new Color(255, 255, 200));
        String stats = "P1: " + (p1Score / FPS) + "s held   |   P2: " + (p2Score / FPS) + "s held";
        fm = g.getFontMetrics();
        g.drawString(stats, (WIDTH - fm.stringWidth(stats)) / 2, HEIGHT / 2 + 50);

        g.setFont(new Font("Arial Black", Font.BOLD, 20));
        String restart = "ENTER — Play Again     ESC — Menu";
        fm = g.getFontMetrics();
        int alpha = 160 + (int)(95 * Math.sin(System.currentTimeMillis() / 400.0));
        g.setColor(new Color(255, 255, 255, Math.min(255, alpha)));
        g.drawString(restart, (WIDTH - fm.stringWidth(restart)) / 2, HEIGHT / 2 + 110);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Key Events
    // ══════════════════════════════════════════════════════════════════════════
    @Override public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W:      p1up    = true;  break;
            case KeyEvent.VK_S:      p1down  = true;  break;
            case KeyEvent.VK_A:      p1left  = true;  break;
            case KeyEvent.VK_D:      p1right = true;  break;
            case KeyEvent.VK_UP:     p2up    = true;  break;
            case KeyEvent.VK_DOWN:   p2down  = true;  break;
            case KeyEvent.VK_LEFT:   p2left  = true;  break;
            case KeyEvent.VK_RIGHT:  p2right = true;  break;
            case KeyEvent.VK_ENTER:
                if (gameState == State.GAME_OVER) startGame();
                break;
            case KeyEvent.VK_ESCAPE:
                if (onBack != null) { timer.stop(); stopMusic(); onBack.run(); }
                break;
        }
    }

    @Override public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W:      p1up    = false; break;
            case KeyEvent.VK_S:      p1down  = false; break;
            case KeyEvent.VK_A:      p1left  = false; break;
            case KeyEvent.VK_D:      p1right = false; break;
            case KeyEvent.VK_UP:     p2up    = false; break;
            case KeyEvent.VK_DOWN:   p2down  = false; break;
            case KeyEvent.VK_LEFT:   p2left  = false; break;
            case KeyEvent.VK_RIGHT:  p2right = false; break;
        }
    }

    @Override public void keyTyped(KeyEvent e) {}
}