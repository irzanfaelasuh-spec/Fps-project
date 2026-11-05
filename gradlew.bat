@echo off
setlocal
set GRADLE_VERSION=8.9
set BASE_DIR=%~dp0
set CACHE_DIR=%USERPROFILE%\.gradle\fpsplus-bootstrap\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%CACHE_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat
if not exist "%GRADLE_BIN%" (
  if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%CACHE_DIR%\gradle.zip'; Expand-Archive -Path '%CACHE_DIR%\gradle.zip' -DestinationPath '%CACHE_DIR%' -Force; Remove-Item '%CACHE_DIR%\gradle.zip'"
)
call "%GRADLE_BIN%" -p "%BASE_DIR%" %*
endlocal
