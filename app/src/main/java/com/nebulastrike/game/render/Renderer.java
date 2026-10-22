package com.nebulastrike.game.render;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import com.nebulastrike.game.core.Boss;
import com.nebulastrike.game.core.BossLaser;
import com.nebulastrike.game.core.BossPart;
import com.nebulastrike.game.core.Bullet;
import com.nebulastrike.game.core.Difficulty;
import com.nebulastrike.game.core.Enemy;
import com.nebulastrike.game.core.EnemyType;
import com.nebulastrike.game.core.FloatText;
import com.nebulastrike.game.core.GameMode;
import com.nebulastrike.game.core.GameWorld;
import com.nebulastrike.game.core.GravityWell;
import com.nebulastrike.game.core.Particle;
import com.nebulastrike.game.core.PowerUp;
import com.nebulastrike.game.core.Relic;
import com.nebulastrike.game.core.Ship;
import com.nebulastrike.game.core.Star;
import com.nebulastrike.game.core.Turret;
import com.nebulastrike.game.core.Ultimate;
import com.nebulastrike.game.core.Ui;
import com.nebulastrike.game.core.WeaponType;

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
    private final android.graphics.RectF arcRect = new android.graphics.RectF();
    /** shared scratch buffer for touch mapping */
    private final float[] touchXY = new float[2];
    /** shared scratch buffer for orbit sphere positions */
    private final float[] orbitPos = new float[2];
    /** shared scratch buffer for 飘字数字，避免每帧为每个数字新建字符串 */
    private final char[] textBuf = new char[8];
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
        drawTurrets(c, w);
        drawGravityWells(c, w);
        if (w.boss.active) {
            drawBoss(c, w);
            drawBossLasers(c, w);
        }
        drawBullets(c, w);
        drawArcs(c, w);
        drawShip(c, w);
        drawOrbitSpheres(c, w);
        drawFloatTexts(c, w);   // 伤害/得分飘字，跟随震屏一起位移

        // 连击边缘辉光：combo 越高，四边越亮（reducedFx 下关闭）
        if (!w.settings.reducedFx && w.comboKills > 1 && w.state == GameWorld.State.PLAYING) {
            int mult = Math.min(4, w.comboMultiplier());
            float pulse = 0.55f + 0.45f * (float) Math.sin(w.timeAlive * 7f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(10f);
            paint.setColor(((int) ((0x30 + 0x18 * mult) * pulse) << 24)
                    | (mult >= 3 ? 0xFFFFD166 : 0xFF7DF9FF));
            c.drawRect(5f, 5f, VW - 5f, VH - 5f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        // 顿帧滤镜：命中瞬间的冷白薄雾，比缓时更短促
        if (w.hitStopTimer > 0f) {
            float k = Math.min(1f, w.hitStopTimer / 0.08f);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(((int) (0x2A * k) << 24) | 0x00D8F4FF);
            c.drawRect(0, 0, VW, VH, paint);
        }

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

        // 新星冲击：向外扩散的暖白冲击环 + 整屏薄亮
        if (w.novaFlash > 0f) {
            float k = w.novaFlash;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(((int) (70 * k) << 24) | 0x00DFFFFF);
            c.drawRect(0, 0, VW, VH, paint);
            Ship s = w.ship;
            float t = 1f - k;
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 2; i++) {
                float rr = 60f + (720f + i * 200f) * Math.min(1f, t + i * 0.10f);
                paint.setStrokeWidth((16f - i * 8f) * k);
                paint.setColor(((int) ((0xB0 - i * 0x40) * k) << 24) | 0x00BFFFFF);
                c.drawCircle(s.x, s.y, rr, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        // 重力井登场：紫罗兰薄雾 + 从井口收缩的引力环（收缩而非扩散 = "吸进去"）
        if (w.gravityFlash > 0f) {
            float k = w.gravityFlash;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(((int) (48 * k) << 24) | 0x00B04DFF);
            c.drawRect(0, 0, VW, VH, paint);
            paint.setStyle(Paint.Style.STROKE);
            for (GravityWell g : w.gravityWells) {
                if (g == null || !g.active) continue;
                float rr = GravityWell.PULL_RADIUS * (0.35f + 0.65f * k);
                paint.setStrokeWidth(6f * k);
                paint.setColor(((int) (0xA0 * k) << 24) | 0x00C9A0FF);
                c.drawCircle(g.x, g.y, rr, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        drawUltFlash(c, w);

        drawHud(c, w);
        c.restore();
    }

    /**
     * 终极技释放：整屏金色薄亮 + 从船位向外炸开的三重棱环。
     * 和时停配合，按下的一瞬间要"看不清战场也没关系"，先让人意识到规则被打破了。
     */
    private void drawUltFlash(Canvas c, GameWorld w) {
        float k = w.ultFlash;
        if (k <= 0f) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(((int) (86 * k) << 24) | 0x00FFE08A);
        c.drawRect(0, 0, VW, VH, paint);
        Ship s = w.ship;
        float t = 1f - k;
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 3; i++) {
            float rr = 40f + (520f + i * 230f) * Math.min(1f, t + i * 0.08f);
            paint.setStrokeWidth((14f - i * 4f) * k);
            paint.setColor(((int) ((0xC0 - i * 0x30) * k) << 24)
                    | (i == 1 ? 0x007DF9FF : 0x00FFE08A));
            c.save();
            c.translate(s.x, s.y - 30f);
            c.rotate(30f * i + 90f * t);
            c.drawLine(-rr, 0f, rr, 0f, paint);
            c.drawLine(0f, -rr, 0f, rr, paint);
            c.restore();
        }
        paint.setStyle(Paint.Style.FILL);
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
            if (b.ult) {
                // 终极光矛：金色长枪 + 冷白核心，沿飞行方向拖出一道亮痕
                float ang = (float) Math.atan2(b.vy, b.vx);
                glow.setColor(0x66FFE08A);
                c.save();
                c.translate(b.x, b.y);
                c.rotate((float) Math.toDegrees(ang));
                c.drawCircle(0, 0, b.radius + 8f, glow);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFD166);
                shapePath.rewind();
                shapePath.moveTo(26f, 0f);
                shapePath.lineTo(-4f, b.radius * 0.55f);
                shapePath.lineTo(-20f, 0f);
                shapePath.lineTo(-4f, -b.radius * 0.55f);
                shapePath.close();
                c.drawPath(shapePath, paint);
                paint.setColor(0xFFFFFFFF);
                c.drawRoundRect(-16f, -2.5f, 20f, 2.5f, 2f, 2f, paint);
                c.restore();
            } else if (b.piercing && b.homing) {
                // 蜂群穿刺束（SWARM）：短细的紫青飞镖，带拖尾亮点
                float ang = (float) Math.atan2(b.vy, b.vx);
                c.save();
                c.translate(b.x, b.y);
                c.rotate((float) Math.toDegrees(ang));
                glow.setColor(0x55B08FFF);
                c.drawCircle(0, 0, 16f, glow);
                paint.setColor(0xFFCFB8FF);
                shapePath.rewind();
                shapePath.moveTo(16f, 0f);
                shapePath.lineTo(-6f, 6f);
                shapePath.lineTo(-14f, 0f);
                shapePath.lineTo(-6f, -6f);
                shapePath.close();
                c.drawPath(shapePath, paint);
                c.restore();
            } else if (b.piercing) {
                // laser / LANCE: vertical light beam, white core + cyan sheath
                float halfW = Math.max(3f, b.radius * 0.45f);
                glow.setColor(0x557DF9FF);
                c.drawCircle(b.x, b.y, b.radius + 6f, glow);
                paint.setColor(0xFF7DF9FF);
                c.drawRoundRect(b.x - halfW, b.y - b.len, b.x + halfW, b.y + 8f,
                        halfW, halfW, paint);
                paint.setColor(0xE6FFFFFF);
                float core = Math.max(1.5f, halfW * 0.4f);
                c.drawRoundRect(b.x - core, b.y - b.len + 6f, b.x + core, b.y + 4f,
                        core, core, paint);
            } else if (b.cluster) {
                // 集束弹（CLUSTER）：琥珀色弹体 + 四向定位齿，命中后炸开破片
                paint.setColor(0xFFFFB03A);
                c.drawCircle(b.x, b.y, b.radius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3f);
                paint.setColor(0x99FFE08A);
                c.drawCircle(b.x, b.y, b.radius + 5f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFE08A);
                float s = b.radius + 8f;
                c.drawRect(b.x - 2f, b.y - s, b.x + 2f, b.y - s + 6f, paint);
                c.drawRect(b.x - 2f, b.y + s - 6f, b.x + 2f, b.y + s, paint);
                c.drawRect(b.x - s, b.y - 2f, b.x - s + 6f, b.y + 2f, paint);
                c.drawRect(b.x + s - 6f, b.y - 2f, b.x + s, b.y + 2f, paint);
            } else if (b.fragment) {
                // 破片：白色小飞屑
                paint.setColor(0x33FFE08A);
                c.drawCircle(b.x, b.y, b.radius * 1.8f, paint);
                paint.setColor(0xFFFFE08A);
                c.drawCircle(b.x, b.y, b.radius, paint);
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
            } else if (b.bounces > 0) {
                // 弹跳弹：薄荷绿橡胶球，剩余反弹次数越多亮环越密
                paint.setColor(0xFF6BFFC8);
                c.drawCircle(b.x, b.y, b.radius, paint);
                paint.setColor(0xFFEAFFF6);
                c.drawCircle(b.x - b.radius * 0.28f, b.y - b.radius * 0.3f,
                        b.radius * 0.34f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f);
                for (int i = 1; i <= b.bounces; i++) {
                    paint.setColor(((0x90 - i * 0x22) << 24) | 0x006BFFC8);
                    c.drawCircle(b.x, b.y, b.radius + 5f * i, paint);
                }
                paint.setStyle(Paint.Style.FILL);
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
                case ORBIT:  color = 0xFFE8F1FF; label = "O"; break;
                case CHAIN:  color = 0xFFFFF06B; label = "Z"; break;
                case BONUS:  color = 0xFFB04DFF; label = "G"; break;
                case BOUNCE: color = 0xFF6BFFC8; label = "BN"; break;
                case NOVA:   color = 0xFFDFF6FF; label = "NV"; break;
                case NANITE: color = 0xFF5CFFB8; label = "NA"; break;
                case TURRET: color = 0xFFC0CCE8; label = "TR"; break;
                case GRAVITY: color = 0xFFC9A0FF; label = "GV"; break;
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
            paint.setTextSize(label.length() > 2 ? 17f : 23f);
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
            if (b.mine) {
                drawMine(c, b, w.timeAlive);
                continue;
            }
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

    /** 水雷：静止的带刺球，外圈危险环缓慢呼吸，核心忽明忽暗。 */
    private void drawMine(Canvas c, com.nebulastrike.game.core.EnemyBullet b, float time) {
        float r = b.radius;
        float hot = 0.5f + 0.5f * (float) Math.sin(time * 6f + b.x * 0.02f);
        glow.setColor(((int) (0x40 + 0x50 * hot) << 24) | 0x00FF5C7A);
        c.drawCircle(b.x, b.y, r * 2.1f, glow);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(((int) (0x60 + 0x60 * hot) << 24) | 0x00FF7089);
        c.drawCircle(b.x, b.y, r * 1.7f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF2A1020);
        for (int i = 0; i < 8; i++) {
            float a = (float) (i * Math.PI / 4.0);
            float sx = b.x + (float) Math.cos(a) * r * 1.45f;
            float sy = b.y + (float) Math.sin(a) * r * 1.45f;
            c.save();
            c.translate(sx, sy);
            c.rotate((float) Math.toDegrees(a));
            shapePath.rewind();
            shapePath.moveTo(6f, 0f);
            shapePath.lineTo(-4f, 4f);
            shapePath.lineTo(-4f, -4f);
            shapePath.close();
            paint.setColor(0xFF3A1428);
            c.drawPath(shapePath, paint);
            c.restore();
        }
        paint.setColor(0xFFB03A5A);
        c.drawCircle(b.x, b.y, r, paint);
        paint.setColor(((int) (0xB0 + 0x4F * hot) << 24) | 0x00FFD166);
        c.drawCircle(b.x, b.y, r * 0.45f, paint);
    }

    /**
     * 放电 / 治疗弧线：沿路径抖动的折线，按剩余寿命淡出。
     * kind 0 = 连锁闪电（青白色、振幅大、有亮芯），kind 1 = 医疗脉冲（绿色、细直）。
     */
    /**
     * 伤害与得分飘字：命中数字向上浮起并末段淡出，暴击用琥珀色大号、击杀得分用金色大号。
     * 低特效开关下整层不画（由 settings.reducedFx 决定）。
     */
    private void drawFloatTexts(Canvas c, GameWorld w) {
        if (w.settings.reducedFx) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setTextAlign(Paint.Align.CENTER);
        for (FloatText f : w.floatTexts) {
            if (!f.active) continue;
            int a = (int) (255 * f.alpha());
            if (a <= 0) continue;
            int len = formatInt(f.value);
            paint.setTextSize(f.bold ? 40f : 26f);
            paint.setFakeBoldText(f.bold);
            // 深色描边垫底，保证在任何背景上都读得清
            paint.setColor((a << 24) | 0x081018);
            c.drawText(textBuf, 0, len, f.x - 1.5f, f.y + 1.5f, paint);
            paint.setColor((a << 24) | f.color);
            c.drawText(textBuf, 0, len, f.x, f.y, paint);
        }
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** 把整数写进复用缓冲区并返回长度（飘字专用，零字符串分配）。 */
    private int formatInt(int v) {
        boolean neg = v < 0;
        int n = neg ? -v : v;
        int i = textBuf.length;
        do {
            i--;
            textBuf[i] = (char) ('0' + n % 10);
            n /= 10;
        } while (n > 0);
        if (neg) {
            i--;
            textBuf[i] = '-';
        }
        int len = textBuf.length - i;
        System.arraycopy(textBuf, i, textBuf, 0, len);
        return len;
    }

    private void drawArcs(Canvas c, GameWorld w) {
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < GameWorld.MAX_ARCS; i++) {
            float age = w.arcAge[i];
            if (age <= 0f) continue;
            boolean heal = w.arcKind[i] == 1;
            float k = Math.min(1f, age / (heal ? 0.34f : 0.42f));
            int rgb = heal ? 0x7CFF6B : 0x7DF9FF;
            float x1 = w.arcX1[i], y1 = w.arcY1[i];
            float x2 = w.arcX2[i], y2 = w.arcY2[i];
            float dx = x2 - x1, dy = y2 - y1;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 2f) continue;
            float nx = -dy / len, ny = dx / len;
            float amp = heal ? 6f : 24f;
            int segs = heal ? 4 : Math.max(4, (int) (len / 42f));
            shapePath.rewind();
            shapePath.moveTo(x1, y1);
            for (int s = 1; s < segs; s++) {
                float t = (float) s / segs;
                float off = amp * (float) Math.sin(s * 2.7f + i * 3.1f + w.timeAlive * 34f)
                        * (heal ? (float) Math.sin(t * Math.PI) : 1f);
                shapePath.lineTo(x1 + dx * t + nx * off, y1 + dy * t + ny * off);
            }
            shapePath.lineTo(x2, y2);
            paint.setStrokeWidth(heal ? 10f : 16f);
            paint.setColor(((int) (0x2A * k) << 24) | rgb);
            c.drawPath(shapePath, paint);
            paint.setStrokeWidth(heal ? 3f : 5f);
            paint.setColor(((int) (0xBB * k) << 24) | rgb);
            c.drawPath(shapePath, paint);
            if (!heal) {
                paint.setStrokeWidth(1.6f);
                paint.setColor(((int) (0xFF * k) << 24) | 0x00FFFFFF);
                c.drawPath(shapePath, paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    /** 环绕球：三颗沿轨道旋转的能量球，末端 2 秒淡出提示即将消失。 */
    private void drawOrbitSpheres(Canvas c, GameWorld w) {
        Ship s = w.ship;
        if (s.orbitTimer <= 0f || w.state == GameWorld.State.GAME_OVER) return;
        float fade = Math.min(1f, s.orbitTimer / 2f);
        // 轨道环
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(((int) (0x22 * fade) << 24) | 0x007DF9FF);
        c.drawCircle(s.x, s.y, Ship.ORBIT_RADIUS, paint);
        for (int i = 0; i < Ship.ORBIT_SPHERES; i++) {
            if (!s.orbitSphere(i, orbitPos)) break;
            float px = orbitPos[0], py = orbitPos[1];
            float wob = 1f + 0.12f * (float) Math.sin(w.timeAlive * 9f + i * 2.1f);
            paint.setStrokeWidth(2f);
            paint.setColor(((int) (0x2E * fade) << 24) | 0x007DF9FF);
            c.drawLine(s.x, s.y, px, py, paint);
            glow.setColor(((int) (0x66 * fade) << 24) | 0x007DF9FF);
            c.drawCircle(px, py, 26f * wob, glow);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(((int) (0xE6 * fade) << 24) | 0x00AFF6FF);
            c.drawCircle(px, py, 13f * wob, paint);
            paint.setColor(((int) (0xFF * fade) << 24) | 0x00FFFFFF);
            c.drawCircle(px, py, 5f * wob, paint);
            paint.setStyle(Paint.Style.STROKE);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawEnemies(Canvas c, GameWorld w) {
        for (Enemy e : w.enemies) {
            if (!e.active) continue;
            boolean flashing = e.flash > 0f;
            int base = palette(e.type.colorHint, flashing ? 255 : 235);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(base);
            float r = e.type.radius;
            if (e.elite) {
                // 精英机：琥珀色脉冲光环 + 描边，一眼认出"高血量高掉落"
                float pulse = 0.5f + 0.5f * (float) Math.sin(w.timeAlive * 5f + e.y * 0.02f);
                glow.setColor(((int) (0x48 + 0x40 * pulse) << 24) | 0x00FFB84D);
                c.drawCircle(e.x, e.y, r * 1.75f + 5f * pulse, glow);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.2f);
                paint.setColor(0xAAFFD166);
                c.drawCircle(e.x, e.y, r * 1.32f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(base);
            }
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
                case SUICIDER: {
                    // 自杀机：箭头对准飞船，核心高频闪烁 = "靠近即爆"
                    float ang = (float) Math.atan2(w.ship.y - e.y, w.ship.x - e.x);
                    c.save();
                    c.translate(e.x, e.y);
                    c.rotate((float) Math.toDegrees(ang) + 90f);
                    shapePath.rewind();
                    shapePath.moveTo(0, r);
                    shapePath.lineTo(-r * 0.9f, -r * 0.7f);
                    shapePath.lineTo(0, -r * 0.25f);
                    shapePath.lineTo(r * 0.9f, -r * 0.7f);
                    shapePath.close();
                    paint.setStyle(Paint.Style.FILL);
                    c.drawPath(shapePath, paint);
                    float hot = 0.5f + 0.5f * (float) Math.sin(w.timeAlive * 16f);
                    glow.setColor(((int) (0x60 + 0x80 * hot) << 24) | 0x00FF5C7A);
                    c.drawCircle(0f, r * 0.25f, r * 0.5f, glow);
                    c.restore();
                    break;
                }
                case SPLITTER:
                    // 分裂舱：左右两个舱叶 + 中线接缝，击毁后裂成两架无人机
                    splitterLobe(c, e.x, e.y, r, base, -1f);
                    splitterLobe(c, e.x, e.y, r, base, 1f);
                    paint.setColor(0xFF101528);
                    c.drawRect(e.x - 2.5f, e.y - r * 0.85f, e.x + 2.5f, e.y + r * 0.85f, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(2.5f);
                    paint.setColor(0x99BFFF4D);
                    c.drawCircle(e.x, e.y, r * 0.98f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    break;
                case HEALER: {
                    // 医疗艇：绿色十字 + 每次脉冲向外扩散的治疗环
                    c.drawCircle(e.x, e.y, r * 0.92f, paint);
                    paint.setColor(0xFF101528);
                    c.drawCircle(e.x, e.y, r * 0.72f, paint);
                    paint.setColor(0xFF7CFF6B);
                    float t = r * 0.52f, u = r * 0.18f;
                    c.drawRect(e.x - u, e.y - t, e.x + u, e.y + t, paint);
                    c.drawRect(e.x - t, e.y - u, e.x + t, e.y + u, paint);
                    float since = 1f - e.healFx;
                    if (e.healFx > 0f) {
                        float k = since;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(3f * e.healFx + 1f);
                        paint.setColor(((int) (0x88 * e.healFx) << 24) | 0x007CFF6B);
                        c.drawCircle(e.x, e.y, 40f + 220f * k, paint);
                        paint.setStyle(Paint.Style.FILL);
                    }
                    break;
                }
                case SNIPER: {
                    // 狙击塔：塔身 + 朝下的枪管，蓄力时画出瞄准线
                    boolean charging = e.isCharging();
                    if (charging) {
                        float aim = (float) Math.atan2(w.ship.y - e.y, w.ship.x - e.x);
                        float p = 1f - e.fireCd / 0.7f;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(1.5f + 2.5f * p);
                        paint.setColor(((int) (0x40 + 0x90 * p) << 24) | 0x00FF7089);
                        float reach = 900f;
                        c.drawLine(e.x, e.y + r * 0.7f,
                                e.x + (float) Math.cos(aim) * reach,
                                e.y + r * 0.7f + (float) Math.sin(aim) * reach, paint);
                        paint.setStyle(Paint.Style.FILL);
                    }
                    octagon(c, e.x, e.y, r, base);
                    paint.setColor(0xFF101528);
                    c.drawRect(e.x - r * 0.16f, e.y + r * 0.2f,
                            e.x + r * 0.16f, e.y + r * 1.15f, paint);
                    glow.setColor(charging ? 0xAAFF7089 : 0x55B08FFF);
                    c.drawCircle(e.x, e.y + r * 1.1f, charging ? 11f : 7f, glow);
                    paint.setColor(0xFFE8F1FF);
                    c.drawCircle(e.x, e.y - r * 0.2f, r * 0.22f, paint);
                    break;
                }
                case MINER: {
                    // 布雷机：腹部弹舱一张一合，倒计时到点就掉一枚水雷
                    hexagon(c, e.x, e.y, r, base);
                    float gate = 0.5f + 0.5f * (float) Math.sin(w.timeAlive * 4f);
                    paint.setColor(0xFF101528);
                    c.drawRect(e.x - r * 0.55f, e.y + r * 0.1f,
                            e.x + r * 0.55f, e.y + r * 0.35f + r * 0.25f * gate, paint);
                    glow.setColor(((int) (0x40 + 0x60 * (1f - Math.min(1f, e.mineCd / 1.2f))) << 24)
                            | 0x00FF5C7A);
                    c.drawCircle(e.x, e.y + r * 0.75f, r * 0.34f, glow);
                    paint.setColor(0xFFE8F1FF);
                    c.drawRect(e.x - r * 0.14f, e.y - r * 0.75f,
                            e.x + r * 0.14f, e.y - r * 0.35f, paint);
                    break;
                }
                case CARRIER: {
                    // 母舰：宽体甲板 + 两条弹射槽，两侧小艇剪影提示"会放无人机"
                    shapePath.rewind();
                    shapePath.moveTo(e.x, e.y - r * 0.85f);
                    shapePath.lineTo(e.x + r * 1.25f, e.y - r * 0.2f);
                    shapePath.lineTo(e.x + r * 0.85f, e.y + r * 0.75f);
                    shapePath.lineTo(e.x - r * 0.85f, e.y + r * 0.75f);
                    shapePath.lineTo(e.x - r * 1.25f, e.y - r * 0.2f);
                    shapePath.close();
                    c.drawPath(shapePath, paint);
                    paint.setColor(0xFF101528);
                    c.drawRect(e.x - r * 0.9f, e.y - r * 0.05f,
                            e.x + r * 0.9f, e.y + r * 0.2f, paint);
                    paint.setColor(0xFFFF8FD1);
                    float blink = 0.5f + 0.5f * (float) Math.sin(w.timeAlive * 7f + e.y);
                    c.drawCircle(e.x - r * 0.62f, e.y + r * 0.45f, r * 0.16f * blink + 3f, paint);
                    c.drawCircle(e.x + r * 0.62f, e.y + r * 0.45f, r * 0.16f * blink + 3f, paint);
                    break;
                }
                case SHIELDER: {
                    // 护盾机：机身外圈能量盾，盾值越低越透明、裂纹越密
                    octagon(c, e.x, e.y, r * 0.8f, base);
                    paint.setColor(0xFF101528);
                    c.drawCircle(e.x, e.y, r * 0.34f, paint);
                    float frac = e.shieldHp > 0f
                            ? e.shieldHp / Enemy.SHIELDER_SHIELD : 0f;
                    if (frac > 0f) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(4f);
                        paint.setColor(((int) (0x50 + 0xA0 * frac) << 24) | 0x007DF9FF);
                        c.drawCircle(e.x, e.y, r * 1.18f, paint);
                        paint.setStrokeWidth(2f);
                        paint.setColor(((int) (0x30 + 0x60 * frac) << 24) | 0x00AFF6FF);
                        float sh = r * 1.18f;
                        arcRect.set(e.x - sh, e.y - sh, e.x + sh, e.y + sh);
                        c.drawArc(arcRect, w.timeAlive * 120f % 360f, 150f * frac, false, paint);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(0xFF7DF9FF);
                        c.drawCircle(e.x, e.y - r * 0.1f, r * 0.2f * frac + 2f, paint);
                    }
                    break;
                }
            }
        }
    }

    /** 玩家炮台：底座 + 旋转炮管 + 耐久条，倒计时最后 2 秒开始闪烁。 */
    private void drawTurrets(Canvas c, GameWorld w) {
        for (Turret t : w.turrets) {
            if (t == null || !t.active) continue;
            float r = Turret.RADIUS;
            float fade = Math.min(1f, t.life / 2f);
            float blink = t.life < 2f
                    ? 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(w.timeAlive * 14f)) : 1f;
            glow.setColor(((int) (0x40 * fade * blink) << 24) | 0x00C0CCE8);
            c.drawCircle(t.x, t.y, r * 1.7f, glow);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(((int) (0xE6 * fade * blink) << 24) | 0x00323C58);
            hexagon(c, t.x, t.y, r, ((int) (0xF0 * fade * blink) << 24) | 0x0044506E);
            // 三根炮管朝上
            paint.setColor(((int) (0xF0 * fade) << 24) | 0x00AFF6FF);
            for (int i = -1; i <= 1; i++) {
                float ox = i * r * 0.45f;
                float reco = i == 0 && t.fireCd < 0.12f ? 5f : 0f;
                c.drawRect(t.x + ox - 3f, t.y - r * 1.35f + reco,
                        t.x + ox + 3f, t.y - r * 0.2f, paint);
            }
            // 耐久条
            float wpx = r * 2f;
            float frac = Math.max(0f, t.hp / Turret.MAX_HP);
            paint.setColor(((int) (0xC0 * fade) << 24) | 0x00151A2A);
            c.drawRect(t.x - wpx / 2f, t.y + r * 0.9f, t.x + wpx / 2f, t.y + r * 0.9f + 6f, paint);
            paint.setColor(((int) (0xF0 * fade) << 24)
                    | (frac > 0.5f ? 0x007CFF6B : frac > 0.2f ? 0x00FFD166 : 0x00FF5C7A));
            c.drawRect(t.x - wpx / 2f, t.y + r * 0.9f,
                    t.x - wpx / 2f + wpx * frac, t.y + r * 0.9f + 6f, paint);
        }
    }

    /**
     * 重力井：引力范围虚线环 + 双向旋转的吸积弧 + 黑核 + 库存点。
     * 库存点绕核排成一圈，吸得越满越接近"喷发倒计时"。
     */
    private void drawGravityWells(Canvas c, GameWorld w) {
        for (GravityWell g : w.gravityWells) {
            if (g == null || !g.active) continue;
            float fade = Math.min(1f, g.life / 1.6f);
            float blink = g.life < 1.6f
                    ? 0.5f + 0.5f * (0.5f + 0.5f * (float) Math.sin(w.timeAlive * 13f)) : 1f;
            float a = fade * blink;
            float r = GravityWell.RADIUS;
            // 引力边界：慢呼吸的细圈，告诉玩家"弹进了这个圈就回不去了"
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            float breathe = 0.5f + 0.5f * (float) Math.sin(w.timeAlive * 2.6f);
            paint.setColor(((int) ((0x22 + 0x14 * breathe) * a) << 24) | 0x00B04DFF);
            c.drawCircle(g.x, g.y, GravityWell.PULL_RADIUS * (0.94f + 0.06f * breathe), paint);
            // 吸积盘：两圈反向旋转的弧，一眼认出"在往里卷"
            for (int i = 0; i < 2; i++) {
                float rr = r * (1.45f + i * 0.5f);
                float spin = w.timeAlive * (i == 0 ? 150f : -110f) + i * 40f;
                paint.setStrokeWidth(6f - i * 2.5f);
                paint.setColor(((int) ((0xB0 - i * 0x40) * a) << 24)
                        | (i == 0 ? 0x00C9A0FF : 0x007DF9FF));
                c.drawArc(g.x - rr, g.y - rr, g.x + rr, g.y + rr, spin, 145f, false, paint);
            }
            // 黑核：外发光 + 纯黑中心 + 紫描边
            glow.setColor(((int) (0x6E * a) << 24) | 0x00B04DFF);
            c.drawCircle(g.x, g.y, r * 1.6f, glow);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(((int) (0xF5 * a) << 24) | 0x00080412);
            c.drawCircle(g.x, g.y, r, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(((int) (0xD0 * a) << 24) | 0x00C9A0FF);
            c.drawCircle(g.x, g.y, r, paint);
            // 库存点：吞了几发就亮几颗，绕核一圈慢慢攒满
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < g.stored; i++) {
                double ang = i / (double) GravityWell.CAP * Math.PI * 2.0 + w.timeAlive * 1.1;
                float ox = g.x + (float) Math.cos(ang) * (r + 13f);
                float oy = g.y + (float) Math.sin(ang) * (r + 13f);
                paint.setColor(((int) (0xE8 * a) << 24) | 0x00FFD34A);
                c.drawCircle(ox, oy, 3.6f, paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBoss(Canvas c, GameWorld w) {
        Boss b = w.boss;
        boolean flashing = b.flash > 0f;
        int hullColor = flashing ? 0xFFFFFFFF
                : b.phase == 3 ? 0xFFFF5C7A : b.phase == 2 ? 0xFFFFB84D : 0xFFB44DFF;
        float r = b.radius;

        // engine glow under the hull
        glow.setColor(0x55B44DFF);
        c.drawCircle(b.x, b.y + r * 0.8f, r * 0.65f, glow);

        // 横冲：舰体拖出速度线，落点方向画红色瞄准带
        if (b.isRamming()) {
            float dir = b.ramTargetX >= b.x ? 1f : -1f;
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 5; i++) {
                float oy = b.y - r * 0.7f + i * (r * 0.35f);
                float lag = 40f + i * 26f;
                paint.setColor(((0xA0 - i * 0x14) << 24) | 0x00FF5C7A);
                paint.setStrokeWidth(6f - i);
                c.drawLine(b.x - dir * 30f, oy, b.x - dir * lag, oy, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            glow.setColor(0x44FF5C7A);
            c.drawCircle(b.ramTargetX, b.y, r * 1.5f, glow);
        }

        // rotating outer ring
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(7f);
        paint.setColor(0x88B44DFF);
        float ringR = r * 1.22f;
        int nodes = 6 + b.tier * 2;
        c.save();
        c.translate(b.x, b.y);
        c.rotate(b.age * 18f);
        for (int i = 0; i < nodes; i++) {
            double a = Math.PI * 2.0 / nodes * i;
            float px = (float) Math.cos(a) * ringR;
            float py = (float) Math.sin(a) * ringR;
            c.drawCircle(px, py, Math.max(5f, r * 0.09f), paint);
        }
        c.restore();

        // 高等级舰体：额外装甲环，轮廓更复杂
        if (b.tier >= 2) {
            paint.setStrokeWidth(3f);
            paint.setColor(0x55B44DFF);
            c.drawCircle(b.x, b.y, r * 1.42f, paint);
        }

        // 侧翼装甲：从第二个 Boss 起展开，舰体形态逐级变化
        if (b.tier >= 1) {
            bossWing(c, b, -1f);
            bossWing(c, b, 1f);
        }

        // hull: layered hexagons + armor seams
        paint.setStyle(Paint.Style.FILL);
        hexagon(c, b.x, b.y, r, 0xFF2A2350);
        hexagon(c, b.x, b.y, r * 0.82f, hullColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x330A0C18);
        for (int i = 0; i < 3; i++) {
            c.save();
            c.translate(b.x, b.y);
            c.rotate(60f * i + b.age * 4f);
            c.drawLine(0f, -r * 0.72f, 0f, r * 0.72f, paint);
            c.restore();
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0A0C18);
        c.drawCircle(b.x, b.y, r * 0.42f, paint);
        paint.setColor(hullColor);
        c.drawCircle(b.x, b.y, r * 0.28f * (1f + 0.15f * (float) Math.sin(b.age * 5f)), paint);

        // 护盾外壳：只要有护盾发生器存活，核心伤害就打折，视觉上给一层六边形能量罩
        if (b.shielded()) {
            int gens = b.shieldGeneratorsAlive();
            float pulse = 0.5f + 0.5f * (float) Math.sin(b.age * 3.4f);
            glow.setColor(((int) (0x18 + 0x10 * pulse) * gens << 24) | 0x009DE8FF);
            c.drawCircle(b.x, b.y, r * 1.62f, glow);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(((int) (0x88 + 0x40 * pulse) << 24) | 0x009DE8FF);
            c.save();
            c.translate(b.x, b.y);
            c.rotate(-b.age * 12f);
            hexPath(0f, 0f, r * 1.55f);
            c.drawPath(shapePath, paint);
            c.rotate(b.age * 26f);
            paint.setStrokeWidth(2f);
            paint.setColor(((int) (0x55 + 0x30 * pulse) << 24) | 0x00AFF6FF);
            hexPath(0f, 0f, r * 1.34f);
            c.drawPath(shapePath, paint);
            c.restore();
            paint.setStyle(Paint.Style.FILL);
        }

        drawBossParts(c, w, b);

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
        paint.setTextSize(22f);
        paint.setTextAlign(Paint.Align.CENTER);
        String status = (b.staggerTimer > 0f ? "   DISARMED" : "")
                + (b.shielded() ? "   ARMORED" : "")
                + (b.isRamming() ? "   RAMMING" : "");
        c.drawText(b.title() + "  MK-" + (b.level + 1) + "   PHASE " + b.phase + "/3" + status,
                VW / 2f, by + 50f, paint);

        // component pips：形状区分部件种类（圆=机炮 方=激光 菱=护盾 三角=分裂舱），暗色=已摧毁
        float pipY = by + 68f;
        float pipGap = 24f;
        float pipX = VW / 2f - (b.partCount - 1) * pipGap / 2f;
        for (int i = 0; i < b.partCount; i++) {
            BossPart p = b.parts[i];
            paint.setColor(pipColor(p));
            float px = pipX + i * pipGap;
            switch (p.kind) {
                case LASER:
                    c.drawRect(px - 5f, pipY - 5f, px + 5f, pipY + 5f, paint);
                    break;
                case SHIELD: {
                    shapePath.rewind();
                    shapePath.moveTo(px, pipY - 6.5f);
                    shapePath.lineTo(px + 5.5f, pipY);
                    shapePath.lineTo(px, pipY + 6.5f);
                    shapePath.lineTo(px - 5.5f, pipY);
                    shapePath.close();
                    c.drawPath(shapePath, paint);
                    break;
                }
                case POD:
                    shapePath.rewind();
                    shapePath.moveTo(px, pipY - 6f);
                    shapePath.lineTo(px + 6f, pipY + 5f);
                    shapePath.lineTo(px - 6f, pipY + 5f);
                    shapePath.close();
                    c.drawPath(shapePath, paint);
                    break;
                default:
                    c.drawCircle(px, pipY, 5.5f, paint);
            }
        }
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setStyle(Paint.Style.FILL);
    }

    /** 侧翼装甲板（side = ±1 镜像），让高等级 Boss 的轮廓逐级变宽。 */
    private void bossWing(Canvas c, Boss b, float side) {
        float r = b.radius;
        c.save();
        c.translate(b.x, b.y);
        c.scale(side, 1f);
        shapePath.rewind();
        shapePath.moveTo(-r * 0.55f, -r * 0.52f);
        shapePath.lineTo(-r * 1.32f, -r * 0.12f);
        shapePath.lineTo(-r * 1.12f, r * 0.36f);
        shapePath.lineTo(-r * 0.62f, r * 0.50f);
        shapePath.lineTo(-r * 0.28f, r * 0.18f);
        shapePath.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF161B33);
        c.drawPath(shapePath, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(0x883A4C88);
        c.drawPath(shapePath, paint);
        paint.setStyle(Paint.Style.FILL);
        c.restore();
    }

    private void drawBossParts(Canvas c, GameWorld w, Boss b) {
        for (int i = 0; i < b.partCount; i++) {
            BossPart p = b.parts[i];
            float px = p.worldX(b), py = p.worldY(b);
            if (!p.active) {
                // wrecked pod: dark husk, cracks and a fading ember
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF1B2030);
                c.drawCircle(px, py, p.radius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f);
                paint.setColor(0xFF3A4158);
                c.drawCircle(px, py, p.radius, paint);
                paint.setColor(0xFF0A0C18);
                c.drawLine(px - p.radius * 0.7f, py - p.radius * 0.5f,
                        px + p.radius * 0.5f, py + p.radius * 0.6f, paint);
                c.drawLine(px + p.radius * 0.6f, py - p.radius * 0.6f,
                        px - p.radius * 0.3f, py + p.radius * 0.5f, paint);
                int emberAlpha = 40 + (int) (60 * Math.abs(Math.sin(w.timeAlive * 5f + i)));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor((emberAlpha << 24) | 0x00FF8A4D);
                c.drawCircle(px, py, p.radius * 0.35f, paint);
                continue;
            }
            float aim = (float) Math.atan2(w.ship.y - py, w.ship.x - px);
            int color = partColor(p);

            // mount
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF232A44);
            c.drawCircle(px, py, p.radius + 4f, paint);
            switch (p.kind) {
                case SHIELD:
                    bossShieldGenerator(c, px, py, p, color, w.timeAlive);
                    break;
                case POD:
                    bossMinionPod(c, px, py, p, color, w.timeAlive);
                    break;
                default: {
                    if (p.kind == BossPart.Kind.LASER) {
                        glow.setColor(0x557DF9FF);
                        c.drawCircle(px, py, p.radius * 1.9f, glow);
                    }
                    // barrel
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(4f, p.radius * 0.38f));
                    paint.setColor(color);
                    c.save();
                    c.translate(px, py);
                    c.rotate((float) Math.toDegrees(aim));
                    c.drawLine(0f, 0f, p.radius * 1.5f, 0f, paint);
                    c.restore();
                    // turret head
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(color);
                    c.drawCircle(px, py, p.radius * 0.62f, paint);
                    paint.setColor(0xFF0A0C18);
                    c.drawCircle(px, py, p.radius * 0.26f, paint);
                    break;
                }
            }

            // damage arc
            if (p.hp < p.maxHp) {
                float rr = p.radius * 1.35f;
                arcRect.set(px - rr, py - rr, px + rr, py + rr);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3f);
                paint.setColor(0x553A4158);
                c.drawArc(arcRect, 0f, 360f, false, paint);
                paint.setColor(color);
                c.drawArc(arcRect, -90f, 360f * Math.max(0f, p.hp / p.maxHp), false, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    /** 部件配色：机炮琥珀、激光青、护盾器淡蓝、分裂舱橙红；受击时闪白。 */
    private static int partColor(BossPart p) {
        if (p.flash > 0f) return 0xFFFFFFFF;
        switch (p.kind) {
            case LASER: return 0xFF7DF9FF;
            case SHIELD: return 0xFF9DE8FF;
            case POD: return 0xFFFF8A4D;
            default: return 0xFFFFB84D;
        }
    }

    private static int pipColor(BossPart p) {
        return !p.active ? 0xFF2A2F42 : partColor(p);
    }

    /** 护盾发生器：旋转晶体 + 向上发射的护盾弧，摧毁后外壳能量罩消失。 */
    private void bossShieldGenerator(Canvas c, float px, float py, BossPart p,
                                     int color, float time) {
        glow.setColor(0x559DE8FF);
        c.drawCircle(px, py, p.radius * 1.8f, glow);
        c.save();
        c.translate(px, py);
        c.rotate(time * 55f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        shapePath.rewind();
        for (int i = 0; i < 3; i++) {
            double a = Math.PI * 2.0 / 3.0 * i;
            float rx = (float) Math.cos(a) * p.radius * 0.85f;
            float ry = (float) Math.sin(a) * p.radius * 0.85f;
            shapePath.moveTo(rx, ry);
            shapePath.lineTo((float) Math.cos(a + 1.0) * p.radius * 0.5f,
                    (float) Math.sin(a + 1.0) * p.radius * 0.5f);
            shapePath.lineTo((float) Math.cos(a - 1.0) * p.radius * 0.5f,
                    (float) Math.sin(a - 1.0) * p.radius * 0.5f);
            shapePath.close();
        }
        c.drawPath(shapePath, paint);
        c.restore();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(color);
        c.drawCircle(px, py, p.radius * 0.55f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0A0C18);
        c.drawCircle(px, py, p.radius * 0.24f, paint);
    }

    /** 分裂舱：椭圆舱体 + 中线舱门，投放僚机时舱门张开并冒光。 */
    private void bossMinionPod(Canvas c, float px, float py, BossPart p,
                               int color, float time) {
        float breathe = 1f + 0.08f * (float) Math.sin(time * 4.5f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        shapePath.rewind();
        shapePath.moveTo(px, py - p.radius * 1.05f * breathe);
        shapePath.quadTo(px + p.radius * 1.15f, py - p.radius * 0.3f,
                px, py + p.radius * 1.05f * breathe);
        shapePath.quadTo(px - p.radius * 1.15f, py + p.radius * 0.3f,
                px, py - p.radius * 1.05f * breathe);
        shapePath.close();
        c.drawPath(shapePath, paint);
        // 舱门缝
        paint.setColor(0xFF0A0C18);
        c.drawRect(px - 2f, py - p.radius, px + 2f, py + p.radius, paint);
        // 投放口（下方发光）
        glow.setColor(0x88FF8A4D);
        c.drawCircle(px, py + p.radius * 0.8f, p.radius * 0.42f * breathe, glow);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0xAAFFE08A);
        c.drawCircle(px, py, p.radius * 0.72f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBossLasers(Canvas c, GameWorld w) {
        for (BossLaser l : w.bossLasers) {
            if (!l.active) continue;
            if (!l.firing) {
                float p = l.warmupProgress();
                int alpha = (int) (45 + 120 * p);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f + 3f * p);
                paint.setColor((alpha << 24) | 0x00FF5C7A);
                c.drawLine(l.x, l.y, l.endX(), l.endY(), paint);
                paint.setStyle(Paint.Style.FILL);
                glow.setColor((int) (60 + 80 * p) << 24 | 0x00FF5C7A);
                c.drawCircle(l.x, l.y, 14f + 10f * p, glow);
            } else {
                float k = Math.max(0f, l.beam / l.totalBeam);
                int a = (int) (200 * Math.min(1f, k * 1.8f));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(30f);
                paint.setColor((int) (a * 0.25f) << 24 | 0x00FF5C7A);
                c.drawLine(l.x, l.y, l.endX(), l.endY(), paint);
                paint.setStrokeWidth(12f);
                paint.setColor((a << 24) | 0x00FF5C7A);
                c.drawLine(l.x, l.y, l.endX(), l.endY(), paint);
                paint.setStrokeWidth(4f);
                paint.setColor((a << 24) | 0x00FFFFFF);
                c.drawLine(l.x, l.y, l.endX(), l.endY(), paint);
                paint.setStyle(Paint.Style.FILL);
                glow.setColor(0xAAFF5C7A);
                c.drawCircle(l.x, l.y, 22f, glow);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawShip(Canvas c, GameWorld w) {
        Ship s = w.ship;
        if (w.state == GameWorld.State.GAME_OVER) return;
        // 重生保护期内战舰常亮并罩护罩；普通无敌仍然闪烁
        boolean grace = w.respawnGrace > 0f;
        if (s.invulnerable && !grace && (int) (w.timeAlive * 12) % 2 == 0) return;

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

        // 相位冲刺残影：沿冲刺轨迹的渐隐舰影 + 冷色光环
        if (s.dashTrailCount > 0) {
            for (int i = 0; i < s.dashTrailCount; i++) {
                float k = (i + 1f) / s.dashTrailCount;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(((int) (70 * k) << 24) | 0x007DF9FF);
                c.save();
                c.translate(s.dashTrailX[i], s.dashTrailY[i]);
                float sc = 0.5f + 0.45f * k;
                c.scale(sc, sc);
                hull(c, 0f, 0f);
                c.restore();
            }
            glow.setColor(s.dashTimer > 0f ? 0x997DF9FF : 0x447DF9FF);
            c.drawCircle(s.x, s.y, Ship.RADIUS * 1.9f, glow);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f);
            paint.setColor(0x99AFF6FF);
            c.drawCircle(s.x, s.y, Ship.RADIUS * 1.5f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        // 弹反冲击环：冲刺中打飞敌弹的瞬间炸开一圈冷白环，环外扩同时淡出
        if (w.parryFlash > 0f) {
            float k = w.parryFlash;
            float grow = 1f - k;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f + 5f * k);
            paint.setColor(((int) (0xE0 * k) << 24) | 0xB0FFF6);
            c.drawCircle(s.x, s.y, Ship.RADIUS * (1.3f + 2.1f * grow), paint);
            glow.setColor(((int) (0x55 * k) << 24) | 0xB0FFF6);
            c.drawCircle(s.x, s.y, Ship.RADIUS * (1.2f + 1.4f * grow), glow);
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

        // shield bubble：每层一个同心环，层数越多越亮
        if (s.hasShield) {
            int layers = Math.max(1, s.shieldLayers);
            float pulse = 1f + 0.03f * (float) Math.sin(w.timeAlive * 6f);
            for (int i = 0; i < layers; i++) {
                float r = Ship.RADIUS * (2.1f + i * 0.34f) * pulse;
                glow.setColor(i == 0 ? 0x447CFF6B : 0x2E7CFF6B);
                c.drawCircle(s.x, s.y, r, glow);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(i == 0 ? 3f : 2f);
                paint.setColor(i == 0 ? 0xFF7CFF6B : 0xB37CFF6B);
                c.drawCircle(s.x, s.y, r, paint);
            }
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

        // 圣物僚机（ETERNAL_WING）：常驻僚机用金色描边区分
        if (w.hasRelic(Relic.ETERNAL_WING)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(0xAAFFD166);
            for (int i = -1; i <= 1; i += 2) {
                c.drawCircle(s.x + i * 52f, s.y + 2f, 16f, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        // 纳米修复：舰身环绕的绿色微粒带，说明火力正在自动升级
        if (s.nanoTimer > 0f) {
            float fade = Math.min(1f, s.nanoTimer / 2f);
            for (int i = 0; i < 6; i++) {
                float a = (w.timeAlive * 2.4f + i * (float) (Math.PI / 3.0f));
                float rr = Ship.RADIUS * (1.25f + 0.12f * (float) Math.sin(a * 3f));
                glow.setColor(((int) (0x70 * fade) << 24) | 0x005CFFB8);
                c.drawCircle(s.x + (float) Math.cos(a) * rr,
                        s.y + (float) Math.sin(a) * rr * 0.85f, 7f, glow);
            }
        }

        // 重生保护罩：蓝色气泡 + 逆时针收拢的倒计时弧，剩余 1 秒开始闪烁
        if (grace) {
            float total = Math.max(0.01f, w.settings.difficulty.respawnInvuln);
            float frac = Math.min(1f, w.respawnGrace / total);
            float blink = w.respawnGrace < 1f
                    ? 0.55f + 0.45f * (float) Math.sin(w.timeAlive * 16f) : 1f;
            float rr = Ship.RADIUS * 2.6f;
            glow.setColor(((int) (0x38 * frac * blink) << 24) | 0x007DF9FF);
            c.drawCircle(s.x, s.y, rr * 1.15f, glow);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(((int) (0xCC * frac * blink) << 24) | 0x009BE8FF);
            c.drawCircle(s.x, s.y, rr, paint);
            paint.setStrokeWidth(6f);
            paint.setColor(((int) (0xFF * blink) << 24) | 0x00E8F1FF);
            arcRect.set(s.x - rr, s.y - rr, s.x + rr, s.y + rr);
            c.drawArc(arcRect, -90f, 360f * frac, false, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(24f);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(((int) (0xAA * blink) << 24) | 0x00E8F1FF);
            c.drawText("无敌 " + (int) Math.ceil(w.respawnGrace) + "s",
                    s.x, s.y - rr - 14f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    // ---- HUD & screens ----
    /** 武器配色：基础三色沿用道具色，融合武器用邻近冷/暖色区分。 */
    private static int weaponColor(WeaponType type) {
        switch (type) {
            case LASER: return 0xFF7DF9FF;
            case MISSILE: return 0xFFFF8FD1;
            case LANCE: return 0xFFB0FFF6;
            case CLUSTER: return 0xFFFFB03A;
            case SWARM: return 0xFFCFB8FF;
            case BOUNCE: return 0xFF6BFFC8;
            default: return 0xFFFFD166;
        }
    }

    private void drawHud(Canvas c, GameWorld w) {
        switch (w.state) {
            case MENU:
                if (w.cheatOpen) drawCheats(c, w);
                else if (w.codexOpen) drawCodex(c, w);
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

        // 异变波芯片：名字 + 剩余时间条，最后 3 秒闪烁提示"窗口快关了，抓紧刷"
        if (w.mutator.isActive()) {
            float frac = Math.max(0f, Math.min(1f, w.mutatorTimer / GameWorld.MUTATOR_DURATION));
            float blink = w.mutatorTimer < 3f
                    ? 0.55f + 0.45f * Math.abs((float) Math.sin(w.timeAlive * 7f)) : 1f;
            int rgb = w.mutator.colorRgb;
            // 顶部一条异变色边：不读文字也能感觉到"这一段不是平时"
            paint.setColor(((int) (0x90 * blink) << 24) | rgb);
            c.drawRect(0f, 0f, VW, 5f, paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(26f);
            paint.setFakeBoldText(true);
            paint.setColor(((int) (0xEE * blink) << 24) | rgb);
            c.drawText("异变 · " + w.mutator.label, VW / 2f + 100f, 150f, paint);
            paint.setFakeBoldText(false);
            paint.setColor((0x66 << 24) | 0x00121A2E);
            c.drawRoundRect(VW / 2f - 20f, 162f, VW / 2f + 220f, 170f, 4f, 4f, paint);
            paint.setColor(((int) (0xD8 * blink) << 24) | rgb);
            c.drawRoundRect(VW / 2f - 20f, 162f,
                    VW / 2f - 20f + 240f * frac, 170f, 4f, 4f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

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

        // weapon loadout: 基础武器可叠 3 种，融合武器额外叠加（最多 6 行）
        float statusY = 112f;
        for (WeaponType type : WeaponType.values()) {
            if (type == WeaponType.VULCAN) continue;
            if (!w.ship.isWeaponEquipped(type)) continue;
            float timer = w.ship.timerOf(type);
            if (timer <= 0f && w.ship.weapon != type) continue;
            int color = weaponColor(type);
            String name = type.label();
            if (type.isFused()) name = "◆ " + name;
            int stack = w.ship.stackOf(type);
            paint.setColor(color);
            paint.setTextSize(25f);
            c.drawText(name + (stack > 1 ? " x" + stack : ""), 40f, statusY, paint);
            float frac = Math.min(1f, timer / Ship.WEAPON_DURATION);
            paint.setColor((color & 0x00FFFFFF) | 0x33000000);
            c.drawRect(40f, statusY + 11f, 40f + 190f, statusY + 19f, paint);
            paint.setColor(color);
            c.drawRect(40f, statusY + 11f, 40f + 190f * frac, statusY + 19f, paint);
            statusY += 38f;
        }
        if (w.ship.orbitTimer > 0f) {
            statusBar(c, "ORBIT", 0xFFAFF6FF,
                    Math.min(1f, w.ship.orbitTimer / Ship.ORBIT_DURATION), statusY);
            statusY += 38f;
        }
        // 火力等级 pips（雷电式）：右上角 3 格，放在残机图标下方避免重叠
        for (int i = 0; i < Ship.MAX_POWER; i++) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(i < w.ship.powerLevel ? 0xFFFFB03A : 0x33FFB03A);
            c.drawCircle(VW - 46f - i * 30f, 96f, 9f, paint);
        }
        if (w.ship.hasShield) {
            int layers = Math.max(1, w.ship.shieldLayers);
            paint.setColor(0xFF7CFF6B);
            paint.setTextSize(25f);
            c.drawText(layers > 1 ? "SHIELD x" + layers : "SHIELD", 40f, statusY, paint);
            statusY += 42f;
        }

        // 速射 / 双倍分数 状态条
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
            statusY += 38f;
        }
        if (w.ship.nanoTimer > 0f) {
            statusBar(c, "NANITE", 0xFF5CFFB8,
                    w.ship.nanoTimer / Ship.NANITE_DURATION, statusY);
            statusY += 38f;
        }
        int turretCount = 0;
        float turretFrac = 0f;
        for (Turret t : w.turrets) {
            if (t != null && t.active) {
                turretCount++;
                turretFrac += Math.min(1f, t.life / Turret.LIFE);
            }
        }
        if (turretCount > 0) {
            statusBar(c, "TURRET x" + turretCount, 0xFFC0CCE8,
                    turretFrac / turretCount, statusY);
            statusY += 38f;
        }
        // 重力井：进度条 = 库存/上限，一眼看出"快喷了"
        int wellCount = 0;
        float wellFrac = 0f;
        for (GravityWell g : w.gravityWells) {
            if (g == null || !g.active) continue;
            wellCount++;
            wellFrac += Math.min(1f, (float) g.stored / GravityWell.CAP);
        }
        if (wellCount > 0) {
            statusBar(c, "GRAVITY x" + wellCount, 0xFFC9A0FF, wellFrac / wellCount, statusY);
        }

        drawRelicChips(c, w);

        // 擦弹充能：固定放左下，避免和左上角的状态条抢位置
        if (w.ship.overdriveTimer > 0f) {
            statusBar(c, "OVERDRIVE", 0xFFFFD166,
                    w.ship.overdriveTimer / Ship.OVERDRIVE_DURATION, 1200f);
        } else {
            statusBar(c, "GRAZE", 0xFFFF8FD1, w.grazeCharge, 1200f);
        }
        // 截弹计数：紧挨充能条右侧，只在有成绩后出现，点掉一发时短暂放大
        if (w.bulletsShotDown > 0) {
            float pulse = w.breakPulse;
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(24f + 8f * pulse);
            paint.setColor(pulse > 0.05f ? 0xFFFFFFFF : 0xB07DF9FF);
            c.drawText("截弹 " + w.bulletsShotDown, 240f, 1202f + 3f * pulse, paint);
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
        drawBanners(c, w);
    }

    /** 已持有圣物的角标：右上角竖排金色芯片，鼠标点不到，只做状态提示。 */
    private void drawRelicChips(Canvas c, GameWorld w) {
        if (w.relicMask == 0) return;
        float y = 150f;
        paint.setTextAlign(Paint.Align.CENTER);
        for (Relic r : Relic.values()) {
            if (!w.hasRelic(r)) continue;
            int rgb = r.colorRgb();
            glow.setColor((0x44 << 24) | rgb);
            c.drawCircle(VW - 46f, y + 8f, 30f, glow);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((0xE0 << 24) | 0x00121A2E);
            c.drawCircle(VW - 46f, y + 8f, 21f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f);
            paint.setColor((0xCC << 24) | rgb);
            c.drawCircle(VW - 46f, y + 8f, 21f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((0xFF << 24) | rgb);
            paint.setTextSize(22f);
            paint.setFakeBoldText(true);
            c.drawText(r.badge(), VW - 46f, y + 16f, paint);
            paint.setFakeBoldText(false);
            y += 48f;
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** 中央提示：突袭奖励波 / 武器融合解锁，两者都用短促的缩放动画。 */
    private void drawBanners(Canvas c, GameWorld w) {
        paint.setTextAlign(Paint.Align.CENTER);
        if (w.mutatorBanner > 0f && w.mutator.isActive()) {
            float k = Math.min(1f, w.mutatorBanner / 0.4f);
            float in = Math.min(1f, (3.2f - w.mutatorBanner) / 0.35f);
            float s = 0.72f + 0.28f * in;
            int rgb = w.mutator.colorRgb;
            paint.setTextSize(58f * s);
            paint.setFakeBoldText(true);
            paint.setColor(((int) (0xFF * k) << 24) | rgb);
            c.drawText("异变 · " + w.mutator.label, VW / 2f, 216f, paint);
            paint.setTextSize(25f * s);
            paint.setColor(((int) (0xD8 * k) << 24) | 0x00E8F1FF);
            c.drawText(w.mutator.blurb, VW / 2f, 256f, paint);
            paint.setFakeBoldText(false);
        }
        if (w.rushBanner > 0f) {
            float k = Math.min(1f, w.rushBanner / 0.35f);
            float in = Math.min(1f, (2.4f - w.rushBanner) / 0.25f);
            float s = 0.7f + 0.3f * in;
            paint.setTextSize(56f * s);
            paint.setFakeBoldText(true);
            paint.setColor(((int) (0xFF * k) << 24) | 0x00B04DFF);
            c.drawText("RUSH WAVE", VW / 2f, 300f, paint);
            paint.setTextSize(26f * s);
            paint.setColor(((int) (0xCC * k) << 24) | 0x00FFE08A);
            c.drawText("护航编队 + 奖励补给", VW / 2f, 342f, paint);
            paint.setFakeBoldText(false);
        }
        if (w.relicBanner > 0f && w.lastRelic != null) {
            float k = Math.min(1f, w.relicBanner / 0.4f);
            float in = Math.min(1f, (3.4f - w.relicBanner) / 0.3f);
            float s = 0.75f + 0.25f * in;
            int rgb = w.lastRelic.colorRgb();
            paint.setTextSize(52f * s);
            paint.setFakeBoldText(true);
            paint.setColor(((int) (0xFF * k) << 24) | rgb);
            c.drawText("圣物 · " + w.lastRelic.label(), VW / 2f, 500f, paint);
            paint.setTextSize(26f * s);
            paint.setColor(((int) (0xD8 * k) << 24) | 0x00E8F1FF);
            c.drawText(w.lastRelic.blurb(), VW / 2f, 542f, paint);
            paint.setFakeBoldText(false);
        }
        if (w.fusionFlash > 0f && w.lastFusion != null) {
            float k = Math.min(1f, w.fusionFlash / 0.3f);
            float s = 1f + 0.25f * (1f - Math.min(1f, w.fusionFlash / 0.9f));
            int color = weaponColor(w.lastFusion);
            paint.setTextSize(46f * s);
            paint.setFakeBoldText(true);
            paint.setColor(((int) (0xFF * k) << 24) | (color & 0xFFFFFF));
            c.drawText("FUSION · " + w.lastFusion.label(), VW / 2f, 420f, paint);
            paint.setFakeBoldText(false);
        }
        paint.setTextAlign(Paint.Align.LEFT);
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

        // 冲刺键：环形充能进度 + 闪电符号，满能量时两格点亮
        Ship s = w.ship;
        Ui.Rect d = Ui.dash(w.settings.leftHanded);
        boolean ready = s.canDash();
        float frac = Math.max(0f, Math.min(1f, s.dashCharge / Ship.DASH_MAX_CHARGE));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ready ? 0x337DF9FF : 0x1A182040);
        c.drawCircle(d.cx(), d.cy(), 46f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(ready ? 0x99AFF6FF : 0x448FA3C8);
        c.drawCircle(d.cx(), d.cy(), 52f, paint);
        if (frac < 0.999f) {
            arcRect.set(d.cx() - 52f, d.cy() - 52f, d.cx() + 52f, d.cy() + 52f);
            paint.setStrokeWidth(5f);
            paint.setColor(ready ? 0xFF7DF9FF : 0x888FA3C8);
            c.drawArc(arcRect, -90f, 360f * frac, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        float boltA = ready ? 1f : 0.45f;
        paint.setColor(((int) (0xFF * boltA) << 24) | 0x00E8F1FF);
        shapePath.rewind();
        float bx = d.cx(), by = d.cy() - 4f;
        shapePath.moveTo(bx + 9f, by - 22f);
        shapePath.lineTo(bx - 12f, by + 2f);
        shapePath.lineTo(bx - 2f, by + 2f);
        shapePath.lineTo(bx - 9f, by + 22f);
        shapePath.lineTo(bx + 12f, by - 2f);
        shapePath.lineTo(bx + 2f, by - 2f);
        shapePath.close();
        c.drawPath(shapePath, paint);
        // 能量格：DASH_MAX_CHARGE 格，取整点亮
        int full = (int) Math.floor(s.dashCharge);
        for (int i = 0; i < (int) Ship.DASH_MAX_CHARGE; i++) {
            paint.setColor(i < full ? 0xFFFFF06B : 0x33FFF06B);
            c.drawCircle(d.cx() - 12f + i * 24f, d.cy() + 36f, 5f, paint);
        }
        drawUltButton(c, w);
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * 终极技按键：环形槽量 + 三档刻度。攒满时金边脉冲并显示 READY，
     * 释放后（时停仍在生效）整圈变冷白，提醒"这一波是你买来的输出窗口"。
     */
    private void drawUltButton(Canvas c, GameWorld w) {
        Ui.Rect u = Ui.ult(w.settings.leftHanded);
        Ultimate ult = w.ult;
        float frac = Math.max(0f, Math.min(1f, ult.charge / Ultimate.CAP));
        boolean ready = ult.full();
        boolean active = ult.stasisTimer > 0f;
        float pulse = ready ? 0.5f + 0.5f * (float) Math.sin(w.timeAlive * 7f) : 0f;
        int base = active ? 0xE8F1FF : 0xFFD166;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ready || active
                ? (((int) (0x30 + 0x40 * pulse)) << 24) | base : 0x1A182040);
        c.drawCircle(u.cx(), u.cy(), 46f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(ready ? ((int) (0xAA + 0x55 * pulse)) << 24 | base : 0x448FA3C8);
        c.drawCircle(u.cx(), u.cy(), 52f, paint);
        if (frac < 0.999f) {
            arcRect.set(u.cx() - 52f, u.cy() - 52f, u.cx() + 52f, u.cy() + 52f);
            paint.setStrokeWidth(5f);
            paint.setColor(frac > 0f ? 0xFF7DF9FF : 0x338FA3C8);
            c.drawArc(arcRect, -90f, 360f * frac, false, paint);
        }

        // 中心符号：八角星（大招 = 全屏点名），未满时半透明
        paint.setStyle(Paint.Style.FILL);
        float a = ready || active ? 1f : 0.45f;
        paint.setColor(((int) (0xFF * a) << 24) | base);
        float ox = u.cx(), oy = u.cy() - 4f;
        shapePath.rewind();
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4f;
            float r = (i & 1) == 0 ? 20f : 8f;
            float px = (float) (ox + Math.cos(ang) * r);
            float py = (float) (oy + Math.sin(ang) * r);
            if (i == 0) shapePath.moveTo(px, py);
            else shapePath.lineTo(px, py);
        }
        shapePath.close();
        c.drawPath(shapePath, paint);

        // 三档刻度：34 / 67 / 100，让"提前放"和"憋满放"的差别一眼可见
        for (int i = 0; i < 3; i++) {
            float threshold = i == 0 ? Ultimate.MIN_CAST : i == 1 ? Ultimate.TIER_TWO : Ultimate.CAP;
            paint.setColor(ult.charge >= threshold ? 0xFFFFF06B : 0x33FFF06B);
            c.drawCircle(u.cx() - 16f + i * 16f, u.cy() + 36f, 5f, paint);
        }

        paint.setTextSize(15f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(ready ? 0xFFFFE08A : 0x888FA3C8);
        c.drawText(ready ? "READY" : active ? "STASIS" : "ULT", u.cx(), u.cy() - 60f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setStyle(Paint.Style.FILL);
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
        button(c, Ui.CODEX, "CODEX  " + w.settings.codexCompletion()
                + "/" + w.settings.codexTotal(), 0xFF182040, 0xFFAFF6FF, false, 30f);

        paint.setTextSize(28f);
        paint.setColor(0xFFFFD166);
        c.drawText("BEST  " + w.bestScore + "    " + w.settings.difficulty.label()
                + "难度    v" + w.currentVersion, VW / 2f, 800f, paint);

        drawUpdateButton(c, w);
        drawDualBoard(c, w, VW / 2f - 165f, 830f, 330f, 44f, 22f);

        paint.setTextSize(26f);
        paint.setColor(0x668FA3C8);
        // 玩法提示轮播：每 4.5 秒换一条，淡入淡出，用来教新机制而不占按钮位
        String[] hints = {
                "按住屏幕任意位置滑动即可操控飞船 · 自动开火 · 炸弹键可切左手",
                "擦弹攒 OVERDRIVE · 过载期间暴击率翻倍、分数翻倍",
                "导弹 / 集束 / 长枪这类重弹可以点爆敌弹 · 但这一发就打不出伤害了",
                "冲刺途中贴身撞上敌弹会把它弹反成友军火力 · 连反多得分成倍涨",
                "打到 45 秒起会随机出现异变波 · 规则变狠但这段时间得分加成最高 1.6 倍",
                "GV 重力井会把附近的敌弹吸进去 · 吸满 12 发朝上方喷还成自己的子弹",
                "ULT 终极槽靠击坠 / 擦弹 / 弹反 / 吞弹积累 · 攒满按右下角大招键，全屏点名",
                "同类道具叠加不替换 · 集齐两把基础武器自动融合出第三把",
        };
        float cycle = w.stateTimer % (hints.length * 4.5f);
        int hintIndex = (int) (cycle / 4.5f);
        float inHint = cycle - hintIndex * 4.5f;
        float fade = Math.min(1f, Math.min(inHint, 4.5f - inHint) / 0.45f);
        paint.setColor(((int) (0xAA * fade) << 24) | 0x8FA3C8);
        c.drawText(hints[Math.min(hintIndex, hints.length - 1)], VW / 2f, 1185f, paint);
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
        c.drawText("SETTINGS", VW / 2f, 330f, paint);

        paint.setTextSize(22f);
        paint.setColor(0xFF8FA3C8);
        c.drawText("难度档位 · 影响掉率、重生保护与坠机惩罚", VW / 2f, 380f, paint);

        Difficulty diff = w.settings.difficulty;
        diffSegment(c, Ui.DIFF_CASUAL, Difficulty.CASUAL, diff);
        diffSegment(c, Ui.DIFF_STANDARD, Difficulty.STANDARD, diff);
        diffSegment(c, Ui.DIFF_HARDCORE, Difficulty.HARDCORE, diff);
        paint.setTextSize(22f);
        paint.setColor(0xFFC8D6F0);
        c.drawText(diff.blurb(), VW / 2f, 486f, paint);

        toggle(c, Ui.TOGGLE_SOUND, "SOUND", w.settings.sound);
        toggle(c, Ui.TOGGLE_PARTICLES, "PARTICLES", w.settings.particles);
        toggle(c, Ui.TOGGLE_HAPTICS, "HAPTICS", w.settings.haptics);
        toggle(c, Ui.TOGGLE_REDUCED_FX, "LOW FX", w.settings.reducedFx);
        toggle(c, Ui.TOGGLE_LEFT_HAND, "LEFT HAND", w.settings.leftHanded);
        button(c, Ui.SETTINGS_BACK, "BACK", 0xFF182040, 0xFF8FA3C8, false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** 难度分段按钮：选中格实心高亮，未选中只留描边。 */
    private void diffSegment(Canvas c, Ui.Rect r, Difficulty d, Difficulty chosen) {
        boolean active = d == chosen;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? 0xFF27405F : 0x22FFFFFF);
        c.drawRoundRect(r.x, r.y, r.x + r.w, r.y + r.h, 12f, 12f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(active ? 3f : 1.5f);
        paint.setColor(active ? diffColor(d) : 0x556B7A99);
        c.drawRoundRect(r.x, r.y, r.x + r.w, r.y + r.h, 12f, 12f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? diffColor(d) : 0xFF8FA3C8);
        paint.setTextSize(27f);
        paint.setFakeBoldText(active);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(d.label(), r.cx(), r.cy() + 10f, paint);
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static int diffColor(Difficulty d) {
        switch (d) {
            case CASUAL: return 0xFF7CFF6B;
            case HARDCORE: return 0xFFFF5C7A;
            default: return 0xFFAFF6FF;
        }
    }

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

    /**
     * 图鉴页：左栏敌机、右栏 Boss。未发现的项目只画轮廓与 "?"，
     * 已记录的项目给出数值与行为说明，Boss 额外显示击坠次数。
     */
    private void drawCodex(Canvas c, GameWorld w) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xCC000000);
        c.drawRect(0, 0, VW, VH, paint);
        paint.setColor(0xE60A0C18);
        c.drawRoundRect(80f, 150f, 820f, 1380f, 26f, 26f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x66AFF6FF);
        c.drawRoundRect(80f, 150f, 820f, 1380f, 26f, 26f, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(56f);
        paint.setColor(0xFFAFF6FF);
        c.drawText("CODEX 图鉴", VW / 2f, 226f, paint);

        // 完成度条
        int seen = w.settings.codexCompletion(), total = Math.max(1, w.settings.codexTotal());
        float barX = 200f, barW = 500f;
        paint.setTextSize(24f);
        paint.setColor(0xFF8FA3C8);
        c.drawText("已记录 " + seen + " / " + total, VW / 2f, 268f, paint);
        paint.setColor(0x33FFFFFF);
        c.drawRect(barX, 278f, barX + barW, 288f, paint);
        paint.setColor(0xFFFFD166);
        c.drawRect(barX, 278f, barX + barW * (seen / (float) total), 288f, paint);

        // 分页
        boolean enemiesTab = w.codexTab == 0;
        tabButton(c, Ui.CODEX_TAB_ENEMIES, "敌机", enemiesTab);
        tabButton(c, Ui.CODEX_TAB_BOSSES, "BOSS", !enemiesTab);

        paint.setTextAlign(Paint.Align.LEFT);
        if (enemiesTab) {
            EnemyType[] types = EnemyType.values();
            for (int i = 0; i < types.length && i < Ui.CODEX_MAX_ROWS; i++) {
                EnemyType t = types[i];
                boolean known = w.settings.enemySeen(t);
                codexRowFrame(c, i, known);
                float y = Ui.CODEX_ROW_Y + i * Ui.CODEX_ROW_H;
                codexEnemyIcon(c, t, 175f, y + 40f, known);
                if (known) {
                    codexText(c, 250f, y, enemyName(t),
                            "HP " + t.hp + "  ·  " + t.score + " 分  ·  半径 " + Math.round(t.radius),
                            enemyDesc(t), 0xFFE8F1FF);
                } else {
                    codexText(c, 250f, y, "？？？", "遭遇后自动记录", "未在战场上出现过", 0xFF5A647F);
                }
            }
        } else {
            for (int tier = 0; tier < 4; tier++) {
                int kills = tier < w.settings.codexBossKills.length
                        ? w.settings.codexBossKills[tier] : 0;
                boolean known = kills > 0;
                codexRowFrame(c, tier, known);
                float y = Ui.CODEX_ROW_Y + tier * Ui.CODEX_ROW_H;
                codexBossIcon(c, tier, 175f, y + 40f, known);
                codexText(c, 250f, y, known ? Boss.titleFor(tier) : "？？？",
                        known ? bossStats(tier) : "击败后解锁",
                        known ? bossDesc(tier) : "尚未战胜这艘舰体",
                        known ? 0xFFE8F1FF : 0xFF5A647F);
                if (known) {
                    paint.setTextSize(24f);
                    paint.setColor(0xFFFFD166);
                    paint.setTextAlign(Paint.Align.RIGHT);
                    c.drawText("击坠 ×" + kills, 770f, y + 42f, paint);
                    paint.setTextAlign(Paint.Align.LEFT);
                }
            }
        }

        button(c, Ui.CODEX_BACK, "BACK", 0xFF182040, 0xFFE8F1FF, false, 30f);
        paint.setTextSize(22f);
        paint.setColor(0x668FA3C8);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText("图鉴记录保存在本机 · 与分数榜无关", VW / 2f, 1520f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void tabButton(Canvas c, Ui.Rect r, String label, boolean active) {
        paint.setStyle(active ? Paint.Style.FILL : Paint.Style.STROKE);
        if (active) {
            paint.setColor(0xFF27405F);
            c.drawRoundRect(r.x, r.y, r.x + r.w, r.y + r.h, 14f, 14f, paint);
        } else {
            paint.setStrokeWidth(2.5f);
            paint.setColor(0x556B7A99);
            c.drawRoundRect(r.x, r.y, r.x + r.w, r.y + r.h, 14f, 14f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? 0xFFAFF6FF : 0xFF8FA3C8);
        paint.setTextSize(30f);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(label, r.cx(), r.cy() + 11f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void codexRowFrame(Canvas c, int index, boolean known) {
        Ui.Rect row = Ui.codexRow(index);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(known ? 0x26274BFF : 0x14FFFFFF);
        c.drawRoundRect(row.x, row.y, row.x + row.w, row.y + row.h, 14f, 14f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(known ? 0x66AFF6FF : 0x336B7A99);
        c.drawRoundRect(row.x, row.y, row.x + row.w, row.y + row.h, 14f, 14f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** 图鉴行文案：名称 + 数值 + 说明三行（行高 86，说明压到 76 基线）。 */
    private void codexText(Canvas c, float x, float rowY, String name,
                           String stats, String desc, int nameColor) {
        paint.setTextSize(29f);
        paint.setFakeBoldText(true);
        paint.setColor(nameColor);
        c.drawText(name, x, rowY + 30f, paint);
        paint.setFakeBoldText(false);
        paint.setTextSize(20f);
        paint.setColor(0xFF8FA3C8);
        c.drawText(stats, x, rowY + 54f, paint);
        paint.setColor(0xFFC8D6F0);
        c.drawText(desc, x, rowY + 76f, paint);
    }

    /** 缩略敌机图标：复用战场轮廓，未发现的只留灰色描边。 */
    private void codexEnemyIcon(Canvas c, EnemyType t, float x, float y, boolean known) {
        float r = Math.min(34f, t.radius);
        int base = known ? palette(t.colorHint, 235) : 0x406B7A99;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(base);
        switch (t) {
            case DRONE:
                triangle(c, x, y, r, true);
                break;
            case WEAVER:
                diamond(c, x, y, r, base);
                break;
            case HUNTER:
                triangle(c, x, y, r, true);
                paint.setColor(known ? 0xFFFF5C7A : 0x406B7A99);
                c.drawCircle(x, y + r * 0.2f, r * 0.28f, paint);
                break;
            case TANK:
                hexagon(c, x, y, r, base);
                break;
            case SUICIDER:
                paint.setColor(base);
                shapePath.rewind();
                shapePath.moveTo(x, y + r);
                shapePath.lineTo(x - r * 0.9f, y - r * 0.7f);
                shapePath.lineTo(x, y - r * 0.25f);
                shapePath.lineTo(x + r * 0.9f, y - r * 0.7f);
                shapePath.close();
                c.drawPath(shapePath, paint);
                break;
            case SPLITTER:
                splitterLobe(c, x, y, r, base, -1f);
                splitterLobe(c, x, y, r, base, 1f);
                break;
            case HEALER: {
                c.drawCircle(x, y, r * 0.92f, paint);
                paint.setColor(known ? 0xFF101528 : 0x406B7A99);
                c.drawCircle(x, y, r * 0.72f, paint);
                paint.setColor(known ? 0xFF7CFF6B : 0x406B7A99);
                float u = r * 0.18f;
                c.drawRect(x - u, y - r * 0.52f, x + u, y + r * 0.52f, paint);
                c.drawRect(x - r * 0.52f, y - u, x + r * 0.52f, y + u, paint);
                break;
            }
            case SNIPER:
                octagon(c, x, y, r, base);
                paint.setColor(known ? 0xFF101528 : 0x406B7A99);
                c.drawRect(x - r * 0.16f, y + r * 0.2f, x + r * 0.16f, y + r * 1.05f, paint);
                break;
            case MINER:
                hexagon(c, x, y, r, base);
                paint.setColor(known ? 0xFF101528 : 0x306B7A99);
                c.drawRect(x - r * 0.5f, y + r * 0.15f, x + r * 0.5f, y + r * 0.45f, paint);
                paint.setColor(known ? 0xFFFF5C7A : 0x406B7A99);
                c.drawCircle(x, y + r * 0.75f, r * 0.26f, paint);
                break;
            case CARRIER: {
                paint.setColor(base);
                shapePath.rewind();
                shapePath.moveTo(x, y - r * 0.85f);
                shapePath.lineTo(x + r * 1.2f, y - r * 0.15f);
                shapePath.lineTo(x + r * 0.8f, y + r * 0.7f);
                shapePath.lineTo(x - r * 0.8f, y + r * 0.7f);
                shapePath.lineTo(x - r * 1.2f, y - r * 0.15f);
                shapePath.close();
                c.drawPath(shapePath, paint);
                paint.setColor(known ? 0xFF101528 : 0x306B7A99);
                c.drawRect(x - r * 0.85f, y - r * 0.02f, x + r * 0.85f, y + r * 0.2f, paint);
                break;
            }
            case SHIELDER:
                octagon(c, x, y, r * 0.8f, base);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f);
                paint.setColor(known ? 0xAA7DF9FF : 0x556B7A99);
                c.drawCircle(x, y, r * 1.1f, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            default:
                break;
        }
        if (!known) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(0x666B7A99);
            c.drawCircle(x, y, r * 1.15f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    /** 缩略 Boss 图标：等级越高舰体越大、部件越多。 */
    private void codexBossIcon(Canvas c, int tier, float x, float y, boolean known) {
        float r = 26f + tier * 5f;
        int color = known
                ? (tier == 3 ? 0xFFFF5C7A : tier == 2 ? 0xFFFFB84D : tier == 1 ? 0xFFB44DFF : 0xFF9A6BE8)
                : 0x406B7A99;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(known ? 0xFF2A2350 : 0x1A6B7A99);
        hexagon(c, x, y, r, paint.getColor());
        hexagon(c, x, y, r * 0.8f, color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(known ? 0x88B44DFF : 0x336B7A99);
        c.drawCircle(x, y, r * 1.25f, paint);
        paint.setStyle(Paint.Style.FILL);
        int guns = tier == 0 ? 2 : tier == 3 ? 5 : 4;
        for (int i = 0; i < guns; i++) {
            double a = Math.PI * (0.15 + 0.7 * i / Math.max(1, guns - 1));
            paint.setColor(known ? 0xFFFFB84D : 0x336B7A99);
            c.drawCircle(x - (float) Math.cos(a) * r * 1.05f,
                    y + (float) Math.sin(a) * r * 0.5f, 5f, paint);
        }
        if (!known) {
            paint.setTextSize(34f);
            paint.setColor(0x668FA3C8);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("?", x, y + 12f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private static String enemyName(EnemyType t) {
        switch (t) {
            case DRONE: return "侦察机 DRONE";
            case WEAVER: return "游荡机 WEAVER";
            case TANK: return "重装堡 TANK";
            case HUNTER: return "猎手机 HUNTER";
            case SUICIDER: return "自杀机 SUICIDER";
            case SPLITTER: return "分裂舱 SPLITTER";
            case HEALER: return "医疗艇 HEALER";
            case SNIPER: return "狙击塔 SNIPER";
            case MINER: return "布雷艇 MINER";
            case CARRIER: return "母舰 CARRIER";
            case SHIELDER: return "盾卫 SHIELDER";
            default: return t.name();
        }
    }

    private static String enemyDesc(EnemyType t) {
        switch (t) {
            case DRONE: return "直线俯冲，靠数量压制";
            case WEAVER: return "正弦摆动，弹道飘忽";
            case TANK: return "厚甲慢速，横向散射";
            case HUNTER: return "之字突进并主动瞄准";
            case SUICIDER: return "锁定飞船加速俯冲，撞击即爆";
            case SPLITTER: return "击毁后裂成两架无人机";
            case HEALER: return "周期脉冲，为周围友舰回血";
            case SNIPER: return "悬停预警后射出高速瞄准弹";
            case MINER: return "沿途布下水雷，击毁后水雷全清";
            case CARRIER: return "持续投放小僚机，本体掉率高";
            case SHIELDER: return "正面能量盾挡弹，需绕射或弹跳弹";
            default: return "未知机型";
        }
    }

    private static String bossStats(int tier) {
        int guns = tier == 0 ? 2 : tier == 3 ? 5 : 4;
        int lasers = tier <= 1 ? (tier == 1 ? 1 : 0) : 2;
        int pods = tier <= 0 ? 0 : tier == 1 ? 1 : tier == 2 ? 1 : 2;
        int shields = tier >= 2 ? tier - 1 : 0;
        String s = "HP " + (420 + 200 * tier) + "  ·  机炮 " + guns + "  激光 " + lasers;
        if (pods > 0) s += "  分裂舱 " + pods;
        if (shields > 0) s += "  护盾器 " + shields;
        return s;
    }

    private static String bossDesc(int tier) {
        switch (tier) {
            case 0: return "直线弹幕，摧毁机炮即可压制";
            case 1: return "展开侧翼，相位二起会横冲撞击";
            case 2: return "护盾器削弱核心伤害，先拆发生器";
            default: return "分裂舱投放僚机，脉冲环覆盖全场";
        }
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
        drawPauseStatus(c, w);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** 暂停页状态摘要：难度、已持有圣物与在场炮台，方便确认当前 build。 */
    private void drawPauseStatus(Canvas c, GameWorld w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x66000011);
        c.drawRoundRect(120f, 636f, 780f, 636f + 132f, 18f, 18f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(24f);
        paint.setColor(diffColor(w.settings.difficulty));
        c.drawText(w.settings.difficulty.label() + "难度", 150f, 682f, paint);
        int turrets = 0;
        for (Turret t : w.turrets) if (t != null && t.active) turrets++;
        int wells = 0;
        for (GravityWell g : w.gravityWells) if (g != null && g.active) wells++;
        paint.setTextSize(22f);
        paint.setColor(0xFF8FA3C8);
        float summaryX = 150f;
        c.drawText("炮台 " + turrets + " 座", summaryX, 724f, paint);
        summaryX += paint.measureText("炮台 " + turrets + " 座") + 28f;
        if (wells > 0) {
            paint.setColor(0xFFC9A0FF);
            c.drawText("重力井 " + wells + " 口", summaryX, 724f, paint);
            summaryX += paint.measureText("重力井 " + wells + " 口") + 28f;
            paint.setColor(0xFF8FA3C8);
        }
        // 终极槽进度：暂停时最该看的数字——"再打多久能放大招"
        if (w.ult.charge > 0.5f || w.ult.casts > 0) {
            paint.setColor(w.ult.full() ? 0xFFFFE08A : 0xFFB08FFF);
            String ultLabel = "大招 " + (int) (w.ult.charge * 100f / Ultimate.CAP)
                    + "% · 已放 " + w.ult.casts + " 次";
            c.drawText(ultLabel, summaryX, 724f, paint);
            paint.setColor(0xFF8FA3C8);
        }
        if (w.relicMask == 0) {
            paint.setColor(0x668FA3C8);
            c.drawText("圣物 · 尚未击败 BOSS", 300f, 682f, paint);
        } else {
            float x = 300f, ry = 682f;
            for (Relic r : Relic.values()) {
                if (!w.hasRelic(r)) continue;
                paint.setColor((0xE0 << 24) | r.colorRgb());
                float tw = paint.measureText(r.label());
                if (x + tw > 756f && x > 300f) { x = 300f; ry += 42f; }
                c.drawText(r.label(), x, ry, paint);
                x += tw + 20f;
            }
        }
        paint.setTextAlign(Paint.Align.CENTER);
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
                + (w.mode == GameMode.ENDLESS ? "ENDLESS" : "WAVE " + w.wave)
                + "   •   " + w.settings.difficulty.label(), VW / 2f, 468f, paint);

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
        drawLeaderboard(c, w.board(w.mode), w.mode, VW / 2f - 160f, 740f, 320f, 28f, 22f, w.scoreRank);
        drawScoreBreakdown(c, w);

        paint.setTextSize(40f);
        float alpha = 0.4f + 0.6f * Math.abs((float) Math.sin(w.stateTimer * 2.2f));
        paint.setColor(((int) (255 * alpha) << 24) | 0x8FA3C8);
        c.drawText("Tap to fly again", VW / 2f, 1230f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** 结算得分构成：五路来源（击坠 / BOSS / 部件 / 道具 / 其他）按占比画条形图。 */
    private void drawScoreBreakdown(Canvas c, GameWorld w) {
        float x = 140f, y = 968f, wdt = 620f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x99000011);
        c.drawRoundRect(x, y, x + wdt, y + 224f, 18f, 18f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x66274BFF);
        c.drawRoundRect(x, y, x + wdt, y + 224f, 18f, 18f, paint);
        paint.setStyle(Paint.Style.FILL);

        long total = 0;
        for (int v : w.scoreBySource) total += Math.max(0, v);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(22f);
        paint.setColor(0xFF7DF9FF);
        c.drawText("SCORE BY SOURCE 得分构成", x + 20f, y + 32f, paint);

        String[] labels = {"击坠", "BOSS", "部件", "道具", "其他"};
        int[] colors = {0xFF5CE1FF, 0xFFFF5C7A, 0xFFFFB84D, 0xFF7CFF6B, 0xFFB08FFF};
        float rowY = y + 62f;
        float barX = x + 110f, barW = wdt - 240f;
        for (int i = 0; i < labels.length; i++) {
            int v = Math.max(0, w.scoreBySource[i]);
            float frac = total <= 0 ? 0f : v / (float) total;
            paint.setTextSize(22f);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(v > 0 ? 0xFFE8F1FF : 0x558FA3C8);
            c.drawText(labels[i], x + 20f, rowY + 15f, paint);
            paint.setColor(0x33FFFFFF);
            c.drawRoundRect(barX, rowY, barX + barW, rowY + 18f, 6f, 6f, paint);
            if (frac > 0.001f) {
                paint.setColor((colors[i] & 0x00FFFFFF) | 0xE0000000);
                float bw = Math.max(8f, barW * frac);
                c.drawRoundRect(barX, rowY, barX + bw, rowY + 18f, 6f, 6f, paint);
            }
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setColor(v > 0 ? colors[i] : 0x558FA3C8);
            c.drawText(String.valueOf(v), x + wdt - 20f, rowY + 15f, paint);
            rowY += 31f;
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /**
     * 本局数据卡（5 列 × 4 行 = 20 格）：火力质量（命中/连击/击坠/精英/擦弹）、
     * 弹幕操作（暴击/截弹/弹反/最高连反/冲刺）、吸收与终结（吞弹/喷还/井口/大招/折弹）、
     * 收获（BOSS/圣物/异变/受伤/存活）。
     */
    private void drawRunStats(Canvas c, GameWorld w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x99000011);
        c.drawRoundRect(120f, 548f, 780f, 722f, 18f, 18f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x66FF8FD1);
        c.drawRoundRect(120f, 548f, 780f, 722f, 18f, 18f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);

        String[] labels = {"ACCURACY", "MAX COMBO", "KILLS", "ELITES", "GRAZES",
                "CRITS", "BREAK", "PARRY", "MAX STREAK", "DASH",
                "EATEN", "BURST", "WELLS", "ULT", "REFLECT",
                "BOSS", "RELICS", "MUTANTS", "HITS", "TIME"};
        String[] values = {
                w.accuracyPct() + "%",
                "x" + w.maxCombo,
                String.valueOf(w.kills),
                String.valueOf(w.elitesKilled),
                String.valueOf(w.grazes),
                String.valueOf(w.crits),
                String.valueOf(w.bulletsShotDown),
                String.valueOf(w.parries),
                "x" + w.maxParryStreak,
                String.valueOf(w.ship.dashesUsed),
                String.valueOf(w.gravityAbsorbed),
                String.valueOf(w.gravityBursts),
                String.valueOf(w.gravityWellsDeployed),
                String.valueOf(w.ult.casts),
                String.valueOf(w.ultConverted),
                String.valueOf(w.bossesDefeated),
                String.valueOf(w.relicsGained),
                String.valueOf(w.mutatorsSeen),
                String.valueOf(w.hitsTaken),
                ((int) w.timeAlive) + "s"
        };
        float[] cx = {186f, 318f, 450f, 582f, 714f};
        float[] rowY = {570f, 612f, 654f, 696f};
        for (int i = 0; i < labels.length; i++) {
            float x = cx[i % 5];
            float y = rowY[i / 5];
            paint.setTextSize(14f);
            paint.setColor(0xFF8FA3C8);
            c.drawText(labels[i], x, y, paint);
            paint.setTextSize(23f);
            paint.setColor(i == 1 ? 0xFFFFD166 : i == 4 ? 0xFFFF8FD1
                    : i == 5 ? 0xFFFFB03A : i == 6 ? 0xFF7DF9FF
                    : i == 7 ? 0xFFB0FFF6 : i == 10 || i == 12 ? 0xFFC9A0FF
                    : i == 13 ? 0xFFE08A : i == 14 ? 0xFFB0FFF6
                    : i == 17 ? 0xFFB08FFF : 0xFFE8F1FF);
            c.drawText(values[i], x, y + 23f, paint);
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

    /** 八边形：与现有三角/菱/六边形都不同轮廓，给狙击塔使用。 */
    private void octagon(Canvas c, float x, float y, float r, int color) {
        paint.setColor(color);
        shapePath.rewind();
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4.0 * i + Math.PI / 8.0;
            float px = x + (float) Math.cos(a) * r;
            float py = y + (float) Math.sin(a) * r * 0.9f;
            if (i == 0) shapePath.moveTo(px, py); else shapePath.lineTo(px, py);
        }
        shapePath.close();
        c.drawPath(shapePath, paint);
    }

    /** 分裂舱的单侧舱叶（side = ±1 镜像）。 */
    private void splitterLobe(Canvas c, float x, float y, float r, int color, float side) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        shapePath.rewind();
        shapePath.moveTo(x, y - r * 0.85f);
        shapePath.lineTo(x + side * r * 0.72f, y - r * 0.42f);
        shapePath.lineTo(x + side * r * 0.92f, y + r * 0.38f);
        shapePath.lineTo(x + side * r * 0.22f, y + r * 0.85f);
        shapePath.lineTo(x, y + r * 0.55f);
        shapePath.close();
        c.drawPath(shapePath, paint);
        // 叶内灯标：暗示"里面还藏着两架无人机"
        paint.setColor(0xFF101528);
        c.drawCircle(x + side * r * 0.42f, y, r * 0.17f, paint);
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

    /** Neon palette shared across the game.
     *  0 cyan 1 green 2 orange 3 magenta 4 yellow 5 lime 6 rose 7 violet
     *  8 mint 9 crimson 10 steel */
    private static int palette(int idx, int alpha) {
        int rgb;
        switch (idx) {
            case 1: rgb = 0x7CFF6B; break;
            case 2: rgb = 0xFFB84D; break;
            case 3: rgb = 0xFF5CF0; break;
            case 4: rgb = 0xFFD166; break;
            case 5: rgb = 0xBFFF4D; break;
            case 6: rgb = 0xFF7089; break;
            case 7: rgb = 0xB08FFF; break;
            case 8: rgb = 0x6BFFC8; break;
            case 9: rgb = 0xFF4D6A; break;
            case 10: rgb = 0xC0CCE8; break;
            default: rgb = 0x5CE1FF; break;
        }
        return (alpha << 24) | rgb;
    }
}
