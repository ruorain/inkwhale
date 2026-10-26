package com.ruorain.ideas;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 统一对话客户端，兼容 DeepSeek 与智谱。
 *
 * DeepSeek（OpenAI 格式）
 *   model            deepseek-flash / deepseek-v4-pro
 *   thinking         {"type":"enabled"|"disabled"}
 *   reasoning_effort low / high / max
 *
 * 智谱（GLM）
 *   model            glm-4.5 / glm-4.5-air / glm-4-flash
 *   thinking         {"type":"enabled"}（只有开关，没有强度分级）
 */
public final class AiClient {

    public static final String DEEPSEEK = "deepseek";
    public static final String ZHIPU = "zhipu";

    private static final String DS_URL = "https://api.deepseek.com/chat/completions";
    private static final String ZP_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static final int EFFORT_OFF = 0;
    public static final int EFFORT_LOW = 1;
    public static final int EFFORT_HIGH = 2;
    public static final int EFFORT_MAX = 3;
    public static final String[] EFFORT_LABELS = {"不思考", "低", "高", "最大"};

    public interface Cb {
        void ok(String content, String reasoning, long elapsedMs);

        void err(String message);
    }

    public static class Result {
        public final String content;
        public final String reasoning;

        Result(String content, String reasoning) {
            this.content = content;
            this.reasoning = reasoning;
        }
    }

    public static class Model {
        public final String provider;
        public final String id;
        public final String label;

        Model(String provider, String id, String label) {
            this.provider = provider;
            this.id = id;
            this.label = label;
        }

        public boolean isZhipu() {
            return ZHIPU.equals(provider);
        }
    }

    public static final Model[] MODELS = {
            new Model(DEEPSEEK, "deepseek-flash", "DeepSeek V4.1 Flash"),
            new Model(DEEPSEEK, "deepseek-v4-pro", "DeepSeek V4 Pro"),
            new Model(ZHIPU, "glm-4.5", "智谱 GLM-4.5"),
            new Model(ZHIPU, "glm-4.5-air", "智谱 GLM-4.5 Air"),
            new Model(ZHIPU, "glm-4-flash", "智谱 GLM-4 Flash"),
    };

    private AiClient() {}

    public static String keyFor(Context c, String provider) {
        return ZHIPU.equals(provider) ? Store.zhipuKey(c) : Store.deepseekKey(c);
    }

    public static Model currentModel(Context c) {
        int idx = Store.prefs(c).getInt("aiModelIndex", 0);
        if (idx < 0 || idx >= MODELS.length) idx = 0;
        return MODELS[idx];
    }

    public static int currentEffort(Context c) {
        return Store.prefs(c).getInt("aiEffort", EFFORT_HIGH);
    }

    /** 智谱只有开关，所以 low/high/max 都当作「开」 */
    public static String effortLabel(Model m, int effort) {
        if (m.isZhipu()) {
            return effort == EFFORT_OFF ? "不思考" : "思考开";
        }
        return EFFORT_LABELS[Math.max(0, Math.min(3, effort))];
    }

