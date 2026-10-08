# Markview: a modern native Android Markdown reader

## Context
The repo (`derekross/Markview`, branch `claude/android-markdown-reader-vqna69`) is empty, so this is a greenfield project. Today's Android Markdown apps put the editor first and feel dated: TextView spans, cramped typography, no Material You. Examples are MD Reader, MarkReader, and Markor. A few newer apps add Mermaid and math (Markdown Viewer: AI Reader, PilcrowMD), but none combine a polished reading experience, rich rendering, and a social/publishing layer.

**Goal:** a reader first. It should feel as polished as Apple Books or Readwise Reader, with quick-edit as a secondary feature.

**Decisions so far:**
- Kotlin + Jetpack Compose, Android only.
- Read-first, with a quick-edit mode.
- Fully FOSS: Play Store + F-Droid, no Google Play Services, no trackers.
- Feature focus: rich rendering, reading comfort, and Nostr (NIP-23 long-form).

## Tech stack
- **Language/UI:** Kotlin 2.x, Compose, Material 3. Use stable `material3`, and opt into Expressive APIs (`MaterialExpressiveTheme`, `MotionScheme.expressive()`, flexible top bars, `LoadingIndicator`, floating toolbar) where they are available. Use dynamic color on Android 12+, with a brand palette as the fallback.
- **minSdk 26, target/compileSdk latest.** Edge-to-edge, predictive back, and adaptive layouts via `material3-adaptive` (list-detail for tablets and foldables).
- **Architecture:** single-activity, Navigation Compose with type-safe routes, MVVM + `StateFlow`, Hilt for DI, Coroutines.
- **Storage:** Room for recents, reading positions, favorites, and Nostr cache. DataStore for preferences.
- **Files:** Storage Access Framework (`OpenDocument` / `OpenDocumentTree`) with persisted URI permissions. Intent filters for `text/markdown`, `text/x-markdown`, `*.md`/`.markdown`, `text/plain`, plus `ACTION_SEND` text and http(s) URLs.
- **Parsing:** `commonmark-java` 0.28+ with these extensions:
  - GFM tables, strikethrough, task lists, autolink
  - heading anchors, footnotes, YAML front matter
  - a custom inline/block parser for `$…$` / `$$…$$` math and GitHub-style `> [!NOTE]` callouts
- **Images:** Coil 3, plus `coil-svg` for remote and local images and badges.
- **Code highlighting:** a pure-Kotlin highlighter. Evaluate `dev.snipme:highlights` (KMP, many languages, themes). Fall back to Prism4j grammars.
- **Build:** Gradle version catalog, KSP, R8, reproducible builds (needed for F-Droid), and GitHub Actions CI (lint, unit tests, assemble).

## Rendering architecture (the core design decision)
Rendering is **Compose-native**, with a headless WebView used only as a background "SVG render service" for Mermaid and math:

1. Parse to an AST off the main thread (`Dispatchers.Default`). Map it to an immutable `List<MdBlock>` with these types:
   - Heading, Paragraph, Quote, Callout, List, CodeBlock
   - Table, Image, MathBlock, Mermaid, ThematicBreak, FootnoteDefs
2. Render with a `LazyColumn`, one item per top-level block. This gives:
   - smooth handling of very large documents
   - an exact scroll position by block index, used for position memory, TOC jumps, and the progress bar
   - stable keys for animations
3. Map inline content to `AnnotatedString`. Links use `LinkAnnotation`. Inline code gets a span background. Inline math uses `InlineTextContent`. Paragraphs sit in `SelectionContainer` for selection.
4. **Mermaid and math** go through a single hidden WebView. It loads bundled MathJax 3 (`tex-svg`, Apache-2.0) and Mermaid (MIT, `htmlLabels:false` so labels are pure SVG) from assets, fully offline.
   - Input is source text; output is an SVG string.
   - Results are cached by content hash in memory and on disk.
   - The SVG is drawn natively via AndroidSVG/Coil.
   - Diagrams get pinch-zoom in a fullscreen viewer.
5. Wide tables and code blocks scroll horizontally inside their own containers. Code blocks get a copy button, a language label, and a wrap toggle.
6. Theme tokens come from a `MarkdownTheme` object (typography, spacing, colors, code theme). It derives from `MaterialTheme` and user reader settings.

**Why not a full WebView or Markwon?** A full WebView feels non-native: scroll physics, selection handles, and theming are all off. Markwon is View-based and largely unmaintained. Off-the-shelf Compose renderers (mikepenz, etc.) don't support callouts, math, Mermaid, or block-indexed scrolling. Owning the renderer is the product's moat.

## Module layout
```
app/                      Activity, nav graph, DI wiring, intent handling
core/markdown/            commonmark setup, custom extensions, AST → MdBlock model (pure JVM, heavily unit tested)
core/render/              Compose renderers for MdBlock, MarkdownTheme, code highlighter, SVG render service
core/data/                Room DB, DataStore, SAF document repository, file watcher
core/designsystem/        Theme, typography presets, fonts, shared components
feature/home/             Recents, favorites, open file/folder, paste, URL
feature/reader/           Reader screen, TOC sheet/rail, find-in-doc, reading settings, TTS
feature/editor/           Quick edit (plain text editor + live preview, split on large screens)
feature/nostr/            NIP-23 browse/read/publish
```

## UX highlights (the "beautiful" part)
- **Home:** large expressive header and "Continue reading" cards that show progress rings. Also recents, favorites, a folder browser, and a FAB menu (Open file, Open folder, Paste, From URL, Nostr).
- **Reader:**
  - A collapsing flexible top app bar shows the document title, and the front matter appears as an elegant metadata card.
  - The top bar hides on scroll down and returns on scroll up.
  - A floating bottom toolbar holds TOC, Search, Aa (typography), Edit, and Share.
  - Heading scrubber: a fast-scroll thumb shows the current section name.
