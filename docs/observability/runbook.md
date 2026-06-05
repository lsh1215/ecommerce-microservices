# Observability 운영 런북

## 스택

| 컴포넌트 | 역할 | 이미지 |
| --- | --- | --- |
| **Grafana Alloy** | OTLP 수신, pod 로그 수집, Prometheus scrape, kafka exporter, service graph 수집 | `grafana/alloy:v1.4.2` |
| **Prometheus** | Metrics TSDB, k6와 Tempo metrics generator용 remote-write receiver | `prom/prometheus:v2.54.1` |
| **Loki** | 파일시스템 기반 로그 저장소 | `grafana/loki:3.1.1` |
| **Tempo** | Trace 저장소, service graph와 span metrics 생성 | `grafana/tempo:2.6.0` |
| **Grafana** | UI, anonymous Admin, dashboard sidecar | `grafana/grafana:11.2.0` |
| **mysqld-exporter** | MySQL 지표 수집 | `prom/mysqld-exporter:v0.15.1` |
| **OTel Java agent** | 각 서비스 이미지의 `/app/otel/`에 포함된 Java agent | `v2.20.1` |

## 진입점 계약

서비스 이미지는 OTel Java agent를 포함하지만 기본 상태에서는 비활성이다.

- `OTEL_EXPORTER_OTLP_ENDPOINT`가 없으면 `-javaagent`를 붙이지 않는다.
- `OTEL_EXPORTER_OTLP_ENDPOINT`가 있으면 agent를 붙이고 telemetry를 Alloy로 보낸다.

활성화는 `otel-config` ConfigMap으로 제어한다.

- `k8s/monitoring/otel-config.yml`: 빈 stub. 모든 환경에 배포 가능하다.
- `k8s/monitoring/otel-config-active.yml`: Alloy가 있는 클러스터에서만 적용하는 활성 overlay이다.

IDE `./gradlew bootRun`이나 일반 Docker Compose 실행은 active overlay를 적용하지 않으므로 tracing overhead가 없다.

## 클러스터 접근 URL

배포 환경마다 ingress IP나 도메인이 달라지므로 문서에는 고정 IP를 남기지 않는다.

| 경로 | 값 |
| --- | --- |
| API | `http://<ingress-ip-or-domain>/api/{products,brands,orders,payments,customers}` |
| Grafana | `http://<ingress-ip-or-domain>/grafana/` |
| Prometheus remote-write | `http://<node-or-lb-ip>:30090/api/v1/write` |

## 대시보드

`monitoring` namespace에서 `grafana_dashboard: "1"` label이 붙은 ConfigMap은 Grafana sidecar가 자동으로 읽는다. 현재 Kubernetes 매니페스트에 포함된 대시보드는 다음과 같다.

| 대시보드 | 목적 |
| --- | --- |
| Ecommerce Service Fleet | 서비스별 상태, RPS, error rate, p95/p99를 한 화면에서 비교 |
| Ecommerce Service Detail | 선택한 서비스의 endpoint, JVM, HikariCP, DB span, 로그 상세 확인 |
| Ecommerce Gateway | Traefik entrypoint 기준 성공률, 상태 코드, 경로별 처리량 확인 |
| Ecommerce Trace Drilldown | 느린 span 후보를 보고 Tempo trace와 Loki 로그로 이동 |
| Ecommerce Traces | 서비스 간 호출 관계와 trace 기반 병목 확인 |
| Ecommerce Operations Overview | 주문/결제 운영 흐름, DB/Kafka/서비스 상태 요약 |
| Ecommerce Load Test / k6 | k6 테스트의 RPS, VU, p95/p99, 실패율 확인 |
| Ecommerce JVM | JVM memory, GC, thread, CPU 확인 |
| Ecommerce MySQL per DB | 서비스별 MySQL 상태 확인 |
| Ecommerce Outbox | outbox publish와 이벤트 흐름 확인 |
| Ecommerce Idempotency Flow | 중복 이벤트 수신과 idempotency 차단 흐름 확인 |
| Ecommerce Dualwrite Flow | DB 저장과 Kafka 이벤트 발행 간 흐름 확인 |
| Ecommerce Circuit Breaker | product client 장애, circuit breaker 상태, fast-fail 확인 |