    public static void chat(Context c, Model m, int effort, JSONArray messages, final Cb cb) {
        final String key = keyFor(c, m.provider);
        if (key == null || key.trim().isEmpty()) {
            final String who = m.isZhipu() ? "智谱" : "DeepSeek";
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    cb.err("还没填" + who + " API Key（设置 → AI）");
                }
            });
            return;
        }

        final String url = m.isZhipu() ? ZP_URL : DS_URL;
        final String body = buildBody(m, effort, messages);

        final long started = System.currentTimeMillis();
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Result r = post(url, key.trim(), body);
                    final long ms = System.currentTimeMillis() - started;
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.ok(r.content, r.reasoning, ms);
                        }
                    });
                } catch (final Exception e) {
                    final String msg = friendly(e);
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.err(msg);
                        }
                    });
                }
            }
        });
    }

    private static String buildBody(Model m, int effort, JSONArray messages) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", m.id);
            body.put("messages", messages);
            body.put("max_tokens", 4000);
            body.put("stream", false);

            JSONObject thinking = new JSONObject();
            if (m.isZhipu()) {
                body.put("temperature", 0.7);
                if (effort != EFFORT_OFF) {
                    thinking.put("type", "enabled");
                    body.put("thinking", thinking);
                }
            } else {
                if (effort == EFFORT_OFF) {
                    thinking.put("type", "disabled");
                    body.put("thinking", thinking);
                    body.put("temperature", 1.0);
                } else {
                    thinking.put("type", "enabled");
                    body.put("thinking", thinking);
                    body.put("reasoning_effort",
                            effort == EFFORT_LOW ? "low" : (effort == EFFORT_MAX ? "max" : "high"));
                }
            }
            return body.toString();
        } catch (Exception e) {
            return "{}";
        }
    }


    // ── 真流式：SSE 逐块返回 ──────────────────────────────
    public interface StreamCb {
        /** 主线程回调，delta 是本次新增的一段文字 */
        void onDelta(String delta);

        void onDone(String content, String reasoning, long elapsedMs);

        void onError(String message);
    }

    public static void streamChat(Context c, Model m, int effort, JSONArray messages,
                                  final StreamCb cb) {
        final String url = m.isZhipu()
                ? "https://open.bigmodel.cn/api/paas/v4/chat/completions"
                : "https://api.deepseek.com/chat/completions";
        final String key = keyFor(c, m.provider);
        if (key.isEmpty()) {
            cb.onError(m.isZhipu() ? "还没配智谱 API Key" : "还没配 DeepSeek API Key");
            return;
        }

        final JSONObject body = new JSONObject();
        try {
            body.put("model", m.id);
            body.put("messages", messages);
            body.put("stream", true);
            JSONObject thinking = new JSONObject();
            if (m.isZhipu()) {
                thinking.put("type", effort == EFFORT_OFF ? "disabled" : "enabled");
                body.put("thinking", thinking);
            } else {
                thinking.put("type", effort == EFFORT_OFF ? "disabled" : "enabled");
                body.put("thinking", thinking);
                if (effort != EFFORT_OFF) {
                    body.put("reasoning_effort",
                            effort == EFFORT_MAX ? "max" : effort == EFFORT_LOW ? "low" : "high");
                } else {
                    body.put("temperature", 0.7);
                }
            }
        } catch (Exception e) {
            cb.onError("组装请求失败：" + e.getMessage());
            return;
        }

        final long started = System.currentTimeMillis();
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                java.net.HttpURLConnection conn = null;
                try {
                    conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(180000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Authorization", "Bearer " + key);
                    conn.setRequestProperty("Accept", "text/event-stream");

                    java.io.OutputStream os = conn.getOutputStream();
                    os.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    os.close();

                    int code = conn.getResponseCode();
                    if (code < 200 || code >= 300) {
                        String err = readStream(conn.getErrorStream());
                        final String msg = "HTTP " + code + "：" + err;
                        MAIN.post(new Runnable() {
                            @Override
                            public void run() {
                                cb.onError(msg);
                            }
                        });
                        return;
                    }

                    // 逐行读 SSE
                    java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream(),
                                    java.nio.charset.StandardCharsets.UTF_8));
                    StringBuilder full = new StringBuilder();
                    StringBuilder reasoning = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (!line.startsWith("data:")) continue;
                        String data = line.substring(5).trim();
                        if (data.isEmpty() || "[DONE]".equals(data)) continue;
                        try {
                            JSONObject chunk = new JSONObject(data);
                            JSONArray choices = chunk.optJSONArray("choices");
                            if (choices == null || choices.length() == 0) continue;
                            JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                            if (delta == null) continue;

                            String rc = delta.isNull("reasoning_content") ? ""
                                    : delta.optString("reasoning_content", "");
                            if (!rc.isEmpty()) reasoning.append(rc);

                            final String piece = delta.isNull("content") ? ""
                                    : delta.optString("content", "");
                            if (!piece.isEmpty()) {
                                full.append(piece);
                                MAIN.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        cb.onDelta(piece);
                                    }
                                });
                            }
                        } catch (Exception ignored) {
                            // 单块解析失败就跳过
                        }
                    }
                    br.close();

                    final String out = full.toString().trim();
                    final String reason = reasoning.toString().trim();
                    final long ms = System.currentTimeMillis() - started;
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onDone(out, reason, ms);
                        }
                    });
                } catch (final Exception e) {
                    final String msg = e.getMessage() == null ? "网络失败" : e.getMessage();
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onError(msg);
                        }
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        });
    }

    private static String readStream(java.io.InputStream in) {
        if (in == null) return "";
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static Result post(String url, String key, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(180000);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setDoOutput(true);

        OutputStream os = c.getOutputStream();
        os.write(body.getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();

        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = readAll(in);

        if (code == 401) throw new ApiError("API Key 无效或已过期");
        if (code == 402) throw new ApiError("账户余额不足");
        if (code == 429) throw new ApiError("请求太频繁，稍后再试");
        if (code == 400) throw new ApiError("参数被拒绝：" + shorten(text));
        if (code >= 400) throw new ApiError("HTTP " + code + "：" + shorten(text));

        JSONObject json = new JSONObject(text);
        JSONArray choices = json.optJSONArray("choices");
        if (choices == null || choices.length() == 0) throw new ApiError("模型没有返回内容");
        JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
        String out = msg == null ? "" : msg.optString("content", "");
        String reasoning = msg == null ? "" : msg.optString("reasoning_content", "");
        if (out.trim().isEmpty() && reasoning.trim().isEmpty()) {
            throw new ApiError("模型返回了空内容");
        }
        return new Result(out.trim(), reasoning.trim());
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String shorten(String s) {
        if (s == null) return "";
        String x = s.replaceAll("\\s+", " ").trim();
        return x.length() > 200 ? x.substring(0, 200) + "…" : x;
    }

    private static String friendly(Exception e) {
        if (e instanceof ApiError) return e.getMessage();
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (m.contains("Unable to resolve host")) return "连不上模型服务，检查网络";
        if (m.contains("timed out") || m.contains("timeout")) return "请求超时";
        return m;
    }

    private static class ApiError extends Exception {
        ApiError(String m) {
            super(m);
        }
    }
}
