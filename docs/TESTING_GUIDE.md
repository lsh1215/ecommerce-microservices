# Testing Guide — 포트폴리오용 증거 캡처 표준

각 Phase 전이(Before → After)를 **포트폴리오·블로그에 이미지로 첨부 가능한** 수준의 증거로 남기기 위한 통합 가이드. 터미널 로그 대신 Pinpoint APM · k6 웹 대시보드 · DB 스냅샷 · Actuator 엔드포인트 등 **시각 자료 중심**으로 증거를 확보한다.

---

## 0. 왜 이 문서를 따라야 하는가

기존 증거 (`docs/phase-*-results/*.txt`, `evidence/*.txt`) 는 k6 CLI 출력을 raw 텍스트로 덤프한 것이다. 이는 **재현성**과 **수치의 객관성**을 보증하지만, 면접관이나 블로그 독자가 한눈에 이해하기 어렵다. 포트폴리오용 이미지 소스로는 부적합하다.

아래 도구는 같은 이벤트·같은 수치를 **그림으로** 보여준다.

| 도구 | 무엇을 시각화하는가 | 어디서 보는가 | 캡처 추천 |
|------|---------------------|---------------|----------|
| **Pinpoint APM** | 서비스 간 호출 토폴로지, 응답 시간 히트맵, 개별 트랜잭션 call tree (Kafka publish/consume 포함) | `http://localhost:8079` | Service Map 스크린샷 · Call Tree 스크린샷 |
| **k6 web dashboard** | 부하 테스트 실시간 시계열 (VU, p95, error rate) | `k6 run --out web-dashboard` 실행 직후 브라우저 자동 오픈 | 전체 대시보드 스크린샷 |
| **k6 HTML report** | 부하 테스트 종료 후 정적 리포트 | `k6 run --out web-dashboard=export=report.html` → `report.html` | HTML 보고서 전체 스크린샷 |
| **Actuator 엔드포인트** | Circuit Breaker 상태, Thread Pool, Kafka offset lag | `http://localhost:8082/actuator/{circuitbreakers,metrics,health}` | JSON 파싱 결과를 마크다운 표로 정리 + 스크린샷 |
| **DB 스냅샷** | `outbox_event`, `payment`, `processed_event` 테이블의 전/후 상태 | MySQL CLI 또는 DBeaver 등 GUI | MySQL 결과 테이블 스크린샷 |
| **Kafka CLI describe** | Consumer group offset lag, topic partition 상태 | `docker exec ecommerce-kafka kafka-consumer-groups.sh --describe` | 명령 출력 스크린샷 |

---

## 1. 사전 준비 (one-time setup)

### 1.1 인프라 기동

```bash
cd /Users/leesanghun/My_Project/ecommerce-microservices

# MySQL :3307 + Kafka :9092
docker compose -f infra/docker-compose.yml up -d mysql kafka

# Pinpoint (HBase + Collector + Web UI :8079)
docker compose -f monitoring/docker-compose.pinpoint.yml up -d

# HBase 초기화 대기 (최초 1분 정도 소요)
sleep 60

# Pinpoint Agent 다운로드 (최초 1회)
./scripts/setup-pinpoint-agent.sh
```

기동 확인:

| 엔드포인트 | 기대 응답 |
|------------|-----------|
| `http://localhost:3307` (MySQL 접속) | 쿼리 성공 |
| `docker ps --filter name=kafka` | `Up (healthy)` |
| `http://localhost:8079` (Pinpoint Web) | Pinpoint 대시보드 열림 |

### 1.2 Worktree 맵 확인

각 Phase의 코드 스냅샷은 별도 worktree에 고정되어 있다. 위치와 SHA 는 [`docs/worktree-map.md`](./worktree-map.md) 참조.

| Worktree | 용도 |
|---|---|
| `phase0` (`274c26d`) | 동기 호출만 있는 MVP (Before) |
| `phase1` (`22b8d1f`) | Event-Driven SAGA 도입 (After) |
| `phase2` (`eedeaa3`) | Transactional Outbox 도입 |
| `phase3` (`9ba1b98`) | 멱등성 인프라 |
| `phase4` (`4a9849f`) | Circuit Breaker |
| `phase5` (`e791aa5`) | 통합 부하 검증 |

### 1.3 k6 Web Dashboard 활성화

k6 v0.49+ 는 내장 웹 대시보드를 지원한다.

```bash
# 실행 중 실시간 대시보드 (브라우저 자동 오픈)
k6 run --out web-dashboard=open=true k6/scripts/cascading-failure.js

# 정적 HTML 보고서 출력 (종료 후 캡처용)
k6 run --out web-dashboard=export=./k6-report.html k6/scripts/cascading-failure.js
```

