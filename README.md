# Inkwhale · 墨鲸

[![release](https://img.shields.io/github/v/release/ruorain/inkwhale?color=2F6FED)](https://github.com/ruorain/inkwhale/releases)
[![license](https://img.shields.io/github/license/ruorain/inkwhale)](LICENSE)
[![apk](https://img.shields.io/badge/APK-177%20KB-2F6FED)](https://github.com/ruorain/inkwhale/releases/latest)
[![android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)](https://github.com/ruorain/inkwhale/releases/latest)

把想法随手记进**你自己的 GitHub 仓库**的 Android 应用。

没有服务器、没有账号体系、没有云同步服务——你的笔记就是仓库里的一堆 `.md` 文件，永远属于你。

---

## 它解决什么问题

- 手机上的笔记应用几乎都要求联网，地铁里没信号就写不了
- 存进别人的服务，导出时格式一团糟
- 记了几百条，再也没打开过

云笔记的做法是：**笔记以 Markdown 存在你的 GitHub 仓库里**。手机端负责「记」和「读」，电脑端和 CI 负责「整理」。

---

## 功能

### 记

- Markdown 编辑，底部工具栏插入标题、粗体、列表、代码块、表格、任务列表
- 实时预览：标题 / 表格 / 代码块 / 引用 / 任务列表全部渲染
- 新建自动带格式：`> V0.1 · 时间 · raw` 元信息行
- 文件夹：标题里写 `设计/新想法` 就直接落进子目录

### 存

保存时二选一：

| 方式 | 行为 |
|---|---|
| 版本迭代 | 旧内容归档到 `.versions/<路径>/<标题>/V0.12.md`，新内容写主文件 |
| 覆盖 | 就地更新，不留归档 |

版本号永远是 `0.x`，不会进位成 `1.0`。

### 离线

- 启动先读本地缓存，没网也能立刻看到列表
- 打开过的文件会缓存正文，断网照样能读
- 断网时编辑 → 自动存本地草稿 → 联网后自动推送

### 整理

- 首页：问候语 + 最新想法 + 本周回顾卡片
- 周报由 GitHub Actions 每周日自动生成：读最近 7 天改动，输出主题归纳 / 该推进的 / 停在半路的 / 下周建议
- 周报可以「签收」，签收后首页不再提醒

### 和 AI 一起想

- 带上下文进对话：从某篇想法点「和 AI 聊聊」，全文自动注入
- 本地检索：发问前先在仓库里搜相关片段，回答会标注「引用了 N 篇想法」
- 存为想法：任何一条 AI 回答一键落成 `.md`
- 整理成一篇：聊满 3 轮，把整段讨论压成结构化笔记，跳进编辑器可改
- 模型：DeepSeek V4.1 Flash / V4 Pro，智谱 GLM-4.5 / Air / Flash
- 思考强度：不思考 / 低 / 高 / 最大（智谱只有开 / 关）

---

## 为什么不用 Gradle / AndroidX

这个项目用手搓工具链构建：`aapt2 → javac → d8 → zipalign → apksigner`，没有 Gradle、没有 AndroidX、没有第三方依赖。

结果：

- APK 只有 **160 KB**
- 编译一次约 3 秒
- 所有 UI 用代码构建，没有一个 XML 布局文件

代价是不能用现成组件库，好处是启动快、体积小、依赖为零。

---

## 构建

需要 JDK 17、Android build-tools 34、platform android-32。

```bash
aapt2 compile --dir res -o build/res
aapt2 link -o build/base.apk --manifest AndroidManifest.xml \
  -I $ANDROID_HOME/platforms/android-32/android.jar \
  -R build/res/*.flat --java gen
javac -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath $ANDROID_HOME/platforms/android-32/android.jar \
  -d build/classes gen/**/*.java src/**/*.java
d8 --lib $ANDROID_HOME/platforms/android-32/android.jar --output build/dex build/classes
# 把 classes.dex 塞进 base.apk，zipalign，apksigner 签名
```

---

## 目录

```
app/
  AndroidManifest.xml
  res/
    drawable/          Lucide 图标（ISC）+ GitHub mark
    drawable-nodpi/    鲸鱼、智谱标志
    mipmap-*/          启动图标
    values/            主题、配色开关
  src/com/ruorain/ideas/
    RouterActivity     启动分发
    OnboardingActivity 四步引导
    WebViewActivity    内置浏览器（带定向提示）
    MainActivity       首页 + 所有想法
    EditorActivity     编辑与保存
    PreviewActivity    Markdown 渲染
    DigestActivity     周报列表
    ChatActivity       AI 对话
    SettingsActivity   设置
    GitHubApi          Contents / Tree API 客户端
    AiClient           DeepSeek + 智谱
    Markdown           md → HTML
    Store              配置、缓存、草稿
    Banner             底部横幅
    Sheet              底部面板
    Demo               演示数据

ci/
  weekly.yml           每周回顾 workflow
  weekly.mjs           周报生成脚本

tools/
  convert_icons.py     把 Lucide SVG 转成 VectorDrawable
  build_preview.py     视觉稿打包（可选）
```

---

## 数据在哪

```
ideas/                    你自己写的
Deepseek/                 模型写的
Deepseek/回顾/            每周自动生成
.versions/                旧版本归档
```

删除应用不会丢任何东西——文件都在你的仓库里。

---

## 演示版

`config.xml` 里把 `is_demo_build` 改成 `true` 再构建，就是纯展示版：启动直接进演示、内置 5 篇示例、不联网也能看。

---

## 许可

代码 MIT。图标来自 [Lucide](https://lucide.dev)（ISC），GitHub mark 属于 GitHub, Inc.

