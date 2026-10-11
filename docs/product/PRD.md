# EnglishFlow â€” Product Requirements Document (PRD)

**Document Version**: 1.1.0  
**Milestone**: M0 â€” Discovery & Blueprint (Task M0-T09)  
**Author**: Principal Software Architect & Senior Business Analyst  
**Reviewed By**: AI Technical Lead  
**Status**: Proposal Pending Technical Lead Review  

---

## 1. Product Definition

### 1.1 Product Vision
EnglishFlow is a focused, high-retention English learning platform engineered for serious language acquisition. Unlike mainstream consumer applications that prioritize gamified streaks, neon graphics, and superficial mini-games, EnglishFlow treats language acquisition as an intellectual discipline. It combines modern cognitive science (SuperMemo SM-2 spaced repetition, verbatim dictation, and speech shadowing) with an austere, distraction-free monochrome user interface inspired by elite developer tools.

### 1.2 Core Product Goals
1. **Accelerate Long-Term Vocabulary Retention**: Minimize forgetting curves through rigorous spaced repetition flashcards with custom collection organization and zero-friction word capture.
2. **Train Auditory Acuity & Spelling Precision**: Close the gap between hearing English speech and understanding word boundaries through daily verbatim dictation exercises with character/word diff feedback.
3. **Develop Spoken Fluency & Rhythm**: Enable oral prosody and intonation practice through native reference shadowing and synchronized speech mimicry.
4. **Enforce Habit Discipline**: Foster daily study consistency via a GitHub-inspired 5-level grayscale activity heatmap and streak tracking that strictly counts verified learning effort.
5. **Eliminate Cognitive Fatigue**: Deliver a clean, minimalist monochrome experience where learning interactions are fast, accessible, keyboard-first, and devoid of visual noise.

### 1.3 Target Audience & Primary Personas

#### Persona 1: "The Academic Candidate" (Minh, 24, IELTS 7.5 Aspirant)
* **Context**: Preparing for academic certification; needs to master 1,500+ C1 academic words and comprehend rapid, natural lecture speech.
* **Pain Points**: Mobile apps quiz elementary terms with childish animations; Anki is clunky on the web and lacks integrated dictation; word lists are scattered across notes and PDFs.
* **Goals**: Rapidly capture academic vocabulary while reading articles, organize words into an "IELTS Academic" collection, and practice daily listening dictations.

#### Persona 2: "The Working Professional" (Elena, 31, Global Product Specialist)
* **Context**: Works in a cross-border tech firm; understands written English well but struggles with natural rhythm, speed, and subtle listening nuances during video conferences.
* **Pain Points**: Lacks time for 60-minute tutoring sessions; wants focused 15-minute daily sessions combining listening and speaking mimicry.
* **Goals**: Shadow native speech clips to master rhythm, review workplace collocations on a clean desktop dashboard, and track longitudinal habit momentum.

#### Persona 3: "The Independent Scholar" (Kenji, 28, Lifelong Language Learner)
* **Context**: Self-studying advanced English literature and technical documentation; values aesthetic minimalism and keyboard-driven efficiency.
* **Pain Points**: Frustrated by gamified countdown timers, cartoons, and social feeds in existing tools.
* **Goals**: Clean desktop keyboard shortcuts (`Mod+Enter`, `Space`, `1-4`), fast bulk import of book vocabulary, and reliable daily habit accountability.

### 1.4 Primary User Problems Solved
* **Tool Fragmentation**: Vocabulary flashcards, dictation transcription, and pronunciation shadowing currently live in disjointed apps or clunky spreadsheets. EnglishFlow consolidates them into a unified modular system.
* **Superficial Gamification vs. Authentic Study**: Traditional platforms grant streaks for logging in or clicking through a 1-minute lesson. EnglishFlow records activity strictly when active cognitive retrieval occurs.
* **Capture Friction**: Adding a word while reading is often slow and cumbersome. EnglishFlow introduces an input-guarded global shortcut (`C`) that allows word capture in under 5 seconds.
* **Visual Distraction & Cognitive Load**: Flashy animations and colorful badges exhaust working memory. EnglishFlow enforces monochrome rigor and 100â€“150ms transitions.

### 1.5 Main User Journey

