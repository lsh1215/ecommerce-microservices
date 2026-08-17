#!/usr/bin/env python3
"""Capture the hardware / deployment fingerprint of an evidence run.

Why this exists
---------------
The 2026-08-01 hot-row re-measurement moved ``service-product`` from a
2 vCPU node (``e2-standard-2``) to an 8 vCPU node (``e2-standard-8``)
without recording it anywhere machine-readable. Every measurement after
that sits on a different baseline than saga / outbox / idempotency /
circuit-breaker, and nothing in the evidence tree can prove which run
used which hardware. See ``docs/observability/loadtest-baseline-audit.md``.

This writes ``<out>/raw/env.json`` (plus the human-readable
``nodes-wide.txt`` / ``pods-wide.txt``) so that
:mod:`verify-evidence` can block a run whose baseline silently drifted.

``baseline_fingerprint`` covers node-pool machine types plus per-workload
CPU/memory requests, limits and nodeSelector. Node instance names, pod hashes
and timestamps are excluded so a plain redeploy on identical hardware keeps the
same value.

Replica counts are deliberately NOT in that hash. They live in ``topology``,
because scaling one service 1 -> 2 -> 3 while everything else stays pinned is a
measurement axis, not drift — hashing them would make the gate reject the
scale-out experiment itself.

Usage:
    capture-env.py <out-dir> [--namespaces ecommerce,monitoring]
    capture-env.py --print          # dump fingerprint to stdout, write nothing
"""
import argparse
import datetime
import hashlib
import json
import os
import subprocess
import sys

DEFAULT_NAMESPACES = ("ecommerce", "monitoring")


def kubectl(*args):
    proc = subprocess.run(["kubectl", *args], capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"kubectl {' '.join(args)} failed: {proc.stderr.strip()}")
    return proc.stdout


def kubectl_json(*args):
    return json.loads(kubectl(*args, "-o", "json"))


def cpu_millis(value):
    """Normalise a Kubernetes CPU quantity to integer millicores."""
    if value is None:
        return None
    value = str(value)
    if value.endswith("m"):
        return int(float(value[:-1]))
    return int(float(value) * 1000)


def mem_mib(value):
    """Normalise a Kubernetes memory quantity to integer MiB."""
    if value is None:
        return None
    value = str(value)
    units = {"Ki": 1 / 1024, "Mi": 1, "Gi": 1024, "Ti": 1024 * 1024,
             "K": 1000 / (1024 * 1024), "M": 1000 ** 2 / (1024 ** 2), "G": 1000 ** 3 / (1024 ** 2)}
    for suffix, factor in units.items():
        if value.endswith(suffix):
            return int(float(value[: -len(suffix)]) * factor)
    return int(float(value) / (1024 * 1024))


def qos(record):
    """Guaranteed only when every request equals its limit and all four are set."""
    pairs = (("cpu_request", "cpu_limit"), ("memory_request", "memory_limit"))
    if any(record.get(key) is None for pair in pairs for key in pair):
        return "Burstable"
    return "Guaranteed" if all(record[req] == record[lim] for req, lim in pairs) else "Burstable"


def collect_nodes():
    nodes = kubectl_json("get", "nodes")["items"]
    pools = {}
    for node in nodes:
        labels = node["metadata"].get("labels", {})
        pool = labels.get("cloud.google.com/gke-nodepool", "unknown")
        machine = (labels.get("node.kubernetes.io/instance-type")
                   or labels.get("beta.kubernetes.io/instance-type") or "unknown")
        entry = pools.setdefault(pool, {
            "machine_type": machine,
            "role": labels.get("role", ""),
            "count": 0,
            "allocatable_cpu_millis": 0,
        })
        entry["count"] += 1
        entry["allocatable_cpu_millis"] += cpu_millis(node["status"]["allocatable"]["cpu"]) or 0
    return pools


