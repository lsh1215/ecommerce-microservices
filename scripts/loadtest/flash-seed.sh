#!/usr/bin/env bash
# 재고를 유닛 row 로 깐다. 재고 1개당 row 1줄이고, 그 수가 곧 재고 상한이라
# 오버셀이 구조적으로 불가능하다.
set -euo pipefail
VARIANT="${1:?variantId required}"
STOCK="${2:?stock required}"
K() { kubectl -n ecommerce "$@"; }

K exec -i mysql-product-0 -- mysql -u root -pchangeme 2>&1 <<EOF | grep -iv insecure || true
USE ecommerce_product;
DELETE FROM stock_unit WHERE variant_id = $VARIANT;
INSERT INTO stock_unit (variant_id, status, created_at, updated_at)
SELECT $VARIANT, 'AVAILABLE', NOW(), NOW()
FROM (SELECT 1 FROM information_schema.columns LIMIT 100) a,
     (SELECT 1 FROM information_schema.columns LIMIT 100) b
LIMIT $STOCK;
UPDATE product_variant SET stock_contention = 'HOT', stock_quantity = $STOCK WHERE id = $VARIANT;
SELECT variant_id, status, COUNT(*) FROM stock_unit WHERE variant_id = $VARIANT GROUP BY variant_id, status;
EOF
