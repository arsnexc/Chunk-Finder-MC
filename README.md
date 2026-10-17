# Chunk Finder 2.7.1

[![Build mod](https://github.com/arsnexc/Chunk-Finder-MC/actions/workflows/build.yml/badge.svg)](https://github.com/arsnexc/Chunk-Finder-MC/actions/workflows/build.yml)

Standalone Minecraft **1.21.11 Fabric** mod project. The Chunk Finder 2.7.1 source lives at this repository's root. Requires Java 21 to run Minecraft, Fabric Loader >=0.19.5 and Fabric API >=0.141.6. Build with **JDK 25** for the Fabric Loom toolchain.

## Download the mod — no local build needed

1. Open [GitHub Actions](https://github.com/arsnexc/Chunk-Finder-MC/actions/workflows/build.yml) and select the latest successful **Build mod** run for the branch containing this version. Until the UI changes are merged, use `arena/64b54617-chunk-finder-mc`, not the older `main` build.
2. Under **Artifacts**, download **chunk-finder-2-2.7.1.jar** (GitHub sign-in is required). This is the installable JAR, not the sources JAR; no ZIP extraction is needed.
3. Put it in your Minecraft 1.21.11 Fabric instance's `mods/` folder alongside Fabric API, and remove older Chunk Finder versions.

The workflow builds automatically on pushes and pull requests. The final **Publish gated installable JAR** job requires all renderer/gameplay/performance variants to pass; `candidate-not-release-*` artifacts are not release artifacts. The verified JAR is retained for 90 days. Build caches and generated JARs are not committed to the repository.

## Build from source (optional)

GitHub Actions runs the command below with **JDK 25**. To reproduce the build yourself, run it from **the repository root**:

```sh
./gradlew --no-daemon clean build
```

On Windows use `gradlew.bat --no-daemon clean build`. Install **only** `build/libs/chunk-finder-2-2.7.1.jar` in the game's `mods/` folder; remove older Chunk Finder versions. Building needs access to Gradle, Fabric, Mojang and Maven repositories.

## New in 2.7.1

Inside-block Freecam no longer depends on occlusion culling to reveal already-received solid terrain. A small render-only opening is rebuilt around the camera; world blocks, collisions and scans are not changed. Scans also inspect nearby heights before deep layers and skip sections whose palette cannot contain a selected target. See [2.7.1 scan and inside-block view](docs/PRODUCTION_2_7_1.md). This still cannot request chunks the server did not send, undo anti-xray, or support every shader pack.

## New in 2.7.0

The [16-item implementation/evidence ledger](docs/PRODUCTION_ROADMAP.md) records scope and results. See [controls, safeguards and verification](docs/PRODUCTION_2_7.md) for full details.

- Correct **resident-chunk checks**, loaded-edge warnings, received block-update probes and a shared bounded ESP read cache.
- **Target rules**: fair per-target priority, distance and display limits so common blocks do not consume every available slot.
- Optional **Diagnostics** with version/renderer, queues, filters, shared-read accounting and measured timing/Java heap.
- **Draggable snapped columns**, saved order/reset, **Find / Ctrl+F** module/setting/action search and saved favorites.
- **HUD layout editor**, normalized anchors, per-row visibility/pins, opacity and hide-zero controls.
- **Palette Tools**: colorblind-friendly/contrast presets, legend, validated local import/export and draft confirmation.
- **Profiles / backups**: local snapshots, explicit previews, backup-before-apply, rollback/default reset and safe live module reload.
- **Cross-chunk base evidence**, explanations and reversible session clue-area ignores.
- Optional **horizontal flight**, acceleration/braking and **V** speed presets; default movement remains immediate.
- Required **real single-player gameplay/performance gates** for vanilla Fabric, Sodium 0.8.14 and Iris 1.10.8 (shaders disabled), in addition to UI/API/headless checks.

This is not a guarantee of perfect server-data accuracy, universal shader/mod compatibility or desktop FPS. All gameplay test saves are disposable; no public server or user save is used.

## Features retained from 2.6.0

- **Full terrain** Freecam mode disables native section occlusion for already-loaded terrain and restores it on exit. It cannot fetch missing server chunks or undo anti-xray data.
- One **compact text HUD**, backgrounds off by default, replaces the oversized cards. Configure it through **K → UI / HUD**.
- Click category headers to **minimize panels**; state is saved. Added keyboard header controls, narration, fitted menu zoom, high contrast, reduced motion and subtle hover/toggle animation.
- **ESP colors** supports module colors and per-block/entity/item overrides, RGB/hex editing and Apply/Cancel without changing target selections.
- **Buried targets** clearly exposes the Block/Storage/Spawner ESP mode. Enable it to include non-exposed client-reported blocks.
- **Base Finder** now defaults to compact, strict evidence clusters; ordinary generated loot/work/light blocks alone no longer qualify. Natural geodes are a separate opt-in feature.

Read [2.6.0 controls, migration and verification limits](docs/PRESENTATION_2_6_0.md). Existing configs/selections are retained; new UI and color files are added.

## Fixes retained from 2.5.3

- Freecam uses Minecraft's native camera/input conventions, prevents F5 front-view mismatches, restores the previous view and handles toggle-sneak/sprint correctly.
- **K → Movement → Freecam → Swap A/D** provides a saved lateral-direction override.
- Ancient Debris Finder defaults to **Exposed only** to suppress buried server-reported replacements. Matches remain **unverified**; a client cannot recover real hidden ore from indistinguishable fake states.
- Other block ESPs offer the same optional filter; their default remains unchanged. Neighbor reads are charged to the scan budget.

See [2.5.3 controls, server-data limitations and OpenVapeCN review](docs/FREECAM_DEBRIS_2_5_3.md). Keep your configs; replace the older JAR.

## Fixes retained from 2.5.2

- Trajectories preflight every crossed chunk using Minecraft's native traversal, not just the endpoint.
- Prediction runs once per client tick; render frames draw cached paths instead of repeating collision queries.
- Previews have hard limits of 72 steps, 32 blocks per segment and 256 blocks total per object; invalid/zero-length data is rejected safely.
- The existing 24-object cap, 96-block starting range, settings and approximate physics remain.

See [2.5.2 trajectory fixes and test limits](docs/TRAJECTORY_FIXES_2_5_2.md). Existing configs and multi-target selections are retained.

## Fixes retained from 2.5.1

- Scanners now include the top build layer; Minecraft 1.21.11's `getMaxY()` is inclusive.
- Freecam A/D strafes in the correct camera-relative direction, without full-speed drift from near-canceling vertical input.
- Mined/unloaded ESP candidates leave the cache instead of repeatedly consuming the nearest-result slots; per-tick read limits remain unchanged.
- Chunk Finder drops queued/partial scans outside the current radius, even before its larger movement threshold triggers a queue rebuild.
- Shared block scan snapshots are batched at the end of each scan tick instead of copied after every completed chunk.

See [2.5.1 fixes and verification limits](docs/MODULE_FIXES_2_5_1.md). Existing configs and multi-target selections are retained.

## Multi-target features retained from 2.5.0

- Multi-select **Targets** for Block, Storage, Spawner, Entity and Dropped Item ESP.
- Search, selected-only view, bulk selection across filtered results, and explicit **Apply / Cancel**.
- Old single-block settings migrate automatically. Empty selections match nothing; changing targets clears stale scans immediately.
- Fixed stationary chunk discovery, added budgeted background refresh/empty-section skipping, and improved nearest-target prioritization.
- Fixed trajectory counting, improved target bounds/Freecam cleanup, and stacked the block-ESP HUD cards.
- Retains the **2.4.1 font readability fix** and dark category UI.

Remove older Chunk Finder JARs before installing. Existing config filenames and scalar settings are retained. See [multi-target update notes](docs/MULTI_TARGET_UPDATE.md) for exact behavior, compatibility, limits, tests and remaining in-game checks.

## Controls and modules

**K** opens the dark module UI; **Esc** closes it. **G** toggles Freecam. Freecam uses first-person while active, with **Swap A/D** and **Full terrain** controls. Left-click a module/pill to toggle; right-click a module or click its chevron for settings. Click a category header to minimize/restore it. Drag sliders and scroll panels independently. Narrow windows use the bottom horizontal scrollbar. **Tab / Shift-Tab** navigates headers and controls; **Enter / Space** activates them; arrows adjust sliders or expand/collapse. **UI / HUD** opens presentation, diagnostics, profiles and accessibility preferences. Drag headers to reorder snapped columns; **Shift+Left/Right** reorders a focused header. **Find / Ctrl+F** searches controls; **Ctrl+D** stars a result and **Ctrl+B** filters favorites.

Each module retains its existing local settings. All modules start off on a clean install, except settings retained from earlier versions. Expand an ESP and choose **Targets**, toggle several entries, then **Apply**. **Cancel / Esc** discards the draft; **Clear all + Apply** intentionally matches nothing. The normal build runs both UI and module regression tests. GitHub Actions additionally runs [native UI/API checks](docs/CLIENT_UI_TESTS.md), then required isolated gameplay and software-rendered performance checks for the pinned renderer matrix before publishing the JAR. Public servers, arbitrary shader packs and desktop GPU performance are not verified. See [multi-target update notes](docs/MULTI_TARGET_UPDATE.md) for controls and remaining checks; [UI history](docs/UI_UPDATE.md) documents the earlier visual/font update.

| Module | What it does |
| --- | --- |
| Base Finder | Strict spatial base-like clues with optional natural geodes; 4,096-block/tick budget. Heuristic, not ownership proof. |
| Ancient Debris Finder | Exposed, client-reported Nether debris by default; suppresses buried replacements but cannot verify server-hidden ore. |
| Block ESP | Choose multiple registered blocks by searchable name/ID and outline matching loaded blocks. |
| Storage ESP | Select container types: chests, barrels, shulkers, hoppers, dispensers/droppers, furnaces, brewing stands, crafters and decorated pots. |
| Spawner ESP **new** | Select normal and/or trial spawners in loaded chunks, with outlines and traces. |
| Entity ESP **new** | Select client-visible entity types (mobs/players by default, optional additional types), with color-coded outlines and a master Players switch. |
| Dropped Item ESP **new** | Filter multiple dropped item IDs; outline matches and show nearest item names and optional stack counts. |
| Trajectories | Budgeted, tick-cached projectile/dropped-item path previews using loaded-chunk collision data. |
| Target Renderer | Outline the entity or block currently selected by the vanilla crosshair. |
| Freecam **fixed** | Detached local camera. Mouse-look now turns the detached camera instead of your player; WASD moves relative to camera look, Space/Shift move vertically, Sprint boosts speed. Restores camera and key states on exit/disconnect/respawn and remembers speed across restarts. |

Scanning features use client-loaded data only; they cannot request hidden ore, storage, spawner or entity positions. Block scanners retain a maximum of 128 matches per chunk and render up to 48 nearest recorded results; entity ESP draws up to 32 nearest entities. Block ESP scanners periodically discover newly loaded chunks and refresh old results within their scan budgets; large radii can take time. **Rescan chunks** clears results and queues a fresh scan. Trajectories are approximate and do not simulate entity collisions or guarantee server outcomes. Freecam is a camera view, not teleportation or remote interaction; it does not request/load chunks. Ancient Debris Finder now defaults to an exposed-only filter, which also hides genuine buried debris; disable it only when you want unfiltered client data. All features are visual and local; follow multiplayer server rules.

Settings live in your instance's `config/` directory. New 2.4 files are `spawner_esp.json`, `chunkfinder-entities.json`, `chunkfinder-dropped-items.json` and `chunkfinder-freecam.json`. Previous 2.3 settings files retain their names.

The common/server `/chunkfinder` commands send an advisory permission payload, but the current client does not enforce it. Installing the mod on a server does **not** guarantee server-side control over untrusted clients. The source is MIT-licensed.

## Anti-cheat and server rules

No "stealth" changes were made after version 2.4. This mod is **not claimed to be undetectable** by anti-cheat, and its use may violate a server's rules. Use it only where permitted; a successful build is not an anti-cheat or in-game compatibility guarantee.
