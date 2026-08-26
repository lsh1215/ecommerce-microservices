#!/usr/bin/env python3
"""Numeric-integrity gate for load-test evidence (ralplan R6).

Canonical numeric source is the k6 summary JSON (--summary-export) plus, optionally,
a dashboard-audit JSON holding the values a human/vision read off the Grafana panels.
This tool NEVER invents headline numbers: it validates the k6 JSON internally and
reconciles it against the dashboard-audit values within tolerance.

Enforced invariants (each a blocking check):
  1. avg throughput <= peak throughput (kills the gist "avg 39.32 > peak 30.5" defect).
  2. both p95 AND p99 are present (kills the SAGA p95/p99-mixing defect).
  3. error-rate <= claim.max_error_rate (when provided).
  4. p95 <= claim.slo_p95_ms (when provided).
  5. every dashboard-audit metric agrees with the k6 value within +/- tolerance
     (default 5%) (kills table<->dashboard mismatch).

Usage:
  verify-evidence.py --k6 measure-k6-summary.json --claim claim.json \
                     [--dashboard dashboard-audit.json] [--tolerance 0.05]
  verify-evidence.py --selftest

claim.json shape (all optional except throughput_metric):
  {
    "phase": "saga",
    "throughput_metric": "http_reqs",   # which k6 metric defines throughput
    "max_error_rate": 0.0,               # fraction, e.g. 0.0 or 0.01
    "slo_p95_ms": 600,                   # p95 SLO in ms for the primary duration metric
    "duration_metric": "http_req_duration"
  }

dashboard-audit.json shape: {"p95_ms": 387.0, "p99_ms": 1010.0, "peak_rps": 40.1, ...}
Keys are matched to k6-derived values by name; only overlapping keys are reconciled.

Exit code 0 = clean, 1 = blocking failure, 2 = usage/parse error.
"""
import argparse
import json
import sys


def _get(d, *path, default=None):
    cur = d
    for p in path:
        if not isinstance(cur, dict) or p not in cur:
            return default
        cur = cur[p]
    return cur


def _mv(metrics, name):
    """Return a metric's value dict, handling both handleSummary (nested under
    'values') and --summary-export (values at the metric top level) formats."""
    m = metrics.get(name, {})
    return m.get("values", m) if isinstance(m, dict) else {}


def extract_k6(summary, phase="measure"):
    """Pull the canonical numbers out of a k6 summary. Prefers the phase-tagged
    (warm measure) duration metric when present, else the untagged one."""
    metrics = summary.get("metrics", {})
    out = {}

    # duration: prefer a phase-tagged metric (warm measure window) over cumulative
    dur = {}
    for name in (f"order_create_ms{{phase:{phase}}}", f"http_req_duration{{phase:{phase}}}",
                 "order_create_ms", "http_req_duration"):
        d = _mv(metrics, name)
        if "p(95)" in d:
            dur = d
            out["duration_metric"] = name
            break
    if "p(95)" in dur:
        out["p95_ms"] = float(dur["p(95)"])
    if "p(99)" in dur:
        out["p99_ms"] = float(dur["p(99)"])
    if "avg" in dur:
        out["avg_ms"] = float(dur["avg"])

    failed = _mv(metrics, f"http_req_failed{{phase:{phase}}}") or _mv(metrics, "http_req_failed")
    if "rate" in failed:
        out["error_rate"] = float(failed["rate"])
    elif "value" in failed:
        out["error_rate"] = float(failed["value"])

    reqs = _mv(metrics, "http_reqs")
    if "rate" in reqs:
        out["avg_rps"] = float(reqs["rate"])
    if "count" in reqs:
        out["req_count"] = float(reqs["count"])

    iters = _mv(metrics, "iterations")
    if "rate" in iters:
        out["avg_iter_rate"] = float(iters["rate"])

    for k in ("peak_rps", "peak_iter_rate", "peak_req_rate"):
        g = _mv(metrics, k)
        for vk in ("max", "value", "rate"):
            if vk in g:
                out["peak_rps"] = float(g[vk])
                break
        if "peak_rps" in out:
            break
    return out


