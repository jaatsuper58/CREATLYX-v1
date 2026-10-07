@rem ChattlyX Gradle bootstrap launcher (Windows). See gradlew for details.
@if "%DEBUG%"=="" @echo off
setlocal

set GRADLE_VERSION=9.7.0
set DIST_DIR=%USERPROFILE%\.gradle\bootstrap\gradle-%GRADLE_VERSION%

if exist "%DIST_DIR%\bin\gradle.bat" goto run

echo Downloading Gradle %GRADLE_VERSION% (one-time bootstrap)...
set ZIP=%TEMP%\gradle-%GRADLE_VERSION%.zip
powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP%'" || goto fail
powershell -NoProfile -Command "Expand-Archive -LiteralPath '%ZIP%' -DestinationPath '%USERPROFILE%\.gradle\bootstrap' -Force" || goto fail
del "%ZIP%"

:run
call "%DIST_DIR%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%

:fail
exit /b 1
