package com.ruorain.ideas;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 内置浏览器：在 app 里完成 GitHub 登录和令牌创建。
 * 页面上会浮一张提示卡，按当前页面告诉用户「现在该点哪里」。
 */
public class WebViewActivity extends BaseActivity {

    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    private WebView web;
    private ProgressBar bar;
    private LinearLayout hintCard;
    private TextView hintText;
    private boolean hintDismissed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String url = getIntent().getStringExtra("url");
        String title = getIntent().getStringExtra("title");
        if (url == null) url = "https://github.com";

        setContentView(build(url, title == null ? "GitHub" : title));
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
            overridePendingTransition(R.anim.back_in, R.anim.back_out);
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }

    private View build(String url, String title) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Colors.bg(dark));

        LinearLayout col = UI.row(this, false);
        root.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout top = UI.row(this, true);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(UI.dp(this, 8), UI.dp(this, 10), UI.dp(this, 8), UI.dp(this, 6));

        top.addView(iconButton(R.drawable.ic_x, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(R.anim.back_in, R.anim.back_out);
            }
        }));

        LinearLayout titles = UI.row(this, false);
        TextView h = UI.text(this, title, 14f, Colors.text(dark), Typeface.BOLD);
        h.setMaxLines(1);
        h.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        titles.addView(h);
        TextView host = UI.text(this, Uri.parse(url).getHost(), 11f, Colors.text3(dark));
        LinearLayout.LayoutParams hlp = UI.lp(WRAP, WRAP);
        hlp.topMargin = UI.dp(this, 2);
        titles.addView(host, hlp);
        top.addView(titles, UI.lp(MATCH, WRAP, 1f));

        top.addView(iconButton(R.drawable.ic_refresh_cw, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                web.reload();
            }
        }));
        top.addView(iconButton(R.drawable.ic_external_link, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(web.getUrl() == null ? "https://github.com" : web.getUrl())));
                } catch (Exception ignored) {
                    // 没有浏览器就算了
                }
            }
        }));
        col.addView(top);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(false);
        bar.setMax(100);
        bar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Colors.accent(this)));
        col.addView(bar, UI.lp(MATCH, UI.dp(this, 3)));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        web.setBackgroundColor(Colors.bg(dark));
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String u) {
                updateHint(u);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                if (bar == null) return;
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        web.loadUrl(url);
        col.addView(web, UI.lp(MATCH, 0, 1f));

        buildHintCard(root);
        updateHint(url);
        return root;
    }

    // ── 覆盖提示 ──────────────────────────────────────────
    private void buildHintCard(FrameLayout root) {
        hintCard = UI.row(this, true);
        hintCard.setGravity(Gravity.CENTER_VERTICAL);
        hintCard.setBackground(UI.roundRect(this, Colors.surface(dark), 14, Colors.line(dark), 0.8f));
        hintCard.setElevation(UI.dp(this, 12));
        hintCard.setPadding(UI.dp(this, 14), UI.dp(this, 12), UI.dp(this, 8), UI.dp(this, 12));

        ImageView ic = UI.icon(this, R.drawable.ic_triangle_alert, 18, Colors.accent(this));
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(UI.dp(this, 18), UI.dp(this, 18));
        icLp.rightMargin = UI.dp(this, 10);
        hintCard.addView(ic, icLp);

        hintText = UI.text(this, "", 12.5f, Colors.text(dark));
        hintText.setLineSpacing(UI.dp(this, 3), 1f);
        hintCard.addView(hintText, UI.lp(0, WRAP, 1f));

        FrameLayout close = new FrameLayout(this);
        ImageView x = UI.icon(this, R.drawable.ic_x, 14, Colors.text3(dark));
        close.addView(x, new FrameLayout.LayoutParams(UI.dp(this, 14), UI.dp(this, 14), Gravity.CENTER));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(UI.dp(this, 30), UI.dp(this, 30));
        clp.leftMargin = UI.dp(this, 6);
        hintCard.addView(close, clp);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hintDismissed = true;
                hintCard.setVisibility(View.GONE);
            }
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH, WRAP);
        lp.gravity = Gravity.BOTTOM;
        lp.leftMargin = UI.dp(this, 12);
        lp.rightMargin = UI.dp(this, 12);
        lp.bottomMargin = UI.dp(this, 18);
        root.addView(hintCard, lp);
    }

    private void updateHint(String url) {
        if (hintText == null || hintCard == null) return;
        if (hintDismissed) return;

        String text;
        String lower = url == null ? "" : url.toLowerCase();

        if (lower.contains("login") || lower.contains("session")) {
            text = "先登录你的 GitHub 账号。登录完会自动回到这里。";
        } else if (lower.contains("personal-access-tokens/new")) {
            String owner = Store.repo(this).split("/")[0];
            text = "从上往下填：\n"
                    + "① Token name：随便写，比如 yunbiji\n"
                    + "② Expiration：选 No expiration\n"
                    + "③ Repository access：选 All repositories\n"
                    + "④ Permissions：点 + 号\n"
                    + "　 → 搜 contents，勾上，右边选 Read and write\n"
                    + "　 → 再搜 administration，也勾上并选 Read and write（这样我能帮你建仓库）\n"
                    + "⑤ 最底下点 Generate token";
        } else if (lower.contains("secrets/actions/new")) {
            text = "Name 填：DEEPSEEK_API_KEY\n"
                    + "Secret 粘贴你的 sk- 开头的 Key\n"
                    + "然后点 Add secret。加完回到应用，周报就能自动跑了。";
        } else if (lower.contains("personal-access-tokens")) {
            text = "在这里找到刚创建的钥匙，点它右边的复制按钮。";
        } else if (lower.contains("github.com")) {
            text = "在下面完成 GitHub 的操作，完成后点左上角 × 回来。";
        } else {
            text = "完成后点左上角 × 回到应用。";
        }

        hintText.setText(text);
        hintCard.setVisibility(View.VISIBLE);
    }

    private View iconButton(int res, View.OnClickListener l) {
        FrameLayout box = new FrameLayout(this);
        box.setBackground(UI.roundRect(this, 0x00000000, 10));
        ImageView ic = UI.icon(this, res, 18, Colors.text2(dark));
        box.addView(ic, new FrameLayout.LayoutParams(UI.dp(this, 18), UI.dp(this, 18), Gravity.CENTER));
        box.setLayoutParams(UI.lp(UI.dp(this, 36), UI.dp(this, 36)));
        box.setOnClickListener(l);
        UI.press(box);
        return box;
    }
}
