# Phase 4 Circuit Breaker — 검증 결과 및 남은 문제

## 테스트 환경
- 로컬 (MacOS), Phase 0~3과 동일 환경
- MySQL 8.0 + Kafka 3.8.1 (Docker Compose)
- 4개 서비스 `./gradlew bootRun` (local 프로파일)
- `app.chaos.enabled=true --app.chaos.stock-delay-ms=2000` 설정으로 Product 서비스에 인위적 2초 지연 주입

---

## Phase 4가 해결한 문제: Slow Dependency → 전체 서비스 마비

### 배경

Phase 0~3에서 Order → Payment 비동기 경로의 신뢰성은 확보됐지만, **동기 호출 경로**는 여전히 남아있다:
- `Order → Product` (재고 예약): RestClient 동기 호출 (강한 일관성 필요)
- `Order → Customer` (고객 검증): RestClient 동기 호출

**Phase 0과의 차이**: Phase 0은 "서비스 DOWN" (빠른 실패). Phase 4는 "서비스 느려짐" — 더 악질. 스레드를 잡아먹으며 전체 서비스를 마비시킴.

### 테스트 방법론

**부하 테스트 + Chaos Engineering**:
- Product 서비스 internal 엔드포인트에 `HandlerInterceptor`로 2초 지연 주입
- k6로 order 생성(30 VU) + health 조회(5 VU) 동시 부하 → 응답 시간 분포 측정
- 동일한 부하 조건에서 CB 비활성(Before)과 CB 활성(After) 두 번 측정

**단위 테스트**:
- Mockito + AspectJ proxy로 `@CircuitBreaker` 동작 격리 검증

### 테스트 시나리오 (부하 테스트)

```
Step 0: Product 서비스에 chaos delay 2s 주입
  $ ./gradlew :service-product:bootRun \
      --args='--app.chaos.enabled=true --app.chaos.stock-delay-ms=2000'
  → fetchSnapshot, reserveStock 등 internal 엔드포인트가 모두 2초 지연

Step 1 — BEFORE (CB 효과 비활성):
  $ ./gradlew :service-order:bootRun \
      --args='--resilience4j.circuitbreaker.instances.productService.minimumNumberOfCalls=1000000
              --resilience4j.circuitbreaker.instances.productService.slidingWindowSize=1000000'
  (minimumNumberOfCalls=100만 → 실질적으로 CB가 절대 OPEN되지 않음)
  $ k6 run k6/scripts/phase4-slow-product.js
  → k6-slow-product-before.txt 에 결과 저장

Step 2 — AFTER (CB 기본 production config):
  $ ./gradlew :service-order:bootRun   (default yml: slidingWindow=10, slowCall=2s, failureThreshold=50%)
  $ k6 run k6/scripts/phase4-slow-product.js
  → k6-slow-product-after.txt 에 결과 저장

Step 3 — CB 상태 변화 관찰:
  $ curl http://localhost:8082/actuator/circuitbreakers
  → cb-state-snapshots.txt 에 결과 저장
```

### 결과 (BEFORE vs AFTER)

| 지표 | BEFORE (CB 없음) | AFTER (CB 적용) | 개선 |
|---|---|---|---|
| order 생성 p95 | **12.58 s** | **21.95 ms** | **573x 빠름** |
| order 생성 p90 | 12.55 s | 17.46 ms | — |
| order 생성 평균 | 11.05 s | 25.7 ms | — |
| health 조회 p95 | 7.72 ms | 15.81 ms | 영향 없음 (두 경우 모두 ms 수준) |
| 전체 throughput | 399 iter / 43s = **9.3 req/s** | 7,406 iter / 30s = **245 req/s** | **26x** |
| order create 성공률 | 1% 성공, 나머지는 타임아웃 직전 완료 | 0.5% 성공 (circuit 열리기 전), 99.5% 503 fast-fail | — |
| Circuit 상태 | CLOSED (실질적으로 비활성) | **OPEN** (5회 slow call 후) | — |
| 차단된 호출 수 | 0 | **1,476 (notPermittedCalls)** | — |

### 결과의 의미

**BEFORE**:
- 30 VU 각각이 2초 지연을 감당하며 대기. 스레드 풀 큐잉 때문에 평균 11초, p95 12.58초.
- 전체 처리량은 9 req/s 수준으로 급락.
- 주문 API가 마비되면 이를 호출하는 상위 시스템(게이트웨이, 프론트엔드)도 영향을 받음.

**AFTER**:
- 첫 5회 호출이 slow call(>2s)로 판정 → `slowCallRate=100%` > 50% 임계치 → CB **OPEN**.
- 이후 1,476건은 CB가 Product 호출 자체를 차단 → 평균 26ms에 503 반환.
- 사용자 관점: "일시적으로 불가" 메시지를 빠르게 받는 것이 "30초 대기 후 타임아웃"보다 우월.
- **스레드 풀이 여유 상태 유지** → 다른 엔드포인트는 정상 동작.

### DB 증거 요약 (`cb-state-snapshots.txt`에서 발췌)

```
--- AFTER test 종료 시점의 Circuit Breaker 상태 ---
productService:
  state=OPEN                          ← Circuit이 열린 상태
  bufferedCalls=3                     ← 현재 슬라이딩 윈도우에 3건
  failedCalls=0                       ← 실제 실패 0 (전부 slow call)
  slowCalls=3                         ← slow call 3건 (>=2s)
  notPermittedCalls=1476              ← CB가 차단한 호출 1,476건
  failureRate=0.0%
  slowCallRate=100.0%                 ← slow call 비율 100% > 50% 임계치 → OPEN
```

