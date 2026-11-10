# EnglishFlow — REST API Contract Specification

**Document Version**: 1.0.0  
**Milestone**: M0 — Discovery & Blueprint (Task M0-T07)  
**Author**: Principal Backend Architect, Senior Spring Boot Engineer & API Designer  
**Reviewed By**: AI Technical Lead  
**Base URL**: `/api/v1`  
**Status**: Proposal Pending Technical Lead Review  

---

## 1. Global API Conventions & Protocol Architecture

### 1.1 HTTP Semantics & URL Naming
* **Base Path**: All endpoints reside under `/api/v1`.
* **Resource Pluralization**: Standard resource-oriented plural nouns (e.g., `/api/v1/vocabulary`, `/api/v1/collections`).
* **Command Endpoints**: Where actions model state transitions rather than CRUD, sub-resource verb actions are permitted (e.g., `/api/v1/review/grade`, `/api/v1/vocabulary/bulk-import/confirm`).
* **HTTP Methods**:
  * `GET`: Safe, idempotent resource retrieval.
  * `POST`: Creation of resources or execution of commands.
  * `PUT`: Complete idempotent resource updates.
  * `PATCH`: Partial resource updates.
  * `DELETE`: Idempotent resource removal or soft deletion.

### 1.2 Authentication, Cookies & Same-Origin Proxy
* **Session Cookie**: Authentication is managed by Spring Security via an `HttpOnly`, `Secure`, `SameSite=Lax` cookie (`JSESSIONID`).
* **CSRF Token**:
  * Spring Security issues a cookie named `XSRF-TOKEN` (`HttpOnly=false`).
  * On all mutating requests (`POST`, `PUT`, `PATCH`, `DELETE`), the client reads the `XSRF-TOKEN` cookie and supplies it in the `X-XSRF-TOKEN` HTTP header.
* **Credentials Policy**: The client sets `credentials: 'same-origin'` (or `'include'`) on all requests.

### 1.3 Serialization & Data Types
* **Media Type**: `application/json;charset=UTF-8` for payloads; `multipart/form-data` for file/audio uploads.
* **Identifiers**: All entity IDs are serialized as lowercase hyphenated canonical UUID strings (RFC 4122).
* **Timestamps**: All absolute dates/times are serialized in UTC ISO 8601 (`YYYY-MM-DDTHH:mm:ss.sssZ`). Calendar days are serialized as `YYYY-MM-DD`.

### 1.4 Standard Error Response Envelope

