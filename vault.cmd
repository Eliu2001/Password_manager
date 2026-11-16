@echo off
setlocal

set "JAR_PATH=%~dp0password-vault-1.1.0.jar"

if not exist "%JAR_PATH%" goto jar_error

where.exe java >nul 2>nul
if errorlevel 1 goto java_error

java -jar "%JAR_PATH%" start
set "VAULT_EXIT_CODE=%ERRORLEVEL%"
goto finish

:jar_error
echo Error: no se encuentra password-vault-1.1.0.jar junto a vault.cmd.
echo Coloca ambos archivos en la misma carpeta.
set "VAULT_EXIT_CODE=1"
goto finish

:java_error
echo Error: Java no esta disponible en PATH. Instala Java 21 o posterior.
set "VAULT_EXIT_CODE=1"

:finish
echo.
pause
exit /b %VAULT_EXIT_CODE%
