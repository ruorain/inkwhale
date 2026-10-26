#!/usr/bin/env node
/**
 * dsh-ideas —— 让 DeepSeek 读写用户「想法」GitHub 仓库的 MCP server。
 *
 * 两种运行模式：
 *   node ideas-mcp.mjs               → MCP server（stdio，给 DSH 挂）
 *   node ideas-mcp.mjs <子命令> ...  → CLI（人/脚本直接用）
 *
 * 目录约定：
 *   用户自己的想法      ideas/<分类>/<标题>.md
 *   DeepSeek 写的        Deepseek/<项目>/<标题>.md
 *   旧版本归档          .versions/<原目录>/<标题>/V0.12.md
 *
 * 版本号永远是 0.x：V0.1 → V0.2 → … → V0.99 → V0.100
 *
 * 配置：~/.config/dsh-ideas/config.json （也可用 DSH_IDEAS_CONFIG 覆盖路径）
 * 日志：绝不写 stdout（会污染 MCP 帧），一律 stderr。
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const CONFIG_PATH =
  process.env.DSH_IDEAS_CONFIG || path.join(os.homedir(), '.config', 'dsh-ideas', 'config.json');
const API = 'https://api.github.com';
const STDERR = (...a) => process.stderr.write(a.join(' ') + '\n');

// ─────────────────────────────────────────────────────────── 配置
const DEFAULTS = {
  owner: 'ruorain',
  repo: 'ideas',
  branch: 'main',
  agentRoot: 'Deepseek', // DeepSeek 产出
  humanRoot: 'ideas', // 用户手写
  token: '',
  deepseekKey: '',
};

function loadConfig() {
  let raw = {};
  try {
    raw = JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8'));
  } catch {
    raw = {};
  }
  const cfg = { ...DEFAULTS, ...(raw && typeof raw === 'object' ? raw : {}) };
  cfg.token = String(cfg.token || process.env.GITHUB_TOKEN || process.env.GH_TOKEN || '').trim();
  cfg.owner = String(cfg.owner || '').trim();
  cfg.repo = String(cfg.repo || '').trim();
  cfg.branch = String(cfg.branch || 'main').trim();
  return cfg;
}

function saveConfig(patch) {
  let raw = {};
  try {
    raw = JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8'));
  } catch {
    raw = {};
  }
  const next = { ...DEFAULTS, ...raw, ...patch };
  fs.mkdirSync(path.dirname(CONFIG_PATH), { recursive: true });
  fs.writeFileSync(CONFIG_PATH, JSON.stringify(next, null, 2) + '\n', 'utf8');
  try {
    fs.chmodSync(CONFIG_PATH, 0o600);
  } catch {
    /* Windows 上可能不支持，忽略 */
  }
  return next;
}

