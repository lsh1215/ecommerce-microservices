import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

// 조회 경로의 무릎(knee) 탐색.
//
// browse-only.js는 고정 rate라 "이 부하를 견디나"만 답한다. 오프로드(read replica /
// Redis 캐시)가 값어치를 갖는 지점을 찾으려면 무엇이 먼저 무너지는지를 봐야 한다.
//
// REV2 A0에서 조회 2,500 rps는 앱 CPU 59%, p95 5.4ms로 여유였다. 즉 그 부하에서는
// 오프로드가 잴 게 없다. 이 스크립트는 SLO를 깨는 지점까지 단계적으로 올린다.
//
// open model(ramping-arrival-rate) 고정: closed model은 서버가 느려지면 부하도 같이
// 줄어 무릎을 과소평가한다.

const API = __ENV.PRODUCT_API || 'http://service-product:8081';
const POOL = Number(__ENV.VARIANT_POOL || 50000);
const START = Number(__ENV.START_RATE || 2000);
const STEP = Number(__ENV.STEP_RATE || 1000);
const STEPS = Number(__ENV.STEPS || 6);
const STAGE = __ENV.STAGE_DUR || '60s';

const ok = new Counter('browse_2xx');
const err = new Counter('browse_err');
const ms = new Trend('browse_ms', true);

// 2000 -> 3000 -> ... 계단식. 각 단계를 STAGE 동안 유지해 정상상태를 만든 뒤 다음으로.
const stages = [];
for (let i = 0; i < STEPS; i++) {
  const target = START + STEP * i;
  stages.push({ target, duration: '15s' });
  stages.push({ target, duration: STAGE });
}

export const options = {
  discardResponseBodies: true,
  tags: { replica: __ENV.REPLICA || 'unset', arm: __ENV.ARM || 'unset' },
  scenarios: {
    knee: {
      executor: 'ramping-arrival-rate',
      startRate: START,
      timeUnit: '1s',
      // 무릎 위에서는 응답이 느려져 VU가 필요해진다. 부족하면 k6가 dropped을 내고
      // run-k6-job.sh의 유효성 게이트가 런을 무효 처리한다.
      preAllocatedVUs: Number(__ENV.PRE_VUS || 1000),
      maxVUs: Number(__ENV.MAX_VUS || 8000),
      stages,
      exec: 'go',
    },
  },
};

export function go() {
  const id = 1 + Math.floor(Math.random() * POOL);
  // name 태그 고정 — URL별 시리즈 분화(카디널리티 폭발) 방지.
  const r = http.get(`${API}/api/internal/products/variants/${id}`,
    { timeout: '10s', tags: { name: 'GET /api/internal/products/variants/:id' } });
  ms.add(r.timings.duration);
  if (r.status >= 200 && r.status < 300) ok.add(1);
  else err.add(1);
}
