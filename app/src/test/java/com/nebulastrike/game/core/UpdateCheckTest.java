package com.nebulastrike.game.core;

import org.junit.Test;

import static org.junit.Assert.*;

/** 迭代 12：应用内升级检测——版本比较、发布 JSON 解析、升级状态机。 */
public class UpdateCheckTest {

    // ---- compareVersions ----

    @Test
    public void comparesSemanticVersions() {
        assertTrue(UpdateCheck.compareVersions("1.2.0", "1.1.0") > 0);
        assertTrue(UpdateCheck.compareVersions("1.1.0", "1.2.0") < 0);
        assertEquals(0, UpdateCheck.compareVersions("1.1.0", "1.1.0"));
    }

    @Test
    public void comparesEachNumericComponent() {
        assertTrue(UpdateCheck.compareVersions("1.10.0", "1.9.0") > 0); // 字符串比较会错
        assertTrue(UpdateCheck.compareVersions("2.0.0", "1.99.99") > 0);
        assertTrue(UpdateCheck.compareVersions("1.1.1", "1.1.0") > 0);
        assertEquals(0, UpdateCheck.compareVersions("1.1", "1.1.0"));  // 缺位按 0
    }

    @Test
    public void toleratesVAndWhitespaceAndGarbage() {
        assertEquals(0, UpdateCheck.compareVersions("v1.1.0", "1.1.0"));
        assertTrue(UpdateCheck.compareVersions(" v1.2.0 ", "1.1.9") > 0);
        assertEquals(0, UpdateCheck.compareVersions("abc", "v"));
        assertTrue(UpdateCheck.compareVersions("1.2.0", null) > 0);
        assertTrue(UpdateCheck.compareVersions(null, "0.0.1") < 0);
    }

    // ---- parseRelease ----

    @Test
    public void parsesTagAndApkUrl() {
        String json = "{\"url\":\"https://api.github.com/repos/x/releases/1\","
                + "\"tag_name\":\"v1.2.0\","
                + "\"target_commitish\":\"main\","
                + "\"assets\":[{\"name\":\"NebulaStrike-v1.2.0.apk\","
                + "\"browser_download_url\":\"https://github.com/x/NebulaStrike/releases/download/v1.2.0/NebulaStrike-v1.2.0.apk\"}]}";
        String[] rel = UpdateCheck.parseRelease(json);
        assertNotNull(rel);
        assertEquals("v1.2.0", rel[0]);
        assertTrue(rel[1].endsWith(".apk"));
        assertTrue(rel[1].startsWith("https://github.com"));
    }

    @Test
    public void skipsNonApkAssets() {
        String json = "{\"tag_name\":\"v2.0.0\",\"assets\":["
                + "{\"name\":\"notes.txt\",\"browser_download_url\":\"https://x/notes.txt\"},"
                + "{\"name\":\"app.apk\",\"browser_download_url\":\"https://x/app.apk\"}]}";
        String[] rel = UpdateCheck.parseRelease(json);
        assertEquals("https://x/app.apk", rel[1]);
    }

    @Test
    public void missingTagOrApkYieldsNulls() {
        assertNull(UpdateCheck.parseRelease("{\"message\":\"Not Found\"}"));
        assertNull(UpdateCheck.parseRelease((String) null));
        String[] noApk = UpdateCheck.parseRelease("{\"tag_name\":\"v1.0.0\",\"assets\":[]}");
        assertEquals("v1.0.0", noApk[0]);
        assertNull(noApk[1]);
    }

    // ---- GameWorld 升级状态机 ----

    private GameWorld world;

    private GameWorld world() {
        GameWorld w = new GameWorld(13L, true);
        w.currentVersion = "1.1.0";
        w.state = GameWorld.State.MENU;
        return w;
    }

    @Test
    public void checkClickTransitionsIdleToCheckingThenResult() {
        world = world();
        world.handleUiAction(Ui.Action.CHECK_UPDATE);
        assertEquals(GameWorld.UpdateState.CHECKING, world.updateState);

        world.onCheckResult(true, true, null, null, null);
        assertEquals(GameWorld.UpdateState.UP_TO_DATE, world.updateState);

        // 提示 4 秒后自动回到 IDLE 可再次检查
        for (int i = 0; i < 250; i++) world.update(1f / 60f);
        assertEquals(GameWorld.UpdateState.IDLE, world.updateState);
    }

