# EnglishFlow — Learning Activity Heatmap Specification

**Document Version**: 3.0.0  
**Milestone**: M0 — Discovery & Blueprint (Task M0-T04)  
**Status**: Final Design Specification (Pending PO Approval on Proposed Intensity Tiers)  
**Component**: `<HeatmapCalendar />`  
**Visual Style**: GitHub Contributions Grayscale Adaptation for Spaced Learning  

---

## 1. Product Objective & Architectural Intent

The Learning Activity Heatmap is EnglishFlow's primary visual instrument for longitudinal study habit accountability. Its architectural purpose is to visualize genuine, verified language practice over a rolling 52-week calendar window.

Unlike gamified consumer apps that inflate streaks through passive browsing or daily login rewards, EnglishFlow enforces strict academic rigor: **only verified, completed learning actions are recorded and visualized**.

---

## 2. Core Conceptual Framework (Five Distinct Concepts)

To prevent conflation between habit maintenance, study volume, and personal goals, the system strictly separates five distinct concepts:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        FIVE DISTINCT CONCEPTS                          │
├─────────────────────────┬──────────────────────────────────────────────┤
│ 1. Learning Activity    │ Atomic, completed, validated study events.   │
├─────────────────────────┼──────────────────────────────────────────────┤
│ 2. Activity Intensity   │ Total daily volume mapped to grayscale.      │
├─────────────────────────┼──────────────────────────────────────────────┤
│ 3. Active Study Day     │ Binary qualification: did user study? (≥1)   │
├─────────────────────────┼──────────────────────────────────────────────┤
│ 4. Learning Streak      │ Consecutive active study days in local tz.   │
├─────────────────────────┼──────────────────────────────────────────────┤
│ 5. Daily Goal Complete  │ User's personal target reached (independent) │
└─────────────────────────┴──────────────────────────────────────────────┘
```

### Concept 1: Learning Activity (Atomic Qualifying Actions)
A **Learning Activity** is an atomic study action that requires active cognitive language production, retrieval, or auditory processing:
* **Atomic Qualifying Actions by Modality**:
  1. **Flashcard Review**: Exactly 1 flashcard answered with an assigned grade (`Again`, `Hard`, `Good`, or `Easy`) within an active SRS review session.
  2. **Dictation Exercise**: Exactly 1 audio sentence transcription submitted and evaluated through the diff comparison engine.
  3. **Shadowing Practice**: Exactly 1 speech take recorded via microphone, verified, and self-assessed or completed.
  4. **Quiz Exercise**: Exactly 1 quiz question (multiple choice, cloze fill-in-the-blank) answered with validation.
* **Strictly Excluded (Zero Learning Units)**:
  * Page views, route navigation, and dashboard visits.
  * Account logins, session refreshes, and cookie renewals.
  * Passive dictionary lookups and audio pronunciation previews.
  * **Vocabulary Creation and Bulk Imports**: Adding words manually or importing 500 words via CSV expands the reference library; it indicates zero study or retention. Bulk-importing awards **zero** learning units and generates **zero** activity log records.

### Concept 2: Activity Intensity (Grayscale Ramp)
Activity Intensity reflects the aggregate volume of verified study events completed during a local calendar day. It determines the fill tone of the cell across 5 grayscale levels (Levels 0 through 4).

### Concept 3: Active Study Day (Binary Qualification)
* **Rule**: A calendar day qualifies as an **Active Study Day** if and only if:
  $$\text{Verified Learning Activities Completed on Local Calendar Date} \ge 1$$
* **Zero Arbitrary Point Hurdles**: A single completed flashcard review, dictation sentence, shadowing take, or quiz question qualifies the day as active. Habit preservation requires genuine contact with English, not an arbitrary point total.

### Concept 4: Learning Streak
* **Rule**: An unbroken sequence of consecutive **Active Study Days** calculated in the user's local timezone.
* **Streak State Machine & Day-in-Progress Handling**:
  * **Active (Studied Today)**: The user has completed $\ge 1$ qualifying activity today. The streak count includes today.
  * **Active (Pending Today)**: The user studied yesterday, but has not yet studied today. **The current streak does NOT reset during the day.** It remains intact and displayed as active throughout today until `23:59:59.999` local time, encouraging the user to complete their daily study.
  * **Broken / Reset**: If an entire calendar day elapses (`00:00:00` to `23:59:59` local time) with zero qualifying learning activities, the streak resets to 0 upon entering the subsequent day.
* **First Day**: When a user with a 0 streak completes their first activity of the day, their streak becomes 1.

### Concept 5: Daily Goal Completion
* **Rule**: Daily Goal Completion is an independent, user-configurable preference (e.g., "Complete 20 reviews per day" or "Complete 15 minutes of study per day").
* **Independence**: A user may achieve Level 3 or 4 intensity without meeting their daily goal if their personal target is set high, or conversely meet their daily goal on a Level 1 or 2 day.
* **Visual Indicator**: Days meeting the configured goal display an inner high-contrast dot indicator (`2px × 2px`) inside the cell and a distinct badge in the popover.

---

## 3. Visual Specification & Day States

### 3.1 The 5-Level Grayscale Mapping

| Level | Token | HEX | Volume Threshold `[PROPOSED]` | Visual Definition |
| :--- | :--- | :--- | :--- | :--- |
| **Level 0** | `--heatmap-level-0` | `#F5F5F5` | 0 qualifying actions | Zero activity on past registered day. Border `1px solid #E5E5E5`. |
| **Level 1** | `--heatmap-level-1` | `#D4D4D4` | 1 – 9 actions | Light study session (e.g., 5 flashcards). |
| **Level 2** | `--heatmap-level-2` | `#A3A3A3` | 10 – 19 actions | Moderate study session. |
| **Level 3** | `--heatmap-level-3` | `#525252` | 20 – 39 actions | Heavy study session. |
| **Level 4** | `--heatmap-level-4` | `#171717` | 40+ actions | Peak immersion day. Deep near-black fill. |

