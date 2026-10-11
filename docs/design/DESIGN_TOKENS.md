# EnglishFlow — Design Tokens & UI Architecture Specification

**Document Version**: 2.0.0  
**Milestone**: M0 — Discovery & Blueprint (Task M0-T03)  
**Status**: Corrected Specification (Aligned with Heatmap Day States & Typing Safety)  
**Theme**: Modern Minimalism & Monochrome SaaS Dashboard  

---

## 1. Design Philosophy & Architectural Principles

EnglishFlow's visual language is engineered for high-efficiency cognitive learning. Learning English (vocabulary acquisition, spaced repetition, dictation, and shadowing) requires deep focus and sustained attention. Visual distractions, colorful gamification widgets, neon gradients, and animated physics deplete working memory.

### Core Principles
1. **Monochrome Rigor**: The interface is built on pure white (`#FFFFFF`), near-black (`#111111`), and calibrated neutral grays. Color is not used for decoration.
2. **Subtle Surface Definition**: Surfaces are separated by crisp 1px borders (`#E5E5E5`) and subtle recessed backgrounds (`#F5F5F5`), rather than fuzzy drop shadows or colorful card backgrounds.
3. **Restrained Typography**: High legibility sans-serif for UI strings and phonetic transcriptions, coupled with monospace precision for audio timestamps, keyboard shortcut badges, and text comparisons.
4. **Micro-Transitions Only**: Transitions are capped at 100–150ms. No bounce, spring physics, or staggered entrance delays. Critical interactions never wait on animation.
5. **Universal Accessibility (WCAG 2.1 AA)**: All text pairings meet a minimum contrast ratio of 4.5:1 (primary text `#111111` on `#FFFFFF` yields 17.4:1; secondary text `#737373` on `#FFFFFF` yields 4.9:1). Focus rings are explicit and high-contrast (`ring-2 ring-[#111111] ring-offset-2`).

---

## 2. Color System

### 2.1 Base Palette (Hex & HSL)

| Token Name | HEX | HSL | Semantic Meaning | Contrast on `#FFF` |
| :--- | :--- | :--- | :--- | :--- |
| `color-white` | `#FFFFFF` | `hsl(0, 0%, 100%)` | Canvas background, card surface, text invert | 1.0:1 |
| `color-gray-50` | `#FAFAFA` | `hsl(0, 0%, 98%)` | Hover states, pre-registration heatmap cells | 1.05:1 |
| `color-gray-100` | `#F5F5F5` | `hsl(0, 0%, 96%)` | Recessed panels, badge backgrounds, heatmap L0 | 1.1:1 |
| `color-gray-200` | `#E5E5E5` | `hsl(0, 0%, 90%)` | Primary borders, card dividers, disabled backgrounds | 1.3:1 |
| `color-gray-300` | `#D4D4D4` | `hsl(0, 0%, 83%)` | Input borders, heatmap L1, active tab borders | 1.6:1 |
| `color-gray-400` | `#A3A3A3` | `hsl(0, 0%, 64%)` | Placeholder text, disabled text, heatmap L2 | 2.5:1 |
| `color-gray-500` | `#737373` | `hsl(0, 0%, 45%)` | Secondary metadata, helper text, icon default | 4.9:1 (AA Pass) |
| `color-gray-700` | `#525252` | `hsl(0, 0%, 32%)` | Strong secondary labels, heatmap L3 | 7.9:1 (AAA Pass) |
| `color-gray-900` | `#171717` | `hsl(0, 0%, 9%)` | Dark surface, heatmap L4, deep headers | 16.5:1 (AAA Pass) |
| `color-black` | `#111111` | `hsl(0, 0%, 7%)` | Primary text, primary CTA buttons, high-contrast borders | 17.4:1 (AAA Pass) |

### 2.2 Semantic CSS Variables & shadcn/ui Mapping

```css
:root {
  /* Canvas & Surfaces */
  --background: 0 0% 100%;           /* #FFFFFF */
  --foreground: 0 0% 7%;             /* #111111 */
  --card: 0 0% 100%;                 /* #FFFFFF */
  --card-foreground: 0 0% 7%;        /* #111111 */
  --popover: 0 0% 100%;              /* #FFFFFF */
  --popover-foreground: 0 0% 7%;     /* #111111 */

  /* Muted & Secondary */
  --muted: 0 0% 96%;                 /* #F5F5F5 */
  --muted-foreground: 0 0% 45%;      /* #737373 */

  /* Primary Interactive Elements */
  --primary: 0 0% 7%;                /* #111111 */
  --primary-foreground: 0 0% 100%;   /* #FFFFFF */

  /* Secondary Interactive Elements */
  --secondary: 0 0% 96%;              /* #F5F5F5 */
  --secondary-foreground: 0 0% 7%;   /* #111111 */

  /* Borders & Dividers */
  --border: 0 0% 90%;                /* #E5E5E5 */
  --input: 0 0% 83%;                 /* #D4D4D4 */
  --ring: 0 0% 7%;                   /* #111111 (High-contrast focus ring) */

  /* Destructive & Warning (Monochrome-anchored with accessible clarity) */
  --destructive: 0 0% 15%;           /* #262626 */
  --destructive-foreground: 0 0% 100%;/* #FFFFFF */
  --error-text: 0 72% 51%;           /* #DC2626 — High-contrast alert text */

  /* Activity Heatmap Greyscale Ramp (Aligned with HEATMAP_SPEC.md) */
  --heatmap-level-0: 0 0% 96%;       /* #F5F5F5 (Zero activity on registered past day) */
  --heatmap-level-1: 0 0% 83%;       /* #D4D4D4 (1–9 actions [PROPOSED]) */
  --heatmap-level-2: 0 0% 64%;       /* #A3A3A3 (10–19 actions [PROPOSED]) */
  --heatmap-level-3: 0 0% 32%;       /* #525252 (20–39 actions [PROPOSED]) */
  --heatmap-level-4: 0 0% 9%;        /* #171717 (40+ actions [PROPOSED]) */
  --heatmap-pre-reg: 0 0% 98%;       /* #FAFAFA (Pre-registration window) */
  --heatmap-future: 0 0% 100%;       /* #FFFFFF (Future day, opacity-40) */

  /* Radii */
  --radius: 0.375rem;                /* 6px standard */
}
```

