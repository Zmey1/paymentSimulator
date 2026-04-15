#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE_DIR="${ROOT_DIR}/k8s"
OUTPUT_DIR="${ROOT_DIR}/.rendered/k8s"

required_vars=(
  ECR_REGISTRY
  IMAGE_TAG
  RDS_ENDPOINT
  DB_PASSWORD
)

for var_name in "${required_vars[@]}"; do
  if [[ -z "${!var_name:-}" ]]; then
    echo "Missing required environment variable: ${var_name}" >&2
    exit 1
  fi
done

DB_USERNAME="${DB_USERNAME:-payments_user}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-kafka.default.svc.cluster.local:9092}"
RAZORPAY_ENABLED="${RAZORPAY_ENABLED:-false}"
RAZORPAY_KEY_ID="${RAZORPAY_KEY_ID:-}"
RAZORPAY_KEY_SECRET="${RAZORPAY_KEY_SECRET:-}"
RAZORPAY_MERCHANT_NAME="${RAZORPAY_MERCHANT_NAME:-PayFlow Demo}"
RAZORPAY_DESCRIPTION="${RAZORPAY_DESCRIPTION:-Sandbox Checkout}"
RAZORPAY_RECEIVER_NAME="${RAZORPAY_RECEIVER_NAME:-Demo Merchant}"

rm -rf "${OUTPUT_DIR}"
mkdir -p "${OUTPUT_DIR}"
cp -R "${TEMPLATE_DIR}/." "${OUTPUT_DIR}/"

escape_for_sed() {
  printf '%s' "$1" | sed -e 's/[\\/&]/\\&/g'
}

render_file() {
  local target_file="$1"
  shift

  while (($#)); do
    local placeholder="$1"
    local replacement="$2"
    local escaped
    escaped="$(escape_for_sed "${replacement}")"
    sed -i "s|${placeholder}|${escaped}|g" "${target_file}"
    shift 2
  done
}

while IFS= read -r file; do
  render_file "${file}" \
    "__ECR_REGISTRY__" "${ECR_REGISTRY}" \
    "__IMAGE_TAG__" "${IMAGE_TAG}" \
    "__RDS_ENDPOINT__" "${RDS_ENDPOINT}" \
    "__DB_USERNAME__" "${DB_USERNAME}" \
    "__DB_PASSWORD__" "${DB_PASSWORD}" \
    "__KAFKA_BOOTSTRAP_SERVERS__" "${KAFKA_BOOTSTRAP_SERVERS}" \
    "__RAZORPAY_ENABLED__" "${RAZORPAY_ENABLED}" \
    "__RAZORPAY_KEY_ID__" "${RAZORPAY_KEY_ID}" \
    "__RAZORPAY_KEY_SECRET__" "${RAZORPAY_KEY_SECRET}" \
    "__RAZORPAY_MERCHANT_NAME__" "${RAZORPAY_MERCHANT_NAME}" \
    "__RAZORPAY_DESCRIPTION__" "${RAZORPAY_DESCRIPTION}" \
    "__RAZORPAY_RECEIVER_NAME__" "${RAZORPAY_RECEIVER_NAME}"
done < <(find "${OUTPUT_DIR}" -type f \( -name '*.yml' -o -name '*.yaml' \))

echo "Rendered Kubernetes manifests to ${OUTPUT_DIR}"
