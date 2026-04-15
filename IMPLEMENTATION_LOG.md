# Implementation Log — Event-Driven Payments Simulator

**Date:** 2026-03-23
**Total Files Created:** 88

---

## What Was Built

### Phase 1: Project Scaffolding & Shared Contracts
- Created 3 Maven projects with identical Spring Boot 3.2.4 + Spring Kafka versions
- `PaymentEvent.java` DTO replicated identically across all 3 services with fields: `paymentId`, `sender`, `receiver`, `amount`, `paymentType`, `status`, `timestamp`
- Topic name constants as `public static final String`: `payment.created`, `payment.approved`, `payment.flagged`
- JSON serialization chosen over Avro (no schema registry needed)
- `String` type for paymentId (avoids UUID serialization issues)

**Files:**
- `services/payment-service/pom.xml`
- `services/fraud-detection-service/pom.xml`
- `services/notification-service/pom.xml`
- `services/*/src/main/java/.../model/PaymentEvent.java` (3 copies)
- `services/*/src/main/java/.../*Application.java` (3 files)

---

### Phase 2: payment-service
- REST controller with `POST /api/payments` (returns 201) and `GET /api/payments`
- `GET /api/payments/{id}` endpoint
- JPA `Payment` entity (separate from Kafka DTO) with fields: id, sender, receiver, amount, paymentType, status, createdAt, updatedAt
- `PaymentRepository` — Spring Data JPA interface
- `PaymentService` — creates payment with UUID, saves as PENDING, publishes `payment.created` Kafka event
- `PaymentProducer` — sends to `payment.created` topic with paymentId as key
- `PaymentEventConsumer` — listens on `payment.approved` and `payment.flagged`, updates DB status. Defensive: checks `status == PENDING` before update, uses `@Transactional`
- `CorsConfig` — `WebMvcConfigurer` allowing `localhost:3000` and `payment-dashboard:80`
- `application.yml` — uses `${SPRING_*:default}` pattern for all config (DB, Kafka)
- `schema.sql` — DDL for payments table
- Consumer config: `spring.json.use.type.headers=false`, `spring.json.trusted.packages=*`, `spring.json.value.default.type` set to local class

**Files:**
- `services/payment-service/src/main/java/com/payments/paymentservice/controller/PaymentController.java`
- `services/payment-service/src/main/java/com/payments/paymentservice/model/Payment.java`
- `services/payment-service/src/main/java/com/payments/paymentservice/repository/PaymentRepository.java`
- `services/payment-service/src/main/java/com/payments/paymentservice/service/PaymentService.java`
- `services/payment-service/src/main/java/com/payments/paymentservice/kafka/PaymentProducer.java`
- `services/payment-service/src/main/java/com/payments/paymentservice/kafka/PaymentEventConsumer.java`
- `services/payment-service/src/main/java/com/payments/paymentservice/config/CorsConfig.java`
- `services/payment-service/src/main/resources/application.yml`
- `services/payment-service/src/main/resources/schema.sql`

---

### Phase 3: fraud-detection-service
- `FraudCheckService` — rule: `amount > 50000 = FLAGGED`, else `APPROVED`
- `PaymentEventConsumer` — listens on `payment.created` (group: `fraud-detection-group`), calls fraud check, mutates status on the event, re-publishes
- `FraudResultProducer` — publishes to `payment.approved` or `payment.flagged` based on status
- `application.yml` — port 8081, same Kafka serialization config as payment-service

**Files:**
- `services/fraud-detection-service/src/main/java/com/payments/frauddetection/service/FraudCheckService.java`
- `services/fraud-detection-service/src/main/java/com/payments/frauddetection/kafka/PaymentEventConsumer.java`
- `services/fraud-detection-service/src/main/java/com/payments/frauddetection/kafka/FraudResultProducer.java`
- `services/fraud-detection-service/src/main/resources/application.yml`

---

