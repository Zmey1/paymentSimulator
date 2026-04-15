# Local Demo Guide — Event-Driven Payments Simulator

This guide shows how to run and test the full project locally using Docker Compose. No AWS account or cloud services required.

---

## Prerequisites

| Tool | Version | Check Command |
|------|---------|---------------|
| Docker | 20+ | `docker --version` |
| Docker Compose | v2+ | `docker compose version` |
| Java | 17 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 18+ | `node --version` |

---

## Step 1: Run Unit Tests (All 23 Tests)

```bash
# From project root
cd services/payment-service && mvn test
cd ../fraud-detection-service && mvn test
cd ../notification-service && mvn test
```

**Expected results:**
- payment-service: 14 tests passed (PaymentServiceTest, PaymentControllerTest, PaymentProducerTest)
- fraud-detection-service: 7 tests passed (FraudCheckServiceTest — multi-rule fraud logic)
- notification-service: 2 tests passed (NotificationServiceTest — event handling)

---

## Step 2: Optional Razorpay Sandbox Setup

If you want to demo a real test checkout alongside the simulator form, set Razorpay test credentials before starting Docker Compose:

```bash
export RAZORPAY_ENABLED=true
export RAZORPAY_KEY_ID=rzp_test_your_key_id
export RAZORPAY_KEY_SECRET=your_test_secret
```

If you skip this, the manual simulator form still works and the Razorpay panel will show as disabled.

---

## Step 3: Start the Full System

```bash
# From project root
docker compose up --build
```

This starts 6 containers:

| Container | Port | Purpose |
|-----------|------|---------|
| kafka | 9092 | Apache Kafka in KRaft mode (no Zookeeper) |
| postgres | 5433 | PostgreSQL 15 database |
| payment-service | 8080 | REST API + Kafka producer/consumer |
| fraud-detection-service | 8081 | Fraud rule engine (amount, velocity, self-transfer, round-amount checks) |
| notification-service | 8082 | Simulated email/SMS notifications |
| payment-dashboard | 3000 | React UI with Nginx reverse proxy |

Wait until all health checks pass (~30-60 seconds). You should see log output from all services.

---

## Step 4: Demo the Application

### 4a. Open the Dashboard

Open **http://localhost:3000** in your browser.

You'll see:
- A dark fintech-style dashboard with header badges, stats cards, and a pipeline diagram
- A simulator payment form (sender, receiver, amount, payment type)
- A Razorpay test checkout panel when sandbox credentials are configured
- A payment history table with status/type pills and auto-refresh every 3 seconds

### 4b. Submit a Normal Payment in Simulator Mode (gets APPROVED)

Fill in the form:
- Sender: `Alice`
- Receiver: `Bob`
- Amount: `500`
- Payment Type: `TRANSFER`

Click **Submit Payment**.

**What happens (event flow):**
1. Dashboard sends `POST /api/payments` to payment-service
2. Payment saved to PostgreSQL with status `PENDING`
3. `payment.created` event published to Kafka
4. fraud-detection-service consumes the event and evaluates all configured fraud rules
5. `payment.approved` event published to Kafka
6. payment-service updates DB status to `APPROVED`
7. notification-service logs: `NOTIFICATION: [EMAIL] Payment ... Status: APPROVED`
8. Dashboard polls `/api/payments` and `/api/payments/stats`, then refreshes the table and stat cards

**Expected:** Status changes from PENDING → APPROVED within a few seconds.

### 4c. Submit a Suspicious Payment in Simulator Mode (gets FLAGGED)

Fill in the form:
- Sender: `Charlie`
- Receiver: `Dave`
- Amount: `60000`
- Payment Type: `TRANSFER`

Click **Submit Payment**.

**Expected:** Status changes from PENDING → FLAGGED because the high-amount rule is triggered.

### 4d. Show the Other Fraud Rules

Use one or more of these quick demo cases:

- **Self-transfer:** Sender `Alice`, Receiver `Alice`, Amount `1000`, Type `UPI` → `FLAGGED`
- **Round amount:** Sender `Rohit`, Receiver `Priya`, Amount `40000`, Type `CARD` → `FLAGGED`
- **Velocity:** Submit 3 small payments quickly from the same sender, for example Sender `Rahul`, Receiver `Nina`, Amount `12500`, Type `TRANSFER` three times within a few seconds. The third payment should be `FLAGGED`

This gives you a better demo story than only showing the amount threshold.

### 4e. Run a Razorpay Test Checkout

If you exported Razorpay test credentials in Step 2:

- Enter `Customer Name`, `Email`, `Contact`, and `Amount`
- Click **Pay With Razorpay Test**
- Complete the sandbox checkout in the Razorpay popup

**What happens:**
1. Dashboard requests a Razorpay test order from `payment-service`
2. Razorpay checkout opens with your Test Mode key
3. On success, the dashboard sends the payment id, order id, and signature back to `payment-service`
4. `payment-service` verifies the Razorpay signature server-side
5. The verified payment is converted into an internal payment and sent into the existing Kafka pipeline
6. Fraud detection, notifications, stats cards, and payment history all update as normal

