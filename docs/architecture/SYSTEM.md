# EnglishFlow — System Architecture & Component Blueprint

**Document Version**: 1.1.0  
**Milestone**: M0 — Discovery & Blueprint (Task M0-T09)  
**Author**: Principal Software Architect & Senior Full-Stack Engineer  
**Reviewed By**: AI Technical Lead  
**Status**: Proposal Pending Technical Lead Review  

---

## 1. Executive Architecture Overview

EnglishFlow is a modern, high-retention English learning platform combining spaced repetition flashcards, dictation listening exercises, speech shadowing, and diagnostic quizzes. 

The system is architected as a **Modular Monolith** backend paired with a **Modern Minimalist Desktop-First Web Frontend**:
* **Backend**: Java 21 (Target LTS) & Spring Boot 3.x (Proposed) modular monolith with strict package encapsulation, transactional event publishing, and session-based security.
* **Frontend**: Next.js 15 (Proposed, App Router), React 19 (Proposed), TypeScript, and Tailwind CSS adhering strictly to a high-contrast monochrome design system.
* **Database**: PostgreSQL 16+ utilizing a 13-table schema, engine-level composite foreign key ownership constraints, business-level attempt idempotency, and Flyway versioned migrations.

### 1.1 High-Level System Architecture Diagram

```mermaid
graph TD
    subgraph ClientBrowser [Client Browser Environment]
        UI[Next.js App / React 19 (Proposed)]
        HTML5Audio[HTML5 Audio Player]
        MediaRec[Web Audio / MediaRecorder API]
    end

    subgraph ReverseProxyGateway [Same-Origin Gateway / Reverse Proxy]
        Proxy[Next.js API Rewrite / Caddy Gateway]
    end

    subgraph ApplicationServer [Spring Boot 3 (Proposed) / Java 21 (Target LTS) Modular Monolith]
        SecurityFilter[Spring Security Session & CSRF Filter]
        
        subgraph CoreModules [Modular Monolith Domain Modules]
            AuthMod[auth module]
            UserMod[user module / settings]
            VocabMod[vocabulary module / collections]
            ReviewMod[review module / SM-2]
            ContentMod[content module]
            DictationMod[dictation module]
            ShadowingMod[shadowing module]
            QuizMod[quiz module]
            AnalyticsMod[analytics module / dashboard]
        end

        StorageInterface[AudioStorageService Abstraction]
    end

    subgraph DataStorage [Persistent Storage Tier]
        PostgreSQL[(PostgreSQL 16 Database - 13 Tables)]
        LocalStorage[Host Filesystem Audio Storage]
        S3Storage[Future: S3 / MinIO Object Storage]
    end

    subgraph OptionalExternal [External Integration Boundaries]
        DictionaryAPI[Optional: External Dictionary Audio Lookup]
    end

    %% Client Interactions
    UI -->|HTTPS Requests / Same-Origin| Proxy
    HTML5Audio -.->|Stream Audio| Proxy
    MediaRec -.->|Browser Recording / Playback| UI

    %% Proxy to Backend
    Proxy -->|Forward /api/* with Cookies & CSRF| SecurityFilter

    %% Security & Modules
    SecurityFilter --> CoreModules

    %% Module Inter-communication
    ReviewMod -->|Query Words & Schedules| VocabMod
    DictationMod -->|Fetch Curated Sentences| ContentMod
    ShadowingMod -->|Fetch Reference Clips| ContentMod
    QuizMod -->|Fetch Questions| ContentMod
    
    %% Transactional Activity Logging
    ReviewMod -->|Publish Verified Event| AnalyticsMod
    DictationMod -->|Publish Verified Event| AnalyticsMod
    ShadowingMod -->|Publish Verified Event| AnalyticsMod
    QuizMod -->|Publish Verified Event| AnalyticsMod

    %% Persistence
    CoreModules -->|Spring Data JPA / SQL| PostgreSQL
    ShadowingMod -.-> StorageInterface
    StorageInterface --> LocalStorage
    StorageInterface -.-> S3Storage

    %% External
    VocabMod -.-> DictionaryAPI
```

