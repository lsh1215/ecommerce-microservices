#!/usr/bin/env python3
"""Pull per-run metrics for one measurement window.

Usage: runmetrics.py <outdir> <label>

Reads the window written by run-k6-job.sh and asks Prometheus what the
system was doing inside it. Reported separately from k6's client-side view
so that a limiter can be attributed to the app or to the database instead
of being guessed at.
"""
import json
import pathlib
import subprocess
import urllib.parse
import sys

OUT = pathlib.Path(sys.argv[1])
LABEL = sys.argv[2] if len(sys.argv) > 2 else OUT.name


_POD = None


def _prom_pod():
    global _POD
    if _POD is None:
        _POD = subprocess.run(
            ["kubectl", "get", "pod", "-n", "monitoring", "-l", "app=prometheus",
             "-o", "jsonpath={.items[0].metadata.name}"],
            capture_output=True, text=True).stdout.strip()
    return _POD


def prom(query, start, end):
    """Evaluate an instant query at the window end via a monitoring pod.

    Uses wget directly: the prometheus image has no python3, so the previous
    `kubectl exec ... python3 -c` form failed silently and every metric came
    back as None.
    """
    url = ("http://localhost:9090/api/v1/query?query="
           + urllib.parse.quote(query) + "&time=" + end)
    raw = subprocess.run(
        ["kubectl", "exec", "-n", "monitoring", _prom_pod(), "-c", "prometheus",
         "--", "wget", "-qO-", url],
        capture_output=True, text=True).stdout.strip()
    try:
        res = json.loads(raw)["data"]["result"]
        return float(res[0]["value"][1]) if res else None
    except Exception:
        return None


def main():
    logs = OUT / "logs"
    try:
        # run-k6-job.sh writes the window in epoch milliseconds; Prometheus
        # wants seconds. Reading these as seconds turned a 120s run into a
        # 120,000s one, which zeroed every derived rate and evaluated every
        # query at a timestamp far in the future, so all of them came back
        # empty.
        raw_from = float((logs / "window-from.txt").read_text().strip())
        raw_to = float((logs / "window-to.txt").read_text().strip())
        scale = 1000.0 if raw_from > 1e11 else 1.0
        start = f"{raw_from / scale:.3f}"
        end = f"{raw_to / scale:.3f}"
    except FileNotFoundError:
        print(f"  {LABEL}: 창 파일 없음 (런 실패)")
        return

    sm = json.loads((logs / "k6-summary.json").read_text())["metrics"]

    def g(name, field):
        v = sm.get(name, {})
        v = v.get("values", v)
        return v.get(field)

    dur = float(end) - float(start)
    ok = g("flash_202", "count") or g("reserve_2xx", "count") or 0
    insuf = g("flash_409", "count") or g("reserve_insufficient", "count") or 0
    e5 = g("flash_5xx", "count") or g("reserve_5xx", "count") or 0
    rng = f"[{start}s,{end}s]"

    # Window-average rates keep a single spike from standing in for the run.
    metrics = {
        "db_cpu": f"sum(rate(container_cpu_usage_seconds_total{{pod=~'mysql-product-.*',container='mysql'}}[{int(dur)}s]))",
        "app_cpu": f"sum(rate(container_cpu_usage_seconds_total{{pod=~'service-product-.*',container='service-product'}}[{int(dur)}s]))",
        "throttle": f"sum(rate(container_cpu_cfs_throttled_periods_total{{pod=~'service-product-.*'}}[{int(dur)}s]))/clamp_min(sum(rate(container_cpu_cfs_periods_total{{pod=~'service-product-.*'}}[{int(dur)}s])),1)",
        "pool_pending": "sum(avg_over_time(hikaricp_connections_pending{pod=~'service-order-.*'}[%ds]))" % int(dur),
        "order_cpu": f"sum(rate(container_cpu_usage_seconds_total{{pod=~'service-order-.*',container='service-order'}}[{int(dur)}s]))",
        "order_throttle": f"sum(rate(container_cpu_cfs_throttled_periods_total{{pod=~'service-order-.*'}}[{int(dur)}s]))/clamp_min(sum(rate(container_cpu_cfs_periods_total{{pod=~'service-order-.*'}}[{int(dur)}s])),0.001)",
        "pool_active": "sum(avg_over_time(hikaricp_connections_active{pod=~'service-product-.*'}[%ds]))" % int(dur),
        "threads_running": "avg_over_time(mysql_global_status_threads_running[%ds])" % int(dur),
        "rows_lock_wait": f"rate(mysql_global_status_innodb_row_lock_waits[{int(dur)}s])",
        "lock_time_avg": "avg_over_time(mysql_global_status_innodb_row_lock_time_avg[%ds])" % int(dur),
        "deadlocks": f"increase(mysql_global_status_innodb_deadlocks[{int(dur)}s])",
    }
    vals = {k: prom(q, start, end) for k, q in metrics.items()}

    row = {
        "label": LABEL,
        "window": rng,
        "duration_s": round(dur, 1),
        "reserved_ok": int(ok),
        "insufficient": int(insuf),
        "http_5xx": int(e5),
        "achieved_rps": round(ok / dur, 1) if dur else None,
        "p95_ms": round(g("flash_ms", "p(95)") or g("reserve_ms", "p(95)") or 0, 1),
        "p99_ms": round(g("flash_ms", "p(99)") or g("reserve_ms", "p(99)") or 0, 1),
        "failed_pct": round(100 * (g("http_req_failed", "value") or 0), 2),
        **{k: (round(v, 3) if isinstance(v, float) else v) for k, v in vals.items()},
    }
    (OUT / "metrics.json").write_text(json.dumps(row, indent=2))

    dbpct = f"{100 * row['db_cpu'] / 2:.0f}%" if row.get("db_cpu") else "?"
    apppct = f"{100 * row['app_cpu'] / 3:.0f}%" if row.get("app_cpu") else "?"
    thr = f"{100 * row['throttle']:.0f}%" if row.get("throttle") is not None else "?"
    print(
        f"  {LABEL:22s} 달성 {row['achieved_rps']:>6}/s  p95 {row['p95_ms']:>7}ms  "
        f"p99 {row['p99_ms']:>7}ms  5xx {row['http_5xx']:>5}  "
        f"DB {dbpct:>4}  앱 {apppct:>4}  스로틀 {thr:>4}  "
        f"주문CPU {row.get('order_cpu')}  락대기/s {row.get('rows_lock_wait')}"
    )


main()
