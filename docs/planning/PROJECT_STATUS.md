# EnglishFlow — Project Status & Delivery Roadmap
 
**Document Version**: 1.6.0
**Effective Date**: Milestone M1 (Task M1-T05)
**Status**: Milestone M1-T05 Completed (Vocabulary Management Full-Stack Vertical Slice Verified on macOS)
**Repository Path**: `/Users/GiangNguyenHuy/EnglishWeb`
**Git Branch**: `main` (Synchronized with `origin/main`)
 
---
 
## 1. Milestone Audit & Task History
 
### Milestone M0: Discovery & Blueprint Audit History
 
| Task ID | Task Description | Technical Lead Decision | Primary Artifacts Produced / Revised |
| :--- | :--- | :--- | :--- |
| **M0-T01** | Workspace Discovery & Environment Readiness Audit | APPROVED | Environment audit, toolchain verification (Java 21, Node 24, Git, Docker). |
| **M0-T02** | Minimalist UI/UX Blueprint & Design System | PROVISIONALLY ACCEPTED | Design tokens, color system, typography, minimalist monochrome guidelines. |
| **M0-T03** | UI/UX Specification Verification & Correction | ACCEPTED | Synchronized `UI_UX.md`, `DESIGN_TOKENS.md`, `SCREEN_INVENTORY.md`, `HEATMAP_SPEC.md`. |
| **M0-T04** | Final UI/UX Alignment & Architecture Handoff | ACCEPTED | Aligned session-based auth cookie standards, keyboard guards, delivery roadmap. |
| **M0-T05** | Product Requirements & System Architecture Blueprint | ACCEPTED | `docs/product/PRD.md`, `docs/architecture/SYSTEM.md`, `ARCHITECTURE_DECISIONS.md`. |
| **M0-T06** | PostgreSQL Database Blueprint & Data Integrity Design | ACCEPTED | `DATABASE.md`, `ERD.md`, `DATA_LIFECYCLE.md` (Identified cross-tenant & idempotency gaps). |
| **M0-T07** | Database Integrity Corrections & REST API Contract | CHANGES INCORPORATED | Composite FK integrity, soft deletes, 13 tables reconciled, `API_CONTRACT.md`, `API_COVERAGE.md`. |
| **M0-T08** | Official Repository Initialization | APPROVED | Local monorepo created, Git initialized (`main`), documentation migrated with SHA-256 validation. |
| **M0-T09** | Baseline Alignment & Initial Commit | APPROVED | Baseline alignment, risk register, initial local blueprint commit `55c0c37`. |
| **M0-T10** | GitHub Setup & Repository Synchronization | APPROVED | GitHub remote configured and synchronized (`https://github.com/HuyGiang1/EnglishWeb.git`). |
 
### Milestone M1: First Vertical Slice Execution History
 
