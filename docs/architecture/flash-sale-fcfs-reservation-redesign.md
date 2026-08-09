# 선착순 예매 시스템 재설계: Redis 장애에도 멈추지 않는 공정한 재고 예약

> 상태: **설계 논의 초안 (미구현)**. 다른 세션에서 이 문서를 놓고 토론을 이어가기 위한 정리본.
> 선행 설계: [`hot-row-redis-reservation-store.md`](./hot-row-redis-reservation-store.md), [`kafka-delivery-contract.md`](./kafka-delivery-contract.md)
> 대상 스택: 현재 인프라 그대로 — MSA 4개(order/product/payment/customer) + Kafka + Redis + MySQL. 새 미들웨어 추가 없음.

---

## 1. 목적과 배경

이 레포는 hot row 병목을 Atomic Update → Redis 예약으로 풀며 **처리량**을 개선해 왔다(136 → 649/s, 약 4.8배). 하지만 예약 경로가 Redis에 의존하면서 **Redis가 SPOF**가 됐다. Redis가 죽으면 예약 접수 자체가 멈춘다.

이 문서는 처리량 개선을 넘어 **선착순 예매가 만족해야 할 요구사항을 처음부터 다시 세우고**, 그 요구사항(특히 "Redis 장애에도 fail-open + 동일 부하")을 만족하는 구조를 설계한다.

> 참고: "선착순 예매 요구사항으로 시스템을 설계"한 선례는 **다른 레포(peak-booking-system)** 이야기이고, 이 레포에서는 지금까지 Redis로 처리량 개선만 했다. 이 문서는 그 요구사항을 **이 레포**에 새로 이식·재설계하는 것이다.

---

## 2. 요구사항

1. **재고 정합성 + 공정성** — 한정 재고에 대해 **오버셀 0 + 언더셀 0**, 트래픽이 몰려도 완벽 보장. 선착순(먼저 도착이 먼저 확보), 모든 사용자 동등 기회.
2. **고가용성 + 부하** — 평시 ~50 TPS, 오픈 순간 1~5분간 500~1000 TPS 급증 흡수. 새 미들웨어 없이 현재 스택 안에서.
3. **멱등성** — 짧은 간격 연속 결제/예약 요청 중복 처리 방지.
4. **Redis 장애 내성 (핵심)** — Redis가 죽어도 서비스가 멈추지 않고 **동일 수준 부하**를 견딘다. **fail-closed 금지.**
5. **수평 확장 (stateless)** — 앱 서버 2대+ 분산, 요청을 어느 서버가 받아도 같은 결과.

**Non-goals(이 설계 범위 밖):** 결제 수단 확장성/복합결제, 실제 PG 연동(mock만), 회원 인증.

---

## 3. 현재 상태와 갭 (코드 근거)

| 요구 | 현재 구현 | 근거 파일 | 갭 |
|---|---|---|---|
| 오버셀 0 | confirm 시 DB `decreaseStock WHERE stock>=qty` backstop (Redis 용량검사는 advisory) | `ProductService.java` L151-153 | ✅ |
| 언더셀 0 | payment 실패 시 SAGA release | — | ⚠️ **버려진/타임아웃 예약 TTL·reaper 없음** |
| 멱등성 | Lua `HGET orderId` dedup + DB unique | `RedisStockReservationStore` RESERVE_SCRIPT | ✅ |
| 공정성 | Redis Lua 원자 예약 = "먼저 Lua 실행한 요청이 이김" | 동상 | ⚠️ 도착순 아닌 **경합순** |
| Redis 장애 fail-open + 동일 부하 | 어드미션 게이트 `admission.fail-open:false`(429), async reserve는 Redis 커넥션 장애 시 폴백 없이 예외 | `AdmissionRateLimiter.java` L104-108, `ProductService.reserveStock` | ❌ **fail-closed. 핵심 미충족** |
| stateless | API 무상태, 카운터는 공유 Redis | — | ✅ 대체로 |
| Redis HA | `replicas: 1`, Sentinel/replica 없음, AOF everysec | `k8s/base/redis-product-statefulset.yml` L20 | ❌ 단일 인스턴스 SPOF |