def running_image_id(namespace, owner):
    """Resolve the container imageID of a live pod owned by `owner`.

    Falls back to None when the workload has no running pod yet; the spec
    `image` tag is still recorded in that case.
    """
    pods = kubectl_json("get", "pods", "-n", namespace)
    if not pods:
        return None
    for p in pods.get("items", []):
        if not p["metadata"]["name"].startswith(owner + "-"):
            continue
        for cs in p["status"].get("containerStatuses", []) or []:
            if cs.get("imageID"):
                return cs["imageID"]
    return None


def collect_workloads(namespaces):
    workloads = {}
    for kind in ("deployments", "statefulsets"):
        for obj in kubectl_json("get", kind, "-A")["items"]:
            namespace = obj["metadata"]["namespace"]
            if namespace not in namespaces:
                continue
            spec = obj["spec"]
            pod_spec = spec["template"]["spec"]
            containers = pod_spec.get("containers", [])
            primary = containers[0] if containers else {}
            resources = primary.get("resources", {})
            requests = resources.get("requests", {})
            limits = resources.get("limits", {})
            record = {
                "kind": kind[:-1],
                "replicas": spec.get("replicas", 1),
                "containers": len(containers),
                "cpu_request": cpu_millis(requests.get("cpu")),
                "cpu_limit": cpu_millis(limits.get("cpu")),
                "memory_request": mem_mib(requests.get("memory")),
                "memory_limit": mem_mib(limits.get("memory")),
                "node_selector": pod_spec.get("nodeSelector") or {},
                # The spec image is what was asked for; the running imageID is
                # the digest that actually served the load. The 2026-08-11
                # campaign recorded neither, so no surviving run can be tied to
                # a commit.
                "image": primary.get("image"),
                "image_id": running_image_id(namespace, obj["metadata"]["name"]),
                # Tuning env vars that change what the benchmark measures.
                # The HikariCP pool sat only on the live cluster (kubectl set
                # env) until 2026-08-12, so no recorded run states the pool it
                # ran with even though it set the ceiling. Captured explicitly
                # rather than dumping all env, which would leak secrets.
                #
                # CACHE / REPLICA are here because a fingerprint that omits the
                # variable under test reports two arms of an A/B as identical.
                # The catalog cache A/B produced env.json files differing only
                # in captured_at, which reads as "these runs were the same" —
                # the flag that made them different was not on the list.
                "tuning_env": {
                    e["name"]: e.get("value")
                    for e in primary.get("env", [])
                    if any(k in e["name"] for k in ("HIKARI", "POOL", "THREADS", "TOMCAT",
                                                    "CACHE", "REPLICA"))
                },
            }
            record["qos"] = qos(record)
            workloads[f"{namespace}/{obj['metadata']['name']}"] = record
    return workloads


def pool_total(workload):
    """Total HikariCP connections a workload opens against its database.

    per-pod maximum-pool-size x replicas. Returns None when the workload
    declares no pool, so non-JDBC workloads stay out of the fingerprint.
    """
    size = (workload.get("tuning_env") or {}).get("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE")
    if size is None:
        return None
    try:
        return int(size) * (workload.get("replicas") or 1)
    except (TypeError, ValueError):
        return None