포트폴리오에는 **HTML 보고서**를 추천. 실행 완료 후 `.html` 파일을 브라우저로 열고 전체 페이지를 스크린샷.

---

## 2. 공통 실행 패턴

모든 Phase 테스트는 아래 5단계 패턴을 따른다.

```
[1] Worktree 선택 + Pinpoint agent 장착한 채로 서비스 기동
     ↓
[2] 시드 데이터 + 재고 리셋 (누적 상태 제거)
     ↓
[3] 테스트 시나리오 실행 (k6 부하 / 수동 POST / 장애 주입)
     ↓
[4] 증거 수집 4종 (Pinpoint 캡처 / k6 report / DB 쿼리 / Actuator)
     ↓
[5] 서비스 정리 + 다음 Phase 준비
```

### 2.1 Worktree + Pinpoint 동시 사용 (중요)

Phase 0/1 worktree 에는 Pinpoint agent 설정이 없지만 **agent 는 JVM 옵션만 추가하면 되므로 worktree 코드 변경 없이 주입 가능**. 구체 절차는 [`docs/PINPOINT_RETROFIT.md`](./PINPOINT_RETROFIT.md) 참조.

요약:

```bash
cd /Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase0
WT=/Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase0/backend-v2
AGENT=/Users/leesanghun/My_Project/ecommerce-microservices/pinpoint-agent

for svc in product order payment customer; do
  java \
    -javaagent:${AGENT}/pinpoint-bootstrap.jar \
    -Dpinpoint.agentId=${svc}-phase0 \
    -Dpinpoint.applicationName=service-${svc} \
    -Dpinpoint.config=${AGENT}/pinpoint-root.config \
    -Dprofiler.transport.grpc.collector.ip=localhost \
    -jar $WT/service-${svc}/build/libs/service-${svc}-0.0.1-SNAPSHOT.jar \
    --spring.profiles.active=local \
    > /tmp/phase0-${svc}.log 2>&1 &
done
```

Pinpoint Web UI(`http://localhost:8079`) 에서 각 서비스가 등록되는 것을 확인 후 테스트 시작.

### 2.2 증거 파일 저장 규칙

**반드시 메인 레포 `docs/phase-*/evidence/` 밑에 저장**한다 (worktree 내부 금지).

```bash
MAIN_DOCS=/Users/leesanghun/My_Project/ecommerce-microservices/docs

# 예: k6 HTML 보고서
k6 run --out web-dashboard=export=$MAIN_DOCS/phase-N-*/evidence/k6-report.html script.js

# 예: Pinpoint 스크린샷
# 브라우저에서 캡처 후 저장: $MAIN_DOCS/phase-N-*/evidence/pinpoint-servicemap.png
```

---

## 3. 포트폴리오 증거 품질 기준

블로그나 이력서에 첨부할 때 **이 3가지 요소가 한 장에 나타나야** 한다.

1. **맥락 (Context)** — 어떤 시나리오인가. 예: "Payment 서비스 다운 시 주문 생성 시도"
2. **수치 (Metric)** — 객관적 숫자. 예: "p95=12.58s → 21.95ms"
3. **메커니즘 (Mechanism)** — 왜 그렇게 되는가의 시각적 힌트. 예: Pinpoint 의 붉은색 slow call bar, Circuit Breaker OPEN 상태 배지, Outbox 테이블의 PENDING→PUBLISHED 전이

### 추천 스크린샷 레이아웃

| 위치 | Phase 0→1 | Phase 1→2 | Phase 2→3 | Phase 3→4 |
|---|---|---|---|---|
| **대표 이미지** | Pinpoint Service Map (Payment 빨간색 slow call) | DB 쿼리: outbox_event 테이블 PENDING→PUBLISHED | DB 쿼리: payment 중복 카운트 (0 vs N) | Actuator `/circuitbreakers` OPEN 상태 JSON |
| **보조 이미지 1** | k6 web dashboard (error rate 100% → 0%) | Pinpoint call tree: async Kafka publish 노드 | Pinpoint service map: 2 Payment 인스턴스에 동시 consume | Pinpoint Thread Pool 포화 그래프 |
| **보조 이미지 2** | Order log: `order.created 이벤트 발행 실패` | OutboxPollingPublisher 로그 (retry_count 증가) | `processed_event` UNIQUE violation 로그 | k6 web dashboard p95 시계열 |

---

## 4. 각 Phase 테스트 문서

