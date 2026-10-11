# EnglishFlow — API Coverage Matrix & Screen Alignment

**Document Version**: 1.0.0  
**Milestone**: M0 — Discovery & Blueprint (Task M0-T07)  
**Status**: Approved REST Contract Alignment  
**Related Documents**: 
- `docs/design/SCREEN_INVENTORY.md` (v3.0.0)
- `docs/api/API_CONTRACT.md` (v1.0.0)
- `docs/architecture/DATABASE.md` (v2.0.0)
- `docs/architecture/DATA_LIFECYCLE.md` (v2.0.0)

---

## 1. Executive Summary & Delivery Phasing

This document establishes the exhaustive traceability matrix linking the **13 user interface screens** specified in `SCREEN_INVENTORY.md` to the **31 REST endpoints** formalized in `API_CONTRACT.md`. 

To support rigorous agile delivery and avoid premature feature bloat, endpoint implementations are strictly partitioned into three distinct phases:

1. **First Vertical Slice (Milestone M1)**: The minimal end-to-end walking skeleton demonstrating authentication, vocabulary persistence, quick entry, card editing, flashcard SRS scheduling, and basic dashboard metrics.
2. **Remaining Target MVP (Milestone M2)**: Full Target MVP delivery encompassing batch import, deck collections, multi-modality exercises (Dictation, Shadowing, Diagnostic Quizzes), local calendar heatmaps, and user configuration.
3. **V1 Enhancements & Future Scope (Milestones M3 & M4)**: Deferred advanced features (e.g., historical audit browsing, server-side speech acoustic evaluation, automated AI sentence generation, and Anki package synchronization).

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                 DELIVERY PHASING MAP                                   │
├──────────────────────────────────┬─────────────────────────────────────────────────────┤
│ Phase 1: First Vertical Slice    │ Auth (01), Vocab List (03), Quick Add (04),         │
│ (Milestone M1)                   │ Vocab Detail (06), Flashcard Review (08),           │
│                                  │ Minimal Dashboard (02)                              │
├──────────────────────────────────┼─────────────────────────────────────────────────────┤
│ Phase 2: Remaining Target MVP    │ Full Dashboard & Heatmap (02), Bulk Import (05),    │
│ (Milestone M2)                   │ Collections (07), Dictation (09), Shadowing (10),   │
│                                  │ Quizzes (11), User Settings (13)                    │
├──────────────────────────────────┼─────────────────────────────────────────────────────┤
│ Phase 3: V1 & Future Scope       │ History Audit Log (12), AI Voice Analysis,          │
│ (Milestones M3 / M4)             │ Automated Sentence Gen, Anki .apkg Export/Import    │
└──────────────────────────────────┴─────────────────────────────────────────────────────┘
```

---

## 2. Comprehensive Screen-to-API Mapping Matrix

| Screen ID | Screen Name | Phase | User Action / Trigger | HTTP Method | Endpoint Route | Request / Query | Response Code | Idempotency / Security |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **01** | **Authentication** | Slice | Register new account | `POST` | `/api/v1/auth/register` | `RegisterRequest` | `201 Created` | CSRF exempt (anon bootstrap); establishes session |
| **01** | **Authentication** | Slice | Log in credentials | `POST` | `/api/v1/auth/login` | `LoginRequest` | `200 OK` | CSRF exempt (anon bootstrap); issues `JSESSIONID` |
| **01** | **Authentication** | Slice | Check active session | `GET` | `/api/v1/auth/me` | None | `200 OK` | Read-only; returns `UserProfileResponse` |
| **01** | **Authentication** | Slice | Fetch CSRF token | `GET` | `/api/v1/auth/csrf` | None | `200 OK` | Emits `XSRF-TOKEN` cookie for subsequent mutations |
| **01** | **Authentication** | Slice | Log out session | `POST` | `/api/v1/auth/logout` | None | `204 No Content` | CSRF required; invalidates session & clears cookie |
| **02** | **Dashboard** | Slice | Fetch summary & streaks | `GET` | `/api/v1/dashboard/summary` | None | `200 OK` | Cached per-minute; authoritative streak calculation |
| **02** | **Dashboard** | MVP | Render activity heatmap | `GET` | `/api/v1/dashboard/heatmap` | `year`, `timezone` | `200 OK` | Aggregated by user IANA local calendar days |
| **02** | **Dashboard** | MVP | Inspect modality breakdown | `GET` | `/api/v1/dashboard/activity-breakdown` | `from_date`, `to_date` | `200 OK` | Returns count & minutes across all 4 modalities |
| **03** | **Vocabulary List** | Slice | Browse library / paginate | `GET` | `/api/v1/vocabularies` | `page, size, query, sort`| `200 OK` | Indexed `user_id, term`; filters out `deleted_at` |
| **03** | **Vocabulary List** | Slice | Live duplicate checking | `GET` | `/api/v1/vocabularies/check-duplicate` | `term` | `200 OK` | Debounced 300ms on frontend input |
| **04** | **Quick Add Modal** | Slice | Submit new word entry | `POST` | `/api/v1/vocabularies` | `CreateVocabularyRequest` | `201 Created` | CSRF required; initializes active `srs_items` row |
| **05** | **Bulk Import** | MVP | Dry-run preview CSV/TSV | `POST` | `/api/v1/vocabularies/import?dry_run=true` | Multipart CSV / TSV | `200 OK` | Validates syntax, duplicates without committing DB |
| **05** | **Bulk Import** | MVP | Commit batch import | `POST` | `/api/v1/vocabularies/import?dry_run=false`| Multipart CSV / TSV | `201 Created` | Transactional batch insert (max 100 entries per file) |
| **06** | **Vocab Detail & Edit**| Slice | Inspect full word card | `GET` | `/api/v1/vocabularies/{id}` | Path: `id` | `200 OK` | Read detail; includes current SRS stage & interval |
| **06** | **Vocab Detail & Edit**| Slice | Update definition/examples | `PUT` | `/api/v1/vocabularies/{id}` | `UpdateVocabularyRequest` | `200 OK` | CSRF required; updates definition, pos, notes |
| **06** | **Vocab Detail & Edit**| Slice | Delete vocabulary | `DELETE`| `/api/v1/vocabularies/{id}` | Path: `id` | `204 No Content` | Soft-deletes (`deleted_at`), suspends SRS schedule |
| **07** | **Collections** | MVP | List user collections | `GET` | `/api/v1/collections` | `page, size` | `200 OK` | Filtered strictly by authenticated `user_id` |
| **07** | **Collections** | MVP | Create new collection | `POST` | `/api/v1/collections` | `CreateCollectionRequest` | `201 Created` | Enforces unique `(user_id, name)` |
| **07** | **Collections** | MVP | View collection details | `GET` | `/api/v1/collections/{id}` | Path: `id` | `200 OK` | Returns name, description, total card count |
| **07** | **Collections** | MVP | Rename / edit collection| `PUT` | `/api/v1/collections/{id}` | `UpdateCollectionRequest` | `200 OK` | Ownership verified; updates metadata |
| **07** | **Collections** | MVP | Delete collection | `DELETE`| `/api/v1/collections/{id}` | Path: `id` | `204 No Content` | Removes deck; vocabulary items preserved intact |
| **07** | **Collections** | MVP | List words in collection | `GET` | `/api/v1/collections/{id}/vocabularies` | Path: `id`, `page, size` | `200 OK` | Joins through `vocabulary_collections` |
| **07** | **Collections** | MVP | Assign word to collection | `POST` | `/api/v1/collections/{id}/vocabularies` | `AssignVocabularyRequest` | `201 Created` | Enforces composite FK `(id, user_id)` on both |
| **07** | **Collections** | MVP | Remove word from collection | `DELETE`| `/api/v1/collections/{id}/vocabularies/{vocabId}` | Path: `id, vocabId` | `204 No Content` | Deletes join row; preserves vocabulary entity |
| **08** | **SRS Flashcard** | Slice | Fetch due study queue | `GET` | `/api/v1/reviews/queue` | `limit, collection_id` | `200 OK` | Pulls cards with `next_review_at <= NOW()` |
| **08** | **SRS Flashcard** | Slice | Submit SM-2 card rating | `POST` | `/api/v1/reviews/submissions` | `ReviewSubmissionRequest` | `200 OK` | Business idempotency on `attempt_id`; updates SM-2 |
| **09** | **Dictation** | MVP | List dictation library | `GET` | `/api/v1/contents?modality=DICTATION` | `difficulty, page, size` | `200 OK` | Hides reference transcripts from exercise list |
| **09** | **Dictation** | MVP | Load dictation exercise | `GET` | `/api/v1/dictation/{contentId}` | Path: `contentId` | `200 OK` | Delivers audio metadata and masked transcript tokens |
| **09** | **Dictation** | MVP | Submit typed transcript | `POST` | `/api/v1/dictation/submissions` | `DictationSubmissionRequest`| `200 OK` | Business idempotency; authoritative backend Levenshtein diff |
| **10** | **Shadowing** | MVP | List shadowing library | `GET` | `/api/v1/contents?modality=SHADOWING` | `difficulty, page, size` | `200 OK` | Returns content catalog with native audio URLs |
| **10** | **Shadowing** | MVP | Load shadowing prompt | `GET` | `/api/v1/shadowing/{contentId}` | Path: `contentId` | `200 OK` | Delivers audio URL, timing cues, full transcript |
| **10** | **Shadowing** | MVP | Complete practice take | `POST` | `/api/v1/shadowing/submissions` | `ShadowingSubmissionRequest`| `200 OK` | Idempotent on `take_id`; browser-only audio, self-rating |
| **11** | **Quiz Engine** | MVP | List diagnostic quizzes | `GET` | `/api/v1/contents?modality=QUIZ` | `difficulty, page, size` | `200 OK` | Catalog of diagnostic grammar and lexical sets |
| **11** | **Quiz Engine** | MVP | Start quiz attempt | `POST` | `/api/v1/quizzes/attempts` | `CreateQuizAttemptRequest` | `201 Created` | Instantiates `quiz_attempts`; obscures correct answers |
| **11** | **Quiz Engine** | MVP | Submit answer for question | `POST` | `/api/v1/quizzes/attempts/{id}/answers` | `SubmitQuizAnswerRequest` | `200 OK` | Idempotent on question; locks after single answer |
| **11** | **Quiz Engine** | MVP | Finalize and grade quiz | `POST` | `/api/v1/quizzes/attempts/{id}/complete` | None | `200 OK` | Idempotent completion; emits activity event once |
| **12** | **Learning History**| V1 | Browse chronological audit | `GET` | `/api/v1/history` (V1 Scope) | `from_date, to_date, modality` | `200 OK` | *Deferred to Milestone 3 (V1 Enhancement)* |
| **13** | **Settings** | MVP | Fetch current user config | `GET` | `/api/v1/settings` | None | `200 OK` | Returns display name, IANA timezone, daily goal |
| **13** | **Settings** | MVP | Update profile name | `PUT` | `/api/v1/settings/profile` | `UpdateProfileRequest` | `200 OK` | Updates `display_name` |
| **13** | **Settings** | MVP | Update timezone & goal | `PUT` | `/api/v1/settings/preferences` | `UpdatePreferencesRequest` | `200 OK` | Validates IANA timezone; recalculates daily goal |

---

## 3. Detailed UI Action & Backend Capability Analysis

### 3.1 First Vertical Slice (Milestone M1)

#### Screen 01: Authentication (`/login`, `/register`)
- **UI Interaction**:
  - User submits email and password on `/register` or `/login`.
  - Frontend triggers `GET /api/v1/auth/csrf` upon app boot to obtain the synchronized token cookie.
- **Backend Flow**:
  - Validates credentials using Spring Security `DaoAuthenticationProvider` and `BCryptPasswordEncoder`.
  - Establishes a server-side session, generating a random session ID returned via `JSESSIONID` cookie (`HttpOnly; SameSite=Lax; Secure`).
  - Emits CSRF token via `X-XSRF-TOKEN` cookie.
- **Edge Cases**:
  - Duplicate registration email yields `409 Conflict`.
  - Invalid credentials yield generic `401 Unauthorized` without leaking user existence.

#### Screen 03: Vocabulary Library (`/vocabulary`) & Screen 04: Quick Add Modal
- **UI Interaction**:
  - User browses term cards with search input (`/` shortcut) and live pagination.
  - User opens Quick Add modal (`C` shortcut or `Mod+K`), types word, and triggers `Mod+Enter`.
  - Real-time duplicate warning indicator appears if the term is already saved.
- **Backend Flow**:
  - `GET /api/v1/vocabularies/check-duplicate?term=...` executes a fast, case-insensitive index scan (`LOWER(term)`) scoped to the user.
  - `POST /api/v1/vocabularies` atomically inserts the vocabulary row and creates an initial `srs_items` record (`stage = 'LEARNING', interval_days = 0, next_review_at = NOW()`).
- **Edge Cases**:
  - Blank or oversized terms fail with `422 Unprocessable Content`.
  - Submitting a duplicate term returns `409 Conflict` referencing the existing vocabulary ID.

#### Screen 06: Vocabulary Detail & Edit (`/vocabulary/:id`)
- **UI Interaction**:
  - User inspects definitions, pronunciation, and examples.
  - User edits example sentences or notes (`Mod+S` to save).
  - User triggers delete with two-stage confirmation.
- **Backend Flow**:
  - `PUT /api/v1/vocabularies/{id}` verifies `user_id` ownership and updates editable metadata.
  - `DELETE /api/v1/vocabularies/{id}` performs an audit-preserving soft deletion (`deleted_at = NOW()`) and sets `srs_items.status = 'SUSPENDED'`. Historical review events are retained for streak integrity.

#### Screen 08: Flashcard SRS Review (`/review`)
- **UI Interaction**:
  - User enters distraction-free Focus Shell canvas.
  - Front card displays term and phonetic transcription; `Space` flips card to reveal definitions.
  - Keys `1`, `2`, `3`, `4` map to ratings: `AGAIN`, `HARD`, `GOOD`, `EASY`.
- **Backend Flow**:
  - `GET /api/v1/reviews/queue` returns up to 50 cards where `next_review_at <= NOW()` and `status = 'ACTIVE'`.
  - `POST /api/v1/reviews/submissions` receives client-generated `attempt_id`. It executes the SM-2 scheduling calculation, logs the review in `srs_review_history`, and logs a `REVIEW` event in `learning_activity_events`.
- **Idempotency Guarantee**:
  - Rapid double-tapping of grade keys sends the same `attempt_id`. The backend recognizes the existing submission, skips the SM-2 update and event insert, and replays the original `200 OK` response with `Idempotent-Replay: true`.

#### Screen 02 (Minimal): Dashboard
- **UI Interaction**:
  - Displays due count badge, current active streak, and daily goal progress bar.
- **Backend Flow**:
  - `GET /api/v1/dashboard/summary` computes due items via fast index scan on `srs_items(user_id, status, next_review_at)`.

---

### 3.2 Remaining Target MVP (Milestone M2)

#### Screen 02 (Full): Dashboard & Heatmap (`/dashboard`)
- **UI Interaction**:
  - GitHub-style 365-day monochrome heatmap canvas.
  - Hover tooltip displays exact local date, completed exercise count, and active streak status.
- **Backend Flow**:
  - `GET /api/v1/dashboard/heatmap?year=2026&timezone=Asia/Ho_Chi_Minh` queries `learning_activity_events`.
  - Aggregates action timestamps into local calendar dates (`created_at AT TIME ZONE timezone`) avoiding UTC date-skew.
  - Computes intensity level (0 to 4) dynamically based on user's target daily goal.

#### Screen 05: Bulk Vocabulary Import (`/vocabulary/import`)
- **UI Interaction**:
  - User drags and drops a CSV or TSV file.
  - UI shows a live preview table with parsed terms, detected duplicates, and invalid formatting flags before importing.
- **Backend Flow**:
  - `POST /api/v1/vocabularies/import?dry_run=true` parses file, checks terms against existing user vocabulary, and returns validation errors without database modifications.
  - `POST /api/v1/vocabularies/import?dry_run=false` executes batch insert in a single transaction (max 100 entries per file to enforce resource bounds).

#### Screen 07: Vocabulary Collections (`/collections`, `/collections/:id`)
- **UI Interaction**:
  - User creates custom decks (e.g., "IELTS Academic", "Business Meetings").
  - User adds/removes vocabulary items to collections.
  - User clicks "Review Collection" button to launch focused review session.
- **Backend Flow**:
  - Enforces database composite foreign keys `(collection_id, user_id)` and `(vocabulary_id, user_id)` preventing cross-user association.
  - Deleting a collection cascades only to `vocabulary_collections` join rows, leaving `vocabularies` intact.
  - Filtered review queue: `GET /api/v1/reviews/queue?collection_id={id}`.

#### Screen 09: Dictation Exercise & Diff (`/dictation/:id`)
- **UI Interaction**:
  - Focus Shell displays audio playback controls (speed 0.75x–1.0x, replay `Mod+R`).
  - User types heard sentence and presses `Mod+Enter` to submit.
  - UI displays an inline monochrome diff: inserted words underlined, omitted words struck through.
- **Backend Flow**:
  - `GET /api/v1/dictation/{contentId}` delivers audio URL and masked token structure (`word_count`, punctuation markers) while withholding the reference text.
  - `POST /api/v1/dictation/submissions` validates submitted text against canonical transcript using authoritative server-side Levenshtein diff engine.
  - An attempt with accuracy >= 80% logs a verified `DICTATION` action in `learning_activity_events`.

#### Screen 10: Shadowing Practice (`/shadowing/:id`)
- **UI Interaction**:
  - Native audio plays reference sentence with synchronized visual cues.
  - User clicks "Record Take" (`Space` to start/stop). Browser records audio locally via `MediaRecorder`.
  - User listens to instant playback comparison and provides a self-assessment rating (1–5 stars).
- **Backend Flow**:
  - Browser-only MVP: No raw audio upload is transmitted to server (eliminating storage overhead and privacy risks).
  - `POST /api/v1/shadowing/submissions` logs `duration_seconds` and `self_rating`.
  - Completed take logs a `SHADOWING` action in `learning_activity_events`.

#### Screen 11: Quiz Exercise & Results (`/quiz/:id`)
- **UI Interaction**:
  - Interactive multiple-choice or fill-in-the-blank diagnostic quiz (5–10 questions).
  - Immediate visual feedback on question submission; final summary card at end of quiz.
- **Backend Flow**:
  - `POST /api/v1/quizzes/attempts` instantiates attempt; question options are shuffled and canonical answers are withheld.
  - `POST /api/v1/quizzes/attempts/{id}/answers` validates each selected answer, returns immediate correctness, and updates score.
  - `POST /api/v1/quizzes/attempts/{id}/complete` closes attempt and emits a single `QUIZ` activity event.

#### Screen 13: User Settings (`/settings`)
- **UI Interaction**:
  - User updates display name, IANA timezone selector (searchable combo box), and daily goal target.
- **Backend Flow**:
  - `PUT /api/v1/settings/preferences` validates timezone against Java `ZoneId.getAvailableZoneIds()`.
  - Persisted timezone immediately takes effect on next dashboard and streak recalculation.

---

### 3.3 Deferred V1 Enhancements & Future Scope (Milestones M3 & M4)

The following features were identified during architectural review as out-of-scope for the Target MVP:

1. **Screen 12: Learning History Log (`/history`)**:
   - *Status*: Deferred to Milestone M3 (V1 Enhancement).
   - *Rationale*: Historical study activity is visible in aggregate on the Dashboard Heatmap. A detailed, paginated chronological audit log of every past attempt requires secondary indexing and dedicated audit view endpoints (`GET /api/v1/history`) that do not block core daily learning.
2. **Server-Side Speech Acoustic Evaluation**:
   - *Status*: Deferred to Milestone M4 (AI & Advanced Analytics).
   - *Rationale*: Native browser audio recording and self-assessment deliver immediate pedagogical value for Shadowing without introducing complex Whisper/Wav2Vec infrastructure or third-party API dependencies in MVP.
3. **Automated AI Example Sentence Generation**:
   - *Status*: Deferred to Milestone M4.
   - *Rationale*: Content seeding in MVP is driven by curated datasets (`learning_contents`) and manual user entry. LLM integrations will be introduced in Milestone M4.
4. **Anki `.apkg` File Export & Sync**:
   - *Status*: Deferred to Milestone M4.
   - *Rationale*: MVP provides complete CSV/TSV bulk import and in-app spaced repetition review, satisfying core user workflows without external synchronization.

---

## 4. Gap Analysis & Contract Alignment Review

| Requirement / Interaction | Screen Inventory Reference | API Contract Alignment | Resolution / Architecture Note |
| :--- | :--- | :--- | :--- |
| **Real-time Duplicate Word Checking** | Screen 03 & 04 (`input-safe`) | `GET /api/v1/vocabularies/check-duplicate` | Backed by index scan on `LOWER(term)`. Fast, debounced UI response. |
| **Bulk Import Validation & Dry-Run** | Screen 05 (`preview before save`) | `POST /api/v1/vocabularies/import?dry_run=true` | Returns validation report with row errors without writing to DB. |
| **Cross-User Deck Poisoning Prevention** | Screen 07 (`deck association`) | Composite FKs in DB & Path Verification | Enforced at DB engine (`collections(id, user_id)`) and Spring authorization. |
| **Accidental Review Double-Tap** | Screen 08 (`rapid keyboard 1-4`) | `attempt_id` Idempotency Cache | Returns `200 OK` with `Idempotent-Replay: true`. Prevents double SM-2 advancement. |
| **Unsaved Form Protection** | Screen 04, 06 (`Esc` safety guard) | Client-side dirty state tracking | Handled in React state (`isDirty === false`); no backend endpoint needed. |
| **Transcript Shielding in Dictation** | Screen 09 (`focus audio playback`) | `GET /api/v1/dictation/{id}` hides text | Sanitized DTO masks reference text until answer submission is scored. |
| **Timezone-Safe Heatmap Bucketing** | Screen 02 (`365-day monochrome grid`)| `GET /api/v1/dashboard/heatmap?timezone=...` | Database groups by `created_at AT TIME ZONE timezone` to match user calendar. |
| **Session Expiration Graceful Handling** | Screen 01 (`401 interceptor`) | Standard `ApiErrorResponse` (`UNAUTHORIZED`) | Client detects 401, stores pending route, and redirects to `/login`. |

---

## 5. Architectural Verification Sign-off

- [x] Every interactive screen in `docs/design/SCREEN_INVENTORY.md` has 100% backing API coverage for all Target MVP flows.
- [x] All 31 REST endpoints in `docs/api/API_CONTRACT.md` adhere strictly to `/api/v1` conventions, session cookie auth, and CSRF token propagation.
- [x] Data model integrity guarantees (composite FKs, soft deletes, submission idempotency) match the REST contract payloads.
- [x] No application source code, SQL migrations, or premature V1 endpoints have been generated.
