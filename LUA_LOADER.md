# Lua loader (first pass)

This is a self-contained LuaJ 3.0.1 loader for the 1.8.9/LWJGL3 base. It does
not depend on the native Moon client, its HWID/security layer, or its module
framework. Scripts live in `run/scripts` (or `.minecraft/scripts` in a normal
run) and are loaded on the first client tick.

## Build and run

```
mvn -q -DskipTests compile
mvn -q exec:exec
```

LuaJ is declared as `org.luaj:luaj-jse:3.0.1`. It is fetched from Maven
Central, or from the local Maven repository. `libs/luaj-jse-3.0.1.jar` is a
vendored copy for offline setup; the pom uses the normal dependency.

## Commands

Type these in chat:

```
.lua list
.lua on <name>
.lua off <name>
.lua toggle <name>
.lua settings <name>
.lua reload
```

`run/scripts/lua.properties` controls `auto_enable` (default `true`).
`run/scripts/lua-config.properties` is created when a script setting changes.

## Implemented API

- `modules.register(name, display, table)` and the 4-argument form with a
  description. Both dot and colon calls are accepted.
- `modules.is_enabled`, `set_state`, `toggle`.
- `modules.create_boolean_setting`, `create_integer_setting`,
  `create_float_setting`, `create_color_setting`, `create_text_setting`.
- `modules.get_setting`, `set_setting`, with persistence.
- Script callbacks: `on_enable`, `on_disable`, `on_tick(pre)`,
  `on_update(event)`, `on_packet_receive(event)`, `on_packet_send(event)`.
  `on_overlay_render` and `on_effect_render` are wired into
  `LuaManager.onRender`/`onEffectRender` but are not called until the renderer
  exists.
- `on_update`: `event.pre` is a boolean; `x/y/z/yaw/pitch/ground` are passed
  as fields. On pre, changed values are written back to the player.
- `on_packet_receive`: called from `NetworkManager.channelRead0`; `cancel =
  true` skips `processPacket`. Inbound edits are applied by reflection for
  S00/S08/S12/S14/S27/S32/S48 before processing.
- `on_packet_send`: called before `NetworkManager.dispatchPacket`; `cancel =
  true` drops the packet. Changed fields rebuild the packet when it is one of
  the outbound types below. Lua callbacks are serialized per script.
- `player.send_packet` fires `on_packet_send`; `player.send_packet_no_event`
  suppresses it. Supported outbound IDs:
  `0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B,
  0x0C, 0x0E, 0x0F, 0x11, 0x13, 0x14, 0x18, 0x19`.
  `0x07`/`0x08` accept either a `vec3i` at argument 2 or flat x/y/z args.

### Packet event fields

Every packet event has `packet_id` and `cancel`. Additional fields:

- `0x03/0x04/0x05/0x06` player: `x`, `y`, `z`, `yaw`, `pitch`, `ground`
  (alias `on_ground`).
- `0x07` digging: `action`, `x`, `y`, `z`, `facing`.
- `0x08` block placement: `x`, `y`, `z`, `facing`, `offset_x/y/z`.
- `0x00` keepalive: `id`.
- `0x01` chat: `message`.
- `0x02` use entity: `target`, `action`, `hit_x/y/z`.
- `0x09` held item: `slot`.
- `0x0B` entity action: `action`, `aux_data`.
- `0x0C` input: `strafe`, `forward`, `jumping`, `sneaking`.
- `0x0E` click window: `window_id`, `slot_id`, `mouse_button`, `mode`,
  `action_number`.
- `0x0F` confirm transaction: `window_id`, `action_number`, `accepted`.
- `0x11` enchant: `window_id`, `enchantment`.
- `0x13` abilities: `invulnerable`, `flying`, `allow_flying`, `creative_mode`,
  `flying_speed`, `walking_speed`.
- `0x14` tab complete: `text`, optional `x`, `y`, `z`.
- `0x18` spectate: `target` (UUID string).
- `0x19` resource pack status: `hash`, `status`.
- `0x08` receive pos-look: `x`, `y`, `z`, `yaw`, `pitch`, `flags`.
- `0x12` entity velocity: `entity_id`, `motion_x/y/z` (blocks per tick).
- `0x14` entity: `entity_id`, `x`, `y`, `z` (delta bytes), `yaw`, `pitch`,
  `ground`, `rotating`.
- `0x27` explosion: `x`, `y`, `z`, `strength`, `affected_blocks`,
  `motion_x/y/z`.
- `0x32` confirm transaction: `window_id`, `action_number`, `accepted`.
- `0x48` resource pack send: `url`, `hash`.
- `0x02` receive chat: `message`, `type` (read-only).

Moon does not publish per-packet field names in its docs, so the names above
are the 1.8.9 protocol names plus aliases; if a migrated script uses a
different Moon name, add an alias in `LuaPackets.read`.
- `player`: movement, ground, jump, motion, position, rotation, health, food,
  sprinting/sneaking, damage time, held slot, input, distance.