### Phase 4: notification-service
- `NotificationConsumer` — single `@KafkaListener` on both `payment.approved` and `payment.flagged` (group: `notification-group`)
- `NotificationService` — logs structured messages: `NOTIFICATION: [EMAIL] Payment {id} - Amount {amount} from {sender} to {receiver} - Status: {status}` and `NOTIFICATION: [SMS]...`
- `application.yml` — port 8082, consumer-only config (no producer needed)

**Files:**
- `services/notification-service/src/main/java/com/payments/notification/kafka/NotificationConsumer.java`
- `services/notification-service/src/main/java/com/payments/notification/service/NotificationService.java`
- `services/notification-service/src/main/resources/application.yml`

---

### Phase 5: docker-compose.yml
- **Updated:** Replaced Bitnami Kafka + Zookeeper with `apache/kafka:3.7.0` in KRaft mode (Bitnami images removed from Docker Hub)
- No Zookeeper needed — KRaft mode handles consensus internally
- KRaft environment variables: `KAFKA_NODE_ID`, `KAFKA_PROCESS_ROLES=broker,controller`, `KAFKA_CONTROLLER_QUORUM_VOTERS`, `CLUSTER_ID`
- PostgreSQL 15 Alpine with health check (`pg_isready`), mapped to **host port 5433** (avoids conflict with local PostgreSQL)
- Kafka health check (`/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list`)
- `depends_on` with `condition: service_healthy` for postgres and kafka
- `KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092` (container hostname, NOT localhost)
- All services use `SPRING_*` env vars (Spring Boot auto-maps these)
- Dashboard on port 3000 (maps to Nginx port 80 inside container)
- Persistent volume for PostgreSQL data
- Removed obsolete `version: '3.8'` line from docker-compose.yml

**Files:**
- `docker-compose.yml`

---

### Phase 6: React payment-dashboard
- `PaymentForm.js` — form with sender, receiver, amount, paymentType fields. Submits via Axios POST
- `PaymentList.js` — table showing all payments with color-coded status (green=APPROVED, red=FLAGGED, orange=PENDING)
- `App.js` — orchestrator with `useState` + `useEffect`, polls `GET /api/payments` every 3 seconds
- `api.js` — Axios service, base URL from `REACT_APP_API_URL` or defaults to `/api` (works with Nginx proxy)
- `nginx.conf` — serves React build, proxies `/api/` to `payment-service:8080` (eliminates CORS in Docker/K8s)
- `package.json` — includes `"proxy": "http://localhost:8080"` for local dev CORS workaround
- Clean CSS with responsive layout

**Files:**
- `payment-dashboard/package.json`
- `payment-dashboard/public/index.html`
- `payment-dashboard/src/index.js`
- `payment-dashboard/src/App.js`
- `payment-dashboard/src/App.css`
- `payment-dashboard/src/components/PaymentForm.js`
- `payment-dashboard/src/components/PaymentList.js`
- `payment-dashboard/src/services/api.js`
- `payment-dashboard/nginx.conf`

---

### Phase 7: Unit Tests & SonarQube Config
- **payment-service tests:**
  - `PaymentServiceTest` — tests createPayment (verifies UUID generation, PENDING status, Kafka event published with correct fields), getAllPayments, getPaymentById (found + not found)
  - `PaymentControllerTest` — `@WebMvcTest` with `@MockBean PaymentService`. Tests POST returns 201, GET returns list, GET by ID returns 404 when not found
  - `PaymentProducerTest` — verifies `kafkaTemplate.send()` called with correct topic and key
- **fraud-detection-service tests:**
  - `FraudCheckServiceTest` — tests small amount (APPROVED), exact threshold 50000 (APPROVED), above threshold (FLAGGED), large amount (FLAGGED)
- **notification-service tests:**
  - `NotificationServiceTest` — tests handling APPROVED and FLAGGED events without exceptions
