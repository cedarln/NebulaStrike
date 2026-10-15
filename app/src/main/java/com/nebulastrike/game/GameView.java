package com.nebulastrike.game;

import android.content.Context;
import android.graphics.Canvas;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.nebulastrike.game.core.GameWorld;
import com.nebulastrike.game.core.Ui;
import com.nebulastrike.game.render.Renderer;

/**
 * Owns the game thread: fixed loop of [simulate -> render] driving the Surface.
 * Touch input is mapped from screen pixels into the 1600x900 virtual space and
 * dispatched to UI hit-testing first, then gameplay.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private final GameWorld world;
    private final Renderer renderer;
    private final AudioEngine audio = new AudioEngine();
    private final Haptics haptics;
    private Thread thread;
    private volatile boolean running;
    private long lastBombAt;
    private int lastShotsSeen;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        world = new GameWorld(System.nanoTime(), true);
        world.attachStore(new SharedPrefsStore(context));
        renderer = new Renderer();
        haptics = new Haptics(context);
        haptics.attach(world.settings);
        audio.init(context, world.settings);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            // second finger = smart bomb (with a small debounce)
            long now = System.nanoTime();
            if (now - lastBombAt > 400_000_000L) {
                lastBombAt = now;
                world.detonateBomb();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            float[] v = renderer.toVirtual(event.getX(), event.getY());
            float vx = v[0], vy = v[1];

            // UI first
            Ui.Action a = Ui.hit(world.state, world.settingsOpen, vx, vy);
            if (a != null) {
                world.handleUiAction(a);
                audio.uiTick();
                return true;
            }
            if (world.state == GameWorld.State.GAME_OVER) {
                if (world.stateTimer > 0.8f) world.handleUiAction(Ui.Action.RESTART);
                return true;
            }
            if (world.state == GameWorld.State.PLAYING) {
                world.moveShipTo(vx, vy);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    /** Back button: pause/resume in game, exit from menu. Returns true if consumed. */
    public boolean handleBack() {
        switch (world.state) {
            case PLAYING:
                world.pauseIfPlaying();
                return true;
            case PAUSED:
                world.handleUiAction(Ui.Action.RESUME);
                return true;
            case GAME_OVER:
                world.handleUiAction(Ui.Action.QUIT_TO_MENU);
                return true;
            default:
                return false; // let the activity finish
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return handleBack() || super.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Exposed for lifecycle hooks from the activity. */
    GameWorld getWorld() {
        return world;
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = (now - last) / 1_000_000_000f;
            last = now;

            world.update(dt);

            // presentation: sound + haptics driven by simulation events
            java.util.List<GameWorld.GameEvent> events = world.pollEvents();
            audio.consume(events);
            for (GameWorld.GameEvent e : events) {
                switch (e.type) {
                    case PLAYER_HIT: haptics.impact(); audio.bigExplosion(); break;
                    case BOMB: haptics.bomb(); break;
                    case GAME_OVER: haptics.death(); break;
                    case BOSS_DEFEATED: haptics.bomb(); break;
                }
            }
            if (world.shotsFired != lastShotsSeen) {
                lastShotsSeen = world.shotsFired;
                audio.shoot();
            }

            long renderStart = System.nanoTime();
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

            // adaptive quality: only gameplay frames feed the governor
            if (world.state == GameWorld.State.PLAYING) {
                world.noteFrameCost((System.nanoTime() - renderStart) / 1_000_000_000f);
            }

            // frame governor: 60fps while playing, low-power 30fps in menus/pause
            long budget = world.state == GameWorld.State.PLAYING ? 16_666_666L : 33_333_333L;
            long sleep = budget - (System.nanoTime() - now);
            if (sleep > 2_000_000L) {
                try { Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L)); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
            } else if (sleep > 0) {
                Thread.yield(); // sub-2ms waits: sleeping overshoots due to timer granularity
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
