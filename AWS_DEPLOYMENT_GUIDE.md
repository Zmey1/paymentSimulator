# AWS Deployment Guide

This guide matches the AWS deployment implementation in this repository as of April 9, 2026.

## Target Architecture

- Terraform provisions VPC, EKS, RDS, ECR, and one Jenkins EC2 host in `ap-south-1`.
- Ansible configures Jenkins, Docker, SonarQube, AWS CLI, `kubectl`, and Helm on the Jenkins host.
- Jenkins builds the services, runs tests, pushes images to ECR, deploys Kafka with Helm, renders Kubernetes manifests, and applies them to EKS.
- Kubernetes runs:
  - `payment-service`
  - `fraud-detection-service`
  - `notification-service`
  - `payment-dashboard`
- RDS PostgreSQL stays private and is only reachable from the EKS worker security group.
- `payment-dashboard` is exposed through an AWS `LoadBalancer` service.

## Before You Start

Create or confirm the Terraform backend resources in `ap-south-1` before `terraform init`:

- S3 bucket: `payments-sim-tfstate`
- DynamoDB table: `terraform-lock`

Prepare these inputs:

- `TF_VAR_db_password`
- an EC2 key pair matching `jenkins_key_name`
- AWS credentials with rights to create the full stack
- your public IP or campus CIDR for `operator_access_cidrs`

Recommended local exports:

```bash
export AWS_REGION=ap-south-1
export TF_VAR_db_password='change-this-db-password'
```

## Phase A: Provision AWS Infrastructure

Edit [`terraform/terraform.tfvars`](/home/zmey1/VSCODE_FILES/devops/terraform/terraform.tfvars) before applying:

- replace `operator_access_cidrs` with your real public IP or campus network
- confirm `jenkins_key_name`

Apply Terraform:

```bash
cd terraform
terraform init
terraform apply
```

Capture these outputs:

- `jenkins_public_ip`
- `jenkins_role_arn`
- `eks_cluster_name`
- `eks_cluster_endpoint`
- `rds_endpoint`
- `ecr_repository_urls`

Notes:

- Terraform now creates an EKS access entry and cluster-admin association for the Jenkins EC2 IAM role.
- Jenkins host access is no longer open to `0.0.0.0/0`; it is restricted by `operator_access_cidrs`.

## Phase B: Configure Jenkins Host With Ansible

Update [`ansible/inventory/hosts.ini`](/home/zmey1/VSCODE_FILES/devops/ansible/inventory/hosts.ini) with the Terraform output IP.

Run the playbooks:

```bash
cd ansible
ansible-playbook -i inventory/hosts.ini playbooks/setup-jenkins.yml
ansible-playbook -i inventory/hosts.ini playbooks/setup-sonarqube.yml
AWS_REGION=ap-south-1 EKS_CLUSTER_NAME=payments-sim-eks \
ansible-playbook -i inventory/hosts.ini playbooks/configure-kubectl.yml
```

What these playbooks do now:

- install Jenkins + required plugins, including `credentials-binding`
- install Docker and Docker Compose
- install AWS CLI
- install `kubectl`
- install Helm
- run SonarQube in Docker on port `9000`
- configure Jenkins user kubeconfig for EKS

Verify:

- `http://<jenkins_public_ip>:8080`
- `http://<jenkins_public_ip>:9000`
- `kubectl get nodes` works on the Jenkins box

## Phase C: Configure Jenkins

Create a Jenkins pipeline job pointing to this repository and branch.

Required Jenkins setup:

- SonarQube server name: `SonarQube`
- Secret text credential ID: `payments-db-password`

Optional Jenkins environment variables for deployed Razorpay sandbox mode:

- `RAZORPAY_KEY_ID`
- `RAZORPAY_KEY_SECRET`

Optional Jenkins pipeline parameters are already defined in [`Jenkinsfile`](/home/zmey1/VSCODE_FILES/devops/Jenkinsfile):

