#!/usr/bin/env bash
# 측정 사이 초기화.
#
# 순서가 중요하다. 토픽을 다시 만든 뒤에 재발매를 알려야 그 신호가 남는다. 반대로 하면
# 재발매 메시지가 토픽과 함께 지워지고, 파드들의 매진 플래그가 선 채로 다음 런이 시작된다.
set -euo pipefail
VARIANT="${1:?variantId required}"
STOCK="${2:?stock required}"
HERE="$(cd "$(dirname "$0")" && pwd)"
K() { kubectl -n ecommerce "$@"; }
KT() { K exec kafka-0 -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 "$@"; }

K exec -i mysql-order-0 -- mysql -u root -pchangeme -e \
  "DELETE FROM ecommerce_order.flash_reservation WHERE variant_id=$VARIANT;" 2>&1 | grep -iv insecure || true

bash "$HERE/flash-seed.sh" "$VARIANT" "$STOCK" >/dev/null

# 접수/결과 토픽은 지우고 다시 만든다. --if-not-exists 로는 백로그도 offset 도 안 지워지고,
# 다음 런의 offset 이 0 에서 시작하지 않아 공정성 검증이 어긋난다.
for t in flash.reserve.requested flash.reserve.result; do
  KT --delete --topic "$t" >/dev/null 2>&1 || true
done
for _ in $(seq 1 30); do
  KT --list 2>/dev/null | grep -qE '^flash\.reserve\.' || break
  sleep 2
done
for t in flash.reserve.requested flash.reserve.result; do
  KT --create --if-not-exists --topic "$t" --partitions 12 --replication-factor 3 \
     --config min.insync.replicas=2 >/dev/null 2>&1 || true
done

# 매진 플래그 해제. 토픽을 다시 만든 뒤라야 신호가 살아남는다.
K exec deploy/service-product -- \
  curl -sf -XPOST "http://localhost:8081/api/products/flash-sale/$VARIANT/reopen" >/dev/null 2>&1 || \
  echo "[reset] 경고: 재발매 호출 실패. 매진 플래그가 남아 있을 수 있다." >&2
sleep 3
echo "[reset] variant=$VARIANT stock=$STOCK done"