> *Status Notice*: The specific numeric volume tiers (`1–9`, `10–19`, `20–39`, `40+`) are explicitly marked **`[PROPOSED]`** pending formal Product Owner approval during Milestone M1.

### 3.2 Cell Dimensions & Geometry
* **Cell Size**: `10px × 10px` square.
* **Cell Gap**: `3px` between adjacent days and columns.
* **Corner Radius**: `rounded-[2px]` (2px radius for clean rectangular precision).
* **Grid Format**: 52 or 53 columns (weeks) × 7 rows (days of the week, Monday to Sunday).

### 3.3 Four Distinct Calendar Day States

```
[Level 0: Past 0-study]   [Pre-Registration]     [Active Level 1-4]    [Future Date]
      ┌──────┐                  ┌ - - -┐               ┌──────┐             ┌ · · ·┐
      │ #F5  │                  │  #FA │               │  #17 │             │  #FF │
      └──────┘                  └ - - -┘               └──────┘             └ · · ·┘
```

1. **Zero Activity Day (`ZERO_ACTIVITY`)**:
   * *Definition*: A calendar day between account registration and yesterday (or today if incomplete) where zero qualifying activities occurred.
   * *Appearance*: Fill `#F5F5F5`, border `1px solid #E5E5E5`. Interactive tooltip: *"October 8, 2026: No learning activity recorded."*
2. **Pre-Registration Day (`PRE_REGISTRATION`)**:
   * *Definition*: Calendar days within the 52-week window prior to user account creation.
   * *Appearance*: Fill `#FAFAFA`, border `1px dashed #E5E5E5`. Tooltip: *"Before account registration."* Non-interactive for keyboard navigation.
3. **Active Study Day (`ACTIVE_DAY`)**:
   * *Definition*: Past or present day with $\ge 1$ verified activity.
   * *Appearance*: Filled with Level 1, 2, 3, or 4 tone. Interactive tooltip and popover breakdown.
4. **Future Day (`FUTURE_DATE`)**:
   * *Definition*: Calendar days occurring after today in the user's local timezone.
   * *Appearance*: Fill `#FFFFFF`, border `1px dotted #E5E5E5`, `opacity-40`. Strictly non-interactive (`cursor-not-allowed`, no tooltip, `aria-disabled="true"`, excluded from keyboard roving focus).

---

## 4. Timezone, Historical Dates & Temporal Boundaries

