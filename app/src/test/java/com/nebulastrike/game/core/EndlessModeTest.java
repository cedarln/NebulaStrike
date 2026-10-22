package com.nebulastrike.game.core;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Iteration-11: ENDLESS mode — steeper uncapped difficulty ramp, faster boss
 * cadence, and a separate persistent leaderboard.
 */
public class EndlessModeTest {
    private GameWorld world;
    private UiStateTest.FakeStore store;

    @Before
    public void setUp() {
        store = new UiStateTest.FakeStore();
        world = new GameWorld(11L, true);
        world.attachStore(store);
    }

    private void runFor(float seconds) {
        float t = 0f;
        while (t < seconds) { world.update(1f / 60f); t += 1f / 60f; }
    }

    @Test
    public void menuButtonsLaunchEachMode() {
        world.state = GameWorld.State.MENU;
        world.handleUiAction(Ui.Action.START);
        assertEquals(GameMode.WAVES, world.mode);
        assertEquals(GameWorld.State.PLAYING, world.state);

        world.state = GameWorld.State.MENU;
        world.handleUiAction(Ui.Action.START_ENDLESS);
        assertEquals(GameMode.ENDLESS, world.mode);
        assertEquals(GameWorld.State.PLAYING, world.state);
    }

    @Test
    public void endlessHitsFullPressureSoonerThanCampaign() {
        // at t=90s endless is at full ramp; campaign is only ~60% there
        world.handleUiAction(Ui.Action.START_ENDLESS);
        world.spawningEnabled = false;
        runFor(90f);
        float endlessSpawn = world.spawnInterval;
        float endlessSpeed = world.enemySpeedScale;

        world.handleUiAction(Ui.Action.START); // same clock, campaign
        world.spawningEnabled = false;
        runFor(90f);
        assertTrue("endless spawns faster than campaign at 90s",
                endlessSpawn < world.spawnInterval);
        assertTrue("endless enemies move faster than campaign at 90s",
                endlessSpeed > world.enemySpeedScale);
    }

    @Test
    public void endlessCreepHasNoSpeedCap() {
        world.handleUiAction(Ui.Action.START_ENDLESS);
        world.spawningEnabled = false;
        runFor(330f); // 90s ramp + 240s creep
        assertTrue("endless creep should push speed past x2.0",
                world.enemySpeedScale > 2.0f);
        assertTrue("spawn floor must hold at 0.16s",
                world.spawnInterval >= 0.159f);
    }

    @Test
    public void endlessBossesComeEveryThirdWave() {
        world.handleUiAction(Ui.Action.START_ENDLESS);
        assertEquals(3, world.nextBossWave);   // first endless boss at wave 3
        world.nextBossWave = 1;               // force a trigger on the next tick
        world.update(1f / 60f);
        assertEquals(4, world.nextBossWave);   // endless steps by 3

        world.handleUiAction(Ui.Action.START); // campaign
        assertEquals(4, world.nextBossWave);
        world.nextBossWave = 1;
        world.update(1f / 60f);
        assertEquals(5, world.nextBossWave);   // campaign steps by 4
    }

    /**
     * v1.17 修复：高分下 wave 会被分数快速推高，只按 wave 节拍会让 Boss 一场接一场，
     * 后期天空永远停在 Boss 战里（普通刷新、突袭波和新舰型都拿不到位置）。
     * 现在打完一场必须留出 BOSS_MIN_GAP 的喘息。
     */
    @Test
    public void endlessBossesLeaveABreatherBetweenFights() {
        world.handleUiAction(Ui.Action.START_ENDLESS);
        world.spawningEnabled = false;
        world.lives = 9999;                     // 喘息期里 Boss 残弹仍可能蹭到飞船
        world.nextBossWave = 1;
        runFor(0.2f);
        assertTrue("应已进入 Boss 战", world.bossActive);

        world.boss.hp = 0f;                      // 一击毙命，隔离"战斗时长"这个变量
        runFor(0.2f);
        assertFalse(world.boss.active);
        assertFalse(world.bossActive);
        assertTrue("打完就该上喘息计时", world.bossGapTimer > 0f);

        world.wave += 50;                        // 分数额度全部预支，模拟高分玩家
        runFor(GameWorld.BOSS_MIN_GAP - 2f);
        assertFalse("喘息期内不该再来一场 Boss", world.bossActive);

        runFor(3f);
        assertTrue("喘息过后 Boss 应回归", world.bossActive);
    }

