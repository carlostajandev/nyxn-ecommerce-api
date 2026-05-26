# NYXN E-Commerce Platform

Monorepo containing the full NYXN e-commerce technical challenge — Java 21 / Spring Boot 3 backend and NestJS notification microservice.

```
nyxn-ecommerce/
├── backend/                  ← Spring Boot API (Java 21)
│   ├── src/
│   ├── Dockerfile
│   └── pom.xml
│
├── notification-service/     ← NestJS microservice (Node 20 / TypeScript)
│   ├── src/
│   ├── Dockerfile
│   └── package.json
│
├── docker-compose.yml        ← full local stack (PostgreSQL, Redis, Pub/Sub emulator, both services)
├── .github/workflows/        ← separate CI pipelines per sub-project
└── README.md
```

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Client (HTTP)                               │
└────────────────────────┬────────────────────────────────────────────┘
                         │
              ┌──────────▼──────────┐
              │   Spring Boot API   │  :8080
              │   (backend/)        │
              │                     │
              │  Hexagonal Arch.    │
              │  Domain → Ports     │
              │  → Adapters         │
              └──┬──────┬──────┬───┘
                 │      │      │
         ┌───────▼─┐ ┌──▼──┐ ┌▼────────────┐
         │Postgres │ │Redis│ │ GCP Pub/Sub │
         │  :5432  │ │:6379│ │   :8085     │
         └─────────┘ └─────┘ └──────┬──────┘
                                     │ product-events
              ┌──────────────────────▼──────────┐
              │  NestJS Notification Service     │  :3000
              │  (notification-service/)         │
              │                                  │
              │  Pub/Sub Subscriber + DLQ        │
              │  Claude AI Agent (Section 6B)    │
              └──────────────────────────────────┘
```

---

## Sections Implemented

| Section | Topic | Tech |
|---------|-------|------|
| 1.1 | Products REST API + Hexagonal Architecture | Spring Boot, JPA, Flyway |
| 1.2 | SOLID refactor of legacy OrderService | Java 21 records, design patterns |
| 2 | Orders hexagonal module + Testcontainers | Spring Boot, PostgreSQL, Redis |
| 3 | Cyber Day stock reservation | Redis Lua, optimistic locking, Spring Retry |
| 4A | SQL analytics with window functions | PostgreSQL RANK(), LAG(), views |
| 4B | Redis enterprise cache patterns | Per-cache TTL, jitter, cache warming |
| 5 | Docker + GCP Pub/Sub | Multi-stage Dockerfile, Pub/Sub emulator, subscriber |
| 6A | NestJS notification microservice | NestJS, TypeScript, Pub/Sub, DLQ |
| 6B | Claude AI agent integration | Anthropic SDK, structured output, tool-use ready |

---

## Local Setup

### Prerequisites
- Docker + Docker Compose
- Java 21 (for backend development without Docker)
- Node 20 (for notification-service development without Docker)

### 1. Start the full stack

```bash
docker-compose up -d
```

This starts: PostgreSQL, Redis, Pub/Sub emulator, Pub/Sub init (topics/subscriptions), Spring Boot API, and NestJS notification service.

Services:
| Service | URL |
|---------|-----|
| Spring Boot API | http://localhost:8080 |
| Swagger UI (backend) | http://localhost:8080/swagger-ui.html |
| NestJS notification | http://localhost:3000 |
| Swagger UI (notifications) | http://localhost:3000/api-docs |
| Health (backend) | http://localhost:8080/actuator/health |
| Health (notifications) | http://localhost:3000/health |

### 2. Backend only (without Docker)

```bash
# Start infrastructure
docker-compose up -d postgres redis pubsub-emulator pubsub-init

# Run backend
cd backend
mvn spring-boot:run
```

### 3. Notification service only (without Docker)

```bash
# Install dependencies
cd notification-service
npm install

# Copy and configure env
cp .env.example .env.local
# Edit .env.local — set ANTHROPIC_API_KEY for Claude agent

# Start in dev mode (hot reload)
npm run start:dev
```

---

## Environment Variables

### backend/

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/nyxn_ecommerce` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `nyxn` | Database user |
| `DB_PASSWORD` | `nyxn` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `PUBSUB_EMULATOR_HOST` | *(unset → real GCP)* | Pub/Sub emulator for local dev |
| `SPRING_CLOUD_GCP_PROJECT_ID` | `local-project` | GCP project ID |
| `JWT_ISSUER_URI` | `https://accounts.google.com` | OAuth2 JWT issuer |

