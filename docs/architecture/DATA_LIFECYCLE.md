# EnglishFlow — Data Lifecycle & Consistency Rules

**Document Version**: 2.0.0  
**Milestone**: M0 — Discovery & Blueprint (Task M0-T07)  
**Author**: Principal Database Architect, Senior Spring Boot Engineer & API Designer  
**Reviewed By**: AI Technical Lead  
**Scope**: Target MVP Relational State Machines, Invariants & Consistency Policies  
**Status**: Corrected Implementation Specification  

---

## 1. Overview & Lifecycle Philosophy

Data in EnglishFlow moves through distinct operational states across learning modalities. Maintaining data consistency across multi-user environments requires explicit rules for creation, mutation, idempotency, cascade behaviors, and deletion.

---

## 2. Entity Lifecycle Specifications

---

### 2.1 User Lifecycle: Registration, Password Change & Account Deletion

```
[ Unregistered ] ──> [ Register / Validated ] ──> [ Active Learner ] ──> [ Deactivated / Erased ]
```

1. **Registration**:
   * User registers with valid email and password.
   * `app_users` record created with `timezone` set to client's detected IANA timezone (default `UTC`), `daily_goal_items = 20`, `preferred_audio_speed = 1.0`, and `is_active = TRUE`.
   * Passwords hashed via BCrypt (work factor 12).
2. **Profile Updates**:
   * Changing `timezone`, `daily_goal_items`, or `preferred_audio_speed` updates `updated_at`.
   * **Timezone Invariant**: Updating `timezone` **never** retroactively alters past dates in `learning_activity_events`. Historical local activity dates (`historical_local_date`) remain permanently fixed.
3. **Password Change & Multi-Session Policy (Policy A)**:
   * Verification enforces current password match via `PasswordEncoder`, registration complexity rules, confirmation match, and disallows new password identical to current.
   * Upon successful password update within `@Transactional`, the application rotates the caller's session ID (`HttpServletRequest.changeSessionId()`) to mitigate session fixation attacks.
   * **Multi-Session Policy (Policy A)**: Under standard Spring Security servlet session management, concurrent sessions active on other devices/browsers remain valid until expiration or explicit logout. Multi-device session revocation requires centralized session registry infrastructure (e.g., Spring Session Redis) deferred to future enterprise milestones.
4. **Account Deletion Transaction Boundary & Session Invalidation**:
   * **Boundary Isolation**: Database hard deletion and HTTP servlet session invalidation are strictly decoupled across transaction boundaries.
   * Database deletion executes inside an isolated `@Transactional` service boundary:
     ```sql
     DELETE FROM app_users WHERE id = :userId;
     ```
   * **Post-Commit Invalidation**: The caller's HTTP session (`session.invalidate()`), Spring Security context (`SecurityContextHolder.clearContext()`), and `JSESSIONID` cookie (`maxAge = 0`) are cleared **strictly after** the database transaction commits successfully.
   * **Rollback Safety**: If any database constraint violation or unexpected exception aborts the deletion transaction, the session and credentials remain completely valid, preventing users from entering a broken or misleading half-deleted state.
5. **Relational Erasure Matrix (PostgreSQL Referential Integrity)**:
   * Foreign keys with `ON DELETE CASCADE` guarantee complete, atomic erasure of all user-owned data from the active operational database:
     * `vocabularies` (`user_id -> app_users(id) ON DELETE CASCADE`)
     * `collections` (`user_id -> app_users(id) ON DELETE CASCADE`)
     * `vocabulary_collections` (`user_id -> app_users(id) ON DELETE CASCADE`)
     * `srs_items` (`user_id -> app_users(id) ON DELETE CASCADE`)
     * `srs_review_history` (`user_id -> app_users(id) ON DELETE CASCADE`)
     * `dictation_attempts` (`user_id -> app_users(id) ON DELETE CASCADE`)
     * `shadowing_takes` (`user_id -> app_users(id) ON DELETE CASCADE`)
     * `quiz_attempts` (`user_id -> app_users(id) ON DELETE CASCADE`)
     * `quiz_answers` (`quiz_attempt_id -> quiz_attempts(id) ON DELETE CASCADE`)
     * `learning_activity_events` (`user_id -> app_users(id) ON DELETE CASCADE`)
   * **Shared Reference Content Strict Preservation**:
     * `learning_contents` (shared dictation/shadowing content bank) has no foreign key to `app_users` and is 100% preserved.
     * `quiz_questions` (`content_id -> learning_contents(id) ON DELETE CASCADE`) is independent of users and is 100% preserved.
