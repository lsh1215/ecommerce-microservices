# Phase 0 — MVP Baseline (Cascading Failure 재현)

이 디렉토리는 **Phase 0 의 역할이 "정상 기능 확인"이 아니라 "의도적으로 깨진 구조의 실측 기록"임**을 보여주기 위한 문서와 증거를 담는다. Phase 1 이후의 Before/After 비교의 "Before" 좌표다.

- **Worktree**: `/Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase0` (`274c26d`)
- **Evidence**: [`evidence/k6-cascading-failure.txt`](./evidence/k6-cascading-failure.txt)

## 문제 정의 (Problem)

Phase 0 의 Order Service 는 Payment / Product / Customer 를 **모두 동기 RestClient 로 호출**한다. 이 구조에서 downstream 한 곳만 죽어도 Order 가 동시에 무너진다 — cascading failure. 단일 서비스 가용성이 각 downstream 가용성의 **곱셈적 결합**이 되는 교과서적 anti-pattern.

> 가용성(Order) = 가용성(Payment) × 가용성(Product) × 가용성(Customer)

Payment 가 99% 가용해도 전체는 96% 이하로 떨어진다.

## 해결 방법 (Solution)

**없음.** Phase 0 은 MVP — 이 취약성을 실측으로 남기기 위한 시작점이다. 해결은 [Phase 1](../phase-1-results/README.md) 에서.

## Before / After 핵심 수치

| 시나리오 | Phase 0 (Before) | Phase 1 (After) |
|---|---|---|
| Payment DOWN 시 주문 POST 성공률 | **0%** (3625 요청 전부 실패) | 100% (3738 요청 전부 성공) |
| http_req_failed | **100.00%** | 0.00% |
| 평균 응답 | 31.5 ms (빠르게 fail) | 20.9 ms (비동기 ACK) |
| p95 | 70 ms | 42 ms |

수치 근거: [`evidence/k6-cascading-failure.txt`](./evidence/k6-cascading-failure.txt) · commit SHA 헤더 참고.

---

## 🧪 Testing Guide

먼저 [`docs/TESTING_GUIDE.md`](../TESTING_GUIDE.md) 의 공통 설정 섹션을 완료해야 한다 (Docker / MySQL / Kafka / Pinpoint 기동, agent 다운로드).

### 1. 테스트 종류

| 항목 | 내용 |
|---|---|
| **유형** | Chaos Engineering + 부하 테스트 |
| **방식** | Payment 서비스를 의도적으로 미기동 상태로 두고 Order 에 부하 주입 |
| **부하 생성기** | k6 (`k6/scripts/cascading-failure.js`, 20 VUs × 60s) |
| **장애 주입 방법** | Payment 서비스 바이너리를 기동하지 않음 (포트 8083 비어있음) |
| **가설** | "동기 RestClient 호출 구조는 단일 downstream 다운에 100% 실패율로 반응한다" |

### 2. 실행 방법

```bash
# --- 준비 ---
cd /Users/leesanghun/My_Project/ecommerce-microservices

# Phase 0 worktree 의 bootJar 가 없다면 빌드
(cd /Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase0/backend-v2 \
  && ./gradlew bootJar -x test -q)

# DB 초기화 (이전 Phase 잔여 스키마 제거)
docker exec ecommerce-mysql mysql -uroot -p1234 -e "
  DROP DATABASE IF EXISTS ecommerce_order;
  DROP DATABASE IF EXISTS ecommerce_product;
  DROP DATABASE IF EXISTS ecommerce_customer;
  DROP DATABASE IF EXISTS ecommerce_payment;
  CREATE DATABASE ecommerce_order;
  CREATE DATABASE ecommerce_product;
  CREATE DATABASE ecommerce_customer;
  CREATE DATABASE ecommerce_payment;"

# --- 서비스 기동 (Payment 제외) ---
./scripts/run-worktree-with-pinpoint.sh phase0 product
./scripts/run-worktree-with-pinpoint.sh phase0 order
./scripts/run-worktree-with-pinpoint.sh phase0 customer
# Payment 는 일부러 띄우지 않는다 (cascading failure 재현용)

# 헬스 확인 — payment 만 미기동 상태여야 한다
curl -s http://localhost:8081/actuator/health -w "\n" # product
curl -s http://localhost:8082/actuator/health -w "\n" # order
curl -s http://localhost:8084/actuator/health -w "\n" # customer
curl -s http://localhost:8083/actuator/health -o /dev/null -w "payment:%{http_code}\n"   # Connection refused → 000

# 시드 데이터 + 재고 확보 (variant 1~5)
docker exec -i ecommerce-mysql mysql -uroot -p1234 < scripts/seed-data.sql
docker exec ecommerce-mysql mysql -uroot -p1234 -e \
  "USE ecommerce_product; UPDATE product_variant SET stock_quantity = 100000 WHERE id IN (1,2,3,4,5);"

# --- k6 실행 (실시간 웹 대시보드 + HTML 보고서 생성) ---
MAIN_DOCS=/Users/leesanghun/My_Project/ecommerce-microservices/docs
k6 run \
  --out web-dashboard=open=true,export=$MAIN_DOCS/phase-0-baseline/evidence/k6-report.html \
  k6/scripts/cascading-failure.js \
  2>&1 | tee $MAIN_DOCS/phase-0-baseline/evidence/k6-cascading-failure.txt
```

