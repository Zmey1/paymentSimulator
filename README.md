# Event-Driven Payments Simulator

An event-driven payments simulator built with microservices architecture, deployed on AWS using a complete DevOps toolchain.

## Architecture

```
User → React Dashboard → Payment Service → Kafka (payment.created)
                                                    ↓
                                          Fraud Detection Service
                                                    ↓
                                    Kafka (payment.approved / payment.flagged)
                                         ↓                    ↓
                              Payment Service           Notification Service
                              (status update)           (simulated alerts)
```

## Tech Stack

- **Backend:** Java 17, Spring Boot 3.2, Apache Kafka
- **Frontend:** React 18
- **Database:** PostgreSQL 15
- **Infrastructure:** AWS (EKS, RDS, ECR, VPC)
- **IaC:** Terraform
- **Configuration:** Ansible
- **CI/CD:** Jenkins
- **Containers:** Docker, Kubernetes
- **Testing:** JUnit 5, Mockito, Selenium
- **Code Quality:** SonarQube

## Quick Start (Local Development)

```bash
docker-compose up --build
```

- Dashboard: http://localhost:3000
- Payment API: http://localhost:8080/api/payments

## Project Structure

```
├── services/
│   ├── payment-service/         # REST API, DB persistence, Kafka producer/consumer
│   ├── fraud-detection-service/ # Fraud rule engine, Kafka consumer/producer
│   └── notification-service/    # Notification logger, Kafka consumer
├── payment-dashboard/           # React UI
├── terraform/                   # AWS infrastructure (VPC, EKS, RDS, ECR, Jenkins EC2)
├── ansible/                     # Jenkins server configuration
├── k8s/                         # Kubernetes deployment manifests
├── tests/                       # Selenium integration tests
├── Jenkinsfile                  # CI/CD pipeline
└── docker-compose.yml           # Local development environment
```

## Infrastructure Setup

1. `cd terraform && terraform init && terraform apply`
2. Update `ansible/inventory/hosts.ini` with Jenkins EC2 IP
3. `cd ansible && ansible-playbook -i inventory/hosts.ini playbooks/setup-jenkins.yml`
4. `ansible-playbook -i inventory/hosts.ini playbooks/setup-sonarqube.yml`
5. `ansible-playbook -i inventory/hosts.ini playbooks/configure-kubectl.yml`
6. Deploy Kafka to EKS: `helm install kafka oci://registry-1.docker.io/bitnamicharts/kafka -f k8s/kafka/helm-values.yml`
7. Apply K8s manifests: `kubectl apply -f k8s/`

## CI/CD Pipeline

Triggered on every `git push` via GitHub webhook:

1. Checkout → Build (parallel) → Unit Tests → SonarQube Analysis → Quality Gate → Docker Build & Push to ECR → Integration Tests → Deploy to EKS
