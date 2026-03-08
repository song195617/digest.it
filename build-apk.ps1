param(
    [switch]$Release
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$BuildType = if ($Release) { 'release' } else { 'debug' }
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AndroidHome = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$StudioJavaHome = 'C:\Program Files\Android\Android Studio\jbr'
$JavaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } elseif (Test-Path (Join-Path $StudioJavaHome 'bin\java.exe')) { $StudioJavaHome } else { $null }
$GradleBin = if ($env:GRADLE_BIN) { $env:GRADLE_BIN } else { Join-Path $HOME '.local\gradle-8.13\bin\gradle.bat' }
$ManifestPath = Join-Path $ScriptDir 'app\src\main\AndroidManifest.xml'
$KotlinFiles = Get-ChildItem (Join-Path $ScriptDir 'app\src\main\java') -Recurse -Filter *.kt -File

function Write-Info($Message) { Write-Host "[build] $Message" -ForegroundColor Green }
function Write-Warn($Message) { Write-Host "[warn]  $Message" -ForegroundColor Yellow }
function Fail($Message) { throw $Message }

if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) {
    Fail 'JAVA_HOME is not ready. Run .\setup-android-build.ps1 first.'
}
if (-not (Test-Path $AndroidHome)) {
    Fail "ANDROID_HOME not found at $AndroidHome"
}
if (-not (Test-Path $GradleBin)) {
    Fail "Gradle not found at $GradleBin. Run .\setup-android-build.ps1 first."
}

$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidHome
$env:ANDROID_SDK_ROOT = $AndroidHome
$env:Path = "$JavaHome\bin;$AndroidHome\platform-tools;$env:Path"

Write-Host ''
Write-Host '── Security Checks ──────────────────────────────────────────' -ForegroundColor Cyan

$secretHits = $KotlinFiles | Select-String -Pattern '(apiKey|api_key|API_KEY|secret|token|bearer|password)\s*=\s*"[A-Za-z0-9+/=_\-]{8,}"' -AllMatches -ErrorAction SilentlyContinue
if ($secretHits) {
    Fail ("Possible hardcoded secrets found:`n" + (($secretHits | ForEach-Object { "  $($_.Path):$($_.LineNumber): $($_.Line.Trim())" }) -join "`n"))
}
Write-Info 'No hardcoded secrets found'

$ipHits = $KotlinFiles | Select-String -Pattern '"https?://([0-9]{1,3}\.){3}[0-9]{1,3}' -AllMatches -ErrorAction SilentlyContinue |
    Where-Object { $_.Line -notmatch 'localhost|127\.0\.0\.1|10\.0\.2\.2' }
if ($ipHits) {
    Write-Warn 'Hardcoded non-local IPs found. Verify before release:'
    $ipHits | ForEach-Object { Write-Host "  $($_.Path):$($_.LineNumber): $($_.Line.Trim())" }
}
else {
    Write-Info 'No hardcoded non-local IPs found'
}

$manifest = Get-Content $ManifestPath -Raw
if ($Release -and $manifest -match 'usesCleartextTraffic="true"') {
    Fail 'Release build must not enable cleartext traffic.'
}
Write-Info 'Manifest cleartext setting looks acceptable'

if ($Release) {
    $bodyLogging = $KotlinFiles | Select-String -Pattern 'Level\.BODY' -ErrorAction SilentlyContinue
    if ($bodyLogging) {
        Write-Warn 'BODY logging references still exist. Verify they are guarded by BuildConfig.DEBUG.'
    }
}

Write-Host ''
Write-Host "── Building ($BuildType) ────────────────────────────────────" -ForegroundColor Cyan
$task = if ($Release) { ':app:assembleRelease' } else { ':app:assembleDebug' }
& $GradleBin $task --stacktrace --no-daemon
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$apkDir = Join-Path $ScriptDir "app\build\outputs\apk\$BuildType"
$apk = Get-ChildItem $apkDir -Filter *.apk -Recurse | Select-Object -First 1
if (-not $apk) {
    Fail "Build succeeded but no APK found under $apkDir"
}

Write-Host ''
Write-Host 'Build successful.' -ForegroundColor Green
Write-Host "APK:  $($apk.FullName)"
Write-Host ("Size: {0:N2} MB" -f ($apk.Length / 1MB))
Write-Host ''
Write-Host "Install with: `"$AndroidHome\platform-tools\adb.exe`" install -r `"$($apk.FullName)`""