- JaCoCo plugin in all 3 pom.xml files — `prepare-agent` before test, `report` after test, generates XML at `target/site/jacoco/jacoco.xml`
- SonarQube properties in each pom.xml: `sonar.projectKey`, `sonar.coverage.jacoco.xmlReportPaths`

**Files:**
- `services/payment-service/src/test/java/.../service/PaymentServiceTest.java`
- `services/payment-service/src/test/java/.../controller/PaymentControllerTest.java`
- `services/payment-service/src/test/java/.../kafka/PaymentProducerTest.java`
- `services/fraud-detection-service/src/test/java/.../service/FraudCheckServiceTest.java`
- `services/notification-service/src/test/java/.../service/NotificationServiceTest.java`

---

### Phase 8: Docker Multi-Stage Builds
- **Spring Boot services:** `maven:3.9-eclipse-temurin-17` build stage → `eclipse-temurin:17-jre-alpine` runtime (roughly 70MB)
- Layer caching: `COPY pom.xml` first → `mvn dependency:go-offline` → then `COPY src/` (code-only changes don't re-download deps)
- **React dashboard:** `node:18-alpine` build → `nginx:alpine` runtime
- `.dockerignore` files exclude `target/`, `node_modules/`, `.git/`, `*.md`

**Files:**
- `services/payment-service/Dockerfile` + `.dockerignore`
- `services/fraud-detection-service/Dockerfile` + `.dockerignore`
- `services/notification-service/Dockerfile` + `.dockerignore`
- `payment-dashboard/Dockerfile` + `.dockerignore`

---

### Phase 9: Terraform Infrastructure
- **providers.tf** — AWS provider pinned to `~> 5.0`, required Terraform `>= 1.5.0`
- **backend.tf** — S3 remote state (`payments-sim-tfstate` bucket) + DynamoDB locking (`terraform-lock` table)
- **variables.tf** — region, project_name, vpc_cidr, db credentials (password is `sensitive`), key pair name, instance types
- **terraform.tfvars** — defaults for ap-south-1, t3.medium instances. DB password via `TF_VAR_db_password` env var

**Module: vpc/**
- VPC with DNS hostnames enabled
- Internet Gateway
- 2 public subnets (different AZs) with `map_public_ip_on_launch = true`
- 2 private subnets (different AZs)
- NAT Gateway with Elastic IP in public subnet (critical for EKS pods to pull ECR images)
- Public route table (0.0.0.0/0 → IGW)
- Private route table (0.0.0.0/0 → NAT GW)
- Kubernetes tags on subnets for ELB discovery

**Module: eks/**
- EKS cluster IAM role with `AmazonEKSClusterPolicy`
- EKS cluster security group (ingress 443 for API)
- EKS cluster in private subnets
- Worker node IAM role with 3 policies: `AmazonEKSWorkerNodePolicy`, `AmazonEKS_CNI_Policy`, `AmazonEC2ContainerRegistryReadOnly`
- Worker node security group: self-ingress (intra-cluster), ingress from cluster SG, full egress
- Node group: 2 desired, 1-3 scaling range, t3.medium

**Module: rds/**
- DB subnet group across private subnets
- RDS security group: ingress on 5432 from EKS worker SG only
- PostgreSQL 15, db.t3.micro, 20GB gp3, `publicly_accessible = false`, `skip_final_snapshot = true`

**Module: ecr/**
- 4 ECR repositories using `for_each`: payment-service, fraud-detection-service, notification-service, payment-dashboard
- Named as `payments-sim/<service-name>`

**Module: jenkins-ec2/**
- Ubuntu 22.04 AMI (latest from Canonical)
- Security group: SSH (22), Jenkins (8080), SonarQube (9000)
- IAM role with `AmazonEC2ContainerRegistryPowerUser` + `AmazonEKSClusterPolicy`
- IAM instance profile attached (no hardcoded AWS keys needed)
- 30GB gp3 root volume

**Outputs:** vpc_id, eks_cluster_endpoint, eks_cluster_name, rds_endpoint, ecr_repository_urls, jenkins_public_ip

**Files:**
- `terraform/providers.tf`, `backend.tf`, `variables.tf`, `terraform.tfvars`, `main.tf`, `outputs.tf`
- `terraform/modules/vpc/main.tf`, `variables.tf`, `outputs.tf`
- `terraform/modules/eks/main.tf`, `variables.tf`, `outputs.tf`
- `terraform/modules/rds/main.tf`, `variables.tf`, `outputs.tf`
- `terraform/modules/ecr/main.tf`, `variables.tf`, `outputs.tf`
- `terraform/modules/jenkins-ec2/main.tf`, `variables.tf`, `outputs.tf`

---

### Phase 10: Ansible Configuration
- **inventory/hosts.ini** — placeholder for Jenkins EC2 IP from Terraform output

**Role: docker/**
- Installs Docker prerequisites, adds Docker GPG key and repo
- Installs docker-ce, docker-ce-cli, containerd, docker-compose-plugin
- Starts/enables Docker service
- Adds `jenkins` user to `docker` group (notifies handler to restart Jenkins)

**Role: jenkins/**
- Installs Java 17 (openjdk-17-jdk)
- Adds Jenkins GPG key and repo, installs Jenkins
- Starts/enables Jenkins
- Installs plugins: workflow-aggregator, docker-workflow, kubernetes-cli, sonar, git, pipeline-stage-view
- Installs kubectl (from official release)
- Installs AWS CLI v2
- Handler: restart jenkins (triggered when docker group membership changes)

**Role: sonarqube/**
- Sets `vm.max_map_count=262144` via sysctl (SonarQube requirement)
- Creates data directory
- Runs `sonarqube:community` Docker container on port 9000 with restart policy

**Playbooks:**
- `setup-jenkins.yml` — applies docker + jenkins roles
- `setup-sonarqube.yml` — applies sonarqube role
- `configure-kubectl.yml` — runs `aws eks update-kubeconfig` as jenkins user, verifies connection

**Files:**
- `ansible/inventory/hosts.ini`
- `ansible/roles/docker/tasks/main.yml`
- `ansible/roles/jenkins/tasks/main.yml`
- `ansible/roles/jenkins/handlers/main.yml`
- `ansible/roles/sonarqube/tasks/main.yml`
- `ansible/playbooks/setup-jenkins.yml`
- `ansible/playbooks/setup-sonarqube.yml`
- `ansible/playbooks/configure-kubectl.yml`

---

### Phase 11: Kubernetes Manifests
- **ConfigMap** (`app-config`) — `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_KAFKA_BOOTSTRAP_SERVERS` (uses `kafka.default.svc.cluster.local:9092`)
- **Secret** (`db-credentials`) — base64 encoded DB password
- **payment-service:** 2 replicas, envFrom configMap + secret for DB password, readiness probe at `/actuator/health` (initialDelay: 30s), liveness probe (initialDelay: 45s), 256Mi-512Mi memory, ClusterIP service
- **fraud-detection-service:** 1 replica, only Kafka env var from configMap, same probe config on port 8081, ClusterIP service
- **notification-service:** 1 replica, only Kafka env var, same probe config on port 8082, ClusterIP service
- **payment-dashboard:** 1 replica, lighter resources (64Mi-128Mi), probes on port 80, **LoadBalancer** service (public access)
- **Kafka Helm values:** 1 replica, 8Gi persistence, pre-provisioned topics (payment.created, payment.approved, payment.flagged with 3 partitions each)

**Files:**
- `k8s/configmaps.yml`, `k8s/secrets.yml`
- `k8s/payment-service/deployment.yml`, `k8s/payment-service/service.yml`
- `k8s/fraud-detection-service/deployment.yml`, `k8s/fraud-detection-service/service.yml`
- `k8s/notification-service/deployment.yml`, `k8s/notification-service/service.yml`
- `k8s/payment-dashboard/deployment.yml`, `k8s/payment-dashboard/service.yml`
- `k8s/kafka/helm-values.yml`

---

### Phase 12: Jenkinsfile CI/CD Pipeline
7-stage declarative pipeline:

1. **Checkout** — `checkout scm`
2. **Build** — parallel `mvn clean package -DskipTests` for all 3 services
3. **Unit Tests** — parallel `mvn test` for all 3 services, publishes JUnit results
4. **SonarQube Analysis** — `withSonarQubeEnv('SonarQube')`, runs `mvn sonar:sonar` for each service
5. **Quality Gate** — `waitForQualityGate abortPipeline: true` (requires SonarQube webhook to Jenkins)
6. **Docker Build & Push** — ECR login via `aws ecr get-login-password` (IAM instance profile), builds and pushes all 4 images tagged with `BUILD_NUMBER`
7. **Integration Tests** — `docker compose up`, waits 30s, runs Selenium tests, tears down
8. **Deploy to EKS** — `aws eks update-kubeconfig`, `kubectl set image` for rolling updates, `kubectl rollout status --timeout=120s` for each deployment

**Files:**
- `Jenkinsfile`

---

### Phase 13: Selenium Tests
- Uses `selenium/standalone-chrome` remote WebDriver
- 4 test cases (ordered execution):
  1. `shouldLoadDashboard` — verifies page loads with correct title
  2. `shouldSubmitPaymentAndShowPending` — fills form (Alice→Bob, 100), submits, verifies appears in table
  3. `shouldShowApprovedStatusForSmallPayment` — waits for status transition to APPROVED (polls with refresh)
  4. `shouldShowFlaggedStatusForLargePayment` — submits 60000 payment (Charlie→Dave), waits for FLAGGED status
- Uses `WebDriverWait` with 15s timeout (no `Thread.sleep`)
- Dashboard and Selenium URLs configurable via system properties

**Files:**
- `tests/pom.xml`
- `tests/src/test/java/com/payments/tests/PaymentDashboardTest.java`
- `tests/Dockerfile`

---

## Pitfall Mitigations Already Baked In

| # | Pitfall | Where Mitigated | How |
|---|---|---|---|
| 1 | Kafka FQCN mismatch | All 3 `application.yml` files | `spring.json.use.type.headers=false` + `trusted.packages=*` + `value.default.type` set to local class |
| 2 | Topic naming inconsistency | All 3 `PaymentEvent.java` files | `public static final String` constants used everywhere |
| 3 | Payment status race condition | `PaymentEventConsumer.java` (payment-service) | Checks `status == PENDING` before update + `@Transactional` |
| 4 | CORS failures | `nginx.conf` + `CorsConfig.java` + `package.json` | Nginx proxy_pass in Docker/K8s, proxy in package.json for dev, WebMvcConfigurer as backup |
| 5 | DB schema conflicts | Architecture | Only payment-service uses DB — no conflict possible |
| 6 | Env var naming mismatch | `docker-compose.yml` + `k8s/configmaps.yml` | Same `SPRING_*` variable names used everywhere |
| 7 | Docker cache invalidation | All Dockerfiles | pom.xml copied first → `dependency:go-offline` → then src/ |
| 8 | EKS networking | `terraform/modules/vpc/` + `rds/` + `eks/` | NAT GW for ECR pulls, RDS SG allows EKS worker SG on 5432 |
| 9 | Jenkins credentials | `terraform/modules/jenkins-ec2/` | IAM instance profile with ECR PowerUser + EKS policies |
| 10 | SonarQube quality gate | `Jenkinsfile` + pom.xml files | `waitForQualityGate`, JaCoCo XML report paths configured |
| 11 | K8s probes kill pods | All K8s deployments | readiness initialDelay=30s, liveness initialDelay=45s, actuator health endpoint |
| 12 | Terraform state corruption | `terraform/backend.tf` | S3 backend + DynamoDB locking |
| 13 | Ansible not idempotent | All Ansible roles | Uses `apt`, `systemd`, `docker_container`, `sysctl` modules, not `shell` |

---

## Next Steps — What You Need To Do

### Step 1: Prerequisites (Before Anything Else)
- [ ] Install on your local machine: Java 17, Maven, Node 18, Docker, Docker Compose
- [ ] Install AWS CLI, configure `aws configure` with your credentials
- [ ] Install Terraform (`>= 1.5.0`)
- [ ] Install Ansible
- [ ] Create an AWS key pair named `payments-sim-key` and download the `.pem` file

### Step 2: Local Development — Test Locally First
```bash
cd services/payment-service && mvn compile
cd ../fraud-detection-service && mvn compile
cd ../notification-service && mvn compile
```
- [ ] Verify all 3 services compile without errors
- [ ] Run `mvn test` in each service directory — verify all tests pass
- [ ] Run `docker-compose up --build` from the project root
- [ ] Open `http://localhost:3000` — submit a payment, verify status transitions
- [ ] Check `docker-compose logs notification-service` for NOTIFICATION log messages

### Step 3: Terraform — Provision AWS Infrastructure
- [ ] **Manually create** the S3 bucket (`payments-sim-tfstate`) and DynamoDB table (`terraform-lock`) for Terraform state — this is a one-time chicken-and-egg step
- [ ] Set the DB password: `export TF_VAR_db_password="your_secure_password"`
- [ ] Run `cd terraform && terraform init && terraform plan` — review the plan (expect ~25-30 resources)
- [ ] Run `terraform apply` — this takes 15-20 minutes (EKS is slow)
- [ ] Note the outputs: Jenkins IP, EKS endpoint, RDS endpoint, ECR URLs

### Step 4: Ansible — Configure Jenkins Server
- [ ] Update `ansible/inventory/hosts.ini` with the Jenkins EC2 public IP from Terraform output
- [ ] Copy your `.pem` key to `~/.ssh/payments-sim-key.pem`
- [ ] Run: `cd ansible && ansible-playbook -i inventory/hosts.ini playbooks/setup-jenkins.yml`
- [ ] Run: `ansible-playbook -i inventory/hosts.ini playbooks/setup-sonarqube.yml`
- [ ] Run: `ansible-playbook -i inventory/hosts.ini playbooks/configure-kubectl.yml`
- [ ] Access Jenkins at `http://<jenkins-ip>:8080` — get initial admin password from EC2
- [ ] Access SonarQube at `http://<jenkins-ip>:9000` (default: admin/admin)

### Step 5: Configure Jenkins & SonarQube
- [ ] In SonarQube: create a custom quality gate (coverage >= 70%, critical bugs = 0), assign to all 3 projects
- [ ] In SonarQube: create a webhook pointing to `http://<jenkins-ip>:8080/sonarqube-webhook/`
- [ ] In SonarQube: generate an authentication token
- [ ] In Jenkins: add SonarQube server under "Manage Jenkins > System" with the token
- [ ] In Jenkins: create a Pipeline job pointing to your GitHub repo
- [ ] In Jenkins: configure GitHub webhook (`http://<jenkins-ip>:8080/github-webhook/`)

### Step 6: Update Placeholders in Code
- [ ] Update `Jenkinsfile` — replace `<AWS_ACCOUNT_ID>` with your actual AWS account ID
- [ ] Update `k8s/configmaps.yml` — replace `<RDS_ENDPOINT>` with actual RDS endpoint from Terraform output
- [ ] Update `k8s/*/deployment.yml` — replace `<ECR_REGISTRY>` with your ECR registry URL

### Step 7: Deploy Kafka to EKS
```bash
aws eks update-kubeconfig --name payments-sim-eks --region ap-south-1
helm install kafka oci://registry-1.docker.io/bitnamicharts/kafka -f k8s/kafka/helm-values.yml
kubectl get pods  # Wait for kafka to be Running
```

### Step 8: Initial K8s Deployment
```bash
kubectl apply -f k8s/configmaps.yml
kubectl apply -f k8s/secrets.yml  # After updating with real base64-encoded password
kubectl apply -f k8s/payment-service/
kubectl apply -f k8s/fraud-detection-service/
kubectl apply -f k8s/notification-service/
kubectl apply -f k8s/payment-dashboard/
kubectl get pods -w  # Watch until all pods are Running and Ready
kubectl get svc payment-dashboard  # Get the LoadBalancer external IP
```

### Step 9: Push Code and Trigger Pipeline
- [ ] Initialize git repo: `git init && git add -A && git commit -m "Initial commit"`
- [ ] Push to GitHub
- [ ] Verify Jenkins pipeline triggers automatically via webhook
- [ ] Watch all 7 stages pass green

### Step 10: Final Verification
- [ ] Access the dashboard via the K8s LoadBalancer URL
- [ ] Submit payments, verify APPROVED/FLAGGED flow works end-to-end
- [ ] Check SonarQube dashboard for code quality metrics
- [ ] Verify Jenkins shows test results and build history

---

## Completed Steps Log

### Step 1: Prerequisites — DONE (2026-03-23)
- Java 17, Maven, Node 18, Docker, Docker Compose — all installed
- AWS CLI configured (account 189060531760, user cli-linux, region ap-south-1)
- Terraform v1.14.7 installed

### Step 2: Local Development Testing — DONE (2026-03-23)
- All 3 services compile successfully
- All 15 unit tests pass (9 payment-service, 4 fraud-detection, 2 notification)
- `docker-compose up --build` — all 6 containers running and healthy
- React dashboard serves at `http://localhost:3000` with working Nginx API proxy
- Full Kafka pipeline verified: POST payment → fraud-detection → status update (APPROVED/FLAGGED) → notification logging
- **Fixes applied during testing:**
  - Fixed `@MockBean` import in `PaymentControllerTest.java`: changed from `org.springframework.boot.test.mock.bean.MockBean` to `org.springframework.boot.test.mock.mockito.MockBean`
  - Replaced Bitnami Kafka/Zookeeper with `apache/kafka:3.7.0` KRaft mode (Bitnami images removed from Docker Hub)
  - Changed PostgreSQL host port from 5432 to 5433 (local port conflict)

### Steps 3-10: Cloud Deployment — PENDING
- AWS region confirmed as ap-south-1 (Mumbai)
- Terraform files ready, awaiting S3 bucket + DynamoDB table creation before `terraform init`

---

## Troubleshooting Checklist

| Symptom | Likely Cause | Fix |
|---|---|---|
| Services crash on startup in docker-compose | Kafka/PostgreSQL not healthy yet | Check `depends_on` conditions, increase Kafka health check retries |
| Kafka messages not consumed | Topic name mismatch or FQCN deserialization error | Check `kafka-topics.sh --list`, verify `spring.json.use.type.headers=false` |
| Payment status stays PENDING | fraud-detection-service not running or not consuming | Check fraud-detection logs, verify consumer group ID |
| CORS error in browser | Nginx proxy not configured or React dev server proxy missing | Check `nginx.conf` proxy_pass, check `package.json` proxy field |
| Pods in CrashLoopBackOff | Liveness probe firing before app starts | Increase `initialDelaySeconds` on liveness probe |
| Pods can't connect to RDS | Security group missing ingress rule | Verify RDS SG allows EKS worker SG on port 5432 |
| Pods can't pull images | Missing NAT gateway or IAM policy | Verify NAT GW exists, verify node IAM role has ECR ReadOnly policy |
| `waitForQualityGate` hangs | SonarQube webhook not configured | Add webhook in SonarQube pointing to Jenkins sonarqube-webhook URL |
| Terraform state locked | Previous `apply` crashed | Run `terraform force-unlock <lock-id>` |
| Ansible "permission denied" on Docker | Jenkins not restarted after group change | SSH to EC2, run `sudo systemctl restart jenkins` |
