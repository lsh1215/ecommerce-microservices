# 재측정 핸드오프

> 이 문서만 읽고 바로 실행할 수 있어야 한다. 새 세션이라면 여기부터 시작한다.

## 현재 상태 (2026-08-01 06:15 UTC) — **R7 완료 · 클라우드 정리 끝 · 포트폴리오 반영 완료**

- 실행 중인 것 없음. `clusters=0` `instances=0` `pvc disks=0` — **과금 없음.**
  (R1~R6이 남긴 고아 PersistentDisk 22개도 이번에 정리했다. 클러스터만 지우면 디스크가 남는다.)
- 판정: `runs/2026-08-01-r7/VERDICT.md` (S1·S2 결론 + 측정 오류 정정)
- 근거 화면: `runs/2026-08-01-r7/shots/` — **실제 Grafana 캡처 24장**

### 확정된 결론

| 항목 | 결론 | 근거 |
|---|---|---|
| **S2 선착순** | **확정.** async가 p95 47→31ms, p99 58→34ms | R5·R7 **두 번 독립 측정이 일치** |
| **S1 재고 핫키** | **Atomic 안정 / 비관적 락 불안정** | 3회 중 비관적 락 **2회 붕괴** |
| S1 붕괴 원인 | **주문 서비스 커넥션 풀 고갈** | pending 191 · 획득 대기 25.5s · 풀 10 전량 점유 |
| CPU | 병목 아님 | 전 시행 스로틀 ≈0 |
| InnoDB 행 락 | 대기 0 | 직렬화는 있으나 락 대기로 나타나지 않음 |

### S1 3회 시행 (같은 부하 `10→40 rps`)

| 시행 | p95 | timeout | 판정 |
|---|---|---|---|
| R6 비관적 락 | 10,943ms | 766 | 붕괴 |
| R7 비관적 락 ① | 40.5ms | 0 | 통과 |
| R7 비관적 락 ② | 15,821ms | 1,988 | 붕괴 |
| R6 / R7 Atomic | 44.2 / 35.4ms | 0 / 0 | 안정 |

**한 번만 돌리면 어느 쪽으로도 틀린 결론이 난다.** S1은 반복 측정이 필수다.

## ★★ 측정 방법 경고 — 게이지는 사후에 찍지 마라

R2~R6 내내 "HikariCP pending 0"으로 판정하고 그것을 근거로 포트폴리오의 **"커넥션 풀 고갈" 서사를
폐기했다. 그 판정이 틀렸다.** `collect-metrics.sh`가 pending을 **게이지 순간값**으로, 그것도
**부하가 끝난 뒤** 조회했기 때문이다. 그 시점엔 대기 큐가 이미 빠져 항상 0이 나온다.

```bash
# ✗ 틀린 방법 — 부하 끝난 뒤의 값을 읽는다. 항상 0.
max by (app) (hikaricp_connections_pending)

# ✓ 구간 최댓값을 봐야 한다.
max by (app) (max_over_time(hikaricp_connections_pending{app=~"service-.*"}[12m]))
```

`max_over_time`으로 다시 재니 **pending 191 · 획득 대기 25.5초**가 나왔다.
포트폴리오 원본의 *"Hikari 풀(10) 고갈 · pending 182"* 는 **기전과 자릿수가 사실상 맞았다.**
`collect-metrics.sh`는 수정 완료(`hikari_pending_max`, `hikari_active_max`, `hikari_acquire_wait_max_seconds`).

> 같은 함정이 다른 게이지에도 있다. `*_active`, `*_pending`, `*_current_waits`, `threads_running`
> 처럼 **순간 상태를 나타내는 지표는 전부 구간 최댓값으로 봐야 한다.**

## 근거 화면을 만드는 법 (R7에서 확립)

포트폴리오에는 **실제 Grafana 캡처만** 넣는다. 손으로 그린 차트는 출처가 없어 근거가 못 되고,
다른 슬라이드가 전부 실제 대시보드라 이질감으로 오히려 신뢰를 깎는다.

```bash
# 1. k6 → Prometheus remote-write (k8s/loadtest/k6-job.yaml 에 이미 반영)
#    이게 없으면 Grafana k6 대시보드가 비어 있다. R6까지 화면이 없던 이유다.
# 2. 렌더링은 클러스터 안에서 시킨다
kubectl apply -f k8s/monitoring/grafana-image-renderer.yml
kubectl -n monitoring set env deploy/grafana \
  GF_RENDERING_SERVER_URL=http://grafana-image-renderer.monitoring.svc.cluster.local:8081/render \
  GF_RENDERING_CALLBACK_URL=http://grafana.monitoring.svc.cluster.local:3000/grafana/
# 3. 캡처 (windows.json 구간에 정확히 맞춘다)
./scripts/loadtest/capture-grafana.sh docs/loadtest/runs/<날짜>-<run> <출력디렉터리>
```

로컬 브라우저 + `kubectl port-forward` 로 Grafana SPA를 띄우면 **에셋 요청이 몰려 터널이 막히고
빈 화면이 찍힌다.** 반드시 in-cluster 렌더러를 쓴다.

