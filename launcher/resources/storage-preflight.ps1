param(
    [ValidateSet('Resolve','Validate','Check','Commit')][string]$Action = 'Check',
    [string]$DataRoot, [string]$InstallDirectory, [string]$ControlDirectory,
    [string]$ReleaseFile, [string]$ResultFile, [string]$CheckFile, [string]$RequestDirectory,
    [switch]$ProcessOverride, [switch]$LibraryOnly
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Get-NeutronStoragePath([string]$Value, [string]$Install) {
    if (-not $Value -or $Value.Length -gt 900 -or $Value -match '[\x00-\x1f<>"|?*]' -or $Value -notmatch '^[A-Za-z]:[\\/]' -or $Value.Substring(2).Contains(':')) { throw 'Choose an absolute local data folder, for example F:\Neutron Data.' }
    $full = [IO.Path]::GetFullPath($Value).TrimEnd('\')
    if ($full.Length -eq 2 -or @($full.Split('\') | Where-Object { $_ -match '[. ]$' }).Count) { throw 'Choose a dedicated data folder, not a drive root.' }
    $app = [IO.Path]::GetFullPath($Install).TrimEnd('\')
    if ($full.Equals($app,[StringComparison]::OrdinalIgnoreCase) -or $full.StartsWith($app+'\',[StringComparison]::OrdinalIgnoreCase) -or $app.StartsWith($full+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'The data folder must be separate from the launcher installation folder.' }
    return $full
}

function Get-NeutronFsPath([string]$Path) {
    # Windows PowerShell 5.1's providers cannot reliably hash/enumerate >260
    # character library paths. Use extended local paths with .NET file APIs.
    if ($Path.StartsWith('\\?\')) { return $Path }
    return '\\?\'+$Path
}

function Assert-NeutronNoLinks([string]$Path) {
    $current=$Path.TrimEnd('\')
    while ($current.Length -ge 2) {
        try {
            $attributePath=if($current.Length -eq 2){$current+'\'}else{$current}
            $attributes=[IO.File]::GetAttributes((Get-NeutronFsPath $attributePath))
            if ($attributes -band [IO.FileAttributes]::ReparsePoint) { throw ('Linked launcher storage is not allowed: '+$current) }
        } catch {
            $cause=$_.Exception
            while($cause.InnerException){$cause=$cause.InnerException}
            if ($cause -isnot [IO.FileNotFoundException] -and $cause -isnot [IO.DirectoryNotFoundException]) { throw }
        }
        if ($current.Length -le 3) { break }
        $current=$current.Substring(0,[math]::Max(3,$current.LastIndexOf('\')))
    }
}

function Get-NeutronFileHash([string]$Path) {
    $stream=[IO.File]::OpenRead((Get-NeutronFsPath $Path)); $sha=[Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','').ToLowerInvariant() }
    finally { $stream.Dispose(); $sha.Dispose() }
}

function Read-NeutronProfile([string]$Root) {
    $file=Join-Path $Root 'state.json'
    if (-not [IO.File]::Exists($file)) { return $null }
    try {
        Assert-NeutronNoLinks $file
        $state=[IO.File]::ReadAllText($file) | ConvertFrom-Json
        if ($state.schemaVersion -ne 1 -or $state.builds -isnot [array] -or $state.builds.Count -gt 100) { throw 'schema' }
        $optionNames=@('ramMiB','gameDir','java','javaPath','width','height','fullscreen','jvmArgs')
        $preferenceNames=@('theme','locale','scale','effects','reducedMotion')
        if (@($state.options.PSObject.Properties).Count -ne 8 -or @($state.preferences.PSObject.Properties).Count -ne 5) { throw 'settings' }
        foreach($key in $optionNames) { if (-not $state.options.PSObject.Properties[$key]) { throw 'settings' } }
        foreach($key in $preferenceNames) { if (-not $state.preferences.PSObject.Properties[$key]) { throw 'settings' } }
        # Same saved-settings contract as electron/contracts.cts, before EXE swap.
        foreach($key in @('ramMiB','width','height')) {
            $value=$state.options.$key
            if (-not ($value -is [int] -or $value -is [long] -or $value -is [double] -or $value -is [decimal]) -or [double]::IsNaN([double]$value) -or [double]::IsInfinity([double]$value) -or $value -ne [math]::Floor($value) -or $value -lt 0 -or $value -gt 32768) { throw 'settings' }
        }
        if ($state.options.fullscreen -isnot [bool] -or $state.options.java -isnot [string] -or $state.options.java -cnotin @('managed','custom')) { throw 'settings' }
        foreach($key in @('gameDir','javaPath','jvmArgs')) {
            $value=$state.options.$key; $limit=if($key -eq 'jvmArgs'){2048}else{1024}
            if ($value -isnot [string] -or $value.Length -gt $limit -or $value.Contains([char]0)) { throw 'settings' }
        }
        if ($state.preferences.theme -isnot [string] -or $state.preferences.theme -cnotin @('sunset','ocean','violet','graphite') -or $state.preferences.locale -isnot [string] -or $state.preferences.locale -cnotin @('de','en') -or $state.preferences.reducedMotion -isnot [bool]) { throw 'settings' }
        $value=$state.preferences.scale
        if (-not ($value -is [int] -or $value -is [long] -or $value -is [double] -or $value -is [decimal]) -or $value -notin @(90,100,110,125)) { throw 'settings' }
        $value=$state.preferences.effects
        if (-not ($value -is [int] -or $value -is [long] -or $value -is [double] -or $value -is [decimal]) -or [double]::IsNaN([double]$value) -or [double]::IsInfinity([double]$value) -or $value -ne [math]::Floor($value) -or $value -lt 0 -or $value -gt 100) { throw 'settings' }
        foreach($record in $state.builds) {
            if ($record.id -notmatch '^[a-f0-9]{40}-[a-f0-9-]{36}$' -or $record.commit -notmatch '^[a-f0-9]{40}$' -or $record.manifestSha256 -notmatch '^[a-f0-9]{64}$' -or $record.sizeMiB -lt 0) { throw 'build' }
            $null=[datetime]$record.date
        }
        if ($null -ne $state.selected) {
            $selected=@($state.builds | Where-Object {$_.id -ceq $state.selected})
            $manifest=Join-Path $Root ('versions/'+$state.selected+'/launch-manifest.json')
            Assert-NeutronNoLinks $manifest
            if ($selected.Count -ne 1 -or -not [IO.File]::Exists($manifest)) { throw 'selected' }
        }
        if ($null -ne $state.runtime) {
            $manifest=Join-Path $Root ($state.runtime.directory+'/runtime-manifest.json'); Assert-NeutronNoLinks $manifest
            if ($state.runtime.directory -notmatch '^runtimes/[a-f0-9-]{36}$' -or $state.runtime.manifestSha256 -notmatch '^[a-f0-9]{64}$' -or -not [IO.File]::Exists($manifest)) { throw 'runtime' }
        }
        return $state
    } catch { throw ('Saved launcher data is invalid or incomplete: '+$Root+'. Restore the complete data folder, including state.json, selected version and runtime; no replacement profile was created.') }
}

function Resolve-NeutronStorage([string]$Install, [string]$Control) {
    Assert-NeutronNoLinks $Control
    $pointer=Join-Path $Control 'data-directory.txt'
    Assert-NeutronNoLinks $pointer
    if ([IO.File]::Exists($pointer)) {
        $bytes=[IO.File]::ReadAllBytes($pointer)
        if ($bytes.Length -lt 2 -or $bytes.Length -gt 2048 -or $bytes.Length % 2) { throw 'The saved data location is invalid. Select the complete existing data folder in setup.' }
        return Get-NeutronStoragePath ([Text.Encoding]::Unicode.GetString($bytes).TrimStart([char]0xfeff).TrimEnd("`r","`n")) $Install
    }
    if ([IO.File]::Exists((Join-Path $Control 'state.json'))) { return Get-NeutronStoragePath $Control $Install }
    return Get-NeutronStoragePath ($Install+' Data') $Install
}

function Get-NeutronFreeBytes([string]$Root) {
    $drive=[IO.DriveInfo]::new([IO.Path]::GetPathRoot($Root))
    if (-not $drive.IsReady -or $drive.DriveType -eq [IO.DriveType]::Network) { throw ('The chosen data drive is unavailable or is not local: '+$Root+'. Reconnect it or select an existing local data folder. No C: fallback was used.') }
    return $drive.AvailableFreeSpace
}

function Get-NeutronProfileFiles([string]$Root, [string]$Relative='') {
    foreach($entry in [IO.Directory]::EnumerateFileSystemEntries((Get-NeutronFsPath (Join-Path $Root $Relative)))) {
        $attributes=[IO.File]::GetAttributes($entry)
        if ($attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'A copied data folder must not contain links.' }
        $leaf=$entry.Substring($entry.LastIndexOf('\')+1)
        if (-not $Relative -and $leaf -in @('temp','logs','work','maven-repository','maven-user','cache','candidates','session','crashes','data-directory.txt','.neutron-storage-v1')) { continue }
        $name=if($Relative){Join-Path $Relative $leaf}else{$leaf}
        if($attributes -band [IO.FileAttributes]::Directory){Get-NeutronProfileFiles $Root $name}else{$name}
    }
}

function Assert-NeutronProfileCopy([string]$Original, [string]$Copy) {
    Assert-NeutronNoLinks $Original
    if (-not [IO.File]::Exists((Join-Path $Original 'state.json'))) { throw 'The original data folder is unavailable. Reconnect it before verifying a copy; no fallback or replacement profile was selected.' }
    # Read-only comparison. Work/cache/logs are regenerable and may differ; all
    # saved settings, versions, runtimes, tools and in-root game data must match.
    foreach($name in @(Get-NeutronProfileFiles $Original)) {
        $from=Join-Path $Original $name; $to=Join-Path $Copy $name
        Assert-NeutronNoLinks $to
        if (-not [IO.File]::Exists((Get-NeutronFsPath $to)) -or (Get-NeutronFileHash $from) -cne (Get-NeutronFileHash $to)) {
            throw ('The selected data copy is incomplete or differs from the original: '+$name+'. Close Neutron and copy the complete data folder again while retaining the original. Separately configured game folders are not moved.')
        }
    }
}

function Test-NeutronStorage([string]$Root, [string]$Install, [string]$Control, $Release, [bool]$Override, [bool]$VerifyCopy=$true) {
    $Root=Get-NeutronStoragePath $Root $Install
    $free=Get-NeutronFreeBytes $Root
    Assert-NeutronNoLinks $Root
    $state=Read-NeutronProfile $Root
    if (-not $Override) {
        Assert-NeutronNoLinks $Control
        $oldRoot=$null; $invalidSelection=$false
        try { $oldRoot=Resolve-NeutronStorage $Install $Control } catch { $invalidSelection=$true }
        $hasOldProfile=$invalidSelection -or [IO.File]::Exists((Join-Path $Control 'state.json')) -or [IO.File]::Exists((Join-Path $Control 'data-directory.txt'))
        if ($hasOldProfile -and $null -eq $state) {
            throw 'An existing profile is present. Close Neutron, copy its complete data folder to the new drive while retaining the original, then select that existing copy. Settings, versions and a separately configured game folder are not moved automatically.'
        }
    }
    $marker=Join-Path $Root '.neutron-storage-v1'
    $ownedMarker=[IO.File]::Exists($marker) -and [IO.File]::ReadAllText($marker) -ceq 'dev.neutron.launcher.storage.v1'
    if ([IO.Directory]::Exists($Root) -and $null -eq $state -and -not $ownedMarker -and [IO.Directory]::GetFileSystemEntries($Root).Length) { throw 'The chosen folder contains unrelated or incomplete data. Choose an empty dedicated folder or a complete existing Neutron data folder.' }
    $cached=$false
    if ($state) {
        foreach($record in @($state.builds | Where-Object {$_.commit -ceq $Release.clientCommit})) {
            $manifest=Join-Path $Root ('versions/'+$record.id+'/launch-manifest.json')
            if ([IO.File]::Exists($manifest) -and (Get-FileHash -LiteralPath $manifest -Algorithm SHA256).Hash -eq $record.manifestSha256) {
                try { $content=[IO.File]::ReadAllText($manifest) | ConvertFrom-Json; if ($content.archiveSha256 -ceq $Release.sourceArchiveSha256) { $cached=$true } } catch { }
            }
        }
    }
    if (-not $cached -and $free -lt 3GB) { throw ('At least 3 GiB free space is required for the new Neutron client. Data folder: '+$Root+'. Available: '+[math]::Round($free/1GB,2)+' GiB. Select a data folder on a drive with enough space; the launcher installation folder is separate. The existing EXE and data have not been replaced.') }
    if ($VerifyCopy -and -not $Override -and $hasOldProfile -and $oldRoot -and -not $Root.Equals($oldRoot,[StringComparison]::OrdinalIgnoreCase)) {
        if (Get-Process -Name 'Neutron Launcher' -ErrorAction SilentlyContinue | Where-Object {$_.SessionId -eq [Diagnostics.Process]::GetCurrentProcess().SessionId}) { throw 'Close Neutron Launcher before verifying a data-folder copy.' }
        if ([IO.File]::Exists((Join-Path $oldRoot 'client-process.json'))) { throw 'The original data folder has a protected client process record. Stop the game through the previous launcher before switching folders; no process was stopped.' }
        Assert-NeutronProfileCopy $oldRoot $Root
    }
    # A cache record is only a preflight hint. The backend must still rehash all
    # client/runtime files and run Bootstrap; a corrupt cache never bypasses it.
    return [pscustomobject]@{Root=$Root;FreeBytes=$free;CachedPinHint=$cached;ExistingProfile=($null -ne $state)}
}

function Test-NeutronWrite([string]$Root) {
    [IO.Directory]::CreateDirectory($Root) | Out-Null
    $probe=Join-Path $Root ('.neutron-write-'+[guid]::NewGuid().ToString('N')+'.tmp')
    $stream=[IO.File]::Open($probe,'CreateNew','Write','None')
    try {$stream.WriteByte(0)} finally {$stream.Dispose();[IO.File]::Delete($probe)}
}

function Complete-NeutronStorage([string]$Root, [string]$Install, [string]$Control, $Release, $Check, [bool]$Override) {
    $Root=Get-NeutronStoragePath $Root $Install
    if (-not $Root.Equals([string]$Check.root,[StringComparison]::OrdinalIgnoreCase) -or $Check.clientCommit -cne $Release.clientCommit) { throw 'The data selection changed after preflight. Rerun setup.' }
    $state=Read-NeutronProfile $Root
    $receipt=[IO.File]::ReadAllText((Join-Path $Root 'logs/installer-result.json')) | ConvertFrom-Json
    if ($receipt.launcherVersion -cne $Release.launcherVersion -or $receipt.clientCommit -cne $Release.clientCommit -or $receipt.sourceArchiveSha256 -cne $Release.sourceArchiveSha256 -or $receipt.phase -cne 'verified-before-activation' -or $receipt.selectedBuildId -cne $state.selected -or ([datetime]$receipt.completedAt).ToUniversalTime() -lt ([datetime]$Check.startedAt).ToUniversalTime()) { throw 'Setup has no fresh committed receipt for this version and data folder. The previous data selection was retained.' }
    if ($null -ne $Check.options) {
        foreach($name in @('options','preferences')) {
            foreach($property in $Check.$name.PSObject.Properties) { if($state.$name.($property.Name) -cne $property.Value) { throw 'Saved settings changed during preparation. The previous data selection was retained.' } }
        }
    }
    if (-not $Override) {
        Assert-NeutronNoLinks $Control
        $pointer=Join-Path $Control 'data-directory.txt'; Assert-NeutronNoLinks $pointer
        if ([IO.File]::Exists($pointer)) {
            if ([IO.File]::GetAttributes($pointer) -band [IO.FileAttributes]::ReadOnly) { throw 'The saved data location is read-only. The previous data selection was retained.' }
            if ([IO.File]::ReadAllText($pointer,[Text.Encoding]::Unicode).TrimStart([char]0xfeff).TrimEnd("`r","`n").Equals($Root,[StringComparison]::OrdinalIgnoreCase)) { return $Root }
        }
        $temporary=$pointer+'.'+[guid]::NewGuid().ToString('N')+'.tmp'
        $bytes=[Text.UnicodeEncoding]::new($false,$false).GetBytes($Root)
        $handle=[IO.File]::Open($temporary,'CreateNew','Write','None')
        try {$handle.Write($bytes,0,$bytes.Length);$handle.Flush($true)} finally {$handle.Dispose()}
        if ([IO.File]::Exists($pointer)) { [IO.File]::Replace($temporary,$pointer,$pointer+'.previous-'+[guid]::NewGuid().ToString('N')) }
        else { [IO.File]::Move($temporary,$pointer) }
    }
    return $Root
}

if ($LibraryOnly) { return }
$encoding=[Text.UnicodeEncoding]::new($false,$false)
$release=$null
try {
    if ($RequestDirectory) {
        $DataRoot=[IO.File]::ReadAllText((Join-Path $RequestDirectory 'neutron-data.txt'),[Text.Encoding]::Unicode)
        $InstallDirectory=[IO.File]::ReadAllText((Join-Path $RequestDirectory 'neutron-install.txt'),[Text.Encoding]::Unicode)
        $ControlDirectory=[IO.File]::ReadAllText((Join-Path $RequestDirectory 'neutron-control.txt'),[Text.Encoding]::Unicode)
    }
    if ($Action -eq 'Resolve') {
        if ($DataRoot -or $ProcessOverride) { $answer=Get-NeutronStoragePath $DataRoot $InstallDirectory }
        else { $answer=Resolve-NeutronStorage $InstallDirectory $ControlDirectory }
    }
    else {
        $release=[IO.File]::ReadAllText($ReleaseFile) | ConvertFrom-Json
        if ($release.clientCommit -notmatch '^[a-f0-9]{40}$' -or $release.sourceArchiveSha256 -notmatch '^[a-f0-9]{64}$') { throw 'The installer source pin is invalid.' }
        if ($Action -eq 'Commit') {
            $check=[IO.File]::ReadAllText($CheckFile) | ConvertFrom-Json
            $answer=Complete-NeutronStorage $DataRoot $InstallDirectory $ControlDirectory $release $check ([bool]$ProcessOverride)
        } elseif ($Action -eq 'Validate') {
            $answer=(Test-NeutronStorage $DataRoot $InstallDirectory $ControlDirectory $release ([bool]$ProcessOverride) $false).Root
        } else {
            $started=[datetime]::UtcNow.ToString('o')
            $result=Test-NeutronStorage $DataRoot $InstallDirectory $ControlDirectory $release ([bool]$ProcessOverride)
            Test-NeutronWrite $result.Root
            if (-not $result.ExistingProfile) { [IO.File]::WriteAllText((Join-Path $result.Root '.neutron-storage-v1'),'dev.neutron.launcher.storage.v1') }
            if (-not $ProcessOverride) {
                Assert-NeutronNoLinks $ControlDirectory
                Test-NeutronWrite $ControlDirectory
                $pointer=Join-Path $ControlDirectory 'data-directory.txt'
                if ([IO.File]::Exists($pointer) -and ([IO.File]::GetAttributes($pointer) -band [IO.FileAttributes]::ReadOnly)) { throw 'The saved data location is read-only. The existing location was retained.' }
            }
            $profile=Read-NeutronProfile $result.Root
            @{root=$result.Root;clientCommit=$release.clientCommit;startedAt=$started;options=$(if($profile){$profile.options}else{$null});preferences=$(if($profile){$profile.preferences}else{$null})} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $CheckFile -Encoding UTF8
            $answer=$result.Root
        }
    }
    [IO.File]::WriteAllText($ResultFile,$answer,$encoding)
    exit 0
} catch {
    [IO.File]::WriteAllText($ResultFile,$_.Exception.Message+"`r`n",$encoding)
    if (-not $ProcessOverride -and $ControlDirectory) {
        try {
            Assert-NeutronNoLinks $ControlDirectory
            [IO.Directory]::CreateDirectory($ControlDirectory) | Out-Null
            @{stage='storage-'+$Action.ToLowerInvariant();error=$_.Exception.Message;date=[datetime]::UtcNow.ToString('o');dataRoot=$DataRoot;launcherFilesChanged=($Action -eq 'Commit')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $ControlDirectory 'storage-preflight-error.json') -Encoding UTF8
        } catch { }
    }
    exit 112
}
