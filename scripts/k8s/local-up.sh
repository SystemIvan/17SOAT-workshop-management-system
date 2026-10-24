#!/usr/bin/env bash
# Creates (or reuses) the local kind cluster "wms" and deploys the application with the k8s/overlays/local manifests:
# MySQL, the supplier simulator and the application, plus metrics-server for the HPA. Idempotent; run it again to
# redeploy after code changes. Requires Docker, kind and kubectl. API: http://localhost:18080
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLUSTER_NAME="wms"
CONTEXT="kind-${CLUSTER_NAME}"
NAMESPACE="workshop"
IMAGE="workshop-management-system:local"
OVERLAY="${ROOT_DIR}/k8s/overlays/local"

# kind, kubectl and docker are Windows binaries under Git Bash: hand them Windows-style paths there.
native_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi
}

kc() {
  kubectl --context "${CONTEXT}" "$@"
}

for tool in docker kind kubectl; do
  command -v "${tool}" >/dev/null 2>&1 || { echo "Missing required tool: ${tool}" >&2; exit 1; }
done

ENV_FILE="${OVERLAY}/.env.local"
[ -f "${ENV_FILE}" ] || ENV_FILE="${OVERLAY}/.env.local.example"
set -a
# shellcheck source=/dev/null
. "${ENV_FILE}"
set +a

if ! kind get clusters | grep -qx "${CLUSTER_NAME}"; then
  kind create cluster --config "$(native_path "${OVERLAY}/kind-config.yaml")"
fi

docker build -t "${IMAGE}" "$(native_path "${ROOT_DIR}")"
kind load docker-image "${IMAGE}" --name "${CLUSTER_NAME}"

kc apply -k "$(native_path "${OVERLAY}/metrics-server")"
kc apply -f "$(native_path "${ROOT_DIR}/k8s/base/namespace.yaml")"

kc -n "${NAMESPACE}" create secret generic mysql-secrets \
  --from-literal=MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD}" \
  --from-literal=MYSQL_USER="${DB_USERNAME}" \
  --from-literal=MYSQL_PASSWORD="${DB_PASSWORD}" \
  --dry-run=client -o yaml | kc apply -f -
kc -n "${NAMESPACE}" create secret generic workshop-app-secrets \
  --from-literal=SPRING_DATASOURCE_URL="jdbc:mysql://mysql:3306/workshop?serverTimezone=America/Sao_Paulo&allowPublicKeyRetrieval=true&useSSL=false" \
  --from-literal=SPRING_DATASOURCE_USERNAME="${DB_USERNAME}" \
  --from-literal=SPRING_DATASOURCE_PASSWORD="${DB_PASSWORD}" \
  --from-literal=APP_SECURITY_JWT_SECRET="${APP_SECURITY_JWT_SECRET}" \
  --from-literal=APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET="${APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET}" \
  --dry-run=client -o yaml | kc apply -f -
kc -n "${NAMESPACE}" create configmap supplier-simulator-mappings \
  --from-file="$(native_path "${ROOT_DIR}/docker/wiremock/mappings")" \
  --dry-run=client -o yaml | kc apply -f -

# MySQL first, so the application does not start (and crash-loop) before the database accepts connections.
kc apply -k "$(native_path "${OVERLAY}")" -l app.kubernetes.io/name=mysql
kc -n "${NAMESPACE}" rollout status statefulset/mysql --timeout=5m
app_existed="$(kc -n "${NAMESPACE}" get deployment workshop-app -o name --ignore-not-found)"
kc apply -k "$(native_path "${OVERLAY}")"
# A rebuilt image keeps the "local" tag, so an existing Deployment must be restarted to pick it up.
if [ -n "${app_existed}" ]; then
  kc -n "${NAMESPACE}" rollout restart deployment/workshop-app
fi
kc -n "${NAMESPACE}" rollout status deployment/supplier-simulator --timeout=3m
kc -n "${NAMESPACE}" rollout status deployment/workshop-app --timeout=6m

echo "Application ready: http://localhost:18080 (POST /api/auth/login)"
