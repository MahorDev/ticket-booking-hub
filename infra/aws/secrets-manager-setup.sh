#!/usr/bin/env bash
set -euo pipefail

REGION="${AWS_REGION:-ap-south-1}"
SECRET_NAME="${SECRET_NAME:-tickethub/production/credentials}"

echo "Configuring AWS Secrets Manager for TicketHub in region: ${REGION}..."

# Generate 48-byte cryptographically random JWT secret if not provided
JWT_SECRET="${JWT_SECRET:-$(openssl rand -base64 48)}"
DB_PASSWORD="${DB_PASSWORD:-$(openssl rand -base64 24)}"

SECRET_PAYLOAD=$(jq -n \
  --arg db "$DB_PASSWORD" \
  --arg jwt "$JWT_SECRET" \
  --arg rk "${RAZORPAY_KEY_ID:-}" \
  --arg rs "${RAZORPAY_KEY_SECRET:-}" \
  --arg rw "${RAZORPAY_WEBHOOK_SECRET:-}" \
  --arg mu "${MAIL_USERNAME:-}" \
  --arg mp "${MAIL_PASSWORD:-}" \
  '{
    DB_PASSWORD: $db,
    JWT_SECRET: $jwt,
    RAZORPAY_KEY_ID: $rk,
    RAZORPAY_KEY_SECRET: $rs,
    RAZORPAY_WEBHOOK_SECRET: $rw,
    MAIL_USERNAME: $mu,
    MAIL_PASSWORD: $mp
  }')

if aws secretsmanager describe-secret --secret-id "$SECRET_NAME" --region "$REGION" >/dev/null 2>&1; then
  echo "Updating existing secret: $SECRET_NAME"
  aws secretsmanager put-secret-value \
    --secret-id "$SECRET_NAME" \
    --secret-string "$SECRET_PAYLOAD" \
    --region "$REGION"
else
  echo "Creating new secret: $SECRET_NAME"
  aws secretsmanager create-secret \
    --name "$SECRET_NAME" \
    --description "TicketHub Production Secrets and API Credentials" \
    --secret-string "$SECRET_PAYLOAD" \
    --region "$REGION"
fi

echo "Successfully configured TicketHub Secrets in AWS Secrets Manager!"
