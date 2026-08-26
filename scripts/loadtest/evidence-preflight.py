#!/usr/bin/env python3
"""Block a measurement run unless the evidence pipeline can actually record it.

The 2026-08-11 rebaseline campaign produced 43 k6 runs and zero dashboard
evidence: Prometheus was OOMKilled for most of the campaign, mysqld-exporter
was never deployed, and grafana-image-renderer did not exist. None of that was
visible from the k6 output, so every run "passed" while its evidence was being
dropped on the floor.

This gate runs BEFORE a measurement and fails loudly when any of those
conditions are back. It checks live cluster state, never manifests.

Usage:
  evidence-preflight.py                       # full gate
  evidence-preflight.py --require-mysql=false # phases where no mysql is SUT
"""

import argparse
import json
import subprocess
import sys
import urllib.parse

NS_MON = "monitoring"
NS_APP = "ecommerce"
MIN_PROM_LIMIT_GI = 6
# 정상 캠페인의 head는 5만 미만이다. 30만이면 이미 폭발 중이라는 뜻.
MAX_HEAD_SERIES = 300_000
MAX_LABEL_VALUES = 2_000


def kubectl(*args, check=True):
    r = subprocess.run(["kubectl", *args], capture_output=True, text=True)
    if check and r.returncode != 0:
        return None
    return r.stdout.strip()


def kjson(*args):
    out = kubectl(*args, "-o", "json")
    return json.loads(out) if out else None


def gi(quantity):
    """Kubernetes memory quantity -> GiB float."""
    if not quantity:
        return 0.0
    q = str(quantity)
    for suffix, mult in (("Gi", 1), ("Mi", 1 / 1024), ("Ki", 1 / 1024 / 1024),
                         ("G", 1000**3 / 1024**3), ("M", 1000**2 / 1024**3)):
        if q.endswith(suffix):
            return float(q[: -len(suffix)]) * mult
    return float(q) / 1024**3


class Gate:
    def __init__(self):
        self.failures = []
        self.warnings = []

    def fail(self, check, detail):
        self.failures.append((check, detail))
        print(f"  BLOCK  {check}: {detail}")

    def warn(self, check, detail):
        self.warnings.append((check, detail))
        print(f"  WARN   {check}: {detail}")

    def ok(self, check, detail=""):
        print(f"  OK     {check}{': ' + detail if detail else ''}")


def check_prometheus(g):
    d = kjson("get", "deploy", "prometheus", "-n", NS_MON)
    if not d:
        return g.fail("prometheus", "deployment not found")
    lim = gi(d["spec"]["template"]["spec"]["containers"][0]["resources"]["limits"]["memory"])
    if lim < MIN_PROM_LIMIT_GI:
        g.fail("prometheus memory limit",
               f"{lim:.1f}Gi < {MIN_PROM_LIMIT_GI}Gi — this is the exact setting that "
               f"OOMKilled the 2026-08-11 campaign")
    else:
        g.ok("prometheus memory limit", f"{lim:.0f}Gi")

    pods = kjson("get", "pods", "-n", NS_MON, "-l", "app=prometheus")
    running = [p for p in (pods or {}).get("items", [])
               if p["status"]["phase"] == "Running"
               and all(c.get("ready") for c in p["status"].get("containerStatuses", []))]
    if not running:
        return g.fail("prometheus pod", "no Ready pod")
    st = running[0]["status"]["containerStatuses"][0]
    restarts = st.get("restartCount", 0)
    last = (st.get("lastState") or {}).get("terminated") or {}
    if last.get("reason") == "OOMKilled":
        g.fail("prometheus stability",
               f"last termination was OOMKilled (restarts={restarts}). Raising the "
               f"limit is usually the wrong fix — check the cardinality gate below, "
               f"a k6 script that tags series per-URL will outgrow any limit")
    elif restarts > 0:
        g.warn("prometheus stability", f"restartCount={restarts} reason={last.get('reason')}")
    else:
        g.ok("prometheus pod", "Ready, 0 restarts")


def prom_query(expr):
    """Run an instant query inside the Prometheus pod.

    The expression MUST be percent-encoded: selectors contain `{`, `"`, `,`
    and `~`, and an unencoded URL silently returns an empty result — which
    reads exactly like "the metric is missing" and makes this gate lie.
    """
    pods = kjson("get", "pods", "-n", NS_MON, "-l", "app=prometheus")
    items = (pods or {}).get("items", [])
    if not items:
        return None
    pod = items[0]["metadata"]["name"]
    url = "http://localhost:9090/api/v1/query?query=" + urllib.parse.quote(expr, safe="")
    out = kubectl("exec", "-n", NS_MON, pod, "--", "wget", "-qO-", url)
    try:
        return json.loads(out)["data"]["result"]
    except Exception:
        return None


