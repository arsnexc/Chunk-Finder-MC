# 2.6.0 — Freecam terrain, compact HUD, accessible panels and ESP palettes

## Freecam terrain visibility

**Movement → Freecam → Full terrain** is on by default. While Freecam owns the
camera, this disables Minecraft's section-occlusion (`smartCull`) optimization
and invalidates its visibility graph. Closed/underground sections already sent
to the client can be considered for rendering from the detached camera, rather
than remaining hidden by the usual terrain-occlusion traversal. The prior flag
is restored on exit; meshes are not fully discarded every tick.

This does **not** ask the server to load/send chunks around the detached camera,
change the player's position, decode anti-xray replacements, or manufacture
missing terrain. Render-distance/frustum limits and normal mesh faces still
apply. Completely solid blocks have no interior visible faces in vanilla meshes.
Disabling occlusion can cost FPS; turn Full terrain off if needed. Alternate
renderers such as Sodium may have their own culling behavior and are not verified.

## Buried Block ESP targets

**Render → Block ESP → Buried targets** makes the existing raw/filtered behavior
explicit. On includes non-exposed client-reported blocks; off requires known air
or fluid exposure. New Block/Storage/Spawner configs default to including buried
targets. Existing explicit exposure choices are retained, so turn Buried targets
on if you previously enabled Exposed only.

Ancient Debris Finder keeps its separately labelled **Exposed only** safeguard
from 2.5.3. Raw data may be fake on an obfuscating server. Neither mode can verify
which hidden blocks are real.

## Compact, background-free HUD

All Chunk Finder HUD drawing now uses one bounded text column at the top-left.
The old large cards and duplicate overlay registrations are removed. Default:
backgrounds off, extra details off, six visible rows, 90% size. Text shadows help
readability. An overflow row summarizes additional active modules instead of
extending across the screen.

Open **K → UI / HUD** to change visibility, backgrounds, details, row count and
HUD size. Optional details show short scan status/coordinates. Other mods' HUDs,
server scoreboards and vanilla chat are not removed or recolored.

## Minimized panels and accessibility

- Click a category header to minimize/restore its body. Saved per category;
  collapsing a panel never disables its modules. Scroll offsets are retained.
- Headers are keyboard reachable. **Enter/Space** toggles a panel; **Left/Right**
  collapse/restore it. Tab/Shift-Tab traverses headers, modules and settings.
- **UI / HUD** opens the shared dark settings menu. Restore all panels is available
  there, without enabling modules or changing targets.
- Menu zoom cycles through 75/100/125/150%, fitted to supported small windows.
  Input and clipping use the same scaled coordinates. Pickers retain native text
  metrics instead of mixing scaled custom glyphs with vanilla caret widths.
- High contrast brightens text and panel boundaries. Contextual tooltips can be
  disabled, while keyboard focus remains labelled.
- Native screen narration includes the focused control, its value and panel state.
- Hover/toggle animations are short and do not move hitboxes. **Reduced motion**
  disables them immediately. Panel/row geometry changes are deliberately instant
  to keep keyboard focus and pointer capture predictable.

Presentation preferences are stored in `chunkfinder-ui.json`.

## Module and per-target ESP colors

Expand a block/entity/item ESP and select **ESP colors**. The editor supports RGB
sliders, hexadecimal `#RRGGBB`, a live swatch, keyboard adjustment and explicit
**Apply / Cancel**. Module default changes the fallback color. Click the scope
button to search for a particular block, entity type or item and override just
that ID. This is color selection, not a change to the ESP's target selection.

Per-target overrides take precedence over the module color. Reset target uses
the module/automatic color; Reset all remains a draft until Apply. Invalid hex
cannot be saved. Missing-mod identifiers are retained. Entity automatic colors
use the registered mob category and keep a separate player color.

Base Finder also offers separate outline colors for base clues and geodes.
Palettes are stored in dedicated `chunkfinder-colors-*.json` files so saving colors
cannot overwrite another settings object's target/scalar changes. Old config
filenames and selections remain supported. Block identities for coloring are
cached once per tick (at most 48 additional state reads per block ESP); rendering
does not query blocks just to look up colors.

## More conservative Base Finder

The old Chunk Finder activity score could identify generated loot/work/light
blocks as activity, and combine evidence over an entire chunk's vertical extent.
The module is now labelled **Base Finder**, using the same `chunkfinder.json`.

**Strict base clues** is on by default. A candidate needs a compact mix of at
least three blocks and two functional families, plus a stronger player-like
signal such as a beacon, respawn anchor, conduit, enchanting table or shulker
box. Chests, furnaces, torches, rails and ender chests alone are not sufficient.
Evidence is collected in overlapping 16-block spatial windows; overlapping hits
are deduplicated and at most four clusters are retained per scanned chunk.
Outlines bound the supporting evidence, not a whole vertical chunk. The nearest
64 detections are chosen once per client tick rather than every rendered frame.

**Amethyst geodes** is a separate opt-in setting, off by default. A natural geode
is never classified as a base. Loose activity hints remain available by disabling
Strict base clues.

This is a **heuristic, not ownership detection**. Simple bases without strong
signals can be missed. Server-built/custom structures can contain player-like
blocks and still trigger it. Evidence split across chunk boundaries may be
incomplete. Hidden/obfuscated server states cannot be authenticated by this mod.

## Verification scope

Headless tests cover panel collapse/focus/scroll, fitted zoom, animation/reduced
motion, color-layout bounds, HUD size/no-background behavior, palette drafts and
persistence, config migration, spatial clustering and generated-structure-like
negative fixtures. Existing input, scanner, budget and font regressions remain.

Native client tests exercise scaled header clicks, saved minimization, narration,
UI/HUD navigation, RGB typing/validation/Apply/Cancel, per-block scope selection,
palette reload and unchanged target sets. Native API checks verify culling flag
ownership/restoration and real vanilla block classification. New screenshots
include accessibility settings, the color editor, and a **clearly labelled
synthetic HUD preview**, not gameplay evidence.

No world/server is started, no server EULA is accepted, and no live-server render,
FPS, multiplayer, anti-cheat or pixel-perfect claim is made. The installable JAR
is uploaded only after all suites and metadata/font/Java-21 checks pass.
No PR may be merged without permission.
