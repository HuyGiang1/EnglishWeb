# EnglishFlow — Minimalist UI/UX Blueprint & Design System Architecture

**Document Version**: 3.0.0  
**Milestone**: M0 — Discovery & Blueprint (Task M0-T04)  
**Status**: Final Design Specification (Session Auth & MVP Basic Collections Aligned)  
**Visual Theme**: Modern Minimalism & Monochrome SaaS Dashboard  

---

## 1. Executive Summary & Design Vision

**EnglishFlow** is a modern, high-retention English learning platform engineered for serious language acquisition. The platform unifies vocabulary management, basic collections/decks, spaced repetition review (SRS), daily dictation, shadowing practice, and diagnostic quizzes into a single, cohesive modular monolith.

### The Visual Direction: "Monochrome SaaS Workspace"
Rather than mimicking consumer language-learning apps that rely on cartoon mascots, bright primary colors, confetti animations, and gamification noise, EnglishFlow adopts the design language of elite developer and productivity tools (such as Linear, Vercel, and GitHub). 

1. **Pure White & Near-Black**: The palette is strictly monochrome (`#FFFFFF` background, `#111111` text and primary CTA, `#E5E5E5` borders).
2. **Zero Visual Clutter**: Whitespace is generous, structural borders are razor-thin (1px), and surfaces are defined through subtle tonality (`#F5F5F5` muted fills) rather than heavy drop shadows.
3. **Restrained Motion**: Animations are restricted to instantaneous or 100–150ms state changes. No bouncy spring physics, no delayed entrances, and no gamified celebration popups that interrupt study flow.
4. **Keyboard-First Ergonomics with Strict Typing Guards**: Every core learning workflow—from adding vocabulary to grading flashcards and submitting dictations—is fully executable via keyboard shortcuts without interfering with text entry.
5. **Session-Based Security Architecture**: Clean web security model powered by Spring Security session cookies (`JSESSIONID`, HttpOnly, SameSite=Lax, CSRF token protected). **Zero security tokens stored in localStorage**.

---

## 2. Information Architecture & Dual-Shell Framework

To reconcile dense management workflows (browsing hundreds of vocabulary terms) with high-concentration study sessions (retaining difficult vocabulary, transcribing rapid speech), EnglishFlow utilizes two distinct app shells:

```
┌────────────────────────────────────────────────────────────────────────┐
│                          GLOBAL ROUTING SYSTEM                         │
├───────────────────────────────────┬────────────────────────────────────┤
│         WORKSPACE SHELL           │         FOCUS LEARNING SHELL       │
│  (Browse, Manage, Track, Config)  │  (Flashcard, Dictation, Shadowing) │
├───────────────────────────────────┼────────────────────────────────────┤
│ • /dashboard                      │ • /review                          │
│ • /vocabulary                     │ • /dictation/:exerciseId           │
│ • /vocabulary/:id                 │ • /shadowing/:exerciseId           │
│ • /vocabulary/import              │ • /quiz/:quizId                    │
│ • /collections                    │                                    │
│ • /collections/:id                │                                    │
│ • /history (V1)                   │                                    │
│ • /settings                       │                                    │
└───────────────────────────────────┴────────────────────────────────────┘
```

### 2.1 The Workspace Shell
* **Persistent Left Sidebar**: 
  * Desktop width: `240px` (`w-60`). Collapsible to icon-only mode (`64px` / `w-16`).
  * Mobile: Hidden off-canvas, accessible via hamburger trigger opening a clean slide-out drawer (`280px`).
  * Structure: Logo header, Main Navigation group (Dashboard, Vocabulary, Collections, History [V1]), Study Launch group (Review, Dictation, Shadowing, Quiz), and Footer group (Settings, User Profile).
* **Top Sub-Header**: `56px` height. Houses breadcrumbs, global search trigger (`Mod+K`), Quick Add trigger (`C` when not typing), and quick sync indicator.
* **Main Canvas**: Fluid responsive layout bounded by `max-w-7xl` (`1280px`) with 24px/32px gutter padding.

