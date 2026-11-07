$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
# This destructive lifecycle is confined to a disposable GitHub-hosted runner.
# Never exercise it against an existing developer/player installation.
if ($env:GITHUB_ACTIONS -ne 'true' -or $env:RUNNER_ENVIRONMENT -ne 'github-hosted' -or -not $IsWindows) {
    throw 'Installer acceptance requires a disposable GitHub-hosted Windows runner.'
}
$principal = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
function Wait-OwnedExecutable($process, [string]$executable, [int]$seconds) {
    # -Wait also waits for unrelated long-lived descendants. The executable's
    # real exit plus the receipt/state/file checks define each installer step.
    if ($process.WaitForExit($seconds * 1000)) { return }
    $identity = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)"
    if ($identity -and $identity.ExecutablePath -eq $executable) {
        & "$env:SystemRoot/System32/taskkill.exe" /PID $process.Id /T /F | Out-Null
        $process.WaitForExit(10000) | Out-Null
    }
    throw "Timed out waiting for the owned executable: $executable."
}
if ($principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    if ($env:NEUTRON_SMOKE_STANDARD_CHILD -eq '1') { throw 'Standard CI child has administrator privileges; refusing acceptance.' }
    & (Join-Path $PSScriptRoot 'run-smoke-standard-user.ps1')
    return
}
$appGuid = '17d12e18-c0d2-5a05-b898-3c34ccfad7ff'
$uninstallKey = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\$appGuid"
$installKey = "HKCU:\Software\$appGuid"
$data = Join-Path $env:APPDATA 'Neutron Launcher'
if ((Test-Path -LiteralPath $uninstallKey) -or (Test-Path -LiteralPath $installKey) -or (Test-Path -LiteralPath $data)) {
    throw 'An existing launcher installation/data directory must not be modified by acceptance.'
}
$runnerRoot = [IO.Path]::GetFullPath($env:RUNNER_TEMP)
$fixture = Join-Path $runnerRoot "neutron-installer-smoke-$env:GITHUB_RUN_ID-$env:GITHUB_RUN_ATTEMPT"
if (Test-Path -LiteralPath $fixture) { throw 'Acceptance fixture already exists.' }
$installed = Join-Path $fixture 'Install Prüfung'
$executable = Join-Path $installed 'Neutron Launcher.exe'
$installer = @(Get-ChildItem -LiteralPath $env:NEUTRON_PACKAGE_OUTPUT -Filter '*.exe' -File)
if ($installer.Count -ne 1) { throw 'Expected exactly one NSIS candidate.' }
New-Item -ItemType Directory -Path $fixture, $data | Out-Null
$marker = Join-Path $data 'personal-data-fixture.txt'
[IO.File]::WriteAllText($marker, 'synthetic settings/world marker; preserve on update and uninstall')
$markerHash = (Get-FileHash -LiteralPath $marker).Hash
$receipt = [ordered]@{ installerSha256 = (Get-FileHash -LiteralPath $installer[0].FullName).Hash; revision = $env:GITHUB_SHA }

function Install-Candidate {
    $began = [datetime]::UtcNow
    # NSIS /D must be last and unquoted, including when the directory has spaces.
    $process = Start-Process -FilePath $installer[0].FullName -ArgumentList "/S /currentuser /D=$installed" -WindowStyle Hidden -PassThru
    Wait-OwnedExecutable $process $installer[0].FullName 1200
    $present = Test-Path -LiteralPath $executable
    if ($process.ExitCode -ne 0 -or -not $present) { throw "NSIS installation failed: exit=$($process.ExitCode), exePresent=$present, target=$installed, elevated=$($principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator))." }
    $preparation = Get-Content -LiteralPath (Join-Path $data 'logs/installer-result.json') -Raw | ConvertFrom-Json
    $selectedState = Get-Content -LiteralPath (Join-Path $data 'state.json') -Raw | ConvertFrom-Json
    if ($preparation.phase -ne 'verified-before-activation' -or $preparation.selectedBuildId -ne $selectedState.selected -or [datetime]::Parse($preparation.completedAt).ToUniversalTime() -lt $began) {
        throw 'Missing, stale or uncommitted installation receipt.'
    }
    $expected = Join-Path $env:NEUTRON_PACKAGE_OUTPUT 'win-unpacked/resources/app.asar'
    if ((Get-FileHash -LiteralPath (Join-Path $installed 'resources/app.asar')).Hash -ne (Get-FileHash -LiteralPath $expected).Hash) { throw 'Installed application differs from the packaged candidate.' }
    $contract = Join-Path $installed 'resources/source-adapter/contract.json'
    if ((Get-FileHash -LiteralPath $contract).Hash -ne (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot '../resources/source-adapter/contract.json')).Hash) { throw 'Installed source contract differs from the reviewed candidate.' }
    if ((Get-FileHash -LiteralPath $marker).Hash -ne $markerHash) { throw 'Installation modified personal data.' }
}

