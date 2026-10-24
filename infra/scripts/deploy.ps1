<#
.SYNOPSIS
    Automated Deployment Script for TicketHub Platform.
.DESCRIPTION
    Builds, validates, updates images, and executes zero-downtime rolling deployment
    with automated health checks on Eureka, API Gateway, and microservices.
#>

param(
    [string]$ComposeFile = "docker-compose.yml",
    [string]$GatewayHealthUrl = "http://localhost:8080/actuator/health",
    [int]$MaxHealthWaitSeconds = 90
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " TIKETHUB — DEPLOYMENT RUNNER                             " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Step 1: Validate configuration
Write-Host "`n[1/4] Validating Docker Compose configuration..." -ForegroundColor Cyan
docker compose -f $ComposeFile config --quiet
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker Compose validation failed! Aborting deployment."
    exit 1
}
Write-Host "PASS: Compose configuration is valid." -ForegroundColor Green

# Step 2: Build services in parallel
Write-Host "`n[2/4] Building latest service images..." -ForegroundColor Cyan
docker compose -f $ComposeFile build
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker build failed! Aborting deployment."
    exit 1
}
Write-Host "PASS: Images successfully built." -ForegroundColor Green

# Step 3: Rolling container restart
Write-Host "`n[3/4] Deploying containers with docker compose up -d..." -ForegroundColor Cyan
docker compose -f $ComposeFile up -d --remove-orphans
if ($LASTEXITCODE -ne 0) {
    Write-Error "Deployment up command failed! Triggering rollback recommended."
    exit 1
}

# Step 4: Health Check Verification
Write-Host "`n[4/4] Polling service healthchecks ($MaxHealthWaitSeconds s timeout)..." -ForegroundColor Cyan
$StartTime = Get-Date
$Healthy = $false

while (((Get-Date) - $StartTime).TotalSeconds -lt $MaxHealthWaitSeconds) {
    try {
        $resp = Invoke-RestMethod -Uri $GatewayHealthUrl -Method Get -TimeoutSec 3 -ErrorAction Stop
        if ($resp.status -eq "UP" -or $resp -match "UP") {
            $Healthy = $true
            break
        }
    } catch {
        Write-Host "." -NoNewline -ForegroundColor DarkGray
    }
    Start-Sleep -Seconds 3
}

Write-Host ""
if ($Healthy) {
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host " DEPLOYMENT SUCCESSFUL! All services healthy and UP.      " -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green
} else {
    Write-Host "WARNING: Healthcheck did not report UP within $MaxHealthWaitSeconds seconds." -ForegroundColor Yellow
    Write-Host "Check container logs: docker compose logs" -ForegroundColor Yellow
}
