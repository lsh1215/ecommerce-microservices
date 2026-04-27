#!/usr/bin/env bash
# Polls Order POST until N consecutive observations land below TARGET_MS.
# Used after k6 warmup smoke to confirm JIT/HikariCP/RestClient pool is warm.
#
# Usage: warmup-stabilizer.sh [target_ms] [consecutive] [max_attempts]
#   default: 600ms / 5 consecutive / 60 attempts
#
# Exits 0 on stable, 1 on max_attempts exceeded.

set -euo pipefail
TARGET_MS=${1:-600}
CONSECUTIVE=${2:-5}
MAX_ATTEMPTS=${3:-60}
EXT=${ORDER_API:-http://34.64.219.137}
AUTH="Bearer ${JWT:-eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.sig}"

PAYLOAD=$(cat <<'JSON'
{"items":[{"productVariantId":1,"productId":1,"productName":"stabilizer","size":"S","color":"B","unitPrice":29900,"quantity":1}],"shippingAddress":{"recipientName":"s","phone":"010","zipCode":"06234","address1":"x","address2":"y"}}
JSON
)

streak=0
attempt=0
while [ $attempt -lt $MAX_ATTEMPTS ]; do
  attempt=$((attempt+1))
  ms=$(curl -sS -o /dev/null -w "%{time_total}" -X POST "${EXT}/api/orders" \
    -H "Content-Type: application/json" \
    -H "Authorization: ${AUTH}" \
    -d "${PAYLOAD}" | awk '{ printf "%d", $1 * 1000 }')
  if [ "${ms}" -lt "${TARGET_MS}" ]; then
    streak=$((streak+1))
    echo "[stabilizer] attempt=${attempt} ms=${ms} streak=${streak}/${CONSECUTIVE}"
    if [ ${streak} -ge ${CONSECUTIVE} ]; then
      echo "[stabilizer] STABLE after ${attempt} attempts (${streak} consecutive < ${TARGET_MS}ms)"
      exit 0
    fi
  else
    streak=0
    echo "[stabilizer] attempt=${attempt} ms=${ms} (above target ${TARGET_MS}ms — reset streak)"
  fi
  sleep 0.5
done
echo "[stabilizer] FAIL — never converged after ${MAX_ATTEMPTS} attempts"
exit 1
