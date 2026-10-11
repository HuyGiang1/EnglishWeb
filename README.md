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

* **Backend**: Java 21 (Target LTS), Spring Boot 3.x (Proposed), Spring Security, Spring Data JPA.
  * *Architecture Pattern*: Modular Monolith partitioned into strictly bounded context packages (`auth`, `user`, `vocabulary`, `review`, `content`, `dictation`, `shadowing`, `quiz`, `analytics`).
  * *Authentication*: Spring Security `HttpSession` with `JSESSIONID` cookies (`HttpOnly; SameSite=Lax; Secure`) and synchronized CSRF (`X-XSRF-TOKEN`).
* **Frontend**: Next.js 15 (Proposed, App Router), React 19 (Proposed), TypeScript, Tailwind CSS, shadcn/ui.
  * *Styling*: Custom monochrome design tokens (near-black foregrounds, pure white/subtle gray canvas, zero saturated accent colors).
* **Database & Persistence**: PostgreSQL 16+ managed via Flyway versioned migrations.
  * *Integrity Guarantees*: Engine-level composite foreign keys preventing cross-tenant data association; client-generated submission UUIDs enforcing business-level idempotency; soft deletion for vocabularies; cascading hard deletion for account erasure.
* **Testing Infrastructure**: JUnit 5, Mockito, Testcontainers (real PostgreSQL instances), Playwright (E2E), React Testing Library.

---

## 3. Repository Directory Structure

```text
EnglishFlow/
├── .github/              # CI/CD workflows and automated quality gates
├── backend/              # Spring Boot modular monolith (pending scaffold)
├── frontend/             # Next.js App Router web application (pending scaffold)
├── infra/                # Docker compose, PostgreSQL, and reverse proxy configs
├── scripts/              # Developer automation and database maintenance scripts
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

> ⚠️ **APPLICATION SCAFFOLDING PENDING**: The repository currently contains official architectural blueprints, design systems, and data models. Application source code (backend and frontend) has not yet been generated.

* **Current Stage**: Milestone M0 — Discovery & Blueprint (Complete)
* **Current Task**: M0-T08 — Repository Initialization
* **Next Stage**: Milestone M1 — First Vertical Slice (Scaffolding Spring Boot, Next.js, and Docker PostgreSQL container)

---

## 6. Environment Prerequisites

When application scaffolding commences, the following toolchains will be required:

* **Java**: OpenJDK 21 (LTS)
* **Node.js**: Node.js 24 (Active LTS) or LTS equivalent
* **Package Manager**: pnpm or npm
* **Database**: PostgreSQL 16+ (or Docker Desktop)
* **Build Tool**: Apache Maven 3.9+ (or Maven Wrapper)
* **Version Control**: Git 2.40+

---

## 7. Planned Local Development Workflow

*(To be enabled upon completion of Milestone M1 Scaffolding)*

```bash
# 1. Start local PostgreSQL infrastructure
docker compose -f infra/docker-compose.yml up -d

# 2. Run backend dev server (Spring Boot on :8080)
cd backend && ./mvnw spring-boot:run

# 3. Run frontend dev server (Next.js on :3000)
cd frontend && pnpm dev
```
