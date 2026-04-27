# Problem-Solution Evidence

이력서 4개 클레임을 GCE LGTM stack 위에서 재현한 **문제 발현 → 해결 검증** evidence 모음.

각 디렉토리는 동일한 시나리오/부하/장애 조건에서 **Before(problem) → After(solution)** 측정값을 raw text + Grafana 대시보드 캡처 + 분석 summary.md로 정리.

| 디렉토리 | 이력서 클레임 | Problem | Solution | 핵심 결과 |
|---|---|---|---|---|
| [`01-cascading-failure/`](./01-cascading-failure/) | #2 SAGA Orchestration | phase0 (sync RestClient) | phase1 (Kafka async + SAGA) | 주문 실패율 **33.3% → 0%**, 처리량 **9.2배** ↑ |
| [`02-outbox-pattern/`](./02-outbox-pattern/) | #1 Outbox / Dual Write | phase1 + Kafka 8s 다운 | phase2 + Kafka 3s 다운 | 이벤트 손실 **143건 → 0건**, outbox PUBLISHED 100% |
| [`03-idempotent-consumer/`](./03-idempotent-consumer/) | #3 Idempotent Consumer | guards OFF + 5 dup inject | guards ON + 5 dup inject | 결제 row **5건 → 1건** + 4 skip 로그 |
| [`04-circuit-breaker/`](./04-circuit-breaker/) | #4 Circuit Breaker | phase3 + chaos + Tomcat squeeze | phase4 + 동일 squeeze | order create **median 14.99s → 1.41s** (10배 단축), 처리량 1.6배 |

## 각 디렉토리 구조

```
NN-name/
  problem/
    k6-output.txt                       # k6 raw stdout
    db-vs-kafka.txt OR duplicate-injection.txt  # 측정 데이터
    timerange.txt                       # FROM/TO unix ms (Grafana URL용)
    dashboards/                         # 시점별 모니터링 캡처
      ecommerce-overview.png
      [관련 대시보드들].png
  solution/
    동일 구조
  summary.md                            # 문제 정의 / 해결 방법 / 비교표 / 검증 판정
```

## 인프라

- VM: GCE `ecommerce-k3s` (asia-northeast3-a, e2-standard-4)
- k3s 단일 노드, Traefik ingress, MySQL/Kafka StatefulSet
- 모니터링 (재배포 없이 phase 전반 재사용): Grafana + Prometheus + Loki + Tempo + Alloy + node-exporter + kube-state-metrics + kafka-exporter + mysqld-exporter
- Grafana: http://34.64.219.137/grafana/

## phase 전환 자동화

`./scripts/deploy-phase.sh phaseN` — phase 워크트리의 service 이미지 재빌드 + 클러스터 배포 (모니터링 stack은 그대로 두고 ecommerce ns만 갈아끼움).

`./scripts/verify-phase.sh phaseN` — 모니터링 helper (mysqld-exporter, kafka-exporter pin) 재정렬 + k6 + audit + 캡처 자동.

## 검증 결과 종합

4개 클레임 모두 **PASS**:

1. ✓ Cascading failure 재현 (phase0 100% fail) → SAGA async로 100% 회복 (phase1)
2. ✓ Dual Write 손실 입증 (143건) → Outbox로 0건 손실
3. ✓ 중복 처리 입증 (5건) → 멱등 consumer로 1건만 처리
4. ✓ Thread starvation 입증 (median 14.99s) → CB로 1.41s + thread 보호
