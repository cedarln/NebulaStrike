package com.nebulastrike.game.core;

import java.util.Arrays;

/**
 * 成就判定器（v1.18）：读取对局统计，推进「单局最佳」与「生涯累计」两本账，
 * 达标就解锁并通过 {@link Listener} 播报。
 *
 * <p>三处设计上的讲究，都是为了守住"绝不白打"的约定：
 *
 * <ul>
 *   <li><b>增量入账</b>：生涯累计不是"结算时把本局数字加上去"，而是每轮结算只累加
 *       "上次入账后新增的部分"。这样对局中途就能解锁生涯成就，坠机、切后台、
 *       直接退出也不会重复或漏计。</li>
 *   <li><b>只写不回滚</b>：单局读数归零时（{@link #beginRun}）只清自己的记账，
 *       已经写进 {@link Settings} 的最佳值、累计值和解锁位图一律保留。</li>
 *   <li><b>判定节流</b>：0.5 秒扫一遍全表（28 条），对局内既够即时又不用每帧白跑。</li>
 * </ul>
 */
public final class Achievements {

    /** 解锁播报回调（GameWorld 用来发事件、发奖励分与演出）。 */
    public interface Listener {
        void onUnlock(Achievement a);
    }

    /** 判定节流间隔（秒）。 */
    static final float CHECK_INTERVAL = 0.5f;

    private final int[] run = new int[Achievement.Metric.COUNT];
    private final int[] credited = new int[Achievement.Metric.COUNT];
    private final Listener listener;
    private float timer = CHECK_INTERVAL;
    /** 有账目或解锁位图变化时需要落盘。 */
    private boolean pendingSave;

    public Achievements(Listener listener) {
        this.listener = listener;
    }

    /** 新的一局：单局读数与增量记账归零，已写入 Settings 的进度原封不动。 */
    public void beginRun() {
        Arrays.fill(run, 0);
        Arrays.fill(credited, 0);
        timer = CHECK_INTERVAL;
    }

    /** 对局内每帧推进：刷新读数，到点才真正结算一次。 */
    public void update(Settings s, int[] metrics, float dt) {
        copy(metrics);
        timer -= dt;
        if (timer > 0f) return;
        timer = CHECK_INTERVAL;
        settle(s);
    }

    /** 立刻结算一次：坠机、退出到菜单、暂停时调用，保证增量不丢。 */
    public void commit(Settings s, int[] metrics) {
        copy(metrics);
        timer = CHECK_INTERVAL;
        settle(s);
    }

    /** 有待落盘的改动时返回 true 并清标记（GameWorld 的存档通道用）。 */
    public boolean consumePendingSave() {
        boolean v = pendingSave;
        pendingSave = false;
        return v;
    }

    /** 某条成就当前用于判定的读数（成就页显示进度）。 */
    public static int valueOf(Settings s, Achievement a) {
        int m = a.metric.ordinal();
        return a.scope == Achievement.Scope.RUN ? s.achieveBest[m] : s.achieveTotal[m];
    }

    public static int unlockedCount(Settings s) {
        return Long.bitCount(s.achieveMask);
    }

    private void copy(int[] metrics) {
        for (int i = 0; i < run.length; i++) {
            run[i] = i < metrics.length && metrics[i] > 0 ? metrics[i] : 0;
        }
    }

    private void settle(Settings s) {
        for (int i = 0; i < run.length; i++) {
            if (run[i] > s.achieveBest[i]) {
                s.achieveBest[i] = run[i];
                pendingSave = true;
            }
            if (run[i] > credited[i]) {
                // 饱和加：生涯账本溢出成负数会被 sanitize 归零，等于几十年的进度蒸发
                int delta = run[i] - credited[i];
                s.achieveTotal[i] = s.achieveTotal[i] > Integer.MAX_VALUE - delta
                        ? Integer.MAX_VALUE : s.achieveTotal[i] + delta;
                credited[i] = run[i];
                pendingSave = true;
            }
        }
        for (Achievement a : Achievement.values()) {
            if (a.unlockedIn(s.achieveMask)) continue;
            if (!a.reached(valueOf(s, a))) continue;
            s.achieveMask |= a.bit();
            pendingSave = true;
            if (listener != null) listener.onUnlock(a);
        }
    }
}
