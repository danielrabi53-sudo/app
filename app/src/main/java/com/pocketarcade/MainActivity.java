package com.pocketarcade;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.util.Arrays;
import java.util.Random;

public class MainActivity extends Activity {
    private ArcadeView arcade;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        arcade = new ArcadeView();
        setContentView(arcade);
    }

    @Override
    public void onBackPressed() {
        if (arcade != null && arcade.screen != ArcadeView.HOME) {
            arcade.goHome();
        } else {
            super.onBackPressed();
        }
    }

    private class ArcadeView extends View {
        static final int HOME = 0, SNAKE = 1, MERGE = 2, MEMORY = 3, BREAKER = 4, REACTION = 5;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Random random = new Random();
        private final float density;
        private float w, h;
        private int screen = HOME;
        private int selected = 0;
        private float scrollY = 0;
        private float downX, downY;
        private long lastTick = 0;
        private long gameStartedAt = 0;

        private final int bg = Color.rgb(10, 14, 29);
        private final int panel = Color.rgb(19, 25, 46);
        private final int panel2 = Color.rgb(28, 36, 62);
        private final int muted = Color.rgb(157, 171, 200);
        private final int white = Color.rgb(246, 248, 255);
        private final int mint = Color.rgb(91, 239, 202);
        private final int purple = Color.rgb(157, 122, 255);
        private final int orange = Color.rgb(255, 179, 92);
        private final int blue = Color.rgb(93, 169, 255);
        private final String[] titles = {"נחש", "2048", "זיכרון", "שובר לבנים", "תגובה מהירה"};
        private final String[] subtitles = {"אוספים נקודות", "מחברים מספרים", "מוצאים זוגות", "פוגעים ושוברים", "כמה מהר תגיב?"};
        private final String[] tags = {"ARCADE", "PUZZLE", "MEMORY", "ACTION", "REFLEX"};
        private final int[] accents = {mint, purple, orange, blue, Color.rgb(255, 111, 153)};

        // Snake state
        private final int[] snakeX = new int[256];
        private final int[] snakeY = new int[256];
        private int snakeLength, snakeDir, foodX, foodY, snakeScore;
        private boolean snakeStarted, snakeOver;
        private int snakeCols = 16, snakeRows = 16;

        // 2048 state
        private final int[][] grid = new int[4][4];
        private int mergeScore, mergeMoves;
        private boolean mergeOver, mergeWon;

        // Memory state
        private final int[] cards = new int[16];
        private final boolean[] revealed = new boolean[16];
        private final boolean[] matched = new boolean[16];
        private int memorySelected, firstCard = -1, secondCard = -1, memoryMoves, memoryPairs;
        private long hideCardsAt = 0;

        // Breaker state (positions are in the playfield's local dp coordinates)
        private final boolean[][] bricks = new boolean[4][6];
        private float ballX, ballY, ballVX, ballVY, paddleX;
        private int breakerScore, lives;
        private boolean breakerStarted, breakerOver;

        // Reaction state: 0 idle, 1 waiting, 2 go, 3 result, 4 false start
        private int reactionState, reactionMs;
        private long reactionGoAt, reactionShownAt;

        ArcadeView() {
            super(MainActivity.this);
            density = getResources().getDisplayMetrics().density;
            setFocusable(true);
            setFocusableInTouchMode(true);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            requestFocus();
            setBackgroundColor(bg);
            resetSnake();
            resetMerge();
            resetMemory();
            resetBreaker();
        }

        private int color(String value) { return Color.parseColor(value); }
        private void round(Canvas c, float l, float t, float r, float b, float rad, int col) {
            paint.setShader(null); paint.setColor(col); paint.setStyle(Paint.Style.FILL);
            c.drawRoundRect(new RectF(l, t, r, b), rad, rad, paint);
        }
        private void strokeRound(Canvas c, float l, float t, float r, float b, float rad, int col, float sw) {
            paint.setShader(null); paint.setColor(col); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(sw);
            c.drawRoundRect(new RectF(l, t, r, b), rad, rad, paint); paint.setStyle(Paint.Style.FILL);
        }
        private void text(Canvas c, String value, float x, float y, float size, int col, boolean bold, Paint.Align align) {
            paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(col);
            paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
            paint.setTextSize(size); paint.setTextAlign(align); c.drawText(value, x, y, paint);
        }
        private void gradient(Canvas c, float l, float t, float r, float b, float rad, int c1, int c2) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(l, t, r, b, c1, c2, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(l, t, r, b), rad, rad, paint);
            paint.setShader(null);
        }
        private void circle(Canvas c, float x, float y, float r, int col) {
            paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(col); c.drawCircle(x, y, r, paint);
        }
        private void line(Canvas c, float x1, float y1, float x2, float y2, int col, float sw) {
            paint.setShader(null); paint.setColor(col); paint.setStrokeWidth(sw); paint.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(x1, y1, x2, y2, paint);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.scale(density, density);
            w = getWidth() / density;
            h = getHeight() / density;
            canvas.drawColor(bg);
            drawBackdrop(canvas);
            if (screen == HOME) drawHome(canvas); else drawGame(canvas);
            canvas.restore();
            updateGame();
            boolean animating = (screen == SNAKE && snakeStarted && !snakeOver)
                    || (screen == BREAKER && breakerStarted && !breakerOver)
                    || (screen == MEMORY && secondCard != -1 && hideCardsAt > 0)
                    || (screen == REACTION && reactionState == 1);
            if (animating) postInvalidateDelayed(45);
        }

        private void drawBackdrop(Canvas c) {
            circle(c, w - 8, 30, 86, 0x102CE7CF);
            circle(c, 4, h - 16, 72, 0x101A8EFF);
            circle(c, w * 0.53f, h * 0.56f, 126, 0x080F7EFF);
        }

        private void drawHome(Canvas c) {
            boolean compact = h < 390;
            float left = 18, right = w - 18;
            float heroTop = compact ? 12 : 16;
            float heroBottom = compact ? 112 : 143;
            gradient(c, left, heroTop, right, heroBottom, 24, Color.rgb(32, 60, 87), Color.rgb(34, 38, 77));
            circle(c, w - 35, compact ? 31 : 35, compact ? 34 : 45, 0x203DE6C8);
            circle(c, w - 20, compact ? 83 : 107, compact ? 20 : 24, 0x20FFFFFF);
            round(c, 30, compact ? 22 : 29, 39, compact ? 31 : 38, 4, mint);
            text(c, "POCKET ARCADE", 47, compact ? 30 : 37, 10, white, true, Paint.Align.LEFT);
            round(c, w - 95, compact ? 18 : 25, w - 30, compact ? 37 : 44, 10, 0x303FE9C5);
            circle(c, w - 84, compact ? 27.5f : 34.5f, 3, mint);
            text(c, "OFFLINE", w - 75, compact ? 31 : 38, 8, mint, true, Paint.Align.LEFT);
            text(c, "המשחק הבא שלך", right - 12, compact ? 60 : 76, Math.min(22, w * 0.095f), white, true, Paint.Align.RIGHT);
            text(c, "מתחיל כאן", right - 12, compact ? 83 : 102, Math.min(22, w * 0.095f), white, true, Paint.Align.RIGHT);
            text(c, "5 משחקים · בלי אינטרנט · בלי סיבוכים", right - 12, compact ? 102 : 126, Math.min(10, w * 0.047f), 0xFFD5E0F5, false, Paint.Align.RIGHT);

            float sectionY = compact ? 132 : 165;
            text(c, "בחר משחק", 20, sectionY, 17, white, true, Paint.Align.LEFT);
            text(c, "החצים לבחירה  ·  OK להפעלה", w - 20, sectionY - 1, 9, muted, false, Paint.Align.RIGHT);

            float top = compact ? 145 : 180;
            float gap = 10;
            float cw = (w - 46) / 2f;
            float ch = compact ? Math.min(103, Math.max(74, h - top - 71)) : 119;
            float step = ch + gap;
            int visibleRows = Math.max(1, (int) ((h - 54 - top) / step));
            int selectedRow = selected / 2;
            scrollY = selectedRow >= visibleRows ? selectedRow * step : 0;
            c.save();
            c.clipRect(0, top, w, h - 54);
            c.translate(0, -scrollY);
            for (int i = 0; i < 5; i++) {
                int row = i / 2;
                int col = i % 2;
                float x = 18 + col * (cw + 10);
                float y = top + row * step;
                float cardW = i == 4 ? w - 36 : cw;
                if (i == 4) x = 18;
                drawGameCard(c, i, x, y, cardW, ch, selected == i);
            }
            c.restore();

            round(c, 18, h - 43, w - 18, h - 12, 12, 0xFF171F36);
            circle(c, 31, h - 27.5f, 4, mint);
            text(c, "משחקים מקומיים · ללא רשת", 42, h - 24, 9, muted, false, Paint.Align.LEFT);
            text(c, "v1.0", w - 28, h - 24, 9, 0xFF7F90B2, true, Paint.Align.RIGHT);
        }

        private void drawGameCard(Canvas c, int i, float x, float y, float cw, float ch, boolean active) {
            int a = accents[i];
            int base = new int[]{Color.rgb(21, 58, 57), Color.rgb(48, 38, 83), Color.rgb(73, 48, 34), Color.rgb(28, 48, 78), Color.rgb(76, 34, 59)}[i];
            gradient(c, x, y, x + cw, y + ch, 18, base, Color.rgb(20, 26, 46));
            if (active) strokeRound(c, x + 1, y + 1, x + cw - 1, y + ch - 1, 18, a, 2);
            else strokeRound(c, x + .5f, y + .5f, x + cw - .5f, y + ch - .5f, 18, 0x253F547A, 1);
            circle(c, x + cw - 21, y + 20, 23, 0x18FFFFFF);
            drawMiniIcon(c, i, x + cw - 34, y + 35, 22, a);
            text(c, tags[i], x + 14, y + 20, 8, a, true, Paint.Align.LEFT);
            text(c, titles[i], x + 14, y + 78, 15, white, true, Paint.Align.LEFT);
            text(c, subtitles[i], x + 14, y + 96, 9, muted, false, Paint.Align.LEFT);
            round(c, x + 14, y + 105, x + 53, y + 109, 2, a);
            if (active) circle(c, x + cw - 14, y + ch - 14, 3, a);
        }

        private void drawMiniIcon(Canvas c, int i, float x, float y, float s, int a) {
            if (i == 0) {
                round(c, x - 11, y - 8, x + 12, y + 8, 6, 0x334FFFFFFF);
                circle(c, x - 5, y, 4, a); circle(c, x + 3, y, 4, a); circle(c, x + 11, y, 4, a);
                circle(c, x + 13, y - 5, 2, white);
            } else if (i == 1) {
                round(c, x - 12, y - 12, x + 12, y + 12, 5, 0x445D47FF);
                round(c, x - 9, y - 9, x - 1, y - 1, 2, 0xFFDBD1FF);
                round(c, x + 2, y - 9, x + 10, y - 1, 2, 0xFFBDA8FF);
                round(c, x - 9, y + 2, x - 1, y + 10, 2, 0xFFAC97FF);
                round(c, x + 2, y + 2, x + 10, y + 10, 2, 0xFF8C75FF);
            } else if (i == 2) {
                for (int j = 0; j < 3; j++) {
                    round(c, x - 12 + j * 9, y - 8, x - 5 + j * 9, y + 9, 3, (j == 1 ? a : 0x44FFFFFF));
                }
                circle(c, x - 8, y - 2, 1.5f, white); circle(c, x + 1, y - 2, 1.5f, white);
            } else if (i == 3) {
                circle(c, x, y, 12, 0x333B9BFF); circle(c, x, y, 8, a); circle(c, x, y, 3, white);
                line(c, x - 17, y + 11, x - 10, y + 4, a, 2);
            } else {
                circle(c, x, y, 13, 0x33FF6F9F);
                circle(c, x, y, 8, a); circle(c, y < 0 ? x : x, y, 3, white);
            }
        }

        private void drawGame(Canvas c) {
            int i = screen - 1;
            if (i < 0 || i > 4) i = 0;
            drawGameHeader(c, titles[i], tags[i], accents[i]);
            if (screen == SNAKE) drawSnake(c);
            else if (screen == MERGE) drawMerge(c);
            else if (screen == MEMORY) drawMemory(c);
            else if (screen == BREAKER) drawBreaker(c);
            else if (screen == REACTION) drawReaction(c);
            drawFooter(c);
        }

        private void drawGameHeader(Canvas c, String title, String tag, int accent) {
            round(c, 18, 18, 66, 56, 13, panel2);
            text(c, "‹", 42, 45, 29, white, false, Paint.Align.CENTER);
            text(c, title, 80, 37, 18, white, true, Paint.Align.LEFT);
            text(c, tag, 80, 53, 8, accent, true, Paint.Align.LEFT);
            round(c, w - 78, 25, w - 18, 49, 9, 0xFF17203A);
            circle(c, w - 66, 37, 3, accent);
            text(c, "משחק", w - 28, 40, 8, muted, true, Paint.Align.RIGHT);
        }

        private void drawFooter(Canvas c) {
            round(c, 18, h - 47, 98, h - 12, 12, panel2);
            text(c, "‹  חזרה", 58, h - 26, 11, white, true, Paint.Align.CENTER);
            round(c, w - 142, h - 47, w - 18, h - 12, 12, 0xFF2C4260);
            text(c, "↻  משחק חדש", w - 80, h - 26, 10, mint, true, Paint.Align.CENTER);
            text(c, "מקשים: חצים · OK", w / 2, h - 26, 8, muted, false, Paint.Align.CENTER);
        }

        // SNAKE --------------------------------------------------------------
        private void resetSnake() {
            snakeLength = 4; snakeDir = 1; snakeScore = 0; snakeStarted = false; snakeOver = false;
            int cx = snakeCols / 2, cy = snakeRows / 2;
            for (int i = 0; i < snakeLength; i++) { snakeX[i] = cx - i; snakeY[i] = cy; }
            placeFood(); lastTick = System.currentTimeMillis();
        }
        private void placeFood() {
            boolean occupied;
            int guard = 0;
            do {
                foodX = random.nextInt(snakeCols); foodY = random.nextInt(snakeRows); occupied = false;
                for (int i = 0; i < snakeLength; i++) if (snakeX[i] == foodX && snakeY[i] == foodY) occupied = true;
                guard++;
            } while (occupied && guard < 500);
        }
        private void drawSnake(Canvas c) {
            text(c, "ניקוד", 22, 82, 10, muted, true, Paint.Align.LEFT);
            text(c, String.valueOf(snakeScore), 22, 106, 19, mint, true, Paint.Align.LEFT);
            text(c, snakeOver ? "המשחק נגמר" : snakeStarted ? "המשך לאסוף" : "לחץ OK להתחלה", w - 22, 96, 10, snakeOver ? orange : muted, true, Paint.Align.RIGHT);
            float size = Math.min((w - 38) / snakeCols, (h - 180) / snakeRows);
            size = Math.max(6, size);
            float bx = (w - size * snakeCols) / 2;
            float by = 119;
            round(c, bx - 6, by - 6, bx + size * snakeCols + 6, by + size * snakeRows + 6, 16, Color.rgb(16, 23, 41));
            for (int yy = 0; yy < snakeRows; yy++) for (int xx = 0; xx < snakeCols; xx++) {
                round(c, bx + xx * size + 1, by + yy * size + 1, bx + (xx + 1) * size - 1, by + (yy + 1) * size - 1,
                        Math.max(1, size * .22f), 0xFF222D48);
            }
            circle(c, bx + (foodX + .5f) * size, by + (foodY + .5f) * size, size * .34f, orange);
            for (int i = snakeLength - 1; i >= 0; i--) {
                float x = bx + snakeX[i] * size, y = by + snakeY[i] * size;
                round(c, x + 1, y + 1, x + size - 1, y + size - 1, size * .28f, i == 0 ? white : mint);
                if (i == 0) {
                    circle(c, x + size * .35f, y + size * .38f, Math.max(1, size * .07f), Color.rgb(26, 48, 56));
                    circle(c, x + size * .68f, y + size * .38f, Math.max(1, size * .07f), Color.rgb(26, 48, 56));
                }
            }
            if (!snakeStarted || snakeOver) {
                round(c, w / 2 - 85, by + size * snakeRows / 2 - 21, w / 2 + 85, by + size * snakeRows / 2 + 21, 12, 0xDD11192B);
                text(c, snakeOver ? "OK כדי לנסות שוב" : "OK כדי להתחיל", w / 2, by + size * snakeRows / 2 + 5, 11, white, true, Paint.Align.CENTER);
            }
        }

        private void stepSnake() {
            if (!snakeStarted || snakeOver) return;
            int dx = snakeDir == 0 ? 0 : snakeDir == 1 ? 1 : snakeDir == 2 ? 0 : -1;
            int dy = snakeDir == 0 ? -1 : snakeDir == 1 ? 0 : snakeDir == 2 ? 1 : 0;
            int nx = snakeX[0] + dx, ny = snakeY[0] + dy;
            if (nx < 0 || ny < 0 || nx >= snakeCols || ny >= snakeRows) { snakeOver = true; return; }
            boolean eating = nx == foodX && ny == foodY;
            int limit = eating ? snakeLength : snakeLength - 1;
            for (int i = 0; i < limit; i++) if (snakeX[i] == nx && snakeY[i] == ny) { snakeOver = true; return; }
            if (eating && snakeLength < snakeX.length) snakeLength++;
            for (int i = snakeLength - 1; i > 0; i--) { snakeX[i] = snakeX[i - 1]; snakeY[i] = snakeY[i - 1]; }
            snakeX[0] = nx; snakeY[0] = ny;
            if (eating) { snakeScore += 10; placeFood(); }
        }

        // 2048 ----------------------------------------------------------------
        private void resetMerge() {
            for (int y = 0; y < 4; y++) Arrays.fill(grid[y], 0);
            mergeScore = 0; mergeMoves = 0; mergeOver = false; mergeWon = false;
            addTile(); addTile();
        }
        private void addTile() {
            int empty = 0;
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) if (grid[y][x] == 0) empty++;
            if (empty == 0) return;
            int at = random.nextInt(empty);
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) if (grid[y][x] == 0) {
                if (at-- == 0) { grid[y][x] = random.nextInt(10) == 0 ? 4 : 2; return; }
            }
        }
        private int tileColor(int n) {
            switch (n) {
                case 2: return 0xFF34455E; case 4: return 0xFF41657D; case 8: return 0xFF277F82;
                case 16: return 0xFF238D8F; case 32: return 0xFF8E6AC8; case 64: return 0xFFB85B90;
                case 128: return 0xFFE5AA5E; case 256: return 0xFFF1BA62; case 512: return 0xFFF39A54;
                case 1024: return 0xFFEA805D; default: return n >= 2048 ? 0xFF69DDB7 : 0xFF5961A7;
            }
        }
        private void drawMerge(Canvas c) {
            text(c, "ניקוד  " + mergeScore, 22, 83, 12, mint, true, Paint.Align.LEFT);
            text(c, mergeOver ? "אין יותר מהלכים" : mergeWon ? "הגעת ל־2048!" : "החלק כדי לחבר", w - 22, 83, 9, muted, true, Paint.Align.RIGHT);
            float size = Math.min((w - 38) / 4f, (h - 170) / 4f);
            float bx = (w - (size * 4 + 8)) / 2f, by = 100;
            round(c, bx - 5, by - 5, bx + size * 4 + 5, by + size * 4 + 5, 17, Color.rgb(25, 32, 53));
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
                float l = bx + x * size, t = by + y * size;
                int n = grid[y][x];
                round(c, l + 3, t + 3, l + size - 3, t + size - 3, 9, n == 0 ? 0xFF19243A : tileColor(n));
                if (n > 0) text(c, String.valueOf(n), l + size / 2, t + size / 2 + Math.min(6, size * .19f),
                        n >= 1024 ? size * .26f : n >= 128 ? size * .32f : size * .39f, white, true, Paint.Align.CENTER);
            }
            text(c, "החצים מזיזים את הלוח", w / 2, by + size * 4 + 27, 10, muted, false, Paint.Align.CENTER);
            if (mergeOver || mergeWon) {
                round(c, 28, by + size * 1.5f, w - 28, by + size * 2.5f, 15, 0xE91A2238);
                text(c, mergeWon ? "מעולה! 2048" : "אין יותר מהלכים", w / 2, by + size * 2 + 4, 15, white, true, Paint.Align.CENTER);
            }
        }
        private boolean moveMerge(int dx, int dy) {
            if (mergeOver) return false;
            boolean changed = false;
            for (int line = 0; line < 4; line++) {
                int[] vals = new int[4];
                for (int i = 0; i < 4; i++) {
                    int x = dx != 0 ? (dx < 0 ? i : 3 - i) : line;
                    int y = dy != 0 ? (dy < 0 ? i : 3 - i) : line;
                    vals[i] = grid[y][x];
                }
                int[] compact = new int[4]; int count = 0;
                for (int i = 0; i < 4; i++) if (vals[i] != 0) compact[count++] = vals[i];
                for (int i = 0; i < count - 1; i++) if (compact[i] == compact[i + 1]) {
                    compact[i] *= 2; mergeScore += compact[i]; compact[i + 1] = 0; i++;
                }
                int[] merged = new int[4]; int at = 0;
                for (int i = 0; i < 4; i++) if (compact[i] != 0) merged[at++] = compact[i];
                for (int i = 0; i < 4; i++) {
                    int x = dx != 0 ? (dx < 0 ? i : 3 - i) : line;
                    int y = dy != 0 ? (dy < 0 ? i : 3 - i) : line;
                    if (grid[y][x] != merged[i]) changed = true;
                    grid[y][x] = merged[i];
                    if (merged[i] >= 2048) mergeWon = true;
                }
            }
            if (changed) { mergeMoves++; addTile(); }
            mergeOver = !hasMergeMoves();
            return changed;
        }
        private boolean hasMergeMoves() {
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
                if (grid[y][x] == 0) return true;
                if (x < 3 && grid[y][x] == grid[y][x + 1]) return true;
                if (y < 3 && grid[y][x] == grid[y + 1][x]) return true;
            }
            return false;
        }

        // MEMORY --------------------------------------------------------------
        private void resetMemory() {
            for (int i = 0; i < 16; i++) { cards[i] = i % 8; revealed[i] = false; matched[i] = false; }
            for (int i = 15; i > 0; i--) { int j = random.nextInt(i + 1); int t = cards[i]; cards[i] = cards[j]; cards[j] = t; }
            memorySelected = 0; firstCard = -1; secondCard = -1; memoryMoves = 0; memoryPairs = 0; hideCardsAt = 0;
        }
        private void drawMemory(Canvas c) {
            text(c, "זוגות  " + memoryPairs + "/8", 22, 83, 12, orange, true, Paint.Align.LEFT);
            text(c, "מהלכים  " + memoryMoves, w - 22, 83, 10, muted, true, Paint.Align.RIGHT);
            float gap = 8;
            float size = Math.min((w - 52) / 4f, (h - 185) / 4f);
            size = Math.max(30, size);
            float total = size * 4 + gap * 3;
            float bx = (w - total) / 2, by = 101;
            int[] iconColors = {mint, purple, orange, blue, 0xFFFF719A, 0xFF79A8FF, 0xFFFFD166, 0xFFC3A6FF};
            for (int i = 0; i < 16; i++) {
                int row = i / 4, col = i % 4;
                float x = bx + col * (size + gap), y = by + row * (size + gap);
                boolean open = revealed[i] || matched[i];
                boolean active = i == memorySelected;
                if (open) {
                    round(c, x, y, x + size, y + size, 10, Color.rgb(35, 45, 68));
                    circle(c, x + size / 2, y + size / 2, size * .24f, iconColors[cards[i]]);
                    drawMemoryGlyph(c, cards[i], x + size / 2, y + size / 2, size * .2f, white);
                } else {
                    gradient(c, x, y, x + size, y + size, 10, 0xFF293653, 0xFF1B2741);
                    text(c, "✦", x + size / 2, y + size / 2 + size * .13f, size * .43f, 0xFF7084AA, true, Paint.Align.CENTER);
                }
                strokeRound(c, x, y, x + size, y + size, 10, active ? orange : (matched[i] ? 0xFF4C8F7C : 0x302A3958), active ? 2 : 1);
            }
            text(c, memoryPairs == 8 ? "ניצחת! כל הזוגות נמצאו" : "בחר קלף בעזרת החצים ולחץ OK", w / 2, by + total + 20, 9, muted, false, Paint.Align.CENTER);
        }
        private void drawMemoryGlyph(Canvas c, int n, float x, float y, float s, int col) {
            if (n == 0) {
                circle(c, x, y, s, col); circle(c, x, y, s * .48f, 0xFF20314A);
            } else if (n == 1) {
                round(c, x - s, y - s, x + s, y + s, s * .25f, col);
            } else if (n == 2) {
                Path p = new Path(); p.moveTo(x, y - s); p.lineTo(x + s, y); p.lineTo(x, y + s); p.lineTo(x - s, y); p.close();
                paint.setColor(col); paint.setStyle(Paint.Style.FILL); c.drawPath(p, paint);
            } else if (n == 3) {
                circle(c, x, y, s, col); circle(c, x, y, s * .35f, 0xFF20314A);
            } else if (n == 4) {
                line(c, x - s, y, x + s, y, col, 3); line(c, x, y - s, x, y + s, col, 3);
            } else if (n == 5) {
                circle(c, x - s * .35f, y, s * .48f, col); circle(c, x + s * .35f, y, s * .48f, col);
            } else if (n == 6) {
                round(c, x - s, y - s * .25f, x + s, y + s * .25f, s * .2f, col);
            } else {
                Path p = new Path(); p.moveTo(x, y - s); p.lineTo(x + s * .8f, y + s * .8f); p.lineTo(x - s * .8f, y + s * .8f); p.close();
                paint.setColor(col); c.drawPath(p, paint);
            }
        }
        private void flipMemory(int at) {
            if (at < 0 || at >= 16 || matched[at] || revealed[at]) return;
            long now = System.currentTimeMillis();
            if (secondCard != -1 && hideCardsAt > 0 && now < hideCardsAt) return;
            if (secondCard != -1) { revealed[firstCard] = false; revealed[secondCard] = false; firstCard = -1; secondCard = -1; hideCardsAt = 0; }
            revealed[at] = true;
            if (firstCard == -1) firstCard = at;
            else {
                secondCard = at; memoryMoves++;
                if (cards[firstCard] == cards[secondCard]) {
                    matched[firstCard] = true; matched[secondCard] = true; memoryPairs++;
                    firstCard = -1; secondCard = -1;
                } else hideCardsAt = now + 850;
            }
        }

        // BRICK BREAKER ------------------------------------------------------
        private float breakerBoardW() { return w - 36; }
        private float breakerBoardH() { return Math.max(155, h - 155); }
        private void resetBreaker() {
            for (int y = 0; y < 4; y++) Arrays.fill(bricks[y], true);
            breakerScore = 0; lives = 3; breakerStarted = false; breakerOver = false;
            paddleX = (breakerBoardW() - 58) / 2f;
            ballX = breakerBoardW() / 2f; ballY = breakerBoardH() - 44;
            ballVX = 2.0f; ballVY = -2.4f; lastTick = System.currentTimeMillis();
        }
        private void drawBreaker(Canvas c) {
            text(c, "ניקוד  " + breakerScore, 22, 81, 11, blue, true, Paint.Align.LEFT);
            text(c, "חיים  " + lives, w - 22, 81, 10, muted, true, Paint.Align.RIGHT);
            float bx = 18, by = 94, bw = breakerBoardW(), bh = breakerBoardH();
            round(c, bx, by, bx + bw, by + bh, 17, Color.rgb(17, 24, 42));
            round(c, bx, by, bx + bw, by + bh, 17, 0x252D6D9E);
            float brickGap = 5, bwBrick = (bw - 28 - brickGap * 5) / 6f;
            int[] bc = {0xFF69E1C5, 0xFF70BFFF, 0xFF9C8BFF, 0xFFFFC56E};
            for (int r = 0; r < 4; r++) for (int col = 0; col < 6; col++) if (bricks[r][col]) {
                float x = bx + 14 + col * (bwBrick + brickGap), y = by + 18 + r * 19;
                gradient(c, x, y, x + bwBrick, y + 13, 4, bc[r], (bc[r] & 0x00FFFFFF) | 0xFF000000);
            }
            float py = by + bh - 23;
            round(c, bx + paddleX, py, bx + paddleX + 58, py + 8, 4, mint);
            circle(c, bx + ballX, by + ballY, 5.5f, white);
            if (!breakerStarted || breakerOver) {
                round(c, bx + 16, by + bh * .48f, bx + bw - 16, by + bh * .48f + 46, 12, 0xE9192237);
                text(c, breakerOver ? (lives == 0 ? "נגמרו החיים" : "ניקית את הלוח!") : "OK כדי לשגר את הכדור",
                        w / 2, by + bh * .48f + 19, 10, white, true, Paint.Align.CENTER);
                text(c, breakerOver ? "משחק חדש כדי לנסות שוב" : "החצים מזיזים את המשטח",
                        w / 2, by + bh * .48f + 34, 8, muted, false, Paint.Align.CENTER);
            }
        }
        private void stepBreaker() {
            if (!breakerStarted || breakerOver) return;
            float bw = breakerBoardW(), bh = breakerBoardH();
            ballX += ballVX; ballY += ballVY;
            if (ballX < 6) { ballX = 6; ballVX = Math.abs(ballVX); }
            if (ballX > bw - 6) { ballX = bw - 6; ballVX = -Math.abs(ballVX); }
            if (ballY < 7) { ballY = 7; ballVY = Math.abs(ballVY); }
            float py = bh - 23;
            if (ballVY > 0 && ballY + 5 >= py && ballY < py + 11 && ballX >= paddleX - 3 && ballX <= paddleX + 61) {
                ballY = py - 6; ballVY = -Math.abs(ballVY);
                ballVX = ((ballX - (paddleX + 29)) / 29f) * 3.0f;
                if (Math.abs(ballVX) < .8f) ballVX = ballVX < 0 ? -.8f : .8f;
            }
            if (ballY > bh + 8) {
                lives--;
                if (lives <= 0) { breakerOver = true; return; }
                ballX = bw / 2; ballY = bh - 44; ballVX = 2.0f; ballVY = -2.4f; breakerStarted = false; return;
            }
            float brickGap = 5, brickW = (bw - 28 - brickGap * 5) / 6f;
            for (int r = 0; r < 4; r++) for (int col = 0; col < 6; col++) if (bricks[r][col]) {
                float x = 14 + col * (brickW + brickGap), y = 18 + r * 19;
                if (ballX >= x && ballX <= x + brickW && ballY + 5 >= y && ballY - 5 <= y + 13) {
                    bricks[r][col] = false; breakerScore += 10; ballVY = -ballVY; r = 4; break;
                }
            }
            boolean any = false;
            for (int r = 0; r < 4; r++) for (int col = 0; col < 6; col++) if (bricks[r][col]) any = true;
            if (!any) breakerOver = true;
        }

        // REACTION -----------------------------------------------------------
        private void resetReaction() { reactionState = 0; reactionMs = 0; reactionGoAt = 0; reactionShownAt = 0; }
        private void beginReaction() {
            reactionState = 1; reactionMs = 0; reactionShownAt = 0;
            reactionGoAt = System.currentTimeMillis() + 1700 + random.nextInt(2200);
        }
        private void pressReaction() {
            long now = System.currentTimeMillis();
            if (reactionState == 0 || reactionState == 3 || reactionState == 4) beginReaction();
            else if (reactionState == 1) { reactionState = 4; reactionMs = 0; }
            else if (reactionState == 2) { reactionMs = (int) Math.min(9999, now - reactionShownAt); reactionState = 3; }
        }
        private void drawReaction(Canvas c) {
            text(c, "בודקים את הרפלקסים שלך", w / 2, 88, 11, muted, false, Paint.Align.CENTER);
            float cx = w / 2, cy = h * .44f;
            int accent = reactionState == 2 ? mint : reactionState == 4 ? orange : purple;
            circle(c, cx, cy, Math.min(95, w * .37f), 0x1E9E8BFF);
            circle(c, cx, cy, Math.min(76, w * .30f), 0x2D9E8BFF);
            circle(c, cx, cy, Math.min(58, w * .23f), accent);
            circle(c, cx, cy, Math.min(39, w * .15f), Color.rgb(16, 25, 44));
            if (reactionState == 0) {
                text(c, "GO?", cx, cy + 8, 22, white, true, Paint.Align.CENTER);
                text(c, "לחץ OK כדי להתחיל", cx, cy + 111, 12, white, true, Paint.Align.CENTER);
            } else if (reactionState == 1) {
                text(c, "חכה...", cx, cy + 8, 17, white, true, Paint.Align.CENTER);
                text(c, "אל תלחץ עדיין", cx, cy + 111, 11, muted, false, Paint.Align.CENTER);
            } else if (reactionState == 2) {
                text(c, "עכשיו!", cx, cy + 8, 19, Color.rgb(11, 39, 34), true, Paint.Align.CENTER);
                text(c, "לחץ מהר!", cx, cy + 111, 12, mint, true, Paint.Align.CENTER);
            } else if (reactionState == 3) {
                text(c, reactionMs + "", cx, cy + 7, 25, white, true, Paint.Align.CENTER);
                text(c, "מילישניות", cx, cy + 25, 8, muted, true, Paint.Align.CENTER);
                text(c, reactionMs < 230 ? "ברקים!" : reactionMs < 340 ? "תגובה מעולה!" : "נסה לשפר את השיא", cx, cy + 111, 11, accent, true, Paint.Align.CENTER);
            } else {
                text(c, "מוקדם!", cx, cy + 8, 19, white, true, Paint.Align.CENTER);
                text(c, "חכה עד שהעיגול יאיר", cx, cy + 111, 10, orange, true, Paint.Align.CENTER);
            }
        }

        private void updateGame() {
            long now = System.currentTimeMillis();
            if (screen == SNAKE && snakeStarted && !snakeOver && now - lastTick >= 145) {
                stepSnake(); lastTick = now; invalidate();
            } else if (screen == BREAKER && breakerStarted && !breakerOver && now - lastTick >= 16) {
                stepBreaker(); lastTick = now; invalidate();
            } else if (screen == MEMORY && secondCard != -1 && hideCardsAt > 0 && now >= hideCardsAt) {
                revealed[firstCard] = false; revealed[secondCard] = false; firstCard = -1; secondCard = -1; hideCardsAt = 0; invalidate();
            } else if (screen == REACTION && reactionState == 1 && now >= reactionGoAt) {
                reactionState = 2; reactionShownAt = now; invalidate();
            }
        }

        private void openGame(int id) {
            screen = id + 1;
            if (screen == SNAKE) resetSnake();
            else if (screen == MERGE) resetMerge();
            else if (screen == MEMORY) resetMemory();
            else if (screen == BREAKER) resetBreaker();
            else if (screen == REACTION) resetReaction();
            requestFocus(); invalidate();
        }
        private void restartCurrent() {
            if (screen == SNAKE) resetSnake();
            else if (screen == MERGE) resetMerge();
            else if (screen == MEMORY) resetMemory();
            else if (screen == BREAKER) resetBreaker();
            else if (screen == REACTION) resetReaction();
            invalidate();
        }
        void goHome() { screen = HOME; selected = Math.min(selected, 4); requestFocus(); invalidate(); }

        @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
            boolean left = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_4;
            boolean right = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_6;
            boolean up = keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_2;
            boolean down = keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_8;
            boolean ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_5 || keyCode == KeyEvent.KEYCODE_SPACE;
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (screen != HOME) { goHome(); return true; }
            }
            if (screen == HOME) {
                if (left && selected % 2 == 1) selected--;
                else if (right && selected % 2 == 0 && selected < 4) selected++;
                else if (up) selected = Math.max(0, selected - 2);
                else if (down) selected = selected < 2 ? selected + 2 : selected < 4 ? 4 : 4;
                else if (ok) { openGame(selected); return true; }
                invalidate(); return left || right || up || down || ok;
            }
            if (screen == SNAKE) {
                if (ok) {
                    if (snakeOver) resetSnake();
                    else { snakeStarted = true; lastTick = System.currentTimeMillis(); }
                } else if (left && snakeDir != 1) { snakeDir = 3; snakeStarted = true; }
                else if (right && snakeDir != 3) { snakeDir = 1; snakeStarted = true; }
                else if (up && snakeDir != 2) { snakeDir = 0; snakeStarted = true; }
                else if (down && snakeDir != 0) { snakeDir = 2; snakeStarted = true; }
            } else if (screen == MERGE) {
                if (ok && (mergeOver || mergeWon)) resetMerge();
                else if (left) moveMerge(-1, 0);
                else if (right) moveMerge(1, 0);
                else if (up) moveMerge(0, -1);
                else if (down) moveMerge(0, 1);
            } else if (screen == MEMORY) {
                if (left) memorySelected = memorySelected % 4 == 0 ? memorySelected : memorySelected - 1;
                else if (right) memorySelected = memorySelected % 4 == 3 ? memorySelected : memorySelected + 1;
                else if (up) memorySelected = Math.max(0, memorySelected - 4);
                else if (down) memorySelected = Math.min(15, memorySelected + 4);
                else if (ok) flipMemory(memorySelected);
            } else if (screen == BREAKER) {
                if (ok) {
                    if (breakerOver) resetBreaker();
                    else { breakerStarted = true; lastTick = System.currentTimeMillis(); }
                } else if (left) paddleX = Math.max(3, paddleX - 14);
                else if (right) paddleX = Math.min(breakerBoardW() - 61, paddleX + 14);
            } else if (screen == REACTION) {
                if (ok) pressReaction();
            }
            invalidate();
            return true;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX() / density, y = e.getY() / density;
            if (e.getAction() == MotionEvent.ACTION_DOWN) { downX = x; downY = y; return true; }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float dx = x - downX, dy = y - downY;
                if (screen == HOME) {
                    if (Math.abs(dy) > 28) {
                        if (selected == 4 && dy > 0) selected = 2;
                        else if (dy < 0) selected = 4;
                        invalidate(); return true;
                    }
                    boolean compact = h < 390;
                    float top = compact ? 145 : 180;
                    float cw = (w - 46) / 2f;
                    float ch = compact ? Math.min(103, Math.max(74, h - top - 71)) : 119;
                    float step = ch + 10;
                    int hit = -1;
                    for (int i = 0; i < 5; i++) {
                        int row = i / 2, col = i % 2;
                        float cx = 18 + col * (cw + 10), cy = top + row * step - scrollY;
                        float cardW = i == 4 ? w - 36 : cw;
                        if (i == 4) cx = 18;
                        if (x >= cx && x <= cx + cardW && y >= cy && y <= cy + ch) hit = i;
                    }
                    if (hit >= 0) { selected = hit; openGame(hit); }
                    else if (y < 62 && x < 75) { selected = 0; }
                    invalidate(); return true;
                }
                if (y >= h - 52 && x < 104) { goHome(); return true; }
                if (y >= h - 52 && x > w - 150) { restartCurrent(); return true; }
                if (screen == SNAKE) {
                    if (snakeOver) resetSnake();
                    else if (Math.abs(dx) > Math.abs(dy)) {
                        if (dx < 0 && snakeDir != 1) snakeDir = 3;
                        else if (dx > 0 && snakeDir != 3) snakeDir = 1;
                        snakeStarted = true;
                    } else if (Math.abs(dy) > 8) {
                        if (dy < 0 && snakeDir != 2) snakeDir = 0;
                        else if (dy > 0 && snakeDir != 0) snakeDir = 2;
                        snakeStarted = true;
                    } else { snakeStarted = true; }
                } else if (screen == MERGE) {
                    if (Math.abs(dx) > 20 || Math.abs(dy) > 20) {
                        if (Math.abs(dx) > Math.abs(dy)) moveMerge(dx < 0 ? -1 : 1, 0);
                        else moveMerge(0, dy < 0 ? -1 : 1);
                    } else if (mergeOver || mergeWon) resetMerge();
                } else if (screen == MEMORY) {
                    float size = Math.min((w - 52) / 4f, (h - 185) / 4f);
                    size = Math.max(30, size);
                    float gap = 8, total = size * 4 + gap * 3, bx = (w - total) / 2, by = 101;
                    int col = (int) ((x - bx) / (size + gap)), row = (int) ((y - by) / (size + gap));
                    if (col >= 0 && col < 4 && row >= 0 && row < 4) {
                        float lx = bx + col * (size + gap), ly = by + row * (size + gap);
                        if (x <= lx + size && y <= ly + size) { memorySelected = row * 4 + col; flipMemory(memorySelected); }
                    }
                } else if (screen == BREAKER) {
                    if (breakerOver) resetBreaker();
                    else {
                        if (x < w / 2) paddleX = Math.max(3, paddleX - 22);
                        else paddleX = Math.min(breakerBoardW() - 61, paddleX + 22);
                        if (y < h * .58f) breakerStarted = true;
                        lastTick = System.currentTimeMillis();
                    }
                } else if (screen == REACTION) pressReaction();
                invalidate(); return true;
            }
            return true;
        }
    }
}
