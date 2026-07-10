

##  System Requirements Document
## Ventry — Event Ticketing Platform
Version: 1.0 | Course: Software Design & Architecture (SDA) | Team Size: 4 | Timeline: 2
## Weeks

## 1. Project Brief
Ventry is a microservice-based online event ticketing platform that allows customers to discover,
book, and manage tickets for concerts and sports events. Admins can create and manage
events with tiered pricing. The platform is designed to industry standards, demonstrating
real-world microservice architecture patterns including Saga, CQRS, and Event Sourcing.
## Primary Goals:
● Build a scalable, loosely coupled ticketing system using microservices
● Demonstrate key SDA patterns: Saga, CQRS, Event Sourcing, Circuit Breaker
● Simulate real Bangladeshi payment gateways (bKash, SSLCommerz)
● Deliver a working demo with report, architecture diagrams, and presentation

## 2. Stakeholders & User Roles
## Role Description
Customer Browses events, books/cancels tickets, receives notifications
Admin Creates/manages events, sets ticket tiers & pricing, manages inventory
System Automated processes — QR generation, payment callbacks,
notifications

## 3. Core Features
## 3.1 Authentication & Authorization

● Customer and Admin registration and login
● JWT-based stateless authentication
● Role-based access control (CUSTOMER, ADMIN)
● Token validation at the API Gateway level
3.2 Event Management (Admin)
● Create, update, and delete events (name, description, date, venue, banner)
● Define multiple ticket tiers per event (e.g., Gold, Silver, Bronze)
● Set price, capacity, and availability per tier
● View event analytics (tickets sold, revenue per tier)
3.3 Ticket Booking (Customer)
● Browse and search available events
● Select event, tier, and quantity
● Reserve tickets (soft lock inventory during payment)
● Confirm booking after successful payment
● View booking history
## 3.4 Payment
● Mocked bKash and SSLCommerz payment gateway integration
● Unified payment interface abstracting both providers
● Handle payment success, failure, and timeout scenarios
● Trigger refund flow on cancellation
## 3.5 Ticket Cancellation & Refunds
● Customer can cancel a confirmed booking
● Initiates compensating transaction via Saga (reverse payment, restore inventory)
● Refund status tracked and communicated via notification
## 3.6 Notifications
● Email and SMS notifications for:
○ Booking confirmation
○ Payment success/failure
○ Cancellation & refund status
○ Event reminders
● Fully async — triggered via Kafka events, no direct service calls
3.7 QR Code & Ticket Generation

● Generate a unique QR code per confirmed ticket
● QR encodes booking ID, event ID, tier, and customer ID
● Ticket delivered via notification and accessible via customer dashboard
● QR can be validated at entry (scan endpoint)

## 4. Architecture Overview
## 4.1 Architectural Style
Microservices Architecture — each service is independently deployable, owns its data, and
communicates via defined contracts.
## 4.2 Key Patterns Used
## Pattern Purpose Applied In
## Saga
(Choreography)
Distributed transaction
management across services
Booking → Payment → QR →
Notification flow
CQRS Separate read and write models for
performance
## Booking Service, Event Service
Event Sourcing Store state as a sequence of
events, not current state
## Booking Service (append-only
event log)
Circuit Breaker Prevent cascading failures on sync
calls
## Gateway, Booking → Event
Service calls
API Gateway
## Pattern
Single entry point for all clients Spring Cloud Gateway
Service Discovery Dynamic service location without
hardcoded URLs
## Eureka Server
Database per
## Service
Data isolation and service
independence
All services
## 4.3 Communication Strategy
## Type Protocol Used For
Synchronous REST over
## HTTP
Auth checks, event queries, user profile reads

