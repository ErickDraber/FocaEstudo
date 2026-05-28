@echo off
title Rastreador de Estudos - Executor Completo

rem Garante que os comandos rodem a partir da pasta do script
cd /d "%~dp0"

echo -------------------------------------
echo 1. Limpando compilacoes antigas...
echo -------------------------------------
del /Q bin\*.*
echo Limpeza concluida!
echo.

echo -------------------------------------
echo 2. Compilando o projeto...
echo -------------------------------------
echo.

rem Comando de compilação
javac -d bin src/StudyTracker.java src/StudyData.java

rem Verifica se houve erro na compilação
if %errorlevel% neq 0 (
    echo.
    echo ===================================================
    echo !! ERRO NA COMPILACAO! O programa nao vai abrir. !!
    echo ===================================================
    echo.
    pause
    exit
)

echo.
echo Compilacao bem-sucedida!
echo -------------------------------------
echo 3. Executando o programa...
echo -------------------------------------
echo.

rem Comando de execução 
start "Rastreador de Estudos" javaw -cp bin StudyTracker
