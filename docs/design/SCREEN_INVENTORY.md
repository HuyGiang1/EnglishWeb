# EnglishFlow — Screen Inventory & Navigation Architecture

**Document Version**: 3.0.0  
**Milestone**: M0 — Discovery & Blueprint (Task M0-T04)  
**Status**: Final Design Specification (Session Auth & MVP Basic Collections Aligned)  
**Architectural Scope**: Complete Application Routes, Phased Delivery Plan & State Contracts  

---

## 1. Global Navigation Architecture

EnglishFlow operates two distinct structural layouts:
1. **Workspace Shell**: Standard layout for browsing, managing, and configuring. Contains a sticky top navigation bar (mobile) or persistent collapsible left sidebar (desktop), global search trigger (`Mod+K`), input-safe quick add trigger (`C` when not typing), and profile menu.
2. **Focus Learning Shell**: Full-screen distraction-free canvas used for study sessions (Flashcard Review, Dictation, Shadowing, Quiz). The global sidebar and secondary navigation are completely unmounted. An escape latch with unsaved-work confirmation and a minimalist top progress bar are the only persistent chrome elements.

```
                      ┌────────────────────────────────────────┐
                      │             Global Routes              │
                      └───────────────────┬────────────────────┘
                                          │
         ┌────────────────────────────────┴────────────────────────────────┐
         ▼                                                                 ▼
┌──────────────────┐                                             ┌──────────────────┐
│  Workspace Shell │                                             │   Focus Shell    │
├──────────────────┤                                             ├──────────────────┤
│ /dashboard       │                                             │ /review          │
│ /vocabulary      │                                             │ /dictation/:id   │
│ /vocabulary/:id  │                                             │ /shadowing/:id   │
│ /vocabulary/import│                                            │ /quiz/:id        │
│ /collections     │                                             └──────────────────┘
│ /collections/:id │
│ /history (V1)    │
│ /settings        │
└──────────────────┘
```

---

## 2. Phased Delivery Matrix (MVP Target Scope vs. Incremental Milestones)

Per Technical Lead directive, the **target MVP scope** includes all core learning modalities and management tools: Authentication (Session-based), Vocabulary Management, Quick Add, Basic Bulk Import, Basic Vocabulary Collections, Spaced Repetition Review, Basic Dictation, Basic Shadowing, Basic Quiz, Dashboard with Statistics, and User Settings.

