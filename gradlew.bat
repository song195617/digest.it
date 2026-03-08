@echo off
setlocal

if "%GRADLE_BIN%"=="" set "GRADLE_BIN=%USERPROFILE%\.local\gradle-8.13\bin\gradle.bat"

if not exist "%GRADLE_BIN%" (
  echo ERROR: Gradle not found at "%GRADLE_BIN%"
  echo Run: powershell -ExecutionPolicy Bypass -File .\setup-android-build.ps1
  exit /b 1
)

call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%
