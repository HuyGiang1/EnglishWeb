# EnglishFlow — Architectural Decision Records (ADRs)

**Document Version**: 1.1.0  
**Milestone**: M0 — Discovery & Blueprint (Task M0-T09)  
**Author**: Principal Software Architect & Senior Business Analyst  
**Reviewed By**: AI Technical Lead  
**Status**: Proposal Pending Technical Lead Review  

---

## Index of Architectural Decisions

| ADR ID | Decision Title | Status | Core Justification |
| :--- | :--- | :--- | :--- |
| **ADR-001** | Modular Monolith Architecture | **ACCEPTED** | Maximizes velocity and operational simplicity while preserving module boundaries. |
| **ADR-002** | Backend Framework: Java 21 & Spring Boot 3 (Proposed) | **ACCEPTED** | Modern LTS platform, enterprise stability, first-class virtual threads. |
| **ADR-003** | Database Engine: PostgreSQL 16 & Flyway | **ACCEPTED** | ACID compliance, JSON support, full-text search, versioned schema migrations. |
| **ADR-004** | Backend Build Tool: Maven Wrapper (`mvnw`) | **PROPOSED** | Universal consistency, zero host setup required, declarative XML builds. |
| **ADR-005** | Frontend Framework: Next.js 15 (Proposed) | **ACCEPTED** | High-performance React framework, optimized routing, desktop-first experience. |
| **ADR-006** | Session-Based Authentication with HttpOnly Cookies & CSRF | **ACCEPTED** | Eliminates XSS token theft in localStorage; robust web security model. |
| **ADR-007** | Spaced Repetition Engine: SuperMemo SM-2 with FSRS Abstraction | **PROPOSED** | Simple, mature initial algorithm with clean interface for future FSRS upgrade. |
| **ADR-008** | Pluggable Media Storage Abstraction (`AudioStorageService`) | **PROPOSED** | Local filesystem storage for MVP with seamless transition to S3/MinIO in V1. |
| **ADR-009** | Transactional Event Logging with Attempt-Level Idempotency | **PROPOSED** | ACID guarantees for learning events; business-level deduplication prevents schedule skew. |
| **ADR-010** | Same-Origin API Routing & Reverse Proxy Strategy | **PROPOSED** | Eliminates CORS preflight latency and browser cross-origin cookie complications. |
| **ADR-011** | On-Demand Query-Based Queue & Streak Evaluation (No Worker Queue) | **PROPOSED** | Eliminates operational overhead of Redis/RabbitMQ/Quartz for MVP. |

---

## ADR-001: Modular Monolith Architecture

* **Status**: **ACCEPTED** (Explicit Technical Lead Directive)
* **Context**: EnglishFlow requires multiple distinct capabilities (vocabulary, spaced repetition, dictation, shadowing, quizzes, analytics). The engineering team must balance separation of concerns with operational simplicity.
* **Decision**: Adopt a **Modular Monolith** architecture inside a single Spring Boot application and single PostgreSQL database. Enforce strict internal package boundaries (`com.englishflow.<module>.*`). Cross-module database access is prohibited; modules communicate exclusively via public Application Services or Spring Application Events.
* **Alternatives Considered**:
  * *Microservices Architecture*: Rejected. Excessive operational overhead, network latency, distributed transaction complexity, and premature optimization for an early-stage application.
  * *Unstructured Monolith*: Rejected. Risk of spaghetti code and tangled entity relationships.
* **Consequences**:
  * *Positive*: Single deployment unit, zero distributed transaction overhead, fast local development, straightforward testing via Testcontainers.
  * *Trade-off*: Requires code review diligence to prevent leakage of internal entities across module boundaries.

---

## ADR-002: Backend Language & Framework: Java 21 & Spring Boot 3 (Proposed)

* **Status**: **ACCEPTED** (Confirmed Tech Stack)
* **Context**: The backend requires an enterprise-grade, strongly typed framework with robust security, database integration, and high throughput.
* **Decision**: Standardize on **Java 21 LTS (Target LTS)** and **Spring Boot 3.3.x / 3.4.x (Proposed)**. Exact patch releases will be verified against Java 21 compatibility during project scaffolding. Utilize modern Java 21 features (records, pattern matching, virtual threads where beneficial).
* **Alternatives Considered**:
  * *Node.js / Express or NestJS*: Rejected per agreed technology stack; Java provides stronger type safety, mature JPA, and native database transaction management.
  * *Go / Fiber*: Rejected; Spring ecosystem provides mature, pre-built modules for security, data migration, and bean validation.
* **Consequences**:
  * *Positive*: Rock-solid reliability, long-term LTS support, first-class Spring Security and Spring Data JPA ecosystems.
  * *Trade-off*: Higher memory footprint than Go (~250–350 MB RAM baseline), acceptable for modern VPS hosting.

---

## ADR-003: Database Engine: PostgreSQL 16 & Flyway Migrations