### 3. 확인 지표

테스트 실행 중/후 아래 지표를 관찰한다.

| 지표 | 출처 | 기대값 | 의미 |
|---|---|---|---|
| `http_req_failed` | k6 CLI / web-dashboard | **100%** | 모든 POST 가 5xx/timeout — cascading failure 재현 성공 |
| `http_req_duration` p95 | k6 | ~70 ms | 빠르게 실패 (Payment 미기동이므로 connection refused 즉시 반환) |
| Order 응답 본문 샘플 | 수동 `curl` | `{"success":false,"message":"Payment service unavailable: I/O error ..."}` | 5xx 원인 확인 |
| Pinpoint Server Map (`service-order-phase0`) | `http://localhost:8079` | **Order → Payment 호출 에지가 빨간색 / 에러 카운트** | 문제를 시각적으로 증명 |
| Pinpoint Inspector Response Time | Pinpoint | error bar 가 그래프의 100% 를 차지 | 시계열 증거 |

### 4. 포트폴리오 증거 캡처

블로그·이력서에 첨부할 이미지를 아래 우선순위로 캡처한다.

#### 🥇 대표 이미지: Pinpoint Server Map — 에러 토폴로지

Pinpoint Web UI (`http://localhost:8079`) → Application 드롭다운에서 `service-order-phase0` 선택 → **Server Map** 탭.
**Order 에서 Payment 로 향하는 화살표가 붉은색 + Error count 100%** 로 표시된 순간을 캡처.

> 💡 캡션 예시: _"Payment 서비스 장애 시 Order 의 sync RestClient 호출이 실패하며 100% 에러율. 장애 영역이 Order 까지 전파됨."_

저장 경로: `docs/phase-0-baseline/evidence/pinpoint-servermap.png`

#### 🥈 보조 이미지 1: k6 Web Dashboard

`--out web-dashboard=open=true` 로 실행된 브라우저 창의 테스트 종료 직전 (3625 iterations 소화 직후) 전체 페이지 스크린샷. **error rate panel 이 100% 로 평평한 라인**.

저장 경로: `docs/phase-0-baseline/evidence/k6-dashboard.png`

#### 🥉 보조 이미지 2: Order Service 로그 — 5xx 응답 body

```bash
grep -E 'Payment service unavailable|PAYMENT_PROCESSING_FAILED' \
  /Users/leesanghun/My_Project/ecommerce-microservices/build/phase0-logs/order.log \
  | head -10
```

터미널 출력 3~5 줄을 스크린샷.

저장 경로: `docs/phase-0-baseline/evidence/order-error-log.png`

#### 포트폴리오 삽입 예시 (마크다운)

```markdown
### Phase 0 — MVP 의 cascading failure 실측

동기 RestClient 로만 엮인 MVP 에서 Payment 하나만 떨어져도 **주문 생성 전체가 마비**된다.

![Pinpoint Server Map](phase-0-baseline/evidence/pinpoint-servermap.png)

- 20 VUs × 60s, 3625 요청 전부 실패 (`http_req_failed: 100%`)
- 응답 본문: `Payment service unavailable: I/O error on POST request for ... Connection refused`
- 가용성 곱셈 결합이 실제로 작동함을 실측.
```

### 정리 (cleanup)

```bash
for port in 8081 8082 8083 8084; do
  pid=$(lsof -iTCP:$port -sTCP:LISTEN -P -n 2>/dev/null | awk 'NR>1 {print $2}' | head -1)
  [ -n "$pid" ] && kill $pid
done
```
