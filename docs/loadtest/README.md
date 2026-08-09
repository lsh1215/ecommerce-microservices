# 부하 테스트 프로토콜

이 문서는 **"이 수치가 어떤 환경에서 나왔는가"를 나중에 반드시 답할 수 있게** 만들기 위해 존재한다.
부하 테스트를 새로 돌리기 전에 이 문서를 먼저 읽고, 끝난 뒤에는 런 기록을 남긴다.

---

## 0. 이 문서가 생긴 이유 (사고 기록)

2026-07 포트폴리오 근거 재감사에서 다음이 드러났다.

- 대시보드 캡처에 `예약 p95 27.7s · 5xx 97.6% · HikariCP pending 182`가 찍혀 있고,
  포트폴리오는 이를 **"커넥션 풀 고갈"** 의 근거로 사용했다.
- 그런데 같은 레포의 `docs/evidence/latest/flash-sale/gke/Summary.md` 에는
  > *The first GKE run used the manifest's default 500 mCPU limit and collapsed (p95 25 s, 8,290 dropped
  > k6 iterations) — dominated by **CPU throttling, NOT the reserve logic**.*
  라고 기록돼 있었다.
- **그 캡처를 어떤 CPU limit으로 돌렸는지 기록이 없어서, 인과를 확정할 수 없었다.**
  `p95 25s`(스로틀)와 `p95 27.7s`(덱 근거)가 사실상 같은 값이라 의심은 강했지만 증명이 불가능했다.

교훈은 하나다. **자원 제약이 병목이면 코드가 아니라 limit을 측정한 것이다.**
그리고 **환경을 기록하지 않으면, 무엇을 측정했는지 나중에 아무도 모른다.**

---

### 0.1 두 번째 교훈 — 안전장치 예산은 빌드 시간을 포함해야 한다 (2026-07-31)

첫 재측정 시도가 **측정 한 건도 못 하고 끝났다.** 원인은 시간 예산이었다.

- dead-man's switch를 **120분**으로 걸고 클러스터 생성과 Cloud Build를 동시에 시작했다.
- 그런데 4개 서비스 Java 이미지 빌드가 **약 2시간**이 걸렸다. 빌드가 끝났을 때 예산이 이미 소진돼
  스위치가 예정대로 발화했고, 클러스터는 배포 직전에 삭제됐다.
- 안전장치는 **설계대로 정확히 동작했다**(클러스터 + PVC 8개 자동 정리, 잔여 0). 틀린 것은 예산 산정이었다.

규칙으로 고정한다.

- **빌드는 측정과 분리한다.** 이미지를 먼저 만들어 Artifact Registry에 올려두고, 클러스터는
  **이미지가 준비된 뒤에** 만든다. 클러스터가 빌드를 기다리며 과금되는 구간을 없앤다.
- **스위치 예산은 클러스터 생성 이후 구간만 덮으면 된다.** 배포 + 시드 + 워밍업 + 시나리오 + 캡처 +
  teardown 기준으로 잡고, 여유를 1.5배 둔다. 빌드 시간을 여기에 포함시키지 않는다.
- 이미지는 **런 사이에 재사용한다.** 코드가 안 바뀌었으면 다시 빌드하지 않는다.
  (AR 이미지는 스위치 발화 시에도 삭제되지 않으므로 재시도 비용이 크게 줄어든다.)

### 0.2 세 번째 교훈 — dead-man's switch는 맥 절전에 멈춘다 (2026-08-01)

`deadman-switch.sh`는 `sleep`으로 대기한다. **macOS가 절전에 들어가면 `sleep` 타이머도 멈추지만
GCP 과금은 계속된다.** 실제로 110분 예약한 스위치가 경과 2시간 50분에도 발화하지 않았고,
클러스터가 그만큼 더 살아 있었다.

- **절대 시각으로 판단하게 고쳐야 한다** — `sleep` 대신 목표 시각을 저장하고
  짧은 주기로 깨어나 `date`와 비교하는 루프로 바꾼다(`caffeinate`는 절전 자체를 막으므로 부적합).