## Asynchronou
s
Apache Kafka Booking confirmed, payment processed, QR generated,
notifications
Rule of thumb: If a response is needed immediately → REST. If it triggers a downstream
process → Kafka.
## 4.4 Services
# Service Tech DB
1 Auth Service Spring Boot PostgreSQL
2 Event Service Spring Boot PostgreSQL + Redis (cache)
3 Booking Service Spring Boot PostgreSQL (+ Event Store
schema)
4 Payment Service Spring Boot PostgreSQL
5 Notification Service Spring Boot PostgreSQL (log only)
6 QR/Ticket Service Spring Boot PostgreSQL
7 User Service (if time permits) Spring Boot PostgreSQL
## 4.5 Infrastructure Components
## Component Tool
API Gateway Spring Cloud Gateway
## Service Registry Netflix Eureka
## Message Broker Apache Kafka
Fault Tolerance Resilience4j (Circuit Breaker, Retry, Timeout)
Caching Redis (Event Service)
## Containerization Docker + Docker Compose (bonus)

- High-Level Design (HLD)
## 5.1 System Topology

## [ Client — Browser / Mobile ]
## │
## ▼
## ┌─────────────────────┐
│    API Gateway       │  ← Spring Cloud Gateway
│  (Auth Validation,   │    (JWT check, routing,
│  Routing, Rate Limit)│     rate limiting)
## └────────┬────────────┘
## │
## ┌──────────────────┼──────────────────┐
## ▼                  ▼                   ▼
[Auth Service]    [Event Service]     [Booking Service]
(Redis Cache)       (CQRS + Event Sourcing)
## │
## ┌────────┴────────┐
## ▼                 ▼
[Payment Service]  [Kafka Event Bus]
## │
## ┌─────────────┼──────────────┐
## ▼             ▼              ▼
[QR/Ticket Service] [Notification]  [User Service]

5.2 Core Flow — Ticket Booking (Saga)
This is the most critical flow in the system, implemented as a Choreography-based Saga:
Customer → API Gateway → Booking Service
## │
├─ 1. Validate event & tier availability (REST → Event Service)
├─ 2. Reserve inventory (soft lock)
├─ 3. Create booking in PENDING state
├─ 4. Publish → [booking.initiated] on Kafka
## │
## └─ Payment Service (consumes [booking.initiated])
├─ 5. Process payment via bKash/SSLCommerz mock
├─ [SUCCESS] → Publish [payment.success]
└─ [FAILURE] → Publish [payment.failed]

[payment.success] consumed by:
├─ Booking Service → Update booking to CONFIRMED
## │                  → Publish [booking.confirmed]
└─ [booking.confirmed] consumed by:
├─ QR Service → Generate QR → Publish [ticket.generated]

└─ Notification Service → Send confirmation email/SMS

[payment.failed] consumed by:
├─ Booking Service → Update booking to FAILED
│                  → Release inventory (compensating transaction)
└─ Notification Service → Send failure notification

5.3 Core Flow — Ticket Cancellation (Compensating Saga)
Customer → API Gateway → Booking Service
├─ 1. Validate booking ownership & cancellation eligibility
├─ 2. Update booking state to CANCELLATION_PENDING
## ├─ 3. Publish → [booking.cancelled]
## │
## └─ Payment Service (consumes [booking.cancelled])
├─ 4. Initiate refund via mock gateway
## ├─ Publish [refund.processed]
## │
## └─ Booking Service (consumes [refund.processed])
├─ 5. Update booking to CANCELLED
├─ 6. Restore inventory in Event Service (REST)
## └─ 7. Publish [cancellation.confirmed]
└─ Notification Service → Send refund notification

