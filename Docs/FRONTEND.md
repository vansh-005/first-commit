# FRONTEND.md

## 1. Purpose

> **Product name: Recollect.** Tagline: "Your digital life, remembered." (Phase 8 supersedes the
> earlier hero copy in §10 and the "Memory Layer" working name.) Login is a split-screen layout on
> desktop (§11 is otherwise unchanged); UI primitives are hand-written Tailwind rather than shadcn (§9).
> The accent is tuned to `#4f5fff` (AA contrast with white text) with a lighter `--accent-text` for
> small accent-coloured text — still cool cobalt/indigo, never orange.

This document defines the frontend UX, information architecture, visual direction, and implementation rules for the hackathon MVP.

The frontend is not treated as a thin shell over the backend. The hackathon explicitly evaluates **design and usability**, so the product must feel intentional, fast, and polished.

This document is the current design contract for contributors.

---

# 2. Design Strategy

We should **finalize the core UX before implementation**, but we should not freeze every pixel before code exists.

There are three levels of decisions:

## Locked before implementation

These should not change casually:

- product navigation
- major pages
- upload flow
- search flow
- ask/citation flow
- information hierarchy
- core interaction model
- visual personality
- component system
- responsive behavior expectations

## Flexible during implementation

These may change after seeing the real UI:

- exact spacing
- font sizes
- card dimensions
- border radius
- icon size
- subtle color values
- animation timing
- shadows
- empty-state copy
- micro-interactions

## Expected post-implementation polish

After the first working frontend, we should deliberately refine:

- visual balance
- responsive behavior
- empty states
- loading states
- transitions
- hover/focus states
- typography rhythm
- thumbnail presentation
- demo-specific polish

The approved dark mockups are now the primary visual reference.

The workflow is:

```text
FRONTEND.md
      ↓
Approved dark mockups
      ↓
Implementation
      ↓
Real-browser usability review
      ↓
Visual polish
```

Do **not** treat the first generated implementation as visually final, but preserve the approved interaction model and overall design language.

---

# 3. Product UX Goal

The product should feel like:

> A calm, intelligent personal memory workspace where users can drop their digital clutter and retrieve it later without remembering filenames or folders.

It should **not** feel like:

- an AWS console,
- a generic admin dashboard,
- a chatbot with a file upload button,
- a developer tool,
- a clone of Google Drive.

The interface should make three things feel obvious:

```text
Store
Find
Ask
```

---

# 4. Primary User Journey

The demo-critical journey is:

```text
Open app
   ↓
Continue with Google
   ↓
Home / Library
   ↓
Drag-and-drop several files
   ↓
See per-file upload progress
   ↓
See files move from Processing → Ready
   ↓
Search:
"find my AWS credit screenshot"
   ↓
Relevant source appears
   ↓
Ask:
"How much AWS credit did I have?"
   ↓
Grounded answer + citation
   ↓
Click citation
   ↓
Original file opens
```

Every important frontend decision should make this flow faster and clearer.

---

# 5. Information Architecture

Recommended routes:

```text
/
    Marketing / product landing

/login
    Authentication entry point

/engineering
    Public engineering showcase (no auth)

/app
    Main authenticated workspace

/app/library
    Browse all uploads

/app/search?q=
    Semantic search results

/app/ask
    Ask-your-memory experience

/app/document/:documentId   (specified, not part of the delivered MVP scope)
    File details / preview
```

For the hackathon, `/app` can also act as the combined Home + Library experience.

Do not create many pages for features that can live naturally in the same workspace.

---

# 6. Navigation

Desktop layout:

```text
┌──────────────────────────────────────────────────────┐
│ Logo / Product Name       Search bar       User menu │
├─────────────┬────────────────────────────────────────┤
│             │                                        │
│ Home        │                                        │
│ Library     │          Main content                  │
│ Ask         │                                        │
│             │                                        │
│ + Upload    │                                        │
│             │                                        │
└─────────────┴────────────────────────────────────────┘
```

Recommended primary sidebar items:

```text
Home
Library
Ask
Upload
```

Search should remain globally accessible in the top bar.

Do not create separate top-level navigation for every media category.

Media categories belong inside Library:

```text
All
Documents
Photos
Videos
Audio
```

## Resizable / collapsible sidebar

The desktop sidebar should follow the approved mockups and reference design language.

It must support:

```text
Collapsed icon rail
      ↕
Compact sidebar
      ↕
Expanded sidebar
```

Requirements:

