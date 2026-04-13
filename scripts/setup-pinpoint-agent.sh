#!/bin/bash
set -euo pipefail

PINPOINT_VERSION="3.0.1"
AGENT_DIR="$(cd "$(dirname "$0")/.." && pwd)/pinpoint-agent"

if [ -d "$AGENT_DIR" ]; then
  echo "Pinpoint agent already exists at $AGENT_DIR"
  exit 0
fi

echo "Downloading Pinpoint Agent v${PINPOINT_VERSION}..."
mkdir -p "$AGENT_DIR"
curl -fSL "https://github.com/pinpoint-apm/pinpoint/releases/download/v${PINPOINT_VERSION}/pinpoint-agent-${PINPOINT_VERSION}.tar.gz" \
  -o "/tmp/pinpoint-agent.tar.gz"
tar -xzf "/tmp/pinpoint-agent.tar.gz" -C "$AGENT_DIR" --strip-components=1
rm -f "/tmp/pinpoint-agent.tar.gz"

echo "Pinpoint agent extracted to $AGENT_DIR"
