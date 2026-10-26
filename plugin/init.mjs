#!/usr/bin/env node
/**
 * 一次性初始化：验证 token → 建仓库（若不存在）→ 建目录骨架 → 写配置。
 *
 *   set GITHUB_TOKEN=github_pat_xxx   （PowerShell: $env:GITHUB_TOKEN="..."）
 *   node init.mjs
 *
 * 可选参数：
 *   --repo <名字>     默认 ideas
 *   --owner <用户名>  默认用 token 所属账号
 *   --public          建公开仓库（默认私有）
 *   --dry-run         只检查，不写任何东西
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const CONFIG_PATH =
  process.env.DSH_IDEAS_CONFIG || path.join(os.homedir(), '.config', 'dsh-ideas', 'config.json');
const API = 'https://api.github.com';

const argv = process.argv.slice(2);
const flag = (n, d) => {
  const i = argv.indexOf(`--${n}`);
  return i >= 0 ? argv[i + 1] ?? true : d;
};
const has = (n) => argv.includes(`--${n}`);

/** 环境变量优先；没有就读配置文件里已经填好的 token */
function tokenFromConfig() {
  try {
    const raw = JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8'));
    const t = String(raw?.token || '').trim();
    return /^(github_pat_|ghp_)/.test(t) ? t : '';
  } catch {
    return '';
  }
}

const TOKEN = String(process.env.GITHUB_TOKEN || process.env.GH_TOKEN || tokenFromConfig() || '').trim();
const DRY = has('dry-run');
const PUBLIC = has('public');
const REPO = String(flag('repo', 'ideas'));
const OWNER_ARG = flag('owner', '');

function die(msg) {
  console.error(`\n  ✗ ${msg}\n`);
  process.exit(1);
}

