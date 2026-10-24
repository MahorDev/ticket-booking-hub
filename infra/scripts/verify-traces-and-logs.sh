#!/usr/bin/env bash
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
JAEGER_URL="${JAEGER_URL:-http://localhost:16686}"
ES_URL="${ES_URL:-http://localhost:9200}"

echo "=========================================================="
echo " TIKETHUB — OBSERVABILITY (TRACES & ELK LOGS) VERIFICATION "
echo "=========================================================="

CORRELATION_ID="test-corr-$(uuidgen 2>/dev/null || date +%s)"
echo -e "\n[Step 1] Sending authenticated request with X-Correlation-ID: ${CORRELATION_ID}..."

TOKEN=$(curl -s -X POST "${GATEWAY_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"customer","password":"customer123"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4 || echo "")

if [ -n "$TOKEN" ]; then
  curl -s -i -X GET "${GATEWAY_URL}/api/events" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "X-Correlation-ID: ${CORRELATION_ID}" | grep -i "X-Correlation-ID" || true
fi

echo -e "\n[Step 2] Checking Jaeger Tracing Service..."
curl -s "${JAEGER_URL}/api/services" || echo "Jaeger not reachable at ${JAEGER_URL}"

echo -e "\n[Step 3] Checking Elasticsearch Log Ingestion..."
curl -s "${ES_URL}/_cluster/health" || echo "Elasticsearch not reachable at ${ES_URL}"

echo -e "\nObservability verification check complete."