$allUsers = Start-Process -FilePath $installer[0].FullName -ArgumentList "/S /allusers /D=$installed" -WindowStyle Hidden -PassThru
Wait-OwnedExecutable $allUsers $installer[0].FullName 60
if ($allUsers.ExitCode -eq 0 -or (Test-Path -LiteralPath $executable)) { throw 'All-users Alpha installation was not refused.' }
$receipt.allUsersRefusal = 'passed'
Install-Candidate
$receipt.install = 'passed'
$prepared = Join-Path $data 'logs/installer-result.json'
$pin = Get-Content -LiteralPath (Join-Path $installed 'resources/release.json') -Raw | ConvertFrom-Json
$preparedReceipt = Get-Content -LiteralPath $prepared -Raw | ConvertFrom-Json
if ($preparedReceipt.clientCommit -ne $pin.clientCommit -or $preparedReceipt.sourceArchiveSha256 -ne $pin.sourceArchiveSha256) {
    throw 'Installer did not finish preparing the exact pinned client.'
}
$receipt.clientPreparation = $preparedReceipt
$offline = Start-Process -FilePath $executable -ArgumentList '--verify-alpha' -WindowStyle Hidden -PassThru
Wait-OwnedExecutable $offline $executable 120
if ($offline.ExitCode -ne 0) { throw 'Installed client/runtime verification failed.' }
$receipt.localVerification = Get-Content -LiteralPath (Join-Path $data 'logs/offline-verification.json') -Raw | ConvertFrom-Json
$env:NEUTRON_DATA_ROOT = $data
$originalPath = $env:PATH
# The installed first-run window must work without Node/Java/Maven/Git on PATH.
$env:PATH = "$env:SystemRoot\System32;$env:SystemRoot"
$app = Start-Process -FilePath $executable -WindowStyle Hidden -PassThru
try {
    $startup = Join-Path $data 'logs/startup.json'
    $deadline = (Get-Date).AddSeconds(45)
    while (-not (Test-Path -LiteralPath $startup)) {
        if ($app.HasExited -or (Get-Date) -gt $deadline) { throw 'Installed renderer did not complete its real startup handshake.' }
        Start-Sleep -Milliseconds 250
        $app.Refresh()
    }
    $receipt.startup = Get-Content -LiteralPath $startup -Raw | ConvertFrom-Json
} finally {
    $env:PATH = $originalPath
    $app.Refresh()
    if (-not $app.HasExited) {
        if ([IO.Path]::GetFullPath($app.Path) -ne [IO.Path]::GetFullPath($executable)) { throw 'Refusing to stop an unowned process.' }
        & "$env:SystemRoot/System32/taskkill.exe" /PID $app.Id /T /F | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not stop the acceptance launcher process tree.' }
        $app.WaitForExit()
    }
}
# Exercise the real installed error paths, only on this disposable user's fixture.
$stateFile = Join-Path $data 'state.json'
$savedState = [IO.File]::ReadAllBytes($stateFile)
$savedHash = (Get-FileHash -LiteralPath $stateFile).Hash
function Assert-FailedPreparation([string]$label) {
    $attempt = Start-Process -FilePath $installer[0].FullName -ArgumentList "/S /currentuser /D=$installed" -WindowStyle Hidden -PassThru
    Wait-OwnedExecutable $attempt $installer[0].FullName 120
    if ($attempt.ExitCode -eq 0) { throw "$label unexpectedly reported installation success." }
    if ((Get-FileHash -LiteralPath $marker).Hash -ne $markerHash) { throw "$label changed personal fixture data." }
}
$env:NEUTRON_OFFLINE = '1'
try {
    [IO.File]::WriteAllText($stateFile, '{"schemaVersion":99}')
    $invalidHash = (Get-FileHash -LiteralPath $stateFile).Hash
    try { Assert-FailedPreparation 'Invalid registry'; if ((Get-FileHash -LiteralPath $stateFile).Hash -ne $invalidHash) { throw 'Invalid registry was overwritten.' } }
    finally { [IO.File]::WriteAllBytes($stateFile, $savedState) }
    $attributes = [IO.File]::GetAttributes($prepared)
    try {
        [IO.File]::SetAttributes($prepared, $attributes -bor [IO.FileAttributes]::ReadOnly)
        Assert-FailedPreparation 'Receipt write failure'
        if ((Get-FileHash -LiteralPath $stateFile).Hash -ne $savedHash) { throw 'Receipt failure changed the selected client registry.' }
    } finally { [IO.File]::SetAttributes($prepared, $attributes) }
    $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    if ($state.selected -notmatch '^[a-f0-9]{40}-[a-f0-9-]{36}$') { throw 'Invalid synthetic client selection.' }
    $jar = [IO.Path]::GetFullPath((Join-Path $data "versions/$($state.selected)/neutron-client.jar"))
    if (-not $jar.StartsWith([IO.Path]::GetFullPath($data) + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Client corruption fixture leaves owned data.' }
    $stream = [IO.File]::Open($jar, 'Open', 'ReadWrite', 'None')
    try { $stream.Position = 100; $original = $stream.ReadByte(); $stream.Position = 100; $stream.WriteByte($original -bxor 1) } finally { $stream.Dispose() }
    try {
        Assert-FailedPreparation 'Corrupt offline client cache'
        if ((Get-FileHash -LiteralPath $stateFile).Hash -ne $savedHash) { throw 'Corrupt cache replaced the selected client registry.' }
    } finally {
        $stream = [IO.File]::Open($jar, 'Open', 'Write', 'None')
        try { $stream.Position = 100; $stream.WriteByte($original) } finally { $stream.Dispose() }
    }
    Install-Candidate
} finally { Remove-Item Env:NEUTRON_OFFLINE }
$receipt.failureCases = 'invalid registry, denied receipt and corrupt offline cache preserved state and synthetic personal data'
$receipt.reinstall = 'passed; all launcher networking denied'
$receipt.warmPreparation = Get-Content -LiteralPath $prepared -Raw | ConvertFrom-Json
$uninstaller = Join-Path $installed 'Uninstall Neutron Launcher.exe'
$process = Start-Process -FilePath $uninstaller -ArgumentList '/S /currentuser /KEEP_APP_DATA' -WindowStyle Hidden -PassThru
Wait-OwnedExecutable $process $uninstaller 120
if ($process.ExitCode -ne 0) { throw 'NSIS uninstaller failed.' }
$deadline = (Get-Date).AddSeconds(45)
while (Test-Path -LiteralPath $executable) {
    if ((Get-Date) -gt $deadline) { throw 'Uninstaller left the installed executable behind.' }
    Start-Sleep -Milliseconds 250
}
if (Test-Path -LiteralPath $uninstallKey) { throw 'Uninstaller left the application registration behind.' }
if ((Get-FileHash -LiteralPath $marker).Hash -ne $markerHash) { throw 'Uninstaller removed personal data.' }
$receipt.uninstall = 'passed; synthetic personal data retained'
$evidence = if ($env:NEUTRON_ACCEPTANCE_OUTPUT) { $env:NEUTRON_ACCEPTANCE_OUTPUT } else { $env:NEUTRON_PACKAGE_OUTPUT }
$receipt | ConvertTo-Json -Depth 5 | Tee-Object -FilePath (Join-Path $evidence 'installer-acceptance.json')
