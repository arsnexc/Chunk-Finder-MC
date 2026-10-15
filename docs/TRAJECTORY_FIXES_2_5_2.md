# 2.5.2 — Bounded trajectory previews

This patch improves the existing Trajectories module. Multi-target selections,
config filenames, UI layout, Freecam controls and the 2.5.1 scanner fixes remain
unchanged. Replace the older JAR and retain your configs.

## Full-segment loaded checks

Previously, each prediction step checked only whether its endpoint's chunk was
loaded before calling Minecraft's block raycast. A long segment could cross an
unloaded chunk between two loaded endpoints. An exact chunk-edge coordinate can
also make Minecraft visit a neighboring cell because ray traversal extends the
endpoints by a small epsilon.

The adapter now preflights the **same `BlockGetter.traverseBlocks` traversal**
used by Minecraft's collision clip. This covers intermediate chunks, negative
coordinates, diagonal corner cells and endpoint epsilon behavior. The preflight
only checks chunk-availability metadata; it does not read block/fluid states.
Repeated voxel visits within one chunk reuse its availability result.

If any traversed chunk is unavailable, that prediction step is not clipped or
drawn, and prediction stops. A previously validated path prefix remains visible.
This is conservative: it can shorten a preview near a loaded-world edge, even
if a collision might have occurred before the unknown area. It never asks the
server to send hidden chunks. Arbitrary modded block-shape code is not validated
by these tests.

## Explicit work limits

Each eligible object has at most:

- **72** predicted steps (the existing setting still ranges from 12 to 72).
- **32 blocks** in one collision segment. Faster single-step motion stops the
  preview instead of clamping velocity into an invented flight path.
- **256 blocks** of total predicted path length. The last accepted segment may
  be shortened to this distance limit.

The existing **24 nearest moving objects** and **96-block starting range** are
retained. Nonfinite inputs, invalid coordinates, zero-length rays and malformed
collision endpoints cannot reach native traversal or create invalid vertices.
A future zero-speed apex can still gain downward velocity from gravity without
emitting duplicate/zero-length segments.

The previous 0.99 drag and per-entity gravity approximation is retained. These
are not exact server predictions: fluid drag, entity collisions, projectile-
specific behavior and future server changes are not fully simulated.

## Tick snapshots, not per-frame collision work

Candidate collection and collision prediction now run in `END_CLIENT_TICK`.
Rendering only draws the cached immutable paths; it does not rerun block raycasts
for every rendered frame. This removes repeated collision work when several
frames show the same client tick. No measured FPS improvement is claimed.

Disabling the module, changing its options/steps, losing the player/world or
changing world identity invalidates or replaces cached paths. Rendering rejects
old-world snapshots and removed/dead sources. The HUD counts paths actually
drawn, and its shorter status text is fitted to the existing card width.

Previews use the latest client-tick snapshot; they are not sub-tick simulations.

## Verification

The headless module suite tests the production predictor's drag/gravity order,
step/segment/total-distance limits, early collision termination, unavailable-data
handling, finite inputs, apex behavior and immutable output. Its step-cap
regression failed against the extracted loop before the predictor gained its
own defensive cap; the original settings already limited the UI to 72 steps.

The [native regression run](https://github.com/arsnexc/Chunk-Finder-MC/actions/runs/38699367819)
first failed against the extracted endpoint-only adapter on the unloaded-middle-
chunk case, before replacing it with full-segment traversal. That diagnostic run
intentionally did not publish an installable JAR.

The native client contract suite uses real Minecraft traversal and collision
APIs with synthetic `BlockGetter` data. It covers:

- Loaded endpoints separated by an unloaded middle chunk, including negative X.
- Diagonal corner traversal and both endpoint-epsilon directions.
- One availability check per crossed chunk, including vertical rays.
- Unsafe rays rejected before any native traversal.
- Actual vanilla stone-shape collision endpoints.
- No block/fluid reads when a preflight fails.
- Native raycast work bounded for a very large requested step count.

The existing UI/font smoke tests and scanner-height contracts still run. Actions
requires every suite to pass and excludes test fixtures from the installable JAR.
See the actual run for execution evidence; tests in source alone do not establish
a passing build.

## Limits and remaining checks

No real world/server is created by these automated tests, and no server EULA is
accepted. Live projectile rendering, snapshot updates during gameplay, world
transitions, modded collision shapes, multiplayer compatibility and performance
still need validation in a permitted test world. No anti-cheat or pixel-perfect
claim is made. No PR may be merged without permission.