* **Status**: **ACCEPTED** (Confirmed Tech Stack)
* **Context**: EnglishFlow requires ACID compliance for SRS intervals, full-text search for vocabulary lookups, and deterministic schema versioning.
* **Decision**: Use **PostgreSQL 16** as the sole primary database. Manage all schema changes through **Flyway** migration scripts (`V1__...sql`).
* **Alternatives Considered**:
  * *MongoDB / Document DB*: Rejected. Relational modeling is essential for users, vocabulary, collections, and immutable activity logs.
  * *MySQL*: Rejected. PostgreSQL offers superior Full-Text Search (`tsvector`, `tsquery`), native UUID generation, and rich timezone support.
* **Consequences**:
  * *Positive*: Strict data integrity, foreign key cascades, high-performance indexing, and zero-drift database migrations across dev, test, and prod.
  * *Trade-off*: Requires disciplined SQL migration authoring for schema changes.

---

## ADR-004: Backend Build Tool: Maven with Maven Wrapper (`mvnw`)

* **Status**: **PROPOSED** (Awaiting Final Technical Lead Confirmation)
* **Context**: The environment audit (Task M0-T01) confirmed that neither Maven nor Gradle is installed globally on the host. A self-bootstrapping build tool is required.
* **Decision**: Use **Maven with the official Maven Wrapper** (`mvnw` / `mvnw.cmd`).
* **Alternatives Considered**:
  * *Gradle*: Rejected. While Gradle offers flexible DSLs, it incurs background daemon overhead and complexity on Windows workstations. Maven XML is declarative, universally understood, and stable.
* **Consequences**:
  * *Positive*: Zero developer environment prerequisites; consistent builds across Windows, Linux, and CI/CD pipelines.
  * *Trade-off*: Slightly slower incremental compilation than Gradle on massive enterprise codebases (negligible for EnglishFlow).

---

## ADR-005: Frontend Framework: Next.js 15 (Proposed)

* **Status**: **ACCEPTED** (Confirmed Tech Stack)
* **Context**: The frontend demands a desktop-first, highly responsive, accessible UI supporting distinct shells (Workspace vs. Focus Mode) and seamless routing.
* **Decision**: Standardize on **Next.js 15 (App Router, Proposed)** with **TypeScript**, **Tailwind CSS**, and **shadcn/ui**. Exact patch releases will be verified during frontend scaffolding.
* **Alternatives Considered**:
  * *Vite Single Page Application (SPA)*: Rejected. Next.js App Router provides superior layout nesting (`route groups`), built-in image/font optimization, and seamless reverse-proxy capabilities.
* **Consequences**:
  * *Positive*: Unified TypeScript end-to-end typing, rich component ecosystem (shadcn/ui, Radix UI primitives), fast client-side navigation.
  * *Trade-off*: App Router mental model requires clear separation between Server and Client Components.

---

## ADR-006: Session-Based Authentication with HttpOnly Cookies & CSRF

* **Status**: **ACCEPTED** (Explicit Technical Lead Directive)
* **Context**: The application requires secure user authentication. Many web apps erroneously store JWTs in browser `localStorage`, exposing them to cross-site scripting (XSS) credential theft.
* **Decision**: Implement **Spring Security stateful session authentication** using an `HttpOnly`, `Secure`, `SameSite=Lax` cookie (`JSESSIONID`). Protect state-changing requests using the synchronized `X-XSRF-TOKEN` cookie-to-header pattern. Ban all tokens in `localStorage`.
* **Alternatives Considered**:
  * *Stateless JWT in localStorage*: Rejected. Highly vulnerable to XSS token exfiltration; complex token revocation.
  * *JWT in HttpOnly Cookie*: Rejected. Adds unnecessary token signing and rotation overhead to a modular monolith when server sessions in PostgreSQL or memory are simpler and instantly revocable.
* **Consequences**:
  * *Positive*: Maximum web security, immune to JavaScript XSS token theft, instant session revocation upon logout or password change.
  * *Important Note on XSS Risk*: While HttpOnly cookies eliminate direct JavaScript access and prevent token exfiltration, they do not eliminate XSS risk entirely; cross-site scripting can still trigger unauthorized session-riding requests. Synchronized CSRF (`X-XSRF-TOKEN`), strict input sanitization, and Content Security Policy (CSP) headers are mandatory.
  * *Trade-off*: Requires state tracking on the backend (negligible memory footprint for hundreds of concurrent sessions).

---

## ADR-007: Spaced Repetition Engine: SuperMemo SM-2 with FSRS Abstraction

* **Status**: **PROPOSED** (Awaiting Final Technical Lead Confirmation)
* **Context**: Spaced repetition review is the core learning engine. While modern algorithms like FSRS (Free Spaced Repetition Scheduler) offer higher accuracy, they require complex parameter tuning and extensive review history.
* **Decision**: Implement the classic, battle-tested **SuperMemo SM-2 algorithm** for the MVP. Encapsulate the algorithm behind a clean `SpacedRepetitionScheduler` interface to enable a seamless migration to FSRS in a future release.
* **Alternatives Considered**:
  * *Direct FSRS Implementation*: Rejected for MVP. Increases implementation complexity and cognitive load before baseline product-market fit.
  * *Fixed Leitner Box System*: Rejected. Too crude; doesn't adapt to item difficulty.
