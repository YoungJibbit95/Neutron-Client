@echo off
setlocal DisableDelayedExpansion
rem Use Windows PowerShell modules even when called from a PowerShell 7 host.
set "PSModulePath=%SystemRoot%\System32\WindowsPowerShell\v1.0\Modules;%ProgramFiles%\WindowsPowerShell\Modules"
pushd "%~dp0" >nul 2>&1
if errorlevel 1 (
  echo Neutron Alpha Check: BLOCKED - cannot access the check directory.
  exit /b 20
)
if not exist "%~dp0check-private-playtest.ps1" (
  echo Neutron Alpha Check: BLOCKED - check helper missing.
  popd
  exit /b 21
)
if not exist "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" (
  echo Neutron Alpha Check: BLOCKED - Windows PowerShell missing.
  popd
  exit /b 22
)
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%~dp0check-private-playtest.ps1"
set "neutronExit=%ERRORLEVEL%"
popd
endlocal & exit /b %neutronExit%
