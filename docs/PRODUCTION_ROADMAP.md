# Production roadmap — implementation and evidence ledger

Requested scope: all 16 reliability/customization/detection items. This ledger distinguishes a source implementation, synthetic test, renderer
launch, and real-world validation. Passing the listed gates is not a promise of
perfect accuracy or universal compatibility.

No promise of perfect accuracy is possible for server-obfuscated block data or
heuristic player-ownership detection. No PR may be merged without permission.

| # | Acceptance gate | Status |
|---|---|---|
| 1 | Isolated real single-player scenarios: underground/boundary Freecam, dimension transfer, death/respawn; separate vanilla/Sodium/Iris runs | Final 2.7.0 passed 34 real-world assertions per renderer, including compiled/visible cave sections (38723920129) |
| 2 | Loaded-terrain edge warning, without sending chunk requests | Passed actual resident-edge and missing-chunk checks on all three renderers |
| 3 | Received block updates invalidate stale results and prioritize bounded rescans | Passed real mining/placement update checks; controlled-fixture latency 2 ticks |
| 4 | Shared bounded block-read cache with hit/miss/read accounting and fair scheduling | Passed shared-state reuse, bounded read/cache checks and rotating scheduling; measured counters recorded |
| 5 | Per-target priority, distance and display caps; common blocks cannot evict every rare target | Passed headless, native policy UI/API and real dense-stone/rare-ore display-cap scenarios |
| 6 | Optional version/renderer/chunk/queue/filter diagnostics page | Passed native diagnostics navigation/version-data checks; live renderer, queue/filter and measured telemetry are available |
| 7 | Header drag reorder, saved snapped positions and reset; keyboard equivalent | Passed saved snapped-column drag/reorder/reset tests and native pointer interaction; Shift+arrows equivalent |
| 8 | Module/setting/action search and saved favorites | Passed reveal-only search and saved favorites tests, including native Ctrl+F/Ctrl+D paths |
| 9 | HUD drag anchors, row visibility/pins, opacity and hide-zero controls | Passed normalized placement bounds, per-row visibility/pins/zero filtering, opacity and native placement save |
| 10 | Colorblind-friendly presets, validated local import/export and legend | Passed preset, legend data, local palette validation/roundtrip and native export/import-preview/cancel paths |
| 11 | Local profiles, explicit preview/apply, backups and rollback/default restoration | Passed allowlist/path/symlink/schema/rollback tests, native preview/cancel/apply, and real-world profile reload with active Freecam |
| 12 | Zoom/resize/focus/narration and optional-animation regression matrix | Passed 1,021 headless UI and 92 native UI assertions including zoom/window matrix, resize, focus and narration |
| 13 | Cross-chunk base evidence, deduplication and unload/dimension invalidation | Passed cross-chunk merge/dedup/invalidation tests and real boundary-spanning clue scenario |
| 14 | Human-readable evidence and reversible ignored clues | Passed real evidence explanation and ignore/restore scenario; session/world-scoped ignores are explicit |
| 15 | Optional horizontal flight, acceleration/deceleration and speed presets | Passed native camera/input checks, real horizontal/braking/preset movement and lifecycle reset scenarios |
| 16 | Measured baseline/on frame-time, heap and first-scan latency; environment-labelled budgets | Measured baseline/on timing, Java heap and latency on three software-rendered variants; provisional derived CI budgets pass |

## Test isolation

New gameplay tests must use disposable saves beneath `build/run/clientGameTest`,
not user saves or public servers. Dedicated server tests and automatic server
EULA acceptance remain disabled. Test worlds and binaries must not be committed.

Renderer compatibility must identify exact versions and whether a shader pack
was active. Mesa/Xvfb CI frame times are not desktop GPU performance claims.
All incomplete/blocked gates must remain explicit in the final delivery.

## Startup investigation history

- Build/UI/API suites pass at `34d723a` and `7a4995a`; these are not gameplay passes.
- Initial renderer matrix run `38713175808` loaded the intended mods but all three
  variants timed out during integrated-world loading before scenario assertions.
- Focused run `38714284627` confirmed the integrated server starts; the client
  remains on LevelLoadingScreen with no ClientLevel. Filtered logs/thread capture
  are available. Normal packet scheduling was then evaluated with Fabric's
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
At that earlier checkpoint, UI/profile/cross-chunk work remained incomplete; RC2 below includes it.

## RC2 integration gate

The remaining workspace/profile/HUD/cross-chunk implementations are now present.
RC2 subsequently passed the expanded native/world checks in run `38722682104`. Final JAR publication
now depends on the same-commit reusable three-renderer gameplay matrix; the
pre-gate artifact is explicitly named `candidate-not-release-*`.

The Minecraft dependency is restricted to the exact tested 1.21.11 version.
The Inter license asset was renamed to a lowercase resource-safe path, with its
license text and the font binary unchanged, eliminating its resource-path error.

## RC2 complete integration evidence

Run **38722682104**, commit `47ad3328916b6afeab0a57dcbdb8ddfbf66563da`, passed:
- 1,021 headless UI assertions and 1,594 module assertions.
- 89 native UI assertions and 352 native module/API assertions.
- 31 real-world assertions **per renderer** on vanilla, Sodium 0.8.14 and Iris
  1.10.8 + Sodium (shader packs disabled), plus measured performance gates.
- Final candidate hash re-verification and gated publication.

The final 2.7.0 version build also requires a compiled-and-visible loaded cave
section check (including Sodium's actual visibility API, not merely compilation).
Its final result is the matching same-commit Actions run, not an assumed pass.
No external/public-server, arbitrary shader-pack, all-hardware or anti-cheat
compatibility claim is made. No PR has been merged.

## Final 2.7.0 acceptance

**Run [38723920129](https://github.com/arsnexc/Chunk-Finder-MC/actions/runs/38723920129),
commit `292813a85d73a5f8b3e83c1979b54e9184abd9eb`, passed every required gate.**

- 1,021 headless UI assertions; 1,594 module assertions.
- 92 native UI assertions; 352 native API/contract assertions.
- **34 real-world assertions per renderer**, including compiled-and-visible
  underground cave sections, actual movement/boundary/lifecycle behavior,
  received updates, dense/common-vs-rare target policies, cross-chunk evidence,
  UI ignore/restore and live profile reloads.
- Vanilla Fabric, Sodium 0.8.14 and Iris 1.10.8 + Sodium, with shaders disabled.
- All measured CI performance gates and final JAR identity verification passed.
- JAR SHA-256: `cf24fea7a20fc22d981362043a46df004b7f0333f3f41e78b2edbd838f3b41de`.

Exact measurements are committed in `tests/performance/ci-mesa-2.7.0.json`.
The controlled fixture measured 15 ticks for first scan and 2 ticks for an
incoming block update on every renderer. This is not a worst-case server or
desktop-GPU guarantee. Shader packs, unrelated mod combinations and external
servers remain outside the tested matrix. No PR was merged.
