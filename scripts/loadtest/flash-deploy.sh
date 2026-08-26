#!/usr/bin/env bash
# 선착순 스택 배포.
#
# 매니페스트의 nodeSelector 는 이 프로젝트가 원래 쓰던 역할 이름(svc-order, db-shared 등)을
# 가리키는데, 이번 클러스터는 풀을 svc/db 둘로 합쳤다. 매니페스트를 고치는 대신 렌더링
# 시점에 매핑한다. 매니페스트가 계속 유일한 출처로 남고, 이 실험만의 배치가 여기 모인다.
#
# Usage: flash-deploy.sh <REPO> <TAG>
set -euo pipefail
REPO="${1:?REPO required}"; TAG="${2:?TAG required}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"
K=kubectl
ORDER_REPLICAS="${ORDER_REPLICAS:-2}"
PRODUCT_REPLICAS="${PRODUCT_REPLICAS:-1}"

OV="$(mktemp -d "$ROOT/.k8s-ov.XXXXXX")"
trap 'rm -rf "$OV"' EXIT

render() { # src -> stdout, 역할 매핑 + 치환
  sed -e 's/role: svc-order/role: svc/' \
      -e 's/role: svc-product/role: svc/' \
      -e 's/role: db-shared/role: db/' \
      -e 's/role: db-product/role: db/' "$@"
}

$K apply -f k8s/namespace.yml -f k8s/monitoring/namespace.yml
$K apply -f k8s/base/configmap.yml -f k8s/base/secrets.yml
$K apply -f k8s/monitoring/otel-config-active.yml 2>/dev/null || true

echo "== monitoring =="
for f in alloy prometheus loki tempo grafana; do render "k8s/monitoring/$f.yml" | $K apply -f -; done
$K apply -f k8s/monitoring/dashboards/ 2>/dev/null || true

echo "== mysql + exporters =="
for db in order product; do render "k8s/base/mysql-$db-statefulset.yml" | $K apply -f -; done
python3 - <<'PY' | $K apply -f -
import pathlib
docs = pathlib.Path("k8s/base/mysqld-exporters.yml").read_text().split("\n---\n")
print("\n---\n".join(d for d in docs if "mysqld-exporter-order" in d or "mysqld-exporter-product" in d))
PY

echo "== kafka =="
render k8s/base/kafka-statefulset.yml | $K apply -f -

echo "== services =="
for s in order product; do
  case "$s" in order) reps=$ORDER_REPLICAS ;; product) reps=$PRODUCT_REPLICAS ;; esac
  render "k8s/services/service-$s.yml" \
    | sed -e "s|^  replicas: .*|  replicas: $reps|" > "$OV/service-$s.yml"
done
cat > "$OV/kustomization.yml" <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources: [service-order.yml, service-product.yml]
images:
  - name: ecommerce/service-order
    newName: $REPO/service-order
    newTag: $TAG
  - name: ecommerce/service-product
    newName: $REPO/service-product
    newTag: $TAG
EOF
rendered="$($K kustomize "$OV")"
# images transformer 는 이름이 안 맞으면 조용히 아무것도 안 한다. 깨끗하게 렌더된 결과가
# 엉뚱한 이미지를 배포한다. 요청한 이미지가 실제로 나왔는지 보고 적용한다.
got="$(printf '%s\n' "$rendered" | awk '$1 == "image:" { print $2 }' | sort | tr '\n' ' ')"
want="$REPO/service-order:$TAG $REPO/service-product:$TAG "
[ "$got" = "$want" ] || { echo "ERROR: 이미지 주입 실패
  기대: $want
  실제: $got" >&2; exit 1; }
printf '%s\n' "$rendered" | $K apply -f -
echo "flash-deploy done: tag=$TAG order=$ORDER_REPLICAS product=$PRODUCT_REPLICAS"
