package com.ruorain.ideas;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** GitHub Contents / Tree API 的最小实现，只用 framework + org.json。 */
public final class GitHubApi {

    private static final String API = "https://api.github.com";
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Cb<T> {
        void ok(T value);

        void err(String message);
    }

    public static class Entry {
        public final String path;
        public final long size;

        Entry(String path, long size) {
            this.path = path;
            this.size = size;
        }
    }

    public static class Doc {
        public String path;
        public String text;
        public String sha;
        public long size;
    }

    public static class PutResult {
        public String path;
        public String sha;
        public long size;
    }

    public static class ApiError extends Exception {
        public final int status;

        ApiError(int status, String msg) {
            super(msg);
            this.status = status;
        }
    }

    private GitHubApi() {}

    // ── 公开操作 ──────────────────────────────────────────

    /** 列出仓库里所有 markdown 文件（一次 tree 请求拿全） */
    public static void listMarkdown(final String repo, final String branch, final String token,
                                    final Cb<List<Entry>> cb) {
        async(new Task<List<Entry>>() {
            public List<Entry> call() throws Exception {
                String json = request("/repos/" + repo + "/git/trees/" + enc(branch) + "?recursive=1", "GET", token, null);
                if (json == null) throw new ApiError(404, "分支 " + branch + " 不存在，或仓库是空的");
                List<Entry> out = new ArrayList<Entry>();
                JSONObject root = new JSONObject(json);
                JSONArray tree = root.optJSONArray("tree");
                if (tree == null) return out;
                for (int i = 0; i < tree.length(); i++) {
                    JSONObject t = tree.getJSONObject(i);
                    if (!"blob".equals(t.optString("type"))) continue;
                    String p = t.optString("path", "");
                    if (!p.toLowerCase().endsWith(".md")) continue;
                    if (p.startsWith(".versions/") || p.startsWith(".trash/") || p.startsWith(".history/")) continue;
                    // README 是占位说明，不是想法，不列出来
                    String bn = p.substring(p.lastIndexOf('/') + 1);
                    if (bn.equalsIgnoreCase("readme.md")) continue;
                    out.add(new Entry(p, t.optLong("size", 0)));
                }
                return out;
            }
        }, cb);
    }

