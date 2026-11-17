package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.71 迭代 61：攻坚路线 · BREACH ROUTE。
 *
 * <p>v1.70 把舰装交回给形态之后，八型旗舰确实各有一套部件排布了——但"知道它们摆在哪儿"和
 * "照着这个顺序拆"之间还差一句话。这一版把那句话补上：每一型给自己的舰装一条拆卸路线
 * （{@link Boss.Form#breach}），路线由站位坐标算成层号（{@link Boss#breachOrder}），
 * 玩家顺着层号往下拆就有连段加分，一整条线走完另有大奖。
 *
 * <p>三条硬口径：① <b>只奖不罚</b>——插队拆掉的那件照样碎、照样拿原本的部件分，
 * 掉的只是连段倍率，绝不倒扣分数，也不改变舰装的耐久；② 层号是从 v1.70 的站位算出来的，
 * 不是另写的一本账，所以它不可能和玩家看到的部件位置打架，对称的一对同层（不逼人二选一）；
 * ③ 棱镜（PRISM）是开打后追加的，不在舰装这本账上——拆它既不算顺线也不算插队。
 */
public class BossBreachRouteV171Test {

    private static final Boss.Form[] FORMS = Boss.Form.values();
    /** 舰级 0..3 的部件件数，沿用 v1.70。 */
    private static final int[] COUNT_BY_TIER = {2, 6, 9, 12};
    /** 量路线用舰级 3：件数最多，八型的差别也最明显。 */
    private static final int FULL_TIER = 3;

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261126L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.restart(GameMode.WAVES);
        events.clear();
        drain();
    }

    // ---- 辅助 ----

    private void drain() {
        world.pollEvents();
    }

    /** 单独造一艘"这一型、这一关"的旗舰：init 摆好件数与血量，再换成指定形态重排舰装。 */
    private Boss bossOf(Boss.Form form, int level) {
        Boss b = new Boss();
        b.init(level);
        b.form = form;
        b.layoutParts();
        return b;
    }

    private int[] ranksOf(Boss b) {
        int[] r = new int[b.partCount];
        for (int i = 0; i < b.partCount; i++) r[i] = b.parts[i].breachRank;
        return r;
    }

    /** 站位归一化（除以舰半径），与 v1.70 的测法一致。 */
    private float[][] stationsOf(Boss b) {
        float[][] s = new float[b.partCount][2];
        for (int i = 0; i < b.partCount; i++) {
            s[i][0] = b.parts[i].ox / b.radius;
            s[i][1] = b.parts[i].oy / b.radius;
        }
        return s;
    }

    /**
     * 测试侧另算一遍拆卸键——故意不共用生产代码里那个 private 方法。
     * 生产哪天改了键序，这里的断言就会指出来：路线话术和真实顺序必须一起改。
     */
    private float keyFor(Boss.Form form, float x, float y) {
        switch (form) {
            case SPECTER: return -Math.abs(x);
            case CINDER: return -x;
            case DOCKYARD: return -y;
            case HIVE: return (float) Math.atan2(y, x);
            case RIFT: return -(x + y);
            case MAGNET: return -x;
            case MIRROR: return -(float) Math.hypot(x, y);
            case SENTINEL: return Math.abs(x);
            default: return 0f;
        }
    }

    /** 件按层分组：layers[k] 是层号 k 上的部件下标。 */
    private List<int[]> layerBuckets(int[] ranks) {
        int levels = 0;
        for (int r : ranks) levels = Math.max(levels, r + 1);
        int[] count = new int[levels];
        for (int r : ranks) count[r]++;
        List<int[]> out = new ArrayList<>();
        int[] at = new int[levels];
        for (int k = 0; k < levels; k++) out.add(new int[count[k]]);
        for (int i = 0; i < ranks.length; i++) out.get(ranks[i])[at[ranks[i]]++] = i;
        return out;
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

    /** 把旗舰钉在航道高度上：部件的相对位置不动，一帧一帧量才不会漂。 */
    private void parkBoss() {
        world.boss.y = world.boss.stationY;
        world.boss.x = GameWorld.WIDTH / 2f;
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) {
            world.ship.weaponTimer = 999f;   // 别让飞船自己的弹混进这一件
            world.update(1f / 60f);
            world.pollEvents();
        }
    }

    /** 这一帧只打掉指定那一件，返回这一件给分了多少（部件分 + 路线分一起算）。 */
    private int killPart(int index) {
        Boss b = world.boss;
        BossPart p = b.parts[index];
        assertTrue("这件本来就该还活着", p.active);
        p.hp = 0.5f;
        // 相邻站位只隔 0.26 个半径，一颗子弹能同时压着两三件，命中循环先撞到谁算谁。
        // 这里把别件的判定半径临时收小，保证这一发只打在要量的那一件上，量完再还原。
        float[] savedRadius = new float[b.partCount];
        for (int k = 0; k < b.partCount; k++) {
            savedRadius[k] = b.parts[k].radius;
            if (k != index && b.parts[k].active) b.parts[k].radius = 1f;
        }
        int before = world.score;
        // 船的走位每帧自己重算横坐标，parkBoss 钉的是"这一帧开头"，一帧过去它已经挪走 30 多像素，
        // 子弹判定圈（半径 9 + 件 24）擦一下就过去了。所以先量出上一帧的位移，把子弹按在
        // "件这一帧会到的地方"等着——预测准了第一帧就中，多给的帧数只是兜底。
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
            // 场上的旧弹全收走：它们停在半空会替目标挨这一发，量出来就不是顺序了
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
        return world.score - before;
    }

    /** 弹池里挑一发空位。场上的弹在 killPart 里每帧清空，池子常年是空的。 */
    private Bullet freeBullet() {
        for (Bullet bl : world.bullets) {
            if (!bl.active) return bl;
        }
        throw new IllegalStateException("弹池该有空位");
    }

    /** 还活着的件里，层号最小的那几件（= 现在该拆的那一层）。 */
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

    /** 还活着的件里，层号最大的那一件（= 插队用的那件）。 */
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

    private int countEvents(GameWorld.EventType type) {
        int n = 0;
        for (GameWorld.GameEvent e : events) {
            if (e.type == type) n++;
        }
        return n;
    }

    private List<GameWorld.GameEvent> ofType(GameWorld.EventType type) {
        List<GameWorld.GameEvent> out = new ArrayList<>();
        for (GameWorld.GameEvent e : events) {
            if (e.type == type) out.add(e);
        }
        return out;
    }

    /** 部件自身的分数口径（v1.31 起就没动过），用来把路线分从总账里剥出来。 */
    private int partBaseScore(BossPart p, int level) {
        return 80 + level * 20 + (p.kind == BossPart.Kind.SHIELD ? 150 : 0);
    }

    // ---- 一、八型八条路线：纯数据 ----

    @Test
    public void theEightFormsNameEightDifferentRoutes() {
        Set<String> seen = new HashSet<>();
        for (Boss.Form f : FORMS) {
            assertNotNull(f.name(), f.breach);
            assertTrue(f.name() + " 的路线太长了: " + f.breach, f.breach.length() <= 6);
            assertTrue(f.name() + " 的路线是空的", f.breach.length() >= 3);
            assertTrue("路线撞句了: " + f.breach, seen.add(f.breach));
        }
        assertEquals(8, seen.size());
    }

    @Test
    public void theRouteWordsMatchTheRigWords() {
        // 舰装那句说的是"摆在哪儿"，路线那句说的是"从哪儿拆"——两句话得指同一件事
        assertEquals("两翼拉开", Boss.Form.SPECTER.loadout);
        assertEquals("先拆翼尖", Boss.Form.SPECTER.breach);
        assertEquals("前压一字", Boss.Form.CINDER.loadout);
        assertEquals("自右向左", Boss.Form.CINDER.breach);
        assertEquals("双层甲板", Boss.Form.DOCKYARD.loadout);
        assertEquals("先拆下甲板", Boss.Form.DOCKYARD.breach);
        assertEquals("六格巢环", Boss.Form.HIVE.loadout);
        assertEquals("顺环而拆", Boss.Form.HIVE.breach);
        assertEquals("斜交叉架", Boss.Form.RIFT.loadout);
        assertEquals("顺斜线拆", Boss.Form.RIFT.breach);
        assertEquals("双极团簇", Boss.Form.MAGNET.loadout);
        assertEquals("先拆一极", Boss.Form.MAGNET.breach);
        assertEquals("内外双环", Boss.Form.MIRROR.loadout);
        assertEquals("先外后内", Boss.Form.MIRROR.breach);
        assertEquals("护心弧墙", Boss.Form.SENTINEL.loadout);
        assertEquals("先破墙心", Boss.Form.SENTINEL.breach);
    }

    @Test
    public void everyFormGetsARouteAtEveryRosterSize() {
        for (Boss.Form f : FORMS) {
            for (int n = 0; n <= Boss.MAX_PARTS; n++) {
                int[] r = Boss.breachOrder(f, n);
                assertEquals(f.name() + " 件数对不上", n, r.length);
                for (int i = 0; i < n; i++) {
                    assertTrue(f.name() + " 第 " + i + " 件没有层号", r[i] >= 0);
                    assertTrue(f.name() + " 层号越界: " + r[i], r[i] < n);
                }
            }
        }
    }

    @Test
    public void theLayersAreDenseAndCoverEveryPart() {
        for (Boss.Form f : FORMS) {
            for (int tier = 0; tier < COUNT_BY_TIER.length; tier++) {
                int n = COUNT_BY_TIER[tier];
                int[] r = Boss.breachOrder(f, n);
                Set<Integer> used = new HashSet<>();
                for (int v : r) used.add(v);
                int levels = 0;
                for (int v : r) levels = Math.max(levels, v + 1);
                assertEquals(f.name() + " 层号有空洞", levels, used.size());
                for (int k = 0; k < levels; k++) {
                    assertTrue(f.name() + " 缺第 " + k + " 层", used.contains(k));
                }
                // 舰级 0 只有两件、静默与船坞还把它们排在同一层——单层是允许的，
                // 但满编 12 件时必须真的排出多层来
                if (n == 12) assertTrue(f.name() + " 满编只有一层，谈不上路线", levels >= 2);
            }
        }
    }

    @Test
    public void theRouteIsTheSameEveryTimeItIsRolled() {
        for (Boss.Form f : FORMS) {
            int[] a = Boss.breachOrder(f, 12);
            int[] b = Boss.breachOrder(f, 12);
            assertTrue(f.name() + " 的路线不稳定", Arrays.equals(a, b));
            Boss one = bossOf(f, FULL_TIER), two = bossOf(f, FULL_TIER);
            assertTrue(f.name() + " 两次排装的层号不一样",
                    Arrays.equals(ranksOf(one), ranksOf(two)));
        }
    }

    @Test
    public void theRouteIsReadOffTheRigItself() {
        // 层号必须由站位坐标算出来：键小的层号小，同键的必须同层
        for (Boss.Form f : FORMS) {
            float[][] s = Boss.formStations(f, 12);
            int[] r = Boss.breachOrder(f, 12);
            for (int i = 0; i < s.length; i++) {
                for (int j = 0; j < s.length; j++) {
                    float ki = keyFor(f, s[i][0], s[i][1]);
                    float kj = keyFor(f, s[j][0], s[j][1]);
                    if (ki < kj - 1e-4f) {
                        assertTrue(f.name() + " 的拆法逆了键序", r[i] < r[j]);
                    } else if (Math.abs(ki - kj) <= 1e-4f) {
                        assertEquals(f.name() + " 同一层的两件分了先后", r[i], r[j]);
                    }
                }
            }
        }
    }

    @Test
    public void everyPairOfFormsWalksADifferentRoute() {
        for (int a = 0; a < FORMS.length; a++) {
            for (int b = a + 1; b < FORMS.length; b++) {
                int[] ra = Boss.breachOrder(FORMS[a], 12);
                int[] rb = Boss.breachOrder(FORMS[b], 12);
                assertFalse(FORMS[a] + " 和 " + FORMS[b] + " 拆法相同", Arrays.equals(ra, rb));
            }
        }
    }

    @Test
    public void specterPeelsInwardFromTheWingTips() {
        float[][] s = Boss.formStations(Boss.Form.SPECTER, 12);
        int[] r = Boss.breachOrder(Boss.Form.SPECTER, 12);
        int tip = 0;
        for (int i = 1; i < s.length; i++) {
            if (Math.abs(s[i][0]) > Math.abs(s[tip][0])) tip = i;
        }
        assertEquals("翼尖那一层就是第一层", 0, r[tip]);
        // 镜像成对：左右两枚翼尖同层，玩家先点哪边都算顺线
        List<int[]> layers = layerBuckets(r);
        assertEquals("静默的舰装是一对一对往里收的", 2, layers.get(0).length);
        for (int i : layers.get(0)) {
            assertTrue("第一层就该是最外的那两件", Math.abs(s[i][0]) > Math.abs(s[tip][0]) - 1e-3f);
        }
    }

    @Test
    public void cinderSweepsRightToLeft() {
        float[][] s = Boss.formStations(Boss.Form.CINDER, 12);
        int[] r = Boss.breachOrder(Boss.Form.CINDER, 12);
        int right = 0, left = 0;
        for (int i = 1; i < s.length; i++) {
            if (s[i][0] > s[right][0]) right = i;
            if (s[i][0] < s[left][0]) left = i;
        }
        assertEquals("最右那一件该是第一个目标", 0, r[right]);
        assertEquals("最左那一件该排在最后", maxRank(r), r[left]);
        // 键是 -x，所以同层的两件只可能是 x 相同的一对，层号随 x 单调
        for (int i = 0; i < s.length; i++) {
            for (int j = 0; j < s.length; j++) {
                if (s[i][0] > s[j][0] + 1e-3f) {
                    assertTrue("灰烬该自右向左刮", r[i] < r[j]);
                }
            }
        }
    }

    @Test
    public void dockyardClearsTheLowerDeckFirst() {
        float[][] s = Boss.formStations(Boss.Form.DOCKYARD, 12);
        int[] r = Boss.breachOrder(Boss.Form.DOCKYARD, 12);
        int lower = 0, upper = 0;
        for (int i = 0; i < s.length; i++) {
            if (s[i][1] > 0.4f) lower++;
            if (s[i][1] < 0.1f) upper++;
        }
        assertEquals("下甲板该有 6 件", 6, lower);
        assertEquals("上甲板该有 6 件", 6, upper);
        List<int[]> layers = layerBuckets(r);
        assertEquals("船坞只有两层甲板", 2, layers.size());
        assertEquals("第一层就是贴近玩家的下甲板", 6, layers.get(0).length);
        for (int i = 0; i < s.length; i++) {
            boolean lowerDeck = s[i][1] > 0.4f;
            assertEquals("哪块甲板先刮不该看错", lowerDeck ? 0 : 1, r[i]);
        }
    }

    @Test
    public void hiveWalksItsPartsAroundTheHull() {
        float[][] s = Boss.formStations(Boss.Form.HIVE, 12);
        int[] r = Boss.breachOrder(Boss.Form.HIVE, 12);
        // 顺环而拆 = 极角单调走一圈，不来回跳
        Integer[] byRank = new Integer[s.length];
        for (int i = 0; i < s.length; i++) byRank[i] = i;
        final int[] rr = r;
        Arrays.sort(byRank, (a, b) -> rr[a] != rr[b] ? Integer.compare(rr[a], rr[b])
                : Integer.compare(a, b));
        for (int k = 1; k < byRank.length; k++) {
            float pa = (float) Math.atan2(s[byRank[k - 1]][1], s[byRank[k - 1]][0]);
            float pb = (float) Math.atan2(s[byRank[k]][1], s[byRank[k]][0]);
            assertTrue("蜂巢的路线在环上回头了", pb >= pa - 1e-4f);
        }
        assertTrue("顺环一圈该有多层", maxRank(r) >= 5);
    }

    @Test
    public void riftFollowsTheDiagonal() {
        float[][] s = Boss.formStations(Boss.Form.RIFT, 12);
        int[] r = Boss.breachOrder(Boss.Form.RIFT, 12);
        for (int i = 0; i < s.length; i++) {
            for (int j = 0; j < s.length; j++) {
                float si = s[i][0] + s[i][1], sj = s[j][0] + s[j][1];
                if (si > sj + 1e-3f) {
                    assertTrue("裂谷该沿斜线一件接一件", r[i] < r[j]);
                }
            }
        }
        assertTrue("斜线上该排开好几层", maxRank(r) >= 5);
    }

    @Test
    public void magnetTakesOneWholePoleBeforeTheOther() {
        float[][] s = Boss.formStations(Boss.Form.MAGNET, 12);
        int[] r = Boss.breachOrder(Boss.Form.MAGNET, 12);
        int maxRight = -1, minLeft = Integer.MAX_VALUE;
        int right = 0, left = 0;
        for (int i = 0; i < s.length; i++) {
            if (s[i][0] > 0f) {
                right++;
                maxRight = Math.max(maxRight, r[i]);
            } else {
                left++;
                minLeft = Math.min(minLeft, r[i]);
            }
        }
        assertEquals("右极 6 件", 6, right);
        assertEquals("左极 6 件", 6, left);
        assertTrue("一极整极拆完才换另一极", maxRight < minLeft);
    }

    @Test
    public void mirrorStripsTheOuterRingFirst() {
        float[][] s = Boss.formStations(Boss.Form.MIRROR, 12);
        int[] r = Boss.breachOrder(Boss.Form.MIRROR, 12);
        for (int i = 0; i < s.length; i++) {
            for (int j = 0; j < s.length; j++) {
                float di = (float) Math.hypot(s[i][0], s[i][1]);
                float dj = (float) Math.hypot(s[j][0], s[j][1]);
                if (di > dj + 1e-3f) {
                    assertTrue("镜面该外环剥完才轮得到内环", r[i] < r[j]);
                }
            }
        }
    }

    @Test
    public void sentinelBreaksTheWallHeartFirst() {
        float[][] s = Boss.formStations(Boss.Form.SENTINEL, 12);
        int[] r = Boss.breachOrder(Boss.Form.SENTINEL, 12);
        List<int[]> layers = layerBuckets(r);
        int[] heart = layers.get(0);
        assertTrue("墙心至少该有一件", heart.length >= 1);
        for (int i : heart) {
            assertTrue("第一层该正对玩家", Math.abs(s[i][0]) < 0.3f);
            assertTrue("星核的件都在核心前面", s[i][1] > 0f);
        }
        // 越靠两侧的件越晚拆：弧墙是从中间凿开的
        for (int i = 0; i < s.length; i++) {
            for (int j = 0; j < s.length; j++) {
                if (Math.abs(s[i][0]) > Math.abs(s[j][0]) + 1e-3f) {
                    assertTrue("护心弧墙该从墙心凿开", r[i] > r[j]);
                }
            }
        }
    }

    // ---- 二、层号发在部件身上 ----

    @Test
    public void aSpawnedBossCarriesItsRouteOnEveryPart() {
        for (int level = 0; level < FORMS.length - 1; level++) {
            Boss b = bossOf(Boss.formFor(level), level);
            int[] r = Boss.breachOrder(b.form, b.partCount);
            int[] onParts = ranksOf(b);
            assertTrue("第 " + level + " 艘的层号和预演不一致", Arrays.equals(r, onParts));
        }
    }

    @Test
    public void theFinalBossWearsTheWallItAdvertises() {
        // v1.70 漏的一角：init 按循环形态摆过一次件，换成星核之后必须重摆，
        // 否则横幅写「护心弧墙」、身上却是别的型的排布，路线也就指错了地方
        // 最终旗舰只在"打完第 8 段航程"那一艘登场，量它得按真正的入口走
        assertEquals(GameWorld.FINAL_BOSS_LEVEL, Story.SECTOR_COUNT + 2);
        Boss spawn = triggerBoss(Story.SECTOR_COUNT - 1);
        assertTrue("这一艘该是最终旗舰", world.finalBossFight);
        assertEquals(GameWorld.FINAL_BOSS_LEVEL, spawn.level);
        Boss b = world.boss;
        assertEquals(Boss.Form.SENTINEL, b.form);
        assertEquals("护心弧墙", b.form.loadout);
        int[] r = ranksOf(b);
        int[] expected = Boss.breachOrder(Boss.Form.SENTINEL, b.partCount);
        assertTrue("最终旗舰的舰装与形态不符", Arrays.equals(expected, r));
        float[][] st = stationsOf(b);
        for (int i = 0; i < st.length; i++) {
            assertTrue("最终旗舰的件不能在核心上面", st[i][1] > 0f);
            assertEquals(b.parts[i].kind + " 层号丢了", b.parts[i].breachRank, r[i]);
        }
    }

    @Test
    public void prismsAreNotOnTheRoute() {
        Boss b = bossOf(Boss.Form.HIVE, FULL_TIER);
        int before = b.partCount;
        b.tier = 3;
        int made = b.summonPrisms();
        assertTrue("棱镜该召唤出来", made > 0);
        for (int i = before; i < b.partCount; i++) {
            assertEquals("追加的棱镜不该有层号", -1, b.parts[i].breachRank);
            assertEquals(BossPart.Kind.PRISM, b.parts[i].kind);
        }
        for (int i = 0; i < before; i++) {
            assertTrue("原有的舰装不该被棱镜抹掉", b.parts[i].breachRank >= 0);
        }
    }

    @Test
    public void pooledPartsLoseTheirOldRouteOnReinit() {
        Boss b = bossOf(Boss.Form.SPECTER, FULL_TIER);
        assertTrue(ranksOf(b)[0] >= 0);
        b.parts[0].init(BossPart.Kind.GUN, 0f, 0f, 20f, 10f, 1f);
        assertEquals("池化部件 reuse 之前该把上一艘船的路线清掉", -1, b.parts[0].breachRank);
    }

    // ---- 三、真拆一遍：顺线给分、插队断线、拆穿给大奖 ----

    @Test
    public void theMultiplierAndTheBonusAreFixedTables() {
        assertEquals(1, GameWorld.breachMultFor(0));
        assertEquals(1, GameWorld.breachMultFor(2));
        assertEquals(2, GameWorld.breachMultFor(3));
        assertEquals(2, GameWorld.breachMultFor(5));
        assertEquals(3, GameWorld.breachMultFor(6));
        assertEquals(3, GameWorld.breachMultFor(8));
        assertEquals(4, GameWorld.breachMultFor(9));
        assertEquals(4, GameWorld.breachMultFor(40));
        assertEquals(60, GameWorld.breachStepScore(0, 1));
        assertEquals(120, GameWorld.breachStepScore(3, 1));
        assertEquals(240, GameWorld.breachStepScore(3, 3));
        assertEquals(480, GameWorld.breachStepScore(3, 9));
        assertEquals(700, GameWorld.breachGrandScoreFor(0));
        assertEquals(1300, GameWorld.breachGrandScoreFor(3));
    }

    @Test
    public void theSpawnSeedsTheRouteLedger() {
        Boss b = triggerBoss(FULL_TIER);
        assertEquals(b.partCount, world.breachRigParts);
        assertEquals(b.partCount, world.breachRemaining);
        assertEquals(0, world.breachNext);
        assertEquals(0, world.breachStreak);
        assertEquals(0, world.breachBroken);
        assertEquals(0, world.breachParts);
        assertTrue(world.breachLevels >= 2);
        assertEquals("舰装件数还是只看舰级", COUNT_BY_TIER[FULL_TIER], b.partCount);
    }

    @Test
    public void peelingInOrderPaysTheStreak() {
        Boss b = triggerBoss(FULL_TIER);
        int level = b.level;
        int steps = 0;
        while (nextTargetIndex() >= 0) {
            int i = nextTargetIndex();
            int rank = b.parts[i].breachRank;
            boolean lastOfLayer = true;
            for (int k = 0; k < b.partCount; k++) {
                if (k != i && b.parts[k].active && b.parts[k].breachRank == rank) lastOfLayer = false;
            }
            events.clear();
            int before = world.breachNext;
            int gained = killPart(i);
            steps++;
            int expected = partBaseScore(b.parts[i], level)
                    + GameWorld.breachStepScore(level, steps);
            if (world.breachRemaining == 0) {
                // 拆空整条舰装那一下，除了这一件自己的钱，还会一起结清两件老/新账：
                // v1.20 就有的缴械奖 300，和这一版的一件没插队的大奖。
                expected += 300 + GameWorld.breachGrandScoreFor(level);
                assertEquals(1, countEvents(GameWorld.EventType.BOSS_DISARMED));
                assertEquals(1, countEvents(GameWorld.EventType.BOSS_BREACH_PERFECT));
            }
            assertEquals("顺线拆第 " + steps + " 件该给"
                    + "部件分+路线分", expected, gained);
            assertEquals("这一件该算顺线", steps, world.breachStreak);
            assertEquals(1, countEvents(GameWorld.EventType.BOSS_BREACH_STEP));
            assertEquals(steps, ofType(GameWorld.EventType.BOSS_BREACH_STEP).get(0).code);
            assertEquals(steps, world.breachParts);
            assertEquals("插队数为 0 才对得起连段", 0, world.breachBroken);
            if (world.breachRemaining == 0) {
                assertEquals("整条线拆完，线头该推到尽头", world.breachLevels, world.breachNext);
            } else if (lastOfLayer) {
                assertTrue("拆完一层线头该往前推", world.breachNext > before);
            } else {
                assertEquals("同层还有件就没到换层的时候", before, world.breachNext);
            }
        }
        assertEquals(COUNT_BY_TIER[FULL_TIER], steps);
        assertEquals(COUNT_BY_TIER[FULL_TIER], world.breachBest);
        assertEquals(0, world.breachRemaining);
    }

    @Test
    public void cuttingInLineKeepsThePartButBreaksTheStreak() {
        Boss b = triggerBoss(FULL_TIER);
        int level = b.level;
        int first = nextTargetIndex();
        int far = lastTargetIndex();
        assertTrue("这艘船得有不止一层", b.parts[far].breachRank > b.parts[first].breachRank);
        events.clear();
        int nextBefore = world.breachNext;
        int gained = killPart(far);
        assertEquals("插队只拿部件分", partBaseScore(b.parts[far], level), gained);
        assertEquals(0, world.breachStreak);
        assertEquals(1, world.breachBroken);
        assertEquals("插队不算顺线件数", 0, world.breachParts);
        assertEquals(1, countEvents(GameWorld.EventType.BOSS_BREACH_BROKEN));
        assertEquals(b.parts[far].breachRank,
                ofType(GameWorld.EventType.BOSS_BREACH_BROKEN).get(0).code);
        assertEquals("线头仍然停在最靠前那一层", nextBefore, world.breachNext);
        // 接着按顺序拆，连段从 1 重新数起，之前的分一分没扣
        events.clear();
        int second = nextTargetIndex();
        int after = killPart(second);
        assertEquals(partBaseScore(b.parts[second], level)
                + GameWorld.breachStepScore(level, 1), after);
        assertEquals(1, world.breachStreak);
        assertEquals("插队那件不算顺线，这一件才算", 1, world.breachParts);
        assertEquals("本局最长的那条线记的是 1", 1, world.breachBest);
    }

    @Test
    public void aCleanRoutePaysTheGrandBonusAndABrokenOneDoesNot() {
        triggerBoss(FULL_TIER);
        while (nextTargetIndex() >= 0) killPart(nextTargetIndex());
        assertEquals(1, countEvents(GameWorld.EventType.BOSS_BREACH_PERFECT));
        assertEquals(1, world.breachPerfects);
        assertEquals(GameWorld.breachGrandScoreFor(world.boss.level), world.breachGrandScore);
        assertTrue(world.breachFlash > GameWorld.BREACH_BANNER_TIME);
        assertEquals(COUNT_BY_TIER[FULL_TIER],
                ofType(GameWorld.EventType.BOSS_BREACH_PERFECT).get(0).code);

        // 第二艘：先插一件再说，整条线就不可能"拆穿"了
        triggerBoss(FULL_TIER);
        assertEquals("换船之后连段从头数", 0, world.breachStreak);
        assertEquals("换船之后舰装账也重开", COUNT_BY_TIER[FULL_TIER], world.breachRigParts);
        assertEquals("顺线件数是本局账，不跟着船清", COUNT_BY_TIER[FULL_TIER], world.breachParts);
        killPart(lastTargetIndex());
        while (nextTargetIndex() >= 0) killPart(nextTargetIndex());
        assertEquals("插过队就不发拆穿奖", 1, world.breachPerfects);
        assertEquals(0, world.breachGrandScore);
        assertEquals("两艘船加起来只插过一次队",
                1, countEvents(GameWorld.EventType.BOSS_BREACH_BROKEN));
    }

    @Test
    public void theRouteDoesNotTouchTheOldPartMath() {
        Boss b = triggerBoss(FULL_TIER);
        float hpBefore = b.hp;
        int scoreBefore = world.score;
        while (nextTargetIndex() >= 0) killPart(nextTargetIndex());
        assertTrue("拆空舰装照常解除武装", b.allPartsDown);
        assertTrue("顺线拆件不该顺手打掉核心血量", b.hp > hpBefore - 40f);
        int expected = 300;                                  // 解除武装那笔老账
        for (int i = 0; i < b.partCount; i++) {
            expected += partBaseScore(b.parts[i], b.level);
        }
        for (int k = 1; k <= b.partCount; k++) {
            expected += GameWorld.breachStepScore(b.level, k);
        }
        expected += GameWorld.breachGrandScoreFor(b.level);
        assertEquals("整艘船的部件账 + 路线账要能一笔不差地加起来",
                expected, world.score - scoreBefore);
        assertEquals(1, countEvents(GameWorld.EventType.BOSS_DISARMED));
    }

    @Test
    public void prismKillsLeaveTheRouteAlone() {
        Boss b = triggerBoss(FULL_TIER);
        b.tier = 3;
        int made = b.summonPrisms();
        assertTrue(made > 0);
        int prismIndex = b.partCount - 1;
        assertEquals(-1, b.parts[prismIndex].breachRank);
        int streakBefore = world.breachStreak;
        events.clear();
        killPart(prismIndex);
        assertEquals(0, countEvents(GameWorld.EventType.BOSS_BREACH_STEP));
        assertEquals(0, countEvents(GameWorld.EventType.BOSS_BREACH_BROKEN));
        assertEquals(streakBefore, world.breachStreak);
        assertEquals("棱镜不进舰装这本账", COUNT_BY_TIER[FULL_TIER], world.breachRigParts);
        assertTrue("棱镜确实挂在舰装之后", b.partCount > world.breachRigParts);
        assertEquals(0, world.breachBroken);
    }

    @Test
    public void aShipWithNoRouteStillFightsNormally() {
        // 舰级 0 只有两件，路线只有两层——顺线拆完照样给拆穿奖，但不会有连段倍率
        Boss b = triggerBoss(0);
        assertEquals(2, b.partCount);
        assertTrue("舰级 0 的舰装最多两层", world.breachLevels >= 1 && world.breachLevels <= 2);
        killPart(nextTargetIndex());
        assertEquals(1, world.breachStreak);
        assertEquals(1, GameWorld.breachMultFor(world.breachStreak));
        killPart(nextTargetIndex());
        assertEquals(1, world.breachPerfects);
        assertEquals(2, world.breachBest);
    }

    @Test
    public void restartClearsTheWholeLedger() {
        triggerBoss(FULL_TIER);
        while (nextTargetIndex() >= 0 && world.breachStreak < 3) killPart(nextTargetIndex());
        assertTrue(world.breachParts > 0);
        world.restart(GameMode.WAVES);
        assertEquals(0, world.breachLevels);
        assertEquals(0, world.breachNext);
        assertEquals(0, world.breachRigParts);
        assertEquals(0, world.breachRemaining);
        assertEquals(0, world.breachStreak);
        assertEquals(0, world.breachBest);
        assertEquals(0, world.breachParts);
        assertEquals(0, world.breachBroken);
        assertEquals(0, world.breachPerfects);
        assertEquals(0, world.breachLastScore);
        assertEquals(0, world.breachGrandScore);
        assertEquals(0f, world.breachFlash, 0.0001f);
        assertFalse(world.breachLastWasStep);
    }

    @Test
    public void anEmptyFieldHasNoRouteToFollow() {
        assertEquals(0, world.breachRigParts);
        assertEquals(0, world.breachLevels);
        // 还没入场时，路线账本就该是空的——渲染层靠这两个字段决定要不要写那一行
        step(60);
        assertEquals(0, world.breachRigParts);
    }

    private static int maxRank(int[] r) {
        int best = -1;
        for (int v : r) best = Math.max(best, v);
        return best;
    }
}