def verify(k6vals, claim, dashboard, tolerance):
    findings = []  # (level, message)

    # 1. avg <= peak throughput
    avg_rps = k6vals.get("avg_rps")
    peak = k6vals.get("peak_rps") or (dashboard or {}).get("peak_rps")
    if avg_rps is not None and peak is not None:
        if avg_rps > peak * (1 + 1e-9):
            findings.append(("BLOCK",
                f"avg throughput {avg_rps:.2f}/s exceeds peak {peak:.2f}/s (impossible; the gist defect)"))
    else:
        findings.append(("WARN", "peak throughput not available; cannot assert avg <= peak"))

    # 2. p95 AND p99 present
    if "p95_ms" not in k6vals:
        findings.append(("BLOCK", "p95 missing from k6 summary"))
    if "p99_ms" not in k6vals:
        findings.append(("BLOCK", "p99 missing from k6 summary (SAGA p95/p99-mixing defect risk)"))

    # 3. error-rate bound
    mer = claim.get("max_error_rate")
    er = k6vals.get("error_rate")
    if mer is not None and er is not None and er > mer + 1e-9:
        findings.append(("BLOCK", f"error rate {er:.4f} exceeds claim max {mer:.4f}"))

    # 4. p95 SLO
    slo = claim.get("slo_p95_ms")
    p95 = k6vals.get("p95_ms")
    if slo is not None and p95 is not None and p95 > slo + 1e-9:
        findings.append(("BLOCK", f"p95 {p95:.2f}ms exceeds SLO {slo}ms"))

    # 5. dashboard reconciliation within tolerance
    if dashboard:
        for key, dv in dashboard.items():
            if key in k6vals and isinstance(dv, (int, float)):
                kv = k6vals[key]
                denom = abs(kv) if abs(kv) > 1e-9 else 1.0
                rel = abs(kv - dv) / denom
                if rel > tolerance:
                    findings.append(("BLOCK",
                        f"{key}: dashboard {dv} vs k6 {kv:.3f} differ {rel*100:.1f}% > tol {tolerance*100:.0f}%"))
    return findings


def _flatten_env(canonical):
    """Flatten the canonical env spec into dotted keys for a readable diff."""
    flat = {}
    for pool, spec in (canonical.get("node_pools") or {}).items():
        for key, value in spec.items():
            flat[f"node_pools.{pool}.{key}"] = value
    for workload, spec in (canonical.get("workloads") or {}).items():
        for key, value in spec.items():
            flat[f"workloads.{workload}.{key}"] = value
    return flat


def diff_env(baseline, current):
    """Return (key, baseline_value, current_value) triples for every spec change."""
    left = _flatten_env(baseline.get("_baseline") or baseline)
    right = _flatten_env(current.get("_baseline") or current)
    changes = []
    for key in sorted(set(left) | set(right)):
        before, after = left.get(key, "<absent>"), right.get(key, "<absent>")
        if before != after:
            changes.append((key, before, after))
    return changes


def diff_topology(baseline, current):
    """Replica-count deltas. This is the declared measurement axis, not drift."""
    left = baseline.get("topology") or {}
    right = current.get("topology") or {}
    return [(name, left.get(name, "<absent>"), right.get(name, "<absent>"))
            for name in sorted(set(left) | set(right))
            if left.get(name) != right.get(name)]


def verify_env(env, baseline, allow_change):
    """Hardware-baseline gate.

    The 2026-08-01 defect: service-product silently moved from a 2 vCPU node
    to an 8 vCPU node between test phases, so throughput numbers across posts
    no longer share a baseline. This blocks that class of drift, plus the two
    conditions that make a run meaningless on its own — no nodes, and CPU
    requests that cannot fit the cluster.
    """
    findings = []

    if not env.get("node_pools"):
        findings.append(("BLOCK", "env: cluster reports 0 nodes; this run measured nothing"))

    capacity = env.get("capacity") or {}
    if capacity.get("allocatable_cpu_millis") and not capacity.get("fits", True):
        findings.append(("BLOCK",
            f"env: CPU requests {capacity['requested_cpu_millis']}m exceed allocatable "
            f"{capacity['allocatable_cpu_millis']}m; workloads cannot all schedule"))

    burstable = sorted(name for name, spec in (env.get("workloads") or {}).items()
                       if spec.get("qos") != "Guaranteed")
    if burstable:
        findings.append(("WARN",
            f"env: {len(burstable)} workload(s) not Guaranteed QoS (request != limit) — "
            f"run-to-run variance is not controlled: {', '.join(burstable[:6])}"
            + (" ..." if len(burstable) > 6 else "")))

    if baseline is None:
        findings.append(("WARN", "env: no --baseline-env given; baseline drift not checked"))
        return findings

    # Replica deltas are the declared axis of a scale-out measurement, so they
    # are reported, never blocked. Drift in anything else is blocked.
    for name, before, after in diff_topology(baseline, env):
        findings.append(("INFO", f"env: topology axis — {name} replicas {before} -> {after}"))

    if baseline.get("baseline_fingerprint") == env.get("baseline_fingerprint"):
        return findings

    changes = diff_env(baseline, env)
    level = "WARN" if allow_change else "BLOCK"
    findings.append((level,
        f"env: baseline drift — {baseline.get('baseline_fingerprint')} -> "
        f"{env.get('baseline_fingerprint')} ({len(changes)} spec change(s)); "
        f"every run sharing the old baseline must be re-measured"))
    for key, before, after in changes[:20]:
        findings.append((level, f"env:   {key}: {before} -> {after}"))
    if len(changes) > 20:
        findings.append((level, f"env:   ... {len(changes) - 20} more change(s)"))
    return findings


