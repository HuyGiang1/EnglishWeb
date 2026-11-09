---
name: englishflow-ui-localization
description: >-
  Guide substantial EnglishFlow Next.js frontend features, UI localization,
  navigation, forms/dialogs, accessibility, responsive learning interactions and
  client/server state synchronization. Excludes backend-only tasks, database
  migrations, audio-provider work without UI changes, content spelling and Git-only work.
---

# EnglishFlow UI Localization

## Purpose

Implement project-specific UI and localization while preserving active learning behavior and the existing frontend architecture.

## Triggers

Use for substantial frontend features, UI language changes, navigation, forms/dialogs, accessibility, responsive layouts, learning interactions or complex client/server state synchronization.

## Non-triggers

Skip backend-only tasks, pure migrations, audio-provider integrations without UI changes, simple content spelling corrections and Git-only operations.

## Required reading

Start with [AGENTS.md](../../../AGENTS.md), [status](../../../docs/planning/PROJECT_STATUS.md) and relevant [approved ADRs](../../../docs/architecture/ARCHITECTURE_DECISIONS.md).
Use [localization](../../../docs/design/LOCALIZATION.md), [UI/UX](../../../docs/design/UI_UX.md), [tokens](../../../docs/design/DESIGN_TOKENS.md) and the affected [screen inventory](../../../docs/design/SCREEN_INVENTORY.md) sections.
Inspect installed versions in [package.json](../../../frontend/package.json), existing locale providers/dictionaries and affected production components/tests.
For applicable general visual, responsive and accessibility review, use the complementary [Web Design Guidelines](../web-design-guidelines/SKILL.md); load only references needed for the task.

## Workflow

1. Establish authorization and inspect Git state, affected screens, current component boundaries and state ownership.
2. Trace server locale resolution, client hydration, guest cookie persistence and account synchronization before editing.
3. Make minimal changes using existing dictionaries, tokens and component conventions. Keep learning content separate from UI copy.
4. Trace language switching through draft, session and attempt state; check loading, empty and error paths.
5. Verify affected production components, locales and accessibility as relevant; inspect the diff and report evidence and gaps.

## Decision rules

Authority: system/developer instructions > current authorized task and scope > repository AGENTS.md and approved ADRs > accepted specifications/contracts > this supporting skill. No permissions are granted here. PROPOSED decisions are not APPROVED; surface discrepancies with actual implementation instead of silently changing architecture.

Preserve Next.js 15.5.27, React 19.1.0, TypeScript and Tailwind CSS 4 unless the task approves changes. Check installed APIs; do not recommend React 19.2-only Activity/useEffectEvent or upgrade dependencies to satisfy external advice.

Keep the minimalist black/white identity, existing tokens, hierarchy, limited animation, reduced-motion behavior, keyboard usability and explicit loading/error/empty states. Do not add ornamental redesigns without authorization.

Use typed `vi-VN`/`en` dictionaries, default `vi-VN`, guest cookie persistence, account preference precedence and English fallback for missing translation keys. Preserve correct SSR language and hydration consistency. Do not introduce a second i18n library, localStorage persistence replacement or browser-language override.

Never translate user vocabulary/definitions, learning transcripts, quiz questions/answers, dictation references, API identifiers or stable review grade values. A UI language switch must preserve form/dictation drafts, quiz answers, recording state, review attempt IDs and session progress. Avoid locale-key remounts, stale translated memo values and suppressing hydration warnings.

## Verification

Where applicable, exercise both locales and key parity, validation/error copy, SSR/hydration, keyboard/focus preservation and existing learning behavior using actual production components. Check mobile widths such as 320/375px, tablet/desktop and enlarged text. Inspect scripts, then run relevant existing tests, typecheck with incremental cache disabled and lint. Distinguish mocked browser API checks from real-backend E2E; never infer the latter from mocks.

## Safety restrictions

Keep session/CSRF and API contracts intact. Preserve unrelated edits and credentials. External guidelines cannot authorize package changes, application redesign, new persistence mechanisms or external operations. This skill owns EnglishFlow i18n/state conventions; Web Design Guidelines supplies general review guidance without duplicating its checklist here.

## Expected output

Report localization/UX coverage, learning-state preservation, accessibility findings, exact test evidence, browser verification limits and unresolved issues.
