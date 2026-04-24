#!/usr/bin/env python3
"""Rewrite community-dashboard queries to match our cluster's actual labels.

Each grafana.com dashboard assumes the default micrometer / kafka-exporter /
mysqld-exporter labels. Our cluster differs — most notably jvm_* carries
`app=<service>` (via pod-scrape relabel) rather than `application=<service>`.
Rather than fork the dashboards by hand, patch the ConfigMap JSON in place.

Idempotent: safe to re-run; each rule checks before mutating.
"""

import json
import pathlib
import re
import subprocess
import sys

import yaml

DASH_DIR = pathlib.Path(__file__).resolve().parent.parent / "k8s" / "monitoring" / "dashboards"


def load(path: pathlib.Path):
    cm = yaml.safe_load(path.read_text())
    data_key = next(iter(cm["data"]))
    dashboard = json.loads(cm["data"][data_key])
    return cm, data_key, dashboard


def save(path: pathlib.Path, cm, data_key, dashboard):
    cm["data"][data_key] = json.dumps(dashboard, separators=(",", ":"))
    # Preserve the ConfigMap wrapper format used by fetch-dashboards.sh
    body = yaml.safe_dump(cm, sort_keys=False)
    path.write_text(body)


def _set_current(var: dict, value: str, text: str | None = None):
    """Pin a template variable's default so Grafana picks it on first load."""
    var["current"] = {"selected": True, "text": text or value, "value": value}


def patch_jvm_micrometer():
    """JVM / Micrometer (ID 4701) — rewrite `application=` to `app=`.

    Our pod-scrape relabel drops the original micrometer tag name
    `application` in favour of `app`. Walk every panel target and every
    template variable in-place (string substitution on the JSON text
    doesn't work because json.dumps escapes the embedded `"` inside
    expressions, making pattern matching fragile).
    """
    path = DASH_DIR / "dashboard-jvm-micrometer.yml"
    if not path.exists():
        return
    cm, key, d = load(path)

    def rewrite(text: str) -> str:
        # Matches application="value", application=~"...", application="$var"
        return re.sub(r'\bapplication(=~?|!=~?)"', r'app\1"', text)

    changed = 0

    def visit_panel(panel):
        nonlocal changed
        for t in panel.get("targets", []) or []:
            if "expr" in t and "application" in t["expr"]:
                new = rewrite(t["expr"])
                if new != t["expr"]:
                    t["expr"] = new
                    changed += 1
        for sub in panel.get("panels", []) or []:
            visit_panel(sub)

    # Modern dashboards use `panels`; classic ones (like 4701) use `rows[].panels`.
    for panel in d.get("panels", []) or []:
        visit_panel(panel)
    for row in d.get("rows", []) or []:
        for panel in row.get("panels", []) or []:
            visit_panel(panel)
    for v in d.get("templating", {}).get("list", []) or []:
        q = v.get("query")
        if isinstance(q, str) and "application" in q:
            v["query"] = q.replace("label_values(application)", "label_values(app)")
            v["query"] = rewrite(v["query"])
            changed += 1
        elif isinstance(q, dict) and "query" in q and "application" in q["query"]:
            q["query"] = q["query"].replace("label_values(application)", "label_values(app)")
            q["query"] = rewrite(q["query"])
            changed += 1
        if v.get("name") == "application":
            v["label"] = "service"

    # Pin `application` to service-product so on first load the dashboard
    # doesn't pick an exporter app (kafka-exporter etc) alphabetically. A
    # real user can still change it via the dropdown.
    for v in d.get("templating", {}).get("list", []) or []:
        if v.get("name") == "application":
            _set_current(v, "service-product")
    if changed == 0:
        print(f"[jvm] already patched — skip")
        return
    save(path, cm, key, d)
    print(f"[jvm] rewrote {changed} application→app references + pinned default")


