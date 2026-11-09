$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot '../resources/storage-preflight.ps1') -LibraryOnly
# Small synthetic files only. No installer, Electron, Java or real profile starts.
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('neutron-preflight-'+[guid]::NewGuid().ToString('N'))
$control=Join-Path $fixture 'Control'; $app=Join-Path $fixture 'App'; $copy=Join-Path $fixture 'Copied Data'
$null=[IO.Directory]::CreateDirectory($control)
$pin='a'*40; $id=$pin+'-'+[guid]::NewGuid().ToString(); $jarHash='b'*64; $jsonHash='c'*64
$release=[pscustomobject]@{schemaVersion=2;channel='stable';launcherVersion='0.3.0';repository='YoungJibbit95/Neutron-Client'}
$script:testFreeBytes=122GB
function Get-NeutronFreeBytes([string]$Root) { return $script:testFreeBytes }
function Assert-True([bool]$Condition,[string]$Message) { if(-not $Condition){throw $Message} }
function Assert-Rejected([scriptblock]$Run,[string]$Pattern) {
    try { & $Run | Out-Null } catch { if($_.Exception.Message -notlike ('*'+$Pattern+'*')){throw}; return }
    throw ('Expected rejection: '+$Pattern)
}
function Save-Json($File,$Value) { [IO.File]::WriteAllText((Get-NeutronFsPath $File),($Value | ConvertTo-Json -Depth 12),[Text.UTF8Encoding]::new($false)) }
function New-Profile([string]$Root) {
    $null=[IO.Directory]::CreateDirectory((Get-NeutronFsPath (Join-Path $Root ('versions/'+$id))))
    $manifest=Join-Path $Root ('versions/'+$id+'/launch-manifest.json')
    Save-Json $manifest @{schemaVersion=2;commit=$pin;release=@{id=123;tag='v0.2.1';jar=@{sha256=$jarHash};json=@{sha256=$jsonHash}};classpath=@(@{path='neutron-client.jar';sha256=$jarHash});versionJson=@{path='Neutron-0.2.1.json';sha256=$jsonHash}}
    $state=@{schemaVersion=1;selected=$id;runtime=$null;builds=@(@{id=$id;commit=$pin;releaseId=123;releaseTag='v0.2.1';version='0.2.1';manifestSha256=(Get-NeutronFileHash $manifest);date=[datetime]::UtcNow.ToString('o');sizeMiB=1});options=@{ramMiB=2048;gameDir='F:\Separate Game';java='managed';javaPath='';width=1280;height=720;fullscreen=$false;jvmArgs=''};preferences=@{theme='ocean';locale='de';scale=100;effects=70;reducedMotion=$false}}
    Save-Json (Join-Path $Root 'state.json') $state
    [IO.File]::WriteAllText((Join-Path $Root 'personal.txt'),'keep my data')
}
try {
    New-Profile $control
    $stateFile=Join-Path $control 'state.json'; $originalHash=(Get-NeutronFileHash $stateFile)
    $null=[IO.Directory]::CreateDirectory($app)
    $exe=Join-Path $app 'Neutron Launcher.exe'; [IO.File]::WriteAllText($exe,'original EXE sentinel'); $exeHash=(Get-NeutronFileHash $exe)
    # Process-name collisions must not block copying the selected profile.
    # No process is spawned: model both the real installer path and a foreign EXE.
    $session=[Diagnostics.Process]::GetCurrentProcess().SessionId
    $mine=[pscustomobject]@{SessionId=$session;Path=(Join-Path $app 'Neutron Launcher.exe')}
    $other=[pscustomobject]@{SessionId=$session;Path=(Join-Path $fixture 'Other/Neutron Launcher.exe')}
    $differentSession=[pscustomobject]@{SessionId=($session+1);Path=$mine.Path}
    Assert-True (-not (Test-NeutronInstalledLauncherRunning $app @($other) $session)) 'Foreign same-name EXE blocked a profile copy'
    Assert-True (-not (Test-NeutronInstalledLauncherRunning $app @($differentSession) $session)) 'Other user session blocked a profile copy'
    Assert-True (Test-NeutronInstalledLauncherRunning $app @($other,$mine) $session) 'Installed EXE did not block a profile copy'
    Assert-True (Test-NeutronInstalledLauncherRunning $app @([pscustomobject]@{SessionId=$session;Path=$mine.Path.ToUpperInvariant()}) $session) 'Installed EXE comparison is case-sensitive'
    Assert-Rejected { Test-NeutronInstalledLauncherRunning $app @([pscustomobject]@{SessionId=$session;Path=$null}) $session } 'Cannot verify'
    # Historical source-built versions are readable and retained, but cannot
    # masquerade as a downloadable release cache during a low-space upgrade.
    $legacy=Join-Path $fixture 'Legacy Profile'; New-Profile $legacy
    $legacyState=Read-NeutronProfile $legacy
    foreach($field in @('releaseId','releaseTag','version')){$legacyState.builds[0].PSObject.Properties.Remove($field)}
    Save-Json (Join-Path $legacy ('versions/'+$id+'/launch-manifest.json')) @{schemaVersion=1;archiveSha256=('d'*64)}
    $legacyState.builds[0].manifestSha256=Get-NeutronFileHash (Join-Path $legacy ('versions/'+$id+'/launch-manifest.json'))
    Save-Json (Join-Path $legacy 'state.json') $legacyState
    Assert-True ($null -ne (Read-NeutronProfile $legacy)) 'Legacy profile migration was rejected'
    $script:testFreeBytes=[long](2.77GB)
    Assert-Rejected { Test-NeutronStorage $legacy $app $control $release $true } 'At least 3 GiB'
    Assert-True ((Get-NeutronFileHash $exe) -eq $exeHash -and (Get-NeutronFileHash $stateFile) -eq $originalHash) 'Low-space refusal changed original files'
    Assert-True (-not (Test-Path (Join-Path $control 'data-directory.txt'))) 'Low-space refusal committed a selector'
    Assert-True ((Test-NeutronStorage $control $app $control $release $false).CachedPinHint) 'Matching cache hint rejected under low space'
    # NSIS uses 32-bit Windows PowerShell. Its optional provider cmdlet may be
    # unavailable; production hashing must work entirely through .NET streams.
    function Get-FileHash { throw 'Provider hash cmdlet deliberately unavailable' }
    try { Assert-True ((Test-NeutronStorage $control $app $control $release $false).CachedPinHint) 'Cache hint depends on optional hash cmdlet' }
    finally { Remove-Item Function:\Get-FileHash }
    $script:testFreeBytes=122GB
    $null=[IO.Directory]::CreateDirectory($copy)
    Assert-Rejected { Test-NeutronStorage $copy $app $control $release $false } 'existing profile'
    # Explicit children avoid wildcard/LiteralPath ambiguity.
    Get-ChildItem -LiteralPath $control | Copy-Item -Destination $copy -Recurse
    $result=Test-NeutronStorage $copy $app $control $release $false
    Assert-True ($result.ExistingProfile -and $result.Root -eq $copy) 'Complete profile copy rejected'
    [IO.File]::WriteAllText((Join-Path $copy 'personal.txt'),'different')
    Assert-Rejected { Test-NeutronStorage $copy $app $control $release $false } 'incomplete or differs'
    [IO.File]::WriteAllText((Join-Path $copy 'personal.txt'),'keep my data')
    $manifest=Join-Path $copy ('versions/'+$id+'/launch-manifest.json'); $manifestBytes=[IO.File]::ReadAllBytes($manifest)
    [IO.File]::Delete($manifest)
    Assert-Rejected { Test-NeutronStorage $copy $app $control $release $false } 'invalid or incomplete'
    [IO.File]::WriteAllBytes($manifest,$manifestBytes)
    $saved=[IO.File]::ReadAllBytes((Join-Path $copy 'state.json'))
    [IO.File]::WriteAllText((Join-Path $copy 'state.json'),'{"schemaVersion":99}')
    Assert-Rejected { Test-NeutronStorage $copy $app $control $release $false } 'invalid or incomplete'
    [IO.File]::WriteAllBytes((Join-Path $copy 'state.json'),$saved)
    foreach($kind in @('ramMiB','theme')) {
        $invalid=[Text.Encoding]::UTF8.GetString($saved).TrimStart([char]0xfeff) | ConvertFrom-Json
        if($kind -eq 'ramMiB'){$invalid.options.ramMiB='invalid'}else{$invalid.preferences.theme='invalid'}
        Save-Json (Join-Path $copy 'state.json') $invalid
        Assert-Rejected { Test-NeutronStorage $copy $app $control $release $false } 'invalid or incomplete'
    }
    [IO.File]::WriteAllBytes((Join-Path $copy 'state.json'),$saved)
    foreach($bad in @([IO.Path]::GetPathRoot($fixture),$app,(Join-Path $app 'data'),(Split-Path $app),'relative','\\server\share','F:\bad:stream')) {
        Assert-Rejected { Get-NeutronStoragePath $bad $app } ''
    }
    $unrelated=Join-Path $fixture 'Unrelated'; $null=[IO.Directory]::CreateDirectory($unrelated); [IO.File]::WriteAllText((Join-Path $unrelated 'photo.txt'),'personal')
    Assert-Rejected { Test-NeutronStorage $unrelated $app $control $release $true } 'unrelated'
    $link=Join-Path $fixture 'Linked'; New-Item -ItemType Junction -Path $link -Target $copy | Out-Null
    Assert-Rejected { Test-NeutronStorage $link $app $control $release $true } 'Linked'
    $profile=Read-NeutronProfile $copy
    $check=[pscustomobject]@{root=$copy;launcherVersion=$release.launcherVersion;repository=$release.repository;startedAt=[datetime]::UtcNow.AddMinutes(-1).ToString('o');options=$profile.options;preferences=$profile.preferences}
    $logs=Join-Path $copy 'logs'; $null=[IO.Directory]::CreateDirectory($logs)
    $receipt=[ordered]@{schemaVersion=2;launcherVersion='0.3.0';clientCommit=$pin;releaseId=123;releaseTag='v0.2.1';clientVersion='0.2.1';clientJarSha256=$jarHash;versionJsonSha256=$jsonHash;clientManifestSha256=$profile.builds[0].manifestSha256;toolchainId='temurin8u482b08';phase='verified-before-activation';selectedBuildId=$id;completedAt=[datetime]::UtcNow.AddHours(-1).ToString('o')}
    Save-Json (Join-Path $logs 'installer-result.json') $receipt
    Assert-Rejected { Complete-NeutronStorage $copy $app $control $release $check $false } 'fresh committed receipt'
    $receipt.completedAt=[datetime]::UtcNow.ToString('o'); Save-Json (Join-Path $logs 'installer-result.json') $receipt
    $receipt.clientJarSha256='e'*64; Save-Json (Join-Path $logs 'installer-result.json') $receipt
    Assert-Rejected { Complete-NeutronStorage $copy $app $control $release $check $true } 'does not match'
    $receipt.clientJarSha256=$jarHash; Save-Json (Join-Path $logs 'installer-result.json') $receipt
    $pointer=Join-Path $control 'data-directory.txt'; [IO.File]::WriteAllText($pointer,$control,[Text.Encoding]::Unicode); $pointerHash=(Get-NeutronFileHash $pointer)
    Complete-NeutronStorage $copy $app $control $release $check $true | Out-Null
    Assert-True ((Get-NeutronFileHash $pointer) -eq $pointerHash) 'Process override changed real selector'
    [IO.File]::SetAttributes($pointer,[IO.FileAttributes]::ReadOnly)
    try { Assert-Rejected { Complete-NeutronStorage $copy $app $control $release $check $false } 'read-only' }
    finally { [IO.File]::SetAttributes($pointer,[IO.FileAttributes]::Normal) }
    Assert-True ((Get-NeutronFileHash $pointer) -eq $pointerHash) 'Failed selector commit changed old pointer'
    Complete-NeutronStorage $copy $app $control $release $check $false | Out-Null
    Assert-True ((Resolve-NeutronStorage $app $control) -eq $copy) 'Saved root did not survive re-resolution'
    Assert-True ((Get-NeutronFileHash $stateFile) -eq $originalHash -and (Get-NeutronFileHash $exe) -eq $exeHash) 'Profile adoption changed original files'
    $longSource=Join-Path $fixture ('Long Source '+('x'*35)); $shortCopy=Join-Path $fixture 'Short Copy'
    New-Profile $longSource; New-Profile $shortCopy
    [IO.File]::WriteAllBytes((Join-Path $shortCopy 'state.json'),[IO.File]::ReadAllBytes((Join-Path $longSource 'state.json')))
    $relative='libraries\'+('n'*(270-$longSource.Length-'libraries\'.Length-'\library.jar'.Length-1))+'\library.jar'
    $longFile=Join-Path $longSource $relative; $shortFile=Join-Path $shortCopy $relative
    Assert-True ($longFile.Length -gt 260 -and $shortFile.Length -lt 260) 'Long-path fixture does not cross MAX_PATH'
    $null=[IO.Directory]::CreateDirectory((Get-NeutronFsPath (Split-Path $longFile)))
    $null=[IO.Directory]::CreateDirectory((Get-NeutronFsPath (Split-Path $shortFile)))
    [IO.File]::WriteAllText((Get-NeutronFsPath $longFile),'long library bytes'); [IO.File]::WriteAllText((Get-NeutronFsPath $shortFile),'long library bytes')
    Assert-NeutronProfileCopy $longSource $shortCopy
    [IO.File]::WriteAllText((Get-NeutronFsPath $shortFile),'different long library bytes')
    Assert-Rejected { Assert-NeutronProfileCopy $longSource $shortCopy } 'incomplete or differs'
    $missing=Join-Path $fixture 'Lost Profile'
    [IO.File]::WriteAllText($pointer,$missing,[Text.Encoding]::Unicode); $pointerHash=(Get-NeutronFileHash $pointer)
    Assert-Rejected { Test-NeutronStorage $missing $app $control $release $false } 'existing profile'
    Assert-True (-not (Test-Path -LiteralPath $missing)) 'Missing saved profile was recreated'
    $null=[IO.Directory]::CreateDirectory($missing)
    Assert-Rejected { Test-NeutronStorage $missing $app $control $release $false } 'existing profile'
    Assert-True ((Get-NeutronFileHash $pointer) -eq $pointerHash -and (Get-NeutronFileHash $stateFile) -eq $originalHash -and [IO.Directory]::GetFileSystemEntries($missing).Length -eq 0) 'Missing/empty profile refusal changed persistent files'
    Write-Host 'Storage preflight fixtures passed: low space, owned executable guard, cache hint, complete/incomplete adoption, settings, invalid state, missing selected build, unsafe paths, links, stale receipt, override, read-only and atomic selector.'
} finally {
    # Only the unique fixture created above. Remove the junction itself first.
    $owned=[IO.Path]::GetFullPath($fixture)
    if(-not $owned.StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath()),[StringComparison]::OrdinalIgnoreCase) -or (Split-Path $owned -Leaf) -notlike 'neutron-preflight-*'){throw 'Unsafe fixture cleanup'}
    $junction=Join-Path $owned 'Linked'; if(Test-Path -LiteralPath $junction){[IO.Directory]::Delete($junction)}
    # .NET extended paths also remove only our synthetic MAX_PATH fixture.
    [IO.Directory]::Delete((Get-NeutronFsPath $owned),$true)
}
