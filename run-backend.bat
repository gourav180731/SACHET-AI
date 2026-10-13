@echo off
title SACHET AI - Backend Server (Port 8000)
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-backend.ps1"
pause
