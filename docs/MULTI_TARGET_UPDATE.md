# 2.5.0 — Multi-target ESP and module improvements

## Using multi-target selection

Open **K**, expand an ESP module, then click **Targets**. Click any number of
rows to toggle their checkboxes/pills. Nothing changes in the running module
until **Apply**. **Cancel**, the close button, or **Esc** discard the draft.

- **Block ESP:** any registered non-air blocks, including modded blocks.
- **Storage ESP:** chests, ender/trapped chests, barrels, shulkers, hoppers,
  dispensers/droppers, furnaces/blast furnaces/smokers, brewing stands, crafters,
  and decorated pots. Modded blocks extending those container classes are
  included. Defaults retain the previously supported storage types.
- **Spawner ESP:** normal and trial spawners, including registered subclasses.
  The two vanilla spawner types remain the default selection.
- **Entity ESP:** registered entity types. Defaults select mob categories,
  players and armor stands; additional types, such as vehicles or modded
  miscellaneous entities, can be chosen explicitly. The **Players** switch is
  still a master filter: players must also be selected in Targets. Your own
  player is excluded.
- **Dropped Item ESP:** item IDs, not the generic `minecraft:item` entity type.
  Defaults select all currently registered non-air items. This filters item
  types, not stack names, enchantments or other component data.

**Search** matches names and registry IDs, case-insensitively, using all entered
words. **Selected** shows selected targets only. **All results / Clear results**
affect every filtered result across all pages, while preserving selections
hidden by the current filter. **Clear all** clears the entire selection.
Selecting no targets is valid and **matches nothing**, never everything.

Use the wheel or Previous/Next to page through results. Tab/Shift-Tab reaches
all buttons and the list; Up/Down navigates rows, Space/Enter toggles a row,
and Ctrl+Enter applies. Normal text-entry shortcuts remain available in Search.
Search uses Minecraft's native text metrics so clicking, caret placement,
selection and horizontal text scrolling agree. The rest of the UI retains the
fixed Inter font resource from 2.4.1 and the existing dark panel styling.

## Settings compatibility

Config filenames are unchanged:

| File | New selection field |
| --- | --- |
| `item_esp.json` | `blockIds` |
| `storage_esp.json` | `blockIds` |
| `spawner_esp.json` | `blockIds` |
| `chunkfinder-entities.json` | `entityIds` |
| `chunkfinder-dropped-items.json` | `itemIds` |

The old Block ESP `blockId` is read as a one-element selection when `blockIds`
is absent. A subsequent settings change writes the array and removes the old
key. An explicit new array, **including an empty array**, takes precedence over
the legacy field. Legacy configs without a selection field receive the defaults
above without losing enabled states, ranges, trace/outline options, the players
switch, or stack-count settings.

IDs are trimmed, namespaced and deduplicated. Invalid array entries are skipped;
an explicitly malformed/null selection fails closed to no targets. Valid IDs
from a currently missing mod remain saved and appear as **Unavailable target**
in the picker. They match nothing until that registry entry becomes available.
Apply does not silently discard those IDs. Bulk selection does not add
unavailable entries. Configs retain unrelated keys and use atomic file
replacement where the filesystem supports it.

## Scanner improvements

Block, Storage, Spawner and Ancient Debris ESP now share a tested, budgeted
loaded-block scanner rather than four slightly different copies.

- Target changes, radius changes, disabling, manual rescans, disconnects and
  dimension changes clear both published results **and partially completed work**.
- Fixed the idle discovery deadline being pushed forward on every idle tick.
  Discovery is checked every 40 client ticks even when the player stands still.
- Added background refresh for already-scanned chunks. Cached chunks become
  eligible after 200 ticks; unseen chunks and older results are prioritized.
  This is a **minimum refresh age, not a guarantee that a large radius is fully
  refreshed every ten seconds**. Work remains budgeted.
- Chunks are checked for availability before each scan slice. Unloaded and
  out-of-radius entries are pruned. No requests for hidden/unloaded data are made.
- Empty 16-block-high sections are skipped. Scan operations remain capped at
  8,192 per tick per Block/Storage/Spawner module and 4,096 for Ancient Debris.
- At most 128 cached positions in one chunk are revalidated per tick; up to 48
  render candidates are also revalidated. Validation no longer rereads every
  retained match in every chunk on every tick.