- 더 확실한 방법은 **클라우드 쪽 안전장치**다. GKE 클러스터에 TTL을 걸거나
  Cloud Scheduler + Cloud Functions로 삭제를 예약하면 로컬 머신 상태와 무관해진다.
- 그때까지는 **런이 끝나면 즉시 수동 teardown**하고 스위치를 백스톱으로만 취급한다.

## 1. 불변 규칙 (타협 금지)

1. **모든 런은 환경 덤프를 남긴다.** `scripts/loadtest/capture-env.sh` 를 실행 직전·직후에 돌린다.
   사람이 기록을 잊어도 환경이 파일로 남아야 한다.
2. **스로틀 knee 위에서 측정한다.** 측정 중 `container_cpu_cfs_throttled_seconds_total` 증가분이
   유의미하면 **그 런은 폐기한다.** 코드가 아니라 limit을 잰 것이기 때문이다.
3. **기본은 open model(arrival-rate)이다.** closed model(`*-vus`)을 쓰려면 런 기록에 이유를 적는다.
   서버가 느려지면 부하도 같이 줄어드는 closed model은 장애 실험에서 blast radius를 과소평가한다.
4. **계측 주체를 항상 라벨링한다.** 같은 "p95"라도 아래는 서로 다른 값이며 섞어 쓰면 안 된다.
   - `k6 client` — 요청 발신부터 응답 수신까지. 큐 대기·커넥션 획득 포함.
   - `server span` — 워커 스레드가 요청을 집어든 뒤부터. **서버가 못 본 대기는 빠진다.**
   - 포화 상태에서는 두 값이 크게 갈리는 것이 **정상**이고, 그 격차 자체가 큐잉의 증거다.
5. **백분위를 고정한다.** 표·그래프·캡션이 같은 백분위를 쓴다. p99가 클라이언트 timeout 상한에
   붙어 있으면 그것은 측정값이 아니라 **검열된 값**이므로 헤드라인으로 쓰지 않는다.
6. **teardown 후 잔여 리소스 0을 확인한다.** 클러스터뿐 아니라 **PersistentDisk(`pvc-*`)와
   Artifact Registry 이미지**까지. GKE는 동적 PD를 클러스터 이름이 아니라 `pvc-<uuid>`로 만들기 때문에
   이름 필터만으로는 놓친다.

---

## 2. 환경 스펙 (정본)

부하 테스트 전용 스펙이다. **개발용 기본값과 다르다.**

### 2.1 클러스터

| 항목 | 값 | 이유 |
|---|---|---|
| 리전/존 | `asia-northeast3-a` | 지연 변수 최소화 (로컬과 무관하게 in-cluster 부하 생성) |
| 노드 | `e2-standard-4` (4 vCPU / 16GB) × 3 | 서비스 pod에 vCPU 2를 주고도 노드가 여유를 갖는 최소 크기 |
| 디스크 | pd-balanced | MySQL write latency가 측정 노이즈가 되지 않도록 |

### 2.2 Pod 리소스 — **부하 테스트 프로파일**

기본 매니페스트(`k8s/services/*.yml`)는 개발용으로 `requests 200m / limits 500m`, `memory 512Mi`다.
**이 값으로는 부하 테스트를 하면 안 된다.** 아래로 오버레이한다.

| 대상 | requests | limits | 비고 |
|---|---|---|---|
| `service-order` | cpu 1000m / mem 1Gi | cpu 2000m / mem 2Gi | 부하 진입점 |
| `service-product` | cpu 1000m / mem 1Gi | cpu 2000m / mem 2Gi | 재고 경합 대상 |
| `service-payment` | cpu 500m / mem 1Gi | cpu 1000m / mem 2Gi | SAGA 소비자 |
| `service-customer` | cpu 200m / mem 512Mi | cpu 500m / mem 1Gi | 부하 경로 밖 |

