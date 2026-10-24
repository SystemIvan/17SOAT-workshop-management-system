#!/usr/bin/env bash
# Cleans up the workshop namespace, waits for Kubernetes-managed load balancers to disappear, and destroys Terraform infrastructure.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INFRA_DIR="${ROOT_DIR}/infra"
AWS_REGION="${AWS_REGION:-us-east-1}"
CLUSTER_NAME="${CLUSTER_NAME:-wms-demo}"
NAMESPACE="workshop"

for tool in aws kubectl terraform; do
  command -v "${tool}" >/dev/null 2>&1 || { echo "Missing required tool: ${tool}" >&2; exit 1; }
done

echo "Validating AWS identity..."
CALLER_ARN="$(aws sts get-caller-identity --query Arn --output text)"
if [[ "${CALLER_ARN}" != *":assumed-role/voclabs/"* ]]; then
  echo "ERROR: Caller ARN '${CALLER_ARN}' does not match expected role 'voclabs'. Aborting destroy." >&2
  exit 1
fi

echo "Updating kubeconfig..."
if aws eks update-kubeconfig --region "${AWS_REGION}" --name "${CLUSTER_NAME}" >/dev/null 2>&1; then
  echo "Deleting namespace '${NAMESPACE}' to trigger ELB removal..."
  kubectl delete namespace "${NAMESPACE}" --ignore-not-found --timeout=3m || true

  echo "Waiting for Classic and Application Load Balancers to be removed..."
  for i in {1..30}; do
    LBS="$(aws elb describe-load-balancers --region "${AWS_REGION}" --query "LoadBalancerDescriptions[?contains(DNSName, 'elb')].LoadBalancerName" --output text 2>/dev/null || true)"
    if [ -z "${LBS}" ]; then
      echo "No orphan load balancers detected."
      break
    fi
    echo "Waiting for load balancers to clear: ${LBS} (${i}/30)..."
    sleep 10
  done
else
  echo "Could not reach EKS cluster. Proceeding directly to Terraform destroy."
fi

echo "Running terraform destroy in ${INFRA_DIR}..."
cd "${INFRA_DIR}"
terraform destroy -auto-approve -input=false

echo "Infrastructure destroyed successfully."