def fingerprint(pools, workloads):
    """Split the spec into the part that must never drift and the part that is
    the experiment.

    ``baseline`` = node-pool machine types + per-pod resources + placement.
    Two runs are comparable only when this matches.

    ``topology`` = replica counts. Scaling ``service-product`` 1 -> 2 -> 3 with
    everything else pinned is the whole point of the scale-out measurement, so
    replica changes are reported as the declared axis, never as drift. Folding
    replicas into the baseline hash would make the gate block the very
    experiment it exists to protect.
    """
    baseline = {
        "node_pools": {
            name: {"machine_type": p["machine_type"], "role": p["role"], "count": p["count"]}
            for name, p in sorted(pools.items())
        },
        "workloads": {
            name: {k: w[k] for k in ("kind", "cpu_request", "cpu_limit",
                                     "memory_request", "memory_limit", "node_selector")}
            for name, w in sorted(workloads.items())
        },
        # Total DB connections, not the per-pod pool. The per-pod value is
        # deliberately reduced as replicas grow (12 / 6 / 4) so that the total
        # stays at the database's capacity; putting the per-pod number in the
        # baseline would make the gate block the declared axis, while leaving
        # it out entirely is how a 34/pod -> 102-total pool went unrecorded
        # through an entire campaign.
        "db_connections_total": {
            name: pool_total(w)
            for name, w in sorted(workloads.items())
            if pool_total(w) is not None
        },
    }
    topology = {name: w["replicas"] for name, w in sorted(workloads.items())}
    blob = json.dumps(baseline, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(blob).hexdigest()[:16], baseline, topology


def capacity_check(pools, workloads):
    """Total CPU requests vs allocatable. Catches the 8.79 > 7.91 vCPU case
    where nothing can schedule in the first place."""
    requested = sum((w["cpu_request"] or 0) * (w["replicas"] or 0) for w in workloads.values())
    allocatable = sum(p["allocatable_cpu_millis"] for p in pools.values())
    return {
        "requested_cpu_millis": requested,
        "allocatable_cpu_millis": allocatable,
        "fits": allocatable == 0 or requested <= allocatable,
    }


def build(namespaces):
    pools = collect_nodes()
    workloads = collect_workloads(namespaces)
    digest, baseline, topology = fingerprint(pools, workloads)
    return {
        "captured_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "context": kubectl("config", "current-context").strip(),
        "baseline_fingerprint": digest,
        "topology": topology,
        "node_pools": pools,
        "workloads": workloads,
        "capacity": capacity_check(pools, workloads),
        "_baseline": baseline,
    }


def main():
    parser = argparse.ArgumentParser(description="Capture evidence-run environment fingerprint")
    parser.add_argument("out", nargs="?", help="run directory; writes <out>/raw/env.json")
    parser.add_argument("--namespaces", default=",".join(DEFAULT_NAMESPACES))
    parser.add_argument("--print", dest="print_only", action="store_true")
    # The deployment fingerprint alone does not identify a run whose
    # independent variable lives in the load script rather than in the
    # workload spec. Two arms that differ only by k6 __ENV produced env.json
    # files identical except for captured_at, which is indistinguishable from
    # having run the same arm twice.
    parser.add_argument("--k6-env", default="",
                        help="comma-separated KEY=VAL passed to k6 as __ENV")
    args = parser.parse_args()

    namespaces = {n.strip() for n in args.namespaces.split(",") if n.strip()}
    try:
        env = build(namespaces)
        k6_env = dict(
            kv.split("=", 1) for kv in args.k6_env.split(",") if "=" in kv
        )
        if k6_env:
            env["k6_env"] = k6_env
    except RuntimeError as exc:
        print(f"[capture-env] {exc}", file=sys.stderr)
        return 2

    if not env["node_pools"]:
        print("[capture-env] WARNING: cluster reports 0 nodes — this run cannot be a valid measurement",
              file=sys.stderr)
    if not env["capacity"]["fits"]:
        cap = env["capacity"]
        print(f"[capture-env] WARNING: CPU requests {cap['requested_cpu_millis']}m exceed "
              f"allocatable {cap['allocatable_cpu_millis']}m — workloads cannot all schedule",
              file=sys.stderr)

    if args.print_only or not args.out:
        json.dump(env, sys.stdout, indent=2, sort_keys=True)
        print()
        return 0

    raw = os.path.join(args.out, "raw")
    os.makedirs(raw, exist_ok=True)
    with open(os.path.join(raw, "env.json"), "w") as handle:
        json.dump(env, handle, indent=2, sort_keys=True)
        handle.write("\n")
    for name, argv in (("nodes-wide.txt", ("get", "nodes", "-o", "wide", "-L", "role")),
                       ("pods-wide.txt", ("get", "pods", "-A", "-o", "wide"))):
        try:
            with open(os.path.join(raw, name), "w") as handle:
                handle.write(kubectl(*argv))
        except RuntimeError as exc:
            print(f"[capture-env] {name}: {exc}", file=sys.stderr)

    print(f"[capture-env] baseline={env['baseline_fingerprint']} "
          f"topology={env['topology']} -> {raw}/env.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
