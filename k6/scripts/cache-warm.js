import http from 'k6/http';
import { Counter } from 'k6/metrics';

// 캐시 웜업. 측정 전에 인기 상품을 Redis에 적재한다.
//
// 왜 필요한가: 측정 런에서 캐시를 채우면서 동시에 재면 앞부분이 전부 미스라
// 히트율과 처리량이 과소평가된다. 반대로 웜업 없이 "캐시 있음" 결과를 발표하면
// 정상 운영 상태를 재지 못한 것이 된다. 운영 중인 서비스의 캐시는 이미 더워져
// 있으므로, 그 상태를 만들어 놓고 재는 것이 맞다.
//
// 적재 대상은 부하 모델의 인기 구간과 동일해야 한다(browse-catalog.js의
// HOT_RATIO / HOT_SHARE). 여기서 상위 HOT_RATIO만 순차 조회해 채운다.
//
// 재고가 든 응답(productDetail / variantDetail)은 TTL이 3초라 웜업 효과가
// 짧다. 그래서 웜업은 목록 캐시(TTL 5분)에 집중하고, 상세는 측정 시작 직전
// 짧게 훑어 커넥션/JIT까지 같이 데운다.

const API = __ENV.PRODUCT_API || 'http://service-product:8081';
const PRODUCT_POOL = Number(__ENV.PRODUCT_POOL || 2000000);
const VARIANT_POOL = Number(__ENV.VARIANT_POOL || 6000000);
// browse 부하 모델(mixed-read-write.js)의 핫셋과 반드시 같아야 한다.
// 다르면 웜업한 키와 측정에서 때리는 키가 어긋나 히트가 나지 않는다.
const HOT_RATIO = Number(__ENV.HOT_RATIO || 0.001);
const PAGE_SIZE = Number(__ENV.PAGE_SIZE || 20);
const DETAIL_SAMPLE = Number(__ENV.DETAIL_SAMPLE || 8000);

const CATEGORIES = ['T-Shirts', 'Hoodies', 'Outerwear', 'Pants', 'Shorts', 'Shirts', 'Coats'];

const warmed = new Counter('warm_ok');
const failed = new Counter('warm_err');

export const options = {
  discardResponseBodies: true,
  scenarios: {
    warm: {
      executor: 'shared-iterations',
      vus: Number(__ENV.WARM_VUS || 40),
      iterations: Number(__ENV.WARM_ITERATIONS || 30000),
      maxDuration: __ENV.WARM_MAX || '10m',
      exec: 'warm',
    },
  },
};

function hit(url, name) {
  const r = http.get(url, { timeout: '30s', tags: { name } });
  if (r.status >= 200 && r.status < 300) warmed.add(1); else failed.add(1);
}

export function warm() {
  const i = __ITER;

  // 1) 목록 캐시: 모든 필터 조합의 앞쪽 페이지. 부하 모델이 실제로 때리는 키와
  //    같은 조합이어야 캐시 키가 맞는다.
  const page = i % 3;
  const mod = Math.floor(i / 3) % 3;
  if (mod === 0) {
    const c = CATEGORIES[Math.floor(i / 9) % CATEGORIES.length];
    hit(`${API}/api/products?page=${page}&size=${PAGE_SIZE}&category=${c}`, 'warm list');
  } else if (mod === 1) {
    const b = 1 + (Math.floor(i / 9) % 100);
    hit(`${API}/api/products?page=${page}&size=${PAGE_SIZE}&brandId=${b}`, 'warm list');
  } else {
    const lo = 10000 + (Math.floor(i / 9) % 8) * 20000;
    hit(`${API}/api/products?page=${page}&size=${PAGE_SIZE}&minPrice=${lo}&maxPrice=${lo + 40000}`,
        'warm list');
  }

  // 2) 인기 상세/재고: 상위 HOT_RATIO 구간을 훑어 버퍼풀과 JIT까지 데운다.
  const hotProducts = Math.max(1, Math.floor(PRODUCT_POOL * HOT_RATIO));
  const hotVariants = Math.max(1, Math.floor(VARIANT_POOL * HOT_RATIO));
  if (i < DETAIL_SAMPLE) {
    hit(`${API}/api/products/${1 + (i % hotProducts)}`, 'warm detail');
    hit(`${API}/api/internal/products/variants/${1 + (i % hotVariants)}`, 'warm variant');
  }
}
