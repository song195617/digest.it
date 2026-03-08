Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$GradleVersion = '8.13'
$GradleHome = Join-Path $HOME ".local\gradle-$GradleVersion"
$GradleBin = Join-Path $GradleHome 'bin\gradle.bat'
$AndroidHome = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$StudioJavaHome = 'C:\Program Files\Android\Android Studio\jbr'
$JavaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } elseif (Test-Path (Join-Path $StudioJavaHome 'bin\java.exe')) { $StudioJavaHome } else { $null }

function Write-Info($Message) { Write-Host "[setup] $Message" -ForegroundColor Green }
function Write-Warn($Message) { Write-Host "[warn]  $Message" -ForegroundColor Yellow }
function Fail($Message) { throw $Message }

Write-Info "Preparing local Android build environment for Windows"

if (-not $JavaHome) {
    Fail 'JAVA_HOME not found. Install Android Studio or JDK 17 first.'
}
if (-not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) {
    Fail "java.exe not found under $JavaHome"
}
if (-not (Test-Path $AndroidHome)) {
    Fail "Android SDK not found at $AndroidHome"
}

$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidHome
$env:Path = "$JavaHome\bin;$AndroidHome\platform-tools;$env:Path"

if (-not (Test-Path $GradleBin)) {
    Write-Info "Downloading Gradle $GradleVersion"
    New-Item -ItemType Directory -Force (Split-Path $GradleHome -Parent) | Out-Null
    $zipPath = Join-Path $env:TEMP "gradle-$GradleVersion-bin.zip"
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" -OutFile $zipPath
    $extractRoot = Split-Path $GradleHome -Parent
    Expand-Archive -Path $zipPath -DestinationPath $extractRoot -Force
    Remove-Item $zipPath -Force
}
else {
    Write-Info "Gradle $GradleVersion already present"
}

[Environment]::SetEnvironmentVariable('JAVA_HOME', $JavaHome, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $AndroidHome, 'User')

Write-Info "JAVA_HOME=$JavaHome"
Write-Info "ANDROID_HOME=$AndroidHome"
& $GradleBin --version

Write-Host ''
Write-Host 'Setup complete.' -ForegroundColor Green
Write-Host 'Build debug APK with:'
Write-Host '  .\build-apk.ps1'
Write-Host 'Build release APK with:'
Write-Host '  .\build-apk.ps1 -Release'
