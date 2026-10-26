package com.ruorain.ideas;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 引导：四步，每步一句话 + 一个大按钮。
 * 原则：一屏只做一件事，术语全换成大白话，随时能跳过。
 *
 * 1 欢迎 → 2 拿钥匙 → 3 AI（可跳过）→ 4 完成
 */
public class OnboardingActivity extends BaseActivity {

    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;
    private static final int STEPS = 4;
    private static final String TOKEN_URL = "https://github.com/settings/personal-access-tokens/new";

    private int step = 0;
    private EditText tokenInput;
    private EditText aiKeyInput;
    private String pendingToken = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从浏览器回来，剪贴板里正好是钥匙就自动填
        if (step == 1 && (pendingToken == null || pendingToken.isEmpty())) {
            String clip = readClipboard();
            if (clip != null && clip.startsWith("github_pat_")) {
                pendingToken = clip;
                if (tokenInput != null) tokenInput.setText(clip);
            }
        }
    }

    private String readClipboard() {
        try {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0) return "";
            CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            return t == null ? "" : t.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void render() {
        LinearLayout col = UI.row(this, false);
        col.setBackgroundColor(Colors.bg(dark));
        col.setPadding(UI.dp(this, 26), UI.dp(this, 30), UI.dp(this, 26), UI.dp(this, 24));

        col.addView(progress());

        switch (step) {
            case 0:
                renderWelcome(col);
                break;
            case 1:
                renderKey(col);
                break;
            case 2:
                renderAi(col);
                break;
            default:
                renderDone(col);
                break;
        }

        setContentView(col);
    }

    // ── 进度：一个数字 + 一条细线，比六个圆点好懂 ──────────
    private View progress() {
        LinearLayout box = UI.row(this, false);

        TextView t = UI.text(this, "第 " + (step + 1) + " 步 / 共 " + STEPS + " 步",
                12f, Colors.text3(dark));
        box.addView(t);

        LinearLayout track = UI.row(this, true);
        View fill = new View(this);
        fill.setBackground(UI.roundRect(this, Colors.accent(this), 2));
        track.addView(fill, new LinearLayout.LayoutParams(0, UI.dp(this, 3),
                (step + 1f) / STEPS));
        View rest = new View(this);
        rest.setBackground(UI.roundRect(this, Colors.line(dark), 2));
        track.addView(rest, new LinearLayout.LayoutParams(0, UI.dp(this, 3),
                (STEPS - step - 1f) / STEPS));

        LinearLayout.LayoutParams tlp = UI.lp(MATCH, UI.dp(this, 3));
        tlp.topMargin = UI.dp(this, 10);
        tlp.bottomMargin = UI.dp(this, 38);
        box.addView(track, tlp);
        return box;
    }

    // ── 1 欢迎 ────────────────────────────────────────────
    private void renderWelcome(LinearLayout col) {
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.whale);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(UI.dp(this, 96), UI.dp(this, 96));
        wlp.gravity = Gravity.CENTER_HORIZONTAL;
        wlp.topMargin = UI.dp(this, 26);
        col.addView(logo, wlp);

        col.addView(UI.spacer(this, 28));

        col.addView(big("把想法存进 GitHub"));
        col.addView(line("删了这个 app，想法也不会丢。"));

        LinearLayout promise = UI.row(this, false);
        promise.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams plp = UI.lp(MATCH, WRAP);
        plp.topMargin = UI.dp(this, 16);

        TextView p1 = UI.text(this, "不用注册，不用云服务", 12.5f, Colors.text3(dark));
        p1.setGravity(Gravity.CENTER);
        promise.addView(p1);

        TextView p2 = UI.text(this, "我们不收集你的任何信息", 12.5f, Colors.text3(dark));
        p2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p2lp = UI.lp(WRAP, WRAP);
        p2lp.topMargin = UI.dp(this, 5);
        promise.addView(p2, p2lp);

        col.addView(promise, plp);

        col.addView(UI.spacer(this, 34));

        col.addView(huge("开始", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                step = 1;
                render();
            }
        }));

        col.addView(small("先看看演示（不用配置）", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Store.setDemo(OnboardingActivity.this, true);
                Store.setDone(OnboardingActivity.this, true);
                goMain();
            }
        }));
    }

    // ── 2 拿钥匙 ──────────────────────────────────────────
    private void renderKey(LinearLayout col) {
        col.addView(big("拿一把钥匙"));
        col.addView(line("点下面的按钮，登录 GitHub，\n按提示生成，复制回来。"));

        col.addView(UI.spacer(this, 22));

        col.addView(huge("打开 GitHub", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(OnboardingActivity.this, WebViewActivity.class);
                i.putExtra("url", TOKEN_URL);
                i.putExtra("title", "GitHub");
                startActivity(i);
            }
        }));

        col.addView(UI.spacer(this, 22));

        col.addView(UI.text(this, "钥匙粘在这里", 13f, Colors.text3(dark)));

        tokenInput = new EditText(this);
        tokenInput.setText(pendingToken);
        tokenInput.setHint("github_pat_...");
        tokenInput.setHintTextColor(Colors.text3(dark));
        tokenInput.setTextColor(Colors.text(dark));
        tokenInput.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tokenInput.setTypeface(Typeface.MONOSPACE);
        tokenInput.setBackground(UI.roundRect(this, Colors.surface(dark), 12, Colors.line(dark), 0.8f));
        tokenInput.setPadding(UI.dp(this, 13), UI.dp(this, 13), UI.dp(this, 13), UI.dp(this, 13));
        tokenInput.setSingleLine(false);
        tokenInput.setMaxLines(3);
        LinearLayout.LayoutParams tlp = UI.lp(MATCH, WRAP);
        tlp.topMargin = UI.dp(this, 8);
        col.addView(tokenInput, tlp);

        col.addView(UI.spacer(this, 14));

        col.addView(huge("粘好了", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitToken();
            }
        }));

        col.addView(small("先跳过", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goMain();
            }
        }));
    }

    private void submitToken() {
        final String t = tokenInput.getText().toString().trim();
        if (!t.startsWith("github_pat_") && !t.startsWith("ghp_")) {
            Banner.show(this, dark, "不对，钥匙应该以 github_pat_ 开头", false);
            return;
        }
        Banner.show(this, dark, "检查中…", true);
        GitHubApi.whoAmI(t, new GitHubApi.Cb<String>() {
            @Override
            public void ok(String login) {
                Banner.hide(OnboardingActivity.this);
                Store.set(OnboardingActivity.this, "token", t);
                Store.setDemo(OnboardingActivity.this, false);

                String cur = Store.repo(OnboardingActivity.this);
                String curOwner = cur.contains("/") ? cur.substring(0, cur.indexOf('/')) : "";
                if (login != null && !login.isEmpty() && !login.equalsIgnoreCase(curOwner)) {
                    Store.set(OnboardingActivity.this, "repo", login + "/ideas");
                }
                step = 2;
                render();
            }

            @Override
            public void err(String message) {
                Banner.hide(OnboardingActivity.this);
                Banner.show(OnboardingActivity.this, dark, "这把钥匙用不了：" + message, false);
            }
        });
    }

    // ── 3 AI（可跳过）─────────────────────────────────────
    private void renderAi(LinearLayout col) {
        col.addView(big("要用 AI 吗？"));
        col.addView(line("可以让它帮你整理想法、写周报。\n不想用就点下面灰的按钮。"));

        col.addView(UI.spacer(this, 22));

        col.addView(UI.text(this, "DeepSeek 的 Key（sk- 开头）", 13f, Colors.text3(dark)));

        aiKeyInput = new EditText(this);
        aiKeyInput.setHint("sk-...");
        aiKeyInput.setHintTextColor(Colors.text3(dark));
        aiKeyInput.setTextColor(Colors.text(dark));
        aiKeyInput.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        aiKeyInput.setTypeface(Typeface.MONOSPACE);
        aiKeyInput.setBackground(UI.roundRect(this, Colors.surface(dark), 12, Colors.line(dark), 0.8f));
        aiKeyInput.setPadding(UI.dp(this, 13), UI.dp(this, 13), UI.dp(this, 13), UI.dp(this, 13));
        aiKeyInput.setSingleLine(true);
        aiKeyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (!Store.deepseekKey(this).isEmpty()) aiKeyInput.setText(Store.deepseekKey(this));
        LinearLayout.LayoutParams ilp = UI.lp(MATCH, WRAP);
        ilp.topMargin = UI.dp(this, 8);
        col.addView(aiKeyInput, ilp);

        col.addView(UI.spacer(this, 14));

        col.addView(huge("用这个", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String k = aiKeyInput.getText().toString().trim();
                if (!k.isEmpty()) {
                    if (!k.startsWith("sk-")) {
                        Banner.show(OnboardingActivity.this, dark, "Key 一般以 sk- 开头，再看看", false);
                        return;
                    }
                    Store.set(OnboardingActivity.this, "deepseekKey", k);
                }
                step = 3;
                render();
            }
        }));

        col.addView(small("不用，跳过", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                step = 3;
                render();
            }
        }));
    }

    // ── 4 完成 ────────────────────────────────────────────
    private void renderDone(LinearLayout col) {
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        col.addView(UI.spacer(this, 56));

        TextView mark = UI.text(this, "✓", 34f, Colors.accent(this), Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(UI.circle(this, Colors.accentSoft(this, dark)));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(UI.dp(this, 68), UI.dp(this, 68));
        mlp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(mark, mlp);

        col.addView(UI.spacer(this, 24));

        col.addView(big("可以开始了"));
        col.addView(line("点右下角的 + 写第一篇。"));

        col.addView(UI.spacer(this, 40));

        col.addView(huge("写第一篇", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Store.setDone(OnboardingActivity.this, true);
                Intent i = new Intent(OnboardingActivity.this, EditorActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
            }
        }));

        col.addView(small("先逛逛", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Store.setDone(OnboardingActivity.this, true);
                goMain();
            }
        }));
    }

    // ── 组件 ──────────────────────────────────────────────
    private TextView big(String s) {
        TextView t = UI.text(this, s, 26f, Colors.text(dark), Typeface.BOLD);
        t.setLineSpacing(UI.dp(this, 2), 1f);
        LinearLayout.LayoutParams lp = UI.lp(MATCH, WRAP);
        lp.bottomMargin = UI.dp(this, 12);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView line(String s) {
        TextView t = UI.text(this, s, 15f, Colors.text2(dark));
        t.setLineSpacing(UI.dp(this, 6), 1f);
        return t;
    }

    private TextView huge(String s, View.OnClickListener l) {
        TextView btn = UI.text(this, s, 17f, 0xFFFFFFFF, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(UI.roundRect(this, Colors.accent(this), 14));
        btn.setPadding(0, UI.dp(this, 18), 0, UI.dp(this, 18));
        btn.setLayoutParams(UI.lp(MATCH, WRAP));
        btn.setOnClickListener(l);
        UI.press(btn);
        return btn;
    }

    private TextView small(String s, View.OnClickListener l) {
        TextView btn = UI.text(this, s, 14f, Colors.text3(dark));
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(UI.roundRect(this, Colors.surface(dark), 14));
        btn.setPadding(0, UI.dp(this, 15), 0, UI.dp(this, 15));
        LinearLayout.LayoutParams lp = UI.lp(MATCH, WRAP);
        lp.topMargin = UI.dp(this, 10);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(l);
        UI.press(btn);
        return btn;
    }

    private void goMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        overridePendingTransition(R.anim.enter, R.anim.exit);
        finish();
    }
}
