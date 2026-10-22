package com.nebulastrike.game.core;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * 每日挑战（v1.19 迭代 9）：把"日期"变成种子，全世界同一台手机、同一颗种子里，
 * 每天只有一套规则。
 *
 * <p>三件事决定今天怎么玩，全部从 {@code yyyyMMdd} 的日期键推导，纯函数、可复现：
 *
 * <ul>
 *   <li><b>今日规则</b>：直接把 v1.14 的异变波常驻化（铁雨 / 虫群 / 钢铁洪流……），
 *       整天都是这一段"规则变狠但更值钱"的时空；它与随机异变波<b>取较强的一档</b>而非相乘，
 *       避免"两倍铁雨"把对局变成猜谜。</li>
 *   <li><b>今日命数</b>：1~3 条。命越少，全局得分加成越高（×1.5 / ×1.15 / ×1.0），
 *       风险与回报同时兑现，不出现"今天天生打不动"的日子。</li>
 *   <li><b>对局种子</b>：由日期键哈希而来。同一天的每一次重开都是同一条时间线，
 *       刷分靠重复练习而不是运气。</li>
 * </ul>
 *
 * <p>日常挑战强制<b>标准难度并屏蔽 LAB 金手指</b>：全世界比的是同一套系数，
 * 否则"今天的最佳"就没有可比性。成就与图鉴照常计入（日常也是真实战斗）。
 */
public final class DailyChallenge {

    private DailyChallenge() {
    }

    /** 历史槽位数：日历条保留最近 14 天。 */
    public static final int HISTORY_SLOTS = 14;

    /** 可选命数：1 / 2 / 3。 */
    public static final int LIVES_MIN = 1;
    public static final int LIVES_MAX = 3;
    public static final int LIVES_COUNT = LIVES_MAX - LIVES_MIN + 1;

    /** 可选的常驻异变种类数（不含 {@link Mutator#NONE}）。 */
    public static final int RULE_COUNT = Mutator.COUNT;

    /** 日期键 → 对局种子（相邻两天也要差得够远，不能像连号）。 */
    public static long seedFor(int dateKey) {
        long x = (dateKey & 0xFFFFFFFFL) * 0x9E3779B97F4A7C15L;
        x ^= x >>> 29;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 32;
        return x == 0L ? 1L : x;
    }

    /** 日期键 → 今日规则（常驻异变，永远不是 NONE）。 */
    public static Mutator ruleFor(int dateKey) {
        int idx = 1 + mod(mix(dateKey, RULE_SALT), RULE_COUNT);
        Mutator[] all = Mutator.values();
        if (idx >= all.length) idx = 1;
        return all[idx];
    }

    /** 日期键 → 今日命数（1~3）。 */
    public static int livesFor(int dateKey) {
        return LIVES_MIN + mod(mix(dateKey, LIVES_SALT), LIVES_COUNT);
    }

    /** 整数散列（LCG 风格，溢出在 Java 里是确定行为）：让相邻两天的摇号结果差得够远。 */
    private static int mix(int value, int salt) {
        int x = value ^ salt;
        x *= 0x45d9f3b;
        x ^= x >>> 16;
        x *= 0x45d9f3b;
        x ^= x >>> 16;
        return x & 0x7FFFFFFF;
    }

    /** 规则与命数用不同盐，避免"整周都是同一条命数"这种相关性。 */
    private static final int RULE_SALT = 0x13579BDF;
    private static final int LIVES_SALT = 0x2468ACE1;

    /** 非负取模：{@code Math.floorMod} 要 API 24，本项目最低支持 21。 */
    private static int mod(int a, int b) {
        int r = a % b;
        return r < 0 ? r + b : r;
    }

    /**
     * 命数换来的全局得分加成。少给的那一档永远有补偿：
     * 一条命的日子打到 1.5 倍分，"今天运气差"就不成立。
     */
    public static float livesScoreBonus(int lives) {
        switch (lives) {
            case 1: return 1.5f;
            case 2: return 1.15f;
            default: return 1f;
        }
    }

    /** 当前本地日期 → {@code yyyyMMdd} 形式的键。 */
    public static int dateKey(long millis) {
        return dateKey(millis, TimeZone.getDefault());
    }

    /** 指定时区的版本（测试用它把"今天"钉死）。 */
    public static int dateKey(long millis, TimeZone zone) {
        Calendar cal = new GregorianCalendar(zone);
        cal.setTimeInMillis(millis);
        return cal.get(Calendar.YEAR) * 10000
                + (cal.get(Calendar.MONTH) + 1) * 100
                + cal.get(Calendar.DAY_OF_MONTH);
    }

    /** 前一天（跨月跨年都按真实日历回退）。 */
    public static int previousKey(int dateKey) {
        Calendar cal = toCalendar(dateKey);
        cal.add(Calendar.DAY_OF_MONTH, -1);
        return keyOf(cal);
    }

    /** 日期键 → "MM-dd" 展示串。 */
    public static String display(int dateKey) {
        int mm = (dateKey / 100) % 100;
        int dd = dateKey % 100;
        return two(mm) + "-" + two(dd);
    }

    /** 今日一句话规则说明（菜单、HUD、结算共用）。 */
    public static String summary(int dateKey) {
        return display(dateKey) + " · " + ruleFor(dateKey).label
                + "全程 · " + livesFor(dateKey) + " 条命";
    }

    private static Calendar toCalendar(int dateKey) {
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.set(dateKey / 10000, (dateKey / 100) % 100 - 1, dateKey % 100, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    private static int keyOf(Calendar cal) {
        return cal.get(Calendar.YEAR) * 10000
                + (cal.get(Calendar.MONTH) + 1) * 100
                + cal.get(Calendar.DAY_OF_MONTH);
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }
}
