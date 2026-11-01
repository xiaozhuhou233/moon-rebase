# HUD / Modern recovery — 2026-11-01

Status: production migration with explicitly unresolved fidelity details.
The restored sources run on this client's Java 24 / LWJGL3 renderer. Compilation
and game acceptance do not establish pixel identity with the original Moon runtime.

## Corrected source identities

Input: `moon-deobf/deliverables/hud-unresolved-2026-11-01`.

The package mixes two Moon builds and contains case-collision errors:

* `original/hud/com.moon.lY.java` actually declares `ly`, a protobuf envelope class.
* Its `com.moon.hj.java` extends `g5`; it is not Nightly's session-information `hj`.
* `f8` / `f9` are the earlier watermark, not the latest Nightly watermark.
* Actual Nightly `hu implements hn` is the watermark. `hj extends hi` is a separate
  session-information component. The supplied README's identification of INFORMATIVE
  as the SessionInfo card is contradicted by `hu` bytecode and is not used.
* Actual Nightly `mZ` is the DEFAULT category panel. `mz` is a button. Archive entries
  were extracted to separate case-specific directories to avoid overwriting them.
* The package reverses DEFAULT and MODERN. Raw `gS` maps MODERN to switch arm 1,
  DEFAULT to arm 2. Raw `lS.c()` constructs `pk` in arm 1 and `pj` in arm 2.
  Therefore **MODERN = pk**, **DEFAULT = pj**. The supplied rewritten `lS.java`
  method and README are contradicted by classfile evidence.

Additional evidence was read from `D:/bypassmoon/nightly-dump.jar`, with CFR and
`javap` used only to read classfiles. No original Moon class was loaded or executed.
Recovered material and the failed constant-decoding attempts are preserved under
`moon-deobf/work/hud-modern-recovery`.

## Migrated watermark

`moon.visual.hud.WatermarkHudRenderer` contains the complete installed rendering
path for TEXT / LOGO / INFORMATIVE. The live data boundary is in `McpHudServices`.

* TEXT preserves custom MSDF / Minecraft font paths, `&` formatting, size 9 and
  position (2,2), and existing static / gradient / pulsing / synchronized colors.
* LOGO preserves the (4,4)-(68,68) bounds and gradient command ordering. Its host
  now uses `UiDispatchers.Texture`, which reads the PNG alpha, instead of incorrectly
  multiplying by PNG RGB through `ColoredTexture`. Transform and texture cleanup
  are protected by `finally`.
* INFORMATIVE is a compact horizontal row: moon, build, identity, server, FPS.
  The SessionInfo-card replacement has been removed. The recovered Nightly literals
  are size 5.85; background (7.25,8.25), radius 6.5; text start x12.75; background
  height `font.height(5.85)+11`; mask (7,8), with +0.5 extents. Black background
  opacity is 0.275 with blur and 0.4 otherwise.
* Icons are the literal Material glyphs U+E86F, U+E7FD, U+E157, U+E8E5, using a
  separate MaterialIcons font binding. They are drawn after Effects and text;
  per-frame batches are transient. Layout is shared across the cached text/mask
  passes so live FPS changes do not move icons away from their text.
* The continuation recovered hu's seed **7931392571438** and all ten string-table
  entries. Draw separators are `  §7§l·§r  `, icon-to-text spacing is two spaces,
  and background measurement uses the distinct original `    §7·  §r` separator.
  Icon color is exactly `0xffcccccc`. The build comes from decrypted SHA
  `256ae016d1ab5f21124f38e269e06bb26cfdc63d`, truncated to seven characters.
* LOGO's decoded sampler values are GL_TEXTURE_2D / MIN_FILTER / MAG_FILTER /
  LINEAR (3553 / 10241 / 10240 / 9729), now applied to the bound logo texture.
* `jn` bytecode proves the final -0.02 argument adds to its first font parameter
  (smoothing), while the preceding zero is the glyph weight offset. The previous
  migration incorrectly sent -0.02 as weight. A separate smoothing overload now
  preserves the existing weight and applies the original smoothing adjustment.
* Existing `watermarkShadow` owns static/flowing-gradient shadow color. Diffusion
  remains controlled by Effects. No SessionInfo placeholders such as AC/TB remain
  in the watermark.

## Migrated Modern integration

`ClickGuiModule.Mode.MODERN` has an independent cached screen with pk's screen
policy. DEFAULT opens the previous material controller; IM_GUI remains separate.
New settings default to MODERN / STACKED; existing saved modes still load.

The pk policy uses 140-wide categories at (5,5), horizontal step 145, Center,
Post Processing, and Scale. The latter defaults/bounds are literal lS bytecode:
Center=false, Post Processing=true, Scale=1 in [0.5,2], step 0.1. Input is mapped
back through GUI scale and opening scale/translation. Center uses the logical
viewport width after GUI scale. Animation progresses during visible drawing,
including when Effects is disabled. The host user's VSync preference is preserved
instead of pk's temporary VSync override.

