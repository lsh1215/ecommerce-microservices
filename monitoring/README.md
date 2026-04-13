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

## Existing Observability Stack

The `docker-compose.observability.yml` file contains the Grafana/Prometheus/OTEL/Loki/Tempo stack. Both stacks can run independently.
