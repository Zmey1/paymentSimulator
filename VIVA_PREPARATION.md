# Viva Preparation — Event-Driven Payments Simulator

A complete explanation of every technology, design decision, and implementation detail in this project.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Microservices — Why and How](#2-microservices--why-and-how)
3. [Apache Kafka — Event-Driven Communication](#3-apache-kafka--event-driven-communication)
4. [Spring Boot — Backend Framework](#4-spring-boot--backend-framework)
5. [PostgreSQL — Database](#5-postgresql--database)
6. [React — Frontend Dashboard](#6-react--frontend-dashboard)
7. [Nginx — Reverse Proxy](#7-nginx--reverse-proxy)
8. [Docker — Containerization](#8-docker--containerization)
9. [Docker Compose — Local Orchestration](#9-docker-compose--local-orchestration)
10. [Terraform — Infrastructure as Code](#10-terraform--infrastructure-as-code)
11. [Ansible — Configuration Management](#11-ansible--configuration-management)
12. [Kubernetes (EKS) — Container Orchestration](#12-kubernetes-eks--container-orchestration)
13. [Jenkins — CI/CD Pipeline](#13-jenkins--cicd-pipeline)
14. [SonarQube — Code Quality](#14-sonarqube--code-quality)
15. [Selenium — End-to-End Testing](#15-selenium--end-to-end-testing)
16. [Common Viva Questions and Answers](#16-common-viva-questions-and-answers)

---

## 1. Architecture Overview

```
                           ┌──────────────────────┐
                           │   React Dashboard    │
                           │   (port 3000/80)     │
                           └──────────┬───────────┘
                                      │ HTTP POST/GET
                                      │ (via Nginx reverse proxy)
                                      ▼
                           ┌──────────────────────┐
                           │   Payment Service    │
                           │   (port 8080)        │
                           │   - REST API         │
                           │   - PostgreSQL       │
                           │   - Kafka Producer   │
                           │   - Kafka Consumer   │
                           └──────────┬───────────┘
                                      │
                    Publishes event    │    Consumes approved/flagged
                    payment.created    │    (updates DB status)
                                      ▼
                           ┌──────────────────────┐
                           │    Apache Kafka      │
                           │    (KRaft mode)      │
                           │    port 9092         │
                           │                      │
                           │  Topics:             │
                           │  - payment.created   │
                           │  - payment.approved  │
                           │  - payment.flagged   │
                           └──────────┬───────────┘
                                      │
                         Consumes     │
                         payment.created
                                      ▼
                           ┌──────────────────────┐
                           │  Fraud Detection     │
                           │  Service (port 8081) │
                           │                      │
                           │  Rule: amount>50000  │
                           │  → FLAGGED           │
                           │  else → APPROVED     │
                           └──────────┬───────────┘
                                      │
                    Publishes to      │
                    payment.approved   │
                    or payment.flagged │
                                      ▼
                           ┌──────────────────────┐
                           │    Apache Kafka      │
                           └──────┬───────┬───────┘
                                  │       │
                    ┌─────────────┘       └──────────────┐
                    ▼                                    ▼
         ┌──────────────────┐                ┌───────────────────┐
         │ Payment Service  │                │ Notification Svc  │
         │ (Consumer)       │                │ (port 8082)       │
         │ Updates status   │                │ Logs EMAIL/SMS    │
         │ in PostgreSQL    │                │ alerts            │
         └──────────────────┘                └───────────────────┘
```

**This is an event-driven microservices architecture.** The key principle: services don't call each other directly. Instead, they communicate by publishing and consuming events through Kafka. This makes them loosely coupled — each service can be developed, deployed, and scaled independently.

---

## 2. Microservices — Why and How

### What is a Microservice?
A microservice is a small, independent application that does one thing well. Instead of building one large application (monolith), you build multiple small services that communicate over a network.

### Our Three Services

#### Payment Service (the core service)
- **Responsibility:** Accepts payment requests, stores them in PostgreSQL, publishes events
- **REST endpoints:**
  - `POST /api/payments` — creates a new payment (status = PENDING), returns 201
  - `GET /api/payments` — lists all payments
  - `GET /api/payments/{id}` — get one payment (returns 404 if not found)
- **Kafka role:** Both producer (publishes `payment.created`) AND consumer (listens for `payment.approved` / `payment.flagged` to update DB)

#### Fraud Detection Service
- **Responsibility:** Listens for new payments, applies fraud rules, publishes result
- **Rule:** If `amount > 50,000` → FLAGGED, otherwise → APPROVED
- **Kafka role:** Consumer of `payment.created`, producer of `payment.approved` or `payment.flagged`
- **No database** — purely event-driven, stateless

#### Notification Service
- **Responsibility:** Listens for approved/flagged events, simulates sending notifications
- **Kafka role:** Consumer only — listens on both `payment.approved` and `payment.flagged`
- **Output:** Structured log messages like `NOTIFICATION: [EMAIL] Payment abc123 - Amount 500.0 from Alice to Bob - Status: APPROVED`
- **No database, no producer** — pure consumer

### Why Microservices Over a Monolith?
| Aspect | Monolith | Microservices |
|--------|----------|---------------|
| Deployment | Deploy everything together | Deploy services independently |
| Scaling | Scale the entire app | Scale only the service that needs it |
| Technology | One language/framework | Each service can use different tech |
| Failure | One bug can crash everything | One service failing doesn't break others |
| Team | Everyone works on same codebase | Teams can own individual services |

### Viva Tip
> "We chose microservices because each service has a distinct responsibility. The fraud detection logic can be updated without touching the payment or notification code. In production, if fraud detection is slow, we can scale just that service horizontally by adding more replicas."

---

## 3. Apache Kafka — Event-Driven Communication

### What is Kafka?
Apache Kafka is a distributed event streaming platform. Think of it as a highly reliable message queue. Producers publish messages to **topics**, and consumers subscribe to those topics.

### Key Kafka Concepts

| Concept | Explanation | In Our Project |
|---------|-------------|----------------|
| **Topic** | A named channel for messages | `payment.created`, `payment.approved`, `payment.flagged` |
| **Producer** | Sends messages to a topic | PaymentProducer, FraudResultProducer |
| **Consumer** | Reads messages from a topic | PaymentEventConsumer, NotificationConsumer |
| **Consumer Group** | A set of consumers that share the work | `fraud-detection-group`, `notification-group`, `payment-group` |
| **Partition** | A topic is split into partitions for parallelism | 3 partitions per topic (in K8s Helm config) |
| **Offset** | Position of a consumer within a partition | Managed automatically by Kafka |
| **Broker** | A Kafka server | 1 broker in our setup |

### KRaft Mode (Important!)
Our project uses **KRaft mode** (Kafka Raft), not the older Zookeeper-based mode.

- **Old way:** Kafka needed a separate Zookeeper cluster to manage metadata (which broker is leader, topic configurations, etc.)
- **New way (KRaft):** Kafka manages its own metadata using the Raft consensus protocol. No Zookeeper needed.
- **Why it matters:** Simpler deployment (one less service), faster startup, fewer moving parts

The relevant environment variables in docker-compose.yml:
```yaml
KAFKA_PROCESS_ROLES: broker,controller    # This node acts as both broker AND controller
KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093   # Raft quorum config
CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk             # Unique cluster identifier
```

### Why Kafka Over REST Calls?

If payment-service called fraud-detection-service via HTTP:
- **Tight coupling:** payment-service must know fraud-detection's URL
- **Synchronous:** payment-service blocks until fraud-detection responds
- **Failure cascade:** if fraud-detection is down, payment creation fails

With Kafka:
- **Loose coupling:** payment-service just publishes an event — it doesn't know or care who consumes it
- **Asynchronous:** payment-service returns immediately to the user (status: PENDING)
- **Resilience:** if fraud-detection is temporarily down, messages queue up in Kafka and get processed when it recovers
- **Fan-out:** multiple consumers can listen to the same topic (both payment-service and notification-service consume `payment.approved`)

### Message Format (PaymentEvent DTO)

```java
public class PaymentEvent {
    public static final String TOPIC_CREATED = "payment.created";
    public static final String TOPIC_APPROVED = "payment.approved";
    public static final String TOPIC_FLAGGED = "payment.flagged";

    private String paymentId;
    private String sender;
    private String receiver;
    private double amount;
    private String paymentType;
    private String status;
    private LocalDateTime timestamp;
}
```

Topic names are defined as `public static final String` constants — not in configuration files. This means if you mistype a topic name, you get a **compile error** instead of a silent runtime bug where messages go to the wrong topic.

### Serialization Pitfall (FQCN Mismatch)

This is the #1 Kafka issue in Spring Boot microservices:

**The problem:** When service A serializes a `com.payments.paymentservice.model.PaymentEvent` and service B tries to deserialize it, Spring Kafka by default checks the fully-qualified class name (FQCN) in the message header. But service B has the class at `com.payments.frauddetection.model.PaymentEvent` — **different package name!** Deserialization fails.

**Our fix (in every service's application.yml):**
```yaml
spring:
  kafka:
    consumer:
      properties:
        spring.json.use.type.headers: false          # Don't check the FQCN header
        spring.json.trusted.packages: "*"            # Trust all packages
        spring.json.value.default.type: com.payments.frauddetection.model.PaymentEvent  # Use local class
```

### Viva Tip
> "We disabled Kafka type headers because each microservice has the PaymentEvent DTO in a different Java package. Without this setting, deserialization would fail due to fully-qualified class name mismatch. This is a well-known pitfall when multiple Spring Boot services share the same message format without a shared library."

---

## 4. Spring Boot — Backend Framework

### What is Spring Boot?
Spring Boot is a Java framework that makes it easy to create production-ready applications. It provides auto-configuration, embedded servers, and starter dependencies so you don't need to write boilerplate XML configuration.

### Key Spring Boot Concepts Used

#### Dependency Injection (`@Autowired`)
Spring manages object creation. Instead of `new PaymentService()`, Spring creates instances and injects them where needed:
```java
@RestController
public class PaymentController {
    @Autowired
    private PaymentService paymentService;  // Spring creates and injects this
}
```

#### REST Controller (`@RestController`)
Handles HTTP requests and returns JSON responses:
```java
@PostMapping("/api/payments")
public ResponseEntity<Payment> createPayment(@RequestBody Payment payment) {
    Payment created = paymentService.createPayment(payment);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
}
```
- `@RequestBody` — parses the incoming JSON into a Payment object
- `ResponseEntity.status(HttpStatus.CREATED)` — returns HTTP 201 (not 200)

#### JPA Entity (`@Entity`)
Maps a Java class to a database table:
```java
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    private String id;       // Primary key
    private String sender;
    private String receiver;
    private double amount;
    // ... Spring auto-generates the SQL
}
```

#### Spring Data JPA Repository
```java
public interface PaymentRepository extends JpaRepository<Payment, String> {
    // Spring auto-generates: findAll(), findById(), save(), delete(), etc.
    // No SQL needed — Spring generates it from the method name
}
```

#### Kafka Listener (`@KafkaListener`)
```java
@KafkaListener(topics = {PaymentEvent.TOPIC_APPROVED, PaymentEvent.TOPIC_FLAGGED},
               groupId = "payment-group")
public void handlePaymentResult(PaymentEvent event) {
    // This method is called automatically when a message arrives on these topics
}
```

#### `@Transactional`
Ensures database operations are atomic:
```java
@Transactional
public void updatePaymentStatus(String id, String status) {
    Payment payment = paymentRepository.findById(id).orElse(null);
    if (payment != null && "PENDING".equals(payment.getStatus())) {
        payment.setStatus(status);
        paymentRepository.save(payment);
    }
}
```
The status check (`"PENDING".equals(...)`) is a **defensive guard** — it prevents processing the same event twice if Kafka delivers it more than once.

#### Externalized Configuration (`application.yml`)
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/payments}
```
The `${SPRING_DATASOURCE_URL:jdbc:postgresql://...}` pattern means:
- Use the environment variable `SPRING_DATASOURCE_URL` if it exists
- Otherwise, fall back to the default value after the colon
- This lets the same code run locally AND in Docker/Kubernetes with different configs

### Entity vs DTO Pattern
We have two separate classes:
- `Payment.java` — JPA entity, mapped to the database table, has `@Entity`, `@Id`, `@Table` annotations
- `PaymentEvent.java` — Kafka DTO (Data Transfer Object), used only for Kafka messages, no JPA annotations

**Why separate?** The database schema and the message format may evolve independently. Adding a column to the DB shouldn't require changing every Kafka consumer.

### Viva Tip
> "Spring Boot's auto-configuration means I didn't need to manually configure a Kafka consumer factory or a JPA entity manager — adding `spring-boot-starter-kafka` and `spring-boot-starter-data-jpa` to the pom.xml is enough. Spring Boot detects these dependencies and configures them automatically from `application.yml`."

---

## 5. PostgreSQL — Database

### Why PostgreSQL?
- Open-source, production-grade relational database
- ACID compliant (Atomicity, Consistency, Isolation, Durability)
- Excellent Spring Boot integration via Spring Data JPA
- Matches the AWS RDS offering in our cloud deployment

### Schema
Only the **payment-service** uses the database. The other two services are stateless.

```sql
CREATE TABLE IF NOT EXISTS payments (
    id VARCHAR(255) PRIMARY KEY,
    sender VARCHAR(255),
    receiver VARCHAR(255),
    amount DOUBLE PRECISION,
    payment_type VARCHAR(255),
    status VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

### Configuration
- `spring.jpa.hibernate.ddl-auto=update` — Hibernate creates/updates the table schema automatically at startup
- Docker Compose maps **host port 5433** to container port 5432 (to avoid conflict with any local PostgreSQL)
- Data persists in a Docker volume `postgres_data`

---

## 6. React — Frontend Dashboard

### Components

**App.js** — Main component, orchestrates everything:
```javascript
useEffect(() => {
    fetchPayments();                          // Fetch once on load
    const interval = setInterval(fetchPayments, 3000);  // Then every 3 seconds
    return () => clearInterval(interval);     // Cleanup on unmount
}, []);
```
This **polling pattern** means the dashboard checks for updates every 3 seconds. It's simpler than WebSockets, though less efficient.

**PaymentForm.js** — Controlled form component:
- Uses React `useState` for form state
- On submit: calls `POST /api/payments` via Axios, then triggers a refresh
- Payment types: TRANSFER, UPI, CARD (dropdown)

**PaymentList.js** — Display component:
- Renders a table of all payments
- Color-coded status: green (APPROVED), red (FLAGGED), orange (PENDING)
- Shows "No payments yet" when the list is empty

**api.js** — Axios HTTP client:
```javascript
const API_BASE_URL = process.env.REACT_APP_API_URL || '/api';
```
- In development: uses React's proxy (`"proxy": "http://localhost:8080"` in package.json)
- In production: uses `/api` which Nginx proxies to the payment-service

### Viva Tip
> "The dashboard uses polling (setInterval every 3 seconds) instead of WebSockets for simplicity. In a production system, you might use WebSockets or Server-Sent Events for real-time updates, but polling is sufficient for this demonstration."

---

## 7. Nginx — Reverse Proxy

### What is a Reverse Proxy?
A reverse proxy sits between the client (browser) and the backend servers. The browser only talks to Nginx — it doesn't know about the payment-service behind it.

### Our nginx.conf:
```nginx
server {
    listen 80;

    location / {
        root /usr/share/nginx/html;     # Serve React static files
        try_files $uri $uri/ /index.html; # SPA routing (all routes → index.html)
    }

    location /api/ {
        proxy_pass http://payment-service:8080/api/;  # Forward API calls to backend
    }
}
```

### Why Use Nginx as a Proxy?

**The CORS Problem:**
- Browser at `localhost:3000` makes an API call to `localhost:8080`
- These are different origins (different ports) → browser blocks the request (CORS policy)

**Solutions we use:**
1. **In Docker/K8s (production):** Nginx serves both the React app AND proxies API calls. Browser sees everything from the same origin (`localhost:3000`). No CORS issue at all.
2. **In React dev mode:** `"proxy": "http://localhost:8080"` in package.json — React's dev server proxies API calls.
3. **Backup:** `CorsConfig.java` explicitly allows `localhost:3000` (defense in depth).

### Viva Tip
> "We use Nginx as a reverse proxy to eliminate CORS issues entirely. The browser talks to Nginx on port 3000, and Nginx forwards `/api/` requests to the payment-service on port 8080. From the browser's perspective, it's all the same origin."

---

## 8. Docker — Containerization

### What is Docker?
Docker packages an application and all its dependencies into a standardized unit called a **container**. A container runs the same way on any machine — your laptop, a CI server, or a cloud instance.

### Key Docker Concepts

| Concept | Explanation |
|---------|-------------|
| **Image** | A read-only template with the application and its dependencies |
| **Container** | A running instance of an image |
| **Dockerfile** | Instructions to build an image |
| **Layer** | Each instruction in a Dockerfile creates a layer. Layers are cached. |
| **Volume** | Persistent storage that survives container restarts |
| **Registry** | Where images are stored (Docker Hub, AWS ECR) |

### Multi-Stage Builds (Important!)

Our Dockerfile for Spring Boot services:
```dockerfile
# Stage 1: BUILD
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .                          # Copy dependency file first
RUN mvn dependency:go-offline -B        # Download all dependencies (cached layer)
COPY src ./src                          # Then copy source code
RUN mvn clean package -DskipTests -B    # Build the JAR

# Stage 2: RUNTIME
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar   # Copy only the JAR from stage 1
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Why multi-stage?**
- Build stage has Maven, JDK, source code (~400MB)
- Runtime stage has only JRE + the JAR (~70MB)
- The final image is **5-6x smaller** because it doesn't include build tools

**Why copy pom.xml before src/?**
This is a **layer caching optimization**:
- If you only change Java source code (not pom.xml), Docker reuses the cached `dependency:go-offline` layer
- Dependencies don't need to be re-downloaded on every code change
- Without this trick, every code change triggers a full dependency download (~2-5 minutes wasted)

### React Dashboard Dockerfile
```dockerfile
# Stage 1: Build React app
FROM node:18-alpine AS build
COPY package*.json ./
RUN npm ci                    # Install exact versions from lock file
COPY . .
RUN npm run build             # Creates optimized static files in /build

# Stage 2: Serve with Nginx
FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
```

### .dockerignore
Prevents unnecessary files from being sent to Docker during build:
```
target/
node_modules/
.git/
*.md
```

### Viva Tip
> "Multi-stage builds are critical for production Docker images. Our build stage uses the full JDK (400MB) to compile, but the runtime stage uses only the JRE Alpine image (70MB). The final image contains just the compiled JAR and a minimal JRE — no Maven, no source code, no build tools."

---

## 9. Docker Compose — Local Orchestration

### What is Docker Compose?
Docker Compose defines and runs multi-container applications. Instead of running 6 separate `docker run` commands, you define everything in one `docker-compose.yml` file and run `docker compose up`.

### Our Setup (6 Services)

```yaml
services:
  kafka:          # Message broker (KRaft mode, no Zookeeper)
  postgres:       # Database
  payment-service:
  fraud-detection-service:
  notification-service:
  payment-dashboard:
```

### Health Checks and Dependencies

```yaml
payment-service:
    depends_on:
      postgres:
        condition: service_healthy    # Wait until postgres health check passes
      kafka:
        condition: service_healthy    # Wait until kafka health check passes
```

Without `condition: service_healthy`, Docker would start the payment-service immediately — before Kafka and PostgreSQL are ready. The service would crash and enter a restart loop.

**Health check examples:**
```yaml
# PostgreSQL: checks if the server accepts connections
test: ["CMD-SHELL", "pg_isready -U payments_user -d payments"]

# Kafka: checks if the broker can list topics (fully initialized)
test: ["CMD-SHELL", "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list || exit 1"]
```

### Networking
Docker Compose creates a default network. Services can reach each other by their service name:
- `payment-service` connects to Kafka at `kafka:9092` (not `localhost:9092`)
- `payment-service` connects to PostgreSQL at `postgres:5432` (not `localhost:5432`)
- Dashboard's Nginx proxies to `payment-service:8080`

### Viva Tip
> "Docker Compose uses `condition: service_healthy` to control startup order. Kafka's health check runs `kafka-topics.sh --list` — this only succeeds after the broker is fully initialized and can serve requests. Without this, the Java services would fail to connect and crash repeatedly."

---

## 10. Terraform — Infrastructure as Code

### What is Terraform?
Terraform lets you define cloud infrastructure (servers, networks, databases) as code. Instead of clicking through the AWS console, you write `.tf` files and Terraform creates/updates/destroys resources to match.

### Key Terraform Concepts

| Concept | Explanation | In Our Project |
|---------|-------------|----------------|
| **Provider** | Plugin for a cloud platform | AWS provider `~> 5.0` |
| **Resource** | A single infrastructure component | VPC, subnet, EC2, RDS |
| **Module** | Reusable group of resources | vpc/, eks/, rds/, ecr/, jenkins-ec2/ |
| **State** | Terraform's record of what exists | Stored in S3 (`payments-sim-tfstate`) |
| **State Lock** | Prevents two people from modifying at once | DynamoDB table (`terraform-lock`) |
| **Variable** | Input parameter | region, instance type, DB password |
| **Output** | Values exported after `terraform apply` | Jenkins IP, RDS endpoint, ECR URLs |

### Our 5 Modules

#### VPC Module (Virtual Private Cloud)
```
10.0.0.0/16 (VPC)
├── 10.0.1.0/24 (Public Subnet 1 - AZ a) — Internet-facing
├── 10.0.2.0/24 (Public Subnet 2 - AZ b) — Internet-facing
├── 10.0.3.0/24 (Private Subnet 1 - AZ a) — No direct internet
└── 10.0.4.0/24 (Private Subnet 2 - AZ b) — No direct internet
```

- **Public subnets:** Have routes to the Internet Gateway. Jenkins EC2 lives here.
- **Private subnets:** Have routes to the NAT Gateway (outbound only, not directly accessible from internet). EKS worker nodes and RDS live here.
- **Internet Gateway (IGW):** Allows inbound/outbound internet access for public subnets
- **NAT Gateway:** Allows private subnet resources to reach the internet (e.g., pull Docker images from ECR) without being directly accessible from outside

**Why separate public/private subnets?**
Security. The database and application pods should not be directly accessible from the internet. Only the load balancer and Jenkins need public access.

#### EKS Module (Elastic Kubernetes Service)
- Creates the EKS **cluster** (Kubernetes control plane managed by AWS)
- Creates a **node group** (EC2 instances that run your pods) — 2 nodes, t3.medium
- **IAM roles:**
  - Cluster role: `AmazonEKSClusterPolicy` — allows EKS to manage AWS resources
  - Worker role: `AmazonEKSWorkerNodePolicy` + `AmazonEKS_CNI_Policy` + `AmazonEC2ContainerRegistryReadOnly`
    - CNI policy: allows pods to get IP addresses from VPC
    - ECR ReadOnly: allows nodes to pull container images from ECR

#### RDS Module (Relational Database Service)
- PostgreSQL 15 on `db.t3.micro` (20GB gp3 storage)
- Placed in **private subnets** (not publicly accessible)
- Security group: only allows port 5432 from EKS worker nodes' security group
- `skip_final_snapshot = true` — for easy cleanup (wouldn't do this in production)

#### ECR Module (Elastic Container Registry)
- Creates 4 repositories: `payments-sim/payment-service`, `payments-sim/fraud-detection-service`, `payments-sim/notification-service`, `payments-sim/payment-dashboard`
- This is where Jenkins pushes Docker images after building them

#### Jenkins EC2 Module
- Ubuntu 22.04 EC2 instance in a public subnet
- IAM instance profile with `AmazonEC2ContainerRegistryPowerUser` + `AmazonEKSClusterPolicy`
  - This means Jenkins can push images to ECR and deploy to EKS **without storing AWS credentials**
- Security group opens ports 22 (SSH), 8080 (Jenkins), 9000 (SonarQube)
- 30GB root volume (SonarQube needs disk space)

### Remote State (S3 + DynamoDB)

```hcl
backend "s3" {
    bucket         = "payments-sim-tfstate"
    key            = "terraform.tfstate"
    region         = "ap-south-1"
    dynamodb_table = "terraform-lock"
    encrypt        = true
}
```

**Why remote state?**
- Local state file (default) can't be shared between team members
- S3 provides versioning — you can recover from bad applies
- DynamoDB locking prevents two people from running `terraform apply` simultaneously (which would corrupt the state)

### Terraform Workflow
```bash
terraform init      # Download provider plugins, initialize backend
terraform plan      # Show what would change (dry run)
terraform apply     # Actually create/modify resources
terraform destroy   # Tear down everything
```

### Viva Tip
> "We use a modular Terraform structure — VPC, EKS, RDS, ECR, and Jenkins are separate modules. This makes the infrastructure reusable and testable. The NAT Gateway is critical: without it, EKS pods in private subnets can't pull container images from ECR, which is the most common EKS deployment failure."

---

## 11. Ansible — Configuration Management

### What is Ansible?
Ansible automates server configuration. Instead of SSHing into a server and manually installing software, you write **playbooks** (YAML files) that describe the desired state. Ansible connects via SSH and makes it happen.

### Key Ansible Concepts

| Concept | Explanation |
|---------|-------------|
| **Inventory** | List of servers to manage (`hosts.ini`) |
| **Playbook** | YAML file describing tasks to run |
| **Role** | Reusable set of tasks (like a function) |
| **Task** | A single action (install a package, start a service) |
| **Module** | Built-in Ansible command (`apt`, `systemd`, `docker_container`) |
| **Handler** | Task that runs only when notified (e.g., restart Jenkins after config change) |
| **Idempotency** | Running the same playbook twice produces the same result |

### Our 3 Roles

#### Docker Role
1. Install prerequisites (apt-transport-https, ca-certificates, curl)
2. Add Docker's official GPG key and APT repository
3. Install docker-ce, docker-ce-cli, containerd, docker-compose-plugin
4. Start and enable Docker service
5. Add `jenkins` user to `docker` group (so Jenkins can build images without sudo)

#### Jenkins Role
1. Install Java 17 (OpenJDK)
2. Add Jenkins APT repository and install Jenkins
3. Install Jenkins plugins: workflow-aggregator (Pipeline), docker-workflow, kubernetes-cli, sonar, git, pipeline-stage-view
4. Install `kubectl` (Kubernetes CLI)
5. Install AWS CLI v2
6. Start and enable Jenkins service

#### SonarQube Role
1. Set `vm.max_map_count = 262144` (Linux kernel parameter required by SonarQube/Elasticsearch)
2. Create data directory `/opt/sonarqube_data`
3. Run `sonarqube:community` Docker container on port 9000 with persistent volume

### Handlers
```yaml
handlers:
  - name: restart jenkins
    systemd:
      name: jenkins
      state: restarted
```
A handler runs only when a task notifies it. When we add Jenkins to the docker group, we notify `restart jenkins` — because the group change only takes effect after a restart.

### Why Ansible Modules Instead of Shell Commands?

**Bad (not idempotent):**
```yaml
- name: Install Docker
  shell: apt-get install -y docker-ce    # Runs every time, even if already installed
```

**Good (idempotent):**
```yaml
- name: Install Docker
  apt:
    name: docker-ce
    state: present    # Only installs if not already present
```

The `apt` module checks if the package is already installed before doing anything. Running the playbook twice is safe — it won't reinstall or break things.

### Viva Tip
> "Ansible is agentless — it connects over SSH and runs commands remotely, unlike Chef or Puppet which require an agent installed on the target server. We use Ansible modules (apt, systemd, docker_container) instead of shell commands because modules are idempotent — running the playbook multiple times produces the same result without side effects."

---

## 12. Kubernetes (EKS) — Container Orchestration

### What is Kubernetes?
Kubernetes (K8s) manages containerized applications across multiple machines. It handles deployment, scaling, networking, and self-healing (restarting crashed containers).

### Key Kubernetes Concepts

| Concept | Explanation | In Our Project |
|---------|-------------|----------------|
| **Pod** | Smallest deployable unit (1+ containers) | Each service runs in a pod |
| **Deployment** | Manages pods — ensures desired number running | `replicas: 2` for payment-service |
| **Service** | Stable network endpoint for pods | ClusterIP (internal), LoadBalancer (external) |
| **ConfigMap** | Non-sensitive configuration key-value pairs | DB URL, Kafka bootstrap servers |
| **Secret** | Sensitive data (base64 encoded) | Database password |
| **Namespace** | Virtual cluster isolation | We use `default` namespace |
| **Node** | A worker machine (EC2 instance in EKS) | 2 nodes, t3.medium |

### Our K8s Resources

#### ConfigMap (`app-config`)
```yaml
data:
  SPRING_DATASOURCE_URL: "jdbc:postgresql://<RDS_ENDPOINT>:5432/payments"
  SPRING_DATASOURCE_USERNAME: "payments_user"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "kafka.default.svc.cluster.local:9092"
```
- Same `SPRING_*` variable names as docker-compose (consistency across environments)
- Kafka URL uses K8s internal DNS: `kafka.default.svc.cluster.local`

#### Deployment (payment-service example)
```yaml
spec:
  replicas: 2                    # Run 2 copies for high availability
  containers:
    - name: payment-service
      image: <ECR_REGISTRY>/payments-sim/payment-service:latest
      resources:
        requests:
          memory: "256Mi"        # Guaranteed minimum
          cpu: "250m"            # 250 millicores = 0.25 CPU
        limits:
          memory: "512Mi"        # Maximum allowed
          cpu: "500m"
      readinessProbe:            # "Is this pod ready to receive traffic?"
        httpGet:
          path: /actuator/health
          port: 8080
        initialDelaySeconds: 30  # Wait 30s before first check
      livenessProbe:             # "Is this pod still alive?"
        httpGet:
          path: /actuator/health
          port: 8080
        initialDelaySeconds: 45  # Wait 45s before first check (MUST be > readiness)
```

#### Probes — Why They Matter

| Probe | Purpose | If it fails |
|-------|---------|-------------|
| **Readiness** | Is the pod ready to handle requests? | Pod removed from Service (no traffic sent to it) |
| **Liveness** | Is the pod alive and healthy? | Pod is killed and restarted |

**Critical pitfall:** If `initialDelaySeconds` for the liveness probe is shorter than the app's startup time, Kubernetes will keep killing and restarting the pod (CrashLoopBackOff). Our Spring Boot apps take ~20-25 seconds to start, so we set readiness at 30s and liveness at 45s.

Both probes hit `/actuator/health` — this is provided by `spring-boot-starter-actuator` and returns HTTP 200 when the application is healthy.

#### Service Types
- **ClusterIP** (default): Internal-only. payment-service, fraud-detection, notification are ClusterIP — they don't need to be accessed from outside the cluster.
- **LoadBalancer**: Creates an AWS ELB (Elastic Load Balancer). payment-dashboard uses this to be publicly accessible.

### Kafka in Kubernetes
Deployed via **Helm** (a package manager for Kubernetes):
```bash
helm install kafka oci://registry-1.docker.io/bitnamicharts/kafka -f k8s/kafka/helm-values.yml
```
Helm chart pre-creates the three topics with 3 partitions each.

### Viva Tip
> "Liveness probes must have a longer initialDelaySeconds than the application's startup time. Our Spring Boot apps take ~25 seconds to start, so we set liveness at 45 seconds. If we set it to 10 seconds, Kubernetes would kill the pod before it finishes starting, causing an infinite CrashLoopBackOff."

---

## 13. Jenkins — CI/CD Pipeline

### What is CI/CD?
- **Continuous Integration (CI):** Automatically build and test code on every commit
- **Continuous Delivery (CD):** Automatically deploy tested code to production

### Our 8-Stage Pipeline (Jenkinsfile)

```
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌───────────┐   ┌─────────┐
│ Checkout │ → │  Build   │ → │  Unit    │ → │ SonarQube │ → │ Quality │
│          │   │(parallel)│   │  Tests   │   │ Analysis  │   │  Gate   │
└──────────┘   └──────────┘   └──────────┘   └───────────┘   └────┬────┘
                                                                   │
┌──────────┐   ┌──────────────┐   ┌─────────────────────┐         │
│ Deploy   │ ← │ Integration  │ ← │ Docker Build & Push │ ← ──────┘
│ to EKS   │   │    Tests     │   │     (to ECR)        │
└──────────┘   └──────────────┘   └─────────────────────┘
```

**Stage 1 — Checkout:** Clone the Git repository

**Stage 2 — Build (parallel):** Run `mvn clean package -DskipTests` for all 3 services simultaneously. Using `parallel` in Jenkins runs them concurrently on the same agent.

**Stage 3 — Unit Tests:** Run `mvn test` for each service. Uses `junit` post step to publish test results to Jenkins dashboard.

**Stage 4 — SonarQube Analysis:** Run `mvn sonar:sonar` within a `withSonarQubeEnv('SonarQube')` block. This sends code to SonarQube for analysis (code smells, bugs, vulnerabilities, coverage).

**Stage 5 — Quality Gate:** `waitForQualityGate abortPipeline: true` — waits for SonarQube to finish analysis and check if the code meets quality standards. If coverage < 70% or critical bugs found, the pipeline stops here.

**Stage 6 — Docker Build & Push:**
```groovy
sh "aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin ${ECR_REGISTRY}"
```
- Authenticates with ECR using the Jenkins EC2's IAM role (no hardcoded credentials)
- Builds all 4 Docker images
- Tags with `BUILD_NUMBER` (so each build has a unique tag)
- Pushes to ECR

**Stage 7 — Integration Tests:** Runs `docker compose up`, waits for services, runs Selenium browser tests, then tears down.

**Stage 8 — Deploy to EKS:**
```groovy
sh "kubectl set image deployment/payment-service payment-service=${ECR_REGISTRY}/payments-sim/payment-service:${BUILD_NUMBER}"
sh "kubectl rollout status deployment/payment-service --timeout=120s"
```
Uses `kubectl set image` for **rolling updates** — pods are replaced one at a time, so there's zero downtime.

### Jenkins Credentials Strategy
Jenkins runs on an EC2 instance with an **IAM instance profile**. This means:
- No AWS access keys stored in Jenkins
- The EC2 instance automatically gets temporary credentials from AWS
- Credentials rotate automatically — no manual key rotation needed

### Viva Tip
> "Our pipeline uses IAM instance profiles instead of stored AWS credentials. The Jenkins EC2 has an IAM role with ECR push and EKS deploy permissions. This is more secure than storing access keys because the credentials are temporary and automatically rotated by AWS."

---

## 14. SonarQube — Code Quality

### What is SonarQube?
SonarQube analyzes source code for:
- **Bugs** — code that is likely wrong
- **Vulnerabilities** — security issues
- **Code smells** — maintainability issues (long methods, duplicated code)
- **Coverage** — percentage of code tested by unit tests

### How It Works in Our Project

1. **JaCoCo** (Java Code Coverage) plugin runs during `mvn test` and generates a coverage XML report
2. Jenkins runs `mvn sonar:sonar` which sends the code + coverage report to SonarQube
3. SonarQube analyzes and assigns a **Quality Gate** status (PASS/FAIL)
4. SonarQube sends a webhook back to Jenkins with the result
5. Jenkins pipeline aborts if the quality gate fails

### Quality Gate Rules
- Code coverage >= 70%
- Zero critical bugs
- Zero blocker bugs

### JaCoCo Configuration (in pom.xml)
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>   <!-- Instruments code before tests -->
        </execution>
        <execution>
            <goals><goal>report</goal></goals>           <!-- Generates report after tests -->
        </execution>
    </executions>
</plugin>
```

### The Webhook Pitfall
`waitForQualityGate` in Jenkins **requires** a SonarQube webhook pointing to `http://<jenkins-ip>:8080/sonarqube-webhook/`. Without this webhook, Jenkins waits forever because SonarQube never notifies it that analysis is complete.

### Viva Tip
> "JaCoCo instruments the bytecode before tests run, then generates a coverage report after. SonarQube consumes this report. The `waitForQualityGate` step in Jenkins requires a webhook from SonarQube — without configuring this webhook, the pipeline hangs indefinitely at the quality gate stage."

---

## 15. Selenium — End-to-End Testing

### What is Selenium?
Selenium automates web browsers. It programmatically opens a browser, clicks buttons, fills forms, and verifies the results — simulating a real user.

### Our 4 Test Cases

```java
@Test @Order(1)
void shouldLoadDashboard()
// Opens the dashboard URL, checks the page title

@Test @Order(2)
void shouldSubmitPaymentAndShowPending()
// Fills in sender=Alice, receiver=Bob, amount=100
// Clicks submit, verifies payment appears in the table

@Test @Order(3)
void shouldShowApprovedStatusForSmallPayment()
// Waits up to 15 seconds for the status to change from PENDING to APPROVED
// (amount 100 < 50000 → fraud check passes)

@Test @Order(4)
void shouldShowFlaggedStatusForLargePayment()
// Submits a payment with amount=60000
// Waits for status to change to FLAGGED (amount > 50000)
```

### Key Implementation Details

**Remote WebDriver:** Tests don't run a browser locally. They connect to a `selenium/standalone-chrome` Docker container:
```java
WebDriver driver = new RemoteWebDriver(
    new URL("http://selenium:4444/wd/hub"),
    new ChromeOptions()
);
```

**Explicit Waits (not Thread.sleep):**
```java
WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
wait.until(ExpectedConditions.textToBePresentInElement(statusCell, "APPROVED"));
```
This waits up to 15 seconds, checking frequently. Much better than `Thread.sleep(15000)` which always waits the full 15 seconds.

### Viva Tip
> "We use WebDriverWait with explicit conditions instead of Thread.sleep. This is more reliable because it returns as soon as the condition is met (e.g., status changes to APPROVED), rather than waiting a fixed duration. It also prevents false failures caused by timing issues."

---

## 16. Common Viva Questions and Answers

### Architecture & Design

**Q: Why event-driven instead of REST-to-REST communication?**
> Loose coupling. Payment-service doesn't need to know the fraud-detection service's URL. If we add a new consumer (e.g., analytics service), we just subscribe to the topic — no changes to existing code. Also provides resilience: if fraud-detection is temporarily down, Kafka holds messages until it recovers.

**Q: What happens if the fraud-detection service goes down?**
> Payments will be created and saved as PENDING, and events will queue up in Kafka. When fraud-detection comes back online, it processes all queued events and statuses update. No data is lost.

**Q: Why separate Payment (JPA entity) from PaymentEvent (Kafka DTO)?**
> Database schema and message format should evolve independently. Adding a database column shouldn't require changing every Kafka consumer across all services.

**Q: What if a payment event is processed twice?**
> Our consumer checks `if (payment.getStatus().equals("PENDING"))` before updating. If the status is already APPROVED/FLAGGED, the duplicate event is ignored. This is called **idempotent processing**.

### Kafka

**Q: What is a consumer group?**
> A consumer group is a set of consumers that cooperate to consume a topic. Each partition in a topic is consumed by exactly one consumer in the group. If you have 3 partitions and 3 consumers in a group, each consumer gets one partition. This enables parallel processing.

**Q: Why three separate consumer group IDs?**
> Each service needs its own copy of every message. If fraud-detection and notification shared a consumer group, each message would go to only ONE of them — not both. Separate groups = each service gets all messages independently.

**Q: What is KRaft mode?**
> KRaft replaces Zookeeper for Kafka metadata management. Instead of running a separate Zookeeper cluster, Kafka uses the Raft consensus protocol internally. Benefits: fewer components to manage, faster controller failover, simpler deployment.

### Docker & Kubernetes

**Q: Why multi-stage Docker builds?**
> To minimize image size. The build stage (~400MB with JDK + Maven) is discarded. The final image has only the JRE and JAR (~70MB). Smaller images mean faster pulls, faster deployments, and smaller attack surface.

**Q: What is the difference between Docker Compose and Kubernetes?**
> Docker Compose runs containers on a single machine — great for local development. Kubernetes runs containers across multiple machines, handles scaling, self-healing, rolling updates, and service discovery. Compose is for dev, K8s is for production.

**Q: Explain readiness vs liveness probes.**
> Readiness probe: "Is this pod ready to receive traffic?" If it fails, the pod is removed from the Service's endpoint list — no traffic is sent to it, but the pod isn't killed. Liveness probe: "Is this pod alive?" If it fails, Kubernetes kills and restarts the pod. Readiness = traffic control, Liveness = health recovery.

**Q: What is a rolling update?**
> When we deploy a new version, Kubernetes creates new pods with the new image while keeping old pods running. Once new pods pass readiness checks, old pods are terminated. Result: zero downtime during deployment.

### Terraform & Ansible

**Q: Why use Terraform remote state in S3?**
> Local state files can't be shared between team members and are easy to lose. S3 provides durability, versioning, and encryption. DynamoDB locking prevents state corruption when two people run `terraform apply` simultaneously.

**Q: What is the difference between Terraform and Ansible?**
> Terraform provisions infrastructure (create a VPC, an EC2 instance, an RDS database). Ansible configures software on existing machines (install Jenkins, configure Docker, set up SonarQube). Terraform says "create a server," Ansible says "install software on that server."

**Q: Why does the NAT Gateway matter?**
> EKS worker nodes run in private subnets (no direct internet access). But pods need to pull Docker images from ECR. The NAT Gateway allows outbound internet connections from private subnets while preventing inbound connections. Without it, pods fail with "image pull failed."

### CI/CD

**Q: Why use IAM instance profiles instead of storing AWS credentials in Jenkins?**
> Security. Stored credentials are static — if they leak, an attacker has permanent access. IAM instance profiles provide temporary credentials that rotate automatically. They can't be extracted or leaked the same way. It's an AWS security best practice.

**Q: What happens if the SonarQube quality gate fails?**
> The pipeline aborts at the Quality Gate stage. Code is never built into Docker images, never pushed to ECR, and never deployed to EKS. This ensures only code meeting quality standards reaches production.

**Q: How does Jenkins know when SonarQube analysis is complete?**
> SonarQube sends a webhook to Jenkins (`http://<jenkins-ip>:8080/sonarqube-webhook/`) when analysis finishes. Jenkins' `waitForQualityGate` step listens for this webhook. Without configuring the webhook, the pipeline hangs indefinitely.

### Testing

**Q: Why mock KafkaTemplate in unit tests instead of using EmbeddedKafka?**
> EmbeddedKafka starts a real Kafka broker inside the test JVM. It's slow (5-10 seconds per test), flaky, and uses a lot of memory. Mocking KafkaTemplate with `@MockBean` is fast, reliable, and tests the unit in isolation — which is what unit tests should do. Integration testing with real Kafka happens in the Docker Compose stage of the pipeline.

**Q: Why use Selenium with a remote WebDriver instead of running Chrome locally?**
> In a CI/CD pipeline, there's no display — you can't open a browser window. The `selenium/standalone-chrome` container runs Chrome in headless mode with a remote WebDriver API. Tests connect to it over the network. This also ensures tests run in a consistent browser environment regardless of the CI server's OS.

---

## Quick Reference: The Full Technology Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Frontend | React 18 | User interface |
| API | Spring Boot 3.2 | REST endpoints |
| Database | PostgreSQL 15 | Payment persistence |
| Messaging | Apache Kafka 3.7 (KRaft) | Event streaming |
| Containerization | Docker | Package applications |
| Local Orchestration | Docker Compose | Run locally |
| Cloud Provider | AWS (ap-south-1) | Production infrastructure |
| IaC | Terraform | Provision AWS resources |
| Config Management | Ansible | Configure servers |
| Container Orchestration | Kubernetes (EKS) | Production deployment |
| CI/CD | Jenkins | Automated pipeline |
| Code Quality | SonarQube + JaCoCo | Analysis + coverage |
| E2E Testing | Selenium + Chrome | Browser automation |
| Unit Testing | JUnit 5 + Mockito | Java unit tests |
| Reverse Proxy | Nginx | Serve React + proxy API |
