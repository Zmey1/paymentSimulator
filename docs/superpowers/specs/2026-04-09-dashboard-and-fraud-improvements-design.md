# Dashboard Overhaul & Fraud Detection Improvements — Design Spec

**Date:** 2026-04-09
**Project:** Event-Driven Payments Simulator (DevOps Elective)
**Timeline:** Tight (few days)
**Scope:** Frontend redesign + fraud detection multi-rule engine

---

## 1. Goals

1. Make the React dashboard look professional (dark fintech theme) so it doesn't look like a homework assignment.
2. Showcase the event-driven pipeline visually — the professor should see the Kafka flow at a glance.
3. Improve the fraud detection service from a single threshold check to a multi-rule engine with externalized config.
4. Keep all existing DevOps tooling (Docker, K8s, Jenkins, Terraform, Ansible, SonarQube, Selenium) working — no breaking changes.

**Non-goals:** No new microservices, no new Kafka topics, no DB schema changes, no new npm/maven dependencies, no routing/multi-page dashboard, no risk scoring system.

---

## 2. Dashboard UI Overhaul

### 2.1 Layout

Single-page layout. All sections visible without navigation. Top-to-bottom order:

1. Header/Navbar
2. Stats Cards Row
3. Pipeline Visualization
4. Submit Payment Form (left) + Payment History Table (right)

### 2.2 Theme

Dark fintech theme applied via `App.css`:

- **Background:** `#0a0a1a` (page), `#16213e` with subtle gradients (cards/panels)
- **Text:** `#e0e0e0` (body), `#fff` (headings/numbers), `#7a8ba8` (labels)
- **Accents:** `#4fc3f7` (primary/blue), `#27ae60` (approved/green), `#e74c3c` (flagged/red), `#e67e22` (pending/orange)
- **Borders:** `#1e3a5f`
- **Inputs:** `#0d1b30` background, `#1e3a5f` border
- **Cards:** `border-radius: 12px`, subtle `box-shadow`, colored left borders for stats

### 2.3 Header/Navbar

- Left: Logo box (gradient blue square with "P") + "PayFlow" title + "Simulator" subtitle
- Right: Status badges — "Kafka Connected" and "PostgreSQL Online" as green pills with glowing dot indicators
- These badges are cosmetic (hardcoded green). They demonstrate awareness of infrastructure status without requiring a health-check endpoint.

### 2.4 Stats Cards

New component: `StatsCards.js`

4 cards in a flex row:

| Card | Color Accent | Value | Subtitle |
|---|---|---|---|
| Total Payments | Blue (`#4fc3f7`) | count | — |
| Approved | Green (`#27ae60`) | count | "X% approval rate" |
| Flagged | Red (`#e74c3c`) | count | "Amount > ₹50,000" |
| Pending | Orange (`#e67e22`) | count | "In pipeline now" |

Data source: `GET /api/payments/stats` (new endpoint — see Section 3).

### 2.5 Pipeline Visualization

New component: `PipelineViz.js`

A static CSS diagram showing the 4-stage event flow:

```
[Payment Service] --payment-created--> [Apache Kafka] --consume--> [Fraud Detection] --approved/flagged--> [Notification]
   REST API + DB                         Event Broker                  Rule Engine                        Email + SMS
```

Each node is a styled box with an icon, service name, and role label. Arrows between nodes show Kafka topic names. Purely visual HTML/CSS — no data binding, no backend calls.

Purpose: makes the event-driven architecture immediately visible during a demo.

### 2.6 Submit Payment Form

Same fields as current: sender, receiver, amount, paymentType. Restyled:

- Dark input backgrounds (`#0d1b30`)
- Uppercase label text (`#7a8ba8`, `letter-spacing: 1px`)
- Gradient submit button (`#4fc3f7` to `#0288d1`)
- Same `handleSubmit` logic — no functional changes

### 2.7 Payment History Table

Same data columns: ID, Sender, Receiver, Amount, Type, Status. Restyled:

- Monospace font for payment IDs (truncated to 8 chars, as current)
- Right-aligned amounts with ₹ prefix
- Pill badges for status: green (APPROVED), red (FLAGGED), orange (PENDING) — `border-radius: 10px`, colored background with low opacity
- Pill badges for payment type with distinct colors: UPI (blue), TRANSFER (orange), CARD (purple)
- Header row with `#7a8ba8` text, no heavy background
- Subtle row separators (`rgba(30,58,95,0.5)`)

### 2.8 Files Changed

| File | Change |
|---|---|
| `payment-dashboard/src/App.js` | New layout structure, import new components |
| `payment-dashboard/src/App.css` | Complete restyle — dark theme, all new classes |
| `payment-dashboard/src/components/PaymentForm.js` | Markup changes for new CSS classes |
| `payment-dashboard/src/components/PaymentList.js` | Pill badges, ₹ formatting, restyled table |
| `payment-dashboard/src/components/StatsCards.js` | **New file** — stats cards component |
| `payment-dashboard/src/components/PipelineViz.js` | **New file** — pipeline diagram component |
| `payment-dashboard/src/services/api.js` | Add `getStats()` function |

