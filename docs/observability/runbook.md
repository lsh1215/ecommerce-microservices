# Observability Runbook

## Stack

| Component            | Role                                                     | Image                        |
| -------------------- | -------------------------------------------------------- | ---------------------------- |
| **Grafana Alloy**    | Single collector: OTLP receive + pod log scrape + Prometheus scrape + kafka exporter + servicegraph | `grafana/alloy:v1.4.2` (DaemonSet) |
| **Prometheus**       | Metrics TSDB + remote_write receiver (for k6 + Tempo generator) | `prom/prometheus:v2.54.1`    |
| **Loki**             | Log store (filesystem, `local-path` PVC)                | `grafana/loki:3.1.1`         |
| **Tempo**            | Trace store + `metrics_generator` (service graph, span metrics) | `grafana/tempo:2.6.0`        |
| **Grafana**          | UI, anonymous Admin, dashboard sidecar                  | `grafana/grafana:11.2.0`     |
| **mysqld-exporter**  | MySQL metrics                                           | `prom/mysqld-exporter:v0.15.1` |
| **OTel Java agent**  | Baked into each service image at `/app/otel/`           | `v2.20.1`                    |

## Entrypoint contract

Services start with the baked-in agent inert by default:

- `OTEL_EXPORTER_OTLP_ENDPOINT` unset → no `-javaagent`, zero overhead
- `OTEL_EXPORTER_OTLP_ENDPOINT` present → agent attached, telemetry flows

The activation switch is the `otel-config` ConfigMap:

- `k8s/monitoring/otel-config.yml`         empty stub (shipped everywhere)
- `k8s/monitoring/otel-config-active.yml`  populated overlay (applied only on clusters where Alloy is reachable)

Local flows such as IDE `./gradlew bootRun` or plain Docker Compose skip the active overlay, so services stay non-traced.

## Cluster URLs (GCE)

| Path                         | Value                                          |
| ---------------------------- | ---------------------------------------------- |
| API                          | `http://34.64.219.137/api/{products,brands,orders,payments,customers}` |
| Grafana (anonymous Admin)    | `http://34.64.219.137/grafana/`                |
| Prometheus remote-write (k6) | `http://34.64.219.137:30090/api/v1/write` (NodePort, firewalled to owner IP only) |

## Dashboards (auto-loaded by sidecar)

ConfigMaps labelled `grafana_dashboard: "1"` in the `monitoring` namespace are picked up on sidecar startup. Current set:

- **10939** — JVM / Spring Boot Micrometer
- **15661** — Kubernetes Pods overview
- **18941** — Kafka Exporter Overview (consumer lag + ISR)
- **7362**  — MySQL Overview
- **13639** — Loki Logs / App
- **19665** — k6 Prometheus remote_write

Apply dashboard ConfigMaps:

```bash
kubectl apply -f k8s/monitoring/dashboards/
```

## Running a k6 load test with live metrics

```bash
export K6_PROMETHEUS_RW_SERVER_URL="http://34.64.219.137:30090/api/v1/write"
export K6_PROMETHEUS_RW_TREND_STATS="avg,p(95),p(99)"
export K6_PROMETHEUS_RW_PUSH_INTERVAL=5s
export PRODUCT_API=http://34.64.219.137
export ORDER_API=http://34.64.219.137
export PAYMENT_API=http://34.64.219.137
export CUSTOMER_API=http://34.64.219.137

k6 run -o experimental-prometheus-rw k6/scenarios/smoke-test.js
```

Open **Dashboards → k6 Prometheus (19665)** in Grafana to watch VUs / iterations / p95 live.

## Cross-signal navigation

- **Metric → Trace**: exemplar on any Prometheus graph → opens the trace in Tempo.
- **Log → Trace**: Loki lines contain `traceId=<hex>` from the Logback pattern. Click the derived `TraceID` field → opens Tempo.
- **Trace → Log**: Tempo datasource has `tracesToLogsV2` pre-wired → click any span, "Logs for this span" button opens Loki filtered to that `service.name`.
- **Trace → Service Graph**: Tempo `serviceMap` reads `traces_service_graph_*` metrics the generator pushes to Prometheus.

## Common operations

### Tail logs for one request across all services

```bash
TRACE_ID=<hex_from_tempo>
# Grafana → Explore → Loki
{namespace="ecommerce"} |~ "traceId=${TRACE_ID}"
```

### Check Kafka consumer lag

Dashboards → Kafka Exporter Overview (18941). Filter `consumergroup` to `service-order`, `service-payment`, etc.

### Snapshot disk before risky changes

```bash
DISK=$(gcloud compute instances describe ecommerce-k3s --zone=asia-northeast3-a \
  --format='value(disks[0].source)' | awk -F'/' '{print $NF}')
gcloud compute disks snapshot "$DISK" --zone=asia-northeast3-a \
  --snapshot-names="pre-ops-$(date +%Y%m%d-%H%M)"
```

### Rollback the monitoring stack

```bash
kubectl delete namespace monitoring
kubectl -n ecommerce delete configmap otel-config  # removes activation
kubectl -n ecommerce rollout restart deploy -l 'app in (service-product,service-order,service-payment,service-customer)'
```

Services fall back to inert-agent mode (verified by absence of `-javaagent:` in `kubectl exec <pod> -- cat /proc/1/cmdline`).

## Resource sizing

Current target node: `e2-standard-4` (4 vCPU / 16 GiB RAM, asia-northeast3-a).

Baseline request usage (Prometheus `kubectl top node`): ~400 m CPU / ~5 GiB memory.

If you must run on e2-standard-2 (2 vCPU / 8 GiB), disable Prometheus+Loki PVC retention (drop to emptyDir), lower Tempo/Loki/Grafana memory limits to 256 Mi each, and skip the Node Exporter Full dashboard (annotation-size cap).

## Known gaps

- **Node Exporter Full (1860)** — 468 KiB JSON exceeds the 262 KiB `kubectl apply` last-applied-config annotation limit. Either use `kubectl apply --server-side` or slim the dashboard before re-adding.
- **Kafka JMX broker internals** — `prometheus.exporter.kafka` covers topic/partition/consumer-lag metrics only. For broker JVM metrics (UnderReplicatedPartitions, ActiveControllerCount) an initContainer-based JMX exporter on the Kafka StatefulSet is the accepted extension.
- **Traefik IPAllowList** — the plan called for a middleware restricting Grafana anonymous-Admin to owner IP. Not yet enforced in manifests; the GCE firewall on tcp:80 is still `0.0.0.0/0`. Tighten via a Traefik `Middleware` CRD before exposing to the public internet.
- **Phase worktree rollout** — the overnight run stopped after verifying `main`. phase0/phase5 deploy sweeps are deferred.
