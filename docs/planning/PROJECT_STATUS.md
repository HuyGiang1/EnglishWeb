# EnglishFlow — Project Status & Delivery Roadmap

**Document Version**: 1.1.0  
**Effective Date**: Milestone M0 (Task M0-T09)  
**Status**: Official Monorepo Initialized & Documentation Baseline Aligned  
**Repository Path**: `C:\Users\truon\Projects\EnglishFlow`  
**Git Branch**: `main` (Local Only)  

---

## 1. Milestone M0: Discovery & Blueprint Audit History

| Task ID | Task Description | Technical Lead Decision | Primary Artifacts Produced / Revised |
| :--- | :--- | :--- | :--- |
| **M0-T01** | Workspace Discovery & Environment Readiness Audit | APPROVED | Environment audit, toolchain verification (Java 21, Node 24, Git, Docker). |
| **M0-T02** | Minimalist UI/UX Blueprint & Design System | PROVISIONALLY ACCEPTED | Design tokens, color system, typography, minimalist monochrome guidelines. |
| **M0-T03** | UI/UX Specification Verification & Correction | NEEDS VERIFICATION | Synchronized `UI_UX.md`, `DESIGN_TOKENS.md`, `SCREEN_INVENTORY.md`, `HEATMAP_SPEC.md`. |
| **M0-T04** | Final UI/UX Alignment & Architecture Handoff | CHANGES REQUIRED | Aligned session-based auth cookie standards, keyboard guards, delivery roadmap. |
| **M0-T05** | Product Requirements & System Architecture Blueprint | CHANGES REQUIRED | `docs/product/PRD.md`, `docs/architecture/SYSTEM.md`, `ARCHITECTURE_DECISIONS.md`. |
| **M0-T06** | PostgreSQL Database Blueprint & Data Integrity Design | CHANGES REQUIRED | `DATABASE.md`, `ERD.md`, `DATA_LIFECYCLE.md` (Identified cross-tenant & idempotency gaps). |
| **M0-T07** | Database Integrity Corrections & REST API Contract | CHANGES INCORPORATED | Composite FK integrity, soft deletes, 13 tables reconciled, `API_CONTRACT.md`, `API_COVERAGE.md`. |
| **M0-T08** | Official Repository Initialization | APPROVED | Local monorepo created, Git initialized (`main`), documentation migrated with SHA-256 validation. |
| **M0-T09** | Baseline Alignment & Initial Commit | IN PROGRESS | Documentation cross-alignment, risk register formalization, initial local commit. |

---

## 2. Current Implementation State

* **Repository State**: Official local Git repository active at `C:\Users\truon\Projects\EnglishFlow` on branch `main`.
* **Remote State**: No remote repository configured (`git remote -v` returns empty). Remote setup deferred.
* **Source Code State**: **Pending Scaffolding**. No Next.js or Spring Boot application code exists.
* **Documentation State**: 100% migrated to `docs/` and aligned across PRD, Architecture, Database, and API contracts.

---

## 3. Architecture Risk Register

The following unresolved architecture risks and implementation follow-up items are tracked to ensure disciplined engineering during Milestones M1 and M2:

| Risk ID | Domain | Classification | Description & Technical Risk | Mitigation & Planned Verification |
| :--- | :--- | :--- | :--- | :--- |
| **RISK-01** | Framework Version Compatibility | **BLOCKER (For Scaffold)** | Exact patch combinations of Spring Boot 3.3.x/3.4.x with Java 21, and Next.js 15 with React 19, must be tested for compile and runtime compatibility during scaffolding. | Execute interactive `--help` checks on CLI initializers; verify clean build and test execution before committing initial scaffold. |
| **RISK-02** | Concurrency-Safe Submission Idempotency | **NON-BLOCKING FOLLOW-UP** | Concurrent identical requests hitting the API simultaneously could both pass `findByAttemptId` checks if executed in parallel before commits. | Rely on database `UNIQUE (attempt_id)` constraint; Spring `@Transactional` service catches `DataIntegrityViolationException` and replays committed response with `Idempotent-Replay: true`. Validate via concurrent integration tests. |
| **RISK-03** | Quiz Answer Retry & Scoring Rules | **NON-BLOCKING FOLLOW-UP** | Need implementation-level clarification on whether users can retry incorrect questions within an active diagnostic session or if all question submissions are strictly final. | Contract locks answers upon submission (`quiz_answers` has `UNIQUE(attempt_id, question_id)`). UI feedback loops and error states will be validated during screen prototyping. |
| **RISK-04** | Validation Response Sanitization | **NON-BLOCKING FOLLOW-UP** | Standard Spring Boot Bean Validation error handlers echo the `rejected_value`. If a user submits a weak or malformed password, echoing it back in the error response creates an information leak. | Global `@ControllerAdvice` must redact `rejected_value` on sensitive fields (`password`, `token`, `secret`, `credentials`) in `ApiErrorResponse`. |
| **RISK-05** | Cookie/CSRF Reverse Proxy Integration | **NON-BLOCKING FOLLOW-UP** | Same-origin rewrite in Next.js (`/api/*` -> `http://localhost:8080/api/*`) must correctly forward `Set-Cookie` headers (`JSESSIONID`, `XSRF-TOKEN`) and browser `Cookie` headers without stripping `SameSite` or domain attributes. | Validate via Playwright end-to-end tests during First Vertical Slice (Milestone M1). |
| **RISK-06** | Media Storage Lifecycle & Browser Audio | **NON-BLOCKING FOLLOW-UP** | Browser-only `MediaRecorder` takes avoid backend storage in MVP. If users switch browsers or clear cache, local recordings are lost. | Accepted for MVP; clearly communicated in UI as ephemeral comparison takes. Cloud persistence via `AudioStorageService` planned for V1. |
| **RISK-07** | Bulk Import Item Limit `[PROPOSED]` | **NON-BLOCKING FOLLOW-UP** | The current contract proposes a batch limit of 100 items per CSV/TSV file to protect against HTTP thread starvation. | Benchmark PostgreSQL batch insertion (`rewriteBatchedStatements=true`) with 500–1,000 items in Milestone M1 before finalizing production quota. |
| **RISK-08** | Heatmap Intensity Thresholds `[PROPOSED]` | **NON-BLOCKING FOLLOW-UP** | Grayscale intensity tiers (1–9, 10–19, 20–39, 40+) are currently proposed based on daily goal heuristics. | Compute intensity levels dynamically in the frontend based on the user's configured `daily_goal` (e.g., 25%, 50%, 100%, 150%) rather than hardcoded DB tiers. |

---

## 4. Next Milestone Roadmap: Milestone M1 (First Vertical Slice)

The upcoming milestone will deliver the initial end-to-end walking skeleton:

* **Task M1-T01: Infrastructure & Docker Scaffolding**: Configure `infra/docker-compose.yml` with PostgreSQL 16 container, health checks, persistent volumes, and `.env.example`.
* **Task M1-T02: Backend Spring Boot Scaffolding**: Initialize Maven project with Java 21, Spring Web, Spring Security, Spring Data JPA, Flyway, and Testcontainers.
* **Task M1-T03: Frontend Next.js Scaffolding**: Initialize Next.js 15 App Router project with TypeScript, Tailwind CSS, and shadcn/ui.
* **Task M1-T04: First Vertical Slice Implementation**: Implement User Registration/Login, Vocabulary Creation (Quick Add), Vocabulary Listing, Flashcard SRS Review (SM-2), and Minimal Dashboard.
