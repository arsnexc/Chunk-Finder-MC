# 2.7.0 — controls, safeguards and verification

This document describes the implementation. **Execution status belongs to
[PRODUCTION_ROADMAP.md](PRODUCTION_ROADMAP.md) and the matching Actions run.**
A release candidate is not a claim of perfect accuracy or universal compatibility.

## Reliability and measurements

- Freecam warns **EDGE** while its current chunk is resident but neighboring data
  is missing, and **UNLOADED** after leaving resident data. These warnings have
  priority in the HUD while the HUD is enabled. No chunks are requested.
- Minecraft 1.21.11's deprecated `ClientLevel.hasChunk/hasChunkAt` returns true
  unconditionally. All availability checks now inspect `ClientChunkCache` with
  `ChunkStatus.FULL, false` instead. This also fixes falsely treating missing
  neighboring chunks as exposed air.
- Applied block updates enter a deduplicated 4,096-position inbox, drained in
  bounded batches. Each enabled block ESP probes changed positions and their six
  exposure neighbors ahead of distant scans, within its existing read allowance.
  Overload resets fail closed rather than retaining untracked stale results.
- The four block ESP modules share a per-tick state cache: at most **32,768 cached
  states** and **40,960 physical reads**. Module order rotates. The cache never
  survives a tick/world change; received updates invalidate affected states.
  Base Finder's separate bounded scan and trajectory collision work are not
  represented as shared-cache reads.
- **Target rules** provides module defaults and per-block/entity/item priority,
  range and display caps. Range 0 inherits the module, display 0 hides that type.
  Drafts require Apply. A fair bounded sampler protects distinct target types
  from common-block flooding while capacity permits; priority weights spare
  slots. Hard module limits remain 48 block candidates and 32 entity outlines.
  Cache identity metadata also avoids extra block reads just to color a target.
- **UI / HUD → Diagnostics** shows installed version, actual renderer/shader
  state, frame timing samples, Java heap, loaded-in-radius counts, queues,
  incoming updates, shared read reuse and active filter/policy information.

Freecam keeps immediate pitch-following motion by default. Optional **Horizontal
flight**, **Acceleration** and **Braking** preserve predictable zero/instant
settings. **V** cycles 2x/5x/10x while Freecam is active; the binding is remappable.
Opening a screen or ending the camera session discards momentum.

## Workspace and accessibility

- **Drag category headers** to reorder snapped columns. Their order is saved;
  this is responsive column placement, not arbitrary overlapping floating panels.
- Click a header to minimize it; Shift+Left/Right reorders a focused header.
  Escape cancels a drag. **Reset panel layout** restores default order and opens
  categories without enabling modules.
- **Find** or **Ctrl/Cmd+F** searches module names, settings, actions, categories
  and help text. Selecting a result **reveals/focuses**, never activates it.
- Star/right-click a search row or use **Ctrl/Cmd+D** to favorite it. **Ctrl/Cmd+B**
  filters favorites. Favorites persist independently of module enable states.
- Menu zoom, high contrast, narration and reduced motion remain supported.
  Headless and native tests cover supported zoom/window combinations and resize
  while editing; animations never move hitboxes.

## HUD controls

**UI / HUD** offers visibility, scale, backgrounds, opacity, details, row cap,
hide-zero and per-module row visibility/pinning. Hiding a row does not disable its
module. Pinned active rows precede ordinary rows and survive hide-zero filtering;
explicit row hiding still wins. Pins do not bypass the total row cap.

**Drag HUD / anchors** opens a placement draft. Drag, use arrows (Shift = 8 pixels),
press **1–4** for corners, **S** for snapping, or **Home** to reset. Save/Cancel are
explicit. Normalized coordinates keep the layout inside resized/scaled windows.
The editor freezes its preview rows for stable dragging; when no modules are
active it labels synthetic preview rows rather than inventing detections.

## Palettes

From **ESP colors → Tools**:

