@echo off
setlocal

rem Set this to the absolute path of the vault file to open.
set "VAULT_PATH=REPLACE_WITH_ABSOLUTE_VAULT_PATH"

set "JAR_PATH=%~dp0password-vault-1.1.0.jar"

if "%VAULT_PATH%"=="" goto vault_path_error
if "%VAULT_PATH%"=="REPLACE_WITH_ABSOLUTE_VAULT_PATH" goto vault_path_error
if not exist "%JAR_PATH%" goto jar_error

where.exe java >nul 2>nul
if errorlevel 1 goto java_error

java -jar "%JAR_PATH%" session --vault "%VAULT_PATH%"
set "VAULT_EXIT_CODE=%ERRORLEVEL%"
goto finish

:vault_path_error
echo Error: edita VAULT_PATH en vault.cmd e indica la ruta absoluta de tu boveda.
set "VAULT_EXIT_CODE=1"
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
