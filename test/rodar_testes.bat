@echo off
rem Compila o app + testes numa pasta temporaria e roda os testes (sem framework).
cd /d "%~dp0.."
set OUT=%TEMP%\focaestudo-testes
if exist "%OUT%" rmdir /S /Q "%OUT%"
mkdir "%OUT%"

javac -encoding UTF-8 --release 17 -Xlint:none -d "%OUT%" src\*.java test\*.java
if %errorlevel% neq 0 (
    echo.
    echo !! ERRO NA COMPILACAO DOS TESTES !!
    exit /b 1
)

set FALHOU=0
java -Djava.awt.headless=true -cp "%OUT%" StudyStatsTest || set FALHOU=1
java -Djava.awt.headless=true -cp "%OUT%" StudyStoreTest || set FALHOU=1
java -Djava.awt.headless=true -cp "%OUT%" AppSettingsTest || set FALHOU=1
java -Djava.awt.headless=true -cp "%OUT%" TimerModelsTest || set FALHOU=1
exit /b %FALHOU%
