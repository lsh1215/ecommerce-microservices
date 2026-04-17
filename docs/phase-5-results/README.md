# Phase 5 Load Test + APM Setup — 검증 결과

> **Phase 5는 개선(defect fix)이 아닌 통합 검증(integration validation) 마일스톤이다.**
> Phase 1 (SAGA) / Phase 2 (Outbox) / Phase 3 (Idempotent Consumer) / Phase 4 (Circuit Breaker) 에서 각각 개별 검증된 resilience 패턴이, 현실적인 운영 부하(300 VUs, 161k iterations)에서도 무너지지 않는다는 것을 end-to-end 로 증명하는 단계다.
> Before/After 비교 구도는 적용되지 않는다 — Phase 5에 대응하는 "미도입 상태"는 존재하지 않기 때문이다 (Phase 0~4 자체가 Before이고, Phase 5는 이들을 통과 기준으로 가두는 테스트 스위트). 이 문서의 "Before/After" 표는 Phase 0→5 누적 변화를 요약한 것일 뿐, Phase 5 단독의 성능 향상치가 아니다.

## 테스트 환경
- 로컬 (MacOS, Apple Silicon), Phase 0~4와 동일 환경
- MySQL 8.0 + Kafka 3.8.1 (Docker Compose)
- 4개 서비스 `./gradlew bootRun` (local 프로파일, Circuit Breaker 활성)
- Seed data: 상품 8종 × 3~4 variants, 고객 20명 (variants 1~9는 테스트 편의상 재고 10만으로 상향)

---

## Phase 5가 달성한 것

### 1. 종합 k6 부하 테스트 스위트 (`k6/scenarios/`)

| 스크립트 | 목적 | 부하 | 기준 |
|---|---|---|---|
| `smoke-test.js` | 핵심 경로 sanity check | 5 VUs × 30s | 100% 성공, p99 < 1s |
| `load-test.js` | 정상 운영 트래픽 | 0→50 VUs ramp, 3분 유지, 30초 ramp-down | p99 < 2s, 에러율 < 1% |
| `stress-test.js` | Breaking point 탐색 | 100→200→300 VUs, 각 1분 후 300 VUs 2분 유지 | p99 < 5s, 에러율 < 10% |
| `phase4-slow-product.js` | CB fast-fail 검증 (Phase 4) | 30 VUs × 30s + Product 2s chaos delay | order_create p95 < 3s |

### 2. Pinpoint APM 기반 분산 추적 인프라

- `monitoring/docker-compose.pinpoint.yml` — HBase + Collector + Web (Pinpoint 3.0.5, amd64 platform 핀)
- `scripts/setup-pinpoint-agent.sh` — 에이전트 자동 다운로드 (v3.0.5)
- `scripts/run-with-pinpoint.sh` — 각 서비스를 `-javaagent`로 띄우는 런처
- Grafana/Prometheus 대신 Pinpoint를 선택 — request-level visibility + service map이 포트폴리오 MSA에 더 적합

### 3. 종합 실행 스크립트 (`scripts/run-full-test.sh`)

Smoke → Load → Stress를 순차 실행하고 결과를 `docs/phase-5-results/`에 저장. Stress 생략 옵션(`--skip-stress`) 포함.

---

## 실측 결과 (2026-04-16 로컬 실행)

### Smoke Test (5 VUs × 30s)

```
$ k6 run k6/scenarios/smoke-test.js

checks_total: 725
checks_succeeded: 100.00% (725/725)

http_req_duration p95 = 34.18ms
http_req_duration p99 = 87.16ms  ← threshold < 1000ms ✓
http_req_failed rate = 0.00%      ← threshold < 5% ✓
iterations = 145 @ 4.76 req/s
```

전체 체크 통과. (증거: [smoke.txt](./smoke.txt))

### Load Test (50 VUs, 4.5 min)

```
$ k6 run k6/scenarios/load-test.js

checks_total: 17,792
checks_succeeded: 100.00% (17,792/17,792)

http_req_duration p99 = 28.19ms   ← threshold < 2000ms ✓
http_req_failed rate = 0.00%       ← threshold < 1% ✓
browse_duration p95 = 7.54ms       ← threshold < 500ms ✓
order_create_duration p95 = 30.2ms ← threshold < 1500ms ✓
iterations = 11,161 @ 41.2 req/s (throughput 65.7 HTTP req/s)
```

