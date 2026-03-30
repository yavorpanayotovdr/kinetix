#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> Stopping all services..."
docker compose \
  -f "$ROOT_DIR/infra/docker-compose.infra.yml" \
  -f "$ROOT_DIR/infra/docker-compose.observability.yml" \
  -f "$ROOT_DIR/docker-compose.services.yml" \
  down

echo ""
echo "  Kinetix stack stopped."
echo "  To also remove volumes: add --volumes to the compose down command."
echo ""
