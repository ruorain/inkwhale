package com.ruorain.ideas;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 把内置的 GitHub Actions 周报配置部署到用户自己的仓库，让「自动周报」对任何人都能用。 */
public final class CiDeploy {

    public static final String WORKFLOW_PATH = ".github/workflows/weekly.yml";
    public static final String SCRIPT_PATH = "scripts/weekly.mjs";

    private CiDeploy() {}

    public interface Cb {
        void ok();

        void err(String message);
    }

    public static void deploy(final Context c, final Cb cb) {
        final String repo = Store.repo(c);
        final String branch = Store.branch(c);
        final String token = Store.token(c);
        if (token.isEmpty()) {
            cb.err("还没有配置访问令牌");
            return;
        }

        final String yml = readAsset(c, "weekly.yml");
        final String mjs = readAsset(c, "weekly.mjs");
        if (yml == null || mjs == null) {
            cb.err("内置的配置文件读不出来");
            return;
        }

        // 先写脚本，再写 workflow（workflow 需要额外的 Workflows 权限，单独判错）
        GitHubApi.writeFile(repo, branch, SCRIPT_PATH, mjs, null,
                "ci: 部署周报生成脚本", token, new GitHubApi.Cb<GitHubApi.PutResult>() {
                    @Override
                    public void ok(GitHubApi.PutResult r) {
                        GitHubApi.writeFile(repo, branch, WORKFLOW_PATH, yml, null,
                                "ci: 部署每周回顾 workflow", token,
                                new GitHubApi.Cb<GitHubApi.PutResult>() {
                                    @Override
                                    public void ok(GitHubApi.PutResult r2) {
                                        cb.ok();
                                    }

                                    @Override
                                    public void err(String message) {
                                        if (message.contains("403") || message.contains("权限")) {
                                            cb.err("脚本写好了，但 workflow 被拒：\n"
                                                    + "钥匙还需要勾上「Workflows」权限（Read and write）。\n"
                                                    + "去 GitHub 的令牌设置里补上再试。");
                                        } else {
                                            cb.err(message);
                                        }
                                    }
                                });
                    }

                    @Override
                    public void err(String message) {
                        cb.err(message);
                    }
                });
    }

    private static String readAsset(Context c, String name) {
        try {
            InputStream in = c.getAssets().open(name);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
