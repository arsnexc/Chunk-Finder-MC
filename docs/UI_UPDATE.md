# Dark category UI — UI-only update

> Historical notes for 2.4/2.4.1. The current 2.5.0 multi-target behavior and module improvements are documented in [MULTI_TARGET_UPDATE.md](MULTI_TARGET_UPDATE.md). Subsequent real-client UI validation is recorded in [CLIENT_UI_TESTS.md](CLIENT_UI_TESTS.md).

## Scope

This update was implemented against the repository's existing 2.4 source at
`c77726d`, using the supplied dark click-GUI image as a visual reference. The
previously mentioned local source ZIP was unavailable. This does **not** claim
to recover its unpublished changes.

Only presentation, screen input handling, bundled UI font resources, UI tests,
and build-artifact verification change. Scanners, module implementations,
settings files, feature permissions, mixins, key bindings, and the
Gradle wrapper remain unchanged. The font-readability hotfix is version 2.4.1. All ten existing modules and their existing
settings/actions remain available. The GitHub Actions workflow additionally
verifies that the actual installable JAR contains the new screens, UI classes,
font, and font license, and excludes headless test fixtures.

The reference's dark, rounded category headers, graphite panels, compact module
rows, white/gray pill toggles, inline settings, and thin sliders are reproduced
in the mod. Its surrounding promotional poster/device frame is not part of the
game interface. This is a close visual interpretation, **not a pixel-perfect or
in-game-verified reproduction**.

### Category contents

| Category | Existing modules |
| --- | --- |
| World | Chunk Finder, Ancient Debris Finder |
| Movement | Freecam |
| Render | Block ESP, Storage ESP, Spawner ESP, Entity ESP, Dropped Item ESP |
| Player | Target Renderer, Trajectories |

World takes the reference's Combat slot: the mod has no combat automation, and
this UI does not add nonfunctional placeholder modules to imitate that list.
These are presentation groups, not changes to the module implementations or
their existing `ModuleCategory` values.

## Controls

- **K** opens the UI; **Esc** or the small **×** closes it.
- **Left-click** a module name or its pill to toggle it. Values are read live
  from the module and saved through the same existing setters.
- **Right-click** a module, or left-click its small chevron, to expand/collapse
  its settings without toggling it. More than one module can stay expanded.
- One module per category starts expanded; this does **not** change enabled
  states or persisted settings.
- Sliders can be dragged, including outside the original panel. Clicking their
  labels does not change their value. Only changed integer values call setters.
- **Wheel** scrolls the hovered panel independently. Scrollbars can be dragged.
- Small GUI widths keep text readable rather than shrinking the entire UI.
  Drag the bottom scrollbar, scroll outside panels, or use a horizontal
  trackpad gesture to reach the other columns.
- **Tab / Shift-Tab** and **Up / Down** move keyboard focus. Focused offscreen
  controls scroll into view. **Space / Enter** toggle or activate. **Left /
  Right** collapse/expand a focused module or adjust a slider. **Home / End**
  move a focused slider to its minimum/maximum.
- Opening/returning from the block picker retains the parent panel expansion
  and scroll positions. Resizing cancels an active drag and clamps offsets.
- Freecam still uses `FreecamModule.toggle`, including its world/player check;
  failed activation displays an explanatory notice rather than a fake ON state.

### Block picker

The existing **single-target** block picker now shares the dark rounded theme
and font. Search still matches registered names and IDs, including modded blocks.
Rows are clipped to the list; names and IDs are ellipsized rather than drawn
over neighboring controls. Query/page state survives resize; an active keyboard
selection stays on its visible page. The clear button, empty-result state,
Previous/Next buttons, wheel pagination, and Up/Down + Enter navigation are
supported. Esc returns without selecting another block.

Choosing a row still immediately saves the single block target. **Multi-target
selection, selected-only filters, bulk selection, Apply/Cancel, migrations,
empty-selection rules, and scan-result invalidation changes from the missing ZIP
are not implemented by this UI-only step.** No scanning behavior was changed.

## Implementation

- `ModuleSettingsScreen`: Minecraft screen adapter and existing-module bindings.
- `BlockPickerScreen`: existing single-block picker, restyled and scale-aware.
- `MinecraftUiCanvas`: `GuiGraphics` / font / scissor adapter for Minecraft 1.21.11.
- `ui/ClickGui`: shared layout-driven rendering, focus, expansion, scrolling,
  and pointer capture. Input and rendering use the same clipped rectangles.
- `ui/ClickGuiModel`: live setting suppliers/callbacks, not duplicate config state.
- `ui/UiGeometry`, `UiTheme`, `PickerLayout`: dependency-free geometry and painting.

Vanilla `Screen.renderBackground` owns the background blur and respects the
Minecraft menu-blur setting. An additional neutral dim layer is applied; no
custom shader, mixin, duplicate blur pass, or rendering-pipeline change is added.
The screens do not pause the world.

### Font attribution

