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
    "validRows": 43,
    "errorRows": 2,
    "errors": [
      { "rowNumber": 12, "reason": "Missing required term or definition" }
    ],
    "previewSample": [
      { "term": "metaphor", "partOfSpeech": "noun", "vietnameseMeaning": "phép ẩn dụ" }
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
        "englishDefinition": "A figure of speech."
      }
    ],
    "collectionId": "e3a2b1c0-4d5e-6f7a-8b9c-0d1e2f3a4b5c"
  }
  ```
* **Response DTO (`200 OK`)**: `{ "importedCount": 43 }`.

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

## 5. Review & SRS APIs (`/api/v1/review`)

---

### Endpoint: Fetch Due Review Queue
* **Method & Route**: `GET /api/v1/review/queue`
* **Purpose**: Fetch cards due for review on the user's current local date.
* **Query Parameters**: `collectionId` (optional UUID), `limit` (int, default `50`).
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

### Endpoint: Load Dictation Exercise
* **Method & Route**: `GET /api/v1/dictation/{contentId}`
* **Purpose**: Load audio reference and exercise prompt. Masked transcript is withheld.
* **Response DTO (`200 OK`)**:
  ```json
  {
    "contentId": "7a8b9c0d-1e2f-3a4b-5c6d-e7f8a9b0c1d2",
    "title": "Economic Resilience",
    "cefrLevel": "B2",
    "audioUrl": "/api/v1/media/audio/curated/dictation-b2-042.mp3",
    "audioDurationSeconds": 4.5
  }
  ```

---

### Endpoint: Submit Dictation Transcription
* **Method & Route**: `POST /api/v1/dictation/{contentId}/submit`
* **Purpose**: Validate typed transcript against canonical text, score accuracy, and record activity event.
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
    "isPassed": true
  }
  ```

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
