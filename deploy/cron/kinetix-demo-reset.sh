#!/bin/sh
# Nightly demo data reset for kinetixrisk.ai
#
# Calls the gateway's demo reset endpoint which fans out to
# position-service, audit-service, and risk-orchestrator.
# Each service truncates visitor data and reseeds from scratch.
#
# Install as a system cron:
#   0 2 * * * /path/to/kinetix-demo-reset.sh >> /var/log/kinetix-demo-reset.log 2>&1
#
# Required environment variables:
#   DEMO_ADMIN_KEY  - matches the gateway's DEMO_ADMIN_KEY (default in compose: kinetix-demo-admin-dev)
#   GATEWAY_URL     - defaults to http://localhost:8080

set -e

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"

if [ -z "$DEMO_ADMIN_KEY" ]; then
    echo "$(date -u '+%Y-%m-%dT%H:%M:%SZ') ERROR: DEMO_ADMIN_KEY not set"
    exit 1
fi

echo "$(date -u '+%Y-%m-%dT%H:%M:%SZ') Starting demo reset..."

RESPONSE=$(curl -sf -X POST "${GATEWAY_URL}/api/v1/admin/demo-reset" \
    -H "X-Demo-Admin-Key: ${DEMO_ADMIN_KEY}" \
    -H "Content-Type: application/json" \
    -w "\nHTTP_STATUS:%{http_code}")

HTTP_STATUS=$(echo "$RESPONSE" | grep "HTTP_STATUS:" | sed 's/HTTP_STATUS://')
BODY=$(echo "$RESPONSE" | grep -v "HTTP_STATUS:")

if [ "$HTTP_STATUS" = "200" ]; then
    echo "$(date -u '+%Y-%m-%dT%H:%M:%SZ') Reset complete: $BODY"
else
    echo "$(date -u '+%Y-%m-%dT%H:%M:%SZ') Reset failed (HTTP $HTTP_STATUS): $BODY"
    exit 1
fi
