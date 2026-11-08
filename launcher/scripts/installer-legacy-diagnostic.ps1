$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if($env:GITHUB_ACTIONS -ne 'true' -or $env:RUNNER_ENVIRONMENT -ne 'github-hosted' -or $env:NEUTRON_DIAGNOSTIC_CHILD -ne '1'){throw 'Only a disposable ordinary-user CI child may execute installer diagnostics.'}
$principal=[Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
if($principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'A real standard account is required.'}
Add-Type -Path (Join-Path $PSScriptRoot 'installer-ui.cs')
$root=$env:NEUTRON_DIAGNOSTIC_ROOT
$control=Join-Path $env:APPDATA 'Neutron Launcher'
$install=Join-Path $root "Install Pruefung O'Neil"
if(Test-Path -LiteralPath $control){throw 'Existing profile must not be touched.'}
$downloads=Join-Path $env:USERPROFILE 'Downloads'
[IO.Directory]::CreateDirectory($downloads) | Out-Null
$temp=Join-Path $env:LOCALAPPDATA 'Temp/NeutronDiagnostic'
[IO.Directory]::CreateDirectory($temp) | Out-Null
$env:TEMP=$temp; $env:TMP=$temp
if([IO.Path]::GetPathRoot($install) -eq [IO.Path]::GetPathRoot($temp)){throw 'The diagnostic must exercise different installation and TEMP volumes.'}
$events=[Collections.Generic.List[object]]::new()
$processes=[Collections.Generic.List[object]]::new()
function Save-Evidence {
    $logs=@{}
    foreach($name in @('installer-error.json','installer-result.json','headless-startup.jsonl','build.log')) {
        $file=Join-Path $control ('logs/'+$name)
        if(Test-Path -LiteralPath $file){$logs[$name]=@(Get-Content -LiteralPath $file -Tail 80)}
    }
    @{install=$install;temp=$temp;ordinaryAccount=$true;uiEvents=$events.ToArray();processes=$processes.ToArray();logs=$logs} | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $root 'legacy-diagnostic.json') -Encoding utf8
}
function Invoke-ObservedInstaller([string]$File,[string]$Arguments,[string]$Label,[bool]$Assisted) {
    $started=[datetime]::UtcNow
    # Assisted means the actual visible dialogs: no /S or equivalent is passed.
    $process=Start-Process -FilePath $File -ArgumentList $Arguments -WindowStyle Normal -PassThru
    $owned=@{}; $owned[$process.Id]=@{process=$process;path=$File;exitRecorded=$false}
    $seenUi=$false; $failure=$null; $lastUi=''; $lastClick=[datetime]::MinValue
    $deadline=[datetime]::UtcNow.AddMinutes(15)
    while($true) {
        foreach($item in @(Get-CimInstance Win32_Process)) {
            if($owned.ContainsKey([int]$item.ProcessId) -or -not $owned.ContainsKey([int]$item.ParentProcessId) -or $item.CreationDate.ToUniversalTime() -lt $started){continue}
            try {
                $child=[Diagnostics.Process]::GetProcessById([int]$item.ProcessId); $child.EnableRaisingEvents=$true
                $owned[[int]$item.ProcessId]=@{process=$child;path=$item.ExecutablePath;exitRecorded=$false}
            } catch { }
        }
        $active=0
        foreach($entry in $owned.GetEnumerator()) {
            try {
                $entry.Value.process.Refresh()
                if(-not $entry.Value.process.HasExited){$active++;continue}
                if(-not $entry.Value.exitRecorded){
                    $processes.Add(@{label=$Label;pid=$entry.Key;path=$entry.Value.path;exitCode=$entry.Value.process.ExitCode})
                    $entry.Value.exitRecorded=$true
                }
            } catch { }
        }
        $windows=@([NeutronInstallerUi]::Read([int[]]@($owned.Keys)))
        foreach($window in $windows) {
            $texts=($window.Controls | Where-Object Visible | ForEach-Object Text) -join "`n"
            $signature=$window.Title+"`n"+$texts
            if($signature -ne $lastUi){$events.Add(@{label=$Label;date=[datetime]::UtcNow.ToString('o');visible=$window.Visible;pid=$window.Pid;title=$window.Title;controls=$window.Controls});$lastUi=$signature;Save-Evidence}
            if($window.Visible -and @($window.Controls | Where-Object Visible).Count -gt 0){$seenUi=$true}
            if($texts -match '(cannot be closed|konnte nicht geschlossen|kann nicht geschlossen|Can.t rename|Error opening file|Fehler beim|Failed to uninstall)') {
                $failure=$texts
                $cancel=@($window.Controls | Where-Object {$_.ClassName -eq 'Button' -and $_.Enabled -and $_.Visible -and $_.Id -eq 2})
                if($cancel.Count){[NeutronInstallerUi]::Click($cancel[0].Handle,$window.Pid)}
                continue
            }
            if($Assisted -and $window.Visible -and [datetime]::UtcNow -gt $lastClick.AddSeconds(1)) {
                $next=@($window.Controls | Where-Object {$_.ClassName -eq 'Button' -and $_.Enabled -and $_.Visible -and $_.Id -eq 1 -and $_.Text -match '(Next|Install|Finish|Uninstall|Weiter|Fertig|Deinstallieren)'})
                if($next.Count){[NeutronInstallerUi]::Click($next[0].Handle,$window.Pid);$lastClick=[datetime]::UtcNow}
            }
        }
        if($active -eq 0 -and $windows.Count -eq 0){break}
        if([datetime]::UtcNow -gt $deadline){$failure='Timed out: no successful assisted acceptance.';break}
        Start-Sleep -Milliseconds 500
    }
    Save-Evidence
    if($failure){throw ($Label+': '+$failure)}
    if($Assisted -and -not $seenUi){throw 'Assisted acceptance did not observe real visible installer controls.'}
    $process.Refresh()
    if($process.ExitCode -ne 0){throw ($Label+': installer exit '+$process.ExitCode)}
}
try {
    $old=Join-Path $downloads 'Neutron-Launcher-0.2.0-Windows-x64.exe'
    $new=Join-Path $downloads 'Neutron-Launcher-0.2.2-Windows-x64.exe'
    foreach($artifact in @(@{path=$old;version='0.2.0';hash='20e8c8a8b0a71a8e3540d87eeae7bce7c4fa2c6323777b0cbce136e12f9091d3'},@{path=$new;version='0.2.2';hash='acfc43e605fc0af9d591068bd12b08d1e05a1a6b5936cdc3af312aaddd6a27bc'})) {
        Invoke-WebRequest ('https://github.com/YoungJibbit95/Neutron-Client/releases/download/launcher-v'+$artifact.version+'/Neutron-Launcher-'+$artifact.version+'-Windows-x64.exe') -OutFile $artifact.path
        if((Get-FileHash -LiteralPath $artifact.path).Hash.ToLowerInvariant() -ne $artifact.hash){throw 'Public installer bytes differ from the pinned release.'}
    }
    $reference=Join-Path $root 'reference'; [IO.Directory]::CreateDirectory($reference) | Out-Null
    $seven=Join-Path $env:ProgramFiles '7-Zip/7z.exe'
    & $seven x $new ('-o'+$reference) '-y' '$PLUGINSDIR\app-64.7z' | Out-Null
    if($LASTEXITCODE){throw 'Could not read the immutable installer payload.'}
    & $seven x (Join-Path $reference '$PLUGINSDIR/app-64.7z') ('-o'+$reference) '-y' 'resources/app.asar' | Out-Null
    if($LASTEXITCODE){throw 'Could not read the expected application ASAR.'}
    $expectedAsar=(Get-FileHash -LiteralPath (Join-Path $reference 'resources/app.asar')).Hash
    try { Invoke-ObservedInstaller $old ("/S /currentuser /D="+$install) 'old-0.2.0-install' $false }
    catch {
        # Diagnostics can still observe repair of a registered but incomplete
        # old install. This is explicitly NOT evidence of a complete old setup.
        $oldExe=Join-Path $install 'Neutron Launcher.exe'
        $registration=Get-ItemProperty -LiteralPath 'HKCU:\Software\17d12e18-c0d2-5a05-b898-3c34ccfad7ff' -ErrorAction SilentlyContinue
        if(-not (Test-Path -LiteralPath $oldExe) -or (Get-Item -LiteralPath $oldExe).VersionInfo.ProductVersion -ne '0.2.0' -or $null -eq $registration -or $registration.InstallLocation -ne $install){throw}
        $events.Add(@{label='old-fixture-incomplete';error=$_.Exception.Message;fullOldInstallationAccepted=$false})
        Save-Evidence
    }
    $marker=Join-Path $control 'personal-diagnostic-marker.txt'; [IO.File]::WriteAllText($marker,'synthetic profile; retain'); $markerHash=(Get-FileHash -LiteralPath $marker).Hash
    Invoke-ObservedInstaller $new ("/currentuser /D="+$install) 'assisted-0.2.0-to-0.2.2' $true
    $registeredInstall=(Get-ItemProperty -LiteralPath 'HKCU:\Software\17d12e18-c0d2-5a05-b898-3c34ccfad7ff').InstallLocation
    $events.Add(@{label='assisted-install-location';requested=$install;registered=$registeredInstall})
    if(-not $registeredInstall.StartsWith($root+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Registered install escaped the owned fixture.'}
    $installedExe=Join-Path $registeredInstall 'Neutron Launcher.exe'
    if((Get-Item -LiteralPath $installedExe).VersionInfo.ProductVersion -ne '0.2.2'){throw 'Assisted upgrade did not install version 0.2.2.'}
    $metadata=Get-Content -LiteralPath (Join-Path $registeredInstall 'resources/release.json') -Raw | ConvertFrom-Json
    if($metadata.launcherVersion -ne '0.2.2'){throw 'Installed release metadata differs from the candidate.'}
    if((Get-FileHash -LiteralPath (Join-Path $registeredInstall 'resources/app.asar')).Hash -ne $expectedAsar){throw 'Assisted upgrade application bytes differ from the immutable public candidate.'}
    Invoke-ObservedInstaller $new ("/S /currentuser /D="+$registeredInstall) 'silent-0.2.2-reinstall' $false
    $uninstaller=Join-Path $registeredInstall 'Uninstall Neutron Launcher.exe'
    Invoke-ObservedInstaller $uninstaller '/currentuser /KEEP_APP_DATA' 'assisted-uninstall-0.2.2' $true
    if((Test-Path -LiteralPath (Join-Path $registeredInstall 'Neutron Launcher.exe')) -or (Test-Path -LiteralPath 'HKCU:\Software\17d12e18-c0d2-5a05-b898-3c34ccfad7ff')){throw 'Assisted uninstaller did not remove application files and registration.'}
    if((Get-FileHash -LiteralPath $marker).Hash -ne $markerHash){throw 'Uninstall changed synthetic personal data.'}
    $events.Add(@{label='completed';result='all observed operations succeeded; user failure not reproduced'})
} finally {
    foreach($entry in $processes){$entry | ConvertTo-Json -Compress | Write-Host}
    Save-Evidence
}
