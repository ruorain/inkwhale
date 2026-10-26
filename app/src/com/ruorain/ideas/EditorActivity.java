package com.ruorain.ideas;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditorActivity extends BaseActivity {

    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;
    private static final Pattern VER = Pattern.compile("(?m)^>\\s*(V\\d+(?:\\.\\d+)*)");

    private EditText editor;
    private TextView status;
    private TextView docName;
    private TextView pathLabel;

    private String path;      // null 表示新建
    private String sha;       // null 表示新建
    private String original;  // 打开时的原文，用于归档
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        path = getIntent().getStringExtra("path");
        setContentView(build());

        if (path != null) {
            loadFile();
        } else {
            String prefill = getIntent().getStringExtra("prefill");
            if (prefill != null && !prefill.isEmpty()) preparePrefill(prefill);
            else prepareNew();
        }
    }

    // ── 构建 ──────────────────────────────────────────────
    private View build() {
        LinearLayout col = UI.row(this, false);
        col.setBackgroundColor(Colors.bg(dark));

        col.addView(buildBar());
        col.addView(buildEditor(), UI.lp(MATCH, 0, 1f));
        col.addView(buildStatus());
        col.addView(buildToolbar());
        return col;
    }

    private View buildBar() {
        LinearLayout bar = UI.row(this, true);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UI.dp(this, 8), UI.dp(this, 10), UI.dp(this, 8), UI.dp(this, 10));

        bar.addView(iconButton(R.drawable.ic_arrow_left, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(R.anim.back_in, R.anim.back_out);
            }
        }));

        LinearLayout mid = UI.row(this, false);
        mid.setGravity(Gravity.CENTER);
        docName = UI.text(this, "未命名想法.md", 14f, Colors.text(dark), Typeface.BOLD);
        docName.setMaxLines(1);
        docName.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        mid.addView(docName);
        pathLabel = UI.text(this, "新建", 11f, Colors.text3(dark));
        LinearLayout.LayoutParams plp = UI.lp(WRAP, WRAP);
        plp.topMargin = UI.dp(this, 2);
        mid.addView(pathLabel, plp);
        bar.addView(mid, UI.lp(MATCH, WRAP, 1f));

        bar.addView(iconButton(R.drawable.ic_eye, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(EditorActivity.this, PreviewActivity.class);
                i.putExtra("content", editor.getText().toString());
                i.putExtra("title", docName.getText().toString());
                startActivity(i);
                overridePendingTransition(R.anim.enter, R.anim.exit);
            }
        }));
        bar.addView(iconButton(R.drawable.ic_cloud_upload, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveDialog();
            }
        }));
        return bar;
    }

    private View buildEditor() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setClipToPadding(false);
        sv.setPadding(UI.dp(this, 20), UI.dp(this, 4), UI.dp(this, 20), UI.dp(this, 16));

        editor = new EditText(this);
        editor.setBackground(null);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13.4f);
        editor.setLineSpacing(UI.dp(this, 6), 1f);
        editor.setTextColor(Colors.text(dark));
        editor.setHintTextColor(Colors.text3(dark));
        editor.setHint("开始写…");
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(0, 0, 0, 0);
        editor.setVerticalScrollBarEnabled(false);

        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateStatus();
            }
        });

        sv.addView(editor, new ScrollView.LayoutParams(MATCH, WRAP));
        return sv;
    }

    private View buildStatus() {
        LinearLayout bar = UI.row(this, true);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UI.dp(this, 20), UI.dp(this, 8), UI.dp(this, 20), UI.dp(this, 8));
        bar.setBackgroundColor(Colors.bg(dark));

        status = UI.text(this, "", 11.5f, Colors.text3(dark));
        status.setTypeface(Typeface.MONOSPACE);
        bar.addView(status, UI.lp(MATCH, WRAP, 1f));

        TextView right = UI.text(this, sha == null ? "本地草稿" : "已同步", 11.5f, Colors.text3(dark));
        right.setTypeface(Typeface.MONOSPACE);
        bar.addView(right);
        return bar;
    }

    private void updateStatus() {
        if (status == null || editor == null) return;
        String s = editor.getText().toString();
        int chars = s.length();
        int lines = s.split("\n", -1).length;
        status.setText(chars + " 字 · " + lines + " 行");
    }

    private View buildToolbar() {
        LinearLayout wrap = UI.row(this, true);
        wrap.setPadding(UI.dp(this, 10), UI.dp(this, 8), UI.dp(this, 10), UI.dp(this, 10));
        wrap.setBackgroundColor(Colors.surface(dark));
        wrap.setMinimumHeight(UI.dp(this, 52));

        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = UI.row(this, true);
        tools.setGravity(Gravity.CENTER_VERTICAL);

        int[] icons = {
                R.drawable.ic_bold, R.drawable.ic_italic,
                R.drawable.ic_heading_1, R.drawable.ic_heading_2, R.drawable.ic_heading_3,
                R.drawable.ic_quote, R.drawable.ic_list, R.drawable.ic_list_ordered,
                R.drawable.ic_square_check, R.drawable.ic_code, R.drawable.ic_table,
                R.drawable.ic_link, R.drawable.ic_image, R.drawable.ic_minus
        };
        for (int i = 0; i < icons.length; i++) {
            FrameLayout btn = new FrameLayout(this);
            btn.setBackground(UI.roundRect(this, 0x00000000, 8));
            ImageView ic = UI.icon(this, icons[i], 19, Colors.text2(dark));
            btn.addView(ic, new FrameLayout.LayoutParams(UI.dp(this, 19), UI.dp(this, 19), Gravity.CENTER));
            btn.setLayoutParams(UI.lp(UI.dp(this, 38), UI.dp(this, 36)));
            final int res = icons[i];
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    insertMarkdown(res);
                }
            });
            tools.addView(btn);
        }

        hs.addView(tools);

        // 右侧固定区：分隔 + AI（不参与横向滚动，永远可见）
        View sep = new View(this);
        sep.setBackgroundColor(Colors.line(dark));
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(UI.dp(this, 1), UI.dp(this, 18));
        sepLp.leftMargin = UI.dp(this, 5);
        sepLp.rightMargin = UI.dp(this, 5);
        wrap.addView(sep, sepLp);

        TextView aiBtn = UI.text(this, "AI 整理", 12.5f, Colors.accent(this), Typeface.BOLD);
        aiBtn.setGravity(Gravity.CENTER);
        aiBtn.setBackground(UI.roundRect(this, Colors.accentSoft(this, dark), 8));
        aiBtn.setPadding(UI.dp(this, 10), 0, UI.dp(this, 10), 0);
        aiBtn.setLayoutParams(UI.lp(WRAP, UI.dp(this, 34)));
        aiBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmPolish();
            }
        });
        UI.press(aiBtn);
        wrap.addView(aiBtn);

        LinearLayout.LayoutParams hl = UI.lp(0, WRAP, 1f);
        wrap.addView(hs, hl);
        return wrap;
    }

    // ── DeepSeek ──────────────────────────────────────────
    /** 整理会改写正文，先让用户确认 */
    private void confirmPolish() {
        String body = editor.getText().toString().trim();
        if (body.isEmpty()) {
            Banner.show(this, dark, "还没有内容可以整理", false);
            return;
        }
        Sheet.showOptions(this, dark, "让 AI 整理这篇？",
                "会改写当前正文（加小标题、理清条理）。\n原文会存到本地草稿，写坏了可以退出不保存。",
                new String[]{"开始整理"},
                new Sheet.OnPick() {
                    @Override
                    public void onPick(int index) {
                        aiPolish();
                    }
                });
    }

    private void aiPolish() {
        String key = Store.deepseekKey(this);
        if (key.isEmpty()) {
            toast("先去设置里填 DeepSeek API Key");
            return;
        }
        final String src = editor.getText().toString();
        if (src.trim().isEmpty()) {
            toast("内容是空的");
            return;
        }
        Banner.show(this, dark, "DeepSeek 正在整理…", true);
        DeepSeek.polish(key, src, new DeepSeek.Cb() {
            @Override
            public void ok(String out) {
                Banner.hide(EditorActivity.this);
                editor.setText(out);
                editor.setSelection(0);
                updateStatus();
                Banner.show(EditorActivity.this, dark, "整理完成，记得保存", false);
            }

            @Override
            public void err(String message) {
                Banner.hide(EditorActivity.this);
                Banner.show(EditorActivity.this, dark, message, false);
            }
        });
    }

    private void insertMarkdown(int res) {
        String snippet = "**粗体**";
        if (res == R.drawable.ic_heading_1) snippet = "\n# 标题\n";
        else if (res == R.drawable.ic_heading_2) snippet = "\n## 小标题\n";
        else if (res == R.drawable.ic_heading_3) snippet = "\n### 更小标题\n";
        else if (res == R.drawable.ic_italic) snippet = "*斜体*";
        else if (res == R.drawable.ic_quote) snippet = "\n> 引用\n";
        else if (res == R.drawable.ic_list) snippet = "\n- 条目\n";
        else if (res == R.drawable.ic_list_ordered) snippet = "\n1. 条目\n";
        else if (res == R.drawable.ic_square_check) snippet = "\n- [ ] 待办\n";
        else if (res == R.drawable.ic_code) snippet = "\n```\n代码\n```\n";
        else if (res == R.drawable.ic_table) snippet = "\n| 列 | 列 |\n| --- | --- |\n| 值 | 值 |\n";
        else if (res == R.drawable.ic_link) snippet = "[链接](https://)";
        else if (res == R.drawable.ic_image) snippet = "![图片](路径)";
        else if (res == R.drawable.ic_minus) snippet = "\n---\n";

        int start = Math.max(0, editor.getSelectionStart());
        editor.getText().insert(start, snippet);
        editor.setSelection(Math.min(editor.getText().length(), start + snippet.length()));
    }

    // ── 加载 ──────────────────────────────────────────────
    private void prepareNew() {
        docName.setText("未命名想法.md");
        pathLabel.setText("新建 · ideas/");
        editor.setText("# \n\n> V0.1 · " + nowStamp() + " · raw\n\n");
        editor.setSelection(2);
        updateStatus();
    }

    private void preparePrefill(String text) {
        docName.setText("未命名想法.md");
        pathLabel.setText("新建 · ideas/");
        editor.setText(text);
        editor.setSelection(0);
        updateStatus();
    }

    private String nowStamp() {
        java.text.SimpleDateFormat f =
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US);
        return f.format(new java.util.Date());
    }

    /** 保证正文有一行 `> V0.x · 时间 · 状态`，没有就补在 H1 下面 */
    private String ensureMeta(String body) {
        if (Pattern.compile("(?m)^>\\s*V\\d+").matcher(body).find()) return body;
        String meta = "> V0.1 · " + nowStamp() + " · raw";
        String[] lines = body.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean inserted = false;
        for (String l : lines) {
            sb.append(l).append('\n');
            if (!inserted && l.trim().startsWith("# ")) {
                sb.append('\n').append(meta).append('\n');
                inserted = true;
            }
        }
        if (!inserted) sb.insert(0, meta + "\n\n");
        return sb.toString();
    }

    private void loadFile() {
        String token = Store.token(this);
        final String cached = Store.cachedFile(this, path);
        final String draft = Store.loadDraft(this, path);
        final boolean hasDraft = draft != null && !draft.isEmpty();
        final String fallback = hasDraft ? draft : cached;

        // 先立刻显示本地内容，断网也能立刻打开
        if (fallback != null && !fallback.isEmpty()) {
            editor.setText(fallback);
            editor.setSelection(0);
            updateStatus();
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        docName.setText(name);
        pathLabel.setText(path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "/");

        if (token.isEmpty()) {
            if (fallback == null || fallback.isEmpty()) toast("先去设置里填令牌");
            return;
        }

        setBusy(true, "同步中…");
        GitHubApi.readFile(Store.repo(this), Store.branch(this), path, token,
                new GitHubApi.Cb<GitHubApi.Doc>() {
                    @Override
                    public void ok(GitHubApi.Doc doc) {
                        setBusy(false, null);
                        original = doc.text;
                        sha = doc.sha;
                        Store.cacheFile(EditorActivity.this, path, doc.text);
                        // 有本地草稿就不覆盖，避免丢掉离线时写的字
                        if (!Store.hasDraft(EditorActivity.this, path)) {
                            editor.setText(doc.text);
                            editor.setSelection(0);
                        } else {
                            Banner.show(EditorActivity.this, dark, "这个文件有未同步的本地修改", false);
                        }
                        updateStatus();
                    }

                    @Override
                    public void err(String message) {
                        setBusy(false, null);
                        if (fallback != null && !fallback.isEmpty()) {
                            Banner.show(EditorActivity.this, dark, "离线：显示的是本地缓存", false);
                        } else {
                            toast(message);
                        }
                    }
                });
    }

    // ── 保存 ──────────────────────────────────────────────
    private void saveDialog() {
        String target = (path != null) ? path : "ideas/（按第一行标题命名）";
        Sheet.showOptions(this, dark, "保存到 GitHub", target,
                new String[]{"版本迭代（旧版归档到 .versions/）", "覆盖当前版本"},
                new Sheet.OnPick() {
                    @Override
                    public void onPick(int index) {
                        doSave(index == 0);
                    }
                });
    }

    private void doSave(boolean iterate) {
        if (busy) return;
        final String token = Store.token(this);
        final String repo = Store.repo(this);
        final String branch = Store.branch(this);
        if (token.isEmpty()) {
            toast("先去设置里填令牌");
            return;
        }

        final String body = ensureMeta(editor.getText().toString());
        if (body.trim().isEmpty()) {
            toast("内容是空的");
            return;
        }

        final String target = (path != null) ? path : buildNewPath(body);
        if (target == null) {
            toast("给这篇想法起个标题（第一行用 # 标题）");
            return;
        }

        // 离线：直接落本地草稿，联网后打开会自动提示
        if (offline) {
            Store.saveDraft(this, target, body, original != null ? original : Store.cachedFile(this, target));
            Banner.show(this, dark, "离线：已存到本地，联网后再保存", false);
            return;
        }

        if (!iterate || sha == null || original == null) {
            commit(target, body, sha, iterate ? "docs: 新建 " + baseName(target) : "docs: 更新 " + baseName(target));
            return;
        }

        // 迭代：先处理归档，再写新内容
        setBusy(true, "归档旧版本…");
        String dir = target.substring(0, target.lastIndexOf('/'));
        String base = baseName(target);
        final String archiveDir = ".versions/" + dir + "/" + base;

        GitHubApi.listDir(repo, branch, archiveDir, token, new GitHubApi.Cb<List<String>>() {
            @Override
            public void ok(List<String> names) {
                int max = 0;
                for (String n : names) {
                    Matcher m = Pattern.compile("^V\\d+\\.(\\d+)\\.md$").matcher(n);
                    if (m.matches()) max = Math.max(max, Integer.parseInt(m.group(1)));
                }
                final String archivePath = archiveDir + "/V0." + (max + 1) + ".md";
                GitHubApi.writeFile(repo, branch, archivePath, original, null,
                        "chore: 归档 " + base + " V0." + (max + 1), token,
                        new GitHubApi.Cb<GitHubApi.PutResult>() {
                            @Override
                            public void ok(GitHubApi.PutResult r) {
                                commit(target, body, sha, "docs: 迭代 " + base + " → 归档 " + archivePath);
                            }

                            @Override
                            public void err(String message) {
                                setBusy(false, null);
                                toast("归档失败：" + message);
                            }
                        });
            }

            @Override
            public void err(String message) {
                setBusy(false, null);
                toast("读取归档目录失败：" + message);
            }
        });
    }

    private void commit(final String target, final String body, String shaForUpdate, String message) {
        setBusy(true, "提交中…");
        final boolean isNew = (path == null);
        GitHubApi.writeFile(Store.repo(this), Store.branch(this), target, body, shaForUpdate, message,
                Store.token(this), new GitHubApi.Cb<GitHubApi.PutResult>() {
                    @Override
                    public void ok(GitHubApi.PutResult r) {
                        setBusy(false, null);
                        sha = r.sha;
                        original = body;
                        path = target;
                        Store.cacheFile(EditorActivity.this, target, body);
                        Store.clearDraft(EditorActivity.this, target);
                        docName.setText(baseName(target));
                        pathLabel.setText(target.contains("/") ? target.substring(0, target.lastIndexOf('/')) : "/");
                        toast(isNew ? "已创建 " + baseName(target) : "已保存 " + baseName(target));
                        setResult(RESULT_OK);
                    }

                    @Override
                    public void err(String message) {
                        setBusy(false, null);
                        // 提交失败也把内容留在本地，避免白写
                        Store.saveDraft(EditorActivity.this, target, body,
                                original != null ? original : Store.cachedFile(EditorActivity.this, target));
                        Banner.show(EditorActivity.this, dark, message + "（已暂存本地）", false);
                    }
                });
    }

    private String buildNewPath(String body) {
        String title = null;
        for (String line : body.split("\n")) {
            String t = line.trim();
            if (t.startsWith("# ")) {
                title = t.substring(2).trim();
                break;
            }
            if (!t.isEmpty() && !t.equals("#")) break;
        }
        if (title == null || title.isEmpty()) return null;
        // 支持「设计/某个想法」这种写法，直接落到子目录
        String[] segs = title.split("/");
        StringBuilder sb = new StringBuilder();
        for (String seg : segs) {
            String s = seg.replaceAll("[\\\\:*?\"<>|]", "-").trim();
            if (s.isEmpty()) continue;
            if (sb.length() > 0) sb.append('/');
            sb.append(s);
        }
        String safe = sb.toString();
        if (safe.isEmpty()) safe = "未命名";
        String root = Store.humanRoot(this);
        return root + "/" + safe + ".md";
    }

    private String baseName(String p) {
        return p.substring(p.lastIndexOf('/') + 1);
    }

    private void setBusy(boolean b, String label) {
        busy = b;
        if (b) {
            if (label != null) Banner.show(this, dark, label, true);
        } else if (label != null) {
            Banner.show(this, dark, label, false);
        } else {
            // 关键：成功后要主动收掉进度横幅，否则它一直挂着
            Banner.hide(this);
        }
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

    private void toast(String s) {
        Banner.show(this, dark, s, false);
    }
}
