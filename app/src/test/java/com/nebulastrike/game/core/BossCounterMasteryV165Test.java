package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.65 迭代 55：破招精通 · COUNTER MASTERY。
 *
 * <p>识破（v1.63）给分，反击（v1.64）给伤害，这一版给<b>熟练</b>：同一型旗舰的招牌被读得越多，
 * 那一型就越容易读干净——招牌弹速降到 90%/80%，反击窗口从 5 秒拉到 6.5/8 秒，窗口里的伤害加成
 * 从 +50% 抬到 +65%/+80%。奖励的是"看习惯这艘船"，不是运气，所以账按形态分开记：磁暴读透了，
 * 星核照样得从头学。
 *
 * <p>四条口径：① 赏金、掉落、招式节拍与常规弹幕一概不动，精通只改这三件事（招牌弹速、窗口长度、
 * 窗口加成）；② 档位是本局的账，换船不清零（下一艘还是这一型就能直接吃加长窗口），重开才归零；
 * ③ 升档只在跨门槛那一瞬报一次，第 3 次、第 5 次不再重复放确认音；④ 弹速只对打了轮次号的招牌弹
 * 生效，扩散环、弹幕网、扇面这些常规弹照旧。
 */
public class BossCounterMasteryV165Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261121L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.reducedFx = false;
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

    /** 叫出这一段的旗舰、跳过演出、压掉词缀，并保证场上没有上一艘船。 */
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

    /** 指定形态的识破次数。 */
    private int reads(Boss.Form form) {
        return world.formReads[form.ordinal()];
    }

    /** 丢出本型招牌并让它当场散尽：识破、反击窗口、精通档位都在这里结算。 */
    private void readThisFormsSignature() {
        assertTrue("招牌要真的丢出弹",
                world.fireSignature(world.boss.form, world.boss.tier) > 0);
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.sigSerial != 0) b.active = false;
        }
        step(2);
    }

    /** 直接把某一型的识破次数摁到目标值（升档之外的账不由测试代劳）。 */
    private void setReads(Boss.Form form, int n) {
        world.formReads[form.ordinal()] = n;
    }

    private void clearBullets() {
        for (Bullet b : world.bullets) b.active = false;
        for (EnemyBullet b : world.enemyBullets) b.active = false;
    }

    /**
     * 把旗舰钉在飞船正上方开火若干帧，返回它（舰体 + 部件）挨的总伤害。
     * 窗口全程挂满，档位由参数指定——这样两组的差别只剩加成百分比。
     *
     * <p>每帧顺手清空敌弹：旗舰的弹幕会半路截掉玩家的子弹（点弹幕那套规则），
     * 不清掉的话量到的其实是"两秒里谁运气好"，10% 与 20% 的差距会被噪声吃掉。
     */
    private float damageToBossAtTier(int frames, int tier) {
        world.boss.maxHp = 400000f;
        world.boss.hp = 400000f;
        float[] partBefore = new float[world.boss.partCount];
        for (int i = 0; i < world.boss.partCount; i++) {
            BossPart p = world.boss.parts[i];
            p.maxHp = 400000f;
            p.hp = 400000f;
            partBefore[i] = p.hp;
        }
        clearBullets();
        world.counterStrikeDamage = 0;
        world.counterStrikeTier = Math.max(0, tier);
        float before = world.boss.hp;
        for (int i = 0; i < frames; i++) {
            world.boss.x = world.ship.x;
            world.boss.y = world.boss.stationY;
            world.counterStrikeTimer = tier < 0 ? 0f
                    : GameWorld.counterStrikeWindowFor(tier);
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
            for (EnemyBullet b : world.enemyBullets) b.active = false;
        }
        float dealt = before - world.boss.hp;
        for (int i = 0; i < world.boss.partCount; i++) {
            dealt += partBefore[i] - world.boss.parts[i].hp;
        }
        return dealt;
    }

    // ---- 尺子本身 ----

    @Test
    public void theLadderIsTheSameForEveryone() {
        assertEquals(2, GameWorld.MASTERY_KNOWING_READS);
        assertEquals(4, GameWorld.MASTERY_MASTERED_READS);
        assertEquals(0, GameWorld.masteryTier(0));
        assertEquals(0, GameWorld.masteryTier(1));
        assertEquals(1, GameWorld.masteryTier(2));
        assertEquals(1, GameWorld.masteryTier(3));
        assertEquals(2, GameWorld.masteryTier(4));
        assertEquals(2, GameWorld.masteryTier(99));
        // 档位名要分得开，也要一眼看得懂
        assertTrue(GameWorld.masteryLabel(0).length() >= 2);
        assertTrue(GameWorld.masteryLabel(1).length() >= 2);
        assertTrue(GameWorld.masteryLabel(2).length() >= 2);
        assertFalse(GameWorld.masteryLabel(0).equals(GameWorld.masteryLabel(1)));
        assertFalse(GameWorld.masteryLabel(1).equals(GameWorld.masteryLabel(2)));
        assertFalse(GameWorld.masteryLabel(0).equals(GameWorld.masteryLabel(2)));
    }

    @Test
    public void bothRewardsGrowMonotonicallyAndStopAtTheTop() {
        float w0 = GameWorld.counterStrikeWindowFor(0);
        float w1 = GameWorld.counterStrikeWindowFor(1);
        float w2 = GameWorld.counterStrikeWindowFor(2);
        assertTrue("窗口一档比一档长", w0 < w1 && w1 < w2);
        assertEquals("没读懂就是 v1.64 那 5 秒", GameWorld.COUNTER_STRIKE_WINDOW, w0, 0.001f);
        assertEquals("读透封顶 8 秒，别把它变成常驻增益", 8f, w2, 0.001f);
        int p0 = GameWorld.counterStrikeBonusPctFor(0);
        int p1 = GameWorld.counterStrikeBonusPctFor(1);
        int p2 = GameWorld.counterStrikeBonusPctFor(2);
        assertEquals(GameWorld.COUNTER_STRIKE_BONUS_PCT, p0);
        assertTrue("加成一档比一档高", p0 < p1 && p1 < p2);
        assertEquals("封顶 +80%", 80, p2);
        // 越界不炸：负数与超高都夹回有效档位
        assertEquals(p0, GameWorld.counterStrikeBonusPctFor(-3));
        assertEquals(p2, GameWorld.counterStrikeBonusPctFor(7));
        assertEquals(w0, GameWorld.counterStrikeWindowFor(-1), 0.001f);
        assertEquals(w2, GameWorld.counterStrikeWindowFor(9), 0.001f);
        // 第三件奖励：招牌弹速
        float s0 = GameWorld.signatureSlowFactor(0);
        float s1 = GameWorld.signatureSlowFactor(1);
        float s2 = GameWorld.signatureSlowFactor(2);
        assertEquals("没读懂就全速", 1f, s0, 0.001f);
        assertTrue("一档比一档慢", s1 < s0 && s2 < s1);
        assertEquals("封顶 80% 速", 0.8f, s2, 0.001f);
        assertEquals(s0, GameWorld.signatureSlowFactor(-5), 0.001f);
        assertEquals(s2, GameWorld.signatureSlowFactor(99), 0.001f);
    }

    // ---- 怎么升档 ----

    @Test
    public void theFirstReadStillPaysThePlainWindow() {
        triggerBoss(2);
        Boss.Form form = world.boss.form;
        assertEquals(0, world.masteryTier(form));
        readThisFormsSignature();
        assertEquals(1, reads(form));
        assertEquals("读一次还不算熟", 0, world.masteryTier(form));
        assertEquals("窗口就是基础的那 5 秒", GameWorld.COUNTER_STRIKE_WINDOW,
                world.counterStrikeTimer, 0.2f);
        assertEquals(GameWorld.COUNTER_STRIKE_WINDOW, world.counterStrikeWindowTotal, 0.001f);
        assertEquals(0, world.counterStrikeTier);
        assertEquals("升档音不该在这一读出现", 0, countEvents(GameWorld.EventType.BOSS_COUNTER_MASTERY));
    }

    @Test
    public void theSecondReadOfTheSameFormIsKnowing() {
        triggerBoss(3);
        Boss.Form form = world.boss.form;
        readThisFormsSignature();
        readThisFormsSignature();
        assertEquals(2, reads(form));
        assertEquals(1, world.masteryTier(form));
        assertEquals("窗口跟着拉长", GameWorld.counterStrikeWindowFor(1),
                world.counterStrikeWindowTotal, 0.001f);
        assertTrue(world.counterStrikeTimer > GameWorld.COUNTER_STRIKE_WINDOW + 0.5f);
        assertEquals("窗口记的是这一型", form, world.counterStrikeForm);
        assertEquals(1, world.counterStrikeTier);
        assertEquals("升到熟悉要报一次", 1, countEvents(GameWorld.EventType.BOSS_COUNTER_MASTERY));
        assertTrue("横幅把原因挂出来", world.counterMasteryBanner > 0f);
        assertEquals(form, world.counterMasteryForm);
        assertEquals(1, world.counterMasteryTier);
    }

    @Test
    public void theFourthReadMastersItAndTheFifthStaysQuiet() {
        triggerBoss(3);
        Boss.Form form = world.boss.form;
        int masteryEvents = 0;
        for (int i = 0; i < 4; i++) {
            drain();
            readThisFormsSignature();
            masteryEvents += countEvents(GameWorld.EventType.BOSS_COUNTER_MASTERY);
        }
        assertEquals(4, reads(form));
        assertEquals(2, world.masteryTier(form));
        assertEquals("只有跨过 2 和 4 两道门槛时报两次", 2, masteryEvents);
        assertEquals(GameWorld.counterStrikeWindowFor(2), world.counterStrikeWindowTotal, 0.001f);
        assertEquals(2, world.counterStrikeTier);

        drain();
        readThisFormsSignature();
        assertEquals(5, reads(form));
        assertEquals("第三档之上不再报", 0, countEvents(GameWorld.EventType.BOSS_COUNTER_MASTERY));
        assertEquals("窗口也不再变长", GameWorld.counterStrikeWindowFor(2),
                world.counterStrikeWindowTotal, 0.001f);
        assertEquals(5, world.countersRead);
    }

    @Test
    public void masteryIsEarnedPerFormNotPerPlayer() {
        triggerBoss(1);
        Boss.Form mine = world.boss.form;
        Boss.Form fresh = null, shaky = null;
        for (Boss.Form f : Boss.Form.values()) {
            if (f == mine) continue;
            if (fresh == null) fresh = f;
            else if (shaky == null) { shaky = f; break; }
        }
        assertTrue(fresh != null && shaky != null);
        setReads(mine, GameWorld.MASTERY_MASTERED_READS);   // 读透
        setReads(shaky, 1);                                  // 只读过一次
        // fresh 一次没读
        assertEquals(2, world.masteryTier(mine));
        assertEquals("只读一次还不算熟", 0, world.masteryTier(shaky));
        assertEquals("没读过就是生疏", 0, world.masteryTier(fresh));

        // 场上换成一次没读的那一型：窗口只能按它自己的账给，吃不到 mine 的读透
        world.boss.applyForm(fresh);
        readThisFormsSignature();
        assertEquals(fresh, world.counterStrikeForm);
        assertEquals(0, world.counterStrikeTier);
        assertEquals(GameWorld.COUNTER_STRIKE_WINDOW, world.counterStrikeWindowTotal, 0.001f);

        // 读到过一次的那一型，这一读正好跨进"熟悉"
        world.boss.applyForm(shaky);
        readThisFormsSignature();
        assertEquals(shaky, world.counterStrikeForm);
        assertEquals(1, world.counterStrikeTier);
        assertEquals(GameWorld.counterStrikeWindowFor(1), world.counterStrikeWindowTotal, 0.001f);

        // 再换回读透的那一型，立刻吃满
        world.boss.applyForm(mine);
        readThisFormsSignature();
        assertEquals(mine, world.counterStrikeForm);
        assertEquals(2, world.counterStrikeTier);
        assertEquals(GameWorld.counterStrikeWindowFor(2), world.counterStrikeWindowTotal, 0.001f);
        assertEquals("别人的账不会替这一型垫高", 2, world.masteryTier(mine));
    }

    @Test
    public void windowKeepsTheFormThatEarnedIt() {
        triggerBoss(4);
        Boss.Form mine = world.boss.form;
        setReads(mine, GameWorld.MASTERY_KNOWING_READS);
        readThisFormsSignature();
        assertEquals(mine, world.counterStrikeForm);
        assertEquals(1, world.counterStrikeTier);
        int ledger = reads(mine);
        // 窗口还挂着，场上又起手一轮别的形态：窗口不该被改写
        Boss.Form other = Boss.Form.SENTINEL == mine ? Boss.Form.SPECTER : Boss.Form.SENTINEL;
        world.fireSignature(other, world.boss.tier);
        assertEquals("这一读之后账本才动，光起手不算", ledger, reads(mine));
        assertEquals(mine, world.counterStrikeForm);
        assertEquals(1, world.counterStrikeTier);
        assertTrue(world.counterStrikeTimer > 0f);
    }

    @Test
    public void aVolleyThatTouchesYouEarnsNoMastery() {
        triggerBoss(2);
        world.lives = 99;
        world.ship.hasShield = false;
        world.ship.shieldLayers = 0;
        world.ship.makeInvulnerable(0f);
        Boss.Form form = world.boss.form;
        assertTrue(world.fireSignature(form, world.boss.tier) > 0);
        // 只留一枚这轮的招牌弹摁在飞船身上：这轮打中了，识破不成立
        int serial = 0;
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.sigSerial > serial) serial = b.sigSerial;
        }
        assertTrue(serial > 0);
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
        step(2);
        assertTrue(world.hitsTaken > 0);
        assertEquals("打中就不算读懂", 0, reads(form));
        assertEquals(0, world.masteryTier(form));
        assertEquals("窗口也没开", 0f, world.counterStrikeTimer, 0.001f);
        assertEquals(0, countEvents(GameWorld.EventType.BOSS_COUNTER_MASTERY));
    }

    // ---- 升档真的更疼 ----

    @Test
    public void theBonusIsFeltOnRealShots() {
        triggerBoss(0);
        world.counterStrikeTimer = 1f;
        // 5 伤的长枪：+50% → 7，+65% → 8，+80% → 9
        world.counterStrikeTier = 0;
        assertEquals(7, world.counterStrikeBoost(5));
        world.counterStrikeTier = 1;
        assertEquals(8, world.counterStrikeBoost(5));
        world.counterStrikeTier = 2;
        assertEquals(9, world.counterStrikeBoost(5));
        // 任何一发伤害，档位高不可能打得更少
        for (int dmg = 1; dmg <= 12; dmg++) {
            int[] g = new int[3];
            for (int tier = 0; tier <= 2; tier++) {
                world.counterStrikeTimer = 1f;
                world.counterStrikeTier = tier;
                g[tier] = world.counterStrikeBoost(dmg);
            }
            assertTrue("dmg " + dmg + " -> " + g[0] + "/" + g[1] + "/" + g[2],
                    g[0] <= g[1] && g[1] <= g[2]);
            assertTrue("至少 +1", g[0] > dmg);
        }
    }

    @Test
    public void windowedShotsReallyDoMoreDamage() {
        triggerBoss(4);
        float plain = damageToBossAtTier(300, -1);   // -1 = 全程不挂窗口，作对照
        float mastered = damageToBossAtTier(300, 2);
        int extra = world.counterStrikeDamage;
        assertTrue("基线要真打出伤害: " + plain, plain > 0f);
        assertTrue("读透的窗口只会更多: " + plain + " -> " + mastered, mastered > plain);
        assertTrue("窗口里记了账", extra > 0);
        assertTrue("多出来的部分不超过 +80% 那一截: " + plain + " -> " + mastered,
                mastered <= plain * 1.9f);
        assertTrue("账本对得上多打的那截: " + extra + " vs " + (mastered - plain),
                Math.abs(extra - (mastered - plain)) <= Math.max(2f, plain * 0.35f));
    }

    @Test
    public void masteredSignaturesArriveSlower() {
        triggerBoss(3);
        Boss.Form form = world.boss.form;
        float base = signatureTopSpeed(form);
        assertTrue("招牌要真的有弹", base > 0f);
        setReads(form, GameWorld.MASTERY_KNOWING_READS);
        float knowing = signatureTopSpeed(form);
        assertEquals("熟悉慢 10%", base * 0.9f, knowing, base * 0.06f);
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        float mastered = signatureTopSpeed(form);
        assertEquals("读透慢 20%", base * 0.8f, mastered, base * 0.06f);
        assertTrue("一档比一档慢", mastered < knowing);
        assertEquals("系数与档位对得上", 0.8f,
                GameWorld.signatureSlowFactor(2), 0.001f);
        assertEquals(1f, GameWorld.signatureSlowFactor(0), 0.001f);
    }

    /** 丢一轮该型的招牌，记录最快那发弹的初速，然后把场上清干净。 */
    private float signatureTopSpeed(Boss.Form form) {
        clearBullets();
        assertTrue("招牌要真的丢出弹", world.fireSignature(form, world.boss.tier) > 0);
        float top = 0f;
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active || b.sigSerial == 0) continue;
            float sp = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
            if (sp > top) top = sp;
        }
        clearBullets();
        return top;
    }

    @Test
    public void ordinaryVolleyAndOtherFormsKeepTheirSpeed() {
        triggerBoss(2);
        Boss.Form mine = world.boss.form;
        Boss.Form other = Boss.Form.SENTINEL == mine ? Boss.Form.SPECTER : Boss.Form.SENTINEL;
        float mineBase = signatureTopSpeed(mine);
        float otherBase = signatureTopSpeed(other);
        float net = netTopSpeed();
        assertTrue(mineBase > 0f && otherBase > 0f && net > 0f);

        setReads(mine, GameWorld.MASTERY_MASTERED_READS);
        assertEquals("常规扩散网不吃精通，节拍照旧", net, netTopSpeed(), net * 0.02f);
        assertEquals("别的形态也不跟着变慢", otherBase, signatureTopSpeed(other), otherBase * 0.02f);
        assertTrue("被读透的那一型确实慢了: " + mineBase + " -> " + signatureTopSpeed(mine),
                signatureTopSpeed(mine) < mineBase * 0.95f);
    }

    private float netTopSpeed() {
        clearBullets();
        world.fireNet(6, 1, 180f);
        float top = 0f;
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active || b.sigSerial != 0) continue;
            float sp = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
            if (sp > top) top = sp;
        }
        clearBullets();
        return top;
    }

    @Test
    public void theBoostFunctionFollowsTheLadder() {
        triggerBoss(0);
        world.counterStrikeTimer = 0f;
        assertEquals("没开窗原样返回", 10, world.counterStrikeBoost(10));
        for (int tier = 0; tier <= 2; tier++) {
            world.counterStrikeTimer = 1f;
            world.counterStrikeTier = tier;
            world.counterStrikeDamage = 0;
            int boosted = world.counterStrikeBoost(10);
            assertEquals("tier " + tier, 10 + 10 * GameWorld.counterStrikeBonusPctFor(tier) / 100,
                    boosted);
            assertEquals("账记的是多出来的那截", boosted - 10, world.counterStrikeDamage);
        }
        // 1 点伤害也要至少抬 1 点，不能被整数除法抹平
        world.counterStrikeTimer = 1f;
        for (int tier = 0; tier <= 2; tier++) {
            world.counterStrikeTier = tier;
            assertTrue("tier " + tier, world.counterStrikeBoost(1) >= 2);
        }
    }

    @Test
    public void ordinaryEnemiesDoNotCareAboutMastery() {
        triggerBoss(0);
        world.boss.active = false;   // 只测小怪，别让旗舰分走子弹
        setReads(Boss.Form.SENTINEL, GameWorld.MASTERY_MASTERED_READS);
        Enemy e = world.spawnEnemyForTest(EnemyType.DRONE, 450f);
        assertTrue("小怪该进场", e != null && e.active);
        e.maxHp = 400000f;
        e.hp = 400000f;
        float tier0 = damageToEnemyPinned(e, 90, 0);
        float tier2 = damageToEnemyPinned(e, 90, 2);
        assertTrue("小怪要吃得到子弹", tier0 > 0f);
        assertEquals("精通只治旗舰，小怪伤害照旧", tier0, tier2, 0.5f);
        assertEquals("小怪那边不该记账", 0, world.counterStrikeDamage);
    }

    private float damageToEnemyPinned(Enemy e, int frames, int tier) {
        clearBullets();
        e.hp = 400000f;
        world.counterStrikeTier = tier;
        world.counterStrikeDamage = 0;
        float before = e.hp;
        for (int i = 0; i < frames; i++) {
            e.x = world.ship.x;
            e.y = world.ship.y - 420f;
            world.counterStrikeTimer = GameWorld.counterStrikeWindowFor(tier);
            world.update(1f / 60f);
            world.pollEvents();
        }
        return before - e.hp;
    }

    // ---- 边界：档位不越界、不跨局 ----

    @Test
    public void aNewFlagshipKeepsTheLedgerButNotTheWindow() {
        triggerBoss(1);
        Boss.Form mine = world.boss.form;
        setReads(mine, 1);
        readThisFormsSignature();                       // 这一下正好跨进"熟悉"
        assertEquals(2, reads(mine));
        assertEquals(1, world.counterStrikeTier);
        int ledger = reads(mine);
        assertTrue(world.counterStrikeTimer > 0f);

        world.boss.hp = 0f;
        step(1);
        assertFalse(world.boss.active);
        assertEquals("换船不烧掉已经到手的账本", ledger, reads(mine));
        assertTrue("击沉不收回：窗口按 v1.64 的规矩自己走完",
                world.counterStrikeTimer > 0f);
        // 真正清零的是"下一艘入场"
        triggerBoss(1);
        assertEquals("新旗舰把窗口清零，档位与长度回到基础值", 0f,
                world.counterStrikeTimer, 0.001f);
        assertEquals("档位跟着清零，等下一读重新开", 0, world.counterStrikeTier);
        assertEquals(GameWorld.COUNTER_STRIKE_WINDOW, world.counterStrikeWindowTotal, 0.001f);

        world.boss.applyForm(mine);
        readThisFormsSignature();                       // 3 次：还是熟悉
        assertEquals(ledger + 1, reads(mine));
        assertEquals("下一艘还是这一型，直接吃加长窗口", 1, world.counterStrikeTier);
        assertEquals(GameWorld.counterStrikeWindowFor(1), world.counterStrikeWindowTotal, 0.001f);
    }

    @Test
    public void restartClearsEveryTraceOfMastery() {
        triggerBoss(2);
        Boss.Form form = world.boss.form;
        for (int i = 0; i < 4; i++) readThisFormsSignature();
        assertTrue(world.masteryTier(form) > 0);
        assertTrue(formsMastered() > 0);

        world.restart(GameMode.WAVES);
        for (Boss.Form f : Boss.Form.values()) {
            assertEquals(f.name(), 0, reads(f));
            assertEquals(f.name(), 0, world.masteryTier(f));
        }
        assertEquals(0, formsMastered());
        assertEquals(0, world.counterStrikeTier);
        assertEquals(GameWorld.COUNTER_STRIKE_WINDOW, world.counterStrikeWindowTotal, 0.001f);
        assertEquals(0f, world.counterMasteryBanner, 0.001f);
        assertEquals(0, world.countersRead);
        drain();
        world.restart(GameMode.WAVES);
        assertEquals(0, countEvents(GameWorld.EventType.BOSS_COUNTER_MASTERY));
    }

    private int formsMastered() {
        return world.formsMastered();
    }

    @Test
    public void formsMasteredCountsDistinctFormsOnceEach() {
        triggerBoss(0);
        Boss.Form[] all = Boss.Form.values();
        setReads(all[0], 1);                       // 不算
        setReads(all[1], GameWorld.MASTERY_KNOWING_READS);
        setReads(all[2], GameWorld.MASTERY_MASTERED_READS);
        setReads(all[3], GameWorld.MASTERY_MASTERED_READS + 5);
        assertEquals("读到 1 次不数进来", 3, formsMastered());
        assertEquals(all[0].name(), 0, world.masteryTier(all[0]));
        assertEquals(all[2].name(), 2, world.masteryTier(all[2]));
    }

    @Test
    public void theReadPayoutStaysExactlyTheSame() {
        triggerBoss(3);
        Boss.Form form = world.boss.form;
        for (int i = 0; i < 3; i++) readThisFormsSignature();
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        int before = world.score;
        readThisFormsSignature();
        assertEquals("精通只改窗口，不动赏金",
                GameWorld.COUNTER_SCORE_BASE + 20 * world.boss.level, world.score - before);
    }

    // ---- 实战与残响 ----

    @Test
    public void aLongFightGrindsTheSameFormUp() {
        triggerBoss(5);
        for (int i = 0; i < 60 * 60 && world.formsMastered() == 0; i++) {
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
        }
        assertTrue("一艘船一轮接一轮地丢同一手，读两轮就该有一型上手: "
                + java.util.Arrays.toString(world.formReads), world.formsMastered() >= 1);
        assertTrue("窗口长度不会超过读透那一档",
                world.counterStrikeWindowTotal <= GameWorld.counterStrikeWindowFor(2) + 0.001f);
        if (world.counterStrikeTimer > 0f) {
            assertEquals("窗口档位始终跟着它自己那一型的账本",
                    world.masteryTier(world.counterStrikeForm), world.counterStrikeTier);
        }
    }

    @Test
    public void theEchoCountsTowardTheSameLedger() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        readThisFormsSignature();
        assertEquals(1, reads(form));
        // 残影也是同一型：读它一样进这一型的账
        world.bossFightHits = 1;
        world.boss.hp = 0f;
        step(1);
        world.bossGapTimer = 0f;
        world.stageClearTimer = 0f;
        world.wave = world.nextBossWave;
        step(1);
        assertTrue(world.boss.active);
        world.boss.applyForm(form);
        readThisFormsSignature();
        assertEquals("残影与本体的账记在同一格里", 2, reads(form));
        assertEquals(1, world.masteryTier(form));
        assertEquals(1, world.counterStrikeTier);
    }

    @Test
    public void everyFormHasItsOwnLadder() {
        for (Boss.Form f : Boss.Form.values()) {
            setReads(f, GameWorld.MASTERY_KNOWING_READS);
        }
        for (Boss.Form f : Boss.Form.values()) {
            assertEquals(f.name(), 1, world.masteryTier(f));
        }
        assertEquals(Boss.Form.values().length, formsMastered());
        setReads(Boss.Form.SENTINEL, 1);
        assertEquals("第 1 次不算熟", Boss.Form.values().length - 1, formsMastered());
    }

    @Test
    public void theMasteryEventCarriesFormAndTier() {
        triggerBoss(2);
        Boss.Form form = world.boss.form;
        drain();
        readThisFormsSignature();
        readThisFormsSignature();
        GameWorld.GameEvent hit = null;
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.BOSS_COUNTER_MASTERY) hit = e;
        }
        assertTrue("该有一条升档事件", hit != null);
        assertEquals("code = 形态序号 × 10 + 档位",
                form.ordinal() * 10 + 1, hit.code);
        assertEquals("尾数就是新档位", 1, hit.code % 10);
    }
}
