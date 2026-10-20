package com.nebulastrike.game.core;

/**
 * 版本比较与发布 JSON 解析的纯逻辑，无任何 Android 依赖，可在 JVM 单测。
 * GitHub Releases API 返回形如：
 * {"tag_name":"v1.2.0","assets":[{"name":"NebulaStrike-v1.2.0.apk",
 *  "browser_download_url":"https://github.com/.../NebulaStrike-v1.2.0.apk"}]}
 */
public final class UpdateCheck {
    private UpdateCheck() {}

    /**
     * 语义化版本比较，忽略前缀 v 与空白。返回 a-b 的符号级差异：
     * 正数表示 a 更新，负数表示 a 更旧，0 表示相同。解析失败的部分按 0 处理。
     */
    public static int compareVersions(String a, String b) {
        int[] pa = parse(a), pb = parse(b);
        for (int i = 0; i < 3; i++) {
            if (pa[i] != pb[i]) return pa[i] - pb[i];
        }
        return 0;
    }

    private static int[] parse(String v) {
        int[] out = new int[]{0, 0, 0};
        if (v == null) return out;
        v = v.trim().toLowerCase().replace("v", "");
        String[] parts = v.split("\\.");
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    /**
     * 从 releases/latest 的 JSON 中提取信息。
     * @return 长度 2 的数组 {tag_name, apk 下载地址}；tag 或 apk 缺失时对应项为 null，
     *         JSON 无 tag_name 时返回 null
     */
    public static String[] parseRelease(String json) {
        if (json == null) return null;
        String tag = extractString(json, "\"tag_name\"");
        if (tag == null) return null;
        String url = extractApkUrl(json);
        return new String[]{tag, url};
    }

    /** 提取 "key":"value" 的 value，找不到返回 null。 */
    private static String extractString(String json, String key) {
        int k = json.indexOf(key);
        if (k < 0) return null;
        int colon = json.indexOf(':', k + key.length());
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    /** 找第一个以 .apk 结尾的 browser_download_url。 */
    private static String extractApkUrl(String json) {
        int from = 0;
        while (true) {
            int k = json.indexOf("\"browser_download_url\"", from);
            if (k < 0) return null;
            String url = extractString(json.substring(k), "\"browser_download_url\"");
            if (url != null && url.toLowerCase().endsWith(".apk")) return url;
            from = k + 1;
        }
    }
}
