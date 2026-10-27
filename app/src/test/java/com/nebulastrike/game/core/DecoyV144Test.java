package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.44 迭代 34：诱饵浮标 DECOY。
 *
 * <p>它是一件"布置型"道具：在船上方投下一个全息诱饵。瞄准弹（追猎 / 狙击 / 追猎者 /
 * 缚网 / 相位）会改瞄诱饵，附近的敌弹被吸过去、吞满 12 发就爆散清一圈；
 * 切割者那种"按自己的列结算"的光束不吃这套——这是它和护盾类道具的分界线。
 */
public class DecoyV144Test {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261027L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.ship.x = 450f;
        world.ship.prevX = 450f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
    }

    // ---- 辅助 ----

    /** 把船放到诱饵该出现的位置下方并拾取，返回诱饵落点。 */
    private void deployAt(float x, float y) {
        world.ship.x = x;
        world.ship.prevX = x;
        world.ship.y = y + 170f;
        world.ship.prevY = y + 170f;
        world.spawnPowerUp(PowerUp.Kind.DECOY, x, y + 170f);
        world.update(1f / 60f);
        assertTrue("拾取后诱饵应已部署", world.decoyActive);
    }

    /** 把船挪开：后面的瞄准断言才分得清"瞄诱饵"还是"瞄船"。 */
    private void moveShip(float x, float y) {
        world.ship.x = x;
        world.ship.prevX = x;
        world.ship.y = y;
        world.ship.prevY = y;
    }

    private Enemy enemyAt(EnemyType type, float x, float y, float hp) {
        for (Enemy e : world.enemies) {
            if (e.active) continue;
            e.init(type, x, y, 1f);
            e.x = x;
            e.y = y;
            if (hp > 0f) {
                e.maxHp = hp;
                e.hp = hp;
            }
            return e;
        }
        throw new IllegalStateException("敌机池已满");
    }

    private EnemyBullet enemyBulletAt(float x, float y, float vx, float vy) {
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active) continue;
            b.init(x, y, vx, vy);
            return b;
        }
        throw new IllegalStateException("敌弹池已满");
    }

    private int activeEnemyBullets() {
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active) n++;
        return n;
    }

    private EnemyBullet firstActiveEnemyBullet() {
        for (EnemyBullet b : world.enemyBullets) if (b.active) return b;
        return null;
    }

    /** 把 12 发弹塞到诱饵脸上，一帧内吃满并爆散。 */
    private void forceBurst(float x, float y) {
        for (int i = 0; i < GameWorld.DECOY_MAX_ABSORB; i++) {
            double ang = Math.PI * 2 * i / GameWorld.DECOY_MAX_ABSORB;
            enemyBulletAt(x + (float) Math.cos(ang) * 10f,
                    y + (float) Math.sin(ang) * 10f, 0f, 0f);
        }
        world.update(1f / 60f);
        assertFalse("吃满 12 发应立刻爆散", world.decoyActive);
    }

    private List<GameWorld.GameEvent> step(int frames) {
        List<GameWorld.GameEvent> all = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            world.update(1f / 60f);
            all.addAll(world.pollEvents());
        }
        return all;
    }

    private static GameWorld.GameEvent lastOf(List<GameWorld.GameEvent> events,
                                              GameWorld.EventType type) {
        GameWorld.GameEvent found = null;
        for (GameWorld.GameEvent e : events) if (e.type == type) found = e;
        return found;
    }

    private static float dotToward(float dx, float dy, float vx, float vy) {
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float vl = (float) Math.sqrt(vx * vx + vy * vy);
        if (len < 1e-3f || vl < 1e-3f) return -1f;
        return (dx * vx + dy * vy) / (len * vl);
    }

    // ---- 部署 / 掉落 ----

    @Test
    public void pickupDeploysDecoyAboveShip() {
        deployAt(300f, 900f);

        assertEquals(300f, world.decoyX, 12f);
        assertEquals(900f, world.decoyY, 12f);
        assertEquals(GameWorld.DECOY_LIFE, world.decoyLife, 0.001f);
        assertEquals(0, world.decoyAbsorb);
    }

    @Test
    public void deployEventReportsFullCapacity() {
        world.ship.x = 300f;
        world.ship.prevX = 300f;
        world.ship.y = 1070f;
        world.ship.prevY = 1070f;
        world.spawnPowerUp(PowerUp.Kind.DECOY, 300f, 1070f);
        List<GameWorld.GameEvent> events = step(1);

        GameWorld.GameEvent deploy = lastOf(events, GameWorld.EventType.DECOY_DEPLOY);
        assertNotNull("部署应发出 DECOY_DEPLOY", deploy);
        assertEquals("code = 满耐久能吞的弹数", GameWorld.DECOY_MAX_ABSORB, deploy.code);
        assertEquals(world.decoyX, deploy.x, 0.001f);
    }

    @Test
    public void dropTableCarvesOutDecoySlice() {
        assertEquals(PowerUp.Kind.BOOMERANG, GameWorld.dropKind(0.87f));
        assertEquals(PowerUp.Kind.DECOY, GameWorld.dropKind(0.885f));
        assertEquals(PowerUp.Kind.NOVA, GameWorld.dropKind(0.90f));
        // 核心武器权重不动
        assertEquals(PowerUp.Kind.MISSILE, GameWorld.dropKind(0.30f));
        assertEquals(PowerUp.Kind.ARC, GameWorld.dropKind(0.96f));
    }

    // ---- 诱导瞄准 ----

    @Test
    public void aimedShotsRetargetOntoDecoy() {
        deployAt(300f, 900f);
        moveShip(760f, 1120f);
        Enemy hunter = enemyAt(EnemyType.HUNTER, 300f, 400f, 0f);
        hunter.fireCd = 0.02f;
        step(2);

        EnemyBullet shot = firstActiveEnemyBullet();
        assertNotNull("追猎者应当开火", shot);
        float towardDecoy = dotToward(world.decoyX - shot.x, world.decoyY - shot.y,
                shot.vx, shot.vy);
        float towardShip = dotToward(world.ship.x - shot.x, world.ship.y - shot.y,
                shot.vx, shot.vy);
        assertTrue("弹道应指向诱饵", towardDecoy > 0.98f);
        assertTrue("弹道不该再指向船（" + towardShip + "）", towardShip < 0.95f);
    }

    @Test
    public void aimedShotsReturnToShipAfterBurst() {
        deployAt(300f, 900f);
        moveShip(760f, 1120f);
        Enemy hunter = enemyAt(EnemyType.HUNTER, 300f, 400f, 0f);
        forceBurst(world.decoyX, world.decoyY);
        hunter.fireCd = 0.02f;
        step(2);

        EnemyBullet shot = firstActiveEnemyBullet();
        assertNotNull("诱饵没了，追猎者照常开火", shot);
        float towardShip = dotToward(world.ship.x - shot.x, world.ship.y - shot.y,
                shot.vx, shot.vy);
        assertTrue("诱饵爆散后瞄准点回到船身", towardShip > 0.98f);
    }

    @Test
    public void kineticBeamStillFollowsItsOwnColumn() {
        deployAt(220f, 900f);
        moveShip(600f, 1120f);
        Enemy cutter = enemyAt(EnemyType.CUTTER, 700f, Enemy.CUTTER_HOVER_Y, 0f);
        cutter.fireCd = 0.02f;
        List<GameWorld.GameEvent> events = step(90);

        GameWorld.GameEvent beam = lastOf(events, GameWorld.EventType.ENEMY_BEAM);
        assertNotNull("切割者应落下光束", beam);
        assertTrue("光束应切在船这一侧（" + beam.x + "）",
                Math.abs(beam.x - world.ship.x) < 160f);
        assertTrue("光束不该被诱饵拉走（" + beam.x + "）",
                Math.abs(beam.x - 220f) > Math.abs(beam.x - world.ship.x));
    }

    // ---- 吸弹 / 爆散 ----

    @Test
    public void nearbyBulletsAreAbsorbed() {
        deployAt(300f, 900f);
        moveShip(760f, 1120f);
        EnemyBullet b = enemyBulletAt(310f, 905f, 0f, 0f);
        step(1);

        assertFalse("贴脸的敌弹应被吞掉", b.active);
        assertEquals(1, world.decoyAbsorb);
    }

    @Test
    public void pulledBulletsBendTowardDecoy() {
        deployAt(300f, 900f);
        moveShip(760f, 1120f);
        EnemyBullet b = enemyBulletAt(150f, 900f, 200f, 0f);
        step(6);

        assertTrue("还在磁吸圈里的弹不应被吞掉", b.active);
        assertTrue("横向飞过的弹应被拉向诱饵（vx=" + b.vx + "）", b.vx > 210f);
    }

    @Test
    public void absorbingFullQuotaBurstsAndSweepsWiderRing() {
        deployAt(300f, 900f);
        moveShip(760f, 1120f);
        EnemyBullet far = enemyBulletAt(420f, 900f, 0f, 0f);
        forceBurst(300f, 900f);

        assertEquals("爆散前吃满 12 发", 0, activeEnemyBullets());
        assertFalse("更外圈、没被吸进去的弹也要被冲击波清掉", far.active);
        assertTrue("爆散会留下一段残光", world.decoyFlash > 0f);
    }

    @Test
    public void burstEventReportsHowManyBulletsItAte() {
        world.ship.x = 300f;
        world.ship.prevX = 300f;
        world.ship.y = 1070f;
        world.ship.prevY = 1070f;
        world.spawnPowerUp(PowerUp.Kind.DECOY, 300f, 1070f);
        world.update(1f / 60f);
        world.pollEvents();

        for (int i = 0; i < GameWorld.DECOY_MAX_ABSORB; i++) {
            enemyBulletAt(300f + i * 2f, 900f, 0f, 0f);
        }
        List<GameWorld.GameEvent> events = step(1);

        GameWorld.GameEvent burst = lastOf(events, GameWorld.EventType.DECOY_BURST);
        assertNotNull("吃满应发出 DECOY_BURST", burst);
        assertEquals("code = 本次吞掉的弹数", GameWorld.DECOY_MAX_ABSORB, burst.code);
    }

    @Test
    public void burstDamagesEnemiesInRadius() {
        deployAt(300f, 900f);
        moveShip(760f, 1120f);
        Enemy tank = enemyAt(EnemyType.TANK, 420f, 900f, 200f);
        forceBurst(300f, 900f);

        assertEquals("贴脸敌机吃 2 点爆散伤害", 198f, tank.hp, 0.001f);
    }

    @Test
    public void absorbScoresPoints() {
        deployAt(300f, 900f);
        moveShip(760f, 1120f);
        int before = world.score;
        enemyBulletAt(305f, 902f, 0f, 0f);
        step(1);

        assertTrue("吞弹应给分", world.score > before);
    }

    @Test
    public void expiresOnItsOwnAndBursts() {
        deployAt(300f, 900f);
        moveShip(760f, 1120f);

        step((int) (GameWorld.DECOY_LIFE * 60f) - 4);
        assertTrue("寿命没到之前仍在场", world.decoyActive);

        step(10);
        assertFalse("到期自然爆散", world.decoyActive);
    }

    @Test
    public void restartClearsDecoyState() {
        deployAt(300f, 900f);
        forceBurst(300f, 900f);

        world.restart(GameMode.WAVES);

        assertFalse("重开后诱饵不应残留", world.decoyActive);
        assertEquals(0, world.decoyAbsorb);
        assertEquals(0f, world.decoyLife, 0.001f);
    }
}
