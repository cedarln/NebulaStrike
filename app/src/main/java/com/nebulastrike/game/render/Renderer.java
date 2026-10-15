package com.nebulastrike.game.render;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import com.nebulastrike.game.core.Bullet;
import com.nebulastrike.game.core.Enemy;
import com.nebulastrike.game.core.GameWorld;
import com.nebulastrike.game.core.Particle;
import com.nebulastrike.game.core.Ship;
import com.nebulastrike.game.core.Star;

/**
 * Draws the whole game with Canvas. The simulation lives in a 1600x900 virtual
 * space; this class letterboxes and scales it onto the real display.
 */
public class Renderer {
    public static final float VW = GameWorld.WIDTH;
    public static final float VH = GameWorld.HEIGHT;

    private float scale = 1f, offX = 0f, offY = 0f;

    private final Paint paint = new Paint();
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);

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

    /** Maps screen pixels -> virtual coords (used by touch input). */
    public float[] toVirtual(float sx, float sy) {
        return new float[]{(sx - offX) / scale, (sy - offY) / scale};
    }

    public void draw(Canvas c, GameWorld w) {
        c.drawColor(Color.BLACK);
        c.save();
        c.translate(offX, offY);
        c.scale(scale, scale);
        c.clipRect(0, 0, VW, VH);

        drawBackground(c, w);
        drawStars(c, w);
        drawParticles(c, w);
        drawEnemies(c, w);
        drawBullets(c, w);
        drawShip(c, w);
        drawHud(c, w);

        c.restore();
    }

    private void drawBackground(Canvas c, GameWorld w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(0xFF05060F);
        c.drawRect(0, 0, VW, VH, paint);
        // two soft nebulae
        nebula(c, VW * 0.22f, VH * 0.35f, 420f, 0x141A3C55);
        nebula(c, VW * 0.78f, VH * 0.72f, 520f, 0x0F2A1B44);
    }

    private void nebula(Canvas c, float x, float y, float r, int color) {
        glow.setShader(new RadialGradient(x, y, r,
                new int[]{color, 0x00000000}, null, Shader.TileMode.CLAMP));
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
        glow.setXfermode(null);
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
            paint.setColor(0xFF7DF9FF);
            c.drawCircle(b.x, b.y, b.radius, paint);
            paint.setColor(0x66FFFFFF);
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
                    // hexagonal brick with hull plates
                    hexagon(c, e.x, e.y, r, base);
                    paint.setColor(0xFF101528);
                    c.drawCircle(e.x, e.y, r * 0.45f, paint);
                    break;
            }
        }
    }

    private void drawShip(Canvas c, GameWorld w) {
        Ship s = w.ship;
        if (w.state == GameWorld.State.GAME_OVER) return;
        // invulnerability blink
        if (s.invulnerable && (int) (w.timeAlive * 12) % 2 == 0) return;

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

        // hull: swept-wing fighter
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFE8F1FF);
        hull(c, s.x, s.y);
        paint.setColor(0xFF274BFF);
        hullStripe(c, s.x, s.y);
        paint.setColor(0xFFFF3355);
        c.drawCircle(s.x, s.y + 2f, 6f, paint);
    }

    // ---- HUD ----
    private void drawHud(Canvas c, GameWorld w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(44f);
        c.drawText(String.valueOf(w.score), 40f, 70f, paint);

        paint.setTextSize(30f);
        paint.setColor(0xFF8FA3C8);
        c.drawText("WAVE " + w.wave, VW / 2f - 70f, 60f, paint);

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

        if (w.state == GameWorld.State.GAME_OVER) {
            paint.setColor(0x99000000);
            c.drawRect(0, 0, VW, VH, paint);
            paint.setColor(0xFFFF3355);
            paint.setTextSize(120f);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("GAME OVER", VW / 2f, VH / 2f - 40f, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(48f);
            c.drawText("Score " + w.score + "   Kills " + w.kills, VW / 2f, VH / 2f + 40f, paint);
            paint.setColor(0xFF8FA3C8);
            c.drawText("Tap to restart", VW / 2f, VH / 2f + 130f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
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
        c.drawCircle(0, 0, 0, paint); // no-op keeps style
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(x, y - r);
        path.lineTo(x - r * 0.85f, y + r * 0.75f);
        path.lineTo(x + r * 0.85f, y + r * 0.75f);
        path.close();
        c.drawPath(path, paint);
    }

    private void diamond(Canvas c, float x, float y, float r, int color) {
        paint.setColor(color);
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(x, y - r);
        p.lineTo(x + r * 0.7f, y);
        p.lineTo(x, y + r);
        p.lineTo(x - r * 0.7f, y);
        p.close();
        c.drawPath(p, paint);
    }

    private void hexagon(Canvas c, float x, float y, float r, int color) {
        paint.setColor(color);
        android.graphics.Path p = new android.graphics.Path();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3.0 * i + Math.PI / 6.0;
            float px = x + (float) Math.cos(a) * r;
            float py = y + (float) Math.sin(a) * r;
            if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
        }
        p.close();
        c.drawPath(p, paint);
    }

    private void hull(Canvas c, float x, float y) {
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(x, y - Ship.RADIUS);
        p.lineTo(x + Ship.RADIUS * 0.35f, y - Ship.RADIUS * 0.1f);
        p.lineTo(x + Ship.RADIUS * 0.95f, y + Ship.RADIUS * 0.8f);
        p.lineTo(x + Ship.RADIUS * 0.30f, y + Ship.RADIUS * 0.45f);
        p.lineTo(x, y + Ship.RADIUS * 0.6f);
        p.lineTo(x - Ship.RADIUS * 0.30f, y + Ship.RADIUS * 0.45f);
        p.lineTo(x - Ship.RADIUS * 0.95f, y + Ship.RADIUS * 0.8f);
        p.lineTo(x - Ship.RADIUS * 0.35f, y - Ship.RADIUS * 0.1f);
        p.close();
        c.drawPath(p, paint);
    }

    private void hullStripe(Canvas c, float x, float y) {
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(x, y - Ship.RADIUS * 0.75f);
        p.lineTo(x + Ship.RADIUS * 0.18f, y);
        p.lineTo(x, y + Ship.RADIUS * 0.4f);
        p.lineTo(x - Ship.RADIUS * 0.18f, y);
        p.close();
        c.drawPath(p, paint);
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
