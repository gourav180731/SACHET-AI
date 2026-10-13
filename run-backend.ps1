# SACHET AI - Run Backend
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "Starting SACHET AI Backend (Spring Boot / Port 8000)..." -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

if (Test-Path "$Root\.env") {
    Write-Host "Loading .env configuration..." -ForegroundColor Gray
    Get-Content "$Root\.env" | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#')) {
            $parts = $line.Split('=', 2)
            if ($parts.Length -eq 2) {
                [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
            }
        }
    }
}

mvn spring-boot:run -f "$Root\backend\pom.xml"
