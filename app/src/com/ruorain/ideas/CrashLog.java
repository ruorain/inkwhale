package com.ruorain.ideas;

import android.content.Context;

import java.io.PrintWriter;
import java.io.StringWriter;

/** 记录最后一次崩溃的堆栈，方便用户截图反馈。 */
public final class CrashLog {

    private static final String P = "crash";
    private static final String K = "last";
    private static Thread.UncaughtExceptionHandler sDefault;
    private static boolean sInstalled;

    private CrashLog() {}

    public static void install(final Context c) {
        if (sInstalled) return;
        sInstalled = true;
        sDefault = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    pw.println("线程: " + t.getName() + "  " + new java.util.Date());
                    pw.println();
                    e.printStackTrace(pw);
                    pw.flush();
                    // 用 commit 同步写，进程马上就要被杀了
                    c.getApplicationContext()
                            .getSharedPreferences(P, Context.MODE_PRIVATE)
                            .edit()
                            .putString(K, sw.toString())
                            .commit();
                } catch (Throwable ignored) {
                    // 记录本身失败就算了
                }
                if (sDefault != null) {
                    sDefault.uncaughtException(t, e);
                }
            }
        });
    }

    public static String last(Context c) {
        return c.getSharedPreferences(P, Context.MODE_PRIVATE).getString(K, "");
    }

    public static void clear(Context c) {
        c.getSharedPreferences(P, Context.MODE_PRIVATE).edit().remove(K).commit();
    }

    public static boolean has(Context c) {
        return !last(c).isEmpty();
    }
}
