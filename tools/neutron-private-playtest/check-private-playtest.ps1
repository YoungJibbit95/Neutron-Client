# Read-only adapter for the existing REL-P01 checker. No game or authentication actions.
$ErrorActionPreference = 'Stop'
try {
    $runner = Join-Path $PSScriptRoot '..\neutron-rel-local\check-runtime.ps1'
    if (!(Test-Path -LiteralPath $runner -PathType Leaf)) {
        Write-Output 'Neutron Alpha Check: BLOCKED - runtime checker missing.'
        exit 21
    }
    $parameters = @{ Check = $true }
    $mapping = @{
        NEUTRON_JAVA_HOME = 'JavaHome'
        NEUTRON_MINECRAFT_HOME = 'MinecraftHome'
        NEUTRON_JAR_PATH = 'JarPath'
        NEUTRON_EXPECTED_JAR_SHA256 = 'ExpectedJarSha256'
        NEUTRON_LAUNCHER_PATH = 'LauncherPath'
        NEUTRON_ISOLATION_ROOT = 'IsolationRoot'
    }
    foreach ($name in $mapping.Keys) {
        $value = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ($value) { $parameters[$mapping[$name]] = $value }
    }
    if ($parameters.ContainsKey('ExpectedJarSha256') -and $parameters.ExpectedJarSha256 -notmatch '^[0-9a-fA-F]{64}$') {
        Write-Output 'Neutron Alpha Check: BLOCKED - expected SHA256 must contain exactly 64 hexadecimal characters.'
        exit 23
    }
    # Hashtable splatting passes values as data, including spaces, Unicode and ampersands.
    # Do not use Invoke-Expression, command strings, token arguments or raw exception output.
    $raw = & $runner @parameters 2>$null
    $runnerExit = $LASTEXITCODE
    $result = ($raw -join "`n") | ConvertFrom-Json
    if ($runnerExit -ne 2 -or $result.status -ne 'BLOCKED' -or $result.mode -ne 'CHECK_ONLY' -or !$result.checks) {
        Write-Output 'Neutron Alpha Check: BLOCKED - unsupported runtime-checker result.'
        exit 24
    }
    # Print only allowlisted check identifiers and states, never supplied paths or input contents.
    $names = @('Java8','JarHash','Manifest','EmbeddedClasses','Jar','VanillaMetadata','VanillaJar','AssetIndex','AssetObjects','VanillaLibraries','OfficialLauncher','IsolationPlan','InputValidation','NativeAndClasspathQualification','UserVanillaStartAndInstallationConsent')
    foreach ($check in $result.checks) {
        if ($names -notcontains $check.check -or @('PASS','BLOCKED') -notcontains $check.status) { throw 'Unsupported check schema' }
        Write-Output ($check.check + ': ' + $check.status)
    }
    Write-Output 'Neutron Alpha Check: CHECK_ONLY / BLOCKED'
    Write-Output 'ABI remains PARTIAL; actual native classpath, separate game/cwd/home, session and loader qualification are open.'
    Write-Output 'Scaffold must remain OFF for the first alpha playtest. This checker grants no game-start permission.'
    exit 2
} catch {
    Write-Output 'Neutron Alpha Check: BLOCKED - invalid or unreadable technical input; no game process started.'
    exit 24
}