// ─────────────────────────────────────────────────────────── GitHub
class GhError extends Error {
  constructor(status, message, body) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

function encodePath(p) {
  return String(p)
    .split('/')
    .map((s) => encodeURIComponent(s))
    .join('/');
}

async function gh(pathname, { method = 'GET', body, timeoutMs = 25000 } = {}) {
  const cfg = loadConfig();
  if (!cfg.token) {
    throw new Error(
      `没有 GitHub token。把 token 写进 ${CONFIG_PATH} 的 "token" 字段，或设置环境变量 GITHUB_TOKEN。`,
    );
  }
  const url = pathname.startsWith('http') ? pathname : API + pathname;
  let res;
  try {
    res = await fetch(url, {
      method,
      headers: {
        accept: 'application/vnd.github+json',
        authorization: `Bearer ${cfg.token}`,
        'x-github-api-version': '2022-11-28',
        'user-agent': 'dsh-ideas-mcp/1.0',
        ...(body ? { 'content-type': 'application/json' } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(timeoutMs),
    });
  } catch (err) {
    throw new Error(`连不上 GitHub（${err.name === 'TimeoutError' ? '超时' : err.message}）。检查网络或配置代理。`);
  }

  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = null;
  }

  if (res.status === 404) return null;
  if (!res.ok) {
    const detail = (json && (json.message || json.error)) || text.slice(0, 200) || res.statusText;
    const hint =
      res.status === 401
        ? 'token 无效或已过期'
        : res.status === 403
          ? '权限不足（需要对 Contents 的读写权限）或触发了限流'
          : res.status === 409
            ? 'sha 冲突：远端在你读取后又被改过'
            : res.status === 422
              ? '请求被拒绝（路径或内容不合法）'
              : '';
    throw new GhError(res.status, `GitHub ${res.status}：${detail}${hint ? `（${hint}）` : ''}`, json);
  }
  return json;
}

function repoBase(cfg) {
  return `/repos/${encodeURIComponent(cfg.owner)}/${encodeURIComponent(cfg.repo)}`;
}

async function getFile(repoPath) {
  const cfg = loadConfig();
  const r = await gh(`${repoBase(cfg)}/contents/${encodePath(repoPath)}?ref=${encodeURIComponent(cfg.branch)}`);
  if (!r || Array.isArray(r)) return null;
  const text = r.content ? Buffer.from(r.content, 'base64').toString('utf8') : '';
  return { path: repoPath, text, sha: r.sha, size: r.size ?? Buffer.byteLength(text, 'utf8') };
}

async function putFile(repoPath, text, sha, message) {
  const cfg = loadConfig();
  const body = {
    message,
    content: Buffer.from(text, 'utf8').toString('base64'),
    branch: cfg.branch,
  };
  if (sha) body.sha = sha;
  return gh(`${repoBase(cfg)}/contents/${encodePath(repoPath)}`, { method: 'PUT', body });
}

async function deleteFile(repoPath, sha, message) {
  const cfg = loadConfig();
  return gh(`${repoBase(cfg)}/contents/${encodePath(repoPath)}`, {
    method: 'DELETE',
    body: { message, sha, branch: cfg.branch },
  });
}

// ─────────────────────────────────────────────────────────── 目录索引
const TREE_TTL_MS = 45_000;
let treeCache = { at: 0, files: null };

function invalidateCache() {
  treeCache = { at: 0, files: null };
}

/** 一次拿到整棵文件树，只留 markdown，剔除归档/垃圾目录 */
async function listMarkdown(force = false) {
  const cfg = loadConfig();
  if (!force && treeCache.files && Date.now() - treeCache.at < TREE_TTL_MS) return treeCache.files;

  const r = await gh(`${repoBase(cfg)}/git/trees/${encodeURIComponent(cfg.branch)}?recursive=1`);
  const files = Array.isArray(r?.tree) ? r.tree : [];
  const skip = /(^|\/)(\.versions|\.trash|\.history|node_modules|\.git)(\/|$)/i;
  const md = files
    .filter((t) => t.type === 'blob' && /\.md$/i.test(t.path))
    .filter((t) => !skip.test(t.path))
    // 目录说明文件不算「想法」，检索和列表都跳过（手机端仍显示，用于撑起空文件夹）
    .filter((t) => !/(^|\/)README\.md$/i.test(t.path))
    .map((t) => ({ path: t.path, size: t.size || 0, sha: t.sha }))
    .sort((a, b) => a.path.localeCompare(b.path));

  treeCache = { at: Date.now(), files: md };
  return md;
}

function normalizeKey(s) {
  return String(s || '')
    .trim()
    .toLowerCase();
}

function matchesProject(filePath, project) {
  if (!project) return true;
  const p = normalizeKey(project);
  const parts = filePath.split('/').map(normalizeKey);
  return parts.includes(p) || normalizeKey(filePath).includes(p);
}

// ─────────────────────────────────────────────────────────── 文档解析
function titleOf(text, fallback) {
  const m = /^#\s+(.+?)\s*$/m.exec(text || '');
  return m ? m[1].trim() : fallback;
}

function versionOf(text) {
  const m = /^>\s*(V\d+(?:\.\d+)*)/m.exec(text || '');
  return m ? m[1] : '';
}

/** 永远 0.x：V0.7 → V0.8；万一遇到 V1.x 就 V1.(x+1)，不强行归零 */
function nextVersion(current) {
  const m = /V(\d+)\.(\d+)/.exec(current || '');
  if (!m) return 'V0.1';
  return `V${Number(m[1])}.${Number(m[2]) + 1}`;
}

function stamp() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function safeTitle(title) {
  const t = String(title || '')
    .replace(/[\\/:*?"<>|\u0000-\u001f]/g, '-')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/^\.+/, '')
    .slice(0, 80);
  return t || '未命名';
}

function safeProject(project) {
  const p = String(project || '')
    .replace(/[\\/:*?"<>|\u0000-\u001f]/g, '-')
    .trim()
    .replace(/^\.+/, '')
    .replace(/^\/+|\/+$/g, '')
    .slice(0, 60);
  return p || 'default';
}

function assertRepoPath(p) {
  const s = String(p || '').replace(/\\/g, '/');
  if (!s || s.includes('..') || s.startsWith('/') || /^[a-zA-Z]:/.test(s)) {
    throw new Error(`不安全的路径：${p}`);
  }
  return s.replace(/^\/+/, '');
}

/** 拼出「DeepSeek 产出」的目标路径 */
function agentPath(project, title) {
  const cfg = loadConfig();
  return `${cfg.agentRoot}/${safeProject(project)}/${safeTitle(title)}.md`;
}

/** 主文件 → 归档路径 */
function archivePath(mainPath, version) {
  const dir = path.posix.dirname(mainPath);
  const base = path.posix.basename(mainPath, '.md');
  const v = version.replace(/^V/, '');
  return `.versions/${dir}/${base}/V${v}.md`;
}

function buildDoc({ title, body, version, author }) {
  const clean = String(body || '')
    .replace(/^\uFEFF/, '')
    .trim();
  // 正文里如果已经带了 H1，就不再重复标题
  const hasH1 = /^#\s+/m.test(clean.split('\n')[0] || '');
  const head = hasH1 ? '' : `# ${title}\n\n`;
  return `${head}> ${version} · ${stamp()} · ${author}\n\n${clean}\n`;
}

// ─────────────────────────────────────────────────────────── 并发工具
async function mapLimit(items, limit, fn) {
  const out = new Array(items.length);
  let i = 0;
  const workers = Array.from({ length: Math.min(limit, Math.max(1, items.length)) }, async () => {
    for (;;) {
      const idx = i++;
      if (idx >= items.length) return;
      out[idx] = await fn(items[idx], idx);
    }
  });
  await Promise.all(workers);
  return out;
}

// ─────────────────────────────────────────────────────────── 业务操作
async function opStatus() {
  const cfg = loadConfig();
  const out = {
    configPath: CONFIG_PATH,
    repo: `${cfg.owner || '?'}/${cfg.repo || '?'}`,
    branch: cfg.branch,
    token: cfg.token ? `已配置（${cfg.token.slice(0, 4)}…${cfg.token.slice(-4)}）` : '未配置',
    agentRoot: cfg.agentRoot,
    humanRoot: cfg.humanRoot,
  };
  if (!cfg.token) {
    out.ok = false;
    out.hint = `把 fine-grained PAT（Contents: Read and write）写进 ${CONFIG_PATH}`;
    return out;
  }
  try {
    const me = await gh('/user');
    out.account = me?.login;
    out.authenticated = true;
  } catch (err) {
    out.ok = false;
    out.error = err.message;
    return out;
  }
  try {
    const files = await listMarkdown(true);
    out.markdownFiles = files.length;
    out.ok = true;
  } catch (err) {
    out.ok = false;
    out.error = err.message;
    out.hint = 'token 有效，但读不到仓库。确认仓库名拼写、且 token 勾了该仓库的 Contents 权限。';
  }
  return out;
}

async function opProjects() {
  const cfg = loadConfig();
  const files = await listMarkdown();
  const buckets = new Map();
  const add = (root, name, p) => {
    const key = `${root}/${name}`;
    if (!buckets.has(key)) buckets.set(key, { root, name, count: 0, paths: [] });
    const b = buckets.get(key);
    b.count += 1;
    if (b.paths.length < 3) b.paths.push(p);
  };
  for (const f of files) {
    const parts = f.path.split('/');
    if (parts.length >= 3 && [cfg.agentRoot, cfg.humanRoot].includes(parts[0])) add(parts[0], parts[1], f.path);
  }
  return [...buckets.values()].sort((a, b) => a.root.localeCompare(b.root) || a.name.localeCompare(b.name));
}

async function opList({ project, limit = 40 } = {}) {
  const cfg = loadConfig();
  const files = await listMarkdown();
  const picked = files.filter((f) => matchesProject(f.path, project)).slice(0, Math.max(1, Number(limit) || 40));

  const rows = await mapLimit(picked, 6, async (f) => {
    try {
      const doc = await getFile(f.path);
      return {
        path: f.path,
        title: titleOf(doc?.text, path.posix.basename(f.path, '.md')),
        version: versionOf(doc?.text) || '—',
        size: f.size,
        ownedBy: f.path.startsWith(cfg.agentRoot + '/') ? 'deepseek' : 'user',
      };
    } catch {
      return {
        path: f.path,
        title: path.posix.basename(f.path, '.md'),
        version: '?',
        size: f.size,
        ownedBy: f.path.startsWith(cfg.agentRoot + '/') ? 'deepseek' : 'user',
      };
    }
  });
  return { total: files.length, returned: rows.length, ideas: rows };
}

async function opSearch({ query, project, limit = 8 } = {}) {
  const q = String(query || '').trim();
  if (!q) throw new Error('query 不能为空');
  const terms = q.toLowerCase().split(/\s+/).filter(Boolean);
  const files = await listMarkdown();
  const pool = files.filter((f) => matchesProject(f.path, project));

  const hits = [];
  await mapLimit(pool, 6, async (f) => {
    let doc;
    try {
      doc = await getFile(f.path);
    } catch {
      return;
    }
    if (!doc?.text) return;
    const text = doc.text;
    const lower = text.toLowerCase();
    if (!terms.every((t) => lower.includes(t))) return;

    // 打分：标题命中权重高
    const title = titleOf(text, '');
    let score = 0;
    if (terms.every((t) => title.toLowerCase().includes(t))) score += 10;
    for (const t of terms) score += (lower.split(t).length - 1) * 0.5;

    // 取第一处命中的上下文
    let snippet = '';
    const at = lower.indexOf(terms[0]);
    if (at >= 0) {
      const from = Math.max(0, at - 60);
      const to = Math.min(text.length, at + 120);
      snippet = (from > 0 ? '…' : '') + text.slice(from, to).replace(/\s+/g, ' ').trim() + (to < text.length ? '…' : '');
    }
    hits.push({
      path: f.path,
      title: title || path.posix.basename(f.path, '.md'),
      version: versionOf(text) || '—',
      score: Math.round(score * 10) / 10,
      snippet,
    });
  });

  hits.sort((a, b) => b.score - a.score);
  return { query: q, scanned: pool.length, matches: hits.slice(0, Math.max(1, Number(limit) || 8)) };
}

async function opRead({ path: p } = {}) {
  const target = assertRepoPath(p);
  const doc = await getFile(target);
  if (!doc) throw new Error(`仓库里没有这个文件：${target}`);
  return {
    path: doc.path,
    size: doc.size,
    sha: doc.sha,
    title: titleOf(doc.text, path.posix.basename(doc.path, '.md')),
    version: versionOf(doc.text) || '—',
    content: doc.text,
  };
}

/**
 * 写入 DeepSeek 产出。mode:
 *   overwrite — 直接覆盖主文件
 *   iterate   — 主文件升版本，旧内容归档到 .versions/
 */
async function opWrite({ project, title, content, mode = 'iterate', version } = {}) {
  const cfg = loadConfig();
  const t = String(title || '').trim();
  const body = String(content || '').trim();
  if (!t) throw new Error('title 不能为空');
  if (!body) throw new Error('content 不能为空');
  if (!['overwrite', 'iterate'].includes(mode)) throw new Error("mode 只能是 overwrite 或 iterate");

  const mainPath = agentPath(project, t);
  const existing = await getFile(mainPath);
  const oldVersion = existing ? versionOf(existing.text) : '';
  const newVersion = version || (existing ? nextVersion(oldVersion) : 'V0.1');

  let archived = null;
  if (existing && mode === 'iterate') {
    const vForArchive = oldVersion || 'V0.0';
    archived = archivePath(mainPath, vForArchive);
    const already = await getFile(archived);
    if (!already) {
      await putFile(archived, existing.text, undefined, `chore: 归档 ${path.posix.basename(mainPath, '.md')} ${vForArchive}`);
    }
  }

  const doc = buildDoc({ title: t, body, version: newVersion, author: 'DeepSeek' });
  const verb = existing ? (mode === 'iterate' ? `迭代到 ${newVersion}` : '覆盖') : '新建';
  await putFile(mainPath, doc, existing?.sha, `docs(ideas): ${verb} ${t}`);

  invalidateCache();
  const after = await getFile(mainPath);
  return {
    ok: true,
    path: mainPath,
    version: newVersion,
    previousVersion: oldVersion || null,
    mode,
    archived,
    url: `https://github.com/${cfg.owner}/${cfg.repo}/blob/${cfg.branch}/${encodePath(mainPath)}`,
    bytes: after?.size ?? Buffer.byteLength(doc, 'utf8'),
  };
}

async function opDelete({ path: p, confirm } = {}) {
  if (confirm !== true) throw new Error('删除需要显式传 confirm: true');
  const target = assertRepoPath(p);
  const existing = await getFile(target);
  if (!existing) throw new Error(`仓库里没有这个文件：${target}`);
  await deleteFile(target, existing.sha, `chore(ideas): 删除 ${path.posix.basename(target, '.md')}`);
  invalidateCache();
  return { ok: true, deleted: target };
}

// ─────────────────────────────────────────────────────────── 工具表
const TOOLS = [
  {
    name: 'ideas_status',
    description:
      '检查想法仓库的连接状态：仓库、分支、token 是否有效、有多少 Markdown 文件。不确定配置对不对时先调它。',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'ideas_projects',
    description: '列出想法仓库里的项目/分类文件夹，以及每个下面有多少篇 markdown。',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'ideas_list',
    description: '列出发想（标题、版本号、路径、大小、作者是用户还是 DeepSeek）。',
    inputSchema: {
      type: 'object',
      properties: {
        project: { type: 'string', description: '可选，限定某个项目/分类' },
        limit: { type: 'number', description: '最多返回多少条，默认 40' },
      },
    },
  },
  {
    name: 'ideas_search',
    description:
      '在想法仓库里做全文检索（标题 + 正文）。多个关键词用空格分隔，全部命中才算匹配。返回命中片断，用来回答「我以前想过什么」「有没有相关的想法」。',
    inputSchema: {
      type: 'object',
      properties: {
        query: { type: 'string', description: '关键词或短语' },
        project: { type: 'string', description: '可选，限定某个项目/分类' },
        limit: { type: 'number', description: '最多返回多少条，默认 8' },
      },
      required: ['query'],
    },
  },
  {
    name: 'ideas_read',
    description: '读取某篇想法的完整 Markdown 原文。',
    inputSchema: {
      type: 'object',
      properties: { path: { type: 'string', description: '仓库内相对路径，例如 ideas/设计/离线优先.md' } },
      required: ['path'],
    },
  },
  {
    name: 'ideas_write',
    description:
      '把 DeepSeek 写好的 Markdown 上传到仓库的 Deepseek/<项目>/<标题>.md。mode=iterate 时自动升版本（永远 0.x）并把旧内容归档到 .versions/；mode=overwrite 直接覆盖。',
    inputSchema: {
      type: 'object',
      properties: {
        project: { type: 'string', description: '项目文件夹名，通常取当前工作目录名' },
        title: { type: 'string', description: '文档标题，会作为文件名' },
        content: { type: 'string', description: 'Markdown 正文（不含标题行也可以，会自动补 H1）' },
        mode: { type: 'string', enum: ['iterate', 'overwrite'], description: '默认 iterate' },
        version: { type: 'string', description: '可选，强制指定版本号，如 V0.5' },
      },
      required: ['project', 'title', 'content'],
    },
  },
  {
    name: 'ideas_delete',
    description: '删除一篇想法（必须显式 confirm:true）。会用一次提交完成，可从 GitHub 历史恢复。',
    inputSchema: {
      type: 'object',
      properties: {
        path: { type: 'string', description: '仓库内相对路径' },
        confirm: { type: 'boolean', description: '必须为 true 才会执行' },
      },
      required: ['path', 'confirm'],
    },
  },
  {
    name: 'note_recent',
    description: '列出最近的想法（按路径排序，可限定项目）。想快速知道「我最近记了什么」时用这个。',
    inputSchema: {
      type: 'object',
      properties: {
        project: { type: 'string', description: '可选，限定某个项目/分类' },
        limit: { type: 'number', description: '返回多少条，默认 10' },
      },
    },
  },
  {
    name: 'note_digest',
    description:
      '在本地生成一份周报草稿并直接提交到仓库的 Deepseek/回顾/ 下（不依赖 GitHub Actions）。会读最近的全部想法，让 DeepSeek 归纳主题、指出停摆的、给下周建议。需要配置 deepseekKey。',
    inputSchema: {
      type: 'object',
      properties: {
        limit: { type: 'number', description: '最多读多少篇，默认 20' },
        commit: { type: 'boolean', description: '是否直接提交，默认 true；false 只返回草稿' },
      },
    },
  },
];

const HANDLERS = {
  ideas_status: () => opStatus(),
  ideas_projects: () => opProjects(),
  ideas_list: (a) => opList(a),
  ideas_search: (a) => opSearch(a),
  ideas_read: (a) => opRead(a),
  ideas_write: (a) => opWrite(a),
  ideas_delete: (a) => opDelete(a),
  note_recent: (a) => opRecent(a),
  note_digest: (a) => opDigest(a),
};


// ─────────────────────────────────────────────────────────── 云笔记：最近 / 周报
async function opRecent({ project, limit = 10 } = {}) {
  const cfg = loadConfig();
  const files = await listMarkdown();
  const picked = files.filter((f) => matchesProject(f.path, project)).slice(-1 * Math.max(1, Number(limit) || 10));
  const rows = picked.map((f) => {
    const base = path.posix.basename(f.path, '.md');
    return {
      path: f.path,
      title: base,
      size: f.size,
      ownedBy: f.path.startsWith(cfg.agentRoot + '/') ? 'deepseek' : 'user',
    };
  }).reverse();
  return { total: files.length, returned: rows.length, ideas: rows };
}

async function opDigest({ limit = 20, commit = true } = {}) {
  const cfg = loadConfig();
  const key = (cfg.deepseekKey || process.env.DEEPSEEK_API_KEY || '').trim();
  if (!key) {
    throw new Error('没有 DeepSeek key：在 ' + CONFIG_PATH + ' 里加 "deepseekKey"，或设置环境变量 DEEPSEEK_API_KEY');
  }

  const files = await listMarkdown();
  if (files.length === 0) throw new Error('仓库里还没有 markdown');
  const pool = files.slice(-1 * Math.max(1, Number(limit) || 20));

  const parts = [];
  for (const f of pool) {
    try {
      const doc = await getFile(f.path);
      if (!doc?.text) continue;
      const text = doc.text.length > 4000 ? doc.text.slice(0, 4000) + '\n…（截断）' : doc.text;
      parts.push('### ' + f.path + '\n\n' + text);
    } catch {
      // 单篇读失败跳过
    }
  }
  if (parts.length === 0) throw new Error('没有可读的内容');

  const prompt = '这是用户最近的想法记录，共 ' + parts.length + ' 篇：\n\n' + parts.join('\n\n---\n\n');

  const res = await fetch('https://api.deepseek.com/chat/completions', {
    method: 'POST',
    headers: { 'content-type': 'application/json', authorization: 'Bearer ' + key },
    body: JSON.stringify({
      model: 'deepseek-flash',
      temperature: 0.5,
      max_tokens: 2500,
      messages: [
        {
          role: 'system',
          content:
            '你是个人想法仓库的回顾助手。根据这些记录写一份中文周报，Markdown 格式，结构固定：' +
            '## 这周在想什么（2-4 条主题归纳）、## 值得继续推进的（最多 3 条，说明理由）、' +
            '## 停在半路的（指出只有开头没有结论的）、## 下周建议（一条具体动作）。' +
            '只基于给定内容，不要编造，控制在 500 字内。',
        },
        { role: 'user', content: prompt },
      ],
    }),
    signal: AbortSignal.timeout(120000),
  });

  const raw = await res.text();
  if (!res.ok) throw new Error('DeepSeek ' + res.status + ': ' + raw.slice(0, 200));
  const body = JSON.parse(raw)?.choices?.[0]?.message?.content;
  if (!body) throw new Error('模型没有返回内容');

  const now = new Date();
  const week = isoWeekName(now);
  const outPath = cfg.agentRoot + '/回顾/' + week + '.md';
  const header = '# 每周回顾 · ' + week + '\n\n> 本地生成 · ' + stamp() + ' · 覆盖 ' + parts.length + ' 篇\n\n';

  if (commit === false) {
    return { path: outPath, committed: false, content: header + body };
  }

  const existing = await getFile(outPath);
  await putFile(outPath, header + body, existing?.sha, 'docs: 每周回顾 ' + week);
  invalidateCache();
  return { ok: true, path: outPath, committed: true, covered: parts.length };
}

function isoWeekName(d) {
  const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
  const day = t.getUTCDay() || 7;
  t.setUTCDate(t.getUTCDate() + 4 - day);
  const yearStart = new Date(Date.UTC(t.getUTCFullYear(), 0, 1));
  const week = Math.ceil(((t - yearStart) / 86400000 + 1) / 7);
  return t.getUTCFullYear() + '-W' + String(week).padStart(2, '0');
}

async function callTool(name, args) {
  const fn = HANDLERS[name];
  if (!fn) throw new Error(`未知工具：${name}`);
  return fn(args || {});
}

// ─────────────────────────────────────────────────────────── MCP stdio
function send(obj) {
  process.stdout.write(JSON.stringify(obj) + '\n');
}

function reply(id, result) {
  send({ jsonrpc: '2.0', id, result });
}

function replyError(id, code, message) {
  send({ jsonrpc: '2.0', id, error: { code, message } });
}

async function handleMessage(msg) {
  const { id, method, params } = msg || {};
  const isNotification = id === undefined || id === null;

  try {
    switch (method) {
      case 'initialize':
        reply(id, {
          protocolVersion: '2024-11-05',
          capabilities: { tools: { listChanged: false } },
          serverInfo: { name: 'dsh-ideas', version: '1.0.0' },
          instructions:
            '这是用户私人「想法」GitHub 仓库的读写通道。检索用 ideas_search，上传自己写的 markdown 用 ideas_write（默认放到 Deepseek/<项目>/ 并自动升版本）。',
        });
        return;
      case 'notifications/initialized':
      case 'notifications/cancelled':
        return;
      case 'ping':
        if (!isNotification) reply(id, {});
        return;
      case 'tools/list':
        reply(id, { tools: TOOLS });
        return;
      case 'tools/call': {
        const raw = await callTool(params?.name, params?.arguments || {});
        const text = typeof raw === 'string' ? raw : JSON.stringify(raw, null, 2);
        reply(id, { content: [{ type: 'text', text }] });
        return;
      }
      default:
        if (isNotification) return;
        replyError(id, -32601, `未实现的方法：${method}`);
    }
  } catch (err) {
    // 工具层的失败按 MCP 约定放在 result.isError 里，而不是 JSON-RPC error
    if (method === 'tools/call') {
      reply(id, { content: [{ type: 'text', text: `错误：${err.message}` }], isError: true });
    } else if (!isNotification) {
      replyError(id, -32603, err.message);
    }
    STDERR(`[dsh-ideas] ${method} 失败：${err.message}`);
  }
}

function startServer() {
  let buf = '';
  process.stdin.setEncoding('utf8');
  process.stdin.on('data', (chunk) => {
    buf += chunk;
    let idx;
    while ((idx = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, idx).trim();
      buf = buf.slice(idx + 1);
      if (!line) continue;
      let msg;
      try {
        msg = JSON.parse(line);
      } catch {
        replyError(null, -32700, 'JSON 解析失败');
        continue;
      }
      void handleMessage(msg);
    }
  });
  process.stdin.on('end', () => process.exit(0));
  STDERR('[dsh-ideas] MCP server 已启动（stdio）');
}

// ─────────────────────────────────────────────────────────── CLI
function flag(args, name, fallback) {
  const i = args.indexOf(`--${name}`);
  if (i < 0) return fallback;
  const v = args[i + 1];
  return v === undefined || v.startsWith('--') ? true : v;
}

function positional(args) {
  return args.filter((a, i) => !a.startsWith('--') && !(i > 0 && args[i - 1].startsWith('--')));
}

async function runCli(argv) {
  const [cmd, ...rest] = argv;
  const print = (v) => console.log(typeof v === 'string' ? v : JSON.stringify(v, null, 2));

  switch (cmd) {
    case 'status':
      return print(await opStatus());
    case 'projects':
      return print(await opProjects());
    case 'list':
      return print(await opList({ project: flag(rest, 'project'), limit: Number(flag(rest, 'limit', 40)) }));
    case 'search': {
      const q = positional(rest).join(' ');
      if (!q) throw new Error('用法：search <关键词> [--project X]');
      return print(await opSearch({ query: q, project: flag(rest, 'project'), limit: Number(flag(rest, 'limit', 8)) }));
    }
    case 'read': {
      const p = positional(rest)[0];
      if (!p) throw new Error('用法：read <路径>');
      return print(await opRead({ path: p }));
    }
    case 'write': {
      const project = flag(rest, 'project', path.basename(process.cwd()));
      const title = flag(rest, 'title');
      const file = flag(rest, 'file');
      if (!title) throw new Error('用法：write --project X --title Y --file a.md [--mode iterate|overwrite]');
      const content = file && file !== true ? fs.readFileSync(file, 'utf8') : '';
      return print(
        await opWrite({ project, title, content, mode: flag(rest, 'mode', 'iterate'), version: flag(rest, 'version') }),
      );
    }
    case 'delete': {
      const p = positional(rest)[0];
      if (!p) throw new Error('用法：delete <路径> --confirm');
      return print(await opDelete({ path: p, confirm: rest.includes('--confirm') }));
    }
    case 'recent':
      return print(await opRecent({ project: flag(rest, 'project'), limit: Number(flag(rest, 'limit', 10)) }));
    case 'digest':
      return print(await opDigest({
        limit: Number(flag(rest, 'limit', 20)),
        commit: !rest.includes('--dry'),
      }));
    case 'tools':
      return print(TOOLS.map((t) => `${t.name} — ${t.description}`));
    case 'help':
    case undefined:
      return print(
        [
          'dsh-ideas CLI',
          '',
          '  status                          配置与连通性',
          '  projects                        项目/分类列表',
          '  list [--project X] [--limit N]  列出想法',
          '  search <关键词> [--project X]    全文检索',
          '  read <路径>                      读原文',
          '  write --project X --title Y --file a.md [--mode iterate|overwrite]',
          '  delete <路径> --confirm          删除',
          '  recent [--project X] [--limit N] 最近的想法',
          '  digest [--limit N] [--dry]       生成周报（--dry 只看不提交）',
          '  tools                           列出 MCP 工具',
          '',
          `配置：${CONFIG_PATH}`,
        ].join('\n'),
      );
    default:
      throw new Error(`未知命令：${cmd}（试试 help）`);
  }
}

// ─────────────────────────────────────────────────────────── 入口
const argv = process.argv.slice(2);
if (argv.length === 0) {
  startServer();
} else {
  runCli(argv)
    .then(() => process.exit(0))
    .catch((err) => {
      console.error(err.message);
      process.exit(1);
    });
}