| Task ID | Task Description | Technical Lead Decision | Primary Artifacts Produced / Revised |
| :--- | :--- | :--- | :--- |
| **M1-T01** | PostgreSQL Infrastructure Scaffolding on macOS | **COMPLETED** | `infra/docker-compose.yml`, `infra/.env.example`, `scripts/dev-up.sh`, `scripts/dev-down.sh`, `scripts/dev-status.sh`. Verified PostgreSQL 16.15 (Alpine arm64) on Docker 28.5.1 / Compose v2.40.3 with automated health checks and data volume persistence (`englishflow_postgres_data`). |
| **M1-T02** | Spring Boot Backend Scaffolding | **COMPLETED** | `backend/pom.xml`, `backend/mvnw`, `backend/.mvn/wrapper/`, `SecurityConfig.java`, 10 modular monolith packages, `scripts/backend-dev.sh`, `application.yml`, `application-dev.yml`, and 4 test suites (10 passing tests). Verified Spring Boot 3.4.7 on Java 21 LTS with Maven 3.10.0, Spring Security 6.4.x, Actuator healthcheck (`GET /actuator/health` -> 200 UP), and Testcontainers PostgreSQL 16. |
| **M1-T03** | Next.js Frontend Scaffolding & Design System Foundation | **COMPLETED** | `frontend/package.json`, `frontend/tsconfig.json`, `frontend/next.config.ts`, `globals.css`, shadcn/ui components (`button`, `card`, `separator`, `tooltip`, `sheet`), Workspace Shell (`sidebar.tsx`, `header.tsx`), Focus Shell (`focus-header.tsx`), typed API client foundation (`src/lib/api/client.ts`), minimal Dashboard, Focus review preview, and same-origin proxy to Spring Boot. Verified Next.js 15.5.27, React 19.1.0, TypeScript 5, Tailwind CSS v4 on Node 26.6.0. Build, lint, and typecheck all pass cleanly (0 errors). |
| **M1-T04** | Full-Stack Session Authentication Vertical Slice | **COMPLETED** | Flyway `V1__create_app_users.sql` on PostgreSQL 16, `User.java`, `UserRepository.java`, `AuthService.java`, `AuthController.java`, `SecurityConfig.java` (HttpOnly `JSESSIONID`, SameSite=Lax, deferred CSRF token bootstrap `/api/v1/auth/csrf` with `CsrfCookieFilter`), `GlobalExceptionHandler.java` (RFC 7807 + credential redaction), Next.js Edge `middleware.ts`, `auth.ts`, `auth-context.tsx`, `(auth)/login`, `(auth)/register`, `WorkspaceAuthGuard`, responsive monochrome UI, and authoritative session Dashboard display. Verified 18 backend integration tests (Testcontainers PostgreSQL 16), 9 frontend unit tests, and full port 3000 E2E proxy cycle. Committed locally as `907fbbd`. |
| **M1-T05** | Vocabulary Management Full-Stack Vertical Slice | **COMPLETED** | Flyway `V2__create_vocabularies.sql`, `Vocabulary.java`, `VocabularyRepository.java`, `VocabularyService.java`, `VocabularyController.java`, multi-tenant isolation, soft deletion (`deleted_at`), pagination & search, duplicate advisory check (`/duplicates/check`), `vocabularyApi` typed client, `QuickAddDialog` (keyboard shortcuts `C`, `Mod+Enter`, inline advisory check), responsive Vocabulary Library (`/vocabulary` with desktop table, mobile cards, search, filters, pagination), and Vocabulary Detail & Edit (`/vocabulary/[id]` with `Mod+S` save, delete confirmation). Verified 29/29 backend tests, 15/15 frontend tests, lint, typecheck, build, and port 3000 full-cycle E2E verification. |
| **M1-T06** | Spaced Repetition & Flashcard Review Vertical Slice | **COMPLETED** | Flyway `V3__create_srs_and_learning_events.sql` (`srs_items`, `srs_review_history`, `learning_activity_events` with composite foreign keys and attempt uniqueness), replaceable `Sm2Scheduler` engine with 4-button rating mapping (Again, Hard, Good, Easy) and 1.30 EF floor, idempotent automatic vocabulary card provisioning, row-level pessimistic locking (`FOR UPDATE`) for concurrency-safe submission idempotency with `Idempotent-Replay: true` replay and `409 IDEMPOTENCY_CONFLICT`, immutable `historical_local_date` learning activity event logging, Focus Review UI (`/review`) with answer reveal, keyboard shortcuts (`1`..`4`, `Space`), retry idempotency guard, and session completion summary. Verified 53/53 backend tests (including real multi-threaded Testcontainers concurrency tests), 23/23 frontend tests, and 13-step full-stack E2E verification through Next.js proxy. Committed locally as `55db6f5`. |
| **M1-T07** | Real Dashboard & Learning Progress Vertical Slice | **COMPLETED** | Read-only analytics endpoints `GET /api/v1/dashboard/summary` and `/api/v1/analytics/dashboard` in `AnalyticsService` & `DashboardController`. Computes real vocabulary count (excluding soft-deleted), accurate due card count (active due SRS cards + unprovisioned active vocabulary without mutating DB on GET), verified today's learning activity count, daily goal progress percentage (capped at 100%), and calendar-day streak state machine (`ACTIVE_STUDIED_TODAY`, `ACTIVE_PENDING_TODAY`, `BROKEN_RESET`) anchored to user IANA timezone. Production monochrome UI on `/dashboard` with skeleton loader, error retry state, 4 KPI cards, goal progress bar, contextual study action CTA, recent sessions list, and deliberate M2 52-week heatmap placeholder. Verified 70/70 backend integration tests (PostgreSQL Testcontainers), 37/37 frontend tests, lint, typecheck, build, and 13-step full-stack E2E verification through Next.js proxy. |
| **M1-T08** | Milestone M1 Stabilization, Regression Audit & Git Checkpoint | **COMPLETED** | Comprehensive quality audit and stabilization: tightened `GlobalExceptionHandler` to prevent broad `app_users` mapping, added `HttpRequestMethodNotSupportedException` handler for HTTP 405 RFC 7807 responses, created `TimezoneStreakRegressionTest` covering UTC boundaries, DST forward (spring forward 23h day), DST backward (fall back 25h day), and historical date immutability across timezones; created React component interaction test suite covering login, quick-add, vocabulary search/empty states, flashcard reveal/grading, duplicate click prevention, and dashboard retry states. Verified all 76 backend tests, 45 frontend tests, `./mvnw clean verify`, and 16-step full-stack E2E audit. |
 
