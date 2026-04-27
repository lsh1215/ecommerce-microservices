#!/usr/bin/env python3
"""Exhaustively verify every panel in every dashboard actually returns data.

Reads each dashboard ConfigMap, walks the modern `panels[]` tree (including
collapsed rows) AND the legacy `rows[].panels` tree, extracts every target's
expression, substitutes the dashboard's template variables with a concrete
value, and executes the query against the live Prometheus / Loki services.

Output: markdown table per dashboard, plus a totals summary. A panel is
"OK" if every one of its targets returns at least one series; "PARTIAL"
if some targets are empty; "EMPTY" if none return data; "ERROR" on
query failure.

Expected runtime: ~90 s (338 targets × ~250 ms each via kubectl exec).
"""

from __future__ import annotations

import json
import pathlib
import re
import subprocess
import sys
import urllib.parse
from collections import defaultdict

import yaml

DASH_DIR = pathlib.Path(__file__).resolve().parent.parent / "k8s" / "monitoring" / "dashboards"

# Default values we substitute for template variables. Picked so every
# query that is *structurally* valid against our cluster returns at
# least one series.
VAR_DEFAULTS = {
    # JVM micrometer / Spring Boot
    "application": "service-product",
    "app": "service-product",
    "instance": ".+",
    "jvm_memory_pool_heap": "G1 Eden Space",
    "jvm_memory_pool_nonheap": "Compressed Class Space",
    "jvm_buffer_pool": "direct",
    # Pods / k8s
    "namespace": "ecommerce",
    "pod": ".+",
    "container": ".+",
    "node": ".+",
    "cluster": ".*",
    "resolution": "5m",
    # Kafka exporter
    "cluster_label": "ecommerce-kafka",
    "topic": ".+",
    # MySQL
    "host": ".+",
    "service": "mysqld-exporter",
    # k6 — testid/scenario are resolved via label_values; the `.+` default
    # is used as a fallback regex when resolution fails. Since panel queries
    # filter `testid=~"$testid"`, substituting `.+` means "match any testid".
    "testid": ".+",
    "scenario": ".+",
    # Time-grain variables
    "__interval": "1m",
    "__rate_interval": "1m",
    "__range": "5m",
    "__range_s": "300",
    "__range_ms": "300000",
    "__auto_interval_interval": "1m",
    "__auto_interval_resolution": "5m",
    "__auto_interval_auto": "1m",
    "__auto": "1m",
    "interval": "1m",
    "rate_interval": "1m",
    # Catch-all — label_values / ad-hoc
    "__all": ".+",
    "__auto_interval_resolution": "5m",
}


def kubectl_exec(pod_selector, namespace, cmd):
    """Run wget inside a live pod and return stdout."""
    full = [
        "gcloud", "compute", "ssh", "ecommerce-k3s",
        "--zone=asia-northeast3-a",
        "--command",
        f"sudo kubectl -n {namespace} exec {pod_selector} -- {cmd} 2>/dev/null",
    ]
    r = subprocess.run(full, capture_output=True, text=True, timeout=30)
    return r.stdout


def query_prometheus_batch(queries):
    """Run many Prom queries in one SSH trip — one shell heredoc, one connection."""
    if not queries:
        return {}
    remote = ["set -e"]
    for i, q in enumerate(queries):
        enc = urllib.parse.quote(q)
        remote.append(
            f'echo "===QBEGIN:{i}==="; '
            f'sudo kubectl -n monitoring exec deploy/prometheus -- '
            f'wget -qO- "http://localhost:9090/api/v1/query?query={enc}" 2>/dev/null || echo "ERROR"'
        )
    script = "; ".join(remote)
    r = subprocess.run(
        ["gcloud", "compute", "ssh", "ecommerce-k3s", "--zone=asia-northeast3-a", "--command", script],
        capture_output=True, text=True, timeout=600,
    )
    out = r.stdout
    results = {}
    # Split on markers
    parts = re.split(r"===QBEGIN:(\d+)===\s*", out)
    # parts: ['', '0', '<json0>', '1', '<json1>', ...]
    for idx in range(1, len(parts), 2):
        i = int(parts[idx])
        blob = parts[idx + 1].strip()
        try:
            data = json.loads(blob)
            results[i] = data.get("data", {}).get("result", [])
        except Exception:
            results[i] = None  # error
    return results