6. **Privacy, Erasure & GDPR Retention Scope Clarification**:
   * **In-Scope**: Immediate atomic hard deletion of all personal data, vocabulary records, study history, and attempts from the live PostgreSQL relational database.
   * **Media Storage Cleanup**: Asynchronous removal of optional recorded audio files under `/data/audio/{userId}/`.
   * **Out-of-Scope / Practical Retention Limitations**:
     * *Database Backups & WAL*: Point-in-time recovery archives and database backups retain data until standard backup retention expiration.
     * *Application & Server Access Logs*: Sanitized web server logs (containing timestamp and route without credentials) persist according to log rotation policies.
     * *External Proxies / CDNs*: Third-party edge/reverse proxies retain access logs per external infrastructure configurations.
   * *Compliance Statement*: Application database account erasure satisfies core "Right to Erasure" requirements within the operational data store; full enterprise GDPR compliance requires organizational backup aging and data processing agreements.

---

### 2.2 Vocabulary Lifecycle: Soft Deletion & Duplicate Management

```
[ Input Term ] ──> [ Normalize & Check ] ──> [ Active Vocabulary ] ──> [ Soft Deleted ]
                                                       │                        │
                                                       ▼                        ▼
                                            [ Auto-Create SRS Item ]  [ SRS Suspended, History Intact ]
```

1. **Creation & Normalization**:
   * When a word is created (via Quick Add or Vocabulary form), the application sets:
     $$\text{normalized\_term} = \text{lower}(\text{trim}(\text{term}))$$
   * **Duplicate Advisory vs. Hard Constraint**:
     * A hard unique constraint on `(user_id, normalized_term)` is **banned**, because legitimate homographs with different parts of speech or definitions exist (e.g., "tear" /noun/ = droplet vs. "tear" /verb/ = rip).
     * If a word with identical `(user_id, normalized_term, part_of_speech)` already exists, the UI presents an advisory warning (*"You already have 'lead' (noun) in your library. Add another entry?"*), but permits creation.
2. **Automatic SRS Provisioning**:
   * In the same `@Transactional` boundary as vocabulary creation, the system automatically inserts an `srs_items` row with `state = 'NEW'`, `interval_days = 0`, `ease_factor = 2.50`, and `due_date = CURRENT_DATE`.
3. **Soft Deletion Policy**:
   * Deleting a vocabulary entry from the active library executes a **soft-deletion**:
     ```sql
     UPDATE vocabularies SET deleted_at = NOW() WHERE id = :id AND user_id = :userId;
     UPDATE srs_items SET state = 'SUSPENDED' WHERE vocabulary_id = :id AND user_id = :userId;
     ```
   * **Historical Preservation Guarantee**: The word disappears from active vocabulary lists and review queues, but all historical entries in `srs_review_history` and `learning_activity_events` remain completely intact. The user's historical study analytics and heatmap are never retroactively corrupted.

---

### 2.3 Bulk Vocabulary Import Lifecycle

1. **Ingestion & Pre-Flight Validation**:
   * Client uploads CSV/TSV file (max 1,000 rows, 2 MB).
   * Server parses rows in memory and validates required fields (`Term`, `Vietnamese Meaning` or `English Definition`).
   * Dry-run validation returns counts: `{ totalRows: 120, validRows: 118, errorRows: 2 }`.
2. **Atomic Batch Insertion**:
   * User confirms import. Insertion executes within a single database transaction using JDBC batching (`batch_size = 100`).
   * For each valid row:
     1. Insert into `vocabularies`.
     2. Insert corresponding initial `srs_items` record.
     3. If collection specified, insert into `vocabulary_collections`.
3. **Strict Heatmap Exclusion**:
   * Bulk import inserts **zero rows** into `learning_activity_events`. Importing words expands reference material; it does not constitute study.

