# Hot Path Heatmap — PhpStorm Plugin

Static **hot path heatmap** for **PHP and JavaScript/TypeScript**. Highlights method call sites
in the editor that may trigger expensive downstream execution paths (deep call chains, nested
loops, repository / HTTP-client access, high fan-out). It does **not** profile at runtime —
everything is a static estimate built from PhpStorm's PSI + indexes. PhpStorm bundles both the
PHP and JavaScript plugins, so both languages are analyzed in the same IDE.

## What it does

A line like `$this->billingService->calculate($user)` looks cheap, but `calculate()` may
fan out through several services, loops, and a repository. The plugin scores each call site
by recursively composing cached *method summaries* of its downstream call graph and paints
the call site (dotted underline: amber → orange → magenta → purple) with a tooltip explaining *why*.

## Build & run

Requires **JDK 17** (installed via Homebrew `openjdk@17`) and the Gradle wrapper.

```bash
./gradlew buildPlugin     # build the distributable zip (build/distributions/)
./gradlew runIde          # launch a sandbox PhpStorm with the plugin loaded
./gradlew verifyPlugin    # run the JetBrains plugin verifier
```

The build downloads a PhpStorm distribution (~1 GB) the first time to compile against PHP PSI
(`com.jetbrains.php`). This is cached under `~/.gradle` and the IntelliJ Platform cache.

If `./gradlew` is missing, regenerate it with `gradle wrapper --gradle-version 8.13`.

## Architecture

The whole pipeline runs through an **`ExternalAnnotator`** (`annotator/HotPathAnnotator`).
That choice is deliberate and is what keeps the IDE responsive:

| Phase                | Thread            | Work                                                            |
|----------------------|-------------------|-----------------------------------------------------------------|
| `collectInformation` | EDT (read action) | grab the `PhpFile`, collect call references (cheap)             |
| `doAnnotate`         | **background**    | resolve targets + traverse the call graph inside a `ReadAction` |
| `apply`              | EDT               | paint highlights, gutter score badge, attach tooltips (cheap)   |

The daemon debounces this automatically and only runs it for open/visible files, so we never
traverse on every keystroke or block the UI thread.

### Packages (`src/main/kotlin/com/hotpath/heatmap/`)

- `model/` — pure data: `MethodSummary`, `CallSiteResult`, `Severity`, `CostBreakdown`.
- `analysis/`
  - `LanguageSupport` — the SPI the engine talks to instead of touching PHP/JS PSI directly
    (collect call sites, resolve a call, loop depth, compute a local summary, …). `LanguageSupports`
    picks the right one per file. `PhpLanguageSupport` (com.jetbrains.php) and `JsLanguageSupport`
    (com.intellij.lang.javascript) are the two adapters. JS treats array-iteration callbacks
    (`forEach`/`map`/`reduce`/…) as loop levels — the JS analog of `foreach`.
  - `HeuristicSignals` — name/class keyword tables (find, save, send, …; Repository/Client/Gateway;
    plus known I/O globals like `fetch`). Built-ins / library / non-project code are excluded via
    `ProjectFileIndex.isInContent`, so trivial calls (`mt_rand`, `sprintf`) don't inflate cost.
  - `MethodSummaryService` (project service) — computes & **caches** per-method *local* facts
    via `CachedValuesManager` keyed on `PsiModificationTracker.MODIFICATION_COUNT`. A summary
    holds only local structure (loops, outgoing calls); it never recurses, so the cache stays
    cheap and is invalidated automatically when any PHP PSI changes.
  - `CallGraphTraversal` — composes cached summaries downstream up to `maxCallDepth`, honoring
    visited-set / methods-visited / time-budget caps, and emits a `CostBreakdown` with reasons.
  - `CostHeuristics` — turns a `CostBreakdown` into a numeric score (formula from the spec).
- `annotator/`
  - `HotPathAnnotator` — the `ExternalAnnotator` wiring described above.
  - `ScoreGutterIconRenderer` — paints the numeric score in the editor gutter (left of the
    code, by the line numbers), colored by severity, so the estimate is visible without hovering.
- `settings/` — `HotPathSettings` (`PersistentStateComponent`, application level) and
  `HotPathConfigurable` (Kotlin UI DSL settings page).

### Scoring (see `CostHeuristics`)

```
score = directLoopDepth + downstreamMaxCallDepth + downstreamLoopCount
      + downstreamNestedLoopDepth + fanOut + expensiveOperationRisk
```

Severity bands: `0-2 none · 3-5 low · 6-8 medium · 9-12 high · 13+ very high`.
Each term is gated by a settings toggle, so heuristics can be turned off individually.

## Performance rules (do not regress these)

1. No synchronous whole-project analysis on the UI thread.
2. No call-graph traversal on every keystroke — rely on the daemon's debounce.
3. Heavy work only inside `doAnnotate` (background) wrapped in `ReadAction`.
4. Prefer PSI + indexes over custom parsing.
5. Cache method-level summaries (`CachedValuesManager`); invalidate via modification tracker.
6. Hard caps: `maxCallDepth=5`, `maxMethodsVisitedPerCallSite=100`, `maxAnalysisTimePerFileMs=500`.
7. Always skip built-in/library/SDK code; skip IDE-marked **Excluded** and **Test Sources**
   folders by default (each toggleable). Driven by `ProjectFileIndex`, not path strings.
8. Only analyze open editor files.
9. A global enable/disable toggle must always short-circuit everything.

When a cap is hit, traversal stops, returns a partial result, and the tooltip says the
analysis was truncated/approximate.

## Scope (MVP)

In: depth-limited static call-graph cost estimate + editor highlighting + explanatory tooltip.
Out (for now): vendor analysis, runtime/Xdebug profiling, DB query parsing, framework magic
(Laravel/Symfony), precise dynamic-dispatch resolution.