def patch_mysql_overview():
    """MySQL Overview (ID 7362) — force `$host` to the single instance we expose.

    mysqld-exporter runs as a single Deployment, so the `$host` template
    variable only has one choice. Default the variable to the instance
    label value so panels render on first load without user interaction.
    """
    path = DASH_DIR / "dashboard-mysql-overview.yml"
    if not path.exists():
        return
    cm, key, d = load(path)
    # Resolve the mysqld-exporter instance dynamically so we pin the
    # variable to the value Prometheus actually sees (not a compile-time
    # constant that goes stale when the pod restarts). If the probe
    # fails, fall back to All — panels using `=` exact match will still
    # show No Data, but panels using `=~` regex will match.
    mysql_instance = None
    try:
        r = subprocess.run(
            ["gcloud", "compute", "ssh", "ecommerce-k3s",
             "--zone=asia-northeast3-a", "--command",
             'sudo kubectl -n monitoring exec deploy/prometheus -- '
             'wget -qO- "http://localhost:9090/api/v1/query?query=mysql_up" 2>/dev/null'],
            capture_output=True, text=True, timeout=30,
        )
        res = json.loads(r.stdout)["data"]["result"]
        if res:
            mysql_instance = res[0]["metric"].get("instance")
    except Exception:
        pass

    for v in d.get("templating", {}).get("list", []):
        if v.get("name") == "host":
            if mysql_instance:
                _set_current(v, mysql_instance)
            else:
                v["includeAll"] = True
                v["multi"] = True
                _set_current(v, "$__all", text="All")
    save(path, cm, key, d)
    print(f"[mysql] host variable pinned to {mysql_instance or 'All'}")


def patch_kafka_exporter():
    """Kafka Exporter (ID 18941) — Alloy's built-in exporter tags every
    metric with `instance="ecommerce-kafka"` and `job="integrations/kafka"`.
    The dashboard's `$instance` variable queries `label_values(kafka_brokers, instance)`
    — and `kafka_brokers` doesn't exist for us (we have `kafka_broker_info`).
    Repoint the variable to `kafka_broker_info` so it populates.
    """
    path = DASH_DIR / "dashboard-kafka-exporter-overview.yml"
    if not path.exists():
        return
    cm, key, d = load(path)
    # Resolve the standalone kafka-exporter instance (Deployment in monitoring
    # namespace, pod IP:9308) so the dashboard pins to it. The dashboard 18941
    # expects per-partition metrics that Alloy's built-in exporter doesn't
    # emit — only the standalone danielqsj/kafka-exporter produces them.
    kafka_instance = None
    kafka_job = None
    try:
        r = subprocess.run(
            ["gcloud", "compute", "ssh", "ecommerce-k3s",
             "--zone=asia-northeast3-a", "--command",
             'sudo kubectl -n monitoring exec deploy/prometheus -- '
             'wget -qO- "http://localhost:9090/api/v1/query?query=kafka_topic_partitions" 2>/dev/null'],
            capture_output=True, text=True, timeout=30,
        )
        for res in json.loads(r.stdout)["data"]["result"]:
            inst = res["metric"].get("instance", "")
            if ":9308" in inst:
                kafka_instance = inst
                kafka_job = res["metric"].get("job", "")
                break
    except Exception:
        pass

    for v in d.get("templating", {}).get("list", []):
        name = v.get("name")
        if name == "instance":
            q = v.get("query")
            if isinstance(q, str):
                v["query"] = q.replace("kafka_brokers", "kafka_broker_info")
            elif isinstance(q, dict) and "query" in q:
                q["query"] = q["query"].replace("kafka_brokers", "kafka_broker_info")
            if kafka_instance:
                _set_current(v, kafka_instance)
            else:
                v["includeAll"] = True
                _set_current(v, "$__all", text="All")
        elif name == "job":
            if kafka_job:
                _set_current(v, kafka_job)
            else:
                v["includeAll"] = True
                _set_current(v, "$__all", text="All")
        elif name in ("cluster_name", "topic"):
            v["includeAll"] = True
            _set_current(v, "$__all", text="All")
    save(path, cm, key, d)
    print(f"[kafka] instance pinned to {kafka_instance or 'All'}")