```
[ Discover Term ] â”€â”€> [ Quick Add (C) / Bulk Import ] â”€â”€> [ Vocabulary Library & Collections ]
                                                                      â”‚
                                                                      â–¼
[ Heatmap & Streak ] <â”€â”€ [ Verified Event Log ] <â”€â”€ [ Study Session (SRS / Dictation / Shadow / Quiz) ]
```

1. **Capture & Organize**: User adds terms via Quick Add modal or bulk CSV import and categorizes them into custom Collections (decks).
2. **Spaced Review**: User opens the Focus Learning Shell to grade due cards using keyboard shortcuts (`Space` to flip, `1-4` to grade).
3. **Active Practice**: User transcribes spoken sentences in Daily Dictation (viewing instant diffs) or mimics speech in Shadowing Lab.
4. **Diagnostic Check**: User takes a quick 5-question Quiz to validate comprehension.
5. **Habit Accountability**: System transactionally records verified actions, updating the 52-week grayscale activity heatmap and maintaining streak momentum.

### 1.6 Measurable Business & Product Success Criteria
* **Daily Active User Retention (D30)**: $\ge 40\%$ 30-day retention among users who create $\ge 10$ vocabulary items.
* **Study Completion Ratio**: $\ge 80\%$ of initiated flashcard review sessions completed to zero-due queue.
* **Dictation Comprehension Velocity**: Average transcription accuracy increases by $\ge 15\%$ across a user's first 30 completed dictations.
* **Interaction Efficiency**: $\le 5$ seconds average time to create a vocabulary item using the Quick Add modal.
* **Zero Cognitive Latency**: P95 backend response time $\le 150\text{ ms}$ for review grading and dictation checking.

---

## 2. Scope Classification

To ensure rapid delivery of customer value while managing technical complexity, product delivery is strictly phased:

```
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚                        PHASED DELIVERY ROADMAP                         â”‚
â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤
â”‚ Milestone 1: Vertical Slice  â”‚ Session Auth, Vocab List, Quick Add,    â”‚
â”‚                              â”‚ SRS Review, Minimal Dashboard           â”‚
â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¼â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤
â”‚ Milestone 2: MVP Target      â”‚ Basic Bulk Import, Basic Collections,   â”‚
â”‚              Full Scope      â”‚ Basic Dictation, Basic Shadowing,       â”‚
â”‚                              â”‚ Basic Quiz, Settings & Heatmap          â”‚
â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¼â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤
â”‚ Milestone 3: V1 Enhancements â”‚ Deep History Audit Log, Detailed SRS    â”‚
â”‚                              â”‚ Analytics, Advanced Speech Overlays,    â”‚
â”‚                              â”‚ Conflict Resolvers, Advanced Decks      â”‚
â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¼â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤
â”‚ Milestone 4: Future / AI     â”‚ AI Sentence Generation, Automated Speechâ”‚
â”‚                              â”‚ Pronunciation Scoring, Anki .apkg Exportâ”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”´â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```

### 2.1 First Vertical Slice (Milestone 1)
* Session-based Authentication (Register, Login, Logout, Session check).
* Core Vocabulary Management (List with search, pagination, detail/edit).
* Quick Vocabulary Entry modal (`Mod+Enter`, input-guarded hotkey `C`).
* Basic Flashcard SRS Review (SM-2 scheduler, Focus mode, keyboard grading `1-4`).
* Minimal Dashboard (Due count card, simple greeting, quick action launch).

### 2.2 Target MVP Scope (Milestone 2)
* Basic Bulk Vocabulary Import (CSV/TSV paste/upload, column mapping, dry-run validation).
* Basic Vocabulary Collections (Create, rename, safe non-cascading delete, assign/remove words, review by collection).
* Basic Daily Dictation (Audio playback 0.75xâ€“1.25x, transcript typing, character/word diff comparison, accuracy score).
* Basic Shadowing Practice (Native audio playback, Web Audio microphone recording, synchronized side-by-side playback, self-grading).
* Basic Diagnostic Quiz (Multiple-choice and cloze fill-in-the-blank questions, scorecard).
* Learning Activity Heatmap (52-week grayscale calendar, streak state machine, timezone aggregation, idempotency).
* User Settings (Profile display name, IANA study timezone, daily review goal).