모든 threshold 통과. **11,161회 주문/브라우징/조회 전부 성공**. (증거: [load.txt](./load.txt))

### Stress Test (100→200→300 VUs, 5.5 min)

```
$ k6 run k6/scenarios/stress-test.js

checks_total: 161,458
checks_succeeded: 100.00% (161,458/161,458)

http_req_duration p99 = 821.32ms   ← threshold < 5000ms ✓
http_req_failed rate = 0.00%        ← threshold < 10% ✓
order_create_duration p95 = 763.26ms
order_create_errors = 0.00% (0 out of 48,474 orders)
iterations = 161,458 @ 488.96 req/s
```

300 VU 지속 부하에서도 **완전한 안정성**: 48,474건 주문 전부 성공, 에러 0%. (증거: [stress.txt](./stress.txt))

---

## Before/After 비교 (Phase 0 → Phase 5)

| 시나리오 | Phase 0 (MVP) | Phase 1~3 | Phase 4~5 (current) |
|---|---|---|---|
| 정상 부하 (50 VU) p99 | ~100ms (측정 X) | ~500ms | **28ms** |
| Payment DOWN 시 주문 | **0% 성공** (cascading) | 100% 성공 (PENDING) | 100% 성공 (PENDING) |
| Kafka DOWN 시 이벤트 | 유실 | Phase 2 이후 **0건 유실** | 0건 유실 |
| 중복 이벤트 처리 | 중복 결제 가능 | Phase 3 이후 **exactly-once** | exactly-once |
| Product 느려짐 (2s) | 스레드 고갈 → 전체 마비 | 동일 (Phase 3까지) | **CB fast-fail p95 22ms** |
| 300 VU 지속 부하 | 불가능 (미측정) | 미측정 | **488 req/s, 0% 에러** |
| 관측성 | 없음 | 없음 | Actuator + Pinpoint APM |

---

## STAR 요약

| | |
|---|---|
| **S** | Phase 0~4 까지 각 resilience 패턴을 단독 검증했으나, 전체 시스템이 다양한 부하 패턴에서 어떻게 동작하는지 종합 증거가 부재. 운영 관측성 인프라도 없었음. |
| **T** | 1) k6 smoke/load/stress 스위트로 end-to-end 검증, 2) Pinpoint APM 기반 분산 추적 인프라 세팅, 3) Phase 0→5 전 구간 성능 비교 증거 확보. |
| **A** | k6 시나리오 4종 작성 (smoke, load, stress, chaos-slow-product), Pinpoint 3.0.5 Docker Compose + 에이전트 다운로드/런처 스크립트 정비, 실행 스크립트 `run-full-test.sh`. |
| **R** | 50 VU 지속 부하 p99=28ms / 0% 에러 / 11,161 성공, 300 VU 스트레스 p99=821ms / 0% 에러 / 161,458 iterations / 488 req/s 처리량. 모든 k6 threshold 통과. |

---

## 한계 및 남은 문제

### 이번 Phase 5에서 검증하지 않은 것

1. **Kafka 실제 다운 시나리오** — Phase 2에서 수동 검증됐지만 k6로 자동화되지 않음. `chaos-kafka-down.js` 같은 스크립트 추가 여지.
2. **Pinpoint 실제 트레이스 수집** — compose/scripts 구조 검증까지만. HBase 초기화가 Apple Silicon amd64 에뮬레이션 때문에 로컬 시연이 느려 실제 트레이스는 실 배포 시점(amd64 native host)으로 이연.
3. **고객 서비스 GET /api/customers/{id}** 등 일부 API는 Controller/Service 단에서 UseCase 호출부가 미구현 (`UnsupportedOperationException`). 부하 테스트는 현재 구현된 경로에 한정.
4. **Order GET /api/orders LazyInitializationException 버그** — Phase 1 로컬 테스트 중 발견. 본 Phase 5 load-test에서는 `/actuator/health`로 우회해 측정.

### 실제 프로덕션 전 필요한 추가 작업

- Distributed tracing 수집 확인 (Pinpoint를 amd64 호스트에 띄운 뒤 전체 서비스 계층 trace 수집)
- Alert rule 정의 (CB OPEN, p99 > threshold 등)
- 장기 로그 보관 (현재는 stdout만)
- Integration test 자동화 (CI에서 k6 smoke 자동 실행)

이 항목들은 실 배포 직전 단계에서 별도 phase로 구성 가능.
