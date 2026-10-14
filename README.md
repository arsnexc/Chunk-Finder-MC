# Chunk Finder 2.5.0

[![Build mod](https://github.com/arsnexc/Chunk-Finder-MC/actions/workflows/build.yml/badge.svg)](https://github.com/arsnexc/Chunk-Finder-MC/actions/workflows/build.yml)

Standalone Minecraft **1.21.11 Fabric** mod project. The Chunk Finder 2.5.0 source lives at this repository's root. Requires Java 21 to run Minecraft, Fabric Loader >=0.19.5 and Fabric API. Build with **JDK 25** for the Fabric Loom toolchain.

## Download the mod — no local build needed

1. Open [GitHub Actions](https://github.com/arsnexc/Chunk-Finder-MC/actions/workflows/build.yml) and select the latest successful **Build mod** run for the branch containing this version. Until the UI changes are merged, use `arena/64b54617-chunk-finder-mc`, not the older `main` build.
2. Under **Artifacts**, download **chunk-finder-2-2.5.0.jar** (GitHub sign-in is required). This is the installable JAR, not the sources JAR; no ZIP extraction is needed.
3. Put it in your Minecraft 1.21.11 Fabric instance's `mods/` folder alongside Fabric API, and remove older Chunk Finder versions.

The workflow builds automatically on pushes and pull requests, can also be run manually from Actions, and retains the JAR artifact for 90 days. Build caches and generated JARs are not committed to the repository.

## Build from source (optional)

GitHub Actions runs the command below with **JDK 25**. To reproduce the build yourself, run it from **the repository root**:

```sh
./gradlew --no-daemon clean build
```

On Windows use `gradlew.bat --no-daemon clean build`. Install **only** `build/libs/chunk-finder-2-2.5.0.jar` in the game's `mods/` folder; remove older Chunk Finder versions. Building needs access to Gradle, Fabric, Mojang and Maven repositories.

## New in 2.5.0

- Multi-select **Targets** for Block, Storage, Spawner, Entity and Dropped Item ESP.
- Search, selected-only view, bulk selection across filtered results, and explicit **Apply / Cancel**.
- Old single-block settings migrate automatically. Empty selections match nothing; changing targets clears stale scans immediately.
- Fixed stationary chunk discovery, added budgeted background refresh/empty-section skipping, and improved nearest-target prioritization.
- Fixed trajectory counting, improved target bounds/Freecam cleanup, and stacked the block-ESP HUD cards.
- Retains the **2.4.1 font readability fix** and dark category UI.

Remove older Chunk Finder JARs before installing. Existing config filenames and scalar settings are retained. See [multi-target update notes](docs/MULTI_TARGET_UPDATE.md) for exact behavior, compatibility, limits, tests and remaining in-game checks.

## Controls and modules

**K** opens the dark category UI; **ESC** closes it. **G** toggles Freecam. Left-click a module/pill to toggle; right-click a module (or click its chevron) for inline settings. Drag sliders and scroll each panel independently. On narrow screens, use the bottom scrollbar or scroll outside panels to reach other columns. **Tab / Shift-Tab** navigates controls; **Enter / Space** activates them, and arrow keys adjust sliders or expand/collapse modules.

Each module retains its existing local settings. All modules start off on a clean install, except settings retained from earlier versions. Expand an ESP and choose **Targets**, toggle several entries, then **Apply**. **Cancel / Esc** discards the draft; **Clear all + Apply** intentionally matches nothing. The build runs both UI and module regression tests and checks the installable JAR and bundled font. A successful build is **not in-game validation**. See [multi-target update notes](docs/MULTI_TARGET_UPDATE.md) for controls and remaining checks; [UI history](docs/UI_UPDATE.md) documents the earlier visual/font update.

| Module | What it does |
| --- | --- |
| Chunk Finder | Geode and possible activity-block clues in loaded chunks; 4,096-block/tick scan budget. |
| Ancient Debris Finder | Budgeted scans across loaded Nether chunk heights; nearby debris coordinates, outlines and traces. |
| Block ESP | Choose multiple registered blocks by searchable name/ID and outline matching loaded blocks. |
| Storage ESP | Select container types: chests, barrels, shulkers, hoppers, dispensers/droppers, furnaces, brewing stands, crafters and decorated pots. |
| Spawner ESP **new** | Select normal and/or trial spawners in loaded chunks, with outlines and traces. |
| Entity ESP **new** | Select client-visible entity types (mobs/players by default, optional additional types), with color-coded outlines and a master Players switch. |
| Dropped Item ESP **new** | Filter multiple dropped item IDs; outline matches and show nearest item names and optional stack counts. |
| Trajectories | Approximate current moving projectile/dropped-item flight paths up to client-visible block collisions. |
| Target Renderer | Outline the entity or block currently selected by the vanilla crosshair. |
| Freecam **fixed** | Detached local camera. Mouse-look now turns the detached camera instead of your player; WASD moves relative to camera look, Space/Shift move vertically, Sprint boosts speed. Restores camera and key states on exit/disconnect/respawn and remembers speed across restarts. |

Scanning features use client-loaded data only; they cannot request hidden ore, storage, spawner or entity positions. Block scanners retain a maximum of 128 matches per chunk and render up to 48 nearest recorded results; entity ESP draws up to 32 nearest entities. Block ESP scanners periodically discover newly loaded chunks and refresh old results within their scan budgets; large radii can take time. **Rescan chunks** clears results and queues a fresh scan. Trajectories are approximate and do not simulate entity collisions or guarantee server outcomes. Freecam is a camera view, not teleportation or remote interaction; it does not request/load chunks. All features are visual and local; follow multiplayer server rules.

Settings live in your instance's `config/` directory. New 2.4 files are `spawner_esp.json`, `chunkfinder-entities.json`, `chunkfinder-dropped-items.json` and `chunkfinder-freecam.json`. Previous 2.3 settings files retain their names.

The common/server `/chunkfinder` commands send an advisory permission payload, but the current client does not enforce it. Installing the mod on a server does **not** guarantee server-side control over untrusted clients. The source is MIT-licensed.

## Anti-cheat and server rules

No "stealth" changes were made after version 2.4. This mod is **not claimed to be undetectable** by anti-cheat, and its use may violate a server's rules. Use it only where permitted; a successful build is not an anti-cheat or in-game compatibility guarantee.
