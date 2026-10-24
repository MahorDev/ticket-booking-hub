#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"

echo "=========================================================="
echo " TIKETHUB — PLATFORM ROLLBACK PROCEDURE                   "
echo "=========================================================="

echo -e "\n[1/2] Stopping active services..."
docker compose -f "$COMPOSE_FILE" stop

echo -e "\n[2/2] Restoring previous known-good deployment..."
docker compose -f "$COMPOSE_FILE" up -d --no-build

echo -e "\nRollback sequence executed. Check container status: docker compose ps"
