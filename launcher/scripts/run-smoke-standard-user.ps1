$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
# This account exists only on a disposable hosted runner. No UAC policy or
# existing account/profile is modified; the product runs with a genuine ordinary
# logon token and its own fresh profile, not a restricted administrator token.
if ($env:GITHUB_ACTIONS -ne 'true' -or $env:RUNNER_ENVIRONMENT -ne 'github-hosted') {
    throw 'Standard-user acceptance requires a disposable hosted Windows runner.'
}
$existing = Join-Path $env:APPDATA 'Neutron Launcher'
if (Test-Path -LiteralPath $existing) { throw 'Elevated refusal fixture must not touch existing launcher data.' }
$candidate = @(Get-ChildItem -LiteralPath $env:NEUTRON_PACKAGE_OUTPUT -Filter '*.exe' -File)
if ($candidate.Count -ne 1) { throw 'Expected exactly one candidate for elevated refusal.' }
$installerHash = (Get-FileHash -LiteralPath $candidate[0].FullName -Algorithm SHA256).Hash
$asar = Join-Path $env:NEUTRON_PACKAGE_OUTPUT 'win-unpacked/resources/app.asar'
$asarHash = (Get-FileHash -LiteralPath $asar -Algorithm SHA256).Hash
$refused = Join-Path $env:RUNNER_TEMP "neutron-elevated-refused-$env:GITHUB_RUN_ID-$env:GITHUB_RUN_ATTEMPT"
$denied = Start-Process -FilePath $candidate[0].FullName -ArgumentList "/S /currentuser /D=$refused" -WindowStyle Hidden -Wait -PassThru
if ($denied.ExitCode -eq 0 -or (Test-Path -LiteralPath (Join-Path $refused 'Neutron Launcher.exe')) -or (Test-Path -LiteralPath $existing)) {
    throw "Elevated installation was not refused before writing application/data (exit $($denied.ExitCode))."
}
$root = Join-Path $env:RUNNER_TEMP "neutron-standard-smoke-$env:GITHUB_RUN_ID-$env:GITHUB_RUN_ATTEMPT"
if (Test-Path -LiteralPath $root) { throw 'Standard-user fixture already exists.' }
New-Item -ItemType Directory -Path $root | Out-Null
$name = 'Ntrn' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$password = ConvertTo-SecureString ('Nn1!' + [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')) -AsPlainText -Force
$account = New-LocalUser -Name $name -Password $password -AccountNeverExpires -UserMayNotChangePassword -Description 'Disposable Neutron installer acceptance'
$sid = $account.SID.Value
$removeAccount = $true
try {
    Add-LocalGroupMember -SID 'S-1-5-32-545' -Member $name
    foreach ($directory in @($root)) {
        & "$env:SystemRoot/System32/icacls.exe" $directory /grant "*${sid}:(OI)(CI)F" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not grant access to the scoped installer fixture.' }
    }
    & "$env:SystemRoot/System32/icacls.exe" $env:NEUTRON_PACKAGE_OUTPUT /grant "*${sid}:(OI)(CI)RX" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not grant read/execute access to the candidate.' }
    & "$env:SystemRoot/System32/icacls.exe" (Split-Path -Parent $PSScriptRoot) /grant "*${sid}:(OI)(CI)RX" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not grant read access to launcher test scripts/resources.' }
    $wrapper = Join-Path $root 'run.ps1'
    $result = Join-Path $root 'result.txt'
    $log = Join-Path $root 'smoke.log'
    $evidence = Join-Path $root 'evidence'
    New-Item -ItemType Directory -Path $evidence | Out-Null
    $script = Join-Path $PSScriptRoot 'windows-installer-smoke.ps1'
    $body = @'
$ErrorActionPreference = 'Stop'
$code = 0
try {
    __ENVIRONMENT__
    $env:NEUTRON_SMOKE_STANDARD_CHILD = '1'
    [Environment]::SetEnvironmentVariable('NEUTRON_DATA_ROOT', $null, 'Process')
    [Environment]::SetEnvironmentVariable('NEUTRON_OFFLINE', $null, 'Process')
    if ([Security.Principal.WindowsIdentity]::GetCurrent().User.Value -ne '__SID__') { throw 'Wrong standard-user SID.' }
    $profile = [Environment]::ExpandEnvironmentVariables((Get-ItemProperty -LiteralPath 'HKLM:\Software\Microsoft\Windows NT\CurrentVersion\ProfileList\__SID__').ProfileImagePath)
    if (-not [IO.Path]::IsPathRooted($profile) -or -not (Test-Path -LiteralPath $profile -PathType Container)) { throw 'The actual standard-user profile was not loaded.' }
    $env:USERPROFILE = $profile
    $env:HOMEDRIVE = [IO.Path]::GetPathRoot($profile).TrimEnd('\')
    $env:HOMEPATH = $profile.Substring($env:HOMEDRIVE.Length)
    # The bootstrap PowerShell may have cached the parent's KnownFolder values.
    # This fresh disposable profile has no folder redirection. Set the complete
    # user environment before spawning NSIS/Electron, and create its real paths.
    $env:APPDATA = Join-Path $profile 'AppData/Roaming'
    $env:LOCALAPPDATA = Join-Path $profile 'AppData/Local'
    New-Item -ItemType Directory -Path $env:APPDATA, $env:LOCALAPPDATA -Force | Out-Null
    if (-not $env:APPDATA.StartsWith($profile + '\', [StringComparison]::OrdinalIgnoreCase) -or -not $env:LOCALAPPDATA.StartsWith($profile + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "Application data does not belong to the loaded test profile: profile=$profile, roaming=$env:APPDATA, local=$env:LOCALAPPDATA." }
    $env:TEMP = '__ROOT__\temp'; $env:TMP = $env:TEMP
    New-Item -ItemType Directory -Path $env:TEMP | Out-Null
    $probe = '__ROOT__\known-folders.ps1'
    [IO.File]::WriteAllText($probe, '$ErrorActionPreference="Stop";[ordered]@{sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value;roaming=[Environment]::GetFolderPath("ApplicationData","Create");local=[Environment]::GetFolderPath("LocalApplicationData","Create")} | ConvertTo-Json | Set-Content -LiteralPath "__ROOT__\known-folders.json"')
    $checked = Start-Process -FilePath (Join-Path $PSHOME 'pwsh.exe') -ArgumentList "-NoProfile -NonInteractive -WindowStyle Hidden -File `"$probe`"" -WorkingDirectory '__ROOT__' -WindowStyle Hidden -Wait -PassThru
    if ($checked.ExitCode -ne 0) { throw 'Fresh standard-user KnownFolder probe failed.' }
    $folders = Get-Content -LiteralPath '__ROOT__\known-folders.json' -Raw | ConvertFrom-Json
    if ($folders.sid -ne '__SID__' -or $folders.roaming -ne $env:APPDATA -or $folders.local -ne $env:LOCALAPPDATA) { throw "Fresh child has wrong KnownFolders: roaming=$($folders.roaming), local=$($folders.local)." }
    Set-Location -LiteralPath '__WORKING__'
    & '__SCRIPT__' *> '__LOG__'
} catch {
    $_ | Out-String | Add-Content -LiteralPath '__LOG__'
    $code = 1
    foreach ($file in @('installer-error.json', 'build.log')) {
        $diagnostic = Join-Path $env:APPDATA ('Neutron Launcher/logs/' + $file)
        if (Test-Path -LiteralPath $diagnostic) {
            Get-Content -LiteralPath $diagnostic -Tail 45 | ForEach-Object { $_.Replace($env:USERPROFILE, '[CI_PROFILE]') } | Add-Content -LiteralPath '__LOG__'
        }
    }
}
[IO.File]::WriteAllText('__RESULT__', [string]$code)
exit $code
'@
    $environment = foreach ($key in @('GITHUB_ACTIONS', 'RUNNER_ENVIRONMENT', 'GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT', 'NEUTRON_PACKAGE_OUTPUT', 'GITHUB_SHA', 'SystemRoot')) {
        $value = [Environment]::GetEnvironmentVariable($key)
        if ([string]::IsNullOrEmpty($value)) { throw "Missing CI variable $key." }
        "[Environment]::SetEnvironmentVariable('$key', '$($value.Replace("'", "''"))', 'Process')"
    }
    $environment += "[Environment]::SetEnvironmentVariable('RUNNER_TEMP', '$($root.Replace("'", "''"))', 'Process')"
    $environment += "[Environment]::SetEnvironmentVariable('NEUTRON_ACCEPTANCE_OUTPUT', '$($evidence.Replace("'", "''"))', 'Process')"
    $body = $body.Replace('__ENVIRONMENT__', ($environment -join "`n    ")).Replace('__SID__', $sid)
    foreach ($item in @{ '__SCRIPT__' = $script; '__ROOT__' = $root; '__WORKING__' = (Split-Path -Parent $PSScriptRoot); '__LOG__' = $log; '__RESULT__' = $result }.GetEnumerator()) {
        $body = $body.Replace($item.Key, $item.Value.Replace("'", "''"))
    }
    [IO.File]::WriteAllText($wrapper, $body)
    $credential = [pscredential]::new(($env:COMPUTERNAME + '\' + $name), $password)
    $child = Start-Process -FilePath (Join-Path $PSHOME 'pwsh.exe') -Credential $credential -LoadUserProfile -WorkingDirectory $root -ArgumentList "-NoProfile -NonInteractive -WindowStyle Hidden -File `"$wrapper`"" -WindowStyle Hidden -PassThru
    if (-not $child.WaitForExit(45 * 60 * 1000)) {
        $owned = Get-CimInstance Win32_Process -Filter "ProcessId=$($child.Id)"
        if ($owned -and $owned.CommandLine -and $owned.CommandLine.Contains($wrapper)) {
            & "$env:SystemRoot/System32/taskkill.exe" /PID $child.Id /T /F | Out-Null
            if (-not $child.WaitForExit(10000)) { $removeAccount = $false; throw 'Owned CI process did not stop; account retained for runner teardown.' }
        } else {
            $removeAccount = $false
            throw 'Timed-out CI process identity could not be verified; account retained for runner teardown.'
        }
        throw 'Standard-user acceptance timed out; only its verified process tree was stopped.'
    }
    $captured = if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Raw } else { 'Standard-user process produced no log.' }
    [IO.File]::WriteAllText((Join-Path $env:NEUTRON_PACKAGE_OUTPUT 'installer-standard-user.log'), $captured)
    $captured
    $acceptance = Join-Path $evidence 'installer-acceptance.json'
    if (Test-Path -LiteralPath $acceptance) { Copy-Item -LiteralPath $acceptance -Destination (Join-Path $env:NEUTRON_PACKAGE_OUTPUT 'installer-acceptance.json') }
    if ($child.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $result) -or (Get-Content -LiteralPath $result -Raw).Trim() -ne '0') {
        throw "Standard-user acceptance failed (PowerShell exit $($child.ExitCode)); see installer-standard-user.log."
    }
} finally {
    $sameAccount = Get-LocalUser -Name $name -ErrorAction SilentlyContinue
    if ($removeAccount -and $sameAccount -and $sameAccount.SID.Value -eq $sid) { Remove-LocalUser -SID $sid }
    if ((Get-FileHash -LiteralPath $candidate[0].FullName -Algorithm SHA256).Hash -ne $installerHash -or (Get-FileHash -LiteralPath $asar -Algorithm SHA256).Hash -ne $asarHash) {
        throw 'Candidate/reference bytes changed during acceptance; this artifact must not be published.'
    }
}