---

## 3. Backend: Stats Endpoint

### 3.1 New Endpoint

`GET /api/payments/stats`

Response:
```json
{
  "total": 247,
  "approved": 218,
  "flagged": 12,
  "pending": 17
}
```

### 3.2 Implementation

- `PaymentRepository.java` — add `countByStatus(String status)` query method (Spring Data derives it from the method name, no `@Query` needed)
- `PaymentService.java` — add `getPaymentStats()` method that calls the count queries
- `PaymentController.java` — add `@GetMapping("/stats")` endpoint returning a `Map<String, Long>`

No new model class needed — return a simple Map or inline record.

---

## 4. Fraud Detection: Multi-Rule Engine

### 4.1 Current State

`FraudCheckService.java` has one rule: `if (amount > 50000) return "FLAGGED"`. Threshold is hardcoded.

### 4.2 New Rules

| Rule | Logic | Default Config |
|---|---|---|
| High amount | `amount > fraud.rules.amount-threshold` | `50000` |
| Velocity | Same sender, N+ payments within time window | `3` payments in `300` seconds |
| Self-transfer | `sender.equalsIgnoreCase(receiver)` | Always flags (enabled by default) |
| Suspicious round amount | Amount is exact multiple of 10,000 AND above minimum | Minimum `30000` |

**Any single rule match = FLAGGED.** No scoring.

### 4.3 Configurable Thresholds

New class: `FraudRuleProperties.java` annotated with `@ConfigurationProperties(prefix = "fraud.rules")`

```yaml
# application.yml
fraud:
  rules:
    amount-threshold: 50000
    velocity-count: 3
    velocity-window-seconds: 300
    self-transfer-enabled: true
    round-amount-minimum: 30000
```

### 4.4 Velocity Tracking

In-memory `ConcurrentHashMap<String, List<Long>>` keyed by sender name. Each entry stores a list of payment timestamps (epoch millis).

On each `checkFraud` call:
1. Get or create timestamp list for sender
2. Remove timestamps older than `velocity-window-seconds`
3. Add current timestamp
4. If list size >= `velocity-count`, flag

No persistence. Data resets on service restart. Acceptable for a demo — not production, but shows the concept.

### 4.5 Logging

When a payment is flagged, log the specific rule that triggered:
```
Payment flagged: paymentId=abc123, rule=VELOCITY, details="3 payments from sender 'Rahul' in 120s"
```

When approved, log all rules that passed:
```
Payment approved: paymentId=def456, amount=15000.0, rulesChecked=4
```

The flag reason is logged only — not stored in DB, not sent via Kafka. Status remains `"FLAGGED"` or `"APPROVED"`.

### 4.6 Files Changed

| File | Change |
|---|---|
| `fraud-detection-service/src/main/java/com/payments/frauddetection/service/FraudCheckService.java` | Multi-rule logic, velocity map, reason logging |
| `fraud-detection-service/src/main/java/com/payments/frauddetection/config/FraudRuleProperties.java` | **New file** — `@ConfigurationProperties` class |
| `fraud-detection-service/src/main/resources/application.yml` | Add `fraud.rules.*` config block |

---

## 5. Test Updates

### 5.1 Fraud Detection Unit Tests

Update `FraudCheckServiceTest.java` to cover:

| Test Case | Setup | Expected |
|---|---|---|
| High amount flags | amount = 60000 | FLAGGED |
| Normal amount passes | amount = 10000 | APPROVED |
| Self-transfer flags | sender = receiver = "Alice" | FLAGGED |
| Round amount flags | amount = 40000 (multiple of 10k, above 30k) | FLAGGED |
| Round amount below minimum passes | amount = 20000 | APPROVED |
| Velocity flags | 3 payments from same sender in quick succession | FLAGGED |
| Velocity passes when spread out | Payments beyond time window | APPROVED |

All unit tests — mock the properties class, no Kafka or DB needed.

### 5.2 Existing Tests

- `PaymentControllerTest.java` — add test for new `/stats` endpoint
- `PaymentServiceTest.java` — add test for `getPaymentStats()`
- All existing tests must continue to pass unchanged

---

## 6. What Does NOT Change

- Docker Compose, Dockerfiles — no changes
- Kubernetes manifests — no changes
- Terraform, Ansible — no changes
- Jenkinsfile — no changes
- Kafka topics — same 3 topics (`payment-created`, `payment-approved`, `payment-flagged`)
- Database schema — no new tables or columns
- `package.json` — no new npm dependencies
- `pom.xml` files — no new maven dependencies (Spring ConfigurationProperties is already included in spring-boot-starter)