- desktop width is draggable/resizable,
- provide an explicit collapse control,
- remember the user's chosen width/state locally,
- collapsed mode keeps recognizable icons and tooltips,
- expanded mode shows labels and secondary information,
- content area must resize smoothly rather than being covered,
- use a drawer/sheet instead of resizing on small screens.

Suggested width behavior:

```text
Collapsed rail     ~56-68 px
Default sidebar    ~220-250 px
Maximum expanded   ~300-320 px
```

Treat these as implementation ranges rather than pixel-perfect requirements.

The resize affordance should be subtle: a thin divider/handle visible on hover or drag, similar to modern workspace applications.

---

# 7. Visual Direction

## Design references

Use the approved mockups as the strongest reference, with inspiration from products such as:

- Dropbox Dash
- Mem
- Fabric
- Glean
- modern productivity/workspace software

Take inspiration from their **clarity, density, navigation, search-first interaction, media presentation, and calm workspace feel**.

Do not copy any product literally.

## Personality

The interface should feel:

- modern,
- premium,
- calm,
- trustworthy,
- intelligent,
- personal,
- warm without feeling decorative.

It should feel like a polished personal workspace rather than a generic SaaS dashboard.

Avoid:

- loud AI-neon styling,
- glassmorphism everywhere,
- heavy gradients,
- cyberpunk styling,
- excessive glow,
- AWS-console aesthetics.

## Theme

The primary product experience is **dark-first**.

Light mode should also be supported through the same design-token system, but dark mode is the primary hackathon/demo design.

Dark theme direction:

```text
App background      deep graphite / warm charcoal
Primary surfaces    slightly lighter charcoal
Raised surfaces     soft neutral dark gray
Primary text        soft off-white
Secondary text      muted neutral gray
Borders             low-contrast neutral gray
Primary accent      cool cobalt / sapphire blue
Secondary accent    soft periwinkle / blue-indigo
Success             restrained green
Warning             muted amber
Error               restrained red
```

### Accent-color rule

Do **not** use peach, salmon, copper, burnt orange, or the warm orange highlight strongly associated with Claude as the product's primary accent.

The approved mockups used a warm peach highlight in several places; when implementing them, replace those highlight treatments with the product's cool blue accent.

Recommended direction:

```text
Primary accent       #5B6CFF   (cobalt-indigo)
Accent hover         #7180FF
Accent subtle        cool blue/indigo at low opacity
Focus ring           blue-indigo
Selected controls    blue-indigo tint
```

Exact values can be tuned in-browser, but the hue family should remain cool blue / indigo rather than orange.

Warmth should come from:

- charcoal surfaces,
- imagery and thumbnails,
- soft neutral backgrounds,
- typography,
- restrained lighting in marketing visuals,

not from an orange primary UI accent.

## Theme toggle

Provide a theme control in the authenticated app.

```text
Dark
Light
System
```

Dark should be the default presentation used for the hackathon demo unless there is a strong reason to change it.

---

# 8. Typography

Use a modern sans-serif with excellent UI readability.

Preferred:

```text
Inter
```

Alternative:

```text
Geist
```

Typography hierarchy:

```text
Hero / page title     strong, spacious
Section title         medium-bold
Body                  comfortable 14-16 px range
Metadata              smaller, muted
Labels                concise, medium weight
```

Avoid excessive bold text.

---

# 9. Component Stack

Recommended frontend stack:

```text
React
Vite
TypeScript
Tailwind CSS
shadcn/ui
Lucide icons
React Router
```

Use shadcn/ui primitives for:

- Dialog
- Dropdown
- Button
- Tabs
- Tooltip
- Sheet / Drawer
- Toast
- Skeleton
- Command / search interactions where useful

Do not mix multiple competing UI libraries.

---

# 10. Landing Page

The landing page should explain the product in seconds.

Recommended structure:

```text
Navbar

Hero
  "Your digital memory, searchable."
  supporting line
  [Get Started]
  [See how it works]

Visual product mockup

Three-value section:
  Upload anything
  Find by memory
  Ask with citations

Short trust / privacy section

Footer
```

Possible hero direction:

> Your files remember more than you do.

Supporting line:

> Upload documents, screenshots, audio and video once. Find them later using natural language.

Keep copy short.

The landing page must not become a large marketing website.

---

# 11. Login Page

The preferred primary action:

```text
[ G  Continue with Google ]
```

Then:

```text
──────── or ────────

Email
Password
[ Sign in ]
```

Google login should visually dominate.

Do not make users create an account manually during the demo if Google auth works.

---

# 12. Home / Workspace

The authenticated home should make the product immediately useful.

