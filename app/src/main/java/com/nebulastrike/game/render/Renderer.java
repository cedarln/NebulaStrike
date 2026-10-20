package com.nebulastrike.game.render;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import com.nebulastrike.game.core.Boss;
import com.nebulastrike.game.core.Bullet;
import com.nebulastrike.game.core.Enemy;
import com.nebulastrike.game.core.GameMode;
import com.nebulastrike.game.core.GameWorld;
import com.nebulastrike.game.core.Particle;
import com.nebulastrike.game.core.PowerUp;
import com.nebulastrike.game.core.Ship;
import com.nebulastrike.game.core.Star;
import com.nebulastrike.game.core.Ui;

/**
 * Draws the whole game with Canvas. The simulation lives in a 900x1600 portrait
 * virtual space; this class letterboxes and scales it onto the real display.
 */
public class Renderer {
    public static final float VW = GameWorld.WIDTH;
    public static final float VH = GameWorld.HEIGHT;

    // surfaceChanged（UI 线程）写入，draw（游戏线程）读取
    private volatile float scale = 1f, offX = 0f, offY = 0f;

    private final Paint paint = new Paint();
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** shared scratch path — zero per-frame allocation for all vector shapes */
    private final android.graphics.Path shapePath = new android.graphics.Path();
    /** shared scratch buffer for touch mapping */
    private final float[] touchXY = new float[2];
    // static nebulae: fixed positions/colors, gradients built once
    private final RadialGradient nebulaA = new RadialGradient(
            VW * 0.22f, VH * 0.35f, 420f, new int[]{0x141A3C55, 0x00000000}, null, Shader.TileMode.CLAMP);
    private final RadialGradient nebulaB = new RadialGradient(
            VW * 0.78f, VH * 0.72f, 520f, new int[]{0x0F2A1B44, 0x00000000}, null, Shader.TileMode.CLAMP);

