# EnglishFlow — Entity Relationship Diagram (ERD)

**Document Version**: 2.0.0  
**Milestone**: M0 — Discovery & Blueprint (Task M0-T07)  
**Author**: Principal Database Architect, Senior Spring Boot Engineer & API Designer  
**Reviewed By**: AI Technical Lead  
**Scope**: Target MVP Relational Persistence Model (Reconciled: 13 Tables)  
**Status**: Corrected Implementation Specification  

---

## 1. Visual Entity Relationship Diagram (Mermaid)

```mermaid
erDiagram
    app_users ||--o{ vocabularies : "owns"
    app_users ||--o{ collections : "owns"
    app_users ||--o{ srs_items : "schedules"
    app_users ||--o{ srs_review_history : "logs"
    app_users ||--o{ dictation_attempts : "transcribes"
    app_users ||--o{ shadowing_takes : "records"
    app_users ||--o{ quiz_attempts : "attempts"
    app_users ||--o{ learning_activity_events : "earns"

    collections ||--o{ vocabulary_collections : "groups"
    vocabularies ||--o{ vocabulary_collections : "assigned_to"
    vocabularies ||--|| srs_items : "schedules_for"

    srs_items ||--o{ srs_review_history : "reviews"

    learning_contents ||--o{ dictation_attempts : "referenced_by"
    learning_contents ||--o{ shadowing_takes : "referenced_by"
    learning_contents ||--o{ quiz_questions : "contains"
    learning_contents ||--o{ quiz_attempts : "referenced_by"

    quiz_attempts ||--o{ quiz_answers : "contains"
    quiz_questions ||--o{ quiz_answers : "answers"

    app_users {
        UUID id PK
        VARCHAR email UK
        VARCHAR password_hash
        VARCHAR display_name
        VARCHAR timezone
        INT daily_goal_items
        BOOLEAN is_active
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    vocabularies {
        UUID id PK
        UUID user_id FK
        VARCHAR term
        VARCHAR normalized_term
        VARCHAR part_of_speech
        VARCHAR phonetic_ipa
        TEXT vietnamese_meaning
        TEXT english_definition
        TEXT example_sentence
        TEXT example_translation
        VARCHAR difficulty_level
        TIMESTAMPTZ deleted_at
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    collections {
        UUID id PK
        UUID user_id FK
        VARCHAR title
        TEXT description
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    vocabulary_collections {
        UUID user_id FK
        UUID collection_id PK,FK
        UUID vocabulary_id PK,FK
        TIMESTAMPTZ added_at
    }

    srs_items {
        UUID id PK
        UUID user_id FK
        UUID vocabulary_id FK,UK
        VARCHAR state
        VARCHAR algorithm
        INT repetition_count
        INT interval_days
        NUMERIC ease_factor
        DATE due_date
        TIMESTAMPTZ last_reviewed_at
        INT version
        NUMERIC fsrs_stability
        NUMERIC fsrs_difficulty
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    srs_review_history {
        UUID id PK
        UUID user_id FK
        UUID srs_item_id FK
        UUID attempt_id UK
        INT grade
        INT previous_interval_days
        INT new_interval_days
        NUMERIC previous_ease_factor
        NUMERIC new_ease_factor
        INT duration_seconds
        TIMESTAMPTZ reviewed_at
    }

    learning_contents {
        UUID id PK
        VARCHAR content_type
        VARCHAR title
        VARCHAR cefr_level
        VARCHAR topic_tag
        TEXT transcript_text
        JSONB transcript_segments
        VARCHAR audio_storage_key
        NUMERIC audio_duration_seconds
        VARCHAR audio_mime_type
        BIGINT audio_file_size_bytes
        BOOLEAN is_published
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    dictation_attempts {
        UUID id PK
        UUID user_id FK
        UUID content_id FK
        UUID attempt_id UK
        TEXT submitted_transcript
        NUMERIC accuracy_score
        INT word_count
        INT error_count
        INT duration_seconds
        TIMESTAMPTZ completed_at
    }

    shadowing_takes {
        UUID id PK
        UUID user_id FK
        UUID content_id FK
        UUID attempt_id UK
        VARCHAR self_rating
        VARCHAR recording_storage_key
        NUMERIC recording_duration_seconds
        TIMESTAMPTZ completed_at
    }

    quiz_questions {
        UUID id PK
        UUID content_id FK
        TEXT prompt
        VARCHAR question_type
        JSONB options
        TEXT correct_answer
        TEXT explanation
        INT order_index
        TIMESTAMPTZ created_at
    }

    quiz_attempts {
        UUID id PK
        UUID user_id FK
        UUID content_id FK
        UUID attempt_id UK
        INT total_questions
        INT correct_answers
        NUMERIC score_percentage
        VARCHAR status
        TIMESTAMPTZ completed_at
    }

    quiz_answers {
        UUID id PK
        UUID quiz_attempt_id FK
        UUID question_id FK
        TEXT user_answer
        BOOLEAN is_correct
        TIMESTAMPTZ answered_at
    }

    learning_activity_events {
        UUID id PK
        UUID user_id FK
        VARCHAR event_type
        UUID attempt_id UK
        TIMESTAMPTZ completed_at_utc
        VARCHAR timezone_at_event
        DATE historical_local_date
        INT duration_seconds
        TIMESTAMPTZ created_at
    }
```