- `client`: chat print/message, fps, time, scaled/raw width/height, key/mouse
  state, timer speed.
- `inventory`: hotbar/inventory item lookup with `name`, `display_name`,
  `stack_size`, `max_stack_size`, `damage`, `max_damage`; the mutation methods
  are currently no-ops.
- `world`: entity/player lists, names, positions, health, flags, block name,
  remove, biome.
- `enums`: `template_type`, `custom_font_type`, `facing`, `entity_action`,
  `digging_action`, `hittype`, `client_status_actions`.
- `types`: `template.*` constructors (rect, rounded_rect,
  outlined_rounded_rect, gradient, triangle, text), `vec2f`, `vec3d`, `vec3i`.
- `font`: `width`, `height`, `minecraft_width`, `minecraft_height`. Widths use
  the vanilla font renderer scaled by `size / 9.0` until the TTF backend is
  added.
- `render`: `batched.render_batch`, `batched.prepare_batch`,
  `immediate.*`, `state.*` exist and are no-ops. `render.implemented` is
  `false`; the loader logs one notice the first time a batch is submitted.

## Deliberate behavior

- Each script gets its own `Globals`. `io`, `os`, `package`, `debug`,
  `luajava`, `require`, `dofile`, `loadfile` and bytecode `load` are removed.
- Lua numbers used as colors are converted with
  `(int) ((long) value.todouble())`. Do not use `LuaValue.toint()` for colors:
  values such as `0xFFFFFFFF` exceed `Integer.MAX_VALUE` and would saturate.
- Unknown native modules are a no-op for `set_state`/`is_enabled`. This is
  what lets `telly.lua` run with no ScaffoldWalk implementation.
- A Lua error disables the module and prints a chat message instead of
  escaping into the game loop.
- `on_tick` is called once per client tick (20 TPS) with `pre = true`.

`run/scripts/examples/GrimVelo.lua` is the packet/update compatibility sample.
It is outside the scan directory on purpose: copy it up one level only when
you want it active, because it cancels S12 and sends C03/C06/C07 in a live
world.

## Verified in a live integrated world

A temporary probe script (removed afterwards) confirmed:

- `on_enable` runs on script load.
- `on_update` fires with `pre=true` before movement and `pre=false` after;
  `x/y/z/ground` values are the live player values.
- `on_packet_receive` runs on the Netty thread; observed real packet IDs
  including `0x12` with per-entity IDs and `motion_x` values.
- `on_packet_send` observed real outbound IDs (`0x04`, `0x06`, `0x07`, `0x09`)
  with `cancel=false`.
- `player.send_packet_no_event(0x03, {ground})` did not fire
  `on_packet_send`; `player.send_packet(0x06, ...)` did.
- `player.send_packet(0x07, {action, vec3i, facing})` constructed C07 without
  errors.
- `modules.set_state`/`get_setting` and `client.set_timer_speed` work in the
  same path.

The Moon example `GrimVelo.lua` loads and registers; its in-world behavior
uses exactly the callbacks above.

## Not implemented / limits

- The 2D renderer. `watermark.lua` loads, creates its settings and ticks, but
  draws nothing until `render.batched` is backed by real rounded-rect/text
  rendering.
- `on_world_render`; only `on_overlay_render`/`on_effect_render` are reserved.
- A click GUI for script modules; use the `.lua` commands.
- Packet fields cover the IDs listed above. Other play packets expose only
  `packet_id`/`cancel`.
- Inbound edits are applied by reflection for S00/S08/S12/S14/S27/S32/S48.
  Complex fields such as S27's affected block list and S14's relative flags
  are read-only.
- `on_packet_send` rebuilds the packet only for types with a known constructor;
  unsupported outbound edits fall back to the original packet.
- Packet events fire for play-state packets only; handshake/login are outside
  the Lua event stream.

## Files

```
src/dev/pier/lua/LuaManager.java   script dir, modules, tick, commands, config
src/dev/pier/lua/LuaModule.java    callbacks, settings, error isolation
src/dev/pier/lua/LuaApi.java       sandbox and all Lua globals
src/dev/pier/lua/LuaPackets.java   packet fields and outbound construction
src/dev/pier/lua/LuaSetting.java   typed setting values
src/dev/pier/lua/LuaConfig.java    settings persistence
```

Integration points:

```
Minecraft.runTick()                 LuaManager.get().onTick()
EntityPlayerSP.sendChatMessage()    LuaManager.get().handleCommand(message)
EntityPlayerSP.onUpdate()           LuaManager.get().onUpdate(this, pre)
NetworkManager.channelRead0()       LuaManager.get().onPacketReceive(packet)
NetworkManager.sendPacket()         LuaManager.get().onPacketSend(packet)
```
