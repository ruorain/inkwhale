package com.ruorain.ideas;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 代码建 UI 用的小工具：dp、圆角背景、文字、图标。 */
public final class UI {

    private UI() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static int sp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics()));
    }

    public static TextView text(Context c, String s, float sizeSp, int color) {
        return text(c, s, sizeSp, color, Typeface.NORMAL);
    }

    public static TextView text(Context c, String s, float sizeSp, int color, int style) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static ImageView icon(Context c, int res, float sizeDp, int color) {
        ImageView v = new ImageView(c);
        android.graphics.drawable.Drawable d = c.getDrawable(res);
        if (d != null) {
            d = d.mutate();
            d.setTint(color);
            v.setImageDrawable(d);
        }
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int s = dp(c, sizeDp);
        v.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return v;
    }

    public static GradientDrawable roundRect(Context c, int fill, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable roundRect(Context c, int fill, float radiusDp, int stroke, float strokeDp) {
        GradientDrawable d = roundRect(c, fill, radiusDp);
        d.setStroke(dp(c, strokeDp), stroke);
        return d;
    }

    public static GradientDrawable circle(Context c, int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        return d;
    }

    public static LinearLayout row(Context c, boolean horizontal) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    public static LinearLayout.LayoutParams withTop(Context c, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, topDp);
        return p;
    }

    public static View spacer(Context c, float hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(c, hDp)));
        return v;
    }

    public static View divider(Context c, boolean dark) {
        View v = new View(c);
        v.setBackgroundColor(Colors.line(dark));
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.8f))));
        return v;
    }

    public static View dot(Context c, int color) {
        View v = new View(c);
        v.setBackground(circle(c, color));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 6), dp(c, 6)));
        return v;
    }

    public static LinearLayout.LayoutParams centered() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    public static int gravityCenterVertical(int base) {
        return base | Gravity.CENTER_VERTICAL;
    }

    /**
     * 按压反馈：按下 110ms 缩到 0.96（无过冲），松手 220ms 弹回（Overshoot 2.2）。
     * 对应动效文档「按压反馈必须在 100ms 内出现」。
     */
    public static void press(final View v) {
        v.setClickable(true);
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    view.animate()
                            .scaleX(0.96f).scaleY(0.96f)
                            .setDuration(110)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    view.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(220)
                            .setInterpolator(new OvershootInterpolator(2.2f))
                            .start();
                }
                return false;
            }
        });
    }
}
