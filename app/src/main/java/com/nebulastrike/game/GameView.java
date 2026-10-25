package com.nebulastrike.game;

import android.content.Context;
import android.graphics.Canvas;
import android.media.AudioManager;
import android.os.Build;
import android.os.Process;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import com.nebulastrike.game.core.EnemyType;
import com.nebulastrike.game.core.GameWorld;
import com.nebulastrike.game.core.Ui;
import com.nebulastrike.game.core.WeaponType;
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
    private long lastDashAt;
    private long lastUltAt;
    private int lastShotsSeen;
    private int lastWingmanSeen;
    /** 浮动摇杆：拖动中手指的 pointerId（-1 = 未拖动） */
    private int dragPointer = -1;
    private float anchorTouchX, anchorTouchY, anchorShipX, anchorShipY;
    private GameWorld.UpdateState lastUpdateState = GameWorld.UpdateState.IDLE;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        // 立刻索取焦点：否则 KeyEvent 不会走到本 View，返回键只能靠 Activity 兜底
        requestFocus();
        world = new GameWorld(System.nanoTime(), true);
        world.attachStore(new SharedPrefsStore(context));
        // low-RAM devices start with reduced fx from frame one (auto quality recovers if fast)
        android.app.ActivityManager am =
                (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null && am.isLowRamDevice()) world.primeQuality(0.5f);
        renderer = new Renderer();
        haptics = new Haptics(context);
        haptics.attach(world.settings);
        // 预热合成音效库，让主菜单出现时音频线程已经就绪
        audio.init(world.settings);
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

    /** 按下沿：UI 点击 / 炸弹键 / 冲刺键 / 开始浮动摇杆拖动。 */
    private void handlePress(MotionEvent event, int idx) {
        float[] v = renderer.toVirtual(event.getX(idx), event.getY(idx));

        // 炸弹键（按下沿触发一次，带去抖）——优先于摇杆
        if (world.state == GameWorld.State.PLAYING
                && Ui.bomb(world.settings.leftHanded).contains(v[0], v[1])) {
            long now = System.nanoTime();
            if (now - lastBombAt > 400_000_000L) {
                lastBombAt = now;
                world.detonateBomb();
            }
            return;
        }

        // 相位冲刺键：能量满时立即位移（同样带去抖，避免误触连续消耗）
        if (world.state == GameWorld.State.PLAYING
                && Ui.dash(world.settings.leftHanded).contains(v[0], v[1])) {
            long now = System.nanoTime();
            if (now - lastDashAt > 180_000_000L) {
                lastDashAt = now;
                // 成功时由 DASH 事件驱动音效；能量不足才在这里给一个"空挡"反馈
                if (!world.tryDash()) audio.dashBlocked();
            }
            return;
        }

        // 终极技键：槽量足够才释放（放在最外侧，避免连招时误触把大招放空）
        if (world.state == GameWorld.State.PLAYING
                && Ui.ult(world.settings.leftHanded).contains(v[0], v[1])) {
            long now = System.nanoTime();
            if (now - lastUltAt > 260_000_000L) {
                lastUltAt = now;
                // 成功由 ULT_CAST 事件驱动音效；槽不够才给"还没攒满"的空挡反馈
                if (!world.tryUltimate()) audio.ultBlocked();
            }
            return;
        }

        Ui.Action a = Ui.hit(world.state, world.settingsOpen, world.cheatOpen,
                world.codexOpen, world.achieveOpen, world.careerOpen, world.storyOpen,
                v[0], v[1]);
        if (a != null) {
            world.handleUiAction(a);
            if (a == Ui.Action.CHECK_UPDATE) dispatchUpdateWork();
            audio.uiTick();
            if (a == Ui.Action.TOGGLE_SOUND && world.settings.sound) {
                audio.confirmSound();
                warnIfMediaMuted();
            }
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

    /** 打开音效时顺带检查媒体音量，避免“开了音效却没有声音”。 */
    private void warnIfMediaMuted() {
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        if (am != null && am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            Toast.makeText(getContext(), "媒体音量为 0，请按音量键调高", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Back button: pause/resume in game, close panels, exit from the bare menu.
     * 逻辑统一在 GameWorld.consumeBack()（可单测），Activity 的 onBackPressed 与本方法共用它。
     * @return true 表示这次返回已被游戏消化，不应再交给系统默认行为
     */
    public boolean handleBack() {
        boolean consumed = world.consumeBack();
        if (consumed) audio.uiTick();
        return consumed;
    }

    /** 供 Activity 的 onBackPressed 使用：返回键被游戏消化时给一声轻响。 */
    void playBackUiSound() {
        audio.uiTick();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 有焦点时也走同一条路径；未消费时交回系统（Activity 会正常退出）
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
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
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
                    case PLAYER_HIT: haptics.impact(); break;
                    case BOMB: haptics.bomb(); break;
                    case OVERDRIVE: haptics.bomb(); break;
                    case GAME_OVER: haptics.death(); break;
                    case BOSS_DEFEATED: haptics.bomb(); break;
                    // 入场预警：一记重震把注意力从走位拉到"抬头看"
                    case BOSS_WARNING: haptics.impact(); break;
                    case STAGE_CLEAR: haptics.tick(); break;
                    case STORY_UNLOCK: haptics.tick(); break;
                    // 被网缠住不是受伤：轻点一下，提示"你现在很钝"
                    case WEB_SNARE: haptics.tick(); break;
                    case BOSS_PART_DESTROYED: haptics.tick(); break;
                    case BOSS_DISARMED: haptics.impact(); break;
                    case BOSS_RAM: haptics.impact(); break;
                    case CHAIN: haptics.impact(); break;
                    case FUSION: haptics.tick(); break;
                    case RUSH: haptics.tick(); break;
                    case DASH: haptics.tick(); break;
                    case ULT_CAST: haptics.bomb(); break;
                    case ULT_READY: haptics.tick(); break;
                    // 成就用长震：一局里最难得的正面事件，手感要和大招区分开
                    case ACHIEVEMENT_UNLOCKED: haptics.bomb(); break;
                    // 日常开局：短促一下即可，只是"今天开始了"的确认
                    case DAILY_START: haptics.tick(); break;
                    case BOSS_RUSH_START: haptics.tick(); break;
                    case RAM_IMPACT: haptics.impact(); break;
                    case PHASE_SHIFT: haptics.tick(); break;
                    // 反射护罩挡下敌弹：比擦弹重、比受击轻，像"接住再丢回去"
                    case REFLECT: haptics.impact(); break;
                    // 电弧连锁：轻点一下，别和连锁闪电道具的重震混淆
                    case ARC_CHAIN: haptics.tick(); break;
                    // 弹幕网展开：整屏压下来的感觉，给一次中等震动
                    case BOSS_NET: haptics.impact(); break;
                    // 换段增援：和预警同档，提示"现在场上是新的一轮"
                    case BOSS_REINFORCE: haptics.impact(); break;
                    // 竖切光束落下：一道必须整列让开的攻击，给一次明确的中震
                    case ENEMY_BEAM: haptics.impact(); break;
                    // 棱镜召唤：比横冲轻，像"多了两个东西在绕圈"
                    case BOSS_PRISM: haptics.tick(); break;
                    // 祭坛降临只是提示；吸弹太频繁不给震，免得整场都在嗡嗡
                    case ALTAR_SPAWN: haptics.tick(); break;
                    // 赐福爆发是一整段正反馈的收口：和大招同档
                    case ALTAR_BLESSED: haptics.bomb(); break;
                    // 悬赏发布轻点一下；兑现给一记重震，脱逃不震（避免惩罚感）
                    case BOUNTY_START: haptics.tick(); break;
                    case BOUNTY_CLAIMED: haptics.bomb(); break;
                    // 连携点亮是一层"变强了"的确认，给一记轻震
                    case SYNERGY_ON: haptics.tick(); break;
                    // 相位冲击波是"清场"动作：比普通冲刺重一档
                    case PHASE_BURST: haptics.impact(); break;
                    case MIRAGE_REVEAL: haptics.tick(); break;
                    // 分数奖励命：和大招同档的长震，让"跨过阈值"这件事被身体记住
                    case EXTRA_LIFE: haptics.bomb(); break;
                    // EMP 放出去要"撞一下"，磁暴艇被打爆吞弹只是轻提示
                    case MAG_BURST:
                        if (e.code > 0) haptics.impact(); else haptics.tick();
                        break;
                    case BROOD_SPAWN: haptics.tick(); break;
                    // 保底登场的震动按舰型分量：磁暴艇"撞一下"，蜂后"沉一下"
                    case NEW_SHIP_DEBUT:
                        if (e.code == EnemyType.MAGNETON.ordinal()) haptics.impact();
                        else if (e.code == EnemyType.QUEEN.ordinal()) haptics.bomb();
                        else if (e.code == EnemyType.RAMMER.ordinal()) haptics.impact();
                        else if (e.code == EnemyType.PHASER.ordinal()) haptics.tick();
                        break;
                }
            }
            if (world.shotsFired != lastShotsSeen) {
                lastShotsSeen = world.shotsFired;
                // 多武器叠层后同一帧可能有多门武器开火：按位掩码逐个发声
                int mask = world.weaponVolleyMask;
                for (int i = 0; i < WeaponType.values().length; i++) {
                    if ((mask & (1 << i)) != 0) {
                        audio.shoot(WeaponType.values()[i], world.ship.x);
                    }
                }
            }
            if (world.wingmanShots > lastWingmanSeen) {
                lastWingmanSeen = world.wingmanShots;
                audio.wingmanShot(world.ship.x);
            }

            long renderStart = System.nanoTime();
            SurfaceHolder holder = getHolder();
            Canvas canvas = null;
            try {
                // lockHardwareCanvas 需要 API 23；更老的设备退回软件 Canvas，避免崩在启动画面
                canvas = Build.VERSION.SDK_INT >= 23
                        ? holder.lockHardwareCanvas()
                        : holder.lockCanvas();
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
        // Surface 重建（切后台回来、锁屏解锁）后音频轨道已被释放，必须重新启动
        audio.init(world.settings);
        // Surface 重建后焦点可能还给 Activity，重新索取以保证 KeyEvent 通路
        requestFocus();
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
        audio.release(); // the AudioTrack must not outlive the surface
    }
}
