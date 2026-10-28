[CmdletBinding()]
param([string]$ConfigPath = $env:NEUTRON_DEV_CONFIG, [switch]$ProbeOnly)
$ErrorActionPreference = 'Stop'
$env:PSModulePath="$env:SystemRoot\System32\WindowsPowerShell\v1.0\Modules"
Set-StrictMode -Version Latest

function FullPath([string]$Path) {
    if (!$Path -or ![IO.Path]::IsPathRooted($Path) -or $Path.StartsWith('\\')) { throw 'An absolute local path is required.' }
    if ($Path.IndexOfAny([char[]]'";') -ge 0 -or $Path.Contains("`n") -or $Path.Contains("`r")) { throw 'Unsupported path character.' }
    return [IO.Path]::GetFullPath($Path).TrimEnd('\')
}
function Under([string]$Path, [string]$Root) {
    return $Path.Equals($Root,[StringComparison]::OrdinalIgnoreCase) -or $Path.StartsWith($Root+'\',[StringComparison]::OrdinalIgnoreCase)
}
function NoLink([string]$Path) {
    $cursor=$Path
    while($cursor) {
        if(Test-Path -LiteralPath $cursor) {
            if((Get-Item -Force -LiteralPath $cursor).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Reparse paths are not permitted.' }
        }
        $cursor=Split-Path -Parent $cursor
    }
}
function HashCheck([string]$Path, [string]$Expected) {
    if($Expected -notmatch '^[a-fA-F0-9]{64}$' -or !(Test-Path -LiteralPath $Path -PathType Leaf)) { throw 'A pinned input is missing.' }
    if((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ine $Expected) { throw 'A pinned input changed; review and prepare again.' }
}
function QuoteArg([string]$Value) {
    # ProcessStartInfo uses the Windows CRT command-line convention; no shell evaluates these values.
    return '"' + [regex]::Replace([regex]::Replace($Value,'(\\*)"','$1$1\"'),'(\\+)$','$1$1') + '"'
}
try {
    if(!$ConfigPath) { throw 'Use -ConfigPath with the reviewed external developer config.' }
    $ConfigPath=FullPath $ConfigPath
    NoLink $ConfigPath
    $c=Get-Content -Raw -Encoding UTF8 -LiteralPath $ConfigPath | ConvertFrom-Json
    if($c.schema -ne 'neutron-local-dev-1' -or $c.mode -ne 'OFFLINE_DEV_ONLY') { throw 'Unsupported developer configuration.' }
    $root=FullPath $c.root; $java=FullPath $c.java; $jar=FullPath $c.jar; $classes=FullPath $c.classes
    $assets=FullPath $c.assets; $natives=FullPath $c.natives
    foreach($p in @($root,$java,$jar,$classes,$assets,$natives)) { NoLink $p }
    # Recheck the actual Git registry on each start. All worktrees remain protected.
    $repo=FullPath (Join-Path $PSScriptRoot '..\..')
    $registered=@(& git -C $repo worktree list --porcelain)
    if($LASTEXITCODE -ne 0) { throw 'Could not inspect protected Git worktrees.' }
    $protected=@($env:USERPROFILE,(Join-Path $env:APPDATA '.minecraft'),$repo)
    $protected+=@($registered | Where-Object {$_ -like 'worktree *'} | ForEach-Object {$_.Substring(9)})
    foreach($p in $protected) {
        $p=FullPath $p
        if((Under $root $p) -or (Under $p $root)) { throw 'Test root overlaps a protected path.' }
    }
    if(!(Under $classes $root) -or !(Under $ConfigPath $root) -or (Under $jar $root) -or (Under $assets $root)) { throw 'Invalid input/root layout.' }
    if((Get-Content -Raw -LiteralPath (Join-Path $root '.neutron-dev-root')) -ne 'neutron-local-dev-1') { throw 'Unprepared test root.' }
    foreach($name in @('game','cwd','home','tmp','logs','appdata','localappdata')) {
        $p=Join-Path $root $name; NoLink $p
        if(!(Test-Path -LiteralPath $p -PathType Container)) { throw 'Missing isolated child directory.' }
    }
    HashCheck $java $c.javaSha256; HashCheck $jar $c.jarSha256
    if(Test-Path -LiteralPath (Join-Path (Split-Path -Parent $jar) 'netty-all-4.0.25.Final.jar')) { throw 'Unexpected indexed classpath sibling.' }
    HashCheck (Join-Path $PSScriptRoot 'NeutronOfflineMain.java') $c.harnessSourceSha256
    foreach($f in $c.classFiles) { HashCheck (Join-Path $classes $f.name) $f.sha256 }
    $actualClasses=@(Get-ChildItem -LiteralPath $classes -File)
    if($actualClasses.Count -ne @($c.classFiles).Count) { throw 'Unexpected harness class files.' }
    foreach($f in $c.nativeFiles) {
        $p=Join-Path $natives $f.name; HashCheck $p $f.sha256
        $bytes=[IO.File]::ReadAllBytes($p); $offset=[BitConverter]::ToInt32($bytes,60)
        if([BitConverter]::ToUInt16($bytes,$offset+4) -ne 0x8664) { throw 'Native is not Windows x64.' }
    }
    if(@(Get-ChildItem -LiteralPath $natives -File).Count -ne @($c.nativeFiles).Count) { throw 'Unexpected native files.' }
    $index=Join-Path $assets 'indexes\1.8.json'; HashCheck $index $c.assetIndexSha256
    $a=Get-Content -Raw -Encoding UTF8 -LiteralPath $index | ConvertFrom-Json
    $objects=0
    foreach($property in $a.objects.PSObject.Properties) {
        $v=$property.Value
        if($v.hash -notmatch '^[a-f0-9]{40}$') { throw 'Invalid asset object hash.' }
        $p=Join-Path $assets ('objects\'+$v.hash.Substring(0,2)+'\'+$v.hash)
        NoLink $p
        if((Get-Item -LiteralPath $p).Length -ne $v.size -or (Get-FileHash -LiteralPath $p -Algorithm SHA1).Hash -ine $v.hash) { throw 'Asset object verification failed.' }
        $objects++
    }
    if($objects -ne $c.assetObjectCount -or $objects -eq 0) { throw 'Asset count changed.' }
    $stamp=Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $logbase=Join-Path $root ('logs\'+$stamp+$(if($ProbeOnly){'-probe'}else{'-game'}))
    $arguments=@('-Xms256m','-Xmx1536m','-Dfile.encoding=UTF-8','-Djava.net.useSystemProxies=false',
        ('-Duser.home='+ (Join-Path $root 'home')),('-Djava.io.tmpdir='+ (Join-Path $root 'tmp')),
        ('-Dorg.lwjgl.librarypath='+$natives),('-Dnet.java.games.input.librarypath='+$natives),('-Djava.library.path='+$natives),
        ('-Dneutron.dev.root='+$root),('-Dneutron.dev.assets='+$assets),('-Dneutron.dev.jar='+$jar),
        ('-Dneutron.dev.classes='+$classes),'-cp',($classes+';'+$jar),'NeutronOfflineMain')
    if($ProbeOnly) { $arguments+='--probe' }
    $info=New-Object Diagnostics.ProcessStartInfo
    $info.FileName=$java; $info.Arguments=($arguments | ForEach-Object {QuoteArg $_}) -join ' '
    $info.WorkingDirectory=Join-Path $root 'cwd'; $info.UseShellExecute=$false; $info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    # Strip JVM injections and redirect all ordinary user-state environment paths in this child only.
    foreach($key in @('_JAVA_OPTIONS','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')) { $info.EnvironmentVariables.Remove($key) }
    $info.EnvironmentVariables['USERPROFILE']=Join-Path $root 'home'
    $info.EnvironmentVariables['HOME']=Join-Path $root 'home'
    $info.EnvironmentVariables['APPDATA']=Join-Path $root 'appdata'
    $info.EnvironmentVariables['LOCALAPPDATA']=Join-Path $root 'localappdata'
    $info.EnvironmentVariables['TEMP']=Join-Path $root 'tmp'; $info.EnvironmentVariables['TMP']=Join-Path $root 'tmp'
    $info.EnvironmentVariables['PATH']=$natives+';'+(Split-Path -Parent $java)+';'+$env:SystemRoot+'\System32;'+$env:SystemRoot
    $info.EnvironmentVariables['HOMEDRIVE']=[IO.Path]::GetPathRoot($root).TrimEnd('\')
    $info.EnvironmentVariables['HOMEPATH']=(Join-Path $root 'home').Substring(2)
    Write-Output 'NEUTRON DEV OFFLINE / NO AUTH / local Singleplayer fixture only. Scaffold must remain OFF.'
    $process=[Diagnostics.Process]::Start($info)
    $outFile=New-Object IO.FileStream(($logbase+'.stdout.log'),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::ReadWrite)
    $errFile=New-Object IO.FileStream(($logbase+'.stderr.log'),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::ReadWrite)
    $outTask=$process.StandardOutput.BaseStream.CopyToAsync($outFile); $errTask=$process.StandardError.BaseStream.CopyToAsync($errFile)
    $receipt=[ordered]@{schema='neutron-dev-process-1';mode='OFFLINE_DEV_ONLY';pid=$process.Id;java=$java;jarSha256=$c.jarSha256;
        started=(Get-Date).ToString('o');cwd=$info.WorkingDirectory;home=(Join-Path $root 'home');game=(Join-Path $root 'game');
        probeOnly=[bool]$ProbeOnly;arguments=$arguments;windows=@();exitCode=$null}
    $receipt|ConvertTo-Json -Depth 6|Set-Content -Encoding UTF8 -LiteralPath ($logbase+'.json')
    Write-Output ('Java8 PID='+$process.Id+'; logs='+$logbase)
    try {
        while(!$process.WaitForExit(500)) {
            $process.Refresh()
            if($process.MainWindowHandle -ne [IntPtr]::Zero) {
                $window=[ordered]@{time=(Get-Date).ToString('o');handle=$process.MainWindowHandle.ToInt64();title=$process.MainWindowTitle}
                if($receipt.windows.Count -eq 0 -or $receipt.windows[-1].title -ne $window.title) {
                    $receipt.windows+=,$window
                    $receipt|ConvertTo-Json -Depth 6|Set-Content -Encoding UTF8 -LiteralPath ($logbase+'.json')
                }
            }
        }
        [Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($outTask,$errTask))
        $receipt.exitCode=$process.ExitCode; $receipt['exited']=(Get-Date).ToString('o')
        $receipt|ConvertTo-Json -Depth 6|Set-Content -Encoding UTF8 -LiteralPath ($logbase+'.json')
        Write-Output ('Java8 exit='+$process.ExitCode)
        $exitCode=$process.ExitCode
    } finally { $outFile.Dispose(); $errFile.Dispose(); $process.Dispose() }
    exit $exitCode
} catch {
    Write-Error ('DEV_START_FAILED at line '+$_.InvocationInfo.ScriptLineNumber+': '+$_.Exception.Message) -ErrorAction Continue
    exit 24
}
