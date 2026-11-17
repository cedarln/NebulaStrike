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
 * v1.72 迭代 62：拆穿即哑火 · BREACH SILENCE。
 *
 * <p>v1.71 给"顺着舰装拆"记的是一本分数账，读透了也只是多拿几千分。这一版把那笔账兑成战场上的东西：
 * 整条攻坚路线<b>一件没插队</b>地拆空 = 这艘船被顺着它的软肋拆开了，它下一手招牌攒不出力，
 * 那一拍真的什么都没有。
 *
 * <p>四条硬口径：① 哑火只在<b>调度层</b>兑现——吃的就是"这一拍该出招牌"这件事，
 * 常规扩散环、弹幕网、增援、冲撞一概照旧，它不会把一场 Boss 战按暂停；
 * ② 一次拆穿只攒一记，吃完就没了，也不会自己补仓；③ 哑火<b>不产生任何敌弹、不给任何分、
 * 不碰识破那本账</b>（招牌没打出来，就没有可读的一手）；④ 它是上一版大奖的另一半：
 * 攒上（armed）的条件与大奖完全同一条，插过队就两样都没有。
 */
public class BossBreachSilenceV172Test {

    /** 舰级 0..3 的部件件数，沿用 v1.70/v1.71。 */
    private static final int[] COUNT_BY_TIER = {2, 6, 9, 12};
    private static final int FULL_TIER = 3;
    /** 招牌最迟几拍出一次（一阶段每 4 拍、二阶段起每 5 拍），留一倍余量。 */
    private static final int BEAT_LIMIT = 12;

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261127L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.restart(GameMode.WAVES);
        events.clear();
        drain();
    }

    // ---- 辅助：入场、钉船、按路线拆件（沿用 v1.71 那套量法） ----

    private void drain() {
        world.pollEvents();
    }

    private Boss triggerBoss(int level) {
        if (world.boss.active) {
            world.boss.hp = 0f;
            step(2);
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
        world.ship.makeInvulnerable(9999f);
        parkBoss();
        drain();
        return world.boss;
    }

    private void parkBoss() {
        world.boss.y = world.boss.stationY;
        world.boss.x = GameWorld.WIDTH / 2f;
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) {
            world.ship.weaponTimer = 999f;
            world.update(1f / 60f);
            world.pollEvents();
        }
    }

    /** 打掉指定那一件；别件的判定半径临时收小，保证这一发只压在量的这件上。 */
    private void killPart(int index) {
        Boss b = world.boss;
        BossPart p = b.parts[index];
        assertTrue("这件本来就该还活着", p.active);
        p.hp = 0.5f;
        float[] savedRadius = new float[b.partCount];
        for (int k = 0; k < b.partCount; k++) {
            savedRadius[k] = b.parts[k].radius;
            if (k != index && b.parts[k].active) b.parts[k].radius = 1f;
        }
        // 船的走位每帧自己重算横坐标，钉不住；量出上一帧的位移，把子弹按在它这一帧会到的地方
        float px = p.worldX(b), py = p.worldY(b);
        float lastPx = px, lastPy = py;
        for (int i = 0; i < 24 && p.active; i++) {
            parkBoss();
            world.ship.weaponTimer = 999f;
            world.hitStopTimer = 0f;
            world.slowTimer = 0f;
            float dx = px - lastPx, dy = py - lastPy;
            lastPx = px;
            lastPy = py;
            for (Bullet bl : world.bullets) bl.active = false;
            freeBullet().init(px + dx, py + dy, 0f, 0f, 2, false);
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
            for (EnemyBullet eb : world.enemyBullets) eb.active = false;
            px = p.worldX(b);
            py = p.worldY(b);
        }
        for (int k = 0; k < b.partCount; k++) b.parts[k].radius = savedRadius[k];
        assertFalse("这一件该被打掉", p.active);
    }

    private Bullet freeBullet() {
        for (Bullet bl : world.bullets) {
            if (!bl.active) return bl;
        }
        throw new IllegalStateException("弹池该有空位");
    }

    /** 还活着的舰装里层号最小的那一件；没有可拆的了返回 -1。 */
    private int nextTargetIndex() {
        int best = Integer.MAX_VALUE;
        int at = -1;
        Boss b = world.boss;
        for (int i = 0; i < b.partCount; i++) {
            BossPart p = b.parts[i];
            if (!p.active || p.breachRank < 0) continue;
            if (p.breachRank < best) { best = p.breachRank; at = i; }
        }
        return at;
    }

    /** 层号最大的那一件——拿它插队最省事。 */
    private int lastTargetIndex() {
        int best = -1;
        int at = -1;
        Boss b = world.boss;
        for (int i = 0; i < b.partCount; i++) {
            BossPart p = b.parts[i];
            if (!p.active || p.breachRank < 0) continue;
            if (p.breachRank > best) { best = p.breachRank; at = i; }
        }
        return at;
    }

    /** 顺着路线把整条舰装拆空：一件都不插队。 */
    private void peelClean() {
        int guard = 0;
        while (nextTargetIndex() >= 0) {
            killPart(nextTargetIndex());
            assertTrue("拆件不该拆不完", ++guard < 64);
        }
    }

    private int countEvents(GameWorld.EventType type) {
        int n = 0;
        for (GameWorld.GameEvent e : events) {
            if (e.type == type) n++;
        }
        return n;
    }

    private int liveEnemyBullets() {
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active) n++;
        }
        return n;
    }

    /** 一直拍到哑火被吃掉为止，返回吃了第几拍（-1 = 没吃掉）。 */
    private int beatUntilSilenced() {
        return beatUntilSilenced(new int[1]);
    }

    /**
     * 同上，另外把"被吃掉那一拍多出几颗敌弹"写进 out[0]。
     * 拍与拍之间不过帧，所以场上敌弹只增不减——这一截差值就是那一拍本身造出来的。
     */
    private int beatUntilSilenced(int[] out) {
        for (int i = 1; i <= BEAT_LIMIT; i++) {
            events.addAll(world.pollEvents());
            int before = liveEnemyBullets();
            events.clear();
            world.bossAttack();
            events.addAll(world.pollEvents());
            if (!world.breachSilenceArmed) {
                out[0] = liveEnemyBullets() - before;
                return i;
            }
        }
        out[0] = -1;
        return -1;
    }

    // ---- 攒上（armed）这一半：和 v1.71 的大奖同一条判据 ----

    @Test
    public void aCleanRouteArmsTheSilence() {
        triggerBoss(FULL_TIER);
        assertFalse("开局不该攒着哑火", world.breachSilenceArmed);
        assertEquals(0, world.breachSilences);
        peelClean();
        assertTrue("整条线拆穿该攒下一记哑火", world.breachSilenceArmed);
        assertEquals(1, world.breachPerfects);
        assertTrue(world.breachGrandScore > 0);
        assertEquals("还没兑现之前不记次数", 0, world.breachSilences);
    }

    @Test
    public void aBrokenRouteArmsNothing() {
        Boss b = triggerBoss(FULL_TIER);
        int far = lastTargetIndex();
        int first = nextTargetIndex();
        assertTrue("这艘船得有不止一层", b.parts[far].breachRank > b.parts[first].breachRank);
        killPart(far);                       // 插队一次
        assertEquals(1, world.breachBroken);
        peelClean();
        assertFalse("插过队就不算拆明白", world.breachSilenceArmed);
        assertEquals(0, world.breachGrandScore);
        assertEquals(0, world.breachPerfects);
    }

    @Test
    public void theSilenceAndTheGrandBonusShareOneJudgement() {
        for (int level = 0; level < 4; level++) {
            triggerBoss(level);
            peelClean();
            assertTrue("舰级 " + level + " 拆穿该同时给大奖和哑火",
                    world.breachGrandScore > 0 && world.breachSilenceArmed);
            assertEquals(GameWorld.breachGrandScoreFor(world.boss.level), world.breachGrandScore);
        }
    }

    @Test
    public void everyFormInTheRotationCanBeSilenced() {
        for (int level = 0; level < Story.SECTOR_COUNT - 1; level++) {
            Boss b = triggerBoss(level);
            Boss.Form form = b.form;
            peelClean();
            assertTrue(form.label + "拆穿了该攒哑火", world.breachSilenceArmed);
            events.clear();
            int beat = beatUntilSilenced();
            assertTrue(form.label + "的招牌该在" + BEAT_LIMIT + "拍内出现", beat > 0);
            assertEquals(form.ordinal(),
                    ofType(GameWorld.EventType.BOSS_BREACH_SILENCE).get(0).code);
        }
    }

    private List<GameWorld.GameEvent> ofType(GameWorld.EventType type) {
        List<GameWorld.GameEvent> out = new ArrayList<>();
        for (GameWorld.GameEvent e : events) {
            if (e.type == type) out.add(e);
        }
        return out;
    }

    @Test
    public void theFinalBossCanBeNullToo() {
        Boss b = triggerBoss(Story.SECTOR_COUNT - 1);
        assertTrue("该是最终旗舰", world.finalBossFight);
        assertEquals(Boss.Form.SENTINEL, b.form);
        peelClean();
        assertTrue(world.breachSilenceArmed);
        // 星核的招牌是「星核爆裂」，最终旗舰的舰级也最高——哑火只看形态那一拍，不看数值
        events.clear();
        assertTrue(beatUntilSilenced() > 0);
        assertEquals(Boss.Form.SENTINEL, world.breachSilencedForm);
    }

    // ---- 兑现（consumed）那一半：吃的是调度层的一拍 ----

    @Test
    public void theMisfireMakesNoBulletAtAll() {
        triggerBoss(FULL_TIER);
        peelClean();
        for (EnemyBullet eb : world.enemyBullets) eb.active = false;
        drain();
        int scoreBefore = world.score;
        float hpBefore = world.boss.hp;
        int[] made = new int[1];
        int beat = beatUntilSilenced(made);
        assertTrue("招牌该在" + BEAT_LIMIT + "拍内轮到", beat > 0);
        assertEquals("哑火那一拍该一颗弹都没有", 0, made[0]);
        assertEquals("哑火不给分", scoreBefore, world.score);
        assertEquals("哑火不扣旗舰血", hpBefore, world.boss.hp, 0.01f);
        assertEquals(1, countEvents(GameWorld.EventType.BOSS_BREACH_SILENCE));
        assertEquals(0, countEvents(GameWorld.EventType.BOSS_SIGNATURE));
        assertEquals(0, countEvents(GameWorld.EventType.BOSS_FINALE));
    }

    @Test
    public void onlyTheSignatureBeatIsEaten() {
        triggerBoss(FULL_TIER);
        peelClean();
        // 前面那些拍没有吃掉了招牌，就一定是没排到招牌：弹照出、flag 照挂
        int eatenAt = -1;
        for (int i = 1; i <= BEAT_LIMIT; i++) {
            events.addAll(world.pollEvents());
            int before = liveEnemyBullets();
            events.clear();
            world.bossAttack();
            events.addAll(world.pollEvents());
            int made = liveEnemyBullets() - before;
            boolean silenced = !world.breachSilenceArmed;
            assertEquals("每一拍都只该有一个结局", silenced,
                    countEvents(GameWorld.EventType.BOSS_BREACH_SILENCE) > 0);
            if (!silenced) {
                assertTrue("没排到招牌的这一拍照常出弹", made > 0);
                continue;
            }
            assertEquals("被吃掉的那一拍一颗弹都不该有", 0, made);
            assertEquals("被吃掉的那一拍不该报招牌", 0,
                    countEvents(GameWorld.EventType.BOSS_SIGNATURE));
            eatenAt = i;
            break;
        }
        assertTrue("哑火该在" + BEAT_LIMIT + "拍内被吃掉", eatenAt > 0);
    }

    @Test
    public void oneBreachBuysExactlyOneMisfire() {
        triggerBoss(FULL_TIER);
        peelClean();
        assertTrue(beatUntilSilenced() > 0);
        assertFalse("一记只够吃一次", world.breachSilenceArmed);
        assertEquals(1, world.breachSilences);
        events.clear();
        // 再拍八拍：该有招牌正常出手，绝不会再报哑火
        boolean sawSignature = false;
        for (int i = 0; i < BEAT_LIMIT && !sawSignature; i++) {
            int before = liveEnemyBullets();
            world.bossAttack();
            events.addAll(world.pollEvents());
            sawSignature = !ofType(GameWorld.EventType.BOSS_SIGNATURE).isEmpty()
                    || !ofType(GameWorld.EventType.BOSS_FINALE).isEmpty();
            // 船坞那一手放的是僚机不是弹，所以这里只认事件、不认弹数
            assertTrue("没攒哑火就不该再报哑火",
                    countEvents(GameWorld.EventType.BOSS_BREACH_SILENCE) == 0);
            events.clear();
        }
        assertTrue("吃完之后招牌该照常出手", sawSignature);
        assertEquals(1, world.breachSilences);
        assertFalse(world.breachSilenceArmed);
    }

    @Test
    public void theMisfireLeavesTheReadLedgerAlone() {
        triggerBoss(FULL_TIER);
        Boss.Form form = world.boss.form;
        int readsBefore = world.formReads[form.ordinal()];
        float windowBefore = world.counterStrikeTimer;
        int readBefore = world.countersRead;
        peelClean();
        events.clear();
        assertTrue(beatUntilSilenced() > 0);
        assertEquals("没打出来的那一手没有可读的账", readsBefore, world.formReads[form.ordinal()]);
        assertEquals("哑火不开反击窗口", windowBefore, world.counterStrikeTimer, 0.001f);
        assertEquals("没打出来的那一手不会被读懂", readBefore, world.countersRead);
        assertEquals(0, countEvents(GameWorld.EventType.BOSS_COUNTER));
    }

    @Test
    public void theSilenceDoesNotHoldTheFightPaused() {
        triggerBoss(FULL_TIER);
        peelClean();
        // 攒着的时候战场照转：部件继续开火，船继续走位，直到某一拍被吃掉
        int bulletsSeen = 0;
        int frames = 0;
        while (world.breachSilenceArmed && frames < 900) {
            parkBoss();
            world.ship.weaponTimer = 999f;
            for (Bullet bl : world.bullets) bl.active = false;
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
            bulletsSeen += liveEnemyBullets();
            frames++;
        }
        assertTrue("攒着的哑火总会在实战里被吃掉", frames < 900);
        assertTrue("这段时间场上不该是空的", bulletsSeen > 0);
        assertEquals(1, world.breachSilences);
        assertTrue(world.breachSilenceFlash > 0f);
    }

    @Test
    public void theBannerLineTollsAndDecays() {
        triggerBoss(FULL_TIER);
        peelClean();
        assertEquals(0f, world.breachSilenceFlash, 0.001f);
        assertTrue(beatUntilSilenced() > 0);
        assertEquals(GameWorld.BREACH_SILENCE_TIME, world.breachSilenceFlash, 0.001f);
        for (int i = 0; i < (int) (GameWorld.BREACH_SILENCE_TIME * 60f) + 30; i++) step(1);
        assertEquals("余辉该自己收走", 0f, world.breachSilenceFlash, 0.001f);
        assertFalse(world.breachSilenceArmed);
    }

    // ---- 账本的边界：跟着船走、跟着重开清 ----

    @Test
    public void aFreshShipDoesNotInheritTheArmedSilence() {
        triggerBoss(FULL_TIER);
        peelClean();
        assertTrue(world.breachSilenceArmed);
        triggerBoss(1);                      // 换一艘船
        assertFalse("哑火是那艘船欠的，不替下一艘还", world.breachSilenceArmed);
        assertEquals(0f, world.breachSilenceFlash, 0.001f);
        assertEquals("但本局吃过几个还是要记着", 0, world.breachSilences);
        peelClean();
        assertTrue(world.breachSilenceArmed);
        assertEquals(0, world.breachSilences);
    }

    @Test
    public void aShipWithoutARouteNeverArms() {
        Boss b = triggerBoss(FULL_TIER);
        // 把舰装账整个抹掉：一件都不在路线上，也就永远谈不上"拆穿"
        for (int i = 0; i < b.partCount; i++) b.parts[i].breachRank = -1;
        assertEquals(COUNT_BY_TIER[FULL_TIER], world.breachRigParts);
        for (int i = 0; i < b.partCount; i++) b.parts[i].breachRank = -1;
        int guard = 0;
        while (world.boss.partsAllDestroyed() == false) {
            int at = -1;
            for (int i = 0; i < b.partCount; i++) {
                if (b.parts[i].active) { at = i; break; }
            }
            if (at < 0) break;
            killPart(at);
            assertTrue("拆件不该拆不完", ++guard < 64);
        }
        // 层号是入场时发的账，事后抹掉只会让每一件都"不进这条线"：既不顺线也不插队
        assertEquals("抹了层号就没有连段这回事", 0, world.breachParts);
        assertEquals(0, world.breachBroken);
        assertEquals("剩下的件数不減，永远判不出拆穿", COUNT_BY_TIER[FULL_TIER],
                world.breachRemaining);
        assertEquals(0, world.breachPerfects);
        assertEquals(0, world.breachGrandScore);
        assertFalse("没有路线就无从拆穿", world.breachSilenceArmed);
        assertEquals(0, world.breachSilences);
    }

    @Test
    public void restartClearsTheSilenceLedger() {
        triggerBoss(FULL_TIER);
        peelClean();
        assertTrue(beatUntilSilenced() > 0);
        assertEquals(1, world.breachSilences);
        world.restart(GameMode.WAVES);
        assertFalse(world.breachSilenceArmed);
        assertEquals(0, world.breachSilences);
        assertEquals(0f, world.breachSilenceFlash, 0.001f);
        assertEquals(Boss.Form.SPECTER, world.breachSilencedForm);
        assertEquals(0, world.breachPerfects);
    }

    @Test
    public void normalBeatsAreUntouchedByTheFeature() {
        // 没攒哑火的船，拍子和以前一模一样：拍下去就该有弹出来
        triggerBoss(FULL_TIER);
        assertFalse(world.breachSilenceArmed);
        for (int i = 0; i < 6; i++) {
            int before = liveEnemyBullets();
            world.bossAttack();
            assertTrue("不攒哑火时每一拍都该有动静", liveEnemyBullets() > before);
        }
        assertEquals(0, world.breachSilences);
    }

    @Test
    public void partsKeepFiringWhileTheSilenceIsArmed() {
        Boss b = triggerBoss(FULL_TIER);
        // 留两件不拆，部件自己那本火力不该被哑火影响
        int killed = 0;
        while (killed < b.partCount - 2) {
            killPart(nextTargetIndex() >= 0 ? nextTargetIndex() : lastTargetIndex());
            killed++;
        }
        boolean armedBefore = world.breachSilenceArmed;
        int before = liveEnemyBullets();
        for (int i = 0; i < 60; i++) {
            parkBoss();
            world.ship.weaponTimer = 999f;
            for (Bullet bl : world.bullets) bl.active = false;
            world.update(1f / 60f);
            world.pollEvents();
        }
        assertTrue("部件照常开火", liveEnemyBullets() >= before);
        assertEquals(armedBefore, world.breachSilenceArmed || armedBefore);
    }

    @Test
    public void theMisfireShakesButDoesNotStun() {
        triggerBoss(FULL_TIER);
        peelClean();
        float hpBefore = world.boss.hp;
        float staggerBefore = world.boss.staggerTimer;   // 拆空舰装那次缴械本来就带着硬直
        assertTrue(beatUntilSilenced() > 0);
        assertEquals("哑火不该让旗舰掉血", hpBefore, world.boss.hp, 0.01f);
        assertEquals("哑火也不该再压一层硬直", staggerBefore,
                world.boss.staggerTimer, 0.001f);
    }

    @Test
    public void armingIsVisibleToTheHudBeforeItIsSpent() {
        triggerBoss(FULL_TIER);
        peelClean();
        assertTrue(world.breachSilenceArmed);
        assertEquals(0f, world.breachSilenceFlash, 0.001f);
        // 横幅在"攒着"和"响过"两种状态下读的是不同字段，渲染层据此分两行字
        assertNotNull(world.breachSilencedForm);
        assertEquals(Boss.Form.SPECTER, world.breachSilencedForm);  // 还没兑现，保持默认
    }

    @Test
    public void theRouteScoreIsStillTheSameWithoutASilence() {
        // 只奖不罚这条口径不能因为 v1.72 松口：插队的件照样碎、照样拿分，只是不攒哑火
        Boss b = triggerBoss(FULL_TIER);
        int level = b.level;
        int first = nextTargetIndex();
        int far = lastTargetIndex();
        int scoreBefore = world.score;
        killPart(far);
        int gained = world.score - scoreBefore;
        int base = 80 + level * 20 + (b.parts[far].kind == BossPart.Kind.SHIELD ? 150 : 0);
        assertEquals("插队那一下只断连段，钱一分不少", base, gained);
        assertFalse(world.breachSilenceArmed);
    }

    @Test
    public void oneMisfirePerShipEvenAcrossPhases() {
        Boss b = triggerBoss(FULL_TIER);
        peelClean();
        assertTrue(beatUntilSilenced() > 0);
        // 同一艘船再拆也攒不出来：舰装已经空了，没有第二条路线
        assertEquals(COUNT_BY_TIER[FULL_TIER], world.breachRigParts);
        for (int i = 0; i < b.partCount; i++) assertFalse(b.parts[i].active);
        peelClean();
        assertFalse(world.breachSilenceArmed);
        assertEquals(1, world.breachSilences);
    }

    @Test
    public void practiceShipsCanBeSilencedToo() {
        world.restart(GameMode.PRACTICE);
        world.spawningEnabled = false;
        drain();
        Boss b = triggerBoss(FULL_TIER);
        assertTrue("练功房也该有路线", b.partCount > 0 && world.breachRigParts > 0);
        peelClean();
        assertTrue(world.breachSilenceArmed);
        events.clear();
        assertTrue(beatUntilSilenced() > 0);
        assertEquals(1, countEvents(GameWorld.EventType.BOSS_BREACH_SILENCE));
    }
}
