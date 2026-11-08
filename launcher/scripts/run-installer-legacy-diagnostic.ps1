$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if($env:GITHUB_ACTIONS -ne 'true' -or $env:RUNNER_ENVIRONMENT -ne 'github-hosted'){throw 'Disposable hosted Windows runner only.'}
$root=Join-Path $env:RUNNER_TEMP ('neutron-legacy-'+$env:GITHUB_RUN_ID+'-'+$env:GITHUB_RUN_ATTEMPT)
if(Test-Path -LiteralPath $root){throw 'Diagnostic root already exists.'}
[IO.Directory]::CreateDirectory($root) | Out-Null
$name='Ntrn'+[guid]::NewGuid().ToString('N').Substring(0,12)
$password=ConvertTo-SecureString ('Nn1!'+[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')) -AsPlainText -Force
$account=New-LocalUser -Name $name -Password $password -AccountNeverExpires
$sid=$account.SID.Value
$child=$null; $completed=$false
try {
    Add-LocalGroupMember -SID 'S-1-5-32-545' -Member $name
    & "$env:SystemRoot/System32/icacls.exe" $root /grant "*${sid}:(OI)(CI)F" | Out-Null
    if($LASTEXITCODE){throw 'Fixture permission grant failed.'}
    $script=Join-Path $PSScriptRoot 'installer-legacy-diagnostic.ps1'
    & "$env:SystemRoot/System32/icacls.exe" $PSScriptRoot /grant "*${sid}:(OI)(CI)RX" | Out-Null
    if($LASTEXITCODE){throw 'Script permission grant failed.'}
    $wrapper=Join-Path $root 'run.ps1'
    $body=@'
$ErrorActionPreference='Stop'
$env:GITHUB_ACTIONS='true'; $env:RUNNER_ENVIRONMENT='github-hosted'; $env:NEUTRON_DIAGNOSTIC_CHILD='1'
$env:NEUTRON_DIAGNOSTIC_ROOT='__ROOT__'
$env:USERPROFILE=[Environment]::ExpandEnvironmentVariables((Get-ItemProperty 'HKLM:\Software\Microsoft\Windows NT\CurrentVersion\ProfileList\__SID__').ProfileImagePath)
$env:APPDATA=Join-Path $env:USERPROFILE 'AppData/Roaming'; $env:LOCALAPPDATA=Join-Path $env:USERPROFILE 'AppData/Local'
New-Item -ItemType Directory -Force -Path $env:APPDATA,$env:LOCALAPPDATA | Out-Null
Remove-Item Env:NEUTRON_DATA_ROOT -ErrorAction SilentlyContinue
try { & '__SCRIPT__' *> '__ROOT__\diagnostic.log'; exit 0 }
catch { $_ | Out-String | Add-Content -LiteralPath '__ROOT__\diagnostic.log'; exit 1 }
'@
    $body=$body.Replace('__ROOT__',$root.Replace("'","''")).Replace('__SID__',$sid).Replace('__SCRIPT__',$script.Replace("'","''"))
    [IO.File]::WriteAllText($wrapper,$body)
    $credential=[pscredential]::new(($env:COMPUTERNAME+'\'+$name),$password)
    $child=Start-Process -FilePath (Join-Path $PSHOME 'pwsh.exe') -Credential $credential -LoadUserProfile -WorkingDirectory $root -ArgumentList ('-NoProfile -NonInteractive -File "'+$wrapper+'"') -WindowStyle Hidden -PassThru
    $deadline=[datetime]::UtcNow.AddMinutes(40)
    while(-not $child.WaitForExit(15000)) {
        if([datetime]::UtcNow -gt $deadline){throw 'Owned diagnostic timed out; account retained for disposable runner teardown.'}
        Write-Host 'Disposable legacy installer diagnosis is still running.'
    }
    $completed=$true
    Get-Content -LiteralPath (Join-Path $root 'diagnostic.log') -Tail 90
    if($child.ExitCode -ne 0){throw ('Legacy diagnosis reported a failure (exit '+$child.ExitCode+'). Inspect actual UI/process evidence.')}
} finally {
    if($completed){Remove-LocalUser -SID $sid}
}