### 단위 테스트 결과 (5/5 PASSED)

```
$ ./gradlew :service-order:test --tests "*ProductCatalogRestClientCircuitBreakerTest*"

ProductCatalogRestClientCircuitBreakerTest > 정상 응답이 반복되면 Circuit Breaker는 CLOSED 상태를 유지한다 PASSED
ProductCatalogRestClientCircuitBreakerTest > Product 서비스에서 5xx 실패가 임계치를 넘으면 Circuit Breaker가 OPEN으로 전이한다 PASSED
ProductCatalogRestClientCircuitBreakerTest > Circuit이 OPEN이면 실제 HTTP 호출 없이 fallback이 즉시 실행된다 (fast-fail) PASSED
ProductCatalogRestClientCircuitBreakerTest > OPEN 상태의 fetchSnapshot은 PRODUCT_SERVICE_UNAVAILABLE 에러를 반환한다 PASSED
ProductCatalogRestClientCircuitBreakerTest > OPEN 상태의 releaseStock은 fallback에서 예외를 삼켜 보상 트랜잭션을 방해하지 않는다 PASSED

BUILD SUCCESSFUL
```

**테스트 코드 위치**: `backend-v2/service-order/src/test/.../infra/client/ProductCatalogRestClientCircuitBreakerTest.java`

---

## CB 상태 전이 확인 (순차 요청 테스트)

`cb-state-snapshots.txt`에 기록된 대로, 느린 Product를 대상으로 순차 주문을 보냈을 때:

```
Request 1: HTTP 201  (2s 지연 후 성공. CB는 아직 CLOSED)
Request 2: HTTP 201  (2s 지연 후 성공. 아직 CLOSED)
Request 3: HTTP 503  ← CB가 OPEN으로 전이! 요청 3의 fetchSnapshot이 sliding window의 5번째 slow call.
Request 4~10: HTTP 503 (전부 fast-fail, Product에 가지 않음)

state=OPEN, slowCalls=5, slowCallRate=100%, notPermittedCalls=8
```

주문 1건당 CB 호출이 2회(fetchSnapshot + reserveStock)이므로, 요청 2.5개 정도면 minimumNumberOfCalls=5를 충족하고 임계치 판정. 설정대로 동작함이 확인됨.

---

## 아키텍처 변화

### Before (Phase 3까지)

```
[Order] ──동기──> [Product (slow 2s)]
  │                     │
  │ 스레드 블록 (2s)     │ 2s 지연
  ↓                     ↓
스레드 풀 포화          Product는 결국 응답
모든 Order 엔드포인트    하지만 Order는 이미 마비
응답 불가
```

### After (Phase 4)

```
[Order] ──동기──> [CB] ─┬─> [Product (slow)]
  │                    │
  │ CB OPEN 시:         │ CB CLOSED 시:
  │  fast-fail (<100ms) │  정상 호출
  │  503 응답           │
  ↓                    ↓
스레드 즉시 해제        정상 동작
```

---

## STAR 요약

### Phase 4 해결

| | |
|---|---|
| **S** | Order 서비스가 Product/Customer를 RestClient 동기 호출. Product가 2초 지연되자 Order 스레드 풀 포화로 p95 12.58초, 처리량 9 req/s로 급락. |
| **T** | 느린 의존성이 전체 서비스를 마비시키지 않도록 회로 차단 적용. |
| **A** | Resilience4j Circuit Breaker: slidingWindow=10, slowCallDurationThreshold=2s, threshold=50%. 임계 초과 시 OPEN → fast-fail → 10초 후 HALF_OPEN → 복구 시 CLOSED. Fallback에서 503 `PRODUCT_SERVICE_UNAVAILABLE` 반환. |
| **R** | 동일 부하 하에서 order 생성 p95 **12.58s → 21.95ms** (573x 빠름), 처리량 **9 → 245 req/s** (27x). CB가 1,476건을 차단해 Product로의 불필요한 호출 제거. |

### Phase 5 Situation (다음 문제)

| | |
|---|---|
| **발견 방법** | 운영 가시성 검토 — 현재는 actuator endpoint로만 CB 상태 확인. 대시보드/알림 없음. |
| **문제** | CB OPEN/HALF_OPEN 전이, slow call 빈도, 실패율 추이를 실시간 관측 불가 → 이상 징후 감지 지연. |
| **영향** | 장애 발생 후 대응이 늦어짐. 부하 패턴 변화에 따른 CB 튜닝(임계치 조정) 근거 부족. |

---

## 남은 문제 (Phase 5 Situation)

Phase 4에서 CB로 fault isolation을 달성했지만, **운영 가시성**은 아직 부족하다:

- CB 상태 실시간 대시보드 없음 (Pinpoint APM 연동 필요)
- 부하 테스트 자동화 및 회귀 검증 환경 부재
- 임계치(50%, 2s, 10s)의 적정성을 뒷받침할 장기 메트릭 없음

**Phase 5**: 부하 테스트 확대 + Pinpoint APM + Prometheus/Grafana 도입으로 관측 가능성 확보.
