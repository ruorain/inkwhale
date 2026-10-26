package com.ruorain.ideas;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 统一的底部面板，替代系统 AlertDialog。 */
public final class Sheet {

    public interface OnPick {
        void onPick(int index);
    }

    public interface OnInput {
        void onInput(String value);
    }

    public interface OnForm {
        void onSubmit(String value, String extra);
    }

    private Sheet() {}

    /** 通用表单：标题 + 输入框 + 可选「保存到」+ 可选预览 + 按钮。新建/重命名/存想法共用。 */
    public static class Form {
        public String title = "";
        public String label = "";
        public String hint = "";
        public String initial = "";
        public String extraLabel;
        public String extraValue;
        public String[] extraOptions;
        public String preview;
        public String confirm = "保存";
        public OnForm onSubmit;
    }

    public static void showForm(final Activity a, final boolean dark, final Form f) {
        final Dialog d = baseDialog(a, dark);
        LinearLayout panel = UI.row(a, false);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Colors.surface(dark));
        float r = UI.dp(a, 20);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(bg);
        panel.setPadding(UI.dp(a, 18), 0, UI.dp(a, 18), UI.dp(a, 18));
        panel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 吃掉点击
            }
        });

        panel.addView(handleBar(a, dark));
        panel.addView(UI.text(a, f.title, 17f, Colors.text(dark), Typeface.BOLD));
        panel.addView(UI.spacer(a, 12));

        if (f.label != null && !f.label.isEmpty()) {
            panel.addView(UI.text(a, f.label, 12f, Colors.text3(dark)));
        }

        final EditText input = new EditText(a);
        input.setText(f.initial == null ? "" : f.initial);
        input.setHint(f.hint == null ? "" : f.hint);
        input.setHintTextColor(Colors.text3(dark));
        input.setTextColor(Colors.text(dark));
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14.5f);
        input.setBackground(UI.roundRect(a, Colors.surface2(dark), 11, Colors.line(dark), 0.8f));
        input.setPadding(UI.dp(a, 13), UI.dp(a, 12), UI.dp(a, 13), UI.dp(a, 12));
        input.setSingleLine(true);
        LinearLayout.LayoutParams ilp = UI.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = UI.dp(a, 7);
        panel.addView(input, ilp);

        final String[] extra = {f.extraValue};
        if (f.extraLabel != null && !f.extraLabel.isEmpty()) {
            panel.addView(UI.spacer(a, 14));
            panel.addView(UI.text(a, f.extraLabel, 12f, Colors.text3(dark)));

            final TextView row = UI.text(a,
                    (extra[0] == null ? "" : extra[0]) + "  ▾",
                    14f, Colors.text(dark));
            row.setBackground(UI.roundRect(a, Colors.surface2(dark), 11, Colors.line(dark), 0.8f));
            row.setPadding(UI.dp(a, 13), UI.dp(a, 12), UI.dp(a, 13), UI.dp(a, 12));
            LinearLayout.LayoutParams rlp = UI.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = UI.dp(a, 7);
            panel.addView(row, rlp);

            if (f.extraOptions != null && f.extraOptions.length > 0) {
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showOptions(a, dark, f.extraLabel, null, f.extraOptions, new OnPick() {
                            @Override
                            public void onPick(int index) {
                                extra[0] = f.extraOptions[index];
                                row.setText(extra[0] + "  ▾");
                            }
                        });
                    }
                });
            }
        }

        if (f.preview != null && !f.preview.isEmpty()) {
            panel.addView(UI.spacer(a, 14));
            String pv = f.preview.trim();
            if (pv.length() > 90) pv = pv.substring(0, 90) + "…";
            TextView p = UI.text(a, pv, 12f, Colors.text3(dark));
            p.setLineSpacing(UI.dp(a, 3), 1f);
            p.setMaxLines(3);
            p.setEllipsize(android.text.TextUtils.TruncateAt.END);
            p.setPadding(UI.dp(a, 12), UI.dp(a, 10), UI.dp(a, 12), UI.dp(a, 10));
            p.setBackground(UI.roundRect(a, Colors.bg(dark), 10));
            panel.addView(p, UI.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        panel.addView(UI.spacer(a, 16));

        LinearLayout btns = UI.row(a, true);
        TextView cancel = UI.text(a, "取消", 15f, Colors.text(dark), Typeface.BOLD);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(UI.roundRect(a, Colors.surface2(dark), 12));
        cancel.setPadding(0, UI.dp(a, 13), 0, UI.dp(a, 13));
        LinearLayout.LayoutParams clp = UI.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.rightMargin = UI.dp(a, 9);
        cancel.setLayoutParams(clp);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });

        TextView ok = UI.text(a, f.confirm, 15f, 0xFFFFFFFF, Typeface.BOLD);
        ok.setGravity(Gravity.CENTER);
        ok.setBackground(UI.roundRect(a, Colors.accent(a), 12));
        ok.setPadding(0, UI.dp(a, 13), 0, UI.dp(a, 13));
        ok.setLayoutParams(UI.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String v1 = input.getText().toString().trim();
                d.dismiss();
                if (f.onSubmit != null) f.onSubmit.onSubmit(v1, extra[0]);
            }
        });

        btns.addView(cancel);
        btns.addView(ok);
        panel.addView(btns);

        finish(a, dark, d, panel);
    }

    // ── 选项面板 ──────────────────────────────────────────
    public static void showOptions(final Activity a, boolean dark, String title, String subtitle,
                                   final String[] items, final OnPick pick) {
        final Dialog d = baseDialog(a, dark);
        LinearLayout panel = panel(a, dark);

        panel.addView(handleBar(a, dark));

        TextView t = UI.text(a, title, 17f, Colors.text(dark), Typeface.BOLD);
        panel.addView(t);

        if (subtitle != null && subtitle.length() > 0) {
            TextView s = UI.text(a, subtitle, 12.5f, Colors.text3(dark));
            s.setPadding(0, UI.dp(a, 4), 0, UI.dp(a, 14));
            panel.addView(s);
        } else {
            panel.addView(UI.spacer(a, 10));
        }

        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            LinearLayout row = UI.row(a, true);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(UI.roundRect(a, Colors.surface2(dark), 12));
            row.setPadding(UI.dp(a, 14), UI.dp(a, 14), UI.dp(a, 14), UI.dp(a, 14));
            row.addView(UI.text(a, items[i], 15f, Colors.text(dark)), UI.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(UI.icon(a, R.drawable.ic_chevron_right, 16, Colors.text3(dark)));
            LinearLayout.LayoutParams lp = UI.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = UI.dp(a, 8);
            row.setLayoutParams(lp);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.dismiss();
                    pick.onPick(idx);
                }
            });
            UI.press(row);
            panel.addView(row);
        }

        panel.addView(cancelButton(a, dark, d));
        finish(a, dark, d, panel);
    }

    // ── 输入面板 ──────────────────────────────────────────
    public static void showInput(final Activity a, boolean dark, String title, String hint,
                                 String initial, boolean password, final OnInput cb) {
        final Dialog d = baseDialog(a, dark);
        LinearLayout panel = panel(a, dark);

        panel.addView(handleBar(a, dark));

        TextView t = UI.text(a, title, 17f, Colors.text(dark), Typeface.BOLD);
        panel.addView(t);
        panel.addView(UI.spacer(a, 12));

        final EditText input = new EditText(a);
        input.setText(initial == null ? "" : initial);
        input.setHint(hint == null ? "" : hint);
        input.setSingleLine(true);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14.5f);
        input.setTextColor(Colors.text(dark));
        input.setHintTextColor(Colors.text3(dark));
        input.setBackground(UI.roundRect(a, Colors.surface2(dark), 11, Colors.line(dark), 0.8f));
        input.setPadding(UI.dp(a, 13), UI.dp(a, 12), UI.dp(a, 13), UI.dp(a, 12));
        input.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT);
        panel.addView(input, UI.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        panel.addView(UI.spacer(a, 14));

        LinearLayout btns = UI.row(a, true);
        TextView cancel = UI.text(a, "取消", 15f, Colors.text(dark), Typeface.BOLD);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(UI.roundRect(a, Colors.surface2(dark), 12));
        cancel.setPadding(0, UI.dp(a, 13), 0, UI.dp(a, 13));
        LinearLayout.LayoutParams clp = UI.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.rightMargin = UI.dp(a, 9);
        cancel.setLayoutParams(clp);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });

        TextView ok = UI.text(a, "保存", 15f, 0xFFFFFFFF, Typeface.BOLD);
        ok.setGravity(Gravity.CENTER);
        ok.setBackground(UI.roundRect(a, Colors.accent(a), 12));
        ok.setPadding(0, UI.dp(a, 13), 0, UI.dp(a, 13));
        ok.setLayoutParams(UI.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                cb.onInput(input.getText().toString().trim());
            }
        });

        btns.addView(cancel);
        btns.addView(ok);
        panel.addView(btns);

        finish(a, dark, d, panel);
    }

    // ── 内部 ──────────────────────────────────────────────
    private static Dialog baseDialog(Activity a, boolean dark) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return d;
    }

    private static LinearLayout panel(Activity a, boolean dark) {
        LinearLayout panel = UI.row(a, false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Colors.surface(dark));
        float r = UI.dp(a, 20);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(bg);
        panel.setPadding(UI.dp(a, 18), 0, UI.dp(a, 18), UI.dp(a, 18));
        panel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 吃掉点击，避免穿透到遮罩
            }
        });
        return panel;
    }

    private static View handleBar(Activity a, boolean dark) {
        FrameLayout wrap = new FrameLayout(a);
        View bar = new View(a);
        bar.setBackground(UI.roundRect(a, Colors.line(dark), 2));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(UI.dp(a, 34), UI.dp(a, 4), Gravity.CENTER_HORIZONTAL);
        blp.topMargin = UI.dp(a, 9);
        blp.bottomMargin = UI.dp(a, 8);
        wrap.addView(bar, blp);
        return wrap;
    }

    private static View cancelButton(final Activity a, boolean dark, final Dialog d) {
        TextView cancel = UI.text(a, "取消", 15f, Colors.text2(dark));
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, UI.dp(a, 14), 0, UI.dp(a, 4));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        return cancel;
    }

    private static void finish(final Activity a, final boolean dark, final Dialog d, final LinearLayout panel) {
        FrameLayout outer = new FrameLayout(a);
        outer.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        outer.addView(panel, plp);

        outer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });

        d.setContentView(outer);
        d.setCanceledOnTouchOutside(true);

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.BOTTOM);
        }

        d.show();

        // 从下方弹起，带一点过冲再回稳；淡入只走前 40%，避免整块发虚
        panel.setTranslationY(UI.dp(a, 64));
        panel.setAlpha(0f);
        panel.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new android.view.animation.PathInterpolator(0.16f, 1.12f, 0.24f, 1f))
                .start();
        if (outer != null) {
            outer.setAlpha(0f);
            outer.animate().alpha(1f).setDuration(180).start();
        }
    }
}
