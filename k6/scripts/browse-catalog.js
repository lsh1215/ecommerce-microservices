import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

// 실제 카탈로그 조회 부하.
//
// 지금까지의 "조회 부하"는 전부 GET /api/internal/products/variants/{id} 하나였다.
// PK 단건 조회라 버퍼풀 히트율이 99%로 유지되고 req당 primary CPU가 0.63ms에 그친다.
// 그 조건에서는 천장을 앱이 먼저 만들고(89% vs 67%) read replica가 덜어낼 부하가 없다.
//
// 실제 이커머스 조회는 단건 조회만이 아니다. GET /api/products는
// ProductQueryRepositoryImpl#search로 들어가 COUNT 쿼리 1회 + 페이지 SELECT 1회를
// 실행하고, brand/image를 LEFT JOIN한 뒤 DISTINCT를 건다. keyword가 붙으면
// name/description에 LIKE '%kw%'라 인덱스를 타지 못한다.
//
// 이건 쿼리를 인위적으로 무겁게 만든 것이 아니라 카탈로그가 원래 쓰는 경로다.
// MIX로 세 유형의 비율을 조정한다(기본값은 상세 위주의 일반적인 브라우징 패턴).

const API = __ENV.PRODUCT_API || 'http://service-product:8081';
const PRODUCT_POOL = Number(__ENV.PRODUCT_POOL || 2000000);
const VARIANT_POOL = Number(__ENV.VARIANT_POOL || 6000000);
const RATE = Number(__ENV.RATE || 1000);
const PAGE_SIZE = Number(__ENV.PAGE_SIZE || 20);

// 비율(%): 목록·검색 / 상품 상세 / 재고 단건
const PCT_LIST = Number(__ENV.PCT_LIST || 20);
const PCT_DETAIL = Number(__ENV.PCT_DETAIL || 30);
// 나머지는 variant 단건

const CATEGORIES = ['T-Shirts', 'Hoodies', 'Outerwear', 'Pants', 'Shorts', 'Shirts', 'Coats'];

// 접근 분포.
//
// 전역 균등 랜덤(1..2,000,000)은 캐시 실험을 무의미하게 만든다. 어떤 캐시도
// 200만 개를 균등하게 맞히지 못하고, 히트율이 캐시 크기에 선형으로 묶여버린다.
// 실제 이커머스는 소수 인기 상품에 조회가 집중된다.
//
// HOT_RATIO: 상위 몇 %를 인기 상품으로 볼 것인가 (기본 1%)
// HOT_SHARE: 그 인기 상품이 전체 조회의 몇 %를 차지하는가 (기본 70%)
const HOT_RATIO = Number(__ENV.HOT_RATIO || 0.01);
const HOT_SHARE = Number(__ENV.HOT_SHARE || 0.70);

// 인기 구간은 앞쪽 id에 몰아둔다. 시드가 id 순서와 속성을 독립적으로 만들었으므로
// (name/category/price가 id % N으로 순환) 앞쪽을 인기로 잡아도 편향이 생기지 않는다.
function pickId(pool) {
  const hotCount = Math.max(1, Math.floor(pool * HOT_RATIO));
  return Math.random() < HOT_SHARE
    ? 1 + Math.floor(Math.random() * hotCount)
    : 1 + hotCount + Math.floor(Math.random() * (pool - hotCount));
}

const ok = new Counter('browse_2xx');
const err = new Counter('browse_err');
const msList = new Trend('browse_list_ms', true);
const msDetail = new Trend('browse_detail_ms', true);
const msVariant = new Trend('browse_variant_ms', true);

export const options = {
  discardResponseBodies: true,
  tags: { replica: __ENV.REPLICA || 'unset', arm: __ENV.ARM || 'unset' },
  scenarios: {
    catalog: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: __ENV.DURATION || '180s',
      preAllocatedVUs: Number(__ENV.PRE_VUS || 500),
      maxVUs: Number(__ENV.MAX_VUS || 8000),
      exec: 'go',
    },
  },
};

function record(res, trend) {
  trend.add(res.timings.duration);
  if (res.status >= 200 && res.status < 300) ok.add(1);
  else err.add(1);
}

export function go() {
  const roll = Math.random() * 100;

  if (roll < PCT_LIST) {
    // 목록/검색. 페이지를 흩어 놓아야 항상 같은 첫 페이지만 캐시되는 상황을 피한다.
    // 목록도 앞쪽 페이지가 압도적으로 많이 조회된다.
    const page = Math.random() < HOT_SHARE
      ? Math.floor(Math.random() * 3)
      : 3 + Math.floor(Math.random() * 47);
    const q = [`page=${page}`, `size=${PAGE_SIZE}`];
    // keyword(FULLTEXT)는 이 부하 모델에서 제외한다. MySQL InnoDB FTS는 LIMIT이
    // 있어도 매칭 문서 목록을 먼저 만들고, 그 위에 brand/image 조인과 DISTINCT가
    // 얹히면 0.82초가 걸린다(FULLTEXT 단독은 0.009초). 다중 단어 AND는 매칭이
    // 적어도 포스팅 리스트 교집합 때문에 1.0~1.2초로 더 느리다.
    //
    // 이 한 종류가 조회 비용을 지배해 버리면 replica/Redis 비교가 "느린 검색을
    // 누가 나눠 받나"로 변질된다. 검색은 별도 실험(ES)으로 분리하고, 여기서는
    // 실제 카탈로그 브라우징의 대부분인 카테고리/브랜드/가격 필터를 쓴다.
    const r2 = Math.random();
    if (r2 < 0.45) q.push(`category=${CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)]}`);
    else if (r2 < 0.75) q.push(`brandId=${1 + Math.floor(Math.random() * 100)}`);
    else {
      const lo = 10000 + Math.floor(Math.random() * 8) * 20000;
      q.push(`minPrice=${lo}`, `maxPrice=${lo + 40000}`);
    }
    // name 태그를 고정하지 않으면 k6가 쿼리스트링마다 시리즈를 만들어
    // Prometheus 카디널리티가 폭발한다(2026-08-11 캠페인 전멸 원인).
    record(http.get(`${API}/api/products?${q.join('&')}`,
      { timeout: '10s', tags: { name: 'GET /api/products' } }), msList);

  } else if (roll < PCT_LIST + PCT_DETAIL) {
    const id = pickId(PRODUCT_POOL);
    record(http.get(`${API}/api/products/${id}`,
      { timeout: '10s', tags: { name: 'GET /api/products/:id' } }), msDetail);

  } else {
    const id = pickId(VARIANT_POOL);
    record(http.get(`${API}/api/internal/products/variants/${id}`,
      { timeout: '10s', tags: { name: 'GET /api/internal/products/variants/:id' } }), msVariant);
  }
}
