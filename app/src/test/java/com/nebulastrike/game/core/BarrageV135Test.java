package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.35 迭代 25：螺旋炮台 HELIX 与两种新弹幕。
 *
 * <p>这一版不靠提高弹速，而是靠"缺口会移动"制造压力：
 * 螺旋炮台三臂连吐、每拍转 0.5 弧度；坦克每第 3 轮改成五连扇形扫射。
 * 测试钉住旋臂角度、连吐节奏、歇口气窗口，以及坦克扫射的往复方向。
 */
public class BarrageV135Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261025L);
        world.spawningEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.ship.x = 450f;
        world.ship.prevX = 450f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
        events.clear();
    }

    private Enemy enemyAt(EnemyType type, float x, float y) {
        for (Enemy e : world.enemies) {
            if (e.active) continue;
            e.init(type, x, y, 1f);
            return e;
        }
        throw new IllegalStateException("敌机池已满");
    }

    private void stepEnemy(Enemy e, int frames) {
        for (int i = 0; i < frames; i++) e.update(1f / 60f, GameWorld.WIDTH, 450f, 1460f);
    }

    private void clearBullets() {
        for (EnemyBullet b : world.enemyBullets) b.active = false;
    }

    private List<EnemyBullet> activeSpiral() {
        List<EnemyBullet> out = new ArrayList<>();
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.spiral) out.add(b);
        }
        return out;
    }

    private List<EnemyBullet> activeBullets() {
        List<EnemyBullet> out = new ArrayList<>();
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active) out.add(b);
        }
        return out;
    }

    /** 清场、拨快冷却、跑一帧：只观察这一轮齐射。 */
    private void fireOneVolley(Enemy e) {
        clearBullets();
        e.fireCd = 0.0001f;
        world.update(1f / 60f);
        events.addAll(world.pollEvents());
    }

    private int spiralBurstEvents() {
        int n = 0;
        for (GameWorld.GameEvent ev : events) {
            if (ev.type == GameWorld.EventType.SPIRAL_BURST) n++;
        }
        return n;
    }

    // ---- 螺旋炮台 ----

    @Test
    public void helixHoversAtItsHighStation() {
        Enemy e = enemyAt(EnemyType.HELIX, 450f, 80f);
        stepEnemy(e, 300);
        assertTrue("螺旋炮台没有压到悬停高度", e.y >= Enemy.HELIX_HOVER_Y - 0.5f);
        assertTrue("螺旋炮台冲过了悬停高度", e.y < Enemy.HELIX_HOVER_Y + 6f);
        assertTrue("悬停时应当有轻微横向巡游", Math.abs(e.x - 450f) <= 61f);
        assertEquals(EnemyType.HELIX, e.type);
    }

    @Test
    public void helixLeavesAfterItsHoverTimerRunsOut() {
        Enemy e = enemyAt(EnemyType.HELIX, 450f, Enemy.HELIX_HOVER_Y);
        stepEnemy(e, (int) (Enemy.HELIX_HOVER * 60f) + 60);
        assertTrue("驻留到期后螺旋炮台没有离场", e.y > Enemy.HELIX_HOVER_Y + 20f);
    }

    @Test
    public void helixFiresThreeEvenlySpacedArms() {
        Enemy e = enemyAt(EnemyType.HELIX, 450f, Enemy.HELIX_HOVER_Y);
        fireOneVolley(e);

        List<EnemyBullet> volley = activeSpiral();
        assertEquals("一轮应当是三条旋臂", GameWorld.SPIRAL_ARMS, volley.size());
        for (EnemyBullet b : volley) {
            assertEquals("螺旋弹速不对", GameWorld.SPIRAL_SPEED,
                    (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy), 0.5f);
            assertFalse("螺旋弹不该带追踪", b.homing);
            assertFalse("螺旋弹不该带减速", b.web);
        }
        // 三条旋臂应当均分一圈
        double a0 = Math.atan2(volley.get(0).vy, volley.get(0).vx);
        for (int i = 1; i < volley.size(); i++) {
            double ai = Math.atan2(volley.get(i).vy, volley.get(i).vx);
            double diff = Math.abs(ai - a0);
            while (diff > Math.PI * 2) diff -= Math.PI * 2;
            if (diff > Math.PI) diff = Math.PI * 2 - diff;
            assertEquals("旋臂没有均分一圈", Math.PI * 2 / 3, diff, 0.05);
        }
        assertEquals("一轮三臂连吐的总拍数不对", GameWorld.SPIRAL_VOLLEYS - 1, e.burstCount);
        assertEquals("连吐间隔不对", GameWorld.SPIRAL_INTERVAL, e.fireCd, 0.001f);
        assertEquals("螺旋开转事件只该报一次", 1, spiralBurstEvents());
        for (GameWorld.GameEvent ev : events) {
            if (ev.type == GameWorld.EventType.SPIRAL_BURST) {
                assertEquals(GameWorld.SPIRAL_ARMS, ev.code);
            }
        }
    }

    @Test
    public void spiralArmsAdvanceEveryVolleyThenRest() {
        Enemy e = enemyAt(EnemyType.HELIX, 450f, Enemy.HELIX_HOVER_Y);
        fireOneVolley(e);
        float firstAngle = e.spiralAngle;
        fireOneVolley(e);
        assertEquals("每拍旋臂应当前进固定角度", GameWorld.SPIRAL_STEP,
                e.spiralAngle - firstAngle, 0.0001f);

        for (int i = 2; i < GameWorld.SPIRAL_VOLLEYS; i++) fireOneVolley(e);
        assertEquals("连吐结束后应当进入歇息", 0, e.burstCount);
        assertTrue("歇息时间太短", e.fireCd >= GameWorld.SPIRAL_REST);
        assertEquals("同一轮螺旋只该报一次开转", 1, spiralBurstEvents());

        fireOneVolley(e);
        assertEquals("下一轮应当重新起转", 2, spiralBurstEvents());
        assertEquals(GameWorld.SPIRAL_VOLLEYS - 1, e.burstCount);
    }

    // ---- 坦克扇形扫射 ----

    @Test
    public void tankEveryThirdVolleyBecomesAFiveShotFan() {
        Enemy e = enemyAt(EnemyType.TANK, 450f, 200f);
        fireOneVolley(e);
        assertEquals("坦克常规齐射应当是三发", 3, activeBullets().size());
        fireOneVolley(e);
        assertEquals("第二轮仍应是三发", 3, activeBullets().size());
        fireOneVolley(e);
        List<EnemyBullet> fan = activeBullets();
        assertEquals("第三轮应当变成五连扇形", 5, fan.size());
        // 弹对象来自对象池，后续齐射会覆写速度——先在这里把方向记下来
        float avg1 = 0f;
        for (EnemyBullet b : fan) avg1 += b.vx;

        fireOneVolley(e);
        assertEquals("扫射之后应当回到三发", 3, activeBullets().size());
        fireOneVolley(e);
        assertEquals("第五轮仍应是三发", 3, activeBullets().size());
        fireOneVolley(e);
        List<EnemyBullet> fan2 = activeBullets();
        assertEquals("第六轮应当再次五连", 5, fan2.size());

        float avg2 = 0f;
        for (EnemyBullet b : fan2) avg2 += b.vx;
        assertTrue("扇形扫射的方向应当在左右之间往复 avg1=" + avg1 + " avg2=" + avg2,
                avg1 * avg2 < 0f);
    }

    @Test
    public void tankFanStillFansDownward() {
        Enemy e = enemyAt(EnemyType.TANK, 450f, 200f);
        for (int i = 0; i < 3; i++) fireOneVolley(e);
        for (EnemyBullet b : activeBullets()) {
            assertTrue("扇形弹不该向上飞", b.vy > 200f);
            assertTrue("扇形弹的横向速度过大", Math.abs(b.vx) < 400f);
        }
    }

    // ---- 图鉴 ----

    @Test
    public void helixJoinsTheCodexWithoutOpeningANewPage() {
        assertTrue("螺旋炮台应当算后期兵种", EnemyType.HELIX.isAdvanced());
        assertTrue("螺旋炮台应当排在图鉴靠后的位置",
                EnemyType.HELIX.ordinal() >= Ui.CODEX_MAX_ROWS);
        int types = EnemyType.values().length;
        assertEquals("全部敌机仍应在两页内", 2, Ui.codexPageCount(types));
        assertTrue(Ui.codexPageCount(types) * Ui.CODEX_MAX_ROWS >= types);
    }
}
