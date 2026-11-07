$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
# This destructive lifecycle is confined to a disposable GitHub-hosted runner.
# Never exercise it against an existing developer/player installation.
if ($env:GITHUB_ACTIONS -ne 'true' -or $env:RUNNER_ENVIRONMENT -ne 'github-hosted' -or -not $IsWindows) {
    throw 'Installer acceptance requires a disposable GitHub-hosted Windows runner.'
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
    # NSIS /D must be last and unquoted, including when the directory has spaces.
    $process = Start-Process -FilePath $installer[0].FullName -ArgumentList "/S /currentuser /D=$installed" -WindowStyle Hidden -Wait -PassThru
    if ($process.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $executable)) { throw 'NSIS installation failed.' }
    $expected = Join-Path $env:NEUTRON_PACKAGE_OUTPUT 'win-unpacked/resources/app.asar'
    if ((Get-FileHash -LiteralPath (Join-Path $installed 'resources/app.asar')).Hash -ne (Get-FileHash -LiteralPath $expected).Hash) { throw 'Installed application differs from the packaged candidate.' }
    $contract = Join-Path $installed 'resources/source-adapter/contract.json'
    if ((Get-FileHash -LiteralPath $contract).Hash -ne (Get-FileHash -LiteralPath 'resources/source-adapter/contract.json').Hash) { throw 'Installed source contract differs from the reviewed candidate.' }
    if ((Get-FileHash -LiteralPath $marker).Hash -ne $markerHash) { throw 'Installation modified personal data.' }
}

Install-Candidate
$receipt.install = 'passed'
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
Install-Candidate
$receipt.reinstall = 'passed'
$uninstaller = Join-Path $installed 'Uninstall Neutron Launcher.exe'
$process = Start-Process -FilePath $uninstaller -ArgumentList '/S /currentuser /KEEP_APP_DATA' -WindowStyle Hidden -Wait -PassThru
if ($process.ExitCode -ne 0) { throw 'NSIS uninstaller failed.' }
$deadline = (Get-Date).AddSeconds(45)
while (Test-Path -LiteralPath $executable) {
    if ((Get-Date) -gt $deadline) { throw 'Uninstaller left the installed executable behind.' }
    Start-Sleep -Milliseconds 250
}
if (Test-Path -LiteralPath $uninstallKey) { throw 'Uninstaller left the application registration behind.' }
if ((Get-FileHash -LiteralPath $marker).Hash -ne $markerHash) { throw 'Uninstaller removed personal data.' }
$receipt.uninstall = 'passed; synthetic personal data retained'
$receipt | ConvertTo-Json -Depth 5 | Tee-Object -FilePath (Join-Path $env:NEUTRON_PACKAGE_OUTPUT 'installer-acceptance.json')
