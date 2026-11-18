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
 * v1.74 迭代 64：终章共鸣 · 把"打核心"接回"读招"那本账上。
 *
 * <p>v1.73 让八型旗舰的核心各自挣扎出一种形状，但挣扎完的这几秒只兑成一笔分数：双倍伤害、
 * 每击 +10，跟玩家在这局里攒下的识破进度是两本不相干的账。这一版把它们接起来——
 * <b>反击窗口（读懂那一手换来的五秒）里打进摊开核心的子弹，四记算一次识破</b>；
 * 而整条舰装拆穿换来的那记哑火，除了吃掉一手招牌，还会把护着核心的那口气一起泄掉：
 * <b>下一次开核提前到 1.4 秒内</b>。
 *
 * <p>四条硬口径：① <b>只补进度不改规则</b>——连读 {@code counterStreak}、读懂次数
 * {@code countersRead}、连读倍率、核心伤害倍率与每击加分、开核间隔全一概不动，共鸣动的是
 * {@code formReads} 这一本；② 一个窗口最多兑一记，没攒满的四记在窗口合拢那一帧蒸发，
 * 不带走也不补进下一窗，站桩刷核心刷不出读透；③ 提前量<b>只压不抬</b>，本来排得更近的一次
 * 不会被推远，灼热词缀那种更快的节拍照旧更快；窗口正开着时不打断它，欠着，合拢那一帧再排队；
 * ④ 命中判定仍走 {@code coreHitAt}，共鸣只认落在形态自己那（几）片圈里的弹，画在哪就能打在哪。
 */
public class BossFinaleEchoV174Test {

