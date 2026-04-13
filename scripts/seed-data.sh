#!/bin/bash
# Load seed data into MySQL.
# Prerequisites:
#   1. MySQL running in Docker (docker compose up mysql)
#   2. All 4 services started at least once so JPA ddl-auto=update creates tables

MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3307}"
MYSQL_USER="${MYSQL_USER:-sa}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-1234}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Loading seed data into MySQL at ${MYSQL_HOST}:${MYSQL_PORT}..."
mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" < "$SCRIPT_DIR/seed-data.sql"

if [ $? -eq 0 ]; then
    echo "Seed data loaded successfully."
else
    echo "Failed to load seed data. Make sure MySQL is running and all services have been started at least once." >&2
    exit 1
fi
