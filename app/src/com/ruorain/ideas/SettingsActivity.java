package com.ruorain.ideas;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SettingsActivity extends BaseActivity {

    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    private LinearLayout body;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(build());
    }

    private View build() {
        LinearLayout col = UI.row(this, false);
        col.setBackgroundColor(Colors.bg(dark));
        col.addView(buildBar());

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        body = UI.row(this, false);
        body.setPadding(0, 0, 0, UI.dp(this, 30));
        sv.addView(body);
        col.addView(sv, UI.lp(MATCH, 0, 1f));

        rebuildBody();
        return col;
    }

    private View buildBar() {
        LinearLayout bar = UI.row(this, true);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UI.dp(this, 8), UI.dp(this, 14), UI.dp(this, 8), UI.dp(this, 10));

        bar.addView(iconButton(R.drawable.ic_arrow_left, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(R.anim.back_in, R.anim.back_out);
            }
        }));

        LinearLayout titles = UI.row(this, false);
        TextView h1 = UI.text(this, "设置", 27f, Colors.text(dark), Typeface.BOLD);
        titles.addView(h1);
        TextView sub = UI.text(this, Store.repo(this), 12.5f, Colors.text3(dark));
        LinearLayout.LayoutParams subLp = UI.lp(WRAP, WRAP);
        subLp.topMargin = UI.dp(this, 4);
        titles.addView(sub, subLp);
        bar.addView(titles, UI.lp(MATCH, WRAP, 1f));
        return bar;
    }

    private void rebuildBody() {
        body.removeAllViews();

        body.addView(sectionLabel("仓库"));
        body.addView(card(
                row(R.drawable.ic_git_branch, "仓库", Store.repo(this), new Runnable() {
                    @Override
                    public void run() {
                        prompt("仓库", "账号/仓库名，例如 ruorain/ideas", Store.repo(SettingsActivity.this), "repo");
                    }
                }),
                row(R.drawable.ic_folder_tree, "分支", Store.branch(this), new Runnable() {
                    @Override
                    public void run() {
                        prompt("分支", "main", Store.branch(SettingsActivity.this), "branch");
                    }
                }),
                row(R.drawable.ic_lock, "访问令牌", tokenLabel(), new Runnable() {
                    @Override
                    public void run() {
                        promptToken();
                    }
                })));

        body.addView(sectionLabel("AI"));
        body.addView(card(
                row(R.drawable.ic_send, "DeepSeek API Key", deepseekLabel(), new Runnable() {
                    @Override
                    public void run() {
                        promptDeepSeek();
                    }
                }),
                row(R.drawable.ic_send, "智谱 API Key", zhipuLabel(), new Runnable() {
                    @Override
                    public void run() {
                        promptZhipu();
                    }
                }),
                row(0, "当前模型", AiClient.currentModel(this).label, null)));

        body.addView(sectionLabel("自动化"));
        body.addView(card(
                row(R.drawable.ic_git_branch, "自动周报", "部署到你的仓库", new Runnable() {
                    @Override
                    public void run() {
                        confirmDeployCi();
                    }
                })));

        body.addView(sectionLabel("外观"));
        body.addView(themeCard());
        body.addView(accentCard());

        body.addView(sectionLabel("关于"));
        body.addView(card(
                row(0, "版本", "3.5.0 (30)", null),
                row(R.drawable.ic_github, "开源地址", "github.com/ruorain/inkwhale",
                        new Runnable() {
                            @Override
                            public void run() {
                                openUrl("https://github.com/ruorain/inkwhale");
                            }
                        }),
                row(0, "B 站", "@若雨の号",
                        new Runnable() {
                            @Override
                            public void run() {
                                openUrl("https://search.bilibili.com/all?keyword=%E8%8B%A5%E9%9B%A8%E3%81%AE%E5%8F%B7");
                            }
                        }),
                row(R.drawable.ic_file_text, "使用说明", "怎么用 · 常见问题",
                        new Runnable() {
                            @Override
                            public void run() {
                                Intent i = new Intent(SettingsActivity.this, PreviewActivity.class);
                                i.putExtra("content", Help.TEXT);
                                i.putExtra("title", "使用说明");
                                startActivity(i);
                            }
                        }),
                row(0, "图标", "Lucide · ISC", null),
                row(R.drawable.ic_file_text, "DeepSeek 产出目录", Store.agentRoot(this) + "/", null),
                row(0, "退出演示模式", Store.isDemo(this) ? "演示中，点这里退出" : "已关闭",
                        new Runnable() {
                            @Override
                            public void run() {
                                Store.setDemo(SettingsActivity.this, false);
                                Store.setDone(SettingsActivity.this, false);
                                startActivity(new Intent(SettingsActivity.this, OnboardingActivity.class));
                                finish();
                            }
                        })));
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Exception e) {
            Banner.show(this, dark, "没有能打开网页的应用", false);
        }
    }

    private void confirmDeployCi() {
        Sheet.showOptions(this, dark, "启用自动周报？",
                "会在你的仓库里创建两个文件：\n"
                        + ".github/workflows/weekly.yml\n"
                        + "scripts/weekly.mjs\n\n"
                        + "之后每周日自动生成一份回顾。部署完还需要加一个 API Key 才会真的跑。",
                new String[]{"部署"},
                new Sheet.OnPick() {
                    @Override
                    public void onPick(int index) {
                        deployCi();
                    }
                });
    }

    private void deployCi() {
        Banner.show(this, dark, "正在部署…", true);
        CiDeploy.deploy(this, new CiDeploy.Cb() {
            @Override
            public void ok() {
                Banner.hide(SettingsActivity.this);
                Banner.show(SettingsActivity.this, dark, "已部署，接着去加 API Key", false);
                Intent i = new Intent(SettingsActivity.this, WebViewActivity.class);
                i.putExtra("url", "https://github.com/" + Store.repo(SettingsActivity.this)
                        + "/settings/secrets/actions/new");
                i.putExtra("title", "添加密钥");
                startActivity(i);
            }

            @Override
            public void err(String message) {
                Banner.hide(SettingsActivity.this);
                Banner.show(SettingsActivity.this, dark, message, false);
            }
        });
    }

    private String deepseekLabel() {
        String k = Store.deepseekKey(this);
        if (k == null || k.isEmpty()) return "未配置";
        return "已配置 · " + k.length() + " 字符";
    }

    private String zhipuLabel() {
        String k = Store.zhipuKey(this);
        if (k == null || k.isEmpty()) return "未配置";
        return "已配置 · " + k.length() + " 字符";
    }

    private void promptZhipu() {
        Sheet.showInput(this, dark, "智谱 API Key", "形如 xxxx.xxxx", Store.zhipuKey(this), true,
                new Sheet.OnInput() {
                    @Override
                    public void onInput(String value) {
                        Store.set(SettingsActivity.this, "zhipuKey", value);
                        rebuildBody();
                    }
                });
    }

    private void promptDeepSeek() {
        Sheet.showInput(this, dark, "DeepSeek API Key", "sk-...",
                Store.deepseekKey(this), true, new Sheet.OnInput() {
                    @Override
                    public void onInput(String value) {
                        Store.set(SettingsActivity.this, "deepseekKey", value);
                        rebuildBody();
                    }
                });
    }

    private String tokenLabel() {
        String t = Store.token(this);
        if (t == null || t.isEmpty()) return "未配置";
        return "已配置 · " + t.length() + " 字符";
    }

    private View accentCard() {
        LinearLayout l = UI.row(this, false);
        l.setBackground(UI.roundRect(this, Colors.surface(dark), 14, Colors.line(dark), 0.8f));

        LinearLayout head = UI.row(this, true);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(UI.dp(this, 14), UI.dp(this, 14), UI.dp(this, 14), UI.dp(this, 10));
        head.addView(UI.icon(this, R.drawable.ic_palette, 20, Colors.text3(dark)), leftIconLp());
        head.addView(UI.text(this, "强调色", 14.5f, Colors.text(dark)));
        l.addView(head);

        LinearLayout sw = UI.row(this, true);
        sw.setPadding(UI.dp(this, 16), UI.dp(this, 2), UI.dp(this, 16), UI.dp(this, 16));
        final int cur = Colors.accent(this);
        for (int i = 0; i < Colors.PRESETS.length; i++) {
            final int color = Colors.PRESETS[i];
            FrameLayout dot = new FrameLayout(this);
            android.graphics.drawable.GradientDrawable bg = UI.circle(this, color);
            if (color == cur) bg.setStroke(UI.dp(this, 2), Colors.text(dark));
            dot.setBackground(bg);
            LinearLayout.LayoutParams p = UI.lp(UI.dp(this, 30), UI.dp(this, 30));
            p.rightMargin = UI.dp(this, 14);
            dot.setLayoutParams(p);
            dot.setContentDescription(Colors.PRESET_NAMES[i]);
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Store.setInt(SettingsActivity.this, "accent", color);
                    recreate();
                }
            });
            sw.addView(dot);
        }
        l.addView(sw);

        LinearLayout.LayoutParams lp = UI.lp(MATCH, WRAP);
        lp.leftMargin = UI.dp(this, 12);
        lp.rightMargin = UI.dp(this, 12);
        lp.topMargin = UI.dp(this, 10);
        l.setLayoutParams(lp);
        return l;
    }

    private View themeCard() {
        LinearLayout l = UI.row(this, false);
        l.setBackground(UI.roundRect(this, Colors.surface(dark), 14, Colors.line(dark), 0.8f));

        LinearLayout head = UI.row(this, true);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(UI.dp(this, 14), UI.dp(this, 14), UI.dp(this, 14), UI.dp(this, 6));
        head.addView(UI.icon(this, R.drawable.ic_monitor, 20, Colors.text3(dark)), leftIconLp());
        head.addView(UI.text(this, "主题", 14.5f, Colors.text(dark)));
        l.addView(head);

        LinearLayout seg = UI.row(this, true);
        seg.setPadding(UI.dp(this, 14), UI.dp(this, 4), UI.dp(this, 14), UI.dp(this, 14));

        String cur = Store.prefs(this).getString("theme", "auto");
        String[] keys = {"auto", "light", "dark"};
        String[] labels = {"跟随系统", "浅色", "深色"};
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            boolean on = key.equals(cur);
            TextView btn = UI.text(this, labels[i], 12.5f,
                    on ? Colors.text(dark) : Colors.text2(dark), Typeface.NORMAL);
            btn.setGravity(Gravity.CENTER);
            btn.setBackground(UI.roundRect(this,
                    on ? Colors.surface(dark) : 0x00000000, 7));
            btn.setPadding(0, UI.dp(this, 8), 0, UI.dp(this, 8));
            LinearLayout.LayoutParams p = UI.lp(0, WRAP, 1f);
            p.rightMargin = UI.dp(this, 2);
            btn.setLayoutParams(p);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Store.set(SettingsActivity.this, "theme", key);
                    recreate();
                }
            });
            seg.addView(btn);
        }
        seg.setBackground(UI.roundRect(this, Colors.surface2(dark), 9));
        LinearLayout.LayoutParams segLp = UI.lp(MATCH, WRAP);
        segLp.leftMargin = UI.dp(this, 14);
        segLp.rightMargin = UI.dp(this, 14);
        segLp.bottomMargin = UI.dp(this, 14);
        l.addView(seg, segLp);

        LinearLayout.LayoutParams lp = UI.lp(MATCH, WRAP);
        lp.leftMargin = UI.dp(this, 12);
        lp.rightMargin = UI.dp(this, 12);
        l.setLayoutParams(lp);
        return l;
    }

    private View row(int icon, String label, String value, Runnable click) {
        LinearLayout r = UI.row(this, true);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(UI.dp(this, 14), UI.dp(this, 13), UI.dp(this, 14), UI.dp(this, 13));
        r.setMinimumHeight(UI.dp(this, 50));
        r.setBackgroundColor(Colors.surface(dark));

        if (icon != 0) r.addView(UI.icon(this, icon, 18, Colors.text3(dark)), leftIconLp());

        r.addView(UI.text(this, label, 14.5f, Colors.text(dark)), UI.lp(MATCH, WRAP, 1f));
        TextView v = UI.text(this, value, 13f, Colors.text3(dark));
        v.setTypeface(Typeface.MONOSPACE);
        r.addView(v);

        if (click != null) {
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    click.run();
                }
            });
            UI.press(r);
        }
        return r;
    }

    private void prompt(final String title, final String hint, String initial, final String key) {
        Sheet.showInput(this, dark, title, hint, initial, false, new Sheet.OnInput() {
            @Override
            public void onInput(String value) {
                Store.set(SettingsActivity.this, key, value);
                rebuildBody();
            }
        });
    }

    private void promptToken() {
        Sheet.showInput(this, dark, "访问令牌", "github_pat_...（Contents: Read and write）",
                Store.token(this), true, new Sheet.OnInput() {
                    @Override
                    public void onInput(String value) {
                        Store.set(SettingsActivity.this, "token", value);
                        rebuildBody();
                    }
                });
    }

    private View sectionLabel(String s) {
        TextView t = UI.text(this, s, 11.5f, Colors.text3(dark));
        t.setLetterSpacing(0.06f);
        t.setPadding(UI.dp(this, 22), UI.dp(this, 20), UI.dp(this, 22), UI.dp(this, 7));
        return t;
    }

    private LinearLayout card(View... children) {
        LinearLayout l = UI.row(this, false);
        l.setBackground(UI.roundRect(this, Colors.surface(dark), 14, Colors.line(dark), 0.8f));
        l.setClipToOutline(true);
        for (int i = 0; i < children.length; i++) {
            if (i > 0) l.addView(UI.divider(this, dark));
            l.addView(children[i]);
        }
        LinearLayout.LayoutParams lp = UI.lp(MATCH, WRAP);
        lp.leftMargin = UI.dp(this, 12);
        lp.rightMargin = UI.dp(this, 12);
        l.setLayoutParams(lp);
        return l;
    }

    private LinearLayout.LayoutParams leftIconLp() {
        LinearLayout.LayoutParams p = UI.lp(UI.dp(this, 18), UI.dp(this, 18));
        p.rightMargin = UI.dp(this, 12);
        return p;
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
}
