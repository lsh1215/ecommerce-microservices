"""Generates the custom Ecommerce Overview dashboard. Every query has been
verified against the live Prometheus/Loki on the cluster as of this
commit. Metric labels captured during verification:

- jvm_memory_used_bytes       labels: {app, pod, namespace, area, id, instance:IP:port}   (pod-scrape)
- http_server_request_*        labels: {job='ecommerce/<svc>', http_route, http_request_method, http_response_status_code}  (OTLP push)
- container_cpu_usage_*        labels: {container, pod, cpu, image, id=cgroup_path}       (cAdvisor)
- kafka_* (exporter)           labels: {instance='ecommerce-kafka', topic, partition, consumergroup}
- mysql_*                      labels: {app='mysqld-exporter', instance:IP:port}
- k6_*                         labels: {testid, scenario}
- traces_service_graph_*       labels: {client, server, source='tempo'}
- traces_spanmetrics_*         labels: {service_name, span_name, span_kind}
"""

import json

def stat(id, title, gridPos, expr, unit="short"):
    return {
        "type": "stat", "id": id, "title": title, "gridPos": gridPos,
        "datasource": {"type": "prometheus", "uid": "prometheus"},
        "targets": [{"refId": "A", "expr": expr,
                     "datasource": {"type": "prometheus", "uid": "prometheus"}}],
        "fieldConfig": {"defaults": {"unit": unit,
                        "color": {"mode": "thresholds"},
                        "thresholds": {"mode": "absolute",
                                       "steps": [{"value": None, "color": "green"}]}}, "overrides": []},
        "options": {"graphMode": "area", "colorMode": "value",
                    "justifyMode": "auto", "textMode": "auto"},
    }

def ts(id, title, gridPos, targets, unit="short"):
    return {
        "type": "timeseries", "id": id, "title": title, "gridPos": gridPos,
        "datasource": {"type": "prometheus", "uid": "prometheus"},
        "targets": [{"refId": chr(65+i), "expr": e, "legendFormat": lf,
                     "datasource": {"type": "prometheus", "uid": "prometheus"}}
                    for i, (e, lf) in enumerate(targets)],
        "fieldConfig": {"defaults": {"unit": unit,
                        "custom": {"lineWidth": 2, "fillOpacity": 10, "showPoints": "never"},
                        "color": {"mode": "palette-classic"}}, "overrides": []},
        "options": {"legend": {"displayMode": "list", "placement": "bottom"},
                    "tooltip": {"mode": "multi"}},
    }

def logs(id, title, gridPos, expr):
    return {
        "type": "logs", "id": id, "title": title, "gridPos": gridPos,
        "datasource": {"type": "loki", "uid": "loki"},
        "targets": [{"refId": "A", "expr": expr,
                     "datasource": {"type": "loki", "uid": "loki"}}],
        "options": {"showLabels": False, "showTime": True, "wrapLogMessage": True,
                    "enableLogDetails": True, "dedupStrategy": "none"},
    }

def ng(id, title, gridPos):
    return {
        "type": "nodeGraph", "id": id, "title": title, "gridPos": gridPos,
        "datasource": {"type": "prometheus", "uid": "prometheus"},
        "targets": [{"refId": "A", "format": "table",
             "expr": "sum by (client, server) (rate(traces_service_graph_request_total[1h]))",
             "datasource": {"type": "prometheus", "uid": "prometheus"}}],
    }

panels = []
pid = 0
def nid(): global pid; pid += 1; return pid

# Row 1 — instant health stats
panels += [
    stat(nid(), "JVM UP (services scraped)", {"h":4,"w":4,"x":0,"y":0},
         'count(count by (pod) (jvm_memory_used_bytes{app=~"service-.*"}))'),
    stat(nid(), "Kafka Brokers", {"h":4,"w":4,"x":4,"y":0}, 'kafka_brokers'),
    stat(nid(), "MySQL Up", {"h":4,"w":4,"x":8,"y":0}, 'mysql_up'),
    stat(nid(), "MySQL QPS", {"h":4,"w":4,"x":12,"y":0},
         'rate(mysql_global_status_queries[5m])', unit="reqps"),
    stat(nid(), "Active DB Connections", {"h":4,"w":4,"x":16,"y":0},
         'sum(hikaricp_connections_active{app=~"service-.*"})'),
    stat(nid(), "Kafka Topic Partitions", {"h":4,"w":4,"x":20,"y":0},
         'sum(kafka_topic_partition_in_sync_replica)'),
]

