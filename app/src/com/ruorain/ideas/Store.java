package com.ruorain.ideas;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** 本地配置与缓存。 */
public final class Store {

    private static final String P = "ideas";
    private static final String P_DEMO = "ideas_demo";

    private Store() {}

    /**
     * 演示版用独立的存储文件，绝不读正式版配好的 token / key。
     * （两个版本包名相同，如果共用一份存储，演示版会「看见」正式版的配置。）
     */
    public static SharedPreferences prefs(Context c) {
        boolean demoBuild = c.getResources().getBoolean(R.bool.is_demo_build);
        return c.getSharedPreferences(demoBuild ? P_DEMO : P, Context.MODE_PRIVATE);
    }

    /** theme 取值 auto / light / dark */
    public static boolean isDark(Context c) {
        String mode = prefs(c).getString("theme", "auto");
        if ("dark".equals(mode)) return true;
        if ("light".equals(mode)) return false;
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    public static String repo(Context c) {
        return prefs(c).getString("repo", "ruorain/ideas");
    }

    public static String branch(Context c) {
        return prefs(c).getString("branch", "main");
    }

    public static String token(Context c) {
        return prefs(c).getString("token", "");
    }

    public static String agentRoot(Context c) {
        return prefs(c).getString("agentRoot", "Deepseek");
    }

    public static String humanRoot(Context c) {
        return prefs(c).getString("humanRoot", "ideas");
    }

    public static String deepseekKey(Context c) {
        return prefs(c).getString("deepseekKey", "");
    }

    public static String zhipuKey(Context c) {
        return prefs(c).getString("zhipuKey", "");
    }

    /** 当前选中的 AI 服务：deepseek / zhipu */
    public static String aiProvider(Context c) {
        return prefs(c).getString("aiProvider", "deepseek");
    }

    public static void setAiProvider(Context c, String p) {
        prefs(c).edit().putString("aiProvider", p).apply();
    }

    // ── 最近打开 ──────────────────────────────────────────
    private static final String K_RECENT = "recent:";

    public static void touchRecent(Context c, String path) {
        if (path == null || path.isEmpty()) return;
        prefs(c).edit().putLong(K_RECENT + path, System.currentTimeMillis()).apply();
    }

    /** 按打开时间倒序返回路径 */
    public static java.util.List<String> recentPaths(Context c, int n) {
        java.util.List<java.util.Map.Entry<String, ?>> hits =
                new java.util.ArrayList<java.util.Map.Entry<String, ?>>();
        for (java.util.Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
            if (e.getKey() != null && e.getKey().startsWith(K_RECENT) && e.getValue() instanceof Long) {
                hits.add(e);
            }
        }
        java.util.Collections.sort(hits, new java.util.Comparator<java.util.Map.Entry<String, ?>>() {
            @Override
            public int compare(java.util.Map.Entry<String, ?> a, java.util.Map.Entry<String, ?> b) {
                return Long.compare((Long) b.getValue(), (Long) a.getValue());
            }
        });
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (java.util.Map.Entry<String, ?> e : hits) {
            out.add(e.getKey().substring(K_RECENT.length()));
            if (out.size() >= n) break;
        }
        return out;
    }

    // ── 对话历史（一个持续会话 + 可手动命名的话题）──────
    private static final String K_CHAT = "chat:";
    private static final String K_TOPIC = "chatTopic";

    public static String chatTopic(Context c) {
        String t = prefs(c).getString(K_TOPIC, "");
        return t == null || t.isEmpty() ? "未命名对话" : t;
    }

    public static void setChatTopic(Context c, String topic) {
        prefs(c).edit().putString(K_TOPIC,
                topic == null || topic.trim().isEmpty() ? "未命名对话" : topic.trim()).apply();
    }

    public static void saveCurrentChat(Context c, String json) {
        prefs(c).edit().putString(K_CHAT + "current", json).apply();
    }

    public static String loadCurrentChat(Context c) {
        return prefs(c).getString(K_CHAT + "current", "");
    }

    public static void saveChat(Context c, String day, String json) {
        prefs(c).edit().putString(K_CHAT + day, json).apply();
    }

    public static String loadChat(Context c, String day) {
        return prefs(c).getString(K_CHAT + day, "");
    }

    public static java.util.List<String> chatDays(Context c) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (java.util.Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
            if (e.getKey() != null && e.getKey().startsWith(K_CHAT)) {
                out.add(e.getKey().substring(K_CHAT.length()));
            }
        }
        java.util.Collections.sort(out);
        java.util.Collections.reverse(out);
        return out;
    }