def run(k6vals, claim, dashboard, tolerance, env=None, baseline_env=None, allow_env_change=False):
    findings = verify(k6vals, claim, dashboard, tolerance)
    if env is not None:
        findings += verify_env(env, baseline_env, allow_env_change)
    blocks = [m for lvl, m in findings if lvl == "BLOCK"]
    warns = [m for lvl, m in findings if lvl == "WARN"]
    infos = [m for lvl, m in findings if lvl == "INFO"]
    print(f"[verify-evidence] phase={claim.get('phase','?')} k6={k6vals}")
    if env is not None:
        print(f"[verify-evidence] env baseline={env.get('baseline_fingerprint')} "
              f"pools={sorted((env.get('node_pools') or {}).keys())}")
    for m in infos:
        print(f"  INFO  {m}")
    for m in warns:
        print(f"  WARN  {m}")
    for m in blocks:
        print(f"  BLOCK {m}")
    if blocks:
        print(f"RESULT: FAIL ({len(blocks)} blocking)")
        return 1
    print("RESULT: PASS")
    return 0


def selftest():
    good_summary = {"metrics": {
        "http_req_duration": {"values": {"avg": 205.0, "p(95)": 387.0, "p(99)": 1010.0}},
        "http_req_failed": {"values": {"rate": 0.0}},
        "http_reqs": {"values": {"rate": 28.5, "count": 4497}},
        "peak_rps": {"values": {"max": 40.1}},
    }}
    claim = {"phase": "saga", "throughput_metric": "http_reqs",
             "max_error_rate": 0.0, "slo_p95_ms": 600, "duration_metric": "http_req_duration"}
    dash_ok = {"p95_ms": 390.0, "p99_ms": 1000.0, "peak_rps": 40.5}

    k6vals = extract_k6(good_summary)
    assert run(k6vals, claim, dash_ok, 0.05) == 0, "good case should PASS"

    # avg > peak (the gist defect) must BLOCK
    bad_peak = {"metrics": {
        "http_req_duration": {"values": {"avg": 205.0, "p(95)": 387.0, "p(99)": 1010.0}},
        "http_req_failed": {"values": {"rate": 0.0}},
        "http_reqs": {"values": {"rate": 39.32, "count": 2382}},
        "peak_rps": {"values": {"max": 30.5}},
    }}
    assert run(extract_k6(bad_peak), claim, None, 0.05) == 1, "avg>peak should FAIL"

    # missing p99 must BLOCK
    no_p99 = {"metrics": {
        "http_req_duration": {"values": {"avg": 42.0, "p(95)": 42.0}},
        "http_reqs": {"values": {"rate": 10.0}}, "peak_rps": {"values": {"max": 12.0}},
    }}
    assert run(extract_k6(no_p99), claim, None, 0.05) == 1, "missing p99 should FAIL"

    # dashboard mismatch > tolerance must BLOCK (the 100x v2http defect analog)
    dash_bad = {"p95_ms": 0.18, "p99_ms": 0.43, "peak_rps": 40.5}
    assert run(k6vals, claim, dash_bad, 0.05) == 1, "dashboard 100x mismatch should FAIL"

    # error-rate over bound must BLOCK
    er_bad = dict(good_summary)
    er_bad = json.loads(json.dumps(good_summary))
    er_bad["metrics"]["http_req_failed"]["values"]["rate"] = 0.12
    assert run(extract_k6(er_bad), claim, dash_ok, 0.05) == 1, "error rate over bound should FAIL"

    # --- hardware-baseline gate (2026-08-01 spec-drift defect) ---
    baseline_env = {
        "baseline_fingerprint": "base0000",
        "topology": {"ecommerce/service-product": 1},
        "node_pools": {"svc-product": {"machine_type": "e2-standard-2", "role": "svc-product", "count": 3}},
        "workloads": {"ecommerce/service-product": {
            "kind": "deployment", "replicas": 1, "cpu_request": 1500, "cpu_limit": 1500,
            "memory_request": 1536, "memory_limit": 1536, "node_selector": {"role": "svc-product"},
            "qos": "Guaranteed"}},
        "capacity": {"requested_cpu_millis": 1500, "allocatable_cpu_millis": 5790, "fits": True},
    }
    baseline_env["_baseline"] = {"node_pools": baseline_env["node_pools"],
                                 "workloads": {k: {kk: vv for kk, vv in v.items()
                                                   if kk not in ("qos", "replicas")}
                                               for k, v in baseline_env["workloads"].items()}}
    same_env = json.loads(json.dumps(baseline_env))
    assert run(k6vals, claim, dash_ok, 0.05, env=same_env, baseline_env=baseline_env) == 0, \
        "identical env should PASS"

    # the scale-out axis: replicas 1 -> 3 with every other spec pinned is the
    # experiment, not drift. Blocking it would defeat the whole re-measurement.
    scaled = json.loads(json.dumps(baseline_env))
    scaled["topology"]["ecommerce/service-product"] = 3
    scaled["workloads"]["ecommerce/service-product"]["replicas"] = 3
    scaled["capacity"]["requested_cpu_millis"] = 4500
    assert run(k6vals, claim, dash_ok, 0.05, env=scaled, baseline_env=baseline_env) == 0, \
        "replica scale-out is the declared axis and must PASS"

    # node machine type changed under us (e2-standard-2 -> e2-standard-8) must BLOCK
    drifted = json.loads(json.dumps(baseline_env))
    drifted["baseline_fingerprint"] = "drift001"
    drifted["node_pools"]["svc-product"]["machine_type"] = "e2-standard-8"
    drifted["_baseline"]["node_pools"]["svc-product"]["machine_type"] = "e2-standard-8"
    assert run(k6vals, claim, dash_ok, 0.05, env=drifted, baseline_env=baseline_env) == 1, \
        "node machine-type drift should FAIL"
    assert run(k6vals, claim, dash_ok, 0.05, env=drifted, baseline_env=baseline_env,
               allow_env_change=True) == 0, "declared env change should PASS"

    # scaling up a pod's limit instead of scaling out must BLOCK
    limit_bump = json.loads(json.dumps(baseline_env))
    limit_bump["baseline_fingerprint"] = "drift002"
    limit_bump["_baseline"]["workloads"]["ecommerce/service-product"]["cpu_limit"] = 6000
    assert run(k6vals, claim, dash_ok, 0.05, env=limit_bump, baseline_env=baseline_env) == 1, \
        "pod cpu-limit drift should FAIL"

    # zero nodes must BLOCK even without a baseline
    empty_env = {"baseline_fingerprint": "empty000", "node_pools": {}, "workloads": {},
                 "capacity": {"requested_cpu_millis": 0, "allocatable_cpu_millis": 0, "fits": True}}
    assert run(k6vals, claim, dash_ok, 0.05, env=empty_env) == 1, "0 nodes should FAIL"

    # requests exceeding allocatable must BLOCK (the 8.79 > 7.91 vCPU case)
    overcommit = json.loads(json.dumps(baseline_env))
    overcommit["capacity"] = {"requested_cpu_millis": 8790, "allocatable_cpu_millis": 7910, "fits": False}
    assert run(k6vals, claim, dash_ok, 0.05, env=overcommit, baseline_env=baseline_env) == 1, \
        "unschedulable request total should FAIL"

    # Burstable QoS warns but does not block
    burstable = json.loads(json.dumps(baseline_env))
    burstable["workloads"]["ecommerce/service-product"]["qos"] = "Burstable"
    assert run(k6vals, claim, dash_ok, 0.05, env=burstable, baseline_env=baseline_env) == 0, \
        "Burstable QoS should WARN, not FAIL"

    print("\nALL SELFTESTS PASSED")
    return 0


