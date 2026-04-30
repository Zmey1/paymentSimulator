#!/bin/bash
set -euo pipefail

# ── Constants ──────────────────────────────────────────────────────────────────
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AWS_REGION="ap-south-1"
EKS_CLUSTER="payments-sim-eks"
TF_STATE_BUCKET="payments-sim-tfstate"
DYNAMO_TABLE="terraform-lock"
KEY_NAME="payments-sim-key"
KEY_FILE="$HOME/.ssh/${KEY_NAME}.pem"

# ── Passwords ──────────────────────────────────────────────────────────────────
export TF_VAR_db_password="Srm102**"

# ── Helpers ────────────────────────────────────────────────────────────────────
log()  { echo ""; echo "==> $*"; }
info() { echo "    $*"; }
err()  { echo ""; echo "ERROR: $*" >&2; exit 1; }

wait_for_port() {
  local host=$1 port=$2 label=$3
  local attempts=0 max=20
  log "Waiting for $label ($host:$port)..."
  until nc -z -w5 "$host" "$port" 2>/dev/null; do
    attempts=$((attempts + 1))
    [ "$attempts" -ge "$max" ] && err "$label not reachable after 5 minutes."
    info "Attempt $attempts/$max — retrying in 15s..."
    sleep 15
  done
  info "$label is reachable."
}

wait_for_http() {
  local url=$1 label=$2 max=${3:-20}
  local attempts=0
  log "Waiting for $label ($url)..."
  until curl -s --max-time 5 "$url" > /dev/null 2>&1; do
    attempts=$((attempts + 1))
    [ "$attempts" -ge "$max" ] && err "$label not available after $((max * 15 / 60)) minutes."
    info "Attempt $attempts/$max — retrying in 15s..."
    sleep 15
  done
  info "$label is up."
}

# ── [0] Preflight checks ───────────────────────────────────────────────────────
log "[0/8] Preflight checks..."
for cmd in aws terraform ansible-playbook nc curl sed; do
  command -v "$cmd" > /dev/null 2>&1 || err "'$cmd' not found. Please install it and re-run."
done
info "All required CLI tools found."

aws sts get-caller-identity --region "$AWS_REGION" > /dev/null 2>&1 \
  || err "AWS credentials not configured. Run 'aws configure' first."
info "AWS credentials valid."

log "[0b/8] Installing required Ansible collections..."
ansible-galaxy collection install community.general community.docker --upgrade \
  || err "Failed to install Ansible collections."
info "Ansible collections ready."