Modern owns seven FBOs (mask, output and five pyramid levels). Its Post Processing
switch uses Effects' Blur enable, iterations and offset, gated by Effects itself.
It never emits shadow
geometry. FBOs resize in place and close with the visual service; draw allocations
use the existing transient frame scope. Settings expand downward within the
category. Left-aligned names, category icons, disclosure arrows, compact key
labels and flat accent rows follow the captured Nightly Modern screenshot.

The later `Moon-Nightly-Modern-ns-nr-nk-nw-2026-11-01.zip` supplies JVMTI method
and constant-pool captures for those four classes. Category and modal geometry
now follow this evidence. Nested module/setting editors still use destination
widgets: the package does not include `m4`, `nt`, `nl`, `nn`, or `np`. The target's
five registered categories are used; no empty categories or duplicate modules
were fabricated. Category glyph identities retain the prior adapter mapping.

The DEFAULT `pj -> mZ -> mB` layout matches the already restored native widget tree:
panels start at (10,10), width 153, construction height 350, horizontal step 157;
headers use 25 units and module rows use the original rounded/stacked heights.
Panel height is content-driven and screen-clamped. Settings expand within each
module. The bundle's simplified 220-wide detached settings column and 22-high
category header are not source-accurate and were not copied.

Reinitialization clears the category tree to prevent duplicate rows. Animation
updates now belong to the visible frame, independent of the Effects shadow event.
ClickGUI emits no shadow geometry, per the user's prior instruction. Existing
downward setting/curve expansion, live Lua category refresh, module bindings,
scrolling, dragging, color controls and modal controls remain in the shared tree.

## Constant recovery correction

The previous offline seed model ignored `e = (int[])e.clone()` in `_c`'s initial
data and merged arrays that later receive independent permutation writes. It
also hardcoded d0..d10 instead of reading all d-method calls (Nightly has d11/d12).
Both are corrected. This is a data-model fix; no original class is executed.

The corrected model's fresh hu prediction equals the validated seed. Every
padded string chunk and all parsed integer/long callsites are decoded, including
the separately measured background delimiters. Evidence and reproducible tools
are in the updated deliverable. Nightly `hn`'s six color constants also decode
to the existing seven-entry palettes, final index 6 and phase -100; no behavior
change to the shared color helper was necessary.

`pk` has a unique finite data-node candidate **95673785565422**, validated against
all four numeric callsites: six pyramid slots, **150 ms** linear animation,
constructor height margin 100, and a 500 ms Effects invalidation window. The
adapter uses 150 ms and the original initial height. The user's later fix request
replaces the fixed two-level blur with the live Effects blur settings.
The shadow-related invalidation remains suppressed per the user's instruction.

## Explicit fidelity limits

* The identity field uses the local session username; original `cp` account
  metadata is unavailable. The existing destination font atlas/profile and logo
  asset remain in use; complete pixel equivalence across builds is not asserted.
* Modern module/setting internals and modal buttons/content remain destination
  adapters pending `m4`, `nt`, `nl`, `nn`, and `np`. Category colors, outline,
  clipping, header metrics, scroll step/limits and modal wrapper are source-backed.
  Center / Post Processing / Scale remain functional.
* `pj.F -> pl` is the DEFAULT footer/browser, outside the requested Modern port.
* `nw` is a captured-coordinate draw callback. Its screen queue is restored, but
  the donor `nt` renderer is absent; existing destination controls do not claim
  original `nt` rendering fidelity.

## November 1 dynamic Modern capture

The input README is evidence, not an implementation specification. Two claims
were corrected from bytecode: `nr.a(IIFFFF)Z` is inclusive rectangle hit testing,
not interpolation; `nk` is the modal wrapper, not just a string decryptor.

The rebuilt classes omit fields and BootstrapMethods. Both ns and nk also end
their pool with a long without including its reserved slot in constant_pool_count.
The analysis repair corrects that count, restores ns's proven nr superclass, and
adds only bootstraps supported by pool entries. In nk the supposedly unavailable
lambda types and concat recipe are present: `(Object)V`, `(np)V`, `np.a()V` and
`\\u0001\\u0001`. Method bytecode remains unchanged. No donor class or DLL is run.

Recovered mappings and destination code:

* `nr`: geometry/config/lifecycle and inclusive hits map to `ClickGuiWidget`.
  Its f/g/h/i fields are cached geometry for Effects invalidation, not tween
  targets. Shadow invalidation remains suppressed under the user's policy.
