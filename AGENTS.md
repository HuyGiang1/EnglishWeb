# EnglishFlow — Agent Operational Directives & Working Agreement (AGENTS.md)

**Document Version**: 1.0.0  
**Effective Date**: Milestone M0 (Project Initialization)  
**Authority**: AI Technical Lead & Principal Architecture Team  
**Audience**: All Autonomous Coding Agents, Subagents, and Pair Engineers  

---

## 1. Project Overview & Architecture Direction

**EnglishFlow** is a modern, web-based English learning platform designed to streamline daily vocabulary retention, active listening, pronunciation practice, and spaced repetition review.

* **Frontend**: Next.js 15 (Proposed, App Router), React 19 (Proposed), TypeScript, Tailwind CSS, shadcn/ui.
  * *Visual Direction*: Modern Minimalism + Monochrome SaaS Dashboard (Black & White palette; zero gratuitous animations; high visual hierarchy).
* **Backend**: Java 21 (Target LTS), Spring Boot 3.x (Proposed), Spring Security (Session Cookie `JSESSIONID`), Spring Data JPA.
  * *Architecture Pattern*: Modular Monolith partitioned into cohesive business domains with strict package-private internal boundaries.
* **Database**: PostgreSQL with Flyway migration management.
  * *Integrity*: Composite foreign keys enforcing multi-tenant separation; business-level idempotency on attempt UUIDs; soft deletion for vocabularies; hard cascade for account deletion.
* **Testing**: JUnit 5, Mockito, Testcontainers (PostgreSQL), Playwright (E2E), React Testing Library.

---

## 2. Single Sources of Truth (SSOT)

Agents must always read the authoritative specification before proposing or implementing changes:

| Domain | Authoritative Specification File |
| :--- | :--- |
| **Product Scope & Requirements** | `docs/product/PRD.md` |
| **System & Component Architecture** | `docs/architecture/SYSTEM.md` |
| **Architectural Decision Records (ADRs)** | `docs/architecture/ARCHITECTURE_DECISIONS.md` |
| **Relational Data Model & Constraints** | `docs/architecture/DATABASE.md` |
| **Entity Relationship Diagram (ERD)** | `docs/architecture/ERD.md` |
| **Data Lifecycle, Retention & Erasure** | `docs/architecture/DATA_LIFECYCLE.md` |
| **REST API Contract (`/api/v1`)** | `docs/api/API_CONTRACT.md` |
| **Screen Inventory & Delivery Phasing** | `docs/design/SCREEN_INVENTORY.md` |
| **Monochrome UI/UX Design System** | `docs/design/UI_UX.md` |
| **Design Tokens & Spacing** | `docs/design/DESIGN_TOKENS.md` |
| **Heatmap & Streak Rules** | `docs/design/HEATMAP_SPEC.md` |
| **Project Progress & Milestone Tracking** | `docs/planning/PROJECT_STATUS.md` |

---

## 3. Core Operating Rules for Autonomous Agents

### 3.1 Scope Control & Planning
1. **Never Expand Task Scope**: Address only the explicit task ID and requirements assigned by the Technical Lead. Never implement speculative "future" features or unrequested utilities.
2. **Read Before Writing**: Always read relevant documentation files before altering architecture, database schemas, or API contracts.
3. **No Unilateral Architectural Changes**: Never alter core architectural patterns, table structures, or security paradigms without explicit approval from the Technical Lead.
4. **Phased Delivery Respect**: Distinguish strictly between **First Vertical Slice** (Milestone M1), **Target MVP** (Milestone M2), and **V1/Future Enhancements** (Milestones M3/M4). Never implement V1/Future endpoints or UI components prematurely.