# Row 2 — JVM heap + HTTP rate
panels += [
    ts(nid(), "JVM Heap Used (per service)", {"h":8,"w":12,"x":0,"y":4},
       [('sum by (app) (jvm_memory_used_bytes{app=~"service-.*",area="heap"})', "{{app}}")],
       unit="bytes"),
    ts(nid(), "HTTP Request Rate (per service, via OTLP)", {"h":8,"w":12,"x":12,"y":4},
       [('sum by (job) (rate(http_server_request_duration_seconds_count{job=~"ecommerce/service-.*"}[10m]))', "{{job}}")],
       unit="reqps"),
]

# Row 3 — JVM threads + process CPU
panels += [
    ts(nid(), "JVM Live Threads (per service)", {"h":8,"w":12,"x":0,"y":12},
       [('jvm_threads_live_threads{app=~"service-.*"}', "{{app}}")]),
    ts(nid(), "Process CPU Usage (per service)", {"h":8,"w":12,"x":12,"y":12},
       [('process_cpu_usage{app=~"service-.*"}', "{{app}}")],
       unit="percentunit"),
]

# Row 4 — container resource usage (cAdvisor)
panels += [
    ts(nid(), "Container CPU (cores, per container)", {"h":8,"w":12,"x":0,"y":20},
       [('sum by (container) (rate(container_cpu_usage_seconds_total{container!=""}[10m]))', "{{container}}")]),
    ts(nid(), "Container Memory WSS (per container)", {"h":8,"w":12,"x":12,"y":20},
       [('sum by (container) (container_memory_working_set_bytes{pod=~"service-.*|kafka-.*|mysql-.*|alloy-.*|grafana-.*|prometheus-.*|tempo-.*|loki-.*|mysqld-.*"})', "{{container}}")],
       unit="bytes"),
]

# Row 5 — Kafka
panels += [
    ts(nid(), "Kafka Latest Offset (per topic)", {"h":8,"w":12,"x":0,"y":28},
       [('max by (topic) (kafka_topic_partition_current_offset)', "{{topic}}")]),
    ts(nid(), "Kafka ISR (min per topic, should be >=1)", {"h":8,"w":12,"x":12,"y":28},
       [('min by (topic) (kafka_topic_partition_in_sync_replica)', "{{topic}}")]),
]

# Row 6 — MySQL
panels += [
    ts(nid(), "MySQL Commands/sec (top 5)", {"h":8,"w":12,"x":0,"y":36},
       [('topk(5, rate(mysql_global_status_commands_total[5m]))', "{{command}}")],
       unit="reqps"),
    ts(nid(), "MySQL Connections", {"h":8,"w":12,"x":12,"y":36},
       [('mysql_global_status_threads_connected', "connected"),
        ('mysql_global_status_threads_running', "running"),
        ('mysql_global_variables_max_connections', "max")]),
]

# Row 7 — Distributed Tracing
panels += [
    ng(nid(), "Service Graph (from Tempo metrics_generator)", {"h":10,"w":12,"x":0,"y":44}),
    ts(nid(), "Span Call Rate (per service)", {"h":10,"w":12,"x":12,"y":44},
       [('sum by (service_name) (rate(traces_spanmetrics_calls_total[5m]))', "{{service_name}}")],
       unit="reqps"),
]

# Row 8 — k6 live panel (visible while running)
panels += [
    stat(nid(), "k6 VUs", {"h":4,"w":6,"x":0,"y":54}, 'k6_vus'),
    stat(nid(), "k6 iterations/s", {"h":4,"w":6,"x":6,"y":54},
         'sum(rate(k6_iterations_total[5m]))', unit="reqps"),
    stat(nid(), "k6 HTTP p95 (ms)", {"h":4,"w":6,"x":12,"y":54},
         'avg(k6_http_req_duration_p95)', unit="ms"),
    stat(nid(), "k6 HTTP failure rate", {"h":4,"w":6,"x":18,"y":54},
         'avg(k6_http_req_failed_rate)', unit="percentunit"),
    ts(nid(), "k6 HTTP Request Duration", {"h":8,"w":24,"x":0,"y":58},
       [('avg(k6_http_req_duration_avg)', "avg"),
        ('avg(k6_http_req_duration_p95)', "p95"),
        ('avg(k6_http_req_duration_p99)', "p99")],
       unit="ms"),
]

# Row 9 — Logs
panels += [
    logs(nid(), "Recent service logs (trace_id included)", {"h":12,"w":24,"x":0,"y":66},
         '{app=~"service-.*"}'),
]

dashboard = {
    "uid": "ecommerce-overview",
    "title": "Ecommerce Overview",
    "tags": ["ecommerce", "custom"],
    "timezone": "browser",
    "schemaVersion": 39,
    "version": 2,
    "editable": True,
    "graphTooltip": 1,
    "refresh": "30s",
    "time": {"from": "now-15m", "to": "now"},
    "panels": panels,
}
print(json.dumps(dashboard, indent=2))
