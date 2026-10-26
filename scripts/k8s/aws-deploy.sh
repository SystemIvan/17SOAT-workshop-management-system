#!/usr/bin/env bash
# Deploys the application and dependencies to EKS. Used by both the CD pipeline and manual local operators.
# Requires: AWS CLI, kubectl, curl.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
AWS_REGION="${AWS_REGION:-us-east-1}"
CLUSTER_NAME="${CLUSTER_NAME:-wms-demo}"
NAMESPACE="workshop"

native_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi
}

for tool in aws kubectl curl; do
  command -v "${tool}" >/dev/null 2>&1 || { echo "Missing required tool: ${tool}" >&2; exit 1; }
done

# Validate required inputs
: "${IMAGE:?IMAGE must be set to the full container image URL with tag}"
: "${DB_ENDPOINT:?DB_ENDPOINT must be set to the host:port of the RDS MySQL instance}"
: "${DB_PASSWORD:?DB_PASSWORD must be set}"
: "${APP_SECURITY_JWT_SECRET:?APP_SECURITY_JWT_SECRET must be set}"
: "${APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET:?APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET must be set}"

DB_USERNAME="${DB_USERNAME:-workshop_app}"
ALLOWED_CIDRS="${ALLOWED_CIDRS:-}"

echo "Validating AWS identity..."
CALLER_ARN="$(aws sts get-caller-identity --query Arn --output text)"
if [[ "${CALLER_ARN}" != *":assumed-role/voclabs/"* ]]; then
  echo "ERROR: Caller ARN '${CALLER_ARN}' does not match expected role 'voclabs'. Aborting deploy." >&2
  exit 1
fi

echo "Connecting to EKS cluster '${CLUSTER_NAME}' in '${AWS_REGION}'..."
aws eks update-kubeconfig --region "${AWS_REGION}" --name "${CLUSTER_NAME}"

echo "Ensuring namespace '${NAMESPACE}' exists..."
kubectl apply -f "$(native_path "${ROOT_DIR}/k8s/base/namespace.yaml")"

echo "Applying secrets and simulator mappings..."
JDBC_URL="jdbc:mysql://${DB_ENDPOINT}/workshop?serverTimezone=America/Sao_Paulo&sslMode=REQUIRED"
kubectl -n "${NAMESPACE}" create secret generic workshop-app-secrets \
  --from-literal=SPRING_DATASOURCE_URL="${JDBC_URL}" \
  --from-literal=SPRING_DATASOURCE_USERNAME="${DB_USERNAME}" \
  --from-literal=SPRING_DATASOURCE_PASSWORD="${DB_PASSWORD}" \
  --from-literal=APP_SECURITY_JWT_SECRET="${APP_SECURITY_JWT_SECRET}" \
  --from-literal=APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET="${APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET}" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n "${NAMESPACE}" create configmap supplier-simulator-mappings \
  --from-file="$(native_path "${ROOT_DIR}/docker/wiremock/mappings")" \
  --dry-run=client -o yaml | kubectl apply -f -

# Prepare temporary kustomization overlay within k8s/overlays to preserve relative paths
TMP_DIR="${ROOT_DIR}/k8s/overlays/.tmp_deploy_$$"
mkdir -p "${TMP_DIR}"
cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

IMAGE_NAME="${IMAGE%:*}"
IMAGE_TAG="${IMAGE##*:}"

cat <<EOF > "${TMP_DIR}/kustomization.yaml"
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - ../aws

images:
  - name: workshop-management-system
    newName: ${IMAGE_NAME}
    newTag: "${IMAGE_TAG}"
EOF

if [ -n "${ALLOWED_CIDRS}" ]; then
  # Format ALLOWED_CIDRS as YAML array items
  CIDR_ITEMS=""
  IFS=',' read -ra CIDR_ARRAY <<< "${ALLOWED_CIDRS}"
  for cidr in "${CIDR_ARRAY[@]}"; do
    trimmed="$(echo "${cidr}" | xargs)"
    if [ -n "${trimmed}" ]; then
      CIDR_ITEMS="${CIDR_ITEMS}        - ${trimmed}\n"
    fi
  done

  cat <<EOF >> "${TMP_DIR}/kustomization.yaml"
patches:
  - target:
      kind: Service
      name: workshop-app
    patch: |-
      - op: add
        path: /spec/loadBalancerSourceRanges
        value:
$(printf '%b' "${CIDR_ITEMS}")
EOF
fi

echo "Applying Kubernetes manifests to '${NAMESPACE}'..."
kubectl apply -k "$(native_path "${TMP_DIR}")"

echo "Waiting for supplier-simulator rollout..."
kubectl -n "${NAMESPACE}" rollout status deployment/supplier-simulator --timeout=3m

echo "Waiting for workshop-app rollout (6 min timeout)..."
if ! kubectl -n "${NAMESPACE}" rollout status deployment/workshop-app --timeout=6m; then
  echo "Rollout failed. Executing rollout undo..." >&2
  kubectl -n "${NAMESPACE}" rollout undo deployment/workshop-app || true
  exit 1
fi

echo "Running smoke test via port-forward..."
PF_PORT=18088
kubectl -n "${NAMESPACE}" port-forward svc/workshop-app "${PF_PORT}:80" >/dev/null 2>&1 &
PF_PID=$!
stop_pf() {
  kill "${PF_PID}" >/dev/null 2>&1 || true
}
trap 'stop_pf; cleanup' EXIT

sleep 3
SMOKE_STATUS=0
SMOKE_RESPONSE="$(curl -s -w "\n%{http_code}" -X POST "http://localhost:${PF_PORT}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"changeme123"}' || SMOKE_STATUS=$?)"

HTTP_CODE="$(echo "${SMOKE_RESPONSE}" | tail -n1)"
if [ "${SMOKE_STATUS}" -ne 0 ] || [ "${HTTP_CODE}" != "200" ]; then
  echo "Smoke test failed (HTTP ${HTTP_CODE}). Executing rollout undo..." >&2
  kubectl -n "${NAMESPACE}" rollout undo deployment/workshop-app || true
  exit 1
fi

stop_pf
trap cleanup EXIT
echo "Deploy and smoke test completed successfully!"
