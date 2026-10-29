@echo off
setlocal DisableDelayedExpansion
pushd "%~dp0" >nul 2>&1
if errorlevel 1 (
  echo Neutron Alpha Start: NOT_READY - cannot access the start directory.
  exit /b 20
)
if not exist "%~dp0Neutron-Check.cmd" (
  echo Neutron Alpha Start: NOT_READY - Neutron-Check.cmd missing.
  popd
  exit /b 21
)
call "%~dp0Neutron-Check.cmd"
set "neutronCheckExit=%ERRORLEVEL%"
echo Neutron Alpha Start: NOT_READY
echo A reviewed session, loader, runtime isolation and explicit game-smoke approval are still required.
echo Run Neutron-Check.cmd and read README.md. No game process was started.
popd
if "%neutronCheckExit%"=="0" set "neutronCheckExit=30"
endlocal & exit /b %neutronCheckExit%