The UI bundles the unmodified Inter variable TrueType font from Google Fonts
(`ofl/inter/Inter[opsz,wght].ttf`), copyright The Inter Project Authors, under the
SIL Open Font License 1.1. Its license is included at
`assets/chunkfinder/font/ofl-inter.txt` (renamed to a resource-safe lowercase path in 2.7; license text unchanged); the mod's MIT license is unchanged.

Font SHA-256:
`29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031`

The font provider is `chunkfinder:ui`, with `minecraft:default` as a glyph
fallback. No global Minecraft font is replaced. The TTF is a required runtime
resource, not a generated mod binary or a build cache.

## 2.4.1 font-readability hotfix

The supplied in-game screenshot revealed missing-glyph boxes across the UI.
The provider declared `chunkfinder:font/inter.ttf`, but Minecraft 1.21.11's
`TrueTypeGlyphProviderDefinition.load` already adds `font/` before opening the
resource. That declaration looked for `assets/chunkfinder/font/font/inter.ttf`,
which does not exist. A failed provider causes `FontManager` to reject the font
bundle, so the `minecraft:default` reference in that same bundle did not rescue it.

The file identifier is now **`chunkfinder:inter.ttf`**, correctly resolving to
**`assets/chunkfinder/font/inter.ttf`**. The existing UI layout, font asset, and
module behavior are unchanged.

The headless fixture now reads the provider manifest and resolves the TTF using
the same prefix rule instead of loading a hard-coded font path. New assertions
cover printable ASCII and the UI's close, separator, and ellipsis characters.
The new regression check was first run against the broken manifest and failed
on `font/font/inter.ttf`, before the path was fixed. With the correction, all
**845 headless assertions pass**. CI also resolves the
manifest's file identifier inside the actual installable JAR.

These checks verify resource lookup and font glyph coverage, not an in-game
render. Confirm readability in Minecraft after replacing the old 2.4.0 JAR
with the 2.4.1 artifact and restarting the game.

## Validation

The Gradle **`uiRegressionTest`** task is wired into **`check`**, so the existing
`./gradlew --no-daemon clean build` command will run it. It compiles the pure UI
core and test fixtures in a separate source set with no Minecraft dependencies;
normal `compileClientJava` is still required to validate the actual game adapters.

For a fast dependency-free check using a local JDK 21 or later:

```sh
./scripts/test-ui.sh
./scripts/test-ui.sh --preview                # also writes build/ui-preview.png
```

An environment with only a Java runtime can alternatively set
`UI_TEST_COMPILER_JAR` to a compatible Eclipse JDT batch compiler JAR. That mode
uses Java 21 source/target rather than javac's `--release 21`.

### Completed locally on 2026-10-14

- **746 assertions passed** with Eclipse JDT 3.45.0 on Java 25.0.2, targeting
  Java 21 bytecode. No compiler warnings.
- Layout checked at 256×144, 320×240, 427×240, 640×360, 854×480, and 960×540 GUI
  dimensions; block-picker geometry checked at the supported 320×240 and larger
  sizes.
- Toggle/expansion isolation, setting callbacks, slider hit areas and pointer
  capture, release/resize cancellation, independent scrolling, scrollbar bounds,
  clipped hit tests, keyboard access, horizontal navigation, picker pagination,
  and resize-selected-page visibility covered by tests.
- The production UI painter was rendered headlessly with the bundled font at
  multiple sizes; clipping stacks balance and all UI colors are monochrome.
- `git diff --check` passes. Generated previews/classes stay in ignored `build/`.

The preview is rendered by Java2D from the same UI layout/painter and uses
illustrative fixture toggle states. It is labelled **not an in-game screenshot**;
font rasterization, blur, and game rendering can differ.

### GitHub Actions build and artifact checks

The `Build mod` workflow runs `./gradlew --no-daemon clean build` with **JDK 25**
against the real **Fabric / Minecraft 1.21.11** APIs. `check` depends on
`uiRegressionTest`; the run log reports the assertion count. Only after a
successful build does it verify the mod metadata, Java 21 bytecode, updated UI
classes, font manifest/checksum, both licenses, and absence of test fixtures.
It then uploads **`chunk-finder-2-2.4.1.jar`** as an installable artifact, not a
sources JAR, and records the SHA-256 and download link in the run summary.

Use the artifact attached to a successful run for the intended branch/commit.
A previously published `main` artifact does not contain unmerged branch changes.
CI results and downloadable JARs are recorded in [GitHub Actions](https://github.com/arsnexc/Chunk-Finder-MC/actions/workflows/build.yml).
Generated JARs, classes, and caches must not be committed to Git.

### Remaining in-game validation

1. Open with K at several GUI scales; test hover/clipping, both scroll
   directions, drag/release outside panels, keyboard navigation, blur, and font
   loading/baselines after resource reload.
2. Open/search/clear/page/select in the block picker, resize while focused, and
   return with Esc; verify that module panels retain their state.
3. Confirm every module and existing setting/action still behaves and persists
   normally, especially Freecam's world/player prerequisites and rescan actions.

No Minecraft launch or in-game test has been performed. A successful build and
headless regression run do not imply in-game or pixel-perfect verification.
**Do not merge without permission.**
