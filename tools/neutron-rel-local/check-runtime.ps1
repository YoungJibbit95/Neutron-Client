# REL-P01 check only. No install/start/download/account/profile commands exist.
[CmdletBinding()]
param(
    [switch]$Check,
    [string]$JavaHome = "$env:ProgramFiles\Java\jdk-1.8",
    [string]$MinecraftHome = "$env:APPDATA\.minecraft",
    [string]$JarPath,
    [string]$ExpectedJarSha256,
    [string]$LauncherPath,
    [string]$IsolationRoot
)
$ErrorActionPreference = 'Stop'
$checks = New-Object System.Collections.Generic.List[object]
function Result([string]$Name, [bool]$Ok, [string]$Detail) {
    $checks.Add([pscustomobject]@{check=$Name; status=$(if($Ok){'PASS'}else{'BLOCKED'}); detail=$Detail})
}
function Under([string]$Path, [string]$Root) {
    $p=[IO.Path]::GetFullPath($Path).TrimEnd('\')
    $r=[IO.Path]::GetFullPath($Root).TrimEnd('\')
    return $p.Equals($r,[StringComparison]::OrdinalIgnoreCase) -or $p.StartsWith($r+'\',[StringComparison]::OrdinalIgnoreCase)
}
function VerifiedFile([string]$Path, $Spec) {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    if (!$Spec.sha1 -or $Spec.sha1 -notmatch '^[0-9a-f]{40}$') { return $false }
    if ($null -ne $Spec.size -and (Get-Item -LiteralPath $Path).Length -ne [long]$Spec.size) { return $false }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA1).Hash -ieq $Spec.sha1
}
function LibraryPath([string]$Relative) {
    if (!$Relative -or [IO.Path]::IsPathRooted($Relative)) { throw 'Invalid library path' }
    $base=Join-Path $MinecraftHome 'libraries'
    $p=[IO.Path]::GetFullPath((Join-Path $base $Relative))
    if (!(Under $p $base)) { throw 'Library path escapes its root' }
    return $p
}
try {
    if (!$JarPath) { $JarPath=Join-Path $PSScriptRoot '..\..\target\Neutron-dev-jar-with-dependencies.jar' }
    $java=Join-Path $JavaHome 'bin\java.exe'
    if (Test-Path -LiteralPath $java -PathType Leaf) {
        # -version only; never enumerate running Java command lines.
        $info=New-Object Diagnostics.ProcessStartInfo
        $info.FileName=$java; $info.Arguments='-version'; $info.UseShellExecute=$false; $info.CreateNoWindow=$true
        $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
        $process=[Diagnostics.Process]::Start($info)
        try {
            $version=$process.StandardOutput.ReadToEnd()+$process.StandardError.ReadToEnd()
            $process.WaitForExit(); $javaExit=$process.ExitCode
        } finally { $process.Dispose() }
        Result 'Java8' ($javaExit -eq 0 -and $version -match 'version "1\.8\.' -and $version -match '64-Bit') 'Requires an actual Java 8 64-bit executable.'
    } else { Result 'Java8' $false 'Java 8 executable missing.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    if (Test-Path -LiteralPath $JarPath -PathType Leaf) {
        $hash=(Get-FileHash -LiteralPath $JarPath -Algorithm SHA256).Hash.ToLowerInvariant()
        Result 'JarHash' ($ExpectedJarSha256 -match '^[0-9a-fA-F]{64}$' -and $hash -ieq $ExpectedJarSha256) ('Actual SHA256: '+$hash+'; supply the reviewed ExpectedJarSha256.')
        $zip=[IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($JarPath))
        try {
            $entry=$zip.GetEntry('META-INF/MANIFEST.MF'); $manifest=''
            if ($entry) { $reader=New-Object IO.StreamReader($entry.Open()); try {$manifest=$reader.ReadToEnd()} finally {$reader.Dispose()} }
            Result 'Manifest' ($manifest -match '(?m)^Main-Class: net\.minecraft\.client\.main\.Main\r?$' -and $null -ne $zip.GetEntry('net/minecraft/client/main/Main.class')) 'Only the Assembly direct Main is selected.'
            $required=@('com/google/gson/Gson.class','com/mojang/authlib/yggdrasil/YggdrasilAuthenticationService.class','com/sun/jna/Native.class','oshi/SystemInfo.class','io/netty/bootstrap/Bootstrap.class','org/apache/logging/log4j/core/LoggerContext.class','org/lwjgl/opengl/Display.class','org/lwjgl/openal/AL.class','paulscode/sound/codecs/CodecJOrbis.class','org/reflections/Reflections.class','META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat')
            $missing=@($required | Where-Object {$null -eq $zip.GetEntry($_)})
            Result 'EmbeddedClasses' ($missing.Count -eq 0) ('Missing required entries: '+$missing.Count+'; dynamic discovery requires the separate packaged checks.')
        } finally { $zip.Dispose() }
    } else { Result 'Jar' $false 'Assembly JAR missing; build offline with Java 8 first.' }
    $metadata=Join-Path $MinecraftHome 'versions\1.8.9\1.8.9.json'
    if (Test-Path -LiteralPath $metadata -PathType Leaf) {
        $v=Get-Content -LiteralPath $metadata -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($v.id -ne '1.8.9' -or $v.mainClass -ne 'net.minecraft.client.main.Main' -or $v.javaVersion.majorVersion -ne 8 -or $v.assetIndex.id -ne '1.8') { throw 'Unexpected Vanilla metadata contract' }
        Result 'VanillaMetadata' $true '1.8.9 / direct Main / Java 8 / asset index 1.8.'
        Result 'VanillaJar' (VerifiedFile (Join-Path $MinecraftHome 'versions\1.8.9\1.8.9.jar') $v.downloads.client) 'Client size/SHA1 compared with installed version metadata.'
        $index=Join-Path $MinecraftHome 'assets\indexes\1.8.json'
        $indexOk=VerifiedFile $index $v.assetIndex
        Result 'AssetIndex' $indexOk 'Installed index size/SHA1.'
        if ($indexOk) {
            $a=Get-Content -LiteralPath $index -Raw -Encoding UTF8 | ConvertFrom-Json
            $bad=0; $count=0
            foreach ($property in $a.objects.PSObject.Properties) {
                $item=$property.Value; $count++
                if ($item.hash -notmatch '^[0-9a-f]{40}$') { $bad++; continue }
                $path=Join-Path $MinecraftHome ('assets\objects\'+$item.hash.Substring(0,2)+'\'+$item.hash)
                if (!(VerifiedFile $path ([pscustomobject]@{sha1=$item.hash;size=$item.size}))) { $bad++ }
            }
            Result 'AssetObjects' ($count -gt 0 -and $bad -eq 0) ('Objects: '+$count+'; invalid/missing: '+$bad)
        }
        $bad=0; $count=0; $natives=0
        foreach ($lib in $v.libraries) {
            $allowed=$null -eq $lib.rules
            foreach ($rule in $lib.rules) {
                if (($null -eq $rule.os -or $rule.os.name -eq 'windows') -and ($null -eq $rule.os.version -or [Environment]::OSVersion.Version.ToString() -match $rule.os.version) -and ($null -eq $rule.os.arch -or 'amd64' -match $rule.os.arch)) { $allowed=$rule.action -eq 'allow' }
            }
            if (!$allowed) { continue }
            if ($lib.downloads.artifact) {
                $count++; if (!(VerifiedFile (LibraryPath $lib.downloads.artifact.path) $lib.downloads.artifact)) { $bad++ }
            }
            if ($lib.natives.windows) {
                $classifier=$lib.natives.windows.Replace('${arch}','64'); $native=$lib.downloads.classifiers.$classifier
                $natives++; $count++
                if (!$native -or !(VerifiedFile (LibraryPath $native.path) $native)) { $bad++ }
            }
        }
        Result 'VanillaLibraries' ($count -gt 0 -and $natives -gt 0 -and $bad -eq 0) ('Windows artifacts/native archives: '+$count+'; native archives: '+$natives+'; invalid/missing: '+$bad+'. This is not the Neutron classpath.')
    } else { Result 'VanillaMetadata' $false 'Installed Vanilla 1.8.9 metadata missing; user must provision it through the official launcher.' }
    $launcher=$false
    if ($LauncherPath) {
        if ((Split-Path -Leaf $LauncherPath) -eq 'MinecraftLauncher.exe' -and (Test-Path -LiteralPath $LauncherPath -PathType Leaf)) {
            $sig=Get-AuthenticodeSignature -LiteralPath $LauncherPath
            $launcher=$sig.Status -eq 'Valid' -and $sig.SignerCertificate.Subject -match 'Microsoft|Mojang'
        }
    } else {
        foreach ($path in @("${env:ProgramFiles(x86)}\Minecraft Launcher\MinecraftLauncher.exe","$env:ProgramFiles\Minecraft Launcher\MinecraftLauncher.exe","$env:LOCALAPPDATA\Programs\Minecraft Launcher\MinecraftLauncher.exe")) {
            if (Test-Path -LiteralPath $path -PathType Leaf) {
                $sig=Get-AuthenticodeSignature -LiteralPath $path
                if ($sig.Status -eq 'Valid' -and $sig.SignerCertificate.Subject -match 'Microsoft|Mojang') { $launcher=$true }
            }
        }
        if (Get-Command Get-AppxPackage -ErrorAction SilentlyContinue) { if (@(Get-AppxPackage -Name 'Microsoft.4297127D64EC6').Count -gt 0) { $launcher=$true } }
    }
    Result 'OfficialLauncher' $launcher 'Checks known executable signatures or launcher AppX identity; does not prove login or prior Vanilla start.'
    $isolation=$false
    if ($IsolationRoot) {
        $repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
        $isolation=!(Test-Path -LiteralPath $IsolationRoot) -and !(Under $IsolationRoot $MinecraftHome) -and !(Under $IsolationRoot $env:USERPROFILE) -and !(Under $IsolationRoot $repo) -and !(Under $MinecraftHome $IsolationRoot) -and !(Under $repo $IsolationRoot)
    }
    Result 'IsolationPlan' $isolation 'Requires a new external root. Future gameDir, CWD and user.home must use separate game/cwd/home children. No directory is created.'
} catch {
    # Do not print exception text: malformed user-supplied metadata may contain secrets.
    Result 'InputValidation' $false ('Invalid, unreadable or unsupported technical metadata; no writes performed. '+$_.Exception.GetType().Name+' at line '+$_.InvocationInfo.ScriptLineNumber)
}
Result 'NativeAndClasspathQualification' $false 'BLOCKED: inventory alone cannot prove native ABI, complete reflective discovery or a launcher classpath. Use reviewed REL-P01 evidence; no automatic start.'
Result 'UserVanillaStartAndInstallationConsent' $false 'BLOCKED: requires explicit user confirmation of official Vanilla start and a separately reviewed concrete installation. No authentication data is read.'
[pscustomobject]@{status='BLOCKED'; mode='CHECK_ONLY'; checks=$checks.ToArray()} | ConvertTo-Json -Depth 6
exit 2