Recommended structure:

```text
Greeting / simple headline

Large global search input
"What are you trying to remember?"

Quick upload area / Upload button

Recent memories

Processing uploads

Recently searched / optional
```

The search input is the primary interaction.

Example:

```text
┌─────────────────────────────────────────────────┐
│  What are you trying to remember?          ⌘ K │
└─────────────────────────────────────────────────┘
```

Suggested placeholder examples can rotate or appear beneath:

```text
"that AWS credit screenshot"
"my electricity bill from August"
"the lecture about fading"
```

---

# 13. Upload UX

Upload must feel frictionless.

Support:

- drag-and-drop,
- click-to-select,
- multiple files,
- mixed file types,
- per-file progress,
- processing state after upload.

Upload surface:

```text
┌───────────────────────────────────────┐
│                                       │
│       Drop your memories here         │
│                                       │
│   PDFs, images, audio, video & docs   │
│                                       │
│            [Browse files]             │
│                                       │
└───────────────────────────────────────┘
```

Do not force the user to select file type or destination folder.

## Per-file states

```text
Queued
Uploading 42%
Uploaded
Processing
Ready
Failed
```

Use progress bars only for actual browser upload progress.

After S3 upload completes, change to an indeterminate processing state rather than pretending extraction progress is known.

---

# 14. Library

The Library is the persistent visual memory store.

Top controls:

```text
Library                         [Upload]
[All] [Documents] [Photos] [Videos] [Audio]
```

Primary content can be a responsive grid.

Use rounded segmented controls / pills for compact filters such as:

```text
All | Documents | Photos | Videos | Audio
```

Selected state should use the cool blue-indigo accent or a subtle tinted surface, not a peach/orange highlight.

## File card

A card should show:

```text
thumbnail / file-type preview
filename
type
date
status
```

Do not make every item look like a generic gray rectangle.

---

# 15. Search Experience

Semantic search is the product's signature interaction.

Search should be globally available.

When a search executes:

```text
Search input stays visible
        ↓
Results appear directly below
```

Example result:

```text
┌──────────────────────────────────────────────┐
│ [thumbnail]  Screenshot_20260903.png         │
│                                              │
│ "AWS promotional credits available..."      │
│                                              │
│ Image · Sep 3                    [Open]      │
└──────────────────────────────────────────────┘
```

Highlight the relevant snippet.

Do not show raw vector scores as "87% confidence."

---

# 16. Ask Experience

The Ask feature should feel connected to the library, not like an unrelated chatbot product.

Recommended layout:

```text
Question input

Answer

Sources
```

For follow-ups, use a lightweight conversational thread.

The source/citation area is visually important.

---

# 17. Citations

Citations are a core trust feature.

Each citation should show:

```text
file icon / thumbnail
filename
relevant snippet
page/timestamp when available
Open source
```

For video/audio, support a future pattern like:

```text
Lecture.mp4
02:05 - 02:21
[Open at 02:05]
```

Citations should be clickable and visually distinct from normal metadata.

---

# 18. Document Detail / Preview

Opening a file should show:

```text
preview
filename
metadata
status
open/download action
```

For supported browser-previewable content:

- image -> inline preview
- PDF -> embedded/browser preview
- video -> video player
- audio -> audio player

For unsupported formats:

- polished file-type panel
- metadata
- download/open action

---

# 19. Processing, Error, Loading, and Empty States

## Processing

Show:

```text
Uploading
Processing
Ready
Failed
```

Avoid technical copy such as:

```text
BDA ingestion running
Knowledge Base indexing
```

## Errors

Errors should be concise and actionable.

Bad:

```text
Bedrock ValidationException
```

Good:

```text
We couldn't process this file.
Try uploading it again.
```

## Loading

Use skeletons for:

- Library loading
- Search results
- Document details

Use spinners only for small isolated actions.

## Empty library

```text
Your memory is empty.

Upload screenshots, documents, audio or videos
and find them later using natural language.

[Upload your first files]
```

---

# 20. Responsive Design

Desktop is the primary hackathon demo target, but mobile must remain usable.

Desktop:

```text
persistent sidebar
multi-column library grid
wide search/result cards
```

Tablet:

```text
compact sidebar or drawer
2-3 column grid
```

Mobile:

```text
top navigation / bottom navigation
single-column grid
full-width search
bottom-sheet upload actions
```

Do not build a separate mobile app.

---

# 21. Accessibility

Minimum requirements:

- keyboard-accessible navigation,
- visible focus states,
- labels for inputs,
- semantic buttons,
- sufficient contrast,
- meaningful alt text,
- no critical action conveyed by color alone.

