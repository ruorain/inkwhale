package com.ruorain.ideas;

import android.app.Activity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 联网后自动把本地草稿推回仓库。
 *
 * 推送前会比对「草稿的基线版本」和远端当前内容：
 *   - 远端没变 → 直接覆盖
 *   - 远端变了 → 自动合并（调模型），合并结果覆盖
 *   - 没有 AI key 或合并失败 → 本地内容照写，远端旧内容归档到 .versions，谁都不丢
 */
public final class DraftSync {

    private static boolean running;

    private DraftSync() {}

    public static boolean isRunning() {
        return running;
    }

    public static void pushAll(final Activity a, final boolean dark, final Runnable onDone) {
        if (running || a == null || a.isFinishing()) return;
        final Map<String, String> drafts = Store.allDrafts(a);
        if (drafts.isEmpty()) {
            if (onDone != null) onDone.run();
            return;
        }

        running = true;
        Banner.show(a, dark, "正在同步 " + drafts.size() + " 篇本地修改…", true);

        final List<Map.Entry<String, String>> list =
                new ArrayList<Map.Entry<String, String>>(drafts.entrySet());
        pushOne(a, dark, list, 0, 0, onDone);
    }

    private static void pushOne(final Activity a, final boolean dark,
                                final List<Map.Entry<String, String>> list,
                                final int index, final int okCount, final Runnable onDone) {
        if (index >= list.size()) {
            finish(a, dark, okCount, onDone);
            return;
        }
        if (a.isFinishing()) {
            running = false;
            return;
        }

        final String path = list.get(index).getKey();
        final String text = list.get(index).getValue();
        final String repo = Store.repo(a);
        final String branch = Store.branch(a);
        final String token = Store.token(a);
        final String name = path.substring(path.lastIndexOf('/') + 1);

        if (token.isEmpty()) {
            running = false;
            return;
        }

        GitHubApi.readFile(repo, branch, path, token, new GitHubApi.Cb<GitHubApi.Doc>() {
            @Override
            public void ok(final GitHubApi.Doc doc) {
                String base = Store.draftBase(a, path);
                boolean remoteChanged = base != null && !base.isEmpty() && !base.equals(doc.text);
                if (remoteChanged) {
                    mergeThenWrite(a, dark, list, index, okCount, onDone,
                            path, text, base, doc.text, doc.sha, repo, branch, token);
                    return;
                }
                write(a, dark, list, index, okCount, onDone, path, text, doc.sha,
                        repo, branch, token, "docs: 同步本地修改 " + name);
            }

            @Override
            public void err(String message) {
                write(a, dark, list, index, okCount, onDone, path, text, null,
                        repo, branch, token, "docs: 新建 " + name);
            }
        });
    }

    /** 远端被改过：让模型把两边合起来 */
    private static void mergeThenWrite(final Activity a, final boolean dark,
                                       final List<Map.Entry<String, String>> list,
                                       final int index, final int okCount, final Runnable onDone,
                                       final String path, final String local, final String base,
                                       final String remote, final String sha,
                                       final String repo, final String branch, final String token) {
        final String name = path.substring(path.lastIndexOf('/') + 1);
        AiClient.Model m = AiClient.currentModel(a);
        String key = AiClient.keyFor(a, m.provider);
        if (key == null || key.isEmpty()) {
            archiveRemoteThenWrite(a, dark, list, index, okCount, onDone,
                    path, local, remote, sha, repo, branch, token);
            return;
        }

        Banner.show(a, dark, "「" + name + "」两边都改过，正在合并…", true);

        JSONArray msgs = new JSONArray();
        try {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", "你要合并同一个 Markdown 文件的两个修改版本。"
                    + "规则：保留两边的所有新增内容，不丢失任何信息；重复的部分合并成一处；"
                    + "保持原有标题层级和格式。只输出合并后的 Markdown 正文，不要解释。");
            msgs.put(sys);

            JSONObject usr = new JSONObject();
            usr.put("role", "user");
            usr.put("content", "【共同祖先】\n" + clamp(base)
                    + "\n\n【远端版本（别人改的）】\n" + clamp(remote)
                    + "\n\n【本地版本（我刚改的）】\n" + clamp(local));
            msgs.put(usr);
        } catch (Exception ignored) {
            // 组装失败就退回保底方案
        }

        AiClient.chat(a, m, AiClient.EFFORT_OFF, msgs, new AiClient.Cb() {
            @Override
            public void ok(String content, String reasoning, long elapsedMs) {
                if (content == null || content.trim().isEmpty()) {
                    archiveRemoteThenWrite(a, dark, list, index, okCount, onDone,
                            path, local, remote, sha, repo, branch, token);
                    return;
                }
                write(a, dark, list, index, okCount, onDone, path, content.trim(), sha,
                        repo, branch, token, "docs: 自动合并 " + name);
            }

            @Override
            public void err(String message) {
                archiveRemoteThenWrite(a, dark, list, index, okCount, onDone,
                        path, local, remote, sha, repo, branch, token);
            }
        });
    }

    private static String clamp(String s) {
        if (s == null) return "";
        return s.length() > 8000 ? s.substring(0, 8000) + "\n…（截断）" : s;
    }

    /** 合并不了时的保底：远端旧内容先归档，再用本地覆盖 */
    private static void archiveRemoteThenWrite(final Activity a, final boolean dark,
                                               final List<Map.Entry<String, String>> list,
                                               final int index, final int okCount, final Runnable onDone,
                                               final String path, final String local, final String remote,
                                               final String sha, final String repo, final String branch,
                                               final String token) {
        String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
        String baseName = path.substring(path.lastIndexOf('/') + 1);
        if (baseName.toLowerCase().endsWith(".md")) {
            baseName = baseName.substring(0, baseName.length() - 3);
        }
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                .format(new java.util.Date());
        String archivePath = ".versions/" + dir + "/" + baseName + "/conflict-" + stamp + ".md";

        GitHubApi.writeFile(repo, branch, archivePath, remote, null,
                "chore: 归档远端版本 " + baseName, token,
                new GitHubApi.Cb<GitHubApi.PutResult>() {
                    @Override
                    public void ok(GitHubApi.PutResult r) {
                        write(a, dark, list, index, okCount, onDone, path, local, sha,
                                repo, branch, token, "docs: 覆盖并归档远端版本");
                    }

                    @Override
                    public void err(String m) {
                        write(a, dark, list, index, okCount, onDone, path, local, sha,
                                repo, branch, token, "docs: 覆盖本地版本");
                    }
                });
    }

    private static void write(final Activity a, final boolean dark,
                              final List<Map.Entry<String, String>> list,
                              final int index, final int okCount, final Runnable onDone,
                              final String path, final String text, String sha,
                              String repo, String branch, String token, String message) {
        GitHubApi.writeFile(repo, branch, path, text, sha, message, token,
                new GitHubApi.Cb<GitHubApi.PutResult>() {
                    @Override
                    public void ok(GitHubApi.PutResult r) {
                        Store.clearDraft(a, path);
                        Store.cacheFile(a, path, text);
                        pushOne(a, dark, list, index + 1, okCount + 1, onDone);
                    }

                    @Override
                    public void err(String m) {
                        // 保留草稿，下次再试
                        pushOne(a, dark, list, index + 1, okCount, onDone);
                    }
                });
    }

    private static void finish(Activity a, boolean dark, int okCount, Runnable onDone) {
        running = false;
        Banner.hide(a);
        if (okCount > 0) {
            Banner.show(a, dark, "已同步 " + okCount + " 篇本地修改", false);
        }
        if (onDone != null) onDone.run();
    }
}
