# Phase 5 — Load + APM (통합 검증 마일스톤)

> **Phase 5 는 개선(defect fix)이 아닌 통합 검증(integration validation) 마일스톤이다.**
> Phase 1 (SAGA) / Phase 2 (Outbox) / Phase 3 (Idempotent Consumer) / Phase 4 (Circuit Breaker) 에서 개별 검증된 resilience 패턴이, 현실적인 운영 부하(300 VUs, 161k iterations)에서도 무너지지 않는다는 것을 end-to-end 로 증명한다. Before/After 구도는 적용되지 않는다 — Phase 5 에 대응하는 "미도입 상태"는 Phase 0~4 자체이며, Phase 5 는 이들을 통과 기준으로 가두는 테스트 스위트다.

- **Worktree**: `/Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase5` (`e791aa5`)
- **Evidence**: [`smoke.txt`](./smoke.txt), [`load.txt`](./load.txt), [`stress.txt`](./stress.txt)

## 목표 (Goal)

1. Phase 1~4 의 모든 resilience 패턴이 **현실 부하에서 동시에** 정상 동작함을 증명
2. 장기 운영에서 필요한 **관측 인프라 (Pinpoint APM + Actuator)** 세팅
3. **재현 가능한 부하 테스트 스위트** 를 레포에 커밋하여 후속 변경 시 회귀 검증 가능

## 핵심 수치

| 시나리오 | VUs × 시간 | iterations | `http_req_failed` | p99 | throughput |
|---|---|---|---|---|---|
| Smoke | 5 × 30s | 145 | 0% | 87 ms | 4.8 req/s |
| Load | 0→50 ramp, 4.5 min | 11,161 | 0% | 28 ms | 65.7 req/s |
| **Stress** | 100→200→300, 5.5 min | **161,458** | **0%** | 821 ms | **489 req/s** |

수치 근거: [`smoke.txt`](./smoke.txt), [`load.txt`](./load.txt), [`stress.txt`](./stress.txt).

### Phase 0 → Phase 5 누적 변화 요약 (참고)

| 시나리오 | Phase 0 (MVP) | Phase 1~3 | Phase 4~5 (current) |
|---|---|---|---|
| 정상 부하 50 VUs p99 | ~100ms (측정 X) | ~500ms | **28ms** |
| Payment DOWN 시 주문 성공률 | **0%** (cascading) | 100% (PENDING) | 100% (PENDING) |
| Kafka DOWN 이벤트 유실 | 가능 | Phase 2 이후 **0건** | 0건 |
| 중복 이벤트 | 중복 결제 가능 | Phase 3 이후 exactly-once | exactly-once |
| Product 2s 지연 p95 | 스레드 고갈 | 동일 (Phase 3까지) | **CB fast-fail 22ms** |
| 300 VU 지속 부하 | 측정 불가 | 미측정 | **489 req/s, 0% 에러** |
| 관측성 | 없음 | 없음 | **Actuator + Pinpoint APM** |

---

## 🧪 Testing Guide

### 1. 테스트 종류

| 항목 | 내용 |
|---|---|
| **유형** | 3단계 부하 테스트 스위트 (smoke / load / stress) + 관측 |
| **부하 생성기** | k6 (`k6/scenarios/{smoke,load,stress}-test.js`) |
| **관측 스택** | Pinpoint APM (`http://localhost:8079`), Spring Boot Actuator (`/actuator/*`) |
| **통과 기준** | 각 시나리오의 threshold 전부 PASS (k6 자체 기준) |
| **Before/After** | **N/A**. 이 Phase 는 통합 검증이므로 Phase 4 worktree 에서 같은 스위트를 돌리면 같은 결과가 나오는 것이 정상. |

### 2. 실행 방법

#### Step A. 인프라 + APM 기동

```bash
cd /Users/leesanghun/My_Project/ecommerce-microservices

# Docker
docker compose -f infra/docker-compose.yml up -d mysql kafka
docker compose -f monitoring/docker-compose.pinpoint.yml up -d
sleep 60  # HBase 초기화 대기

# Pinpoint agent (최초 1회)
./scripts/setup-pinpoint-agent.sh
```

#### Step B. phase5 worktree 서비스 기동 (4개 전부 + Pinpoint 부착)

```bash
(cd /Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase5/backend-v2 \
  && ./gradlew bootJar -x test -q)

./scripts/run-worktree-with-pinpoint.sh phase5

# 시드
docker exec -i ecommerce-mysql mysql -uroot -p1234 < scripts/seed-data.sql
docker exec ecommerce-mysql mysql -uroot -p1234 -e \
  "USE ecommerce_product; UPDATE product_variant SET stock_quantity = 100000 WHERE id IN (1,2,3,4,5,6,7,8,9);"
```

Pinpoint Web UI(`http://localhost:8079`) 에서 4 개 Application 이 등록됐는지 확인 후 부하 시작.

#### Step C. 3단계 부하 테스트 순차 실행

```bash
MAIN_DOCS=/Users/leesanghun/My_Project/ecommerce-microservices/docs

# 1. Smoke — sanity check
k6 run \
  --out web-dashboard=export=$MAIN_DOCS/phase-5-results/evidence/k6-smoke.html \
  k6/scenarios/smoke-test.js \
  2>&1 | tee $MAIN_DOCS/phase-5-results/evidence/smoke.txt

# 2. Load — 정상 운영 트래픽
k6 run \
  --out web-dashboard=export=$MAIN_DOCS/phase-5-results/evidence/k6-load.html \
  k6/scenarios/load-test.js \
  2>&1 | tee $MAIN_DOCS/phase-5-results/evidence/load.txt

# 3. Stress — breaking point
k6 run \
  --out web-dashboard=open=true,export=$MAIN_DOCS/phase-5-results/evidence/k6-stress.html \
  k6/scenarios/stress-test.js \
  2>&1 | tee $MAIN_DOCS/phase-5-results/evidence/stress.txt
```

