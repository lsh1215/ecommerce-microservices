# Phase 3 멱등성 — 검증 결과 및 남은 문제

## 테스트 환경
- 로컬, Phase 0/1/2와 동일 환경
- MySQL 8.0 + Kafka 3.8.1 (Docker Compose)
- 4개 서비스 local 프로파일

---

## Phase 3이 해결한 문제: 중복 이벤트 처리

### 테스트 시나리오

```
1. 주문 생성 → orderId=426, eventId=01KP7TQQ258BRJMNJNBPMP02QZ
2. 정상 결제 처리 확인: Payment 1건 COMPLETED
3. processed_event 테이블에 eventId 기록 확인
4. 같은 이벤트를 Kafka에 5번 수동 중복 발행
5. 5초 대기 후 확인
```

### 결과

| 검증 항목 | 기대 | 실제 | 결과 |
|---|---|---|---|
| Payment 건수 | 1건 | **1건** | ✅ 중복 결제 없음 |
| processed_event 건수 | 1건 | **1건** | ✅ 중복 기록 없음 |
| 멱등성 동작 | 중복 이벤트 skip | **5건 전부 skip** | ✅ |

### 멱등성 보장 메커니즘

```
이벤트 도착
  ↓
1차 체크: processedEventRepository.existsByEventId(eventId)
  → 이미 처리됨 → skip (로그: "중복 이벤트 감지, 건너뜀")
  → 미처리 → processor.run() 실행
  ↓
2차 체크: processedEventRepository.saveAndFlush(ProcessedEvent.of(...))
  → 성공 → 완료
  → DataIntegrityViolationException → 다른 인스턴스가 동시에 처리함 → 안전하게 skip
```

두 단계 보호:
1. **existsByEventId** — fast path (대부분의 중복을 여기서 차단)
2. **DB unique constraint** — 동시 접근 safety net (race condition 차단)

---

## Phase 2 → Phase 3 Before/After

| 지표 | Phase 2 (멱등성 없음) | Phase 3 (멱등성 적용) |
|---|---|---|
| 중복 이벤트 5건 발행 시 | 단일 인스턴스에서는 `existsByOrderIdAndStatus`로 방지 (다중 인스턴스에서 race 가능) | **eventId 기반 중복 체크 + DB unique constraint로 완전 차단** |
| Outbox 다중 폴링 시 | @Version은 있지만 발행 후 markPublished에서만 충돌 감지 | **OptimisticLockException catch → continue (skip)** |
| 보호 범위 | Order→Payment 경로만 (existsByOrderIdAndStatus) | **모든 Consumer 경로 (eventId 기반 범용)** |

---

## STAR 요약

| | |
|---|---|
| **S** | Phase 2 Outbox가 at-least-once 전달을 보장하지만, 같은 이벤트가 2번 이상 소비될 수 있음. 다중 인스턴스 환경에서 중복 결제/재고 차감 가능 |
| **T** | 모든 이벤트 소비를 exactly-once semantics로 처리 |
| **A** | processed_event 테이블 + IdempotentEventHandler (existsByEventId + DB unique constraint). OutboxPollingPublisher에 OptimisticLockException 처리 추가 |
| **R** | 동일 이벤트 5번 중복 발행 → Payment 1건만 생성, 중복 처리 0건 |

---

## 남은 문제 (Phase 4 Situation)

Phase 1~3에서 Order→Payment 비동기 경로의 신뢰성을 확보했다. 하지만 **동기 경로가 아직 남아있다**:

- Order → Product (재고 예약): **동기 RestClient 호출**
- Order → Customer (고객 검증): **동기 RestClient 호출**

Product 서비스가 느려지거나 다운되면 Order 서비스의 스레드 풀이 고갈되어 **전체 주문 서비스가 멈춘다**. 이건 Phase 0에서 해결했던 Payment cascading failure와 같은 유형의 문제이지만, 동기 호출 경로에서 발생한다.

**검증 방법**: Product 서비스에 인위적 지연(Thread.sleep) 추가 → k6 부하 테스트 → Order 서비스 응답 시간/에러율 측정 → Circuit Breaker 도입 (Phase 4)