> **왜 limit을 올려도 실사용은 낮은가**
> G006에서 product를 2.5 vCPU로 올렸더니 실사용은 70~92 mCPU(3~4%)에 그쳤다.
> CPU 수요가 커서가 아니라 **CFS quota가 100ms 주기로 리셋되기 때문**이다. 평균 사용률이 낮아도
> 버스트가 몰리면 주기 내 quota를 태우고 스로틀된다. **평균 CPU 사용률이 낮다는 것은
> 스로틀이 없었다는 증거가 아니다.** 반드시 throttled 지표를 직접 본다.

JVM 힙은 컨테이너 메모리의 1/4이 기본이라 512Mi 컨테이너에서는 128Mi가 된다. GC가 측정을 오염시키므로
`-XX:MaxRAMPercentage=75` 를 함께 준다.

### 2.3 부하 생성기

**in-cluster k6 Job으로 돌린다.** 로컬 `kubectl port-forward`는 단일 커넥션 프록시라
~20 rps에서 막히고 **서비스가 아니라 터널을 측정한다** (G006에서 실측 확인됨).

---

## 3. 시나리오 카탈로그

각 시나리오는 **무엇을 증명하는가**를 먼저 적는다. 증명 대상이 없으면 그 런은 돌리지 않는다.

| ID | 스크립트 | 증명 대상 | 모델 | 규모 |
|---|---|---|---|---|
| **S1** | `hot-row-rampup.js` | 단일 재고 row 경합에서 **락 구현별 knee 차이** (비관적 락 vs 조건부 Atomic UPDATE) | open · `ramping-arrival-rate` | 5 → 180 rps 단계 상승 |
| **S2** | `flash-sale-spike.js` | 선착순 스파이크에서 **예약 경로의 DB 커넥션 점유 제거 효과** (동기 DB vs Redis) | open · `ramping-arrival-rate` | 5 → 600 rps 버스트 |
| **S3** | `phase4-slow-product.js` | downstream 지연의 **blast radius 격리** (CB on/off) | **open으로 전환 필요** (현재 closed) | 고정 도착률 + 지연 주입 |
| **S4** | `cascading-failure.js` | 결제 지연의 **주문 API 전파 차단** (동기 vs SAGA) | **open으로 전환 필요** (현재 closed) | 고정 도착률 |
| **S5** | (장애 주입) | Kafka 다운 중 **이벤트 유실 0** · 복구 후 전량 발행 | 부하 아님 | 주문 30건 + 브로커 정지 |
| **S6** | (중복 주입) | 동일 eventId 재유입에서 **결제 1건** | 부하 아님 | 같은 eventId 8건 |
| **S7** | `hot-row-rampup.js` | **수평 확장 시 knee 이동** (replicas 1 → 3) | open | S1과 동일 프로파일 |

### 3.1 S3·S4를 open model로 바꿔야 하는 이유

현재 두 스크립트는 `constant-vus`다. VU를 고정하면 **서버가 느려질 때 클라이언트도 같이 느려져서
제공 부하가 저절로 줄어든다.** 장애 격리 실험에서 이건 치명적이다 — 장애가 번지는 정도를
과소평가하게 되고, "CB가 막아줬다"가 아니라 "부하가 알아서 빠졌다"일 수 있다.
`constant-arrival-rate`로 바꿔 **응답이 느려져도 제공 부하는 유지**되게 한다.

### 3.2 규모에 대한 입장

절대 rps로 "대용량"을 주장하지 않는다. 개인 프로젝트 예산에서 나올 수 있는 숫자는
프로덕션 규모와 비교되면 오히려 과장으로 읽힌다. 대신 아래를 증명 대상으로 삼는다.

1. **포화점(knee)을 찾았는가** — 어느 도착률에서 무엇이 먼저 무너지는가
2. **병목을 계층별로 특정했는가** — CPU / 커넥션 / 락 / 큐 중 무엇인가, 그리고 그것을 어떻게 갈랐는가
3. **degradation이 graceful한가** — 429 shedding, circuit breaking, DLQ
4. **불변식이 유지되는가** — 오버셀 0 · 유실 0 · 중복 0
5. **수평 확장에 반응하는가** — replica를 늘리면 knee가 이동하는가 (S7)

