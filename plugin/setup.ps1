# 想法仓库 · 一次性配置
# 右键「使用 PowerShell 运行」，或在 PowerShell 里执行：
#   powershell -ExecutionPolicy Bypass -File .\setup.ps1
#
# 它会把 token 交给 init.mjs：验证权限 → 仓库不存在就创建 → 铺好目录 → 写配置文件。
# token 不会打印在屏幕上，也不会写进任何仓库。

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$node = if ($env:DSH_NODE) { $env:DSH_NODE } else { 'node' }

Write-Host ''
Write-Host '  ─────────────────────────────────────────────' -ForegroundColor DarkGray
Write-Host '   想法仓库 · 配置向导' -ForegroundColor White
Write-Host '  ─────────────────────────────────────────────' -ForegroundColor DarkGray
Write-Host ''

if (-not (Test-Path $node)) { throw "找不到 Node：$node" }

Write-Host '  第一步：生成一个令牌（如果还没有）' -ForegroundColor Cyan
Write-Host ''
Write-Host '    打开  https://github.com/settings/personal-access-tokens/new'
Write-Host '    · Token name      随便填，比如 dsh-ideas'
Write-Host '    · Expiration      1 year'
Write-Host '    · Repository access 先选 All repositories（新仓库还没建）'
Write-Host '    · Permissions → Repository permissions → Contents 改为 Read and write'
Write-Host '    · 点 Generate token，复制 github_pat_ 开头那串'
Write-Host ''

Start-Process 'https://github.com/settings/personal-access-tokens/new'

$secure = Read-Host '  第二步：在这里粘贴 token（输入时不显示），回车确认' -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
  $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
}

if ([string]::IsNullOrWhiteSpace($token)) { throw '没有输入 token' }
$token = $token.Trim()
if (-not $token.StartsWith('github_pat_') -and -not $token.StartsWith('ghp_')) {
  Write-Host ''
  Write-Host '  提示：token 通常以 github_pat_ 或 ghp_ 开头，你给的这串看起来不像。' -ForegroundColor Yellow
  $go = Read-Host '  仍然继续？(y/N)'
  if ($go -notin @('y', 'Y')) { exit 1 }
}

Write-Host ''
$env:GITHUB_TOKEN = $token
& $node (Join-Path $here 'init.mjs')

Write-Host ''
Write-Host '  完成。回到 DeepSeek Harness 里让我跑一次 ideas_status 就能确认。' -ForegroundColor Green
Write-Host '  （MCP 进程每次调用都会重读配置，不需要重启 DSH。）' -ForegroundColor DarkGray
Write-Host ''
Read-Host '  按回车关闭'
