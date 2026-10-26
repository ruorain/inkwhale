@echo off
rem 用 DSH 自带的 Node 跑 ideas-mcp，不依赖系统 PATH。
rem 用法：run.cmd status / run.cmd search 关键词 / run.cmd（无参数 = MCP stdio 模式）
setlocal
set NODE=C:\Users\buguo\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe
"%NODE%" "%~dp0ideas-mcp.mjs" %*
