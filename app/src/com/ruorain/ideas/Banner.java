package com.ruorain.ideas;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 置底横幅：替代系统 Toast / 浮窗。
 *
 * busy=true  → 顶部一条贯穿整宽的细进度条（不确定，不写百分比），保持到主动 hide
 * busy=false → 没有进度条，2.6 秒后自己走
 * 两种情况都有 × 可以立刻关掉。
 */
public final class Banner {

    private static View current;
    private static Runnable pendingHide;

    private Banner() {}

    public static void show(Activity a, boolean dark, String text, boolean busy) {
        if (a == null || a.isFinishing()) return;
        // 挂到内容区而不是 decorView：键盘弹出时内容区会缩小，横幅跟着上移，
        // 挂 decorView 会被键盘盖住（键盘在 decor 之上的独立 window）。
        View contentRoot = a.findViewById(android.R.id.content);
        ViewGroup host = contentRoot != null ? (ViewGroup) contentRoot
                : (ViewGroup) a.getWindow().getDecorView();
        if (host == null) return;

        hideInternal();

        // 竖向：进度条（贯穿）+ 一行文字
        LinearLayout box = UI.row(a, false);
        box.setBackground(UI.roundRect(a, Colors.surface(dark), 12, Colors.line(dark), 0.8f));
        box.setClipToOutline(true);
        box.setElevation(UI.dp(a, 10));

        if (busy) {
            ProgressBar pb = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
            pb.setIndeterminate(true);
            pb.setIndeterminateTintList(ColorStateList.valueOf(Colors.accent(a)));
            pb.setMinimumHeight(0);
            pb.setMinimumWidth(0);
            pb.setBackgroundColor(0x00000000);
            box.addView(pb, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(a, 4)));
        }

        LinearLayout content = UI.row(a, true);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(UI.dp(a, 14), UI.dp(a, 12), UI.dp(a, 8), UI.dp(a, 12));

        TextView label = UI.text(a, text, 13f, Colors.text(dark));
        label.setMaxLines(2);
        content.addView(label, UI.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout close = new FrameLayout(a);
        close.setBackground(UI.roundRect(a, 0x00000000, 8));
        ImageView x = UI.icon(a, R.drawable.ic_x, 15, Colors.text3(dark));
        close.addView(x, new FrameLayout.LayoutParams(UI.dp(a, 15), UI.dp(a, 15), Gravity.CENTER));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(UI.dp(a, 32), UI.dp(a, 32));
        clp.leftMargin = UI.dp(a, 6);
        content.addView(close, clp);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideInternal();
            }
        });

        box.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        lp.leftMargin = UI.dp(a, 12);
        lp.rightMargin = UI.dp(a, 12);
        lp.bottomMargin = UI.dp(a, 18);

        host.addView(box, lp);
        current = box;

        box.setTranslationY(UI.dp(a, 24));
        box.setAlpha(0f);
        box.animate().translationY(0f).alpha(1f).setDuration(200).start();

        if (!busy) {
            pendingHide = new Runnable() {
                @Override
                public void run() {
                    hideInternal();
                }
            };
            box.postDelayed(pendingHide, 2600);
        }
    }

    public static void hide(Activity a) {
        hideInternal();
    }

    private static void hideInternal() {
        View v = current;
        current = null;
        pendingHide = null;
        if (v == null) return;
        if (v.getParent() == null) return;
        v.animate().cancel();
        v.animate().translationY(v.getHeight() * 0.4f).alpha(0f).setDuration(160)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        View view = v;
                        ViewGroup p = (ViewGroup) view.getParent();
                        if (p != null) p.removeView(view);
                    }
                }).start();
    }
}
