#!/usr/bin/env python3
"""Render Grafana dashboards over recorded k6 measure windows (protocol step 7).

Discovers every run under an evidence root by its logs/window-from.txt +
window-to.txt pair, resolves which k6 series labels that window actually holds,
skips dashboards whose backing series are empty, renders the rest through
grafana-image-renderer and records the queried panel values for step 8.

Requires `kubectl port-forward -n monitoring svc/grafana 3000:3000`.
"""

import argparse
import json
import os
import pathlib
import sys
import urllib.error
import urllib.parse
import urllib.request

GRAFANA = "http://localhost:3000/grafana"
PROM = GRAFANA + "/api/datasources/proxy/uid/prometheus/api/v1"


def http(url, binary=False, timeout=180):
    req = urllib.request.Request(url, headers={"Accept": "*/*"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read() if binary else json.loads(r.read())


def promq(expr, at, dur):
    """Peak value of `expr` inside the measure window.

    A plain instant query at the window end returns nothing when scraping
    stopped before the run finished, which would wrongly mark a panel empty.
    """
    wrapped = f"max_over_time(({expr})[{dur}s:15s])"
    url = PROM + "/query?" + urllib.parse.urlencode({"query": wrapped, "time": at})
    try:
        d = http(url)
    except urllib.error.URLError as e:
        return None, f"query failed: {e}"
    res = d.get("data", {}).get("result", [])
    if not res:
        return None, "no data"
    return float(res[0]["value"][1]), None


def series_labels(at, dur):
    """Which (write_path, pods) k6 reserve series carried samples in the window."""
    expr = f"count_over_time(k6_reserve_ms_p99{{scenario=\"reserve\"}}[{dur}s]) > 0"
    url = PROM + "/query?" + urllib.parse.urlencode({"query": expr, "time": at})
    try:
        d = http(url)
    except urllib.error.URLError:
        return []
    out = []
    for r in d.get("data", {}).get("result", []):
        m = r["metric"]
        if "write_path" in m and "pods" in m:
            out.append((m["write_path"], m["pods"]))
    return sorted(set(out))


def discover(root):
    runs = []
    for wf in pathlib.Path(root).rglob("logs/window-from.txt"):
        wt = wf.with_name("window-to.txt")
        if not wt.exists():
            continue
        a, b = int(wf.read_text().strip()), int(wt.read_text().strip())
        runs.append({"name": str(wf.parent.parent.relative_to(root)),
                     "dir": wf.parent.parent, "from_ms": a, "to_ms": b})
    return sorted(runs, key=lambda r: r["from_ms"])


def render(uid, out_path, from_ms, to_ms, variables, width, height):
    # kiosk=1 is what makes the PNG usable outside Grafana: without it the nav
    # sidebar and the anonymous-user "Failed to update user preferences" toast
    # are baked into the image. theme=dark is passed explicitly so the renderer
    # never round-trips user preferences.
    qs = {"orgId": 1, "from": from_ms, "to": to_ms, "width": width,
          "height": height, "tz": "Asia/Seoul", "kiosk": 1, "theme": "dark"}
    qs.update({f"var-{k}": v for k, v in variables.items()})
    url = f"{GRAFANA}/render/d/{uid}/x?" + urllib.parse.urlencode(qs)
    png = http(url, binary=True)
    if not png.startswith(b"\x89PNG"):
        raise RuntimeError(f"{uid}: renderer returned non-PNG ({len(png)}B)")
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_bytes(png)
    return len(png)


# dashboard -> (uid, render height, panel probes keyed by title)
def reserve_probes(wp, pods, rw):
    sel = f'write_path="{wp}", pods="{pods}", scenario="reserve"'
    return {
        "RPS (reserve, k6) — req/s": f"sum(rate(k6_reserve_2xx_total{{{sel}}}[{rw}]))",
        "error rate": f"100 * max(k6_http_req_failed_rate{{{sel}}})",
        "p95 (k6 client)": f"avg_over_time(k6_reserve_ms_p95{{{sel}}}[{rw}])",
        "p99 (k6 client)": f"avg_over_time(k6_reserve_ms_p99{{{sel}}}[{rw}])",
        "avg (k6 client)": f"avg_over_time(k6_reserve_ms_avg{{{sel}}}[{rw}])",
        "HikariCP pending (product DB)": 'sum(hikaricp_connections_pending{app="service-product"})',
    }


def ba_probes(service, rw):
    base = f'app="{service}", uri!~"/actuator.*"'
    return {
        "RPS — req/s": f"sum(rate(http_server_requests_seconds_count{{{base}}}[{rw}])) or vector(0)",
        "5xx rate": (f'100 * ((sum(rate(http_server_requests_seconds_count{{{base}, status=~"5.."}}[{rw}])) or vector(0))'
                     f" / clamp_min(sum(rate(http_server_requests_seconds_count{{{base}}}[{rw}])), 0.001))"),
        "avg server latency": (f"sum(rate(http_server_requests_seconds_sum{{{base}}}[{rw}]))"
                               f" / clamp_min(sum(rate(http_server_requests_seconds_count{{{base}}}[{rw}])), 0.001)"),
        "app container CPU (cores)": (f'sum(rate(container_cpu_usage_seconds_total{{namespace="ecommerce",'
                                      f' pod=~"{service}-.*", container!="", container!="POD"}}[{rw}]))'),
        "Max HTTP latency": f"max(http_server_requests_seconds_max{{{base}}})",
        "Hikari pending": f'max(hikaricp_connections_pending{{app="{service}"}})',
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default="docs/evidence/latest/rebaseline")
    ap.add_argument("--services", default="service-product,service-order")
    ap.add_argument("--rw", default="2m")
    ap.add_argument("--dbs", default="product")
    ap.add_argument("--audit", default="docs/evidence/latest/rebaseline/dashboard-audit.json")
    args = ap.parse_args()

    runs = discover(args.root)
    if not runs:
        sys.exit(f"no runs under {args.root}")
    audit, captured, skipped = [], 0, []

    for run in runs:
        at = run["to_ms"] // 1000
        dur = max(1, (run["to_ms"] - run["from_ms"]) // 1000)
        shots = run["dir"] / "screenshots"
        entry = {"run": run["name"], "from_ms": run["from_ms"], "to_ms": run["to_ms"],
                 "window_kst": None, "captures": [], "skipped": []}

        for wp, pods in series_labels(at, dur):
            probes = reserve_probes(wp, pods, args.rw)
            values = {k: promq(v, at, dur) for k, v in probes.items()}
            if all(v is None for v, _ in values.values()):
                entry["skipped"].append({"dashboard": "ecommerce-ev-reserve", "reason": "all panels empty"})
                continue
            name = f"ev-reserve-{wp}-pods{pods}.png"
            size = render("ecommerce-ev-reserve", shots / name, run["from_ms"], run["to_ms"],
                          {"write_path": wp, "pods": pods, "rw": args.rw}, 1500, 260)
            captured += 1
            entry["captures"].append({
                "dashboard": "ecommerce-ev-reserve", "file": str((shots / name).relative_to(args.root)),
                "bytes": size, "vars": {"write_path": wp, "pods": pods, "rw": args.rw},
                "panels": {k: (None if v is None else round(v, 6)) for k, (v, _) in values.items()},
                "empty_panels": [k for k, (v, _) in values.items() if v is None]})

            k6ts = f"ev-k6ts-{wp}.png"
            size = render("ecommerce-ev-k6ts", shots / k6ts, run["from_ms"], run["to_ms"],
                          {"write_path": wp}, 1500, 460)
            captured += 1
            entry["captures"].append({"dashboard": "ecommerce-ev-k6ts",
                                      "file": str((shots / k6ts).relative_to(args.root)), "bytes": size,
                                      "vars": {"write_path": wp}, "panels": {}, "empty_panels": []})

        # InnoDB row-lock evidence. The whole SKIP LOCKED argument rests on
        # "row_lock_current_waits stayed at 0 while throughput collapsed", and
        # until mysqld-exporter was actually deployed there was no image for it.
        for db in args.dbs.split(","):
            probes = {
                "current waiters": f'mysql_global_status_innodb_row_lock_current_waits{{db="{db}"}}',
                "threads_running": f'mysql_global_status_threads_running{{db="{db}"}}',
                "mysql CPU (cores)": (f'sum(rate(container_cpu_usage_seconds_total{{namespace="ecommerce",'
                                      f' pod=~"mysql-{db}-.*", container!="", container!="POD"}}[2m]))'),
            }
            values = {k: promq(v, at, dur) for k, v in probes.items()}
            if all(v is None for v, _ in values.values()):
                entry["skipped"].append({"dashboard": f"ecommerce-ev-lock/{db}",
                                         "reason": "no mysql metrics for this db in window"})
                continue
            lock = f"ev-lock-{db}.png"
            size = render("ecommerce-ev-lock", shots / lock, run["from_ms"], run["to_ms"],
                          {"db": db}, 1500, 760)
            captured += 1
            entry["captures"].append({
                "dashboard": "ecommerce-ev-lock", "file": str((shots / lock).relative_to(args.root)),
                "bytes": size, "vars": {"db": db},
                "panels": {k: (None if v is None else round(v, 6)) for k, (v, _) in values.items()},
                "empty_panels": [k for k, (v, _) in values.items() if v is None]})

        # 선착순 공정성 대시보드. 이 캠페인의 주 증거다.
        #
        # 패널이 비었는지 먼저 재고 비면 렌더하지 않는다. 빈 그래프를 저장해두면 나중에
        # 그림은 있는데 아무것도 안 담긴 증거가 되고, 그게 있으면 측정했다고 착각한다.
        flash_probes = {
            "202/s": "sum(rate(k6_flash_202_total[%s]))" % args.rw,
            "409/s": "sum(rate(k6_flash_409_total[%s]))" % args.rw,
            "p95": "avg_over_time(k6_flash_ms_p95[%s])" % args.rw,
            "order cpu": ('sum(rate(container_cpu_usage_seconds_total{'
                          'pod=~"service-order-.*", container="service-order"}[%s]))' % args.rw),
            "mysql product": 'sum(rate(mysql_global_status_queries{db="product"}[%s]))' % args.rw,
        }
        fv = {k: promq(v, at, dur) for k, v in flash_probes.items()}
        if all(v is None for v, _ in fv.values()):
            entry["skipped"].append({"dashboard": "flash-fair", "reason": "all panels empty"})
        else:
            ff = "flash-fair.png"
            try:
                size = render("flash-fair", shots / ff, run["from_ms"], run["to_ms"], {}, 1600, 1250)
                captured += 1
                entry["captures"].append({
                    "dashboard": "flash-fair",
                    "file": str((shots / ff).relative_to(args.root)), "bytes": size, "vars": {},
                    "panels": {k: (None if v is None else round(v, 6)) for k, (v, _) in fv.items()},
                    "empty_panels": [k for k, (v, _) in fv.items() if v is None]})
            except Exception as exc:
                entry["skipped"].append({"dashboard": "flash-fair", "reason": str(exc)})

        # 혼합 부하(조회 + 예약) 전용 대시보드. arm 라벨로 런을 격리한다 — k6 시리즈는
        # 런이 끝나도 마지막 값을 몇 분간 유지해서, 창 앞부분에 직전 런의 백분위가
        # 그대로 찍히기 때문이다.
        arm = run.get("arm") or (run.get("env") or {}).get("ARM")
        if arm:
            cr = "ev-catalog-reserve.png"
            try:
                size = render("ecommerce-ev-catalog-reserve", shots / cr,
                              run["from_ms"], run["to_ms"], {"arm": arm}, 1600, 760)
                captured += 1
                entry["captures"].append({
                    "dashboard": "ecommerce-ev-catalog-reserve",
                    "file": str((shots / cr).relative_to(args.root)),
                    "bytes": size, "vars": {"arm": arm}})
            except Exception as exc:
                entry["skipped"].append({"dashboard": "ecommerce-ev-catalog-reserve",
                                         "reason": str(exc)})

        for svc in args.services.split(","):
            # `or vector(0)` in the panels makes a genuine zero render as 0 instead
            # of No-data, so presence must be decided on the raw series, never on
            # the padded expression.
            scraped, _ = promq(f'count(count_over_time(http_server_requests_seconds_count{{app="{svc}"}}[{dur}s]))',
                               at, dur)
            if scraped is None:
                entry["skipped"].append({"dashboard": f"ecommerce-ev-ba/{svc}",
                                         "reason": "app not scraped during window"})
                continue
            probes = ba_probes(svc, args.rw)
            values = {k: promq(v, at, dur) for k, v in probes.items()}
            name = f"ev-ba-{svc}.png"
            size = render("ecommerce-ev-ba", shots / name, run["from_ms"], run["to_ms"],
                          {"service": svc, "rw": args.rw}, 1500, 260)
            captured += 1
            entry["captures"].append({
                "dashboard": "ecommerce-ev-ba", "file": str((shots / name).relative_to(args.root)),
                "bytes": size, "vars": {"service": svc, "rw": args.rw},
                "panels": {k: (None if v is None else round(v, 6)) for k, (v, _) in values.items()},
                "empty_panels": [k for k, (v, _) in values.items() if v is None]})

        if not entry["captures"]:
            skipped.append(run["name"])
        audit.append(entry)
        print(f"{run['name']:<36} captured={len(entry['captures'])}", flush=True)

    pathlib.Path(args.audit).write_text(json.dumps(
        {"generated_from": "prometheus TSDB (rebaseline cluster)", "range_window": "per-run measure window",
         "runs": audit, "runs_without_any_data": skipped}, indent=2) + "\n")
    print(f"\ncaptured PNGs: {captured}")
    print(f"runs with no renderable dashboard: {len(skipped)} -> {skipped}")
    print(f"audit: {args.audit}")


if __name__ == "__main__":
    main()
