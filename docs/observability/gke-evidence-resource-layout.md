# GKE Evidence 리소스 배치 기준

이 문서는 부하테스트 evidence를 수집할 때 사용하는 GKE 배치 기준을 정리한다. 목표는 머신 스펙을 과도하게 올리는 것이 아니라, 같은 하드웨어 급 안에서 WAS, DB, Kafka, 모니터링 부하가 서로의 결과를 오염시키지 않도록 역할을 분리하는 것이다.

## 노드 역할

| 역할 | 노드 수 | 머신 타입 | 배치 대상 |
|---|---:|---|---|
| Product/Order 서비스 | 2 | `e2-standard-2` | Product, Order WAS |
| Payment/Customer 서비스 | 2 | `n1-standard-1` | Payment, Customer WAS |
| DB | 1 | `e2-highmem-2` | mysql-product, mysql-order, mysql-payment, mysql-customer |
| Kafka | 1 | `n1-standard-1` | kafka-0 single broker |
| 모니터링/인프라 | 1 | `e2-highmem-2` | LGTM Stack, Traefik, k6 job, mysqld-exporter |

## 배치 이유

- 서비스 노드는 WAS만 담당하게 해서 JVM CPU 사용량과 DB CPU/IO 경합을 분리한다.
- DB는 shared DB 노드에 모아 비용을 과도하게 늘리지 않으면서 서비스 노드의 부하 오염을 줄인다.
- Kafka는 Outbox, Idempotency evidence의 핵심 관측 대상이므로 LGTM Stack과 같은 노드에서 분리한다.
- Traefik은 evidence 환경에서는 인프라 성격이 강하므로 모니터링 노드에 둔다. 별도 gateway 노드는 운영 고가용성 검증 단계에서 분리한다.

## 대표 리소스 기준

| 워크로드 | request | limit | 목적 |
|---|---:|---:|---|
| service-product | `600m / 768Mi` | `1500m / 1536Mi` | hot row 테스트에서 기존 `500m` CPU limit 병목 제거 |
| service-order | `600m / 768Mi` | `1500m / 1536Mi` | 주문 orchestration, trace/log 부하 반영 |
| service-payment | `400m / 512Mi` | `1000m / 1024Mi` | PG 즉시 결제 mock과 consumer 처리 반영 |
| service-customer | `300m / 512Mi` | `800m / 1024Mi` | 상대적으로 낮은 read/write 부하 기준 |
| mysql-product/order | `350m / 768Mi` | `1000m / 1536Mi` | 2 vCPU shared DB 노드에서 네 DB가 함께 뜨도록 request 조정 |
| mysql-payment/customer | `200m / 512Mi` | `700m / 1024Mi` | 상대적으로 낮은 write path 기준 |
| kafka | `500m / 1024Mi` | `1500m / 2048Mi` | 단일 broker evidence 환경 안정화 |

## evidence 수집 시 확인할 것

- `kubectl get nodes -L role`에서 `monitoring`, `db`, `kafka`, `svc-*` 역할이 모두 보여야 한다.
- `kubectl get pods -A -o wide`에서 WAS, DB, Kafka, LGTM/Traefik/k6/exporter가 의도한 노드에 있어야 한다.
- Grafana 대시보드에 query error, 의도하지 않은 No data, NaN이 없어야 한다.
- k6 로그의 p95, p99, throughput, error rate와 대시보드 수치가 같은 시간대에서 일치해야 한다.