## 5.4 Event Sourcing — Booking Service
Instead of storing only the current state, the Booking Service stores every state transition as an
immutable event:
Event Store (append-only PostgreSQL table)
## ┌────────────────────────────────────────────────────────┐
│ event_id │ booking_id │ event_type           │ payload │ timestamp │
## │──────────│────────────│──────────────────────│─────────│──
## ─────────│
## │ 1        │ B-001      │ BOOKING_INITIATED     │ {...}   │ 10:00:00  │
## │ 2        │ B-001      │ PAYMENT_PROCESSING    │ {...}   │ 10:00:05  │
## │ 3        │ B-001      │ PAYMENT_SUCCESS       │ {...}   │ 10:00:08  │
## │ 4        │ B-001      │ BOOKING_CONFIRMED     │ {...}   │ 10:00:09  │
## └────────────────────────────────────────────────────────┘
Current state = replaying all events for a booking_id

5.5 CQRS — Booking & Event Service

Write Side (Commands)          Read Side (Queries)
## ──────────────────────         ──────────────────────
POST /bookings                 GET /bookings/{id}
PUT /bookings/cancel           GET /bookings/history
GET /events (optimized read model)

Command → Event Store          Query → Read-optimized DB view
→ Kafka event                  (updated via event projections)

## 5.6 Kafka Topics
## Topic Published By Consumed By
booking.initi
ated

## Booking Service Payment Service
payment.succe
ss

## Payment
## Service
## Booking Service
payment.faile
d

## Payment
## Service
## Booking Service, Notification Service
booking.confi
rmed

Booking Service QR Service, Notification Service
booking.cance
lled

## Booking Service Payment Service, Notification Service
refund.proces
sed

## Payment
## Service
## Booking Service, Notification Service
ticket.genera
ted

QR Service Notification Service

- Non-Functional Requirements
## Concern Requirement
Scalability Services independently scalable via multiple instances
Fault Tolerance Circuit Breaker on all sync REST calls between services

Consistency Eventual consistency via Saga + Kafka (not ACID across services)
Security JWT on all protected endpoints, role-based access enforced at
## Gateway
Observability Spring Boot Actuator health endpoints on all services
Data Isolation No service directly accesses another service's database

## 7. Tech Stack Summary
## Layer Technology
## Language Java 17+
## Framework Spring Boot 3.x
API Gateway Spring Cloud Gateway
Service Discovery Netflix Eureka (Spring Cloud Netflix)
## Messaging Apache Kafka
Database PostgreSQL (per service)
## Caching Redis
## Fault Tolerance Resilience4j
Auth JWT (jjwt library)
QR Generation ZXing (Google)
## Containerization Docker + Docker Compose (bonus)
Build Tool Maven or Gradle

## 8. Team Responsibilities
## Member Primary Ownership
Member 1 Auth Service, API Gateway, Eureka Server, shared Kafka setup

Member 2 Event Service (CQRS read side, Redis cache), QR/Ticket Service
Member 3 Booking Service — Saga orchestration, CQRS write side, Event Sourcing
## (heaviest)
Member 4 Payment Service (bKash + SSLCommerz mock), Notification Service
Day 1 team task: Set up shared Kafka, Eureka, and PostgreSQL instances. Define
all Kafka topic names and REST API contracts before anyone writes business logic.

- Suggested 2-Week Timeline
## Days Milestone
Day 1–2 Project setup, shared infra (Kafka, Eureka, PostgreSQL), API contracts
defined
Day 3–5 Auth Service, Event Service, basic Booking Service (write side)
Day 6–8 Payment Service, Saga flow (booking → payment), Booking confirmed flow
Day 9–10 QR Service, Notification Service, Cancellation Saga
Day 11–12 CQRS read models, Event Sourcing, Circuit Breaker integration
Day 13 Integration testing, end-to-end flow validation
Day 14 Report, diagrams, demo prep, presentation

## 10. Deliverables Checklist
● Architecture diagram (HLD + service communication diagram)
● Kafka topic flow diagram
● Saga flow diagrams (booking + cancellation)
● Working demo (end-to-end booking flow)
● Source code (GitHub repository, one repo per service or monorepo with modules)
● System Requirements Document (this document)
● Final report
● Presentation slides


Document prepared for SDA Lab — Ventry Ticketing Platform. Subject to revision as
implementation progresses.