def query_loki_batch(queries):
    if not queries:
        return {}
    remote = ["set -e"]
    for i, q in enumerate(queries):
        enc = urllib.parse.quote(q)
        remote.append(
            f'echo "===LBEGIN:{i}==="; '
            f'sudo kubectl -n monitoring exec deploy/loki -- '
            f'wget -qO- "http://localhost:3100/loki/api/v1/query_range?query={enc}&limit=1&start=$(date -u -d \'5 min ago\' +%s)000000000&end=$(date -u +%s)000000000" 2>/dev/null || echo ERROR'
        )
    script = "; ".join(remote)
    r = subprocess.run(
        ["gcloud", "compute", "ssh", "ecommerce-k3s", "--zone=asia-northeast3-a", "--command", script],
        capture_output=True, text=True, timeout=600,
    )
    out = r.stdout
    results = {}
    parts = re.split(r"===LBEGIN:(\d+)===\s*", out)
    for idx in range(1, len(parts), 2):
        i = int(parts[idx])
        blob = parts[idx + 1].strip()
        try:
            data = json.loads(blob)
            results[i] = data.get("data", {}).get("result", [])
        except Exception:
            results[i] = None
    return results


def substitute(expr: str, local_vars: dict) -> str:
    """Replace ${var:format} and $var tokens with concrete values.

    Iterates up to 3 times so that a variable resolving to another
    variable (e.g. `$interval` → `$__auto_interval_interval` → `1m`)
    fully collapses.
    """
    for _ in range(3):
        prev = expr
        expr = re.sub(
            r"\$\{([a-zA-Z_][\w]*)(?::[^}]*)?\}",
            lambda m: local_vars.get(m.group(1), VAR_DEFAULTS.get(m.group(1), ".+")),
            expr,
        )
        expr = re.sub(
            r"\$(__[a-z_]+|[a-zA-Z_][\w]*)",
            lambda m: local_vars.get(m.group(1), VAR_DEFAULTS.get(m.group(1), ".+")),
            expr,
        )
        if expr == prev:
            break
    return expr


def collect_panels(dashboard):
    """Yield every renderable panel (not rows)."""
    def walk(obj):
        if isinstance(obj, dict):
            for p in obj.get("panels", []) or []:
                if p.get("type") == "row":
                    walk(p)
                else:
                    yield p
            for r in obj.get("rows", []) or []:
                for p in r.get("panels", []) or []:
                    yield p
    yield from walk(dashboard)


def resolve_label_values(query: str):
    """Execute a Grafana `label_values(...)` expression against live Prometheus.

    Supports:
      label_values(metric_selector, label)     -> series label API
      label_values(label)                      -> top-level label values API
    Returns the first value or None.
    """
    q = query.strip()
    m = re.match(r"label_values\((.+)\s*,\s*([a-zA-Z_][\w]*)\s*\)\s*$", q)
    if m:
        matcher = m.group(1).strip()
        label = m.group(2)
        enc = urllib.parse.quote(matcher)
        cmd = (
            f'sudo kubectl -n monitoring exec deploy/prometheus -- '
            f'wget -qO- "http://localhost:9090/api/v1/series?match[]={enc}" 2>/dev/null'
        )
        r = subprocess.run(
            ["gcloud", "compute", "ssh", "ecommerce-k3s", "--zone=asia-northeast3-a", "--command", cmd],
            capture_output=True, text=True, timeout=30,
        )
        try:
            series = json.loads(r.stdout)["data"]
            vals = {s[label] for s in series if label in s}
            return sorted(vals)[0] if vals else None
        except Exception:
            return None
    m2 = re.match(r"label_values\(([a-zA-Z_][\w]*)\)\s*$", q)
    if m2:
        label = m2.group(1)
        cmd = (
            f'sudo kubectl -n monitoring exec deploy/prometheus -- '
            f'wget -qO- "http://localhost:9090/api/v1/label/{label}/values" 2>/dev/null'
        )
        r = subprocess.run(
            ["gcloud", "compute", "ssh", "ecommerce-k3s", "--zone=asia-northeast3-a", "--command", cmd],
            capture_output=True, text=True, timeout=30,
        )
        try:
            vals = json.loads(r.stdout)["data"]
            return sorted(vals)[0] if vals else None
        except Exception:
            return None
    return None


SKIP_APP_VALUES = {
    "alloy", "grafana", "loki", "mysqld-exporter", "prometheus", "tempo",
    "kafka-exporter", "kube-state-metrics", "node-exporter",
}

# Template-variable names where the audit should always use a regex
# wildcard rather than resolving against a specific label value. Avoids
# pinning to a stale testid / run identifier that has decayed out of
# Prometheus' staleness window.
WILDCARD_ON_RESOLVE = {"testid", "scenario"}


