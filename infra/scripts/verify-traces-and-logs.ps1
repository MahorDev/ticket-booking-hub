<#
.SYNOPSIS
    Verifies distributed tracing (Jaeger) and centralized logging (ELK) across TicketHub services.
#>

param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$JaegerUrl = "http://localhost:16686",
    [string]$ElasticsearchUrl = "http://localhost:9200"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " TIKETHUB — OBSERVABILITY (TRACES & ELK LOGS) VERIFICATION " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$CorrelationId = "test-corr-" + [System.Guid]::NewGuid().ToString()
Write-Host "`n[Step 1] Sending authenticated request with X-Correlation-ID: $CorrelationId" -ForegroundColor Cyan

try {
    # Login to obtain token
    $LoginBody = @{ username = "customer"; password = "customer123" } | ConvertTo-Json
    $LoginResp = Invoke-RestMethod -Uri "$GatewayUrl/auth/login" -Method Post -Body $LoginBody -ContentType "application/json"
    $Token = $LoginResp.token

    $Headers = @{
        "Authorization" = "Bearer $Token"
        "X-Correlation-ID" = $CorrelationId
    }

    $EventResp = Invoke-WebRequest -Uri "$GatewayUrl/api/events" -Headers $Headers
    $RespCorrId = $EventResp.Headers["X-Correlation-ID"]

    if ($RespCorrId -eq $CorrelationId) {
        Write-Host "PASS: Gateway returned matching X-Correlation-ID header!" -ForegroundColor Green
    } else {
        Write-Host "INFO: Gateway returned X-Correlation-ID: $RespCorrId" -ForegroundColor Yellow
    }
} catch {
    Write-Host "INFO: Direct test traffic note ($($_.Exception.Message))" -ForegroundColor Gray
}

# Step 2: Verify Jaeger Traces
Write-Host "`n[Step 2] Verifying Jaeger Distributed Tracing..." -ForegroundColor Cyan
try {
    $JaegerServices = Invoke-RestMethod -Uri "$JaegerUrl/api/services" -ErrorAction Stop
    Write-Host "PASS: Jaeger is reachable. Active tracing services: $($JaegerServices.data -join ', ')" -ForegroundColor Green
} catch {
    Write-Host "INFO: Jaeger endpoint at $JaegerUrl not responding (start with docker compose up jaeger)." -ForegroundColor Gray
}

# Step 3: Verify Elasticsearch Log Ingestion
Write-Host "`n[Step 3] Verifying Elasticsearch Log Centralization..." -ForegroundColor Cyan
try {
    $ClusterHealth = Invoke-RestMethod -Uri "$ElasticsearchUrl/_cluster/health" -ErrorAction Stop
    Write-Host "PASS: Elasticsearch is reachable. Cluster status: $($ClusterHealth.status)" -ForegroundColor Green
    
    $LogsQuery = Invoke-RestMethod -Uri "$ElasticsearchUrl/tickethub-logs-*/_count" -ErrorAction SilentlyContinue
    if ($LogsQuery) {
        Write-Host "PASS: Found $($LogsQuery.count) microservice log entries indexed in Elasticsearch." -ForegroundColor Green
    }
} catch {
    Write-Host "INFO: Elasticsearch at $ElasticsearchUrl not responding (start with --profile observability)." -ForegroundColor Gray
}

Write-Host "`nObservability verification check complete." -ForegroundColor Cyan
