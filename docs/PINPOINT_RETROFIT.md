# Pinpoint Retrofit — Phase 0/1/2/3 Worktree 에 APM 장착하기

Phase 4 이전 commit 들에는 Pinpoint 설정 파일이나 agent 호출 스크립트가 포함되어 있지 않다. 하지만 Pinpoint agent 는 **bytecode instrumentation** 으로 동작하므로, worktree 의 소스 코드를 전혀 건드리지 않고 **JVM `-javaagent:` 옵션만 추가**하면 각 Phase worktree 에 APM 을 장착할 수 있다.

## 1. 전제 조건

- Pinpoint stack 이 실행 중: `docker compose -f monitoring/docker-compose.pinpoint.yml up -d`
- Pinpoint agent 다운로드 완료: `./scripts/setup-pinpoint-agent.sh` 실행 후 `pinpoint-agent/pinpoint-bootstrap.jar` 존재
- 각 worktree 에서 `./gradlew build -x test` 가 성공해 `build/libs/*.jar` 가 존재

## 2. 장착 스크립트

아래 스크립트를 **메인 레포 루트**에서 실행하면 지정된 worktree 의 모든 서비스가 Pinpoint agent 를 부착한 채 기동된다.

```bash
#!/usr/bin/env bash
# scripts/run-worktree-with-pinpoint.sh
#
# Usage:
#   scripts/run-worktree-with-pinpoint.sh <phase>        # all 4 services
#   scripts/run-worktree-with-pinpoint.sh <phase> <svc>  # single service
#
# Example:
#   scripts/run-worktree-with-pinpoint.sh phase0
#   scripts/run-worktree-with-pinpoint.sh phase1 order

set -euo pipefail

PHASE="${1:?phase required (phase0|phase1|phase2|phase3|phase4|phase5)}"
ONLY="${2:-}"

REPO_ROOT="/Users/leesanghun/My_Project/ecommerce-microservices"
WT_ROOT="/Users/leesanghun/My_Project/ecommerce-microservices-worktrees/${PHASE}"
AGENT="${REPO_ROOT}/pinpoint-agent"
LOG_DIR="${REPO_ROOT}/build/${PHASE}-logs"
mkdir -p "${LOG_DIR}"

if [[ ! -f "${AGENT}/pinpoint-bootstrap.jar" ]]; then
  echo "Pinpoint agent not found. Run ./scripts/setup-pinpoint-agent.sh first." >&2
  exit 1
fi

run_svc() {
  local svc="$1"
  local jar="${WT_ROOT}/backend-v2/service-${svc}/build/libs/service-${svc}-0.0.1-SNAPSHOT.jar"
  if [[ ! -f "${jar}" ]]; then
    echo "jar missing: ${jar}. Build it from the worktree first." >&2
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
    run_svc "${svc}"
  done
fi

echo "Waiting for health..."
for port in 8081 8082 8083 8084; do
  if [[ -n "${ONLY}" ]]; then
    case "${ONLY}" in
      product) [[ ${port} != 8081 ]] && continue ;;
      order) [[ ${port} != 8082 ]] && continue ;;
      payment) [[ ${port} != 8083 ]] && continue ;;
      customer) [[ ${port} != 8084 ]] && continue ;;
    esac
  fi
  for i in {1..60}; do
    if curl -sf "http://localhost:${port}/actuator/health" >/dev/null 2>&1; then
      echo "  :${port} up"
      break
    fi
    sleep 2
  done
done
echo "Done. Pinpoint Web UI: http://localhost:8079"
```

위 스크립트를 `scripts/run-worktree-with-pinpoint.sh` 로 저장하고 `chmod +x` 후 사용한다.

## 3. Pinpoint Web UI 에서 확인

1. http://localhost:8079 접속
2. 좌측 상단 **Application** 드롭다운에서 `service-<svc>-<phase>` 선택 (예: `service-order-phase0`)
3. **Inspector** 탭 → 서비스 상태 / CPU / Heap / Tx per minute 실시간 그래프
4. **Server Map** → 다른 서비스와의 호출 관계 시각화 (Order → Product, Order → Payment 등)
5. **Transaction List** → 개별 트랜잭션 drill-down, Call Tree 확인

