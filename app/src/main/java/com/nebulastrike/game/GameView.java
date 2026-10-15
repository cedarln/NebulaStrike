package com.nebulastrike.game;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.nebulastrike.game.core.GameWorld;
import com.nebulastrike.game.render.Renderer;

/**
 * Owns the game thread: fixed loop of [simulate -> render] driving the Surface.
 * Touch input is mapped from screen pixels into the 1600x900 virtual space.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private final GameWorld world;
    private final Renderer renderer;
    private Thread thread;
    private volatile boolean running;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);
        world = new GameWorld(System.nanoTime());
        renderer = new Renderer();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            float[] v = renderer.toVirtual(event.getX(), event.getY());
            world.moveShipTo(v[0], v[1]);
            if (world.state == GameWorld.State.GAME_OVER && world.stateTimer > 0.8f) {
                world.restart();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = (now - last) / 1_000_000_000f;
            last = now;

            world.update(dt);

            SurfaceHolder holder = getHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockHardwareCanvas();
                if (canvas != null) {
                    synchronized (holder) {
                        renderer.draw(canvas, world);
                    }
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }

            // target ~60fps; yields CPU and keeps thermals sane on low-end devices
            long frameNanos = System.nanoTime() - now;
            long sleep = 16_000_000L - frameNanos;
            if (sleep > 0) {
                try { Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L)); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        thread = new Thread(this, "game-loop");
        thread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        renderer.setViewport(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) { }
            thread = null;
        }
    }
}
