import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// 선착순(first-come-first-served) 플래시 세일 스파이크 시나리오.
//
// 목적: admission gate(레이어 1, 토큰 버킷 429) + async settle 하에서 급격한 스파이크를
// 흘려보낼 때 다음을 관측한다.
//   - 승인(admitted_2xx) vs 거부(rejected_429) 비율 — 게이트가 부하를 앞단에서 흡수하는가.
//   - 5xx 폭주가 없는가 — 게이트가 DB reserve 경로에 닿기 전에 초과분을 차단하는가.
//   - 오버셀 0 — 재고가 음수로 내려가지 않는가(DB decreaseStock WHERE stock>=qty 백스톱).
//
// 오버셀 최종 판정은 실행 후 DB row-count(scripts/loadtest/verify-evidence 계열)로 확정한다.
// 이 스크립트의 oversell_probe는 in-run HTTP 확률 프로브로 재고 음수를 조기에 감지하는 보조 신호다.
//
// 결과 해석 주의(리뷰 반영):
//  1) oversell 게이트는 vacuous-pass를 막기 위해 oversell_probe_observed(실제 수치 관측 횟수)에
//     count>0 threshold를 함께 건다. 프로브가 한 번도 재고를 못 읽었다면(엔드포인트 미도달 등)
//     실행은 FAIL 처리되어 "관측 없이 초록불"을 방지한다.
//  2) 프로브 대상은 PROBE_URL로 분리한다. 로컬 docker-compose는 product:8081 직결이면 되지만,
//     GKE에서 /api/internal/**는 ingress로 노출되지 않으므로 in-cluster runner/port-forward로
//     service-product:8081을 PROBE_URL에 지정해야 한다(공용 LB로 쏘면 404).
//  3) ramping-arrival-rate는 응답이 느려져도 offered load를 유지하지만, latency가 커지면
//     maxVUs 한계로 dropped_iterations가 발생해 실제 offered rate가 목표보다 낮아질 수 있다.
//     피크 offered load를 신뢰하기 전 summary의 dropped_iterations를 반드시 확인한다.
//  4) rejected_429는 order 서비스를 직접(기본 8082) 칠 때만 admission-shed로 해석된다.
//     k6와 order 사이에 자체 rate-limiter를 둔 프록시가 있으면 그 429가 섞여 오분류된다.
//  5) 불변식: PROBE_DURATION >= sum(SPIKE_STAGES) + gracefulStop. SPIKE_STAGES를 늘리면
//     PROBE_DURATION도 같이 늘려야 스파이크 꼬리 구간까지 관측된다.

const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';
const PRODUCT_API = __ENV.PRODUCT_API || 'http://localhost:8081';
// 오버셀 프로브 전용 base(변형 변수 {id}는 붙이지 않은 상태). GKE에서는 in-cluster 주소로 덮어쓴다.
const PROBE_BASE = __ENV.PROBE_URL || `${PRODUCT_API}/api/internal/products/variants`;
const AUTH_HEADER = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.sig'}`;
const VARIANT_ID = Number(__ENV.VARIANT_ID || 1);
const CUSTOMER_ID = Number(__ENV.CUSTOMER_ID || 1);

// 스파이크 형태: 짧은 warm baseline -> 급격한 버스트(선착순 개시) -> 지속 -> 완화.
const SPIKE_STAGES = __ENV.SPIKE_STAGES
  ? JSON.parse(__ENV.SPIKE_STAGES)
  : [
      { duration: '30s', target: 20 }, // warm baseline (JVM warm-up 포함)
      { duration: '10s', target: 600 }, // 급격한 스파이크
      { duration: '1m', target: 600 }, // 스파이크 지속
      { duration: '20s', target: 50 }, // 완화
      { duration: '20s', target: 0 },
    ];

