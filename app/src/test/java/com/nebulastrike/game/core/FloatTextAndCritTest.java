package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.11 迭代 1：伤害/得分飘字与暴击判定。
 * 飘字走对象池 + 每帧预算，暴击只影响伤害数值与统计，两者都不允许改变既有战斗平衡。
 */
public class FloatTextAndCritTest {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261022L);
        world.spawningEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
    }

    // ---- 辅助 ----

    /** 在敌机身上放一发指定伤害的实弹，返回是否成功布弹。 */
    private boolean shootAt(Enemy target, int damage) {
        for (Bullet b : world.bullets) {
            if (b.active) continue;
            b.init(target.x, target.y, 0f, 0f, damage, false);
            return true;
        }
        return false;
    }

    /** 造一只不会死的靶机（hp 每帧回满，飘字只来自命中判定本身）。 */
    private Enemy dummy(float x, float y) {
        Enemy e = world.enemies[0];
        e.init(EnemyType.TANK, x, y, 1f);
        e.hp = 999_999f;
        e.maxHp = 999_999f;
        world.spawningEnabled = false;
        return e;
    }

    private int floatCount(int color) {
        int n = 0;
        for (FloatText f : world.floatTexts) {
            if (f.active && f.color == color) n++;
        }
        return n;
    }

    // ---- 飘字触发规则 ----

    @Test
    public void heavyHitFloatsItsDamageNumber() {
        Enemy e = dummy(450f, 600f);
        assertTrue(shootAt(e, 5));
        world.update(1f / 60f);
        assertEquals("伤害 >=3 的命中应起一条普伤飘字", 1, floatCount(GameWorld.FLOAT_DAMAGE));
    }

    @Test
    public void weakHitsStayQuietToAvoidSpam() {
        Enemy e = dummy(450f, 600f);
        assertTrue(shootAt(e, 1));   // 基础机炮单发伤害 1，不配飘字
        world.update(1f / 60f);
        assertEquals(0, world.activeFloatTexts());
    }

    @Test
    public void killFloatsTheScoreItGained() {
        Enemy e = dummy(450f, 600f);
        int before = world.score;
        e.hp = 1f;
        assertTrue(shootAt(e, 5));
        world.update(1f / 60f);
        int gained = world.score - before;
        assertTrue("击杀应当计分", gained > 0);
        boolean found = false;
        for (FloatText f : world.floatTexts) {
            if (f.active && f.color == GameWorld.FLOAT_SCORE) {
                assertEquals("得分飘字应显示本条命实际计入的分数", gained, f.value);
                assertTrue("击杀得分用大号", f.bold);
                found = true;
            }
        }
        assertTrue("击杀应有金色得分飘字", found);
    }

    @Test
    public void floatBudgetCapsNewNumbersPerFrame() {
        Enemy e = dummy(450f, 600f);
        for (int i = 0; i < 20; i++) shootAt(e, 5);
        world.update(1f / 60f);
        assertTrue("同帧最多放行 " + GameWorld.FLOAT_BUDGET_PER_FRAME + " 条",
                world.activeFloatTexts() <= GameWorld.FLOAT_BUDGET_PER_FRAME);
    }

    @Test
    public void floatsExpireAndPoolIsReused() {
        Enemy e = dummy(450f, 600f);
        assertTrue(shootAt(e, 5));
        world.update(1f / 60f);
        assertTrue(world.activeFloatTexts() > 0);
        stepSeconds(1.5f);
        assertEquals("飘完之后不应有残留", 0, world.activeFloatTexts());
        assertTrue(shootAt(e, 5));
        world.update(1f / 60f);
        assertEquals("槽位应能被复用", 1, world.activeFloatTexts());
    }

    @Test
    public void reducedFxTurnsOffFloatingNumbers() {
        world.settings.reducedFx = true;
        Enemy e = dummy(450f, 600f);
        assertTrue(shootAt(e, 5));
        world.update(1f / 60f);
        assertEquals("低特效下不画飘字，也不占池", 0, world.activeFloatTexts());
    }

    @Test
    public void restartClearsFloatsAndCritStats() {
        Enemy e = dummy(450f, 600f);
        assertTrue(shootAt(e, 5));
        world.update(1f / 60f);
        world.damageOrCritForTest(9);
        world.restart(GameMode.WAVES);
        assertEquals(0, world.activeFloatTexts());
        assertEquals(0, world.crits);
    }

    // ---- 暴击判定 ----

    @Test
    public void critOnlyAppliesToMeaningfulDamage() {
        for (int i = 0; i < 200; i++) {
            assertEquals("单发伤害 1 不参与暴击", 1, world.damageOrCritForTest(1));
        }
        assertEquals(0, world.crits);
    }

    @Test
    public void critRateIsRoughlyEightPercent() {
        int crits = 0;
        for (int i = 0; i < 2000; i++) {
            if (world.damageOrCritForTest(3) != 3) crits++;
        }
        assertTrue("暴击率应接近 " + (int) (GameWorld.CRIT_CHANCE * 100) + "%，实测 " + crits / 20f
                + "%", crits > 100 && crits < 300);
        assertEquals("统计数应与判定次数一致", crits, world.crits);
    }

    @Test
    public void overdriveCritsMoreOften() {
        int normal = 0;
        for (int i = 0; i < 600; i++) if (world.damageOrCritForTest(3) != 3) normal++;
        world.ship.overdriveTimer = 3f;
        int boosted = 0;
        for (int i = 0; i < 600; i++) if (world.damageOrCritForTest(3) != 3) boosted++;
        assertTrue("过载期间暴击应更多：" + normal + " vs " + boosted, boosted > normal);
    }

    @Test
    public void critDamageIsAmplified() {
        int crit = 3;
        for (int i = 0; i < 400 && crit == 3; i++) crit = world.damageOrCritForTest(3);
        assertNotEquals(3, crit);
        assertEquals(Math.round(3 * GameWorld.CRIT_MULT), crit);
    }

    @Test
    public void critOnEnemyEmitsEventForAudioLayer() {
        Enemy e = dummy(450f, 600f);
        boolean emitted = false;
        for (int i = 0; i < 400 && !emitted; i++) {
            world.pollEvents();
            assertTrue(shootAt(e, 3));
            world.update(1f / 60f);
            for (GameWorld.GameEvent ev : world.pollEvents()) {
                if (ev.type == GameWorld.EventType.CRIT) emitted = true;
            }
            e.hp = 999_999f;
        }
        assertTrue("暴击应发出 CRIT 事件供音效层点缀", emitted);
    }

    // ---- FloatText 自身 ----

    @Test
    public void floatTextFadesOutThenRetires() {
        FloatText f = new FloatText();
        f.init(100f, 500f, 12, GameWorld.FLOAT_DAMAGE, false, 0f);
        assertEquals(1f, f.alpha(), 0.001f);
        float startY = f.y;
        for (int i = 0; i < 30; i++) f.update(1f / 60f);
        assertTrue("应向上浮起", f.y < startY);
        assertTrue("末段应淡出", f.alpha() < 1f);
        for (int i = 0; i < 30; i++) f.update(1f / 60f);
        assertFalse(f.active);
    }

    @Test
    public void boldNumbersStayLonger() {
        FloatText small = new FloatText();
        small.init(0f, 0f, 1, GameWorld.FLOAT_DAMAGE, false, 0f);
        FloatText big = new FloatText();
        big.init(0f, 0f, 1, GameWorld.FLOAT_SCORE, true, 0f);
        assertTrue(FloatText.LIFE_BOLD > FloatText.LIFE);
        assertTrue(big.maxLife > small.maxLife);
    }

    private void stepSeconds(float seconds) {
        for (float t = 0f; t < seconds; t += 1f / 60f) world.update(1f / 60f);
    }
}
