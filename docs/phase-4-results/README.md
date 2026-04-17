# Phase 4 — Circuit Breaker (Slow Dependency 격리)

- **Worktree**: `/Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase4` (`4a9849f`)
- **Evidence**:
  - [`evidence/before-slow-product.txt`](./evidence/before-slow-product.txt) — CB 비활성 (phase3 worktree 혹은 CB 임계치 무효화 상태)
  - [`evidence/after-slow-product.txt`](./evidence/after-slow-product.txt) — CB 활성 (phase4)
  - [`evidence/cb-state-snapshots.txt`](./evidence/cb-state-snapshots.txt) — Actuator CB 상태 시리즈

## 문제 정의 (Problem)

[Phase 1~3](../phase-3-results/README.md) 에서 Order → Payment 비동기 경로는 신뢰성 확보. 하지만 Order → Product (재고 예약) / Order → Customer (고객 검증) 는 **여전히 동기 RestClient** — 강한 일관성이 필요해 비동기화 불가.

Product 서비스가 **다운** 되는 게 아니라 **느려지는** 상황은 [Phase 0 의 cascading failure](../phase-0-baseline/README.md) 보다 악질이다:
- 다운은 connection refused → 즉시 실패 → 스레드 빠르게 해제
- 느림은 응답 대기 → 스레드가 내내 block → Tomcat 스레드 풀 (기본 200) 포화 → **주문과 무관한 `/actuator/health` 까지 응답 지연**

실측: Product internal 엔드포인트에 2s chaos delay 주입 + 30 VUs 부하 → order create p95 **12.58s**, `http_req_failed` **75.43%** (k6 15s iteration timeout 이 트리거).

## 해결 방법 (Solution)

**Resilience4j Circuit Breaker** — Order Service 의 Product/Customer RestClient 호출을 CB 로 감싼다.

| 상태 | 동작 |
|---|---|
| `CLOSED` | 정상. 모든 호출이 Product 로 전달됨. slow call / failure 를 sliding window (기본 10개) 에 기록. |
| `OPEN` | `failureRate ≥ 50%` 또는 `slowCallRate ≥ 50%` 초과 시 전이. Product 호출을 **즉시 중단** 하고 fallback 메서드 실행 (fast-fail `CallNotPermittedException` → 503). `waitDurationInOpenState` (기본 10s) 동안 유지. |
| `HALF_OPEN` | OPEN 타이머 만료 후 전이. `permittedNumberOfCallsInHalfOpenState` (기본 3) 개 호출만 허용. 성공률 기반으로 CLOSED or OPEN 으로 재결정. |

### 설정 (`backend-v2/service-order/src/main/resources/application.yml`)

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        slowCallDurationThreshold: 2s
        slowCallRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
    instances:
      productService: { baseConfig: default }
      customerService: { baseConfig: default }
