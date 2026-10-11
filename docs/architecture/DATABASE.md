# EnglishFlow — PostgreSQL Database Blueprint & Data Integrity Design

**Document Version**: 2.0.0  
**Milestone**: M0 — Discovery & Blueprint (Task M0-T07)  
**Author**: Principal Database Architect, Senior Spring Boot Engineer & API Designer  
**Reviewed By**: AI Technical Lead  
**Database Engine**: PostgreSQL 16  
**Migration Tool**: Flyway  
**Status**: Corrected Implementation Blueprint  

---

## 1. Database Overview & Design Principles

The EnglishFlow relational database schema is engineered for strict multi-tenant data integrity, robust relational constraints, auditable event tracking, and transaction safety within a single PostgreSQL 16 database.

### 1.1 Core Principles
1. **Relational Guarantees for Cross-User Ownership**: Cross-user entity association is prevented not merely through application code, but via **database composite foreign keys**. A user cannot assign another user's vocabulary to their collection, nor can an SRS schedule track a word owned by someone else.
2. **Business-Level Submission Idempotency**: Idempotency is enforced at the business entity mutation level as well as the event sink level. A network retry cannot advance an SRS schedule twice, insert duplicate review logs, or double-count dictation scores.
3. **Historical Data Preservation on Vocabulary Archival**: Deleting a vocabulary entry from the active library employs **soft-deletion** (`deleted_at`), preserving historical review logs and learning statistics. Account closure executes an explicit, full **hard deletion** cascading to all user records.
4. **Calendar-Day Semantics for Habit Tracking**: Historical study days are permanent calendar dates (`DATE`) anchored to the user's active IANA timezone at event completion. Timezone updates never retroactively shift historical dates.
5. **Separation of Media Storage from Metadata**: Audio binaries are **never** stored in PostgreSQL columns. PostgreSQL stores media metadata and relative storage keys referencing pluggable filesystem/S3 storage. Shadowing recordings are strictly optional (`recording_storage_key` is nullable).
6. **No Bespoke Session Entities**: Spring Security manages HTTP servlet sessions in-memory for the single-node MVP without requiring custom database session tables.

---

## 2. Table Inventory (Reconciled: Exactly 13 Tables)

| # | Table Name | Owning Domain Module | Primary Purpose | Row Cardinality Profile |
| :-: | :--- | :--- | :--- | :--- |
| **1** | **`app_users`** | `user` / `auth` | User account identity, credentials (BCrypt), timezone, and daily study goal. | 1 row per registered user |
| **2** | **`vocabularies`** | `vocabulary` | User vocabulary terms, IPA, Vietnamese meanings, English definitions, examples. | 100 – 10,000 rows per user |
| **3** | **`collections`** | `vocabulary` | Custom user-created decks/collections for grouping vocabulary. | 1 – 50 rows per user |
| **4** | **`vocabulary_collections`** | `vocabulary` | Join table mapping words to collections with composite cross-user integrity. | 100 – 20,000 rows per user |
| **5** | **`srs_items`** | `review` | Spaced repetition state, SM-2 interval, ease factor, and due dates. | 1 row per active vocabulary word |
| **6** | **`srs_review_history`** | `review` | Detailed audit log of every flashcard review submission, grade, and attempt. | 10 – 100 rows per user/day |
| **7** | **`learning_contents`** | `learningcontent` | Curated exercises bank (dictation sentences, shadowing clips, quizzes). | 100 – 10,000 system rows |
| **8** | **`dictation_attempts`** | `dictation` | Submitted transcriptions, accuracy scores, and evaluation metadata. | 1 – 20 rows per user/day |
| **9** | **`shadowing_takes`** | `shadowing` | Self-assessed speech takes and optional audio recording metadata. | 1 – 20 rows per user/day |
| **10** | **`quiz_questions`** | `quiz` | Individual quiz prompts, options JSONB, and canonical answers. | 5 – 20 questions per quiz pack |
| **11** | **`quiz_attempts`** | `quiz` | Quiz session attempts, composite scores, and overall status. | 1 – 5 rows per user/day |
| **12** | **`quiz_answers`** | `quiz` | Individual question responses and validation within a quiz attempt. | 5 – 50 rows per user/day |
| **13** | **`learning_activity_events`**| `analytics` | Immutable, append-only event sink recording verified learning actions. | 5 – 150 rows per user/day |

