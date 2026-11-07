$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot '../resources/storage-preflight.ps1') -LibraryOnly
# Small synthetic files only. No installer, Electron, Java or real profile starts.
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('neutron-preflight-'+[guid]::NewGuid().ToString('N'))
$control=Join-Path $fixture 'Control'; $app=Join-Path $fixture 'App'; $copy=Join-Path $fixture 'Copied Data'
$null=[IO.Directory]::CreateDirectory($control)
$pin='a'*40; $id=$pin+'-'+[guid]::NewGuid().ToString(); $archive='b'*64
$release=[pscustomobject]@{launcherVersion='0.2.2';clientCommit=$pin;sourceArchiveSha256=$archive}
$script:testFreeBytes=122GB
function Get-NeutronFreeBytes([string]$Root) { return $script:testFreeBytes }
function Assert-True([bool]$Condition,[string]$Message) { if(-not $Condition){throw $Message} }
function Assert-Rejected([scriptblock]$Run,[string]$Pattern) {
    try { & $Run | Out-Null } catch { if($_.Exception.Message -notlike ('*'+$Pattern+'*')){throw}; return }
    throw ('Expected rejection: '+$Pattern)
}
function Save-Json($File,$Value) { $Value | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $File -Encoding UTF8 }
function New-Profile([string]$Root) {
    $null=[IO.Directory]::CreateDirectory((Join-Path $Root ('versions/'+$id)))
    $manifest=Join-Path $Root ('versions/'+$id+'/launch-manifest.json')
    Save-Json $manifest @{archiveSha256=$archive}
    $state=@{schemaVersion=1;selected=$id;runtime=$null;builds=@(@{id=$id;commit=$pin;manifestSha256=(Get-FileHash $manifest).Hash.ToLowerInvariant();date=[datetime]::UtcNow.ToString('o');sizeMiB=1});options=@{ramMiB=2048;gameDir='F:\Separate Game';java='managed';javaPath='';width=1280;height=720;fullscreen=$false;jvmArgs=''};preferences=@{theme='ocean';locale='de';scale=100;effects=70;reducedMotion=$false}}
    Save-Json (Join-Path $Root 'state.json') $state
    [IO.File]::WriteAllText((Join-Path $Root 'personal.txt'),'keep my data')
}
try {
    New-Profile $control
    $stateFile=Join-Path $control 'state.json'; $originalHash=(Get-FileHash $stateFile).Hash
    $null=[IO.Directory]::CreateDirectory($app)
    $exe=Join-Path $app 'Neutron Launcher.exe'; [IO.File]::WriteAllText($exe,'original EXE sentinel'); $exeHash=(Get-FileHash $exe).Hash
    $newRelease=[pscustomobject]@{launcherVersion='0.2.2';clientCommit=('c'*40);sourceArchiveSha256=$archive}
    $script:testFreeBytes=[long](2.77GB)
    Assert-Rejected { Test-NeutronStorage $control $app $control $newRelease $false } 'At least 3 GiB'
    Assert-True ((Get-FileHash $exe).Hash -eq $exeHash -and (Get-FileHash $stateFile).Hash -eq $originalHash) 'Low-space refusal changed original files'
    Assert-True (-not (Test-Path (Join-Path $control 'data-directory.txt'))) 'Low-space refusal committed a selector'
    Assert-True ((Test-NeutronStorage $control $app $control $release $false).CachedPinHint) 'Matching cache hint rejected under low space'
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
    $check=[pscustomobject]@{root=$copy;clientCommit=$pin;startedAt=[datetime]::UtcNow.AddMinutes(-1).ToString('o');options=$profile.options;preferences=$profile.preferences}
    $logs=Join-Path $copy 'logs'; $null=[IO.Directory]::CreateDirectory($logs)
    $receipt=[ordered]@{launcherVersion='0.2.2';clientCommit=$pin;sourceArchiveSha256=$archive;phase='verified-before-activation';selectedBuildId=$id;completedAt=[datetime]::UtcNow.AddHours(-1).ToString('o')}
    Save-Json (Join-Path $logs 'installer-result.json') $receipt
    Assert-Rejected { Complete-NeutronStorage $copy $app $control $release $check $false } 'fresh committed receipt'
    $receipt.completedAt=[datetime]::UtcNow.ToString('o'); Save-Json (Join-Path $logs 'installer-result.json') $receipt
    $pointer=Join-Path $control 'data-directory.txt'; [IO.File]::WriteAllText($pointer,$control,[Text.Encoding]::Unicode); $pointerHash=(Get-FileHash $pointer).Hash
    Complete-NeutronStorage $copy $app $control $release $check $true | Out-Null
    Assert-True ((Get-FileHash $pointer).Hash -eq $pointerHash) 'Process override changed real selector'
    [IO.File]::SetAttributes($pointer,[IO.FileAttributes]::ReadOnly)
    try { Assert-Rejected { Complete-NeutronStorage $copy $app $control $release $check $false } 'read-only' }
    finally { [IO.File]::SetAttributes($pointer,[IO.FileAttributes]::Normal) }
    Assert-True ((Get-FileHash $pointer).Hash -eq $pointerHash) 'Failed selector commit changed old pointer'
    Complete-NeutronStorage $copy $app $control $release $check $false | Out-Null
    Assert-True ((Resolve-NeutronStorage $app $control) -eq $copy) 'Saved root did not survive re-resolution'
    Assert-True ((Get-FileHash $stateFile).Hash -eq $originalHash -and (Get-FileHash $exe).Hash -eq $exeHash) 'Profile adoption changed original files'
    $missing=Join-Path $fixture 'Lost Profile'
    [IO.File]::WriteAllText($pointer,$missing,[Text.Encoding]::Unicode); $pointerHash=(Get-FileHash $pointer).Hash
    Assert-Rejected { Test-NeutronStorage $missing $app $control $release $false } 'existing profile'
    Assert-True (-not (Test-Path -LiteralPath $missing)) 'Missing saved profile was recreated'
    $null=[IO.Directory]::CreateDirectory($missing)
    Assert-Rejected { Test-NeutronStorage $missing $app $control $release $false } 'existing profile'
    Assert-True ((Get-FileHash $pointer).Hash -eq $pointerHash -and (Get-FileHash $stateFile).Hash -eq $originalHash -and [IO.Directory]::GetFileSystemEntries($missing).Length -eq 0) 'Missing/empty profile refusal changed persistent files'
    Write-Host 'Storage preflight fixtures passed: low space, cache hint, complete/incomplete adoption, settings, invalid state, missing selected build, unsafe paths, links, stale receipt, override, read-only and atomic selector.'
} finally {
    # Only the unique fixture created above. Remove the junction itself first.
    $owned=[IO.Path]::GetFullPath($fixture)
    if(-not $owned.StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath()),[StringComparison]::OrdinalIgnoreCase) -or (Split-Path $owned -Leaf) -notlike 'neutron-preflight-*'){throw 'Unsafe fixture cleanup'}
    $junction=Join-Path $owned 'Linked'; if(Test-Path -LiteralPath $junction){[IO.Directory]::Delete($junction)}
    Remove-Item -LiteralPath $owned -Recurse -Force
}