* `ns`: `CategoryPanel` Modern path uses header 28, row initial height 23,
  radius 13, background `0xcc1a1a1a`, title/icons `0xbbffffff`, outline
  `0xff5b5b5b` with thickness .25. Body clipping is x+.5, y+28,
  width-1, height-28.5 with top corners disabled. Title x includes actual
  icon width. Original constructor cap is min(viewport-125,550,content+28);
  ongoing layout additionally floors available viewport at 200. The adapter
  uses ongoing layout at render preparation to size the blur mask consistently.
  Content includes a one-unit bottom pad. Scroll offsets are positive, with
  +/-22 per wheel sign, .4 FPS-adjusted interpolation, and original bounds.
  A modal consumes/discards hovered panel wheel input without moving the panel.
* `nk`: `ModalOverlay` Modern path uses content padding20, minimum width200,
  height content+56+font heights, panel radius16, backdrop `0x80000000`, panel
  `0xff111111`, outline `0xff5b5b5b`, description `0xffaaaaaa`, bold white
  title and inset10. Original fixed button/content anchors (x+200 / x+100)
  remain explicit. Animation duration is100ms. The user's requested downward
  reveal replaces the donor's scale/translation, and safe viewport placement
  and transform cleanup remain destination adaptations.
* `nw`: `NativeClickGuiScreen.DeferredOverlay` forwards two stored coordinates
  to its renderer once, after panel drawing and before transform reset/modal.

`ns` seed46618925851859 decodes all11 callsites /10 table entries;
`nk` seed90708872692326 decodes all4 numeric callsites, the padded `§l` string
and100ms duration. Scripts and decoded tables are in the v3 deliverable.

## Verification

Latest installed revision: Java 24 full compile passed; real-game run
`work/hud-modern-recovery/game-4_8_ppf5` passed **125 assertions**. It checks
Effects-driven blur by framebuffer pixel comparison, independent ESP Glow
settings, depth state after FBO resize, SessionInfo texture preservation through
the actual GuiScreen entry, seamless adjacent enabled rows, and both bottom
corners rejecting the enabled-row fill outside the rounded stencil mask.
Visual resources returned to zero on shutdown. The earlier v3 ZIP predates
these fixes; repository source is authoritative for the current revision.

The later Effects/texture fix adds these destination corrections:

* FBO creation/resize enables depth in the shared framebuffer implementation.
  Modern disables depth testing/writes throughout post-processing and restores
  them afterward. The caller saves/restores MCP's cached blend/alpha/texture state;
  the real GuiScreen entry uses the same HUD drawing scope as other visual modules.
* Modern rows calculate animated heights before the category places the next row.
  Flat fills disable per-row edge smoothing so neighboring enabled rows have no
  dark seam. Closed animated rows cover their complete allocated height.
  The Modern stencil-mask draw temporarily enables alpha testing: HUD drawing
  scopes disable it, which otherwise lets transparent corner fragments write
  stencil and leak the bottom enabled row outside the panel's rounded bounds.
  Alpha-test enable/function/reference are restored before drawing module rows.
* ESP Glow uses its own enabled/iterations/offset settings. Its Intensity Settings
  exposes iterations and offset again. It has no Effects suppliers; HUD shadow
  synchronization is unaffected.

Validation for these later corrections is recorded separately in
`moon-deobf/work/modern-dynamic-2026-11-01/EFFECTS-FIX-RESULT.md`.

`mvn -DskipTests compile` uses JDK 24 (2520 Java sources).
Final build succeeded. The dynamic Modern actual-game run passed **88 assertions**:
`work/hud-modern-recovery/game-nuq34qsh`. It adds category viewport/scroll checks,
one-shot deferred coordinates, modal dimensions, downward reveal, button routing,
background scroll suppression and no wheel replay after modal close.
It also includes decoded brand/build text,
formatted delimiters, two-space icon gaps, original separate content/separator
advancement, icon color, actual logo texture filters, independent font smoothing
and weight parameters, the decoded 150 ms animation, scrolled setting clicks,
linear sampling after FBO allocation and a full output pixel scan: zero opaque
pixels outside the current panels after animation/centering changes. The blur
output is cleared every frame so alpha rejection cannot retain old footprints.
Final screenshots were inspected; the earlier blur trails are gone. Frame-rate
observations in the isolated flat-world fixture are not a general performance
guarantee. Visual resource count reached zero at exit.
`moon-deobf/tools/validate_hud_modern.py` starts the installed target classes in a
disposable real Minecraft world. It checks independent Modern/Default routing, one-press
RShift binding dispatch, configuration round trip, category geometry, repeated
initialization, centered/scaled input, settings expansion, DEFAULT row styles, independent Effects/open/ESC
lifecycle, no ClickGUI shadow output, all watermark modes, live server width,
Material icon resources, stable Modern blur resources, and GL error state. Screenshots include normal game
frames and HUD scale 1.5. Inspect the deliverable manifest for the final run path.

This is a functional migration and source-backed geometry restoration; the limits
above remain before a claim of complete Nightly fidelity is justified.
