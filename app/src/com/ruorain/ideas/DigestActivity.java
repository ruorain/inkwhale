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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 周报列表：Deepseek/回顾/ 下的所有 md */
public class DigestActivity extends BaseActivity {

    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    private LinearLayout body;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(build());
        load();
    }

    private View build() {
        LinearLayout col = UI.row(this, false);
        col.setBackgroundColor(Colors.bg(dark));

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
        titles.addView(UI.text(this, "周报", 22f, Colors.text(dark), Typeface.BOLD));
        TextView sub = UI.text(this, "DeepSeek 定期汇总", 12f, Colors.text3(dark));
        LinearLayout.LayoutParams slp = UI.lp(WRAP, WRAP);
        slp.topMargin = UI.dp(this, 3);
        titles.addView(sub, slp);
        bar.addView(titles, UI.lp(MATCH, WRAP, 1f));

        bar.addView(iconButton(R.drawable.ic_refresh_cw, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        }));
        col.addView(bar);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        body = UI.row(this, false);
        body.setPadding(0, 0, 0, UI.dp(this, 30));
        sv.addView(body);
        col.addView(sv, UI.lp(MATCH, 0, 1f));
        return col;
    }

    private void load() {
        if (loading) return;
        String token = Store.token(this);
        if (token.isEmpty()) {
            showMessage("先去设置里填令牌", null, null);
            return;
        }
        loading = true;
        body.removeAllViews();
        showMessage("正在读取…", null, null);

        GitHubApi.listMarkdown(Store.repo(this), Store.branch(this), token,
                new GitHubApi.Cb<List<GitHubApi.Entry>>() {
                    @Override
                    public void ok(List<GitHubApi.Entry> files) {
                        loading = false;
                        render(files);
                    }

                    @Override
                    public void err(String message) {
                        loading = false;
                        body.removeAllViews();
                        showMessage(message, "重试", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                load();
                            }
                        });
                    }
                });
    }

    private void render(List<GitHubApi.Entry> files) {
        body.removeAllViews();
        String prefix = Store.agentRoot(this) + "/回顾/";
        List<GitHubApi.Entry> list = new ArrayList<GitHubApi.Entry>();
        for (GitHubApi.Entry e : files) {
            if (e.path.startsWith(prefix)) list.add(e);
        }
        Collections.sort(list, new java.util.Comparator<GitHubApi.Entry>() {
            @Override
            public int compare(GitHubApi.Entry a, GitHubApi.Entry b) {
                return b.path.compareTo(a.path);
            }
        });

        if (list.isEmpty()) {
            showMessage("还没有周报\n\n在仓库里配置 GitHub Actions 后，每周会自动生成到 " + prefix, null, null);
            return;
        }

        TextView label = UI.text(this, "共 " + list.size() + " 期", 11.5f, Colors.text3(dark));
        label.setPadding(UI.dp(this, 22), UI.dp(this, 10), UI.dp(this, 22), UI.dp(this, 7));
        body.addView(label);

        LinearLayout card = UI.row(this, false);
        card.setBackground(UI.roundRect(this, Colors.surface(dark), 14, Colors.line(dark), 0.8f));
        card.setClipToOutline(true);
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) card.addView(UI.divider(this, dark));
            card.addView(row(list.get(i)));
        }
        LinearLayout.LayoutParams lp = UI.lp(MATCH, WRAP);
        lp.leftMargin = UI.dp(this, 12);
        lp.rightMargin = UI.dp(this, 12);
        card.setLayoutParams(lp);
        body.addView(card);
    }

    private View row(final GitHubApi.Entry e) {
        LinearLayout row = UI.row(this, true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UI.dp(this, 14), UI.dp(this, 15), UI.dp(this, 14), UI.dp(this, 15));
        row.setBackgroundColor(Colors.surface(dark));

        row.addView(UI.icon(this, R.drawable.ic_history, 20, Colors.accent(this)), leftIconLp());

        String name = e.path.substring(e.path.lastIndexOf('/') + 1);
        if (name.toLowerCase().endsWith(".md")) name = name.substring(0, name.length() - 3);

        LinearLayout mid = UI.row(this, false);
        mid.addView(UI.text(this, name, 15f, Colors.text(dark)));
        TextView meta = UI.text(this, sizeLabel(e.size), 12f, Colors.text3(dark));
        meta.setPadding(0, UI.dp(this, 3), 0, 0);
        mid.addView(meta);
        row.addView(mid, UI.lp(MATCH, WRAP, 1f));

        row.addView(UI.icon(this, R.drawable.ic_chevron_right, 17, Colors.text3(dark)));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(e.path);
            }
        });
        UI.press(row);
        return row;
    }

    private void open(final String path) {
        String token = Store.token(this);
        Banner.show(this, dark, "正在读取周报…", true);
        GitHubApi.readFile(Store.repo(this), Store.branch(this), path, token,
                new GitHubApi.Cb<GitHubApi.Doc>() {
                    @Override
                    public void ok(GitHubApi.Doc doc) {
                        Banner.hide(DigestActivity.this);
                        Intent i = new Intent(DigestActivity.this, PreviewActivity.class);
                        i.putExtra("content", doc.text);
                        i.putExtra("title", path.substring(path.lastIndexOf('/') + 1));
                        startActivity(i);
                        overridePendingTransition(R.anim.enter, R.anim.exit);
                    }

                    @Override
                    public void err(String message) {
                        Banner.hide(DigestActivity.this);
                        Banner.show(DigestActivity.this, dark, message, false);
                    }
                });
    }

    private String sizeLabel(long size) {
        if (size < 1024) return size + " B";
        return String.format(java.util.Locale.US, "%.1f KB", size / 1024.0);
    }

    private LinearLayout.LayoutParams leftIconLp() {
        LinearLayout.LayoutParams p = UI.lp(UI.dp(this, 20), UI.dp(this, 20));
        p.rightMargin = UI.dp(this, 12);
        return p;
    }

    private void showMessage(String text, String action, View.OnClickListener l) {
        LinearLayout box = UI.row(this, false);
        box.setGravity(Gravity.CENTER);
        box.setPadding(UI.dp(this, 30), UI.dp(this, 70), UI.dp(this, 30), UI.dp(this, 30));
        TextView t = UI.text(this, text, 13.5f, Colors.text3(dark));
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
}
