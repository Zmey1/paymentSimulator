#!/bin/bash
set -euo pipefail

AWS_REGION="ap-south-1"
EKS_CLUSTER="payments-sim-eks"
TF_STATE_BUCKET="payments-sim-tfstate"
ECR_REPOS=(
  "payments-sim/payment-service"
  "payments-sim/notification-service"
  "payments-sim/fraud-detection-service"
  "payments-sim/payment-dashboard"
)

echo "==> [1/7] Configuring kubectl..."
aws eks update-kubeconfig --name "$EKS_CLUSTER" --region "$AWS_REGION"

echo "==> [2/7] Uninstalling monitoring Helm release..."
helm uninstall kube-prometheus-stack -n monitoring --ignore-not-found || true
kubectl delete namespace monitoring --ignore-not-found --wait=true || true

echo "==> [3/7] Deleting Kubernetes LoadBalancer services..."
kubectl delete svc payment-dashboard --ignore-not-found || true

echo "==> [4/7] Waiting 45s for AWS ELBs to fully deregister..."
sleep 45

echo "==> [5/7] Emptying ECR repositories..."
for REPO in "${ECR_REPOS[@]}"; do
  IMAGE_IDS=$(aws ecr list-images --repository-name "$REPO" --region "$AWS_REGION" \
    --query 'imageIds[*]' --output json 2>/dev/null || echo "[]")
  if [ "$IMAGE_IDS" != "[]" ] && [ -n "$IMAGE_IDS" ]; then
    echo "    Deleting images in $REPO..."
    aws ecr batch-delete-image --repository-name "$REPO" \
      --region "$AWS_REGION" --image-ids "$IMAGE_IDS" > /dev/null
  fi
done

echo "==> [6/7] Running terraform destroy..."
cd "$(dirname "$0")/../terraform"
terraform destroy -auto-approve
cd - > /dev/null

echo "==> [7/7] Deleting Terraform state bucket..."
aws s3 rm "s3://$TF_STATE_BUCKET" --recursive --region "$AWS_REGION"
aws s3api delete-bucket --bucket "$TF_STATE_BUCKET" --region "$AWS_REGION"

echo ""
echo "✓ All resources destroyed. AWS account is clean."
