#!/usr/bin/env node
/**
 * 把本地文件推到想法仓库的指定路径（一次性工具，不进 app）。
 *
 *   node tools/push-to-repo.mjs <本地文件> <仓库内路径> "<提交信息>"
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const CONFIG = path.join(os.homedir(), '.config', 'dsh-ideas', 'config.json');
const cfg = JSON.parse(fs.readFileSync(CONFIG, 'utf8'));
const TOKEN = cfg.token;

const [localf, repoPath, message] = process.argv.slice(2);
if (!localf || !repoPath) {
  console.error('用法：node push-to-repo.mjs <本地文件> <仓库路径> "<提交信息>"');
  process.exit(1);
}

const API = `https://api.github.com/repos/${cfg.owner}/${cfg.repo}/contents/`;
const enc = (p) => p.split('/').map(encodeURIComponent).join('/');
const headers = {
  accept: 'application/vnd.github+json',
  authorization: `Bearer ${TOKEN}`,
  'x-github-api-version': '2022-11-28',
  'user-agent': 'dsh-push',
  'content-type': 'application/json',
};

async function getSha() {
  const r = await fetch(API + enc(repoPath) + `?ref=${cfg.branch || 'main'}`, { headers });
  if (r.status === 404) return null;
  if (!r.ok) throw new Error(`GET ${r.status}: ${await r.text()}`);
  const j = await r.json();
  return j && j.sha ? j.sha : null;
}

async function main() {
  const content = fs.readFileSync(localf);
  const sha = await getSha();
  const body = {
    message: message || `docs: 更新 ${repoPath}`,
    content: content.toString('base64'),
    branch: cfg.branch || 'main',
  };
  if (sha) body.sha = sha;

  const r = await fetch(API + enc(repoPath), {
    method: 'PUT',
    headers,
    body: JSON.stringify(body),
  });
  const text = await r.text();
  if (!r.ok) throw new Error(`PUT ${r.status}: ${text.slice(0, 300)}`);
  console.log(`OK ${repoPath} (${sha ? '更新' : '新建'})`);
}

main().catch((e) => {
  console.error('失败：', e.message);
  process.exit(1);
});
