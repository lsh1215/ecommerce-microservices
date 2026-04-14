# Phase 0 MVP Baseline — 부하 테스트 결과

## 테스트 환경
- 로컬 (MacOS)
- MySQL 8.0 + Kafka 3.8.1 (Docker Compose)
- 4개 서비스 `./gradlew bootRun` (local 프로파일)
- Seed data: 10 brands, 30 products, 90 variants, 20 customers
- k6 v0.x (로컬 설치)

---

## 1. 정상 상태 Baseline (4 서비스 전부 UP)

**k6 설정**: 1 VU, 10초, smoke 테스트
**스크립트**: `k6/scripts/order-flow.js` (상품 조회 → 상품 상세 → 주문 생성)

| 지표 | 값 |
|---|---|
| 주문 생성 성공률 | **100%** (10/10) |
| 에러율 | **0%** |
| http_req_duration avg | 30.34ms |
| http_req_duration p(90) | 57.64ms |
| http_req_duration p(95) | **95.38ms** |
| http_req_duration p(99) | 136.03ms |
| 처리량 | 0.92 iter/s |

결과 파일: `k6-normal-load.txt`

---

## 2. 장애 상태 — Payment 서비스 DOWN (Cascading Failure 증거)

**k6 설정**: 5 VUs, 30초
**시나리오**: Payment 서비스(8083) 중지 → 같은 order-flow 스크립트 실행

| 지표 | 정상 | 장애 (Payment DOWN) |
|---|---|---|
| 주문 생성 성공률 | 100% | **0%** (140건 전부 실패) |
| 전체 에러율 | 0% | **33.33%** (주문 API = 전체의 1/3) |
| http_req_duration p(95) | 95ms | 68ms (빠르게 에러 반환) |

**핵심 발견**: Payment 서비스가 DOWN이면 주문 생성이 100% 실패한다. Order 서비스가 Payment를 동기 호출하기 때문에, Payment 장애가 Order 서비스 기능 전체를 마비시킨다. 이것이 **Cascading Failure** — 동기 호출 결합의 대표적 문제.

결과 파일: `k6-cascading-failure.txt`

---

## Phase 0 → Phase 1 개선 포인트

이 baseline에서 확인된 문제:
1. **동기 결합**: Payment DOWN → Order 100% 실패 (cascading failure)
2. **장애 격리 없음**: 하나의 서비스 장애가 전체 주문 흐름을 차단

Phase 1 (Event-Driven + SAGA)에서:
- Order → Payment 통신을 Kafka 비동기 이벤트로 전환
- Payment DOWN이어도 Order는 PENDING 상태로 생성 가능
- Payment 복구 시 이벤트 재처리로 결제 완료

**목표**: 동일 장애 시나리오에서 주문 생성 성공률 0% → 100% (PENDING 상태)
