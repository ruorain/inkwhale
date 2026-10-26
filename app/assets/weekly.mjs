#!/usr/bin/env node
/**
 * 每周回顾生成器（在 GitHub Actions 里跑，不依赖本地环境）。
 *
 * 流程：
 *   1. 用 git 找出最近 7 天改动过的 markdown
 *   2. 拼成一次请求发给 DeepSeek
 *   3. 写到 Deepseek/回顾/<年>-W<周>.md
 *
 * 没有配置 DEEPSEEK_API_KEY 时不会让 workflow 失败，只打印提示。
 */
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const KEY = (process.env.DEEPSEEK_API_KEY || '').trim();
const ROOT = process.cwd();
const OUT_DIR = path.join(ROOT, 'Deepseek', '回顾');

const MAX_FILES = 40;
const MAX_CHARS_PER_FILE = 4000;
const MAX_TOTAL_CHARS = 40000;

function sh(cmd) {
  try {
    return execSync(cmd, { cwd: ROOT, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 });
  } catch {
    return '';
  }
}

function isoWeek(date) {
  const d = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  const day = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - day);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  const week = Math.ceil(((d - yearStart) / 86400000 + 1) / 7);
  return `${d.getUTCFullYear()}-W${String(week).padStart(2, '0')}`;
}

function collectChanged() {
  const raw = sh('git log --since="7 days ago" --name-only --pretty=format: -- "*.md"');
  const set = new Set();
  for (const line of raw.split('\n')) {
    const p = line.trim();
    if (!p || !p.toLowerCase().endsWith('.md')) continue;
    if (p.startsWith('.versions/') || p.startsWith('.trash/')) continue;
    if (p.startsWith('Deepseek/回顾/')) continue;
    if (!fs.existsSync(path.join(ROOT, p))) continue;
    set.add(p);
  }
  return [...set].sort();
}

function collectFallback() {
  const out = [];
  const walk = (dir) => {
    if (!fs.existsSync(dir)) return;
    for (const name of fs.readdirSync(dir)) {
      const full = path.join(dir, name);
      const stat = fs.statSync(full);
      if (stat.isDirectory()) {
        if (name === '.versions' || name === '.git' || name === 'node_modules') continue;
        walk(full);
      } else if (name.toLowerCase().endsWith('.md') && !full.includes('/Deepseek/回顾/')) {
        out.push({ p: path.relative(ROOT, full), t: stat.mtimeMs });
      }
    }
  };
  walk(path.join(ROOT, 'ideas'));
  walk(path.join(ROOT, 'Deepseek'));
  return out.sort((a, b) => b.t - a.t).slice(0, 15).map((x) => x.p);
}

function buildContext(files) {
  let total = 0;
  const parts = [];
  for (const p of files) {
    if (parts.length >= MAX_FILES || total >= MAX_TOTAL_CHARS) break;
    let text = '';
    try {
      text = fs.readFileSync(path.join(ROOT, p), 'utf8');
    } catch {
      continue;
    }
    if (text.length > MAX_CHARS_PER_FILE) text = text.slice(0, MAX_CHARS_PER_FILE) + '\n…（截断）';
    total += text.length;
    parts.push(`### 文件：${p}\n\n${text}\n`);
  }
  return parts.join('\n---\n\n');
}

async function askDeepSeek(prompt) {
  const res = await fetch('https://api.deepseek.com/chat/completions', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      authorization: `Bearer ${KEY}`,
    },
    body: JSON.stringify({
      model: 'deepseek-chat',
      temperature: 0.5,
      max_tokens: 3000,
      messages: [
        {
          role: 'system',
          content:
            '你是个人想法仓库的回顾助手。根据用户这一周的想法记录，写一份简洁的中文周报，用 Markdown。' +
            '结构固定为：## 这周在想什么（2-4 条主题归纳）、## 值得继续推进的（最多 3 条，说明理由）、' +
            '## 停在半路的（指出哪些只有开头没有结论）、## 下周建议（一条具体动作）。' +
            '只基于给定内容，不要编造；内容太少就直接说"这周记录很少"。控制在 500 字以内。',
        },
        { role: 'user', content: prompt },
      ],
    }),
    signal: AbortSignal.timeout(90000),
  });

  const text = await res.text();
  if (!res.ok) throw new Error(`DeepSeek ${res.status}: ${text.slice(0, 200)}`);
  const json = JSON.parse(text);
  const out = json?.choices?.[0]?.message?.content;
  if (!out) throw new Error('模型没有返回内容');
  return out.trim();
}

async function main() {
  if (!KEY) {
    console.log('没有配置 DEEPSEEK_API_KEY，跳过。');
    console.log('在仓库 Settings → Secrets and variables → Actions 里加一个名为 DEEPSEEK_API_KEY 的 secret。');
    return;
  }

  let files = collectChanged();
  let mode = '本周变更';
  if (files.length === 0) {
    files = collectFallback();
    mode = '最近文件';
  }
  console.log(`${mode}：${files.length} 个文件`);
  if (files.length === 0) {
    console.log('仓库里没有可回顾的 markdown，跳过。');
    return;
  }

  const context = buildContext(files);
  const week = isoWeek(new Date());

  const prompt = [
    `这是 ${week} 这一周的想法记录，共 ${files.length} 篇：`,
    '',
    context,
  ].join('\n');

  console.log('调用 DeepSeek…');
  const body = await askDeepSeek(prompt);

  const header = [
    `# 每周回顾 · ${week}`,
    '',
    `> 生成于 ${new Date().toISOString().slice(0, 16).replace('T', ' ')} UTC · 覆盖 ${files.length} 篇`,
    '',
    '## 本周涉及',
    '',
    ...files.map((f) => `- \`${f}\``),
    '',
    '---',
    '',
  ].join('\n');

  fs.mkdirSync(OUT_DIR, { recursive: true });
  const outPath = path.join(OUT_DIR, `${week}.md`);
  fs.writeFileSync(outPath, header + body + '\n', 'utf8');
  console.log(`已写入 ${path.relative(ROOT, outPath)}`);
}

main().catch((err) => {
  console.error('生成失败：', err.message);
  process.exitCode = 1;
});