---

## 3. Detailed Table Specifications & Integrity Constraints

---

### 3.1 Module: `user` & `auth`

#### Table: `app_users`
Stores user identity, authentication credentials, and learning environment settings.

```sql
CREATE TABLE app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    daily_goal_items INT NOT NULL DEFAULT 20,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_app_users_email UNIQUE (email),
    CONSTRAINT chk_app_users_email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_app_users_daily_goal CHECK (daily_goal_items >= 1 AND daily_goal_items <= 500)
);

CREATE INDEX idx_app_users_email ON app_users (email);
```

---

### 3.2 Module: `vocabulary`

#### Table: `vocabularies`
Stores user-owned vocabulary entries with soft-deletion support.

```sql
CREATE TABLE vocabularies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    term VARCHAR(150) NOT NULL,
    normalized_term VARCHAR(150) NOT NULL,
    part_of_speech VARCHAR(32) NOT NULL,
    phonetic_ipa VARCHAR(100),
    vietnamese_meaning TEXT NOT NULL,
    english_definition TEXT,
    example_sentence TEXT,
    example_translation TEXT,
    notes TEXT,
    source VARCHAR(100),
    difficulty_level VARCHAR(8),
    deleted_at TIMESTAMPTZ, -- Soft deletion: preserves historical reviews
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_vocab_id_user UNIQUE (id, user_id), -- Required for composite FK integrity
    CONSTRAINT chk_vocab_part_of_speech CHECK (part_of_speech IN ('noun', 'verb', 'adjective', 'adverb', 'idiom', 'phrase', 'preposition', 'conjunction', 'other')),
    CONSTRAINT chk_vocab_difficulty CHECK (difficulty_level IS NULL OR difficulty_level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2'))
);

CREATE INDEX idx_vocab_user_active ON vocabularies (user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_vocab_user_normalized ON vocabularies (user_id, normalized_term) WHERE deleted_at IS NULL;
CREATE INDEX idx_vocab_search_gin ON vocabularies USING gin (to_tsvector('english', term || ' ' || COALESCE(english_definition, '') || ' ' || COALESCE(vietnamese_meaning, ''))) WHERE deleted_at IS NULL;
```

#### Table: `collections`
Stores custom thematic decks created by users.

```sql
CREATE TABLE collections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    title VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_collection_id_user UNIQUE (id, user_id), -- Required for composite FK integrity
    CONSTRAINT uq_collection_user_title UNIQUE (user_id, title)
);

CREATE INDEX idx_collections_user_id ON collections (user_id);
```

#### Table: `vocabulary_collections`
Join table with **composite foreign key enforcement** guaranteeing that a word and collection belong to the exact same user.

```sql
CREATE TABLE vocabulary_collections (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    collection_id UUID NOT NULL,
    vocabulary_id UUID NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (collection_id, vocabulary_id),
    -- Hard database constraint: prevents assigning User A's word to User B's collection
    CONSTRAINT fk_vocab_col_collection FOREIGN KEY (collection_id, user_id) 
        REFERENCES collections(id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_vocab_col_vocabulary FOREIGN KEY (vocabulary_id, user_id) 
        REFERENCES vocabularies(id, user_id) ON DELETE CASCADE
);

CREATE INDEX idx_vocab_col_user_vocab ON vocabulary_collections (user_id, vocabulary_id);
```

---

### 3.3 Module: `review` (Spaced Repetition Engine)

#### Table: `srs_items`
Tracks memory retention state with composite ownership enforcement.

