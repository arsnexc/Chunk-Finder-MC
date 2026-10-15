# Native Minecraft UI and API-contract checks

The separate `src/gametest` test mod uses Fabric's client game-test API to launch
a real **Minecraft 1.21.11** client and exercise the production screens. A second
entrypoint checks native height API contracts with synthetic block access. Both
are excluded from the installable Chunk Finder JAR.

## Recorded result

The [first passing native run](https://github.com/arsnexc/Chunk-Finder-MC/actions/runs/38694025291)
on commit `b774d3b` completed **27 native UI assertions**, alongside **872 UI**
and **984 module** regression assertions. It captured four native screenshots.
The newly built 2.5.0 JAR was byte-for-byte identical to the prior 2.5.0 artifact:
that 2.5.0 follow-up added tests and CI support, not gameplay changes. The later
[2.5.1 patch](MODULE_FIXES_2_5_1.md) contains runtime fixes and the additional
height API-contract entrypoint; consult its build for current results.

## UI coverage

- Actual custom-font glyph metrics, including the missing-glyph-box regression.
- Native rendering of the module screen and multi-target picker, with screenshots.
- Legacy single-block config loaded by the real module adapter.
- Native search typing, mouse caret placement and Ctrl+A replacement.
- Selecting both diamond ore variants without immediately changing the module.
- Apply and disk persistence; Cancel/Esc and unchanged live state.
- Empty selection persistence and selected-only/bulk-clear behavior.
- Keyboard row selection, Ctrl+Enter, and query/draft preservation after resize.
- Real registry choices and Apply callbacks for Storage, Spawner, Entity and
  Dropped Item ESP, not just a fake picker catalog.
- Font availability after an actual Minecraft resource reload.

## Module API contracts

`ClientModuleContractTest` checks Minecraft's actual inclusive `getMaxY()` API
against the production scanner adapters. Bottom/top synthetic targets cover
Overworld, Nether-style, negative and single-layer height ranges. It also checks
that all 24 full-height empty sections can be skipped without individual block
reads. These are API/algorithm checks, **not live world scanning**. Their separate
success marker is required alongside the UI marker before JAR upload.

The test run directory is **`build/run/clientGameTest`**. Only that isolated
run's configs are changed. The client tests do not open/create a world or run a
server; the build does **not** auto-accept a server EULA.

## Running

With JDK 25 and a graphical desktop:

```sh
./gradlew --no-daemon --no-configuration-cache runClientGameTest
```

On a Linux CI runner with Xvfb and Mesa installed:

```sh
LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null \
  xvfb-run -a -s '-screen 0 1280x720x24' \
  ./gradlew --no-daemon --no-configuration-cache runClientGameTest
```

GitHub Actions runs the normal `./gradlew --no-daemon clean build` first,
including the existing UI and module regression tasks. It then runs the native
UI and API-contract tests with software rendering and a bounded timeout. Artifact
verification requires all four suites to report success and verifies that test
classes and entrypoints are absent from the installable JAR.

Native screenshots, the smoke-test report and the client log are uploaded in
**`minecraft-ui-smoke-evidence`**. Generated evidence and game assets stay out of
Git. Consult the run result for whether this test actually passed; merely adding
the test does not establish that it ran successfully.

## Input-driver note

Fabric API 0.141.6's `TestInput` sends `KeyEvent` with zero modifier flags, even
when its held-key state includes Ctrl. The test therefore sends Ctrl+A (Cmd+A on
macOS) and Ctrl+Enter as explicit native `KeyEvent`s to the actual screen handlers.
Ordinary typing, caret clicks, row keys, mouse buttons and resizing use Fabric's
input driver. This tests native UI behavior, not operating-system keyboard wiring.

## Limits

These are real-client **UI** checks, not a manual gameplay session. They do not
validate world scanning, live target visibility, Freecam movement/death/respawn,
network behavior, multiplayer compatibility, anti-cheat behavior or performance
on the user's machine. Screenshot capture is evidence for inspection, not a
pixel-perfect comparison with the design reference. The headless scanner tests
remain complementary to, not a substitute for, those world tests.
