param([switch]$RunAndroidTests, [string]$LocalSamples = '', [switch]$FullKotlinRebuild)
$ErrorActionPreference = 'Stop'
$panelgroveRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $panelgroveRoot
$panelgroveOldEnvironment = @{}
foreach ($panelgroveKey in @('PANELGROVE_KEYSTORE','PANELGROVE_STORE_PASSWORD','PANELGROVE_KEY_ALIAS','PANELGROVE_KEY_PASSWORD')) {
    $panelgroveOldEnvironment[$panelgroveKey] = [Environment]::GetEnvironmentVariable($panelgroveKey, 'Process')
}
try {
    New-Item -ItemType Directory -Force -Path 'local-validation','dist' | Out-Null
    $panelgroveSigningPath = Join-Path $panelgroveRoot 'local-validation\preview-signing.json'
    if (-not (Test-Path -LiteralPath $panelgroveSigningPath)) {
        $panelgrovePassword = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
        @{password=$panelgrovePassword; alias='panelgrove-preview'} | ConvertTo-Json | Set-Content -LiteralPath $panelgroveSigningPath
    }
    $panelgroveSigning = Get-Content -LiteralPath $panelgroveSigningPath -Raw | ConvertFrom-Json
    $env:PANELGROVE_KEYSTORE = Join-Path $panelgroveRoot 'local-validation\panelgrove-preview.p12'
    $env:PANELGROVE_STORE_PASSWORD = $panelgroveSigning.password
    $env:PANELGROVE_KEY_PASSWORD = $panelgroveSigning.password
    $env:PANELGROVE_KEY_ALIAS = $panelgroveSigning.alias
    if (-not (Test-Path -LiteralPath $env:PANELGROVE_KEYSTORE)) {
        & keytool -genkeypair -noprompt -storetype PKCS12 -keystore $env:PANELGROVE_KEYSTORE -alias $env:PANELGROVE_KEY_ALIAS `
            -storepass:env PANELGROVE_STORE_PASSWORD -keypass:env PANELGROVE_KEY_PASSWORD -keyalg RSA -keysize 3072 -validity 3650 -dname 'CN=Panelgrove Local Preview'
        if ($LASTEXITCODE -ne 0) { throw 'Preview key generation failed.' }
    }
    $panelgroveTasks = @(':core:test', ':app:testDebugUnitTest', ':app:assembleRelease', ':app:assembleDebug', ':app:assembleDebugAndroidTest', ':app:lintRelease')
    if ($RunAndroidTests) { $panelgroveTasks += ':app:connectedDebugAndroidTest' }
    $panelgroveArguments = @('--console=plain','--no-daemon','--continue')
    if ($FullKotlinRebuild) { $panelgroveArguments += '-Pkotlin.incremental=false' }
    if ($LocalSamples) { $panelgroveArguments += "-PpanelgroveSamples=$LocalSamples" }
    & .\gradlew.bat @panelgroveTasks @panelgroveArguments
    if ($LASTEXITCODE -ne 0) { throw 'Build or validation failed. No new preview was published.' }
    $panelgroveMetadata = Get-Content -LiteralPath 'app\build\outputs\apk\release\output-metadata.json' -Raw | ConvertFrom-Json
    $panelgroveArtifact = "Panelgrove-$($panelgroveMetadata.elements[0].versionName)-preview.apk"
    Copy-Item -LiteralPath 'app\build\outputs\apk\release\app-release.apk' -Destination (Join-Path 'dist' $panelgroveArtifact)
    Get-ChildItem -LiteralPath 'dist' -Filter '*.apk' | Sort-Object Name | ForEach-Object {
        $panelgroveHash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
        "$($panelgroveHash.Hash.ToLower())  $($_.Name)"
    } | Set-Content -LiteralPath 'dist\SHA256SUMS'
    Write-Output (Join-Path $panelgroveRoot "dist\$panelgroveArtifact")
} finally {
    foreach ($panelgroveKey in $panelgroveOldEnvironment.Keys) { [Environment]::SetEnvironmentVariable($panelgroveKey, $panelgroveOldEnvironment[$panelgroveKey], 'Process') }
    Pop-Location
}
