#!/bin/bash
# Load seed data into the MySQL Docker container.
# Waits for MySQL health check before loading.
# Prerequisites: All 4 services started at least once (JPA ddl-auto=update creates tables)

CONTAINER="${MYSQL_CONTAINER:-ecommerce-mysql}"
MYSQL_USER="${MYSQL_USER:-sa}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-1234}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Waiting for MySQL container '${CONTAINER}' to be healthy..."
until docker inspect --format='{{.State.Health.Status}}' "$CONTAINER" 2>/dev/null | grep -q "healthy"; do
    sleep 2
done
echo "MySQL is healthy."

echo "Loading seed data..."
docker exec -i "$CONTAINER" mysql -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" < "$SCRIPT_DIR/seed-data.sql"

if [ $? -eq 0 ]; then
    echo "Seed data loaded successfully."
else
    echo "Failed to load seed data. Make sure all 4 services have been started at least once so JPA creates the tables." >&2
    exit 1
fi
