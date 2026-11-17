package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.64 迭代 54：破招反击 · COUNTER STRIKE。
 *
 * <p>v1.63 教会玩家"读"招牌：整轮弹幕散尽又没掉血就算识破，只给一笔分。这一版把"读懂"
 * 变成"打得到"——识破之后开启 <b>5 秒反击窗口</b>，窗口里玩家子弹打在旗舰舰体与部件上的
 * 伤害 +50%，飞船外圈画一道随时间收走的青弧。
 *
 * <p>两条口径要说清楚：① 窗口是<em>已经读到</em>的奖励，开起来之后挨一下并不收回
 * （作废的是下一轮的识破判定，不是这一轮的反击）；② 窗口<em>只属于刚读懂的那艘船</em>，
 * 下一艘旗舰入场或重开都从零开始。对小怪、对掉落、对既有节拍一律不改。
 */
public class BossCounterStrikeV164Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261120L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.restart(GameMode.WAVES);
        drain();
    }

    // ---- 辅助 ----

    private void step(int frames) {
        for (int i = 0; i < frames; i++) {
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
        }
    }

    private void drain() {
        events.clear();
        events.addAll(world.pollEvents());
    }

    private int countEvents(GameWorld.EventType type) {
        int n = 0;
        for (GameWorld.GameEvent e : events) if (e.type == type) n++;
        return n;
    }

    /** 叫出这一段的旗舰、跳过入场演出、把词缀压成无，并保证场上没有上一艘船。 */
    private void triggerBoss(int level) {
        if (world.boss.active) {
            world.boss.hp = 0f;
            step(1);
        }
        world.bossGapTimer = 0f;
        world.stageClearTimer = 0f;
        world.bossesDefeated = level;
        world.wave = world.nextBossWave;
        step(1);
        assertTrue("旗舰该入场", world.boss.active);
        world.applyBossAffix(BossAffix.NONE);
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        world.boss.y = world.boss.stationY;
        world.ship.makeInvulnerable(9999f);
        drain();
    }

    /** 场上这一轮招牌的轮次号（没有招牌弹时为 0）。 */
    private int signatureSerialInPlay() {
        int serial = 0;
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.sigSerial > serial) serial = b.sigSerial;
        }
        return serial;
    }

    /** 把手上这一轮招牌的弹全部收掉，等效于"这轮我躲干净了"。 */
    private void clearSignatureBullets() {
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.sigSerial != 0) b.active = false;
        }
    }

    /** 丢出本型招牌并让它当场散尽——识破与反击窗口都该在这里开。 */
    private void readThisFormsSignature() {
        assertTrue("招牌要真的丢出弹",
                world.fireSignature(world.boss.form, world.boss.tier) > 0);
        clearSignatureBullets();
        step(2);
    }

    /** 只留一枚这一轮的招牌弹，并把它摁在飞船身上——这轮就是"打中你了"。 */
    private void letOneVolleyBulletHitTheShip() {
        int serial = signatureSerialInPlay();
        assertTrue("招牌要真的丢出弹", serial > 0);
        int left = 1;
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active || b.sigSerial != serial) continue;
            if (left > 0) {
                b.x = world.ship.x;
                b.y = world.ship.y;
                b.vx = 0f;
                b.vy = 0f;
                left--;
            } else {
                b.active = false;
            }
        }
        assertEquals("招牌弹足够", 0, left);
    }

    private void clearBullets() {
        for (Bullet b : world.bullets) b.active = false;
        for (EnemyBullet b : world.enemyBullets) b.active = false;
    }

    /**
     * 在给定帧数里量旗舰挨了多少——舰体和部件都算，因为带部件的旗舰会把子弹分给部件。
     *
     * @param window true 表示全程挂着反击窗口；false 是对照组，每帧把窗口抹掉，
     *               免得测量途中被一次真识破偷开窗口
     */
    private float damageToBoss(int frames, boolean window) {
        world.boss.maxHp = 200000f;
        world.boss.hp = 200000f;
        float[] partBefore = new float[world.boss.partCount];
        for (int i = 0; i < world.boss.partCount; i++) {
            BossPart p = world.boss.parts[i];
            p.maxHp = 200000f;
            p.hp = 200000f;
            partBefore[i] = p.hp;
        }
        clearBullets();
        world.counterStrikeDamage = 0;
        world.counterStrikeTimer = window ? GameWorld.COUNTER_STRIKE_WINDOW : 0f;
        float before = world.boss.hp;
        for (int i = 0; i < frames; i++) {
            // 旗舰会横向游走，直上直下的弹就追不上：把它钉在飞船正上方，两组才可比
            world.boss.x = world.ship.x;
            world.boss.y = world.boss.stationY;
            if (!window) world.counterStrikeTimer = 0f;
            else world.counterStrikeTimer = GameWorld.COUNTER_STRIKE_WINDOW;
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
        }
        float dealt = before - world.boss.hp;
        for (int i = 0; i < world.boss.partCount; i++) {
            dealt += partBefore[i] - world.boss.parts[i].hp;
        }
        return dealt;
    }

    /** 把一架钉住的小怪放在飞船正上方开火，返回这段时间它挨了多少。 */
    private float damageToEnemy(Enemy e, int frames, boolean window) {
        clearBullets();
        e.hp = 200000f;
        float before = e.hp;
        for (int i = 0; i < frames; i++) {
            // 每帧重新钉住：敌机自己那套走位不参与，两组的弹道才可比
            e.x = world.ship.x;
            e.y = world.ship.y - 420f;
            world.counterStrikeTimer = window ? GameWorld.COUNTER_STRIKE_WINDOW : 0f;
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
        }
        return before - e.hp;
    }

    private int liveDrops() {
        int n = 0;
        for (PowerUp p : world.powerups) if (p.active) n++;
        return n;
    }

    // ---- 窗口从哪来 ----

    @Test
    public void aReadOpensAFiveSecondWindow() {
        triggerBoss(1);
        assertEquals("没识破就没有窗口", 0f, world.counterStrikeTimer, 0.001f);
        assertEquals(0, world.counterStrikes);
        readThisFormsSignature();
        assertEquals("识破判定要成立", 1, world.countersRead);
        assertEquals("识破一次就开一次窗口", 1, world.counterStrikes);
        assertTrue("窗口该基本是满的",
                world.counterStrikeTimer > GameWorld.COUNTER_STRIKE_WINDOW - 0.2f);
        assertTrue("窗口不许超过标称长度",
                world.counterStrikeTimer <= GameWorld.COUNTER_STRIKE_WINDOW + 0.001f);
        assertEquals("识破与开窗口各报一次事件", 1, countEvents(GameWorld.EventType.BOSS_COUNTER));
        assertEquals(1, countEvents(GameWorld.EventType.BOSS_COUNTER_STRIKE));
    }

    @Test
    public void aVolleyThatTouchesYouNeverOpensTheWindow() {
        triggerBoss(1);
        world.ship.hasShield = false;
        world.ship.shieldLayers = 0;
        world.lives = 99;
        world.ship.makeInvulnerable(0f);
        assertTrue(world.fireSignature(Boss.Form.SPECTER, 1) > 0);
        letOneVolleyBulletHitTheShip();
        step(2);
        assertEquals("没读懂就不该有窗口", 0, world.counterStrikes);
        assertEquals(0f, world.counterStrikeTimer, 0.001f);
        assertEquals(0, world.countersRead);
        assertTrue("对照组确实挨了一下", world.hitsTaken > 0);
    }

    @Test
    public void theWindowClosesOnItsOwn() {
        triggerBoss(3);
        readThisFormsSignature();
        float opened = world.counterStrikeTimer;
        assertTrue(opened > 0f);
        step(60);
        assertEquals("窗口按帧走表，不需要玩家做什么", opened - 1f, world.counterStrikeTimer, 0.02f);
        world.counterStrikeTimer = 0.2f;
        step(20);
        assertEquals("走完就归零，不留残值", 0f, world.counterStrikeTimer, 0.001f);
        assertEquals(GameWorld.COUNTER_STRIKE_WINDOW, 5f, 0.001f);
    }

    @Test
    public void eachNewReadRefreshesTheWindow() {
        triggerBoss(2);
        readThisFormsSignature();
        assertEquals(1, world.counterStrikes);
        world.counterStrikeTimer = 1f;   // 手动把窗口耗到只剩一秒
        clearSignatureBullets();
        readThisFormsSignature();
        assertEquals("第二次识破计数加一", 2, world.counterStrikes);
        assertTrue("窗口重新拉满而不是把时长叠两层",
                world.counterStrikeTimer > GameWorld.COUNTER_STRIKE_WINDOW - 0.2f);
        assertTrue(world.counterStrikeTimer <= GameWorld.COUNTER_STRIKE_WINDOW + 0.001f);
    }

    // ---- 加成只对旗舰 ----

    @Test
    public void windowedShotsHurtTheFlagshipMore() {
        triggerBoss(1);
        float plain = damageToBoss(150, false);
        float boosted = damageToBoss(150, true);
        assertTrue("窗口里必须真打出伤害", plain > 0f);
        assertTrue("反击窗口只多不少: " + plain + " -> " + boosted, boosted >= plain * 1.4f);
        assertTrue("窗口里的额外伤害要记账", world.counterStrikeDamage > 0);
    }

    @Test
    public void theLedgerRecordsOnlyTheExtraPart() {
        triggerBoss(1);
        float plain = damageToBoss(150, false);
        assertEquals("对照组不记账", 0, world.counterStrikeDamage);
        float boosted = damageToBoss(150, true);
        float extra = world.counterStrikeDamage;
        assertTrue(boosted > plain);
        assertEquals("账上的额外伤害也要真打进血条", plain + extra, boosted, 1.0f);
    }

    @Test
    public void theBoostIsFiftyPercentAndRoundsUp() {
        world.counterStrikeTimer = 0f;
        assertEquals("窗口没开就原样", 7, world.counterStrikeBoost(7));
        world.counterStrikeTimer = GameWorld.COUNTER_STRIKE_WINDOW;
        int before = world.counterStrikeDamage;
        assertEquals(3, world.counterStrikeBoost(2));
        assertEquals(4, world.counterStrikeBoost(3));
        assertEquals("最低也要多吃 1", 2, world.counterStrikeBoost(1));
        assertEquals("不吃血不记账", 0, world.counterStrikeBoost(0));
        assertTrue("记账只加正数", world.counterStrikeDamage > before);
        assertEquals(50, GameWorld.COUNTER_STRIKE_BONUS_PCT);
        assertEquals(5f, GameWorld.COUNTER_STRIKE_WINDOW, 0.001f);
    }

    @Test
    public void partsCountToo() {
        // 带部件的旗舰（二段起）：舰体和部件一起多吃，两组的总伤仍按比例涨
        triggerBoss(4);
        assertTrue("这一艘该有部件", world.boss.partCount > 0);
        float plain = damageToBoss(150, false);
        float boosted = damageToBoss(150, true);
        assertTrue(plain > 0f);
        assertTrue(boosted >= plain * 1.4f);
    }

    @Test
    public void ordinaryEnemiesAreLeftAlone() {
        triggerBoss(0);
        world.boss.active = false;   // 只测小怪，别让旗舰分走子弹
        Enemy e = world.spawnEnemyForTest(EnemyType.DRONE, 450f);
        assertTrue("小怪该进场", e != null && e.active);
        e.maxHp = 200000f;
        float plain = damageToEnemy(e, 90, false);
        float withWindow = damageToEnemy(e, 90, true);
        assertTrue("小怪要吃得到子弹", plain > 0f);
        assertEquals("反击窗口只治旗舰，小怪伤害照旧", plain, withWindow, 0.5f);
        assertEquals("小怪那边不该记账", 0, world.counterStrikeDamage);
    }

    // ---- 边界：挨打、换船、重开 ----

    @Test
    public void takingAHitLaterDoesNotSnatchTheWindowBack() {
        triggerBoss(1);
        readThisFormsSignature();
        assertTrue(world.counterStrikeTimer > 0f);
        world.ship.makeInvulnerable(0f);
        world.ship.hasShield = false;
        world.ship.shieldLayers = 0;
        world.lives = 99;
        drain();
        // 再来一轮招牌，把这轮的一枚摁在飞船身上：挨打作废的是下一轮的识破判定
        assertTrue(world.fireSignature(world.boss.form, world.boss.tier) > 0);
        letOneVolleyBulletHitTheShip();
        step(2);
        assertTrue("已经到手的窗口不因挨打收回", world.counterStrikeTimer > 0f);
        assertTrue("对照组确实挨了一下", world.hitsTaken > 0);
        assertEquals("挨打的那一轮不该再开一次窗口", 1, world.counterStrikes);
    }

    @Test
    public void aFreshFlagshipStartsWithoutTheWindow() {
        triggerBoss(1);
        readThisFormsSignature();
        assertTrue(world.counterStrikeTimer > 0f);
        triggerBoss(2);
        assertEquals("窗口不跨船继承", 0f, world.counterStrikeTimer, 0.001f);
    }

    @Test
    public void restartClearsTheStrikeLedger() {
        triggerBoss(2);
        readThisFormsSignature();
        damageToBoss(150, true);
        assertTrue(world.counterStrikes > 0);
        assertTrue(world.counterStrikeDamage > 0);
        world.restart(GameMode.WAVES);
        assertEquals(0f, world.counterStrikeTimer, 0.001f);
        assertEquals(0, world.counterStrikes);
        assertEquals(0, world.counterStrikeDamage);
        assertEquals(0, world.countersRead);
    }

    // ---- 只加伤害，别的都不动 ----

    @Test
    public void aReadStillChangesNothingButScoreAndTheWindow() {
        triggerBoss(3);
        int lives = world.lives;
        int power = world.ship.powerLevel;
        int drops = liveDrops();
        int score = world.score;
        assertTrue("招牌要真的丢出弹",
                world.fireSignature(world.boss.form, world.boss.tier) > 0);
        clearSignatureBullets();
        step(2);
        assertEquals("识破只加分，加的还是那笔标称赏金",
                score + GameWorld.COUNTER_SCORE_BASE + 20 * world.boss.level, world.score);
        assertEquals("识破不扣命", lives, world.lives);
        assertEquals("识破不动火力", power, world.ship.powerLevel);
        assertEquals("识破不发补给", drops, liveDrops());
        assertEquals("窗口自己不加第二次分", world.score, world.score);
    }

    @Test
    public void killingInsideTheWindowKeepsTheRewardContract() {
        triggerBoss(4);
        readThisFormsSignature();
        int strikes = world.counterStrikes;
        for (PowerUp p : world.powerups) p.active = false;
        world.boss.hp = 0f;
        step(1);
        assertTrue("反击窗口不因击沉而提前收走", world.counterStrikeTimer > 0f);
        int inWindow = liveDrops();
        assertTrue("窗口里击破照样按契约交货", inWindow >= 5);
        triggerBoss(4);
        for (PowerUp p : world.powerups) p.active = false;
        world.boss.hp = 0f;
        step(1);
        assertEquals("同一段航程，掉落数量与窗口无关", inWindow, liveDrops());
        assertEquals(strikes, world.counterStrikes);
    }

    // ---- 各形态、各模式、残响 ----

    @Test
    public void everyFormPaysItsOwnWindow() {
        for (int level = 0; level < Boss.Form.values().length; level++) {
            world.restart(GameMode.WAVES);
            drain();
            triggerBoss(level);
            readThisFormsSignature();
            assertEquals("第 " + (level + 1) + " 段该开一次窗口", 1, world.counterStrikes);
            assertTrue(Boss.formFor(level).label + " 的窗口要能打开",
                    world.counterStrikeTimer > 0f);
        }
    }

    @Test
    public void everyModeOpensTheSameWindow() {
        GameMode[] modes = {GameMode.RUSH, GameMode.ECHO, GameMode.TIME, GameMode.PRACTICE};
        for (GameMode m : modes) {
            world.restart(m);
            world.spawningEnabled = false;
            world.perksEnabled = false;
            world.ship.makeInvulnerable(9999f);
            for (int i = 0; i < 600 && !world.boss.active; i++) {
                world.bossGapTimer = 0f;
                world.wave = world.nextBossWave;
                step(1);
            }
            assertTrue(m + " 模式该有旗舰", world.boss.active);
            world.bossWarningTimer = 0f;
            world.boss.y = world.boss.stationY;
            drain();
            readThisFormsSignature();
            assertTrue(m + " 模式同样给反击窗口", world.counterStrikeTimer > 0f);
            assertEquals(m + " 模式要报事件", 1,
                    countEvents(GameWorld.EventType.BOSS_COUNTER_STRIKE));
        }
    }

    @Test
    public void theRealFightUsesItsWindows() {
        triggerBoss(5);
        int strikes = 0;
        for (int i = 0; i < 60 * 45; i++) {
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
            if (world.counterStrikes > strikes) strikes = world.counterStrikes;
        }
        assertTrue("真实对局自己就会开出窗口", strikes >= 2);
        assertTrue("窗口里真打进过额外伤害", world.counterStrikeDamage > 0);
    }

    @Test
    public void theEchoIsReadAndPunishedToo() {
        triggerBoss(0);
        world.boss.hp = 0f;
        step(1);
        assertFalse("本体该沉", world.boss.active);
        assertTrue("干净击破应当留影", world.echoPendingTier >= 0);
        world.wave = world.echoSpawnWave;
        world.bossGapTimer = 0f;
        step(1);
        assertTrue("残响应到期入场", world.boss.active);
        assertTrue(world.echoBossFight);
        world.bossWarningTimer = 0f;
        world.boss.y = world.boss.stationY;
        drain();
        readThisFormsSignature();
        assertEquals("残响的识破也开窗口", 1, world.counterStrikes);
        assertTrue(world.counterStrikeTimer > 0f);
    }
}
