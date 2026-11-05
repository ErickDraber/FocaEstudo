@echo off
rem Testes de fumaca: abrem o app DE VERDADE (janelas aparecem e fecham sozinhas) numa
rem pasta de dados temporaria. Nunca tocam nos seus dados em %APPDATA%\FocaEstudo.
cd /d "%~dp0.."
set OUT=%TEMP%\focaestudo-fumaca-bin
if exist "%OUT%" rmdir /S /Q "%OUT%"
mkdir "%OUT%"

javac -encoding UTF-8 --release 17 -Xlint:none -d "%OUT%" src\*.java test\fumaca\*.java
if %errorlevel% neq 0 (
    echo.
    echo !! ERRO NA COMPILACAO DOS TESTES DE FUMACA !!
    exit /b 1
)

set FALHOU=0
java -Dfile.encoding=UTF-8 -cp "%OUT%" FumacaTimers || set FALHOU=1
java -Dfile.encoding=UTF-8 -cp "%OUT%" FumacaAreas  || set FALHOU=1
exit /b %FALHOU%