대시보드 ConfigMap 적용:

```bash
kubectl apply -f k8s/monitoring/dashboards/
```

## k6 부하테스트와 실시간 지표

```bash
export BASE_URL="http://<ingress-ip-or-domain>"
export K6_PROMETHEUS_RW_SERVER_URL="http://<node-or-lb-ip>:30090/api/v1/write"
export K6_PROMETHEUS_RW_TREND_STATS="avg,p(95),p(99)"
export K6_PROMETHEUS_RW_PUSH_INTERVAL=5s
export PRODUCT_API="$BASE_URL"
export ORDER_API="$BASE_URL"
export PAYMENT_API="$BASE_URL"
export CUSTOMER_API="$BASE_URL"

k6 run -o experimental-prometheus-rw k6/scenarios/smoke-test.js
```

Grafana에서는 **Dashboards → Ecommerce Load Test / k6**로 들어가 VU, iteration, p95/p99, 실패율을 확인한다.

## 신호 간 이동

- **Metric → Trace**: Prometheus graph의 exemplar를 눌러 Tempo trace로 이동한다.
- **Log → Trace**: Loki 로그의 `traceId=<hex>` derived field를 눌러 Tempo trace로 이동한다.
- **Trace → Log**: Tempo datasource의 `tracesToLogsV2` 설정으로 span에서 해당 서비스 로그를 연다.
- **Trace → Service Graph**: Tempo metrics generator가 Prometheus로 보낸 `traces_service_graph_*` 지표를 사용한다.

## 자주 쓰는 작업

### 단일 요청 로그 추적

```bash
TRACE_ID=<tempo에서_확인한_trace_id>
# Grafana → Explore → Loki
{namespace="ecommerce"} |~ "traceId=${TRACE_ID}"
```

### Kafka consumer lag 확인

Grafana의 Kafka 관련 패널에서 `consumergroup`을 `service-order`, `service-payment` 등으로 필터링한다.

### 위험 작업 전 디스크 스냅샷

```bash
DISK=$(gcloud compute instances describe <instance-name> --zone=<zone> \
  --format='value(disks[0].source)' | awk -F'/' '{print $NF}')
gcloud compute disks snapshot "$DISK" --zone=<zone> \
  --snapshot-names="pre-ops-$(date +%Y%m%d-%H%M)"
```

### 모니터링 스택 롤백

```bash
kubectl delete namespace monitoring
kubectl -n ecommerce delete configmap otel-config
kubectl -n ecommerce rollout restart deploy -l 'app in (service-product,service-order,service-payment,service-customer)'
```

서비스는 inert-agent 모드로 돌아간다. 필요하면 `kubectl exec <pod> -- cat /proc/1/cmdline`에서 `-javaagent:`가 없는지 확인한다.

## 리소스 기준

권장 모니터링 노드는 `e2-standard-4` 수준이다.

| 리소스 | 기준 |
| --- | --- |
| CPU | 4 vCPU |
| Memory | 16 GiB |
| 용도 | Prometheus, Grafana, Loki, Tempo, Alloy 수집/저장 |

`e2-standard-2` 수준에서 실행해야 한다면 Prometheus/Loki retention을 줄이고, Tempo/Loki/Grafana memory limit을 낮추며, 고카디널리티 대시보드는 제외한다.

## 알려진 한계

- **Node Exporter Full (1860)**: JSON이 커서 `kubectl apply`의 last-applied-config annotation 제한을 넘을 수 있다. 필요하면 server-side apply 또는 slim dashboard를 사용한다.
- **Kafka broker JVM 내부 지표**: 현재 kafka exporter는 topic/partition/consumer lag 중심이다. UnderReplicatedPartitions, ActiveControllerCount 같은 broker JVM 지표는 Kafka StatefulSet에 JMX exporter를 추가해야 한다.
- **Grafana anonymous Admin 외부 노출**: 공개 인터넷에 노출할 경우 Traefik middleware 또는 GCP firewall로 접근 대역을 제한해야 한다.
- **DataDog 수준의 code-path drilldown**: CPU time, socket read, thread pending, 코드 경로 flame graph는 Grafana/Tempo dashboard만으로 완전히 대체되지 않는다. 필요하면 Pyroscope 같은 continuous profiler를 추가한다.