    @Test
    public void endlessRunScoresOnlyTheEndlessBoard() {
        world.handleUiAction(Ui.Action.START_ENDLESS);
        world.spawningEnabled = false;
        world.score = 250;
        world.lives = 1;
        // wait out spawn protection, then take the hit
        float t = 0f;
        while (world.ship.invulnerable && t < 3f) { world.update(1f / 60f); t += 1f / 60f; }
        world.enemyBullets[0].init(world.ship.x, world.ship.y, 0f, 0f);
        world.update(1f / 60f);
        assertEquals(GameWorld.State.GAME_OVER, world.state);
        assertEquals(0, world.scoreRank);
        assertArrayEquals(new int[]{250, 0, 0, 0, 0}, world.leaderboardEndless);
        assertArrayEquals(new int[]{0, 0, 0, 0, 0}, world.leaderboard);
        assertEquals(1, store.elbWrites);
        assertEquals(0, store.lbWrites);
    }

    @Test
    public void boardsAreIndependent() {
        world.insertScore(400, GameMode.ENDLESS);
        world.insertScore(100, GameMode.WAVES);
        assertArrayEquals(new int[]{400, 0, 0, 0, 0}, world.board(GameMode.ENDLESS));
        assertArrayEquals(new int[]{100, 0, 0, 0, 0}, world.board(GameMode.WAVES));
        // a mid campaign score ranks on the waves board without touching endless
        assertEquals(0, world.insertScore(150, GameMode.WAVES));
        assertArrayEquals(new int[]{150, 100, 0, 0, 0}, world.board(GameMode.WAVES));
        assertArrayEquals(new int[]{400, 0, 0, 0, 0}, world.board(GameMode.ENDLESS));
        // and a weak score only fills an empty waves slot, never the endless board
        assertEquals(2, world.insertScore(50, GameMode.WAVES));
        assertArrayEquals(new int[]{150, 100, 50, 0, 0}, world.board(GameMode.WAVES));
        assertArrayEquals(new int[]{400, 0, 0, 0, 0}, world.board(GameMode.ENDLESS));
    }

    @Test
    public void endlessBoardSurvivesStoreRoundTrip() {
        world.insertScore(777, GameMode.ENDLESS);
        GameWorld fresh = new GameWorld(12L, true);
        fresh.attachStore(store);
        assertArrayEquals(new int[]{777, 0, 0, 0, 0}, fresh.leaderboardEndless);
        assertArrayEquals(new int[]{0, 0, 0, 0, 0}, fresh.leaderboard);
    }

    @Test
    public void restartKeepsModeResumeSwitchesIt() {
        world.handleUiAction(Ui.Action.START_ENDLESS);
        world.score = 999;
        world.handleUiAction(Ui.Action.RESTART); // pause-menu restart
        assertEquals(GameMode.ENDLESS, world.mode);
        assertEquals(0, world.score);

        world.state = GameWorld.State.MENU;
        world.handleUiAction(Ui.Action.START); // back to campaign
        assertEquals(GameMode.WAVES, world.mode);
    }

    @Test
    public void endlessMenuUiHitsBothNewButtons() {
        world.state = GameWorld.State.MENU;
        assertNotNull(Ui.hit(GameWorld.State.MENU, false, false,
                Ui.START_ENDLESS.cx(), Ui.START_ENDLESS.cy()));
        assertEquals(Ui.Action.START_ENDLESS, Ui.hit(GameWorld.State.MENU, false, false,
                Ui.START_ENDLESS.cx(), Ui.START_ENDLESS.cy()));
        assertEquals(Ui.Action.START, Ui.hit(GameWorld.State.MENU, false, false,
                Ui.START.cx(), Ui.START.cy()));
        // buttons must not overlap
        assertFalse(Ui.START.contains(Ui.START_ENDLESS.cx(), Ui.START_ENDLESS.cy()));
        assertFalse(Ui.START_ENDLESS.contains(Ui.START.cx(), Ui.START.cy()));
    }
}
