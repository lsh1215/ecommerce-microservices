#!/usr/bin/env python3
"""Patch monitoring dashboards in k8s/monitoring/dashboards/.

Strategy: load dashboards (which are stored as JSON inside a ConfigMap YAML
file), patch their JSON in-place, then write back using `kubectl create
configmap --from-file=... --dry-run=client -o yaml` to avoid YAML escape
issues with embedded JSON strings.
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

import yaml

DASH_DIR = Path("k8s/monitoring/dashboards")

FAILURE_RATE_STEPS = [
    {"value": None, "color": "green"},
    {"value": 0.05, "color": "yellow"},
    {"value": 0.3, "color": "red"},
]


def load_dashboard(yml_path: Path) -> tuple[dict, str]:
    raw = yaml.safe_load(yml_path.read_text())
    json_keys = list(raw.get("data", {}).keys())
    if not json_keys:
        raise SystemExit(f"{yml_path}: no data keys")
    json_key = json_keys[0]
    return raw, json_key


def write_dashboard(yml_path: Path, raw: dict, json_key: str, dashboard: dict) -> None:
    """Write back via kubectl dry-run to avoid YAML escape pitfalls.

    The ConfigMap's `data.<filename>` value is a literal JSON string. PyYAML's
    safe_dump silently breaks long JSON strings via line-folding which corrupts
    the embedded escape sequences. kubectl's literal block scalar emit is the
    safe path.
    """
    cm_name = raw["metadata"]["name"]
    namespace = raw["metadata"].get("namespace", "monitoring")
    labels = raw["metadata"].get("labels", {})

    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
        json.dump(dashboard, f, indent=2, ensure_ascii=False)
        tmp_json = f.name

    args = [
        "kubectl", "create", "configmap", cm_name,
        f"--namespace={namespace}",
        f"--from-file={json_key}={tmp_json}",
        "--dry-run=client", "-o", "yaml",
    ]
    out = subprocess.check_output(args).decode()
    cm = yaml.safe_load(out)
    cm.setdefault("metadata", {}).setdefault("labels", {}).update(labels)
    yml_path.write_text(yaml.safe_dump(cm, default_flow_style=False, allow_unicode=True))
    Path(tmp_json).unlink(missing_ok=True)


def walk_panels(dashboard: dict):
    """Yield every panel reachable from rows[] (legacy) or panels[] (modern)."""
    def emit(panel):
        yield panel
        for sub in panel.get("panels", []) or []:
            yield from emit(sub)
    for row in dashboard.get("rows", []) or []:
        for panel in row.get("panels", []) or []:
            yield from emit(panel)
    for panel in dashboard.get("panels", []) or []:
        yield from emit(panel)


def fix_overview_thresholds() -> int:
    yml = DASH_DIR / "dashboard-ecommerce-overview.yml"
    raw, key = load_dashboard(yml)
    dash = json.loads(raw["data"][key])
    touched = 0
    for panel in walk_panels(dash):
        targets = panel.get("targets") or []
        exprs = " ".join(t.get("expr", "") for t in targets)
        if "k6_http_req_failed" not in exprs:
            continue
        fc = panel.setdefault("fieldConfig", {}).setdefault("defaults", {})
        thr = fc.setdefault("thresholds", {})
        thr["mode"] = "absolute"
        thr["steps"] = [dict(s) for s in FAILURE_RATE_STEPS]
        fc.setdefault("color", {})["mode"] = "thresholds"
        if fc.get("unit") not in ("percentunit", "percent"):
            fc["unit"] = "percentunit"
        touched += 1
    write_dashboard(yml, raw, key, dash)
    return touched


def patch_label_schema(expr: str) -> str:
    if not isinstance(expr, str):
        return expr
    out = expr
    out = re.sub(r'\bapp\s*=\s*"', 'job="', out)
    out = re.sub(r'\bapp\s*=~\s*"', 'job=~"', out)
    out = re.sub(r'\bapplication\s*=\s*"', 'job="', out)
    out = re.sub(r'\barea\s*=\s*"heap"', 'jvm_memory_type="heap"', out)
    out = re.sub(r'\barea\s*=\s*"nonheap"', 'jvm_memory_type="non_heap"', out)
    out = re.sub(r'\barea\s*=\s*"non_heap"', 'jvm_memory_type="non_heap"', out)
    # Multi-value friendly: switch single-equals on template-driven labels to
    # regex-equals so $instance="All" or multi-select work.
    out = re.sub(r'\bjob\s*=\s*"\$application"', 'job=~"$application"', out)
    out = re.sub(r'\binstance\s*=\s*"\$instance"', 'instance=~"$instance"', out)
    return out


def fix_jvm_labels() -> tuple[int, int]:
    yml = DASH_DIR / "grafana-dashboard-jvm-micrometer.yml"
    raw, key = load_dashboard(yml)
    dash = json.loads(raw["data"][key])

    label_count = 0
    for panel in walk_panels(dash):
        for target in panel.get("targets", []) or []:
            for field in ("expr", "query"):
                old = target.get(field)
                new = patch_label_schema(old)
                if new != old:
                    target[field] = new
                    label_count += 1
    for ann in dash.get("annotations", {}).get("list", []) or []:
        old = ann.get("expr")
        new = patch_label_schema(old)
        if new != old:
            ann["expr"] = new
            label_count += 1

    var_count = 0
    for var in dash.get("templating", {}).get("list", []) or []:
        name = var.get("name")
        if name == "application":
            var["query"] = 'label_values(jvm_memory_used_bytes, job)'
            var["definition"] = var["query"]
            var["regex"] = "/^service-.*$/"
            var["current"] = {"selected": True, "text": "service-order", "value": "service-order"}
            var["includeAll"] = True
            var["multi"] = True
            var_count += 1
        elif name == "instance":
            var["query"] = 'label_values(jvm_memory_used_bytes{job=~"$application"}, instance)'
            var["definition"] = var["query"]
            var["includeAll"] = True
            var["multi"] = True
            var["allValue"] = ".*"
            var["current"] = {"selected": True, "text": "All", "value": "$__all"}
            var_count += 1
        else:
            q = var.get("query", "")
            new_q = patch_label_schema(q)
            if new_q != q:
                var["query"] = new_q
                var["definition"] = new_q
                var_count += 1

    write_dashboard(yml, raw, key, dash)
    return label_count, var_count


def main() -> int:
    overview_panels = fix_overview_thresholds()
    label_subs, var_subs = fix_jvm_labels()
    print(f"overview: thresholds restored on {overview_panels} k6 failure-rate panels")
    print(f"jvm-micrometer: {label_subs} label substitutions, {var_subs} template-variable rewrites")
    return 0


if __name__ == "__main__":
    sys.exit(main())
