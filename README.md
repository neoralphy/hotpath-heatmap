# Hot Path Heatmap

A PhpStorm plugin that paints a **static "hot path" heatmap** over your code: it highlights
method call sites that may trigger expensive downstream execution paths — deep call chains,
nested loops, repository / HTTP-client access, high fan-out — so an innocent-looking line like

```php
foreach ($users as $user) {
    $this->billingService->calculate($user);   // ← may fan out through services, loops, a repo…
}
```

gets a visible warning before it becomes a production problem.

It does **not** profile at runtime. Everything is a static estimate composed from the IDE's
PSI + indexes, computed in the background so the editor stays responsive.

Supports **PHP** and **JavaScript / TypeScript** (PhpStorm bundles both languages).

## What you see

- The call name gets a **bold straight underline**, colored by severity (yellow → orange → red).
  It sits on the lowest highlighting layer, so any IDE warning/error underline on the same token
  always takes precedence (ours is suppressed there rather than doubling up).
- The **numeric score** is drawn in the **gutter**, next to the line number, in the same colors.
- Hovering shows a **tooltip** explaining *why* (loop depth, downstream depth, repository/client
  findings, fan-out, …).

## Scoring

```
score = directLoopDepth + downstreamMaxCallDepth + downstreamLoopCount
      + downstreamNestedLoopDepth + fanOut + expensiveOperationRisk

bands: 0-2 none · 3-5 low · 6-8 medium · 9-12 high · 13+ very high
```

Signals include nested loops, method calls inside loops, repository/client/gateway-like classes,
expensive method names (`find`, `save`, `send`, `query`, `dispatch`, …) and known I/O globals
(`fetch`). In JS/TS, array-iteration callbacks (`forEach`/`map`/`reduce`/…) count as loops.
Each term is gated by a setting, so heuristics can be toggled individually.

## Build & run

Requires **JDK 17**.

```bash
./gradlew buildPlugin     # build the distributable zip (build/distributions/)
./gradlew runIde          # launch a sandbox PhpStorm with the plugin loaded
./gradlew verifyPlugin    # run the JetBrains plugin verifier
./gradlew test            # run the unit/integration tests
```

The first build downloads a PhpStorm distribution (~1 GB) to compile against PHP + JS PSI.

## Install

Build the zip (`./gradlew buildPlugin`), then in PhpStorm:
**Settings → Plugins → ⚙ → Install Plugin from Disk…** and pick
`build/distributions/hotpath-heatmap-*.zip`.

## Performance & scope

Heavy work runs only in the background phase of an `ExternalAnnotator`, debounced by the daemon,
with hard caps (`maxCallDepth`, `maxMethodsVisitedPerCallSite`, a per-file time budget).
Built-in/library code is always skipped, and folders you've marked (via the IDE's
**Mark Directory as…** menu) as **Excluded** or **Test Sources Root** are skipped by default
(each toggleable in settings). See [CLAUDE.md](CLAUDE.md) for the architecture and the
performance rules.

Out of scope (for now): runtime/Xdebug profiling, DB query parsing, framework magic
(Laravel/Symfony), precise dynamic-dispatch resolution.

## License

Proprietary — © 2026 Aron Pelyhe, all rights reserved. The source is available for viewing and
evaluation only; it may not be used, copied, modified, or redistributed without written
permission. See [LICENSE](LICENSE).
