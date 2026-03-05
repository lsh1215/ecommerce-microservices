#!/bin/bash
# Start infrastructure services
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "Starting infrastructure services..."
docker compose -f "$PROJECT_ROOT/infra/docker-compose.yml" up -d

echo "Waiting for MySQL to be ready..."
until docker compose -f "$PROJECT_ROOT/infra/docker-compose.yml" exec mysql mysqladmin ping -h localhost --silent 2>/dev/null; do
  sleep 2
done
echo "MySQL is ready."