def resolve_label_values_all(query: str):
    """Like resolve_label_values but returns all candidate values."""
    q = query.strip()
    m = re.match(r"label_values\((.+)\s*,\s*([a-zA-Z_][\w]*)\s*\)\s*$", q)
    if m:
        matcher = m.group(1).strip()
        label = m.group(2)
        enc = urllib.parse.quote(matcher)
        cmd = (
            f'sudo kubectl -n monitoring exec deploy/prometheus -- '
            f'wget -qO- "http://localhost:9090/api/v1/series?match[]={enc}" 2>/dev/null'
        )
        r = subprocess.run(
            ["gcloud", "compute", "ssh", "ecommerce-k3s", "--zone=asia-northeast3-a", "--command", cmd],
            capture_output=True, text=True, timeout=30,
        )
        try:
            series = json.loads(r.stdout)["data"]
            return sorted({s[label] for s in series if label in s})
        except Exception:
            return []
    m2 = re.match(r"label_values\(([a-zA-Z_][\w]*)\)\s*$", q)
    if m2:
        label = m2.group(1)
        cmd = (
            f'sudo kubectl -n monitoring exec deploy/prometheus -- '
            f'wget -qO- "http://localhost:9090/api/v1/label/{label}/values" 2>/dev/null'
        )
        r = subprocess.run(
            ["gcloud", "compute", "ssh", "ecommerce-k3s", "--zone=asia-northeast3-a", "--command", cmd],
            capture_output=True, text=True, timeout=30,
        )
        try:
            return sorted(json.loads(r.stdout)["data"])
        except Exception:
            return []
    # metrics(prefix) — Grafana-specific, returns metric names matching prefix
    m3 = re.match(r"metrics\((.+)\)\s*$", q)
    if m3:
        prefix = m3.group(1).strip().strip('"\'')
        cmd = (
            f'sudo kubectl -n monitoring exec deploy/prometheus -- '
            f'wget -qO- "http://localhost:9090/api/v1/label/__name__/values" 2>/dev/null'
        )
        r = subprocess.run(
            ["gcloud", "compute", "ssh", "ecommerce-k3s", "--zone=asia-northeast3-a", "--command", cmd],
            capture_output=True, text=True, timeout=30,
        )
        try:
            names = json.loads(r.stdout)["data"]
            # Return the suffix after prefix (dashboards use $var concatenated
            # into a metric name stem, e.g. k6_http_req_duration_$quantile_stat)
            out = []
            for n in sorted(names):
                if n.startswith(prefix):
                    # Strip prefix to get what the variable holds
                    out.append(n[len(prefix):] or n)
            return out
        except Exception:
            return []
    return []


def dashboard_vars(dashboard):
    """Resolve template variables into concrete first-value substitutions.

    Strategy: for each variable, query label_values to get candidates,
    then pick the first candidate that *isn't* a well-known exporter
    (Alloy/Prometheus/Loki/Grafana/mysqld-exporter) so we land on an
    actual ecommerce service. This matches what a human would pick
    when loading the dashboard for the first time.
    """
    out = {}
    for v in dashboard.get("templating", {}).get("list", []) or []:
        name = v.get("name")
        if not name:
            continue
        cur = v.get("current") or {}
        val = cur.get("value") or cur.get("text")
        if isinstance(val, list):
            val = val[0] if val else None
        if val and val not in ("$__all", "All", ""):
            out[name] = val
            continue
        if name in WILDCARD_ON_RESOLVE:
            out[name] = ".+"
            continue
        if v.get("type") == "query":
            q = v.get("query")
            if isinstance(q, dict):
                q = q.get("query", "")
            if isinstance(q, str) and q:
                resolved_q = substitute(q, out)
                candidates = resolve_label_values_all(resolved_q)
                # Prefer non-exporter candidates (so app resolves to service-*)
                filtered = [c for c in candidates if c not in SKIP_APP_VALUES]
                pick = filtered[0] if filtered else (candidates[0] if candidates else None)
                if pick:
                    out[name] = pick
                    continue
        # textbox / constant variables: default to empty string (user would
        # type a filter) — `|= "$search"` with empty `$search` becomes
        # `|= ""` which matches every line.
        if v.get("type") in ("textbox", "constant"):
            cur_val = (v.get("current") or {}).get("value")
            out[name] = cur_val if cur_val else ""
            continue
        opts = v.get("options") or []
        for o in opts:
            ov = o.get("value")
            if ov and ov not in ("$__all", "All"):
                out[name] = ov
                break
    return out


