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

### 2.1 User Lifecycle: Registration to Account Deletion

```
[ Unregistered ] ──> [ Register / Validated ] ──> [ Active Learner ] ──> [ Deactivated / Erased ]
```

1. **Registration**:
   * User registers with valid email and password.
   * `app_users` record created with `timezone` set to client's detected IANA timezone (default `UTC`), `daily_goal_items = 20`, and `is_active = TRUE`.
   * Passwords hashed via BCrypt (work factor 12).
2. **Profile Updates**:
   * Changing `timezone` or `daily_goal_items` updates `updated_at`.
   * **Timezone Invariant**: Updating `timezone` **never** retroactively alters past dates in `learning_activity_events`. Historical local activity dates remain permanently fixed.
3. **Account Deletion (Complete Data Erasure Policy)**:
   * **Hard Delete Strategy for Account Closure**: Initiating account deletion executes:
     ```sql
     DELETE FROM app_users WHERE id = :userId;
     ```
   * **Atomic Cascade Execution**: PostgreSQL foreign keys with `ON DELETE CASCADE` automatically and atomically delete:
     * `vocabularies` (and their `vocabulary_collections` join rows)
     * `collections`
     * `srs_items` and `srs_review_history`
     * `dictation_attempts`
     * `shadowing_takes`
     * `quiz_attempts` and `quiz_answers`
     * `learning_activity_events`
   * **Media Storage Cleanup**: Spring Boot triggers asynchronous deletion of any uploaded audio recordings stored under `/data/audio/{userId}/` via `AudioStorageService.deleteAudio()`.

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
