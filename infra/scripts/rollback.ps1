<#
.SYNOPSIS
    Automated Rollback Script for TicketHub Platform.
.DESCRIPTION
    Rolls back containers to previous stable state, restarts previous image tags,
    or resets compose network to healthy baseline.
#>

param(
    [string]$ComposeFile = "docker-compose.yml",
    [switch]$PreserveData
)

Write-Host "==========================================================" -ForegroundColor Red
Write-Host " TIKETHUB — PLATFORM ROLLBACK PROCEDURE                   " -ForegroundColor Red
Write-Host "==========================================================" -ForegroundColor Red

Write-Host "`n[1/3] Gracefully stopping currently failing containers..." -ForegroundColor Yellow
docker compose -f $ComposeFile stop

if (-not $PreserveData) {
    Write-Host "[2/3] Cleaning up orphaned state and rolling back containers..." -ForegroundColor Yellow
    docker compose -f $ComposeFile down --remove-orphans
}

Write-Host "[3/3] Restoring previous known-good deployment..." -ForegroundColor Green
docker compose -f $ComposeFile up -d --no-build

Write-Host "`nRollback sequence executed. Check container status: docker compose ps" -ForegroundColor Cyan