def panel_datasource(panel):
    ds = panel.get("datasource")
    if isinstance(ds, dict):
        return ds.get("type") or "prometheus"
    if isinstance(ds, str):
        return "loki" if "loki" in ds.lower() else "prometheus"
    return "prometheus"


def audit():
    totals = defaultdict(lambda: {"ok": 0, "partial": 0, "empty": 0, "error": 0, "skip": 0})
    panel_reports = defaultdict(list)

    # First pass: collect every (dashboard, panel, target, resolved_expr, ds_type)
    items = []  # each: (dash_name, panel_id, panel_title, ds_type, raw, resolved, ref_key)
    for dash_file in sorted(DASH_DIR.glob("dashboard-*.yml")):
        cm = yaml.safe_load(dash_file.read_text())
        dash = json.loads(cm["data"][next(iter(cm["data"]))])
        local_vars = dashboard_vars(dash)
        for panel in collect_panels(dash):
            ds_type_default = panel_datasource(panel)
            targets = panel.get("targets", []) or []
            if not targets:
                # treat as skip (no query to verify — typically a text panel)
                totals[dash_file.stem]["skip"] += 1
                panel_reports[dash_file.stem].append(
                    (panel.get("id"), panel.get("title", "?"), "SKIP", "no targets", [])
                )
                continue
            for ti, t in enumerate(targets):
                ds_type = ds_type_default
                if isinstance(t.get("datasource"), dict):
                    ds_type = t["datasource"].get("type", ds_type)
                expr = t.get("expr") or t.get("query") or t.get("rawQuery") or ""
                if not isinstance(expr, str) or not expr.strip():
                    continue
                resolved = substitute(expr, local_vars)
                items.append({
                    "dash": dash_file.stem,
                    "panel_id": panel.get("id"),
                    "panel_title": panel.get("title", "?"),
                    "ti": ti,
                    "ds": ds_type,
                    "raw": expr,
                    "resolved": resolved,
                })

    # Batch queries by datasource
    prom = [it["resolved"] for it in items if it["ds"] == "prometheus"]
    prom_idx = [i for i, it in enumerate(items) if it["ds"] == "prometheus"]
    loki = [it["resolved"] for it in items if it["ds"] == "loki"]
    loki_idx = [i for i, it in enumerate(items) if it["ds"] == "loki"]

    print(f"Querying {len(prom)} Prometheus expressions, {len(loki)} Loki expressions…",
          file=sys.stderr, flush=True)

    # Chunk prom queries to keep SSH command size manageable
    CHUNK = 30
    prom_results = {}
    for start in range(0, len(prom), CHUNK):
        chunk = prom[start:start + CHUNK]
        res = query_prometheus_batch(chunk)
        for k, v in res.items():
            prom_results[prom_idx[start + k]] = v
        print(f"  prom {start + len(chunk)}/{len(prom)}", file=sys.stderr, flush=True)

    loki_results = {}
    for start in range(0, len(loki), CHUNK):
        chunk = loki[start:start + CHUNK]
        res = query_loki_batch(chunk)
        for k, v in res.items():
            loki_results[loki_idx[start + k]] = v
        print(f"  loki {start + len(chunk)}/{len(loki)}", file=sys.stderr, flush=True)

    # Aggregate per-panel outcomes
    per_panel = defaultdict(lambda: {"total": 0, "hit": 0, "err": 0, "resolved_samples": []})
    for i, it in enumerate(items):
        key = (it["dash"], it["panel_id"], it["panel_title"])
        per_panel[key]["total"] += 1
        if it["ds"] == "prometheus":
            r = prom_results.get(i)
        else:
            r = loki_results.get(i)
        if r is None:
            per_panel[key]["err"] += 1
            per_panel[key]["resolved_samples"].append((it["resolved"], "ERR"))
        elif len(r) == 0:
            per_panel[key]["resolved_samples"].append((it["resolved"], "0"))
        else:
            per_panel[key]["hit"] += 1
            per_panel[key]["resolved_samples"].append((it["resolved"], f"{len(r)}s"))

    for (dash, pid, title), info in per_panel.items():
        total = info["total"]
        hit = info["hit"]
        err = info["err"]
        if err > 0:
            status = "ERROR"
            totals[dash]["error"] += 1
        elif hit == total:
            status = "OK"
            totals[dash]["ok"] += 1
        elif hit == 0:
            status = "EMPTY"
            totals[dash]["empty"] += 1
        else:
            status = "PARTIAL"
            totals[dash]["partial"] += 1
        panel_reports[dash].append((pid, title, status, f"{hit}/{total}", info["resolved_samples"]))

    # Emit markdown
    print("\n# Dashboard audit — per-panel results\n")
    for dash in sorted(panel_reports):
        t = totals[dash]
        print(f"\n## {dash}")
        print(f"**OK**: {t['ok']}   **PARTIAL**: {t['partial']}   **EMPTY**: {t['empty']}   "
              f"**ERROR**: {t['error']}   **SKIP**: {t['skip']}")
        print("\n| ID | Panel | Status | Hit/Total | First failing query |")
        print("|---|---|---|---|---|")
        for pid, title, status, hit, samples in sorted(panel_reports[dash], key=lambda r: (r[2] != "OK", r[1])):
            failing = next((s[0] for s in samples if s[1] != "0" and "s" not in s[1]), "")
            if status in ("OK", "SKIP"):
                failing = ""
            else:
                failing = next((s[0] for s in samples if s[1] in ("0", "ERR")), "")
            short = (failing[:90] + "…") if len(failing) > 90 else failing
            print(f"| {pid} | {title} | **{status}** | {hit} | `{short}` |")
    print("\n---\n## Overall")
    all_t = {"ok":0,"partial":0,"empty":0,"error":0,"skip":0}
    for dash, t in totals.items():
        for k in all_t:
            all_t[k] += t[k]
    print(f"OK: {all_t['ok']}   PARTIAL: {all_t['partial']}   EMPTY: {all_t['empty']}   "
          f"ERROR: {all_t['error']}   SKIP (no-targets): {all_t['skip']}")

    return all_t, panel_reports


