param([string]$Serial = 'emulator-5554', [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")
$ErrorActionPreference = 'Stop'
$panelgroveRoot = Split-Path -Parent $PSScriptRoot
foreach ($panelgroveApk in @('app\build\outputs\apk\debug\app-debug.apk', 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')) {
    & $Adb -s $Serial install -r (Join-Path $panelgroveRoot $panelgroveApk)
    if ($LASTEXITCODE -ne 0) { throw "Cannot install $panelgroveApk" }
}
function Invoke-PanelgrovePhase([string]$Phase, [string]$Method) {
    $panelgroveResult = & $Adb -s $Serial shell am instrument -w -r -e class "dev.soto.panelgrove.ProcessRestartTest#$Method" -e panelgroveProcessPhase $Phase dev.soto.panelgrove.test/androidx.test.runner.AndroidJUnitRunner
    $panelgroveResult | Set-Content -LiteralPath (Join-Path $panelgroveRoot "local-validation\process-$Phase.txt")
    $panelgroveResult | Write-Output
    if ($LASTEXITCODE -ne 0 -or ($panelgroveResult -join "`n") -notmatch 'OK \(1 test\)') { throw "Process $Phase failed" }
}
$panelgroveToken = [guid]::NewGuid().ToString()
$panelgrovePrepareOutput = Join-Path $panelgroveRoot 'local-validation\process-prepare.txt'
$panelgrovePrepareError = Join-Path $panelgroveRoot 'local-validation\process-prepare.stderr.txt'
$panelgrovePrepare = Start-Process -FilePath $Adb -ArgumentList @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'dev.soto.panelgrove.ProcessRestartTest#prepare', '-e', 'panelgroveProcessPhase', 'prepare', '-e', 'panelgroveProcessToken', $panelgroveToken, 'dev.soto.panelgrove.test/androidx.test.runner.AndroidJUnitRunner') -WindowStyle Hidden -PassThru -RedirectStandardOutput $panelgrovePrepareOutput -RedirectStandardError $panelgrovePrepareError
try {
    $panelgroveDeadline = [datetime]::UtcNow.AddSeconds(30)
    do {
        $panelgroveReady = & $Adb -s $Serial shell run-as dev.soto.panelgrove cat files/hardening-process-ready.txt 2>$null
        if (($panelgroveReady -join '').Trim() -eq $panelgroveToken) { break }
        if ($panelgrovePrepare.HasExited -or [datetime]::UtcNow -ge $panelgroveDeadline) { throw 'The reader did not reach its process-termination boundary' }
        Start-Sleep -Milliseconds 200
    } while ($true)
$panelgroveBeforePid = & $Adb -s $Serial shell pidof dev.soto.panelgrove
if (-not $panelgroveBeforePid) { throw 'No live app process remained to terminate; process-death evidence is incomplete' }
& $Adb -s $Serial shell am force-stop dev.soto.panelgrove
if ($LASTEXITCODE -ne 0) { throw 'Force-stop failed' }
$panelgroveAfterPid = & $Adb -s $Serial shell pidof dev.soto.panelgrove
if ($panelgroveAfterPid) { throw 'The old app process is still running' }
"Before force-stop PID: $panelgroveBeforePid; after force-stop: no process" | Set-Content -LiteralPath (Join-Path $panelgroveRoot 'local-validation\process-termination.txt')
$panelgrovePrepare.WaitForExit(10000) | Out-Null
} finally {
    if (-not $panelgrovePrepare.HasExited) { Stop-Process -Id $panelgrovePrepare.Id }
    $panelgrovePrepare.Dispose()
}
Invoke-PanelgrovePhase 'verify' 'verify'
