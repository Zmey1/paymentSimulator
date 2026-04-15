# Viva Notes — DevOps Tools Used In This Project

This document explains the DevOps tools used in the **Event-Driven Payments Simulator**, why each tool was chosen, and how it is used in this repository.

The goal of the project was not just to build a payments app, but to show a complete DevOps workflow:

- local development
- automated testing
- code quality checks
- containerization
- infrastructure as code
- CI/CD
- Kubernetes deployment

---

## 1. Project Context

This project is an **event-driven microservices system**.

Main application components:

- `payment-service` — accepts payments, stores them in PostgreSQL, publishes Kafka events
- `fraud-detection-service` — consumes payment events and applies fraud rules
- `notification-service` — consumes fraud results and logs notifications
- `payment-dashboard` — React frontend used to create and monitor payments

The DevOps tools were used to support the full lifecycle of this application, from development to deployment.

---

## 2. Docker

### What Docker does in this project

Docker is used to package each service into a reproducible container image.

We use Docker for:

- consistent runtime environments
- easier local setup
- image-based deployment to Kubernetes
- CI/CD build and push to Amazon ECR

### How we used Docker

Each major component has its own Dockerfile:

- `services/payment-service/Dockerfile`
- `services/fraud-detection-service/Dockerfile`
- `services/notification-service/Dockerfile`
- `payment-dashboard/Dockerfile`
- `tests/Dockerfile`

### Important implementation detail

We used **multi-stage builds**.

For Java services:

- build stage uses Maven + JDK
- runtime stage uses a lighter JRE image

For the frontend:

- build stage uses Node.js
- runtime stage uses Nginx to serve static files

### Viva point

If asked why Docker is important:

> Docker ensures that the same application runs the same way on the developer machine, in Jenkins, and in Kubernetes. It removes “works on my machine” problems.

---

## 3. Docker Compose

### What Docker Compose does in this project

Docker Compose is used for **local orchestration** of the full system.

### How we used it

The root `docker-compose.yml` starts:

- Kafka
- PostgreSQL
- payment-service
- fraud-detection-service
- notification-service
- payment-dashboard

It also passes environment variables such as:

- database connection strings
- Kafka bootstrap server
- optional Razorpay test mode configuration

### Why it matters

It lets us run the entire distributed system locally with one command:

```bash
docker compose up --build
```

### Viva point

> Docker Compose is for local multi-container development. Kubernetes is for production-grade orchestration. Compose is simpler and faster for demos and development.

---

## 4. Apache Kafka

### What Kafka does in this project

Kafka is the backbone of the event-driven architecture.

Instead of direct service-to-service REST calls, services communicate through events.

### How we used it

Topics used in the system:

- `payment.created`
- `payment.approved`
- `payment.flagged`

Flow:

1. `payment-service` publishes `payment.created`
2. `fraud-detection-service` consumes it and decides the result
3. it then publishes `payment.approved` or `payment.flagged`
4. other services consume those result events

### Why this is a DevOps-relevant design

Kafka shows:

- loose coupling between services
- asynchronous processing
- scalable event-driven communication

### Viva point

> Kafka helps us decouple microservices. The payment service does not need to wait for fraud detection synchronously; it publishes an event and other services react to it.

---

## 5. PostgreSQL

### What PostgreSQL does in this project

PostgreSQL is the persistence layer for payment records.

### How we used it

- locally through Docker Compose
- in cloud design through AWS RDS

The `payment-service` stores payment details and later updates the payment status when fraud results arrive.

### Why it matters

It demonstrates:

- persistence beyond in-memory processing
- integration between microservices and a relational database
- environment-specific configuration for local and cloud deployment

---

## 6. Terraform

### What Terraform does in this project

Terraform provides **Infrastructure as Code**.

Instead of manually creating AWS resources in the console, we define them in code.

### How we used it

The `terraform/` directory provisions AWS infrastructure using reusable modules.

Modules present in the repo:

- `modules/vpc`
- `modules/eks`
- `modules/rds`
- `modules/ecr`
- `modules/jenkins-ec2`

Main infrastructure covered:

- VPC
- subnets
- EKS cluster
- RDS PostgreSQL
- ECR repositories
- Jenkins EC2 instance

### Why Terraform was useful

- infrastructure is reproducible
- changes are version-controlled
- easy to review and explain
- suitable for team and cloud workflows

### Viva point

> Terraform is used for provisioning infrastructure, not configuring software inside servers. It creates the AWS resources we need before deployment starts.

---

## 7. Ansible

### What Ansible does in this project

Ansible is used for **configuration management** after infrastructure is provisioned.

### How we used it

Playbooks in the repo:

- `ansible/playbooks/setup-jenkins.yml`
- `ansible/playbooks/setup-sonarqube.yml`
- `ansible/playbooks/configure-kubectl.yml`