- `AWS_REGION`
- `EKS_CLUSTER`
- `DB_INSTANCE_IDENTIFIER`
- `DB_USERNAME`
- `KAFKA_BOOTSTRAP_SERVERS`
- `RAZORPAY_ENABLED`
- `RAZORPAY_MERCHANT_NAME`
- `RAZORPAY_DESCRIPTION`
- `RAZORPAY_RECEIVER_NAME`

## Phase D: First Pipeline Run

The pipeline now performs these deployment steps automatically:

1. Resolve AWS account ID and ECR registry dynamically.
2. Resolve the RDS endpoint from AWS using the RDS instance identifier.
3. Build and test all Java services.
4. Run SonarQube analysis and wait for the quality gate.
5. Build and push all service images to ECR.
6. Run Docker Compose integration tests.
7. Update kubeconfig for the Jenkins user.
8. Deploy Kafka with Helm using [`k8s/kafka/helm-values.yml`](/home/zmey1/VSCODE_FILES/devops/k8s/kafka/helm-values.yml).
9. Render Kubernetes manifests with real values into `.rendered/k8s/`.
10. Apply ConfigMap, Secret, Services, and Deployments to EKS.
11. Wait for Kubernetes rollout completion.

The rendered manifest path is produced by [`scripts/render-k8s-manifests.sh`](/home/zmey1/VSCODE_FILES/devops/scripts/render-k8s-manifests.sh).

## Runtime Configuration Injection

The Kubernetes templates now use placeholders and are rendered during deployment:

- [`k8s/configmaps.yml`](/home/zmey1/VSCODE_FILES/devops/k8s/configmaps.yml)
- [`k8s/secrets.yml`](/home/zmey1/VSCODE_FILES/devops/k8s/secrets.yml)
- [`k8s/payment-service/deployment.yml`](/home/zmey1/VSCODE_FILES/devops/k8s/payment-service/deployment.yml)
- [`k8s/fraud-detection-service/deployment.yml`](/home/zmey1/VSCODE_FILES/devops/k8s/fraud-detection-service/deployment.yml)
- [`k8s/notification-service/deployment.yml`](/home/zmey1/VSCODE_FILES/devops/k8s/notification-service/deployment.yml)
- [`k8s/payment-dashboard/deployment.yml`](/home/zmey1/VSCODE_FILES/devops/k8s/payment-dashboard/deployment.yml)

Injected values include:

- ECR registry
- image tag
- RDS endpoint
- DB username
- DB password from Jenkins credential
- Kafka bootstrap service
- optional Razorpay toggle and labels
- optional Razorpay key ID and key secret

No real secret values are stored in git.

## Acceptance Checks

After the first successful pipeline run, verify:

```bash
kubectl get pods
kubectl get svc payment-dashboard
kubectl get svc payment-service
```

Expected outcomes:

- all four application deployments become `Ready`
- Kafka is installed through Helm
- `payment-dashboard` gets an external `LoadBalancer` address
- the dashboard loads through the AWS-generated URL
- `GET /api/payments`
- `GET /api/payments/stats`
- `GET /api/payments/razorpay/config`

Example:

```bash
curl http://<load-balancer-host>/api/payments/stats
curl http://<load-balancer-host>/api/payments/razorpay/config
```

## Razorpay In AWS

Razorpay remains optional in deployed environments.

- If `RAZORPAY_ENABLED=false`, the system still deploys and simulator mode works normally.
- If `RAZORPAY_ENABLED=true`, also provide `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET` in Jenkins.
- Verified Razorpay payments still enter the same internal payment and fraud pipeline.

## Operational Notes

- The Jenkins pipeline archives the rendered Kubernetes YAML from `.rendered/k8s/` for traceability.
- The dashboard service remains `LoadBalancer` based; no ingress or custom domain is required in v1.
- Kafka topic names remain unchanged:
  - `payment.created`
  - `payment.approved`
  - `payment.flagged`
- For the first live rollout, run the Jenkins job manually once before enabling GitHub webhooks.