    /** 招牌最迟几拍出一次（一阶段每 4 拍），留一倍余量。 */
    private static final int BEAT_LIMIT = 12;
    /** 一帧 = 1/60 秒，用来把"提前多少秒"换算成帧数。 */
    private static final int FRAMES_PER_SECOND = 60;

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261129L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.restart(GameMode.WAVES);
        events.clear();
        drain();
    }

    // ---- 辅助：沿用 v1.73 那套入场、开核、放弹的量法 ----

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

    /** 把指定那一型请进场：常规七型按形态循环走舰级，星核是最终旗舰那一艘。 */
    private Boss use(Boss.Form f) {
        Boss b = triggerBoss(f == Boss.Form.SENTINEL
                ? Story.SECTOR_COUNT - 1 : f.ordinal());
        assertEquals("进场的那艘应该是" + f.label, f, b.form);
        return b;
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

    /** 同上，但把事件收进 {@link #events}——提前开核那一拍要读事件里的秒数。 */
    private void stepTaken(int frames) {
        for (int i = 0; i < frames; i++) {
            world.ship.weaponTimer = 999f;
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
        }
    }

    /** 走正常节拍把核心打开：钉位坐标、事件秒数、共鸣那页新账都在开核那一帧落下。 */
    private void openCore() {
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        world.bossCoreTimer = 0f;
        world.bossCoreCooldown = 0.02f;
        events.clear();
        for (int i = 0; i < 20 && !world.bossCoreOpen(); i++) stepTaken(1);
        assertTrue("按节拍该开核", world.bossCoreOpen());
    }

    /** 把地上的道具先收走：一笔 +300 的折算奖励混进来，共鸣那一笔分就量不准了。 */
    private void clearPickups() {
        for (PowerUp pu : world.powerups) pu.active = false;
    }

    private void disableParts() {
        for (int i = 0; i < world.boss.partCount; i++) world.boss.parts[i].active = false;
    }

    /** 清场后放一发玩家弹：速度给 0 就是"钉在这一点上挨一次判定"。 */
    private Bullet shot(float x, float y, float vx, float vy, int damage) {
        for (Bullet b : world.bullets) b.active = false;
        for (Bullet b : world.bullets) {
            if (!b.active) {
                b.init(x, y, vx, vy, damage, false);
                return b;
            }
        }
        throw new IllegalStateException("弹池该有空位");
    }

    /**
     * 打过一发"对照弹"：部件全灭那笔过路奖励、倍率计时器、狂热都要在这之前归零，
     * 之后量到的分数差才是共鸣这一笔本身。
     */
    private void zeroScoreNoise() {
        world.hitStopTimer = 0f;
        world.slowTimer = 0f;
        world.x2Timer = 0f;
        world.frenzyTimer = 0f;
        clearPickups();
        parkBoss();
    }

    /**
     * 按来源量分：过路的部件奖励、召唤物击杀、满级折算都记在别的账上，
     * 混在总分里就量不准共鸣这一笔了。核心那一击走 {@code SCORE_OTHER}，共鸣那笔走 {@code SCORE_BOSS}。
     */
    private int scoreOf(int source) {
        return world.scoreBySource[source];
    }

    /**
     * "其它"这本账上除了核心那一击，还可能在同一帧落下成就解锁的 +300（打着打着正好攒满一枚），
     * 那是成就的分，不是核心的分——量之前把它扣掉，剩下的数才是能写死的。
     */
    private int coreScore() {
        return scoreOf(GameWorld.SCORE_OTHER) - world.achieveScore;
    }

    /** 收掉这一窗、再按节拍开一窗：共鸣一窗只兑一记，跨窗口记账得先翻页。 */
    private void nextWindow() {
        world.bossCoreTimer = 0f;
        step(2);
        openCore();
        openCounterWindow();
    }

    /** 血量固定成"随便打也不会死、也不会换段"，让读只反映共鸣本身。 */
    private void freezeHull() {
        world.boss.maxHp = 500_000f;
        world.boss.hp = 500_000f;
        world.boss.phase = 1;
    }

    /** 把反击窗口按满长度摊开（读懂那一手的原样奖励）。 */
    private void openCounterWindow() {
        world.counterStrikeTimer = GameWorld.COUNTER_STRIKE_WINDOW;
    }

    private int countOf(GameWorld.EventType type) {
        int n = 0;
        for (GameWorld.GameEvent e : events) {
            if (e.type == type) n++;
        }
        return n;
    }

    /** 连续往共鸣那本账上记 n 记，返回这几记一共补进去的识破进度。 */
    private int bank(int hits) {
        int before = world.formReads[world.boss.form.ordinal()];
        for (int i = 0; i < hits; i++) world.registerCoreEcho(world.boss.form);
        // 共鸣的事件是直接落进世界那口队列的，不帧循环就得自己收一次
        events.addAll(world.pollEvents());
        return world.formReads[world.boss.form.ordinal()] - before;
    }

    /** 拍出若干拍，遇到哑火兑现就停下，返回用掉的拍数（-1 表示没兑现）。 */
    private int beatsUntilSilence() {
        int before = world.breachSilences;
        for (int i = 1; i <= BEAT_LIMIT; i++) {
            world.bossAttack();
            events.addAll(world.pollEvents());
            if (world.breachSilences > before) return i;
        }
        return -1;
    }

    // ---- 一、共鸣的两把锁：窗口 + 摊开的核心 ----

    @Test
    public void echoNeedsTheCounterStrikeWindow() {
        use(Boss.Form.HIVE);
        freezeHull();
        openCore();
        world.counterStrikeTimer = 0f;
        events.clear();
        assertEquals("没读懂就没有共鸣", 0, bank(GameWorld.CORE_ECHO_HITS));
        assertEquals(0, world.coreEchoProgress());
        assertEquals(0, countOf(GameWorld.EventType.BOSS_CORE_ECHO_STEP));
        assertFalse(world.coreEchoPaid);
        assertEquals("一本没记上，总账也不该动", 0, world.finaleEchoes);

        openCounterWindow();
        assertEquals("窗口一开，同样的四记就该记上", 1, bank(GameWorld.CORE_ECHO_HITS));
    }

    @Test
    public void echoNeedsAnOpenCore() {
        use(Boss.Form.RIFT);
        freezeHull();
        openCounterWindow();
        events.clear();
        assertEquals("核心没摊开，子弹只是普通伤害", 0, bank(GameWorld.CORE_ECHO_HITS));
        assertEquals(0, world.coreEchoProgress());

        openCore();
        assertEquals("同一型核心摊开了才算", 1, bank(GameWorld.CORE_ECHO_HITS));
    }

    @Test
    public void fourCoreHitsBankOneRead() {
        Boss.Form f = Boss.Form.MIRROR;
        use(f);
        freezeHull();
        openCore();
        openCounterWindow();
        events.clear();
        int reads = world.formReads[f.ordinal()];

        for (int i = 0; i < GameWorld.CORE_ECHO_HITS - 1; i++) {
            assertEquals("差一记不该提前兑现", 0, bank(1));
            assertEquals(i + 1, world.coreEchoProgress());
        }
        assertEquals("凑满四记就该补一格", 1, bank(1));
        assertEquals(reads + 1, world.formReads[f.ordinal()]);
        assertEquals(1, world.finaleEchoes);
        assertEquals("兑完这一窗的进度条归零", 0, world.coreEchoProgress());
        assertTrue(world.coreEchoPaid);

        int steps = 0;
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.BOSS_CORE_ECHO_STEP) {
                steps++;
                assertEquals("第几记要按顺序报", steps, e.code);
            }
        }
        assertEquals(GameWorld.CORE_ECHO_HITS, steps);
        GameWorld.GameEvent paid = null;
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.BOSS_CORE_ECHO) paid = e;
        }
        assertNotNull("兑账要有自己的一声", paid);
        assertEquals("事件里带的是这一型", f.ordinal(), paid.code);
    }

    @Test
    public void thePayoutGrowsWithTheShipClass() {
        for (Boss.Form f : Boss.Form.values()) {
            use(f);
            freezeHull();
            openCore();
            openCounterWindow();
            zeroScoreNoise();
            world.coreEchoPaid = false;
            events.clear();
            int before = scoreOf(GameWorld.SCORE_BOSS);
            bank(GameWorld.CORE_ECHO_HITS);
            assertEquals(f.label + " 的共鸣该按舰级抬",
                    GameWorld.CORE_ECHO_SCORE_BASE + 40 * world.boss.level,
                    scoreOf(GameWorld.SCORE_BOSS) - before);
        }
    }

    @Test
    public void oneWindowPaysAtMostOneEcho() {
        Boss.Form f = Boss.Form.HIVE;
        use(f);
        freezeHull();
        int reads = world.formReads[f.ordinal()];
        openCore();
        openCounterWindow();
        events.clear();
        bank(GameWorld.CORE_ECHO_HITS * 3);
        assertEquals("一个窗口只兑一记", 1, world.formReads[f.ordinal()] - reads);
        assertEquals("多出来的那些记不该再记账", GameWorld.CORE_ECHO_HITS,
                countOf(GameWorld.EventType.BOSS_CORE_ECHO_STEP));
        assertEquals(1, world.finaleEchoes);
        assertEquals(0, world.coreEchoProgress());
    }

    @Test
    public void unspentEchoHitsEvaporateWhenTheCoreCloses() {
        Boss.Form f = Boss.Form.SPECTER;
        use(f);
        freezeHull();
        int reads = world.formReads[f.ordinal()];
        openCore();
        openCounterWindow();
        bank(GameWorld.CORE_ECHO_HITS - 1);
        assertEquals(GameWorld.CORE_ECHO_HITS - 1, world.coreEchoProgress());

        step((int) (Boss.coreFinaleFor(f).window * FRAMES_PER_SECOND) + 6);
        assertFalse("窗口该合拢了", world.bossCoreOpen());
        assertEquals("没攒满的不带走", 0, world.coreEchoProgress());
        assertEquals("也不补进下一窗", reads, world.formReads[f.ordinal()]);
        assertEquals(0, world.finaleEchoes);

        openCore();
        assertEquals("新窗口是一页新账", 0, world.coreEchoProgress());
        assertFalse(world.coreEchoPaid);
        assertEquals("上一窗差的这一记要在这一窗重新攒", 0, bank(1));
    }

    @Test
    public void aNewWindowStartsAFreshEchoLedger() {
        Boss.Form f = Boss.Form.DOCKYARD;
        use(f);
        freezeHull();
        int reads = world.formReads[f.ordinal()];
        openCounterWindow();

        openCore();
        assertEquals(1, bank(GameWorld.CORE_ECHO_HITS));
        step((int) (Boss.coreFinaleFor(f).window * FRAMES_PER_SECOND) + 6);
        openCore();
        assertFalse("新的一页还没兑过", world.coreEchoPaid);
        assertEquals(1, bank(GameWorld.CORE_ECHO_HITS));

        assertEquals("两窗各兑一记", reads + 2, world.formReads[f.ordinal()]);
        assertEquals(2, world.finaleEchoes);
    }

    @Test
    public void echoLeavesTheCounterStreakLedgerAlone() {
        Boss.Form f = Boss.Form.HIVE;
        use(f);
        freezeHull();
        openCore();
        openCounterWindow();
        world.countersRead = 3;
        world.counterStreak = 2;
        world.counterScoreMult = 4;
        float windowBefore = world.counterStrikeTimer;
        events.clear();

        bank(GameWorld.CORE_ECHO_HITS);

        assertEquals("共鸣只补 formReads", 3, world.countersRead);
        assertEquals("连读那本账不受打核心影响", 2, world.counterStreak);
        assertEquals("倍率也不是靠打核心抬的", 4, world.counterScoreMult);
        assertEquals("窗口长度不被共鸣吃掉", windowBefore,
                world.counterStrikeTimer, 1e-3f);
        assertEquals(f, world.boss.form);
    }

    @Test
    public void theEchoCrossesATierWithTheMasteryBanner() {
        Boss.Form f = Boss.Form.RIFT;
        use(f);
        freezeHull();
        openCore();
        openCounterWindow();

        world.formReads[f.ordinal()] = GameWorld.MASTERY_KNOWING_READS - 1;
        events.clear();
        assertEquals(1, bank(GameWorld.CORE_ECHO_HITS));
        assertEquals("补上的那一格照样升档", 1, world.masteryTier(f));
        assertEquals(f, world.counterMasteryForm);
        assertEquals(1, world.counterMasteryTier);
        assertTrue("横幅该亮起来", world.counterMasteryBanner > 2f);
        GameWorld.GameEvent banner = null;
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.BOSS_COUNTER_MASTERY) banner = e;
        }
        assertNotNull("升档走识破同一套播报", banner);
        assertEquals(f.ordinal() * 10 + 1, banner.code);

        world.formReads[f.ordinal()] = GameWorld.MASTERY_MASTERED_READS - 1;
        nextWindow();
        events.clear();
        assertEquals("下一窗再兑一记", 1, bank(GameWorld.CORE_ECHO_HITS));
        assertEquals(2, world.masteryTier(f));
        assertEquals(2, world.counterMasteryTier);

        world.formReads[f.ordinal()] = GameWorld.MASTERY_KNOWING_READS;
        nextWindow();
        events.clear();
        assertEquals("档内再补一格也算读懂了一次", 1, bank(GameWorld.CORE_ECHO_HITS));
        assertEquals(GameWorld.MASTERY_KNOWING_READS + 1, world.formReads[f.ordinal()]);
        assertEquals("没跨过档位线就不重复播报",
                0, countOf(GameWorld.EventType.BOSS_COUNTER_MASTERY));
    }

    // ---- 二、真帧：共鸣认的就是形态自己那片圈 ----

    @Test
    public void realCoreHitsInsideTheWindowResonate() {
        Boss.Form f = Boss.Form.HIVE;
        use(f);
        freezeHull();
        disableParts();
        zeroScoreNoise();
        shot(world.coreZoneX(0), world.coreZoneY(), 0f, 0f, 1);
        world.update(1f / 60f);
        drain();

        int reads = world.formReads[f.ordinal()];
        openCore();
        parkBoss();
        openCounterWindow();
        zeroScoreNoise();
        events.clear();
        int core = coreScore();
        int echo = scoreOf(GameWorld.SCORE_BOSS);
        for (int i = 0; i < GameWorld.CORE_ECHO_HITS; i++) {
            shot(world.coreZoneX(0), world.coreZoneY(), 0f, 0f, 1);
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
            if (i < GameWorld.CORE_ECHO_HITS - 1) {
                assertEquals("第 " + (i + 1) + " 记", i + 1, world.coreEchoHits);
                assertFalse(world.coreEchoPaid);
            } else {
                assertEquals("凑满就该归零", 0, world.coreEchoHits);
                assertTrue("而且是当场兑的", world.coreEchoPaid);
            }
        }
        assertEquals(reads + 1, world.formReads[f.ordinal()]);
        assertEquals("四记核心各 +10",
                GameWorld.CORE_ECHO_HITS * GameWorld.CORE_HIT_SCORE, coreScore() - core);
        assertEquals("兑的那一笔走旗舰账",
                GameWorld.CORE_ECHO_SCORE_BASE + 40 * world.boss.level,
                scoreOf(GameWorld.SCORE_BOSS) - echo);
        assertEquals(GameWorld.CORE_ECHO_HITS,
                countOf(GameWorld.EventType.BOSS_CORE_ECHO_STEP));
        assertEquals(1, countOf(GameWorld.EventType.BOSS_CORE_ECHO));
    }

    @Test
    public void aHitOutsideTheZoneDoesNotResonate() {
        use(Boss.Form.HIVE);
        freezeHull();
        disableParts();
        zeroScoreNoise();
        openCore();
        parkBoss();
        openCounterWindow();
        events.clear();

        shot(world.coreZoneX(0) + world.coreZoneRadius() + 16f, world.coreZoneY(), 0f, 0f, 1);
        world.update(1f / 60f);
        drain();
        assertEquals("圈外贴舰体不算共鸣", 0, world.coreEchoProgress());
        assertEquals(0, countOf(GameWorld.EventType.BOSS_CORE_ECHO_STEP));

        shot(world.coreZoneX(0), world.coreZoneY(), 0f, 0f, 1);
        world.update(1f / 60f);
        drain();
        assertEquals("挪进圈里就算一记", 1, world.coreEchoProgress());
    }

    @Test
    public void noEchoWhenTheWindowIsShutInRealFrames() {
        use(Boss.Form.SPECTER);
        freezeHull();
        disableParts();
        zeroScoreNoise();
        openCore();
        parkBoss();
        world.counterStrikeTimer = 0f;
        events.clear();
        int core = coreScore();
        for (int i = 0; i < GameWorld.CORE_ECHO_HITS; i++) {
            shot(world.coreZoneX(0), world.coreZoneY(), 0f, 0f, 1);
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
        }
        assertEquals("没窗口就照旧只是双倍伤害加分",
                GameWorld.CORE_ECHO_HITS * GameWorld.CORE_HIT_SCORE, coreScore() - core);
        assertEquals(0, world.coreEchoProgress());
        assertEquals(0, world.finaleEchoes);
    }

    @Test
    public void twinZoneFormsEchoFromEitherPiece() {
        for (Boss.Form f : Boss.Form.values()) {
            if (Boss.coreFinaleFor(f).zones != 2) continue;
            use(f);
            freezeHull();
            disableParts();
            zeroScoreNoise();
            openCore();
            parkBoss();
            openCounterWindow();
            events.clear();

            shot(world.coreZoneX(0), world.coreZoneY(), 0f, 0f, 1);
            world.update(1f / 60f);
            drain();
            shot(world.coreZoneX(1), world.coreZoneY(), 0f, 0f, 1);
            world.update(1f / 60f);
            drain();
            assertEquals(f.label + " 的两片都算共鸣", 2, world.coreEchoProgress());

            shot(world.boss.x, world.boss.y, 0f, 0f, 1);
            world.update(1f / 60f);
            drain();
            assertEquals(f.label + " 中间那道缝不算", 2, world.coreEchoProgress());
        }
    }

    @Test
    public void theEchoLeavesTheCorePayoutUntouched() {
        for (Boss.Form f : Boss.Form.values()) {
            use(f);
            freezeHull();
            disableParts();
            zeroScoreNoise();
            shot(world.coreZoneX(0), world.coreZoneY(), 0f, 0f, 1);
            world.update(1f / 60f);
            openCore();
            parkBoss();
            zeroScoreNoise();
            int before = coreScore();
            float hp = world.boss.hp;
            shot(world.coreZoneX(0), world.coreZoneY(), 0f, 0f, 1);
            world.update(1f / 60f);
            assertEquals(f.label + " 的核心一击还是 +" + GameWorld.CORE_HIT_SCORE,
                    GameWorld.CORE_HIT_SCORE, coreScore() - before);
            assertEquals(f.label + " 的倍率没被共鸣改动",
                    GameWorld.CORE_DAMAGE_MULT, (long) (hp - world.boss.hp));

            // 反击窗口照旧是 v1.64 那一条 +50%，共鸣只是记账，不在这上面加东西
            openCounterWindow();
            zeroScoreNoise();
            before = coreScore();
            hp = world.boss.hp;
            shot(world.coreZoneX(0), world.coreZoneY(), 0f, 0f, 1);
            world.update(1f / 60f);
            assertEquals(f.label + " 的窗口里核心分还是那一个数",
                    GameWorld.CORE_HIT_SCORE, coreScore() - before);
            assertEquals(f.label + " 的伤害 = 双倍 × 反击加成",
                    GameWorld.CORE_DAMAGE_MULT * 3 / 2, (long) (hp - world.boss.hp));
            assertEquals("第一记共鸣也记上了", 1, world.coreEchoHits);
            world.counterStrikeTimer = 0f;
        }
    }

    @Test
    public void theEchoOnlyTellsOnTheFormThatWasRead() {
        Boss.Form f = Boss.Form.MIRROR;
        use(f);
        freezeHull();
        openCore();
        openCounterWindow();
        bank(GameWorld.CORE_ECHO_HITS);
        for (Boss.Form other : Boss.Form.values()) {
            if (other == f) continue;
            assertEquals("共鸣补的是眼前这一型，别的船不跟着涨",
                    0, world.formReads[other.ordinal()]);
        }
        assertEquals(1, world.formReads[f.ordinal()]);
    }

    // ---- 三、哑火兑现的下一笔：弱点提前 ----

    @Test
    public void silencePullsTheNextCoreOpeningIn() {
        use(Boss.Form.SPECTER);
        freezeHull();
        world.bossCoreTimer = 0f;
        world.bossCoreCooldown = GameWorld.CORE_INTERVAL;
        world.breachSilenceArmed = true;
        events.clear();

        assertTrue("招牌那拍才会兑哑火", beatsUntilSilence() > 0);
        assertEquals(1, world.breachSilences);
        assertTrue("提前量落在预算内",
                world.bossCoreCooldown <= GameWorld.CORE_SILENCE_LEAD + 1e-3f);
        assertTrue("别把它压成零", world.bossCoreCooldown > 0f);
        GameWorld.GameEvent lead = null;
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.BOSS_CORE_LEAD) lead = e;
        }
        assertNotNull("提前摊开要有它自己的一声", lead);
        assertEquals(Math.round(world.bossCoreCooldown), lead.code);
        assertTrue("提示行该亮起来", world.coreLeadFlash > 0f);
    }

    @Test
    public void theLeadOnlyPullsNeverPushes() {
        use(Boss.Form.RIFT);
        freezeHull();
        world.bossCoreTimer = 0f;
        world.bossCoreCooldown = 0.6f;
        world.breachSilenceArmed = true;
        events.clear();

        assertTrue(beatsUntilSilence() > 0);
        assertEquals("本来排得更近的一次不该被推远", 0.6f, world.bossCoreCooldown, 1e-3f);
        assertEquals("也没兑现就不报", 0, countOf(GameWorld.EventType.BOSS_CORE_LEAD));
        assertEquals(0f, world.coreLeadFlash, 1e-3f);
    }

    @Test
    public void theLeadLandsInRealFrames() {
        use(Boss.Form.DOCKYARD);
        freezeHull();
        world.bossCoreTimer = 0f;
        world.bossCoreCooldown = GameWorld.CORE_INTERVAL;
        world.breachSilenceArmed = true;
        assertTrue(beatsUntilSilence() > 0);

        int frames = 0;
        while (!world.bossCoreOpen() && frames < 600) {
            step(1);
            frames++;
        }
        assertTrue("提前之后很快就该摊开", world.bossCoreOpen());
        assertTrue("帧数对上 1.4 秒这一档：" + frames,
                frames <= (GameWorld.CORE_SILENCE_LEAD + 0.1f) * FRAMES_PER_SECOND);
        assertTrue(frames >= 1);
    }

    @Test
    public void anOpenWindowIsNotInterruptedByTheLead() {
        Boss.Form f = Boss.Form.SPECTER;
        use(f);
        freezeHull();
        openCore();
        float windowLeft = world.bossCoreTimer;
        world.breachSilenceArmed = true;
        assertTrue("窗口里兑掉的哑火照样兑", beatsUntilSilence() > 0);
        assertEquals("但别把这几秒掐了", windowLeft - 0.0f, world.bossCoreTimer, 0.05f);
        assertTrue(world.bossCoreOpen());

        int frames = 0;
        while (world.bossCoreOpen() && frames < BEAT_LIMIT * 60) {
            step(1);
            frames++;
        }
        assertFalse("合拢之后欠的那笔才排队", world.bossCoreOpen());
        assertTrue("合拢那一帧把提前量补上：" + world.bossCoreCooldown,
                world.bossCoreCooldown <= GameWorld.CORE_SILENCE_LEAD + 1e-3f);
        events.clear();
        openCore();
        assertTrue("欠的这笔只兑一次", world.bossCoreOpen());
    }

    @Test
    public void theLeadAppliesToTheNextOpeningOnly() {
        Boss.Form f = Boss.Form.HIVE;
        use(f);
        freezeHull();
        world.bossCoreTimer = 0f;
        world.bossCoreCooldown = GameWorld.CORE_INTERVAL;
        world.breachSilenceArmed = true;
        assertTrue(beatsUntilSilence() > 0);

        openCoreByCadence();
        float window = Boss.coreFinaleFor(f).window;
        step((int) (window * FRAMES_PER_SECOND) + 6);
        assertFalse(world.bossCoreOpen());
        assertEquals("第二次回到常规节拍", GameWorld.CORE_INTERVAL,
                world.bossCoreCooldown, 0.1f);
    }

    @Test
    public void theLedWindowKeepsTheFormsOwnShape() {
        Boss.Form f = Boss.Form.MIRROR;
        use(f);
        freezeHull();
        world.bossCoreTimer = 0f;
        world.bossCoreCooldown = GameWorld.CORE_INTERVAL;
        world.breachSilenceArmed = true;
        assertTrue(beatsUntilSilence() > 0);

        openCoreByCadence();
        Boss.CoreFinale fin = Boss.coreFinaleFor(f);
        assertTrue("提前那一核开的还是本型的秒数",
                world.bossCoreTimer <= fin.window + 1e-3f
                        && world.bossCoreTimer > fin.window - 0.06f);
        assertEquals(fin.zones, world.coreZoneCount());
        assertEquals(fin.radius, world.coreZoneRadius(), 1e-3f);
        assertFalse("两片中间的缝依旧是缝", world.coreHitAt(world.boss.x, world.boss.y));
    }

    @Test
    public void blazeAffixKeepsItsFasterCadence() {
        use(Boss.Form.SPECTER);
        freezeHull();
        world.applyBossAffix(BossAffix.BLAZE);
        float blazeInterval = GameWorld.CORE_INTERVAL * 0.82f;
        world.bossCoreTimer = 0f;
        world.bossCoreCooldown = blazeInterval;
        world.breachSilenceArmed = true;
        assertTrue(beatsUntilSilence() > 0);
        assertTrue("疾火的节拍不会被这条规则改慢",
                world.bossCoreCooldown <= GameWorld.CORE_SILENCE_LEAD + 1e-3f);

        world.bossCoreCooldown = blazeInterval * 0.1f;
        world.breachSilenceArmed = true;
        beatsUntilSilence();
        assertEquals("已经比提前量更近的，一动都不动", blazeInterval * 0.1f,
                world.bossCoreCooldown, 1e-3f);
        world.applyBossAffix(BossAffix.NONE);
    }

    @Test
    public void silentFormsStillKeepTheirArmedSilence() {
        Boss.Form f = Boss.Form.CINDER;
        use(f);
        freezeHull();
        openCore();
        world.breachSilenceArmed = true;
        world.bossCoreCooldown = GameWorld.CORE_INTERVAL;
        beatsUntilSilence();
        assertTrue("静默那一拍没排到出手，别替玩家花掉哑火", world.breachSilenceArmed);
        assertEquals(0, world.breachSilences);
        assertEquals("也就没有提前这一说", GameWorld.CORE_INTERVAL,
                world.bossCoreCooldown, 1e-3f);
    }

    @Test
    public void aFreshBossDoesNotInheritTheDebt() {
        use(Boss.Form.SPECTER);
        freezeHull();
        openCore();
        world.breachSilenceArmed = true;
        assertTrue("窗口里兑的哑火先欠着", beatsUntilSilence() > 0);

        Boss b = use(Boss.Form.MIRROR);
        assertEquals("换船把节拍清回入场那一档", GameWorld.CORE_FIRST_DELAY,
                world.bossCoreCooldown, 1e-3f);
        events.clear();
        step((int) (GameWorld.CORE_FIRST_DELAY * FRAMES_PER_SECOND) + 6);
        assertTrue("该开核了", world.bossCoreOpen());
        assertEquals("欠的那笔不该跟到下一艘船",
                0, countOf(GameWorld.EventType.BOSS_CORE_LEAD));
        assertEquals(0, world.coreEchoProgress());
        assertFalse(world.coreEchoPaid);
        assertTrue(b.active);
    }

    @Test
    public void restartClearsTheEchoLedger() {
        Boss.Form f = Boss.Form.HIVE;
        use(f);
        freezeHull();
        openCore();
        openCounterWindow();
        bank(GameWorld.CORE_ECHO_HITS);
        assertEquals(1, world.finaleEchoes);

        world.restart(GameMode.WAVES);
        drain();
        use(f);
        assertEquals("共鸣进度是局内账", 0, world.coreEchoProgress());
        assertFalse(world.coreEchoPaid);
        assertEquals(0, world.coreEchoHits);
        assertEquals("识破那本账跟着整局清掉", 0, world.formReads[f.ordinal()]);
        assertEquals("提前量也该归零", GameWorld.CORE_FIRST_DELAY,
                world.bossCoreCooldown, 1e-3f);
    }

    // ---- 四、口径与读数 ----

    @Test
    public void theEchoReadoutsFollowTheLedger() {
        use(Boss.Form.HIVE);
        freezeHull();
        openCore();
        openCounterWindow();
        assertEquals(0, world.coreEchoProgress());
        bank(1);
        assertEquals(1, world.coreEchoProgress());
        bank(2);
        assertEquals(3, world.coreEchoProgress());
        assertTrue("提示行的余辉跟着记数亮", world.coreEchoFlash > 0f);
        bank(1);
        assertEquals(0, world.coreEchoProgress());
        assertTrue(world.coreEchoPaid);

        step((int) (GameWorld.CORE_ECHO_FLASH_TIME * FRAMES_PER_SECOND) + 6);
        assertEquals("共鸣的余辉该自己收走", 0f, world.coreEchoFlash, 1e-3f);
        assertEquals("提前那行的余辉也归零", 0f, world.coreLeadFlash, 1e-3f);
    }

    @Test
    public void theEchoBudgetSitsInsideTheWindow() {
        assertEquals("四记共鸣：一窗五秒，够打但不白送", 4, GameWorld.CORE_ECHO_HITS);
        assertTrue("共鸣的加分要明显高于核心那一记普通分",
                GameWorld.CORE_ECHO_SCORE_BASE > 8 * GameWorld.CORE_HIT_SCORE);
        assertTrue("提前量必须短于常规间隔",
                GameWorld.CORE_SILENCE_LEAD < GameWorld.CORE_INTERVAL);
        assertTrue("也得短于入场后的第一次",
                GameWorld.CORE_SILENCE_LEAD < GameWorld.CORE_FIRST_DELAY);
        assertTrue("提示行的余辉比窗口长，但别拖到下一个人",
                GameWorld.CORE_ECHO_FLASH_TIME >= 1.2f
                        && GameWorld.CORE_ECHO_FLASH_TIME
                        <= Boss.coreFinaleFor(Boss.Form.HIVE).window + 0.05f);
        for (Boss.Form f : Boss.Form.values()) {
            assertTrue(f.label + " 的窗口装不满四记共鸣太亏",
                    Boss.coreFinaleFor(f).window >= 2.6f);
        }
    }

    @Test
    public void theEchoIsNotADamageOrCadenceChange() {
        use(Boss.Form.SENTINEL);
        freezeHull();
        openCore();
        openCounterWindow();
        assertEquals("静默系的终章口径没被共鸣改动",
                Boss.CORE_SILENT, world.coreFinale().mode);
        float interval = world.bossCoreCooldown;
        bank(GameWorld.CORE_ECHO_HITS);
        assertEquals("兑账不改节拍", interval, world.bossCoreCooldown, 1e-3f);
        assertEquals("也不改双倍圈的伤害倍率",
                2, GameWorld.CORE_DAMAGE_MULT);
        assertNotNull(GameWorld.EventType.BOSS_CORE_ECHO);
        assertNotNull(GameWorld.EventType.BOSS_CORE_ECHO_STEP);
        assertNotNull(GameWorld.EventType.BOSS_CORE_LEAD);
    }

    // ---- 辅助：按真正的节拍把核心开出来（不是手拨计时器） ----

    private void openCoreByCadence() {
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        events.clear();
        int frames = 0;
        while (!world.bossCoreOpen() && frames < 900) {
            step(1);
            frames++;
        }
        assertTrue("按排好的节拍该开核", world.bossCoreOpen());
    }
}
