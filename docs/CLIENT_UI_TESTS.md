# Native Minecraft UI smoke tests

The separate `src/gametest` test mod uses Fabric's client game-test API to launch
a real **Minecraft 1.21.11** client and exercise the production screens. It is
not included in the installable Chunk Finder JAR.

## Coverage

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
UI test with software rendering and a bounded timeout. Artifact verification
requires all three suites to report success and verifies that test classes and
entrypoints are absent from the installable JAR.

Native screenshots, the smoke-test report and the client log are uploaded in
**`minecraft-ui-smoke-evidence`**. Generated evidence and game assets stay out of
Git. Consult the run result for whether this test actually passed; merely adding
the test does not establish that it ran successfully.

## Limits

These are real-client **UI** checks, not a manual gameplay session. They do not
validate world scanning, live target visibility, Freecam movement/death/respawn,
network behavior, multiplayer compatibility, anti-cheat behavior or performance
on the user's machine. Screenshot capture is evidence for inspection, not a
pixel-perfect comparison with the design reference. The headless scanner tests
remain complementary to, not a substitute for, those world tests.