이 다섯은 규모와 무관하게 성립하며, **규모를 키운 숫자보다 방어하기 쉽고 설명하기 좋다.**

---

## 4. 수집 체크리스트

런 하나당 아래를 **전부** 남긴다. 하나라도 빠지면 그 수치는 포트폴리오에 쓰지 않는다.

### 4.1 k6 (클라이언트 관점)

- `summary.json` (`--summary-export`) 및 터미널 출력 캡처
- 필수 지표
  - `http_req_duration` — **p95 · p99 · avg · max**
  - `http_req_blocked` / `http_req_connecting` / `http_req_waiting`
    → 클라이언트 대기 중 **서버가 받아주기 전 구간**을 분리하는 데 필요
  - `dropped_iterations` — **0이 아니면 제공 부하가 목표에 못 미친 것이다.** rps 라벨을 쓰기 전에 확인
  - 시나리오별 커스텀 카운터 (`admitted_2xx` / `rejected_429` / `failed_5xx` / `client_timeouts`)
- `TESTID` 태그를 반드시 지정 (대시보드 라벨과 파일명이 일치해야 함)

### 4.2 서버 관점

- Grafana 대시보드 캡처 시 **시간 범위를 런 구간에 정확히 맞춘다.**
  범위가 넓으면 `rate()[1m]` 평균이 유휴 구간에 희석돼 k6 처리량과 어긋난다.
- stat 패널의 reducer가 `Last`인지 `Mean`인지 캡처에 함께 남긴다.
- 필수 패널: HTTP 5xx 비율 · server span p95/p99 · **HikariCP pending** · JVM heap/GC
- **CPU throttling**: `rate(container_cpu_cfs_throttled_seconds_total[1m])` — 규칙 2의 판정 근거
- `kubectl top pod` 스냅샷

### 4.3 데이터 정합성

- 재고: `SELECT MIN(stock_quantity) FROM product_variant` → **음수 없음**
- 결제 중복: `SELECT order_id, COUNT(*) FROM payment GROUP BY order_id HAVING COUNT(*) > 1` → **0행**
- Outbox: 잔여 `PENDING` 수 → 복구 후 **0**

---

## 5. 실행 절차

```bash
# 0) 환경 덤프 (실행 전)
scripts/loadtest/capture-env.sh <run-id> pre

# 1) 부하 테스트 프로파일 오버레이 적용 (개발 기본값 500m로 돌리지 말 것)
kubectl apply -k k8s/overlays/loadtest

# 2) JVM warm-up (측정 대상 아님 — 결과는 버린다)
#    각 서비스에 저부하를 1분 흘린 뒤 본 측정 시작

# 3) 시나리오 실행 (in-cluster k6 Job)
#    TESTID는 대시보드 라벨·파일명과 일치시킨다

# 4) 환경 덤프 (실행 후) + 증거 수집
scripts/loadtest/capture-env.sh <run-id> post

# 5) teardown 후 잔여 확인
gcloud container clusters list          # 0
gcloud compute disks list               # pvc-* 0
gcloud artifacts docker images list ... # 런 태그 0
```

---

## 6. 런 기록

모든 런은 `docs/loadtest/runs/<YYYY-MM-DD>-<run-id>/` 에 남긴다.

```
docs/loadtest/runs/2026-07-31-r1/
├── run-record.md      # RUN-TEMPLATE.md 복사본 — 사람이 쓰는 부분
├── env-pre.json       # capture-env.sh 자동 생성
├── env-post.json
├── k6/                # summary.json + 터미널 출력
├── grafana/           # 패널 캡처
└── db/                # 정합성 쿼리 결과
```

인덱스는 [`RUNS.md`](./RUNS.md) 에서 관리한다.