```sql
CREATE TABLE srs_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    vocabulary_id UUID NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'NEW',
    algorithm VARCHAR(16) NOT NULL DEFAULT 'SM2',
    repetition_count INT NOT NULL DEFAULT 0,
    interval_days INT NOT NULL DEFAULT 0,
    ease_factor NUMERIC(4, 2) NOT NULL DEFAULT 2.50,
    due_date DATE NOT NULL DEFAULT CURRENT_DATE,
    last_reviewed_at TIMESTAMPTZ,
    version INT NOT NULL DEFAULT 0, -- Optimistic locking
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- FSRS extensible columns (reserved for future migration)
    fsrs_stability NUMERIC(8, 4),
    fsrs_difficulty NUMERIC(8, 4),
    CONSTRAINT uq_srs_id_user UNIQUE (id, user_id), -- Required for composite FK integrity
    CONSTRAINT uq_srs_user_vocab UNIQUE (user_id, vocabulary_id),
    CONSTRAINT fk_srs_user_vocabulary FOREIGN KEY (vocabulary_id, user_id)
        REFERENCES vocabularies(id, user_id) ON DELETE CASCADE,
    CONSTRAINT chk_srs_state CHECK (state IN ('NEW', 'LEARNING', 'REVIEW', 'SUSPENDED')),
    CONSTRAINT chk_srs_ease_factor CHECK (ease_factor >= 1.30)
);

CREATE INDEX idx_srs_user_due ON srs_items (user_id, due_date) WHERE state != 'SUSPENDED';
CREATE INDEX idx_srs_user_vocab ON srs_items (user_id, vocabulary_id);
```

#### Table: `srs_review_history`
Audit log of individual card reviews with attempt-level idempotency and payload hashing.

```sql
CREATE TABLE srs_review_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    srs_item_id UUID NOT NULL,
    attempt_id UUID NOT NULL,
    grade INT NOT NULL, -- 1 = Again, 2 = Hard, 3 = Good, 4 = Easy
    previous_interval_days INT NOT NULL,
    new_interval_days INT NOT NULL,
    previous_ease_factor NUMERIC(4, 2) NOT NULL,
    new_ease_factor NUMERIC(4, 2) NOT NULL,
    duration_seconds INT DEFAULT 0,
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_srs_review_attempt UNIQUE (user_id, attempt_id),
    CONSTRAINT fk_srs_history_item FOREIGN KEY (srs_item_id, user_id)
        REFERENCES srs_items(id, user_id) ON DELETE CASCADE,
    CONSTRAINT chk_srs_history_grade CHECK (grade BETWEEN 1 AND 4)
);

CREATE INDEX idx_srs_history_user_item ON srs_review_history (user_id, srs_item_id);
CREATE INDEX idx_srs_history_reviewed_at ON srs_review_history (user_id, reviewed_at);
```

---

### 3.4 Module: `learningcontent`

#### Table: `learning_contents`
Curated bank of native reference sentences, audio clips, and exercises.

```sql
CREATE TABLE learning_contents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type VARCHAR(32) NOT NULL, -- DICTATION, SHADOWING, QUIZ
    title VARCHAR(150) NOT NULL,
    cefr_level VARCHAR(8) NOT NULL,
    topic_tag VARCHAR(64) NOT NULL,
    transcript_text TEXT NOT NULL,
    transcript_segments JSONB,
    audio_storage_key VARCHAR(255),
    audio_duration_seconds NUMERIC(6, 2),
    audio_mime_type VARCHAR(64),
    audio_file_size_bytes BIGINT,
    source_attribution VARCHAR(255),
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_content_type CHECK (content_type IN ('DICTATION', 'SHADOWING', 'QUIZ')),
    CONSTRAINT chk_content_cefr CHECK (cefr_level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2'))
);

CREATE INDEX idx_content_type_level ON learning_contents (content_type, cefr_level) WHERE is_published = TRUE;
```

---

### 3.5 Module: `dictation`

#### Table: `dictation_attempts`
Records user transcription submissions and accuracy metrics.

