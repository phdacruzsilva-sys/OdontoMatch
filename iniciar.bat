@echo off
chcp 65001 >nul
title OdontoMatch
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo.
  echo  [ERRO] Java nao encontrado. Instale o JDK 17 ou superior e configure o PATH.
  echo.
  pause
  exit /b 1
)
set PORTA=%1
if "%PORTA%"=="" set PORTA=8080
echo Iniciando OdontoMatch na porta %PORTA% ...
java -jar OdontoMatch.jar %PORTA%
pause