Implementation is partitioned into structured engineering phases:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PHASED DELIVERY ROADMAP                         │
├──────────────────────────────┬─────────────────────────────────────────┤
│ Milestone 1: Vertical Slice  │ Session Auth, Vocab List, Quick Add,    │
│                              │ SRS Review, Minimal Dashboard           │
├──────────────────────────────┼─────────────────────────────────────────┤
│ Milestone 2: MVP Target      │ Basic Bulk Import, Basic Collections,   │
│              Full Scope      │ Basic Dictation, Basic Shadowing,       │
│                              │ Basic Quiz, Settings & Heatmap          │
├──────────────────────────────┼─────────────────────────────────────────┤
│ Milestone 3: V1 Enhancements │ Deep History Audit Log, Detailed SRS    │
│                              │ Analytics, Advanced Speech Overlays,    │
│                              │ Conflict Resolvers, Advanced Decks      │
├──────────────────────────────┼─────────────────────────────────────────┤
│ Milestone 4: Future / AI     │ AI Sentence Generation, Automated Speech│
│                              │ Pronunciation Scoring, Anki .apkg Export│
└──────────────────────────────┴─────────────────────────────────────────┘
```

| Screen / Feature | Classification | Primary User Job | Architectural Mechanism |
| :--- | :--- | :--- | :--- |
| **01. Authentication** | **MVP (Vertical Slice)** | Secure identity, session cookie | Spring Security Session (`JSESSIONID`, HttpOnly, CSRF) |
| **02. Dashboard** | **MVP (Target Scope)** | Daily summary, streak, due count, heatmap | Analytics Service, SRS Queue |
| **03. Vocabulary List** | **MVP (Vertical Slice)** | Search, filter, inspect saved terms | PostgreSQL FTS, Pagination |
| **04. Quick Add Modal** | **MVP (Vertical Slice)** | Zero-friction word entry (`Mod+Enter`) | Word Service, Audio lookup |
| **05. Bulk Vocabulary Import** | **MVP (Target Scope: Basic)** | Import CSV/TSV word lists into library | CSV Parser, Batch Inserts |
| **06. Vocabulary Detail & Edit** | **MVP (Vertical Slice)** | Word IPA, definitions, examples, edit form | Word Service, Audio Player |
| **07. Vocabulary Collections** | **MVP (Target Scope: Basic)** | Decks (Create, rename, delete, assign, review) | Collection Entity, Word Mapping |
| **08. Flashcard SRS Review** | **MVP (Vertical Slice)** | Spaced repetition study in Focus Mode | SM-2 Algorithm, Event Log |
| **09. Dictation Exercise & Diff**| **MVP (Target Scope: Basic)** | Listen to sentence, type transcript, view diff| Audio Player, Diff Engine |
| **10. Shadowing Practice** | **MVP (Target Scope: Basic)** | Listen native, record user take, compare | Web Audio / MediaRecorder |
| **11. Quiz Exercise & Results** | **MVP (Target Scope: Basic)** | Diagnostic multiple choice & cloze test | Quiz Generator Service |
| **12. Learning History Log** | **V1 Enhancement** | Chronological audit log of past sessions | Study Session Log API |
| **13. Settings** | **MVP (Target Scope)** | Timezone (vital for heatmap), daily goals | User Settings API |

---

## 3. Global Keyboard Interaction Standards & Safety Guards

1. **Typing Safety Guard (Single-Key Shortcuts)**:
   * Shortcuts like `C` (Quick Add) or `/` (Search) **must never fire** while the user's cursor is inside any form input element:
     ```typescript
     const isTyping = ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName) ||
                      (document.activeElement as HTMLElement)?.isContentEditable;
     if (isTyping && event.key === 'c') return; // Pass through naturally
     ```
2. **OS Platform Parity (`Mod` Key)**:
   * All modifier shortcuts support both Windows/Linux (`Ctrl`) and macOS (`Cmd`), documented as `Mod+Enter`, `Mod+K`, `Mod+S`.
3. **Unsaved Work Protection (`Esc` Guard)**:
   * Pressing `Esc` in modals or edit views when form state is pristine (`isDirty === false`) closes the modal immediately.
   * If unsaved changes exist (`isDirty === true`), `Esc` halts dismissal and triggers a high-contrast confirmation popover:
     ```text
     "Discard unsaved changes? [Keep Editing] [Discard]"
     ```
4. **Context-Restricted Study Keys (`Space`, `1–4`)**:
   * Flashcard keys (`1–4` for grading, `Space` for flip) are active **only** when focused on the review canvas.
   * In Dictation, typing in the transcript textarea accepts standard `Space` characters. Controlling audio playback from within the textarea requires `Mod+Space` (Play/Pause) or `Mod+R` (Replay) to prevent typing collisions.
5. **Universal Mouse & Touch Parity**:
   * Every keyboard-triggered action has a prominent, accessible on-screen button with visible label and tooltips indicating the shortcut key (`<kbd>`).

---

## 4. Detailed Screen Specifications

---

### SCREEN 01: Authentication (Login / Register / Forgot Password)
* **Route**: `/login`, `/register`, `/forgot-password`, `/reset-password`
* **Phase**: **MVP (First Vertical Slice)**
* **Shell**: Centered Card Minimalist Canvas (No sidebar).
* **Architecture**: 
  * Spring Security session-based authentication.
  * Secure, HttpOnly, SameSite (`Lax`) session cookie (`JSESSIONID`).
  * CSRF protection enabled via synchronized token (`X-XSRF-TOKEN` cookie/header).
  * **Zero tokens in localStorage**. Session state managed strictly on the backend.
* **Component Hierarchy**:
  ```text
  <AuthLayout>
    <AuthCardContainer max-w-sm>
      <LogoMark text="EnglishFlow" />
      <AuthHeader title="Sign in to your account" subtitle="Enter your credentials below" />
      <AuthForm method="POST" action="/api/v1/auth/login">
        <FormField label="Email" type="email" placeholder="name@example.com" />
        <FormField label="Password" type="password" />
        <Button variant="primary" w-full>Sign In</Button>
      </AuthForm>
      <AuthFooter text="Don't have an account? Sign up" />
    </AuthCardContainer>
  </AuthLayout>
  ```
* **Main Actions**: Submit credentials, toggle password visibility, navigate between sign-in/register.
* **Loading State**: Primary button shows spinner icon with label "Signing in...", input fields disabled.
* **Empty State**: Clean blank form fields with autofocus on Email input.
* **Error State**: Inline text banner under form header (`border border-[#E5E5E5] bg-[#F5F5F5] text-[#111111] p-3 rounded-md text-sm`) stating "Invalid email or password."
* **Responsive Behavior**: Vertically centered on desktop; pinned to top with 24px padding on mobile (<640px).
* **Accessibility**: Full `<form>` markup with `autocomplete="username"` and `autocomplete="current-password"`, `aria-describedby` for error strings.

---

### SCREEN 02: Dashboard
* **Route**: `/` or `/dashboard`
* **Phase**: **MVP (Target Scope)** *(Minimal metrics in Vertical Slice; full Heatmap in Target Scope)*
* **Shell**: Workspace Shell (Collapsible Sidebar + Top Sub-header).
* **Purpose**: Central mission control displaying daily study progress, review workload, habit heatmap, and quick launch triggers.
* **Component Hierarchy**:
  ```text
  <DashboardView>
    <PageHeader title="Overview" subtitle="Monday, October 12, 2026" />
    <MetricsRow grid grid-cols-1 md:grid-cols-3 gap-4>
      <MetricCard title="Due for Review" value="38 words" action="Review Now ->" />
      <MetricCard title="Daily Goal" progress="14/20 items (70%)" />
      <MetricCard title="Study Streak" value="12 Days" badge="Active Today" />
    </MetricsRow>
    <HeatmapWidgetCard border rounded-md p-6>
      <HeatmapHeader title="Learning Activity" legend="5-level grayscale" />
      <HeatmapCalendar 52-weeks-grid />
    </HeatmapWidgetCard>
    <QuickActionsSection grid grid-cols-2 md:grid-cols-4 gap-3>
      <ActionTile icon="Layers" label="Flashcard Review" href="/review" />
      <ActionTile icon="Headphones" label="Dictation" href="/dictation" />
      <ActionTile icon="Mic" label="Shadowing" href="/shadowing" />
      <ActionTile icon="HelpCircle" label="Quick Quiz" href="/quiz" />
    </QuickActionsSection>
    <RecentActivityCard border rounded-md p-4>
      <ActivityTable rows="Last 5 verified learning sessions" />
    </RecentActivityCard>
  </DashboardView>
  ```
* **Main Actions**: Launch SRS review queue (`[Review Now]`), click quick study tiles, inspect heatmap popovers.
* **Loading State**: Skeleton blocks (`bg-[#F5F5F5] animate-pulse`) mirroring metric cards and heatmap footprint.
* **Empty State**: New user sees onboarding welcome card with single primary CTA: `[ Add Your First 5 Words ]`.
* **Error State**: Non-blocking toast or banner for individual failed widget queries with inline retry button.
* **Responsive Behavior**: Metrics stack from 3 columns to 1 column on mobile; heatmap displays compact 16-week window or horizontal scroll.
* **Accessibility**: Heading hierarchy (`h1` for page, `h2` for cards), screen-reader labels on counters.

---

### SCREEN 03: Vocabulary List & Management
* **Route**: `/vocabulary`
* **Phase**: **MVP (First Vertical Slice)**
* **Shell**: Workspace Shell.
* **Purpose**: Repository of all saved vocabulary terms with search, collection filter, CEFR filter, and pronunciation audio.
* **Component Hierarchy**:
  ```text
  <VocabularyListView>
    <PageHeader title="Vocabulary" count="482 words">
      <Button variant="outline" href="/vocabulary/import">Import CSV</Button>
      <Button variant="primary" onClick="openQuickAdd">Add Word (C)</Button>
    </PageHeader>
    <FilterToolbar flex flex-wrap gap-3>
      <SearchInput placeholder="Search word, definition, or tag..." w-72 />
      <SelectFilter label="Collection" options="All, Academic, IELTS Core, Business" />
      <SelectFilter label="Mastery Level" options="Learning, Reviewing, Mastered" />
      <SelectFilter label="CEFR Level" options="A1, A2, B1, B2, C1, C2" />
      <SortDropdown options="Date Added, Due Date, Alphabetical" />
    </FilterToolbar>
    <VocabularyTable>
      <TableHeader columns="Term, Phonetic, Part of Speech, Definition, Next Due, Actions" />
      <TableBody>
        <VocabularyRow term="Resilience" ipa="/rɪˈzɪl.jəns/" pos="noun" due="Today" />
      </TableBody>
    </VocabularyTable>
    <PaginationControls page="1" totalPages="25" />
  </VocabularyListView>
  ```
* **Main Actions**: Filter words by collection, play audio, edit word, delete word, quick add (`C` guarded).
* **Loading State**: Table header visible with 8 skeleton rows in alternating `#FFFFFF` and `#FAFAFA`.
* **Empty State**: Zero words: "Your vocabulary list is empty. Add your first word or import a CSV deck."
* **Error State**: Table replacement card: "Failed to load vocabulary list. [Retry]".
* **Responsive Behavior**: Table collapses into clean card list on screens <768px.
* **Accessibility**: `<table>` with `scope="col"` headers, audio buttons labeled with word name.

---

### SCREEN 04: Vocabulary Quick Add (Modal / Slide-Over)
* **Route**: Triggered globally via keyboard shortcut `C` (when not typing) or `[Add Word]` button.
* **Phase**: **MVP (First Vertical Slice)**
* **Shell**: Accessible Dialog / Sheet Overlay (`max-w-md`).
* **Purpose**: Fast, keyboard-safe word entry allowing users to add terms in under 5 seconds without leaving their current screen.
* **Component Hierarchy**:
  ```text
  <QuickAddDialog open={isOpen} onOpenChange={handleSafeClose}>
    <DialogHeader title="Add Vocabulary" subtitle="Press Mod+Enter to save and add another" />
    <QuickAddForm>
      <FormField label="Word / Phrase" required autofocus />
      <FormFieldRow grid grid-cols-2 gap-3>
        <FormField label="Part of Speech" select options="noun, verb, adj, adv, idiom" />
        <FormField label="Phonetic (IPA)" placeholder="/.../" optional />
      </FormFieldRow>
      <FormField label="Definition" textarea rows={2} required />
      <FormField label="Example Sentence" textarea rows={2} optional />
      <FormField label="Collection" select options="Select collection..." optional />
      <DialogFooter flex justify-between>
        <KbdShortcutHint text="Mod+Enter to save & new · Esc to cancel" />
        <Button variant="primary">Save Word</Button>
      </DialogFooter>
    </QuickAddForm>
    {showUnsavedWarning && (
      <UnsavedAlert title="Discard unsaved changes?" onKeep="keep" onDiscard="discard" />
    )}
  </QuickAddDialog>
  ```
* **Main Actions**: Type fields using `Tab`, submit via `Mod+Enter`, cancel via `Esc` (guarded if dirty).
* **Loading State**: Save button shows inline spinner while persisting.
* **Empty State**: Input fields pre-cleared, autofocus pinned to first field.
* **Error State**: Field-level validation error text (`text-sm text-[#737373]`).
* **Responsive Behavior**: Centered modal on desktop; full-screen bottom drawer on mobile.
* **Accessibility**: Focus trapped within dialog, `aria-modal="true"`, focus returns upon dismissal.

---

### SCREEN 05: Bulk Vocabulary Import (Basic)
* **Route**: `/vocabulary/import`
* **Phase**: **MVP (Target Scope: Basic)**
* **Shell**: Workspace Shell.
* **Purpose**: Batch import word decks via CSV/TSV paste or file upload without claiming unstudied words as learning activity.
* **Component Hierarchy**:
  ```text
  <BulkImportView>
    <PageHeader title="Import Vocabulary" subtitle="Upload CSV or paste tabular text into your library" />
    <ImportInfoBanner border p-3 bg-[#F5F5F5] rounded-md text-xs>
      Note: Imported words expand your vocabulary library. They do not count toward your study streak or activity heatmap until reviewed.
    </ImportInfoBanner>
    <ImportStepWizard currentStep={step}>
      <Step1Upload>
        <DropZone border-dashed p-10 text-center text="Drag & drop CSV/TSV or click to upload" />
        <TextareaInput placeholder="Or paste raw text here (word, definition, example)..." rows={6} />
      </Step1Upload>
      <Step2Mapping>
        <ColumnMappingTable mapSourceToTarget="Word, Definition, IPA, Sentence, Collection" />
      </Step2Mapping>
      <Step3Preview>
        <ValidationSummary valid="45" errors="2" duplicates="3" />
        <PreviewTable rows={5} />
      </Step3Preview>
    </ImportStepWizard>
    <ActionBar flex justify-end gap-3>
      <Button variant="outline">Cancel</Button>
      <Button variant="primary">Confirm Import (45 words)</Button>
    </ActionBar>
  </BulkImportView>
  ```
* **Main Actions**: Upload file, map CSV columns to schema, confirm import.
* **Loading State**: Deterministic progress bar: "Importing 34 of 120 words...".
* **Empty State**: Clean dropzone with downloadable sample CSV template (`[Download sample.csv]`).
* **Error State**: Table listing invalid rows with specific row numbers and error reasons.
* **Responsive Behavior**: Column mapping and preview tables provide horizontal scroll on mobile.
* **Accessibility**: Accessible file upload button with native `<input type="file" class="sr-only" />`.

---

### SCREEN 06: Vocabulary Detail & Edit
* **Route**: `/vocabulary/:id` & `/vocabulary/:id/edit`
* **Phase**: **MVP (First Vertical Slice)**
* **Shell**: Workspace Shell.
* **Purpose**: Deep inspection and editing of an individual word: definitions, audio, example sentences, collections, and review status.
* **Component Hierarchy**:
  ```text
  <VocabularyDetailView>
    <HeaderBreadcrumbs path="Vocabulary > Resilience" />
    <WordHeroCard border rounded-md p-6>
      <TermRow flex items-baseline justify-between>
        <TermHeading text="Resilience" text-3xl font-bold />
        <AudioPlayButton term="Resilience" />
      </TermRow>
      <PhoneticBadge text="/rɪˈzɪl.jəns/" font-mono text-sm text-[#737373] />
      <DefinitionBlock mt-4>
        <PosBadge text="noun" />
        <DefinitionText text="The capacity to withstand or recover quickly from difficulties." />
      </DefinitionBlock>
    </WordHeroCard>
    <DetailsGrid grid grid-cols-1 md:grid-cols-2 gap-6 mt-6>
      <ExampleSentencesCard border p-4>
        <SentenceList items={["Her resilience in the face of adversity was remarkable."]} />
      </ExampleSentencesCard>
      <SRSStatusCard border p-4>
        <IntervalStat label="Current Interval" value="16 days" />
        <EaseFactorStat label="Ease Factor" value="2.50" />
        <NextReviewDate label="Next Due Date" value="Oct 28, 2026" />
      </SRSStatusCard>
    </DetailsGrid>
  </VocabularyDetailView>
  ```
* **Main Actions**: Listen to audio, edit word fields, delete word, reset review interval.
* **Loading State**: Skeleton cards for hero and details grid.
* **Empty State**: N/A (URL routes to specific ID).
* **Error State**: 404 page if ID not found: "Word not found. [Return to Vocabulary]".
* **Responsive Behavior**: Two-column details grid stacks vertically on mobile.
* **Accessibility**: Audio player includes text fallback for speech synthesis.

---

### SCREEN 07: Vocabulary Collections (Basic Collections)
* **Route**: `/collections` and `/collections/:id`
* **Phase**: **MVP (Target Scope: Basic)** *(Collaborative decks and community sharing outside MVP)*
* **Shell**: Workspace Shell.
* **Purpose**: Organize vocabulary into thematic collections and study specific decks.
* **Functional Requirements in MVP**:
  1. **Create Collection**: Modal/form with Title and Description.
  2. **Rename Collection**: Inline or modal editing of title.
  3. **Delete Collection**: Explicit confirmation dialog stating: *"Deleting this collection will NOT delete the vocabulary entries contained inside it."*
  4. **Add / Remove Entries**: Assign words to collections via word edit or multi-select table action.
  5. **Browse by Collection**: Filter vocabulary table to view only words in this collection.
  6. **Review by Collection**: Direct CTA `[Study Collection]` launching SRS review filtered to this collection (`/review?collectionId=:id`).
* **Component Hierarchy**:
  ```text
  <CollectionsView>
    <PageHeader title="Collections" count="4 collections">
      <Button variant="primary" onClick="openCreateModal">New Collection</Button>
    </PageHeader>
    <DeckGrid grid grid-cols-1 md:grid-cols-3 gap-4>
      <DeckCard border rounded-md p-5>
        <DeckHeader flex justify-between items-start>
          <DeckTitle text="IELTS Academic Vocabulary" font-semibold />
          <DropdownMenu trigger="MoreVertical">
            <DropdownItem label="Rename" />
            <DropdownItem label="Delete Collection" destructive />
          </DropdownMenu>
        </DeckHeader>
        <DeckMeta text="84 words · 12 due for review" text-sm text-[#737373] mt-2 />
        <DeckActions flex justify-between items-center mt-6>
          <Button variant="outline" size="sm" href="/collections/ielts-academic">Browse Words</Button>
          <Button variant="primary" size="sm" href="/review?collectionId=123">Study Deck -></Button>
        </DeckActions>
      </DeckCard>
    </DeckGrid>
    {showDeleteDialog && (
      <ConfirmDialog 
        title="Delete collection 'IELTS Academic'?"
        description="The collection will be removed. All 84 vocabulary words will remain safely in your library."
        confirmLabel="Delete Collection"
      />
    )}
  </CollectionsView>
  ```
* **Main Actions**: Create collection, browse words in deck, launch deck review, rename, delete with safe non-cascading warning.
* **Loading State**: 3 skeleton deck cards.
* **Empty State**: "No collections created yet. Create a collection to organize your vocabulary by topic."
* **Error State**: Error banner with retry.
* **Responsive Behavior**: 3-column grid collapses to 1 column on mobile.
* **Accessibility**: Dialogs trap focus; destructive actions clearly described for screen readers.

---

### SCREEN 08: Flashcard Review (SRS Engine)
* **Route**: `/review` (or `/review?collectionId=:id`)
* **Phase**: **MVP (First Vertical Slice)**
* **Shell**: Focus Learning Shell (Zero distraction; full-screen canvas).
* **Purpose**: Spaced repetition review session using SM-2 algorithm. Promotes active recall through self-grading.
* **Component Hierarchy**:
  ```text
  <FlashcardFocusView max-w-2xl mx-auto>
    <SessionHeader flex justify-between items-center py-4>
      <ExitButton icon="X" label="Exit (Esc)" href="/dashboard" />
      <SessionProgressBar value={currentIndex} max={totalDue} />
      <QueueBadge text="14 remaining" />
    </SessionHeader>
    <CardContainer border rounded-lg p-10 min-h-[360px] flex flex-col justify-center items-center text-center>
      <WordDisplay text="Metaphor" text-3xl font-bold />
      <PhoneticDisplay text="/ˈmet.ə.fɔːr/" text-[#737373] mt-2 />
      <AudioPlayShortcut label="[Space] Listen" />
      {isFlipped && (
        <AnswerRevealBlock border-t border-[#E5E5E5] pt-6 mt-6 w-full text-left>
          <PosBadge text="noun" />
          <DefinitionText text="A figure of speech in which a word or phrase is applied to an object or action." mt-2 />
          <ExampleSentence text="Her tears were a river flowing down her cheeks." text-[#737373] mt-2 />
        </AnswerRevealBlock>
      )}
    </CardContainer>
    <InteractionFooter mt-8>
      {!isFlipped ? (
        <Button variant="primary" w-full size="lg" onClick="flipCard">Show Answer (Space)</Button>
      ) : (
        <GradeButtonGroup grid grid-cols-4 gap-3>
          <GradeButton key="1" grade="Again" interval="< 10m" kbd="1" />
          <GradeButton key="2" grade="Hard" interval="1d" kbd="2" />
          <GradeButton key="3" grade="Good" interval="3d" kbd="3" />
          <GradeButton key="4" grade="Easy" interval="7d" kbd="4" />
        </GradeButtonGroup>
      )}
    </InteractionFooter>
  </FlashcardFocusView>
  ```
* **Main Actions**: Press `Space` to flip card, press keys `1–4` to grade difficulty, press `Esc` to exit (confirms if session in progress).
* **Loading State**: Centered skeleton card box while queue pre-fetches.
* **Empty State (Session Complete)**: Clean summary card: "Queue Complete! You reviewed 38 words today. [Return to Dashboard]".
* **Error State**: "Failed to submit review grade. Reconnecting... [Retry]".
* **Responsive Behavior**: Card padding adjusts from 40px to 20px; grade buttons stack into a 2x2 grid on mobile.
* **Accessibility**: ARIA live region announces card state changes; buttons provide visible keyboard badges.

---

### SCREEN 09: Dictation Exercise & Comparison Results
* **Route**: `/dictation` and `/dictation/:exerciseId`
* **Phase**: **MVP (Target Scope: Basic)**
* **Shell**: Focus Learning Shell.
* **Purpose**: Train listening comprehension and spelling precision by transcribing spoken audio verbatim and reviewing character-level differences.
* **Component Hierarchy**:
  ```text
  <DictationFocusView max-w-3xl mx-auto>
    <SessionHeader flex justify-between items-center py-4>
      <ExitButton label="Exit" href="/dashboard" />
      <ExerciseProgress text="Sentence 3 of 10" />
      <SpeedSelector options="0.75x, 1.0x, 1.25x" />
    </SessionHeader>
    <AudioPlayerSection border rounded-md p-6 text-center>
      <PlayButton icon="Play" size="xl" />
      <WaveformProgressBar h-1.5 bg-[#F5F5F5] mt-4 />
      <KeyboardHints text="Mod+Space: Play/Pause from input · Mod+R: Replay" mt-2 />
    </AudioPlayerSection>
    <InputSection mt-6>
      <TextareaTranscriptInput placeholder="Type what you hear..." rows={4} autofocus />
    </InputSection>
    {!isSubmitted ? (
      <Button variant="primary" w-full mt-4 onClick="checkDictation">Check Answer (Mod+Enter)</Button>
    ) : (
      <DiffResultSection border rounded-md p-6 mt-6>
        <ResultScoreHeading score="92% Accuracy" badge="Pass" />
        <DiffComparisonBlock mt-4 font-mono>
          <DiffLegend text="Black: Correct · Strikethrough: Missed · Underline: Extra" />
          <InlineDiffViewer diffChunks={diffData} />
        </DiffComparisonBlock>
        <ActionRow flex justify-end gap-3 mt-6>
          <Button variant="outline" onClick="retrySentence">Try Again</Button>
          <Button variant="primary" onClick="nextSentence">Next Sentence -></Button>
        </ActionRow>
      </DiffResultSection>
    )}
  </DictationFocusView>
  ```
* **Main Actions**: Play audio (`Mod+Space`), type transcript (`Space` types naturally), submit (`Mod+Enter`), view diff.
* **Loading State**: Waveform skeleton and disabled input while audio buffer loads.
* **Empty State**: Directory of available dictation exercises.
* **Error State**: Audio load failure message with fallback audio URL or text retry.
* **Responsive Behavior**: Textarea scales fluidly; audio controls remain sticky at comfortable thumb reach on mobile.
* **Accessibility**: Diff uses non-color attributes (strikethrough and underline) as well as descriptive screen-reader text.

---

### SCREEN 10: Shadowing Exercise & Audio Recording (Basic)
* **Route**: `/shadowing` and `/shadowing/:exerciseId`
* **Phase**: **MVP (Target Scope: Basic)**
* **Shell**: Focus Learning Shell.
* **Purpose**: Train oral fluency, intonation, and rhythm by listening to native reference speech and recording synchronized voice mimicry.
* **Component Hierarchy**:
  ```text
  <ShadowingFocusView max-w-3xl mx-auto>
    <SessionHeader flex justify-between items-center py-4>
      <ExitButton label="Exit" href="/dashboard" />
      <SentenceCounter text="Step 1 of 5" />
    </SessionHeader>
    <ScriptDisplayCard border rounded-md p-6 text-center>
      <ReferenceSentence text="Fluency is not about perfection, but connection." text-xl font-medium />
      <PhoneticAnnotation text="IPA guide toggleable" text-sm text-[#737373] mt-2 />
    </ScriptDisplayCard>
    <DualAudioController grid grid-cols-1 md:grid-cols-2 gap-4 mt-6>
      <ReferencePlayerCard border p-4>
        <Label text="Native Speaker" />
        <AudioPlayButton label="Play (Mod+P)" />
      </ReferencePlayerCard>
      <UserRecorderCard border p-4>
        <Label text="Your Recording" />
        <RecordButton icon="Mic" label="Record (Mod+R)" />
        <UserAudioPlayback disabled={!hasRecording} />
      </UserRecorderCard>
    </DualAudioController>
    <AssessmentFooter flex justify-between items-center mt-8>
      <SelfAssessmentButtons options="Needs Work, Acceptable, Good, Fluent" />
      <Button variant="primary">Next Step -></Button>
    </AssessmentFooter>
  </ShadowingFocusView>
  ```
* **Main Actions**: Play native audio, record microphone take, playback user voice, self-grade, proceed.
* **Loading State**: Microphone permission prompt handling (`navigator.mediaDevices.getUserMedia`).
* **Empty State**: Directory of available shadowing scripts.
* **Error State**: Hardware error dialog if microphone is denied or unavailable.
* **Responsive Behavior**: Dual player cards stack on mobile.
* **Accessibility**: Microphone status announced to screen readers (`"Microphone recording active"`).

---

### SCREEN 11: Quiz Exercise & Results (Basic)
* **Route**: `/quiz` and `/quiz/:quizId`
* **Phase**: **MVP (Target Scope: Basic)**
* **Shell**: Focus Learning Shell.
* **Purpose**: Active testing via multiple-choice and sentence fill-in-the-blank questions.
* **Component Hierarchy**:
  ```text
  <QuizFocusView max-w-2xl mx-auto>
    <SessionHeader flex justify-between items-center py-4>
      <ExitButton label="Exit" href="/dashboard" />
      <QuizProgress text="Question 4/10" />
    </SessionHeader>
    <QuestionCard border rounded-md p-8 mt-4>
      <QuestionPrompt text="Select the word that best matches: 'To delay or postpone action'" text-lg font-medium />
      <OptionsGrid flex flex-col gap-3 mt-6>
        <OptionButton index="A" label="Procrastinate" kbd="1" />
        <OptionButton index="B" label="Accelerate" kbd="2" />
        <OptionButton index="C" label="Articulate" kbd="3" />
        <OptionButton index="D" label="Substantiate" kbd="4" />
      </OptionsGrid>
    </QuestionCard>
    {hasAnswered && (
      <ExplanationCard border-t border-[#E5E5E5] p-4 mt-6>
        <ResultFeedback text="Correct! Procrastinate means..." />
        <Button variant="primary" mt-4 onClick="nextQuestion">Continue (Mod+Enter)</Button>
      </ExplanationCard>
    )}
  </QuizFocusView>
  ```
* **Main Actions**: Select answer via click or keys `1–4`, press `Mod+Enter` to advance.
* **Loading State**: Question card skeleton.
* **Empty State (Scorecard)**: Results screen showing final score (`8/10, 80%`), review mistakes button, and return to dashboard button.
* **Error State**: Generic question load error with retry.
* **Responsive Behavior**: Full-width buttons on mobile with 48px touch targets.
* **Accessibility**: `role="radiogroup"` with `aria-checked` states for options.

---

### SCREEN 12: Learning History & Statistics (Detailed Audit)
* **Route**: `/history`
* **Phase**: **V1 Enhancement** *(Basic streak and due counts on Dashboard in MVP; deep historical audit log in V1)*
* **Shell**: Workspace Shell.
* **Purpose**: Chronological drill-down of all completed study sessions with date filters.
* **Component Hierarchy**:
  ```text
  <LearningHistoryView>
    <PageHeader title="Learning History" subtitle="Audit log of completed study sessions" />
    <HistoryFilterBar flex gap-3>
      <DateRangePicker placeholder="Filter by date range" />
      <ActivityTypeFilter options="All, Flashcard, Dictation, Shadowing, Quiz" />
    </HistoryFilterBar>
    <TimelineList mt-6>
      <TimelineDayGroup date="Today — October 12, 2026">
        <SessionLogItem time="09:15 AM" type="Flashcard Review" count="24 words" duration="12 min" />
        <SessionLogItem time="09:30 AM" type="Dictation" count="3 sentences" accuracy="94%" />
      </TimelineDayGroup>
    </TimelineList>
  </LearningHistoryView>
  ```
* **Main Actions**: Filter by date or activity type, export session history to CSV.
* **Loading State**: Timeline skeleton items.
* **Empty State**: "No study history found for the selected date range."
* **Error State**: Standard data error card with retry.
* **Responsive Behavior**: Timestamps and session duration stack vertically on mobile screens.
* **Accessibility**: Chronological list wrapped in semantic `<ol>` with accessible date headers.

---

### SCREEN 13: Settings
* **Route**: `/settings`
* **Phase**: **MVP (Target Scope)**
* **Shell**: Workspace Shell.
* **Purpose**: Manage user profile, study goals, preferred study timezone (crucial for heatmap day boundary), and audio playback preferences.
* **Component Hierarchy**:
  ```text
  <SettingsView max-w-4xl>
    <PageHeader title="Settings" subtitle="Configure your learning preferences and account" />
    <SettingsTabs value={currentTab}>
      <TabList border-b border-[#E5E5E5]>
        <TabTrigger value="general">General & Goals</TabTrigger>
        <TabTrigger value="srs">Spaced Repetition</TabTrigger>
        <TabTrigger value="audio">Audio & Playback</TabTrigger>
        <TabTrigger value="account">Account</TabTrigger>
      </TabList>
      <GeneralTabContent p-6>
        <SettingField label="Display Name" input="John Doe" />
        <SettingField label="Study Timezone" select="Asia/Ho_Chi_Minh" description="Essential: Determines midnight day boundary for heatmap and streak calculation." />
        <SettingField label="Daily Review Goal" input="20 words" description="Target for daily goal completion badge (independent of streak)." />
        <Button variant="primary" mt-4>Save Preferences</Button>
      </GeneralTabContent>
    </SettingsTabs>
  </SettingsView>
  ```
* **Main Actions**: Update timezone, adjust daily study goal targets, save preferences.
* **Loading State**: Form fields populated with pulsing skeleton placeholders.
* **Empty State**: N/A (form fields populate current user config).
* **Error State**: Validation errors rendered directly beneath specific form inputs.
* **Responsive Behavior**: Settings tabs switch from horizontal bar to scrollable strip on mobile.
* **Accessibility**: Field labels linked via `for` and `id` attributes; timezone selector includes search filtering.