Roles used:

- `docker`
- `jenkins`
- `sonarqube`

Examples of tasks automated by Ansible:

- installing Docker Engine
- installing Java 17
- installing Jenkins
- installing Jenkins plugins
- installing AWS CLI
- installing `kubectl`
- configuring Jenkins user access to Docker
- running SonarQube as a container
- updating kubeconfig for EKS

### Why we used both Terraform and Ansible

Terraform and Ansible solve different problems:

- Terraform creates infrastructure
- Ansible configures machines and software inside them

### Viva point

> Terraform answers “what infrastructure should exist?” Ansible answers “how should a server be configured after it exists?”

---

## 8. Jenkins

### What Jenkins does in this project

Jenkins implements the **CI/CD pipeline**.

### How we used it

The pipeline is defined in the root `Jenkinsfile`.

Stages included:

1. Checkout
2. Build
3. Unit Tests
4. SonarQube Analysis
5. Quality Gate
6. Docker Build & Push
7. Integration Tests
8. Deploy to EKS

### Specific behavior in our pipeline

- Maven builds the Java services
- unit tests run in parallel for services
- SonarQube analysis is triggered through Maven
- Docker images are built and pushed to ECR
- Docker Compose is used for integration testing
- Selenium tests run after the system is started
- `kubectl set image` updates deployments in EKS
- `kubectl rollout status` checks whether deployment succeeded

### Why Jenkins was appropriate

It demonstrates:

- automation of build-test-deploy flow
- repeatable pipeline execution
- integration with AWS, Docker, and Kubernetes

### Viva point

> Jenkins turns our manual release process into an automated pipeline. Every code change can be built, tested, scanned, containerized, and deployed in a defined sequence.

---

## 9. SonarQube

### What SonarQube does in this project

SonarQube is used for **static code analysis** and quality gating.

### How we used it

- Ansible deploys SonarQube on the Jenkins server
- Jenkins runs `mvn sonar:sonar`
- the pipeline waits for a quality gate result before continuing

### What it checks

Typical checks include:

- bugs
- code smells
- maintainability issues
- test coverage integration through JaCoCo

### Why it matters

This shows that the project is not only deployable, but also monitored for code quality before release.

### Viva point

> SonarQube acts as a quality checkpoint in the CI/CD pipeline. It helps prevent poor-quality code from progressing further in deployment.

---

## 10. JUnit and Mockito

### What they do in this project

These tools are used for **unit testing** the Java services.

### How we used them

Each service has tests for core logic such as:

- controller behavior
- service logic
- Kafka producer interactions
- fraud detection rules

Mockito is used to isolate components such as:

- repositories
- Kafka producers
- service dependencies

### Why they matter in DevOps

Automated unit tests are essential for CI. Jenkins can fail fast before code reaches deployment.

### Viva point

> Unit tests give fast feedback on business logic. They are the first safety layer in the pipeline.

---

## 11. Selenium

### What Selenium does in this project

Selenium is used for **end-to-end integration testing** of the dashboard and full workflow.

### How we used it

The `tests/` project contains Selenium-based browser tests.

The integration test container:

- is built from `tests/Dockerfile`
- uses Maven + Java 17
- runs Selenium tests through `mvn test`

Jenkins starts the system with Docker Compose before running these tests.

### What the tests prove

They verify that:

- the dashboard loads
- a payment can be submitted
- status changes appear in the UI
- the full backend event pipeline is functioning

### Why it matters

Unit tests validate isolated components. Selenium validates the complete user-visible workflow.

### Viva point

> Selenium is our top-level validation. It proves that the frontend, backend, Kafka flow, and database updates work together end to end.

---

## 12. Kubernetes

### What Kubernetes does in this project

Kubernetes is used for container orchestration in the cloud deployment design.

### How we used it

The `k8s/` directory contains deployment manifests for:

- payment-service
- fraud-detection-service
- notification-service
- payment-dashboard

Also included:

- `configmaps.yml`
- `secrets.yml`
- `k8s/kafka/helm-values.yml`

### Key Kubernetes concepts shown in the repo

#### Deployments

Used to manage pods and rolling updates.

Example:

- `payment-service` runs with `replicas: 2`
- readiness and liveness probes are configured using `/actuator/health`

#### Services

Used for networking inside and outside the cluster.

- `payment-service` uses `ClusterIP`
- `payment-dashboard` uses `LoadBalancer`

This shows the difference between internal service communication and public exposure.

#### ConfigMaps and Secrets

Used to separate config from container images.

- ConfigMap stores Kafka and DB connection values
- Secret stores the database password

### Why Kubernetes matters

It demonstrates:

- declarative deployment
- service discovery
- self-healing
- scaling
- rolling updates

