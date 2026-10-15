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
| 3 | Received block updates invalidate stale results and prioritize bounded rescans | Implemented bounded update inbox + immediate-priority probes; headless tests pass, native hook/gameplay pending |
| 4 | Shared bounded block-read cache with hit/miss/read accounting and fair scheduling | Implemented per-tick cache and rotating module order; headless tests pass, real measurements pending |
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