```sql
CREATE TABLE dictation_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES learning_contents(id) ON DELETE RESTRICT,
    attempt_id UUID NOT NULL,
    submitted_transcript TEXT NOT NULL,
    accuracy_score NUMERIC(5, 2) NOT NULL, -- 0.00 to 100.00%
    word_count INT NOT NULL,
    error_count INT NOT NULL,
    duration_seconds INT DEFAULT 0,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dictation_user_attempt UNIQUE (user_id, attempt_id),
    CONSTRAINT chk_dictation_accuracy CHECK (accuracy_score >= 0.00 AND accuracy_score <= 100.00)
);

CREATE INDEX idx_dictation_user_content ON dictation_attempts (user_id, content_id);
CREATE INDEX idx_dictation_user_date ON dictation_attempts (user_id, completed_at);
```

---

### 3.6 Module: `shadowing`

#### Table: `shadowing_takes`
Tracks shadowing practice sessions. Audio recording upload is strictly optional.

```sql
CREATE TABLE shadowing_takes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES learning_contents(id) ON DELETE RESTRICT,
    attempt_id UUID NOT NULL,
    self_rating VARCHAR(32) NOT NULL, -- NEEDS_WORK, ACCEPTABLE, GOOD, FLUENT
    recording_storage_key VARCHAR(255), -- NULLABLE: supports browser-only practice
    recording_duration_seconds NUMERIC(6, 2),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_shadowing_user_attempt UNIQUE (user_id, attempt_id),
    CONSTRAINT chk_shadowing_rating CHECK (self_rating IN ('NEEDS_WORK', 'ACCEPTABLE', 'GOOD', 'FLUENT'))
);

CREATE INDEX idx_shadowing_user_content ON shadowing_takes (user_id, content_id);
CREATE INDEX idx_shadowing_user_date ON shadowing_takes (user_id, completed_at);
```

---

### 3.7 Module: `quiz`

#### Table: `quiz_questions`
Stores individual questions belonging to quiz content packs.

```sql
CREATE TABLE quiz_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id UUID NOT NULL REFERENCES learning_contents(id) ON DELETE CASCADE,
    prompt TEXT NOT NULL,
    question_type VARCHAR(32) NOT NULL, -- MULTIPLE_CHOICE, CLOZE
    options JSONB NOT NULL,
    correct_answer TEXT NOT NULL,
    explanation TEXT,
    order_index INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_quiz_q_type CHECK (question_type IN ('MULTIPLE_CHOICE', 'CLOZE'))
);

CREATE INDEX idx_quiz_questions_content ON quiz_questions (content_id, order_index);
```

#### Table: `quiz_attempts`
Represents an overall quiz session attempt.

```sql
CREATE TABLE quiz_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES learning_contents(id) ON DELETE RESTRICT,
    attempt_id UUID NOT NULL,
    total_questions INT NOT NULL,
    correct_answers INT NOT NULL DEFAULT 0,
    score_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED',
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_quiz_user_attempt UNIQUE (user_id, attempt_id),
    CONSTRAINT chk_quiz_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED'))
);

CREATE INDEX idx_quiz_attempts_user ON quiz_attempts (user_id, completed_at);
```

#### Table: `quiz_answers`
Stores individual question answers within a quiz attempt.

```sql
CREATE TABLE quiz_answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_attempt_id UUID NOT NULL REFERENCES quiz_attempts(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES quiz_questions(id) ON DELETE RESTRICT,
    user_answer TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    answered_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_quiz_answers_attempt ON quiz_answers (quiz_attempt_id);
```

---

### 3.8 Module: `analytics`

#### Table: `learning_activity_events`
Immutable, append-only event sink recording every verified learning action.

