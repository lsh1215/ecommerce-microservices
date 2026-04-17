# Phase 1 — Event-Driven SAGA (Cascading Failure 제거)

- **Worktree**: `/Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase1` (`22b8d1f`)
- **Evidence**: [`evidence/k6-cascading-failure.txt`](./evidence/k6-cascading-failure.txt)
- **Before**: [Phase 0](../phase-0-baseline/README.md)

## 문제 정의 (Problem)

[Phase 0 cascading failure 실측](../phase-0-baseline/README.md) 에서 확인된 문제:
- Order 는 Payment / Product / Customer 를 **동기 RestClient** 로 호출
- Payment 만 다운돼도 Order 성공률 0%, 전체 에러율 33%

근본 원인: **가용성 곱셈 결합**. 동기 결합을 끊지 않으면 어떤 retry/timeout 튜닝도 미봉책.

## 해결 방법 (Solution)

**Order ↔ Payment 경로를 Kafka 이벤트로 비동기화** (Orchestration SAGA).

| 경로 | Before | After |
|---|---|---|
| Order → Payment | 동기 RestClient | `order.created` Kafka publish (fire-and-forget async) |
| Payment → Order (결과 통보) | 함수 반환값 | `payment.completed` / `payment.failed` Kafka event |
| Order → Product (재고 예약) | 동기 RestClient | 동기 유지 (강한 일관성 필요) — [Phase 4](../phase-4-results/README.md) 에서 Circuit Breaker 로 보호 |
| Order → Customer (프로필 조회) | 동기 RestClient | 동기 유지 — Phase 4 에서 CB |

### 구현 포인트

| 파일 | 역할 |
|---|---|
| `backend-v2/service-order/src/main/java/com/ecommerce/order/application/service/OrderSagaOrchestrator.java` | SAGA 상태 머신 (`startSaga`, `onPaymentCompleted`, `onPaymentFailed`, `compensate`) |
| `backend-v2/service-order/src/main/java/com/ecommerce/order/infra/kafka/producer/OrderEventProducer.java` | `kafkaTemplate.send(...).whenComplete()` — fire-and-forget async publish |
| `backend-v2/service-order/src/main/java/com/ecommerce/order/domain/model/SagaInstance.java` | 분산 트랜잭션 상태 추적 엔티티 (`ORDER_CREATED → PAYMENT_PROCESSING → COMPLETED / COMPENSATED / FAILED`) |

## Before / After 핵심 수치

| 시나리오 | Phase 0 (Before) | Phase 1 (After) |
|---|---|---|
| Payment DOWN 시 주문 POST 성공률 | **0%** (3625 요청 실패) | **100%** (3738 요청 성공) |
| http_req_failed | 100.00% | **0.00%** |
| 응답 본문 | `500 Payment service unavailable` | `201 status=PENDING` |
| p95 | 70 ms | 42 ms |

수치 근거: [`evidence/k6-cascading-failure.txt`](./evidence/k6-cascading-failure.txt).

---

## 🧪 Testing Guide

공통 설정은 [`docs/TESTING_GUIDE.md`](../TESTING_GUIDE.md) 참조.

### 1. 테스트 종류

| 항목 | 내용 |
|---|---|
| **유형** | Chaos Engineering (Payment DOWN) + 부하 테스트 + SAGA 상태 전이 검증 |
| **부하 생성기** | k6 (`k6/scripts/cascading-failure.js`, 20 VUs × 60s) — Phase 0 과 동일 스크립트 |
| **검증 포인트** | (a) HTTP 성공률 100% (b) 주문 status=PENDING 으로 저장 (c) Payment 복구 후 자동 PAID 전이 |

### 2. 실행 방법

#### Step A. Payment DOWN 시 주문 생성 성공 증명 (핵심)

```bash
cd /Users/leesanghun/My_Project/ecommerce-microservices

# Phase 1 worktree bootJar 준비
(cd /Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase1/backend-v2 \
  && ./gradlew bootJar -x test -q)

# DB 초기화
docker exec ecommerce-mysql mysql -uroot -p1234 -e "
  DROP DATABASE IF EXISTS ecommerce_order;
  DROP DATABASE IF EXISTS ecommerce_product;
  DROP DATABASE IF EXISTS ecommerce_customer;
  DROP DATABASE IF EXISTS ecommerce_payment;
  CREATE DATABASE ecommerce_order;
  CREATE DATABASE ecommerce_product;
  CREATE DATABASE ecommerce_customer;
  CREATE DATABASE ecommerce_payment;"

# Payment 제외한 3개 서비스 기동 (Pinpoint agent 부착)
./scripts/run-worktree-with-pinpoint.sh phase1 product
./scripts/run-worktree-with-pinpoint.sh phase1 order
./scripts/run-worktree-with-pinpoint.sh phase1 customer

# 시드 + 재고
docker exec -i ecommerce-mysql mysql -uroot -p1234 < scripts/seed-data.sql
docker exec ecommerce-mysql mysql -uroot -p1234 -e \
  "USE ecommerce_product; UPDATE product_variant SET stock_quantity = 100000 WHERE id IN (1,2,3,4,5);"

# k6 실행 — Payment DOWN 상태로 부하
MAIN_DOCS=/Users/leesanghun/My_Project/ecommerce-microservices/docs
k6 run \
  --out web-dashboard=open=true,export=$MAIN_DOCS/phase-1-results/evidence/k6-report.html \
  k6/scripts/cascading-failure.js \
  2>&1 | tee $MAIN_DOCS/phase-1-results/evidence/k6-cascading-failure.txt
```

