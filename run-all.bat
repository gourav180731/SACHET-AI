@echo off
title SACHET AI - Unified Launcher
cd /d "%~dp0"

echo ============================================================
echo Starting SACHET AI Application Services...
echo ============================================================
echo [1/2] Launching Backend on http://localhost:8000 ...
start "SACHET AI Backend (Port 8000)" cmd /c "run-backend.bat"

echo Waiting 5 seconds for backend initialization...
timeout /t 5 /nobreak >nul

echo [2/2] Launching Frontend on http://localhost:3000 ...
start "SACHET AI Frontend (Port 3000)" cmd /c "run-frontend.bat"

echo.
echo ============================================================
echo SACHET AI services launched!
echo - Frontend UI: http://localhost:3000
echo - Backend API: http://localhost:8000/api/v1
echo - API Health:  http://localhost:8000/api/v1/health
echo ============================================================
echo.
timeout /t 8 >nul
