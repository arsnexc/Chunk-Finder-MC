# Production roadmap — implementation and evidence ledger

Requested scope: all 16 reliability/customization/detection items. This ledger is
not a claim of completion. A source implementation, synthetic test, renderer
launch, and real-world validation are different evidence levels.

No promise of perfect accuracy is possible for server-obfuscated block data or
heuristic player-ownership detection. No PR may be merged without permission.

| # | Acceptance gate | Status |
|---|---|---|
| 1 | Isolated real single-player scenarios: underground/boundary Freecam, dimension transfer, death/respawn; separate vanilla/Sodium/Iris runs | Passed checkpoint 7ca755e: 21 world assertions each on vanilla/Sodium/Iris, shaders disabled; rerun required after remaining features |
| 2 | Loaded-terrain edge warning, without sending chunk requests | Passed real missing-chunk and stationary-body checks at checkpoint 7ca755e |
| 3 | Received block updates invalidate stale results and prioritize bounded rescans | Passed real mining/placement updates at checkpoint 7ca755e (2-tick fixture latency) |
| 4 | Shared bounded block-read cache with hit/miss/read accounting and fair scheduling | Passed real read reuse/hard-cap checks at checkpoint 7ca755e; counters recorded |
| 5 | Per-target priority, distance and display caps; common blocks cannot evict every rare target | Implemented fair bounded cache/display selection, transactional rules UI and persistence; headless tests pass; native validation pending |
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
| 16 | Measured baseline/on frame-time, heap and first-scan latency; environment-labelled budgets | Initial three-renderer measurements recorded; provisional regression budgets added, broader workloads still needed |

## Test isolation

New gameplay tests must use disposable saves beneath `build/run/clientGameTest`,
not user saves or public servers. Dedicated server tests and automatic server
EULA acceptance remain disabled. Test worlds and binaries must not be committed.

Renderer compatibility must identify exact versions and whether a shader pack
was active. Mesa/Xvfb CI frame times are not desktop GPU performance claims.
All incomplete/blocked gates must remain explicit in the final delivery.

## Current validation findings

- Build/UI/API suites pass at `34d723a` and `7a4995a`; these are not gameplay passes.
- Initial renderer matrix run `38713175808` loaded the intended mods but all three
  variants timed out during integrated-world loading before scenario assertions.
- Focused run `38714284627` confirmed the integrated server starts; the client
  remains on LevelLoadingScreen with no ClientLevel. Filtered logs/thread capture
  are available. Normal packet scheduling is being evaluated with Fabric's
  test-only `disableNetworkSynchronizer` option; no production networking bypass.
- Renderer artifacts are pinned in `tests/renderer-lock.json` with exact IDs and
  SHA-512 digests: Sodium 0.8.14 and Iris 1.10.8, Fabric/Minecraft 1.21.11.
- Manual workflow dispatch returned HTTP 403 (integration permission). Focused
  validation uses ordinary branch pushes; automatic gameplay is temporarily
  vanilla-only until common startup is resolved. Full matrix is still required.

- Run `38715291514` with normal packet scheduling successfully entered the real
  world and passed underground A/D, stationary-body and horizontal-flight checks.
  It then caught a production availability defect: Minecraft 1.21.11's deprecated
  `ClientLevel.hasChunk/hasChunkAt` returns true unconditionally. All affected
  production checks now use `getChunkSource().getChunk(..., FULL, false) != null`.
  The full three-renderer matrix is being rerun; no gameplay pass is claimed yet.

## First successful gameplay checkpoint

Run **38715957096**, commit `7ca755e`, passed **21 gameplay assertions per renderer**
on vanilla Fabric, Sodium 0.8.14, and Iris 1.10.8 + Sodium. Iris reported shaders
**disabled**; this is not arbitrary shader-pack compatibility.

The real world exercised underground A/D, unmoved player body, horizontal flight,
solid-terrain camera movement, missing-chunk warning, dimension transfer,
death/respawn, real ESP scan results and received block updates. Initial scan:
15 ticks; update visibility: 2 ticks. The fixture recorded 114,727 physical reads
and 229,424 shared-cache hits by the first scan checkpoint.

Measured reports are preserved in `tests/performance/ci-mesa-initial.json`.
Provisional CI regression gates now compare p95 CPU frame time/heap with the same
run's baseline and enforce scan/update/read limits. These are software-rendered
CI fixture budgets, not desktop GPU FPS promises or full workload coverage.
The remaining UI/profile/cross-chunk work is still not complete.