All non-2xx responses return a standardized `ApiErrorResponse`:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "The request contains invalid or missing fields.",
  "status": 400,
  "correlationId": "req-9b8c2d1e-4a5f",
  "timestamp": "2026-10-12T08:30:00.000Z",
  "validationErrors": [
    {
      "field": "term",
      "rejectedValue": "",
      "message": "Term must not be blank."
    }
  ]
}
```

#### Standard Error Codes:
* `VALIDATION_FAILED` (`400 Bad Request`): Bean validation constraints violated.
* `UNAUTHORIZED` (`401 Unauthorized`): Missing or invalid session cookie.
* `FORBIDDEN` (`403 Forbidden`): CSRF verification failed or access denied.
* `RESOURCE_NOT_FOUND` (`404 Not Found`): Target entity does not exist or belongs to another user.
* `CONFLICT` (`409 Conflict`): Optimistic lock collision or unique constraint violation.
* `IDEMPOTENCY_CONFLICT` (`409 Conflict`): Idempotency key reused with mismatched payload parameters.
* `INTERNAL_SERVER_ERROR` (`500 Internal Server Error`): Unexpected server failure (stack traces redacted).

### 1.5 Pagination & Sorting Parameters
All collection endpoints support standard query parameters:
* `page`: Zero-indexed page number (default: `0`).
* `size`: Page size limit (default: `20`, max: `100`).
* `sort`: Property and direction tuple (e.g., `sort=createdAt,desc`).

---

## 2. Authentication APIs (`/api/v1/auth`)

### M3-EVO-1B additive locale fields

- Registration accepts optional nullable `preferredLocale`: exactly `vi-VN` or
  `en` when non-null. The current UI supplies the selected guest locale. Omitted
  or null values remain null for backward-compatible clients; they are not
  converted to a database default. Unsupported values return 400
  `VALIDATION_FAILED` using the existing error envelope.
- Register, Login and Current User responses include `preferredLocale` as
  `"vi-VN"`, `"en"` or `null`. Existing response fields are unchanged.
- Login and session bootstrap do not write a locale or infer it from a cookie.
  An explicit account preference wins on the client; null preserves guest locale.

---

### Endpoint: Register User
* **Method & Route**: `POST /api/v1/auth/register`
* **Purpose**: Register a new user account and establish session.
* **Authentication**: Public (Unauthenticated).
* **CSRF Requirement**: Required (`X-XSRF-TOKEN`).
* **Request DTO**:
  ```json
  {
    "email": "learner@example.com",
    "password": "SecurePassword123!",
    "displayName": "Minh Tran",
    "timezone": "Asia/Ho_Chi_Minh"
  }
  ```
* **Validation**:
  * `email`: Non-blank, RFC 5322 syntax, max 255 chars.
  * `password`: Min 8 chars, at least 1 digit, 1 special character.
  * `displayName`: Non-blank, 2–100 chars.
  * `timezone`: Valid IANA timezone identifier (default `UTC`).
* **Response DTO (`201 Created`)**:
  ```json
  {
    "id": "c1f82d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
    "email": "learner@example.com",
    "displayName": "Minh Tran",
    "timezone": "Asia/Ho_Chi_Minh",
    "dailyGoalItems": 20
  }
  ```
* **Cookies Set**: `JSESSIONID` (`HttpOnly`), `XSRF-TOKEN` (`HttpOnly=false`).
* **Error Codes**: `400 VALIDATION_FAILED`, `409 EMAIL_ALREADY_REGISTERED`.

---

### Endpoint: Login User
* **Method & Route**: `POST /api/v1/auth/login`
* **Purpose**: Authenticate credentials and issue session cookie.
* **Authentication**: Public.
* **CSRF Requirement**: Required.
* **Request DTO**:
  ```json
  {
    "email": "learner@example.com",
    "password": "SecurePassword123!"
  }
  ```
* **Response DTO (`200 OK`)**:
  ```json
  {
    "id": "c1f82d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
    "email": "learner@example.com",
    "displayName": "Minh Tran",
    "timezone": "Asia/Ho_Chi_Minh",
    "dailyGoalItems": 20
  }
  ```
* **Error Codes**: `401 INVALID_CREDENTIALS`.

---

### Endpoint: Current Authenticated User (Session Check)
* **Method & Route**: `GET /api/v1/auth/me`
* **Purpose**: Retrieve profile of current active session.
* **Authentication**: Required (`JSESSIONID`).
* **CSRF Requirement**: None (Safe `GET`).
* **Response DTO (`200 OK`)**: User profile object.
* **Error Codes**: `401 UNAUTHORIZED`.

---

### Endpoint: Logout User
* **Method & Route**: `POST /api/v1/auth/logout`
* **Purpose**: Invalidate server session and clear cookies.
* **Authentication**: Required.
* **CSRF Requirement**: Required.
* **Response (`204 No Content`)**: Clears `JSESSIONID`.

---

### Endpoint: Bootstrap CSRF Token
* **Method & Route**: `GET /api/v1/auth/csrf`
* **Purpose**: Issue an initial `XSRF-TOKEN` cookie prior to first mutation if not already present.
* **Authentication**: Public.
* **Response (`204 No Content`)**: Sets `XSRF-TOKEN` cookie.

---

## 3. Vocabulary APIs (`/api/v1/vocabulary`)

---

### Endpoint: List / Search Vocabulary
* **Method & Route**: `GET /api/v1/vocabulary`
* **Purpose**: Retrieve paginated vocabulary list with filtering and search.
* **Authentication**: Required.
* **Query Parameters**:
  * `search` (optional): Query string matching term, definition, or meaning.
  * `collectionId` (optional): UUID of collection deck.
  * `partOfSpeech` (optional): Enum filter.
  * `difficultyLevel` (optional): `A1`..`C2`.
  * `page` (int, default `0`), `size` (int, default `20`), `sort` (string).
* **Response DTO (`200 OK`)**:
  ```json
  {
    "content": [
      {
        "id": "d2a1b3c4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
        "term": "resilience",
        "partOfSpeech": "noun",
        "phoneticIpa": "/rɪˈzɪl.jəns/",
        "vietnameseMeaning": "khả năng phục hồi, sự kiên cường",
        "englishDefinition": "The capacity to recover quickly from difficulties.",
        "exampleSentence": "Her resilience in the face of adversity was remarkable.",
        "difficultyLevel": "B2",
        "createdAt": "2026-10-12T08:00:00.000Z",
        "srsStatus": {
          "state": "REVIEW",
          "dueDate": "2026-10-14",
          "intervalDays": 6
        }
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
  ```

---

### Endpoint: Create Vocabulary (Quick Add or Standard)
* **Method & Route**: `POST /api/v1/vocabulary`
* **Purpose**: Add a new word to the library and auto-provision an initial SRS item.
* **Authentication**: Required.
* **CSRF Requirement**: Required.
* **Request DTO**:
  ```json
  {
    "term": "perseverance",
    "partOfSpeech": "noun",
    "phoneticIpa": "/ˌpɜː.sɪˈvɪə.rəns/",
    "vietnameseMeaning": "sự kiên trì, bền chí",
    "englishDefinition": "Persistence in doing something despite difficulty.",
    "exampleSentence": "Success requires hard work and perseverance.",
    "collectionId": "e3a2b1c0-4d5e-6f7a-8b9c-0d1e2f3a4b5c"
  }
  ```
* **Validation**:
  * `term`: Non-blank, max 150 chars.
  * `partOfSpeech`: Allowed enum values.
  * `vietnameseMeaning`: Non-blank.
* **Response DTO (`201 Created`)**: Created vocabulary object with initialized SRS schedule.
* **Error Codes**: `400 VALIDATION_FAILED`, `404 COLLECTION_NOT_FOUND`.

---

### Endpoint: Check Vocabulary Duplicate (Advisory)
* **Method & Route**: `GET /api/v1/vocabulary/duplicates/check`
* **Purpose**: Check if a term with the same normalized text already exists in the user's library.
* **Query Parameters**: `term=lead&partOfSpeech=noun`
* **Response DTO (`200 OK`)**:
  ```json
  {
    "exists": true,
    "existingEntries": [
      {
        "id": "f4b3c2a1-0e1d-2c3b-4a5f-6e7d8c9b0a1f",
        "term": "lead",
        "partOfSpeech": "noun",
        "vietnameseMeaning": "chì (kim loại nặng)"
      }
    ]
  }
  ```

---

### Endpoint: Soft-Delete Vocabulary Entry
* **Method & Route**: `DELETE /api/v1/vocabulary/{id}`
* **Purpose**: Soft-delete a word from the active library (`deleted_at = NOW()`), suspending its SRS schedule while preserving historical review analytics.
* **Authentication**: Required.
* **CSRF Requirement**: Required.
* **Response (`204 No Content`)**.
* **Error Codes**: `404 NOT_FOUND`.

---

### Endpoint: Bulk Import Preview (Dry-Run)
* **Method & Route**: `POST /api/v1/vocabulary/bulk-import/preview`
* **Purpose**: Validate CSV/TSV contents in memory without writing to database.
* **Request**: `multipart/form-data` containing `file` (CSV/TSV, max 2 MB).
* **Response DTO (`200 OK`)**:
  ```json
  {
    "totalRows": 45,
    "validRows": 40,
    "duplicateRows": 3,
    "errorRows": 2,
    "errors": [
      { "rowNumber": 12, "reason": "Missing required term or definition" }
    ],
    "previewRows": [
      { "rowNumber": 2, "term": "metaphor", "partOfSpeech": "noun", "vietnameseMeaning": "phép ẩn dụ", "status": "VALID", "validationMessage": null }
    ],
    "previewSample": [
      { "rowNumber": 2, "term": "metaphor", "partOfSpeech": "noun", "vietnameseMeaning": "phép ẩn dụ", "status": "VALID" }
    ]
  }
  ```

---

### Endpoint: Bulk Import Confirm
* **Method & Route**: `POST /api/v1/vocabulary/bulk-import/confirm`
* **Purpose**: Atomically ingest valid rows into vocabulary and srs tables. (Zero learning activity events generated).
* **Request DTO**:
  ```json
  {
    "items": [
      {
        "term": "metaphor",
        "partOfSpeech": "noun",
        "vietnameseMeaning": "phép ẩn dụ",
        "englishDefinition": "A figure of speech.",
        "difficultyLevel": "B2"
      }
    ],
    "collectionId": "e3a2b1c0-4d5e-6f7a-8b9c-0d1e2f3a4b5c",
    "duplicatePolicy": "SKIP"
  }
  ```
* **Response DTO (`200 OK`)**: `{ "importedCount": 40, "skippedCount": 3, "rejectedCount": 0 }`.

---

### Endpoint: Bulk Import Template Download
* **Method & Route**: `GET /api/v1/vocabulary/bulk-import/template`
* **Purpose**: Download official CSV/TSV vocabulary template with UTF-8 BOM and example rows.
* **Query Parameters**:
  * `format` (optional, default `csv`): `csv` or `tsv`.
* **Response (`200 OK`)**: File binary stream with `Content-Disposition: attachment; filename="englishflow_vocabulary_template.{format}"`.

---

## 4. Collections APIs (`/api/v1/collections`)

---

### Endpoint: Create Collection
* **Method & Route**: `POST /api/v1/collections`
* **Purpose**: Create a new thematic deck.
* **Request DTO**:
  ```json
  {
    "title": "IELTS Academic Core",
    "description": "Essential vocabulary for IELTS Band 7.5+"
  }
  ```
* **Response DTO (`201 Created`)**: Collection object with `wordCount: 0`.
* **Error Codes**: `400 VALIDATION_FAILED`, `409 TITLE_ALREADY_EXISTS`.

---

### Endpoint: Delete Collection (Safe Non-Cascading Deletion)
* **Method & Route**: `DELETE /api/v1/collections/{id}`
* **Purpose**: Delete deck metadata and join rows. Underlying vocabulary words remain safe in library.
* **Response (`204 No Content`)**.

---

### Endpoint: Add Words to Collection
* **Method & Route**: `POST /api/v1/collections/{id}/words`
* **Purpose**: Assign vocabulary entries to a collection with composite ownership check.
* **Request DTO**:
  ```json
  {
    "vocabularyIds": ["d2a1b3c4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"]
  }
  ```
* **Response DTO (`200 OK`)**: `{ "addedCount": 1 }`.

---

## 4.1 Study Set Folders — M3-EVO-3B

All routes derive owner identity from the authenticated session. Mutations require
the existing synchronized CSRF header/cookie. Foreign-owned and nonexistent
resources both return `404 NOT_FOUND` without disclosing ownership.

| Method / route | Request | Success |
| --- | --- | --- |
| `GET /api/v1/folders?page=0&size=50` | Optional page/size | 200 paged Folder list |
| `POST /api/v1/folders` | `{name, description?}` | 201 Folder |
| `GET /api/v1/folders/{id}` | — | 200 Folder |
| `PUT /api/v1/folders/{id}` | `{name, description?}` | 200 updated Folder |
| `DELETE /api/v1/folders/{id}` | — | 204, Study Sets preserved |
| `PUT /api/v1/collections/{id}/folder` | Required `{"folderId": UUID or null}` | 200 existing Collection DTO plus folderId |

Folder fields: `id`, `name`, nullable `description`, `studySetCount`,
`createdAt`, `updatedAt`. Lists use the existing page envelope
`{content,page,size,totalElements,totalPages}`; page is clamped to >=0 and size
to 1–100, with deterministic name/id ordering. Counts include only the owner's
Study Sets and are aggregated for the page.

Folder name is required, max 100 UTF-16 units in request validation; description
is optional, max 1000. ASCII whitespace is collapsed and stripped at the ends.
Duplicate checking folds ASCII A–Z only; other Unicode case distinctions remain.
The database uses the matching canonical CHECK/generated-key unique constraint.
A duplicate create/rename returns `409 FOLDER_NAME_EXISTS`. Invalid fields,
identifiers, blank Folder filter and malformed UUID bodies return
`400 VALIDATION_FAILED`. Unauthenticated reads return 401; CSRF failures return
403. Constraint errors use safe existing envelopes, never raw SQL.

Collection create accepts additive optional `folderId`; omission/null creates
Unfiled. Collection list/detail/create/update/move responses add nullable
`folderId`. Legacy metadata `PUT /collections/{id}` retains assignment: use the
dedicated move route to change it. On move, omitted `folderId` is invalid;
explicit JSON null unassigns. Blank strings and non-UUID/non-null types are
invalid, not an unassignment shortcut. Destination and Collection must both
belong to the caller. Deleted destinations return 404.

`GET /collections` without filters remains the existing global list.
Optional `folderId=<UUID>` selects one owned Folder; `unfiled=true` selects
null assignment. Combining these filters is 400. Existing pagination/sort bounds
and membership/Review routes remain unchanged.

Folder deletion clears assignments and deletes only Folder metadata in one
transaction. Study Set IDs, vocabulary membership, schedules and history survive.
Owner-scoped transaction locks serialize application Folder mutations/moves;
concurrent duplicate names yield one success and one 409. Move versus deletion
either moves before deletion/unassignment or receives 404 afterwards. A second
delete returns 404. No nested/shared/public Folder endpoints are supported.

---

## 5. Review & SRS APIs (`/api/v1/review`)

---

### Endpoint: Fetch Due Review Queue
* **Method & Route**: `GET /api/v1/review/queue`
* **Purpose**: Fetch cards due for review on the user's current local date.
* **Query Parameters**: `collectionId` (optional UUID), `limit` (int, default `50`).
* **Scope semantics (M3-EVO-3A)**:
  * Omitting `collectionId` preserves global review. A supplied ID selects only active vocabulary belonging to the authenticated user's Collection (Study Set), using the existing shared user/vocabulary SRS records.
  * Validate Collection ownership before queue queries or lazy SRS provisioning. Missing, deleted and foreign Collections return `404 NOT_FOUND` without disclosing whether another user's Collection exists. Blank, malformed or non-canonical UUID inputs return `400 VALIDATION_FAILED`; they never fall back to global review.
  * Both `items` and `totalDue` exclude soft-deleted vocabulary, suspended cards and cards due after the user's current local date. Existing empty Collections and Collections with no due words return `200`, `totalDue: 0`, `items: []`.
  * Ordering remains due date ascending, then SRS creation time ascending. Positive limits are capped at 100; absent/non-positive limits use 50. `totalDue` counts all eligible cards in the selected scope before the batch limit.
  * Lazy provisioning in scoped mode affects only active member vocabulary lacking an SRS record; global mode retains existing provisioning behavior. Queue loading does not reset schedules or create review history.
  * Clients continue batches by fetching the same scoped queue after grading. `totalDue` is a queue-load snapshot, not a live counter.

* **Response DTO (`200 OK`)**:
  ```json
  {
    "totalDue": 14,
    "items": [
      {
        "srsItemId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
        "vocabularyId": "d2a1b3c4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
        "term": "resilience",
        "partOfSpeech": "noun",
        "phoneticIpa": "/rɪˈzɪl.jəns/",
        "vietnameseMeaning": "sự kiên cường",
        "englishDefinition": "The capacity to recover quickly.",
        "exampleSentence": "Her resilience was remarkable.",
        "intervalDays": 6,
        "easeFactor": 2.50
      }
    ]
  }
  ```

---

### Endpoint: Submit Card Grade (Idempotent Action)
* **Method & Route**: `POST /api/v1/review/grade`
* **Purpose**: Grade a card (1=Again, 2=Hard, 3=Good, 4=Easy), calculate new interval, record history, and publish activity event.
* **Collection selection versus submission authorization (M3-EVO-3A)**: The payload is unchanged and does not require `collectionId`. Collection scope selects a queue; submission authority remains ownership of the active vocabulary/SRS item. A user can already review their own cards globally, so removing membership after queue load does not revoke that authority. An identical retry must retain the entire payload, including `attemptId`, grade and `durationSeconds`, to replay the original result. Collection membership never authorizes another user's card or creates a separate schedule/history.

* **Request DTO**:
  ```json
  {
    "srsItemId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "attemptId": "9b8a7c6d-5e4f-3a2b-1c0d-e5f6a7b8c9d0",
    "grade": "GOOD",
    "durationSeconds": 12
  }
  ```
* **Validation**:
  * `grade`: One of `AGAIN`, `HARD`, `GOOD`, `EASY`.
  * `attemptId`: Mandatory UUID.
* **Response DTO (`200 OK`)**:
  ```json
  {
    "srsItemId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "attemptId": "9b8a7c6d-5e4f-3a2b-1c0d-e5f6a7b8c9d0",
    "newIntervalDays": 15,
    "newEaseFactor": 2.50,
    "dueDate": "2026-10-27",
    "nextReviewLabel": "15 days"
  }
  ```
* **Headers**: On network retry with identical parameters, returns `HTTP 200` with header `Idempotent-Replay: true`.
* **Error Codes**: `409 IDEMPOTENCY_CONFLICT` (if attemptId reused with different grade), `409 OPTIMISTIC_LOCK_CONFLICT`.

---

## 6. Dictation APIs (`/api/v1/dictation`)

---

### Endpoint: List Dictation Exercises
* **Method & Route**: `GET /api/v1/dictation`
* **Purpose**: List all available published listening exercises with difficulty level and user's best attempt score. Canonical transcripts are withheld.
* **Response DTO (`200 OK`)**:
  ```json
  [
    {
      "contentId": "b2000000-0000-0000-0000-000000000042",
      "title": "Economic Resilience",
      "topicTag": "Economics",
      "cefrLevel": "B2",
      "audioDurationSeconds": 3.44,
      "bestAccuracyScore": 100.0,
      "attemptCount": 1
    }
  ]
  ```

---

### Endpoint: Load Dictation Exercise
* **Method & Route**: `GET /api/v1/dictation/{contentId}`
* **Purpose**: Load audio reference and exercise prompt. Masked transcript is strictly withheld.
* **Response DTO (`200 OK`)**:
  ```json
  {
    "contentId": "b2000000-0000-0000-0000-000000000042",
    "title": "Economic Resilience",
    "topicTag": "Economics",
    "cefrLevel": "B2",
    "audioUrl": "/api/v1/media/audio/curated/dictation-b2-042.wav",
    "audioDurationSeconds": 3.44
  }
  ```

---

### Endpoint: Submit Dictation Transcription
* **Method & Route**: `POST /api/v1/dictation/{contentId}/submit`
* **Purpose**: Validate typed transcript against canonical text, score accuracy, produce word diff tokens, and record activity event.
* **Request DTO**:
  ```json
  {
    "attemptId": "1a2b3c4d-5e6f-7a8b-9c0d-e1f2a3b4c5d6",
    "submittedTranscript": "The country showed great economic resillience during the crisis.",
    "durationSeconds": 28
  }
  ```
* **Response DTO (`200 OK`)**:
  ```json
  {
    "attemptId": "1a2b3c4d-5e6f-7a8b-9c0d-e1f2a3b4c5d6",
    "canonicalTranscript": "The country showed great economic resilience during the crisis.",
    "accuracyScore": 96.5,
    "wordCount": 10,
    "errorCount": 1,
    "isPassed": true,
    "diffTokens": [
      { "type": "EQUAL", "canonical": "The", "submitted": "The" },
      { "type": "SUBSTITUTION", "canonical": "resilience", "submitted": "resillience" }
    ]
  }
  ```
* **Headers**: Replayed duplicate returns `Idempotent-Replay: true` header. Conflicting payload returns `HTTP 409 Conflict`.

---

## 7. Shadowing APIs (`/api/v1/shadowing`)

---

### Endpoint: Complete Shadowing Take
* **Method & Route**: `POST /api/v1/shadowing/{contentId}/complete`
* **Purpose**: Record completed speech take. Audio recording is optional (supports browser-only practice).
* **Request**: Accepts `multipart/form-data` if recording is uploaded, or standard JSON if browser-only:
  ```json
  {
    "attemptId": "2b3c4d5e-6f7a-8b9c-0d1e-f2a3b4c5d6e7",
    "selfRating": "GOOD",
    "durationSeconds": 15
  }
  ```
* **Validation**: `selfRating` must be `NEEDS_WORK`, `ACCEPTABLE`, `GOOD`, or `FLUENT`.
* **Response DTO (`200 OK`)**:
  ```json
  {
    "attemptId": "2b3c4d5e-6f7a-8b9c-0d1e-f2a3b4c5d6e7",
    "contentId": "7a8b9c0d-1e2f-3a4b-5c6d-e7f8a9b0c1d2",
    "selfRating": "GOOD",
    "hasRecordingSaved": false,
    "completedAt": "2026-10-12T08:45:00.000Z"
  }
  ```

---

## 8. Quiz APIs (`/api/v1/quizzes`)

---

### Endpoint: Start Quiz Attempt
* **Method & Route**: `POST /api/v1/quizzes/{contentId}/start`
* **Purpose**: Initialize a quiz session. Questions return prompts and options; correct answers are withheld.
* **Request DTO**: `{ "attemptId": "3c4d5e6f-7a8b-9c0d-1e2f-a3b4c5d6e7f8" }`
* **Response DTO (`200 OK`)**:
  ```json
  {
    "quizAttemptId": "4d5e6f7a-8b9c-0d1e-2f3a-b4c5d6e7f8a9",
    "totalQuestions": 5,
    "questions": [
      {
        "questionId": "5e6f7a8b-9c0d-1e2f-3a4b-c5d6e7f8a9b0",
        "prompt": "Select the synonym for 'resilient':",
        "questionType": "MULTIPLE_CHOICE",
        "options": ["fragile", "tough", "rigid", "temporary"]
      }
    ]
  }
  ```

---

### Endpoint: Submit Quiz Answer
* **Method & Route**: `POST /api/v1/quizzes/attempts/{quizAttemptId}/answers`
* **Purpose**: Submit individual question answer and transactionally log 1 `QUIZ_QUESTION` activity event.
* **Request DTO**:
  ```json
  {
    "questionId": "5e6f7a8b-9c0d-1e2f-3a4b-c5d6e7f8a9b0",
    "attemptId": "6f7a8b9c-0d1e-2f3a-4b5c-d6e7f8a9b0c1",
    "selectedAnswer": "tough"
  }
  ```
* **Response DTO (`200 OK`)**:
  ```json
  {
    "questionId": "5e6f7a8b-9c0d-1e2f-3a4b-c5d6e7f8a9b0",
    "isCorrect": true,
    "canonicalAnswer": "tough",
    "explanation": "'Tough' denotes the ability to endure hardship."
  }
  ```

---

## 9. Dashboard & Heatmap APIs

---

### Endpoint: Fetch Dashboard Summary
* **Method & Route**: `GET /api/v1/dashboard/summary`
* **Purpose**: Single composite query fetching due review count, streak, daily goal progress, and recent sessions.
* **Response DTO (`200 OK`)**:
  ```json
  {
    "greeting": "Welcome back, Minh",
    "currentDate": "2026-10-12",
    "dueReviewCount": 14,
    "dailyGoalTarget": 20,
    "dailyGoalCompletedCount": 16,
    "currentStreakDays": 12,
    "streakStatus": "ACTIVE_PENDING_TODAY",
    "recentSessions": [
      {
        "type": "FLASHCARD_REVIEW",
        "count": 14,
        "completedAt": "2026-10-12T08:30:00.000Z"
      }
    ]
  }
  ```

---

### Endpoint: Fetch Activity Heatmap (52-Week Rolling Calendar)
* **Method & Route**: `GET /api/v1/analytics/activity-heatmap`
* **Purpose**: Fetch 52-week calendar grid aggregated by local calendar dates.
* **Response DTO (`200 OK`)**:
  ```json
  {
    "startDate": "2025-10-13",
    "endDate": "2026-10-12",
    "userTimezone": "Asia/Ho_Chi_Minh",
    "currentStreakDays": 12,
    "streakStatus": "ACTIVE_PENDING_TODAY",
    "longestStreakDays": 38,
    "totalActiveDays": 142,
    "days": {
      "2026-10-12": {
        "date": "2026-10-12",
        "state": "ACTIVE_DAY",
        "actionCount": 16,
        "intensityLevel": 2,
        "goalCompleted": false,
        "breakdown": {
          "flashcardReviews": 14,
          "dictationExercises": 1,
          "shadowingTakes": 1,
          "quizQuestions": 0
        }
      }
    }
  }
  ```

---

## 10. Settings APIs (`/api/v1/settings`)

### M3-EVO-1B locale preferences

`GET /settings` and settings update responses include nullable `preferredLocale`.
Both existing `PUT /settings` and `PUT /settings/preferences` accept optional
`preferredLocale` (`vi-VN` or `en`). Omitted/null means no change; this API does
not clear an explicit preference. Other preference fields remain optional and
retain their existing semantics. Invalid non-null locales produce HTTP 400
`VALIDATION_FAILED` and roll back the transaction, including other supplied fields.

Example locale-only request to `PUT /api/v1/settings/preferences`:

```json
{ "preferredLocale": "en" }
```

The account is always resolved from the authenticated session, never a client
user ID. Mutations require `X-XSRF-TOKEN` matched to `XSRF-TOKEN` as before.
Without authentication a request with valid CSRF receives 401; a mutation with
missing CSRF may receive 403 before the authentication check.

---

### Endpoint: Read User Settings
* **Method & Route**: `GET /api/v1/settings`
* **Response DTO (`200 OK`)**:
  ```json
  {
    "displayName": "Minh Tran",
    "email": "learner@example.com",
    "timezone": "Asia/Ho_Chi_Minh",
    "dailyGoalItems": 20
  }
  ```

---

### Endpoint: Update Settings
* **Method & Route**: `PUT /api/v1/settings`
* **Purpose**: Update display name, study timezone, and daily goal. Timezone update affects future day boundaries; historical activity dates remain fixed.
* **Request DTO**:
  ```json
  {
    "displayName": "Minh Tran",
    "timezone": "Asia/Ho_Chi_Minh",
    "dailyGoalItems": 25
  }
  ```
* **Validation**: `timezone` must be valid IANA ID; `dailyGoalItems` between 1 and 500.
* **Response DTO (`200 OK`)**: Updated settings object.


## M3-EVO-3C — Paste vocabulary import

The existing CSV/TSV template, multipart preview and `SKIP`/`REJECT`
confirmation routes and payloads remain compatible. Paste uses additive routes
under `/api/v1/vocabulary/bulk-import/paste`. All three are authenticated POSTs
requiring the existing session and synchronized CSRF cookie/header, including
the read-only preview and validation operations.

### POST /preview

Request: `{text, delimiter?, mapping?, skipHeader, collectionId?}`.
`text` is required UTF-8 text, at most **2 MiB**; decoded control characters
other than TAB/CR/LF are rejected. `delimiter` is `AUTO` (default), `TAB`,
`COLON`, `SEMICOLON`, `COMMA`, or `NONE`.
AUTO samples up to 20 nonempty records per candidate and suggests the delimiter
with the most multi-column records; ties produce `AMBIGUOUS_DELIMITER`.
It is a heuristic, not a guarantee. Clients must allow override and require
acknowledgement/correction of warnings before confirmation.

Commons CSV handles quoted fields, doubled quotes, embedded separators and
multiline fields. COLON is deliberately a **two-field dialect**: only the first
unquoted colon per record separates Term and Meaning; subsequent colons remain
content (e.g. `approach : phương pháp: cách tiếp cận`). Use TAB, semicolon or
comma for IPA/POS columns. Blank records are skipped; row numbers identify
original physical start lines, including headers and multiline records.
Malformed quoting returns a safe `PARSER_ERROR`, without echoing input.

`mapping` is an ordered list of source-column destinations: `term`,
`vietnameseMeaning`, `phoneticIpa`, `partOfSpeech`, `ignore`.
It defaults to those four fields in that order, then ignore. Term must be mapped
once; repeating any non-ignore destination is `INVALID_MAPPING`. Mapping up to
16 source columns is supported. Unmapped/ignored source data is retained in
preview `cells` and warns `UNUSED_COLUMNS`; varying record widths warn
`RAGGED_ROW`. No data is silently truncated. `skipHeader` skips the first
nonempty record.

Response 200: `{delimiter, mapping, warnings, rows}`. Each row contains
`{rowNumber, cells, term, vietnameseMeaning, phoneticIpa, partOfSpeech, excluded,
status, issues, existingMeaning, existingIpa}`. Statuses: `NEW`, `EXISTING`,
`ALREADY_PRESENT`, `DUPLICATE_INPUT`, `INVALID`, `EXCLUDED`,
`AMBIGUOUS_EXISTING`. Existing metadata is returned only from the current
user's active vocabulary. Preview creates no vocabulary, membership, SRS or
learning activity.

### POST /validate

Request: `{rows, collectionId?}`, with 1–1,000 rows of
`{rowNumber, term, vietnameseMeaning, phoneticIpa?, partOfSpeech?, excluded}`.
Row numbers must be unique positive physical line numbers (at most 2,097,153).
Response uses the preview envelope with delimiter `EDITED`, empty mapping/cells
and no source parsing warnings. Clients retain original source cells/warnings.
No client status, vocabulary ID or duplicate assessment is authoritative.

All active rows reuse the existing import field validator: trimmed Term
required, maximum 100 UTF-16 code units; trimmed Meaning required, maximum 500;
optional IPA maximum 100. Meaning is NOT nullable in persistence: term-only
input is incomplete, never supplied a fabricated definition.
POS is optional, defaults to `other`; lowercase canonical values are `noun`,
`verb`, `adjective`, `adverb`, `idiom`, `phrase`, `preposition`,
`conjunction`, `other`. Existing supported abbreviations normalize to those
values. The historical parser accepted `pronoun`/`interjection` despite the
V2 database CHECK: these (including `pron`) now fail validation before writes.
No migration changes the database allowlist. Manual vocabulary create has its
existing separate validation limits; paste keeps the established bulk limits.

### POST /confirm

Request: `{rows, collectionId?, conflictPolicy:"PRESERVE_EXISTING"}`.
Omitted/null collectionId imports into the library only. A supplied destination
must be a canonical UUID and an owned existing Collection; malformed/blank
IDs return 400 `INVALID_DESTINATION`, foreign/missing IDs return the same
404 and never fall back to another Collection or to the library.

Canonical identity is unchanged: current owner + active vocabulary +
`Vocabulary.normalize(term)` (trim, Locale.ROOT lowercase) + normalized POS.
Meaning is not identity. First included valid occurrence wins within the batch.
Excluded rows do not consume that identity.
New items create one vocabulary and its existing shared SRS state, then optional
membership. A unique existing item is linked without changing meaning, IPA,
schedule, review history or activity; an existing library item (no destination)
or existing destination membership is counted already present. Metadata
differences warn `PRESERVE_EXISTING`; later repeated identities warn
`FIRST_ROW_KEPT`. More than one existing active match is
`AMBIGUOUS_EXISTING`: the server refuses to choose or merge one.

Backend confirmation revalidates the entire batch, ownership and memberships.
Any active invalid/ambiguous row blocks the whole transaction with 400
`INVALID_ITEM_DATA`; all-excluded batches are rejected. Unsupported conflict
policy is 400 `CONFLICT_POLICY_REQUIRED`. Excluded rows are intentionally
omitted. Constraint/database failures roll back vocabulary, SRS and membership
together. Error responses use the existing sanitized error envelope.

Response 200:
`{createdCount, linkedCount, alreadyPresentCount, duplicateCount, excludedCount,
rejectedCount:0, collectionId}`. Created counts new vocabulary (including its
destination link if selected); linked counts only newly linked existing
vocabulary. The five non-rejected counts partition submitted rows.
There are no partial-success batches or learning events.

### Bounds, concurrency and retry

Maximum 1,000 nonempty data records/rows, 16 columns and 2 MiB raw paste.
All new JSON routes have a separate **4 MiB request-body cap**, checked before
JSON decoding including chunked bodies; oversized bodies return 413
`PAYLOAD_TOO_LARGE`. Clients enforce raw/body limits and surface field/row
errors without truncation. CSV multipart limits are explicitly 2 MiB per file
and 3 MiB including multipart overhead, correcting Spring's previous implicit
1 MiB file default; oversized multipart requests return sanitized 413.

Paste, legacy import and Quick Add share the existing per-user transaction
advisory lock; vocabulary edit/delete and membership add/remove now participate
in that same lock. Paste locks the owned destination row before writing.
Duplicate lookup uses bounded input terms and candidate memberships, not a
network request per row. Membership PK and tenant composite FKs remain.

A successful retry rechecks current state and does not create another active
word/membership/SRS through these coordinated application paths. Counts can
change from created/linked to already present. **No exact response replay,
attempt ID or exactly-once guarantee is provided.** There is no new canonical
vocabulary unique index; out-of-band database writes bypass application locks
and legacy ambiguous identities must be resolved explicitly, without merging
learning history here. Existing review attempt-id semantics are unchanged.