---

## 2. Reconciled 13-Table Mapping & Composite Foreign Keys

```
1.  app_users                  (Identity, credentials, timezone, daily goal)
2.  vocabularies               (Vocabulary terms, definitions, translations, soft-delete)
3.  collections                (Thematic decks, unique title per user)
4.  vocabulary_collections     (Composite FK join table: (collection_id, user_id) & (vocabulary_id, user_id))
5.  srs_items                  (Composite FK to vocabularies(id, user_id), SM-2 schedule, version)
6.  srs_review_history         (Composite FK to srs_items(id, user_id), attempt_id idempotency)
7.  learning_contents          (Curated exercise bank: dictation, shadowing, quizzes, audio metadata)
8.  dictation_attempts         (Transcriptions, accuracy scores, attempt_id idempotency)
9.  shadowing_takes            (Speech takes, self-ratings, optional audio keys, attempt_id idempotency)
10. quiz_questions             (Prompts, options JSONB, canonical answers)
11. quiz_attempts              (Quiz sessions, scorecards, attempt_id idempotency)
12. quiz_answers               (Individual question submissions)
13. learning_activity_events   (Append-only immutable event sink for heatmap & streaks)
```

---

## 3. Structural Domains & Database Ownership Guarantees

### 3.1 Composite Foreign Key Enforcement
To prevent cross-user entity linkage, the database enforces composite foreign keys across critical relationships:
* `vocabulary_collections`:
  * `(collection_id, user_id)` references `collections(id, user_id)`.
  * `(vocabulary_id, user_id)` references `vocabularies(id, user_id)`.
  * *Guarantee*: It is physically impossible at the SQL engine level to associate User A's vocabulary word with User B's collection.
* `srs_items`:
  * `(vocabulary_id, user_id)` references `vocabularies(id, user_id)`.
  * *Guarantee*: An SRS schedule can never point to a vocabulary word owned by another user.
* `srs_review_history`:
  * `(srs_item_id, user_id)` references `srs_items(id, user_id)`.
  * *Guarantee*: A review log entry cannot reference an SRS card owned by another user.

### 3.2 Non-Cascading Collection Deletion & Vocabulary Soft-Deletion
* When a collection is deleted, PostgreSQL cascading foreign keys remove only the `vocabulary_collections` join rows. The underlying `vocabularies` rows are never deleted.
* When a user removes a word from their active library, `vocabularies.deleted_at` is set. The word is excluded from active study lists, while historical logs in `srs_review_history` and `learning_activity_events` remain preserved.
