---
name: englishflow-backend-integrity
description: >-
  Guide substantial EnglishFlow Spring Boot controller/service, authentication,
  authorization, REST contract, PostgreSQL/Flyway, ownership, transaction,
  concurrency, idempotency and backend integration-test work. Excludes frontend-only
  copy/CSS, Git bookkeeping, media licensing alone and documentation spelling.
---

# EnglishFlow Backend Integrity

## Purpose

Make minimal, contract-compatible backend changes while preserving tenant isolation, learning history and database safety.

## Triggers

Use for substantial Spring implementation, API/schema changes, access control, transaction behavior, concurrent submissions, idempotency or integration-test changes.

## Non-triggers

Skip frontend-only copy or CSS, Git maintenance, media licensing alone and minor documentation spelling corrections.

## Required reading

Start with [AGENTS.md](../../../AGENTS.md), [status](../../../docs/planning/PROJECT_STATUS.md) and affected [approved ADRs](../../../docs/architecture/ARCHITECTURE_DECISIONS.md).
Read the relevant sections of [system architecture](../../../docs/architecture/SYSTEM.md), [API contract](../../../docs/api/API_CONTRACT.md), [database](../../../docs/architecture/DATABASE.md) and [data lifecycle](../../../docs/architecture/DATA_LIFECYCLE.md) for the affected domain.
Inspect its actual controllers, services, repositories, migrations and tests; use existing Review/Vocabulary behavior as context, not a replacement specification.

## Workflow

1. Establish the authorized task, branch, HEAD and working tree. Preserve unrelated edits.
2. Identify the business domain and accepted API/database contract. Trace session identity through ownership checks, repository predicates and database constraints.
3. Trace transaction boundaries, rollback behavior, concurrent writes and attempt replay. Preserve review history and progress.
4. Plan the smallest change. Review backward compatibility, deployment ordering and migration/data preservation before writing.
5. Inspect test datasource wiring before execution. Use explicit PostgreSQL Testcontainers isolation, following existing integration tests with `@ServiceConnection`; verify no development datasource fallback.
6. Run only authorized, relevant checks; inspect the final diff and report actual commands, exit codes, counts and limitations.

## Decision rules

Authority: system/developer instructions > current authorized task and scope > repository AGENTS.md and approved ADRs > accepted specifications/contracts > this supporting skill. This skill grants no permissions. A PROPOSED ADR is not APPROVED. Report discrepancies between documents and implementation; do not silently rewrite architecture to match an outdated blueprint.

Preserve the modular monolith with Spring Boot as backend authority, session authentication, CSRF, existing RFC 7807 errors and REST conventions. Do not relocate business logic into Next.js or introduce microservices without approval.

Keep ownership checks and PostgreSQL composite tenant constraints. Preserve client attempt IDs, original-response replay, replay headers and parameter-mismatch conflicts. Make Flyway changes append-only; never edit applied migrations.

## Verification

Select checks relevant to the change: authentication, authorization and cross-user rejection; CSRF and validation; rollback and concurrent submissions; identical replay versus mismatched reuse; database constraints; migration compatibility/data preservation; and error-contract compatibility. Use real PostgreSQL containers, never H2 substitutes. Inspect scripts before running them and report untested behavior honestly.

## Safety restrictions

During M3-EVO-1B, legacy smoke tests unexpectedly applied V11 to the development database. Never repeat this: prove isolated datasource configuration before any database test; if isolation is uncertain, stop database-dependent execution and report the gap. Never fall back to development configuration. Development/production migration or destructive schema work needs explicit task authorization. Do not disable tests to obtain a pass, expose secrets or weaken security.

## Expected output

Report scope and affected files; API/schema impacts; authorization, CSRF and integrity findings; exact verification commands/results; known risks; and documentation changes.
