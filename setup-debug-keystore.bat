@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo PharmaOS - Android Debug Keystore Setup
echo ===================================================

set KEYSTORE_FILE=%~dp0debug.keystore
set BASE64_FILE=%~dp0debug.keystore.base64

if exist "%KEYSTORE_FILE%" (
    echo [INFO] debug.keystore already exists at:
    echo        "%KEYSTORE_FILE%"
    echo [INFO] No action required. You can build the project in Android Studio or via Gradle.
    exit /b 0
)

echo [INFO] debug.keystore not found. Preparing to generate/restore debug keystore...

:: Step 1: Attempt to decode from debug.keystore.base64 if present
if exist "%BASE64_FILE%" (
    echo [INFO] Found debug.keystore.base64. Decoding keystore using certutil...
    certutil -decode "%BASE64_FILE%" "%KEYSTORE_FILE%" >nul 2>&1
    if exist "%KEYSTORE_FILE%" (
        echo [SUCCESS] debug.keystore restored successfully from debug.keystore.base64!
        goto :verify
    ) else (
        echo [WARN] Failed to decode base64 file. Falling back to keytool generation...
    )
)

:: Step 2: Generate fresh debug.keystore using JDK keytool
echo [INFO] Generating new debug.keystore using keytool...
where keytool >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] 'keytool' command not found in PATH!
    echo         Please ensure Java JDK (version 11 or 17+) is installed and added to PATH,
    echo         or run this script inside Android Studio's Terminal or Developer Command Prompt.
    exit /b 1
)

keytool -genkeypair -v ^
  -keystore "%KEYSTORE_FILE%" ^
  -storepass android ^
  -alias androiddebugkey ^
  -keypass android ^
  -keyalg RSA ^
  -keysize 2048 ^
  -validity 10000 ^
  -dname "CN=Android Debug,O=Android,C=US"

if %errorlevel% equ 0 (
    echo [SUCCESS] debug.keystore generated successfully!
) else (
    echo [ERROR] Failed to generate debug.keystore.
    exit /b %errorlevel%
)

:verify
if exist "%KEYSTORE_FILE%" (
    echo.
    echo ===================================================
    echo Keystore: %KEYSTORE_FILE%
    echo Alias:    androiddebugkey
    echo Password: android
    echo Status:   Ready for Android Studio and Gradle builds
    echo ===================================================
    exit /b 0
) else (
    echo [ERROR] debug.keystore was not created.
    exit /b 1
)