- **Typography presets:** "Editorial" (serif, e.g. Literata or Source Serif), "Modern" (Inter), and "Technical" (sans + JetBrains Mono). Settings also cover size, line height, reading width (measure), and paragraph spacing.
- **Themes:** System/Light/Dark, Sepia, and OLED Black, plus dynamic color. A custom code palette matches each theme.
- **Motion:** spring-based expressive motion, a shared-element transition from a recent card into the reader, and a shaped `LoadingIndicator` while parsing.
- **Tablets and foldables:** a persistent TOC rail beside the document, and a split editor/preview layout.

## Standout features (beyond UI)

**Reading comfort**
1. Reading position memory per document. Resumes exactly where you left off, with a progress % on the home cards.
2. Smart TOC: a bottom sheet on phone and a rail on tablet. It highlights the current section and allows collapsing.
3. Reading time and word count, with time left in the current section.
4. Focus mode: dims everything except the current paragraph, hides chrome, and keeps the screen on.
5. Read aloud with the offline Android TTS. It highlights the paragraph being read, auto-scrolls, keeps playing in the background via a media notification, and offers a speed control.
6. Find in document with match highlighting and next/previous navigation.

**Rich rendering**
7. Mermaid diagrams and LaTeX math, rendered fully offline.
8. GitHub-style callouts (`[!NOTE]`, `[!TIP]`, `[!WARNING]`, …), footnotes with tap-to-peek popups, task lists, and syntax highlighting for 30+ languages.
9. Smart links:
   - relative `.md` links open inside the app with a back stack
   - `#anchors` scroll to the target
   - external links open in Custom Tabs
   - long-press shows a link preview

**Open from anywhere**
10. "Open with" and the share sheet.
11. Paste Markdown from the clipboard.
12. Open a URL. GitHub `blob` URLs are auto-converted to raw, and relative images are resolved against the source.
13. Live reload when a file changes, which is useful while editing on a PC through Syncthing.

**Output and sharing**
14. Export to PDF via the Android print framework, with a styled layout.
15. Share a selected passage as a beautiful quote image.
16. Copy as rich text or HTML.

**Nostr (unique in this market)**
17. Browse and read NIP-23 long-form articles (kind 30023). Open from `naddr`/`nostr:` deep links. Follow authors by npub.
18. Publish a local `.md` file as a NIP-23 article. Signing goes through NIP-55 (Amber) external signer, or NIP-46 remote signer; no key is ever stored in-app. Front matter maps to `title`/`summary`/`image`/`t` tags.
19. NIP-84 highlights: highlight a passage in a Nostr article and publish it.

**Quick edit**
20. A monospace editor with Markdown syntax coloring and a formatting toolbar. Shows a live preview (split view on large screens) and saves back through SAF.

**Platform polish**
21. App shortcuts and a home-screen widget for recent documents.
22. Per-app language.
23. Accessibility: TalkBack semantics for headings, tables, and images, plus large-font scaling.

## Phased roadmap
- **Phase 0, Foundation:**
  - Gradle multi-module skeleton, version catalog, CI, and theme/design system.
  - Open a file via SAF and intent filters.
  - Basic `commonmark` → Compose rendering covering headings, paragraphs, lists, quotes, inline styles, and links.
- **Phase 1, MVP reader:**
  - Full GFM: tables, task lists, strikethrough, autolink, footnotes, front matter, callouts.
  - Images, code highlighting, TOC, position memory, and recents/favorites home.
  - Typography presets, themes, find-in-doc, and share/open-with.
- **Phase 2, Rich rendering and comfort:**
  - The SVG render service (Mermaid and math).
  - Focus mode, TTS, the heading scrubber, smart relative links, open-from-URL, and live reload.
  - Adaptive tablet layout.
- **Phase 3, Quick edit and export:** editor with preview, PDF export, quote images, widget, and shortcuts.
- **Phase 4, Nostr:**
  - Relay client: evaluate `quartz` from Amethyst as the Kotlin Nostr library; otherwise use a minimal OkHttp WebSocket client with secp256k1 via `fr.acinq.secp256k1`.
  - NIP-23 read and browse, `naddr` deep links, NIP-55/NIP-46 publishing, and NIP-84 highlights.
- **Phase 5, Release:**
  - Baseline profiles and a startup benchmark.
  - Play listing, F-Droid metadata (`fastlane/metadata`), and reproducible-build verification.

The first implementation pass after this plan is approved covers **Phase 0 and Phase 1**.

## Verification
- **Unit tests** (`core/markdown`): run the CommonMark spec examples and GFM fixtures through the parser into the `MdBlock` model. Add golden tests for callouts, math delimiters, and front matter.
- **Compose UI tests** (`feature/reader`) check:
  - the TOC jumps to the right block index
  - position restores after process death
  - tables and code blocks scroll horizontally
- **Screenshot tests** (Roborazzi, which runs on JVM) cover a fixture document across Light, Dark, Sepia, and OLED themes and all three typography presets.
- **Performance:** check a 1 MB `.md` fixture against parse time and jank with the Macrobenchmark module.
- **Manual testing** on an emulator via `./gradlew installDebug` uses sample docs in `samples/`: a README with badges, a math-heavy doc, Mermaid diagrams, and a long-form article. Test opening from the Files app, share sheet, and URL.
- **CI** runs `./gradlew lint test assembleDebug` on every push.