```

### 구현 포인트

| 파일 | 역할 |
|---|---|
| `backend-v2/service-order/src/main/java/com/ecommerce/order/infra/client/ProductCatalogRestClient.java` | `@CircuitBreaker(name="productService", fallbackMethod="fallbackFetchSnapshot")` |
| `backend-v2/service-order/src/main/java/com/ecommerce/order/infra/client/CustomerDirectoryRestClient.java` | 동일 패턴으로 `customerService` |
| `backend-v2/service-product/src/main/java/com/ecommerce/product/infra/web/ChaosInterceptor.java` | 부하 테스트용 인위적 지연 주입 (`app.chaos.stock-delay-ms`) |
| `k6/scripts/phase4-slow-product.js` | 30 VUs order create + 5 VUs health — 스레드 풀 고갈 영향을 별도 경로로 측정 |

## Before / After 핵심 수치

| 지표 | Before (CB 비활성) | After (CB 활성) | 개선 |
|---|---|---|---|
| `order_create_duration` p95 | **12.58 s** | **21.95 ms** | **573x** |
| `order_create_duration` 평균 | 11.05 s | 25.7 ms | 430x |
| `http_req_failed` | **75.43%** (k6 15s client timeout) | 96% (intentional CB 503 fast-fail, <50ms) | 의미 반전 |
| Throughput | 9.3 req/s | **245 req/s** | 26x |
| Circuit state | CLOSED (임계치 무효화 상태) | **OPEN** (5 번째 slow call 직후 전이) | — |
| `notPermittedCalls` | 0 | **1,476** (CB 차단으로 Product 에 도달하지 않은 호출) | — |

수치 근거: [`evidence/before-slow-product.txt`](./evidence/before-slow-product.txt), [`evidence/after-slow-product.txt`](./evidence/after-slow-product.txt), [`evidence/cb-state-snapshots.txt`](./evidence/cb-state-snapshots.txt).

> **http_req_failed 의 의미 반전 주의**: Before 의 75.43% 는 k6 iteration timeout (15s) 에 걸린 client-side 실패다. 서버는 여전히 지연만 되고 있었다. After 의 96% 는 CB 가 의도적으로 반환한 503 — **스레드를 잡지 않은 채** 빠르게 실패. 사용자 관점에서는 "22ms 만에 명확한 실패 메시지" 가 "12초 기다린 후 타임아웃"보다 훨씬 우월.

---

## 🧪 Testing Guide

### 1. 테스트 종류

| 항목 | 내용 |
|---|---|
| **유형** | Chaos Engineering (slow dependency 주입) + 부하 테스트 + CB 상태 전이 관측 |
| **부하 생성기** | k6 (`k6/scripts/phase4-slow-product.js`, 30 VUs order create + 5 VUs health × 30s) |
| **장애 주입** | Product 서비스의 `ChaosInterceptor` 로 internal 엔드포인트에 2s 지연 주입 (`--app.chaos.enabled=true --app.chaos.stock-delay-ms=2000`) |
| **Before 재현 방식** | (a) phase3 worktree 는 CB 가 없으므로 그냥 실행, 또는 (b) phase4 worktree 에서 `minimumNumberOfCalls=1000000` 으로 override 해 CB 를 사실상 무효화 |

### 2. 실행 방법

#### Step A. Before — phase4 worktree + CB 임계치 무효화 (권장)

phase3 worktree 는 chaos interceptor 자체가 없으므로 Before 재현이 어렵다. phase4 worktree 에서 CB 임계치를 비활성화하는 것이 가장 깨끗.

```bash
cd /Users/leesanghun/My_Project/ecommerce-microservices
(cd /Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase4/backend-v2 \
  && ./gradlew bootJar -x test -q)

# 인프라
docker compose -f infra/docker-compose.yml up -d mysql kafka
docker compose -f monitoring/docker-compose.pinpoint.yml up -d

# DB 초기화
docker exec ecommerce-mysql mysql -uroot -p1234 -e "
  DROP DATABASE IF EXISTS ecommerce_order;   CREATE DATABASE ecommerce_order;
  DROP DATABASE IF EXISTS ecommerce_product; CREATE DATABASE ecommerce_product;
  DROP DATABASE IF EXISTS ecommerce_customer; CREATE DATABASE ecommerce_customer;
  DROP DATABASE IF EXISTS ecommerce_payment; CREATE DATABASE ecommerce_payment;"

# Product: chaos delay 2s 주입
WT=/Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase4/backend-v2
AGENT=/Users/leesanghun/My_Project/ecommerce-microservices/pinpoint-agent

java -javaagent:$AGENT/pinpoint-bootstrap.jar \
  -Dpinpoint.agentId=svc-product-phase4before \
  -Dpinpoint.applicationName=service-product-phase4 \
  -Dpinpoint.config=$AGENT/pinpoint-root.config \
  -Dprofiler.transport.grpc.collector.ip=localhost \
  -jar $WT/service-product/build/libs/service-product-*.jar \
  --spring.profiles.active=local \
  --app.chaos.enabled=true --app.chaos.stock-delay-ms=2000 \
  > /tmp/phase4-product.log 2>&1 &

# Order: CB 임계치 무효화로 Before 재현
java -javaagent:$AGENT/pinpoint-bootstrap.jar \
  -Dpinpoint.agentId=svc-order-phase4before \
  -Dpinpoint.applicationName=service-order-phase4-before \
  -Dpinpoint.config=$AGENT/pinpoint-root.config \
  -Dprofiler.transport.grpc.collector.ip=localhost \
  -jar $WT/service-order/build/libs/service-order-*.jar \
  --spring.profiles.active=local \
  --resilience4j.circuitbreaker.instances.productService.minimumNumberOfCalls=1000000 \
  --resilience4j.circuitbreaker.instances.productService.slidingWindowSize=1000000 \
  > /tmp/phase4-order-before.log 2>&1 &

