@echo off
setlocal

set "GRADLE_VERSION=8.8"
set "APP_HOME=%~dp0"
set "GRADLE_DIR=%APP_HOME%.gradle\wrapper\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%GRADLE_DIR%\bin\gradle.bat"
set "GRADLE_ZIP=%APP_HOME%.gradle\wrapper\gradle-%GRADLE_VERSION%-bin.zip"

if exist "%GRADLE_BIN%" goto runGradle

echo Gradle %GRADLE_VERSION% not found. Downloading into .gradle\wrapper...
if not exist "%APP_HOME%.gradle\wrapper" mkdir "%APP_HOME%.gradle\wrapper"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%GRADLE_ZIP%'"
if errorlevel 1 (
    echo Failed to download Gradle %GRADLE_VERSION%.
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "Expand-Archive -Path '%GRADLE_ZIP%' -DestinationPath '%APP_HOME%.gradle\wrapper' -Force"
if errorlevel 1 (
    echo Failed to extract Gradle %GRADLE_VERSION%.
    exit /b 1
)

del /q "%GRADLE_ZIP%" >nul 2>&1

:runGradle
call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%