1. **Event Data Model & Raw Persistence**:
   To support future aggregation changes and guarantee data integrity, every qualifying learning action is recorded in an immutable raw log table:
   * `id`: Unique event UUID.
   * `user_id`: Foreign key to user.
   * `event_type`: Enum (`FLASHCARD_REVIEW`, `DICTATION_SENTENCE`, `SHADOWING_TAKE`, `QUIZ_QUESTION`).
   * `completed_at_utc`: Absolute UTC timestamp (`TIMESTAMPTZ`).
   * `timezone_at_completion`: IANA timezone ID active at completion time (e.g., `Asia/Ho_Chi_Minh`).
   * `historical_local_date`: ISO local date string (`YYYY-MM-DD`) derived at event time:
     $$\text{historical\_local\_date} = \text{DATE}(\text{completed\_at\_utc} \text{ AT TIME ZONE } \text{timezone\_at\_completion})$$
   * `idempotency_key`: Client-provided deduplication token (`session_id` + `item_id`).
   * `duration_seconds`: Active time spent on this unit.
2. **Policy on Timezone Changes**:
   * **Historical Stability**: Historical activity dates remain fixed and stable after a user changes their timezone in Settings. If a user studied at 11:00 PM in Tokyo on October 10th and later travels to London, that activity remains permanently credited to October 10th. Historical dates are not retroactively shifted across date lines.
   * **Future Aggregation**: Subsequent study events use the newly selected timezone for local midnight-to-midnight bucketing.
3. **Daylight Saving Time (DST) Transitions**:
   * On 23-hour ("spring forward") or 25-hour ("fall back") transition days, day boundaries are resolved via standard local calendar dates (`YYYY-MM-DD`), preventing duplicate or omitted day buckets.

---

## 5. Anti-Gaming, Idempotency & Data Integrity

1. **Idempotency Enforcement**:
   * Client requests submitting learning actions must include a unique `idempotency_key` (combining session identifier and item identifier).
   * Backend enforces unique constraints on `(user_id, idempotency_key)` to ensure network retries, browser refreshes, or double-clicks never create duplicate activity events.
2. **Strict Exclusion of Vocabulary Additions & Imports**:
   * Creating words manually or importing CSV word decks inserts rows into the `vocabulary` table, but generates **zero** entries in `learning_activity_log`.
   * Words only generate activity when actively retrieved in a review, transcribed in dictation, or answered in a quiz.

---

## 6. Interaction, Tooltip & Popover Contract

### 6.1 Hover / Focus Tooltip (`shadcn/ui Tooltip`)
* **Trigger**: Mouse hover or keyboard focus on active or past zero-activity cell.
* **Delay**: `50ms` delay to eliminate visual stutter during cursor transit.
* **Content**:
  ```text
  [Tuesday, Oct 13, 2026]
  18 activities completed · Daily goal achieved
  ```

### 6.2 Click / Details Popover (`shadcn/ui Popover`)
* **Trigger**: Mouse click or `Enter`/`Space` keyboard activation.
* **Dimensions**: `w-64` (256px), border `1px solid #E5E5E5`, background `#FFFFFF`, shadow `shadow-sm`.
* **Content Hierarchy**:
  1. **Date Header**: Full localized date + Goal status badge (`Goal Achieved` / `Incomplete`).
  2. **Total Verified Actions**: `18 verified study actions`.
  3. **Verified Activity Breakdown**:
     * Flashcard Reviews: `14 cards`
     * Dictations: `2 sentences`
     * Shadowing: `1 take`
     * Quizzes: `1 question`
     *(Vocabulary additions and bulk imports are explicitly omitted from this list).*
  4. **Direct Link**: `[ View Day Details in History -> ]` navigating to `/history?date=2026-10-13`.

---

## 7. Keyboard Navigation & Accessibility (ARIA Grid)

1. **Grid Roles**:
   * Container: `role="grid"` with `aria-label="Annual learning activity calendar"`.
   * Rows: `role="row"` (7 rows for days of the week).
   * Headers: `role="columnheader"` for months, `role="rowheader"` for weekdays (`Mon`, `Wed`, `Fri`).
2. **Roving Tabindex**:
   * Only the cell representing **Today** (or the latest active day) has `tabIndex={0}`. All other historical cells have `tabIndex={-1}`.
   * Future cells and pre-registration cells have `tabIndex={-1}` and `aria-disabled="true"`.
3. **Arrow Key Navigation**:
   * `ArrowRight`: Move 1 week forward (next column).
   * `ArrowLeft`: Move 1 week backward (previous column).
   * `ArrowDown`: Move 1 day forward (next row).
   * `ArrowUp`: Move 1 day backward (previous row).
   * `Home`: Jump to first day of the 52-week period.
   * `End`: Jump to today.
