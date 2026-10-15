# 2.5.1 — Scanner boundaries, candidate eviction and Freecam controls

This is a runtime bug-fix update to 2.5.0, not another visual redesign. All
multi-target pickers, config filenames, selected IDs and scalar options remain
compatible. Replace the older mod JAR; do not delete your configs.

## Fixes

### Scan the complete build height

Minecraft 1.21.11's `LevelHeightAccessor.getMaxY()` is **inclusive**: a standard
Overworld accessor has minimum Y -64, height 384 and maximum Y 319. The previous
block ESP adapter passed 319 as an exclusive cursor bound; Chunk Finder likewise
used `maxY - minY` instead of the actual height. Both omitted the top build layer.

`ScanHeight` now supplies the exclusive upper bound as `minY + height` and the
full per-chunk block count as `256 * height`. The fix applies to Block, Storage,
Spawner and Ancient Debris ESP as well as Chunk Finder. It also allows the final
complete 16-block section to use the empty-section skip instead of unnecessarily
reading its first 15 layers individually.

### Evict stale nearest candidates

The old adapter filtered mined blocks out of the current frame's nearest
candidates but left those blocks in the shared result cache. The same invalid
48 candidates could repeatedly consume every render slot until the separate
round-robin validation reached their chunk, temporarily hiding surviving targets.

Nearest-candidate validation now removes invalid positions from the cache.
An unloaded candidate invalidates its entire cached chunk and pending work.
Later bounded passes can reach surviving targets instead of selecting the same
invalid positions again. This is **not an unbounded refill**: each module still
checks at most 48 render candidates per client tick, so several ticks can be
needed after many blocks change. Empty cached chunks keep their refresh age;
mining does not trigger an immediate full rescan or postpone scheduled refresh.

### Correct Freecam directions

Right/D now moves to the camera's right, and Left/A to its left, at all yaw
angles. Previously the strafe signs were reversed. For example, facing South
(+Z), screen-right is West (-X), not East (+X).

Forward/back still use Minecraft's native look vector, including pitch. Vertical
controls, configured speed and sprint scaling are retained; diagonals remain
normalized. Almost-canceling vertical input no longer magnifies floating-point
residue into a full-speed horizontal step. Camera ownership, input masking,
network behavior and the death/respawn cleanup policy are unchanged.

### Drop out-of-radius Chunk Finder work

Chunk Finder now checks queued and active scan coordinates against the **current**
player-centered radius before reading another slice. A one-chunk move could
previously prune cached results while unfinished work continued outside the
radius, because it had not crossed the larger queue-rebuild movement threshold.
The shared `ChunkRange` predicate also avoids integer-overflow surprises.
Geode/activity scoring and the existing queue-rebuild schedule are unchanged.

### Batch scan snapshots

Shared block-scanner completions, pruning and validation publish their combined
snapshot at the end of the scan tick, rather than copying the entire result cache
after every chunk completion. Unchanged ticks reuse the existing immutable
snapshot. Explicit resets still clear it immediately. The subsequent visible
candidate pass may publish one additional snapshot if it removes stale entries.
This reduces repeated copying; no FPS improvement is claimed without measurement.

## Verification

- **Module regression tests:** 1,125 assertions, including cardinal Freecam
  directions, pitch/diagonal/cancellation cases, stale candidate eviction,
  unload/reload, strict candidate read caps, moved-radius boundaries and batched
  immutable publication. The Freecam-direction, candidate-eviction and snapshot
  regressions were each run against the old behavior and failed before the fix.
- **UI regression tests:** the existing 872-assertion suite is unchanged.
- **Native Minecraft UI tests:** the existing picker/font/input checks remain a
  required CI step.
- **Native module API contracts:** a separate client-test entrypoint uses the
  actual Minecraft `LevelHeightAccessor` with the production `ScanHeight` and
  scanner. Synthetic blocks at the bottom/top of four height ranges verify full
  inclusion and exact read counts. A full-height synthetic empty chunk checks
  all 24 section skips without individual block reads. This does not open a world.

GitHub Actions must compile the Minecraft/Fabric adapters, pass all four suites,
verify 2.5.1 metadata/font/Java-21 bytecode and exclude both native test classes
before it uploads `chunk-finder-2-2.5.1.jar`. Consult the actual Actions run for
pass/fail evidence; tests being present in source is not proof of execution.

## Still needs gameplay validation

The automated data-access fixtures are synthetic, even when using Minecraft's
real height API. No world/server was opened, and no server EULA was accepted.
In a permitted test world, verify live scans at build-height boundaries, mining
large groups of selected blocks, moving across the configured chunk radius,
Freecam controls/death/respawn, restart persistence and performance. Multiplayer
compatibility, anti-cheat behavior and pixel-perfect appearance are not verified.

No PR may be merged without permission.
