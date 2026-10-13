# EnglishFlow

> A modern, minimalist web platform for daily English vocabulary acquisition, spaced repetition review, dictation, and speech shadowing.

[![Milestone](https://img.shields.io/badge/Milestone-M0%20Blueprint-black?style=flat-square)](#project-status)
[![License](https://img.shields.io/badge/License-Proprietary-black?style=flat-square)](#license)

---

## 1. Project Overview

**EnglishFlow** is designed for serious English learners who prioritize high-efficiency retention, distraction-free study, and measurable daily progress. Built around a **Modern Minimalism & Monochrome SaaS Dashboard** visual identity, the platform eliminates gamified clutter in favor of typographic clarity, keyboard-driven navigation, and high-contrast ergonomics.

### Core Product Modules
1. **Vocabulary Management & Quick Add**: Seamless lexical capture with IPA phonetics, definitions, examples, and instant duplicate detection.
2. **Spaced Repetition System (SRS)**: SuperMemo SM-2 flashcard scheduling delivering cards in an isolated, distraction-free Focus Mode.
3. **Daily Dictation**: Audio playback with interactive transcription, evaluated by a real-time server-side Levenshtein difference visualizer.
4. **Speech Shadowing**: Native audio pacing paired with browser-native recording comparison and self-assessment scoring.
5. **Diagnostic Quizzes**: Multiple-choice and fill-in-the-blank lexical assessments with randomized question banks.
6. **Monochrome Activity Heatmap**: GitHub-style 365-day study consistency matrix aggregated strictly against user local calendar timezones.

---

## 2. Technical Direction & Architecture

EnglishFlow is architected as a modular monorepo balancing developer ergonomics with production-grade security and data integrity:

* **Backend**: Java 21 LTS, Spring Boot 3.4.7, Spring Security 6.4.x, Spring Data JPA, Flyway.
  * *Architecture Pattern*: Modular Monolith partitioned into strictly bounded context packages (`auth`, `user`, `vocabulary`, `review`, `content`, `dictation`, `shadowing`, `quiz`, `analytics`).
  * *Authentication*: Spring Security `HttpSession` with `JSESSIONID` cookies (`HttpOnly; SameSite=Lax; Secure`) and synchronized CSRF (`X-XSRF-TOKEN`).
* **Frontend**: Next.js 15.5.27 (App Router), React 19.1.0, TypeScript 5, Tailwind CSS v4, shadcn/ui.
  * *Styling*: Custom monochrome design tokens (near-black `#111111` foregrounds, pure white `#FFFFFF` canvas, `#E5E5E5` borders, zero saturated accent colors).
  * *Layouts*: Dual-shell architecture—Workspace Shell for dense browsing/configuration and Focus Shell for distraction-free study.
  * *API Integration*: Same-origin proxy forwarding `/api/*` to Spring Boot (`http://127.0.0.1:8080/api/*`) with typed RFC 7807 client foundation.
* **Database & Persistence**: PostgreSQL 16+ managed via Flyway versioned migrations and Docker Compose.
  * *Integrity Guarantees*: Engine-level composite foreign keys preventing cross-tenant data association; client-generated submission UUIDs enforcing business-level idempotency; soft deletion for vocabularies; cascading hard deletion for account erasure.
* **Testing Infrastructure**: JUnit 5, Mockito, Testcontainers (real PostgreSQL instances), Playwright (E2E), React Testing Library.

---

## 3. Repository Directory Structure

```text
EnglishFlow/
├── .github/              # CI/CD workflows and automated quality gates
├── backend/              # Spring Boot 3.4.7 modular monolith (Java 21 LTS, Maven wrapper)
├── frontend/             # Next.js 15.5.27 App Router web application (React 19, Tailwind v4, shadcn/ui)
│   ├── src/
│   │   ├── app/          # App Router: root, (workspace) shell, (focus) shell routes
│   │   ├── components/   # UI primitives (shadcn), layouts (sidebar, header, focus-header)
│   │   └── lib/          # API client foundation, utilities, design tokens
│   ├── next.config.ts    # Same-origin reverse proxy rewrites to Spring Boot
│   └── package.json      # Dependencies and verified npm scripts
├── infra/                # Docker compose, PostgreSQL 16 container, and environment templates
├── scripts/              # Developer automation (dev-up, dev-down, dev-status, backend-dev)
├── docs/                 # Authoritative project specifications and blueprints
│   ├── product/          # Product Requirements Document (PRD)
│   ├── design/           # UI/UX guidelines, design tokens, screen inventory, heatmap
│   ├── architecture/     # System architecture, ADRs, database blueprint, ERD, lifecycle
│   ├── api/              # REST API contract (/api/v1) and screen coverage matrix
│   ├── planning/         # Project status, milestones, and task roadmap
│   └── reports/          # Verification logs and audit reports
├── AGENTS.md             # Authoritative directives for AI coding agents
├── README.md             # Project documentation and developer overview
├── .gitignore            # Monorepo version control exclusion rules
├── .gitattributes        # Line ending (LF/CRLF) normalization rules
└── .editorconfig         # Universal editor and code formatting standards
```

---

## 4. Documentation Index

The repository maintains an exhaustive set of blueprints serving as the single source of truth:

| Document | Path | Focus Area |
| :--- | :--- | :--- |
| **Product Requirements** | [`docs/product/PRD.md`](docs/product/PRD.md) | User personas, product scope, modality specifications |
| **System Architecture** | [`docs/architecture/SYSTEM.md`](docs/architecture/SYSTEM.md) | Component architecture, security model, performance targets |
| **Architecture Decisions** | [`docs/architecture/ARCHITECTURE_DECISIONS.md`](docs/architecture/ARCHITECTURE_DECISIONS.md) | ADR records (Session auth, monolith, Flyway, PostgreSQL) |
| **Database Blueprint** | [`docs/architecture/DATABASE.md`](docs/architecture/DATABASE.md) | 13-table schema, composite FKs, indexes, Flyway migrations |
| **Entity Relationship Diagram** | [`docs/architecture/ERD.md`](docs/architecture/ERD.md) | Mermaid ERD and relational cardinalities |
| **Data Lifecycle Spec** | [`docs/architecture/DATA_LIFECYCLE.md`](docs/architecture/DATA_LIFECYCLE.md) | Retention rules, soft deletes, account erasure, idempotency |
| **REST API Contract** | [`docs/api/API_CONTRACT.md`](docs/api/API_CONTRACT.md) | 31 endpoints under `/api/v1`, DTO schemas, error responses |
| **API Coverage Matrix** | [`docs/api/API_COVERAGE.md`](docs/api/API_COVERAGE.md) | 13-screen traceability matrix and delivery phasing |
| **UI/UX Design Blueprint** | [`docs/design/UI_UX.md`](docs/design/UI_UX.md) | Monochrome minimalist visual design system |
| **Design Tokens** | [`docs/design/DESIGN_TOKENS.md`](docs/design/DESIGN_TOKENS.md) | Color palettes, typography, spacing, border radiuses |
| **Screen Inventory** | [`docs/design/SCREEN_INVENTORY.md`](docs/design/SCREEN_INVENTORY.md) | Detailed screen definitions, navigation flows, keyboard guards |
| **Heatmap Specification** | [`docs/design/HEATMAP_SPEC.md`](docs/design/HEATMAP_SPEC.md) | 365-day grid geometry, SVG rendering, timezone bucketing |
| **Project Status** | [`docs/planning/PROJECT_STATUS.md`](docs/planning/PROJECT_STATUS.md) | Milestone delivery status and pending corrections |

---

## 5. Project Development Status

> 🚀 **MILESTONE M1 VERTICAL SLICE COMPLETE**: Full-stack session authentication, vocabulary library, spaced repetition flashcard review, authoritative learning dashboard, timezone/DST streak engine, and security stabilization verified on macOS Apple Silicon.

* **Current Stage**: Milestone M2 — Target MVP Expansion (**IN PROGRESS — M2-T01 COMPLETED**)
* **Completed Tasks**:
  * M1-T01: PostgreSQL Infrastructure Scaffolding on macOS (Docker Compose, Healthchecks, Volume Persistence)
  * M1-T02: Spring Boot Backend Scaffolding (Java 21 LTS, Spring Boot 3.4.7, Modular Monolith)
  * M1-T03: Next.js Frontend Scaffolding & Design System Foundation (Next.js 15, Tailwind v4, shadcn/ui, Workspace & Focus Shells)
  * M1-T04: Full-Stack Session Authentication Vertical Slice (Flyway V1, HttpOnly Cookies, Synchronized CSRF, Protected Guards)
  * M1-T05: Vocabulary Management Full-Stack Vertical Slice (Flyway V2, Multi-Tenant CRUD, Quick Add, Search, Soft Deletes)
  * M1-T06: Spaced Repetition SRS Flashcard Review (Flyway V3, SM-2 Engine, Row-Locked Concurrency Idempotency, Activity Events)
  * M1-T07: Real Dashboard & Learning Progress Vertical Slice (Authoritative Read-Only Summary, Streak State Machine, Daily Goal Tracking)
  * M1-T08: Milestone M1 Stabilization, Regression Audit & Git Checkpoint (76 Backend Tests, 45 Frontend Tests, 16-Step Full-Stack E2E Audit)
  * M2-T01: Vocabulary Collections & Deck Management (Flyway V4, Many-to-Many Composite Tenant FKs, Non-Destructive Deletion, Real Collection Library & Detail Screens, Quick Add Deck Assignment, 96 Backend Tests, 55 Frontend Tests, 14-Step Full-Stack E2E Verification)
* **Next Target**: Task M2-T02: CSV/TSV Bulk Vocabulary Import

---

## 6. Environment Prerequisites

The following toolchains are verified in the local environment:

* **Java**: OpenJDK 21 LTS
* **Node.js**: Node.js 26.6.0 (or Node.js 24+)
* **Package Manager**: npm 11.18.0
* **Database**: PostgreSQL 16 (Docker Compose)
* **Build Tool**: Maven Wrapper (`./mvnw`)
* **Version Control**: Git 2.40+

---

## 7. Local Development Workflow

```bash
# 1. Start local PostgreSQL infrastructure (PostgreSQL 16 on :5432)
./scripts/dev-up.sh

# Check infrastructure status and health
./scripts/dev-status.sh

# Stop infrastructure safely (preserving data volume)
./scripts/dev-down.sh

# 2. Run backend dev server (Spring Boot on :8080)
./scripts/backend-dev.sh
# Or directly:
cd backend && ./mvnw spring-boot:run

# Verify backend health endpoint
curl http://localhost:8080/actuator/health

# 3. Run frontend dev server (Next.js on :3000)
cd frontend
npm run dev

# Quality checks in frontend/
npm run lint         # ESLint 9
npm run typecheck    # TypeScript strict check (tsc --noEmit)
npm run build        # Turbopack production build

# 4. Verify same-origin API proxy to backend
curl -i http://localhost:3000/api/v1/vocabulary
# Expected response: HTTP 401 Unauthorized with Set-Cookie: JSESSIONID, XSRF-TOKEN
```

