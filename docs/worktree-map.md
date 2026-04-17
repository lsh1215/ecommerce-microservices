# Phase Worktree Map

Each phase of this project was developed as a rolling stack of improvements on the same codebase. To make the "before / after" evidence reproducible — and to let a reviewer boot any historical snapshot without clobbering `main` — every phase has its own `git worktree` pinned to the terminal commit of that phase.

All worktrees live next to the main repo at `/Users/leesanghun/My_Project/ecommerce-microservices-worktrees/` and share the same `.git` object store (no extra disk cost beyond source checkouts).

## Layout

```
/Users/leesanghun/My_Project/
├── ecommerce-microservices/                  # main repo (HEAD = main)
└── ecommerce-microservices-worktrees/
    ├── phase0/   detached HEAD @ 274c26d
    ├── phase1/   detached HEAD @ 22b8d1f
    ├── phase2/   detached HEAD @ eedeaa3
    ├── phase3/   detached HEAD @ 9ba1b98
    ├── phase4/   detached HEAD @ 4a9849f
    └── phase5/   detached HEAD @ e791aa5
```

## Commit pins

| Worktree | Commit | Commit title | Build (`./gradlew build -x test`) |
|----------|--------|-------------|-----------------------------------|
| `phase0` | `274c26d` | fix: change Product collections from List to Set (MultipleBagFetchException) | ✅ |
| `phase1` | `22b8d1f` | feat: implement event-driven SAGA orchestration (Phase 1) | ✅ |
| `phase2` | `eedeaa3` | refactor: migrate Outbox to status-based state model | ✅ |
| `phase3` | `9ba1b98` | test: add comprehensive tests for Phase 1-3 core logic | ✅ |
| `phase4` | `4a9849f` | test: add k6 script for Phase 4 circuit breaker slow-service scenario | ✅ |
| `phase5` | `e791aa5` | test: add comprehensive k6 load test suite (Phase 5) | ✅ |

### Why these commits

- **phase0** is pinned to `274c26d` (the last pre-baseline-docs commit) rather than an earlier mid-Phase-0 commit because it includes the `MultipleBagFetchException` fix; Product can boot reliably under fetch load from this point forward.
- **phase2** is pinned to `eedeaa3` (the status-based Outbox refactor merged from `refactor/outbox-status-redesign`) so that Phase 2 demonstrations use the canonical Outbox pattern. Phases 3–5 deliberately stay pinned to their historical commits with the older `publishedAt`-only schema — their evidence tests (idempotency, Circuit Breaker, load) do not depend on Outbox internals, so the temporal asymmetry is not observable in evidence output.
- **phase3–5** pins are the terminal commit of each phase as originally merged to `main` (pre-refactor history).

## Evidence file location (important)

**All evidence artifacts (k6 output, SQL snapshots, harness logs) live under the MAIN REPO's `docs/phase-*/evidence/` tree, NEVER inside a worktree checkout.**

Worktrees are read-only reproduction environments. When capturing evidence from a worktree, redirect output via an absolute path back to the main repo:

```bash
cd /Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase0
MAIN_DOCS=/Users/leesanghun/My_Project/ecommerce-microservices/docs

k6 run k6/scripts/cascading-failure.js 2>&1 \
  | tee "$MAIN_DOCS/phase-0-baseline/evidence/k6-cascading-failure.txt"
```

Commits that add or update evidence files are made from the **main repo checkout** on an appropriate feature branch (`docs/portfolio-evidence-sync`), not from the worktree — the worktree stays on detached HEAD at its frozen phase commit.

## Usage

### Boot a phase for reproduction

```bash
# 1. Start shared infra (one time, from main repo)
cd /Users/leesanghun/My_Project/ecommerce-microservices
docker compose -f infra/docker-compose.yml up -d mysql kafka

# 2. Switch to the phase worktree and boot that phase's services
cd /Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase1
cd backend-v2
./gradlew :service-order:bootRun --args='--spring.profiles.active=local' &
./gradlew :service-payment:bootRun --args='--spring.profiles.active=local' &
./gradlew :service-product:bootRun --args='--spring.profiles.active=local' &
./gradlew :service-customer:bootRun --args='--spring.profiles.active=local' &
```

### Verify a worktree's commit pin

```bash
cd /Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase2
git rev-parse HEAD        # should match the commit in the table above
git log --oneline -1      # commit title
```

### Before / after convention

The evidence convention is:
- Phase N → N+1 **Before** = reproduce from `phaseN` worktree
- Phase N → N+1 **After** = reproduce from `phaseN+1` worktree

Example: Phase 1 → Phase 2 "event loss" demonstration — Before runs from `phase1` (no Outbox), After runs from `phase2` (status-based Outbox).

## Cleanup

To remove all phase worktrees when you no longer need them:

```bash
cd /Users/leesanghun/My_Project/ecommerce-microservices
for p in 0 1 2 3 4 5; do
  git worktree remove --force \
    /Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase$p
done
git worktree prune
```

To recreate them (e.g., after a fresh clone):

```bash
cd /Users/leesanghun/My_Project/ecommerce-microservices
mkdir -p /Users/leesanghun/My_Project/ecommerce-microservices-worktrees
git worktree add --detach ../ecommerce-microservices-worktrees/phase0 274c26d
git worktree add --detach ../ecommerce-microservices-worktrees/phase1 22b8d1f
git worktree add --detach ../ecommerce-microservices-worktrees/phase2 eedeaa3
git worktree add --detach ../ecommerce-microservices-worktrees/phase3 9ba1b98
git worktree add --detach ../ecommerce-microservices-worktrees/phase4 4a9849f
git worktree add --detach ../ecommerce-microservices-worktrees/phase5 e791aa5
```

## Notes on stale docker compose resources

When multiple worktrees boot the same docker compose stack, container/volume names collide.  Use per-worktree project names to namespace them:

```bash
cd /Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase0
docker compose -p ecomm-phase0 -f infra/docker-compose.yml up -d
```

Stop one phase before starting another to avoid port conflicts on MySQL `:3307` and Kafka `:9092`. Alternatively, override ports per-worktree via a `.env` file (local only; not committed).
