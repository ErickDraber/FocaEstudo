@echo off
title FocaEstudo v2

cd /d "%~dp0"

echo Compilando FocaEstudo v2...
del /Q bin\*.* 2>nul

javac -d bin src/StudyData.java src/StudySession.java src/AppTheme.java src/RoundedPanel.java src/StyledButton.java src/CustomTabs.java src/PieChartPanel.java src/HistoryPanel.java src/StudyTracker.java

if %errorlevel% neq 0 (
    echo.
    echo !! ERRO NA COMPILACAO !!
    pause
    exit
)

echo Iniciando...
start "FocaEstudo" javaw -cp bin StudyTracker
