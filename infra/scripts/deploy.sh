#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
GATEWAY_HEALTH_URL="${GATEWAY_HEALTH_URL:-http://localhost:8080/actuator/health}"
MAX_WAIT_SECONDS=90

echo "=========================================================="
echo " TIKETHUB — DEPLOYMENT RUNNER                             "
echo "=========================================================="

echo -e "\n[1/4] Validating Docker Compose configuration..."
docker compose -f "$COMPOSE_FILE" config --quiet
echo "PASS: Configuration valid."

echo -e "\n[2/4] Building latest service images..."
docker compose -f "$COMPOSE_FILE" build
echo "PASS: Build completed."

echo -e "\n[3/4] Deploying updated services..."
docker compose -f "$COMPOSE_FILE" up -d --remove-orphans

echo -e "\n[4/4] Verifying health check on Gateway ($MAX_WAIT_SECONDS s max)..."
START_TIME=$(date +%s)
HEALTHY=false

while [ $(($(date +%s) - START_TIME)) -lt $MAX_WAIT_SECONDS ]; do
  if curl -s -f "$GATEWAY_HEALTH_URL" >/dev/null 2>&1; then
    HEALTHY=true
    break
  fi
  echo -n "."
  sleep 3
done
echo ""

if [ "$HEALTHY" = true ]; then
  echo "=========================================================="
  echo " DEPLOYMENT SUCCESSFUL! Services UP and healthy.          "
  echo "=========================================================="
else
  echo "WARNING: Healthcheck timeout reached. Check 'docker compose logs'."
fi