# Customer / Payment 는 기본
./scripts/run-worktree-with-pinpoint.sh phase4 customer
./scripts/run-worktree-with-pinpoint.sh phase4 payment

# 기동 대기
sleep 30
docker exec -i ecommerce-mysql mysql -uroot -p1234 < scripts/seed-data.sql
docker exec ecommerce-mysql mysql -uroot -p1234 -e \
  "USE ecommerce_product; UPDATE product_variant SET stock_quantity = 100000 WHERE id IN (1,2,3,4,5);"

# k6 실행 — Before 측정
MAIN_DOCS=/Users/leesanghun/My_Project/ecommerce-microservices/docs
k6 run \
  --out web-dashboard=open=true,export=$MAIN_DOCS/phase-4-results/evidence/k6-report-before.html \
  k6/scripts/phase4-slow-product.js \
  2>&1 | tee $MAIN_DOCS/phase-4-results/evidence/before-slow-product.txt

# CB 상태 스냅샷 (Before — 결코 OPEN 되지 않음)
curl -s http://localhost:8082/actuator/circuitbreakers | jq '.'
```

#### Step B. After — phase4 default CB 설정

```bash
# Order 재기동 (이번엔 override 없이 기본 임계치)
pid=$(lsof -iTCP:8082 -sTCP:LISTEN | awk 'NR>1 {print $2}' | head -1); [ -n "$pid" ] && kill $pid
sleep 5

java -javaagent:$AGENT/pinpoint-bootstrap.jar \
  -Dpinpoint.agentId=svc-order-phase4after \
  -Dpinpoint.applicationName=service-order-phase4-after \
  -Dpinpoint.config=$AGENT/pinpoint-root.config \
  -Dprofiler.transport.grpc.collector.ip=localhost \
  -jar $WT/service-order/build/libs/service-order-*.jar \
  --spring.profiles.active=local \
  > /tmp/phase4-order-after.log 2>&1 &
sleep 20

# k6 실행 — After
k6 run \
  --out web-dashboard=open=true,export=$MAIN_DOCS/phase-4-results/evidence/k6-report-after.html \
  k6/scripts/phase4-slow-product.js \
  2>&1 | tee $MAIN_DOCS/phase-4-results/evidence/after-slow-product.txt

# CB 상태 스냅샷 (After — OPEN 전이)
curl -s http://localhost:8082/actuator/circuitbreakers | jq '.' \
  | tee $MAIN_DOCS/phase-4-results/evidence/cb-state-snapshots.txt