**핵심 문제:** Redis가 (a) 빠른 예약 카운터 + (b) 어드미션 게이트를 동시에 쥔 SPOF. 단순 fail-open 전환 시 DB 단일 row 폴백(~135/s)으로 떨어져 500~1000 TPS를 못 견딘다. → **"Redis 다운에도 동일 부하"와 정면 충돌.**

---

## 4. 대안 분석

| 대안 | 공정성 | Redis 장애 시 동일 부하 | API | 비고 |
|---|---|---|---|---|
| A. Redis HA(Sentinel/replica) 단독 | 경합순 | ❌ failover blip, 여전히 Redis 의존 | sync | 방어층으로만 |
| B. 현 Redis 빠른경로 + DB 폴백(예외 catch) | 경합순 | ❌ 폴백이 단일 row 병목(~135/s) | sync | 최소 변경, 요구 미충족 |
| C. DB를 SoT로(`SKIP LOCKED`/재고 샤딩) + Redis 가속 | 근사(샤드국소) | ✅ DB 경로가 스케일 | **sync** | Shopify 노선, 언더셀=크로스샤드 steal 필요 |
| **D. Kafka 로그 직렬화 + DB backstop + Redis 결과캐시** | **엄밀(로그 offset)** | ✅ **Redis가 결정 경로 밖** | **async(티켓/bounded-wait)** | 기존 Kafka 재사용, 정석 선착순 |

**외부 근거(리서치):**
- Shopify(2026): Redis 예약을 MySQL(ACID + `SKIP LOCKED`)로 교체해 스케일. DB를 SoT로 두면 실패 모드가 사라짐. https://shopify.engineering/scaling-inventory-reservations
- Fault-tolerant 설계: 표시는 fail-open, 실제 예약은 DB 트랜잭션 보증 + Redis 자동 failover 캐시.
- 한국 선착순 정석: "앞단 Redis 줄세우기 + 뒷단 Kafka 처리량", 이탈/만료 시 슬롯 재할당(언더셀 방지). https://rhcwlq89.github.io/blog/fcfs-queue-implementation/
- Kafka: same-key→same-partition = 키별 엄밀 순서(공정성), idempotent producer로 중복 제거.

---

## 5. 권장 설계 (D 중심 + C의 DB backstop)

### 5.1 핵심 발상
**Redis를 부하의 주경로에서 빼 "가속기"로 강등한다.** 그러면 "Redis 장애에도 동일 부하"가 자동 충족된다 — 애초에 Redis가 부하를 지고 있지 않으니까. 예약 **결정의 권위**는 Redis가 아니라 **Kafka 순서 + 소비자 인메모리 카운터 + DB**에 둔다.

### 5.2 역할 재배치
| 계층 | 역할 | 죽으면 |
|---|---|---|
| **MySQL** | intake 내구성 앵커 + 재고 SoT + 오버셀 backstop(`decreaseStock WHERE stock>=qty`) | 최후 보루(HA 대상) |
| **Kafka** | 순서(공정성, key=variantId)·전송·스파이크 버퍼 | 주문은 outbox로 DB에 남고 복구 시 발행 → **유실 0** |
| **Redis** | ① 재고 표시 스냅샷 ② 예약 결과 캐시(폴링) ③ (선택) 조기 거절 | 조회·폴링만 DB 폴백 → **fail-open** |
| **소비자(파티션당 단일 writer)** | 인메모리 잔여 카운터로 로그순 용량 판정 | 리밸런스 시 다른 인스턴스가 DB에서 재적재 |

