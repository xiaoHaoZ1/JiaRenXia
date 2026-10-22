@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\jrx.ps1" %*
exit /b %ERRORLEVEL%
