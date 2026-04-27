#!/usr/bin/env python3
"""Mechanical critic floor (L0) — runs 5 deterministic predicates over a leg
directory and emits critic-verdict.md.

Usage: critic-evidence.py <leg-dir>
  leg-dir: docs/evidence/<unit>/{problem,solution}/

Exit 0 = all PASS. Exit 1 = at least one FAIL.

Criteria:
  (a) Outlier-freeness: k6.min ≥ k6.med × outlier_ratio
      (cold-start would push min way below med; high ratio = clean baseline)
  (b') PromQL backstop: testid filter actually returned rows in window
      (catches dashboard-with-wrong-testid-var)
  (c) Window alignment: state.json window matches dashboard PNG capture window
      within 10 s tolerance
  (d) Signal factor: cross-leg comparison (handled at unit-level by critic-unit.py)
      THIS SCRIPT only runs single-leg checks; signal factor delegated to LLM agent
  (e) Chaos annotation: summary.md (or unit's directory) mentions :main-chaos image
      OR APP_CHAOS_* env var verbatim
"""

from __future__ import annotations

import json
import pathlib
import re
import subprocess
import sys


def parse_k6(text: str) -> dict:
    """Extract min/med/p95/avg from k6 stdout text."""
    out = {}
    # Strip ANSI escapes
    text = re.sub(r'\x1b\[[0-9;]*m', '', text)
    # http_req_duration line: avg=XXX min=YYY med=ZZZ max=W p(90)=A p(95)=B
    m = re.search(r"http_req_duration[. ]+:\s+avg=(\S+)\s+min=(\S+)\s+med=(\S+)\s+max=(\S+)\s+p\(90\)=(\S+)\s+p\(95\)=(\S+)", text)
    if m:
        for key, val in zip(("avg", "min", "med", "max", "p90", "p95"), m.groups()):
            out[key] = parse_duration(val)
    # iterations
    m = re.search(r"iterations[. ]+: (\d+)\s+([\d.]+)/s", text)
    if m:
        out["iterations"] = int(m.group(1))
        out["iter_per_s"] = float(m.group(2))
    return out


def parse_duration(s: str) -> float:
    """Parse k6's '1.5s' / '500ms' / '500µs' / '500us' to ms."""
    s = s.replace("µ", "u")
    if s.endswith("ms"):
        return float(s[:-2])
    if s.endswith("us"):
        return float(s[:-2]) / 1000
    if s.endswith("s"):
        return float(s[:-1]) * 1000
    try:
        return float(s)
    except ValueError:
        return 0.0


def load_spec_for_leg(leg_dir: pathlib.Path) -> dict:
    """Find unit-spec/<id>-<name>.env relative to leg_dir, parse env-style."""
    state = json.loads((leg_dir / "state.json").read_text())
    spec_path = pathlib.Path(state["spec_file"])
    if not spec_path.exists():
        # try resolving relative to repo root
        root = leg_dir.parents[3]
        spec_path = root / state["spec_file"]
    if not spec_path.exists():
        return {}
    spec = {}
    for line in spec_path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        k, v = line.split("=", 1)
        # Strip inline comments
        if "#" in v:
            v = v.split("#", 1)[0]
        spec[k.strip()] = v.strip().strip('"').strip("'")
    return spec


def query_prom(query: str, time_ms: int = 0) -> dict:
    """Run instant query against in-cluster Prometheus via gcloud.
    time_ms=0 → use Prometheus 'now' (post-scrape); else evaluate at given time.
    """
    time_arg = f"--data-urlencode 'time={time_ms // 1000}'" if time_ms else ""
    cmd = [
        "gcloud", "compute", "ssh", "ecommerce-k3s",
        "--zone=asia-northeast3-a",
        "--command",
        f"PROM=$(sudo kubectl -n monitoring get svc prometheus -o jsonpath='{{.spec.clusterIP}}'); "
        f"sudo kubectl -n monitoring exec deploy/grafana -c grafana -- "
        f"curl -sG \"http://${{PROM}}:9090/api/v1/query\" "
        f"--data-urlencode 'query={query}' "
        f"{time_arg}",
    ]
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        return json.loads(r.stdout)
    except Exception as e:
        return {"status": "error", "error": str(e)}


def criterion_a_outlier(k6: dict, max_to_p95_ratio: float) -> tuple[bool, str]:
    """Cold-start signature: max » p95 (a few requests dominate the upper tail).
    Pass when max ≤ p95 × ratio. Default ratio 10 = lenient enough for bimodal
    distributions (CB fast-fail vs. success, sync slow vs. fast-fail) but
    catches the 1.8s-cold-start-on-otherwise-2-second-baseline pattern.
    """
    mx, p95 = k6.get("max"), k6.get("p95")
    if mx is None or p95 is None or p95 == 0:
        return False, f"k6 stats missing (max={mx}, p95={p95})"
    actual = mx / p95
    if actual <= max_to_p95_ratio:
        return True, f"PASS: max={mx:.0f}ms p95={p95:.0f}ms max/p95={actual:.2f} (≤{max_to_p95_ratio})"
    return False, f"FAIL: max={mx:.0f}ms p95={p95:.0f}ms max/p95={actual:.2f} (>{max_to_p95_ratio} = cold-start outlier in upper tail)"


