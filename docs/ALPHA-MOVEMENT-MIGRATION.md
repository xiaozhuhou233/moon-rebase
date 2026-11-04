# Alpha Speed and TargetStrafe migration

The requested `Moon-Alpha-TargetStrafe-Speed-2026-11-04.zip` contains the original
Alpha `jr` and `ju` class files and renamed decompiler output. Its README does not
contain all dependencies, and some event names in the renamed source are wrong.
The port uses the class-file descriptors and the matching Alpha dependency dump
in `D:/bypassmoon/dump-case-client-v3` as evidence. No donor class is loaded into
the destination client. Both `jr.class` and `ju.class` are SHA-256 identical
to the dependency dump copies, confirming that this is the matching Alpha build.

## Installed modules

Both modules appear in the destination's existing **Player** category. Their
readable implementations live in `src/moon/module/movement`, one file per module.
They use the normal configuration, binding, command completion and HUD suffix
paths. New installations leave both modules disabled.

| Destination | Original implementation |
| --- | --- |
| Speed settings and mode order | `jr`, `aN`, `aO` |
| Vanilla | `ew` |
| NCP | `eu` |
| Watchdog | `eA` |
| Legit | `et` |
| Vanilla Bhop | `ex` |
| Vulcan | `ez` |
| Verus Ground / Hop | `ey`, `fb` |
| Strafe, including Fast | `ev` |
| TargetStrafe / interpolated ring points | `ju`, `fc` |
| Movement vector calculation | `dz`, `kV` |

Speed retains the original mode order and setting IDs: `mode`, `wd-damage-boost`,
`wd-damage-boost-amount`, `verus-mode`, `strafe-fast`. The original default mode is
Vanilla, damage boost defaults to false, Verus defaults to Hop, and Fast defaults
to true. The original potion formulas use the amplifier directly in the speed
multiplier; they were not changed to amplifier plus one.

TargetStrafe keeps `radius` (1.5, range 0.5–4), `points` (15, range 5–45),
`only-space` (true), and `draw-points` (true). Its current target comes from the
registered KillAura implementation. It retains line-of-sight checks, feet/head
clearance, the three-block support limit, closest valid point selection, and
direction reversal when the next point is blocked. A/D consume press edges.
The movement aim uses the original 135-degree offset; it does not simply aim at
the selected display point.

## Event boundaries

- `dH PRE` is client tick PRE. Legit uses this hook; it is not a render event.
- `c0 PRE` is walking motion PRE, after the current tick's collision movement.
- `dz` is the displacement event. TargetStrafe edits its movement direction
  first; Speed consumes that direction when a mode sets horizontal speed.
  TargetStrafe alone does not manufacture horizontal acceleration. Modes that
  do not set event speed retain that limitation from the original.
- `cW` cancels the vanilla ground-jump branch for Legit and Watchdog, preventing
  duplicate jumps while leaving water/lava behavior intact.
- `c1.b()` distinguishes explosions. Watchdog observes ordinary incoming entity
  velocity before the destination's knockback transform and ignores explosions.
- `dQ` is a render-view-position event in `kU`, not packet position. Verus Ground
  temporarily locks the render entity's current Y, restoring it with a scope even
  if rendering fails. Physical coordinates and packet Y are not locked by it.
- `c9 POST` draws the ring points. The destination captures the world matrices,
  draws half-alpha red/green/white 0.04-block cubes, and restores GL state.

The destination remembers the already-corrected physics input for the current
player/tick. This lets Speed cooperate with existing MoveFix without dispatching
the same strafe event twice. Mode/world changes reset cached movement state;
disabling Vulcan also retains its original horizontal-stop behavior. The existing
Velocity module now sees the installed Speed's enabled state.

## Constant evidence and remaining fidelity limit

The supplied runtime seed `ju.B = 83784099047754` decrypts all three numeric sites:
point count **15**, maximum **45**, and orbit offset **135**.

Watchdog's boost-window field is used as **`(int)eA.m`**. The offline search over
3,073,621 combinations of the original data values and permutation families found
one candidate whose low signed int is in 1–999:

```
seed candidate: 9333439061242
decrypted long: -2946589431844306934
effective low int: 10 ticks
```

The port currently uses 10 ticks. This is a data-derived candidate, **not a
confirmed runtime capture**. A runtime capture of Alpha `com.moon.eA.l` and `m`
was requested to settle that one remaining constant. The original default keeps
Damage Boost off. No anticheat compatibility claim is made by the migration.

## Validation

Java 24 full Maven compilation passed (2,522 source files). The final disposable
integrated-world run passed **76 assertions**, with no GL errors and zero owned
visual resources remaining on shutdown. Checks cover all eight modes, potion math, actual
`EntityPlayerSP.moveEntity` dispatch, ordinary velocity versus explosion,
configuration round trips, the current KillAura target, Space gating, blocked
points, direction reversal, void support, GL state restoration, and world cleanup.
The validation tools and evidence remain in the analysis workspace under
`work/alpha-movement-2026-11-04`; user `run` files are not part of the change.

Final evidence: `game-sbpsmhra/proof/passed.txt` and `build-final.txt` in the
analysis workspace. These checks verify local behavior and integration, not
multiplayer server acceptance or the unconfirmed runtime seed.