### 2.2 The Focus Learning Shell
* **Total Distraction Elimination**: The sidebar, top navigation, search bar, and user profile are entirely unmounted.
* **Persistent Chrome**:
  * **Top Left**: Exit study button (`[Esc] Exit`) with confirmation if an active session or uncommitted input is dirty.
  * **Top Center**: Minimalist progress indicator (`Item 12 of 30` + subtle 2px progress bar).
  * **Top Right**: Session settings (audio volume, playback speed, shortcut cheat sheet).
* **Focused Canvas**: Centered container bounded by `max-w-3xl` (`768px`), optimizing peripheral vision and eye travel during study.

---

## 3. Dashboard Blueprint

The dashboard serves as the central command center for language acquisition. It answers three daily questions in under 5 seconds:
1. *What do I need to review right now?*
2. *Did I maintain my study habit today?*
3. *What is my overall momentum over the past year?*

```
┌────────────────────────────────────────────────────────────────────────┐
│  Overview — Monday, October 12, 2026                 [+ Add Word (C)]  │
├───────────────────┬───────────────────────────┬────────────────────────┤
│  DUE FOR REVIEW   │  DAILY GOAL               │  STUDY STREAK          │
│  38 words         │  14 / 20 items (70%)      │  12 Days               │
│  [Review Now ->]  │  ■■■■■■■□□□               │  ● Active Today        │
├───────────────────┴───────────────────────────┴────────────────────────┤
│  LEARNING ACTIVITY (Heatmap)                          Less □ ▨ ▩ ■ More│
│  Jan   Feb   Mar   Apr   May   Jun   Jul   Aug   Sep   Oct   Nov   Dec │
│  ■ □ □ ■ ■ □ □ ■ ■ ■ □ □ ... ■ ■ ■ ■ ■ ■ ■ □ □ ■ ■ ■ ■ ■ ■ ■ □ □ ■ ■   │
├────────────────────────────────────────────────────────────────────────┤
│  QUICK START                                                           │
│  [ 🗂 Flashcard Review ]   [ 🎧 Daily Dictation ]                      │
│  [ 🎙 Shadowing Lab    ]   [ 📝 Diagnostic Quiz ]                      │
├────────────────────────────────────────────────────────────────────────┤
│  RECENT ACTIVITY                                      [View All ->]    │
│  • 09:30 AM  Dictation (B2) — "Economic Resilience"      94% Accuracy  │
│  • 09:15 AM  Flashcard Review — 24 cards completed       100% On-Time  │
│  • Yesterday Quiz (A2) — 10 questions completed          80% Score     │
└────────────────────────────────────────────────────────────────────────┘
```

### 3.1 Component Breakdown
1. **Welcome & Daily Summary**: Non-intrusive heading displaying the user's local date and a one-line summary of queue status.
2. **Key Metric Row**: 3 equal-width cards (`bg-[#FFFFFF] border border-[#E5E5E5] rounded-md p-5`):
   * *Due for Review*: Count of SRS cards due today with direct primary CTA (`[Review Now ->]`).
   * *Daily Goal*: Progress towards the configured daily target (items or minutes) with a monochrome segmented progress bar.
   * *Study Streak*: Consecutive active days with status indicator. Streak remains active throughout the current day even if study is still pending.