Potential shortcut:

```text
Cmd/Ctrl + K
```

for global search.

---

# 22. Motion

Motion should make the interface feel polished, not flashy.

Use subtle transitions for:

- upload completion,
- card hover,
- search result appearance,
- sidebar state,
- dialogs,
- Processing -> Ready.

Avoid:

- excessive gradients,
- bouncing UI,
- large animated backgrounds,
- long intro animations.

---

# 23. Design Tokens

Keep tokens centralized.

Example categories:

```text
background
surface
surface-raised
surface-muted
border
border-strong

text-primary
text-secondary
text-muted

accent
accent-hover
accent-subtle
focus-ring

success
warning
error

radius-sm
radius-md
radius-lg

shadow-sm
shadow-md

sidebar-width
sidebar-width-collapsed
```

Keep both dark and light values behind semantic tokens rather than styling components with mode-specific hardcoded colors.

The exact values may be tuned after seeing the implemented UI.

Avoid scattering arbitrary hex values across components.

### Primary accent invariant

Across buttons, selected tabs, focus states, active navigation items, links, progress indicators, and highlighted search states:

```text
use cool cobalt / blue-indigo
do not use peach / orange as the primary accent
```

---

# 24. Suggested Component Structure

```text
src/
├── app/
│   ├── router.tsx
│   └── providers.tsx
├── components/
│   ├── layout/
│   ├── upload/
│   ├── library/
│   ├── search/
│   ├── ask/
│   └── ui/
├── pages/
│   ├── LandingPage
│   ├── LoginPage
│   ├── HomePage
│   ├── LibraryPage
│   ├── SearchPage
│   ├── AskPage
│   └── DocumentPage
├── api/
├── auth/
├── hooks/
├── types/
└── styles/
```

Do not over-separate components before they are reusable.

---

# 25. Demo-specific polish

Prepare a curated dataset containing visually distinct examples:

```text
AWS credit screenshot
internship offer PDF
electricity bill
lecture audio/video
receipt or warranty
```

Avoid demoing with filenames like:

```text
file1.pdf
test.png
sample.docx
```

---

# 26. Approved Mockup Direction

The following four mockup concepts are approved as the implementation reference:

1. Landing + Login
2. Home + Library
3. Semantic Search Results
4. Ask + Citations

The approved qualities to preserve are:

- dark premium workspace layout,
- left-side icon rail / resizable navigation,
- global search at the top,
- strong media thumbnails,
- dense but breathable information hierarchy,
- rounded controls and cards,
- subtle borders rather than heavy shadows,
- visible source/citation cards,
- restrained motion,
- warm overall atmosphere.

### Required deviation from the mockups

The generated mockups contain peach/orange accent treatments.

**Do not reproduce those accent colors.**

Replace them with the cool cobalt / blue-indigo accent specified in this document.

The mockups define layout and visual character; this document overrides them for color-system decisions.

---

# 27. What can change later?

Yes — the frontend can and should improve after the first implementation.

The important rule is:

> Iterate visually without casually changing the product interaction model.

Cheap changes later:

```text
card spacing
colors
typography
animations
thumbnail style
```

Expensive changes later:

```text
navigation model
upload workflow
search vs ask behavior
page structure
```

That is why this document freezes UX architecture but leaves visual polish iterative.

---

# 28. Agent Rules

Before implementing frontend work:

1. read this document,
2. read `PRODUCT.md`,
3. read `API.md`,
4. inspect existing frontend code,
5. preserve established design tokens and interaction patterns.

Do not invent a new visual style per page.

When using the approved mockups:

- preserve layout, hierarchy, density, and workspace feel,
- replace peach/orange primary highlights with cool cobalt/blue-indigo,
- implement the sidebar as resizable/collapsible,
- support dark/light/system themes through semantic design tokens.

Do not make the UI look like an AWS console.

Do not expose implementation names such as:

```text
Bedrock
BDA
S3 Vectors
DynamoDB
```

to end users unless explicitly part of an architecture/demo page.

---

# 29. Frontend completion criteria

The frontend MVP is complete when:

- Google sign-in feels smooth,
- upload requires minimal explanation,
- batch upload progress is understandable,
- processing state is visible,
- the library is pleasant to browse,
- semantic search feels like the primary product feature,
- Ask produces clear citations,
- source files are easy to open,
- loading/error/empty states are polished,
- the app looks cohesive on a fresh browser,
- a judge can understand the product within roughly 30 seconds.