    public Renderer() {
        paint.setAntiAlias(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
        glow.setAntiAlias(true);
        glow.setStyle(Paint.Style.FILL);
    }

    public void setViewport(int w, int h) {
        scale = Math.min(w / VW, h / VH);
        offX = (w - VW * scale) / 2f;
        offY = (h - VH * scale) / 2f;
    }

    /** Maps screen pixels -> virtual coords (used by touch input). Reuses a scratch buffer. */
    public float[] toVirtual(float sx, float sy) {
        touchXY[0] = (sx - offX) / scale;
        touchXY[1] = (sy - offY) / scale;
        return touchXY;
    }

    public void draw(Canvas c, GameWorld w) {
        c.drawColor(Color.BLACK);
        c.save();
        c.translate(offX, offY);
        c.scale(scale, scale);
        c.clipRect(0, 0, VW, VH);

        // screen shake (applied inside the letterbox so bars stay put)
        c.save();
        if (w.shakeTime > 0f) {
            float mag = w.shakeMag * Math.min(1f, w.shakeTime * 3f);
            float sx = (float) Math.sin(w.timeAlive * 89f) * mag;
            float sy = (float) Math.cos(w.timeAlive * 71f) * mag * 0.7f;
            c.translate(sx, sy);
        }

        drawBackground(c, w);
        drawStars(c, w);
        drawParticles(c, w);
        drawPowerUps(c, w);
        drawEnemyBullets(c, w);
        drawEnemies(c, w);
        if (w.boss.active) drawBoss(c, w);
        drawBullets(c, w);
        drawShip(c, w);

        // 缓时滤镜：冷色薄雾 + 边框脉冲（只罩住战场，不影响 HUD）
        if (w.slowTimer > 0f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(0x1F66B8FF);
            c.drawRect(0, 0, VW, VH, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(6f);
            paint.setColor(0x668FE3FF);
            c.drawRect(3f, 3f, VW - 3f, VH - 3f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        c.restore(); // shake

        // bomb white flash
        if (w.bombFlash > 0f) {
            paint.setShader(null);
            paint.setColor(((int) (160 * w.bombFlash)) << 24);
            c.drawRect(0, 0, VW, VH, paint);
        }

        drawHud(c, w);
        c.restore();
    }

    private void drawBackground(Canvas c, GameWorld w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(0xFF05060F);
        c.drawRect(0, 0, VW, VH, paint);
        // two soft nebulae (cached gradients — no per-frame allocation)
        nebula(c, nebulaA, VW * 0.22f, VH * 0.35f, 420f);
        nebula(c, nebulaB, VW * 0.78f, VH * 0.72f, 520f);
    }

    private void nebula(Canvas c, RadialGradient g, float x, float y, float r) {
        glow.setShader(g);
        c.drawCircle(x, y, r, glow);
        glow.setShader(null);
    }

    private void drawStars(Canvas c, GameWorld w) {
        paint.setStyle(Paint.Style.FILL);
        for (Star s : w.stars) {
            float tw = 0.55f + 0.45f * (float) Math.sin(s.twinklePhase + w.timeAlive * 2.5f);
            int alpha = (int) ((90 + s.layer * 55) * tw);
            paint.setColor(0x00FFFFFF | (alpha << 24));
            c.drawCircle(s.x, s.y, s.size, paint);
        }
    }

    private void drawParticles(Canvas c, GameWorld w) {
        if (!w.settings.particles) return;
        for (Particle p : w.particles) {
            if (!p.active) continue;
            float t = p.life / p.maxLife;
            glow.setColor(palette(p.color, (int) (200 * t)));
            c.drawCircle(p.x, p.y, p.size * (0.4f + t), glow);
        }
    }

    private void drawBullets(Canvas c, GameWorld w) {
        paint.setStyle(Paint.Style.FILL);
        for (Bullet b : w.bullets) {
            if (!b.active) continue;
            if (b.piercing) {
                // laser: vertical light beam, white core + cyan sheath
                glow.setColor(0x557DF9FF);
                c.drawCircle(b.x, b.y, 20f, glow);
                paint.setColor(0xFF7DF9FF);
                c.drawRoundRect(b.x - 5f, b.y - b.len, b.x + 5f, b.y + 8f, 5f, 5f, paint);
                paint.setColor(0xE6FFFFFF);
                c.drawRoundRect(b.x - 2f, b.y - b.len + 6f, b.x + 2f, b.y + 4f, 2f, 2f, paint);
            } else if (b.homing) {
                // missile: small rocket with exhaust
                paint.setColor(0xFFFFD166);
                float ang = (float) Math.atan2(b.vy, b.vx);
                c.save();
                c.translate(b.x, b.y);
                c.rotate((float) Math.toDegrees(ang) + 90f);
                shapePath.rewind();
                shapePath.moveTo(0, -14f);
                shapePath.lineTo(7f, 10f);
                shapePath.lineTo(-7f, 10f);
                shapePath.close();
                c.drawPath(shapePath, paint);
                paint.setColor(0x887DF9FF);
                c.drawCircle(0, 14f, 5f, paint);
                c.restore();
            } else if (b.drone) {
                // 僚机弹：粉色小弹，和主武器区分
                paint.setColor(0xFFFF8FD1);
                c.drawCircle(b.x, b.y, b.radius, paint);
                paint.setColor(0xAAFFFFFF);
                c.drawCircle(b.x, b.y, b.radius * 0.4f, paint);
            } else {
                paint.setColor(0xFF7DF9FF);
                c.drawCircle(b.x, b.y, b.radius, paint);
                paint.setColor(0x66FFFFFF);
                c.drawCircle(b.x, b.y, b.radius * 0.45f, paint);
            }
        }
    }

    /**
     * 道具呈现（与敌机强区分）：旋转六边形胶囊 + 双层脉冲光环 + 闪烁描边。
     * 磁吸中（可收集）时：闪烁频率加倍、光环变亮、外圈出现第三道捕获环。
     * 敌机是恒定描边的几何体，不发光不闪烁——动效即"可拾取"信号。
     */
    private void drawPowerUps(Canvas c, GameWorld w) {
        for (PowerUp p : w.powerups) {
            if (!p.active) continue;
            int color;
            String label;
            switch (p.kind) {
                case SPREAD: color = 0xFF7DF9FF; label = "W"; break;
                case MISSILE: color = 0xFFFFD166; label = "M"; break;
                case LASER:  color = 0xFF66E0FF; label = "L"; break;
                case POWER:  color = 0xFFFFB03A; label = "P"; break;
                case SHIELD: color = 0xFF7CFF6B; label = "S"; break;
                case RAPID:  color = 0xFFFF9D5C; label = "R"; break;
                case X2:     color = 0xFFB08FFF; label = "×2"; break;
                case LIFE:   color = 0xFFFF7089; label = "+"; break;
                case WINGMAN: color = 0xFFFF8FD1; label = "D"; break;
                case TIME:   color = 0xFF8FE3FF; label = "T"; break;
                default: color = 0xFFFF5C7A; label = "B"; break;
            }
            boolean hot = p.attracted;
            // 呼吸缩放 + 闪烁频率（磁吸时翻倍）
            float freq = hot ? 12f : 6f;
            float pulse = 1f + 0.12f * (float) Math.sin(p.age * freq);
            float blink = 0.55f + 0.45f * (0.5f + 0.5f * (float) Math.sin(p.age * freq * 2.2f));

            // 光环（两层）：alpha 随闪烁呼吸
            glow.setColor(((int) ((hot ? 0x55 : 0x33) * blink) << 24) | (color & 0xFFFFFF));
            c.drawCircle(p.x, p.y, 34f * pulse, glow);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(hot ? 4f : 2.5f);
            paint.setColor((blink >= 0.8f ? 0xB4 : 0x66) << 24 | (color & 0xFFFFFF));
            c.drawCircle(p.x, p.y, 30f * pulse, paint);
            // 外圈扩散环：从 44 收缩到 30 循环（雷达捕获感），磁吸时更亮
            float ringT = (p.age * (hot ? 2.4f : 1.2f)) % 1f;
            paint.setStrokeWidth(2f);
            paint.setColor(((int) ((hot ? 0x88 : 0x44) * (1f - ringT)) << 24) | (color & 0xFFFFFF));
            c.drawCircle(p.x, p.y, 30f + 16f * (1f - ringT), paint);

            // 主体：旋转六边形（敌机从不旋转）+ 白色高光描边
            c.save();
            c.rotate(p.age * 40f, p.x, p.y);
            paint.setStyle(Paint.Style.FILL);
            hexagon(c, p.x, p.y, 20f * pulse, ((int) (0xD9 * blink) << 24) | (color & 0xFFFFFF));
            c.restore();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(0xFFFFFFFF);
            hexPath(p.x, p.y, 20f * pulse);
            c.drawPath(shapePath, paint);
            paint.setStyle(Paint.Style.FILL);

            paint.setColor(0xFF0A0C18);
            paint.setTextSize(23f);
            paint.setFakeBoldText(true);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText(label, p.x, p.y + 8f, paint);
            paint.setFakeBoldText(false);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawEnemyBullets(Canvas c, GameWorld w) {
        for (com.nebulastrike.game.core.EnemyBullet b : w.enemyBullets) {
            if (!b.active) continue;
            // fading trail
            glow.setColor(0x55FF7A4D);
            c.drawCircle(b.x - b.vx * 0.02f, b.y - b.vy * 0.02f, b.radius * 0.8f, glow);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFF7A4D);
            c.drawCircle(b.x, b.y, b.radius, paint);
            paint.setColor(0xFFFFE08A);
            c.drawCircle(b.x, b.y, b.radius * 0.45f, paint);
        }
    }

    private void drawEnemies(Canvas c, GameWorld w) {
        for (Enemy e : w.enemies) {
            if (!e.active) continue;
            boolean flashing = e.flash > 0f;
            int base = palette(e.type.colorHint, flashing ? 255 : 235);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(base);
            float r = e.type.radius;
            switch (e.type) {
                case DRONE:
                    triangle(c, e.x, e.y, r, true);
                    cockpit(c, e.x, e.y - r * 0.15f, r * 0.3f, 0xFF101528);
                    break;
                case WEAVER:
                    diamond(c, e.x, e.y, r, base);
                    break;
                case HUNTER:
                    triangle(c, e.x, e.y, r, true);
                    paint.setColor(0xFFFF5C7A);
                    c.drawCircle(e.x, e.y + r * 0.2f, r * 0.28f, paint);
                    break;
                case TANK:
                    // hexagonal brick with hull plates + hp pips
                    hexagon(c, e.x, e.y, r, base);
                    paint.setColor(0xFF101528);
                    c.drawCircle(e.x, e.y, r * 0.45f, paint);
                    paint.setColor(0xFFFFD166);
                    for (int i = 0; i < (int) e.hp; i++) {
                        c.drawRect(e.x - r * 0.5f + i * (r * 0.25f),
                                e.y + r * 0.6f,
                                e.x - r * 0.5f + i * (r * 0.25f) + r * 0.16f,
                                e.y + r * 0.6f + 5f, paint);
                    }
                    break;
            }
        }
    }

    private void drawBoss(Canvas c, GameWorld w) {
        Boss b = w.boss;
        boolean flashing = b.flash > 0f;
        int hullColor = flashing ? 0xFFFFFFFF
                : b.phase == 3 ? 0xFFFF5C7A : b.phase == 2 ? 0xFFFFB84D : 0xFFB44DFF;

        // engine glow under the hull
        glow.setColor(0x55B44DFF);
        c.drawCircle(b.x, b.y + Boss.RADIUS * 0.8f, 60f, glow);

        // rotating outer ring
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(7f);
        paint.setColor(0x88B44DFF);
        float ringR = Boss.RADIUS * 1.25f;
        c.save();
        c.translate(b.x, b.y);
        c.rotate(b.age * 18f);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3.0 * i;
            float px = (float) Math.cos(a) * ringR;
            float py = (float) Math.sin(a) * ringR;
            c.drawCircle(px, py, 9f, paint);
        }
        c.restore();

        // hull: layered hexagons
        paint.setStyle(Paint.Style.FILL);
        hexagon(c, b.x, b.y, Boss.RADIUS, 0xFF2A2350);
        hexagon(c, b.x, b.y, Boss.RADIUS * 0.82f, hullColor);
        paint.setColor(0xFF0A0C18);
        c.drawCircle(b.x, b.y, Boss.RADIUS * 0.42f, paint);
        paint.setColor(hullColor);
        c.drawCircle(b.x, b.y, Boss.RADIUS * 0.28f * (1f + 0.15f * (float) Math.sin(b.age * 5f)), paint);

        // side pods
        paint.setColor(0xFF2A2350);
        c.drawCircle(b.x - Boss.RADIUS * 0.95f, b.y, 26f, paint);
        c.drawCircle(b.x + Boss.RADIUS * 0.95f, b.y, 26f, paint);

        // top health bar
        float barW = 620f, barH = 22f;
        float bx = VW / 2f - barW / 2f, by = 30f;
        paint.setColor(0x66000000);
        c.drawRect(bx - 4f, by - 4f, bx + barW + 4f, by + barH + 4f, paint);
        paint.setColor(0xFF3A1040);
        c.drawRect(bx, by, bx + barW, by + barH, paint);
        float frac = Math.max(0f, b.hp / b.maxHp);
        paint.setColor(b.phase == 3 ? 0xFFFF5C7A : b.phase == 2 ? 0xFFFFB84D : 0xFFB44DFF);
        c.drawRect(bx, by, bx + barW * frac, by + barH, paint);
        paint.setColor(0xFFE8F1FF);
        paint.setTextSize(24f);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText("DREADNOUGHT MK-" + (b.level + 1) + "   PHASE " + b.phase + "/3",
                VW / 2f, by + 52f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawShip(Canvas c, GameWorld w) {
        Ship s = w.ship;
        if (w.state == GameWorld.State.GAME_OVER) return;
        // invulnerability blink
        if (s.invulnerable && (int) (w.timeAlive * 12) % 2 == 0) return;

        // overdrive aura: 擦弹奖励的视觉状态
        if (s.overdriveTimer > 0f) {
            float pulse = 1f + 0.07f * (float) Math.sin(w.timeAlive * 10f);
            glow.setColor(0x66FFD166);
            c.drawCircle(s.x, s.y, Ship.RADIUS * 1.7f * pulse, glow);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(0xCCFFD166);
            c.drawCircle(s.x, s.y, Ship.RADIUS * 1.45f * pulse, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        // engine flame
        float flicker = 8f + 6f * (float) Math.sin(w.timeAlive * 40f);
        glow.setColor(0x8844CCFF);
        c.save();
        c.translate(s.x, s.y + Ship.RADIUS * 0.9f);
        triangleAbs(c, 0, flicker * 1.6f, 12f, false);
        c.restore();
        glow.setColor(0xAA9BE8FF);
        c.save();
        c.translate(s.x, s.y + Ship.RADIUS * 0.8f);
        triangleAbs(c, 0, flicker, 8f, false);
        c.restore();

        // shield bubble
        if (s.hasShield) {
            glow.setColor(0x447CFF6B);
            c.drawCircle(s.x, s.y, Ship.RADIUS * 2.1f, glow);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(0xFF7CFF6B);
            c.drawCircle(s.x, s.y, Ship.RADIUS * 2.1f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        // hull: swept-wing fighter
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFE8F1FF);
        hull(c, s.x, s.y);
        paint.setColor(0xFF274BFF);
        hullStripe(c, s.x, s.y);
        paint.setColor(0xFFFF3355);
        c.drawCircle(s.x, s.y + 2f, 6f, paint);

        // 僚机：两翼小型无人机 + 能量连线，剩余 2 秒开始闪烁提醒
        if (s.wingmanTimer > 0f) {
            boolean expiring = s.wingmanTimer < 2f && (int) (w.timeAlive * 8f) % 2 == 0;
            if (!expiring) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f);
                paint.setColor(0x33FF8FD1);
                c.drawLine(s.x, s.y, s.x - 52f, s.y + 2f, paint);
                c.drawLine(s.x, s.y, s.x + 52f, s.y + 2f, paint);
                paint.setStyle(Paint.Style.FILL);
                for (int i = -1; i <= 1; i += 2) {
                    float dx = s.x + i * 52f;
                    glow.setColor(0x66FF8FD1);
                    c.drawCircle(dx, s.y + 2f, 18f, glow);
                    paint.setColor(0xFFFF8FD1);
                    c.save();
                    c.translate(dx, s.y + 2f);
                    c.rotate((float) Math.sin(w.timeAlive * 5f + i) * 8f);
                    triangleAbs(c, 0f, 0f, 12f, false);
                    c.restore();
                }
            }
        }
    }

    // ---- HUD & screens ----
    private void drawHud(Canvas c, GameWorld w) {
        switch (w.state) {
            case MENU:
                if (w.cheatOpen) drawCheats(c, w);
                else if (w.settingsOpen) drawSettings(c, w);
                else drawMenu(c, w);
                return;
            case PAUSED:
                drawInGameHud(c, w);
                drawPauseOverlay(c, w);
                return;
            case GAME_OVER:
                drawInGameHud(c, w);
                drawGameOver(c, w);
                return;
            default:
                drawInGameHud(c, w);
        }
    }

    private void drawInGameHud(Canvas c, GameWorld w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(44f);
        c.drawText(String.valueOf(w.score), 40f, 70f, paint);

        paint.setTextSize(30f);
        paint.setColor(w.mode == GameMode.ENDLESS ? 0xFFB08FFF : 0xFF8FA3C8);
        c.drawText((w.mode == GameMode.ENDLESS ? "ENDLESS " : "WAVE ") + w.wave, VW / 2f - 70f, 60f, paint);

        // LAB 参数生效的标记（避免改过参数却忘记）
        if (w.settings.cheatsActive()) {
            paint.setTextSize(22f);
            paint.setColor(0xFFFF8FD1);
            paint.setTextAlign(Paint.Align.RIGHT);
            c.drawText("LAB", VW - 40f, 146f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        // combo counter
        if (w.comboKills > 1) {
            int mult = w.comboMultiplier();
            paint.setTextSize(26f + mult * 2f);
            paint.setColor(mult >= 3 ? 0xFFFFD166 : 0xFF7DF9FF);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("COMBO x" + mult + "  (" + w.comboKills + ")", VW / 2f, 100f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        // weapon status + timer bar
        if (w.ship.weapon != com.nebulastrike.game.core.WeaponType.VULCAN) {
            String name;
            switch (w.ship.weapon) {
                case SPREAD: name = "SPREAD"; break;
                case LASER: name = "LASER"; break;
                default: name = "MISSILES"; break;
            }
            paint.setColor(0xFFFFD166);
            paint.setTextSize(26f);
            c.drawText(name, 40f, 112f, paint);
            float frac = w.ship.weaponTimer / Ship.WEAPON_DURATION;
            paint.setColor(0x33FFD166);
            c.drawRect(40f, 124f, 40f + 180f, 132f, paint);
            paint.setColor(0xFFFFD166);
            c.drawRect(40f, 124f, 40f + 180f * frac, 132f, paint);
        }
        // 火力等级 pips（雷电式）：右上角 3 格，放在残机图标下方避免重叠
        for (int i = 0; i < Ship.MAX_POWER; i++) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(i < w.ship.powerLevel ? 0xFFFFB03A : 0x33FFB03A);
            c.drawCircle(VW - 46f - i * 30f, 96f, 9f, paint);
        }
        if (w.ship.hasShield) {
            paint.setColor(0xFF7CFF6B);
            paint.setTextSize(26f);
            c.drawText("SHIELD", 40f, 166f, paint);
        }

        // 速射 / 双倍分数 状态条
        float statusY = w.ship.hasShield ? 204f : 166f;
        if (w.ship.rapidTimer > 0f) {
            paint.setColor(0xFFFF9D5C);
            paint.setTextSize(26f);
            c.drawText("RAPID", 40f, statusY, paint);
            float frac = w.ship.rapidTimer / Ship.RAPID_DURATION;
            paint.setColor(0x33FF9D5C);
            c.drawRect(40f, statusY + 12f, 40f + 180f, statusY + 20f, paint);
            paint.setColor(0xFFFF9D5C);
            c.drawRect(40f, statusY + 12f, 40f + 180f * frac, statusY + 20f, paint);
            statusY += 38f;
        }
        if (w.x2Timer > 0f) {
            paint.setColor(0xFFB08FFF);
            paint.setTextSize(26f);
            c.drawText("SCORE ×2", 40f, statusY, paint);
            float frac = w.x2Timer / GameWorld.X2_DURATION;
            paint.setColor(0x33B08FFF);
            c.drawRect(40f, statusY + 12f, 40f + 180f, statusY + 20f, paint);
            paint.setColor(0xFFB08FFF);
            c.drawRect(40f, statusY + 12f, 40f + 180f * frac, statusY + 20f, paint);
            statusY += 38f;
        }
        if (w.ship.wingmanTimer > 0f) {
            statusBar(c, "WINGMAN", 0xFFFF8FD1,
                    w.ship.wingmanTimer / Ship.WINGMAN_DURATION, statusY);
            statusY += 38f;
        }
        if (w.slowTimer > 0f) {
            statusBar(c, "TIME SLOW", 0xFF8FE3FF,
                    w.slowTimer / GameWorld.SLOW_DURATION, statusY);
        }

        // 擦弹充能：固定放左下，避免和左上角的状态条抢位置
        if (w.ship.overdriveTimer > 0f) {
            statusBar(c, "OVERDRIVE", 0xFFFFD166,
                    w.ship.overdriveTimer / Ship.OVERDRIVE_DURATION, 1200f);
        } else {
            statusBar(c, "GRAZE", 0xFFFF8FD1, w.grazeCharge, 1200f);
        }

        // lives as mini ships
        c.save();
        for (int i = 0; i < w.lives; i++) {
            c.save();
            c.translate(VW - 60f - i * 70f, 52f);
            c.scale(0.55f, 0.55f);
            hull(c, 0, 0);
            c.restore();
        }
        c.restore();

        // pause button
        paint.setColor(0xAA8FA3C8);
        c.drawRect(Ui.PAUSE_BTN.x + 14f, Ui.PAUSE_BTN.y + 8f,
                Ui.PAUSE_BTN.x + 26f, Ui.PAUSE_BTN.y + 56f, paint);
        c.drawRect(Ui.PAUSE_BTN.x + 38f, Ui.PAUSE_BTN.y + 8f,
                Ui.PAUSE_BTN.x + 50f, Ui.PAUSE_BTN.y + 56f, paint);

        drawControls(c, w);
    }

    /** 竖屏操作呈现：浮动摇杆（按下才显示）+ 右下角炸弹圆键。 */
    private void drawControls(Canvas c, GameWorld w) {
        // 浮动摇杆：基座 = 按下锚点，摇杆头朝手指方向偏移（限幅在基座圆内）。
        // joyAnim 驱动"锁定"动画：出现时圆环从 1.5 倍半径收缩到位并渐入，
        // 松手反向渐出消失；拖动方向上有连线与箭头反馈。
        if (w.joyAnim > 0.01f) {
            float a = w.joyAnim;
            float ease = 1f - (1f - a) * (1f - a);          // easeOutQuad
            float baseR = 70f * (0.55f + 0.45f * ease);    // 收缩到位
            float ringR = baseR * (1.5f - 0.5f * ease);    // 扩散环追上基座

            float dx = w.joyX - w.joyBaseX, dy = w.joyY - w.joyBaseY;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float hx = w.joyBaseX, hy = w.joyBaseY;
            if (d > 1f) {
                float clamped = Math.min(d, baseR);
                hx = w.joyBaseX + dx / d * clamped;
                hy = w.joyBaseY + dy / d * clamped;
            }

            // 外圈光晕
            glow.setColor(((int) (0x33 * a) << 24) | 0x7DF9FF);
            c.drawCircle(w.joyBaseX, w.joyBaseY, baseR * 1.25f, glow);

            // 基座：内填充 + 主圆环
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(((int) (0x22 * a) << 24) | 0x7DF9FF);
            c.drawCircle(w.joyBaseX, w.joyBaseY, baseR, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(((int) (0x99 * a) << 24) | 0x7DF9FF);
            c.drawCircle(w.joyBaseX, w.joyBaseY, baseR, paint);

            // 扩散锁定环（按住期间缓慢呼吸，按下瞬间明显收缩）
            float breathe = 1f + 0.04f * (float) Math.sin(w.timeAlive * 6f);
            paint.setStrokeWidth(2f);
            paint.setColor(((int) (0x55 * a) << 24) | 0x7DF9FF);
            c.drawCircle(w.joyBaseX, w.joyBaseY, ringR * breathe, paint);

            // 方向连线（拖动偏离基座时）
            if (d > 14f) {
                paint.setStrokeWidth(4f);
                paint.setColor(((int) (0x77 * a) << 24) | 0x7DF9FF);
                c.drawLine(w.joyBaseX, w.joyBaseY, hx, hy, paint);
            }

            // 摇杆头：实心圆 + 高光内芯
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(((int) (0x99 * a) << 24) | 0x7DF9FF);
            c.drawCircle(hx, hy, 26f, paint);
            paint.setColor(((int) (0xE6 * a) << 24) | 0xE8F1FF);
            c.drawCircle(hx, hy, 13f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(((int) (0xCC * a) << 24) | 0xE8F1FF);
            c.drawCircle(hx, hy, 26f, paint);
        }

        // 炸弹圆键 + 数量（左手模式镜像到左下）
        Ui.Rect b = Ui.bomb(w.settings.leftHanded);
        boolean bombReady = w.ship.bombs > 0;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(bombReady ? 0x88FF5C7A : 0x448FA3C8);
        c.drawCircle(b.cx(), b.cy(), 80f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bombReady ? 0xFFFF5C7A : 0x668FA3C8);
        c.drawCircle(b.cx(), b.cy(), 62f, paint);
        paint.setColor(0xFFFFE08A);
        c.drawCircle(b.cx(), b.cy() - 14f, 18f, paint);
        paint.setColor(0xFF0A0C18);
        paint.setTextSize(40f);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(String.valueOf(w.ship.bombs), b.cx(), b.cy() + 44f, paint);
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawMenu(Canvas c, GameWorld w) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x66000000);
        c.drawRect(0, 0, VW, VH, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(120f);
        paint.setColor(0xFF7DF9FF);
        c.drawText("NEBULA", VW / 2f, 190f, paint);
        paint.setColor(0xFFFF3355);
        c.drawText("STRIKE", VW / 2f, 305f, paint);

        paint.setColor(0xFF8FA3C8);
        paint.setTextSize(28f);
        c.drawText("SLIDE ANYWHERE TO FLY  •  AUTO FIRE  •  BOMB BUTTON", VW / 2f, 360f, paint);

        button(c, Ui.START, "CAMPAIGN", 0xFF274BFF, 0xFFE8F1FF, true);
        button(c, Ui.START_ENDLESS, "ENDLESS", 0xFF8A2BE2, 0xFFFFD166, true);
        button(c, Ui.SETTINGS, "SETTINGS", 0xFF182040, 0xFF8FA3C8, false, 30f);
        button(c, Ui.CHEATS, w.settings.cheatsActive() ? "LAB ON" : "LAB",
                0xFF182040, 0xFFFF8FD1, false, 30f);

        paint.setTextSize(28f);
        paint.setColor(0xFFFFD166);
        c.drawText("BEST  " + w.bestScore + "    v" + w.currentVersion, VW / 2f, 690f, paint);

        drawUpdateButton(c, w);
        drawDualBoard(c, w, VW / 2f - 165f, 830f, 330f, 44f, 22f);

        paint.setTextSize(26f);
        paint.setColor(0x668FA3C8);
        c.drawText("按住屏幕任意位置滑动即可操控飞船 · 自动开火 · 炸弹键可切左手", VW / 2f, 1185f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** 菜单底部“检查更新”入口，按升级状态机渲染不同文案与颜色。 */
    private void drawUpdateButton(Canvas c, GameWorld w) {
        String label;
        int bg, fg;
        switch (w.updateState) {
            case CHECKING:    label = "检查中…"; bg = 0xFF182040; fg = 0xFF8FA3C8; break;
            case UP_TO_DATE:  label = "已是最新版本 v" + w.currentVersion;
                              bg = 0xFF182040; fg = 0xFF6FE38B; break;
            case AVAILABLE:   label = "发现新版本 " + w.updateVersion + "，点击下载";
                              bg = 0xFF2E7D32; fg = 0xFFE8FFEA; break;
            case DOWNLOADING: label = "下载中 " + w.updateProgress + "%";
                              bg = 0xFF182040; fg = 0xFFFFD166; break;
            case READY:       label = "已就绪，正在打开安装…"; bg = 0xFF182040; fg = 0xFF7DF9FF; break;
            case ERROR:       label = w.updateError != null ? w.updateError : "检查失败，点击重试";
                              bg = 0xFF401820; fg = 0xFFFF7089; break;
            default:          label = "检查更新"; bg = 0xFF182040; fg = 0xFF8FA3C8; break;
        }
        button(c, Ui.CHECK_UPDATE, label, bg, fg, w.updateState == GameWorld.UpdateState.AVAILABLE,
                34f);

        // 下载中画进度条
        if (w.updateState == GameWorld.UpdateState.DOWNLOADING) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFD166);
            float pad = 14f;
            float frac = Math.max(0.02f, w.updateProgress / 100f);
            c.drawRoundRect(Ui.CHECK_UPDATE.x + pad,
                    Ui.CHECK_UPDATE.y + Ui.CHECK_UPDATE.h - 6f,
                    Ui.CHECK_UPDATE.x + pad + (Ui.CHECK_UPDATE.w - pad * 2f) * frac,
                    Ui.CHECK_UPDATE.y + Ui.CHECK_UPDATE.h - 2f, 2f, 2f, paint);
        }
    }

    private void drawSettings(Canvas c, GameWorld w) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x88000000);
        c.drawRect(0, 0, VW, VH, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(64f);
        paint.setColor(0xFFE8F1FF);
        c.drawText("SETTINGS", VW / 2f, 400f, paint);

        toggle(c, Ui.TOGGLE_SOUND, "SOUND", w.settings.sound);
        toggle(c, Ui.TOGGLE_PARTICLES, "PARTICLES", w.settings.particles);
        toggle(c, Ui.TOGGLE_HAPTICS, "HAPTICS", w.settings.haptics);
        toggle(c, Ui.TOGGLE_REDUCED_FX, "LOW FX", w.settings.reducedFx);
        toggle(c, Ui.TOGGLE_LEFT_HAND, "LEFT HAND", w.settings.leftHanded);
        button(c, Ui.SETTINGS_BACK, "BACK", 0xFF182040, 0xFF8FA3C8, false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** 金手指面板：数值类参数用 [-] 值 [+] 行，覆盖掉率/生命/敌速/火力与无敌。 */
    private void drawCheats(Canvas c, GameWorld w) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xAA000000);
        c.drawRect(0, 0, VW, VH, paint);
        paint.setColor(0xE60A0C18);
        c.drawRoundRect(90f, 250f, 810f, 1090f, 24f, 24f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x66FF8FD1);
        c.drawRoundRect(90f, 250f, 810f, 1090f, 24f, 24f, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(58f);
        paint.setColor(0xFFFF8FD1);
        c.drawText("LAB", VW / 2f, 330f, paint);
        paint.setTextSize(24f);
        paint.setColor(0xFF8FA3C8);
        c.drawText("金手指参数 · 生命/火力下一局生效", VW / 2f, 372f, paint);

        stepper(c, Ui.CHEAT_LIVES_MINUS, Ui.CHEAT_LIVES_PLUS,
                "初始生命", String.valueOf(w.settings.startLives));
        stepper(c, Ui.CHEAT_DROP_MINUS, Ui.CHEAT_DROP_PLUS,
                "道具掉率", w.settings.dropRatePct + "%");
        stepper(c, Ui.CHEAT_SPEED_MINUS, Ui.CHEAT_SPEED_PLUS,
                "敌机速度", w.settings.enemySpeedPct + "%");
        stepper(c, Ui.CHEAT_POWER_MINUS, Ui.CHEAT_POWER_PLUS,
                "初始火力", "LV" + w.settings.startPower);
        toggle(c, Ui.TOGGLE_GOD, "无敌模式", w.settings.godMode);
        button(c, Ui.CHEAT_RESET, "RESET", 0xFF182040, 0xFF8FA3C8, false, 30f);
        button(c, Ui.CHEATS_BACK, "BACK", 0xFF182040, 0xFFE8F1FF, false, 30f);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void stepper(Canvas c, Ui.Rect minus, Ui.Rect plus, String label, String value) {
        button(c, minus, "−", 0xFF182040, 0xFF8FA3C8, false, 44f);
        button(c, plus, "+", 0xFF182040, 0xFF8FA3C8, false, 44f);
        paint.setTextSize(32f);
        paint.setColor(0xFFE8F1FF);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText(label, minus.x + 110f, minus.cy() + 11f, paint);
        paint.setColor(0xFFFFD166);
        paint.setTextAlign(Paint.Align.RIGHT);
        c.drawText(value, plus.x - 24f, plus.cy() + 11f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** HUD 状态条：label + 剩余时间比例条。 */
    private void statusBar(Canvas c, String label, int color, float frac, float y) {
        paint.setColor(color);
        paint.setTextSize(26f);
        c.drawText(label, 40f, y, paint);
        paint.setColor((color & 0x00FFFFFF) | 0x33000000);
        c.drawRect(40f, y + 12f, 220f, y + 20f, paint);
        paint.setColor(color);
        c.drawRect(40f, y + 12f, 40f + 180f * frac, y + 20f, paint);
    }

    private void drawPauseOverlay(Canvas c, GameWorld w) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x99000000);
        c.drawRect(0, 0, VW, VH, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(90f);
        paint.setColor(0xFFE8F1FF);
        c.drawText("PAUSED", VW / 2f, 270f, paint);
        button(c, Ui.RESUME, "RESUME", 0xFF274BFF, 0xFFE8F1FF, true);
        button(c, Ui.RESTART, "RESTART", 0xFF182040, 0xFF8FA3C8, false);
        button(c, Ui.QUIT_TO_MENU, "MENU", 0xFF182040, 0xFF8FA3C8, false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawGameOver(Canvas c, GameWorld w) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x99000000);
        c.drawRect(0, 0, VW, VH, paint);
        paint.setTextAlign(Paint.Align.CENTER);

        paint.setTextSize(96f);
        paint.setColor(0xFFFF3355);
        c.drawText("GAME OVER", VW / 2f, 400f, paint);

        paint.setTextSize(44f);
        paint.setColor(0xFFFFFFFF);
        c.drawText("Score " + w.score + "   •   "
                + (w.mode == GameMode.ENDLESS ? "ENDLESS" : "WAVE " + w.wave), VW / 2f, 468f, paint);

        if (w.newBest) {
            float pulse = 1f + 0.06f * (float) Math.sin(w.stateTimer * 6f);
            paint.setTextSize(38f * pulse);
            paint.setColor(0xFFFFD166);
            c.drawText("★ NEW BEST " + w.bestScore + " ★", VW / 2f, 520f, paint);
        } else {
            paint.setTextSize(34f);
            paint.setColor(0xFF8FA3C8);
            c.drawText("BEST  " + w.bestScore, VW / 2f, 518f, paint);
        }

        drawRunStats(c, w);
        drawLeaderboard(c, w.board(w.mode), w.mode, VW / 2f - 160f, 708f, 320f, 30f, 22f, w.scoreRank);

        paint.setTextSize(40f);
        float alpha = 0.4f + 0.6f * Math.abs((float) Math.sin(w.stateTimer * 2.2f));
        paint.setColor(((int) (255 * alpha) << 24) | 0x8FA3C8);
        c.drawText("Tap to fly again", VW / 2f, 960f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** 本局数据卡：命中率 / 连击 / 擦弹 / 击坠 / 受伤 / 存活。 */
    private void drawRunStats(Canvas c, GameWorld w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x99000011);
        c.drawRoundRect(120f, 548f, 780f, 688f, 18f, 18f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x66FF8FD1);
        c.drawRoundRect(120f, 548f, 780f, 688f, 18f, 18f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);

        String[] labels = {"ACCURACY", "MAX COMBO", "GRAZES", "KILLS", "HITS", "TIME"};
        String[] values = {
                w.accuracyPct() + "%",
                "x" + w.maxCombo,
                String.valueOf(w.grazes),
                String.valueOf(w.kills),
                String.valueOf(w.hitsTaken),
                ((int) w.timeAlive) + "s"
        };
        float[] cx = {230f, 450f, 670f};
        for (int i = 0; i < labels.length; i++) {
            float x = cx[i % 3];
            float y = i < 3 ? 586f : 654f;
            paint.setTextSize(21f);
            paint.setColor(0xFF8FA3C8);
            c.drawText(labels[i], x, y, paint);
            paint.setTextSize(32f);
            paint.setColor(i == 1 ? 0xFFFFD166 : i == 2 ? 0xFFFF8FD1 : 0xFFE8F1FF);
            c.drawText(values[i], x, y + 32f, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** Menu panel: WAVES and ENDLESS top-5 boards side by side. */
    private void drawDualBoard(Canvas c, GameWorld w, float x, float y,
                               float wdt, float rowH, float size) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x99000011);
        float h = rowH * 6.8f;
        c.drawRoundRect(x, y, x + wdt, y + h, 18f, 18f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x66274BFF);
        c.drawRoundRect(x, y, x + wdt, y + h, 18f, 18f, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setTextSize(size);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(0xFF7DF9FF);
        c.drawText("WAVES", x + 15f, y + rowH, paint);
        paint.setColor(0xFFB08FFF);
        paint.setTextAlign(Paint.Align.RIGHT);
        c.drawText("ENDLESS", x + wdt - 15f, y + rowH, paint);

        for (int i = 0; i < 5; i++) {
            float ry = y + rowH * (i + 2f);
            drawBoardRow(c, w.leaderboard[i], x + 15f, ry, Paint.Align.LEFT);
            drawBoardRow(c, w.leaderboardEndless[i], x + wdt - 15f, ry, Paint.Align.RIGHT);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawBoardRow(Canvas c, int score, float edge, float y, Paint.Align align) {
        paint.setTextAlign(align);
        paint.setColor(score <= 0 ? 0x558FA3C8 : 0xFFE8F1FF);
        c.drawText(score <= 0 ? "—" : String.valueOf(score), edge, y, paint);
    }

    /** Compact one-board panel (game-over screen): the board of the run's mode. */
    private void drawLeaderboard(Canvas c, int[] board, GameMode mode, float x, float y,
                                 float wdt, float rowH, float size, int highlightRank) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x99000011);
        float h = rowH * 6.8f;
        c.drawRoundRect(x, y, x + wdt, y + h, 18f, 18f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x66274BFF);
        c.drawRoundRect(x, y, x + wdt, y + h, 18f, 18f, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(size * 0.92f);
        paint.setColor(mode == GameMode.ENDLESS ? 0xFFB08FFF : 0xFF7DF9FF);
        c.drawText(mode == GameMode.ENDLESS ? "ENDLESS TOP 5" : "CAMPAIGN TOP 5",
                x + 20f, y + rowH, paint);

        paint.setTextSize(size);
        for (int i = 0; i < 5; i++) {
            float ry = y + rowH * (i + 2f);
            if (board[i] <= 0) {
                paint.setColor(0x558FA3C8);
                paint.setTextAlign(Paint.Align.LEFT);
                c.drawText((i + 1) + ".", x + 20f, ry, paint);
            } else {
                paint.setColor(i == highlightRank ? 0xFFFFD166 : 0xFFE8F1FF);
                paint.setTextAlign(Paint.Align.LEFT);
                c.drawText((i + 1) + ".", x + 20f, ry, paint);
                paint.setTextAlign(Paint.Align.RIGHT);
                c.drawText(String.valueOf(board[i]), x + wdt - 20f, ry, paint);
            }
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void button(Canvas c, Ui.Rect r, String label, int bg, int fg, boolean filled) {
        button(c, r, label, bg, fg, filled, 42f);
    }

    private void button(Canvas c, Ui.Rect r, String label, int bg, int fg, boolean filled,
                        float textSize) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bg);
        if (filled) {
            c.drawRoundRect(r.x, r.y, r.x + r.w, r.y + r.h, 18f, 18f, paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            c.drawRoundRect(r.x, r.y, r.x + r.w, r.y + r.h, 18f, 18f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fg);
        paint.setTextSize(textSize);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        c.drawText(label, r.cx(), r.cy() + textSize * 0.36f, paint);
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void toggle(Canvas c, Ui.Rect r, String label, boolean on) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF182040);
        c.drawRoundRect(r.x, r.y, r.x + r.w, r.y + r.h, 14f, 14f, paint);
        paint.setColor(on ? 0xFF7CFF6B : 0xFF5A647F);
        c.drawCircle(r.x + r.w - 46f, r.cy(), 18f, paint);
        paint.setColor(0xFFE8F1FF);
        paint.setTextSize(34f);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText(label, r.x + 30f, r.cy() + 12f, paint);
    }

    // ---- shape helpers ----
    private void triangle(Canvas c, float x, float y, float r, boolean down) {
        c.save();
        c.translate(x, y);
        if (down) c.rotate(180f);
        triangleAbs(c, 0, 0, r, false);
        c.restore();
    }

    private void triangleAbs(Canvas c, float x, float y, float r, boolean fill) {
        paint.setStyle(Paint.Style.FILL);
        shapePath.rewind();
        shapePath.moveTo(x, y - r);
        shapePath.lineTo(x - r * 0.85f, y + r * 0.75f);
        shapePath.lineTo(x + r * 0.85f, y + r * 0.75f);
        shapePath.close();
        c.drawPath(shapePath, paint);
    }

    private void diamond(Canvas c, float x, float y, float r, int color) {
        paint.setColor(color);
        shapePath.rewind();
        shapePath.moveTo(x, y - r);
        shapePath.lineTo(x + r * 0.7f, y);
        shapePath.lineTo(x, y + r);
        shapePath.lineTo(x - r * 0.7f, y);
        shapePath.close();
        c.drawPath(shapePath, paint);
    }

    /** Builds a hexagon into shapePath (no drawing). */
    private void hexPath(float x, float y, float r) {
        shapePath.rewind();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3.0 * i + Math.PI / 6.0;
            float px = x + (float) Math.cos(a) * r;
            float py = y + (float) Math.sin(a) * r;
            if (i == 0) shapePath.moveTo(px, py); else shapePath.lineTo(px, py);
        }
        shapePath.close();
    }

    private void hexagon(Canvas c, float x, float y, float r, int color) {
        hexPath(x, y, r);
        paint.setColor(color);
        c.drawPath(shapePath, paint);
    }

    private void hull(Canvas c, float x, float y) {
        shapePath.rewind();
        shapePath.moveTo(x, y - Ship.RADIUS);
        shapePath.lineTo(x + Ship.RADIUS * 0.35f, y - Ship.RADIUS * 0.1f);
        shapePath.lineTo(x + Ship.RADIUS * 0.95f, y + Ship.RADIUS * 0.8f);
        shapePath.lineTo(x + Ship.RADIUS * 0.30f, y + Ship.RADIUS * 0.45f);
        shapePath.lineTo(x, y + Ship.RADIUS * 0.6f);
        shapePath.lineTo(x - Ship.RADIUS * 0.30f, y + Ship.RADIUS * 0.45f);
        shapePath.lineTo(x - Ship.RADIUS * 0.95f, y + Ship.RADIUS * 0.8f);
        shapePath.lineTo(x - Ship.RADIUS * 0.35f, y - Ship.RADIUS * 0.1f);
        shapePath.close();
        c.drawPath(shapePath, paint);
    }

    private void hullStripe(Canvas c, float x, float y) {
        shapePath.rewind();
        shapePath.moveTo(x, y - Ship.RADIUS * 0.75f);
        shapePath.lineTo(x + Ship.RADIUS * 0.18f, y);
        shapePath.lineTo(x, y + Ship.RADIUS * 0.4f);
        shapePath.lineTo(x - Ship.RADIUS * 0.18f, y);
        shapePath.close();
        c.drawPath(shapePath, paint);
    }

    private void cockpit(Canvas c, float x, float y, float r, int color) {
        paint.setColor(color);
        c.drawCircle(x, y, r, paint);
    }

    /** Neon palette shared across the game. index: 0 cyan 1 green 2 orange 3 magenta 4 blue */
    private static int palette(int idx, int alpha) {
        int rgb;
        switch (idx) {
            case 1: rgb = 0x7CFF6B; break;
            case 2: rgb = 0xFFB84D; break;
            case 3: rgb = 0xFF5CF0; break;
            case 4: rgb = 0x4D7CFF; break;
            default: rgb = 0x5CE1FF; break;
        }
        return (alpha << 24) | rgb;
    }
}
