package com.example.myapp;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {

    private GameView game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(12, 17, 22));
        getWindow().setNavigationBarColor(Color.rgb(12, 17, 22));

        game = new GameView(this);
        setContentView(game);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (game != null) game.saveGame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (game != null) game.resumeGame();
    }

    @Override
    protected void onDestroy() {
        if (game != null) game.saveGame();
        super.onDestroy();
    }

    private static class Zombie {
        float x, y;
        float radius = 19;
        float speed;
        float hp;
        long lastAttack;

        Zombie(float x, float y, float speed, float hp) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.hp = hp;
        }
    }

    private static class Bullet {
        float x, y;
        float vx, vy;
        float damage;
        float radius = 6;

        Bullet(float x, float y, float vx, float vy,
               float damage) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.damage = damage;
        }
    }

    private class GameView extends View {

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();

        private final ArrayList<Zombie> zombies =
                new ArrayList<>();

        private final ArrayList<Bullet> bullets =
                new ArrayList<>();

        private android.content.SharedPreferences prefs;

        private float playerX;
        private float playerY;
        private float playerRadius = 22;

        private float screenW;
        private float screenH;

        private float health = 100;
        private float maxHealth = 100;

        private int coins = 0;
        private int kills = 0;
        private int wave = 1;

        private int damageLevel = 1;
        private int healthLevel = 1;
        private int fireLevel = 1;

        private float bulletDamage = 35;
        private float fireDelay = 330;

        private boolean left;
        private boolean right;
        private boolean up;
        private boolean down;
        private boolean firing;

        private boolean shopOpen = false;
        private boolean gameOver = false;

        private long lastFrame;
        private long lastSpawn;
        private long lastShot;

        GameView(Context context) {
            super(context);

            setFocusable(true);

            prefs = context.getSharedPreferences(
                    "ZombieSurvivalSave",
                    Context.MODE_PRIVATE
            );

            coins = prefs.getInt("coins", 0);
            kills = prefs.getInt("kills", 0);

            damageLevel = prefs.getInt("damageLevel", 1);
            healthLevel = prefs.getInt("healthLevel", 1);
            fireLevel = prefs.getInt("fireLevel", 1);

            maxHealth = 100 + (healthLevel - 1) * 25;
            health = maxHealth;

            bulletDamage = 35 + (damageLevel - 1) * 15;
            fireDelay = Math.max(120, 330 - (fireLevel - 1) * 45);

            wave = Math.max(1, kills / 10 + 1);

            lastFrame = System.currentTimeMillis();
        }

        void saveGame() {
            if (prefs == null) return;

            prefs.edit()
                    .putInt("coins", coins)
                    .putInt("kills", kills)
                    .putInt("damageLevel", damageLevel)
                    .putInt("healthLevel", healthLevel)
                    .putInt("fireLevel", fireLevel)
                    .apply();
        }

        void resumeGame() {
            lastFrame = System.currentTimeMillis();
            invalidate();
        }

        private void resetRound() {
            zombies.clear();
            bullets.clear();

            playerX = screenW / 2f;
            playerY = screenH / 2f;

            health = maxHealth;
            wave = Math.max(1, kills / 10 + 1);

            gameOver = false;
            shopOpen = false;

            left = right = up = down = firing = false;

            lastSpawn = System.currentTimeMillis();
            lastFrame = lastSpawn;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            screenW = getWidth();
            screenH = getHeight();

            if (screenW <= 0 || screenH <= 0) return;

            if (playerX == 0 && playerY == 0) {
                playerX = screenW / 2f;
                playerY = screenH / 2f;
                lastSpawn = System.currentTimeMillis();
            }

            drawBackground(canvas);

            if (!gameOver && !shopOpen) {
                updateGame();
            }

            drawBullets(canvas);
            drawZombies(canvas);
            drawPlayer(canvas);
            drawHUD(canvas);
            drawControls(canvas);

            if (shopOpen) {
                drawShop(canvas);
            }

            if (gameOver) {
                drawGameOver(canvas);
            }

            postInvalidateDelayed(16);
        }

        private void drawBackground(Canvas c) {
            c.drawColor(Color.rgb(20, 29, 29));

            p.setColor(Color.rgb(29, 42, 40));
            p.setStrokeWidth(1);

            float gap = 45;

            for (float x = 0; x < screenW; x += gap) {
                c.drawLine(x, 0, x, screenH, p);
            }

            for (float y = 0; y < screenH; y += gap) {
                c.drawLine(0, y, screenW, y, p);
            }

            // بقع صغيرة على الأرض
            p.setColor(Color.rgb(38, 51, 43));

            for (int i = 0; i < 18; i++) {
                float x = (i * 83 + 31) % screenW;
                float y = (i * 127 + 55) % screenH;
                c.drawCircle(x, y, 3, p);
            }
        }

        private void updateGame() {
            long now = System.currentTimeMillis();

            float dt = (now - lastFrame) / 1000f;
            lastFrame = now;

            dt = Math.min(dt, 0.04f);

            float speed = 230f;

            if (left) playerX -= speed * dt;
            if (right) playerX += speed * dt;
            if (up) playerY -= speed * dt;
            if (down) playerY += speed * dt;

            playerX = clamp(playerX, playerRadius,
                    screenW - playerRadius);

            playerY = clamp(playerY, 65,
                    screenH - 155);

            if (firing && now - lastShot >= fireDelay) {
                shoot();
                lastShot = now;
            }

            long spawnDelay = Math.max(400, 1500 - wave * 65);

            if (now - lastSpawn > spawnDelay) {
                spawnZombie();
                lastSpawn = now;

                if (kills > 0 && kills % 10 == 0) {
                    wave = kills / 10 + 1;
                }
            }

            updateBullets(dt);
            updateZombies(dt, now);

            if (health <= 0) {
                health = 0;
                gameOver = true;
                saveGame();
            }
        }

        private void spawnZombie() {
            float x, y;

            int edge = random.nextInt(4);

            if (edge == 0) {
                x = random.nextFloat() * screenW;
                y = 70;
            } else if (edge == 1) {
                x = random.nextFloat() * screenW;
                y = screenH - 160;
            } else if (edge == 2) {
                x = 5;
                y = 70 + random.nextFloat()
                        * Math.max(1, screenH - 230);
            } else {
                x = screenW - 5;
                y = 70 + random.nextFloat()
                        * Math.max(1, screenH - 230);
            }

            float speed = 40 + wave * 4
                    + random.nextFloat() * 18;

            float hp = 35 + wave * 8;

            zombies.add(new Zombie(x, y, speed, hp));
        }

        private void shoot() {
            if (zombies.isEmpty()) return;

            Zombie target = null;
            float bestDistance = Float.MAX_VALUE;

            for (Zombie z : zombies) {
                float dx = z.x - playerX;
                float dy = z.y - playerY;
                float distance = dx * dx + dy * dy;

                if (distance < bestDistance) {
                    bestDistance = distance;
                    target = z;
                }
            }

            if (target == null) return;

            float dx = target.x - playerX;
            float dy = target.y - playerY;

            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (distance < 1) return;

            float bulletSpeed = 560f;

            bullets.add(new Bullet(
                    playerX,
                    playerY,
                    dx / distance * bulletSpeed,
                    dy / distance * bulletSpeed,
                    bulletDamage
            ));
        }

        private void updateBullets(float dt) {
            Iterator<Bullet> iterator = bullets.iterator();

            while (iterator.hasNext()) {
                Bullet b = iterator.next();

                b.x += b.vx * dt;
                b.y += b.vy * dt;

                if (b.x < 0 || b.x > screenW
                        || b.y < 0 || b.y > screenH) {
                    iterator.remove();
                    continue;
                }

                boolean hit = false;

                Iterator<Zombie> zi = zombies.iterator();

                while (zi.hasNext()) {
                    Zombie z = zi.next();

                    float dx = z.x - b.x;
                    float dy = z.y - b.y;

                    float distance = (float) Math.sqrt(
                            dx * dx + dy * dy
                    );

                    if (distance < z.radius + b.radius) {
                        z.hp -= b.damage;
                        hit = true;

                        if (z.hp <= 0) {
                            zi.remove();

                            coins += 5;
                            kills++;

                            if (kills % 10 == 0) {
                                wave++;
                            }

                            saveGame();
                        }

                        break;
                    }
                }

                if (hit) {
                    iterator.remove();
                }
            }
        }

        private void updateZombies(float dt, long now) {
            for (Zombie z : zombies) {
                float dx = playerX - z.x;
                float dy = playerY - z.y;

                float distance = (float) Math.sqrt(
                        dx * dx + dy * dy
                );

                if (distance > 0) {
                    z.x += dx / distance * z.speed * dt;
                    z.y += dy / distance * z.speed * dt;
                }

                if (distance < z.radius + playerRadius) {
                    if (now - z.lastAttack > 700) {
                        health -= 8;
                        z.lastAttack = now;
                    }
                }
            }
        }

        private void drawPlayer(Canvas c) {
            // ظل اللاعب
            p.setColor(Color.rgb(8, 12, 12));
            c.drawCircle(playerX + 3, playerY + 4,
                    playerRadius + 2, p);

            // جسم اللاعب
            p.setColor(Color.rgb(60, 155, 255));
            c.drawCircle(playerX, playerY, playerRadius, p);

            // خوذة
            p.setColor(Color.rgb(200, 225, 240));
            c.drawCircle(playerX, playerY - 5, 10, p);

            // اتجاه السلاح نحو أقرب زومبي
            Zombie target = nearestZombie();

            if (target != null) {
                float dx = target.x - playerX;
                float dy = target.y - playerY;

                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                if (distance > 0) {
                    p.setColor(Color.rgb(225, 225, 225));
                    p.setStrokeWidth(7);

                    c.drawLine(
                            playerX,
                            playerY,
                            playerX + dx / distance * 32,
                            playerY + dy / distance * 32,
                            p
                    );
                }
            }
        }

        private Zombie nearestZombie() {
            Zombie target = null;
            float best = Float.MAX_VALUE;

            for (Zombie z : zombies) {
                float dx = z.x - playerX;
                float dy = z.y - playerY;
                float d = dx * dx + dy * dy;

                if (d < best) {
                    best = d;
                    target = z;
                }
            }

            return target;
        }

        private void drawZombies(Canvas c) {
            for (Zombie z : zombies) {
                p.setColor(Color.rgb(8, 12, 12));
                c.drawCircle(z.x + 2, z.y + 3,
                        z.radius + 2, p);

                p.setColor(Color.rgb(89, 180, 75));
                c.drawCircle(z.x, z.y, z.radius, p);

                p.setColor(Color.RED);
                c.drawCircle(z.x - 6, z.y - 4, 3, p);
                c.drawCircle(z.x + 6, z.y - 4, 3, p);

                // شريط صحة الزومبي
                float barW = 34;
                float ratio = Math.max(0,
                        Math.min(1, z.hp / (35f + wave * 8)));

                p.setColor(Color.DKGRAY);
                c.drawRect(
                        z.x - barW / 2,
                        z.y - z.radius - 9,
                        z.x + barW / 2,
                        z.y - z.radius - 4,
                        p
                );

                p.setColor(Color.RED);
                c.drawRect(
                        z.x - barW / 2,
                        z.y - z.radius - 9,
                        z.x - barW / 2 + barW * ratio,
                        z.y - z.radius - 4,
                        p
                );
            }
        }

        private void drawBullets(Canvas c) {
            p.setColor(Color.rgb(255, 220, 65));

            for (Bullet b : bullets) {
                c.drawCircle(b.x, b.y, b.radius, p);
            }
        }

        private void drawHUD(Canvas c) {
            // خلفية شريط المعلومات
            p.setColor(Color.argb(220, 7, 12, 18));
            c.drawRoundRect(
                    new RectF(10, 10, screenW - 10, 94),
                    15, 15, p
            );

            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(17);
            p.setColor(Color.WHITE);

            c.drawText("🪙 " + coins, 22, 35, p);

            p.setTextSize(14);
            c.drawText("Kills: " + kills, 22, 59, p);
            c.drawText("Wave: " + wave, 22, 80, p);

            // زر المتجر
            p.setColor(Color.rgb(35, 130, 95));
            c.drawRoundRect(
                    new RectF(screenW - 110, 17,
                            screenW - 20, 57),
                    12, 12, p
            );

            p.setColor(Color.WHITE);
            p.setTextSize(15);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("SHOP", screenW - 65, 43, p);
            p.setTextAlign(Paint.Align.LEFT);

            // شريط الصحة
            float barLeft = screenW / 2f - 65;
            float barTop = 68;

            p.setColor(Color.DKGRAY);
            c.drawRoundRect(
                    new RectF(barLeft, barTop,
                            barLeft + 130, barTop + 12),
                    6, 6, p
            );

            float ratio = Math.max(0,
                    Math.min(1, health / maxHealth));

            p.setColor(ratio > 0.3f
                    ? Color.rgb(65, 220, 100)
                    : Color.RED);

            c.drawRoundRect(
                    new RectF(barLeft, barTop,
                            barLeft + 130 * ratio,
                            barTop + 12),
                    6, 6, p
            );
        }

        private void drawControls(Canvas c) {
            float baseY = screenH - 70;

            drawButton(c, 65, baseY, "◀");
            drawButton(c, 135, baseY, "▶");
            drawButton(c, 100, baseY - 55, "▲");
            drawButton(c, 100, baseY + 2, "▼");

            p.setColor(Color.rgb(175, 45, 45));
            c.drawCircle(screenW - 65, baseY, 42, p);

            p.setColor(Color.WHITE);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(16);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("FIRE", screenW - 65, baseY + 6, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        private void drawButton(Canvas c, float x, float y, String label) {
            p.setColor(Color.argb(200, 65, 80, 95));
            c.drawCircle(x, y, 29, p);

            p.setColor(Color.WHITE);
            p.setTextSize(24);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(label, x, y + 8, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        private void drawShop(Canvas c) {
            p.setColor(Color.argb(225, 0, 0, 0));
            c.drawRect(0, 0, screenW, screenH, p);

            float boxW = Math.min(screenW - 28, 360);
            float leftX = (screenW - boxW) / 2f;
            float topY = screenH / 2f - 155;

            p.setColor(Color.rgb(26, 38, 49));
            c.drawRoundRect(
                    new RectF(leftX, topY,
                            leftX + boxW, topY + 310),
                    20, 20, p
            );

            p.setColor(Color.WHITE);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(25);
            p.setTextAlign(Paint.Align.CENTER);

            c.drawText("ZOMBIE SHOP",
                    screenW / 2f, topY + 38, p);

            p.setTextSize(15);
            c.drawText("Coins: " + coins,
                    screenW / 2f, topY + 65, p);

            drawShopItem(c, topY + 82,
                    "Damage Upgrade",
                    "Level " + damageLevel + "  |  "
                            + damageCost() + " coins");

            drawShopItem(c, topY + 145,
                    "Health Upgrade",
                    "Level " + healthLevel + "  |  "
                            + healthCost() + " coins");

            drawShopItem(c, topY + 208,
                    "Fire Rate Upgrade",
                    "Level " + fireLevel + "  |  "
                            + fireCost() + " coins");

            p.setColor(Color.rgb(200, 65, 65));
            c.drawRoundRect(
                    new RectF(leftX + boxW / 2f - 60,
                            topY + 270,
                            leftX + boxW / 2f + 60,
                            topY + 300),
                    10, 10, p
            );

            p.setColor(Color.WHITE);
            p.setTextSize(15);
            c.drawText("CLOSE",
                    screenW / 2f, topY + 291, p);

            p.setTextAlign(Paint.Align.LEFT);
        }

        private void drawShopItem(Canvas c, float y,
                                  String title, String subtitle) {
            p.setColor(Color.rgb(44, 65, 78));

            c.drawRoundRect(
                    new RectF(25, y, screenW - 25, y + 54),
                    10, 10, p
            );

            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(16);
            p.setTypeface(Typeface.DEFAULT_BOLD);

            c.drawText(title, screenW / 2f, y + 22, p);

            p.setTextSize(12);
            p.setTypeface(Typeface.DEFAULT);
            c.drawText(subtitle, screenW / 2f, y + 42, p);

            p.setTextAlign(Paint.Align.LEFT);
        }

        private int damageCost() {
            return damageLevel * 25;
        }

        private int healthCost() {
            return healthLevel * 25;
        }

        private int fireCost() {
            return fireLevel * 30;
        }

        private void buyItem(int item) {
            if (item == 0 && coins >= damageCost()) {
                coins -= damageCost();
                damageLevel++;
                bulletDamage += 15;

            } else if (item == 1 && coins >= healthCost()) {
                coins -= healthCost();
                healthLevel++;

                maxHealth += 25;
                health = Math.min(maxHealth, health + 25);

            } else if (item == 2 && coins >= fireCost()) {
                coins -= fireCost();
                fireLevel++;

                fireDelay = Math.max(120, fireDelay - 45);
            }

            saveGame();
            invalidate();
        }

        private void drawGameOver(Canvas c) {
            p.setColor(Color.argb(220, 0, 0, 0));
            c.drawRect(0, 0, screenW, screenH, p);

            p.setColor(Color.RED);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(36);

            c.drawText("GAME OVER",
                    screenW / 2f, screenH / 2f - 45, p);

            p.setColor(Color.WHITE);
            p.setTextSize(18);

            c.drawText("Kills: " + kills,
                    screenW / 2f, screenH / 2f, p);

            p.setColor(Color.rgb(40, 155, 100));
            c.drawRoundRect(
                    new RectF(screenW / 2f - 95,
                            screenH / 2f + 25,
                            screenW / 2f + 95,
                            screenH / 2f + 80),
                    15, 15, p
            );

            p.setColor(Color.WHITE);
            p.setTextSize(18);

            c.drawText("PLAY AGAIN",
                    screenW / 2f, screenH / 2f + 59, p);

            p.setTextAlign(Paint.Align.LEFT);
        }

        private float clamp(float value, float min, float max) {
            if (max < min) return min;
            return Math.max(min, Math.min(max, value));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN) {

                if (gameOver) {
                    if (y > screenH / 2f + 15
                            && y < screenH / 2f + 100) {
                        resetRound();
                    }

                    return true;
                }

                if (shopOpen) {
                    float topY = screenH / 2f - 155;

                    if (y > topY + 270 && y < topY + 310) {
                        shopOpen = false;

                    } else if (y > topY + 82
                            && y < topY + 136) {
                        buyItem(0);

                    } else if (y > topY + 145
                            && y < topY + 199) {
                        buyItem(1);

                    } else if (y > topY + 208
                            && y < topY + 262) {
                        buyItem(2);
                    }

                    invalidate();
                    return true;
                }

                if (y < 65 && x > screenW - 125) {
                    shopOpen = true;
                    left = right = up = down = firing = false;
                    invalidate();
                    return true;
                }

                updateTouchControls(x, y);
                invalidate();
                return true;
            }

            if (action == MotionEvent.ACTION_MOVE) {
                if (!shopOpen && !gameOver) {
                    updateTouchControls(x, y);
                }

                invalidate();
                return true;
            }

            if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {

                left = right = up = down = firing = false;
                invalidate();
                return true;
            }

            return true;
        }

        private void updateTouchControls(float x, float y) {
            float baseY = screenH - 70;

            left = inside(x, y, 65, baseY, 38);
            right = inside(x, y, 135, baseY, 38);
            up = inside(x, y, 100, baseY - 55, 38);
            down = inside(x, y, 100, baseY + 2, 38);

            firing = inside(x, y, screenW - 65, baseY, 48);
        }

        private boolean inside(float x, float y,
                               float cx, float cy, float radius) {
            float dx = x - cx;
            float dy = y - cy;

            return dx * dx + dy * dy <= radius * radius;
        }
    }
}