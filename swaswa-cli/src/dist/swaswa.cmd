@echo off
rem swaswa: new, run and build swaswa apps. Needs Java 25 (java on the PATH, or JAVA_HOME).
setlocal
set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java"
"%JAVA%" -jar "%~dp0..\lib\swaswa-cli.jar" %*