## 4. Agent ID 네이밍 컨벤션

동일한 서비스의 서로 다른 Phase 를 함께 띄우려면 agent ID 가 달라야 한다. 이미 스크립트에 `${svc}-${phase}` 로 구분해놨지만, Application Name 도 Phase 별로 다르게 지정하면 Pinpoint 대시보드에서 비교가 쉽다.

| Phase | agentId | applicationName |
|---|---|---|
| phase0 | `svc-order-phase0` | `service-order-phase0` |
| phase1 | `svc-order-phase1` | `service-order-phase1` |
| phase2 | `svc-order-phase2` | `service-order-phase2` |
| phase3 | `svc-order-phase3` | `service-order-phase3` |
| phase4 | `svc-order-phase4` | `service-order-phase4` |

이렇게 하면 Pinpoint 에서 Phase 0 과 Phase 4 를 **동일한 대시보드에 나란히** 놓고 비교 캡처 가능.

## 5. 스크린샷 캡처 팁 (포트폴리오 기준)

### Phase 0 → 1 (Cascading Failure)

**장면**: Payment 서비스를 다운시킨 상태에서 주문 POST 부하를 주고, Pinpoint Server Map 에서 Order → Payment 화살표에 **빨간색 에러 배지**가 생기는 순간을 캡처한다.

```
# Pinpoint Inspector 의 경우
Application: service-order-phase0
Time range: 부하 테스트 실행 구간
→ Response Time 그래프에서 spike 또는 timeout bar 캡처
```

### Phase 1 → 2 (Dual Write)

**장면**: Phase 1 worktree 에서 Kafka 를 중지한 직후의 Server Map — Order → Kafka Producer 노드가 **회색/비활성**으로 표시됨. Phase 2 에서는 같은 상황이지만 Outbox → Kafka 의 **별도 경로**가 보임.

### Phase 2 → 3 (Multi Consumer)

**장면**: Phase 3 idempotency 테스트 실행 중 Server Map 에 `service-payment-groupA` 와 `service-payment-groupB` **두 개의 consumer 노드**가 동시에 `order.created` 토픽에서 수신하는 모습. 한쪽은 성공, 한쪽은 `DataIntegrityViolationException` 으로 rollback.

### Phase 3 → 4 (Circuit Breaker)

**장면**:
- Before: Pinpoint 의 **Thread Dump** 탭에서 `http-nio-8082-exec-*` 스레드가 Product RestClient 호출에서 대기 중인 모습을 캡처.
- After: Transaction Call Tree 에서 동일 경로가 **22ms 내에 종료** (`CallNotPermittedException` 발생 노드 확인).

### Phase 5 (통합 부하)

**장면**: 300 VUs 지속 부하 중 Pinpoint Inspector 의 **Transactions Per Minute** 그래프. 분당 29,000 건 안정 처리되는 평평한 선.

## 6. 주의사항

- **Apple Silicon (arm64) 에서 HBase 초기화 시간**: `platform: linux/amd64` 로 고정되어 에뮬레이션 때문에 첫 부팅에 60~90초 걸린다. 서비스 기동 전에 `http://localhost:8079` 가 응답하는지 먼저 확인.
- **Spring Profile**: `application-local.yml` 에 `spring.kafka.bootstrap-servers=localhost:9092` 가 명시되어 있어야 한다. phase0~phase5 모두 해당.
- **Port 충돌**: Phase 를 바꿀 때는 반드시 이전 JVM 프로세스를 종료해야 한다 (`lsof -iTCP:8082 | awk 'NR>1 {print $2}' | xargs kill`).
- **Pinpoint 데이터 보관**: 기본 retention 은 짧다 (~7일). 포트폴리오 캡처 직후 스크린샷 저장을 권장.