### 1.2 Request Flows & Deployment Boundaries
1. **Same-Origin API Routing Strategy**:
   * In local development, the Next.js development server runs on `http://localhost:3000` and transparently rewrites `/api/:path*` to the Spring Boot backend at `http://localhost:8080/api/:path*`.
   * In production, a lightweight edge reverse proxy (such as Caddy or Nginx) or Next.js routes all browser traffic on a single domain (`englishflow.app`), routing `/api/*` directly to Spring Boot and all page routes to Next.js.
   * **Architectural Advantage**: Completely eliminates CORS preflight latency (`OPTIONS` requests), eliminates cross-origin cookie drop issues, and ensures seamless, secure cookie transmission.
2. **Session Persistence**:
   * When a user logs in, Spring Boot generates a random session token, stores the session via Spring Security `HttpSession`, and returns an `HttpOnly`, `Secure`, `SameSite=Lax` cookie named `JSESSIONID`.
   * Subsequent client requests automatically include the `JSESSIONID` cookie.

---

## 2. Backend Architecture (Modular Monolith)

The backend is built as a single Spring Boot application with strict internal module boundaries. Each module is encapsulated within its own Java package (`com.englishflow.<module>.*`). Cross-module database access is strictly prohibited: **modules communicate solely through public Application Service interfaces or Spring Application Events**.

```
com.englishflow/
  ├── auth/               # Identity, login/logout, session lifecycle
  ├── user/               # User profiles, IANA timezones, study goals, settings
  ├── vocabulary/         # Vocabulary entries, collections/decks, CSV import
  ├── review/             # Spaced repetition engine (SM-2 scheduler)
  ├── content/            # Curated exercises bank (dictation, shadowing, quizzes)
  ├── dictation/          # Verbatim transcription checking & diff engine
  ├── shadowing/          # Voice recording comparison & takes
  ├── quiz/               # Diagnostic assessments & scorecards
  ├── analytics/          # Immutable activity log, streak & heatmap engine
  └── shared/             # Base domain types, exceptions, storage abstraction
```

### 2.1 Module Boundary Specifications

| Module Name | Core Responsibilities | Owned Entities | Public Application Services (Exported API) | Dependencies | Invariants & Rules |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`auth`** | Session-based authentication, password hashing, CSRF token coordination. | Security Context, Spring `HttpSession`. | `AuthService` (authenticate, logout, validateSession) | `user` | No tokens stored client-side. BCrypt password verification. |
| **`user`** | User account identity, display name, timezone, daily goals. | `User`, `UserSettings` (in `app_users`) | `UserService` (findById, updateTimezone, updateGoal) | *None* | `timezone` must be valid IANA identifier. |
| **`vocabulary`**| Vocabulary dictionary, custom collections/decks, CSV bulk import. | `Vocabulary`, `Collection`, `VocabularyCollection` | `VocabularyService`, `CollectionService`, `BulkImportService` | `user` | Words are owned by `user_id`. Deleting a collection **never** deletes words. Deleting a vocabulary performs soft deletion (`deleted_at = NOW()`), setting SRS status to `SUSPENDED` while preserving historical review events. Deleting a user cascades hard deletes. Importing words awards **zero** activity units. |
| **`review`** | SRS review queue generation, SM-2 interval scheduling, difficulty grading. | `SrsItem`, `SrsReviewHistory` | `ReviewService` (getDueQueue, gradeCard) | `vocabulary`, `user`, `analytics` | Due items evaluated on-demand. Graded cards enforce client `attempt_id` idempotency. Graded card transactionally logs 1 activity event. |
| **`content`** | Curated content bank for dictation sentences, shadowing clips, quiz prompts. | `LearningContent`, `QuizQuestion` | `LearningContentService` (findContentById, listContents) | *None* | Curated content is read-only for students. Reference transcripts are withheld during active exercises. |
| **`dictation`**| Audio playback, authoritative accuracy scoring, token-level diff visualizer. | `DictationAttempt` | `DictationService` (submitTranscription, calculateDiff) | `content`, `user`, `analytics` | Authoritative backend Levenshtein accuracy calculation. Valid attempt (>= 80%) transactionally logs 1 activity event. |
| **`shadowing`**| Speech take capture, audio comparison, self-assessment grading. | `ShadowingTake` | `ShadowingService` (saveTake, completeTake) | `content`, `shared`, `analytics` | Browser-only audio in MVP (no server upload required). Verified take transactionally logs 1 activity event. |
| **`quiz`** | Diagnostic quiz assembly, answer validation, score calculation. | `QuizAttempt`, `QuizAnswer` | `QuizService` (startQuiz, submitAnswer, completeQuiz) | `content`, `user`, `analytics` | Validated quiz completion transactionally logs 1 activity event. Obscures canonical answers during attempt. |
| **`analytics`**| Immutable learning activity logging, streak state machine, 52-week heatmap aggregation. | `LearningActivityEvent` | `ActivityLoggingService`, `HeatmapQueryService`, `StreakService` | `user` | Pure event sink (`learning_activity_events`). Events are immutable. Re-aggregation operates on UTC timestamps and local calendar dates. |