### 2.3 V1 Enhancements (Milestone 3)
* Detailed Learning History Audit Log (Filterable timeline of past sessions with CSV export).
* Advanced SRS Analytics (Memory retention curves, ease factor distributions, projected workload).
* Advanced Speech Waveform Visualizer (Canvas peak rendering via Web Audio API).
* Advanced Import Conflict Resolver (Overwrite, Merge tags, or Skip options).
* Tag-based Smart Collections & Multi-deck filtering.

### 2.4 Future Capabilities (Milestone 4)
* AI Contextual Sentence & Collocation Generation (LLM integration).
* Automated Speech Pronunciation Scoring (Phoneme-level acoustic model evaluation).
* Anki Deck Interoperability (`.apkg` export and import).
* Collaborative Decks & Community Sharing.

---

## 3. Feature Requirements by Core Module

---

### MODULE 01: Authentication & User Settings
* **Purpose**: Provide secure, session-managed identity persistence, authorization, and personal learning preferences.
* **Phase**: Auth in **First Vertical Slice**; Timezone & Goals in **Target MVP**.
* **Main User Actions**: Register with email/password, log in, log out, view session state, update display name, configure IANA timezone, set daily review goal.
* **Functional Requirements**:
  1. Authenticate users using Spring Security session management (`JSESSIONID` HttpOnly, Secure, `SameSite=Lax` cookie).
  2. Implement CSRF protection for all state-changing requests using cookie-to-header token verification (`X-XSRF-TOKEN`).
  3. Support per-user configuration: IANA timezone identifier (e.g., `Asia/Ho_Chi_Minh`, `Europe/London`) and daily review goal target (integer between 5 and 200).
  4. Ensure passwords are encrypted using BCrypt with a minimum work factor of 12.
* **Business Rules**:
  * No user credentials or session tokens may ever be persisted in browser `localStorage` or `sessionStorage`.
  * The user's configured IANA timezone governs all calendar day boundaries (`00:00:00` to `23:59:59`) for heatmap aggregation and streak calculation.
* **Validation Requirements**:
  * Email must conform to RFC 5322 and be unique per account.
  * Password must be at least 8 characters, containing at least one numeral and one special character.
  * Timezone must be a valid IANA identifier verified against Java `ZoneId.getAvailableZoneIds()`.
* **Error Conditions**:
  * `401 Unauthorized` for invalid email/password or expired session.
  * `409 Conflict` if attempting to register an existing email.
  * `403 Forbidden` if CSRF token is missing or invalid on mutation requests.
* **Acceptance Criteria**:
  * User can register and log in; session survives page refreshes via `JSESSIONID` cookie.
  * Updating timezone immediately alters the local day boundary for subsequent study actions.

---

### MODULE 02: Vocabulary Management
* **Purpose**: Serve as the user's permanent, structured English dictionary and knowledge repository.
* **Phase**: **First Vertical Slice**.
* **Main User Actions**: Browse vocabulary list, search by term/definition/tag, filter by mastery level, inspect detail view, edit definitions, delete words, trigger pronunciation audio.
* **Functional Requirements**:
  1. Store vocabulary records: `term`, `part_of_speech`, `phonetic_ipa`, `definition`, `example_sentence`, `created_at`.
  2. Provide full-text search across `term` and `definition` using PostgreSQL full-text search indexes (`tsvector`).
  3. Support server-side pagination (default 20 items per page) and sorting (Alphabetical, Date Added, Next Due).
  4. Associate each vocabulary record with its owner `user_id` to enforce strict tenant isolation.
* **Business Rules**:
  * Vocabulary creation expands the user's reference library; it is **strictly excluded** from activity event logging ($0$ learning units).
  * Deleting a vocabulary entry performs an audit-preserving soft deletion (deleted_at = NOW()), and sets its associated SRS schedule status to SUSPENDED, removing it from future review queues while preserving historical srs_review_history and learning_activity_events to protect streak and heatmap analytics integrity.
  * Deleting a user account triggers an explicit cascading hard deletion across all associated user records.
* **Validation Requirements**:
  * `term` is mandatory, trimmed, 1â€“100 characters.
  * `part_of_speech` must be one of: `noun`, `verb`, `adjective`, `adverb`, `idiom`, `phrase`, `other`.
  * `definition` is mandatory, 1â€“1,000 characters.
  * `example_sentence` is optional, max 1,000 characters.