4. **Screen Reader Announcements**:
   * Active day: `"October 13, 2026: 18 study actions, Level 2 activity, daily goal met."`
   * Zero-activity day: `"October 14, 2026: Zero learning activity recorded."`
   * Pre-registration day: `"October 1, 2025: Prior to account registration."`

---

## 8. Frontend Component & API Contracts

### 8.1 TypeScript Component Props (`HeatmapCalendar.tsx`)

```typescript
export type DayState = 'ACTIVE_DAY' | 'ZERO_ACTIVITY' | 'PRE_REGISTRATION' | 'FUTURE_DATE';

export interface DayActivity {
  date: string;               // ISO date "YYYY-MM-DD"
  state: DayState;
  actionCount: number;        // Total verified atomic study actions
  intensityLevel: 0 | 1 | 2 | 3 | 4;
  goalCompleted: boolean;
  breakdown: {
    flashcardReviews: number;
    dictationExercises: number;
    shadowingTakes: number;
    quizQuestions: number;
  };
}

export interface HeatmapResponse {
  startDate: string;          // "YYYY-MM-DD" (52 weeks prior)
  endDate: string;            // "YYYY-MM-DD" (Today in user tz)
  userTimezone: string;       // Current IANA identifier
  accountCreatedAt: string;   // ISO timestamp
  currentStreakDays: number;  // Current unbroken active streak
  streakStatus: 'ACTIVE_STUDIED_TODAY' | 'ACTIVE_PENDING_TODAY' | 'BROKEN_RESET';
  longestStreakDays: number;
  totalActiveDays: number;
  days: Record<string, DayActivity>; // Keyed by "YYYY-MM-DD"
}

export interface HeatmapCalendarProps {
  data?: HeatmapResponse;
  isLoading: boolean;
  isError: boolean;
  error?: string;
  onSelectDay?: (day: DayActivity) => void;
  className?: string;
}
```

### 8.2 Backend REST Contract (`GET /api/v1/analytics/activity-heatmap`)

* **Authentication**: Session cookie (`Cookie: JSESSIONID=...`). No tokens in localStorage.

```http
GET /api/v1/analytics/activity-heatmap HTTP/1.1
Host: api.englishflow.local
Cookie: JSESSIONID=4A8B29C1F82D3E4F5A6B7C8D9E0F1A2B
X-XSRF-TOKEN: 8f9b2c3d-e4a5-6b7c-8d9e-0f1a2b3c4d5e
Accept: application/json
```

```json
{
  "startDate": "2025-10-12",
  "endDate": "2026-10-11",
  "userTimezone": "Asia/Ho_Chi_Minh",
  "accountCreatedAt": "2026-01-15T08:00:00Z",
  "currentStreakDays": 14,
  "streakStatus": "ACTIVE_STUDIED_TODAY",
  "longestStreakDays": 38,
  "totalActiveDays": 142,
  "days": {
    "2026-10-11": {
      "date": "2026-10-11",
      "state": "ACTIVE_DAY",
      "actionCount": 24,
      "intensityLevel": 3,
      "goalCompleted": true,
      "breakdown": {
        "flashcardReviews": 20,
        "dictationExercises": 2,
        "shadowingTakes": 1,
        "quizQuestions": 1
      }
    },
    "2026-10-10": {
      "date": "2026-10-10",
      "state": "ZERO_ACTIVITY",
      "actionCount": 0,
      "intensityLevel": 0,
      "goalCompleted": false,
      "breakdown": {
        "flashcardReviews": 0,
        "dictationExercises": 0,
        "shadowingTakes": 0,
        "quizQuestions": 0
      }
    }
  }
}
```

---

## 9. Fallback & Responsive States

1. **Desktop ($\ge$ 1024px)**: Full 52-week horizontal calendar inline with sticky month and weekday coordinates.
2. **Mobile (< 768px)**: Segmented control: `[ Last 90 Days | Full Year (Scroll) ]`. Tapping any cell opens a clean bottom slide-up `Drawer` displaying day statistics.
3. **Loading State**: 52 columns × 7 rows of skeleton blocks in `#F5F5F5` with subtle 100ms fade to maintain zero Cumulative Layout Shift (CLS = 0).
4. **Error State**: Non-blocking neutral warning alert (`Lucide AlertCircle`, `#737373`) with inline `[ Retry ]` button.
