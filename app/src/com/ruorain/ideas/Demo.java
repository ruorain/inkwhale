package com.ruorain.ideas;

import java.util.ArrayList;
import java.util.List;

/** 演示模式的内置数据：不联网、不用配置就能看完整界面。 */
public final class Demo {

    private Demo() {}

    private static final String[][] FILES = {
            {"ideas/欢迎.md", "欢迎"},
            {"ideas/设计/离线优先的本地知识库.md", "离线优先的本地知识库"},
            {"ideas/产品/语音速记的降级方案.md", "语音速记的降级方案"},
            {"Deepseek/回顾/2026-W43.md", "每周回顾 2026-W43"},
            {"Deepseek/idea-repo-app/动效规格.md", "动效规格"},
    };

    public static List<GitHubApi.Entry> files() {
        List<GitHubApi.Entry> out = new ArrayList<GitHubApi.Entry>();
        for (String[] f : FILES) {
            out.add(new GitHubApi.Entry(f[0], content(f[0]).length()));
        }
        return out;
    }

    public static String content(String path) {
        if (path == null) return "";
        if (path.endsWith("欢迎.md")) {
            return "# 欢迎\n\n> V0.1 · 2026-10-26 14:00 · raw\n\n这是演示数据，不会上传到任何地方。\n\n"
                    + "## 你可以试试\n\n- 点开下面几篇看看排版\n- 回到首页看「最新想法」和「查看全部」\n"
                    + "- 点右上角那个纸飞机，和模型聊天\n\n"
                    + "> 真正的数据存在你自己的 GitHub 仓库里，这个演示只是给你看个样子。\n";
        }
        if (path.endsWith("离线优先的本地知识库.md")) {
            return "# 离线优先的本地知识库\n\n> V0.13 · 2026-10-25 21:30 · raw\n\n"
                    + "## 背景\n\n手机上的笔记应用几乎都要求联网。地铁里没信号的时候，灵感只能记在备忘录里，回头再手动搬。\n\n"
                    + "## 目标\n\n- 本地写入零等待\n- 联网后自动合并\n- 冲突时不丢数据\n\n"
                    + "## 实现路径\n\n1. 用 SQLite 存草稿\n2. 队列化推送\n3. 版本号作为乐观锁\n\n"
                    + "> 关键点：冲突不是错误，是一个需要用户决策的状态。\n\n"
                    + "| 场景 | 处理 |\n| --- | --- |\n| sha 一致 | 直接写入 |\n| sha 不一致 | 提示用户 |\n"
                    + "| 网络失败 | 入队重试 |\n\n- [x] 本地存储\n- [x] 推送队列\n- [ ] 自动合并\n";
        }
        if (path.endsWith("语音速记的降级方案.md")) {
            return "# 语音速记的降级方案\n\n> V0.4 · 2026-10-24 18:02 · active\n\n"
                    + "## 问题\n\n识别率在嘈杂环境下掉得厉害，用户会觉得「这东西不能用」。\n\n"
                    + "## 想法\n\n不要追求一次识别对，改成「先留声，后转写」：\n\n"
                    + "1. 按下就开始录音，不做实时识别\n2. 松手后本地先存音频\n3. 联网时批量转写\n\n"
                    + "这样即使转写质量一般，至少内容不丢。\n";
        }
        if (path.contains("回顾")) {
            return "# 每周回顾 · 2026-W43\n\n> 生成于 2026-10-25 01:00 UTC · 覆盖 7 篇\n\n"
                    + "## 这周在想什么\n\n主线是「离线可用」。从本地知识库到语音速记，都在解决同一个问题：网络不好时别让人干等。\n\n"
                    + "## 值得继续推进的\n\n1. **冲突处理** —— 已经有清晰的三选一方案，可以直接实现\n"
                    + "2. **降级策略** —— 先留声后转写，思路完整\n\n"
                    + "## 停在半路的\n\n- 「把想法自动同步到日历」只有标题，没有展开\n\n"
                    + "## 下周建议\n\n先把冲突处理做完，它是其他离线功能的地基。\n";
        }
        return "# 动效规格\n\n> V0.1 · 2026-10-25 23:30 · raw\n\n"
                + "## 总原则\n\n1. 所有位移/缩放都用弹簧，不用线性\n2. 按压反馈 100ms 内出现\n"
                + "3. 过冲幅度 3–8%\n4. 进场慢（380–420ms），出场快（140–160ms）\n\n"
                + "## 参数\n\n| 场景 | damp | freq | 时长 |\n| --- | --- | --- | --- |\n"
                + "| 页面淡入 | 7.5 | 7.0 | 460ms |\n| 底部面板 | 7.0 | 8.0 | 420ms |\n"
                + "| 居中弹窗 | 7.2 | 7.6 | 380ms |\n\n"
                + "```kotlin\nfun spring(t: Float): Float =\n    1f - exp(-damp * t) * (cos(freq * t) + (damp / freq) * sin(freq * t))\n```\n";
    }
}