### 2.2 Cross-Module Transactional Event Publishing
When a learning module completes a verified study action, it writes to its own tables and publishes a `LearningActivityCompletedEvent` within the **same database transaction**:

```java
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {
    private final SrsItemRepository srsItemRepository;
    private final SrsReviewHistoryRepository historyRepository;
    private final ActivityLoggingService activityLoggingService;

    @Transactional
    public ReviewResultDto gradeCard(UUID userId, UUID srsItemId, GradeCardCommand cmd) {
        // Enforce business-level idempotency on attempt_id
        Optional<SrsReviewHistory> existing = historyRepository.findByAttemptId(cmd.getAttemptId());
        if (existing.isPresent()) {
            return replayResponse(existing.get());
        }

        SrsItem item = srsItemRepository.findByIdAndUserId(srsItemId, userId)
            .orElseThrow(() -> new EntityNotFoundException("Item not found"));
        
        // 1. Calculate new SM-2 interval and ease factor
        item.applyGrade(cmd.getGrade());
        srsItemRepository.save(item);

        // 2. Insert review history record
        SrsReviewHistory history = historyRepository.save(new SrsReviewHistory(cmd.getAttemptId(), item, cmd.getGrade()));

        // 3. Transactionally log the learning activity event
        activityLoggingService.recordActivity(
            userId,
            ActivityType.REVIEW,
            cmd.getDurationSeconds()
        );

        return ReviewResultDto.from(item, history);
    }
}
```

---

## 3. Frontend Architecture (Next.js & React 19)

### 3.1 App Router Layout Hierarchy
The application leverages Next.js App Router route groups to enforce clean layout separation:

```
src/app/
  ├── (auth)/                    # Centered Minimalist Auth Shell (No sidebar)
  │   ├── login/page.tsx
  │   ├── register/page.tsx
  │   └── layout.tsx
  │
  ├── (workspace)/               # Persistent Workspace Shell (Sidebar + Sub-header)
  │   ├── dashboard/page.tsx
  │   ├── vocabulary/
  │   │   ├── page.tsx
  │   │   ├── import/page.tsx
  │   │   └── [id]/page.tsx
  │   ├── collections/
  │   │   ├── page.tsx
  │   │   └── [id]/page.tsx
  │   ├── history/page.tsx       # (V1)
  │   ├── settings/page.tsx
  │   └── layout.tsx             # Houses <Sidebar /> and <TopHeader />
  │
  ├── (focus)/                   # Fullscreen Focus Learning Shell (Zero navigation chrome)
  │   ├── review/page.tsx
  │   ├── dictation/[id]/page.tsx
  │   ├── shadowing/[id]/page.tsx
  │   ├── quiz/[id]/page.tsx
  │   └── layout.tsx             # Houses <FocusHeader /> with Esc latch & progress bar
  │
  └── layout.tsx                 # Root layout (Fonts, Providers, Toast viewport)
```

