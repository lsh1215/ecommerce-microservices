import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

// 선착순 접수 스파이크.
//
// 재는 것은 처리량이 아니라 공정성과 정합성이다. 재고 100~1,000 개에 90만 요청이 오므로
// 성공률은 0.01~0.1% 다. "몇 rps 를 냈나"가 아니라 "누가 이겼나"가 결과다.
//
// 202 접수(=Kafka 에 실림), 409 매진 거절, 503 발행 실패를 나눠 센다. 합치면 매진 플래그가
// 언제 섰는지, 그래서 몇 건이나 토픽에 들어가는 것을 막았는지가 안 보인다.

// 409 는 매진 플래그가 일한 결과이지 실패가 아니다. 기본값대로 두면 실패율 게이트가
// 이 런을 통째로 무효 처리한다.
http.setResponseCallback(http.expectedStatuses(202, 409));

const ORDER_API = __ENV.ORDER_API || 'http://service-order:8082';
const VARIANT_ID = Number(__ENV.VARIANT_ID || 1);
const RATE = Number(__ENV.RATE || 3000);
const DURATION = __ENV.DURATION || '300s';

// 사전 할당 VU.
//
// 필요한 동시성 = 도착률 x 응답시간이다. 이 경로는 매진 전 몇백 ms 동안만 Kafka 발행
// (5~10ms)을 하고 그 뒤로는 메모리 조회 + 409 (1ms 안팎)라 동시성이 아주 낮다.
//
// 그런데 여유를 크게 잡으면 안 된다. 앞선 캠페인에서 같은 조건에 이 값만 240 에서 480 으로
// 올렸더니 p95 가 31ms 에서 3,503ms 로 갈렸다. 여유분 VU 도 시작하면서 연결을 열고, 서버가
// 그 연결을 전부 돌보느라 늦어지고, 늦어지니 k6 가 VU 를 더 만드는 되먹임이 돈다.
//
// 최악 구간(발행 10ms)의 동시성이 RATE x 0.01 이므로 그 8배를 잡는다.
// t=0 의 연결 폭발을 흡수할 만큼 미리 잡는다.
//
// 정상 구간의 동시성은 아주 낮다(도착률 x 1ms = 3). 그런데 부하가 0 에서 목표까지 1초 만에
// 오르는 순간에는 모든 VU 가 TCP 연결을 새로 열고, 그 첫 왕복이 수십 ms 다. 그때 VU 가
// 모자라면 반복이 빠진다. 실측에서 240 으로 두었을 때 91만 건 중 1,669 건이 그렇게 빠졌고
// VU 는 816 까지 늘었다.
//
// 여유를 크게 잡는 것이 늘 안전한 것은 아니다. 앞선 캠페인에서 응답이 20ms 인 경로에
// 이 값을 240 에서 480 으로 올렸더니 p95 가 31ms 에서 3,503ms 로 갈렸다. 유휴 VU 도 연결을
// 쥐고 있어서, 서버가 그 연결을 돌보느라 늦어지고 k6 가 VU 를 더 만드는 되먹임이 돈다.
//
// 이 경로는 응답이 1ms 라 사정이 다르다. 연결당 서버가 하는 일이 거의 없으므로 연결 폭발을
// 덮을 만큼 잡아도 그 고리가 시작되지 않는다.
const PRE_VUS = Number(__ENV.PRE_VUS || Math.max(50, Math.ceil(RATE * 0.3)));
const MAX_VUS = Number(__ENV.MAX_VUS || Math.min(RATE * 5, 6000));
const WARM = __ENV.WARM === '1';

const accepted = new Counter('flash_202');
const soldOut = new Counter('flash_409');
const failed = new Counter('flash_5xx');
const latency = new Trend('flash_ms', true);

export const options = {
  discardResponseBodies: true,
  tags: { arm: __ENV.ARM || 'unset', stock: __ENV.STOCK || 'unset' },
  scenarios: WARM ? {
    // 식은 JVM 에 곧장 목표 속도를 때리면 워밍업 자체가 포화 상태로 돌고 회복하지 못한다.
    warm: {
      executor: 'ramping-arrival-rate',
      startRate: Math.max(10, Math.ceil(RATE * 0.1)),
      timeUnit: '1s',
      preAllocatedVUs: PRE_VUS,
      maxVUs: MAX_VUS,
      exec: 'submit',
      stages: [
        { target: Math.ceil(RATE * 0.35), duration: '60s' },
        { target: Math.ceil(RATE * 0.7), duration: '60s' },
        { target: RATE, duration: '60s' },
      ],
    },
  } : {
    // 스파이크가 끝난 뒤 낮은 부하로 30초를 더 돈다. 여기서 지연이 평시 수준으로
    // 돌아오지 않으면 스파이크가 시스템에 흔적을 남긴 것이고, 그건 런 안에서만 보인다.
    spike: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: PRE_VUS,
      maxVUs: MAX_VUS,
      exec: 'submit',
      stages: [
        // 0 에서 목표까지 1초. 발매 시각에 사람들이 한꺼번에 누르는 모양이다.
        { target: RATE, duration: '1s' },
        { target: RATE, duration: DURATION },
        { target: Math.ceil(RATE * 0.05), duration: '5s' },
        { target: Math.ceil(RATE * 0.05), duration: '30s' },
      ],
    },
  },
};

export function submit() {
  // customerId 는 요청마다 달라야 한다. 같은 값이 재사용되면 1인당 제한 같은 가드에 걸려
  // 접수 경로가 아니라 그 가드를 재게 된다.
  const customerId = __VU * 100000000 + __ITER;
  const res = http.post(
    `${ORDER_API}/api/orders/flash-reserve`,
    JSON.stringify({ variantId: VARIANT_ID, quantity: 1 }),
    {
      headers: { 'Content-Type': 'application/json', 'X-Customer-Id': String(customerId) },
      timeout: '10s',
      tags: { name: 'POST /api/orders/flash-reserve' },
    },
  );
  latency.add(res.timings.duration);
  if (res.status === 202) accepted.add(1);
  else if (res.status === 409) soldOut.add(1);
  else failed.add(1);
}
