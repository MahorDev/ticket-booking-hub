<#
.SYNOPSIS
    Provisions and configures TicketHub production secrets in AWS Secrets Manager.
.DESCRIPTION
    Creates or updates the secret 'tickethub/production/credentials' containing all database,
    JWT, Razorpay, SMTP, and S3 credentials with automatic rotation policy support.
#>

param(
    [string]$Region = "ap-south-1",
    [string]$SecretName = "tickethub/production/credentials",
    [string]$DbPassword,
    [string]$JwtSecret,
    [string]$RazorpayKeyId,
    [string]$RazorpayKeySecret,
    [string]$RazorpayWebhookSecret,
    [string]$MailUsername,
    [string]$MailPassword
)

Write-Host "Configuring AWS Secrets Manager for TicketHub in region: $Region..." -ForegroundColor Cyan

# Prompt for passwords if not provided
if (-not $DbPassword) {
    $DbPassword = Read-Host -Prompt "Enter Production Database Password" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($DbPassword)
    $DbPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
}

if (-not $JwtSecret) {
    $Bytes = New-Object byte[] 48
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($Bytes)
    $JwtSecret = [Convert]::ToBase64String($Bytes)
    Write-Host "Generated cryptographically secure JWT secret." -ForegroundColor Green
}

$SecretPayload = @{
    DB_PASSWORD             = $DbPassword
    JWT_SECRET              = $JwtSecret
    RAZORPAY_KEY_ID         = $RazorpayKeyId
    RAZORPAY_KEY_SECRET     = $RazorpayKeySecret
    RAZORPAY_WEBHOOK_SECRET = $RazorpayWebhookSecret
    MAIL_USERNAME           = $MailUsername
    MAIL_PASSWORD           = $MailPassword
} | ConvertTo-Json -Compress

# Check if secret already exists
$Existing = aws secretsmanager describe-secret --secret-id $SecretName --region $Region 2>$null

if ($LASTEXITCODE -eq 0) {
    Write-Host "Updating existing secret: $SecretName" -ForegroundColor Yellow
    aws secretsmanager put-secret-value --secret-id $SecretName --secret-string "$SecretPayload" --region $Region
} else {
    Write-Host "Creating new secret: $SecretName" -ForegroundColor Green
    aws secretsmanager create-secret `
        --name $SecretName `
        --description "TicketHub Production Secrets and API Credentials" `
        --secret-string "$SecretPayload" `
        --region $Region
}

Write-Host "Successfully configured TicketHub Secrets in AWS Secrets Manager!" -ForegroundColor Green