### 5.3 예약 흐름 (async)
```
Client → Order 트랜잭션 1개:  INSERT order(PENDING) + INSERT outbox(ReservationRequested)  → commit → "접수됨" 반환
         (commit 후 즉시 nudge 발행; @Scheduled 폴러는 크래시/Kafka다운 백업)
outbox → Kafka (key=variantId, idempotent producer)
Kafka → Product 소비자(로그순 단일 writer): 인메모리 용량검사
         → 성공: DB stock_reservation(RESERVED, TTL) 커밋 (+ Redis 결과캐시)  ← 유저 대기 종료 지점
         → 마감: 빠른 거절
Client: 결과 폴링/SSE → Redis(빠름, 없으면 DB) 읽어 확정/마감 표시
결제성공 → CONFIRMED + DB 조건부 차감(오버셀 backstop)
결제실패/타임아웃 → TTL 만료 → reaper ReservationExpired → 용량 반환(언더셀 방지)
```

### 5.4 요구사항 충족 방식
- **공정성**: Kafka 파티션 offset 순 = "빠른 서버가 이김" 제거, 엄밀한 도착순.
- **오버셀 0**: 단일 writer + 결제 시 DB 조건부 차감 backstop.
- **언더셀 0**: 예약 TTL + reaper 슬롯 재할당.
- **멱등성**: idempotent producer + orderId dedup + DB unique(`DataIntegrityViolation` 멱등 처리 기존 존재).
- **수평확장**: API/producer 완전 무상태. 소비자 카운터는 로그+DB로 재구성.
- **Redis 장애**: 결정 경로에 Redis 없음 → 조회·폴링만 느려짐(DB 폴백), 예약 결정/부하 처리 무영향.

---

## 6. 유저 플로우 (UX)

```
① 조회      재고 스냅샷 표시. 재고>0 → [주문] 활성 / =0 → 회색(막힘)
② 주문클릭   Order PENDING 생성 → "주문 접수, 예약 확인 중…"
③ 확인중     (보통 수십~수백 ms) 소비자가 로그순 판정
④ 결과      확정 → "예약 완료, 결제 진행" / 마감 → "마감" + 주문 자동취소
```
- **"조회 땐 있었는데 주문하니 마감"**은 async 탓이 아니라 **선착순의 본질**(sync도 동일). 차이는 통지 시점뿐 → PENDING 자동취소+통지로 처리.
- 상태모델: `PENDING(예약확인중) → RESERVED/CONFIRMED → 결제` / `PENDING → CANCELLED(마감 자동취소)`

### UX 두 가지 (미결정)
- **(a) 대기표 노출**: 티켓 + SSE/폴링("n번째"). 진짜 티켓팅 UX.
- **(b) bounded-wait(동기처럼 보이는 비동기)**: produce 후 결과를 짧게(예: 300ms) 기다렸다 한 응답. 대부분 즉시 응답, 초과 시만 대기표 전환. **현재 sync 체감 유지.**

---

## 7. 지연(latency) 분석

**확정시간 ≈ (outbox→Kafka 발행지연) + (Kafka 전송+소비자 pull) + (Product 예약로직+DB commit) + (클라 결과 읽기)**

| 항 | 크기 |
|---|---|
| outbox→Kafka 발행지연 | **순수 폴링(현재 `@Scheduled(fixedDelay=500)`) = 0~500ms(avg~250)** / **nudge = 수 ms** |
| Kafka 전송 + 소비자 pull | ~수 ms (소비자 상시 pull) |
| Product 예약로직 + DB commit | ~수~수십 ms (`stock_reservation` INSERT는 단일 row 경합 아님) |
| 클라 결과 읽기 | 폴링 1회/SSE |

- **핵심 튜닝**: 발행을 폴링에 맡기지 말고 **commit 후 nudge, 폴러는 백업** → 지배항 제거 → 확정 체감 수십 ms.
- **편도 1번만**: 유저는 Product가 커밋한 결과(DB/Redis)를 **직접 폴링**. `Product→Order` 되돌아오는 `ReservationConfirmed`(SAGA 상태 동기화)는 백그라운드 → 유저 대기에 미포함(왕복 2배 방지).
- 부하 시: 단일 상품은 단일 writer라 뒤 요청이 큐 대기(선착순 본질). 단 재고 0 이후는 무거운 작업 없는 "빠른 거절".

---

## 8. 내구성 분석 (Kafka 장애)

