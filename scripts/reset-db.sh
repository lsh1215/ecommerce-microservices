#!/bin/bash
# Reset database by removing volume and reinitializing
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "WARNING: This will delete all data in the database."
read -p "Continue? (y/N) " confirm
if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
  echo "Cancelled."
  exit 0
fi

echo "Stopping services and removing volumes..."
docker compose -f "$PROJECT_ROOT/infra/docker-compose.yml" down -v

echo "Starting fresh..."
"$SCRIPT_DIR/start.sh"