```sql
CREATE TABLE learning_activity_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    event_type VARCHAR(32) NOT NULL,
    attempt_id UUID NOT NULL,
    completed_at_utc TIMESTAMPTZ NOT NULL,
    timezone_at_event VARCHAR(64) NOT NULL,
    historical_local_date DATE NOT NULL,
    duration_seconds INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_activity_user_attempt UNIQUE (user_id, event_type, attempt_id),
    CONSTRAINT chk_activity_event_type CHECK (event_type IN ('FLASHCARD_REVIEW', 'DICTATION_SENTENCE', 'SHADOWING_TAKE', 'QUIZ_QUESTION'))
);

CREATE INDEX idx_activity_user_local_date ON learning_activity_events (user_id, historical_local_date);
CREATE INDEX idx_activity_user_utc ON learning_activity_events (user_id, completed_at_utc);
```

---

## 4. Business-Level Idempotency & Replay Strategy

### 4.1 Business-Operation Protection vs. Activity Logging
Idempotency is enforced within the primary business service before and during event recording.

```
Client Request (attempt_id, payload)
       │
       ▼
1. Query Attempt Store (e.g. srs_review_history by user_id and attempt_id)
       │
       ├── FOUND?
       │     ├── Validate Payload Match:
       │     │     ├── IDENTICAL: Return Cached Result (HTTP 200, Idempotent-Replay: true)
       │     │     └── MISMATCH:  Return 409 Conflict ("Idempotency key reused with different parameters")
       │     └── (Zero state mutations. Zero event log insertions.)
       │
       └── NOT FOUND:
             Execute in @Transactional Boundary:
               a. Mutate Business State (SM-2 update, srs_items)
               b. Insert Attempt History (srs_review_history)
               c. Insert Activity Event (learning_activity_events)
             Commit & Return HTTP 200/201.
```

### 4.2 Concurrent Duplicate Submissions
If identical requests arrive concurrently at the same millisecond:
* The first transaction acquires the row lock on `srs_items` and commits.
* The second transaction fails immediately on `uq_srs_review_attempt (user_id, attempt_id)`.
* Spring Boot's transaction wrapper catches `DataIntegrityViolationException`, reads the newly committed attempt record, and safely returns the authoritative result.

---

## 5. Query Patterns & Indexing Strategy

### 5.1 Primary Query Workloads
1. **SRS Due Queue**: `SELECT * FROM srs_items WHERE user_id = :userId AND due_date <= :localDate AND state != 'SUSPENDED'`.
   * *Index*: Partial composite B-tree `idx_srs_user_due (user_id, due_date) WHERE state != 'SUSPENDED'`.
2. **Annual Heatmap Aggregation**: `SELECT historical_local_date, count(*) FROM learning_activity_events WHERE user_id = :userId AND historical_local_date BETWEEN :start AND :end GROUP BY historical_local_date`.
   * *Index*: Composite B-tree `idx_activity_user_local_date (user_id, historical_local_date)`.
3. **Vocabulary Autocomplete/Search**: Normalized prefix and full-text search.
   * *Index*: GIN expression index on `tsvector` and B-tree on `(user_id, normalized_term)`.

### 5.2 Scaling Bottleneck & Verification Plan
* As `learning_activity_events` scales past 10 million rows across all users, PostgreSQL range partitioning by year (`PARTITION BY RANGE (historical_local_date)`) will be evaluated.
* **Verification Standard**: Query performance will be validated using `EXPLAIN (ANALYZE, BUFFERS)` in Milestone M1 integration testing against populated Testcontainers fixtures, rather than making unverified microsecond performance claims.

---

## 6. Flyway Migration & Seeding Separation

```
backend/src/main/resources/db/
  ├── migration/                              # Production & Dev Base Schema
  │   ├── V1__init_user_and_auth.sql
  │   ├── V2__init_vocabulary_and_collections.sql
  │   ├── V3__init_srs_engine.sql
  │   ├── V4__init_learning_content.sql
  │   ├── V5__init_study_modalities.sql
  │   └── V6__init_activity_events.sql
  │
  └── seed/dev/                               # Strictly Dev / Test Profiles Only
      └── R__seed_dev_reference_content.sql  # Curated public-domain sentences & clips
```

* **Rule**: Production migrations contain zero dummy test data. Educational reference sentences are seeded via profile-activated scripts or administrative ingestion APIs.
