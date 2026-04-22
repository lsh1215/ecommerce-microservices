#!/bin/sh
# Service launcher. Conditionally attaches the Pinpoint APM agent when a
# collector address is provided via env; otherwise starts Spring Boot as-is.
#
# Activation contract:
#   PINPOINT_COLLECTOR_IP        present  -> agent attached
#   PINPOINT_APPLICATION_NAME    required when agent attaches
#   PINPOINT_PROFILE             optional, default: release
#
# Leaving PINPOINT_COLLECTOR_IP unset is the supported path for local
# Docker runs, k3d clusters, and any environment where Pinpoint is not
# deployed — the service starts normally with zero tracing overhead.

set -e

JAVA_OPTS="${JAVA_OPTS:-}"

if [ -n "${PINPOINT_COLLECTOR_IP:-}" ]; then
  AGENT_DIR="/app/pinpoint-agent"
  JAVA_OPTS="${JAVA_OPTS} -javaagent:${AGENT_DIR}/pinpoint-bootstrap.jar"
  JAVA_OPTS="${JAVA_OPTS} -Dpinpoint.config=${AGENT_DIR}/pinpoint-root.config"
  JAVA_OPTS="${JAVA_OPTS} -Dpinpoint.profiler.profiles.active=${PINPOINT_PROFILE:-release}"
  JAVA_OPTS="${JAVA_OPTS} -Dpinpoint.applicationName=${PINPOINT_APPLICATION_NAME:?PINPOINT_APPLICATION_NAME is required when PINPOINT_COLLECTOR_IP is set}"
  JAVA_OPTS="${JAVA_OPTS} -Dpinpoint.agentId=${HOSTNAME}"
  JAVA_OPTS="${JAVA_OPTS} -Dprofiler.transport.grpc.collector.ip=${PINPOINT_COLLECTOR_IP}"
fi

exec java ${JAVA_OPTS} org.springframework.boot.loader.launch.JarLauncher "$@"