---

### 2.4 Vocabulary Collections: Safe Non-Cascading Deletion

```
[ Create Collection ] ──> [ Assign Words ] ──> [ Browse / Study ] ──> [ Delete Collection ]
                                                                               │
                                                          ┌────────────────────┴────────────────────┐
                                                          ▼                                         ▼
                                              [ Delete Join Table Rows ]              [ Words Remain Safe in Library ]
```

1. **Creation**:
   * User creates a collection with a unique title (`uq_collection_user_title`).
2. **Word Assignment & Removal with Composite Integrity**:
   * Assigning a word inserts `(user_id, collection_id, vocabulary_id)` into `vocabulary_collections`.
   * **Database Constraint Guarantee**: Foreign keys `(collection_id, user_id)` referencing `collections(id, user_id)` and `(vocabulary_id, user_id)` referencing `vocabularies(id, user_id)` guarantee that cross-user assignment is rejected by the database engine.
3. **Safe Deletion Invariant**:
   * Deleting a collection deletes only the `collections` row and its associated `vocabulary_collections` join rows (`ON DELETE CASCADE`).
   * **The underlying vocabulary entries and their SRS review schedules remain 100% untouched.**

---

### 2.5 Spaced Repetition (SM-2) Scheduling Lifecycle

```
[ NEW ] ──> Grade Card (1-4) ──> [ LEARNING ] ──> Grade >= 3 ──> [ REVIEW ] ──> [ SUSPENDED ]
                                      │                                 ▲
                                      └─── Grade 1 (Again) ─────────────┘
```

1. **State Progression**:
   * `NEW`: Freshly added card, never reviewed.
   * `LEARNING`: In initial learning steps (repetition count = 0, interval = 1 day).
   * `REVIEW`: Graduated card following SM-2 interval progression.
   * `SUSPENDED`: User manually pauses reviews or card is soft-deleted.
2. **SM-2 Algorithm Execution**:
   * Upon submitting grade $q \in \{1, 2, 3, 4\}$:
     * New Ease Factor: $EF' = \max(1.30, EF + (0.1 - (5 - q) \times (0.08 + (5 - q) \times 0.02)))$.
     * If $q = 1$ (Again): `repetition_count = 0`, `interval_days = 1`, `state = 'LEARNING'`.
     * If $q \ge 2$:
       * If `repetition_count = 0`: `interval_days = 1`.
       * If `repetition_count = 1`: `interval_days = 6`.
       * If `repetition_count > 1`: `interval_days = round(interval_days * EF')`.
       * `repetition_count = repetition_count + 1`, `state = 'REVIEW'`.
     * `due_date = current_local_date + interval_days`.
3. **Transactional Action Recording**:
   * In the same transaction:
     1. Update `srs_items` (optimistic lock checks `version`).
     2. Insert audit row in `srs_review_history` with `attempt_id`.
     3. Insert verified activity event in `learning_activity_events` (`event_type = 'FLASHCARD_REVIEW'`).

---

### 2.6 Daily Dictation Lifecycle

1. **Sentence Presentation**: Client loads curated `learning_contents` record (audio URL and masked transcript).
2. **Transcription Submission**: User types transcript and submits with client-generated `attempt_id`.
3. **Diff Scoring (Separation of Concerns)**:
   * **Backend Evaluation**: Normalizes strings (lowercasing, punctuation stripping) and computes edit distance to produce `accuracy_score` ($0.00$ to $100.00\%$), `word_count`, and `error_count`.
   * **Client-Side Diff Rendering**: The visual inline diff (strikethrough for omissions, underline for additions) is computed dynamically in React via Myers' diff algorithm.
4. **Persistence & Activity**:
   * In a single transaction:
     1. Insert `dictation_attempts` with `accuracy_score`.
     2. Insert `learning_activity_events` (`event_type = 'DICTATION_SENTENCE'`).

---

### 2.7 Shadowing Lifecycle & Optional Audio Persistence

1. **A/B Auditory Mimicry**:
   * User listens to reference native audio.
   * User records speech take via browser Web Audio API / `MediaRecorder`.
   * User listens to user take in browser memory (Blob URL) and reference audio.
