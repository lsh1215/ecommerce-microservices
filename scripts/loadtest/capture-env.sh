#!/usr/bin/env bash
# 부하 테스트 환경을 런 폴더에 자동으로 덤프한다.
#
# 존재 이유: 2026-07 재감사에서 "그 캡처를 어떤 CPU limit으로 돌렸는지" 기록이 없어
# 수치의 인과(커넥션 고갈 vs CPU 스로틀)를 확정할 수 없었다. 사람이 기록을 잊어도
# 환경은 파일로 남아야 한다.
#
# 사용법:
#   scripts/loadtest/capture-env.sh <run-id> <pre|post> [namespace]
#
# 출력:
#   docs/loadtest/runs/<YYYY-MM-DD>-<run-id>/env-<phase>.json

set -euo pipefail

RUN_ID="${1:-}"
PHASE="${2:-}"
NS="${3:-ecommerce}"

if [[ -z "$RUN_ID" || -z "$PHASE" ]]; then
  echo "usage: $0 <run-id> <pre|post> [namespace]" >&2
  exit 2
fi
if [[ "$PHASE" != "pre" && "$PHASE" != "post" ]]; then
  echo "phase must be 'pre' or 'post'" >&2
  exit 2
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="$REPO_ROOT/docs/loadtest/runs/$(date +%Y-%m-%d)-${RUN_ID}"
OUT_FILE="$OUT_DIR/env-${PHASE}.json"
mkdir -p "$OUT_DIR"

need() { command -v "$1" >/dev/null 2>&1 || { echo "missing required tool: $1" >&2; exit 1; }; }
need kubectl
need jq

# git 상태 — 어떤 코드로 돌렸는지
GIT_BRANCH="$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
GIT_COMMIT="$(git -C "$REPO_ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
GIT_DIRTY="$(if [[ -n "$(git -C "$REPO_ROOT" status --porcelain 2>/dev/null)" ]]; then echo true; else echo false; fi)"

# 노드 스펙 — 머신 타입과 할당 가능 자원
NODES="$(kubectl get nodes -o json 2>/dev/null | jq '[.items[] | {
  name: .metadata.name,
  machineType: (.metadata.labels["node.kubernetes.io/instance-type"] // .metadata.labels["beta.kubernetes.io/instance-type"]),
  zone: (.metadata.labels["topology.kubernetes.io/zone"] // null),
  capacity: {cpu: .status.capacity.cpu, memory: .status.capacity.memory},
  allocatable: {cpu: .status.allocatable.cpu, memory: .status.allocatable.memory}
}]' || echo '[]')"

# ★ 핵심: 컨테이너별 resources + 이미지 태그 + JVM 옵션
# 이 블록이 없어서 이전 런의 CPU limit을 확정할 수 없었다.
WORKLOADS="$(kubectl get deploy,statefulset -n "$NS" -o json 2>/dev/null | jq '[.items[] | {
  kind: .kind,
  name: .metadata.name,
  replicas: (.spec.replicas // 1),
  containers: [.spec.template.spec.containers[] | {
    name: .name,
    image: .image,
    resources: .resources,
    javaOpts: [ (.env // [])[] | select(.name | test("JAVA_OPTS|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS")) | {name: .name, value: (.value // "")} ]
  }]
}]' || echo '[]')"

# 실행 중 pod의 실제 리소스 사용량 (metrics-server 필요)
TOP="$(kubectl top pod -n "$NS" --no-headers 2>/dev/null | awk '{printf "{\"pod\":\"%s\",\"cpu\":\"%s\",\"memory\":\"%s\"}\n", $1, $2, $3}' | jq -s '.' || echo '[]')"

# 클러스터 식별
CLUSTER_CTX="$(kubectl config current-context 2>/dev/null || echo unknown)"

jq -n \
  --arg runId "$RUN_ID" \
  --arg phase "$PHASE" \
  --arg capturedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --arg namespace "$NS" \
  --arg context "$CLUSTER_CTX" \
  --arg branch "$GIT_BRANCH" \
  --arg commit "$GIT_COMMIT" \
  --argjson dirty "$GIT_DIRTY" \
  --argjson nodes "$NODES" \
  --argjson workloads "$WORKLOADS" \
  --argjson top "$TOP" \
  '{
    runId: $runId,
    phase: $phase,
    capturedAt: $capturedAt,
    cluster: {context: $context, namespace: $namespace, nodes: $nodes},
    code: {branch: $branch, commit: $commit, dirty: $dirty},
    workloads: $workloads,
    resourceUsage: $top
  }' > "$OUT_FILE"

echo "wrote $OUT_FILE"

# 사람이 바로 볼 수 있게 CPU limit만 요약 출력 — 규칙 2 위반을 즉시 잡기 위한 것
echo
echo "── CPU/메모리 limit 요약 (부하 테스트 프로파일인지 확인) ──"
jq -r '.workloads[] | .name as $n | .containers[] |
  "\($n)/\(.name)\tcpu req=\(.resources.requests.cpu // "-") lim=\(.resources.limits.cpu // "-")\tmem req=\(.resources.requests.memory // "-") lim=\(.resources.limits.memory // "-")"' \
  "$OUT_FILE" | column -t -s $'\t' || true
echo
echo "※ order/product의 cpu limit이 500m이면 개발 기본값이다. 부하 테스트는 2000m 프로파일로 돌린다."