---
 
## 2. Current Implementation State
 
* **Repository State**: Official local Git repository active at `/Users/GiangNguyenHuy/EnglishWeb` on branch `main`. Baseline scaffold (`aa73e40`), Authentication checkpoint (`907fbbd`), and Vocabulary checkpoint (`3cad141`) committed locally.
* **Remote State**: Tracking `https://github.com/HuyGiang1/EnglishWeb.git`.
* **Infrastructure State**: **Operational**. PostgreSQL 16 (`postgres:16-alpine`, port 5432) active and verified with persistent volume and developer scripts.
* **Backend Source Code State**: **Authentication, Vocabulary & Spaced Repetition (SRS) Operational**. Spring Boot 3.4.7 modular monolith foundation active in `backend/` with Flyway migrations V1, V2, and V3, session authentication (`/api/v1/auth/*`), Vocabulary management (`/api/v1/vocabulary/*`), and Review APIs (`GET /api/v1/review/queue`, `POST /api/v1/review/grade`) with strict concurrency safety and multi-tenant authorization.
* **Frontend Source Code State**: **Authentication, Vocabulary & Spaced Repetition (SRS) Operational**. Next.js 15.5.27 App Router application active in `frontend/` with monochrome SaaS design tokens, dual layout shells, typed API client, Next.js Edge session middleware, auth pages (`/login`, `/register`), workspace guard, Vocabulary Library (`/vocabulary`), Quick Add dialog, Vocabulary Detail/Edit (`/vocabulary/[id]`), and Focus Flashcard Review (`/review`).
* **Documentation State**: 100% aligned across PRD, Architecture, Database, API contracts, design tokens, and delivery roadmap.
 
---
 
## 3. Architecture Risk Register
 
The following unresolved architecture risks and implementation follow-up items are tracked to ensure disciplined engineering during Milestones M1 and M2:
 
