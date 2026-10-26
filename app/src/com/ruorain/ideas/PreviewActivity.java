package com.ruorain.ideas;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PreviewActivity extends BaseActivity {

    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String content = getIntent().getStringExtra("content");
        String title = getIntent().getStringExtra("title");
        setContentView(build(content, title));
    }

    private View build(String content, String title) {
        LinearLayout col = UI.row(this, false);
        col.setBackgroundColor(Colors.bg(dark));

        col.addView(buildBar(title));

        WebView wv = new WebView(this);
        wv.setBackgroundColor(Colors.bg(dark));
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(false);
        s.setDefaultTextEncodingName("utf-8");
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(false);
        s.setLoadWithOverviewMode(false);
        wv.setVerticalScrollBarEnabled(false);
        wv.loadDataWithBaseURL(null, Markdown.toHtml(content, dark, Colors.accent(this)), "text/html", "utf-8", null);

        col.addView(wv, UI.lp(MATCH, 0, 1f));
        return col;
    }

    private View buildBar(String title) {
        LinearLayout bar = UI.row(this, true);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UI.dp(this, 8), UI.dp(this, 10), UI.dp(this, 8), UI.dp(this, 10));

        bar.addView(iconButton(R.drawable.ic_arrow_left, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }));

        LinearLayout mid = UI.row(this, false);
        mid.setGravity(Gravity.CENTER);
        TextView name = UI.text(this, "预览", 14f, Colors.text(dark), Typeface.BOLD);
        mid.addView(name);
        TextView sub = UI.text(this, title == null ? "" : title, 11f, Colors.text3(dark));
        LinearLayout.LayoutParams slp = UI.lp(WRAP, WRAP);
        slp.topMargin = UI.dp(this, 2);
        sub.setMaxLines(1);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        mid.addView(sub, slp);
        bar.addView(mid, UI.lp(MATCH, WRAP, 1f));

        bar.addView(iconButton(R.drawable.ic_pencil, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }));
        return bar;
    }

    private View iconButton(int res, View.OnClickListener l) {
        FrameLayout box = new FrameLayout(this);
        box.setBackground(UI.roundRect(this, 0x00000000, 10));
        ImageView ic = UI.icon(this, res, 20, Colors.text2(dark));
        box.addView(ic, new FrameLayout.LayoutParams(UI.dp(this, 20), UI.dp(this, 20), Gravity.CENTER));
        box.setLayoutParams(UI.lp(UI.dp(this, 38), UI.dp(this, 38)));
        box.setOnClickListener(l);
        return box;
    }
}
