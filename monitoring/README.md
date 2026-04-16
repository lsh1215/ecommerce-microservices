# Monitoring

## Pinpoint APM

Pinpoint provides distributed tracing, service map visualization, and request-level profiling for the MSA services.

### Prerequisites

- Docker and Docker Compose

### Start Pinpoint

```bash
docker compose -f monitoring/docker-compose.pinpoint.yml up -d
```

HBase initialization takes 30-60 seconds on first start. Wait before connecting services.

### Access

| Component          | URL                    |
|--------------------|------------------------|
| Pinpoint Web UI    | http://localhost:8079  |
| HBase Master UI    | http://localhost:16010 |
| HBase Region UI    | http://localhost:16030 |

### Run services with Pinpoint agent

1. Download the agent (one-time):
   ```bash
   ./scripts/setup-pinpoint-agent.sh
   ```

2. Start a service with the agent attached:
   ```bash
   ./scripts/run-with-pinpoint.sh service-product
   ./scripts/run-with-pinpoint.sh service-order
   ./scripts/run-with-pinpoint.sh service-payment
   ./scripts/run-with-pinpoint.sh service-customer
   ```

3. Open the Pinpoint Web UI and select the application from the dropdown.

### Stop Pinpoint

```bash
docker compose -f monitoring/docker-compose.pinpoint.yml down
```

To remove stored trace data:

```bash
docker compose -f monitoring/docker-compose.pinpoint.yml down -v
```

## Notes

Pinpoint is the sole APM for this project. A Grafana/Prometheus stack was
evaluated and removed in favor of Pinpoint's distributed tracing + service
map capabilities, which better fit a portfolio-scale MSA where request-level
visibility matters more than time-series dashboards.

### Local Apple Silicon caveat

The Pinpoint 3.0.x HBase image is amd64 only — it runs under Docker Desktop
emulation on Apple Silicon. The provided compose pins `platform: linux/amd64`
and sets the Zookeeper address via Spring relaxed-binding env vars. HBase
initialization takes 60-120 seconds on first start due to the emulation
overhead; wait for `hbase-create` table creation to complete in
`docker logs pinpoint-hbase` before starting services with the agent.

For production, consider running Pinpoint on a native amd64 host or VM.