### 3.2 Feature-Based Modular Organization
Components, hooks, and schemas are co-located by domain feature under `src/features/`:

```
src/features/
  ├── auth/            # LoginForm, RegisterForm, authHooks, authApi
  ├── vocabulary/      # VocabTable, QuickAddModal, VocabDetail, vocabApi
  ├── collections/     # DeckCard, CreateDeckModal, collectionApi
  ├── review/          # FlashcardCanvas, GradeButtons, useSrsQueue
  ├── dictation/       # AudioPlayer, TranscriptInput, DiffViewer, useDictation
  ├── shadowing/       # DualAudioPlayer, RecorderWidget, useMediaRecorder
  ├── quiz/            # QuestionCard, QuizScorecard, useQuiz
  ├── heatmap/         # HeatmapCalendar, HeatmapCell, DayPopover, useHeatmap
  └── settings/        # ProfileSettingsForm, TimezoneSelect, GoalInput
```

### 3.3 State Management Strategy
1. **Server State (TanStack Query / React Query v5)**:
   * Handles caching, deduplication, background re-validation, and optimistic updates for all server data.
   * Query Keys follow strict hierarchical tuples:
     * `['vocabulary', { search, page, collectionId }]`
     * `['review', 'due-queue', { collectionId }]`
     * `['analytics', 'heatmap', { timezone }]`
2. **Local UI State**:
   * Handled via React standard `useState` and `useReducer` for focused study canvases (flashcard flip state, audio scrubber positions).
3. **Form State**:
   * Handled via `React Hook Form` paired with `zod` schema resolvers for strict client-side validation.

### 3.4 API Client & CSRF Token Injection
A centralized, typed HTTP client (`apiClient`) intercepts all outbound mutation requests:
1. Automatically reads the browser-accessible `XSRF-TOKEN` cookie issued by Spring Security.
2. Injects the token into the `X-XSRF-TOKEN` HTTP request header for `POST`, `PUT`, `DELETE`, and `PATCH` requests.
3. Sets `credentials: 'same-origin'` to ensure session cookies are transmitted automatically.
4. On `401 Unauthorized` responses, clears query cache and redirects to `/login`.

---

## 4. Authentication & Security Architecture

### 4.1 Session-Based Security Model
EnglishFlow adopts the classic, robust **Session-Based Authentication** model rather than stateless JWTs:

```
Browser Client                                Next.js Proxy / Backend
      │                                                    │
      │ ─── 1. POST /api/v1/auth/login (email, pass) ────► │
      │                                                    │ Verify Credentials (BCrypt)
      │                                                    │ Create Session in Store
      │ ◄─── 2. HTTP 200 OK ────────────────────────────── │
      │        Set-Cookie: JSESSIONID=...; HttpOnly;       │
      │                    Secure; SameSite=Lax            │
      │        Set-Cookie: XSRF-TOKEN=...; SameSite=Lax    │
      │                                                    │
      │ ─── 3. POST /api/v1/reviews/submissions ─────────► │
      │        Cookie: JSESSIONID=...                      │ Validate Session
      │        X-XSRF-TOKEN: [read from cookie]            │ Validate CSRF Token
      │ ◄─── 4. HTTP 200 OK (submission accepted) ──────── │
```

### 4.2 Security Token Comparison & Protection Rationale

