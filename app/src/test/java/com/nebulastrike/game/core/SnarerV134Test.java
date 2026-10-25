package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.34 迭代 24：缚网者 SNARER 与减速网弹。
 *
 * <p>缚网者补的是"不致命但会变钝"的压力：网弹命中不扣命，
 * 只把拖动速度压到 45%、封锁冲刺 2 秒，而且可以被玩家子弹点掉。
 * 这组测试钉住"不致死、可反制、会结束"三条边界，避免它退化成隐形的地雷。
 */
public class SnarerV134Test {

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
        world.ship.invulnerable = false;
        world.ship.webTimer = 0f;
        events.clear();
    }

    private Enemy snarerAt(float x, float y) {
        for (Enemy e : world.enemies) {
            if (e.active) continue;
            e.init(EnemyType.SNARER, x, y, 1f);
            return e;
        }
        throw new IllegalStateException("敌机池已满");
    }

    private EnemyBullet idleWebOrb() {
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active) continue;
            b.initWeb(100f, 300f, 0f, 0f, 5f);
            return b;
        }
        throw new IllegalStateException("敌弹池已满");
    }

    private int activeWebOrbs() {
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active && b.web) n++;
        return n;
    }

    private void stepWorld(int frames) {
        for (int i = 0; i < frames; i++) {
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
        }
    }

    private void stepEnemy(Enemy e, int frames) {
        for (int i = 0; i < frames; i++) e.update(1f / 60f, GameWorld.WIDTH, 450f, 1460f);
    }

    // ---- 缚网者本体 ----

    @Test
    public void snarerHoversAndSweepsAcrossTheMidfield() {
        Enemy e = snarerAt(450f, 100f);
        stepEnemy(e, 300);
        assertTrue("缚网者没有压到悬停高度", e.y >= Enemy.SNARER_HOVER_Y - 0.5f);
        assertTrue("缚网者冲过了悬停高度", e.y < Enemy.SNARER_HOVER_Y + 6f);
        assertTrue("缚网者没有横向摆渡", Math.abs(e.x - 450f) > 10f);
        assertTrue("摆渡幅度超出设定", Math.abs(e.x - 450f) <= Enemy.SNARER_SWING + 1f);
        assertEquals(EnemyType.SNARER, e.type);
    }

    @Test
    public void snarerLeavesAfterItsHoverTimerRunsOut() {
        Enemy e = snarerAt(450f, Enemy.SNARER_HOVER_Y);
        stepEnemy(e, (int) (Enemy.SNARER_HOVER * 60f) + 60);
        assertTrue("驻留到期后缚网者没有离场", e.y > Enemy.SNARER_HOVER_Y + 20f);
    }

    // ---- 网弹 ----

    @Test
    public void snarerThrowsTwoSlowWebOrbsThatAreNotLethal() {
        Enemy e = snarerAt(450f, Enemy.SNARER_HOVER_Y);
        e.fireCd = 0.0001f;
        stepWorld(1);

        assertEquals("缚网者应当一次抛两枚网弹", 2, activeWebOrbs());
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active || !b.web) continue;
            float speed = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
            assertEquals("网弹速度不对", GameWorld.WEB_ORB_SPEED, speed, 0.5f);
            assertEquals("网弹寿命不对", GameWorld.WEB_ORB_LIFE, b.life, 0.05f);
        }
        assertEquals("网弹不该在飞行途中扣命", 999_999, world.lives);
        assertEquals("网弹不该给飞船挂减速", 0f, world.ship.webTimer, 0.0001f);
    }

    @Test
    public void webOrbSnareSlowsTheShipAndBlocksDashWithoutCostingALife() {
        EnemyBullet orb = idleWebOrb();
        // 放在飞船正下方：玩家自动射击朝上，不会先把它点掉
        orb.x = world.ship.x;
        orb.y = world.ship.y + 40f;
        orb.vx = 0f;
        orb.vy = 0f;
        int livesBefore = world.lives;
        stepWorld(1);

        assertFalse("网弹命中后应当消失", orb.active);
        assertEquals("被网缠住不该扣命", livesBefore, world.lives);
        assertEquals("减速时长不对", GameWorld.WEB_SNARE_TIME, world.ship.webTimer, 0.05f);
        assertTrue("被网缠住时应当无法冲刺", !world.ship.canDash());
        assertFalse("被网缠住时 tryDash 必须失败", world.ship.tryDash());

        int snareEvents = 0;
        for (GameWorld.GameEvent ev : events) {
            if (ev.type == GameWorld.EventType.WEB_SNARE) {
                snareEvents++;
                assertEquals("事件要带减速时长", (int) (GameWorld.WEB_SNARE_TIME * 100f), ev.code);
            }
        }
        assertEquals("被网缠住没有广播 WEB_SNARE", 1, snareEvents);
    }

    @Test
    public void webSlowReducesDragSpeedAndRecoversWhenItExpires() {
        Ship s = new Ship();
        s.reset(200f, 900f);
        s.setTarget(700f, 900f);
        for (int i = 0; i < 10; i++) s.update(1f / 60f, GameWorld.WIDTH, GameWorld.HEIGHT);
        float normal = s.x - 200f;
        assertTrue("正常拖动速度低于预期", normal > 380f);

        s.reset(200f, 900f);
        s.applyWeb(2f);
        s.setTarget(700f, 900f);
        for (int i = 0; i < 10; i++) s.update(1f / 60f, GameWorld.WIDTH, GameWorld.HEIGHT);
        float webbed = s.x - 200f;
        assertTrue("被网缠住时没有明显变慢", webbed < normal * 0.55f);

        for (int i = 0; i < 140; i++) s.update(1f / 60f, GameWorld.WIDTH, GameWorld.HEIGHT);
        assertEquals("减速到期后应当恢复", 0f, s.webTimer, 0.0001f);
        assertTrue("减速到期后应当可以冲刺", s.canDash());
    }

    @Test
    public void webOrbsExpireInsteadOfLitteringTheField() {
        EnemyBullet orb = idleWebOrb();
        orb.life = 1f;
        stepWorld(50);
        assertTrue("网弹在寿命内提前消失", orb.active);
        stepWorld(15);
        assertFalse("网弹寿命到期后没有自行消散", orb.active);
    }

    @Test
    public void webOrbsDoNotAwardGrazeCharge() {
        EnemyBullet orb = idleWebOrb();
        orb.x = world.ship.x;
        orb.y = world.ship.y + 62f;   // 擦弹带内、命中圈外
        orb.vx = 0f;
        orb.vy = 0f;
        int grazesBefore = world.grazes;
        stepWorld(1);
        assertEquals("网弹不该提供擦弹收益", grazesBefore, world.grazes);
    }

    @Test
    public void webOrbsCanBeShotDown() {
        EnemyBullet orb = idleWebOrb();
        orb.x = 300f;
        orb.y = 700f;
        orb.vx = 0f;
        orb.vy = 0f;
        Bullet shot = null;
        for (Bullet b : world.bullets) {
            if (b.active) continue;
            shot = b;
            break;
        }
        assertTrue("没有空闲玩家弹", shot != null);
        shot.init(300f, 706f, 0f, -700f, 2, false);
        int scoreBefore = world.score;
        stepWorld(1);
        assertFalse("网弹应当可以被点掉", orb.active);
        assertTrue("点掉网弹应当有分数", world.score > scoreBefore);
    }

    // ---- 图鉴 ----

    @Test
    public void snarerJoinsTheCodexWithoutOpeningANewPage() {
        assertEquals(EnemyType.SNARER, EnemyType.values()[EnemyType.values().length - 1]);
        assertTrue("缚网者应当算后期兵种", EnemyType.SNARER.isAdvanced());
        assertTrue(EnemyType.SNARER.hp > 0 && EnemyType.SNARER.score > 0);
        int types = EnemyType.values().length;
        assertEquals("18 种敌机仍应在两页内", 2, Ui.codexPageCount(types));
        assertTrue(Ui.codexPageCount(types) * Ui.CODEX_MAX_ROWS >= types);
    }
}
