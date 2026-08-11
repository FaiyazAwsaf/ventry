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
- **CQRS, Circuit Breaker** — architecturally scoped now, built next (see
  [`docs/progress.md`](docs/progress.md) for current status).


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
                   └───────────►│  Kafka topics  │◄────────────┬──────────────┐
                                └───────────────┘              │              │
                                        │                       │              │
                                        ▼                       │              │
                                Payment Service :8084 ──────────┘              │
                                bKash/SSLCommerz mock gateways                 │
                                                                                │
                                QR/Ticket Service :8085 ───────────────────────┘
                                consumes booking.confirmed, generates QR,
                                publishes ticket.generated
```

See [`docs/architecture.md`](docs/architecture.md) for the full system requirements doc,
Saga/CQRS/Event-Sourcing pattern writeups, and Kafka topic table.

Not pictured above: `notification-service` (`:8086`) also consumes from the same Kafka topics
(`payment.failed`, `booking.confirmed`, `booking.cancelled`, `refund.processed`,
`ticket.generated`). It's a pure sink with no REST surface and nothing calls into it, so it
doesn't fit cleanly into the request-flow diagram above.

## Services

| Service | Port | Status | Responsibility |
|---|---|---|---|
| `eureka-server` | 8761 | ✅ | Service registry |
| `api-gateway` | 8080 | ✅ | Single entry point, JWT verification, identity forwarding |
| `auth-service` | 8081 | ✅ | Register/login, BCrypt, JWT issuance |
| `event-service` | 8082 | ✅ | Event/tier CRUD, Redis-cached browse, atomic inventory, analytics |
| `booking-service` | 8083 | ✅ (write side) | Saga trigger, event-sourced booking state |
| `payment-service` | 8084 | ✅ | Mocked bKash/SSLCommerz, Kafka-only (no REST surface) |
| `qr-ticket-service` | 8085 | ✅ | QR generation, entry-gate scan validation, customer ticket fetch |
| `notification-service` | 8086 | ✅ | Mocked email/SMS (logged, not delivered) on every booking/payment/refund/ticket event, Kafka-only (no REST surface) |

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

# 3. Create each service's Postgres schema (Hibernate's ddl-auto: update creates
#    tables within a schema, but never the schema itself - a one-time step per
#    service, same category as the Kafka topics above)
docker exec ventry-postgres psql -U ventry -d ventry -c "CREATE SCHEMA IF NOT EXISTS auth;"
docker exec ventry-postgres psql -U ventry -d ventry -c "CREATE SCHEMA IF NOT EXISTS event;"
docker exec ventry-postgres psql -U ventry -d ventry -c "CREATE SCHEMA IF NOT EXISTS booking;"
docker exec ventry-postgres psql -U ventry -d ventry -c "CREATE SCHEMA IF NOT EXISTS payment;"
docker exec ventry-postgres psql -U ventry -d ventry -c "CREATE SCHEMA IF NOT EXISTS ticket;"
docker exec ventry-postgres psql -U ventry -d ventry -c "CREATE SCHEMA IF NOT EXISTS notification;"

# 4. Build and install the shared event-contracts module
mvn -pl common -am install

# 5. Start services, in order (each waits ~10s to register with Eureka before the next depends on it)
mvn -pl eureka-server spring-boot:run &
mvn -pl api-gateway spring-boot:run &
mvn -pl auth-service spring-boot:run &
mvn -pl event-service spring-boot:run &
mvn -pl booking-service spring-boot:run &
mvn -pl payment-service spring-boot:run &
mvn -pl qr-ticket-service spring-boot:run &
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
`payment.success`/`payment.failed` → Booking Service confirms or releases inventory →
QR/Ticket Service generates a ticket on `booking.confirmed`.

```bash
# Fetch the QR image for a confirmed booking - the Gateway derives X-User-Id from
# $TOKEN itself (never client-supplied), so this only succeeds for the booking's
# own customer
curl localhost:8080/api/tickets/<booking-id>/qr -H "Authorization: Bearer $TOKEN" -o ticket.png

# Validate a scanned ticket at the gate (ADMIN only) - qrContent is whatever a real
# scanner reads back out of the QR image above
curl -X POST localhost:8080/api/tickets/validate -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"qrContent":"<content encoded in the scanned QR>"}'

# Cancel a confirmed booking (must be CONFIRMED - the compensating Saga's trigger)
curl -X POST localhost:8080/api/bookings/<booking-id>/cancel -H "Authorization: Bearer $TOKEN"
```

Watch the logs again: `booking.cancelled` → Payment Service refunds → `refund.processed` →
Booking Service restores inventory and flips the booking to `CANCELLED`. The response to the
cancel call itself only reflects `CANCELLATION_PENDING` — completion happens asynchronously.

## Testing

```bash
mvn verify   # unit (Surefire) + integration tests (Failsafe), needs Postgres/Redis/Kafka up
```

Test strategy: JUnit 5 + Mockito for pure logic, `@SpringBootTest` + `@AutoConfigureMockMvc`
against a real local Postgres for integration tests (no H2, to avoid Postgres/H2 dialect
drift — see `docs/progress.md`). Concurrency-sensitive logic (inventory reservation) has a
dedicated load test, not just a happy-path unit test.

