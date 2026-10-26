package com.ruorain.ideas;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 所有界面的基类。
 * 统一处理：主题、系统栏配色、主题热切换重建、离线状态提示。
 */
public abstract class BaseActivity extends Activity {

    protected boolean dark;

    private ConnectivityManager.NetworkCallback netCallback;
    private View offlineBar;
    protected boolean offline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        dark = Store.isDark(this);
        setTheme(dark ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
        Theme.applySystemBars(this, dark);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Store.isDark(this) != dark) {
            recreate();
            return;
        }
        startNetworkWatch();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopNetworkWatch();
    }

    @Override
    protected void onDestroy() {
        stopNetworkWatch();
        super.onDestroy();
    }

    /** 子类可以覆盖，用于离线/在线变化时的额外处理 */
    protected void onConnectivityChanged(boolean online) {
    }

    // ── 网络状态 ──────────────────────────────────────────
    private void startNetworkWatch() {
        if (netCallback != null) return;
        final ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        applyOffline(!isOnline());

        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                refresh();
            }

            @Override
            public void onLost(Network network) {
                refresh();
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                refresh();
            }

            private void refresh() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        applyOffline(!isOnline());
                    }
                });
            }
        };

        try {
            NetworkRequest req = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            cm.registerNetworkCallback(req, netCallback);
        } catch (Exception e) {
            netCallback = null;
        }
    }

    private void stopNetworkWatch() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null && netCallback != null) {
            try {
                cm.unregisterNetworkCallback(netCallback);
            } catch (Exception ignored) {
                // 已注销
            }
        }
        netCallback = null;
    }

    /**
     * 宽松判断：只要有一条能用的链路就算在线。
     * 不强制要求 INTERNET 能力位——部分代理/VPN 网络不会打这个标记，会把在线误判成离线。
     */
    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        if (caps == null) return false;
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return true;
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    // ── 离线横幅 ──────────────────────────────────────────
    private void applyOffline(boolean off) {
        if (off == offline) return;
        offline = off;
        if (off) showOfflineBar();
        else hideOfflineBar();
        onConnectivityChanged(!off);
    }

    private void showOfflineBar() {
        if (offlineBar != null) return;
        ViewGroup decor = (ViewGroup) getWindow().getDecorView();
        if (decor == null) return;

        LinearLayout bar = UI.row(this, true);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Colors.surface2(dark));
        bar.setPadding(UI.dp(this, 18), UI.dp(this, 8), UI.dp(this, 18), UI.dp(this, 8));

        bar.addView(UI.icon(this, R.drawable.ic_wifi_off, 14, Colors.text2(dark)));
        TextView t = UI.text(this, "离线模式 · 只显示本地缓存", 12f, Colors.text2(dark));
        t.setPadding(UI.dp(this, 8), 0, 0, 0);
        bar.addView(t);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        lp.topMargin = statusBarHeight();
        decor.addView(bar, lp);
        offlineBar = bar;

        // 只给 Activity 自己的根布局加内边距，别动 content 容器
        View root = contentRoot();
        if (root != null) root.setPadding(0, UI.dp(this, 34), 0, 0);

        bar.setTranslationY(-UI.dp(this, 10));
        bar.setAlpha(0f);
        bar.animate().translationY(0f).alpha(1f).setDuration(180).start();
    }

    private void hideOfflineBar() {
        if (offlineBar == null) return;
        ViewGroup p = (ViewGroup) offlineBar.getParent();
        if (p != null) p.removeView(offlineBar);
        offlineBar = null;
        View root = contentRoot();
        if (root != null) root.setPadding(0, 0, 0, 0);
    }

    private View contentRoot() {
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return null;
        return content.getChildAt(0);
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : UI.dp(this, 24);
    }
}
