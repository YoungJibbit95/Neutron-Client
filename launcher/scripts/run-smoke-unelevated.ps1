$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
# Hosted Windows runners may launch jobs with an elevated token. Exercise the
# actual per-user installer with a restricted token of the SAME synthetic user;
# never create accounts, change UAC settings or load another user's profile.
if ($env:GITHUB_ACTIONS -ne 'true' -or $env:RUNNER_ENVIRONMENT -ne 'github-hosted') {
    throw 'This token-restricted bridge is only for disposable hosted CI.'
}
$existing = Join-Path $env:APPDATA 'Neutron Launcher'
if (Test-Path -LiteralPath $existing) { throw 'Elevated refusal fixture must not touch existing launcher data.' }
$candidate = @(Get-ChildItem -LiteralPath $env:NEUTRON_PACKAGE_OUTPUT -Filter '*.exe' -File)
if ($candidate.Count -ne 1) { throw 'Expected exactly one candidate for elevated refusal.' }
$refused = Join-Path $env:RUNNER_TEMP "neutron-elevated-refused-$env:GITHUB_RUN_ID-$env:GITHUB_RUN_ATTEMPT"
$denied = Start-Process -FilePath $candidate[0].FullName -ArgumentList "/S /currentuser /D=$refused" -WindowStyle Hidden -Wait -PassThru
if ($denied.ExitCode -eq 0 -or (Test-Path -LiteralPath (Join-Path $refused 'Neutron Launcher.exe')) -or (Test-Path -LiteralPath $existing)) {
    throw 'Elevated installer was not refused before installing application/data.'
}
$root = Join-Path $env:RUNNER_TEMP "neutron-smoke-token-$env:GITHUB_RUN_ID-$env:GITHUB_RUN_ATTEMPT"
if (Test-Path -LiteralPath $root) { throw 'Restricted smoke bridge already exists.' }
New-Item -ItemType Directory -Path $root | Out-Null
$wrapper = Join-Path $root 'run.ps1'
$result = Join-Path $root 'result.txt'
$log = Join-Path $root 'smoke.log'
$script = Join-Path $PSScriptRoot 'windows-installer-smoke.ps1'
$body = @'
$ErrorActionPreference = 'Stop'
$env:NEUTRON_SMOKE_LIMITED_CHILD = '1'
$code = 0
try {
    __ENVIRONMENT__
    if ([Security.Principal.WindowsIdentity]::GetCurrent().User.Value -ne '__SID__') { throw 'Restricted smoke process changed Windows user.' }
    Set-Location -LiteralPath '__WORKING__'
    & '__SCRIPT__' *> '__LOG__'
} catch { $_ | Out-String | Add-Content -LiteralPath '__LOG__'; $code = 1 }
[IO.File]::WriteAllText('__RESULT__', [string]$code)
exit $code
'@
$body = $body.Replace('__SCRIPT__', $script.Replace("'", "''")).Replace('__LOG__', $log.Replace("'", "''")).Replace('__RESULT__', $result.Replace("'", "''"))
$environment = foreach ($name in @('GITHUB_ACTIONS', 'RUNNER_ENVIRONMENT', 'RUNNER_TEMP', 'GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT', 'NEUTRON_PACKAGE_OUTPUT', 'GITHUB_SHA', 'APPDATA', 'SystemRoot')) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrEmpty($value)) { throw "Missing required CI environment $name." }
    "[Environment]::SetEnvironmentVariable('$name', '$($value.Replace("'", "''"))', 'Process')"
}
$body = $body.Replace('__ENVIRONMENT__', ($environment -join "`n    ")).Replace('__SID__', [Security.Principal.WindowsIdentity]::GetCurrent().User.Value).Replace('__WORKING__', (Split-Path -Parent $PSScriptRoot).Replace("'", "''"))
[IO.File]::WriteAllText($wrapper, $body)
$powershell = Join-Path $PSHOME 'pwsh.exe'
if (-not (Test-Path -LiteralPath $powershell)) { throw 'PowerShell 7 runner executable is unavailable.' }
if ($powershell.Contains('"') -or $wrapper.Contains('"')) { throw 'Unexpected executable/script path.' }
$command = '"' + $powershell + '" -NoProfile -NonInteractive -WindowStyle Hidden -File "' + $wrapper + '"'
$arguments = '/trustlevel:0x20000 "' + $command.Replace('"', '\"') + '"'
$bridge = Start-Process -FilePath (Join-Path $env:SystemRoot 'System32/runas.exe') -ArgumentList $arguments -WindowStyle Hidden -Wait -PassThru
if ($bridge.ExitCode -ne 0) { throw 'Could not start the restricted same-user acceptance process.' }
$deadline = (Get-Date).AddMinutes(40)
while (-not (Test-Path -LiteralPath $result)) {
    if ((Get-Date) -gt $deadline) { throw 'Restricted acceptance did not report completion.' }
    Start-Sleep -Milliseconds 500
}
Get-Content -LiteralPath $log -Tail 50
if ((Get-Content -LiteralPath $result -Raw).Trim() -ne '0') { throw 'Restricted acceptance failed; see the retained CI smoke log.' }
