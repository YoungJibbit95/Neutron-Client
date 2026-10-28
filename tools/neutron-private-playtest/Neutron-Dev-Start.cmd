@echo off
setlocal DisableDelayedExpansion
title NEUTRON DEV OFFLINE - NO AUTH - Singleplayer test
if not exist "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" exit /b 20
if not exist "%~dp0start-neutron-dev.ps1" exit /b 21
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-neutron-dev.ps1" %*
set "NEUTRON_DEV_EXIT=%ERRORLEVEL%"
endlocal & exit /b %NEUTRON_DEV_EXIT%