* **Acceptance Criteria**:
  * Searching for a term returns matching records in $< 100\text{ ms}$ across 10,000 vocabulary rows.
  * Deleted words disappear immediately from the active vocabulary list and SRS queue, while historical activity logs remain intact.

---

### MODULE 03: Quick Vocabulary Entry
* **Purpose**: Eliminate capture friction by allowing rapid, keyboard-first word entry from any workspace view in under 5 seconds.
* **Phase**: **First Vertical Slice**.
* **Main User Actions**: Press shortcut `C`, fill form using `Tab`, submit via `Mod+Enter`, cancel via `Esc`.
* **Functional Requirements**:
  1. Provide a global overlay modal accessible from anywhere in the Workspace Shell.
  2. Implement an input-focus safety guard: single-key shortcut `C` **never fires** when focus is within an `<input>`, `<textarea>`, or contenteditable element.
  3. On `Mod+Enter`, persist the word and immediately reset form inputs with cursor refocused on the `Term` field.
  4. Implement an unsaved work guard: if `isDirty === true`, pressing `Esc` prompts for confirmation rather than silently discarding user input.
* **Business Rules**:
  * Fast batch entry: user can add 10 consecutive words without touching a mouse.
* **Acceptance Criteria**:
  * Typing "c" into the vocabulary search input types the character 'c' naturally without opening the modal.
  * Pressing `Mod+Enter` saves the word and clears the form in $< 200\text{ ms}$.

---

### MODULE 04: Basic Bulk Vocabulary Import
* **Purpose**: Enable seamless migration of vocabulary decks from external spreadsheets and text files.
* **Phase**: **Target MVP**.
* **Main User Actions**: Upload CSV/TSV file or paste raw tabular text, map columns to schema, review dry-run validation preview, execute batch import.
* **Functional Requirements**:
  1. Parse standard CSV, TSV, and semicolon-delimited text files up to 2 MB (max 1,000 rows per batch).
  2. Provide interactive column mapping for: `Term` (required), `Definition` (required), `Part of Speech`, `Phonetic IPA`, `Example Sentence`, `Collection`.
  3. Execute dry-run validation displaying counts for Valid Rows, Syntax Errors, and Duplicates.
  4. Perform atomic batch insertion within a single database transaction.
* **Business Rules**:
  * **Strict Heatmap Exclusion**: Importing 500 words awards **zero learning activity units** and creates **zero entries** in the activity log.
  * Partial failure handling: If errors exist, the user may choose to "Skip invalid rows and import valid rows" or "Cancel import".
* **Acceptance Criteria**:
  * A 500-row clean CSV imports in $< 2.0\text{ seconds}$.
  * Zero learning units or streak increments are generated by the bulk import operation.

---

### MODULE 05: Basic Vocabulary Collections (Decks)
* **Purpose**: Group vocabulary into thematic study decks (e.g., "IELTS Academic", "Business English") and launch targeted reviews.
* **Phase**: **Target MVP**.
* **Main User Actions**: Create collection, rename collection, delete collection, add words to collection, remove words, browse vocabulary by collection, study collection.
* **Functional Requirements**:
  1. Maintain `Collection` entity (`id`, `user_id`, `title`, `description`, `created_at`).
  2. Maintain many-to-many relationship via `vocabulary_collection` join table.
  3. Filter the main vocabulary list by selected collection.
  4. Launch targeted SRS review queues scoped strictly to the words within a collection (`/review?collectionId=:id`).
* **Business Rules**:
  * **Safe Non-Cascading Deletion**: Deleting a collection deletes only the grouping metadata; all underlying vocabulary entries **must remain safely in the user's vocabulary library**.
  * Advanced sharing, public decks, and community imports are strictly outside MVP.
* **Validation Requirements**:
  * `title` is mandatory, 1â€“80 characters, unique per user.
* **Acceptance Criteria**:
  * Deleting a collection prompts: *"Deleting this collection will NOT delete the vocabulary entries contained inside it."*
  * Confirming deletion leaves all associated words accessible in `/vocabulary`.

---