This gives you both demo paths:
- pure simulator flow for controlled fraud cases
- real sandbox gateway flow for external payment integration

---

## Step 5: Verify the Event Pipeline via Logs

```bash
# Watch fraud detection decisions
docker compose logs fraud-detection-service | grep -i "Payment flagged\|Payment approved"

# Watch notification service alerts
docker compose logs notification-service | grep "NOTIFICATION"

# Watch payment status updates
docker compose logs payment-service | grep -i "status\|updated"
```

---

## Step 6: Test the REST API Directly

```bash
# Create a payment via API
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{"sender":"Girish","receiver":"Ayush","amount":1000,"paymentType":"TRANSFER"}'

# List all payments
curl http://localhost:8080/api/payments | python3 -m json.tool

# Get dashboard stats
curl http://localhost:8080/api/payments/stats | python3 -m json.tool

# Check whether Razorpay sandbox checkout is enabled
curl http://localhost:8080/api/payments/razorpay/config | python3 -m json.tool

# Get a specific payment by ID (replace with actual ID from above)
curl http://localhost:8080/api/payments/<payment-id> | python3 -m json.tool
```

---

## Step 7: Verify Docker Multi-Stage Builds

```bash
# Check image sizes — Spring Boot services should be ~200MB, dashboard ~30MB
docker images | grep -E "devops-(payment|fraud|notification)"
```

The Dockerfiles use two-stage builds:
- **Build stage:** `maven:3.9-eclipse-temurin-17` (compiles Java)
- **Runtime stage:** `eclipse-temurin:17-jre-alpine` (minimal JRE)
- **Dashboard:** `node:18-alpine` build → `nginx:alpine` runtime

---

## Step 8: Inspect Code Quality Setup

```bash
# Generate JaCoCo coverage reports
cd services/payment-service && mvn test jacoco:report
cd ../fraud-detection-service && mvn test jacoco:report
cd ../notification-service && mvn test jacoco:report
```

Coverage reports are generated at `target/site/jacoco/index.html` in each service directory — open in browser to view.

---

## What to Show the Professor

### Architecture Highlights
1. **Microservices:** 3 independent Spring Boot services communicating via Kafka events
2. **Event-Driven:** Asynchronous processing — payment-service doesn't call fraud-detection directly
3. **Configurable fraud engine:** Amount, velocity, self-transfer, and round-amount rules are externalized in `application.yml`
4. **Dual payment modes:** Manual simulator input and Razorpay sandbox checkout both feed the same internal event pipeline
5. **Dashboard visibility:** Stats cards and pipeline visualization make the Kafka flow obvious during a live demo
6. **Defensive coding:** Status checked before update (`PENDING` only), `@Transactional` for consistency
7. **Kafka deserialization pitfalls handled:** `spring.json.use.type.headers=false` prevents FQCN mismatch
8. **Docker multi-stage builds:** Optimized image sizes, layer caching for fast rebuilds

### DevOps Toolchain (Code Ready, Awaiting Cloud Deployment)
- **Terraform:** Full AWS infrastructure as code (VPC, EKS, RDS, ECR, Jenkins EC2) — 6 modules, ~25 resources
- **Ansible:** 3 roles (Docker, Jenkins, SonarQube), 3 playbooks — idempotent configuration management
- **Kubernetes:** Deployment manifests with health probes, ConfigMaps, Secrets, LoadBalancer
- **Jenkins:** 8-stage CI/CD pipeline (build → test → SonarQube → Docker push → deploy to EKS)
- **SonarQube:** Code quality analysis with JaCoCo coverage integration
- **Selenium:** 4 end-to-end browser tests with WebDriverWait

### File Count
- **88 files** across 13 implementation phases
- All code compiles, all 23 unit tests pass, full Docker Compose deployment works
- Razorpay sandbox checkout is optional and enabled only when test credentials are provided

---

## Cleanup

```bash
docker compose down -v    # Stop containers and remove volumes
```

---


## Architecture Diagram

```
User → React Dashboard (port 3000)
           │
           │ POST /api/payments (via Nginx proxy)
           ▼
     Payment Service (port 8080)
           │
           │ Saves to PostgreSQL (PENDING)
           │ Publishes to Kafka topic: payment.created
           ▼
     ┌─────────────────────────────┐
     │         Apache Kafka        │
     │    (KRaft mode, port 9092)  │
     └─────────────────────────────┘
           │
           │ payment.created
           ▼
     Fraud Detection Service (port 8081)
           │
           │ Rules:
           │ - amount > 50,000
           │ - self-transfer
           │ - suspicious round amount (10k, 20k, etc in self transfer flagged)
           │ - sender velocity burst
           │
           │ Publishes to: payment.approved OR payment.flagged
           ▼
     ┌─────────────────────────────┐
     │         Apache Kafka        │
     └─────────────────────────────┘
         │                     │
         ▼                     ▼
  Payment Service      Notification Service
  (updates DB status)  (logs EMAIL/SMS alerts)
```
