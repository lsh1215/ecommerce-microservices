import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';
// 조회 전용 부하. replica 라우팅 OFF/ON의 읽기 용량을 직접 비교한다.
const API = __ENV.PRODUCT_API || 'http://service-product:8081';
const POOL = Number(__ENV.VARIANT_POOL || 50000);
const RATE = Number(__ENV.RATE || 2500);
const ok = new Counter('browse_2xx'); const err = new Counter('browse_err');
const ms = new Trend('browse_ms', true);
export const options = {
  discardResponseBodies: true,
  tags: { replica: __ENV.REPLICA || 'unset' },
  scenarios: { read: { executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s',
    duration: __ENV.DURATION || '120s', preAllocatedVUs: 500, maxVUs: 5000, exec: 'go' } },
};
export function go() {
  const id = 1 + Math.floor(Math.random() * POOL);
  // name 태그 고정: 이게 없으면 k6가 URL마다 시리즈를 만들어(variant 5만개)
  // 메트릭 1종당 5만 시리즈 × 23종 = 115만 시리즈로 Prometheus가 OOM된다.
  const r = http.get(`${API}/api/internal/products/variants/${id}`,
    { timeout: '10s', tags: { name: 'GET /api/internal/products/variants/:id' } });
  ms.add(r.timings.duration);
  if (r.status >= 200 && r.status < 300) ok.add(1); else err.add(1);
}