* **Consequences**:
  * *Positive*: Predictable mathematical behavior, widely understood by learners, zero machine learning training required.
  * *Trade-off*: SM-2 can result in ease factor deflation ("ease hell") over long periods, which FSRS solves in later milestones.

---

## ADR-008: Pluggable Media Storage Abstraction (`AudioStorageService`)

* **Status**: **PROPOSED** (Awaiting Final Technical Lead Confirmation)
* **Context**: Dictation and Shadowing modules handle audio files. Introducing MinIO or AWS S3 in the initial vertical slice introduces unnecessary infrastructure overhead.
* **Decision**: Create an `AudioStorageService` abstraction. Implement a local filesystem provider (`LocalStorageService`) for the MVP. Prepare an S3-compatible provider (`S3StorageService`) for future cloud deployment.
* **Alternatives Considered**:
  * *Mandatory MinIO in MVP*: Rejected per Technical Lead guidance. Adds container complexity and disk overhead for early local development.
  * *Direct File I/O scattered in Controllers*: Rejected. Tight coupling would make cloud migration painful.
* **Consequences**:
  * *Positive*: Zero extra containers needed for local dev; seamless one-line configuration switch to S3/MinIO later.
  * *Trade-off*: Local filesystem requires persistent volume mounts when deploying in Docker.

---

## ADR-009: Transactional Learning Activity Logging with Attempt Idempotency

* **Status**: **PROPOSED** (Awaiting Final Technical Lead Confirmation)
* **Context**: Learning activity events must be recorded accurately to populate the heatmap and streak counters without being vulnerable to duplicate network submissions or false credit.
* **Decision**: Every learning submission generates a client-side `attempt_id` (UUIDv4). Idempotency is enforced at the business-operation level on `srs_review_history`, `dictation_attempts`, `shadowing_takes`, and `quiz_attempts` (each with `UNIQUE (attempt_id)`), not solely on event logs.
  * *Replay Behavior*: Replayed requests with the identical `attempt_id` and payload bypass business execution, skipping SM-2 interval progression and activity logging, and return the cached outcome with header `Idempotent-Replay: true`.
  * *Conflict Behavior*: Reusing the same `attempt_id` with a mismatched payload returns `HTTP 409 Conflict`.
  * *Legitimate Future Attempts*: A subsequent study session generates a new, unique `attempt_id`.
* **Alternatives Considered**:
  * *Asynchronous Event Queue (Kafka / RabbitMQ)*: Rejected. Over-engineered; risks event loss or eventual consistency bugs for immediate streak updates.
  * *Deduplication solely on `(session_id, item_id)`*: Rejected. Would incorrectly block a user from legitimately reviewing the same card again if graded "Again" in the same session.
* **Consequences**:
  * *Positive*: 100% ACID consistency, zero duplicate activity counts, immediate UI reflection.
  * *Trade-off*: Requires database unique constraint checking and transaction catch-and-replay logic.

---

## ADR-010: Same-Origin API Routing & Reverse Proxy Strategy

* **Status**: **PROPOSED** (Awaiting Final Technical Lead Confirmation)
* **Context**: Next.js runs on port 3000 and Spring Boot runs on port 8080 during development. Browser cross-origin cookie policies (`SameSite`, CORS) can cause subtle session-dropping bugs when origins differ.
* **Decision**: Adopt a **same-origin routing strategy**. In development, Next.js rewrites `/api/*` requests to `http://localhost:8080/api/*`. In production, Caddy or Nginx routes `/api/*` to Spring Boot on the same public domain.
* **Alternatives Considered**:
  * *Cross-Origin Resource Sharing (CORS)*: Rejected. Requires complex CORS pre-flight configurations, `Access-Control-Allow-Credentials: true`, and third-party cookie handling across different ports.
* **Consequences**:
  * *Positive*: Zero CORS configuration headaches, zero pre-flight latency, 100% reliable session cookie transmission.
  * *Trade-off*: Developers must be mindful of reverse-proxy routing rules during deployment.

---

## ADR-011: On-Demand Query-Based Queue & Streak Evaluation (No Worker Queue)

* **Status**: **PROPOSED** (Awaiting Final Technical Lead Confirmation)
* **Context**: Many applications implement scheduled background jobs (e.g., cron workers at midnight) to recalculate streaks and populate daily review queues.
* **Decision**: Calculate due review items and streak states **on-demand at query time** using indexed SQL queries against authoritative tables. Do not deploy a background worker queue (Quartz / Redis) for the MVP.
* **Alternatives Considered**:
  * *Midnight Batch Worker*: Rejected. Unreliable across multiple timezones; users in 24 different timezones would require constant scheduled triggers.
* **Consequences**:
  * *Positive*: Eliminates background worker infrastructure, eliminates timezone scheduler bugs, ensures real-time accuracy.
  * *Trade-off*: Heatmap queries execute SQL aggregations, mitigated by sub-15ms indexing over annual records.