// 주문 결과 분류 카운터. http_reqs는 실패도 포함하므로 성공 throughput으로 읽으면 안 된다.
const admitted2xx = new Counter('admitted_2xx'); // 주문 생성 성공(선착순 당첨)
const rejected429 = new Counter('rejected_429'); // admission gate가 흘린 요청(PRODUCT_ADMISSION_REJECTED)
const rejectedBusiness4xx = new Counter('rejected_business_4xx'); // 재고 부족 등 비즈니스 4xx
const failed5xx = new Counter('failed_5xx'); // 서버 오류(게이트가 있으면 낮아야 함)
const clientTimeouts = new Counter('client_timeouts'); // status 0
const oversellProbeChecks = new Counter('oversell_probe_checks'); // 프로브 시도 횟수(도달 여부 무관)
const oversellProbeObserved = new Counter('oversell_probe_observed'); // 실제 수치 재고를 읽은 횟수
const oversellProbeNegative = new Counter('oversell_probe_negative'); // 재고 음수 목격 횟수 -> 반드시 0
const stockObserved = new Trend('stock_observed', false);

export const options = {
  tags: { testid: __ENV.TESTID || 'flash-sale-spike' },
  summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    flash_spike: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.PRE_VUS || 500),
      maxVUs: Number(__ENV.MAX_VUS || 3000),
      stages: SPIKE_STAGES,
      gracefulStop: '15s',
      exec: 'placeOrder',
    },
    // 스파이크 전 구간을 덮는 저빈도 재고 프로브(오버셀 조기 감지).
    oversell_probe: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.PROBE_RATE || 5),
      timeUnit: '1s',
      duration: __ENV.PROBE_DURATION || '2m40s',
      preAllocatedVUs: 5,
      maxVUs: 20,
      exec: 'probeStock',
    },
  },
  thresholds: {
    // 오버셀은 절대 허용되지 않는다: in-run 프로브가 재고 음수를 한 번이라도 보면 FAIL.
    oversell_probe_negative: ['count<1'],
    // Vacuous-pass 방지: 프로브가 실제 재고를 최소 한 번은 읽었어야 위 게이트가 의미를 가진다.
    oversell_probe_observed: ['count>0'],
    // 관측용(첫 실패로 멈추지 않고 스파이크 곡선 전체를 남긴다).
    http_req_failed: ['rate<1'],
  },
};

export function placeOrder() {
  const payload = JSON.stringify({
    customerId: CUSTOMER_ID,
    items: [
      {
        productVariantId: VARIANT_ID,
        productId: 1,
        productName: 'Flash Sale',
        size: 'M',
        color: 'Black',
        unitPrice: 29900,
        quantity: 1,
      },
    ],
    shippingAddress: {
      recipientName: 'Test',
      phone: '010-0000-0000',
      zipCode: '06234',
      address1: 'Seoul',
      address2: 'Test',
    },
  });

  const res = http.post(`${ORDER_API}/api/orders`, payload, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: AUTH_HEADER,
      'X-Customer-Id': String(CUSTOMER_ID),
    },
    timeout: '30s',
  });

  if (res.status >= 200 && res.status < 300) admitted2xx.add(1);
  else if (res.status === 429) rejected429.add(1); // admission gate가 흘림
  else if (res.status === 0) clientTimeouts.add(1);
  else if (res.status >= 500) failed5xx.add(1);
  else rejectedBusiness4xx.add(1);

  // 게이트가 제대로 동작하면 초과 부하는 429로 깔끔히 거부되지 5xx/timeout으로 무너지지 않아야 한다.
  check(res, {
    'no 5xx storm (status < 500)': (r) => r.status < 500,
    'not client-timeout': (r) => r.status !== 0,
  });
}

export function probeStock() {
  const res = http.get(`${PROBE_BASE}/${VARIANT_ID}`, {
    headers: { Authorization: AUTH_HEADER },
    timeout: '5s',
  });
  oversellProbeChecks.add(1);
  if (res.status === 200) {
    let stock = NaN;
    try {
      stock = res.json('data.stockQuantity');
    } catch (e) {
      stock = NaN;
    }
    if (typeof stock === 'number' && !Number.isNaN(stock)) {
      oversellProbeObserved.add(1);
      stockObserved.add(stock);
      // DB decreaseStock WHERE stock>=qty 백스톱이 유지되면 재고는 절대 음수가 될 수 없다.
      if (stock < 0) oversellProbeNegative.add(1);
    }
  }
}
