#!/bin/bash
set -euo pipefail

SERVICE_NAME="${1:?Usage: $0 <service-name> (e.g. service-product)}"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AGENT_DIR="${PROJECT_ROOT}/pinpoint-agent"
AGENT_ID="${SERVICE_NAME}-$(hostname | cut -c1-8)"

if [ ! -d "$AGENT_DIR" ]; then
  echo "Error: Pinpoint agent not found at $AGENT_DIR"
  echo "Run ./scripts/setup-pinpoint-agent.sh first."
  exit 1
fi

if [ ! -f "$AGENT_DIR/pinpoint-bootstrap.jar" ]; then
  echo "Error: pinpoint-bootstrap.jar not found in $AGENT_DIR"
  exit 1
fi

cd "${PROJECT_ROOT}/backend-v2"

JAVA_OPTS="-javaagent:${AGENT_DIR}/pinpoint-bootstrap.jar"
JAVA_OPTS="${JAVA_OPTS} -Dpinpoint.agentId=${AGENT_ID}"
JAVA_OPTS="${JAVA_OPTS} -Dpinpoint.applicationName=${SERVICE_NAME}"
JAVA_OPTS="${JAVA_OPTS} -Dpinpoint.profiler.profiles.active=local"
JAVA_OPTS="${JAVA_OPTS} -Dpinpoint.config=${AGENT_DIR}/pinpoint-root.config"
JAVA_OPTS="${JAVA_OPTS} -Dprofiler.transport.grpc.collector.ip=localhost"

export JAVA_OPTS
echo "Starting ${SERVICE_NAME} with Pinpoint agent (agentId=${AGENT_ID})..."
./gradlew ":${SERVICE_NAME}:bootRun" --args="--spring.profiles.active=local"
