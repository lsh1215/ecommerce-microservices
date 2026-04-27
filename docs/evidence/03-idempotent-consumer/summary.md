# Unit 03 — Idempotent Consumer (이력서 클레임 #3: 중복 이벤트 차단)

## 문제 정의

Kafka는 본질적으로 **at-least-once** 전달:
- Producer 재시도: ack timeout 후 같은 이벤트 재전송 (Outbox poller의 `retry_count`도 해당)
- Consumer rebalance: offset commit 전 재시작 → 동일 메시지 재소비
- 다중 consumer group: 같은 토픽 구독 시 각 그룹이 독립 수신

장애 시나리오:
- 같은 `order.created` 이벤트가 5번 전달됨 → 멱등 가드 없으면 5건의 결제 row 생성
- **이중 결제, 과다 재고 차감, SAGA 상태 머신 오작동**

## 해결 방법

**`processed_event` UNIQUE 테이블 + `IdempotentEventHandler` 래퍼**:

```java
@Transactional
public boolean tryProcess(String eventId, String eventType, Runnable processor) {
    if (!idempotencyEnabled) {                          // 토글 OFF: 그냥 실행
        log.warn("idempotency disabled");
        processor.run();
        return true;
    }
    if (processedEventRepository.existsByEventId(eventId)) {
        log.info("중복 이벤트 감지, 건너뜀: eventId={}", eventId);
        return false;                                   // 1차 방어: pre-check
    }
    processor.run();
    try {
        processedEventRepository.saveAndFlush(ProcessedEvent.of(eventId, eventType));
    } catch (DataIntegrityViolationException e) {
        // 2차 방어: 동시 처리 시 DB UNIQUE 제약이 차단 → @Transactional 롤백
    }
    return true;
}
```

**2-Layer 방어**:
1. **사전 조회 (`existsByEventId`)** — 일반적인 중복 차단
2. **DB UNIQUE 제약** — 두 인스턴스가 동시에 commit 시도 시 한 쪽이 `DataIntegrityViolationException` → `@Transactional` 롤백 → `payment.save(...)`도 함께 취소

`PaymentService.processFromEvent`에는 추가로 `existsByOrderIdAndStatus(orderId, COMPLETED)` 비즈니스 가드. 두 가드 모두 토글 가능 (`application.idempotency.enabled`, `application.business-idempotency-guard.enabled`).

## 테스트 시나리오

| 항목 | Problem (guards OFF) | Solution (guards ON, default) |
|---|---|---|
| 동작 | 같은 eventId의 `order.created` 5번 inject | 동일 |
| 환경 | phase3 + main의 toggle 코드 cherry-pick + env var false | 동일 + env var true |
| 측정 | Payment 테이블 row 수, processed_event 레코드, 로그 |

> Note: phase3 worktree에는 toggle 코드가 없어서 main에서 cherry-pick 후 service-order/service-payment 이미지 재빌드. phase3-multi-consumer-test 하니스(docker-compose 기반)와 본질적으로 같은 의도지만 단일 consumer 단순화 버전.

## 결과 요약

| 지표 | Problem (guards OFF) | Solution (guards ON) |
|---|---|---|
| 5 dup `order.created` 주입 | ✓ | ✓ |
| **Payment 테이블 row 수** | **5건** (중복 결제) | **1건** ✓ |
| processed_event 항목 | (체크 안 함, 가드 OFF) | **1 (eventId 기록)** |
| Skip 로그 | (없음, 모두 처리) | `중복 이벤트 감지, 건너뜀` × **4회** |
| `idempotency disabled` 로그 | × **5회** (토글 적용 확인) | (없음) |

→ **5번 주입 / 1번 처리 = exactly-once 효과** 입증

## Evidence

| | 측정 raw | Grafana 화면 |
|---|---|---|
| Problem | [`problem/duplicate-injection.txt`](./problem/duplicate-injection.txt) | [`problem/dashboards/`](./problem/dashboards/) — overview, logs-app |
| Solution | [`solution/duplicate-injection.txt`](./solution/duplicate-injection.txt) | [`solution/dashboards/`](./solution/dashboards/) |

## 모니터링 대시보드 핵심 panel

- **Logs / App** — service-payment 로그에서 `중복 이벤트 감지, 건너뜀` 메시지 4회 (Solution) vs `idempotency disabled` 5회 (Problem)
- **Ecommerce Overview** — Recent service logs 패널에서 동일 정보 일부 노출

## 검증 결과 — **PASS**

- Problem: guards OFF로 5 row 생성 ✓ (가드 비활성 시 중복 처리 입증)
- Solution: 1 row + 4 skip ✓ (이력서 "동일 이벤트 5회 중복 주입 → 결제 1건만 정상 처리" 정확히 재현)

## 재현 명령

```bash
# Cherry-pick toggle code (phase3 worktree에 없으므로)
cp backend-v2/common/src/main/java/com/ecommerce/common/idempotency/IdempotentEventHandler.java \
   ../ecommerce-microservices-worktrees/phase3/backend-v2/common/src/main/java/com/ecommerce/common/idempotency/
cp backend-v2/service-payment/src/main/java/com/ecommerce/payment/application/service/PaymentService.java \
   ../ecommerce-microservices-worktrees/phase3/backend-v2/service-payment/src/main/java/com/ecommerce/payment/application/service/

./scripts/deploy-phase.sh phase3
# 이미지 재빌드 (no-cache 권장 — gradle 캐시 회피)
cd ../ecommerce-microservices-worktrees/phase3
docker buildx build --no-cache --build-arg SERVICE_NAME=service-payment -t ecommerce/service-payment:latest --load backend-v2/

# Problem
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a -- \
  'sudo kubectl -n ecommerce set env deploy/service-payment APPLICATION_IDEMPOTENCY_ENABLED=false APPLICATION_BUSINESS_IDEMPOTENCY_GUARD_ENABLED=false'
# Inject 5x same eventId via kafka-console-producer

# Solution — flip env vars to true, repeat
```