### Viva point

> Docker packages the app. Kubernetes manages the app at runtime across a cluster.

---

## 13. Helm

### What Helm does in this project

Helm is used to simplify Kafka deployment in Kubernetes.

### How we used it

Instead of manually writing full Kafka manifests, we use a Helm values file:

- `k8s/kafka/helm-values.yml`

This file configures:

- topic provisioning
- persistence
- replica counts
- listener settings

### Why Helm was useful

Kafka is complex to deploy manually. Helm makes deployment faster and less error-prone while still allowing customization.

### Viva point

> Helm is a package manager for Kubernetes. We used it because deploying Kafka manually is much more complex than deploying our own services.

---

## 14. Amazon ECR

### What ECR does in this project

Amazon ECR is the container registry used to store built images.

### How we used it

Terraform creates the repositories.

Jenkins:

- builds Docker images
- tags them with `BUILD_NUMBER`
- pushes them to ECR

Kubernetes then pulls those images during deployment.

### Why it matters

It connects the CI part and the deployment part of the pipeline.

---

## 15. Amazon EKS

### What EKS does in this project

Amazon EKS is the managed Kubernetes platform for cloud deployment.

### How we used it

- Terraform provisions the EKS cluster
- Ansible configures `kubectl`
- Jenkins deploys updated images to EKS

This shows a production-style deployment target rather than running only on a single VM.

---

## 16. AWS RDS

### What RDS does in this project

RDS is the managed PostgreSQL database used in the cloud architecture.

### How we used it

- Terraform provisions the database
- Kubernetes services use the RDS endpoint through environment configuration

### Why it matters

It demonstrates using a managed database instead of running PostgreSQL inside the Kubernetes cluster in production.

---

## 17. End-to-End DevOps Workflow

Here is the complete flow of how all tools fit together:

### Local development

1. Developer writes code
2. `docker compose up --build` starts the full local environment
3. local unit tests are run with Maven
4. Selenium tests can validate end-to-end behavior

### Infrastructure provisioning

1. Terraform provisions AWS resources
2. outputs provide values like Jenkins IP, EKS cluster name, RDS endpoint, and ECR URLs

### Server configuration

1. Ansible installs Jenkins, Docker, kubectl, AWS CLI, and SonarQube
2. Jenkins becomes ready to run the CI/CD pipeline

### CI/CD

1. GitHub webhook triggers Jenkins
2. Jenkins checks out code
3. services build with Maven
4. unit tests run
5. SonarQube analysis runs
6. quality gate is checked
7. Docker images are built
8. images are pushed to ECR
9. integration tests run with Docker Compose + Selenium
10. Kubernetes deployments are updated in EKS

### Production-style runtime

1. Kubernetes manages replicas and service discovery
2. Kafka handles event flow
3. RDS stores data
4. dashboard is exposed to users through a LoadBalancer service

---

## 18. Short Viva Answers

### Why did you use both Terraform and Ansible?

Terraform provisions AWS infrastructure. Ansible configures the provisioned machine and installs tools like Jenkins, Docker, kubectl, and SonarQube.

### Why Docker if Kubernetes is already used?

Kubernetes runs containers, but Docker is what builds and packages the application into images first.

### Why do we need Jenkins if we can run commands manually?

Jenkins automates the sequence and ensures every code push goes through the same build, test, analysis, and deployment process.

### Why use SonarQube?

To enforce code quality and integrate static analysis into the CI/CD pipeline.

### Why use Selenium when unit tests already exist?

Unit tests verify components in isolation. Selenium verifies the whole user journey through the running system.

### Why use Kafka here?

To demonstrate a real event-driven microservices pattern instead of tightly coupling services through synchronous API calls.

### Why use Helm for Kafka?

Kafka is operationally complex in Kubernetes. Helm reduces that complexity and gives a reusable deployment method.

### Why use Docker Compose if the final target is EKS?

Docker Compose gives a fast, laptop-friendly environment for development and demo without requiring AWS access.

---

## 19. Final Summary

This project demonstrates a full DevOps toolchain:

- **Docker** for packaging
- **Docker Compose** for local orchestration
- **Kafka** for event-driven communication
- **PostgreSQL** for persistence
- **Terraform** for AWS infrastructure provisioning
- **Ansible** for machine and tool configuration
- **Jenkins** for CI/CD automation
- **SonarQube** for static analysis and quality gates
- **JUnit + Mockito** for unit testing
- **Selenium** for end-to-end testing
- **Kubernetes** for orchestration
- **Helm** for Kafka deployment in K8s
- **ECR / EKS / RDS** as cloud delivery components

The main idea to say in the viva is:

> We did not just build an application. We built the application together with the automation, infrastructure, testing, deployment, and quality processes needed to run it using real DevOps practices.
