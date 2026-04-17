#!/usr/bin/env bash
#
# run-worktree-with-pinpoint.sh
# ------------------------------------------------------------
# Launch Phase worktree services with Pinpoint agent attached.
# Phase 0/1/2/3 source does not contain Pinpoint integration,
# but the agent works purely via JVM -javaagent option, so no
# code changes are required in the worktree.
#
# Usage:
#   scripts/run-worktree-with-pinpoint.sh <phase>             # all 4 services
#   scripts/run-worktree-with-pinpoint.sh <phase> <svc>       # single service
#
# Examples:
#   scripts/run-worktree-with-pinpoint.sh phase0
#   scripts/run-worktree-with-pinpoint.sh phase1 order
#
# Prerequisites:
#   - Pinpoint stack running: docker compose -f monitoring/docker-compose.pinpoint.yml up -d
#   - Agent downloaded: ./scripts/setup-pinpoint-agent.sh
#   - Worktree built: (cd <worktree>/backend-v2 && ./gradlew build -x test)
# ------------------------------------------------------------
set -euo pipefail

PHASE="${1:?phase required (phase0|phase1|phase2|phase3|phase4|phase5)}"
ONLY="${2:-}"

REPO_ROOT="/Users/leesanghun/My_Project/ecommerce-microservices"
WT_ROOT="/Users/leesanghun/My_Project/ecommerce-microservices-worktrees/${PHASE}"
AGENT="${REPO_ROOT}/pinpoint-agent"
LOG_DIR="${REPO_ROOT}/build/${PHASE}-logs"
mkdir -p "${LOG_DIR}"

if [[ ! -d "${WT_ROOT}" ]]; then
  echo "Worktree not found: ${WT_ROOT}" >&2
  echo "See docs/worktree-map.md to create it." >&2
  exit 1
fi

if [[ ! -f "${AGENT}/pinpoint-bootstrap.jar" ]]; then
  echo "Pinpoint agent not found. Run ./scripts/setup-pinpoint-agent.sh first." >&2
  exit 1
fi

run_svc() {
  local svc="$1"
  local jar
  jar="$(ls "${WT_ROOT}/backend-v2/service-${svc}/build/libs/"service-${svc}-*.jar 2>/dev/null | grep -v '\-plain\.jar' | head -n1)"
  if [[ -z "${jar}" || ! -f "${jar}" ]]; then
    echo "jar missing for service-${svc}. Run (cd ${WT_ROOT}/backend-v2 && ./gradlew :service-${svc}:bootJar) first." >&2
    return 1
  fi

  java \
    -javaagent:"${AGENT}/pinpoint-bootstrap.jar" \
    -Dpinpoint.agentId="svc-${svc}-${PHASE}" \
    -Dpinpoint.applicationName="service-${svc}-${PHASE}" \
    -Dpinpoint.config="${AGENT}/pinpoint-root.config" \
    -Dprofiler.transport.grpc.collector.ip=localhost \
    -jar "${jar}" \
    --spring.profiles.active=local \
    > "${LOG_DIR}/${svc}.log" 2>&1 &
  echo "  ${svc} pid=$!  log=${LOG_DIR}/${svc}.log"
}

echo "Launching ${PHASE} services with Pinpoint agent..."
if [[ -n "${ONLY}" ]]; then
  run_svc "${ONLY}"
else
  for svc in product order payment customer; do
    run_svc "${svc}" || true
  done
fi

echo
echo "Waiting for health..."
declare -A svc_ports=(
  [product]=8081
  [order]=8082
  [payment]=8083
  [customer]=8084
)

for svc in product order payment customer; do
  if [[ -n "${ONLY}" && "${ONLY}" != "${svc}" ]]; then continue; fi
  port="${svc_ports[$svc]}"
  for i in {1..60}; do
    if curl -sf "http://localhost:${port}/actuator/health" >/dev/null 2>&1; then
      echo "  ${svc} (:${port}): healthy"
      break
    fi
    sleep 2
  done
done

echo
echo "Pinpoint Web UI: http://localhost:8079"
echo "Applications should appear as:"
if [[ -n "${ONLY}" ]]; then
  echo "  service-${ONLY}-${PHASE}"
else
  echo "  service-product-${PHASE}"
  echo "  service-order-${PHASE}"
  echo "  service-payment-${PHASE}"
  echo "  service-customer-${PHASE}"
fi
