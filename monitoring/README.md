# Monitoring

## Kafka UI

Web UI for inspecting Kafka topics, partitions, consumer groups, and messages
during local development.

### Start

```bash
# Bring up MySQL + Kafka first
./scripts/start.sh

# Then attach Kafka UI (joins the same compose network)
docker compose -f monitoring/docker-compose.kafka-ui.yml up -d
```

### Access

http://localhost:8090

### Stop

```bash
docker compose -f monitoring/docker-compose.kafka-ui.yml down
```

## Pinpoint APM

For distributed tracing in the deployed k8s cluster, see `k8s/monitoring/`. The
Pinpoint agent is baked into every service image and activated only when
`PINPOINT_COLLECTOR_IP` is present in the environment — applied automatically
when `k8s/monitoring/pinpoint-config.yml` is deployed alongside the rest of the
stack.

Pinpoint is not available for local development because the 3.x HBase image is
amd64-only and Apple Silicon emulation makes local usage impractical.
