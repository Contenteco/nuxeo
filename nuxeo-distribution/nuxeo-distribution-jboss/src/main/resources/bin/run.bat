@echo off
rem #####
rem #
rem # Nuxeo windows startup script
rem # Inspired/copied from RedHat JBoss's run.bat
rem #
rem #####
@if "%OS%" == "Windows_NT" setlocal

set DIRNAME=.\
if "%OS%" == "Windows_NT" set DIRNAME=%~dp0%

set PROGNAME=run.bat
if "%OS%" == "Windows_NT" set PROGNAME=%~nx0%

pushd %DIRNAME%..
set NUXEO_HOME=%CD%
popd

set NXCTL=%NUXEO_HOME%\bin\nuxeoctl.exe
if exist "%NXCTL%" goto FOUND_NXCTL
echo Could not locate %NXCTL%. Please check that you are in the
echo bin directory when running this script.
goto END

:FOUND_NXCTL

"%NXCTL%" %*

:END