# Event-Driven Payments Simulator — Design Specification

**Date:** 2026-03-23
**Type:** Cloud & DevOps University Project
**Stack:** Java (Spring Boot), Apache Kafka, React, AWS, Full DevOps Toolchain

---

## 1. Project Overview

An event-driven payments simulator built with microservices architecture, deployed on AWS using a complete DevOps toolchain. The system simulates a payment processing pipeline where payments flow through creation, fraud detection, and notification stages via Kafka events. A React dashboard provides a UI for submitting and monitoring payments.

**Primary Goal:** University/college submission that clearly demonstrates Terraform, Ansible, Jenkins, Docker, Kubernetes, and a full testing pyramid (JUnit, Mockito, Selenium, SonarQube).

---

## 2. Architecture & Data Flow

### 2.1 Payment Event Pipeline

1. User submits a payment via the **React Dashboard** (amount, sender, receiver, payment type).
2. Dashboard calls **Payment Service** REST API (`POST /api/payments`).
3. **Payment Service** validates the request, persists to PostgreSQL with status `PENDING`, publishes a `payment-created` event to Kafka.
4. **Fraud Detection Service** consumes `payment-created`, runs rule-based checks (e.g., amount > ₹50,000 = flagged), publishes `payment-approved` or `payment-flagged`.
5. **Notification Service** consumes approval/flag events, logs simulated email/SMS notifications.
6. **Payment Service** consumes approval/flag events to update the payment record's final status in the DB.
7. **React Dashboard** fetches updated payment statuses and displays them.

### 2.2 Microservices

| Service | Responsibility | Kafka Topics |
|---|---|---|
| `payment-service` | Accept payments, persist to DB, publish events | Produces: `payment-created`. Consumes: `payment-approved`, `payment-flagged` |
| `fraud-detection-service` | Consume & analyze payments, apply fraud rules | Consumes: `payment-created`. Produces: `payment-approved`, `payment-flagged` |
| `notification-service` | Consume results, simulate email/SMS alerts | Consumes: `payment-approved`, `payment-flagged` |
| `payment-dashboard` | React UI — submit payments, view statuses | Calls Payment Service REST API |

### 2.3 Database

Single PostgreSQL instance (AWS RDS). Each service uses its own schema/tables for logical separation.

---

## 3. DevOps Toolchain

### 3.1 Terraform — Infrastructure Provisioning

Provisions all AWS resources from code:

- VPC with public/private subnets across 2 Availability Zones
- EKS cluster (managed Kubernetes) with a worker node group
- ECR repositories (4 total: one per microservice + frontend)
- RDS PostgreSQL instance in the private subnet
- Security groups for EKS, RDS, and bastion access
- EC2 instance for Jenkins server
- S3 bucket for Terraform remote state backend

Organized into reusable modules: `modules/vpc`, `modules/eks`, `modules/rds`, `modules/ecr`, `modules/jenkins-ec2`.

### 3.2 Ansible — Configuration Management

Configures the Jenkins EC2 instance after Terraform provisions it:

- Installs Jenkins, Java 17, Docker, kubectl, AWS CLI
- Configures Jenkins plugins (Pipeline, Docker, Kubernetes CLI, SonarQube Scanner)
- Adds Jenkins user to the docker group
- Deploys SonarQube as a Docker container
- Configures system-level settings (swap, firewall, SSH)

Playbooks: `setup-jenkins.yml`, `setup-sonarqube.yml`, `configure-kubectl.yml`. Uses Ansible roles for modularity: `roles/jenkins`, `roles/docker`, `roles/sonarqube`.

### 3.3 Docker — Containerization

Each component gets its own Dockerfile:

- **Spring Boot services:** Multi-stage build (Maven build stage → slim JDK 17 runtime image)
- **React dashboard:** Node build stage → Nginx serving static files
- **docker-compose.yml** at the root for local development (all services + Kafka + Zookeeper + PostgreSQL)

### 3.4 Jenkins — CI/CD Pipeline

A single `Jenkinsfile` defining the full pipeline:

1. **Checkout** — pull code from GitHub
2. **Build** — `mvn clean package` for each service (parallel builds)
3. **Unit Test** — `mvn test` (JUnit + Mockito), publish results to Jenkins
4. **SonarQube Analysis** — `mvn sonar:sonar`, quality gate check (≥70% coverage, 0 critical bugs). Pipeline fails if gate not met.
5. **Docker Build & Push** — build images, tag with `BUILD_NUMBER`, push to ECR
6. **Integration Test** — spin up containers via docker-compose, run Selenium tests against dashboard, teardown
7. **Deploy to Kubernetes** — `kubectl set image` for rolling updates to EKS, `kubectl rollout status` to verify

Triggered automatically via GitHub webhook on every `git push`.

### 3.5 Kubernetes — Orchestration & Deployment

Manifests organized in `k8s/` directory:

- **Deployments** — one per service with replica counts, resource limits, readiness/liveness probes
- **Services** — ClusterIP for internal microservices, LoadBalancer for the React dashboard
- **ConfigMaps** — Kafka broker URLs, DB connection strings, service endpoints
- **Secrets** — DB credentials, ECR pull secrets
- **Kafka** — deployed via Bitnami Helm chart inside the cluster
- **Ingress** (optional) — Nginx ingress controller for routing

### 3.6 Testing — Full Test Pyramid

