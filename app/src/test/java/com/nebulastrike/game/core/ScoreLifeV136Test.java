package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.36 迭代 26：分数奖励生命。
 *
 * <p>阈值按 5 000 × 档号² 增长（5 000 / 20 000 / 45 000 …），
 * 跨档就发一条命；命数满时折成炸弹，炸弹也满再折分。
 * 测试钉住阈值曲线、多档连发、折价顺序、横幅与重开归零。
 */
public class ScoreLifeV136Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261025L);
        world.spawningEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.state = GameWorld.State.PLAYING;
        world.ship.x = 450f;
        world.ship.y = 1460f;
        world.pollEvents();
        events.clear();
    }

    private void grant(int points) {
        world.addScore(points, GameWorld.SCORE_OTHER);
        events.addAll(world.pollEvents());
    }

    private int extraLifeEvents() {
        int n = 0;
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.EXTRA_LIFE) n++;
        }
        return n;
    }

    @Test
    public void firstLifeLandsAtFiveThousand() {
        world.lives = 1;
        grant(GameWorld.LIFE_SCORE_UNIT - 1);
        assertEquals("差一分不该发命", 1, world.lives);
        assertEquals(0, world.scoreLives);
        assertEquals(GameWorld.LIFE_SCORE_UNIT, world.nextLifeScore);

        grant(1);
        assertEquals("跨过 5 000 应当发一条命", 2, world.lives);
        assertEquals(1, world.scoreLives);
        assertEquals("下一档应涨到 20 000", 20_000, world.nextLifeScore);
        assertEquals(1, extraLifeEvents());
        assertEquals(1, events.get(events.size() - 1).code);
    }

    @Test
    public void thresholdsGrowWithEachAward() {
        world.lives = 1;
        world.ship.bombs = 0;
        grant(5_000);
        assertEquals(20_000, world.nextLifeScore);
        grant(15_000);
        assertEquals(45_000, world.nextLifeScore);
        grant(25_000);
        assertEquals(80_000, world.nextLifeScore);
        assertEquals(3, world.scoreLives);
        assertEquals(3, extraLifeEvents());
    }

    @Test
    public void bigScoreCanAwardSeveralTiersInOneGo() {
        world.lives = 3;
        world.ship.bombs = 0;
        grant(45_000);
        assertEquals("一口气跨三档应当结算三次", 3, world.scoreLives);
        assertEquals(4, world.lives);
        assertEquals("满命后的两档折成炸弹", 2, world.ship.bombs);
        assertEquals(80_000, world.nextLifeScore);
        assertEquals(3, extraLifeEvents());
    }

    @Test
    public void lifeCapTurnsRewardIntoBomb() {
        world.lives = GameWorld.MAX_LIVES;
        world.ship.bombs = 0;
        grant(GameWorld.LIFE_SCORE_UNIT);
        assertEquals("命数已满不该超上限", GameWorld.MAX_LIVES, world.lives);
        assertEquals(1, world.ship.bombs);
        assertEquals(2, events.get(events.size() - 1).code);
        assertTrue(world.extraLifeBanner > 0f);
        assertEquals(2, world.extraLifeBannerKind);
    }

    @Test
    public void fullEverythingTurnsRewardIntoScore() {
        world.lives = GameWorld.MAX_LIVES;
        world.ship.bombs = Ship.MAX_BOMBS;
        world.score = 0;
        grant(GameWorld.LIFE_SCORE_UNIT);
        assertEquals("命数与炸弹都满时应折算成分数",
                GameWorld.LIFE_SCORE_UNIT + GameWorld.EXTRA_LIFE_OVERFLOW_SCORE, world.score);
        assertEquals(3, events.get(events.size() - 1).code);
        assertEquals("折算分不该再触发下一档", 20_000, world.nextLifeScore);
    }

    @Test
    public void doubleScoreCountsTowardThreshold() {
        world.lives = 1;
        world.x2Timer = 5f;
        grant(GameWorld.LIFE_SCORE_UNIT / 2);
        assertEquals("双倍分数下 2 500 分应算作 5 000", 2, world.lives);
        assertEquals(1, world.scoreLives);
    }

    @Test
    public void bannerTimesOutAndProgressResetsOnRestart() {
        world.lives = 1;
        grant(GameWorld.LIFE_SCORE_UNIT);
        assertTrue(world.extraLifeBanner > 0f);
        assertEquals(GameWorld.EXTRA_LIFE_BANNER_TIME, world.extraLifeBanner, 0.0001f);

        for (int i = 0; i < 200; i++) world.update(1f / 60f);
        assertEquals("横幅应当自然淡出", 0f, world.extraLifeBanner, 0.0001f);

        world.restart(GameMode.WAVES);
        assertEquals("重开应当回到第一档", 0, world.scoreLives);
        assertEquals(GameWorld.LIFE_SCORE_UNIT, world.nextLifeScore);
        assertEquals(0f, world.extraLifeBanner, 0.0001f);
    }
}
