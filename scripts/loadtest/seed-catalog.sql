-- 카탈로그 데이터셋. 읽기가 포함된 모든 측정의 전제 조건이다.
--
-- 왜 필요한가
--
--   커밋된 seed-data.sql은 brand 10 / product 30 / product_variant 90 /
--   product_image 0 이다. 실제 측정에 쓰인 product_variant 50,000 행은 어느
--   스크립트에도 없었고 애드혹 SQL로 만들어졌다(재현 불가).
--
--   그 50,000 행은 인덱스를 포함해도 20MB 미만이라 128MB 기본 버퍼풀에 통째로
--   들어간다. 그래서 조회는 100% 메모리 히트였고 req당 primary CPU가 0.73ms로
--   측정됐다. "앱이 DB보다 1.45배 비싸다 / DB는 병목이 아니다"라는 결론이
--   전부 이 조건 위에 서 있다.
--
--   버퍼풀을 1G로 올렸으므로(mysql-product-statefulset.yml) 데이터는 그보다
--   커야 한다. 아래 규모는 인덱스 포함 대략 3~5GB로, 히트율이 100%가 아닌
--   현실적인 구간을 만든다.
--
-- 규모: brand 100 / product 2,000,000 / product_variant 6,000,000 /
--       product_image 6,000,000
--
-- 재귀 CTE로 600만 행을 만들면 매우 느리므로 배수 증식(INSERT ... SELECT)을 쓴다.
-- 100만 행 CTE 한 번 + 더블링 반복이 훨씬 빠르다.
--
-- 사용:
--   kubectl exec -i -n ecommerce mysql-product-0 -- sh -c \
--     'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" ecommerce_product' < seed-catalog.sql

SET SESSION cte_max_recursion_depth = 1100000;
SET SESSION foreign_key_checks = 0;
SET SESSION unique_checks = 0;
SET SESSION sql_log_bin = 0;

TRUNCATE TABLE product_image;
TRUNCATE TABLE product_variant;
TRUNCATE TABLE product;
TRUNCATE TABLE brand;

-- ---------------------------------------------------------------- brands 100
INSERT INTO brand (id, name, description, logo_url, country, created_at, updated_at)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 100)
SELECT n,
       CONCAT('Brand-', LPAD(n, 3, '0')),
       CONCAT('Catalog seed brand ', n),
       NULL,
       ELT(1 + (n % 5), 'KR', 'US', 'JP', 'DE', 'IT'),
       NOW(), NOW()
FROM seq;

-- ------------------------------------------------------------ products 2,000,000
--
-- name/description을 단어 풀에서 조합한다. 값이 전부 같으면 옵티마이저와 버퍼풀
-- 지역성이 비현실적으로 유리해진다. keyword 검색(LIKE '%kw%')이 전체의 일부만
-- 맞히되 결과가 페이지 하나를 넘게 나오는 모양을 만든다.
INSERT INTO product (id, brand_id, name, description, price, status, category, created_at, updated_at)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 1000000)
SELECT n,
       1 + (n % 100),
       CONCAT(ELT(1 + (n % 8), 'Essential', 'Performance', 'Heritage', 'Lightweight',
                               'Insulated', 'Tailored', 'Oversized', 'Technical'),
              ' ',
              ELT(1 + (n % 6), 'Cotton', 'Merino', 'Nylon', 'Denim', 'Fleece', 'Linen'),
              ' ',
              ELT(1 + (n % 7), 'Tee', 'Hoodie', 'Jacket', 'Pants', 'Shorts', 'Shirt', 'Coat'),
              ' #', n),
       CONCAT('Catalog seed product ', n, '. ',
              ELT(1 + (n % 8), 'Classic crew neck in premium cotton.',
                               'Moisture-wicking fabric built for training.',
                               'Archive silhouette reissued for this season.',
                               'Packable and light enough for daily carry.',
                               'Synthetic insulation rated for cold weather.',
                               'Structured fit with a clean finished hem.',
                               'Relaxed drape with dropped shoulders.',
                               'Bonded seams and a water resistant face.')),
       9900 + (n % 40) * 5000,
       IF(n % 50 = 0, 'INACTIVE', 'ACTIVE'),
       ELT(1 + (n % 7), 'T-Shirts', 'Hoodies', 'Outerwear', 'Pants', 'Shorts', 'Shirts', 'Coats'),
       NOW(), NOW()
FROM seq;

-- 1,000,000 -> 2,000,000
INSERT INTO product (id, brand_id, name, description, price, status, category, created_at, updated_at)
SELECT id + 1000000, brand_id,
       CONCAT(SUBSTRING_INDEX(name, ' #', 1), ' #', id + 1000000),
       description, price, status, category, created_at, updated_at
FROM product;

-- ------------------------------------------------------- variants 6,000,000 (product당 3)
INSERT INTO product_variant (id, product_id, sku, size, color, stock_quantity, price, created_at, updated_at)
SELECT (p.id - 1) * 3 + s.k,
       p.id,
       CONCAT('SKU-', LPAD((p.id - 1) * 3 + s.k, 8, '0')),
       ELT(s.k, 'S', 'M', 'L'),
       ELT(1 + ((p.id + s.k) % 5), 'Black', 'White', 'Navy', 'Grey', 'Olive'),
       100000000,
       p.price,
       NOW(), NOW()