def main():
    ap = argparse.ArgumentParser(description="Load-test evidence numeric-integrity gate")
    ap.add_argument("--k6")
    ap.add_argument("--claim")
    ap.add_argument("--dashboard")
    ap.add_argument("--env", help="this run's raw/env.json from capture-env.py")
    ap.add_argument("--baseline-env", help="env.json of the run this one must be comparable to")
    ap.add_argument("--allow-env-change", action="store_true",
                    help="declare the baseline change intentional; drift downgrades to WARN. "
                         "Every earlier run on the old baseline then needs re-measurement.")
    ap.add_argument("--tolerance", type=float, default=0.05)
    ap.add_argument("--selftest", action="store_true")
    args = ap.parse_args()

    if args.selftest:
        return selftest()
    if not args.k6 or not args.claim:
        ap.error("--k6 and --claim are required (or use --selftest)")
    try:
        summary = json.load(open(args.k6))
        claim = json.load(open(args.claim))
        dashboard = json.load(open(args.dashboard)) if args.dashboard else None
        env = json.load(open(args.env)) if args.env else None
        baseline_env = json.load(open(args.baseline_env)) if args.baseline_env else None
    except (OSError, json.JSONDecodeError) as e:
        print(f"parse error: {e}", file=sys.stderr)
        return 2
    return run(extract_k6(summary), claim, dashboard, args.tolerance,
               env=env, baseline_env=baseline_env, allow_env_change=args.allow_env_change)


if __name__ == "__main__":
    sys.exit(main())
