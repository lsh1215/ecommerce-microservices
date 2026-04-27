#!/usr/bin/env python3
"""Add a `testid` template variable to dashboard-ecommerce-overview and rewrite
the k6 panels so they filter by `testid=~"$testid"` and use proper sum/clamp
formulas (instead of averaging averages, which yields meaningless numbers).

Idempotent — re-runs are safe."""

import json
import pathlib
import yaml

DASH_PATH = pathlib.Path(__file__).resolve().parent.parent / "k8s" / "monitoring" / "dashboards" / "dashboard-ecommerce-overview.yml"

cm = yaml.safe_load(DASH_PATH.read_text())
key = next(iter(cm["data"]))
dash = json.loads(cm["data"][key])

# 1) Add `testid` template variable if absent.
templating = dash.setdefault("templating", {})
varlist = templating.setdefault("list", [])
# Drop and re-create. Use query-type with includeAll so that URL var-testid
# can pin the value while the dropdown still populates from Prometheus.
varlist[:] = [v for v in varlist if v.get("name") != "testid"]
varlist.append({
    "name": "testid",
    "label": "k6 testid",
    "type": "query",
    "datasource": {"type": "prometheus", "uid": "prometheus"},
    "definition": "label_values(k6_http_reqs_total, testid)",
    "query": "label_values(k6_http_reqs_total, testid)",
    "refresh": 2,
    "regex": "",
    "sort": 1,
    "current": {"selected": True, "text": "All", "value": "$__all"},
    "options": [],
    "includeAll": True,
    "multi": False,
    "allValue": ".+",
    "hide": 0,
})

# 2) Rewrite k6 panel queries.
NEW_QUERIES = {
    19: 'max(max_over_time(k6_vus{testid=~"$testid"}[$__range]))',
    20: 'sum(rate(k6_iterations_total{testid=~"$testid"}[$__rate_interval]))',
    21: 'max(k6_http_req_duration_p95{testid=~"$testid"}) * 1000',
    22: '(sum(max_over_time(k6_http_reqs_total{testid=~"$testid", expected_response="false"}[$__range])) or vector(0)) / clamp_min(sum(max_over_time(k6_http_reqs_total{testid=~"$testid"}[$__range])), 1)',
}

# Panel 23 has 3 separate trend lines (avg / p95 / p99) — keep them but filtered.
PANEL_23_TARGETS = [
    {"refId": "A", "expr": 'avg(k6_http_req_duration_avg{testid=~"$testid"}) * 1000', "legendFormat": "avg"},
    {"refId": "B", "expr": 'avg(k6_http_req_duration_p95{testid=~"$testid"}) * 1000', "legendFormat": "p95"},
    {"refId": "C", "expr": 'avg(k6_http_req_duration_p99{testid=~"$testid"}) * 1000', "legendFormat": "p99"},
]


def walk(panels):
    for p in panels:
        pid = p.get("id")
        if pid in NEW_QUERIES:
            new = NEW_QUERIES[pid]
            for t in p.get("targets", []):
                t["expr"] = new
                t["legendFormat"] = ""
            print(f"[patch] panel #{pid} '{p.get('title')}' -> {new[:80]}…")
        elif pid == 23:
            ds = (p.get("targets") or [{}])[0].get("datasource", {"type": "prometheus", "uid": "prometheus"})
            new_targets = [{**t, "datasource": ds} for t in PANEL_23_TARGETS]
            p["targets"] = new_targets
            print(f"[patch] panel #{pid} '{p.get('title')}' -> 3 targets (avg/p95/p99) with testid filter")
        walk(p.get("panels", []))


walk(dash.get("panels", []))

cm["data"][key] = json.dumps(dash, ensure_ascii=False)
DASH_PATH.write_text(yaml.safe_dump(cm, sort_keys=False, allow_unicode=True))
print(f"[done] wrote {DASH_PATH}")
