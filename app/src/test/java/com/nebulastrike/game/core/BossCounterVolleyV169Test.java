package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.69 迭代 59：破招回敬 · COUNTER VOLLEY。
 *
 * <p>破招这条线前五层都在给"看的人"发钱：v1.63 给分、v1.64 给伤害、v1.65 给节奏、v1.66 给手感、
 * v1.67 给解药。读懂了这件事，场上其实一直没有<b>动作</b>上的回报。这一版补上：
 * <b>同一型读到「熟悉」以上，它刚丢出来的那一手当场原样反打回去</b>——静默回三层针，星核回三层叠波，
 * 读透比熟悉多一排。金色弹道，自己找它，不占玩家的命中率。
 *
 * <p>四条口径：① 档位只看精通（{@code counterVolleyDamage}：生疏 0 / 熟悉 3 / 读透 5），
 * 与连读、词缀、舰级都无关；② 回敬走玩家弹池但不进 {@code bulletsFired} 也不进 {@code shotsHit}——
 * 它算它的账，玩家的准星是玩家的；③ 赏金、反击窗口、掉落、双份解药一件不改，
 * 回敬只是<b>额外</b>的那一手；④ 旗舰不在场就打不出去，玩家弹池满了就少打几发，绝不报错。
 */
public class BossCounterVolleyV169Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261125L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.reducedFx = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.restart(GameMode.WAVES);
        drain();
    }

    // ---- 辅助 ----

    private void step(int frames) {
        for (int i = 0; i < frames; i++) {
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
            shieldFromHarm();
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

    private GameWorld.GameEvent last(GameWorld.EventType type) {
        GameWorld.GameEvent hit = null;
        for (GameWorld.GameEvent e : events) if (e.type == type) hit = e;
        return hit;
    }

    /** 叫出这一段的旗舰、跳过演出、压掉词缀。 */
    private void triggerBoss(int level) {
        if (world.boss.active) {
            world.boss.hp = 0f;
            step(1);
        }
        clearPlayerBullets();
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
        shieldFromHarm();
        drain();
    }

    private void shieldFromHarm() {
        world.ship.makeInvulnerable(9999f);
        world.ship.hasShield = false;
    }

    /** 把旗舰钉在第几阶段：打不沉，也不会因为回敬弹磨血而偷偷换段。 */
    private void pinPhase(int phase) {
        world.boss.maxHp = 1_000_000f;
        world.boss.hp = phase == 1 ? 900_000f : phase == 2 ? 500_000f : 200_000f;
        for (int i = 0; i < world.boss.partCount; i++) {
            BossPart p = world.boss.parts[i];
            p.maxHp = 1_000_000f;
            p.hp = 1_000_000f;
        }
        world.update(1f / 60f);
        events.addAll(world.pollEvents());
        assertEquals("阶段该钉住", phase, world.boss.phase);
    }

    private void clearPlayerBullets() {
        for (Bullet b : world.bullets) b.active = false;
    }

    private void killTaggedBullets() {
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.sigSerial != 0) b.active = false;
        }
    }

    /** 丢一手招牌并让它当场散尽：识破、窗口、回敬都在这一刻结算。 */
    private void readOneSignature() {
        assertTrue("招牌要真的丢出弹",
                world.fireSignature(world.boss.form, world.boss.tier) > 0);
        killTaggedBullets();
        step(2);
    }

    /** 丢一手终式并当场散尽。 */
    private void readOneFinale() {
        assertTrue("终式要真的丢出弹",
                world.fireSignature(world.boss.form, world.boss.tier, true) > 0);
        killTaggedBullets();
        step(2);
    }

    private void setReads(Boss.Form form, int n) {
        world.formReads[form.ordinal()] = n;
    }

    private int volleyCount() {
        int n = 0;
        for (Bullet b : world.bullets) if (b.active && b.volley) n++;
        return n;
    }

    private int playerBulletCount() {
        int n = 0;
        for (Bullet b : world.bullets) if (b.active) n++;
        return n;
    }

    /** 船体加部件的总血量：回敬弹打到哪儿都算打到它。 */
    private float bossTotalHp() {
        float sum = world.boss.hp;
        for (int i = 0; i < world.boss.partCount; i++) sum += world.boss.parts[i].hp;
        return sum;
    }

    /** 把场上所有回敬弹瞬移到旗舰身上，下一步就只看它们打不打得动。 */
    private int parkVolleyOnBoss() {
        int n = 0;
        for (Bullet b : world.bullets) {
            if (!b.active || !b.volley) continue;
            b.x = world.boss.x;
            b.y = world.boss.y;
            n++;
        }
        return n;
    }

    // ---- 档位 ----

    @Test
    public void onlyKnowingItAndBeyondSendsFireBack() {
        assertEquals("生疏不回敬", 0, GameWorld.counterVolleyDamage(0));
        assertEquals("熟悉 3 点", 3, GameWorld.counterVolleyDamage(1));
        assertEquals("读透 5 点", 5, GameWorld.counterVolleyDamage(2));
        assertEquals("读透之上不再往上加", 5, GameWorld.counterVolleyDamage(7));
        assertEquals("脏数据也不给负伤害", 0, GameWorld.counterVolleyDamage(-3));
    }

    @Test
    public void aFreshRunHasNoVolleyToSpeakOf() {
        assertEquals(0, world.counterVolleys);
        assertEquals(0, world.counterVolleyShots);
        assertEquals(0, world.counterVolleyLast);
        assertEquals(0f, world.counterVolleyFlash, 0f);
        assertFalse(world.counterVolleyWasFinale);
    }

    // ---- 识破的那一刻 ----

    @Test
    public void anUnreadFormSendsNothingBack() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        setReads(form, 0);
        readOneSignature();
        assertEquals("生疏那一型只识破，不回敬", 0, world.counterVolleys);
        assertEquals(0, volleyCount());
        assertEquals(0, countEvents(GameWorld.EventType.BOSS_COUNTER_VOLLEY));
        assertEquals(0f, world.counterVolleyFlash, 0f);
        assertEquals("识破本身照样算", 1, world.countersRead);
        assertTrue("反击窗口也照样开", world.counterStrikeTimer > 0f);
    }

    @Test
    public void knowingItSendsTheSameMoveBack() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        setReads(form, GameWorld.MASTERY_KNOWING_READS - 1);   // 这一读正好升到熟悉
        readOneSignature();
        assertEquals(1, world.counterVolleys);
        assertTrue("场上要真有金色的回敬弹", volleyCount() > 0);
        assertEquals(volleyCount(), world.counterVolleyLast);
        assertTrue(world.counterVolleyFlash > 0f);
        assertEquals(form, world.counterVolleyForm);
        assertFalse("这一手是招牌，不是终式", world.counterVolleyWasFinale);
        assertEquals(1, countEvents(GameWorld.EventType.BOSS_COUNTER_VOLLEY));
        assertEquals("事件带的是形态序号 × 2 + 是否终式",
                form.ordinal() * 2, last(GameWorld.EventType.BOSS_COUNTER_VOLLEY).code);
    }

    @Test
    public void everyReadGetsItsOwnVolley() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        int shots = 0;
        for (int i = 0; i < 3; i++) {
            clearPlayerBullets();
            readOneSignature();
            shots += world.counterVolleyLast;
            assertEquals("第 " + (i + 1) + " 读也要有自己的回敬", i + 1, world.counterVolleys);
        }
        assertEquals("累计弹数就是各次之和", shots, world.counterVolleyShots);
        assertTrue(shots > 0);
    }

    @Test
    public void masteringItThickensTheVolley() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        clearPlayerBullets();
        int knowing = world.fireCounterVolley(form, false, 1);
        clearPlayerBullets();
        int mastered = world.fireCounterVolley(form, false, 2);
        assertTrue("熟悉就要打得出去", knowing > 0);
        assertTrue("读透比熟悉多一排: " + knowing + " → " + mastered, mastered > knowing);
        assertTrue("一次也别把玩家弹池打满", mastered <= 45);
    }

    @Test
    public void everyFormReturnsItsOwnShape() {
        triggerBoss(0);
        for (Boss.Form form : Boss.Form.values()) {
            world.boss.applyForm(form);
            clearPlayerBullets();
            int made = world.fireCounterVolley(form, false, 2);
            assertTrue(form.label + " 该打得出回敬", made > 0);
            assertTrue(form.label + " 一次不超过 45 发: " + made, made <= 45);
            assertEquals(form.label + " 打出去的就是场上这些", made, volleyCount());
            for (Bullet b : world.bullets) {
                if (!b.active || !b.volley) continue;
                assertEquals(form.label + " 的回敬伤害按读透走",
                        GameWorld.counterVolleyDamage(2), b.damage);
                assertTrue(form.label + " 的回敬要往上飞", b.vy < 0f);
            }
        }
    }

    @Test
    public void theFinaleComesBackTaggedAsAFinale() {
        triggerBoss(3);
        pinPhase(3);
        Boss.Form form = world.boss.form;
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        readOneFinale();
        assertEquals(1, world.counterVolleys);
        assertTrue("读懂终式也要有弹打回去", volleyCount() > 0);
        assertTrue(world.counterVolleyWasFinale);
        assertEquals(form, world.counterVolleyForm);
        assertEquals("终式的 code 多一格",
                form.ordinal() * 2 + 1, last(GameWorld.EventType.BOSS_COUNTER_VOLLEY).code);
        assertEquals("终式的账还是记在同一型上", 1, world.finaleReads);
    }

    // ---- 真的打得到它 ----

    @Test
    public void theVolleyActuallyHurtsTheShipThatFiredIt() {
        triggerBoss(0);
        pinPhase(1);
        Boss.Form form = world.boss.form;
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        clearPlayerBullets();
        int made = world.fireCounterVolley(form, false, 2);
        assertTrue(made > 0);
        float before = bossTotalHp();
        assertEquals(made, parkVolleyOnBoss());
        world.update(1f / 60f);
        events.addAll(world.pollEvents());
        assertTrue("回敬弹要真的啃下血来: " + before + " → " + bossTotalHp(),
                bossTotalHp() < before);
    }

    // ---- 不替玩家的准星负责 ----

    @Test
    public void volleyBulletsNeverTouchAccuracy() {
        triggerBoss(0);
        pinPhase(1);
        Boss.Form form = world.boss.form;
        clearPlayerBullets();
        world.bulletsFired = 0;
        world.shotsHit = 0;
        int made = world.fireCounterVolley(form, false, 2);
        assertEquals("回敬不算玩家开的那一枪", 0, world.bulletsFired);
        parkVolleyOnBoss();
        world.update(1f / 60f);
        events.addAll(world.pollEvents());
        assertEquals("打中了也不记在玩家账上", 0, world.shotsHit);
        assertEquals("所以命中率一个点都不动", 0, world.accuracyPct());
        assertTrue(made > 0);
    }

    // ---- 边界：打不出去的时候 ----

    @Test
    public void nothingComesBackWhenTheShipIsGone() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        world.boss.hp = 0f;
        step(4);
        assertFalse("旗舰收场", world.boss.active);
        clearPlayerBullets();
        assertEquals("船都不在场，回敬个谁", 0, world.fireCounterVolley(form, false, 2));
        assertEquals(0, volleyCount());
        assertEquals(0, world.counterVolleys);
    }

    @Test
    public void aFullBulletPoolJustSendsNothing() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        for (Bullet b : world.bullets) b.init(b.x, b.y, 0f, -600f, 1, false);
        assertEquals("玩家弹池先塞满", world.bullets.length, playerBulletCount());
        int volleys = world.counterVolleys;
        assertEquals("满池就一发也打不出去", 0, world.fireCounterVolley(form, false, 2));
        assertEquals("满池也不该多记一笔账", volleys, world.counterVolleys);
        assertEquals(0, countEvents(GameWorld.EventType.BOSS_COUNTER_VOLLEY));
        assertTrue("战斗照常", world.state == GameWorld.State.PLAYING);
    }

    @Test
    public void aHalfFullPoolSendsWhatFits() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        for (Bullet b : world.bullets) b.init(b.x, b.y, 0f, -600f, 1, false);
        assertEquals("玩家弹池先塞满", world.bullets.length, playerBulletCount());
        int free = 3;
        for (Bullet b : world.bullets) {
            if (free <= 0) break;
            b.active = false;
            free--;
        }
        assertEquals("只留 3 个空位", world.bullets.length - 3, playerBulletCount());
        int made = world.fireCounterVolley(form, false, 2);
        assertEquals("有几个位置就还几发", 3, made);
        assertEquals(1, world.counterVolleys);
        assertEquals("场上就是这些", world.bullets.length, playerBulletCount());
    }

    // ---- 别的不掺和 ----

    @Test
    public void theVolleyChangesNothingElseAboutTheRead() {
        int level = 0;
        // 同一型、同一段，唯一变的就是精通档位（也就是有没有回敬）
        triggerBoss(level);
        Boss.Form form = world.boss.form;
        setReads(form, 0);
        readOneSignature();
        int bareBounty = world.counterScore;
        float bareWindow = world.counterStrikeWindowTotal;
        int bareStrikes = world.counterStrikes;
        int bareChain = world.counterStreak;

        triggerBoss(level);
        world.boss.applyForm(form);
        setReads(form, GameWorld.MASTERY_MASTERED_READS - 1);
        readOneSignature();
        assertEquals("赏金还是那把尺子算出来的", bareBounty, world.counterScore);
        assertTrue("窗口只按精通变长，跟回敬无关", world.counterStrikeWindowTotal > bareWindow);
        assertEquals("反击次数照旧一-read-一次", bareStrikes + 1, world.counterStrikes);
        assertEquals("连读照旧数招，不看档位", bareChain + 1, world.counterStreak);
        assertEquals("倍率也照旧", GameWorld.chainMultFor(world.counterStreak), world.counterScoreMult);
    }

    @Test
    public void theBountyIsStillPureHullAndChainMath() {
        triggerBoss(4);
        Boss.Form form = world.boss.form;
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        readOneSignature();
        int expected = (GameWorld.COUNTER_SCORE_BASE + 20 * world.boss.level)
                * GameWorld.chainMultFor(world.counterStreak);
        assertEquals("回敬不额外发一分赏金", expected, world.counterScore);
    }

    @Test
    public void affixesAndChainDoNotChangeTheVolley() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        clearPlayerBullets();
        int bare = world.fireCounterVolley(form, false, 2);
        assertTrue(bare > 0);
        read(3);                                    // 把串攒到 3 招，档位 ×2
        assertEquals(2, world.counterScoreMult);
        clearPlayerBullets();
        assertEquals("串再长，回敬的密度也只听精通的",
                bare, world.fireCounterVolley(form, false, 2));
    }

    private void read(int n) {
        for (int i = 0; i < n; i++) readOneSignature();
    }

    @Test
    public void theVolleyDoesNotTouchTheEnemyDanmaku() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        int enemyBefore = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active) enemyBefore++;
        clearPlayerBullets();
        int made = world.fireCounterVolley(form, false, 2);
        int enemyAfter = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active) enemyAfter++;
        assertTrue(made > 0);
        assertEquals("回敬走的是玩家弹池，一格敌弹也不占", enemyBefore, enemyAfter);
    }

    // ---- 读数与清零 ----

    @Test
    public void theBannerFadesOnItsOwn() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        readOneSignature();
        assertTrue(world.counterVolleyFlash > 0f);
        step((int) (GameWorld.VOLLEY_BANNER_TIME * 60) + 6);
        assertEquals("横幅自己走掉", 0f, world.counterVolleyFlash, 0f);
        assertEquals("账还在", 1, world.counterVolleys);
    }

    @Test
    public void restartClearsEveryTraceOfTheVolley() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        readOneSignature();
        assertTrue(world.counterVolleys > 0);
        world.restart(GameMode.WAVES);
        assertEquals(0, world.counterVolleys);
        assertEquals(0, world.counterVolleyShots);
        assertEquals(0, world.counterVolleyLast);
        assertEquals(0f, world.counterVolleyFlash, 0f);
        assertFalse(world.counterVolleyWasFinale);
        assertEquals(Boss.Form.SPECTER, world.counterVolleyForm);
        assertEquals(0, volleyCount());
    }

    // ---- 真实一场打下来 ----

    @Test
    public void aRealFightReturnsFireOnItsOwn() {
        triggerBoss(2);
        Boss.Form form = world.boss.form;
        pinPhase(1);                                // 这一条只验"能不能自己接上"，别让靶子半路沉了
        setReads(form, GameWorld.MASTERY_MASTERED_READS);      // 档位先摁好，只等它自己丢出一手被读懂
        int frames = 0;
        while (frames < 60 * 150 && world.counterVolleys == 0) {
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
            shieldFromHarm();
            frames++;
        }
        assertTrue("两分钟里它至少丢过一手、也被读懂过一次: 识破 "
                        + world.countersRead + " 回敬 " + world.counterVolleys,
                world.counterVolleys > 0);
        assertTrue(world.counterVolleyShots > 0);
        assertEquals(form, world.counterVolleyForm);
        assertEquals(1, countEvents(GameWorld.EventType.BOSS_COUNTER_VOLLEY));
    }
}