def load_expectations(path: str):
    """Load phase-panel-expectations.json (or yml) into a list of rules.
    Each rule is a dict: {dashboard_pattern, panel_pattern?, metric_pattern?,
                          disposition: idle-by-phase|metric-unavailable-permanent|deferred|audit-false-positive,
                          exceptions: [tag,...]}.
    Returns [] if path is missing.
    """
    p = pathlib.Path(path)
    if not p.exists():
        return []
    txt = p.read_text()
    if path.endswith(".json"):
        return json.loads(txt)
    return yaml.safe_load(txt) or []


def panel_disposition(rules, dash, panel_title, expr_sample, active_tags):
    """Match a panel against expectations rules. Returns (category, rule) or
    (None, None) if no match.
    """
    for rule in rules:
        if "dashboard_pattern" in rule and not re.search(rule["dashboard_pattern"], dash):
            continue
        if "panel_pattern" in rule and not re.search(rule["panel_pattern"], panel_title or ""):
            continue
        if "metric_pattern" in rule and not re.search(rule["metric_pattern"], expr_sample or ""):
            continue
        # Exceptions: if any active_tag matches an exception, the rule is suppressed.
        if rule.get("exceptions") and any(tag in rule["exceptions"] for tag in active_tags):
            continue
        return rule.get("disposition", "deferred"), rule
    return None, None


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true",
                        help="Exit non-zero if any panel is EMPTY/PARTIAL/ERROR without a "
                             "documented disposition in --expectations.")
    parser.add_argument("--expectations", default=".omc/research/phase-panel-expectations.json",
                        help="Path to expectations file (json or yml). Missing file = no rules.")
    parser.add_argument("--active-tags", default="",
                        help="Comma-separated tags identifying the current run context "
                             "(e.g., 'under-T3-C1', 'phase4'). Used to suppress 'idle-by-phase' "
                             "rules for panels that should be live in this run.")
    args = parser.parse_args()

    overall, panel_reports = audit()

    if not args.strict:
        sys.exit(0)

    rules = load_expectations(args.expectations)
    active_tags = [t.strip() for t in args.active_tags.split(",") if t.strip()]
    unaccounted = []
    accounted = {"idle-by-phase": 0, "metric-unavailable-permanent": 0,
                 "deferred": 0, "audit-false-positive": 0}
    for dash, panels in panel_reports.items():
        for pid, title, status, hit, samples in panels:
            if status in ("OK", "SKIP"):
                continue
            expr = samples[0][0] if samples else ""
            disp, rule = panel_disposition(rules, dash, title, expr, active_tags)
            if disp is None:
                unaccounted.append((dash, pid, title, status))
            else:
                accounted[disp] = accounted.get(disp, 0) + 1

    print("\n## Strict Mode")
    print(f"accounted: {accounted}")
    print(f"unaccounted: {len(unaccounted)}")
    if unaccounted:
        print("\nFirst 20 unaccounted:")
        for dash, pid, title, status in unaccounted[:20]:
            print(f"  - {dash} #{pid} '{title}' = {status}")
        sys.exit(2)
    sys.exit(0)