- **직접 produce는 위험**: DB write 없이 Kafka로만 쏘면 Kafka 다운 시 intake 유실 → SPOF를 Redis→Kafka로 옮긴 셈.
- **그래서 intake=outbox**: `order(PENDING)` + `outbox` 한 트랜잭션. **유저 "접수"는 Kafka ack가 아니라 DB commit에 묶임.** Kafka 다운이어도 DB에 남고 복구 시 발행 → 유실 0.
- **지연은 nudge로 해결**(§7). "내구성은 outbox, 저지연은 nudge" — 둘 다 잡는다.
- 트레이드오프: 여러 Order 인스턴스가 동시 발행 시 `createdAt`↔Kafka offset 사이 미세 재정렬 가능. 선착순 수준에선 허용; 밀리초 엄밀 순서가 필요하면 별도 조임.

---

## 9. Redis 다운 플로우 (fail-open by construction)

```
state = Redis DOWN
① 조회      캐시 미스 → DB에서 재고 읽어 표시            (조금 느림, 됨)
② 주문      outbox→Kafka 그대로                          (Redis 안 탐)
③ 결정      소비자 인메모리 + DB로 판정                   (Redis 안 탐) → 동일 부하 유지, backstop 그대로
④ 결과확인   결과캐시 미스 → DB 예약행 직접 폴링           (조금 느림, 됨)
⑤ 조기거절   스냅샷 없어 스킵 → 모든 요청 소비자 도달, 마감이면 빠른 거절 (부하 약간↑, 감당)
```
**결론: Redis 다운 = "예약이 멈추는 사건"이 아니라 "조회/폴링이 DB로 느려지는 사건".** 이게 되려면 예약 결정 권위가 처음부터 Redis 밖(Kafka+DB)에 있어야 함.

---

## 10. 정직한 비용 / 트레이드오프

- Redis를 카운터에서 빼면 **평상시** 예약도 동기 Redis Lua(~1ms) 대신 Kafka+소비자(비동기 수십 ms). → "Redis 독립성 + 엄밀 공정성"의 대가. sync/async 결정과 직결.
- 소비자는 파티션당 단일 writer라 "완전 무상태"는 아님(상태는 Kafka-managed·DB로 재구성 가능). API/producer 계층은 완전 무상태.
- Redis HA(replica 1개 + Sentinel)는 **방어층(defense-in-depth)**으로 별도 검토 — 필수는 아님(Redis가 이미 결정 경로 밖).

---

## 11. 미결정 사항 (다음 세션 토론용)

1. **API 형태**: (a) 대기표 노출 vs (b) bounded-wait — C/D 최종 선택을 가름.
2. **C vs D**: sync 유지가 중요하면 C(DB SoT + SKIP LOCKED/샤딩 + Redis 가속), 엄밀 공정성 + Redis 완전분리면 D(Kafka 직렬화).
3. **소비자 카운터 재구성** 상세: 파티션 리밸런스/재시작 시 DB 재적재 vs compacted 토픽 스냅샷.
4. **언더셀 reaper**: TTL 값, 만료 이벤트, 용량 반환 경로.
5. **조기 거절(early-reject)** 상세: Redis 스냅샷 신선도, 오탐 허용범위.
6. **공정성 엄밀도**: outbox `createdAt`↔offset 재정렬 허용 범위.
7. **Redis HA** 도입 여부(방어층).
8. **구현 착수 승인 + 브랜치/워크트리**: `flash-sale-async-settle` 기반 새 브랜치, 필요한 커밋만 push.

---

## 12. 참고 링크
- Shopify — Redis→MySQL(SKIP LOCKED) 예약: https://shopify.engineering/scaling-inventory-reservations
- 선착순 대기열(Redis+Kafka) 구현: https://rhcwlq89.github.io/blog/fcfs-queue-implementation/
- 선착순 6가지 방식 비교: https://rhcwlq89.github.io/blog/fcfs-system-comparison-guide/
- Kafka 순서·내구성 보장: https://developer.confluent.io/courses/architecture/guarantees/
