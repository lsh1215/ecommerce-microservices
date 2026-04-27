#!/usr/bin/env bash
# Mid-measurement kafka chaos cycle for Unit 02 outbox evidence.
# Designed to be backgrounded by measure-unit.sh — runs in parallel with k6.
#
# Timeline (relative to script start):
#   t+30s: kubectl scale deploy/kafka --replicas=0
#   t+33s: kubectl delete pods -l app=service-order  (drops producer in-memory buffer)
#   t+45s: kubectl scale deploy/kafka --replicas=1
#   t+50s: rollout status (blocks until kafka recovers)
#
# Total wall-clock: ~50-90s depending on kafka recovery time.

set -euo pipefail
echo "[chaos] kicker started — kafka blip in 30s"
sleep 30

echo "[chaos] t+30s: kafka scale -> 0"
sudo kubectl -n ecommerce scale deploy/kafka --replicas=0 2>&1 | tail -1

sleep 3
echo "[chaos] t+33s: drop service-order pod (producer accumulator dies with it)"
sudo kubectl -n ecommerce delete pods -l app=service-order --grace-period=1 2>&1 | tail -1

sleep 12
echo "[chaos] t+45s: kafka scale -> 1"
sudo kubectl -n ecommerce scale deploy/kafka --replicas=1 2>&1 | tail -1
sudo kubectl -n ecommerce rollout status deploy/kafka --timeout=120s 2>&1 | tail -1
echo "[chaos] kafka recovered"