- Okabe-Ito/colorblind-friendly, bright high-contrast and soft presets assign
  colors to selected target IDs and the module fallback, retaining unrelated
  overrides. Suitability for every individual's color vision is not guaranteed.
- The textual legend explains effective IDs/colors and inherited overrides.
- Export writes the current draft to `config/chunkfinder-palettes/<name>.json`.
  Import validates format, RGB values, resource IDs, nesting and size, then shows
  a preview. Confirming import replaces **only the draft**; normal Apply/Cancel
  still determines whether live colors change. No targets are enabled/selected.

## Profiles and backups

**UI / HUD → Profiles / backups** saves named local snapshots for servers or play
styles. Profiles are manually selected; no server address or credentials are
stored or auto-applied on connection.

- Managed paths: `config/chunkfinder-profiles` and `config/chunkfinder-backups`.
- Preview lists file replacements, creations and resets before explicit Apply.
- Only an allowlist of this mod's **client** settings is included. Server
  authorization, Minecraft options, unrelated mods, credentials and world saves
  are excluded.
- File names cannot be paths. Symlink destinations, malformed/oversized/deep JSON,
  invalid scalar/target/color/rule values and unowned entries are rejected.
- A backup must succeed **before** any changes. Files are atomically replaced
  where supported; partial failures attempt rollback and retain the backup.
- Confirmed Apply safely stops Freecam and rebuilds live module bindings. Freecam
  never auto-starts from a profile. Restore defaults previews removal of owned
  settings only. Backups are retained; users may archive old managed backups.

A disk failure can also prevent rollback; the UI reports failure rather than
claiming success. No software can guarantee writes on a failing filesystem.

## Base evidence

Completed chunks contribute to a shared world-aligned evidence index. Compact
windows combine evidence across neighboring chunk boundaries without counting a
block twice. Overlapping detections are deduplicated; chunk unload/range/world
changes invalidate contributions. Received edits prioritize refreshed evidence
and prevent stale partial scans from publishing.

**Base Finder → Review base clues** explains the score and signals, such as
`beacon x1 + storage x1 + work x1`. Click a clue to ignore/restore its area; use
review tools to clear ignores. Ignores are bounded and session/world scoped, not
permanent server-proven ownership labels.

These remain heuristics. Custom/server-built structures can resemble bases;
simple player builds may lack sufficient evidence. Identical server-obfuscated
states cannot be authenticated or decoded by a client-only detector.

## Gameplay and performance gates

The reusable matrix creates disposable integrated single-player worlds beneath
`build/run/clientGameTest`. It tests real underground movement, loaded edges,
solid-terrain camera movement and compiled/visible cave sections, stationary player body, dimension transfer,
death/respawn, speed/horizontal/braking controls, real ESP updates/fairness,
cross-chunk clues, ignores and live profile reloads. User saves and public
servers are never used, dedicated tests remain disabled and `eula=false` remains.

Pinned test renderers are in `tests/renderer-lock.json`: Vanilla Fabric, Sodium
0.8.14 and Iris 1.10.8 + Sodium, Minecraft 1.21.11. Iris is tested **with shaders
disabled**, not with every shader pack or renderer combination. Third-party
renderer binaries are test-only and excluded from the installable JAR.

The initial measured reference is `tests/performance/ci-mesa-initial.json`:
15-tick first scan and 2-tick received-update latency in the controlled fixture.
`scripts/check-performance.py` applies provisional CI budgets derived from those
measurements: 45/10-tick scan/update bounds, p95 CPU frame time relative to the
same run's baseline, extra Java heap allowance and hard shared-cache bounds.
These are **Xvfb/Mesa CI regression budgets**, not desktop GPU FPS targets, VRAM
measurements, worst-case server guarantees or shader-pack performance claims.

The final installable artifact is published only after the same commit passes
build/UI/API checks and all three gameplay/performance variants. Candidate
artifacts are explicitly not release artifacts. No PR is merged without approval.
