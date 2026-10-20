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
    /** 浮动摇杆：拖动中手指的 pointerId（-1 = 未拖动） */
    private int dragPointer = -1;
    private float anchorTouchX, anchorTouchY, anchorShipX, anchorShipY;
    private GameWorld.UpdateState lastUpdateState = GameWorld.UpdateState.IDLE;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        world = new GameWorld(System.nanoTime(), true);
        world.attachStore(new SharedPrefsStore(context));
        // low-RAM devices start with reduced fx from frame one (auto quality recovers if fast)
        android.app.ActivityManager am =
                (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null && am.isLowRamDevice()) world.primeQuality(0.5f);
        renderer = new Renderer();
        haptics = new Haptics(context);
        haptics.attach(world.settings);
        audio.init(context, world.settings);
        // 升级：清理上次安装的 APK 残留，注入当前版本号
        UpdateManager.cleanup(context);
        world.currentVersion = UpdateManager.currentVersion(context);
    }

    /** 手指位移 → 飞船位移 的灵敏度（1.0 = 等距跟随）。 */
    private static final float DRAG_SENSITIVITY = 1.0f;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        final int idx = event.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                handlePress(event, idx);
                return true;
            case MotionEvent.ACTION_MOVE:
                handleDrag(event);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                handleRelease(event, idx);
                return true;
            case MotionEvent.ACTION_CANCEL:
                endDrag();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    /** 按下沿：UI 点击 / 炸弹键 / 开始浮动摇杆拖动。 */
    private void handlePress(MotionEvent event, int idx) {
        float[] v = renderer.toVirtual(event.getX(idx), event.getY(idx));

        // 炸弹键（按下沿触发一次，带去抖）——优先于摇杆
        if (world.state == GameWorld.State.PLAYING && Ui.BOMB.contains(v[0], v[1])) {
            long now = System.nanoTime();
            if (now - lastBombAt > 400_000_000L) {
                lastBombAt = now;
                world.detonateBomb();
            }
            return;
        }

        Ui.Action a = Ui.hit(world.state, world.settingsOpen, v[0], v[1]);
        if (a != null) {
            world.handleUiAction(a);
            if (a == Ui.Action.CHECK_UPDATE) dispatchUpdateWork();
            audio.uiTick();
            return;
        }
        if (world.state == GameWorld.State.GAME_OVER && world.stateTimer > 0.8f) {
            world.handleUiAction(Ui.Action.RESTART);
            return;
        }

        // 任意空白位置按下 = 摇杆锚点（王者荣耀式相对拖动）
        if (world.state == GameWorld.State.PLAYING && dragPointer < 0) {
            dragPointer = event.getPointerId(idx);
            anchorTouchX = v[0];
            anchorTouchY = v[1];
            anchorShipX = world.ship.x;
            anchorShipY = world.ship.y;
            world.joyActive = true;
            world.joyBaseX = v[0];
            world.joyBaseY = v[1];
            world.joyX = v[0];
            world.joyY = v[1];
        }
    }

    /** MOVE：按锚点→手指的相对位移驱动飞船（限速跟随由 Ship 保证）。 */
    private void handleDrag(MotionEvent event) {
        if (dragPointer < 0) return;
        int idx = event.findPointerIndex(dragPointer);
        if (idx < 0) return;
        float[] v = renderer.toVirtual(event.getX(idx), event.getY(idx));
        float dx = (v[0] - anchorTouchX) * DRAG_SENSITIVITY;
        float dy = (v[1] - anchorTouchY) * DRAG_SENSITIVITY;
        world.moveShipTo(anchorShipX + dx, anchorShipY + dy);
        world.joyX = v[0];
        world.joyY = v[1];
    }

    private void handleRelease(MotionEvent event, int idx) {
        if (dragPointer >= 0 && event.getPointerId(idx) == dragPointer) endDrag();
    }

    private void endDrag() {
        dragPointer = -1;
        world.joyActive = false;
    }

    /** 升级检测点击后：按状态机派发后台 IO（检查 / 下载）。 */
    private void dispatchUpdateWork() {
        if (world.updateState == GameWorld.UpdateState.CHECKING) {
            UpdateManager.checkAsync(getContext(), world, () -> { });
        } else if (world.updateState == GameWorld.UpdateState.DOWNLOADING) {
            UpdateManager.downloadAsync(getContext(), world, () -> { });
        }
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

            // 升级流：APK 就绪后自动唤起系统安装器（每次进入 READY 只唤起一次）
            if (world.updateState == GameWorld.UpdateState.READY
                    && lastUpdateState != GameWorld.UpdateState.READY) {
                UpdateManager.install(getContext(), world);
            }
            lastUpdateState = world.updateState;

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
        audio.release(); // SoundPool must not outlive the surface
    }
}
