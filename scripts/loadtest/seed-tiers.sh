kubectl exec -n ecommerce mysql-product-0 -- sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" ecommerce_product -N -e "
-- 측정 대상 두 옵션. 같은 엔드포인트를 타고 등급만 다르다.
UPDATE product_variant SET stock_contention=\"NORMAL\", stock_quantity=100000000 WHERE id=1;
UPDATE product_variant SET stock_contention=\"HOT\",    stock_quantity=100000000 WHERE id=2;

-- HOT 등급 유닛. 최대 부하 × 지속시간을 넘도록 넉넉히(재고 소진이 천장 판정을 오염시키지 않게).
TRUNCATE TABLE stock_unit;
SET SESSION cte_max_recursion_depth = 1100000;
INSERT INTO stock_unit (created_at, updated_at, holder_id, status, variant_id)
SELECT NOW(6), NOW(6), NULL, \"AVAILABLE\", 2
FROM (WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM seq WHERE n < 1000000) SELECT n FROM seq) t;

TRUNCATE TABLE stock_reservation;
SELECT CONCAT(\"variant1(NORMAL) stock=\", stock_quantity) FROM product_variant WHERE id=1;
SELECT CONCAT(\"variant2(HOT) units=\", COUNT(*)) FROM stock_unit WHERE status=\"AVAILABLE\";
" 2>/dev/null'
