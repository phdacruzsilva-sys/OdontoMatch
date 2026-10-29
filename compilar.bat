@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo Compilando OdontoMatch a partir do codigo-fonte...
if exist out rmdir /s /q out
if exist build rmdir /s /q build
mkdir out
mkdir build
javac --release 17 -encoding UTF-8 -cp lib\hsqldb.jar -d out src\odontomatch\*.java
if errorlevel 1 ( echo ERRO na compilacao. & pause & exit /b 1 )
xcopy /e /q /y out build\ >nul
cd build
jar xf ..\lib\hsqldb.jar
if exist META-INF rmdir /s /q META-INF
cd ..
echo Main-Class: odontomatch.App> manifest.txt
jar cfm OdontoMatch.jar manifest.txt -C build .
rmdir /s /q out
rmdir /s /q build
del manifest.txt
echo.
echo Pronto! OdontoMatch.jar atualizado. Rode iniciar.bat
pause
