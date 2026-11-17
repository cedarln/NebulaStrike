package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.61 迭代 51：旗舰形态 BOSS FORMS。
 *
 * <p>解决"每艘 Boss 长得都差不多、技能也都差不多"：舰级（tier）继续负责多大多硬，
 * 形态（form）负责长什么样、怎么动、招牌招式是哪一招。前七段航程用七种常规形态，
 * 第八段固定由星核形态压阵，无尽与回声周目按同一循环滚回来。
 * 招牌招式插在常规节拍的空档里（扩散环与弹幕网优先），所以既有招式测试全部照旧。
 */
public class BossFormsV161Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261117L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.restart(GameMode.WAVES);
        shieldForever();
        drain();
    }

    // ---- 辅助 ----

    /** 长跑几秒的招式测试需要真无敌：makeInvulnerable() 默认只管一次重生的时长。 */
    private void shieldForever() {
        world.ship.makeInvulnerable(9999f);
    }

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

    /** 把旗舰换成指定型号，摆到"已就位、已进入指定相位"的状态。 */
    private void place(int level, int phase) {
        shieldForever();
        world.wave = 4;
        step(1);
        assertTrue("第 4 波应该压旗舰", world.boss.active);
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        world.boss.init(level);
        world.boss.y = world.boss.stationY;
        world.boss.hp = world.boss.maxHp * (phase == 1 ? 0.9f : phase == 2 ? 0.5f : 0.2f);
        step(1);
        assertEquals("相位由血量决定", phase, world.boss.phase);
        drain();
    }

    private void clearHostiles() {
        for (EnemyBullet b : world.enemyBullets) b.active = false;
        for (Enemy e : world.enemies) e.active = false;
    }

    private int activeBullets() {
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active) n++;
        return n;
    }

    private int activeFlag(BulletFlag f) {
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active && f.test(b)) n++;
        return n;
    }

    private int activeEnemies() {
        int n = 0;
        for (Enemy e : world.enemies) if (e.active) n++;
        return n;
    }

    private interface BulletFlag {
        boolean test(EnemyBullet b);
    }

    private float span(boolean horizontal, int level, int frames) {
        place(level, 2);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int i = 0; i < frames; i++) {
            world.boss.update(1f / 60f);
            float v = horizontal ? world.boss.x : world.boss.y;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return max - min;
    }

    // ---- 形态分配 ----

    @Test
    public void eachSectorGetsItsOwnForm() {
        Set<Boss.Form> unique = new HashSet<>();
        for (int level = 0; level < Story.SECTOR_COUNT - 1; level++) {
            unique.add(Boss.formFor(level));
        }
        assertEquals("前七段各是一种形态", Story.SECTOR_COUNT - 1, unique.size());
        assertSame(Boss.Form.SPECTER, Boss.formFor(0));
        assertSame(Boss.Form.MIRROR, Boss.formFor(Story.SECTOR_COUNT - 2));
        assertEquals("形态名和剧情代号对得上", "镜面", Boss.formLabelFor(6));
        assertTrue("剧情里也这么叫", Story.bossName(7).contains("镜面"));
        assertTrue(Story.bossName(2).contains(Boss.formLabelFor(1)));
    }

    @Test
    public void endlessWrapsTheCycleInsteadOfRepeatingTheSameShip() {
        assertSame(Boss.Form.SPECTER, Boss.formFor(7));
        assertSame(Boss.Form.RIFT, Boss.formFor(11));
        assertNotEquals("滚回循环时形态仍在换", Boss.formFor(7), Boss.formFor(8));
        assertSame("循环周期就是七型常规旗舰", Boss.formFor(0), Boss.formFor(7));
    }

    @Test
    public void formsDifferInShapeMovementAndSignature() {
        Set<String> labels = new HashSet<>();
        Set<String> signatures = new HashSet<>();
        Set<Integer> modes = new HashSet<>();
        for (Boss.Form f : Boss.Form.values()) {
            assertTrue(f.name(), labels.add(f.label));
            assertTrue(f.name(), signatures.add(f.signature));
            modes.add(f.moveMode);
            assertTrue(f.name(), f.spinRate >= 0f);
        }
        assertEquals(8, labels.size());
        assertEquals(8, signatures.size());
        assertEquals("七种走位模式", 7, modes.size());
    }

    @Test
    public void initAssignsTheFormAndResetsItsMotionState() {
        world.boss.init(6);
        assertSame(Boss.Form.MIRROR, world.boss.form);
        assertEquals(0f, world.boss.spin, 0.001f);
        assertEquals(0f, world.boss.blink, 0.001f);
        assertEquals(GameWorld.WIDTH / 2f, world.boss.anchorX, 0.001f);
        world.boss.spin = 2f;
        world.boss.blink = 1f;
        world.boss.init(0);
        assertSame(Boss.Form.SPECTER, world.boss.form);
        assertEquals("下一艘船不带上一艘的自旋进场", 0f, world.boss.spin, 0.001f);
        assertEquals(0f, world.boss.blink, 0.001f);
    }

    // ---- 走位 ----

    @Test
    public void movementIsNotTheSameCurveForEveryHull() {
        float specterX = span(true, 0, 480), specterY = span(false, 0, 480);
        float cinderX = span(true, 1, 480), cinderY = span(false, 1, 480);
        float dockyardX = span(true, 2, 480), dockyardY = span(false, 2, 480);
        float hiveX = span(true, 3, 480), hiveY = span(false, 3, 480);
        float riftX = span(true, 4, 480);
        float magnetX = span(true, 5, 480), magnetY = span(false, 5, 480);
        float mirrorX = span(true, 6, 480), mirrorY = span(false, 6, 480);
        // 定点系几乎不走
        assertTrue("船坞钉在原地: " + dockyardX, dockyardX < 24f);
        assertTrue("镜面钉在原地: " + mirrorX, mirrorX < 24f);
        assertTrue("船坞起伏很小: " + dockyardY, dockyardY < 30f);
        // 航道高度本身也是形态的一部分：贴顶的压上、极板舰压低
        assertTrue("磁暴航道比灰烬低: " + stationOf(5) + " vs " + stationOf(1),
                stationOf(5) > stationOf(1) + 40f);
        assertTrue("船坞航道比灰烬低: " + stationOf(2) + " vs " + stationOf(1),
                stationOf(2) > stationOf(1));
        // 巡逻系横向压满航道，起伏只有呼吸感
        assertTrue("灰烬横向巡逻: " + cinderX, cinderX > 300f);
        assertTrue("灰烬起伏有限: " + cinderY, cinderY < 80f);
        // 滑行系更慢但纵向大幅摆动
        assertTrue("静默滑行比巡逻慢: " + specterX, specterX < cinderX * 0.8f);
        assertTrue("静默纵向起伏大: " + specterY, specterY > 90f);
        // 环绕系横向铺开
        assertTrue("蜂巢横向铺开: " + hiveX, hiveX > 300f);
        // 横跳系横向活动明显
        assertTrue("裂谷横向跳跃: " + riftX, riftX > 200f);
        // 极板系纵向拉满
        assertTrue("磁暴纵向起伏最大: " + magnetY,
                magnetY > specterY && magnetY > cinderY && magnetY > 140f);
    }

    /** 指定型号的形态航道高度（同一舰级下才有可比性）。 */
    private float stationOf(int level) {
        world.boss.init(level);
        return world.boss.stationY;
    }

    @Test
    public void topmostAppendageAlwaysStaysOnScreen() {
        // 舰级越高、吊舱越靠上：航道收口必须保证最上的吊舱不会被推出屏幕上沿，
        // 否则玩家既打不到护盾发生器，也看不出它下一步要丢什么
        for (int level = 0; level < 12; level++) {
            world.boss.init(level);
            Boss b = world.boss;
            float podTop = b.radius * 0.72f + 26f;   // 最高吊舱的偏移 + 吊舱半径
            b.y = b.stationY;
            b.update(1f / 60f);                       // 入场滑入结束、轨迹上锁
            float lowestY = Float.MAX_VALUE;
            for (int i = 0; i < 600; i++) {
                b.update(1f / 60f);
                if (b.y < lowestY) lowestY = b.y;
            }
            assertTrue(b.form.label + " level" + level + " 顶部吊舱出界 y=" + lowestY
                    + " 需要 >= " + podTop, lowestY >= podTop - 0.5f);
        }
    }

    @Test
    public void stationKeepingFormsStayNearTheirAnchor() {
        place(2, 2); // 船坞
        for (int i = 0; i < 300; i++) world.boss.update(1f / 60f);
        assertTrue("船坞回到锚点附近: " + world.boss.x,
                Math.abs(world.boss.x - world.boss.anchorX) < 12f);
        place(6, 2); // 镜面
        for (int i = 0; i < 300; i++) world.boss.update(1f / 60f);
        assertTrue("镜面压在中线: " + world.boss.x,
                Math.abs(world.boss.x - world.boss.anchorX) < 8f);
    }

    @Test
    public void rotatingFormsActuallySpin() {
        place(3, 2); // 蜂巢
        float before = world.boss.spin;
        for (int i = 0; i < 60; i++) world.boss.update(1f / 60f);
        assertTrue("蜂巢一秒转起来", world.boss.spin > before + 0.8f);
        place(0, 2); // 静默
        before = world.boss.spin;
        for (int i = 0; i < 60; i++) world.boss.update(1f / 60f);
        assertTrue("静默只是缓缓侧倾", world.boss.spin - before < 0.5f);
    }

    @Test
    public void specterBlinksAcrossTheArenaFromPhaseTwo() {
        place(0, 2);
        boolean blinked = false;
        for (int i = 0; i < 420; i++) {
            world.boss.update(1f / 60f);
            if (world.boss.blink > 0.5f) blinked = true;
        }
        assertTrue("二阶段起会闪现换位", blinked);

        place(0, 1);
        blinked = false;
        for (int i = 0; i < 420; i++) {
            world.boss.update(1f / 60f);
            if (world.boss.blink > 0.5f) blinked = true;
        }
        assertFalse("一阶段不闪现", blinked);
    }

    @Test
    public void everyFormStaysInsideTheArena() {
        for (int level = 0; level < Boss.Form.values().length; level++) {
            place(level, 3);
            for (int i = 0; i < 600; i++) {
                world.boss.update(1f / 60f);
                Boss b = world.boss;
                assertTrue(b.form + " 越界 x=" + b.x,
                        b.x >= b.radius + 29.9f && b.x <= GameWorld.WIDTH - b.radius - 29.9f);
                assertTrue(b.form + " 越界 y=" + b.y, b.y > 0f && b.y < GameWorld.HEIGHT);
            }
        }
    }

    @Test
    public void riftHopsAcrossTheArenaFast() {
        place(4, 2); // 裂谷
        assertSame(Boss.Form.RIFT, world.boss.form);
        float moved = 0f, hopStep = 0f, prev = world.boss.x;
        for (int i = 0; i < 300; i++) {
            world.boss.update(1f / 60f);
            float d = Math.abs(world.boss.x - prev);
            moved += d;
            if (d > hopStep) hopStep = d;
            prev = world.boss.x;
        }
        float patrol = 0f, cruiseStep = 0f;
        place(1, 2); // 灰烬：同舰级但只是巡逻
        prev = world.boss.x;
        for (int i = 0; i < 300; i++) {
            world.boss.update(1f / 60f);
            float d = Math.abs(world.boss.x - prev);
            patrol += d;
            if (d > cruiseStep) cruiseStep = d;
            prev = world.boss.x;
        }
        // 横跳不是"一直快"，而是一跳一大步、跳完停一下：单帧步长差才是它的特征。
        assertTrue("裂谷单帧跳跃远比巡逻平移猛: " + hopStep + " vs " + cruiseStep,
                hopStep > cruiseStep * 3f);
        assertTrue("裂谷总位移也更大: " + moved + " vs " + patrol, moved > patrol * 1.4f);
    }

    // ---- 招牌招式 ----

    @Test
    public void everyFormHasItsOwnSignatureVolley() {
        for (Boss.Form f : Boss.Form.values()) {
            for (int tier = 0; tier <= 3; tier++) {
                world.boss.init(tier);
                world.boss.y = world.boss.arenaY;
                clearHostiles();
                drain();
                int made = world.fireSignature(f, tier);
                assertTrue(f.name() + " tier" + tier + " 要打出弹幕", made > 0);
                assertEquals(f.name() + " 实际生成的弹数", made, activeBullets());
                drain();
                assertEquals(f.name() + " 要广播一次招式事件",
                        1, countEvents(GameWorld.EventType.BOSS_SIGNATURE));
            }
        }
    }

    @Test
    public void signaturesUseDifferentBulletKinds() {
        world.boss.init(3);
        world.boss.y = world.boss.arenaY;

        clearHostiles();
        world.fireSignature(Boss.Form.HIVE, 3);
        assertTrue("蜂巢丢漂流水雷", activeFlag(b -> b.mine) > 0);

        clearHostiles();
        world.fireSignature(Boss.Form.MAGNET, 3);
        assertTrue("磁暴丢追踪磁球", activeFlag(b -> b.homing) > 0);

        clearHostiles();
        world.fireSignature(Boss.Form.RIFT, 3);
        int fromEdges = 0;
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && (b.x < 0f || b.x > GameWorld.WIDTH)) fromEdges++;
        }
        assertTrue("交叉束从两侧屏幕边缘进场", fromEdges >= 2);
        assertEquals("裂谷不借水雷和磁球", 0, activeFlag(b -> b.mine || b.homing));

        clearHostiles();
        world.fireSignature(Boss.Form.CINDER, 3);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active) continue;
            min = Math.min(min, b.x);
            max = Math.max(max, b.x);
        }
        assertTrue("落灰盖满全场: " + (max - min), max - min > GameWorld.WIDTH * 0.85f);

        clearHostiles();
        int dockyardMade = world.fireSignature(Boss.Form.DOCKYARD, 3);
        assertTrue("船坞的招牌会带出僚机", activeEnemies() > 0);
        assertTrue(dockyardMade > 0);

        clearHostiles();
        int sentinelMade = world.fireSignature(Boss.Form.SENTINEL, 3);
        int fast = 0, slow = 0;
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active) continue;
            float sp = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
            if (sp > 380f) fast++; else slow++;
        }
        assertTrue("星核爆裂是双层速度: " + fast + "/" + slow, fast > 0 && slow > 0);
        assertEquals(sentinelMade, fast + slow);
    }

    @Test
    public void mirrorRingsAreLeftRightSymmetric() {
        world.boss.init(2);
        world.boss.y = world.boss.arenaY;
        world.boss.x = GameWorld.WIDTH / 2f;
        clearHostiles();
        int made = world.fireSignature(Boss.Form.MIRROR, 2);
        int left = 0, right = 0;
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active) continue;
            if (b.x < GameWorld.WIDTH / 2f) left++; else right++;
        }
        assertEquals("左右两环弹数一样", left, right);
        assertEquals("两个环一起张开", left + right, made);
        assertEquals(2 * (8 + 2 * 2), made);
    }

    @Test
    public void signaturesFillTheGapsBetweenGenericVolleys() {
        place(3, 2); // 蜂巢，tier 3：既有环和网，也该有招牌
        int nets = 0, signatures = 0;
        for (int block = 0; block < 8; block++) {
            clearHostiles();
            drain();
            step(300); // 5 秒
            nets += countEvents(GameWorld.EventType.BOSS_NET);
            signatures += countEvents(GameWorld.EventType.BOSS_SIGNATURE);
        }
        // 招牌只插在空档里（每 4~5 轮一次），不抢环和网的节拍：两者都该有，且网更密。
        assertTrue("招牌招式确实打了出来: " + signatures, signatures >= 2);
        assertTrue("弹幕网的骨架节拍没被挤掉: " + nets, nets >= 2);
        assertTrue("招牌只是加料，不是替换: 网" + nets + " 招牌" + signatures, nets >= signatures / 2);
    }

    @Test
    public void signatureFlashMarksTheVolleyForTheHud() {
        place(0, 2);
        assertEquals(0f, world.signatureFlash, 0.001f);
        clearHostiles();
        world.fireSignature(Boss.Form.SPECTER, 0);
        assertTrue(world.signatureFlash > 0f);
        assertSame(Boss.Form.SPECTER, world.signatureForm);
        drain();
        step(120);
        assertEquals("余辉两秒后自己收掉", 0f, world.signatureFlash, 0.001f);
    }

    // ---- 与既有系统的边界 ----

    @Test
    public void theFinalFlagshipTakesTheSentinelForm() {
        assertNotEquals("按航段循环本来给的不是星核",
                Boss.Form.SENTINEL, Boss.formFor(GameWorld.FINAL_BOSS_LEVEL));
        world.bossesDefeated = Story.SECTOR_COUNT - 1;
        world.wave = world.nextBossWave;
        world.bossGapTimer = 0f;
        step(1);
        assertTrue(world.boss.active);
        assertSame(Boss.Form.SENTINEL, world.boss.form);
        assertEquals(1, countEvents(GameWorld.EventType.BOSS_FORM));
        assertEquals(Boss.Form.SENTINEL.ordinal(), latestFormCode());
    }

    @Test
    public void spawnAnnouncesTheForm() {
        world.wave = 4;
        step(1);
        assertTrue(world.boss.active);
        assertEquals("入场就播报形态", 1, countEvents(GameWorld.EventType.BOSS_FORM));
        assertSame(Boss.Form.SPECTER, Boss.Form.values()[latestFormCode()]);
    }

    private int latestFormCode() {
        int code = -1;
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.BOSS_FORM) code = e.code;
        }
        assertTrue("应该有形态播报事件", code >= 0);
        return code;
    }

    @Test
    public void echoFightsKeepTheSameFormRule() {
        place(3, 3); // 蜂巢
        assertSame(Boss.Form.HIVE, world.boss.form);
        world.boss.hp = 0f;
        step(1);
        assertTrue("无伤击破才会留残响", world.echoPendingTier >= 0);
        world.bossGapTimer = 0f;
        world.wave = world.echoSpawnWave;
        step(1);
        assertTrue("残响登场", world.boss.active);
        assertTrue(world.echoBossFight);
        assertSame("残响沿用本体那型船的外形与走位",
                Boss.formFor(world.boss.tier), world.boss.form);
    }

    @Test
    public void formsDoNotChangeStatsOrProgression() {
        place(4, 2); // 裂谷
        int tier = world.boss.tier;
        float maxHp = world.boss.maxHp;
        int defeats = world.bossesDefeated;
        world.boss.hp = 0f;
        step(1);
        assertEquals("形态不改血量", maxHp, world.boss.maxHp, 0.001f);
        assertEquals("形态不额外推进航段", defeats + 1, world.bossesDefeated);
        assertEquals(tier, world.boss.tier);
        assertFalse("击破后形态字段不影响下一艘的命名",
                world.boss.active);
    }

    @Test
    public void practiceModeUsesTheSameFormForTheSameHull() {
        world.restart(GameMode.PRACTICE);
        shieldForever();
        world.practiceTier = 5;
        world.bossGapTimer = 0f;
        step(1);
        assertTrue("练功也能开旗舰", world.boss.active);
        assertEquals("练功档位仍夹在 0..3", 3, world.boss.tier);
        assertSame("同一艘船用同一型形态", Boss.formFor(3), world.boss.form);
        assertSame(Boss.Form.HIVE, world.boss.form);
        assertEquals("练功同样播报形态", 1, countEvents(GameWorld.EventType.BOSS_FORM));
    }
}
