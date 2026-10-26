# 云笔记 · DeepSeek 插件

让 DeepSeek 直接读写你 GitHub 上的云笔记仓库：**检索**你写过的想法、**投稿**它自己写的 Markdown、**在本地生成周报**。

同一个文件既是 MCP server（挂进 DSH），也是 CLI（手动用）。

## 现在就能用的工具

挂进 DSH 后，任何会话里都会出现这 9 个工具：

| 工具 | 作用 |
|---|---|
| `ideas_status` | 检查仓库、分支、token 是否通，有多少 Markdown |
| `ideas_projects` | 列出项目/分类文件夹 |
| `ideas_list` | 列想法（标题、版本、大小、作者） |
| `ideas_search` | 全文检索，返回命中片断 |
| `ideas_read` | 读某篇全文 |
| `ideas_write` | 上传 Markdown，自动分项目、自动升版本 |
| `ideas_delete` | 删除（必须 `confirm: true`） |
| `note_recent` | 最近的想法（按路径排序，可限定项目） |
| `note_digest` | **本地生成周报并提交**，不依赖 GitHub Actions |

> `note_digest` 需要额外配一个 `deepseekKey`（见下面配置）。没有它也不影响其他工具。

## 目录约定

```
ideas/<分类>/<标题>.md          ← 你自己写的（手机 app 也写这里）
Deepseek/<项目>/<标题>.md       ← DeepSeek 写的，项目名取工作目录名
.versions/<原路径>/<标题>/V0.12.md  ← 旧版本自动归档
```

版本号永远 `0.x`：`V0.1 → V0.2 → … → V0.99 → V0.100`。
`mode=iterate` 会把旧内容先归档再升版本；`mode=overwrite` 就地覆盖。

## 配置（只差一步：给 token）

GitHub 不允许程序替你登录，令牌必须你本人生成一次。

**最省事的方式：**

```
右键 plugin\setup.ps1 → 使用 PowerShell 运行
```

它会打开令牌生成页，你粘贴回来，剩下全自动：验证权限 → 建私有仓库 `ruorain/ideas`（不存在的话）→ 铺 `ideas/` 和 `Deepseek/` → 写配置。

**手动方式：** 把下面写进 `%USERPROFILE%\.config\dsh-ideas\config.json`

```json
{
  "owner": "ruorain",
  "repo": "ideas",
  "branch": "main",
  "agentRoot": "Deepseek",
  "humanRoot": "ideas",
  "token": "github_pat_xxx"
}
```

令牌权限只需要 **Contents: Read and write**。

改完配置**不用重启 DSH**——MCP 进程每次调用都会重读这个文件。

## 命令行用法

不经过 MCP 也能直接用：

```powershell
.\run.cmd status
.\run.cmd list --project idea-repo-app
.\run.cmd search 动效 弹簧
.\run.cmd read "Deepseek/idea-repo-app/动效规格.md"
.\run.cmd write --project idea-repo-app --title 新方案 --file .\方案.md --mode iterate
```

## 注册位置

`%USERPROFILE%\.dsh\profiles\desktop\cordis.patch.yml` 末尾的 `mcp-ideas` 段。

## 安全

- token 只存在本机 `~/.config/dsh-ideas/config.json`（写入时尝试 `0600`）
- 插件不往 stdout 写日志（会破坏 MCP 帧），日志一律 stderr
- 写入前校验路径，拒绝 `..` 和绝对路径；标题里的 `/ \ : * ? " < > |` 会被替换成 `-`
- 删除必须显式 `confirm: true`