FROM product p
JOIN (SELECT 1 AS k UNION ALL SELECT 2 UNION ALL SELECT 3) s;

-- --------------------------------------------------------- images 6,000,000 (product당 3)
--
-- search 쿼리가 `LEFT JOIN images ON image.is_primary = true`를 하므로 primary가
-- product당 정확히 하나여야 DISTINCT와 조인 비용이 실제 동작을 반영한다.
-- 지금까지 이 테이블은 0행이어서 조인이 공짜였다.
INSERT INTO product_image (id, product_id, url, sort_order, is_primary, created_at, updated_at)
SELECT (p.id - 1) * 3 + s.k,
       p.id,
       CONCAT('https://cdn.example.com/p/', p.id, '/', s.k, '.jpg'),
       s.k - 1,
       s.k = 1,
       NOW(), NOW()
FROM product p
JOIN (SELECT 1 AS k UNION ALL SELECT 2 UNION ALL SELECT 3) s;

SET SESSION unique_checks = 1;
SET SESSION foreign_key_checks = 1;

-- FULLTEXT 인덱스.
--
-- JPA @Index로는 FULLTEXT를 만들 수 없어 여기서 선언한다. 카탈로그 검색의 keyword
-- 필터가 LIKE '%kw%'에서 MATCH ... AGAINST로 바뀌었고(ProductQueryRepositoryImpl),
-- 그 술어가 이 인덱스를 탄다. 없으면 쿼리가 실패한다.
--
-- 시드 후에 만드는 이유: 600만 행을 넣은 뒤 한 번에 구축하는 편이
-- 행마다 인덱스를 갱신하는 것보다 훨씬 빠르다.
ALTER TABLE product ADD FULLTEXT INDEX ftx_product_name_desc (name, description);

ANALYZE TABLE brand, product, product_variant, product_image;

SELECT 'brand'           AS t, COUNT(*) AS rows_seeded FROM brand
UNION ALL SELECT 'product',         COUNT(*) FROM product
UNION ALL SELECT 'product_variant', COUNT(*) FROM product_variant
UNION ALL SELECT 'product_image',   COUNT(*) FROM product_image;

SELECT table_name,
       ROUND((data_length + index_length) / 1024 / 1024) AS mb
FROM information_schema.tables
WHERE table_schema = 'ecommerce_product'
  AND table_name IN ('brand', 'product', 'product_variant', 'product_image')
ORDER BY mb DESC;

-- ---------------------------------------------------------------------------
-- 경합 등급별 재고 구성
--
-- 재고 차감을 어디서 직렬화할지는 옵션의 경합 정도가 정한다. 부하 테스트에서 세 등급을
-- 같은 조건으로 비교하려면 등급마다 재고 구조가 실제로 준비돼 있어야 한다.
--
--   NORMAL  product_variant.stock_quantity 하나를 조건부 UPDATE로 깎는다. 별도 준비 없음
--   POPULAR stock_shard 16행으로 쪼갠다
--   HOT     stock_unit 재고 1개당 1행
-- ---------------------------------------------------------------------------

-- 등급 배정: 앞쪽 소수 옵션만 상위 등급으로. 실제 카탈로그에서도 경합이 관측되는
-- 옵션은 극소수이고, 나머지는 NORMAL로 충분하다.
UPDATE product_variant SET stock_contention = 'NORMAL';
UPDATE product_variant SET stock_contention = 'POPULAR' WHERE id BETWEEN 1001 AND 1100;
UPDATE product_variant SET stock_contention = 'HOT'     WHERE id BETWEEN 1 AND 10;

-- POPULAR: 옵션당 재고를 16샤드로 균등 분배
TRUNCATE TABLE stock_shard;
INSERT INTO stock_shard (created_at, updated_at, variant_id, shard_no, quantity)
SELECT NOW(6), NOW(6), v.id, s.n, 2000
FROM product_variant v
JOIN (WITH RECURSIVE sh(n) AS (SELECT 0 UNION ALL SELECT n+1 FROM sh WHERE n < 15) SELECT n FROM sh) s
WHERE v.stock_contention = 'POPULAR';

-- HOT: 옵션당 유닛 row. 선착순 규모(옵션당 수천)를 가정한다.
TRUNCATE TABLE stock_unit;
SET SESSION cte_max_recursion_depth = 200000;
INSERT INTO stock_unit (created_at, updated_at, holder_id, status, variant_id)
SELECT NOW(6), NOW(6), NULL, 'AVAILABLE', v.id
FROM product_variant v
JOIN (WITH RECURSIVE u(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM u WHERE n < 5000) SELECT n FROM u) t
WHERE v.stock_contention = 'HOT';

SELECT stock_contention, COUNT(*) AS variants FROM product_variant GROUP BY stock_contention;
SELECT 'stock_shard' AS t, COUNT(*) AS rows_seeded FROM stock_shard
UNION ALL SELECT 'stock_unit', COUNT(*) FROM stock_unit;