| Phase | 링크 | 테스트 종류 |
|---|---|---|
| 0 — MVP Baseline | [`phase-0-baseline/README.md`](./phase-0-baseline/README.md) | smoke + chaos (Payment DOWN) |
| 1 — Event-Driven SAGA | [`phase-1-results/README.md`](./phase-1-results/README.md) | chaos (Payment DOWN) + SAGA 상태 전이 검증 |
| 2 — Transactional Outbox | [`phase-2-results/README.md`](./phase-2-results/README.md) | Kafka 장애 주입 + Outbox 재발행 검증 |
| 3 — Idempotent Consumer | [`phase-3-results/README.md`](./phase-3-results/README.md) | Multi-consumer-group race 재현 |
| 4 — Circuit Breaker | [`phase-4-results/README.md`](./phase-4-results/README.md) | Slow dependency chaos + CB 상태 전이 |
| 5 — Load + APM | [`phase-5-results/README.md`](./phase-5-results/README.md) | 3단계 부하 (smoke/load/stress) 통합 검증 |

---

## 5. 테스트 종료 시 정리 (cleanup)

```bash
# 서비스 종료
for port in 8081 8082 8083 8084 8183; do
  pid=$(lsof -iTCP:$port -sTCP:LISTEN -P -n 2>/dev/null | awk 'NR>1 {print $2}' | head -1)
  [ -n "$pid" ] && kill $pid
done

# DB 초기화 (다음 Phase 준비)
docker exec ecommerce-mysql mysql -uroot -p1234 -e "
  DROP DATABASE IF EXISTS ecommerce_order;
  DROP DATABASE IF EXISTS ecommerce_product;
  DROP DATABASE IF EXISTS ecommerce_payment;
  DROP DATABASE IF EXISTS ecommerce_customer;
  CREATE DATABASE ecommerce_order;
  CREATE DATABASE ecommerce_product;
  CREATE DATABASE ecommerce_payment;
  CREATE DATABASE ecommerce_customer;"

# Kafka 토픽 리셋 (선택적 — consumer offset 초기화가 필요한 경우)
docker exec ecommerce-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic 'order\.created|payment\.completed|payment\.failed|order\.cancelled'
```

전체 환경 종료 시:

```bash
docker compose -f monitoring/docker-compose.pinpoint.yml down
docker compose -f infra/docker-compose.yml down
```

---

## 6. 트러블슈팅

| 증상 | 원인 | 해결 |
|---|---|---|
| Pinpoint Web 에 서비스가 안 보임 | HBase 초기화 미완료 / Collector 미기동 | `docker compose -f monitoring/docker-compose.pinpoint.yml logs pinpoint-collector` 확인 후 60초 대기 |
| k6 error `connection refused` | Order 서비스 `:8082` 미기동 | `curl http://localhost:8082/actuator/health` 로 확인 후 재기동 |
| Stock reservation failed for variant 1 | Seed data variant 1 재고 0 | `UPDATE product_variant SET stock_quantity=100000 WHERE id IN (1..5);` |
| Phase 0/1 에서 outbox_event 테이블이 있음 | 이전 Phase 2 실행 잔여 스키마 (ddl-auto=update) | `DROP DATABASE ecommerce_order;` 로 초기화 |
| Kafka consumer lag 계속 증가 | 이전 테스트의 누적 이벤트 | 위 cleanup 섹션의 `kafka-topics --delete` 실행 후 재시작 |

---

## 7. 자주 묻는 질문 (FAQ)

**Q. 왜 worktree 가 필요한가? main 에서 직접 Before/After 측정하면 안 되나?**
A. Before 상태를 main 에서 재현하려면 Phase 4 의 Circuit Breaker, Phase 2 의 Outbox 등을 "끄는" 코드 변경이 필요하다. Worktree 는 각 Phase 가 실제로 merge 된 시점의 소스를 그대로 보존하므로 **이력 정확성**이 보장된다. Phase 3 처럼 toggle 로 on/off 가능한 경우에만 main 에서 직접 테스트한다.

**Q. Phase 0/1 worktree 는 Pinpoint 설정이 없는데 어떻게 모니터링하나?**
A. Pinpoint agent 는 bytecode instrumentation 기반이므로 **서비스 코드 변경 없이 JVM 옵션만 추가하면 동작**한다. [`PINPOINT_RETROFIT.md`](./PINPOINT_RETROFIT.md) 참조.

**Q. k6 HTML 보고서는 어디에 저장하나?**
A. `docs/phase-*/evidence/k6-*.html` 에 저장 후 git 에 커밋한다. 크기가 크면 (보통 500KB 이하) 그대로 커밋하고, 필요하면 `.gitattributes` 로 `*.html binary` 지정.

**Q. 포트폴리오에 스크린샷 몇 장이 적절한가?**
A. Phase 당 **2~3 장**. 대표 이미지 1장 + 보조 이미지 1~2장. Phase 5 는 1~2장 (smoke/load/stress 중 stress 대표).
