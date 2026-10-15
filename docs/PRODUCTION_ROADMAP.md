# Production roadmap — implementation and evidence ledger

Requested scope: all 16 reliability/customization/detection items. This ledger is
not a claim of completion. A source implementation, synthetic test, renderer
launch, and real-world validation are different evidence levels.

No promise of perfect accuracy is possible for server-obfuscated block data or
heuristic player-ownership detection. No PR may be merged without permission.

| # | Acceptance gate | Status |
|---|---|---|
| 1 | Isolated real single-player scenarios: underground/boundary Freecam, dimension transfer, death/respawn; separate vanilla/Sodium/Iris runs | In progress: harness/API and renderer pinning review |
| 2 | Loaded-terrain edge warning, without sending chunk requests | Implemented; headless bounds pass; real-world validation pending |
| 3 | Received block updates invalidate stale results and prioritize bounded rescans | Pending |
| 4 | Shared bounded block-read cache with hit/miss/read accounting and fair scheduling | Pending |
| 5 | Per-target priority, distance and display caps; common blocks cannot evict every rare target | Pending |
| 6 | Optional version/renderer/chunk/queue/filter diagnostics page | Implemented; native build/input validation pending |
| 7 | Header drag reorder, saved snapped positions and reset; keyboard equivalent | Pending |
| 8 | Module/setting/action search and saved favorites | Pending |
| 9 | HUD drag anchors, row visibility/pins, opacity and hide-zero controls | Pending |
| 10 | Colorblind-friendly presets, validated local import/export and legend | Pending |
| 11 | Local profiles, explicit preview/apply, backups and rollback/default restoration | Pending |
| 12 | Zoom/resize/focus/narration and optional-animation regression matrix | Pending |
| 13 | Cross-chunk base evidence, deduplication and unload/dimension invalidation | Pending |
| 14 | Human-readable evidence and reversible ignored clues | Pending |
| 15 | Optional horizontal flight, acceleration/deceleration and speed presets | Implemented with legacy instant defaults; headless dynamics pass; gameplay pending |
| 16 | Measured baseline/on frame-time, heap and first-scan latency; environment-labelled budgets | Telemetry and CI benchmark harness implemented; no results or measured budgets yet |

## Test isolation

New gameplay tests must use disposable saves beneath `build/run/clientGameTest`,
not user saves or public servers. Dedicated server tests and automatic server
EULA acceptance remain disabled. Test worlds and binaries must not be committed.

Renderer compatibility must identify exact versions and whether a shader pack
was active. Mesa/Xvfb CI frame times are not desktop GPU performance claims.
All incomplete/blocked gates must remain explicit in the final delivery.