### MODULE 06: Spaced Repetition Review (SRS Engine)
* **Purpose**: Optimize memory consolidation using active recall and SuperMemo SM-2 interval scheduling in a distraction-free Focus Shell.
* **Phase**: **First Vertical Slice**.
* **Main User Actions**: Open review queue, flip flashcard (`Space`), grade recall difficulty (`1=Again`, `2=Hard`, `3=Good`, `4=Easy`), replay pronunciation audio (`Mod+P`), exit session (`Esc`).
* **Functional Requirements**:
  1. Fetch review queue: all words owned by user where `due_date <= current_local_date`.
  2. Implement SM-2 algorithm:
     * Ease factor ($EF$): Initial 2.5, min 1.3. $EF' = EF + (0.1 - (5 - q) \times (0.08 + (5 - q) \times 0.02))$.
     * Intervals: Grade 1 (Again) resets interval to 1 day; Grade 2 (Hard) interval $\times 1.2$; Grade 3 (Good) interval $\times EF$; Grade 4 (Easy) interval $\times EF \times 1.3$.
  3. Update `SrsItem` state and transactionally record a `FLASHCARD_REVIEW` learning event.
* **Business Rules**:
  * Each graded flashcard constitutes **exactly 1 atomic learning action**.
  * Idempotent submission: Client provides an `attempt_id` (UUIDv4) per card presentation. Submissions with the same `attempt_id` and matching payload replay the cached response (`Idempotent-Replay: true`) without advancing the SM-2 interval twice or duplicating activity events. Submissions with mismatched payloads return `HTTP 409 Conflict`.
* **Acceptance Criteria**:
  * Grading a card updates `due_date` and inserts a verified activity event in a single atomic database transaction.
  * Focus Shell hides all global navigation chrome; keyboard grading functions without mouse input.

---

### MODULE 07: Daily Dictation
* **Purpose**: Train listening comprehension, speech segment parsing, and spelling precision through audio transcription and text diff analysis.
* **Phase**: **Target MVP**.
* **Main User Actions**: Listen to curated audio sentence, adjust playback speed (0.75x, 1.0x, 1.25x), type transcript, submit for diff check (`Mod+Enter`), inspect diff.
* **Functional Requirements**:
  1. Provide reference audio player with HTML5 audio controller and loop controls.
  2. Implement typing collision protection: typing in textarea accepts `Space` normally; audio play/pause from inside textarea uses `Mod+Space`.
  3. Compare user transcript against canonical sentence using Myers' diff algorithm.
  4. Render accessible inline diff: correct words (standard text), omissions (strikethrough text with gray background), extra words (underlined text with subtle background).
  5. Authoritative backend Levenshtein accuracy calculation produces the official score (accuracy percentage >= 80% qualifying as a completed study action), while token-level diff alignment (equal, missing, extra) is generated for client-side visual display.
  6. Transactionally record a `DICTATION` learning action upon successful check.
* **Business Rules**:
  * Each submitted and evaluated dictation attempt generates **exactly 1 atomic learning action**.
* **Acceptance Criteria**:
  * User can transcribe and evaluate a sentence; diff visualizer renders mistakes using non-color indicators (strikethrough/underline).

---

### MODULE 08: Basic Shadowing Practice
* **Purpose**: Develop oral fluency, pronunciation rhythm, and intonation through native audio mimicry.
* **Phase**: **Target MVP**.
* **Main User Actions**: Listen to reference sentence, record voice via microphone (`Mod+R`), playback user recording alongside native model, self-assess performance (Needs Work, Acceptable, Good, Fluent), proceed to next exercise.
* **Functional Requirements**:
  1. Capture microphone audio in browser via Web Audio API / `MediaRecorder` (`audio/webm` or `audio/mp4`).
  2. Handle browser permission states gracefully (Prompt, Granted, Denied).
  3. Provide dual playback controls allowing immediate A/B auditory comparison.
  4. Provide browser-native A/B auditory comparison without requiring raw audio upload to the server in MVP (server-side audio persistence deferred to V1).
  5. Transactionally record a `SHADOWING` learning action upon completion of a self-assessed take.
* **Business Rules**:
  * Each completed and self-assessed speech take generates **exactly 1 atomic learning action**.
* **Acceptance Criteria**:
  * Microphone capture operates in modern browsers; denying permission displays an accessible hardware advisory dialog.

---

