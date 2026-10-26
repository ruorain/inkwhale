package com.ruorain.ideas;

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

/** DeepSeek 对话接口（OpenAI 兼容）。只做两件事：整理草稿、起标题。 */
public final class DeepSeek {

    private static final String ENDPOINT = "https://api.deepseek.com/chat/completions";
    private static final String MODEL = "deepseek-chat";

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Cb {
        void ok(String text);

        void err(String message);
    }

    private DeepSeek() {}

    /** 把草稿整理成结构清晰的 Markdown，保留原意、不编造 */
    public static void polish(final String key, final String content, final Cb cb) {
        run(key,
                "你是一个 Markdown 整理助手。用户给你一段想法草稿，请整理成结构清晰、语言干净的 Markdown："
                        + "保留全部原意和事实，绝不编造新内容；合并重复表述；补上合适的标题层级和列表；"
                        + "保留原有的代码块、表格、链接。只输出整理后的 Markdown 正文，不要任何解释或前后缀。",
                content, 0.4, cb);
    }

    /** 从正文里提炼一个短标题 */
    public static void autoTitle(final String key, final String content, final Cb cb) {
        String head = content.length() > 1500 ? content.substring(0, 1500) : content;
        run(key,
                "给下面这段想法起一个中文标题，不超过 12 个字，不要标点，不要引号，不要'标题'二字。只输出标题本身。",
                head, 0.3, cb);
    }

    // ── 内部 ──────────────────────────────────────────────
    private static void run(final String key, final String system, final String user,
                            final double temp, final Cb cb) {
        if (key == null || key.trim().isEmpty()) {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    cb.err("还没填 DeepSeek API Key（设置 → DeepSeek）");
                }
            });
            return;
        }
        if (user == null || user.trim().isEmpty()) {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    cb.err("内容是空的");
                }
            });
            return;
        }

        POOL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final String out = call(key.trim(), system, user, temp);
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.ok(out);
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

    private static String call(String key, String system, String user, double temp) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("temperature", temp);
        body.put("max_tokens", 4000);
        body.put("stream", false);

        JSONArray messages = new JSONArray();
        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", system);
        messages.put(sys);
        JSONObject usr = new JSONObject();
        usr.put("role", "user");
        usr.put("content", user);
        messages.put(usr);
        body.put("messages", messages);

        HttpURLConnection c = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(90000);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setDoOutput(true);

        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();

        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = readAll(in);

        if (code == 401) throw new ApiError("API Key 无效或已过期");
        if (code == 402) throw new ApiError("账户余额不足");
        if (code == 429) throw new ApiError("请求太频繁，稍后再试");
        if (code >= 400) throw new ApiError("HTTP " + code + "：" + shorten(text));

        JSONObject json = new JSONObject(text);
        JSONArray choices = json.optJSONArray("choices");
        if (choices == null || choices.length() == 0) throw new ApiError("模型没有返回内容");
        JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
        String out = msg == null ? "" : msg.optString("content", "");
        if (out.trim().isEmpty()) throw new ApiError("模型返回了空内容");
        return out.trim();
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
        return x.length() > 160 ? x.substring(0, 160) + "…" : x;
    }

    private static String friendly(Exception e) {
        if (e instanceof ApiError) return e.getMessage();
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (m.contains("Unable to resolve host")) return "连不上 api.deepseek.com";
        if (m.contains("timed out") || m.contains("timeout")) return "请求超时";
        return m;
    }

    private static class ApiError extends Exception {
        ApiError(String m) {
            super(m);
        }
    }
}
