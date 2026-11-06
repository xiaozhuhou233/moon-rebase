# Moon Nightly native KillAura / ScaffoldWalk

The native registry now exposes `MoonKillAuraModule` as Combat / KillAura (`killaura`) and `MoonScaffoldModule` as Player / ScaffoldWalk (`scaffoldwalk`). The Myau implementations remain in `moon/port/myau`, and the OpenB4 KillAura / Lua ScaffoldWalk sources remain in their existing files, but those replacements are not registered in the GUI or configuration owner. Myau Velocity remains registered.

## Provenance and recovery

Reference: `D:/bypassmoon/Moon-Nightly-KillAura-Scaffold-HUD-Rotation-2026-10-31.zip`, Nightly 256ae01.

The archive's `killaura-scaffold/classes/com/moon/lK.class` is **not ScaffoldWalk**: its internal name is lowercase `com/moon/lk`, a packet-delay module. Windows case-insensitive extraction had collided. The real class is `D:/bypassmoon/dump-nightly/com.moon.l~k.class` (34,692 bytes), with internal name `com/moon/lK`. Never use the archive filename alone as class identity.

`moon-deobf/tools/recover_native_moon.py` and `recover_moon_helpers.py` simplify the original class data for inspection. They do not load or initialize donor classes. The guard values are the initialized normal paths; outputs are decompilation evidence, not production Java sources. Recovery output is under `moon-deobf/work/moon-original-2026-11-04/`.

| Original | Native responsibility |
| --- | --- |
| `lb`, `gd`, `gl/gm/gk` | lifecycle, target filters and Single / Switch / Dynamic selection |
| `gc` | aim point, randomization, circular offsets, back-snap and angle stepping |
| `ga`, `f0..f9/f_` | attack scheduler and block lifecycle |
| `lK` | scaffold settings, event orchestration, slot restore, movement and sneak |
| `gA`, `gD` | five rotation solvers, retained rotation and smoothing |
| `gC` | X/Z -5..5, Y 0..-2 search lattice and nearest support face |
| `gy` | ray hit vector, right-click placement and swing |
| `gE`, `gI`, `gL` | sneak timer, Hypixel sprint buffering and tower |

Offline numeric-table decoding found unique candidates in the existing seed data for `gC` (lattice -5/-3/-5), `gk` (both hurt-resistance thresholds 16), and `ga` (Hurt Time 14, velocity sync 17, delay resampling 5000..25000 ms, deviation retries 50, probability range 100, protocol swing threshold 47). Evidence is in `numeric-constants.json`. `gc`'s seed was not recovered by this candidate set; its 360-entry ring is supported by the supplied rotation map and prior recovery. This is not a universal decryption claim.

## Destination adaptation and limits

The port uses native Java 24 settings, vanilla MCP entity/ray/placement APIs and LWJGL3 input compatibility. Settings are grouped under Placement / Movement / Tower / Sneak / Rotations; no Lua runtime or Myau scaffold logic is needed. Input direction correction, visual model rotation, item display spoof, TargetHUD target lookup, TargetStrafe target supplier and NoSlow's aura guard route through the native modules.

The destination lifecycle plans before walking motion and submits placement after the matching motion packet, so the server receives the matching view before the placement. Lifecycle cleanup restores slots and physical keys, releases blocking, and drains only packets belonging to the same connection. These are adapter boundaries; this is not a byte-for-byte reproduction of the old event bus.

The original synthetic rotation switch map and `nQ` / `nT` helpers were absent from the package. Mode-to-case mapping follows the recovered solver behavior; ray tracing and mouse quantization use the destination MCP implementation. Native hotbar filtering and GUI range bounds use destination policies where the donor helpers/constants are not recovered. Existing FOV and Stop Sprint On Attack options are retained for configuration compatibility. Cross-server anti-cheat compatibility is not implied by local integrated-server tests.

**Normal Watchdog autoblock is not implemented on this 1.8.9 protocol base.** Nightly `f_` explicitly requires its 1.12.2 `hT` packet bridge for the non-aggressive path, which is not present here. The independent aggressive null-stack placement path is available. PRE, POST, FAKE, SPOOF, INVALID and the click-block paths use native packets/keys. The old bed-placement approximation is not used in the new module.

## Validation

`moon-deobf/tools/validate_moon_native.py` launches the installed production classes with a disposable survival world and configuration. `validate_noslow.py` separately checks input slowdown and packet-phase behavior. Neither runner uses the user's gameplay saves or changes the user's `run/moon` configuration.

Completed on 2026-11-04:

- Java 24 offline Maven compilation: BUILD SUCCESS, 2525 source files.
- `MoonNativeGameCheck`: 153 assertions passed (`game-p6q1h31g`). All five rotation modes placed successfully on the integrated server, with exactly one block consumed per case. GUI/native registration, unique setting keys, config round trip, silent camera, model rotation, item display/restore, eight-direction MoveFix with Velocity enabled, sprint state, real server damage, POST blocking and target publication passed.
- `NoSlowGameCheck`: 33 assertions passed (`game-tzm_bw_x`). Actual living-update input, Watchdog phase/height/Speed/aura gates, packet-only Y, NCP payload, per-use/slot/mode/world/enable resets and config round trip passed.
- An initial test fixture teleported the player before distant chunks loaded and caused a fatal fall. The acceptance harness now holds test flight until the platform is replicated, then restores survival physics before placement. This correction changes only the disposable test fixture.
- Tests cover a local integrated server; optional Hypixel packet buffering, tower timing and every external-server protocol combination are not exhaustively exercised.

## Desktop launcher linkage fix (2026-11-04)

The desktop BAT launches `D:/Moon-rebase-launcher/Launch-Moon.ps1`, whose output is separate from this repository's `target/classes`. Its previous `useIncrementalCompilation=false` compiled only changed files, leaving the old `McpVisualServices` field reference to `KillAuraModule` after `McpModuleServices.killAura` became `MoonKillAuraModule`. This caused the reported `NoSuchFieldError` during TargetHUD preparation.

TargetHUD now calls `combatTarget()` instead of referencing the concrete module field. The installed launcher fingerprints source/resource/POM content, removes only compiler output directories on a fingerprint change, enables Maven dependency-aware recompilation, and writes the successful fingerprint only after compilation. Runtime data remains outside the cleaned directories. Java 24 Maven memory is bounded to 768 MB.

Validation used the actual launcher output `D:/Moon-rebase-launcher/target/classes`: full compile passed (2525 sources), `check_module_service_linkage.py` resolved all 60 service field references, and `validate_moon_native.py` passed 155 assertions in disposable world `game-ctidu1bq`. The added checks invoke the actual HUD target accessor both with aura disabled and with a real selected target. Launcher backup and build logs are under `moon-deobf/work/launcher-linkage-fix`.