### MODULE 09: Basic Diagnostic Quiz
* **Purpose**: Reinforce vocabulary retention and test syntactic context through structured active assessments.
* **Phase**: **Target MVP**.
* **Main User Actions**: Answer multiple-choice questions or fill-in-the-blank cloze tests using keys `1â€“4`, view instant feedback, review mistakes on final scorecard.
* **Functional Requirements**:
  1. Deliver 5-to-10 question quizzes generated from the user's active vocabulary library or curated learning content.
  2. Validate submitted answers against canonical solutions.
  3. Display final scorecard: percentage score, mistakes review, and return to dashboard CTA.
  4. Transactionally record 1 `QUIZ_QUESTION` learning action for each validated question answered.
* **Acceptance Criteria**:
  * Answering a question provides instant feedback and records exactly 1 activity event.

---

### MODULE 10: Learning Activity Heatmap
* **Purpose**: Foster unbroken daily study habits through visual accountability over a 52-week rolling calendar.
* **Phase**: **Target MVP**.
* **Main User Actions**: View 52-week calendar grid on dashboard, inspect day tooltips, click cell to open breakdown popover, navigate grid via arrow keys.
* **Functional Requirements**:
  1. Render 52â€“53 columns (weeks) $\times$ 7 rows (days, Mondayâ€“Sunday) using 5 grayscale levels (`#F5F5F5` to `#171717`).
  2. Enforce four distinct day states:
     * `ZERO_ACTIVITY`: Past registered day with 0 study actions (`#F5F5F5`, border `#E5E5E5`).
     * `PRE_REGISTRATION`: Past days before account creation (`#FAFAFA`, dashed border).
     * `ACTIVE_DAY`: Past/present day with $\ge 1$ verified activity (grayscale Levels 1â€“4).
     * `FUTURE_DATE`: Days after today (`#FFFFFF`, dotted border, `opacity-40`, disabled).
  3. Support full keyboard navigation via roving `tabIndex` and arrow keys (`role="grid"` / `role="gridcell"`).
  4. Display accessible popover with breakdown of verified reviews, dictations, shadowings, and quizzes.
* **Business Rules**:
  * **Strict Separation of 5 Concepts**: Learning Activity, Activity Intensity, Active Study Day, Learning Streak, Daily Goal Completion.
  * **Zero Passive Credit**: Logins, page visits, word creation, and bulk imports generate $0$ units.
  * **Active Study Day**: $\ge 1$ verified learning action on that local calendar date.
  * **Streak Continuity**: An active streak **never resets during today** simply because study is still pending. Reset occurs only after an entire 24-hour local calendar day passes with 0 activity.
  * **Timezone Stability**: Historical local dates are immutable; changing timezones does not retroactively shift historical activity across date lines.
* **Proposed Intensity Tiers `[PROPOSED]`**: Level 1 (1â€“9 actions), Level 2 (10â€“19), Level 3 (20â€“39), Level 4 (40+ actions).
* **Acceptance Criteria**:
  * Completing 1 flashcard review today preserves streak and updates cell color in $< 300\text{ ms}$.

---

### MODULE 11: Learning Dashboard & Progress Tracking
* **Purpose**: Provide a fast, unified mission control overview answering daily study needs in under 5 seconds.
* **Phase**: Minimal metrics in **First Vertical Slice**; full dashboard with Heatmap in **Target MVP**.
* **Main User Actions**: Check due review count, monitor daily goal progress, check active streak, launch study modes via Quick Start tiles, inspect recent activity feed.
* **Functional Requirements**:
  1. Display 3 summary metric cards: Due for Review, Daily Goal progress, Active Streak.
  2. Integrate the 52-week Learning Activity Heatmap widget.
  3. Provide 4 Quick Start tiles (Review, Dictation, Shadowing, Quiz).
  4. Render recent activity table showing the 5 most recent verified study sessions.
* **Acceptance Criteria**:
  * Dashboard loads and renders with zero Cumulative Layout Shift (CLS = 0) in $< 500\text{ ms}$.

---

## 4. Non-Functional Requirements (NFRs)