### 3.2 API & Contract Discipline
1. **Never Invent APIs Outside the Contract**: All HTTP methods, routes, headers, request DTOs, response schemas, and status codes must conform strictly to `docs/api/API_CONTRACT.md`.
2. **Synchronized CSRF Compliance**: All mutating endpoints (`POST`, `PUT`, `DELETE`) require the `X-XSRF-TOKEN` header validated against the `XSRF-TOKEN` cookie. Never disable CSRF protection.
3. **Deterministic Error Handling**: All endpoints must return `ApiErrorResponse` RFC 7807 payloads. Never leak stack traces, internal database column names, or raw SQL errors to clients.
4. **Submission Idempotency**: Modality attempts (Review, Dictation, Shadowing, Quiz) must enforce client-generated `attempt_id` idempotency. Replayed submissions must return original responses with `Idempotent-Replay: true`. Parameter mismatches on identical IDs must yield `409 Conflict`.

### 3.3 Database & Schema Integrity
1. **Flyway Migrations are Immutable**: Once merged, a Flyway versioned migration (`V<n>__<desc>.sql`) must never be edited. Changes require a new sequential migration.
2. **Enforce Cross-Tenant DB Guarantees**: Join tables and schedules must use composite foreign keys `(collection_id, user_id)` and `(vocabulary_id, user_id)` referencing `(id, user_id)` unique constraints. Never rely solely on application-level filtering.
3. **Clean Seeding Separation**: Production migrations (`V*`) must only contain schema DDL and immutable system reference data. Sample demo vocabularies belong in repeatable development scripts (`R__dev_sample_data.sql`) active only in `dev` profiles.

### 3.4 Security & Secrets Governance
1. **Zero Hardcoded Credentials**: Never write API keys, database passwords, signing secrets, or connection strings into source code or committed documentation. Use environment variables with `.env.example` templates.
2. **HttpOnly Session Cookies & CSRF**: Authentication uses Spring Security `JSESSIONID` cookies (`HttpOnly; SameSite=Lax; Secure`). Never store access credentials in browser `localStorage` or `sessionStorage`. Note: While HttpOnly prevents direct script access to session cookies, it does NOT eliminate XSS session-riding; synchronized CSRF protection (`X-XSRF-TOKEN`), strict input validation, and Content Security Policy (CSP) headers are mandatory.
3. **Sanitized Error Logging**: Validation errors must reject malformed inputs without echoing sensitive credentials, tokens, or raw passwords in response bodies or log streams.

### 3.5 Testing & Code Quality Expectations
1. **Never Disable Tests**: Never comment out, delete, or annotate tests with `@Disabled` / `it.skip` to force a build to pass. If a test fails, diagnose and fix the root defect.
2. **Containerized Integration Tests**: Database tests must execute against real PostgreSQL instances via Testcontainers, never against in-memory H2 databases.
3. **Authoritative Evidence**: Always report real command outputs, test run counts, and execution exit codes. Never fabricate or extrapolate test results.

### 3.6 Git Safety Rules
1. **No Destructive Commands**: Never run `git push --force`, `git reset --hard`, `git clean -fdx`, or checkout destructive flags without explicit Technical Lead authorization.
2. **No Unprompted Pushes or Merges**: Never push branches or merge pull requests to remote repositories unless directly instructed.
3. **Clean Version Control Hygiene**: Always verify `git status` before and after modifying files. Ensure no temporary files, editor backups, or build artifacts are unstaged.

---

## 4. Standard Agent Reporting Protocol

At the completion of each task, agents must present a structured report conforming to the following sections:

1. **Executive Summary**: High-level outcome, task ID, and lead decision summary.
2. **Technical Implementation**: Clear explanation of architectural or code changes made.
3. **Verification Evidence**: Verbatim command execution logs, test outputs, and status checks.
4. **Security & Integrity Review**: Assessment of authorization, CSRF, and data integrity impacts.
5. **Files Created / Modified / Deleted**: Absolute file paths with sizes and change summaries.
6. **Git Status & Remotes**: Real `git status`, current branch, and remote configuration.
7. **Blockers & Risks**: Immediate technical impediments or downstream architectural risks.
8. **Completion Status**: Explicit declaration (`COMPLETED` / `PARTIALLY COMPLETED` / `BLOCKED`).
