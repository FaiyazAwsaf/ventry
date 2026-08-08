# Ventry

A microservices-based event ticketing platform, built solo as a portfolio project to
demonstrate production-grade distributed-systems patterns — not a toy CRUD app with services
wired together for the sake of it. Customers discover events, book tiered tickets, and pay
through mocked bKash/SSLCommerz gateways; admins manage events and inventory. The booking flow
is a real choreographed Saga across four services, with an append-only event store backing
Booking Service's state.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.0-brightgreen)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-4.3-black)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![Redis](https://img.shields.io/badge/Redis-7-red)

## Why this project exists

Every pattern here earns its place rather than being added for keyword coverage:

- **Saga (choreography)** — booking → payment → confirmation is a distributed transaction
  across Booking, Payment, and Event Service. No 2PC, no orchestrator; each service reacts to
  Kafka events and knows how to compensate (release inventory) on failure.
- **Event Sourcing** — Booking Service never overwrites state; it appends every transition
  (`BOOKING_INITIATED`, `PAYMENT_SUCCESS`, `BOOKING_CONFIRMED`, ...) to an append-only store.
  Current state is a replay, not a row.
- **Atomic inventory control under concurrency** — ticket reservation is a single conditional
  `UPDATE ... WHERE available >= quantity`, proven race-free under real concurrent load (20
  threads, capacity 10 → exactly 10 succeed, zero oversell — see `TicketTierInventoryIT`).
