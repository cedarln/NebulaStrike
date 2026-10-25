package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.33 迭代 23：追猎者 SEEKER 与追踪弹。
 *
 * <p>追猎者的威胁不在单发伤害，而在"逼你一直横移"：
 * 它悬停在弹幕层上方、横向跟船但比玩家慢，发射的追踪弹转向率和寿命都有限。
 * 这组测试钉住三件事：能跟但能甩、双发且会预告、寿命到点必自毁。
 * 顺带钉住图鉴分页——敌机涨到 17 种后，旧的一页到底会压到返回键。
 */
public class SeekerV133Test {

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

    private Enemy seekerAt(float x, float y) {
        for (Enemy e : world.enemies) {
            if (e.active) continue;
            e.init(EnemyType.SEEKER, x, y, 1f);
            return e;
        }
        throw new IllegalStateException("敌机池已满");
    }

    private EnemyBullet homingBullet() {
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.homing) return b;
        }
        return null;
    }

    private int activeHoming() {
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active && b.homing) n++;
        return n;
    }

    private void stepEnemy(Enemy e, int frames, float shipX) {
        for (int i = 0; i < frames; i++) e.update(1f / 60f, GameWorld.WIDTH, shipX, 1460f);
    }

    private void stepWorld(int frames) {
        for (int i = 0; i < frames; i++) {
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
        }
    }

    // ---- 追猎者本体 ----

    @Test
    public void seekerDropsToHoverAltitudeAndStaysThere() {
        Enemy e = seekerAt(450f, 80f);
        stepEnemy(e, 240, 450f);
        assertTrue("追猎者没有压到悬停高度", e.y >= Enemy.SEEKER_HOVER_Y - 0.5f);
        assertTrue("追猎者冲过了悬停高度", e.y < Enemy.SEEKER_HOVER_Y + 6f);
        assertTrue("悬停计时没有开始流逝", e.hoverCd < Enemy.SEEKER_HOVER);
        assertEquals(EnemyType.SEEKER, e.type);
    }

    @Test
    public void seekerTracksTheShipButCanBeOutrun() {
        Enemy e = seekerAt(450f, Enemy.SEEKER_HOVER_Y);
        stepEnemy(e, 120, 800f);
        float right = e.x;
        assertTrue("追猎者没有向右侧跟船", right > 520f);
        // 每秒最多追 120px：两秒还追不到半个屏幕——横向拉开确实能把它甩开
        assertTrue("追猎者跟船太快，玩家无法甩开", right < 760f);

        stepEnemy(e, 180, 80f);
        assertTrue("追猎者不会反向跟船", e.x < right - 150f);
    }

    @Test
    public void seekerStaysInsideThePlayfield() {
        Enemy e = seekerAt(450f, Enemy.SEEKER_HOVER_Y);
        e.hoverCd = 999f;   // 只验证边界钳制，不让驻留计时提前结束
        stepEnemy(e, 400, 5000f);
        assertTrue("追猎者冲出右边界", e.x + e.type.radius <= GameWorld.WIDTH);
        stepEnemy(e, 400, -5000f);
        assertTrue("追猎者冲出左边界", e.x - e.type.radius >= 0f);
    }

    @Test
    public void seekerLeavesAfterItsHoverTimerRunsOut() {
        Enemy e = seekerAt(450f, Enemy.SEEKER_HOVER_Y);
        stepEnemy(e, (int) (Enemy.SEEKER_HOVER * 60f) + 60, 450f);
        assertTrue("悬停到期后追猎者没有俯冲离场", e.y > Enemy.SEEKER_HOVER_Y + 20f);
    }

    // ---- 追踪弹 ----

    @Test
    public void seekerFiresTwoHomingMissilesAndAnnouncesTheVolley() {
        Enemy e = seekerAt(450f, Enemy.SEEKER_HOVER_Y);
        e.fireCd = 0.0001f;
        stepWorld(1);

        assertEquals("追猎者应当一次放两枚追踪弹", 2, activeHoming());
        int launches = 0;
        for (GameWorld.GameEvent ev : events) {
            if (ev.type == GameWorld.EventType.HOMING_LAUNCH) {
                launches++;
                assertEquals("事件要带本次发射数", 2, ev.code);
            }
        }
        assertEquals("追猎者发射没有广播 HOMING_LAUNCH", 1, launches);

        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active || !b.homing) continue;
            float speed = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
            assertEquals("追踪弹初速不对", GameWorld.HOMING_SPEED, speed, 0.5f);
            assertEquals("追踪弹寿命不对", GameWorld.HOMING_LIFE, b.life, 0.05f);
            // 初速朝飞船：速度向量应当指向下方（飞船在下面）
            assertTrue("追踪弹初速没有朝飞船", b.vy > 0f);
        }
    }

    @Test
    public void homingMissileSteersTowardTheShipWithoutChangingSpeed() {
        EnemyBullet b = null;
        for (EnemyBullet candidate : world.enemyBullets) {
            if (candidate.active) continue;
            b = candidate;
            break;
        }
        assertTrue("没有空闲敌弹", b != null);
        b.initHoming(450f, 800f, 0f, GameWorld.HOMING_SPEED,
                GameWorld.HOMING_TURN_RATE, GameWorld.HOMING_LIFE);
        // 飞船在右下：直落的弹要先向右拐
        for (int i = 0; i < 90; i++) {
            assertTrue("追踪弹提前自毁", b.updateHoming(1f / 60f, 760f, 1400f));
        }
        assertTrue("追踪弹没有向飞船转向", b.vx > 40f);
        float speed = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
        assertEquals("转向不该改变弹速", GameWorld.HOMING_SPEED, speed, 0.5f);
    }

    @Test
    public void homingMissileExpiresAfterItsLifetime() {
        EnemyBullet b = null;
        for (EnemyBullet candidate : world.enemyBullets) {
            if (candidate.active) continue;
            b = candidate;
            break;
        }
        assertTrue("没有空闲敌弹", b != null);
        b.initHoming(100f, 800f, 0f, GameWorld.HOMING_SPEED,
                GameWorld.HOMING_TURN_RATE, GameWorld.HOMING_LIFE);

        int frames = 0;
        boolean alive = true;
        while (alive && frames < 600) {
            alive = b.updateHoming(1f / 60f, 700f, 1400f);
            frames++;
        }
        assertFalse("追踪弹永远不会自毁", alive);
        assertTrue("追踪弹寿命比设定长太多",
                frames <= Math.ceil(GameWorld.HOMING_LIFE * 60f) + 2);
        assertTrue("寿命耗尽后 life 应为非正数", b.life <= 0f);
    }

    @Test
    public void ordinaryBulletsAreNotAffectedByHomingFields() {
        EnemyBullet b = null;
        for (EnemyBullet candidate : world.enemyBullets) {
            if (candidate.active) continue;
            b = candidate;
            break;
        }
        assertTrue("没有空闲敌弹", b != null);
        b.init(450f, 800f, 0f, 300f);
        assertFalse("普通弹被误标成追踪弹", b.homing);
        b.update(1f / 60f);
        assertEquals("普通弹的横向速度被改写了", 0f, b.vx, 0.0001f);
        assertTrue(b.y > 800f);
    }

    // ---- 图鉴分页 ----

    @Test
    public void codexPaginationCoversEveryEnemyType() {
        int types = EnemyType.values().length;
        int pages = Ui.codexPageCount(types);
        assertTrue("追猎者之后图鉴应当需要分页", pages >= 2);
        assertTrue("分页容量不足以装下全部敌机", pages * Ui.CODEX_MAX_ROWS >= types);
        assertTrue("上一页的容量装不完时不该多开页",
                (pages - 1) * Ui.CODEX_MAX_ROWS < types);
        assertEquals(Ui.codexPageCount(types), world.codexPageCount());
        assertEquals(1, Ui.codexPageCount(0));
        assertEquals(1, Ui.codexPageCount(Ui.CODEX_MAX_ROWS));
        assertEquals(2, Ui.codexPageCount(Ui.CODEX_MAX_ROWS + 1));
    }

    @Test
    public void codexPageButtonsClampAndBringBackTheEnemyTab() {
        world.state = GameWorld.State.MENU;
        world.handleUiAction(Ui.Action.OPEN_CODEX);
        assertTrue(world.codexOpen);
        assertEquals(0, world.codexPage);

        world.handleUiAction(Ui.Action.CODEX_NEXT);
        assertEquals(1, world.codexPage);
        world.handleUiAction(Ui.Action.CODEX_NEXT);
        assertEquals("已经在最后一页，不应继续翻", 1, world.codexPage);

        world.handleUiAction(Ui.Action.CODEX_TAB);
        assertEquals(1, world.codexTab);
        world.handleUiAction(Ui.Action.CODEX_NEXT);
        assertEquals("翻页应从 Boss 页带回敌机页", 0, world.codexTab);
        assertEquals(1, world.codexPage);

        world.handleUiAction(Ui.Action.CODEX_PREV);
        assertEquals(0, world.codexPage);
        world.handleUiAction(Ui.Action.CODEX_PREV);
        assertEquals("已经在第一页，不应继续翻", 0, world.codexPage);
    }

    @Test
    public void codexPageButtonsAreHittable() {
        assertEquals(Ui.Action.CODEX_NEXT, Ui.hit(GameWorld.State.MENU, false, false,
                true, false, false, false,
                Ui.CODEX_NEXT.x + 10f, Ui.CODEX_NEXT.y + 10f));
        assertEquals(Ui.Action.CODEX_PREV, Ui.hit(GameWorld.State.MENU, false, false,
                true, false, false, false,
                Ui.CODEX_PREV.x + 10f, Ui.CODEX_PREV.y + 10f));
    }
}