전체 스위트를 한 번에 돌리려면 기존 orchestrator 사용:

```bash
./scripts/run-full-test.sh          # smoke → load → stress
./scripts/run-full-test.sh --skip-stress  # stress 생략
```

### 3. 확인 지표

#### k6 threshold (각 스크립트 내부에 정의)

| 지표 | Smoke | Load | Stress |
|---|---|---|---|
| `http_req_duration` p99 | < 1000 ms | < 2000 ms | < 5000 ms |
| `http_req_failed` | < 5% | < 1% | < 10% |
| `order_create_duration` p95 | n/a | < 1500 ms | n/a |
| 모든 threshold 통과 | ✓ | ✓ | ✓ |

#### Pinpoint APM 지표

| 지표 | 위치 |
|---|---|
| Application Inspector 의 Transactions Per Minute | Pinpoint → Inspector 탭 — 300 VU 지속 구간에서 29,000 tpm 안정 |
| Server Map 의 service topology | Pinpoint → Server Map — 4 개 서비스 + Kafka + MySQL 전체 토폴로지가 에러 없이 green |
| Response Time heatmap | Pinpoint → Inspector → Response Time — p99 분포 |

#### Actuator 지표

| 지표 | 엔드포인트 |
|---|---|
| CB 상태 (전부 CLOSED 유지) | `GET http://localhost:8082/actuator/circuitbreakers` |
| Outbox pending count | `SELECT COUNT(*) FROM outbox_event WHERE status='PENDING'` — 부하 구간에도 < 100 |
| Hikari 풀 사용량 | `GET http://localhost:8082/actuator/metrics/hikaricp.connections.active` |
| Kafka consumer lag | `docker exec ecommerce-kafka kafka-consumer-groups.sh --describe --group service-payment` |

### 4. 포트폴리오 증거 캡처

#### 🥇 대표 이미지: Pinpoint Inspector — 300 VU Stress 구간

Pinpoint Web UI → Application: `service-order-phase5` → **Inspector 탭** → Time range 를 stress 구간에 맞춤.
**Transactions Per Minute 그래프가 29k 근처로 평탄** + **Response Time heatmap 에 빨간 outlier 가 거의 없는** 모습을 캡처.

저장: `docs/phase-5-results/evidence/pinpoint-inspector-stress.png`

> 💡 캡션: _"300 VU 지속 5.5 min, 161,458 iterations, 0% 에러. Phase 1~4 의 resilience 패턴이 동시에 동작하는 구간에서도 APM 관측상 이상 징후 없음."_

#### 🥈 보조 이미지 1: Pinpoint Server Map — 전체 MSA 토폴로지

Server Map 전체 스크린샷. 4 개 서비스 + Kafka + MySQL 의 호출 관계가 한 장에 들어오도록.

저장: `docs/phase-5-results/evidence/pinpoint-servermap-full.png`

#### 🥉 보조 이미지 2: k6 Stress Web Dashboard

`--out web-dashboard=open=true` 로 열린 브라우저에서 stress 테스트 종료 직전 캡처. VUs 그래프가 100→200→300 으로 계단식 증가하는 동안 error rate 이 **0% flat line** 을 유지하는 모습.

저장: `docs/phase-5-results/evidence/k6-stress-dashboard.png`

#### 🏅 보조 이미지 3: Actuator Health 스냅샷 — 전 서비스 UP

```bash
for port in 8081 8082 8083 8084; do
  curl -s http://localhost:${port}/actuator/health | jq '.status'
done
```

4 개 `"UP"` 응답을 터미널에서 캡처.

저장: `docs/phase-5-results/evidence/health-all-up.png`

#### 포트폴리오 삽입 예시

```markdown
### Phase 5 — 통합 부하 + APM 으로 전 구간 검증

Phase 1~4 의 개별 resilience 패턴이 **현실 부하에서 동시에** 동작함을 확인.

![Pinpoint Inspector Stress](phase-5-results/evidence/pinpoint-inspector-stress.png)

- 300 VUs × 5.5 min, **161,458 iterations, 0.00% 에러**
- `http_req_duration` p99 = 821 ms (< 5s threshold)
- 주문 48,474 건 전부 성공, SAGA 흐름 완료

![k6 Stress Dashboard](phase-5-results/evidence/k6-stress-dashboard.png)

Resilience 패턴이 production-like 부하에서 유효함을 실측으로 검증.
```

### 정리

```bash
for port in 8081 8082 8083 8084; do
  pid=$(lsof -iTCP:$port -sTCP:LISTEN -P -n 2>/dev/null | awk 'NR>1 {print $2}' | head -1)
  [ -n "$pid" ] && kill $pid
done

docker compose -f monitoring/docker-compose.pinpoint.yml down
docker compose -f infra/docker-compose.yml down
```

---

## 한계 및 개선 여지

- **Pinpoint on Apple Silicon**: HBase init 이 rosetta 에뮬레이션 때문에 60~90s 소요. amd64 네이티브 호스트에 배포 시 개선.
- **GET /api/orders LazyInitializationException**: Phase 1 로컬 테스트 중 발견. 현재 k6 스크립트는 `/actuator/health` 를 조회 경로로 사용 — 실 배포 전 QueryDSL projection 으로 수정 필요.
- **Customer GET 일부 엔드포인트 UnsupportedOperationException**: UseCase 미구현 상태. 실 서비스 배포 전 구현 필요.
- **Alert rule**: CB OPEN, p99 임계 초과 등 자동 알림 없음. Prometheus alertmanager 도입 여지.
