package com.ruorain.ideas;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 会话（话题）本地存储。
 *
 * 索引存在 sessions 里（JSON 数组），每条会话内容单独存 session:<id>。
 * 全部只在本机，不上传。
 */
public final class SessionStore {

    public static class Session {
        public String id;
        public String topic;
        public long updatedAt;
        public int turns;

        public Session() {}

        Session(String id, String topic, long updatedAt, int turns) {
            this.id = id;
            this.topic = topic;
            this.updatedAt = updatedAt;
            this.turns = turns;
        }
    }

    private static final String K_INDEX = "sessions";
    private static final String K_BODY = "session:";

    private SessionStore() {}

    private static SharedPreferences p(Context c) {
        return Store.prefs(c);
    }

    public static List<Session> list(Context c) {
        List<Session> out = new ArrayList<Session>();
        String raw = p(c).getString(K_INDEX, "");
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Session s = new Session();
                s.id = o.optString("id", "");
                s.topic = o.optString("topic", "未命名对话");
                s.updatedAt = o.optLong("ts", 0);
                s.turns = o.optInt("turns", 0);
                if (!s.id.isEmpty()) out.add(s);
            }
        } catch (Exception ignored) {
            // 索引坏了就当空
        }
        Collections.sort(out, new Comparator<Session>() {
            @Override
            public int compare(Session a, Session b) {
                return Long.compare(b.updatedAt, a.updatedAt);
            }
        });
        return out;
    }

    public static void upsert(Context c, String id, String topic, int turns) {
        List<Session> all = list(c);
        boolean found = false;
        for (Session s : all) {
            if (s.id.equals(id)) {
                s.topic = topic;
                s.turns = turns;
                s.updatedAt = System.currentTimeMillis();
                found = true;
                break;
            }
        }
        if (!found) {
            all.add(new Session(id, topic, System.currentTimeMillis(), turns));
        }
        saveIndex(c, all);
    }

    public static void touch(Context c, String id, String topic, int turns) {
        upsert(c, id, topic, turns);
    }

    public static void remove(Context c, String id) {
        List<Session> all = list(c);
        List<Session> keep = new ArrayList<Session>();
        for (Session s : all) if (!s.id.equals(id)) keep.add(s);
        saveIndex(c, keep);
        p(c).edit().remove(K_BODY + id).apply();
    }

    public static void rename(Context c, String id, String topic) {
        List<Session> all = list(c);
        for (Session s : all) {
            if (s.id.equals(id)) {
                s.topic = topic;
                s.updatedAt = System.currentTimeMillis();
            }
        }
        saveIndex(c, all);
    }

    public static String body(Context c, String id) {
        return p(c).getString(K_BODY + id, "");
    }

    public static void saveBody(Context c, String id, String json) {
        p(c).edit().putString(K_BODY + id, json == null ? "" : json).apply();
    }

    public static void clearBody(Context c, String id) {
        p(c).edit().remove(K_BODY + id).apply();
    }

    public static String newId() {
        return "s" + System.currentTimeMillis();
    }

    private static void saveIndex(Context c, List<Session> all) {
        try {
            JSONArray arr = new JSONArray();
            for (Session s : all) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("topic", s.topic);
                o.put("ts", s.updatedAt);
                o.put("turns", s.turns);
                arr.put(o);
            }
            p(c).edit().putString(K_INDEX, arr.toString()).apply();
        } catch (Exception ignored) {
            // 写失败就算了
        }
    }
}
