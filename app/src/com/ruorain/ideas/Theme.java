package com.ruorain.ideas;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

/** 主题应用与系统栏配色，所有 Activity 走同一条路。 */
public final class Theme {

    private Theme() {}

    public static boolean apply(Activity a) {
        boolean dark = Store.isDark(a);
        a.setTheme(dark ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
        applySystemBars(a, dark);
        return dark;
    }

    public static void applySystemBars(Activity a, boolean dark) {
        Window w = a.getWindow();
        if (w == null) return;
        int bg = Colors.bg(dark);
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);

        View decor = w.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        decor.setSystemUiVisibility(flags);
    }
}
