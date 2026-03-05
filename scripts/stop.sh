#!/bin/bash
# Stop infrastructure services
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "Stopping infrastructure services..."
docker compose -f "$PROJECT_ROOT/infra/docker-compose.yml" down
echo "Done."