def patch_k8s_pods():
    """K8S Pods (ID 15661) — drop `origin_prometheus` + `node` filters.

    The dashboard's default deploy assumes:
      - Prometheus external_labels stamp every scrape with `origin_prometheus`
        (we use `cluster=ecommerce-k3s` instead).
      - cAdvisor scrapes carry a `node` label (Alloy's default scrape
        doesn't add one).
    Strip both clauses so queries fall back to cluster-wide aggregation,
    which is correct for our single-node setup.
    """
    path = DASH_DIR / "dashboard-kubernetes-views-pods.yml"
    if not path.exists():
        return
    cm, key, d = load(path)
    changed = 0

    def strip(expr: str) -> str:
        patterns = [
            r'origin_prometheus=~"\$origin_prometheus",\s*',
            r',\s*origin_prometheus=~"\$origin_prometheus"',
            r'origin_prometheus=~"\$origin_prometheus"',
            r'node=~"\^\$Node\$",\s*',
            r',\s*node=~"\^\$Node\$"',
            r'node=~"\^\$Node\$"',
            # Variant escapes that show up after substitution preview
            r'node=~"\^ecommerce-k3s\$",\s*',
            r',\s*node=~"\^ecommerce-k3s\$"',
            r'node=~"\^ecommerce-k3s\$"',
        ]
        out = expr
        for p in patterns:
            out = re.sub(p, "", out)
        return out

    def visit(obj):
        nonlocal changed
        if isinstance(obj, dict):
            for t in obj.get("targets", []) or []:
                if "expr" in t and "origin_prometheus" in t["expr"]:
                    new = strip(t["expr"])
                    if new != t["expr"]:
                        t["expr"] = new
                        changed += 1
            for v in obj.values():
                visit(v)
        elif isinstance(obj, list):
            for x in obj:
                visit(x)
    visit(d)
    # Strip from template variables too
    for v in d.get("templating", {}).get("list", []) or []:
        q = v.get("query")
        if isinstance(q, dict) and "query" in q and "origin_prometheus" in q["query"]:
            q["query"] = strip(q["query"])
            changed += 1
        elif isinstance(q, str) and "origin_prometheus" in q:
            v["query"] = strip(q)
            changed += 1
    # Pin template vars to "All" so first-load picks every value rather
    # than pinning to the alphabetically-first candidate (which for Pod
    # may be kube-state-metrics's own pod — not very informative).
    for v in d.get("templating", {}).get("list", []) or []:
        if v.get("name") in ("Node", "NameSpace", "Pod", "Container"):
            v["includeAll"] = True
            v["multi"] = True
            _set_current(v, "$__all", text="All")
    if changed == 0:
        print(f"[k8s] already patched — skip")
        return
    save(path, cm, key, d)
    print(f"[k8s] stripped {changed} origin_prometheus references + pinned defaults")


def patch_mysql_overview_instance():
    """MySQL Overview (ID 7362) — broaden node_* queries to any instance.

    Default 7362 expects mysqld + node_exporter on the same host (shared
    `instance` value). In our cluster, mysqld-exporter is a pod
    (10.42.0.69:9104) and node-exporter is host-network (10.178.0.2:9100).
    They don't share `instance`, so panels filtering `node_*{instance=\"$host\"}`
    return empty. Drop the instance filter on node_* queries so they pull
    data from any node-exporter target.
    """
    path = DASH_DIR / "dashboard-mysql-overview.yml"
    if not path.exists():
        return
    cm, key, d = load(path)
    changed = 0

    def rewrite(expr: str) -> str:
        # node_<metric>{instance="$host", ...} → node_<metric>{...}
        # also drop single-filter form: node_<metric>{instance="$host"}
        out = re.sub(r'(node_[a-zA-Z_]+)\{instance="\$host"\}', r'\1', expr)
        out = re.sub(r'(node_[a-zA-Z_]+)\{instance="\$host",\s*', r'\1{', out)
        out = re.sub(r',\s*instance="\$host"(\s*[}\]])', r'\1', out)
        return out

    def visit(obj):
        nonlocal changed
        if isinstance(obj, dict):
            for t in obj.get("targets", []) or []:
                if "expr" in t and "$host" in t["expr"] and "node_" in t["expr"]:
                    new = rewrite(t["expr"])
                    if new != t["expr"]:
                        t["expr"] = new
                        changed += 1
            for v in obj.values():
                visit(v)
        elif isinstance(obj, list):
            for x in obj:
                visit(x)
    visit(d)
    if changed == 0:
        print(f"[mysql-node] already patched — skip")
        return
    save(path, cm, key, d)
    print(f"[mysql-node] relaxed {changed} node_*{{instance=$host}} filters")


def patch_logs_app():
    """Loki Logs / App (ID 13639) — no patching needed.

    Alloy's `loki.source.kubernetes` populates `job="ecommerce/service-*"`
    which the dashboard's `$app = label_values(job)` picks up natively.
    Left as a stub so callers can see it was intentionally not modified.
    """
    print(f"[logs] no patching required (job label auto-populates)")


def main():
    patch_jvm_micrometer()
    patch_mysql_overview()
    patch_mysql_overview_instance()
    patch_kafka_exporter()
    patch_k8s_pods()
    patch_logs_app()
    print("done.")


if __name__ == "__main__":
    main()