async function gh(pathname, { method = 'GET', body } = {}) {
  const res = await fetch(API + pathname, {
    method,
    headers: {
      accept: 'application/vnd.github+json',
      authorization: `Bearer ${TOKEN}`,
      'x-github-api-version': '2022-11-28',
      'user-agent': 'dsh-ideas-init',
      ...(body ? { 'content-type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(25000),
  });
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = null;
  }
  if (res.status === 404) return { status: 404, json: null };
  if (!res.ok) {
    const msg = (json && json.message) || text.slice(0, 200);
    throw new Error(`GitHub ${res.status}: ${msg}`);
  }
  return { status: res.status, json };
}

async function ensureFile(owner, repo, branch, filePath, content, message) {
  const enc = filePath.split('/').map(encodeURIComponent).join('/');
  const existing = await gh(`/repos/${owner}/${repo}/contents/${enc}?ref=${encodeURIComponent(branch)}`);
  if (existing.json && !Array.isArray(existing.json)) {
    console.log(`  · 已存在 ${filePath}`);
    return false;
  }
  if (DRY) {
    console.log(`  · [dry-run] 会创建 ${filePath}`);
    return false;
  }
  await gh(`/repos/${owner}/${repo}/contents/${enc}`, {
    method: 'PUT',
    body: { message, content: Buffer.from(content, 'utf8').toString('base64'), branch },
  });
  console.log(`  + 创建 ${filePath}`);
  return true;
}

async function main() {
  console.log('\n  dsh-ideas 初始化\n');

  if (!TOKEN) {
    die(
      [
        '没有找到 token。',
        '',
        '  1. 打开 https://github.com/settings/personal-access-tokens/new',
        '  2. Token name 随便填（如 dsh-ideas）',
        '  3. Expiration 选 1 year',
        '  4. Repository access → Only select repositories → 选好仓库（新仓库就先选 All repositories）',
        '  5. Permissions → Repository permissions → Contents 设为 Read and write',
        '  6. 生成后复制，然后在 PowerShell 里执行：',
        '',
        '       $env:GITHUB_TOKEN="github_pat_xxx"',
        '       node init.mjs',
        '',
        '  token 只会写进本机配置文件，不会进入任何仓库。',
      ].join('\n'),
    );
  }

  console.log('  → 验证 token');
  let me;
  try {
    me = (await gh('/user')).json;
  } catch (err) {
    die(`token 无效：${err.message}`);
  }
  const owner = OWNER_ARG || me.login;
  console.log(`  ✓ 账号 ${me.login}${owner !== me.login ? `（仓库归属 ${owner}）` : ''}`);

  console.log(`  → 检查仓库 ${owner}/${REPO}`);
  let repoInfo = (await gh(`/repos/${owner}/${REPO}`)).json;
  let created = false;

  if (!repoInfo) {
    if (DRY) {
      console.log(`  · [dry-run] 仓库不存在，会创建 ${PUBLIC ? '公开' : '私有'}仓库`);
    } else {
      console.log(`  → 创建${PUBLIC ? '公开' : '私有'}仓库 ${owner}/${REPO}`);
      const out = await gh(owner === me.login ? '/user/repos' : `/orgs/${owner}/repos`, {
        method: 'POST',
        body: {
          name: REPO,
          private: !PUBLIC,
          auto_init: true,
          description: '想法收集器：Markdown 存储，DeepSeek 可检索与投稿',
        },
      });
      repoInfo = out.json;
      created = true;
      console.log('  ✓ 仓库已创建');
      await new Promise((r) => setTimeout(r, 1500)); // 等 auto_init 落分支
    }
  } else {
    console.log('  ✓ 仓库已存在');
  }

  if (!DRY && repoInfo) {
    const branch = repoInfo.default_branch || 'main';
    console.log(`  → 铺目录骨架（分支 ${branch}）`);
    await ensureFile(
      owner,
      REPO,
      branch,
      'README.md',
      [
        '# 想法',
        '',
        '存放个人想法的仓库，用 Markdown 组织。',
        '',
        '```',
        'ideas/            我自己的想法',
        'Deepseek/         DeepSeek 写的东西（按项目分文件夹）',
        '.versions/        历史版本归档（自动生成）',
        '```',
        '',
        '由 Android 客户端和 DeepSeek MCP 插件共同读写。',
        '',
      ].join('\n'),
      'docs: 初始化仓库说明',
    );
    await ensureFile(
      owner,
      REPO,
      branch,
      'ideas/README.md',
      '# 我自己的想法\n\n每篇想法一个 `.md` 文件，可以按分类放进子文件夹。\n',
      'docs: 建立 ideas 目录',
    );
    await ensureFile(
      owner,
      REPO,
      branch,
      'Deepseek/README.md',
      '# DeepSeek 产出\n\n按项目分文件夹存放，例如 `Deepseek/idea-repo-app/xxx.md`。\n旧版本自动归档到仓库根的 `.versions/` 下。\n',
      'docs: 建立 Deepseek 目录',
    );
  }

  if (!DRY) {
    let prev = {};
    try {
      prev = JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8'));
    } catch {
      prev = {};
    }
    const next = {
      ...prev,
      owner,
      repo: REPO,
      branch: repoInfo?.default_branch || prev.branch || 'main',
      agentRoot: prev.agentRoot || 'Deepseek',
      humanRoot: prev.humanRoot || 'ideas',
      token: TOKEN,
    };
    fs.mkdirSync(path.dirname(CONFIG_PATH), { recursive: true });
    fs.writeFileSync(CONFIG_PATH, JSON.stringify(next, null, 2) + '\n', 'utf8');
    try {
      fs.chmodSync(CONFIG_PATH, 0o600);
    } catch {
      /* Windows 忽略 */
    }
    console.log(`  ✓ 配置已写入 ${CONFIG_PATH}`);
  }

  console.log('\n  下一步：\n');
  console.log('    1. 重启 DeepSeek Harness（加载 MCP 插件）');
  console.log('    2. 在任意会话里让我跑 ideas_status 验证\n');
  if (created) console.log(`    仓库地址 https://github.com/${owner}/${REPO}\n`);
}

main().catch((err) => {
  console.error(`\n  ✗ 失败：${err.message}\n`);
  process.exit(1);
});
