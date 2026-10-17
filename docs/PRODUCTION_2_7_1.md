# 2.7.1 — faster scans and inside-block Freecam

2.7.1 does not request chunks from a server. It changes how already-received
client data is scanned and how already-built solid terrain is shown around
Freecam.

## Inside solid blocks

Minecraft and Sodium mesh only the outside of opaque blocks. Walking the camera
into stone therefore has no inward faces to draw. Turning occlusion off does
not create those faces.

While Freecam is inside a solid block and **Inside-block view** is on, the mod
keeps a small render-only opening around the camera:

- Vanilla rebuilds use a worker-local render copy.
- Sodium 0.8.14 rebuilds use its `LevelSlice` copy, including a cloned block-light
  layer so the opening is not black. The original chunk light array is not edited.
- World storage, collisions, lighting, and ESP/base scans still read the real blocks.
- If the pinned Sodium layout changes, the feature disables itself and logs the
  reason. The rest of the mod keeps running.
- Sections are marked dirty again until a rebuild is actually observed. Sodium
  ignores a rebuild request for a section that is not built yet.

This cannot recover anti-xray substitutions or terrain the server never sent.
Shader packs are not part of the tested matrix.

## Scanning

- Section palettes that cannot contain a selected target are skipped in one
  operation, and only when all 16 layers of that section are still pending.
- Near the camera's height is scanned before deep layers.
- Block ESP can show a neighboring chunk's near-height match before the first
  chunk's deep layers finish. Base Finder still publishes only completed chunks,
  so a partial base scan cannot be mistaken for a finished one.
- Per-tick block-read and section-check limits are unchanged.

## Verification

Headless tests cover section skipping, early neighboring previews, and cutaway
volume bounds. The gameplay matrix checks that the camera block remains stone
while a render-only opening is rebuilt, and that Sodium/Iris attach the pinned
render-copy hook.

**Run [39011563911](https://github.com/arsnexc/Chunk-Finder-MC/actions/runs/39011563911),
commit `e0800d8f2a4f58a4298615f8c8caa9ef29f8b687`, passed every required gate.**

- 1,021 headless UI assertions; 1,615 module assertions.
- 92 native UI assertions; 352 native API/contract assertions.
- 36 real-world vanilla assertions, and 37 each on Sodium 0.8.14 and Iris 1.10.8
  with shaders disabled. The extra renderer check is the pinned Sodium hook.
- The controlled fixture's first block scan was 3 ticks, down from 15 in 2.7.0.
  A received block update still completed in 2 ticks. This is not a desktop or
  worst-case server guarantee.
- JAR SHA-256: `0b9346820c227e59119a85232a9e124b252f31ef02372db033696cf071e299b7`.

CI/Xvfb/Mesa timings are not desktop FPS promises. Shader packs, unrelated mod
combinations, anti-xray recovery, and external servers remain untested. No PR
was merged.