| Risk ID | Domain | Classification | Description & Technical Risk | Mitigation & Planned Verification |
| :--- | :--- | :--- | :--- | :--- |
| **RISK-01** | Framework Version Compatibility | **RESOLVED** | Exact patch combinations of Spring Boot 3.4.7 with Java 21, and Next.js 15.5.27 with React 19.1.0 on Node 26.6.0, verified compatible during M1-T02 and M1-T03. | Both backend build/tests and frontend build/typecheck/lint pass cleanly. |
| **RISK-02** | Concurrency-Safe Submission Idempotency | **RESOLVED** | Concurrent requests hitting the grading API simultaneously could cause duplicate schedules or activity events if not guarded under concurrent transactions. | Implemented row-level pessimistic locking (`FOR UPDATE`) on target `srs_items` row combined with DB unique constraints `uq_srs_review_attempt` and `uq_activity_user_attempt`. Replays original response with `Idempotent-Replay: true` or yields HTTP 409 `IDEMPOTENCY_CONFLICT`. Verified under real PostgreSQL multi-threaded tests. |
| **RISK-03** | Quiz Answer Retry & Scoring Rules | **NON-BLOCKING FOLLOW-UP** | Need implementation-level clarification on whether users can retry incorrect questions within an active diagnostic session or if all question submissions are strictly final. | Contract locks answers upon submission (`quiz_answers` has `UNIQUE(attempt_id, question_id)`). UI feedback loops and error states will be validated during screen prototyping. |
| **RISK-04** | Validation Response Sanitization | **RESOLVED** | Standard Spring Boot Bean Validation error handlers echo the `rejected_value`. If a user submits a weak or malformed password, echoing it back in the error response creates an information leak. | Implemented in `GlobalExceptionHandler.java`: sensitive fields (`password`, `current_password`, `new_password`, `token`, `secret`, `credentials`) have `rejected_value` redacted to `"[REDACTED]"`. Verified via test. |
| **RISK-05** | Cookie/CSRF Reverse Proxy Integration | **VERIFIED** | Same-origin rewrite in Next.js (`/api/*` -> `http://localhost:8080/api/*`) forwards `Set-Cookie` (`JSESSIONID`, `XSRF-TOKEN`) properly to browser origin. | Verified during M1-T03 and verified end-to-end in M1-T04/M1-T05/M1-T06 via cookie-jar HTTP proxy execution across `/auth/*`, `/vocabulary/*`, and `/review/*`. |
| **RISK-06** | Media Storage Lifecycle & Browser Audio | **NON-BLOCKING FOLLOW-UP** | Browser-only `MediaRecorder` takes avoid backend storage in MVP. If users switch browsers or clear cache, local recordings are lost. | Accepted for MVP; clearly communicated in UI as ephemeral comparison takes. Cloud persistence via `AudioStorageService` planned for V1. |
| **RISK-07** | Bulk Import Item Limit `[PROPOSED]` | **NON-BLOCKING FOLLOW-UP** | The current contract proposes a batch limit of 100 items per CSV/TSV file to protect against HTTP thread starvation. | Benchmark PostgreSQL batch insertion (`rewriteBatchedStatements=true`) with 500–1,000 items in Milestone M1 before finalizing production quota. |
| **RISK-08** | Heatmap Intensity Thresholds `[PROPOSED]` | **NON-BLOCKING FOLLOW-UP** | Grayscale intensity tiers (1–9, 10–19, 20–39, 40+) are currently proposed based on daily goal heuristics. | Compute intensity levels dynamically in the frontend based on the user's configured `daily_goal` (e.g., 25%, 50%, 100%, 150%) rather than hardcoded DB tiers. |
| **RISK-09** | Dictation Activity Completion Rule | **REQUIREMENT ALIGNMENT** | A valid completed dictation submission counts as learning activity regardless of whether accuracy reaches 80%. | Activity logging service records `DICTATION_SENTENCE` event for all completed submissions. |
 
---
 
## 4. Next Milestone Roadmap: Milestone M2 (Target MVP Expansion)

With Milestone M1 fully stabilized and verified end-to-end (all 8 tasks completed), Milestone M2 delivers target MVP features:

* **Task M2-T01: Vocabulary Collections & Tagging**: Organize vocabulary into structured decks and thematic categories with many-to-many relationship management.
* **Task M2-T02: CSV / TSV Bulk Vocabulary Import**: Scalable batch vocabulary ingestion with deduplication advisories and transactional safety.
* **Task M2-T03: Daily Dictation Vertical Slice**: Audio playback engine, real-time user transcription, Levenshtein difference comparison, accuracy calculation, and activity event integration.
* **Task M2-T04: Speech Shadowing Vertical Slice**: Dual native/learner audio waveform visualization, microphone recording comparison, and pronunciation self-assessment.
* **Task M2-T05: Diagnostic Vocabulary Quizzes**: Multiple-choice and fill-in-the-blank assessment sessions with randomized question generation and score calculation.
* **Task M2-T06: 52-Week GitHub-Style Activity Heatmap**: Interactive 365-day SVG consistency grid with dynamic grayscale intensity mapping and multi-year navigation.
* **Task M2-T07: Learner Profile & Settings**: Configurable study daily targets, audio playback speed preferences, IANA timezone selector, and account data export/erasure.