    // ── 周报签收 ──────────────────────────────────────────
    private static final String K_ACK = "digestAck";

    public static void ackDigest(Context c, String name) {
        java.util.Set<String> s = new java.util.HashSet<String>(
                prefs(c).getStringSet(K_ACK, new java.util.HashSet<String>()));
        s.add(name);
        prefs(c).edit().putStringSet(K_ACK, s).apply();
    }

    public static boolean isDigestAcked(Context c, String name) {
        return prefs(c).getStringSet(K_ACK, new java.util.HashSet<String>()).contains(name);
    }

    // ── 引导与演示模式 ────────────────────────────────────
    public static boolean isDone(Context c) {
        return prefs(c).getBoolean("onboarded", false);
    }

    public static void setDone(Context c, boolean v) {
        prefs(c).edit().putBoolean("onboarded", v).apply();
    }

    public static boolean isDemo(Context c) {
        return prefs(c).getBoolean("demo", false);
    }

    public static void setDemo(Context c, boolean v) {
        prefs(c).edit().putBoolean("demo", v).apply();
    }

    public static void set(Context c, String key, String value) {
        prefs(c).edit().putString(key, value).apply();
    }

    public static void setInt(Context c, String key, int value) {
        prefs(c).edit().putInt(key, value).apply();
    }

    // ── 列表缓存（先本地后网络）──────────────────────────
    private static final String K_CACHE = "listCache";
    private static final String K_CACHE_AT = "listCacheAt";

    public static void saveCache(Context c, String json) {
        prefs(c).edit()
                .putString(K_CACHE, json)
                .putLong(K_CACHE_AT, System.currentTimeMillis())
                .apply();
    }

    /** 返回 JSON 数组字符串；没有缓存返回空串 */
    public static String loadCache(Context c) {
        return prefs(c).getString(K_CACHE, "");
    }

    public static long cacheTime(Context c) {
        return prefs(c).getLong(K_CACHE_AT, 0L);
    }

    // ── 单个文件的离线缓存与本地草稿 ─────────────────────
    private static final String K_FILE = "f:";
    private static final String K_DRAFT = "d:";
    private static final String K_DRAFT_BASE = "db:";

    public static void cacheFile(Context c, String path, String text) {
        prefs(c).edit().putString(K_FILE + path, text).apply();
    }

    public static String cachedFile(Context c, String path) {
        return prefs(c).getString(K_FILE + path, "");
    }

    public static void saveDraft(Context c, String path, String text) {
        saveDraft(c, path, text, "");
    }

    /** base 是「开始改之前的原文」，用来判断远端有没有被别人改过 */
    public static void saveDraft(Context c, String path, String text, String base) {
        prefs(c).edit()
                .putString(K_DRAFT + path, text)
                .putString(K_DRAFT_BASE + path, base == null ? "" : base)
                .apply();
    }

    public static String draftBase(Context c, String path) {
        return prefs(c).getString(K_DRAFT_BASE + path, "");
    }

    public static String loadDraft(Context c, String path) {
        return prefs(c).getString(K_DRAFT + path, "");
    }

    public static boolean hasDraft(Context c, String path) {
        return !loadDraft(c, path).isEmpty();
    }

    public static void clearDraft(Context c, String path) {
        prefs(c).edit().remove(K_DRAFT + path).remove(K_DRAFT_BASE + path).apply();
    }

    /** 所有未同步的本地草稿：路径 → 内容 */
    public static java.util.Map<String, String> allDrafts(Context c) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<String, String>();
        for (java.util.Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
            String k = e.getKey();
            if (k == null || !k.startsWith(K_DRAFT)) continue;
            Object v = e.getValue();
            if (v instanceof String && !((String) v).isEmpty()) {
                out.put(k.substring(K_DRAFT.length()), (String) v);
            }
        }
        return out;
    }
}
