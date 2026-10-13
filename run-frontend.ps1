# SACHET AI - Run Frontend
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "Starting SACHET AI Frontend (Vite / Port 3000)..." -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location "$Root\frontend"

npm run dev