2. **Optional Audio Retention Invariant**:
   * **Browser-Only Mode (Default)**: User self-grades performance (e.g., "Good") without uploading audio. Database stores `recording_storage_key = NULL`.
   * **Persistent Mode (Optional)**: User chooses to save recording. Audio uploaded via multipart request; stored under `/data/audio/{userId}/{takeId}.webm`; storage key saved.
3. **Activity Qualification**:
   * Submitting a valid self-assessment rating inserts a `shadowing_takes` row and transactionally publishes a `SHADOWING_TAKE` event to `learning_activity_events`.

---

### 2.8 Diagnostic Quiz Lifecycle

1. **Quiz Initialization**: System serves a 5–10 question quiz from `quiz_questions`.
2. **Question Submission**:
   * User selects answer. Server validates against `correct_answer`.
   * Insert row in `quiz_answers`.
   * Transactionally publish 1 `QUIZ_QUESTION` event to `learning_activity_events`.
3. **Quiz Completion**:
   * System aggregates total correct answers, updates `quiz_attempts` with final `score_percentage` and `status = 'COMPLETED'`.

---

## 3. Business-Level Idempotency, Replay & Concurrency

### 3.1 Attempt Deduplication vs. Re-Attempts

| Scenario | Client Behavior | Database & Service Handling | Outcome |
| :--- | :--- | :--- | :--- |
| **Network Retry (Identical Payload)** | Client resends request with identical `attempt_id` and matching payload. | Service queries `srs_review_history` by `(user_id, attempt_id)`. Record found; payload hash matches. | Replays cached authoritative state. Returns `HTTP 200 OK` with header `Idempotent-Replay: true`. Zero duplicate mutations. |
| **Idempotency Key Reuse (Different Payload)** | Client sends existing `attempt_id` with conflicting grade/parameters. | Record found, but payload hash differs. | Rejects with `HTTP 409 Conflict` ("Attempt ID reused with conflicting payload"). |
| **Legitimate Re-Review** | User grades the same flashcard again later today or tomorrow. | Client generates a **fresh `attempt_id`** (new UUID). | Insert succeeds. Card interval updates again; new activity action logged. |
| **Concurrent Browser Tabs** | User submits grades for the same card in two open tabs simultaneously. | First transaction commits. Second transaction detects `version` mismatch on `srs_items`. | Throws `OptimisticLockException`. Returns `HTTP 409 Conflict` prompting client queue refresh. |

---

## 4. Timezone, Streak & Calendar Date Continuity

```
[ Event Completed (UTC) ] ──> Convert to User's IANA Timezone ──> [ Historical Local Date (DATE) ]
                                                                              │
                                                                              ▼
                                                                [ Permanent Calendar Day Sink ]
```

### 4.1 Immutability of Historical Dates
* The calendar date of an activity is stamped at completion time using the user's active timezone:
  $$\text{historical\_local\_date} = \text{DATE}(\text{completed\_at\_utc} \text{ AT TIME ZONE } \text{timezone\_at\_event})$$
* **Invariant**: If a user studies in Tokyo (`Asia/Tokyo`) at 11:30 PM on Oct 12 and subsequently updates their timezone to London (`Europe/London`), that past event **remains permanently stamped `2026-10-12`**. Historical study records are never shifted retroactively.

### 4.2 Streak State Machine Across Calendar Days
Streaks evaluate consecutive calendar days, accommodating 23-hour and 25-hour Daylight Saving Time transitions naturally through calendar `DATE` boundaries:

```
Yesterday Studied? ──── YES ───> Today Studied? ──── YES ───> [ ACTIVE (Streak Incremented) ]
       │                                │
       │                                └─── NO ────> [ ACTIVE_PENDING_TODAY (Preserved) ]
       │                                                      (Maintained until 23:59:59 local time)
       │
       └─── NO ────> Today Studied? ──── YES ───> [ ACTIVE (New Streak = 1) ]
                            │
                            └─── NO ────> [ BROKEN / ZERO (Streak = 0) ]
```
* **Pending Today Rule**: A streak **never resets during the active day**. If a user studied yesterday, their streak remains displayed as active throughout today until local midnight passes without study.