### 캡처 함정 3개 (전부 R7에서 겪음)

1. **k6 지연 단위는 초다.** `k6_http_req_duration_p95=0.0396` → 39.6ms. 패널 단위를 `s`로 둔다.
2. **실패 응답이 별도 시계열이다.** 503 한 건의 p95가 5초짜리 평평한 선으로 그려져 성공 지연을 가린다.
   지연 패널은 `expected_response="true"` 로 거른다.
3. **Grafana 다운샘플링이 스파이크를 지운다.** p99 3,087ms가 403ms로 보였다.
   `max_over_time(...[$__interval])` 로 감싼다. **근거가 실제보다 유리하게 보이면 안 된다.**

## 포트폴리오 반영 현황 — **완료**

편집 대상: `/Users/leesanghun/My_Project/agent-engineering/Career-Skills/portfolio-v3-web/`
(**레포가 다르다.** 이 워크트리가 아니다. 원본 `portfolio-v2-web/`은 건드리지 않는다.)

| 슬라이드 | 반영 |
|---|---|
| Cover 카드 | `47ms → 31ms` (선착순 예약 p95) |
| 3 성과 카드 | `타임아웃 1,988 → 0` (재고 핫키 3회 반복) |
| 4 고민 05 | 칩 수치 교체 |
| 5 노드 표 | **실제 구성으로 교정** — 4× e2-standard-8 + 역할 분리(db/redis/loadgen taint) |
| 5 요약표 | Hot Row·Redis 재고 행 교체 |
| **8 (S1)** | 표=3회 시행 · 서술=붕괴 2/3회 · **근거=실제 Grafana 2장** |
| **9 (S2)** | 표=R7 수치 · **근거=실제 Grafana 2장** |
| 24 기술 표 | 수치 교체 |

렌더 확인 완료 — 슬라이드 8·9 모두 넘침 0.

```bash
cd /Users/leesanghun/My_Project/agent-engineering/Career-Skills/portfolio-v3-web
agent-browser open "file://$PWD/index.html?v=$(date +%s)#slide-8" --viewport 1920x1080
agent-browser screenshot /tmp/s8.png
# ★ ?v=$(date +%s) 를 빼면 이전 렌더가 그대로 나온다 (캐시)
```

## 다음에 할 수 있는 것

- **S1 붕괴 조건 특정.** 지금은 "3회 중 2회"라는 빈도만 안다. 반복을 5~10회로 늘리거나
  램프 상한을 낮춰(30 rps 등) knee 위치를 좁힌다. 포폴 문구는 현재 3회 기준으로 정확하다.
- **SAGA `HikariCP pending 10 → 0`** (슬라이드 5·7·24) 재검증. R2~R7이 다루지 않은 시나리오다.
  이제는 "pending은 원래 0"이라고 의심할 근거가 없다 — 그 판정 자체가 측정 오류였다.
  재볼 때는 반드시 `max_over_time`.
- **S3~S6** (CB 격리 · SAGA 전파 · Outbox 유실 · Idempotent 중복) — open model 전환은 됐으나 미실행.

## 참고 — 빌드는 다시 하지 않는다

Artifact Registry(`asia-northeast3-docker.pkg.dev/project-4605f2b6-fff1-4fc2-bdf/ecommerce/`)에
이미지 5종이 있다. **코드가 안 바뀌었으면 다시 빌드하지 않는다.** R1에서 빌드에만 약 2시간이 걸렸고
그것이 dead-man's switch 예산을 소진시켜 측정 없이 끝난 원인이었다.

```
service-order:loadtest-r1       service-payment:loadtest-r1
service-customer:loadtest-r1    service-product:loadtest-r1        ← Atomic UPDATE + Redis 예약
                                service-product:loadtest-r1-pess   ← S1 비교군(비관적 락, @Lock(PESSIMISTIC_WRITE))
```

## 실행 스크립트

| 스크립트 | 용도 |
|---|---|
| `run-r7.sh` | 전체 4 시나리오 + 구간 기록 + **teardown 안 함**(캡처용) |
| `run-r7-repeat-pess.sh` | 살아 있는 클러스터에서 비관적 락만 1회 더 (재현성 확인) |
| `capture-grafana.sh` | windows.json 구간에 맞춰 대시보드 PNG 회수 |
| `collect-metrics.sh` | promQL 지표 회수 (**max_over_time 기반으로 수정됨**) |
| `deadman-switch.sh` | 세션이 죽어도 클러스터 자동 정리 |

**teardown 후에는 반드시 디스크까지 확인한다.** 클러스터만 지우면 `pvc-*` 디스크가 남아 계속 과금된다.

```bash
gcloud container clusters list                      # 0
gcloud compute disks list --filter='name~^pvc-'     # 0 이어야 한다
gcloud compute disks list --filter="name~^pvc- AND -users:*" --format="value(name)" \
  | xargs -n 10 gcloud compute disks delete --zone asia-northeast3-a --quiet
```
