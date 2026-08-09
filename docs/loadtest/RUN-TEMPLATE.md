# 런 기록 — `<run-id>`

> 복사해서 `docs/loadtest/runs/<YYYY-MM-DD>-<run-id>/run-record.md` 로 쓴다.
> `env-pre.json` / `env-post.json` 은 `capture-env.sh` 가 자동 생성하므로 여기서 다시 적지 않는다.
> **이 문서는 "자동으로 못 남는 것"만 사람이 채운다.**

## 1. 무엇을 증명하려는 런인가

- 시나리오 ID: `S?`
- 증명 대상 (한 문장):
- 비교 축 (before/after가 무엇과 무엇인가):
- 이 런으로 **증명되지 않는 것** (미리 적는다):

## 2. 코드 상태

| 항목 | 값 |
|---|---|
| 브랜치 / 커밋 | `` |
| 이미지 태그 | `` |
| 비교 대상 커밋 (before) | `` |
| 코드 차이 한 줄 요약 | |

## 3. 환경 — 자동 덤프와 대조

`env-pre.json` 을 열어 아래를 **눈으로 확인하고** 체크한다. 자동 덤프가 있어도 확인은 사람이 한다.

- [ ] pod CPU limit이 **부하 테스트 프로파일**(order/product 2000m)인가? 개발 기본값 500m이 아닌가?
- [ ] JVM `MaxRAMPercentage` 가 적용됐는가?
- [ ] 부하 생성기가 **in-cluster k6 Job**인가? (port-forward는 터널을 측정한다)
- [ ] 시드 재고 / 초기 상태가 이전 런과 동일한가?
- [ ] JVM warm-up을 했고 그 구간을 버렸는가?

## 4. 부하 프로파일

| 항목 | 값 |
|---|---|
| 스크립트 | `k6/scripts/*.js` |
| executor | `ramping-arrival-rate` / `constant-arrival-rate` / (closed면 이유 필수) |
| stages 또는 rate | |
| 총 제공 요청(offered) | |
| `TESTID` | |

closed model을 썼다면 이유:

## 5. 결과 — 클라이언트 (k6)

| 지표 | before | after |
|---|---|---|
| `http_req_duration` p95 | | |
| `http_req_duration` p99 | | |
| `http_req_waiting` p95 (TTFB) | | |
| `http_req_blocked` p95 | | |
| `admitted_2xx` | | |
| `rejected_429` | | |
| `failed_5xx` | | |
| `client_timeouts` | | |
| **`dropped_iterations`** | | |

> `dropped_iterations` 가 0이 아니면 **제공 부하가 목표에 못 미친 것**이다. rps 라벨을 쓰기 전에 반드시 확인.

## 6. 결과 — 서버

| 지표 | before | after |
|---|---|---|
| server span p95 / p99 | | |
| 5xx 비율 | | |
| HikariCP pending | | |
| JVM heap / GC pause | | |
| `kubectl top` CPU | | |

### 6.1 CPU 스로틀 판정 (필수)

`rate(container_cpu_cfs_throttled_seconds_total[1m])` 측정 구간 최대값:

- order: ` ` / product: ` `
- [ ] **유의미한 스로틀 없음 — 이 런은 유효하다**
- [ ] 스로틀 발생 — **이 런은 폐기하고 limit을 올려 재실행한다** (코드가 아니라 limit을 측정한 것)

## 7. 데이터 정합성

| 검증 | 쿼리 | 결과 |
|---|---|---|
| 오버셀 | `SELECT MIN(stock_quantity) FROM product_variant` | |
| 결제 중복 | `SELECT order_id, COUNT(*) FROM payment GROUP BY order_id HAVING COUNT(*)>1` | |
| Outbox 잔여 | `SELECT COUNT(*) FROM outbox WHERE status='PENDING'` | |

## 8. 해석

- 관측된 것 (사실만):
- 병목이 어느 계층이었는가, **그리고 그것을 무엇으로 갈랐는가**:
- 대안 가설과 그것을 배제한 근거:
- 이 수치를 포트폴리오에 쓸 때 반드시 함께 적어야 할 라벨:

## 9. teardown

- [ ] 클러스터 삭제 확인 (`gcloud container clusters list` → 0)
- [ ] PersistentDisk `pvc-*` 삭제 확인 (이름 필터로는 놓친다 — 전체 목록에서 확인)
- [ ] Artifact Registry 런 태그 이미지 삭제 확인
- [ ] 최종 잔여: clusters=0 / instances=0 / disks=0