| Token Type | Storage Location | Accessibility | Security Purpose & Threat Mitigation |
| :--- | :--- | :--- | :--- |
| **`JSESSIONID` (Session Cookie)** | HTTP Cookie Jar | **`HttpOnly` (JavaScript CANNOT read)** | Authenticates the user session. Because JavaScript has zero access, cross-site scripting (XSS) cannot directly read or exfiltrate the session token. **Important Note on XSS**: While HttpOnly mitigates token theft, it does NOT eliminate XSS risk entirely; malicious scripts can still trigger unauthorized session-riding requests. Synchronized CSRF (`X-XSRF-TOKEN`), strict input sanitization, and Content Security Policy (CSP) headers are mandatory. |
| **`XSRF-TOKEN` (CSRF Cookie)** | HTTP Cookie Jar | **`HttpOnly = false` (JavaScript CAN read)** | Protects against Cross-Site Request Forgery. Client reads the cookie value and places it in the `X-XSRF-TOKEN` header. Third-party malicious sites cannot read the cookie due to Same-Origin Policy. |
| **`localStorage` Credentials** | LocalStorage | **BANNED IN ARCHITECTURE** | Storing JWTs or credentials in `localStorage` makes them permanently vulnerable to XSS. EnglishFlow stores **zero credentials** in localStorage. |

---

## 5. Media Architecture (Audio & Speech)

### 5.1 Storage Abstraction Interface
Media storage is decoupled from the underlying physical storage mechanism through a clean Java interface:

```java
public interface AudioStorageService {
    /** Stores audio bytes and returns a relative storage key. */
    String storeAudio(UUID userId, String filename, byte[] audioBytes, String contentType);

    /** Retrieves an input stream for audio playback. */
    InputStream retrieveAudio(String storageKey);

    /** Deletes an audio asset. */
    void deleteAudio(String storageKey);
}
```

### 5.2 Implementations
1. **Local Filesystem Implementation (`LocalStorageService` — MVP Default)**:
   * Writes audio files to a host directory configured via environment variable `STORAGE_LOCAL_ROOT` (e.g., `./data/audio/`).
   * File path structure: `${STORAGE_LOCAL_ROOT}/${userId}/${takeId}.webm`.
   * Served via Spring Boot streaming controller with HTTP `Accept-Ranges` byte-range headers for seeking.
2. **S3-Compatible Implementation (`S3StorageService` — V1 / Production)**:
   * Implements the same interface to store objects in MinIO (local Docker) or AWS S3 / Cloudflare R2 in production without altering application business logic.

### 5.3 Audio Capture, Validation & Limits
* **Reference Audio Playback**: Served in standard `audio/mpeg` (MP3) or `audio/ogg` format. Custom audio controller provides zero-latency speed adjustments (0.75x, 1.0x, 1.25x) via HTML5 `playbackRate`.
* **Microphone Recording**: Captured via Web Audio API / `MediaRecorder` using `audio/webm;codecs=opus` (supported in Chrome/Firefox/Edge) or `audio/mp4` (Safari).
* **Validation**:
  * Max file upload size strictly enforced at **10 MB**.
  * File header inspection verifies audio magic numbers (preventing executable file uploads).

---

## 6. Learning Activity, Heatmap & Timezone Architecture

### 6.1 Business-Level Attempt Idempotency
To prevent duplicate requests from advancing review schedules, inflating quiz scores, or generating duplicate activity events, every learning submission generates a client-side `attempt_id` (UUIDv4).

Idempotency is enforced at the business-operation level on `srs_review_history`, `dictation_attempts`, `shadowing_takes`, and `quiz_attempts` (each with `UNIQUE (attempt_id)`), not solely on event logs:

* **Matching Payload (Replay)**: Replayed requests with the identical `attempt_id` and payload bypass business execution, skipping SM-2 interval progression and duplicate event inserts, and return the cached outcome with header `Idempotent-Replay: true`.
* **Mismatched Payload (Conflict)**: Reusing the same `attempt_id` with different parameters triggers `HTTP 409 Conflict` (`IDEMPOTENCY_KEY_PAYLOAD_MISMATCH`).
* **Legitimate Future Attempt**: A subsequent study session generates a new, unique `attempt_id`.

### 6.2 Data Model: `learning_activity_events` (13-Table Schema)

