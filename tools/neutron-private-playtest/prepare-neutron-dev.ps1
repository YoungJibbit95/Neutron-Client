[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$IsolationRoot,
    [Parameter(Mandatory=$true)][string]$AssetsDir,
    [Parameter(Mandatory=$true)][string]$VersionMetadata,
    [Parameter(Mandatory=$true)][string]$ExpectedJarSha256,
    [string]$JavaHome='C:\Program Files\Java\jdk-1.8',
    [string]$JarPath
)
$ErrorActionPreference='Stop'
$env:PSModulePath="$env:SystemRoot\System32\WindowsPowerShell\v1.0\Modules"
Set-StrictMode -Version Latest
if(!$JarPath) {$JarPath=Join-Path $PSScriptRoot '..\..\target\Neutron-dev-jar-with-dependencies.jar'}
function Full([string]$p) {
    if(!$p -or ![IO.Path]::IsPathRooted($p) -or $p.StartsWith('\\') -or $p.IndexOfAny([char[]]'";') -ge 0 -or $p.Contains("`n") -or $p.Contains("`r")) {throw 'Absolute local path required.'}
    return [IO.Path]::GetFullPath($p).TrimEnd('\')
}
function Under([string]$p,[string]$r) {return $p.Equals($r,[StringComparison]::OrdinalIgnoreCase) -or $p.StartsWith($r+'\',[StringComparison]::OrdinalIgnoreCase)}
function NoLink([string]$p) {
    while($p) {
        if((Test-Path -LiteralPath $p) -and ((Get-Item -Force -LiteralPath $p).Attributes -band [IO.FileAttributes]::ReparsePoint)) {throw 'Reparse paths are forbidden.'}
        $p=Split-Path -Parent $p
    }
}
function Hash([string]$p) {return (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant()}
$root=Full $IsolationRoot; $assets=Full $AssetsDir; $jar=Full $JarPath; $jdk=Full $JavaHome
$metadata=Full $VersionMetadata; $repo=Full (Join-Path $PSScriptRoot '..\..')
if(Test-Path -LiteralPath $root) {throw 'The entire test root must be new; no import or reuse of existing roots.'}
$protected=@($env:USERPROFILE,(Join-Path $env:APPDATA '.minecraft'),$repo,$assets,$jdk)
$registered=@(& git -C $repo worktree list --porcelain)
if($LASTEXITCODE) {throw 'Git registry unavailable.'}
$protected+=@($registered|Where-Object {$_ -like 'worktree *'}|ForEach-Object {$_.Substring(9)})
foreach($p in $protected) { $p=Full $p; if((Under $root $p) -or (Under $p $root)) {throw 'Root overlaps a protected location.'} }
foreach($p in @($root,$assets,$jar,$jdk,$metadata)) {NoLink $p}
if($ExpectedJarSha256 -notmatch '^[a-fA-F0-9]{64}$' -or (Hash $jar) -ine $ExpectedJarSha256) {throw 'Assembly SHA256 mismatch.'}
$java=Join-Path $jdk 'bin\java.exe'; $javac=Join-Path $jdk 'bin\javac.exe'
$info=New-Object Diagnostics.ProcessStartInfo
$info.FileName=$java; $info.Arguments='-version'; $info.UseShellExecute=$false; $info.CreateNoWindow=$true
$info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
$p=[Diagnostics.Process]::Start($info)
try { $version=$p.StandardOutput.ReadToEnd()+$p.StandardError.ReadToEnd(); $p.WaitForExit(); $javaExit=$p.ExitCode } finally {$p.Dispose()}
if($javaExit -ne 0 -or $version -notmatch 'version "1\.8\.' -or $version -notmatch '64-Bit') {throw 'Java8 x64 required.'}
$v=Get-Content -Raw -Encoding UTF8 -LiteralPath $metadata|ConvertFrom-Json
if($v.id -ne '1.8.9' -or $v.assetIndex.id -ne '1.8' -or $v.javaVersion.majorVersion -ne 8) {throw '1.8.9 metadata contract required.'}
$index=Join-Path $assets 'indexes\1.8.json'
if((Get-Item -LiteralPath $index).Length -ne $v.assetIndex.size -or (Get-FileHash -LiteralPath $index -Algorithm SHA1).Hash -ine $v.assetIndex.sha1) {throw 'Index differs from installed metadata.'}
$a=Get-Content -Raw -Encoding UTF8 -LiteralPath $index|ConvertFrom-Json
$count=0
foreach($prop in $a.objects.PSObject.Properties) {
    $item=$prop.Value
    if($item.hash -notmatch '^[0-9a-f]{40}$') {throw 'Invalid asset hash.'}
    $p=Join-Path $assets ('objects\'+$item.hash.Substring(0,2)+'\'+$item.hash)
    NoLink $p
    if((Get-Item -LiteralPath $p).Length -ne $item.size -or (Get-FileHash -LiteralPath $p -Algorithm SHA1).Hash -ine $item.hash) {throw 'Missing/corrupt asset object.'}
    $count++
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($jar)
try {
    $reader=New-Object IO.StreamReader($zip.GetEntry('META-INF/MANIFEST.MF').Open())
    try {$manifest=$reader.ReadToEnd()} finally {$reader.Dispose()}
    if($manifest -notmatch '(?m)^Main-Class: net\.minecraft\.client\.main\.Main\r?$') {throw 'Wrong Assembly Main-Class.'}
    foreach($entry in @('net/minecraft/client/main/Main.class','org/lwjgl/opengl/Display.class','org/reflections/Reflections.class','com/mojang/authlib/yggdrasil/YggdrasilAuthenticationService.class')) {
        if(!$zip.GetEntry($entry)) {throw 'Required embedded class missing.'}
    }
} finally {$zip.Dispose()}
New-Item -ItemType Directory -Path $root|Out-Null
foreach($name in @('game','cwd','home','tmp','logs','appdata','localappdata','classes','natives')) {New-Item -ItemType Directory -Path (Join-Path $root $name)|Out-Null}
[IO.File]::WriteAllText((Join-Path $root '.neutron-dev-root'),'neutron-local-dev-1')
[IO.File]::WriteAllText((Join-Path $root 'game\options.txt'),"snooperEnabled:false`nfullscreen:false`nmaxFps:60`nrenderDistance:6`nmipmapLevels:2`n")
$source=Join-Path $PSScriptRoot 'NeutronOfflineMain.java'; $classes=Join-Path $root 'classes'
& $javac ("-J-Djava.io.tmpdir="+(Join-Path $root 'tmp')) -encoding UTF-8 -source 8 -target 8 -d $classes $source
if($LASTEXITCODE) {throw 'Harness compilation failed.'}
$nativeFiles=@()
foreach($name in @('lwjgl64.dll','OpenAL64.dll','jinput-dx8_64.dll','jinput-raw_64.dll')) {
    $from=Join-Path $repo ('test_natives\windows\'+$name)
    $bytes=[IO.File]::ReadAllBytes($from); $offset=[BitConverter]::ToInt32($bytes,60)
    if([BitConverter]::ToUInt16($bytes,$offset+4) -ne 0x8664) {throw 'Expected AMD64 native.'}
    $to=Join-Path $root ('natives\'+$name); Copy-Item -LiteralPath $from -Destination $to
    if((Hash $from) -ne (Hash $to)) {throw 'Native copy mismatch.'}
    $nativeFiles+=,[ordered]@{name=$name;sha256=(Hash $to);bytes=$bytes.Length;peMachine='0x8664'}
}
$classFiles=@(Get-ChildItem -LiteralPath $classes -File|ForEach-Object {[ordered]@{name=$_.Name;sha256=(Hash $_.FullName)}})
$c=[ordered]@{schema='neutron-local-dev-1';mode='OFFLINE_DEV_ONLY';root=$root;java=$java;javaSha256=(Hash $java);
    javaVersion=$version.Trim();jar=$jar;jarSha256=(Hash $jar);classes=$classes;classFiles=$classFiles;
    harnessSourceSha256=(Hash $source);assets=$assets;assetIndexSha256=(Hash $index);assetObjectCount=$count;
    versionMetadataSha256=(Hash $metadata);natives=(Join-Path $root 'natives');nativeFiles=$nativeFiles;
    protectedRoots=$protected;created=(Get-Date).ToString('o')}
$config=Join-Path $root 'dev-config.json'
$c|ConvertTo-Json -Depth 6|Set-Content -Encoding UTF8 -LiteralPath $config
Write-Output ('Prepared OFFLINE_DEV_ONLY; assets='+$count+'; ConfigPath='+$config)