| Dimension | Specification & Measurable Target | Verification Strategy |
| :--- | :--- | :--- |
| **Security** | Spring Security session cookies (`JSESSIONID`, HttpOnly, Secure, SameSite=Lax). Synchronized CSRF token verification (`X-XSRF-TOKEN`). Zero tokens in localStorage. Passwords BCrypt hashed (work factor 12). Note: HttpOnly cookies mitigate token exfiltration via JavaScript but do not eliminate XSS session-riding; synchronized CSRF protection, CSP, and strict input validation are mandatory. | Automated security tests, OWASP ZAP scan. |
| **Performance** | API P95 latency $\le 150\text{ ms}$ for core study actions; $\le 300\text{ ms}$ for heatmap aggregation. Frontend Time to Interactive (TTI) $\le 1.2\text{ s}$ on 4G. | Lighthouse audits, JMeter load tests. |
| **Reliability & ACID** | All study actions and event logging executed in single `@Transactional` database boundaries. Foreign key cascades enforce relational integrity. | Integration tests with Testcontainers PostgreSQL. |
| **Accessibility** | Strict WCAG 2.1 AA compliance. Minimum contrast 4.5:1 (achieved 17.4:1). Non-color diff markers. Full keyboard navigability with visible focus rings. | Axe DevTools, Playwright accessibility suite. |
| **Responsiveness** | Fluid responsive layouts supporting viewport widths from 360px (mobile) to 1440px+ (desktop). | Browser subagent visual verification. |
| **Data Privacy** | Strict tenant isolation: all database queries scoped by authenticated `user_id`. Audio uploads isolated in user-specific storage keys. | Multi-user isolation test suites. |
| **Storage Limits** | Max audio file upload: 10 MB. Max CSV import: 1,000 rows (2 MB). | Spring Multipart file size filters. |
| **Maintainability** | Modular monolith backend architecture. Decoupled feature folders on frontend. Code coverage $\ge 80\%$. | SonarQube, JaCoCo, JUnit 5. |

---

## 5. Edge Cases & Resilience Rules

1. **Duplicate Vocabulary Terms**:
   * *Scenario*: User adds "lead" as a noun ("heavy metal") and later adds "lead" as a verb ("guide").
   * *Resolution*: Allowed. Uniqueness constraint is scoped to `(user_id, term, part_of_speech)` rather than `term` alone.
2. **Bulk Import Syntax & Encoding Corruption**:
   * *Scenario*: CSV uploaded with non-UTF-8 encoding or mismatched quote delimiters.
   * *Resolution*: Parser detects encoding (UTF-8, ISO-8859-1); pre-flight validation highlights malformed row numbers with specific failure reasons without inserting partial corrupt data.
3. **Concurrent Reviews in Multiple Browser Tabs**:
   * *Scenario*: User opens `/review` in two browser tabs and submits grades for the same card concurrently.
   * *Resolution*: Optimistic locking (`@Version` on `SrsItem`) or unique constraint on `(user_id, attempt_id)`. The first submission succeeds; the duplicate attempt is gracefully recognized as already graded.
4. **Network Drop During Study Submission**:
   * *Scenario*: User clicks "Grade Good"; connection drops before HTTP 200 is received. Client retries request.
   * *Resolution*: Client-generated `attempt_id` UUIDv4. Backend verifies `attempt_id` in `srs_review_history`; matching payload replays previous HTTP 200 result with `Idempotent-Replay: true` without incrementing intervals or event counts. Reusing `attempt_id` with different parameters triggers `409 Conflict`.
5. **Microphone Permission Denied**:
   * *Scenario*: User launches Shadowing but has microphone blocked in browser settings.
   * *Resolution*: Web Audio capture catches `NotAllowedError`; UI presents a neutral alert card explaining how to re-enable permissions in browser address bar, with an option to practice in listening-only mode.
6. **Timezone Travel Across Date Line**:
   * *Scenario*: User studies at 11:00 PM in Tokyo, then flies to London (9 hours behind).
   * *Resolution*: Tokyo activity remains permanently stamped with its historical local date (`2026-10-12`). Subsequent study in London counts toward the London local date. Streak calculation checks calendar date continuity without penalizing the user for traveling westward.
7. **Streak Calculation on Incomplete Day**:
   * *Scenario*: User with a 12-day streak logs in at 9:00 AM. They have not studied yet today.
   * *Resolution*: The UI displays streak status as **Active Today (Pending)** with streak count = 12. The streak does **not** drop to 0. It resets to 0 only after midnight passes (`23:59:59.999`) without any qualifying activity.
8. **Deletion of Vocabulary in Active Collections**:
   * *Scenario*: A user deletes a collection that contains 50 words.
   * *Resolution*: Collection metadata is removed; join table rows are deleted via `ON DELETE CASCADE`. **The 50 vocabulary records and their SRS schedules remain completely intact in the user's library.**