# ── [1] Auto-detect public IP + patch terraform.tfvars ────────────────────────
log "[1/8] Detecting public IP..."
PUBLIC_IP=$(curl -sf https://checkip.amazonaws.com) \
  || err "Could not detect public IP. Check your internet connection."
info "Your public IP: ${PUBLIC_IP}"

sed -i "s|operator_access_cidrs.*|operator_access_cidrs  = [\"${PUBLIC_IP}/32\"]|" \
  "$REPO_ROOT/terraform/terraform.tfvars"
info "Patched operator_access_cidrs in terraform/terraform.tfvars."

# ── [2] Bootstrap Terraform backend ───────────────────────────────────────────
log "[2/8] Bootstrapping Terraform backend (S3 + DynamoDB + key pair)..."

if ! aws s3api head-bucket --bucket "$TF_STATE_BUCKET" --region "$AWS_REGION" 2>/dev/null; then
  aws s3 mb "s3://$TF_STATE_BUCKET" --region "$AWS_REGION" > /dev/null
  info "Created S3 bucket: $TF_STATE_BUCKET"
else
  info "S3 bucket already exists: $TF_STATE_BUCKET"
fi

if ! aws dynamodb describe-table --table-name "$DYNAMO_TABLE" --region "$AWS_REGION" > /dev/null 2>&1; then
  aws dynamodb create-table \
    --table-name "$DYNAMO_TABLE" \
    --attribute-definitions AttributeName=LockID,AttributeType=S \
    --key-schema AttributeName=LockID,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region "$AWS_REGION" > /dev/null
  info "Created DynamoDB table: $DYNAMO_TABLE"
else
  info "DynamoDB table already exists: $DYNAMO_TABLE"
fi

if ! aws ec2 describe-key-pairs --key-names "$KEY_NAME" --region "$AWS_REGION" > /dev/null 2>&1; then
  mkdir -p "$HOME/.ssh"
  aws ec2 create-key-pair --key-name "$KEY_NAME" --region "$AWS_REGION" \
    --query 'KeyMaterial' --output text > "$KEY_FILE"
  chmod 400 "$KEY_FILE"
  info "Created EC2 key pair → $KEY_FILE"
else
  info "Key pair '$KEY_NAME' already exists in AWS."
  if [ ! -f "$KEY_FILE" ]; then
    echo ""
    echo "  WARNING: $KEY_FILE not found locally."
    echo "           Ansible SSH will fail without it."
    echo "           Copy your existing key to $KEY_FILE then re-run this script."
    exit 1
  fi
  info "Local key file found: $KEY_FILE"
fi

# ── [3] Terraform ──────────────────────────────────────────────────────────────
log "[3/8] Running Terraform (init → plan → apply)..."
cd "$REPO_ROOT/terraform"

info "terraform init..."
terraform init -input=false

info "terraform plan..."
terraform plan -input=false -out=tfplan \
  || err "terraform plan failed. Review errors above before proceeding."

info "terraform apply..."
terraform apply -input=false tfplan \
  || err "terraform apply failed. Review errors above before proceeding."

JENKINS_IP=$(terraform output -raw jenkins_public_ip 2>/dev/null | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' | head -1)
[ -n "$JENKINS_IP" ] || err "Could not read jenkins_public_ip from Terraform outputs."
info "Jenkins EC2 public IP: $JENKINS_IP"

cd "$REPO_ROOT"

# ── [4] Wait for Jenkins EC2 to accept SSH ─────────────────────────────────────
wait_for_port "$JENKINS_IP" 22 "Jenkins SSH"

# ── [5] Update Ansible inventory ───────────────────────────────────────────────
log "[5/8] Patching Ansible inventory with Jenkins IP..."
sed -i "s/ansible_host=[^ ]*/ansible_host=${JENKINS_IP}/" \
  "$REPO_ROOT/ansible/inventory/hosts.ini"
info "ansible/inventory/hosts.ini updated."

# ── [6] Ansible: Docker + Jenkins ─────────────────────────────────────────────
log "[6/8] Ansible: setup-jenkins (Docker, Jenkins, kubectl, AWS CLI, Helm)..."
cd "$REPO_ROOT/ansible"
ansible-playbook -i inventory/hosts.ini playbooks/setup-jenkins.yml \
  --private-key "$KEY_FILE" \
  || err "setup-jenkins.yml failed. See Ansible output above."

wait_for_http "http://${JENKINS_IP}:8080" "Jenkins UI" 20

# ── [7] Ansible: SonarQube ─────────────────────────────────────────────────────
log "[7/8] Ansible: setup-sonarqube..."
ansible-playbook -i inventory/hosts.ini playbooks/setup-sonarqube.yml \
  --private-key "$KEY_FILE" \
  || err "setup-sonarqube.yml failed. See Ansible output above."

wait_for_http "http://${JENKINS_IP}:9000" "SonarQube UI" 32

# ── [8] Ansible: configure kubectl on Jenkins ─────────────────────────────────
log "[8/8] Ansible: configure-kubectl..."
AWS_REGION="$AWS_REGION" EKS_CLUSTER_NAME="$EKS_CLUSTER" \
  ansible-playbook -i inventory/hosts.ini playbooks/configure-kubectl.yml \
  --private-key "$KEY_FILE" \
  || err "configure-kubectl.yml failed. See Ansible output above."

cd "$REPO_ROOT"

# ── Summary ────────────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Infrastructure is ready!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  Jenkins   →  http://${JENKINS_IP}:8080"
echo "  SonarQube →  http://${JENKINS_IP}:9000"
echo ""
echo "  Manual steps before running the pipeline:"
echo ""
echo "  1. Open Jenkins and complete the setup wizard"
echo "     Initial admin password:  sudo cat /var/lib/jenkins/secrets/initialAdminPassword"
echo "     (SSH into ${JENKINS_IP} to get it)"
echo "      command: ssh -i ~/.ssh/payments-sim-key.pem ubuntu@13.126.234.25"
echo ""
echo "  2. Manage Jenkins → System → SonarQube servers"
echo "     Name: SonarQube   URL: http://localhost:9000"
echo ""
echo "  3. Manage Jenkins → Credentials → Add:"
echo "     Kind: Secret text   ID: payments-db-password   Value: Srm102**"
echo ""
echo "  4. SonarQube (http://${JENKINS_IP}:9000) → Administration → Webhooks → Create:"
echo "     URL: http://${JENKINS_IP}:8080/sonarqube-webhook/"
echo ""
echo "  5. Create a Pipeline job in Jenkins pointing to this Git repo"
echo ""
echo "  6. Run the pipeline — it will build, test, push to ECR,"
echo "     deploy all services to EKS, and deploy Grafana + Prometheus"
echo ""
echo "  After the pipeline finishes:"
echo "     kubectl get svc -n monitoring kube-prometheus-stack-grafana"
echo "     Open EXTERNAL-IP in browser  →  admin / Srm102**"
echo ""
echo "  After the demo:"
echo "     bash scripts/teardown.sh"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