---

## 3. Typography Hierarchy

### 3.1 Typefaces
* **Primary UI Font**: `Inter`, `-apple-system`, `BlinkMacSystemFont`, `"Segoe UI"`, `Roboto`, `sans-serif`.
* **Phonetic & Pronunciation**: `Inter` (supports full International Phonetic Alphabet Unicode blocks).
* **Monospace / Code / Timestamps**: `JetBrains Mono`, `ui-monospace`, `"SFMono-Regular"`, `Menlo`, `monospace`.

### 3.2 Type Scale

| Scale Token | Font Size | Line Height | Tracking | Weight | Target Use Case |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `text-xs` | `12px` (0.75rem) | `16px` (1rem) | `+0.01em` | 400 / 500 | Badges, keyboard shortcut labels, table timestamps |
| `text-sm` | `14px` (0.875rem) | `20px` (1.25rem) | `0` | 400 / 500 | Body default, input labels, table cell content, buttons |
| `text-base` | `16px` (1.0rem) | `24px` (1.5rem) | `0` | 400 / 500 | Dictation exercise text, vocabulary definitions, modals |
| `text-lg` | `18px` (1.125rem) | `28px` (1.75rem) | `-0.01em` | 500 / 600 | Section headers, card titles, drawer subheadings |
| `text-xl` | `20px` (1.25rem) | `28px` (1.75rem) | `-0.015em`| 600 | Screen headers, modal titles |
| `text-2xl` | `24px` (1.5rem) | `32px` (2rem) | `-0.02em` | 600 | Flashcard word display (normal), major metric totals |
| `text-3xl` | `30px` (1.875rem) | `36px` (2.25rem) | `-0.025em`| 700 | Flashcard focus word display, dashboard greeting |
| `text-4xl` | `36px` (2.25rem) | `40px` (2.5rem) | `-0.03em` | 700 | Hero statistics, milestone celebration numerals |

---

## 4. Spacing, Layout & Grid System

### 4.1 Base 4px Spacing Tokens
* `space-1` (4px): Micro gaps between icon and text, tag paddings.
* `space-2` (8px): Button internal vertical padding, dense list item gaps.
* `space-3` (12px): Standard button horizontal padding, form field gaps.
* `space-4` (16px): Card internal padding, standard component margins.
* `space-6` (24px): Grid row/column gaps, card-to-card spacing.
* `space-8` (32px): Section separation, dashboard widget margins.
* `space-12` (48px): Page-level vertical padding.

### 4.2 Structural Dimensions
* **Header Height**: `56px` (`h-14`), fixed with 1px border-b `#E5E5E5`.
* **Sidebar Width (Desktop Expanded)**: `240px` (`w-60`), fixed with 1px border-r `#E5E5E5`.
* **Sidebar Width (Desktop Collapsed)**: `64px` (`w-16`).
* **Sidebar Mobile Drawer**: `280px` slide-out sheet over `rgba(0,0,0,0.4)` backdrop.
* **Main Container Max Widths**:
  * Dashboard / Table Views: `max-w-7xl` (`1280px`) with fluid margins.
  * Focused Study Canvas (Flashcards / Dictation / Shadowing): `max-w-3xl` (`768px`) centered.
  * Form Modals / Quick Add Slide-Over: `max-w-lg` (`512px`) or `w-full sm:max-w-md`.

---

## 5. Shape, Borders & Elevation

### 5.1 Corner Radii (`border-radius`)
* `rounded-none` (`0px`): Strict rectangular containers (tables, heatmap cells, toolbars).
* `rounded-sm` (`2px`): Kbd shortcut keys, heatmap grid cells (`rounded-[2px]`).
* `rounded-md` (`6px`): Buttons, text inputs, dropdown menus, cards, modal dialogs (**Standard**).
* `rounded-lg` (`8px`): Outer perimeter of major dialogs and focus canvas.
* *Explicit Ban*: No `rounded-full` pills or oversized `rounded-2xl`/`3xl` bubbly corners.

