#!/usr/bin/env python3
"""Strip server-managed fields from a Kubernetes resource YAML.

Removes the following so a `kubectl get -o yaml` dump can be checked into
git as a clean source-of-truth manifest:
- metadata.creationTimestamp / uid / resourceVersion / generation /
  managedFields / selfLink
- metadata.annotations.kubectl.kubernetes.io/last-applied-configuration
- spec.template.metadata.creationTimestamp (StatefulSet/Deployment)
- spec.template.spec.dnsPolicy / restartPolicy / schedulerName /
  terminationGracePeriodSeconds (server-side defaults that drift)
- containers[*].terminationMessagePath / terminationMessagePolicy
- ports[*].protocol when value is TCP (default)
- imagePullPolicy when "IfNotPresent" (default for non-:latest)
- status block

Usage:
    python3 scripts/strip-k8s-metadata.py <input.yaml> [<input2.yaml> ...]
                                          --out <output.yaml>

If multiple inputs are given, they are concatenated with `---` separators
in the output file.
"""
from __future__ import annotations

import argparse
import sys

import yaml

SERVER_META_KEYS = (
    "creationTimestamp",
    "uid",
    "resourceVersion",
    "generation",
    "managedFields",
    "selfLink",
)

KUBECTL_LAST_APPLIED = "kubectl.kubernetes.io/last-applied-configuration"

POD_DEFAULT_KEYS = (
    "dnsPolicy",
    "restartPolicy",
    "schedulerName",
    "terminationGracePeriodSeconds",
    "securityContext",
)

CONTAINER_TERM_KEYS = (
    "terminationMessagePath",
    "terminationMessagePolicy",
)


def strip_metadata(meta: dict) -> dict:
    if not isinstance(meta, dict):
        return meta
    for k in SERVER_META_KEYS:
        meta.pop(k, None)
    annotations = meta.get("annotations")
    if isinstance(annotations, dict):
        annotations.pop(KUBECTL_LAST_APPLIED, None)
        if not annotations:
            meta.pop("annotations", None)
    return meta


def strip_pod_spec(spec: dict) -> dict:
    if not isinstance(spec, dict):
        return spec
    for k in POD_DEFAULT_KEYS:
        spec.pop(k, None)
    for ctr in spec.get("containers", []) or []:
        for k in CONTAINER_TERM_KEYS:
            ctr.pop(k, None)
        ctr.pop("imagePullPolicy", None)
        for port in ctr.get("ports", []) or []:
            if port.get("protocol") == "TCP":
                port.pop("protocol")
    return spec


def strip_resource(doc: dict) -> dict:
    if not isinstance(doc, dict):
        return doc
    doc.pop("status", None)
    strip_metadata(doc.get("metadata", {}) or {})
    spec = doc.get("spec")
    if isinstance(spec, dict):
        template = spec.get("template")
        if isinstance(template, dict):
            strip_metadata(template.get("metadata", {}) or {})
            strip_pod_spec(template.get("spec", {}) or {})
        for vct in spec.get("volumeClaimTemplates", []) or []:
            strip_metadata(vct.get("metadata", {}) or {})
            vct.pop("status", None)
    return doc


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("inputs", nargs="+")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    docs: list[dict] = []
    for path in args.inputs:
        with open(path) as f:
            for doc in yaml.safe_load_all(f):
                if doc is not None:
                    docs.append(strip_resource(doc))

    with open(args.out, "w") as f:
        yaml.safe_dump_all(
            docs,
            f,
            sort_keys=False,
            default_flow_style=False,
            allow_unicode=True,
        )

    print(f"wrote {len(docs)} document(s) to {args.out}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
