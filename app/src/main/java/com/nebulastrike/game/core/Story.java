package com.nebulastrike.game.core;

/**
 * 主线档案：每一段航程对应一章，击破 Boss 即解密下一章。
 *
 * <p>纯数据 + 纯函数，不引用 Android，方便测试与渲染层共用。
 * 章节从 0 开始索引，但用来存档的 {@code storyUnlocked} 是"已解密章数"（1 起）。
 */
public final class Story {
    public static final int CHAPTER_COUNT = 9;

    private static final String[] TITLES = {
            "序章 · 静默轨道",
            "灰烬航道",
            "断链船坞",
            "蜂巢回声",
            "相位裂谷",
            "黑潮磁暴",
            "镜面深渊",
            "星核之环",
            "终章 · 黎明之后"
    };

    private static final String[] TAGS = {
            "SECTOR 1 · 唤醒",
            "SECTOR 2 · 残响",
            "SECTOR 3 · 复制",
            "SECTOR 4 · 节点",
            "SECTOR 5 · 干涉",
            "SECTOR 6 · 漩涡",
            "SECTOR 7 · 倒影",
            "SECTOR 8 · 摇篮",
            "ENDING · 返航"
    };

    private static final String[][] BODIES = {
            {
                    "星火号在中继站的冷启动中醒来。舷窗外没有星光，只有一段重复播放的求救信号。",
                    "信号来自三小时前的自己。航道尽头，一支本该返航的舰队正在掉头——炮口对准母星。",
                    "指挥官留下最后一条命令：沿旧航线回溯，找到回声的源头。不要相信任何仍然在说话的东西。"
            },
            {
                    "第一艘被改写的旗舰挡在航道上。它还在用旧识别码呼叫你，语气和死亡前一模一样。",
                    "击破它之后，黑匣子里只剩一句话：回声会复制战术，也会复制恐惧。",
                    "你把残骸坐标发回母星，然后继续向星云深处推进。"
            },
            {
                    "断链船坞的自动装配线仍在运转。传送带上没有一艘友舰，只有不断成形的敌舰。",
                    "回声不只会模仿，它开始学习。每一次交手，它都会把没见过的东西加进下一批蓝图。",
                    "你炸掉船坞的供能核心。离开时，身后的流水线仍在黑暗里继续组装。"
            },
            {
                    "蜂后把整片空域变成了孵化场。她的幼机共享同一个回声，像同一颗心脏的无数根血管。",
                    "你击穿蜂后。一屏幼机同时熄火——第一次证明，回声也有可以被切断的节点。",
                    "这段经验被写进作战手册：找到那个最吵的节点，然后只打它。"
            },
            {
                    "裂谷里的舰船在两个位置之间闪烁。炮火穿过它们，像穿过一段没有录完的影像。",
                    "你在它们重新实体化的瞬间开火。回声第一次用你自己的声音回话。",
                    "它说：你也在重复。你飞过的每一条航线，都是三小时前的你留下的。"
            },
            {
                    "磁暴艇把弹道拧成漩涡，星火号的引擎第一次熄火。你靠擦弹、引力井和不规则的走位撑了过去。",
                    "回声在计算你。它害怕不可预测的东西——因为那让它无法提前三小时写好结局。",
                    "你重新点火，把航线改成一个没有记录过的角度。"
            },
            {
                    "伪装者变成补给、变成友军、变成你的倒影。每一枚假道具都在教你同一件事：停下来。",
                    "你识破它们，也识破自己：你不是来寻找真相的，你是来结束一段循环的。",
                    "镜面深处，第八艘旗舰亮起灯光。它长得和星火号一模一样。"
            },
            {
                    "航道的终点是一枚被八艘旗舰环绕的星核。回声称它为摇篮，并邀请你留下，成为第九艘。",
                    "世界吞噬者的轮廓在星核背后展开。它没有面孔，只有一整片被折叠的旧战场。",
                    "你把所有武器解锁，关掉返航信标。这一战之后，无论谁还活着，都不会再回到原来的航线。"
            },
            {
                    "世界吞噬者倒下，回声第一次沉默。星核熄灭，折叠的旧战场像潮水一样退回黑暗。",
                    "星火号把最后一段航程发回母星：这里没有神，只有一艘不肯返航的船。",
                    "天亮了。档案到此为止，而航线仍在继续——下一次，由你来写。"
            }
    };

    private Story() {
    }

    /** 章节标题（0 起）。越界返回空串，渲染层不必再包一层判空。 */
    public static String title(int chapter) {
        return chapter >= 0 && chapter < CHAPTER_COUNT ? TITLES[chapter] : "";
    }

    /** 章节小标签（SECTOR / ENDING）。 */
    public static String tag(int chapter) {
        return chapter >= 0 && chapter < CHAPTER_COUNT ? TAGS[chapter] : "";
    }

    /** 章节正文段落（0 起）；越界返回空数组。 */
    public static String[] body(int chapter) {
        return chapter >= 0 && chapter < CHAPTER_COUNT ? BODIES[chapter] : new String[0];
    }

    /** 第 n 章（0 起）是否是终章。 */
    public static boolean isEnding(int chapter) {
        return chapter == CHAPTER_COUNT - 1;
    }

    /**
     * 据点编号 → 已解密章数（1 起）。
     * 第 1 关对应序章；打穿第 8 关（最终 Boss）解密终章。
     */
    public static int chaptersForSector(int sector) {
        if (sector < 1) return 1;
        return sector > CHAPTER_COUNT ? CHAPTER_COUNT : sector;
    }

    /** 累计击破 Boss 数 → 已解密章数。 */
    public static int chaptersForBosses(int bossesDefeated) {
        if (bossesDefeated < 0) bossesDefeated = 0;
        return chaptersForSector(bossesDefeated + 1);
    }

    /** 该章的解密条件文案（锁定时显示）。 */
    public static String unlockHint(int chapter) {
        if (chapter <= 0) return "开局即解密";
        if (isEnding(chapter)) return "击破最终 Boss 后解密";
        return "打穿第 " + chapter + " 关后解密";
    }

    /** 第 n 关的档案标题（1 起）；越界回退到序章。 */
    public static String sectorTitle(int sector) {
        int chapter = chaptersForSector(sector) - 1;
        return title(chapter);
    }

    public static String total() {
        return String.valueOf(CHAPTER_COUNT);
    }
}
