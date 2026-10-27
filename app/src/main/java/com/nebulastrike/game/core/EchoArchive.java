package com.nebulastrike.game.core;

/**
 * 后日谈档案（v1.50）：回声周目的终局之后，用「回声碎片」逐段解密。
 *
 * <p>纯数据 + 纯函数，不引用 Android。前六段按顺序解锁，最后一段是隐藏档案：
 * 需要前六段全部解密、且至少打穿一次回声周目，费用为 0——它不是商品，是完成奖励。
 */
public final class EchoArchive {

    public static final int COUNT = 7;

    private static final String[] TAGS = {
            "FRAGMENT 01 · 返航",
            "FRAGMENT 02 · 残响",
            "FRAGMENT 03 · 座次",
            "FRAGMENT 04 · 静默",
            "FRAGMENT 05 · 留白",
            "FRAGMENT 06 · 循环",
            "HIDDEN · 起飞"
    };

    private static final String[] TITLES = {
            "返航日志",
            "黑匣子的上一行",
            "第九个座位",
            "摇篮之外",
            "没有回声的星区",
            "第三次循环",
            "隐藏档案 · 起飞"
    };

    /** 解密费用（回声碎片）。第 0 段起逐段变贵，隐藏档案免费。 */
    private static final int[] COSTS = {2, 3, 4, 5, 6, 8, 0};

    private static final String[][] BODIES = {
            {
                    "星火号回港那天，母星没有举行仪式。塔台只问了一句：你从哪条航线回来？",
                    "你报出八段航程的编号，对面沉默了七秒。然后说：那条航线三小时前已经注销了。"
            },
            {
                    "黑匣子最旧的一行不是遗言，是一串倒计时。每一秒都对应一次起飞，也对应一次没有回来。",
                    "工程兵把日志翻到第一页，发现起始时间比星火号下水还早三年。"
            },
            {
                    "摇篮熄灭后，八艘旗舰的座次空出一个。回声残余把星火号的名字刻上去，刻到一半停住了。",
                    "你伸手把那行字划掉。第九个座位从此空着——不是没人配得上，而是不需要了。"
            },
            {
                    "星核之外没有回声。第一次听见真正的静默时，全舰的人都以为通讯坏了。",
                    "导航员说，静默也是一种回答。你点点头，把引擎功率降到最低，听完了一整段。"
            },
            {
                    "新的航线图上出现一块空白：所有信号发进去都不返回，连回声也没有。",
                    "参谋建议派无人艇测绘。你否决了，把那一格留白——有些地方，不需要被填满。"
            },
            {
                    "档案员核对了三遍，才敢把结论写下来：这是第三次天亮。前两次的你也走到了这里。",
                    "前两次的你没有选择返航。这一次，你把返航键接上了主电源。"
            },
            {
                    "后日谈归档完成。星火号再次点火，但这一次没有人下达命令。",
                    "航线尽头没有敌人，也没有等待被改写的结局，只有一段尚未被写下的星海。",
                    "你推下油门。日志自动添上第一行：今天，由我来写。"
            }
    };

    private EchoArchive() {
    }

    public static String tag(int i) {
        return i >= 0 && i < COUNT ? TAGS[i] : "";
    }

    public static String title(int i) {
        return i >= 0 && i < COUNT ? TITLES[i] : "";
    }

    public static int cost(int i) {
        return i >= 0 && i < COUNT ? COSTS[i] : 0;
    }

    public static String[] body(int i) {
        return i >= 0 && i < COUNT ? BODIES[i] : new String[0];
    }

    /** 隐藏档案：完成前六段并至少通关一次回声周目才出现。 */
    public static boolean hidden(int i) {
        return i == COUNT - 1;
    }

    public static boolean unlocked(long mask, int i) {
        return i >= 0 && i < COUNT && (mask & (1L << i)) != 0L;
    }

    public static long unlock(long mask, int i) {
        if (i < 0 || i >= COUNT) return mask;
        return mask | (1L << i);
    }

    public static int unlockedCount(long mask) {
        int n = 0;
        for (int i = 0; i < COUNT; i++) if (unlocked(mask, i)) n++;
        return n;
    }

    /** 全部解密（含隐藏档案）。 */
    public static boolean complete(long mask) {
        return unlockedCount(mask) >= COUNT;
    }

    /** 前六段的总费用。 */
    public static int totalCost() {
        int sum = 0;
        for (int i = 0; i < COUNT; i++) if (!hidden(i)) sum += COSTS[i];
        return sum;
    }

    /**
     * 该段是否已经走到可解密的位置：前六段只要求上一段已解密；
     * 隐藏档案要求前六段全部解密且至少通关一次回声周目。
     */
    public static boolean reachable(long mask, int i, int echoClears) {
        if (i < 0 || i >= COUNT) return false;
        if (hidden(i)) return unlockedCount(mask) >= COUNT - 1 && echoClears >= 1;
        return i == 0 || unlocked(mask, i - 1);
    }

    /** 锁定时的解锁条件文案。 */
    public static String unlockHint(int i) {
        if (hidden(i)) return "解密前六段后日谈，并至少打穿一次回声周目";
        if (i == 0) return "任意一场回声旗舰掉落的碎片即可解密";
        return "先解密上一段档案";
    }
}