    @Test
    public void availableThenDownloadThenReady() {
        world = world();
        world.handleUiAction(Ui.Action.CHECK_UPDATE);
        world.onCheckResult(true, false, "v1.2.0", "https://x/app.apk", null);
        assertEquals(GameWorld.UpdateState.AVAILABLE, world.updateState);
        assertEquals("v1.2.0", world.updateVersion);
        assertEquals("https://x/app.apk", world.updateUrl);

        // 点击 AVAILABLE -> 开始下载；进度单调不超 99；完成后 READY
        world.handleUiAction(Ui.Action.CHECK_UPDATE);
        assertEquals(GameWorld.UpdateState.DOWNLOADING, world.updateState);
        world.onDownloadProgress(10);
        world.onDownloadProgress(5);      // 回退的进度被忽略
        assertEquals(10, world.updateProgress);
        world.onDownloadProgress(100);    // 100 留给 READY
        assertEquals(99, world.updateProgress);
        world.onDownloadReady();
        assertEquals(GameWorld.UpdateState.READY, world.updateState);
    }

    @Test
    public void readyTimesBackToAvailableForRetry() {
        world = world();
        world.updateState = GameWorld.UpdateState.READY;
        for (int i = 0; i < 380; i++) world.update(1f / 60f);
        assertEquals(GameWorld.UpdateState.AVAILABLE, world.updateState);
    }

    @Test
    public void errorPathAndRetry() {
        world = world();
        world.handleUiAction(Ui.Action.CHECK_UPDATE);
        world.onCheckResult(false, false, null, null, "网络不可用或检查失败");
        assertEquals(GameWorld.UpdateState.ERROR, world.updateState);
        assertEquals("网络不可用或检查失败", world.updateError);
        world.handleUiAction(Ui.Action.CHECK_UPDATE); // 点击重试
        assertEquals(GameWorld.UpdateState.CHECKING, world.updateState);
    }

    @Test
    public void downloadFailureReturnsToRetryableError() {
        world = world();
        world.updateState = GameWorld.UpdateState.DOWNLOADING;
        world.onDownloadError("下载失败，点击重试");
        assertEquals(GameWorld.UpdateState.ERROR, world.updateState);
        assertEquals("下载失败，点击重试", world.updateError);

        // 提示 4 秒后自动回到 IDLE，按钮恢复可点
        for (int i = 0; i < 320; i++) world.update(1f / 60f);
        assertEquals(GameWorld.UpdateState.IDLE, world.updateState);
        world.handleUiAction(Ui.Action.CHECK_UPDATE);
        assertEquals(GameWorld.UpdateState.CHECKING, world.updateState);
    }

    @Test
    public void staleCallbacksAreIgnored() {
        world = world();
        world.handleUiAction(Ui.Action.CHECK_UPDATE);
        world.onCheckResult(true, false, "v1.2.0", "https://x/app.apk", null);
        // 此时 AVAILABLE，迟到的检查回调不得改写状态
        world.onCheckResult(false, false, null, null, "过期错误");
        assertEquals(GameWorld.UpdateState.AVAILABLE, world.updateState);
        // 下载中途迟到的不相关结果也不得改写
        world.handleUiAction(Ui.Action.CHECK_UPDATE);
        world.onCheckResult(true, true, null, null, null);
        assertEquals(GameWorld.UpdateState.DOWNLOADING, world.updateState);
    }

    @Test
    public void dismissReturnsToDownloadable() {
        world = world();
        world.updateState = GameWorld.UpdateState.READY;
        world.onUpdateDismissed();
        assertEquals(GameWorld.UpdateState.AVAILABLE, world.updateState);
        // 再点一次按钮就重新下载（需要网络）
        world.handleUiAction(Ui.Action.CHECK_UPDATE);
        assertEquals(GameWorld.UpdateState.DOWNLOADING, world.updateState);
        assertTrue(world.updateNeedsNetwork());
    }

    @Test
    public void enteringGameDoesNotTouchUpdateFlow() {
        world = world();
        world.handleUiAction(Ui.Action.CHECK_UPDATE); // CHECKING
        world.handleUiAction(Ui.Action.START);        // 进战役
        assertEquals(GameWorld.State.PLAYING, world.state);
        // 进入战斗不打断检查状态；回调仍在（若已 READY/ERROR 会被忽略）
        world.onCheckResult(true, false, "v1.2.0", "https://x/app.apk", null);
        assertEquals(GameWorld.UpdateState.AVAILABLE, world.updateState);
    }
}