- **JUnit 5 + Mockito** — unit tests per service (mock Kafka producers/consumers, mock DB repositories)
- **Selenium** — automated browser tests on the React dashboard (submit payment, verify status transitions, check notification logs)
- **SonarQube** — static code analysis as a Jenkins pipeline quality gate

---

## 4. Project Structure

```
event-driven-payments-simulator/
│
├── terraform/
│   ├── main.tf
│   ├── variables.tf
│   ├── outputs.tf
│   ├── backend.tf
│   ├── terraform.tfvars
│   └── modules/
│       ├── vpc/
│       ├── eks/
│       ├── rds/
│       ├── ecr/
│       └── jenkins-ec2/
│
├── ansible/
│   ├── inventory/
│   │   └── hosts.ini
│   ├── playbooks/
│   │   ├── setup-jenkins.yml
│   │   ├── setup-sonarqube.yml
│   │   └── configure-kubectl.yml
│   └── roles/
│       ├── jenkins/
│       ├── docker/
│       └── sonarqube/
│
├── services/
│   ├── payment-service/
│   │   ├── src/
│   │   ├── Dockerfile
│   │   └── pom.xml
│   ├── fraud-detection-service/
│   │   ├── src/
│   │   ├── Dockerfile
│   │   └── pom.xml
│   └── notification-service/
│       ├── src/
│       ├── Dockerfile
│       └── pom.xml
│
├── payment-dashboard/
│   ├── src/
│   ├── Dockerfile
│   └── package.json
│
├── k8s/
│   ├── payment-service/
│   │   ├── deployment.yml
│   │   └── service.yml
│   ├── fraud-detection-service/
│   │   ├── deployment.yml
│   │   └── service.yml
│   ├── notification-service/
│   │   ├── deployment.yml
│   │   └── service.yml
│   ├── payment-dashboard/
│   │   ├── deployment.yml
│   │   └── service.yml
│   ├── kafka/
│   │   └── helm-values.yml
│   ├── configmaps.yml
│   └── secrets.yml
│
├── tests/
│   ├── src/
│   ├── Dockerfile
│   └── pom.xml
│
├── Jenkinsfile
├── docker-compose.yml
└── README.md
```

---

## 5. End-to-End Workflow

### Phase 1: Infrastructure Setup (One-time)

1. Run `terraform init && terraform apply` from `terraform/` directory.
2. Terraform provisions VPC → Subnets → EKS → RDS → ECR → EC2 for Jenkins.
3. Terraform outputs EC2 public IP, EKS endpoint, ECR URLs, RDS endpoint.
4. Run `ansible-playbook -i inventory/hosts.ini playbooks/setup-jenkins.yml` targeting the EC2 IP.
5. Ansible installs Jenkins, Docker, kubectl, configures kubeconfig for EKS, spins up SonarQube.
6. Access Jenkins at `http://<ec2-ip>:8080`, configure GitHub webhook and ECR credentials.

### Phase 2: Local Development Loop

1. Write code in `services/` or `payment-dashboard/`.
2. Run `docker-compose up` to spin up all services + Kafka + Zookeeper + PostgreSQL locally.
3. Test the flow: submit payment → Kafka event → fraud check → notification.
4. Run `mvn test` locally to verify unit tests pass.

### Phase 3: CI/CD Pipeline (Every git push)

```
git push → GitHub Webhook → Jenkins Pipeline

Stage 1: Checkout → Pull latest code
Stage 2: Build → mvn clean package (parallel)
Stage 3: Unit Tests → JUnit + Mockito, publish results
Stage 4: SonarQube → Quality gate (≥70% coverage, 0 critical bugs)
Stage 5: Docker Build & Push → Tag with BUILD_NUMBER, push to ECR
Stage 6: Integration Tests → docker-compose up, Selenium tests, teardown
Stage 7: Deploy to EKS → kubectl rolling update, rollout status check
```

### Phase 4: Running in Production (EKS)

Kubernetes manages pods across nodes. Internal services communicate via ClusterIP. Dashboard exposed via LoadBalancer with a public URL. Kafka runs inside the cluster via Helm. RDS accessed through the private subnet. Liveness/readiness probes handle self-healing.

---

## 6. DevOps Tool Accountability Map

| Tool | What It Proves |
|---|---|
| **Terraform** | Infrastructure as Code — entire AWS stack is reproducible |
| **Ansible** | Configuration management — server setup is automated, not manual |
| **Jenkins** | CI/CD — every push triggers build, test, scan, deploy automatically |
| **Docker** | Containerization — consistent environments from dev to production |
| **Kubernetes** | Orchestration — scaling, self-healing, rolling deployments |
| **JUnit + Mockito** | Unit testing — each service is tested in isolation |
| **Selenium** | Integration testing — end-to-end browser-based validation |
| **SonarQube** | Code quality — static analysis as a pipeline gate |

---

## 7. Key Technical Decisions

- **3 microservices** — enough to demonstrate event-driven architecture without overcomplicating the submission.
- **Single PostgreSQL instance** — shared DB with schema-per-service keeps infrastructure simple while showing logical separation.
- **Multi-stage Docker builds** — demonstrates best practice for small, production-ready images.
- **Bitnami Kafka Helm chart** — avoids manually writing complex Kafka K8s manifests.
- **SonarQube as quality gate** — adds a tangible, visible code quality check in the pipeline that evaluators can see.
- **docker-compose for local dev** — lets the project run entirely on a laptop without AWS for development and demo purposes.