- **CQRS, Circuit Breaker** — architecturally scoped now, built next (see [Roadmap](#roadmap)).

Every non-obvious engineering decision — and there are many, e.g. why the admin-role check is
a one-line inline check instead of Spring Security, why analytics reports "reserved" instead
of "sold," why `@Modifying` repository queries need their own `@Transactional` — is logged with
its reasoning in [`docs/progress.md`](docs/progress.md), not just implemented silently.

## Architecture

```
 Client
   │
   ▼
 API Gateway (Spring Cloud Gateway, :8080)
 — verifies JWTs locally (no per-request call to Auth Service)
 — forwards trusted X-User-Id / X-User-Role headers downstream
   │
   ├──────────────┬──────────────────┬───────────────────┐
   ▼              ▼                  ▼                    ▼
 Auth Service   Event Service     Booking Service      (Eureka Server
 :8081          :8082             :8083                 service registry,
 register/login CRUD + Redis-     Saga trigger +         :8761)
                cached browse +   append-only event
                atomic inventory  store
                   │                  │
                   │                  ▼
                   │            ┌───────────────┐
                   └───────────►│  Kafka topics  │◄────────────┐
                                └───────────────┘              │
                                        │                       │
                                        ▼                       │
                                Payment Service :8084 ──────────┘
                                bKash/SSLCommerz mock gateways
```

See [`docs/architecture.md`](docs/architecture.md) for the full system requirements doc,
Saga/CQRS/Event-Sourcing pattern writeups, and Kafka topic table.

## Services

| Service | Port | Status | Responsibility |
|---|---|---|---|
| `eureka-server` | 8761 | ✅ | Service registry |
| `api-gateway` | 8080 | ✅ | Single entry point, JWT verification, identity forwarding |
| `auth-service` | 8081 | ✅ | Register/login, BCrypt, JWT issuance |
| `event-service` | 8082 | ✅ | Event/tier CRUD, Redis-cached browse, atomic inventory, analytics |
| `booking-service` | 8083 | ✅ (write side) | Saga trigger, event-sourced booking state |
| `payment-service` | 8084 | ✅ | Mocked bKash/SSLCommerz, Kafka-only (no REST surface) |
| QR/Ticket Service | — | ❌ not started | QR generation + entry validation |
| Notification Service | — | ❌ not started | Async email/SMS via Kafka |
| User Service | — | ❌ optional | Stretch goal |

Full endpoint-by-endpoint contract (including what's implemented vs. still a gap) is in
[`docs/api-contracts.md`](docs/api-contracts.md).

## Running locally

**Prerequisites:** Java 21, Maven, Docker.

```bash
# 1. Start shared infra (Postgres, Redis, Kafka)
docker-compose up -d

# 2. Create Kafka topics (not yet automated — see docs/progress.md's known gap:
#    the Kafka container has no persistent volume, so this step repeats after
#    any container/volume recreate)
docker exec ventry-kafka /opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
  --bootstrap-server localhost:9092 --topic booking.initiated
docker exec ventry-kafka /opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
  --bootstrap-server localhost:9092 --topic payment.success
docker exec ventry-kafka /opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
  --bootstrap-server localhost:9092 --topic payment.failed
docker exec ventry-kafka /opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
  --bootstrap-server localhost:9092 --topic booking.confirmed
docker exec ventry-kafka /opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
  --bootstrap-server localhost:9092 --topic booking.cancelled
docker exec ventry-kafka /opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
  --bootstrap-server localhost:9092 --topic refund.processed
docker exec ventry-kafka /opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
  --bootstrap-server localhost:9092 --topic ticket.generated

# 3. Build and install the shared event-contracts module
mvn -pl common -am install

# 4. Start services, in order (each waits ~10s to register with Eureka before the next depends on it)
mvn -pl eureka-server spring-boot:run &
mvn -pl api-gateway spring-boot:run &
mvn -pl auth-service spring-boot:run &
mvn -pl event-service spring-boot:run &
mvn -pl booking-service spring-boot:run &
mvn -pl payment-service spring-boot:run &
```

Eureka dashboard: http://localhost:8761. All traffic goes through the Gateway at
`http://localhost:8080`.

### Try the booking flow end-to-end

```bash
# Register + login as a customer
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"customer@example.com","password":"password123"}'
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"customer@example.com","password":"password123"}' | jq -r .token)

# Browse events (an ADMIN account, seeded directly via SQL, creates events — see
# docs/progress.md's "Registration always creates CUSTOMER" decision)
curl localhost:8080/api/events -H "Authorization: Bearer $TOKEN"

# Book a ticket
curl -X POST localhost:8080/api/bookings -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"<id>","tierId":"<tier-id>","quantity":2}'
```

Watch the services' logs: `booking.initiated` → Payment Service picks it up →
`payment.success`/`payment.failed` → Booking Service confirms or releases inventory.

## Testing

```bash
mvn verify   # unit (Surefire) + integration tests (Failsafe), needs Postgres/Redis/Kafka up
```

Test strategy: JUnit 5 + Mockito for pure logic, `@SpringBootTest` + `@AutoConfigureMockMvc`
against a real local Postgres for integration tests (no H2, to avoid Postgres/H2 dialect
drift — see `docs/progress.md`). Concurrency-sensitive logic (inventory reservation) has a
dedicated load test, not just a happy-path unit test.

## Documentation

- [`docs/architecture.md`](docs/architecture.md) — system requirements, patterns, Kafka topics, NFRs
- [`docs/api-contracts.md`](docs/api-contracts.md) — every REST endpoint and Kafka contract, verified against source, gaps called out explicitly
- [`docs/progress.md`](docs/progress.md) — milestone-by-milestone log with the reasoning behind every non-obvious decision

## Roadmap

Currently past the core write path and the primary booking Saga. Next up: the Cancellation
Saga + QR/Notification services, then the CQRS read side, Event Sourcing replay, and
Resilience4j circuit breakers. Full detail in [`docs/progress.md`](docs/progress.md).

## Known gaps (tracked, not hidden)

A portfolio project should be honest about what's incomplete rather than looking finished when
it isn't:

- No containerization of the Spring Boot services themselves yet (only infra — Postgres/Redis/Kafka)
- No CI pipeline
- No Actuator health endpoints wired yet
- `api-gateway`'s JWT secret has a hardcoded local-dev fallback — fine for `docker-compose up`, not for any real deployment
- No transactional outbox — a crash between Booking Service's DB commit and its Kafka publish can strand a booking `PENDING`

Full list, with the reasoning behind each accepted gap, in
[`docs/progress.md`](docs/progress.md)'s Release Readiness milestone.