```sql
CREATE TABLE learning_activity_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    action_type VARCHAR(32) NOT NULL, -- REVIEW, DICTATION, SHADOWING, QUIZ
    duration_seconds INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_learning_events_user_created ON learning_activity_events (user_id, created_at);
```

### 6.3 Timezone Stability & Daily Aggregation
1. **Historical Stability**:
   * Activity events store authoritative UTC timestamps `created_at`.
   * When aggregating for the user heatmap or dashboard, queries project timestamps onto the user's active IANA timezone:
     $$\text{local\_date} = \text{DATE}(\text{created\_at} \text{ AT TIME ZONE } \text{timezone})$$
   * When a user changes their timezone in Settings, past timestamps remain untouched; recalculation uses the new reference timezone without data corruption.
2. **Streak State Machine**:
   * Evaluated dynamically across local calendar dates:
     * **`ACTIVE_STUDIED_TODAY`**: User completed $\ge 1$ action today. Streak count includes today.
     * **`ACTIVE_PENDING_TODAY`**: User completed $\ge 1$ action yesterday, but 0 so far today. **The current streak remains active throughout today.** It does not reset during the day.
     * **`BROKEN_RESET`**: An entire local calendar day (`00:00:00` to `23:59:59`) elapsed with zero qualifying activity. Streak resets to 0.

---

## 7. Background Processing Evaluation

* **Architectural Evaluation for MVP**:
  * *Question*: Do we require an asynchronous task worker (e.g., Spring `@Scheduled`, Redis queue, or Quartz) in the MVP?
  * *Decision*: **NO**. Adding background task workers or external queues adds operational overhead without product need.
* **On-Demand Authoritative Queries**:
  1. **SRS Due Queue**: Calculated on-demand when user opens `/review` using indexed queries:
     ```sql
     SELECT * FROM srs_items WHERE user_id = :userId AND status = 'ACTIVE' AND next_review_at <= NOW();
     ```
  2. **Heatmap & Streak Aggregation**: Calculated on-demand using indexed queries over `learning_activity_events`. With composite indexing, aggregating 365 days for a single user executes efficiently in PostgreSQL.
  3. **No Batch Midnight Cron Jobs**: Streak states evaluate cleanly at query time by comparing `currentLocalDate` with the most recent local activity date.

---

## 8. Observability & Logging Strategy

1. **Structured JSON Logging**: Spring Boot utilizes Logback with Logstash JSON encoder for machine-readable stdout logs.
2. **Request Correlation Tracking**: Every request entering the system is stamped with an `X-Correlation-Id` header (generated at the reverse proxy or Spring filter) and injected into the SLF4J MDC (Mapped Diagnostic Context).
3. **Secret Redaction**: Passwords, cookie values, and database connection strings are masked using custom log scrubbers.
4. **Health & Metrics**:
   * Spring Boot Actuator exposed on management port: `/actuator/health` (liveness and readiness probes) and `/actuator/metrics`.

---

## 9. Deployment Strategy (Low-Cost / High-Reliability)

### 9.1 Local Development Environment
Managed via Docker Compose:
* **PostgreSQL 16**: Port 5432, mounted volume for persistent development data.
* **Next.js Dev Server**: Port 3000 (`pnpm dev`), proxying `/api/*` to backend.
* **Spring Boot Dev Server**: Port 8080 (`./mvnw spring-boot:run`).

### 9.2 Low-Cost Production Deployment
Targeted for a single modest Linux VPS (e.g., 2 vCPU, 4 GB RAM):
* Single `docker-compose.prod.yml` deploying:
  1. `caddy`: Reverse proxy handling automated Let's Encrypt SSL/TLS, static caching, and routing.
  2. `englishflow-backend`: Multi-stage Docker build of Spring Boot jar (Temurin 21 JRE Alpine).
  3. `englishflow-frontend`: Standalone Next.js Node.js server build.
  4. `postgres`: PostgreSQL 16 Alpine with automated nightly volume backups.
* Total hosting cost: $\approx \$10 - \$20\text{ / month}$ with zero vendor lock-in.
