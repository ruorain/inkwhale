package com.ruorain.ideas;

import android.content.Context;

/** 全局配色。全部纯色，零渐变。强调色可在设置里换。 */
public final class Colors {

    private Colors() {}

    /** 五种强调色，和视觉稿一致 */
    public static final int[] PRESETS = {
            0xFF2F6FED, // 石墨蓝
            0xFF17876B, // 松绿
            0xFFB8641F, // 赤陶
            0xFF6B4FD8, // 靛紫
            0xFFC24B6A  // 绯红
    };

    public static final String[] PRESET_NAMES = {"石墨蓝", "松绿", "赤陶", "靛紫", "绯红"};

    public static final int WARN = 0xFFA96A00;

    public static int accent(Context c) {
        return Store.prefs(c).getInt("accent", PRESETS[0]);
    }

    public static int accentSoft(Context c, boolean dark) {
        int a = accent(c) & 0x00FFFFFF;
        int alpha = dark ? 0x38 : 0x18;
        return (alpha << 24) | a;
    }

    public static int bg(boolean dark) {
        return dark ? 0xFF0D0D0E : 0xFFFBFBFB;
    }

    public static int surface(boolean dark) {
        return dark ? 0xFF161617 : 0xFFFFFFFF;
    }

    public static int surface2(boolean dark) {
        return dark ? 0xFF1F1F21 : 0xFFF1F1F1;
    }

    public static int text(boolean dark) {
        return dark ? 0xFFEDEDEE : 0xFF161616;
    }

    public static int text2(boolean dark) {
        return dark ? 0xFF9C9CA2 : 0xFF6B6B70;
    }

    public static int text3(boolean dark) {
        return dark ? 0xFF6C6C73 : 0xFF9A9AA0;
    }

    public static int line(boolean dark) {
        return dark ? 0xFF26262A : 0xFFE8E8E8;
    }

    public static int danger(boolean dark) {
        return dark ? 0xFFE07070 : 0xFFC94A4A;
    }
}
