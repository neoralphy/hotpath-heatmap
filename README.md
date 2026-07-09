# Hot Path Heatmap

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/32400.svg)](https://plugins.jetbrains.com/plugin/32400-hot-path-heatmap)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/32400.svg)](https://plugins.jetbrains.com/plugin/32400-hot-path-heatmap)
[![Rating](https://img.shields.io/jetbrains/plugin/r/rating/32400.svg)](https://plugins.jetbrains.com/plugin/32400-hot-path-heatmap)

**Spot the expensive call before production does.**

Hot Path Heatmap statically scores every method call in your editor and lights up the ones that may
trigger costly downstream work — database and HTTP calls, N+1 query loops, deep call chains — for
**PHP, JavaScript/TypeScript, Java, Kotlin, Groovy, and Scala**.

<!-- Screenshot slot (tracked in the listing-assets task): editor heatmap with underlines, gutter
     scores and the why-tooltip. -->

In source, every call reads as equally cheap: `user.getId()` looks exactly like
`billing.recalculate(user)` — yet one is a field access and the other may fan out through services,
loops, and a repository. And the classic performance killer hides in plain sight: a harmless-looking
call inside a loop that turns into hundreds of queries.

```php
foreach ($users as $user) {
    $this->billingService->calculate($user);   // ← one call, potentially hundreds of queries
}
```

The heatmap makes that difference visible at a glance — no profiler, no instrumentation, no test
traffic.

## Highlights

- **Resource-aware scoring.** Database, HTTP/API, cache, message-queue, search, mail and filesystem
  operations anywhere down the call chain dominate the score — a real DB call is never buried under
  trivial fan-out.
- **N+1 detection.** A resource operation reached *inside a loop* is amplified and called out, so the
  classic N+1 pattern surfaces as a top offender.
- **Explains itself.** Every flagged call gets a colored underline, a cost score in the gutter, and a
  tooltip that says *why* — which downstream operations, at which loop depth.
- **One-click tuning.** Wrongly flagged? Exclude that target from the tooltip or with Alt+Enter.
  Your exclusions and custom resource keywords are stored per-developer, no setup required.
- **Zero setup.** A static estimate built from the IDE's own PSI/indexes, computed in the background
  for open files only, so the editor stays responsive.

It does **not** profile at runtime — everything is a static, heuristic estimate. It is an
early-warning system that makes you look twice at the right lines, not a substitute for a profiler.

## Install

**From the JetBrains Marketplace:**
Settings → Plugins → Marketplace → search **"Hot Path Heatmap"** → Install.
Or open the listing directly: <https://plugins.jetbrains.com/plugin/32400-hot-path-heatmap>.

Works in any IntelliJ-based IDE (PhpStorm, WebStorm, IntelliJ IDEA, …); it activates whichever
supported languages are present. Dynamic languages (Python, Ruby) are intentionally excluded — their
weak static call-resolution would make cost estimates misleading.

## Free vs Pro

The full single-developer experience is — and stays — **free**. Pro exists for teams that want to
share their tuning through version control. Pro licensing arrives with 1.0.0 on the JetBrains
Marketplace; nothing is purchasable before that.

|                                                              | Free | Pro |
|--------------------------------------------------------------|:----:|:---:|
| All languages (PHP · JS/TS · Java · Kotlin · Groovy · Scala) | ✅   | ✅  |
| Resource-first scoring & N+1 detection                       | ✅   | ✅  |
| Underlines, gutter scores, explanatory tooltips              | ✅   | ✅  |
| One-click "not expensive" exclusions & custom keywords (personal) | ✅ | ✅ |
| Sensitivity presets, per-heuristic toggles, color presets (incl. colorblind-safe) | ✅ | ✅ |
| **Team-shared, committable `hotpath-heatmap.json`** (exclusions & keywords synced via VCS) | — | ✅ |
| Configurable team-config file location                       | —    | ✅  |

## Privacy

**All analysis is 100% local.** The plugin reads your code through the IDE's own indexes, computes
everything on your machine, and sends nothing anywhere — no network calls, no telemetry, no account.

## What you see

- The call name gets a **straight underline** on a 5-level, theme-aware deep-sea ramp
  (teal → cyan → blue → violet → magenta), sitting just below the text. It's on the lowest
  highlighting layer, so any IDE warning/error on the same token always wins.
- **Watch (teal)** and **Costly (cyan)** are *absolute* — the same in every file. The **Hot-path**
  band is split **file-relatively** across the top three shades, so one file distinguishes its
  mildest hot call from its worst. The **numeric gutter score** is always the *absolute* cost, so you
  keep a cross-file comparison.
- Hovering shows a **tooltip** that leads with the detected resource operations (and their loop
  context), then the structural context — and, for hot-path calls, their relative intensity within
  the file.

<!-- GIF slot (tracked in the listing-assets task): the one-click exclude flow. -->

## Scoring

Resource operations lead; structure is context (capped):

```
score           = resourceScore + structuralScore
resourceScore   = Σ  weight(kind) × confidence × loopFactor(loopDepth)   // the headline
structuralScore = min(fanOut, 14) + downstreamMaxCallDepth + min(loopCount, 6)   // context, capped
```

Each resource kind (DB / HTTP / cache / queue / search / mail / filesystem) weighs ~8–12 while
structural terms are ~1 and capped, so a real resource op outweighs a pile of trivial fan-out. A
resource op **inside a loop** — the classic N+1 — is multiplied by `1 + 2·min(loopDepth, 3)`.

Three marked bands — **Watch · Costly · Hot path** (plus unmarked trivial). The cutoffs come from a
**Sensitivity** preset (Low / Medium / High); **Medium is the default**, so one clear resource op is
Costly and a resource-in-loop / two resource ops / extreme structure is Hot path. In JS/TS and the
JVM languages, higher-order iteration callbacks (`forEach`/`map`/`reduce`/…) count as loops. Every
term is gated by a setting, so heuristics can be toggled individually.

## Performance & scope

Heavy work runs only in a background, daemon-debounced analysis pass with hard caps (call depth,
methods visited, a per-file time budget) — the editor never blocks on it. Built-in/library code is
always skipped; folders marked as **Excluded** or **Test Sources Root** are skipped by default
(each toggleable).

Out of scope (for now): runtime/Xdebug profiling, DB query parsing, framework magic
(Laravel/Symfony), precise dynamic-dispatch resolution.

## Bugs & feedback

This repository is the plugin's homepage and **issue tracker** — [open an issue](../../issues) for
bugs, false positives/negatives, or feature requests. Release announcements are published under
[Releases](../../releases).

## License

Hot Path Heatmap is commercial, closed-source software — © 2026 Aron Pelyhe, all rights reserved.
This repository contains the product homepage and issue tracker only, no source code. The free tier
is free to use; Pro is licensed through the JetBrains Marketplace (EULA will be linked here and in
the listing before Pro goes on sale).
