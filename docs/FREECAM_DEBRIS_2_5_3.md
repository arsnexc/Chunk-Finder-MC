# 2.5.3 — Freecam controls and server-reported debris

> Retained in 2.6.0. See [the presentation/terrain update](PRESENTATION_2_6_0.md) for current HUD, colors, panel controls and Base Finder behavior.

## Freecam A/D and perspective

The camera now obtains forward/left axes from Minecraft's native `Camera`
implementation, rather than relying only on a separate yaw formula. Right moves
opposite the native screen-left axis; Left follows it. Actual remappable movement
bindings are captured/masked/restored as a native `Input` snapshot.

A front-facing F5 perspective rotates the rendered view by 180 degrees relative
to the detached marker and can make its controls appear reversed. Freecam now
owns a first-person perspective while active, enforced before and after the
vanilla tick. Its previous perspective is restored on exit when it still owns
the view or vanilla has returned the camera to the player. Another mod's camera
ownership is not overwritten.

**K → Movement → Freecam → Swap A/D** reverses only lateral movement if your
preferred setup still needs the opposite mapping. It is saved in the existing
`chunkfinder-freecam.json` as `swapStrafe`; default is false (native directions).
The existing `range` speed setting is retained. The old generic `option: true`
field is not reinterpreted as a request to invert movement.

Input masking also handles vanilla toggle-sneak/toggle-sprint. Those mappings
ignore `setDown(false)` while toggle mode is enabled; blindly restoring `true`
can toggle them off. Mask/restore now changes a key only when needed and handles
the toggle transition explicitly. Normal speed, sprint scaling, diagonal
normalization and near-vertical cancellation safeguards are retained.

The previous first-person movement math already matched vanilla signs. This
patch addresses native basis/input integration and the perspective mismatch,
and supplies an explicit correction control; it does not claim to have
reproduced the user's exact unreported build/view combination in a live world.

## Ancient Debris spam from server obfuscation

If a server sends fake Ancient Debris block states, they can be identical to real
ones in client memory. **No client-only filter can reliably recover the hidden
real blocks from those identical states.** This update is not an anti-xray bypass
or a guarantee that a marker represents genuine ore.

Ancient Debris Finder now defaults to **Exposed only**, including older configs
without that field. It suppresses buried matches and accepts only a target with
a face next to **known, loaded air or fluid**. Unknown neighboring chunks and
positions outside build height are never treated as exposed air. Cached results
are rechecked against the same filter. The HUD labels these matches unverified.

This reduces buried replacement spam, but also intentionally hides genuine
buried debris. A server that still sends fake exposed blocks cannot be corrected
by this heuristic. Normal server block updates can reveal better data as you dig.

Controls:

- **K → World → Ancient Debris Finder → Exposed only**: on by default.
- Turn it **off** in single-player/trusted worlds if you want buried matches too.
- **Rescan chunks** requests a fresh budgeted scan; detection is not instantaneous.
- Block, Storage and Spawner ESP expose the same optional switch. Their default
  remains off, preserving existing behavior. Enable it in Block ESP too if you
  selected Ancient Debris there.

`exposedOnly` is stored in the existing block-module config files. Changing it
clears published, cached and partial scans. Existing target IDs and scalar
settings are retained.

## Bounded neighbor reads

An exposed-only test costs one target read plus at most six neighbor reads.
The shared scanner now reserves the maximum predicate cost, charges its actual
reads and yields when the remaining slice cannot safely cover another probe.
Non-target blocks still cost one read rather than seven. Section skips remain
bounded operations.

The primary scan budgets remain 4,096 reads for Ancient Debris and 8,192 for the
other block ESPs. Separate revalidation remains capped at 128 cached positions
and 48 visible candidates. With exposure enabled, their combined upper bound is
`(128 + 48) × 7 = 1,232` additional reads. Filtering dense fake data can make a
full scan take longer; no FPS or refresh-completion guarantee is made.

## OpenVapeCN reference request

The supplied repository,
[OpenVapeCN/OpenVapeCN](https://github.com/OpenVapeCN/OpenVapeCN), contained only
an organization README when reviewed (commit
`d663afd0908fda83e8591d1da819b1bc3157adda`), not module source.

Its related OpenVape and VapeV4.21 source endpoints returned GitHub **HTTP 451,
DMCA-blocked** responses. No attempt was made to retrieve blocked source via
mirrors, and no code from those projects was copied or claimed as reviewed.
These improvements are independently implemented; this repository's MIT license
and existing third-party font license are unchanged. Actual code reuse would
require accessible source with suitable rights and license terms.

## Verification and limits

Headless tests cover exposure boundaries, unknown neighbors, weighted scan
budgets, progress and config migration/persistence. Native client contract tests
compare movement with real `KeyboardInput` and camera axes at multiple yaw/pitch
angles, verify masking/restoration in hold/toggle modes, and exercise perspective
restoration. Native block-state fixtures simulate a solid chunk of fake debris,
air/fluid exposure, closed faces and unavailable neighboring chunks.

The tests use synthetic block data and no player/world/server is created. No
server EULA is accepted. They do not validate the particular server's obfuscation,
live Freecam movement, multiplayer compatibility, anti-cheat behavior or FPS.
GitHub Actions must pass the headless/native suites and JAR checks before upload.
No PR may be merged without permission.