def check_targets(g, require_mysql):
    res = prom_query("up")
    if res is None:
        return g.fail("prometheus api", "query failed")
    by_job = {}
    for s in res:
        job = s["metric"].get("job", "?")
        by_job.setdefault(job, []).append(int(float(s["value"][1])))

    if not by_job:
        return g.fail("scrape targets", "zero targets — nothing is being scraped")

    for job, vals in sorted(by_job.items()):
        up, tot = sum(vals), len(vals)
        (g.ok if up == tot else g.warn)(f"target {job}", f"{up}/{tot} up")

    # cAdvisor is what every container-CPU panel is built on. Scoping the check
    # to the SUT pods matters: the e2-scaleout runs had app-level metrics and a
    # live cadvisor job, yet zero container_cpu series for service-*, so every
    # CPU panel rendered No-data.
    cad = prom_query('count(container_cpu_usage_seconds_total'
                     '{namespace="ecommerce", pod=~"service-.*", container!=""})')
    n = int(float(cad[0]["value"][1])) if cad else 0
    if n == 0:
        g.fail("cAdvisor SUT series",
               "0 container_cpu series for service-* pods — container CPU panels "
               "will render No-data. Either the SUT is not deployed or the cadvisor "
               "scrape is broken; both make this run unevidenceable")
    else:
        g.ok("cAdvisor SUT series", f"{n} series")

    app = prom_query("count(count by (app)(http_server_requests_seconds_count))")
    if not app:
        g.fail("application metrics",
               "http_server_requests_seconds_count absent — ev-ba cannot render")
    else:
        g.ok("application metrics", f"{int(float(app[0]['value'][1]))} app(s) reporting")

    if require_mysql:
        mysql_jobs = [j for j in by_job if "mysqld" in j]
        if not mysql_jobs:
            g.fail("mysqld-exporter", "no mysqld-exporter scrape target — "
                                      "InnoDB lock dashboards cannot render "
                                      "(this was missing for all 43 runs)")
        else:
            lock = prom_query("count(mysql_global_status_innodb_row_lock_current_waits)")
            if not lock:
                g.fail("mysql lock metrics", "mysql_global_status_* absent from TSDB")
            else:
                g.ok("mysql lock metrics", f"{int(float(lock[0]['value'][1]))} series")


def check_cardinality(g):
    """Head-series and per-label-value guard.

    This is what actually killed the 2026-08-11 campaign. `browse-only.js`
    issued GET /variants/{random 1..50000} without a `name` tag, so k6 tagged
    every request with its own URL: ~50k series per k6 metric, 1.15M series
    total, and Prometheus OOMKilled regardless of its memory limit. Raising the
    limit only moves the failure later, so the cardinality itself is gated.
    """
    head = prom_query("prometheus_tsdb_head_series")
    if not head:
        return g.fail("tsdb head series", "prometheus_tsdb_head_series unavailable")
    n = int(float(head[0]["value"][1]))
    if n > MAX_HEAD_SERIES:
        g.fail("tsdb head series",
               f"{n:,} > {MAX_HEAD_SERIES:,}. A per-URL `name`/`url` tag is the usual "
               f"cause; pin it with tags:{{name:'GET /path/:id'}} in the k6 script "
               f"and drop the exploded series before measuring")
    else:
        g.ok("tsdb head series", f"{n:,}")

    for label in ("name", "url"):
        res = prom_query(f"count(count by ({label})({{{label}!=\"\"}}))")
        if not res:
            continue
        v = int(float(res[0]["value"][1]))
        if v > MAX_LABEL_VALUES:
            g.fail(f"label cardinality: {label}",
                   f"{v:,} distinct values (> {MAX_LABEL_VALUES:,}) — a load script is "
                   f"emitting one series per URL")
        else:
            g.ok(f"label cardinality: {label}", f"{v:,} distinct")


def check_renderer(g):
    pods = kjson("get", "pods", "-n", NS_MON, "-l", "app=grafana-image-renderer")
    items = [p for p in (pods or {}).get("items", [])
             if p["status"]["phase"] == "Running"
             and all(c.get("ready") for c in p["status"].get("containerStatuses", []))]
    if not items:
        return g.fail("grafana-image-renderer",
                      "not Ready — dashboard capture (protocol step 7) will fail")
    g.ok("grafana-image-renderer", "Ready")

    d = kjson("get", "deploy", "grafana", "-n", NS_MON)
    env = {e["name"]: e.get("value")
           for e in d["spec"]["template"]["spec"]["containers"][0].get("env", [])}
    if not env.get("GF_RENDERING_SERVER_URL"):
        g.fail("grafana rendering", "GF_RENDERING_SERVER_URL unset — /render returns 404")
    else:
        g.ok("grafana rendering", env["GF_RENDERING_SERVER_URL"])


def check_remote_write(g):
    svc = kjson("get", "svc", "prometheus-remote-write", "-n", NS_MON)
    if not svc:
        return g.fail("k6 remote-write", "prometheus-remote-write Service missing")
    g.ok("k6 remote-write", "Service present")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--require-mysql", default="true")
    args = ap.parse_args()
    require_mysql = args.require_mysql.lower() not in ("false", "0", "no")

    print("== evidence preflight ==")
    g = Gate()
    check_prometheus(g)
    check_targets(g, require_mysql)
    check_cardinality(g)
    check_renderer(g)
    check_remote_write(g)

    print()
    if g.failures:
        print(f"BLOCKED — {len(g.failures)} check(s) failed. "
              f"Fix these before measuring; a run started now produces numbers "
              f"with no recoverable dashboard evidence.")
        for c, d in g.failures:
            print(f"  - {c}: {d}")
        sys.exit(1)
    print(f"PASS — evidence pipeline can record this run"
          f"{f' ({len(g.warnings)} warning(s))' if g.warnings else ''}")


if __name__ == "__main__":
    main()