- A chunk keeps the **nearest 128 matches to the scan's starting player
  position**, rather than the first 128 found at the bottom of the world.
  This is a shared cap across selected block types, not 128 per type.
- The nearest 48 render candidates are selected once per client tick with a
  bounded heap, instead of sorting the entire retained result set every frame.
- Ancient Debris Finder remains Nether-only, now checks the full dimension
  height, and benefits from empty-section skipping and background refresh.

The four block scanner HUD cards now share a size-aware right-side stack.
Their headings no longer have hard-coded overlapping secondary labels. HUD
rows shorten at smaller GUI sizes, and custom HUDs are hidden while a screen
is open to keep menu text clear. Other existing HUD positions remain unchanged.

## Other module fixes

- **Entity / Dropped Item ESP:** resolve selected IDs without registry-default
  fallbacks; collect the nearest 32 matches once per tick; show the actual
  nearby match count instead of deriving a misleading count from the cap;
  discard old-world snapshots and dead/removed entities.
- **Chunk Finder:** adds a Rescan action, prunes unloaded/out-of-radius cached
  chunks, aborts a scan whose chunk unloads, fixes sentinel-coordinate overflow
  in movement detection, resets scheduling on dimension changes, and caps
  world rendering to the nearest 64 detections. Geode/activity scoring is unchanged.
- **Trajectories:** prioritizes the nearest 24 eligible moving objects rather
  than registry iteration order, fixes the 25-object off-by-one HUD count, and
  counts paths actually drawn. Prediction is still approximate and uses the
  original gravity/drag approximation; no entity-collision guarantee is added.
- **Target Renderer:** checks range from the camera hit location (including
  Freecam), avoids unloaded/empty block targets, and uses the selected block
  shape's bounding box instead of always outlining an entire cube.
- **Freecam:** checks the owning player identity and life state so death/respawn
  ends the detached camera session; generic disabling restores camera/input
  state; input cleanup is null-safe. Movement behavior and server interactions
  are otherwise unchanged.

No server protocol, anti-cheat bypass, hidden-chunk request, or permission policy
was added. Follow server rules. Existing advisory server permissions remain as
documented in the README.

## Verification

`./gradlew --no-daemon clean build` on **JDK 25** runs both tasks through `check`:

- **`uiRegressionTest`**: UI layout/input/painting, picker toolbar/footer bounds,
  and the Minecraft 1.21.11 font lookup/glyph regression.
- **`moduleRegressionTest`**: the production selection, config and scanner code
  against temporary config files and a fake loaded world. Covers multi-select
  union, Apply/Cancel, selected-only/bulk semantics, old single-block migration,
  empty/malformed/unknown IDs, persistence, scalar option preservation, scan
  budgets, stationary discovery, newly placed/mined blocks, partial-scan
  invalidation, dimension/unload/radius changes, empty-section skipping and
  nearest-match caps.

Local runs passed **872 UI assertions** and **984 module assertions** using Java
25.0.2 and Eclipse JDT targeting Java 21. The real Minecraft/Fabric adapter
compilation and both regression tasks must also succeed in Actions before the
installable artifact is uploaded. CI checks required classes/resources, font
lookup/checksum, version metadata, Java 21 bytecode, and exclusion of test fixtures.

Optional local commands:

```sh
./scripts/test-ui.sh
# Gson is already supplied by Minecraft during a Gradle build; the standalone script needs its JAR:
MODULE_TEST_GSON_JAR=/path/to/gson-2.13.2.jar ./scripts/test-modules.sh
```

The [native Minecraft UI smoke tests](CLIENT_UI_TESTS.md) now also pass in
Actions: **27 assertions** exercising the actual client font, picker input,
Apply/Cancel, empty selections, module reload persistence, resize and resource
reload, with four native screenshots. These tests are separate from `check` and
must pass before Actions uploads the installable JAR.

**World/gameplay behavior remains unverified in a running world.** After
installing 2.5.0, check live ore/container/spawner results, item/entity visibility,
persistence across a full game restart, target changes during an active scan,
stationary chunk loading/block changes, Freecam death/respawn, and performance at
the actual render distance/GUI scale. The native UI smoke test does not establish
pixel-perfect appearance, multiplayer compatibility or anti-cheat safety.

No PR may be merged without permission.
