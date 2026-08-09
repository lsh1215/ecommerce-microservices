# 부하 테스트 런 인덱스

모든 런은 여기에 한 줄로 등록한다. **등록되지 않은 런의 수치는 포트폴리오에 쓰지 않는다.**

| 날짜 | run-id | 시나리오 | 코드 | pod cpu limit | 스로틀 | 상태 | 기록 |
|---|---|---|---|---|---|---|---|
| 2026-07-31 | — | (예정) S1~S7 재측정 | `flash-sale-async-settle` | order/product 2000m | — | 예정 | — |

## 과거 런 — 환경 미기록 (신뢰 불가)

아래는 이 프로토콜이 생기기 전의 런이다. **어떤 pod CPU limit으로 돌렸는지 기록이 없어
수치의 인과를 확정할 수 없다.** 포트폴리오 근거로 쓰려면 재측정이 필요하다.

| 시점 | 산출물 | 문제 |
|---|---|---|
| 2026-07-26 07:22 | `docs/evidence/latest/flash-sale/gke/Summary.md` (G006) | 환경은 기록됨(2.5 vCPU). 단 Grafana/Hikari 미수집, reserve 엔드포인트 직결이라 전체 주문 경로 아님 |
| 2026-07-26 07:22 | `docs/evidence/latest/flash-sale/dry-run-local/Summary.md` (G005) | 로컬 단일 머신 — k6가 JVM과 CPU 공유. 절대 수치 아님(상대 비교만 유효) |
| 2026-07-26 21:09 | `flashsale-chart.png` (서버 관점 대시보드) | **CPU limit 기록 없음.** G006이 "500m에서 p95 25s로 붕괴, CPU 스로틀이 지배 원인"이라 적어둔 값과 캡처의 p95 27.7s가 사실상 일치 → 인과 확정 불가 |
| 2026-07-27 04:40 | `redis-chart.png` (k6 관점 대시보드) | 위와 동일 런으로 추정(실패율 97.6% 일치)이나 **시각 축이 없어 동일 런임을 증명 불가** |
| 2026-07-26~27 | `saga-ba.png` / `hotrow-anno.png` / `lock-hold.png` / `cb-panels.png` / `idem-*.png` / `outbox-anno.png` | 동일하게 환경 미기록 |

### 재측정이 필요한 이유 (요약)

1. **인과 미확정** — 붕괴 원인이 커넥션 고갈인지 CPU 스로틀인지 가를 근거가 없다.
2. **계측 주체 혼용** — 표는 k6 클라이언트, 대시보드는 server span인데 라벨이 없어 3.5배 격차가 모순으로 읽힌다.
3. **부하 모델 결함** — CB·cascading 시나리오가 closed model이었다 (2026-07-31 open model로 전환 완료).
4. **rps 라벨 불명** — 대시보드 주석은 `300rps`인데 스크립트 기본값은 180/600이고, `stress-test.js`의
   `ramping-vus 300`(VU≠rps)과 혼동됐을 가능성이 있다.
