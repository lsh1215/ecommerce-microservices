#!/usr/bin/env bash
# Scale service replicas while holding total DB connections constant.
#
# The HikariCP pool is a per-pod setting but the right *total* is a property of
# the database: (6 cores x 2) + 1 spindle = 13 by HikariCP's own guidance, and
# 3,000 rps x 2.3ms hold = 6.8 by Little's law, against a measured
# threads_running of 2-7. 12 total is the declared operating point.
#
# Scaling pods without shrinking the per-pod pool moves two variables at once —
# "app instances" and "database concurrency" — which is how the 2026-08-11
# campaign ended up running 102 connections and 104 running threads on a 6-core
# database (17x oversubscribed) while claiming to measure pod count alone.
#
# Usage: set-pool.sh <deployment> <replicas> [total-connections]
set -euo pipefail
DEPLOY="${1:?deployment required}"
REPLICAS="${2:?replicas required}"
TOTAL="${3:-12}"
NS="${NS:-ecommerce}"

PER_POD=$(( TOTAL / REPLICAS ))
[ "$PER_POD" -lt 1 ] && PER_POD=1
ACTUAL=$(( PER_POD * REPLICAS ))

# minimum-idle stays well below maximum so the pool shrinks when idle. The old
# min == max kept every connection open even at 11 rps, which is why MySQL
# showed 103 threads_connected on an idle cluster.
MIN_IDLE=$(( PER_POD / 4 ))
[ "$MIN_IDLE" -lt 1 ] && MIN_IDLE=1

echo "[set-pool] $DEPLOY: replicas=$REPLICAS pool=$PER_POD/pod -> total=$ACTUAL (target $TOTAL)"
[ "$ACTUAL" -ne "$TOTAL" ] && \
  echo "[set-pool] NOTE: $TOTAL is not divisible by $REPLICAS; actual total is $ACTUAL" >&2

kubectl set env "deploy/$DEPLOY" -n "$NS" \
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="$PER_POD" \
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="$MIN_IDLE" >/dev/null

kubectl scale "deploy/$DEPLOY" -n "$NS" --replicas="$REPLICAS" >/dev/null
kubectl rollout status "deploy/$DEPLOY" -n "$NS" --timeout=300s | tail -1

# The value is what shapes the numbers, so prove it landed rather than trusting
# the command. capture-env.py records it into raw/env.json for every run and
# folds the total into baseline_fingerprint.
echo -n "[set-pool] applied: "
kubectl get "deploy/$DEPLOY" -n "$NS" \
  -o jsonpath='{range .spec.template.spec.containers[0].env[?(@.name=="SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE")]}{.value}{end}'
echo "/pod x $(kubectl get deploy/$DEPLOY -n $NS -o jsonpath='{.spec.replicas}') pods"
