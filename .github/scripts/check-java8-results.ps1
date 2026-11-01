param(
    [Parameter(Mandatory = $true)][datetime]$Since,
    [string]$Reports = 'target/surefire-reports',
    [string]$Summary = 'target/ci-java8-summary.json',
    [switch]$RequireNative
)

$ErrorActionPreference = 'Stop'
$files = @(Get-ChildItem -LiteralPath $Reports -Filter 'TEST-*.xml' -File |
    Where-Object LastWriteTime -GE $Since)
if ($files.Count -eq 0) { throw 'No fresh Surefire XML suites; no test PASS can be claimed' }

$totals = [ordered]@{ suites = $files.Count; tests = 0; failures = 0; errors = 0; skipped = 0 }
$skippedCases = @()
foreach ($file in $files) {
    $suite = ([xml](Get-Content -LiteralPath $file.FullName -Raw)).testsuite
    foreach ($field in @('tests', 'failures', 'errors', 'skipped')) {
        $totals[$field] += [int]$suite.$field
    }
    foreach ($case in $suite.testcase) {
        if ($null -ne $case.skipped) { $skippedCases += "$($case.classname).$($case.name)" }
    }
}
$totals['skippedCases'] = $skippedCases
$totals['nativeOptInsRequired'] = [bool]$RequireNative
$totals | ConvertTo-Json | Set-Content -LiteralPath $Summary -Encoding utf8
$totals | ConvertTo-Json
if ($totals.tests -le 0 -or $totals.failures -ne 0 -or $totals.errors -ne 0) {
    throw 'Surefire reports contain failures/errors or no tests'
}

$expectedSkips = @(
    'me.youngjibbit.utils.render.WorldOverlayContractTest.nativeDispatchWhenRequested',
    'net.minecraft.client.gui.GuiChatInputTest.nativeInputAndDrawingWhenRequested',
    'net.minecraft.client.gui.GuiOverlayDebugGlStringsTest.realOffscreenContextRecreationRefreshesDriverStrings'
)
if ($RequireNative) {
    if ($totals.skipped -ne 0) { throw 'Native acceptance requires zero skipped tests' }
} else {
    $difference = @(Compare-Object ($expectedSkips | Sort-Object) ($skippedCases | Sort-Object))
    if ($difference.Count -ne 0 -or $totals.skipped -ne $expectedSkips.Count) {
        throw 'Unexpected skips or missing native opt-in cases; hosted regression permits only the three named existing opt-ins'
    }
}