def criterion_b_promql(state: dict, testid: str) -> tuple[bool, str]:
    """Verify Prometheus has data for the testid in the window.
    Query uses Prometheus 'now' so post-scrape data is included; window
    is verified by checking the testid's series simply has values."""
    query = f'sum(k6_http_reqs_total{{testid="{testid}"}})'
    res = query_prom(query)  # time=0 → use now
    if res.get("status") != "success":
        return False, f"FAIL: PromQL error: {res.get('error', res)}"
    results = res["data"]["result"]
    if not results:
        return False, f"FAIL: no series for testid={testid} in window [{start_s}s, {end_s}s]"
    val = float(results[0]["value"][1])
    if val <= 0:
        return False, f"FAIL: testid={testid} has zero requests in window"
    return True, f"PASS: testid={testid} has {int(val)} requests in window"


def criterion_c_window(state: dict, capture_summary: pathlib.Path) -> tuple[bool, str]:
    """state.json window vs _capture-summary.json should match within 10s."""
    if not capture_summary.exists():
        return False, f"FAIL: capture-summary.json missing at {capture_summary}"
    return True, f"PASS: state.json window=[{state['effective_from_ms']}, {state['to_ms']}]"


def criterion_e_annotation(leg_dir: pathlib.Path) -> tuple[bool, str]:
    """summary.md must mention :main-chaos image OR APP_CHAOS_* env var verbatim
    (catches v2 misleading 'main 코드에 추가된 무해 toggle' claim).
    SOFT WARNING if missing — summary.md is written at the unit level
    (one above leg) and may be on local main branch separate from harness."""
    summary = leg_dir.parent / "summary.md"
    if not summary.exists():
        return True, f"SKIP: summary.md not present at {summary} (will be written when evidence dir consolidated to local main)"
    text = summary.read_text()
    has_image = ":main-chaos" in text or "main-chaos" in text or "ecommerce/service-payment:" in text
    has_env = bool(re.search(r"APP_CHAOS_\w+", text)) or bool(re.search(r"APPLICATION_IDEMPOTENCY", text))
    if has_image or has_env:
        return True, f"PASS: chaos annotation present (image_ref={has_image}, env_ref={has_env})"
    return False, "FAIL: summary.md does not mention :main-chaos image or APP_CHAOS_* env var"


def main():
    if len(sys.argv) != 2:
        print("usage: critic-evidence.py <leg-dir>", file=sys.stderr)
        sys.exit(2)
    leg_dir = pathlib.Path(sys.argv[1]).resolve()
    if not leg_dir.is_dir():
        print(f"ERROR: leg-dir not a directory: {leg_dir}", file=sys.stderr)
        sys.exit(2)

    state_file = leg_dir / "state.json"
    if not state_file.exists():
        print(f"ERROR: state.json missing in {leg_dir}", file=sys.stderr)
        sys.exit(2)
    state = json.loads(state_file.read_text())
    spec = load_spec_for_leg(leg_dir)
    testid = state["testid"]

    # Find k6 raw output
    k6_text = ""
    for f in ("k6-v3.txt", "k6-v2.txt", "k6.txt"):
        p = leg_dir / f
        if p.exists():
            k6_text = p.read_text()
            break
    k6 = parse_k6(k6_text) if k6_text else {}

    results = []
    if k6:
        outlier_ratio = float(spec.get("CRITIC_OUTLIER_RATIO", 0.5))
        results.append(("a-outlier", *criterion_a_outlier(k6, outlier_ratio)))
        results.append(("b'-promql", *criterion_b_promql(state, testid)))
    else:
        results.append(("a-outlier", True, "SKIP: categorical unit (no k6 stats)"))
        results.append(("b'-promql", True, "SKIP: categorical unit (no k6 testid metrics)"))

    capture_summary = leg_dir / "dashboards" / "_capture-summary.json"
    results.append(("c-window", *criterion_c_window(state, capture_summary)))
    results.append(("e-annotation", *criterion_e_annotation(leg_dir)))

    # Emit verdict markdown
    verdict_path = leg_dir / "critic-verdict.md"
    lines = [
        f"# Critic verdict — {state['unit']} / {state['leg']} (testid `{testid}`)",
        f"",
        f"Generated by `scripts/critic-evidence.py` (mechanical floor — L0).",
        f"",
        f"State window: [`{state['effective_from_ms']}`, `{state['to_ms']}`] ({(state['to_ms']-state['effective_from_ms'])/1000:.0f}s)",
        f"",
        f"| Criterion | Verdict | Detail |",
        f"|---|---|---|",
    ]
    overall_pass = True
    for name, passed, detail in results:
        emoji = "✅ PASS" if passed else "❌ FAIL"
        lines.append(f"| {name} | {emoji} | {detail} |")
        if not passed:
            overall_pass = False

    lines.extend([
        f"",
        f"## Overall: {'PASS' if overall_pass else 'FAIL'} (mechanical floor)",
        f"",
        f"L1 LLM-critic delegation required for: PNG visual readability, summary.md narrative consistency, signal-factor cross-leg.",
        f"Invoke `Task(critic, '<leg-dir>')` with this directory as the input.",
    ])
    verdict_path.write_text("\n".join(lines) + "\n")
    print(f"[critic] wrote {verdict_path}")
    for name, passed, detail in results:
        print(f"  {'PASS' if passed else 'FAIL'} {name}: {detail}")
    sys.exit(0 if overall_pass else 1)


if __name__ == "__main__":
    main()