### 5.2 Borders & Dividers
* Default Border: `1px solid #E5E5E5` (`border border-neutral-200`).
* Input Active / Hover Border: `1px solid #111111` or `#D4D4D4`.
* Dividers: Horizontal rules and list separators use `h-[1px] bg-[#E5E5E5]`.

### 5.3 Elevation & Shadows
* Default Elements: **Zero shadow** (`shadow-none`). Depth is achieved strictly via surface color shifts (`#FFFFFF` on `#FAFAFA`) and `#E5E5E5` perimeter borders.
* Floating Overlays (Dropdowns, Tooltips, Popovers): `shadow-sm` (`0 1px 2px 0 rgba(0, 0, 0, 0.05)`).
* Modal Dialogs: `shadow-md` (`0 4px 6px -1px rgba(0, 0, 0, 0.08)`), combined with a neutral backdrop `bg-black/20`.

---

## 6. Motion & Interaction Standards

1. **Duration**: All CSS transitions must be between `100ms` and `150ms`.
   * Standard: `transition-colors duration-150 ease-out` or `transition-opacity duration-100 ease-out`.
2. **Timing Function**: Strict cubic-bezier `ease-out` (`cubic-bezier(0, 0, 0.2, 1)`).
3. **Restricted Motion**:
   * No bouncing, elastic, or spring easing curves.
   * No animated page slide entrances or staggered cascading lists.
   * Tooltip appearances use instantaneous or `100ms` opacity fade.
4. **Accessibility Override**:
   ```css
   @media (prefers-reduced-motion: reduce) {
     *, ::before, ::after {
       animation-duration: 0.01ms !important;
       animation-iteration-count: 1 !important;
       transition-duration: 0.01ms !important;
       scroll-behavior: auto !important;
     }
   }
   ```

---

## 7. Component Token Specifications

### 7.1 Buttons (`shadcn/ui Button`)
* **Primary**: `bg-[#111111] text-[#FFFFFF] hover:bg-[#262626] active:bg-[#000000] border-transparent font-medium rounded-md h-9 px-4 text-sm`.
* **Secondary / Outline**: `bg-[#FFFFFF] text-[#111111] border border-[#E5E5E5] hover:bg-[#F5F5F5] hover:border-[#D4D4D4] rounded-md h-9 px-4 text-sm`.
* **Ghost**: `bg-transparent text-[#111111] hover:bg-[#F5F5F5] rounded-md h-9 px-3 text-sm`.
* **Destructive**: `bg-[#FFFFFF] text-[#111111] border border-[#E5E5E5] hover:bg-[#111111] hover:text-[#FFFFFF] rounded-md h-9 px-4 text-sm`.
* **Focus State**: `focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#111111] focus-visible:ring-offset-2`.

### 7.2 Form Controls (Input, Textarea, Select)
* **Default**: `bg-[#FFFFFF] text-[#111111] border border-[#D4D4D4] rounded-md h-9 px-3 text-sm placeholder:text-[#A3A3A3]`.
* **Focus State**: `focus:border-[#111111] focus:ring-1 focus:ring-[#111111] focus:outline-none`.
* **Disabled**: `bg-[#F5F5F5] text-[#A3A3A3] border-[#E5E5E5] cursor-not-allowed`.
* **Keyboard Shortcut Badges (`<kbd>`)**: `bg-[#F5F5F5] text-[#525252] border border-[#E5E5E5] rounded-[2px] px-1.5 py-0.5 text-xs font-mono`.

### 7.3 Cards & Containers
* `bg-[#FFFFFF] border border-[#E5E5E5] rounded-md p-6 shadow-none`.
* Header inside card: `border-b border-[#E5E5E5] pb-4 mb-4`.

### 7.4 Tables & Lists
* Table Header (`<thead>`): `bg-[#FAFAFA] border-b border-[#E5E5E5] text-xs font-medium text-[#737373] uppercase tracking-wider h-10 px-4`.
* Table Row (`<tr>`): `border-b border-[#E5E5E5] hover:bg-[#FAFAFA] transition-colors h-12`.
* Table Cell (`<td>`): `px-4 text-sm text-[#111111] align-middle`.

### 7.5 Dictation Text Comparison Diff Tokens
* **Correct Match**: `text-[#111111] bg-transparent font-normal`.
* **Missing / Omission**: `text-[#737373] line-through bg-[#F5F5F5] px-1 rounded-[2px]`.
* **Incorrect Input**: `text-[#111111] underline decoration-[#111111] decoration-1 underline-offset-4 bg-[#F5F5F5] px-1 rounded-[2px] font-medium`.

### 7.6 Audio Controls & Waveforms
* Audio Track Background: `bg-[#F5F5F5] h-1.5 rounded-none relative`.
* Audio Progress / Buffer Fill: `bg-[#111111] h-1.5`.
* Play/Pause Button: 32px x 32px circular or square icon button in `#111111` with `#FFFFFF` Lucide icon.
* Speed Selector: Monospace chip (`0.75x`, `1.0x`, `1.25x`) toggled via segmented control (`border border-[#E5E5E5] rounded-md p-0.5`).
