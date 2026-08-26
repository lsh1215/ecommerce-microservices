#!/usr/bin/env python3
"""Render the dashboards that carry the NORMAL-vs-HOT reserve comparison.

capture-dashboards.py keys its panels on write_path/pods, which the tier arms do
not set; they tag runs with arm/tier instead, so that tool skips them silently.
This renders the dashboards whose variables the tier runs actually populate, and
records the panel values it queried so the PNGs can be checked against numbers
rather than trusted.

Requires: kubectl port-forward -n monitoring svc/grafana 3000:3000
Usage: capture-tier-evidence.py --root docs/evidence/latest/rev8/knee --runs normal-200,hot-600
"""

import argparse, json, pathlib, sys, urllib.parse, urllib.request

GRAFANA = "http://localhost:3000/grafana"
PROM = GRAFANA + "/api/datasources/proxy/uid/prometheus/api/v1"


def http(url, binary=False, timeout=180):
    req = urllib.request.Request(url, headers={"Accept": "*/*"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read() if binary else json.loads(r.read())


def peak(expr, at, dur):
    """Peak inside the window. An instant query at the window edge reads empty
    when the last scrape landed before the run ended, which looks like a panel
    with no data rather than a panel nobody queried correctly."""
    q = f"max_over_time(({expr})[{dur}s:15s])"
    try:
        d = http(PROM + "/query?" + urllib.parse.urlencode({"query": q, "time": at}))
        r = d.get("data", {}).get("result") or []
        return float(r[0]["value"][1]) if r else None
    except Exception:
        return None


def render(uid, out, from_ms, to_ms, variables, height=900, width=1600):
    qs = {"orgId": 1, "from": from_ms, "to": to_ms, "width": width, "height": height,
          "tz": "Asia/Seoul", "kiosk": 1, "theme": "dark"}
    qs.update({f"var-{k}": v for k, v in variables.items()})
    png = http(f"{GRAFANA}/render/d/{uid}/x?" + urllib.parse.urlencode(qs), binary=True)
    if not png.startswith(b"\x89PNG"):
        raise RuntimeError(f"{uid}: renderer returned non-PNG ({len(png)} bytes)")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(png)
    return len(png)


def probes(arm, rw="1m"):
    app = 'app="service-product", uri!~"/actuator.*"'
    k6 = f'arm="{arm}"'
    return {
        "k6 2xx rps": f"sum(rate(k6_reserve_2xx_total{{{k6}}}[{rw}]))",
        # k6_reserve_ms_p95 is exported in SECONDS despite the name; the trend
        # was declared with isTime and the remote-write exporter normalises to
        # seconds. Reading it as milliseconds understates latency 1000x.
        "k6 p95 ms": f"1000 * avg_over_time(k6_reserve_ms_p95{{{k6}}}[{rw}])",
        "k6 p99 ms": f"1000 * avg_over_time(k6_reserve_ms_p99{{{k6}}}[{rw}])",
        "k6 error rate %": f"100 * max(k6_http_req_failed_rate{{{k6}}})",
        "server rps": f"sum(rate(http_server_requests_seconds_count{{{app}}}[{rw}]))",
        "server 5xx rps": f'sum(rate(http_server_requests_seconds_count{{{app}, status=~"5.."}}[{rw}])) or vector(0)',
        "app cpu cores": 'sum(rate(container_cpu_usage_seconds_total{namespace="ecommerce",'
                         f' pod=~"service-product-.*", container!="", container!="POD"}}[{rw}]))',
        "hikari pending": 'max(hikaricp_connections_pending{app="service-product"})',
        "mysql row lock waits/s": 'sum(rate(mysql_global_status_innodb_row_lock_waits{db="product"}[' + rw + ']))',
        "mysql row lock time avg ms": 'max(mysql_global_status_innodb_row_lock_time_avg{db="product"})',
        "mysql threads running": 'max(mysql_global_status_threads_running{db="product"})',
    }


DASHBOARDS = [
    ("ecommerce-ev-lock", "mysql-lock-product", {"db": "product"}, 900),
    ("ecommerce-ev-ba", "service-product-server", {"service": "service-product", "rw": "1m"}, 1000),
    ("ecommerce-jvm", "jvm-service-product", {"app": "service-product"}, 800),
    ("ecommerce-mysql-per-db", "mysql-product", {"db": "product"}, 900),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", required=True)
    ap.add_argument("--runs", required=True, help="comma-separated run directory names")
    ap.add_argument("--audit", default=None)
    a = ap.parse_args()

    root = pathlib.Path(a.root)
    audit, failures = {}, []
    for name in [r.strip() for r in a.runs.split(",") if r.strip()]:
        d = root / name
        wf, wt = d / "logs/window-from.txt", d / "logs/window-to.txt"
        if not (wf.exists() and wt.exists()):
            failures.append(f"{name}: measure window missing")
            continue
        from_ms, to_ms = int(wf.read_text().strip()), int(wt.read_text().strip())
        dur = max(1, (to_ms - from_ms) // 1000)
        at = to_ms // 1000
        vals = {k: peak(v, at, dur) for k, v in probes(name).items()}
        audit[name] = {"from_ms": from_ms, "to_ms": to_ms, "window_s": dur, "values": vals}
        print(f"\n== {name}  window {dur}s")
        for k, v in vals.items():
            print("   %-26s %s" % (k, "no data" if v is None else round(v, 2)))
        for uid, label, variables, h in DASHBOARDS:
            out = d / "screenshots" / f"{label}.png"
            try:
                n = render(uid, out, from_ms, to_ms, variables, height=h)
                print("   rendered %-26s %6.1f KB" % (label, n / 1024))
            except Exception as exc:
                failures.append(f"{name}/{label}: {exc}")
                print(f"   FAILED   {label}: {exc}")
        # arm-scoped k6 dashboard
        out = d / "screenshots" / "k6-client.png"
        try:
            n = render("ecommerce-ev-catalog-reserve", out, from_ms, to_ms, {"arm": name}, height=900)
            print("   rendered %-26s %6.1f KB" % ("k6-client", n / 1024))
        except Exception as exc:
            failures.append(f"{name}/k6-client: {exc}")
            print(f"   FAILED   k6-client: {exc}")

    if a.audit:
        pathlib.Path(a.audit).parent.mkdir(parents=True, exist_ok=True)
        json.dump(audit, open(a.audit, "w"), indent=2, ensure_ascii=False)
        print(f"\naudit -> {a.audit}")
    if failures:
        print("\n%d failure(s):" % len(failures), file=sys.stderr)
        for f in failures:
            print("  " + f, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