#### Step B. SAGA 복구 흐름 증명 (Payment 복구 시 자동 전이)

위 부하 테스트 직후, 남아있는 PENDING 주문들을 Payment 복구로 완결시키는 과정 관측.

```bash
# Payment 기동 (지금까지 비어있던 :8083)
./scripts/run-worktree-with-pinpoint.sh phase1 payment

# Payment 기동 로그에서 이벤트 소비 확인
sleep 15
grep -E 'order.created 이벤트 수신|Payment COMPLETED' \
  /Users/leesanghun/My_Project/ecommerce-microservices/build/phase1-logs/payment.log \
  | head -20

# 주문 상태 전이 확인
docker exec ecommerce-mysql mysql -uroot -p1234 -e "
  SELECT status, COUNT(*) AS n FROM ecommerce_order.orders GROUP BY status;
  SELECT status, COUNT(*) AS n FROM ecommerce_payment.payment GROUP BY status;
  SELECT state, COUNT(*) AS n FROM ecommerce_order.saga_instance GROUP BY state;"
```

### 3. 확인 지표

| 지표 | 출처 | 기대값 (Payment DOWN) | 기대값 (Payment 복구 후) |
|---|---|---|---|
| `http_req_failed` (k6) | web-dashboard | **0.00%** | n/a (기존 주문 조회) |
| `http_req_duration` p95 (k6) | web-dashboard | ~42 ms | n/a |
| Order `status` 분포 (DB) | MySQL | **PENDING = 3738** | 전부 `PAID` 로 전이 |
| Payment `status` 분포 (DB) | MySQL | (Payment 미기동이므로 행 없음) | **COMPLETED ≈ 3738** |
| SagaInstance `state` 분포 | MySQL | **PAYMENT_PROCESSING = 3738** | **COMPLETED = 3738** |
| Pinpoint Server Map (`service-order-phase1`) | `:8079` | Order → Kafka 토픽 에지 정상 | Kafka → Payment 에지 추가 |

### 4. 포트폴리오 증거 캡처

#### 🥇 대표 이미지 A: Phase 0 vs Phase 1 Pinpoint Server Map 나란히

`service-order-phase0` 과 `service-order-phase1` 을 **같은 시간창** 에서 캡처해 좌우로 붙이면 가장 설득력 있다. Phase 0 화살표는 빨강, Phase 1 은 Kafka 경유로 우회.

저장: `docs/phase-1-results/evidence/pinpoint-servermap-compare.png`

> 💡 캡션: _"동기 결합(Phase 0)을 Kafka 이벤트로 끊어낸 결과, Payment 장애가 더 이상 Order 로 전파되지 않는다."_

#### 🥈 보조 이미지 1: k6 Web Dashboard (Before/After)

Phase 0 실행과 Phase 1 실행의 `k6-report.html` 두 개를 브라우저로 열어 **error rate 패널만 트리밍** 하여 한 장에 나란히 합성.

- Phase 0: 100% 에러 (평평한 빨강)
- Phase 1: 0% 에러 (평평한 초록)

저장: `docs/phase-1-results/evidence/k6-dashboard-compare.png`

#### 🥉 보조 이미지 2: DB 스냅샷 테이블 — SAGA 상태 전이

MySQL CLI 또는 DBeaver 에서 아래 쿼리를 실행한 결과 테이블을 스크린샷.

```sql
-- Payment 복구 전
SELECT o.id, o.status AS order_status,
       s.state AS saga_state,
       (SELECT COUNT(*) FROM ecommerce_payment.payment p WHERE p.order_id = o.id) AS payments
FROM ecommerce_order.orders o
LEFT JOIN ecommerce_order.saga_instance s ON s.order_id = o.id
ORDER BY o.id DESC
LIMIT 10;

-- Payment 복구 후 같은 쿼리 → status / saga_state 가 전부 전이됨
```

저장: `docs/phase-1-results/evidence/saga-state-before-after.png`

#### 🏅 보조 이미지 3: Pinpoint Transaction Call Tree (Kafka publish 성공)

Pinpoint → Transaction List → `POST /api/orders` 중 하나 클릭 → Call Tree 에서 `kafkaTemplate.send` 노드가 **성공 (초록 배지)** 으로 끝나는 캡처.

저장: `docs/phase-1-results/evidence/pinpoint-calltree-kafka-publish.png`

#### 포트폴리오 삽입 예시

```markdown
### Phase 1 — Event-Driven SAGA 로 장애 격리 달성

Order 서비스가 Payment 를 Kafka 이벤트로 비동기 호출하도록 재설계. Payment 다운 시에도 주문은 **PENDING 으로 즉시 생성**되고, Payment 복구 후 자동으로 PAID 로 전이된다.

![Pinpoint Server Map — Phase 0 vs Phase 1](phase-1-results/evidence/pinpoint-servermap-compare.png)

- 동일 부하 조건 (20 VUs × 60s, Payment 미기동): 실패율 100% → **0%**
- 주문 3738 건 전부 `status=PENDING` 저장, SAGA state = `PAYMENT_PROCESSING`
- Payment 기동 15초 뒤 모든 주문이 `PAID` 로 자동 전이
```

### 정리

```bash
for port in 8081 8082 8083 8084; do
  pid=$(lsof -iTCP:$port -sTCP:LISTEN -P -n 2>/dev/null | awk 'NR>1 {print $2}' | head -1)
  [ -n "$pid" ] && kill $pid
done
```
