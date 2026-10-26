@echo off
chcp 65001 >nul
title Ideas Repo Setup
cd /d "%~dp0"

rem Use PowerShell 7 (UTF-8 native) so Chinese output is not garbled.
set "PWSH=C:\Program Files\PowerShell\7\pwsh.exe"
if not exist "%PWSH%" set "PWSH=powershell.exe"

"%PWSH%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1"

if errorlevel 1 (
  echo.
  echo [Setup failed] Copy the red lines above and send them to DeepSeek.
  pause >nul
)