### notification-service/

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `3000` | HTTP server port |
| `GCP_PROJECT_ID` | `local-project` | GCP project ID |
| `PUBSUB_EMULATOR_HOST` | *(unset → real GCP)* | Pub/Sub emulator for local dev |
| `PUBSUB_SUBSCRIPTION_PRODUCT_EVENTS` | `product-events-subscription` | Subscription name |
| `ANTHROPIC_API_KEY` | *(required for Claude agent)* | Anthropic API key |

---

## Running Tests

### Backend unit tests (no Docker needed)

```bash
cd backend
mvn test -Dtest="ProductTest,CreateProductUseCaseTest,PlaceOrderServiceTest,StockReservationServiceTest,ConcurrentStockReservationTest"
```

### Backend integration tests (Docker required for Testcontainers)

```bash
cd backend
mvn verify
```

### Notification service tests

```bash
cd notification-service
npm install
npm test
```

---

## Key Design Decisions

### Hexagonal Architecture (backend)

The domain layer has zero imports from Spring, JPA, or any framework. All external dependencies (database, cache, message broker) are represented as interfaces (ports) in `domain/ports/`. Adapters in `infrastructure/` implement those ports.

```
domain/              ← pure Java, no framework imports
  model/
  ports/
    in/              ← use cases (inbound ports)
    out/             ← repository, event publisher (outbound ports)
application/         ← orchestrates domain, imports only domain
infrastructure/      ← implements outbound ports (JPA, Redis, Pub/Sub)
interfaces/          ← REST controllers, input validation
```

### Redis Cache Strategy

| Cache | TTL | Jitter | Eviction trigger |
|-------|-----|--------|-----------------|
| `products` | 45 min | ±5 min | Product update/delete |
| `analytics:top-products` | 15 min | ±90 s | Natural expiry |
| `analytics:revenue-trend` | 15 min | ±90 s | Natural expiry |
| `analytics:low-stock` | 15 min | ±90 s | Stock reserve/release |

**Stampede prevention:** Each cache entry's TTL includes random jitter (`ThreadLocalRandom`) computed independently per cache at startup. Entries for different caches expire at different times, spreading the DB query load.

**Cache warming:** `CacheWarmupService` pre-populates all analytics caches on `ApplicationReadyEvent` — before load ramps up on Cyber Day.

### Cyber Day Stock Reservation (Section 3)

```
Request → Redis Lua DECREMENT_IF_SUFFICIENT (atomic, O(1))
        → PostgreSQL versioned UPDATE (optimistic locking, max 3 retries)
        → Compensating Redis INCREMENT on DB failure
```

Redis acts as a fast gate: 70 requests for a product with 30 units are rejected at Redis without touching the DB. The 30 successful requests reach PostgreSQL with optimistic locking + exponential backoff + jitter.

### GCP Pub/Sub DLQ

Topics and subscriptions are configured with `maxDeliveryAttempts=5`. After 5 consecutive nacks (from either the Spring Boot subscriber or the NestJS subscriber), GCP automatically routes the message to the dead-letter topic (`*-dlq`). This prevents poison-pill messages from blocking subscriptions indefinitely.

### Claude Agent (Section 6B)

`ClaudeAgentService` uses `claude-haiku-4-5` — chosen for speed and cost at notification scale. The system prompt constrains Claude to return a JSON object `{ subject, body, channel }` — structured output is more reliable for downstream consumers than parsing free-form prose.

The endpoint `POST /agent/smart-notification` accepts a product event + optional audience context and returns AI-generated notification copy ready for delivery.

---

## CI/CD

Two independent GitHub Actions pipelines triggered by monorepo path filters:

- **`backend-ci.yml`** — triggers on `backend/**` changes: Spotless format check → compile → unit tests → integration tests (Testcontainers) → Docker build smoke test → Checkstyle.
- **`notification-ci.yml`** — triggers on `notification-service/**` changes: lint → TypeScript compile → unit tests → coverage report → Docker build smoke test.

---

## Scaling Strategy

| Bottleneck | Strategy |
|------------|----------|
| Product catalog reads | Redis Cache Aside (45 min TTL + jitter). Read replicas for overflow. |
| Flash sale stock reservation | Redis atomic Lua gate filters 99 % of excess requests before they touch PostgreSQL. |
| Analytics queries | PostgreSQL views + Redis (15 min TTL). Acceptable staleness for dashboards. |
| Notification throughput | NestJS subscribes to Pub/Sub with `flowControl.maxMessages=10`. Scale horizontally — each instance subscribes independently. |
| Claude API latency | Haiku model (~200 ms P99). Not on the hot path — called only for premium notification generation, not for every Pub/Sub message. |
