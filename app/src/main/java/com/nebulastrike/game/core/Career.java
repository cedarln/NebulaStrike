package com.nebulastrike.game.core;

/**
 * 生涯数据面板的纯计算层（v1.30）：把账本里的原始数字折算成面板上一格文字。
 *
 * <p>只做格式化和四则运算，不碰任何状态，面板与测试共用同一套口径，
 * 避免"渲染里写一套、测试里猜一套"。
 */
public final class Career {
    private Career() {
    }

    /** 大数字缩写：一万以下原样，再往上用"万 / 亿"，保留一位小数。 */
    public static String count(int n) {
        if (n <= 0) return "0";
        if (n < 10_000) return String.valueOf(n);
        if (n < 100_000_000) return oneDecimal(n / 10_000f) + "万";
        return oneDecimal(n / 100_000_000f) + "亿";
    }

    /** 秒数口语化：1h / 2h 5m / 59m 59s / 42s。 */
    public static String duration(int seconds) {
        if (seconds <= 0) return "0s";
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return m > 0 ? h + "h " + m + "m" : h + "h";
        if (m > 0) return s > 0 ? m + "m " + s + "s" : m + "m";
        return s + "s";
    }

    /** 生涯平均每分钟得分（总分为 0 或没飞过时返回 0）。 */
    public static int scorePerMinute(int totalScore, int totalSeconds) {
        if (totalScore <= 0 || totalSeconds <= 0) return 0;
        return (int) Math.round(totalScore * 60.0 / totalSeconds);
    }

    /** 场均数值（四舍五入）；没出勤过返回 0。 */
    public static int perRun(int total, int runs) {
        if (total <= 0 || runs <= 0) return 0;
        return Math.round(total / (float) runs);
    }

    private static String oneDecimal(float v) {
        float r = Math.round(v * 10f) / 10f;
        return Math.abs(r - Math.round(r)) < 0.001f
                ? String.valueOf(Math.round(r))
                : String.valueOf(r);
    }
}
