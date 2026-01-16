@echo off
REM ================================================
REM Svetoofor - Self-Contained Installer Builder
REM Using jpackage with Liberica JDK
REM ================================================

echo ================================================
echo Svetoofor Self-Contained Installer Builder
echo Using jpackage with Liberica JDK
echo ================================================
echo.

REM Check if jpackage exists (requires JDK 17+)
where jpackage >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: jpackage not found!
    echo.
    echo Please install Liberica JDK 17+ Full version:
    echo https://bell-sw.com/pages/downloads/#jdk-17-lts
    echo.
    echo Make sure to download "Full JDK" (not Standard)
    echo and add JAVA_HOME/bin to PATH.
    echo.
    pause
    exit /b 1
)

echo [OK] Found jpackage
java -version
echo.

REM Check if client JAR exists
if not exist "..\target\svetoofor-client.jar" (
    echo ERROR: svetoofor-client.jar not found!
    echo Please build the project first: mvn clean package
    echo.
    pause
    exit /b 1
)

echo [OK] Found svetoofor-client.jar
echo.

REM Create output directory
if not exist "jpackage-output" mkdir jpackage-output

REM Copy required files to input directory
if not exist "jpackage-input" mkdir jpackage-input
copy /Y "..\target\svetoofor-client.jar" "jpackage-input\"
copy /Y "..\client.properties" "jpackage-input\"
if exist "..\src\main\resources\44_85245.ico" copy /Y "..\src\main\resources\44_85245.ico" "jpackage-input\icon.ico"

echo ================================================
echo Building self-contained Windows installer...
echo This may take 2-5 minutes...
echo ================================================
echo.

REM Build the installer
jpackage ^
  --input jpackage-input ^
  --name "Svetoofor-JIRA-Client" ^
  --main-jar svetoofor-client.jar ^
  --main-class incuat.kg.svetoofor.TrafficLightApp ^
  --type exe ^
  --dest jpackage-output ^
  --app-version 1.0.0 ^
  --vendor "ITSMJIRA" ^
  --description "Traffic Light JIRA Client" ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut ^
  --win-menu-group "Svetoofor JIRA" ^
  --icon jpackage-input\icon.ico ^
  --java-options "-Dfile.encoding=UTF-8"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ================================================
    echo SUCCESS! Self-contained installer created:
    echo.
    dir jpackage-output\*.exe
    echo.
    echo ================================================
    echo.
    echo The installer includes:
    echo   - Svetoofor Client application
    echo   - Embedded Liberica JDK runtime
    echo   - All required dependencies
    echo.
    echo Users DO NOT need to install Java separately!
    echo.
    echo Installer location:
    echo   %CD%\jpackage-output\
    echo.
) else (
    echo.
    echo ERROR: Failed to create installer!
    echo Check the error messages above.
    echo.
)

pause