```

### 3. 확인 지표

| 지표 | 출처 | Before | After |
|---|---|---|---|
| `order_create_duration` p95 (k6) | k6 web-dashboard | ≥ 10 s (12.58s 실측) | < 100 ms (21.95ms 실측) |
| `http_req_failed` (k6) | web-dashboard | ≥ 50% (k6 client timeout) | ≥ 90% (CB 503 fast-fail, 의미 반전) |
| Pinpoint Thread Dump (Order) | `:8079` → Inspector → Thread | `http-nio-8082-exec-*` 스레드 대다수가 `RestClient` 호출 대기 | 대기 스레드 없음 (모두 idle) |
| `/actuator/circuitbreakers` → `productService.state` | Actuator | `CLOSED` (임계치 무효화됨) | **`OPEN`** |
| `productService.metrics.slowCallRate` | Actuator | 100% 지만 minimumNumberOfCalls 초과 전까지 OPEN 불가 | 100% 초과 → 즉시 OPEN |
| `productService.metrics.notPermittedCalls` | Actuator | 0 | **≥ 1000** |

### 4. 포트폴리오 증거 캡처

#### 🥇 대표 이미지: k6 Web Dashboard p95 시계열 Before/After 합성

두 `--out web-dashboard=export=...html` 결과물을 브라우저로 각각 열고 `http_req_duration` 패널만 잘라서 좌우 합성.

- 왼쪽: 12s 근처 flat bar → "모든 요청이 12초 걸림"
- 오른쪽: 22ms 근처 flat bar → "CB fast-fail"

저장: `docs/phase-4-results/evidence/k6-p95-compare.png`

> 💡 캡션: _"동일 부하(30 VUs × 30s)에 동일 chaos delay(2s). Circuit Breaker 적용으로 p95 = 12.58s → 21.95ms 로 573배 단축."_

#### 🥈 보조 이미지 1: Pinpoint Thread Dump — Before 스레드 포화

Pinpoint → `service-order-phase4-before` → Inspector 탭 → Thread Dump 스크린샷. 대부분의 Tomcat 스레드가 `RestTemplate.execute` / `URLConnection.connect` 같은 네트워크 대기 스택에 고정된 모습.

저장: `docs/phase-4-results/evidence/pinpoint-thread-saturation.png`

#### 🥉 보조 이미지 2: Actuator `/circuitbreakers` JSON 스크린샷

After 측정 중 다음 명령 결과 (JSON) 를 터미널이나 Postman 에서 캡처:

```bash
curl -s http://localhost:8082/actuator/circuitbreakers | jq '.circuitBreakers.productService'
```

```json
{
  "state": "OPEN",
  "metrics": {
    "failureRate": "0.0%",
    "slowCallRate": "100.0%",
    "notPermittedCalls": 1476,
    "bufferedCalls": 3,
    "slowCalls": 3,
    "failedCalls": 0
  }
}
```

저장: `docs/phase-4-results/evidence/actuator-circuitbreakers.png`

#### 🏅 보조 이미지 3: CB 상태 전이 그래프 (수동 재현)

Actuator 상태를 1초 간격으로 50회 폴링해 state 변화를 표로 기록:

```bash
for i in $(seq 1 50); do
  state=$(curl -s http://localhost:8082/actuator/circuitbreakers | jq -r '.circuitBreakers.productService.state')
  slow=$(curl -s http://localhost:8082/actuator/circuitbreakers | jq -r '.circuitBreakers.productService.metrics.slowCallRate')
  echo "$(date +%H:%M:%S) state=$state slowCallRate=$slow"
  sleep 1
done > $MAIN_DOCS/phase-4-results/evidence/cb-timeline.txt
```

이 결과를 Excel/Google Sheets 로 옮겨 state 가 `CLOSED → OPEN → HALF_OPEN → CLOSED` 로 전이되는 타임라인을 그림으로 그리면 포트폴리오에 효과적.

저장: `docs/phase-4-results/evidence/cb-timeline.png`

#### 포트폴리오 삽입 예시

```markdown
### Phase 4 — Resilience4j Circuit Breaker 로 thread pool 보호

Product internal 엔드포인트에 2초 지연을 주입한 뒤 30 VUs 부하:

![k6 p95 Before/After](phase-4-results/evidence/k6-p95-compare.png)

- order_create p95: **12.58s → 21.95ms** (573배)
- Actuator: `state=OPEN`, `slowCallRate=100%`, `notPermittedCalls=1,476`

![Actuator circuitbreakers](phase-4-results/evidence/actuator-circuitbreakers.png)

CB 가 열려있는 동안 Product 에 대한 호출은 서버에 도달하지 않고 fallback 에서 즉시 503 을 반환한다. Tomcat 스레드 풀이 free pool 을 회복해 `/actuator/health` 같은 무관한 경로도 정상 응답.
```

### 정리

```bash
for port in 8081 8082 8083 8084; do
  pid=$(lsof -iTCP:$port -sTCP:LISTEN -P -n 2>/dev/null | awk 'NR>1 {print $2}' | head -1)
  [ -n "$pid" ] && kill $pid
done
```

---

## 다음 단계 (Phase 5 로 이어짐)

Phase 1~4 의 각 개선은 개별 시나리오에서만 검증됐다. 통합된 운영 수준 부하에서 모든 resilience 패턴이 동시에 동작하는지 — 그리고 APM 으로 그 동작이 관측 가능한지 — 가 [Phase 5](../phase-5-results/README.md) 의 검증 목표.