3. **Learning Activity Heatmap Widget**:
   * Year-long 52-week calendar grid using 5 neutral grayscale levels (`#F5F5F5` to `#171717`).
   * Adheres strictly to the 5 distinct concepts: only completed, verified learning actions count; adding or bulk-importing vocabulary awards zero learning units. (Detailed specification in [HEATMAP_SPEC.md](file:///C:/Users/truon/.gemini/antigravity-ide/scratch/docs/design/HEATMAP_SPEC.md)).
4. **Quick Navigation Tiles**: 4 minimalist tiles with Lucide icons (`Layers`, `Headphones`, `Mic`, `HelpCircle`) providing instant jump into study modes.
5. **Recent Learning Activity Table**: Clean audit list of the 5 most recent verified study events with timestamps and accuracy metrics.

---

## 4. Learning Experience & Interaction Design

### 4.1 Frictionless Vocabulary Entry with Keyboard Safety
* **Input-Guarded Hotkey `C`**: Opens the Quick Add dialog from any screen within the Workspace Shell, **active only when focus is outside any form input, textarea, or editable element**.
* **Fast Field Traversal**: Form order: `Term` $\rightarrow$ `Part of Speech` $\rightarrow$ `Definition` $\rightarrow$ `Example Sentence` $\rightarrow$ `Collection`.
* **Instant Submission**: Pressing `Mod+Enter` (`Ctrl+Enter` on Windows/Linux, `Cmd+Enter` on macOS) saves the word and immediately clears the form with the cursor refocused on `Term`, allowing users to enter a list of 10 words in under 2 minutes.
* **Unsaved Work Protection**: If a user has entered text into the form (`isDirty === true`), pressing `Esc` triggers a confirmation prompt (*"Discard unsaved word? [Keep Editing] [Discard]"*), preventing accidental data loss.

### 4.2 Focus Mode Flashcard Review (SRS)
* **Grading Engine**: Implements the standard SM-2 algorithm:
  * Key `1` = **Again** (Restart learning step; interval < 10 mins).
  * Key `2` = **Hard** (Difficult recall; interval 1.2x).
  * Key `3` = **Good** (Standard recall; interval 2.5x).
  * Key `4` = **Easy** (Effortless recall; interval 3.5x + ease bonus).
* **Card Flip Interaction**: Pressing `Space` flips between front (Word, IPA, audio shortcut) and back (Definition, Part of Speech, Example sentence). Review keys are scoped strictly to the review canvas.
* **Audio Pronunciation**: Pressing `Mod+P` or `Ctrl+Space` replays the audio clip at any point.

### 4.3 Daily Dictation Text Comparison
* **Typing Collision Protection**:
  * While typing in the transcript `<textarea>`, pressing `Space` produces a normal space character.
  * Controlling audio from within the textarea uses `Mod+Space` (Play/Pause) and `Mod+R` (Replay current sentence).
  * Speed selector chips (`0.75x`, `1.0x`, `1.25x`) are accessible via click or `[` / `]`.
* **Transcript Diff Engine**:
  * Upon submission (`Mod+Enter`), the user's transcript is compared against the canonical sentence using Myers' diff algorithm.
  * Results are styled without aggressive red/green blocks:
    * **Correct**: Standard text (`#111111`).
    * **Omission (Missed word)**: Strikethrough text with gray background (`text-[#737373] line-through bg-[#F5F5F5] px-1`).
    * **Insertion (Extra word)**: Underlined text with subtle background (`text-[#111111] underline bg-[#F5F5F5] px-1`).
  * Accuracy percentage is computed as Levenshtein distance normalized by total characters.

### 4.4 Shadowing Practice & Audio Recording
* **Speech-to-Speech Synchrony**:
  1. User listens to the native audio reference (`Mod+P`).
  2. User records speech via microphone using Web Audio API / `MediaRecorder` (`Mod+R`).
  3. UI displays side-by-side audio controls allowing the user to play their take immediately after the native reference.
  4. User provides a self-assessment score (1–4) or moves to the next sentence.

---

## 5. Design System Implementation Architecture

The design system is structured to use **Tailwind CSS** (for layout, typography, and utility tokens) paired with **shadcn/ui** (accessible primitives built on Radix UI).

### 5.1 shadcn/ui Component Inventory
The following shadcn/ui primitives are selected for direct adoption and restyled according to [DESIGN_TOKENS.md](file:///C:/Users/truon/.gemini/antigravity-ide/scratch/docs/design/DESIGN_TOKENS.md):

| Category | shadcn/ui Primitive | Customization Applied |
| :--- | :--- | :--- |
| **Actions** | `Button` | Black primary (`#111111`), white outline, 6px radius, no shadow |
| **Forms** | `Input`, `Textarea`, `Select` | 1px border `#D4D4D4`, black focus ring, no colored glow |
| **Feedback** | `Dialog`, `Sheet` | Clean modal borders, neutral `bg-black/20` backdrop, 0.05 shadow |
| **Navigation** | `DropdownMenu`, `Tabs` | Segmented tab bar with `#E5E5E5` border; clean menu list |
| **Display** | `Table`, `Card`, `Badge` | Unshaded cards with 1px border; monochrome status chips |
| **Overlays** | `Tooltip`, `Popover` | Instantaneous fade (50ms delay), 1px border, compact text |

### 5.2 Required Custom Components
Where standard shadcn/ui components are insufficient, specialized custom components must be created:

1. `<HeatmapCalendar />`:
   * 52-week SVG/CSS Grid calendar.
   * Roving tabindex keyboard navigation, 4 distinct day states (Zero, Pre-registration, Active, Future).
   * Full specification detailed in [HEATMAP_SPEC.md](file:///C:/Users/truon/.gemini/antigravity-ide/scratch/docs/design/HEATMAP_SPEC.md).
2. `<DictationDiffViewer />`:
   * Character and word level diff renderer with accessible non-color markers (strikethrough, underline, badge).
3. `<AudioWaveformPlayer />`:
   * Minimalist progress bar and audio buffer indicator with zero latency scrubber.
4. `<FlashcardCanvas />`:
   * Accessible front/back flipping container with ARIA live region support.
5. `<AudioRecorderWidget />`:
   * Lightweight browser microphone recorder handling permissions, recording state, and playback comparison.

---

## 6. Motion & Animation Standards

1. **Maximal Restraint**: Animation must only be used where it communicates a change in interface state (e.g., accordion expansion, dialog open).
2. **Transition Specs**:
   * Duration: `100ms` (instantaneous feedback) to `150ms` (sheet sliding).
   * Easing: `ease-out` (`cubic-bezier(0, 0, 0.2, 1)`).
3. **Strictly Prohibited**:
   * Spring physics, rubber-band bouncing.
   * Cascading staggered card entrances.
   * Celebration confetti or particle explosions upon completing review queues.
4. **Accessibility Compliance**: Full support for `prefers-reduced-motion: reduce`, dropping transition durations to `0.01ms`.

---

## 7. Accessibility (WCAG 2.1 AA) Compliance Matrix

| Criterion | Requirement | EnglishFlow Implementation |
| :--- | :--- | :--- |
| **1.4.3 Contrast (Minimum)** | Contrast ratio $\ge$ 4.5:1 for normal text | Primary text `#111111` on `#FFFFFF` = 17.4:1; Secondary `#737373` on `#FFFFFF` = 4.9:1. |
| **1.4.1 Use of Color** | Color is not used as the only visual means of conveying info | Heatmap uses tooltips with exact numerical counts; Dictation diff uses strikethrough/underline; Streaks use explicit text labels. |
| **2.1.1 Keyboard** | All functionality operable through keyboard | Global hotkeys guarded against typing collision (`C`, `Mod+K`, `Esc`), review hotkeys (`1-4`, `Space`), grid navigation (arrow keys). |
| **2.4.7 Focus Visible** | Focus indicator must be clearly visible | Explicit 2px `#111111` ring with 2px offset (`ring-2 ring-[#111111] ring-offset-2`). |
| **4.1.2 Name, Role, Value** | Controls expose accessible names and roles | Proper ARIA roles on custom widgets (`role="grid"`, `role="gridcell"`, `aria-live="polite"`). |
