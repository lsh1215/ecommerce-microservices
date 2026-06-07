# DataDog APM 대시보드 매핑

이 문서는 DataDog에서 사용하던 `Bottleneck Detection` 대시보드 구조를 현재 프로젝트의 Grafana, Prometheus, Loki, Tempo, OpenTelemetry 기반 모니터링으로 옮길 때의 기준을 정리한다.

## 목표

DataDog 대시보드를 그대로 변환하지 않는다. 대신 다음 정보 구조를 가져온다.

| DataDog 섹션 | 현재 프로젝트 대시보드 |
| --- | --- |
| Overview | Service Fleet, Service Detail 상단 KPI |
| Request Performance | Service Detail의 요청량, 상태 코드, p50/p95/p99 |
| Endpoint-Level Bottleneck Detection | Service Detail의 endpoint p95/p99, error count, request volume |
| JVM Performance | Service Detail의 heap, GC, thread, CPU |
| Database / HikariCP | Service Detail의 Hikari active/pending/acquire/usage, DB span latency |
| Cache / Runtime Store | 현재 atomic baseline에는 별도 runtime store를 두지 않는다. 기본 서비스 대시보드는 API/JVM/Hikari/trace를 우선하고, 외부 저장소를 별도 도입할 때 전용 화면으로 분리한다. |
| Logs & Observability Gaps | Loki 로그, traceId 기반 Tempo 이동 |

## 메트릭 매핑

| DataDog metric | Grafana/LGTM 대응 |
| --- | --- |
| `trace.servlet.request.hits` | `http_server_requests_seconds_count`, `traces_spanmetrics_calls_total{span_kind="SPAN_KIND_SERVER"}` |
| `trace.servlet.request` p50/p95/p99 | `http_server_requests_seconds_*`, `traces_spanmetrics_latency_bucket` |
| `resource_name` | `uri`, `span_name`, 가능하면 `http.route` |
| `trace.postgresql.query` | OTel JDBC/MySQL span + `traces_spanmetrics_latency_bucket` |
| `jvm.heap_memory` | `jvm_memory_used_bytes{area="heap"}` |
| `jvm.gc.major_collection_time` | `jvm_gc_pause_seconds_sum/count` |
| `jvm.thread_count` | `jvm_threads_states_threads` |
| `jvm.cpu_load.process` | `process_cpu_usage` |
| `hikaricp.connections.active` | `hikaricp_connections_active` |
| `hikaricp.connections.pending` | `hikaricp_connections_pending` |
| `hikaricp.connections.acquire.avg` | `rate(hikaricp_connections_acquire_seconds_sum) / rate(hikaricp_connections_acquire_seconds_count)` |

## 구현 방침

- `Service Fleet`는 서비스 인벤토리와 전체 병목 위치를 보는 첫 화면이다.
- `Service Detail`은 `$service` 변수를 선택해서 특정 서비스의 상세 요청, JVM, Hikari, DB, Kafka, 로그를 본다.
- `Trace Drilldown`은 느린 span 후보를 보고 Tempo trace와 Loki 로그로 넘어가기 위한 화면이다.
- `db.statement`, 고객 ID, 주문 ID 같은 고카디널리티 값은 Prometheus label로 승격하지 않는다.
- CPU time, socket read, thread pending, 코드 경로 flame graph는 Grafana dashboard만으로 재현하지 않는다. 필요하면 Pyroscope를 별도 도입한다.
