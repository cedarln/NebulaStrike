package com.nebulastrike.game;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.nebulastrike.game.core.GameWorld;
import com.nebulastrike.game.core.UpdateCheck;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 应用内升级：检查 GitHub Releases -> 应用内直接下载 APK 到私有目录 ->
 * 通过自定义 ContentProvider 以 content:// URI 唤起系统安装器（绝不跳转浏览器）。
 * 全部网络/磁盘操作在后台线程，通过传入的 Runnable 包装回 UI 线程。
 */
public final class UpdateManager {
    /** 检查更新的发布通道；私有仓库匿名 404，公开仓库正常。 */
    static final String LATEST_URL =
            "https://api.github.com/repos/cedarln/NebulaStrike/releases/latest";
    private static final int TIMEOUT_MS = 15000;

    private UpdateManager() {}

    /** 当前 versionName，如 "1.1.0"。 */
    public static String currentVersion(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /**
     * 后台线程检查最新版本，结束后在 UI 线程回调 world.onCheckResult。
     * 比较语义版本：远端 <= 当前 -> 已是最新；远端更新且带 .apk 资产 -> 可下载。
     */
    public static void checkAsync(final Context ctx, final GameWorld world,
                                  final Runnable uiCallback) {
        new Thread(() -> {
            boolean ok = false, upToDate = false;
            String tag = null, url = null, err = null;
            try {
                String json = httpGet(LATEST_URL);
                String[] rel = UpdateCheck.parseRelease(json);
                if (rel == null) {
                    err = "发布通道暂不可用";
                } else if (rel[1] == null) {
                    err = "发布页没有可用的安装包";
                } else {
                    tag = rel[0];
                    url = rel[1];
                    int cmp = UpdateCheck.compareVersions(tag, world.currentVersion);
                    upToDate = cmp <= 0;
                    ok = true;
                }
            } catch (Exception e) {
                err = "网络不可用或检查失败";
            }
            final boolean fOk = ok, fUp = upToDate;
            final String fTag = tag, fUrl = url, fErr = err;
            ((Activity) ctx).runOnUiThread(() -> {
                world.onCheckResult(fOk, fUp, fTag, fUrl, fErr);
                uiCallback.run();
            });
        }, "update-check").start();
    }

    /**
     * 后台线程下载 world.updateUrl 到私有 update/ 目录，结束后在 UI 线程回调
     * world.onDownloadReady；下载过程中每收到数据就回报进度。
     */
    public static void downloadAsync(final Context ctx, final GameWorld world,
                                     final Runnable uiCallback) {
        new Thread(() -> {
            try {
                File dir = new File(ctx.getFilesDir(), "update");
                dir.mkdirs();
                File out = new File(dir, "NebulaStrike-" + world.updateVersion + ".apk");
                HttpURLConnection conn = (HttpURLConnection) new URL(world.updateUrl)
                        .openConnection();
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setInstanceFollowRedirects(true);
                long total = conn.getContentLengthLong();
                try (InputStream in = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[16384];
                    long read = 0;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                        read += n;
                        final int pct = total > 0 ? (int) (read * 100 / total) : 0;
                        if (pct > world.updateProgress && pct % 2 == 0) {
                            ((Activity) ctx).runOnUiThread(() -> {
                                world.onDownloadProgress(pct);
                                uiCallback.run();
                            });
                        }
                    }
                }
                ((Activity) ctx).runOnUiThread(() -> {
                    world.onDownloadReady();
                    uiCallback.run();
                });
            } catch (Exception e) {
                ((Activity) ctx).runOnUiThread(() -> {
                    world.onCheckResult(false, false, null, null, "下载失败，点击重试");
                    uiCallback.run();
                });
            }
        }, "update-download").start();
    }

    /**
     * 以 content:// URI 唤起系统安装器（非浏览器）。若用户在系统页面取消安装，
     * 调用方应随后 world.onUpdateDismissed()。
     */
    public static void install(Context ctx, GameWorld world) {
        File dir = new File(ctx.getFilesDir(), "update");
        File apk = new File(dir, "NebulaStrike-" + world.updateVersion + ".apk");
        if (!apk.exists()) {
            world.onUpdateDismissed();
            return;
        }
        Uri uri = Uri.parse("content://" + ctx.getPackageName() + ".updatefiles/update/"
                + apk.getName());
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (Exception e) {
            // 设备不允许侧载或无安装器：回到可下载状态，文件留给启动清理
            world.onUpdateDismissed();
        }
    }

    /**
     * 清理下载残留。升级安装成功会杀死本进程，重启后调用本方法即完成
     * “安装完成后删除 APK”；用户中途取消安装同样在下次启动清理。
     */
    public static void cleanup(Context ctx) {
        deleteTree(new File(ctx.getFilesDir(), "update"));
    }

    static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "NebulaStrike-App");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        int code = conn.getResponseCode();
        if (code == 404) throw new IllegalStateException("404");
        InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192];
        int n;
        while (in != null && (n = in.read(buf)) > 0) {
            sb.append(new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8));
        }
        if (in != null) in.close();
        if (code >= 400) throw new IllegalStateException("HTTP " + code);
        return sb.toString();
    }
}