    /** 读一个文件 */
    public static void readFile(final String repo, final String branch, final String path, final String token,
                                final Cb<Doc> cb) {
        async(new Task<Doc>() {
            public Doc call() throws Exception {
                String json = request("/repos/" + repo + "/contents/" + encPath(path) + "?ref=" + enc(branch),
                        "GET", token, null);
                if (json == null) throw new ApiError(404, "仓库里没有 " + path);
                JSONObject o = new JSONObject(json);
                Doc d = new Doc();
                d.path = path;
                d.sha = o.optString("sha", "");
                d.size = o.optLong("size", 0);
                String b64 = o.optString("content", "").replace("\n", "").replace("\r", "");
                d.text = b64.isEmpty() ? "" : new String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8);
                return d;
            }
        }, cb);
    }

    /** 写一个文件；sha 为空表示新建 */
    public static void writeFile(final String repo, final String branch, final String path, final String text,
                                 final String sha, final String message, final String token,
                                 final Cb<PutResult> cb) {
        async(new Task<PutResult>() {
            public PutResult call() throws Exception {
                JSONObject body = new JSONObject();
                body.put("message", message);
                body.put("content", Base64.encodeToString(text.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
                body.put("branch", branch);
                if (sha != null && !sha.isEmpty()) body.put("sha", sha);

                String json = request("/repos/" + repo + "/contents/" + encPath(path), "PUT", token, body.toString());
                if (json == null) throw new ApiError(404, "写入失败：路径不存在");
                JSONObject o = new JSONObject(json);
                JSONObject content = o.optJSONObject("content");
                PutResult r = new PutResult();
                r.path = path;
                r.sha = content != null ? content.optString("sha", "") : "";
                r.size = text.getBytes(StandardCharsets.UTF_8).length;
                return r;
            }
        }, cb);
    }

    /** 列一个目录下的文件名；目录不存在返回空列表 */
    public static void listDir(final String repo, final String branch, final String dir, final String token,
                               final Cb<List<String>> cb) {
        async(new Task<List<String>>() {
            public List<String> call() throws Exception {
                String json = request("/repos/" + repo + "/contents/" + encPath(dir) + "?ref=" + enc(branch),
                        "GET", token, null);
                List<String> names = new ArrayList<String>();
                if (json == null) return names;
                if (json.trim().startsWith("[")) {
                    JSONArray arr = new JSONArray(json);
                    for (int i = 0; i < arr.length(); i++) {
                        names.add(arr.getJSONObject(i).optString("name", ""));
                    }
                }
                return names;
            }
        }, cb);
    }

    /** 删除一个文件（需要它的 sha） */
    public static void deleteFile(final String repo, final String branch, final String path, final String sha,
                                  final String message, final String token, final Cb<String> cb) {
        async(new Task<String>() {
            public String call() throws Exception {
                JSONObject body = new JSONObject();
                body.put("message", message);
                body.put("sha", sha);
                body.put("branch", branch);
                String json = request("/repos/" + repo + "/contents/" + encPath(path), "DELETE", token, body.toString());
                if (json == null) throw new ApiError(404, "仓库里没有 " + path);
                return path;
            }
        }, cb);
    }

    public static class RepoInfo {
        public String fullName;
        public String defaultBranch;
        public long size;
        public boolean isPrivate;
    }

    /** 仓库元信息：用来判断「空仓库」还是「分支名配错了」 */
    public static void repoInfo(final String repo, final String token, final Cb<RepoInfo> cb) {
        async(new Task<RepoInfo>() {
            public RepoInfo call() throws Exception {
                String json = request("/repos/" + repo, "GET", token, null);
                if (json == null) throw new ApiError(404, "仓库 " + repo + " 不存在，或钥匙没授权它");
                JSONObject o = new JSONObject(json);
                RepoInfo r = new RepoInfo();
                r.fullName = o.optString("full_name", repo);
                r.defaultBranch = o.optString("default_branch", "main");
                r.size = o.optLong("size", 0);
                r.isPrivate = o.optBoolean("private", false);
                return r;
            }
        }, cb);
    }

    /** 往空仓库里塞一份初始说明，顺带把 ideas/ 目录建出来 */
    public static void initRepo(final String repo, final String branch, final String token,
                                final Cb<String> cb) {
        async(new Task<String>() {
            public String call() throws Exception {
                String readme = "# 云笔记\n\n"
                        + "这个仓库用来存放我的想法笔记，全部是普通的 Markdown 文件。\n\n"
                        + "```\n"
                        + "ideas/            我自己写的\n"
                        + "Deepseek/         AI 写的（按项目分文件夹）\n"
                        + "Deepseek/回顾/    每周自动生成的回顾\n"
                        + ".versions/        旧版本归档\n"
                        + "```\n\n"
                        + "由「云笔记」Android 应用创建和同步。删除应用不会影响这里的任何文件。\n";

                JSONObject body = new JSONObject();
                body.put("message", "docs: 初始化仓库说明");
                body.put("content", Base64.encodeToString(readme.getBytes(StandardCharsets.UTF_8),
                        Base64.NO_WRAP));
                if (branch != null && !branch.isEmpty()) body.put("branch", branch);

                // 空仓库第一次 PUT 会创建初始提交和分支；失败就再试一次不带 branch
                String json;
                try {
                    json = request("/repos/" + repo + "/contents/README.md", "PUT", token, body.toString());
                } catch (ApiError e) {
                    if (e.status == 422) {
                        body.remove("branch");
                        json = request("/repos/" + repo + "/contents/README.md", "PUT", token, body.toString());
                    } else {
                        throw e;
                    }
                }
                if (json == null) throw new ApiError(404, "初始化失败");

                // 再建一个 ideas/ 占位，让目录先出现
                String ideasReadme = "# 我的想法\n\n每篇想法一个 .md 文件，可以按分类放进子文件夹。\n";
                JSONObject ib = new JSONObject();
                ib.put("message", "docs: 建立 ideas 目录");
                ib.put("content", Base64.encodeToString(ideasReadme.getBytes(StandardCharsets.UTF_8),
                        Base64.NO_WRAP));
                if (branch != null && !branch.isEmpty()) ib.put("branch", branch);
                try {
                    request("/repos/" + repo + "/contents/ideas/README.md", "PUT", token, ib.toString());
                } catch (Exception ignored) {
                    // 目录建失败不影响主流程
                }
                return "初始化完成";
            }
        }, cb);
    }

    /** 用钥匙给当前账号建一个仓库（需要 Administration 权限） */
    public static void createRepo(final String name, final boolean priv, final String token,
                                  final Cb<String> cb) {
        async(new Task<String>() {
            public String call() throws Exception {
                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("private", priv);
                body.put("auto_init", true);
                body.put("description", "云笔记：Markdown 想法仓库");
                String json = request("/user/repos", "POST", token, body.toString());
                if (json == null) throw new ApiError(404, "创建仓库失败");
                return new JSONObject(json).optString("full_name", "");
            }
        }, cb);
    }

    /** 用令牌查它是谁的 */
    public static void whoAmI(final String token, final Cb<String> cb) {
        async(new Task<String>() {
            public String call() throws Exception {
                String json = request("/user", "GET", token, null);
                if (json == null) throw new ApiError(401, "令牌无效");
                return new JSONObject(json).optString("login", "");
            }
        }, cb);
    }

    /** 检查 token 与仓库是否可用 */
    public static void checkConnection(final String repo, final String branch, final String token,
                                       final Cb<String> cb) {
        async(new Task<String>() {
            public String call() throws Exception {
                String json = request("/repos/" + repo, "GET", token, null);
                if (json == null) throw new ApiError(404, "仓库 " + repo + " 不存在，或令牌没有授权它");
                JSONObject o = new JSONObject(json);
                return o.optString("full_name", repo) + " · " + o.optString("default_branch", branch);
            }
        }, cb);
    }

    // ── 内部 ──────────────────────────────────────────────

    private interface Task<T> {
        T call() throws Exception;
    }

    private static <T> void async(final Task<T> task, final Cb<T> cb) {
        POOL.execute(new Runnable() {
            public void run() {
                try {
                    final T v = task.call();
                    MAIN.post(new Runnable() {
                        public void run() {
                            cb.ok(v);
                        }
                    });
                } catch (final Exception e) {
                    MAIN.post(new Runnable() {
                        public void run() {
                            cb.err(friendly(e));
                        }
                    });
                }
            }
        });
    }

    private static String friendly(Exception e) {
        if (e instanceof ApiError) return e.getMessage();
        String m = e.getMessage();
        if (m == null) m = e.getClass().getSimpleName();
        if (m.contains("Unable to resolve host")) return "无法解析 api.github.com，检查网络";
        if (m.contains("timed out") || m.contains("timeout")) return "请求超时，网络不稳";
        if (m.contains("SSL") || m.contains("Cert")) return "TLS 握手失败（可能有代理或证书问题）";
        return m;
    }

    private static String request(String path, String method, String token, String body) throws Exception {
        URL url = new URL(path.startsWith("http") ? path : API + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "ideas-android/1.0");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
        }

        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = readAll(in);

        if (code == 404) return null;
        if (code >= 400) throw new ApiError(code, explain(code, text));
        return text;
    }

    private static String explain(int code, String body) {
        String detail = "";
        try {
            if (body != null && body.length() > 0) detail = new JSONObject(body).optString("message", "");
        } catch (Exception ignored) {
            // 忽略
        }
        if (code == 401) return "令牌无效或已过期";
        if (code == 403) {
            if (detail.contains("rate limit")) return "触发了 GitHub 限流，稍后再试";
            return "权限不足（需要对 Contents 的 Read and write）" + (detail.isEmpty() ? "" : "：" + detail);
        }
        if (code == 409) return "冲突：远端在你读取后又被改过";
        if (code == 422) return "请求被拒绝" + (detail.isEmpty() ? "" : "：" + detail);
        return "HTTP " + code + (detail.isEmpty() ? "" : "：" + detail);
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

    private static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    private static String encPath(String p) {
        String[] parts = p.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(enc(parts[i]));
        }
        return sb.toString();
    }
}
