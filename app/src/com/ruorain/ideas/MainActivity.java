package com.ruorain.ideas;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页（mode=home）：问候 + 周报入口 + 最新想法 + 查看全部
 * 全部想法（mode=all）：按文件夹分组
 */
public class MainActivity extends BaseActivity {

    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;
    private static final int REQ_EDITOR = 11;
    private static final int REQ_SETTINGS = 12;

    private String mode = "home";
    private int textColor;
    private LinearLayout body;
    private TextView subTitle;
    private boolean loading;
    private List<GitHubApi.Entry> lastFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mode = getIntent().getStringExtra("mode");
        if (mode == null || mode.isEmpty()) mode = "home";
        super.onCreate(savedInstanceState);
        setContentView(build());
        load();
        handleShare(getIntent());
    }

    /** 从别的 app 分享文字进来，直接开一篇新想法 */
    private void handleShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null || text.trim().isEmpty()) return;
        setIntent(new Intent(this, MainActivity.class)); // 防止旋转重放

        String prefill = "# \n\n> V0.1 · " + new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm", java.util.Locale.US).format(new java.util.Date())
                + " · raw\n\n" + text.trim() + "\n";
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra("prefill", prefill);
        startActivityForResult(i, REQ_EDITOR);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String m = intent.getStringExtra("mode");
        if (m != null && !m.equals(mode)) {
            mode = m;
            setContentView(build());
            load();
        }
        handleShare(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (body != null && !loading && lastFiles != null) render(lastFiles);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EDITOR && resultCode == RESULT_OK) {
            load();
            // GitHub tree 有 CDN 缓存，刚写完立刻读可能还是旧列表
            body.postDelayed(new Runnable() {
                @Override
                public void run() {
                    load();
                }
            }, 1600);
        }
        if (requestCode == REQ_SETTINGS) {
            Store.saveCache(this, "");
            load();
        }
    }

    @Override
    protected void onConnectivityChanged(boolean online) {
        if (!online) return;
        // 一联网就把离线时写的草稿推上去
        DraftSync.pushAll(this, dark, new Runnable() {
            @Override
            public void run() {
                if (!loading) load();
            }
        });
    }

    private boolean isHome() {
        return "home".equals(mode);
    }

    // ── 构建 ──────────────────────────────────────────────
    private View build() {
        textColor = Colors.text(dark);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Colors.bg(dark));

        LinearLayout col = UI.row(this, false);
        root.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));

        col.addView(isHome() ? buildHomeBar() : buildAllBar());

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setClipToPadding(false);
        body = UI.row(this, false);
        body.setPadding(0, 0, 0, UI.dp(this, 104));
        sv.addView(body);
        col.addView(sv, UI.lp(MATCH, 0, 1f));

        // 演示版是纯展示，不给新建入口
        if (!Store.isDemo(this)) root.addView(buildFab());
        return root;
    }

    private View buildHomeBar() {
        LinearLayout bar = UI.row(this, true);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UI.dp(this, 20), UI.dp(this, 18), UI.dp(this, 12), UI.dp(this, 8));

        ImageView whale = new ImageView(this);
        whale.setImageResource(R.drawable.whale);
        whale.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(UI.dp(this, 34), UI.dp(this, 34));
        wlp.rightMargin = UI.dp(this, 12);
        bar.addView(whale, wlp);

        LinearLayout titles = UI.row(this, false);
        TextView greet = UI.text(this, greeting(), 20f, textColor, Typeface.BOLD);
        titles.addView(greet);
        subTitle = UI.text(this, "想从什么项目开始？", 12.5f, Colors.text3(dark));
        LinearLayout.LayoutParams subLp = UI.lp(WRAP, WRAP);
        subLp.topMargin = UI.dp(this, 4);
        titles.addView(subTitle, subLp);
        bar.addView(titles, UI.lp(MATCH, WRAP, 1f));

        bar.addView(iconButton(R.drawable.ic_chat, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ChatActivity.class));
                overridePendingTransition(R.anim.enter, R.anim.exit);
            }
        }));
        bar.addView(iconButton(R.drawable.ic_settings, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(MainActivity.this, SettingsActivity.class), REQ_SETTINGS);
                overridePendingTransition(R.anim.enter, R.anim.exit);
            }
        }));
        return bar;
    }

    private View buildAllBar() {
        LinearLayout bar = UI.row(this, true);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UI.dp(this, 8), UI.dp(this, 14), UI.dp(this, 12), UI.dp(this, 10));

        bar.addView(iconButton(R.drawable.ic_arrow_left, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(R.anim.back_in, R.anim.back_out);
            }
        }));

        LinearLayout titles = UI.row(this, false);
        TextView h1 = UI.text(this, "所有想法", 22f, textColor, Typeface.BOLD);
        titles.addView(h1);
        subTitle = UI.text(this, "", 12f, Colors.text3(dark));
        LinearLayout.LayoutParams subLp = UI.lp(WRAP, WRAP);
        subLp.topMargin = UI.dp(this, 3);
        titles.addView(subTitle, subLp);
        bar.addView(titles, UI.lp(MATCH, WRAP, 1f));

        bar.addView(iconButton(R.drawable.ic_refresh_cw, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        }));
        bar.addView(iconButton(R.drawable.ic_ellipsis_vertical, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMore();
            }
        }));
        return bar;
    }

    private String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 5) return "夜深了";
        if (h < 11) return "早上好";
        if (h < 14) return "中午好";
        if (h < 18) return "下午好";
        return "晚上好";
    }

    // ── 数据 ──────────────────────────────────────────────
    private void load() {
        if (loading) return;
        if (Store.isDemo(this)) {
            lastFiles = Demo.files();
            render(lastFiles);
            scheduleDemoDigest();
            return;
        }
        String token = Store.token(this);
        if (token.isEmpty()) {
            body.removeAllViews();
            showMessage("还没有配置令牌", "去设置", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivityForResult(new Intent(MainActivity.this, SettingsActivity.class), REQ_SETTINGS);
                    overridePendingTransition(R.anim.enter, R.anim.exit);
                }
            });
            return;
        }

        boolean hadCache = renderCached();

        loading = true;
        if (!hadCache) {
            body.removeAllViews();
            showMessage("正在读取仓库…", null, null);
        }

        GitHubApi.listMarkdown(Store.repo(this), Store.branch(this), token,
                new GitHubApi.Cb<List<GitHubApi.Entry>>() {
                    @Override
                    public void ok(List<GitHubApi.Entry> files) {
                        loading = false;
                        lastFiles = files;
                        // 空结果不覆盖缓存：一次异常响应不该把本地列表清空
                        if (!files.isEmpty()) Store.saveCache(MainActivity.this, encode(files));
                        render(files);
                    }

                    @Override
                    public void err(String message) {
                        loading = false;
                        if (hadCache) {
                            Banner.show(MainActivity.this, dark, "刷新失败，正在显示本地缓存", false);
                        } else {
                            recover(message);
                        }
                    }
                });
    }

    /**
     * 读不到内容时先诊断：
     *   空仓库 → 自动写一份说明并建 ideas/
     *   分支名不对 → 自动改成仓库的默认分支再试
     *   其他 → 原样显示错误
     */
    private void recover(final String original) {
        final String token = Store.token(this);
        final String repo = Store.repo(this);
        GitHubApi.repoInfo(repo, token, new GitHubApi.Cb<GitHubApi.RepoInfo>() {
            @Override
            public void ok(final GitHubApi.RepoInfo info) {
                if (info.size == 0) {
                    Banner.show(MainActivity.this, dark, "仓库还是空的，正在写入说明…", true);
                    GitHubApi.initRepo(repo, info.defaultBranch, token,
                            new GitHubApi.Cb<String>() {
                                @Override
                                public void ok(String x) {
                                    Banner.hide(MainActivity.this);
                                    Store.set(MainActivity.this, "branch", info.defaultBranch);
                                    Banner.show(MainActivity.this, dark, "仓库初始化完成", false);
                                    load();
                                }

                                @Override
                                public void err(final String m) {
                                    Banner.hide(MainActivity.this);
                                    body.removeAllViews();
                                    showMessage("这个空仓库没能自动初始化：\n" + m
                                                    + "\n\n可以在网页上手动建一个文件，回来再点重试",
                                            "去网页创建", new View.OnClickListener() {
                                                @Override
                                                public void onClick(View v) {
                                                    Intent wi = new Intent(MainActivity.this, WebViewActivity.class);
                                                    wi.putExtra("url", "https://github.com/" + repo
                                                            + "/new/" + info.defaultBranch);
                                                    wi.putExtra("title", "创建第一个文件");
                                                    startActivity(wi);
                                                }
                                            });
                                }
                            });
                    return;
                }

                String cfg = Store.branch(MainActivity.this);
                if (info.defaultBranch != null && !info.defaultBranch.isEmpty()
                        && !info.defaultBranch.equals(cfg)) {
                    Store.set(MainActivity.this, "branch", info.defaultBranch);
                    Banner.show(MainActivity.this, dark, "分支改成 " + info.defaultBranch + " 了", false);
                    load();
                    return;
                }

                fail(original, original);
            }

            @Override
            public void err(String m) {
                // 仓库根本不存在时，试着用钥匙直接建一个
                String[] parts = repo.split("/");
                boolean notFound = m != null && (m.contains("404") || m.contains("不存在"));
                if (notFound && parts.length == 2) {
                    Banner.show(MainActivity.this, dark, "没找到这个仓库，正在为你创建…", true);
                    GitHubApi.createRepo(parts[1], true, token, new GitHubApi.Cb<String>() {
                        @Override
                        public void ok(String full) {
                            Banner.hide(MainActivity.this);
                            Banner.show(MainActivity.this, dark, "已创建仓库 " + full, false);
                            load();
                        }

                        @Override
                        public void err(String e) {
                            Banner.hide(MainActivity.this);
                            fail("仓库不存在，自动创建也失败了：\n" + e
                                    + "\n\n可能是钥匙没勾 Administration 权限，或者仓库名拼错了。", original);
                        }
                    });
                    return;
                }
                fail(original, original);
            }
        });
    }

    private void fail(String message, String original) {
        body.removeAllViews();
        showMessage(message, "重试", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        });
    }

    private boolean renderCached() {
        String json = Store.loadCache(this);
        if (json == null || json.isEmpty()) return false;
        try {
            List<GitHubApi.Entry> files = decode(json);
            if (files.isEmpty()) return false;
            lastFiles = files;
            render(files);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String encode(List<GitHubApi.Entry> files) {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < files.size(); i++) {
            GitHubApi.Entry e = files.get(i);
            try {
                JSONObject o = new JSONObject();
                o.put("p", e.path);
                o.put("s", e.size);
                arr.put(o);
            } catch (Exception ignored) {
                // 单条失败不影响整体
            }
        }
        return arr.toString();
    }

    private List<GitHubApi.Entry> decode(String json) throws Exception {
        List<GitHubApi.Entry> out = new ArrayList<GitHubApi.Entry>();
        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            out.add(new GitHubApi.Entry(o.optString("p", ""), o.optLong("s", 0)));
        }
        return out;
    }

    // ── 渲染 ──────────────────────────────────────────────
    private void render(List<GitHubApi.Entry> files) {
        if (files.isEmpty()) {
            body.removeAllViews();
            if (subTitle != null) subTitle.setText("仓库里还没有内容");
            showMessage("仓库里还没有 markdown", "写第一篇", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openEditor(null);
                }
            });
            return;
        }
        if (isHome()) renderHome(files);
        else renderAll(files);
    }

    private void renderHome(List<GitHubApi.Entry> files) {
        body.removeAllViews();

        if (subTitle != null) {
            subTitle.setText(Store.isDemo(this) ? "演示模式 · 数据是内置的"
                    : (digestOf(files) != null ? "本周回顾已生成" : "想从什么项目开始？"));
        }

        GitHubApi.Entry digest = digestOf(files);
        if (digest != null) body.addView(digestCard(digest, files));

        List<GitHubApi.Entry> recent = recentOf(files, 5);
        body.addView(sectionLabel("最近打开"));
        if (recent.isEmpty()) {
            body.addView(emptyCard());
        } else {
            LinearLayout card = UI.row(this, false);
            card.setBackground(UI.roundRect(this, Colors.surface(dark), 14, Colors.line(dark), 0.8f));
            card.setClipToOutline(true);
            for (int i = 0; i < recent.size(); i++) {
                if (i > 0) card.addView(UI.divider(this, dark));
                card.addView(fileRow(recent.get(i), true));
            }
            body.addView(cardWithMargin(card));
        }

        body.addView(seeAllButton(files.size()));
        body.addView(helpCard());
        body.addView(brandFooter());
    }

    /** 演示版：进来 5 秒后自动弹一次周报，让人看到这个功能 */
    private void scheduleDemoDigest() {
        if (demoDigestShown) return;
        demoDigestShown = true;
        body.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || !Store.isDemo(MainActivity.this)) return;
                Intent i = new Intent(MainActivity.this, PreviewActivity.class);
                i.putExtra("content", Demo.content("Deepseek/回顾/2026-W43.md"));
                i.putExtra("title", "每周回顾 2026-W43");
                startActivity(i);
                overridePendingTransition(R.anim.enter, R.anim.exit);
            }
        }, 5000);
    }

    private boolean demoDigestShown = false;

    private View brandFooter() {
        LinearLayout row = UI.row(this, true);
        row.setGravity(Gravity.CENTER);

        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.inkwhale);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(UI.dp(this, 16), UI.dp(this, 16));
        mlp.rightMargin = UI.dp(this, 7);
        row.addView(mark, mlp);

        TextView t = UI.text(this, "inkwhale · 墨鲸", 12f, Colors.text3(dark));
        row.addView(t);

        LinearLayout.LayoutParams rlp = UI.lp(MATCH, WRAP);
        rlp.topMargin = UI.dp(this, 26);
        rlp.bottomMargin = UI.dp(this, 18);
        row.setLayoutParams(rlp);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/ruorain/inkwhale")));
                } catch (Exception ignored) {
                    // 没有浏览器就算了
                }
            }
        });
        return row;
    }

    private View helpCard() {
        LinearLayout card = UI.row(this, true);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(UI.roundRect(this, Colors.surface(dark), 12, Colors.line(dark), 0.8f));
        card.setPadding(UI.dp(this, 14), UI.dp(this, 14), UI.dp(this, 14), UI.dp(this, 14));

        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(UI.dp(this, 18), UI.dp(this, 18));
        icLp.rightMargin = UI.dp(this, 10);
        card.addView(UI.icon(this, R.drawable.ic_file_text, 18, Colors.text3(dark)), icLp);

        card.addView(UI.text(this, "使用说明 · 怎么用、常见问题", 13.5f, Colors.text2(dark)),
                UI.lp(MATCH, WRAP, 1f));
        card.addView(UI.icon(this, R.drawable.ic_chevron_right, 16, Colors.text3(dark)));

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, PreviewActivity.class);
                i.putExtra("content", Help.TEXT);
                i.putExtra("title", "使用说明");
                startActivity(i);
                overridePendingTransition(R.anim.enter, R.anim.exit);
            }
        });
        UI.press(card);

        LinearLayout.LayoutParams lp = UI.lp(MATCH, WRAP);
        lp.leftMargin = UI.dp(this, 12);
        lp.rightMargin = UI.dp(this, 12);
        lp.topMargin = UI.dp(this, 10);
        card.setLayoutParams(lp);
        return card;
    }

    private void renderAll(List<GitHubApi.Entry> files) {
        body.removeAllViews();
        if (subTitle != null) subTitle.setText(files.size() + " 篇");

        final Map<String, List<GitHubApi.Entry>> groups = new LinkedHashMap<String, List<GitHubApi.Entry>>();
        for (GitHubApi.Entry e : files) {
            String dir = e.path.contains("/") ? e.path.substring(0, e.path.lastIndexOf('/')) : "根目录";
            List<GitHubApi.Entry> list = groups.get(dir);
            if (list == null) {
                list = new ArrayList<GitHubApi.Entry>();
                groups.put(dir, list);
            }
            list.add(e);
        }

        final String humanRoot = Store.humanRoot(this);
        List<String> keys = new ArrayList<String>(groups.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                boolean ah = a.equals(humanRoot) || a.startsWith(humanRoot + "/");
                boolean bh = b.equals(humanRoot) || b.startsWith(humanRoot + "/");
                if (ah != bh) return ah ? -1 : 1;
                return a.compareTo(b);
            }
        });

        for (String key : keys) {
            List<GitHubApi.Entry> list = groups.get(key);
            body.addView(sectionLabel(prettyDir(key) + "  ·  " + list.size()));

            LinearLayout card = UI.row(this, false);
            card.setBackground(UI.roundRect(this, Colors.surface(dark), 14, Colors.line(dark), 0.8f));
            card.setClipToOutline(true);
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) card.addView(UI.divider(this, dark));
                card.addView(fileRow(list.get(i), true));
            }
            body.addView(cardWithMargin(card));
        }
    }

    private View cardWithMargin(LinearLayout card) {
        LinearLayout.LayoutParams lp = UI.lp(MATCH, WRAP);
        lp.leftMargin = UI.dp(this, 12);
        lp.rightMargin = UI.dp(this, 12);
        card.setLayoutParams(lp);
        return card;
    }

    private GitHubApi.Entry digestOf(List<GitHubApi.Entry> files) {
        String dir = Store.agentRoot(this) + "/回顾/";
        GitHubApi.Entry best = null;
        for (GitHubApi.Entry e : files) {
            if (!e.path.startsWith(dir)) continue;
            if (Store.isDigestAcked(this, digestBase(e.path))) continue;
            if (best == null || e.path.compareTo(best.path) > 0) best = e;
        }
        return best;
    }

    private List<GitHubApi.Entry> recentOf(List<GitHubApi.Entry> files, int n) {
        List<GitHubApi.Entry> out = new ArrayList<GitHubApi.Entry>();
        // 先按真实「打开过」的记录
        for (String path : Store.recentPaths(this, n)) {
            for (GitHubApi.Entry e : files) {
                if (e.path.equals(path)) {
                    out.add(e);
                    break;
                }
            }
            if (out.size() >= n) break;
        }
        // 不够就用最新文件补齐
        if (out.size() < n) {
            for (GitHubApi.Entry e : files) {
                if (e.path.contains("/回顾/")) continue;
                if (out.contains(e)) continue;
                out.add(e);
                if (out.size() >= n) break;
            }
        }
        return out;
    }

    private View digestCard(final GitHubApi.Entry digest, final List<GitHubApi.Entry> all) {
        LinearLayout card = UI.row(this, false);
        card.setBackground(UI.roundRect(this, Colors.accentSoft(this, dark), 14,
                Colors.accent(this), 1.2f));
        card.setPadding(UI.dp(this, 16), UI.dp(this, 16), UI.dp(this, 16), UI.dp(this, 14));

        LinearLayout top = UI.row(this, true);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(UI.icon(this, R.drawable.ic_history, 18, Colors.accent(this)));

        LinearLayout titles = UI.row(this, false);
        TextView t = UI.text(this, "本周回顾", 15.5f, Colors.accent(this), Typeface.BOLD);
        TextView s = UI.text(this, digestBase(digest.path), 11.5f, Colors.text3(dark));
        LinearLayout.LayoutParams slp = UI.lp(WRAP, WRAP);
        slp.topMargin = UI.dp(this, 3);
        titles.addView(t);
        titles.addView(s, slp);
        LinearLayout.LayoutParams tlp = UI.lp(MATCH, WRAP, 1f);
        tlp.leftMargin = UI.dp(this, 10);
        top.addView(titles, tlp);

        card.addView(top);

        TextView btn = UI.text(this, "查看周报", 14f, 0xFFFFFFFF, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(UI.roundRect(this, Colors.accent(this), 10));
        btn.setPadding(0, UI.dp(this, 11), 0, UI.dp(this, 11));
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDigest(digest.path);
            }
        });

        TextView ack = UI.text(this, "签收", 14f, Colors.accent(this), Typeface.BOLD);
        ack.setGravity(Gravity.CENTER);
        ack.setBackground(UI.roundRect(this, Colors.surface(dark), 10,
                Colors.accent(this), 1f));
        ack.setPadding(0, UI.dp(this, 11), 0, UI.dp(this, 11));
        ack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Sheet.showOptions(MainActivity.this, dark, "签收这份周报？",
                        "签收后首页不再提醒，之后仍可以在「查看周报」里翻回来",
                        new String[]{"确认签收"},
                        new Sheet.OnPick() {
                            @Override
                            public void onPick(int index) {
                                Store.ackDigest(MainActivity.this, digestBase(digest.path));
                                Banner.show(MainActivity.this, dark, "已签收，首页不再提醒", false);
                                if (lastFiles != null) renderHome(lastFiles);
                            }
                        });
            }
        });

        LinearLayout btns = UI.row(this, true);
        LinearLayout.LayoutParams b1 = UI.lp(0, WRAP, 1f);
        b1.rightMargin = UI.dp(this, 9);
        btn.setLayoutParams(b1);
        ack.setLayoutParams(UI.lp(0, WRAP, 0.55f));
        btns.addView(btn);
        btns.addView(ack);

        LinearLayout.LayoutParams blp = UI.lp(MATCH, WRAP);
        blp.topMargin = UI.dp(this, 14);
        card.addView(btns, blp);

        return cardWithMargin(card);
    }

    private String digestBase(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.toLowerCase().endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }

    private void openDigest(final String path) {
        String token = Store.token(this);
        Banner.show(this, dark, "正在读取周报…", true);
        GitHubApi.readFile(Store.repo(this), Store.branch(this), path, token,
                new GitHubApi.Cb<GitHubApi.Doc>() {
                    @Override
                    public void ok(GitHubApi.Doc doc) {
                        Banner.hide(MainActivity.this);
                        Intent i = new Intent(MainActivity.this, PreviewActivity.class);
                        i.putExtra("content", doc.text);
                        i.putExtra("title", digestBase(path));
                        startActivity(i);
                        overridePendingTransition(R.anim.enter, R.anim.exit);
                    }

                    @Override
                    public void err(String message) {
                        Banner.hide(MainActivity.this);
                        Banner.show(MainActivity.this, dark, message, false);
                    }
                });
    }

    private View emptyCard() {
        LinearLayout card = UI.row(this, false);
        card.setBackground(UI.roundRect(this, Colors.surface(dark), 14, Colors.line(dark), 0.8f));
        card.setPadding(UI.dp(this, 18), UI.dp(this, 22), UI.dp(this, 18), UI.dp(this, 22));
        TextView t = UI.text(this, "还没有想法，点右下角 + 开始", 13f, Colors.text3(dark));
        t.setGravity(Gravity.CENTER);
        card.addView(t);
        return cardWithMargin(card);
    }

    private View seeAllButton(int total) {
        TextView btn = UI.text(this, "查看全部 " + total + " 篇  ›", 14f, Colors.text2(dark), Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(UI.roundRect(this, Colors.surface(dark), 12, Colors.line(dark), 0.8f));
        btn.setPadding(0, UI.dp(this, 13), 0, UI.dp(this, 13));
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, MainActivity.class);
                i.putExtra("mode", "all");
                startActivity(i);
                overridePendingTransition(R.anim.enter, R.anim.exit);
            }
        });
        UI.press(btn);
        LinearLayout.LayoutParams lp = UI.lp(MATCH, WRAP);
        lp.leftMargin = UI.dp(this, 12);
        lp.rightMargin = UI.dp(this, 12);
        lp.topMargin = UI.dp(this, 16);
        btn.setLayoutParams(lp);
        return btn;
    }

    // ── 行 ────────────────────────────────────────────────
    private View fileRow(final GitHubApi.Entry e, boolean withMenu) {
        LinearLayout row = UI.row(this, true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UI.dp(this, 14), UI.dp(this, 13), UI.dp(this, 14), UI.dp(this, 13));
        row.setMinimumHeight(UI.dp(this, 62));
        row.setBackgroundColor(Colors.surface(dark));

        row.addView(UI.icon(this, R.drawable.ic_file_text, 20, Colors.text3(dark)), leftIconLp());

        String name = e.path.substring(e.path.lastIndexOf('/') + 1);
        if (name.toLowerCase().endsWith(".md")) name = name.substring(0, name.length() - 3);
        String dir = e.path.contains("/") ? e.path.substring(0, e.path.lastIndexOf('/')) : "";

        LinearLayout mid = UI.row(this, false);
        mid.addView(UI.text(this, name, 15f, textColor));
        TextView meta = UI.text(this, dir + " · " + sizeLabel(e.size), 12f, Colors.text3(dark));
        meta.setPadding(0, UI.dp(this, 3), 0, 0);
        mid.addView(meta);
        row.addView(mid, UI.lp(MATCH, WRAP, 1f));

        if (withMenu && !Store.isDemo(this)) {
            FrameLayout more = new FrameLayout(this);
            more.setBackground(UI.roundRect(this, 0x00000000, 8));
            ImageView moreIcon = UI.icon(this, R.drawable.ic_ellipsis_vertical, 18, Colors.text3(dark));
            more.addView(moreIcon, new FrameLayout.LayoutParams(UI.dp(this, 18), UI.dp(this, 18), Gravity.CENTER));
            more.setLayoutParams(UI.lp(UI.dp(this, 34), UI.dp(this, 34)));
            more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showFileActions(e);
                }
            });
            row.addView(more);
        } else {
            row.addView(UI.icon(this, R.drawable.ic_chevron_right, 17, Colors.text3(dark)));
        }

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Store.isDemo(MainActivity.this)) {
                    Intent pi = new Intent(MainActivity.this, PreviewActivity.class);
                    pi.putExtra("content", Demo.content(e.path));
                    pi.putExtra("title", e.path.substring(e.path.lastIndexOf('/') + 1));
                    startActivity(pi);
                    overridePendingTransition(R.anim.enter, R.anim.exit);
                } else {
                    openEditor(e.path);
                }
            }
        });
        if (withMenu) {
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showFileActions(e);
                    return true;
                }
            });
        }
        UI.press(row);
        return row;
    }

    private LinearLayout.LayoutParams leftIconLp() {
        LinearLayout.LayoutParams p = UI.lp(UI.dp(this, 20), UI.dp(this, 20));
        p.rightMargin = UI.dp(this, 12);
        return p;
    }

    private String sizeLabel(long size) {
        if (size < 1024) return size + " B";
        return String.format(java.util.Locale.US, "%.1f KB", size / 1024.0);
    }

    private View sectionLabel(String s) {
        TextView t = UI.text(this, s, 11.5f, Colors.text3(dark));
        t.setLetterSpacing(0.05f);
        t.setPadding(UI.dp(this, 22), UI.dp(this, 18), UI.dp(this, 22), UI.dp(this, 7));
        return t;
    }

    private String prettyDir(String dir) {
        String human = Store.humanRoot(this);
        String agent = Store.agentRoot(this);
        if ("根目录".equals(dir)) return "根目录";
        if (dir.equals(human)) return "我的想法";
        if (dir.startsWith(human + "/")) return "我的想法 / " + dir.substring(human.length() + 1);
        if (dir.equals(agent)) return "DeepSeek 产出";
        if (dir.startsWith(agent + "/")) return "DeepSeek / " + dir.substring(agent.length() + 1);
        return dir;
    }

    // ── 文件操作 ──────────────────────────────────────────
    private void showMore() {
        Sheet.showOptions(this, dark, "更多", null,
                new String[]{"新建文件夹", "新建想法", "生成回顾", "查看周报", "刷新列表"},
                new Sheet.OnPick() {
                    @Override
                    public void onPick(int index) {
                        if (index == 0) newFolderDialog();
                        else if (index == 1) openEditor(null);
                        else if (index == 2) generateSummary();
                        else if (index == 3) startActivity(new Intent(MainActivity.this, DigestActivity.class));
                        else load();
                    }
                });
    }

    // ── 手动生成回顾 ──────────────────────────────────────
    private void generateSummary() {
        final String token = Store.token(this);
        if (token.isEmpty()) {
            toast("先去设置里填令牌");
            return;
        }
        final AiClient.Model model = AiClient.currentModel(this);
        if (AiClient.keyFor(this, model.provider).isEmpty()) {
            toast("要先配 " + (model.isZhipu() ? "智谱" : "DeepSeek") + " 的 Key");
            return;
        }
        Banner.show(this, dark, "正在读取最近的想法…", true);

        GitHubApi.listMarkdown(Store.repo(this), Store.branch(this), token,
                new GitHubApi.Cb<List<GitHubApi.Entry>>() {
                    @Override
                    public void ok(List<GitHubApi.Entry> files) {
                        List<GitHubApi.Entry> pool = new ArrayList<GitHubApi.Entry>();
                        for (GitHubApi.Entry e : files) {
                            if (e.path.contains("/回顾/")) continue;
                            pool.add(e);
                            if (pool.size() >= 12) break;
                        }
                        if (pool.isEmpty()) {
                            Banner.hide(MainActivity.this);
                            toast("仓库里还没有内容");
                            return;
                        }
                        collect(pool, 0, new StringBuilder(), model, token);
                    }

                    @Override
                    public void err(String message) {
                        Banner.hide(MainActivity.this);
                        toast(message);
                    }
                });
    }

    private void collect(final List<GitHubApi.Entry> pool, final int idx, final StringBuilder sb,
                         final AiClient.Model model, final String token) {
        if (idx >= pool.size()) {
            callSummary(sb.toString(), pool.size(), model, token);
            return;
        }
        GitHubApi.readFile(Store.repo(this), Store.branch(this), pool.get(idx).path, token,
                new GitHubApi.Cb<GitHubApi.Doc>() {
                    @Override
                    public void ok(GitHubApi.Doc doc) {
                        String t = doc.text;
                        if (t.length() > 3000) t = t.substring(0, 3000) + "\n…（截断）";
                        sb.append("### ").append(pool.get(idx).path).append("\n\n").append(t).append("\n\n");
                        collect(pool, idx + 1, sb, model, token);
                    }

                    @Override
                    public void err(String message) {
                        collect(pool, idx + 1, sb, model, token);
                    }
                });
    }

    private void callSummary(String context, int count, AiClient.Model model, final String token) {
        Banner.show(this, dark, "正在生成回顾…", true);

        JSONArray msgs = new JSONArray();
        try {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", "你是个人想法仓库的回顾助手。根据这些记录写一份中文周报，Markdown 格式，结构固定："
                    + "## 这周在想什么（2-4 条主题归纳）、## 值得继续推进的（最多 3 条，说明理由）、"
                    + "## 停在半路的（只有开头没有结论的）、## 下周建议（一条具体动作）。"
                    + "只基于给定内容，不要编造，控制在 500 字内。不要输出 # 一级标题。");
            msgs.put(sys);
            JSONObject usr = new JSONObject();
            usr.put("role", "user");
            usr.put("content", "共 " + count + " 篇：\n\n" + context);
            msgs.put(usr);
        } catch (Exception ignored) {
            // 组装失败就用空的
        }

        AiClient.chat(this, model, AiClient.EFFORT_OFF, msgs, new AiClient.Cb() {
            @Override
            public void ok(String content, String reasoning, long elapsedMs) {
                String week = new java.text.SimpleDateFormat("yyyy-'W'ww", java.util.Locale.US)
                        .format(new java.util.Date());
                String path = Store.agentRoot(MainActivity.this) + "/回顾/" + week + ".md";
                String body = "# 每周回顾 · " + week + "\n\n> 手动生成 · "
                        + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                        .format(new java.util.Date()) + "\n\n" + content.trim() + "\n";

                GitHubApi.writeFile(Store.repo(MainActivity.this), Store.branch(MainActivity.this),
                        path, body, null, "docs: 手动生成回顾 " + week, token,
                        new GitHubApi.Cb<GitHubApi.PutResult>() {
                            @Override
                            public void ok(GitHubApi.PutResult r) {
                                Banner.hide(MainActivity.this);
                                Banner.show(MainActivity.this, dark, "回顾已生成，去看周报吧", false);
                                load();
                            }

                            @Override
                            public void err(String message) {
                                Banner.hide(MainActivity.this);
                                toast(message);
                            }
                        });
            }

            @Override
            public void err(String message) {
                Banner.hide(MainActivity.this);
                toast(message);
            }
        });
    }

    private void newFolderDialog() {
        Sheet.Form f = new Sheet.Form();
        f.title = "新建文件夹";
        f.label = "文件夹名称";
        f.hint = "例如：设计";
        f.onSubmit = new Sheet.OnForm() {
            @Override
            public void onSubmit(String value, String extra) {
                createFolder(value);
            }
        };
        Sheet.showForm(this, dark, f);
    }

    private void createFolder(String name) {
        final String clean = name.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        if (clean.isEmpty()) {
            toast("名字不能为空");
            return;
        }
        String token = Store.token(this);
        if (token.isEmpty()) {
            toast("先去设置里填令牌");
            return;
        }
        String dir = Store.humanRoot(this) + "/" + clean;
        String readme = "# " + clean + "\n\n这里放「" + clean + "」这个分类的想法。\n";
        toastBusy("正在创建文件夹…");
        GitHubApi.writeFile(Store.repo(this), Store.branch(this), dir + "/README.md", readme, null,
                "docs: 新建文件夹 " + clean, token, new GitHubApi.Cb<GitHubApi.PutResult>() {
                    @Override
                    public void ok(GitHubApi.PutResult r) {
                        toast("已创建 " + clean + "/");
                        load();
                    }

                    @Override
                    public void err(String message) {
                        toast(message);
                    }
                });
    }

    private void showFileActions(final GitHubApi.Entry e) {
        String base = baseName(e.path);
        if (base.toLowerCase().endsWith(".md")) base = base.substring(0, base.length() - 3);
        Sheet.showOptions(this, dark, base, e.path,
                new String[]{"和 AI 聊聊这篇", "重命名", "移动到…", "删除"},
                new Sheet.OnPick() {
                    @Override
                    public void onPick(int index) {
                        if (index == 0) chatAbout(e);
                        else if (index == 1) renameDialog(e);
                        else if (index == 2) moveDialog(e);
                        else deleteConfirm(e);
                    }
                });
    }

    private void chatAbout(GitHubApi.Entry e) {
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("contextPath", e.path);
        startActivity(i);
        overridePendingTransition(R.anim.enter, R.anim.exit);
    }

    private void moveDialog(final GitHubApi.Entry e) {
        final List<String> dirs = collectDirs();
        Sheet.showOptions(this, dark, "移动到", e.path,
                dirs.toArray(new String[0]),
                new Sheet.OnPick() {
                    @Override
                    public void onPick(int index) {
                        String dir = dirs.get(index);
                        String target = dir.isEmpty() ? baseName(e.path) : dir + "/" + baseName(e.path);
                        if (target.equals(e.path)) {
                            toast("已经在这个文件夹里了");
                            return;
                        }
                        transfer(e, target, "移动");
                    }
                });
    }

    private List<String> collectDirs() {
        List<String> out = new ArrayList<String>();
        out.add(Store.humanRoot(this));
        if (lastFiles != null) {
            for (int i = 0; i < lastFiles.size(); i++) {
                String p = lastFiles.get(i).path;
                String dir = p.contains("/") ? p.substring(0, p.lastIndexOf('/')) : "";
                if (!dir.isEmpty() && !out.contains(dir) && !dir.startsWith(".versions")) {
                    out.add(dir);
                }
            }
        }
        Collections.sort(out);
        return out;
    }

    private void renameDialog(final GitHubApi.Entry e) {
        String base = baseName(e.path);
        if (base.toLowerCase().endsWith(".md")) base = base.substring(0, base.length() - 3);

        Sheet.Form f = new Sheet.Form();
        f.title = "重命名";
        f.label = "新名字";
        f.hint = "可以带子目录，例如 设计/新名字";
        f.initial = base;
        f.onSubmit = new Sheet.OnForm() {
            @Override
            public void onSubmit(String value, String extra) {
                doRename(e, value);
            }
        };
        Sheet.showForm(this, dark, f);
    }

    private void doRename(final GitHubApi.Entry e, String input) {
        String name = input == null ? "" : input.trim();
        if (name.isEmpty()) {
            toast("名字不能为空");
            return;
        }
        String dir = e.path.contains("/") ? e.path.substring(0, e.path.lastIndexOf('/')) : "";
        String target;
        if (name.contains("/")) {
            target = Store.humanRoot(this) + "/" + cleanRelative(name) + ".md";
        } else {
            target = (dir.isEmpty() ? "" : dir + "/") + safeSegment(name) + ".md";
        }
        if (target.equals(e.path)) return;
        transfer(e, target, "重命名");
    }

    /** 读原文 → 写新路径 → 删旧文件。重命名和移动共用。 */
    private void transfer(final GitHubApi.Entry e, final String target, final String action) {
        final String token = Store.token(this);
        final String repo = Store.repo(this);
        final String branch = Store.branch(this);
        toastBusy("正在" + action + "…");

        GitHubApi.readFile(repo, branch, e.path, token, new GitHubApi.Cb<GitHubApi.Doc>() {
            @Override
            public void ok(final GitHubApi.Doc doc) {
                GitHubApi.writeFile(repo, branch, target, doc.text, null,
                        "docs: " + action + " " + baseName(e.path), token,
                        new GitHubApi.Cb<GitHubApi.PutResult>() {
                            @Override
                            public void ok(GitHubApi.PutResult r) {
                                GitHubApi.deleteFile(repo, branch, e.path, doc.sha,
                                        "docs: 移出旧文件 " + baseName(e.path), token,
                                        new GitHubApi.Cb<String>() {
                                            @Override
                                            public void ok(String x) {
                                                toast("已" + action + "：" + target);
                                                load();
                                            }

                                            @Override
                                            public void err(String message) {
                                                toast("新文件已建好，旧文件没删掉：" + message);
                                                load();
                                            }
                                        });
                            }

                            @Override
                            public void err(String message) {
                                toast(message);
                            }
                        });
            }

            @Override
            public void err(String message) {
                toast(message);
            }
        });
    }

    private void deleteConfirm(final GitHubApi.Entry e) {
        Sheet.showOptions(this, dark, "删除这个想法？",
                e.path + "\nGitHub 提交历史里仍可找回",
                new String[]{"确认删除"},
                new Sheet.OnPick() {
                    @Override
                    public void onPick(int index) {
                        doDelete(e);
                    }
                });
    }

    private void doDelete(final GitHubApi.Entry e) {
        final String token = Store.token(this);
        final String repo = Store.repo(this);
        final String branch = Store.branch(this);
        toastBusy("正在删除…");
        GitHubApi.readFile(repo, branch, e.path, token, new GitHubApi.Cb<GitHubApi.Doc>() {
            @Override
            public void ok(GitHubApi.Doc doc) {
                GitHubApi.deleteFile(repo, branch, e.path, doc.sha, "docs: 删除 " + baseName(e.path), token,
                        new GitHubApi.Cb<String>() {
                            @Override
                            public void ok(String x) {
                                toast("已删除");
                                load();
                            }

                            @Override
                            public void err(String message) {
                                toast(message);
                            }
                        });
            }

            @Override
            public void err(String message) {
                toast(message);
            }
        });
    }

    private String baseName(String p) {
        return p.substring(p.lastIndexOf('/') + 1);
    }

    private String safeSegment(String s) {
        String x = s.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        return x.isEmpty() ? "未命名" : x;
    }

    private String cleanRelative(String s) {
        String[] segs = s.split("/");
        StringBuilder sb = new StringBuilder();
        for (String seg : segs) {
            String x = safeSegment(seg);
            if (x.isEmpty()) continue;
            if (sb.length() > 0) sb.append('/');
            sb.append(x);
        }
        return sb.length() == 0 ? "未命名" : sb.toString();
    }

    // ── 杂项 ──────────────────────────────────────────────
    private void openEditor(String path) {
        if (path != null) Store.touchRecent(this, path);
        Intent i = new Intent(this, EditorActivity.class);
        if (path != null) i.putExtra("path", path);
        startActivityForResult(i, REQ_EDITOR);
        overridePendingTransition(R.anim.enter, R.anim.exit);
    }

    private void toast(String s) {
        Banner.show(this, dark, s, false);
    }

    private void toastBusy(String s) {
        Banner.show(this, dark, s, true);
    }

    private void showMessage(String text, String action, View.OnClickListener l) {
        LinearLayout box = UI.row(this, false);
        box.setGravity(Gravity.CENTER);
        box.setPadding(UI.dp(this, 30), UI.dp(this, 70), UI.dp(this, 30), UI.dp(this, 30));

        TextView t = UI.text(this, text, 14f, Colors.text3(dark));
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(UI.dp(this, 4), 1f);
        box.addView(t);

        if (action != null) {
            TextView btn = UI.text(this, action, 14f, Colors.accent(this), Typeface.BOLD);
            btn.setGravity(Gravity.CENTER);
            btn.setBackground(UI.roundRect(this, Colors.accentSoft(this, dark), 10));
            btn.setPadding(UI.dp(this, 18), UI.dp(this, 9), UI.dp(this, 18), UI.dp(this, 9));
            LinearLayout.LayoutParams blp = UI.lp(WRAP, WRAP);
            blp.topMargin = UI.dp(this, 16);
            btn.setLayoutParams(blp);
            btn.setOnClickListener(l);
            box.addView(btn);
        }
        body.addView(box);
    }

    private View iconButton(int res, View.OnClickListener l) {
        FrameLayout box = new FrameLayout(this);
        box.setBackground(UI.roundRect(this, 0x00000000, 10));
        ImageView ic = UI.icon(this, res, 20, Colors.text2(dark));
        box.addView(ic, new FrameLayout.LayoutParams(UI.dp(this, 20), UI.dp(this, 20), Gravity.CENTER));
        box.setLayoutParams(UI.lp(UI.dp(this, 38), UI.dp(this, 38)));
        box.setOnClickListener(l);
        UI.press(box);
        return box;
    }

    private View buildFab() {
        FrameLayout fab = new FrameLayout(this);
        fab.setBackground(UI.circle(this, Colors.accent(this)));
        fab.setElevation(UI.dp(this, 6));
        ImageView ic = UI.icon(this, R.drawable.ic_plus, 24, 0xFFFFFFFF);
        fab.addView(ic, new FrameLayout.LayoutParams(UI.dp(this, 24), UI.dp(this, 24), Gravity.CENTER));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(UI.dp(this, 56), UI.dp(this, 56));
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.rightMargin = UI.dp(this, 20);
        lp.bottomMargin = UI.dp(this, 26);
        fab.setLayoutParams(lp);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Store.isDemo(MainActivity.this)) {
                    Banner.show(MainActivity.this, dark, "演示模式不能新建。配置自己的仓库后就能写了", false);
                    return;
                }
                openEditor(null);
            }
        });
        UI.press(fab);
        return fab;
    }
}
