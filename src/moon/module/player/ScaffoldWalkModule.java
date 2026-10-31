package moon.module.player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import moon.module.NativeModule;
import moon.module.combat.killaura.KillAuraRotations.Angles;
import moon.module.player.scaffoldwalk.ScaffoldGeometry;
import moon.module.player.scaffoldwalk.ScaffoldGeometry.Face;
import moon.module.player.scaffoldwalk.ScaffoldSettings;
import moon.module.player.sprint.SprintController;
import moon.visual.gui.SettingValue;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/**
 * Native ScaffoldWalk, rebuilt from the Moon {@code Scaffold.lua} controller.
 *
 * <p>The Lua module combined three jobs that the destination client splits across
 * ticks: pick a support face under the route, publish a verified look in the
 * movement packet, then place on the preceding pose. This port keeps that
 * contract:</p>
 *
 * <ul>
 *   <li>{@link #tick()} plans a face, rotation and telly state before movement;</li>
 *   <li>{@link #motionPlace()} places from the pre-move update, using the pose the
 *       server already has;</li>
 *   <li>{@link #motionPre()} publishes the placement's look in the following C03;</li>
 *   <li>{@link #outgoing(ScaffoldPacket)} aligns and validates the C08; and</li>
 *   <li>{@link #packetReceive(Object)} rebuilds state after corrections.</li>
 * </ul>
 *
 * <p>While enabled the scaffold owns sprint: the vanilla sprint-key block, the
 * native Sprint module and KillAura's sprint correction are muted, and only the
 * applied forward-facing telly look may sprint. KillAura's two attack paths
 * (packet C02 and real mouse click) stay available; both are observed so sprint
 * stays off for the tick after a sprint attack.</p>
 */
public final class ScaffoldWalkModule extends NativeModule {
    public record Position(int x, int y, int z) { }
    public record Placement(Position support, int face) { }
    public record BlockInfo(String name, boolean fullCube, boolean glass, boolean replaceable,
                            boolean blocksMovement) { }
    public record ItemInfo(String name, int count, boolean block) { }
    public record RayHit(int x, int y, int z, int face, double hx, double hy, double hz) { }

    private record Cell(int x, int y, int z) { }
    private record Context(int tick, double x, double y, double z,
                           double eyeX, double eyeY, double eyeZ, float yaw, float pitch) { }
    private record RotationHit(float yaw, float pitch, RayHit hit, double margin) { }
    private record FaceKey(Cell support, int face, float lastYaw, float lastPitch,
                           float forward, float strafe, boolean preferSprint,
                           double eyeX, double eyeY, double eyeZ) { }

    /** Outbound/inbound packet view supplied by the MCP boundary. */
    public interface ScaffoldPacket {
        int id();
        default boolean cancelled() { return false; }
        default boolean excluded() { return false; }
        default boolean hasPosition() { return false; }
        default boolean hasLook() { return false; }
        default double x() { return 0; }
        default double y() { return 0; }
        default double z() { return 0; }
        default float yaw() { return 0; }
        default float pitch() { return 0; }
        default boolean onGround() { return false; }
        default int slot() { return -1; }
        default int placeX() { return 0; }
        default int placeY() { return 0; }
        default int placeZ() { return 0; }
        default int placeFace() { return -1; }
        default float offsetX() { return 0; }
        default float offsetY() { return 0; }
        default float offsetZ() { return 0; }
        default boolean alignPlacement(Placement placement, double hitX, double hitY, double hitZ) {
            return false;
        }
    }

    /** Game boundary. Rays, blocks and placement stay in the MCP adapter. */
    public interface Host {
        boolean available();
        boolean inLiquid(); boolean onLadder(); boolean riding(); boolean inWeb();
        double feetY(); double positionX(); double positionZ();
        double motionX(); double motionY(); double motionZ();
        boolean grounded();
        boolean sneaking();
        void setSprinting(boolean value);
        boolean jump(); boolean jumpHeld(); boolean shiftHeld(); void suppressSneak();
        int playerId(); int ticksExisted(); int foodLevel();
        Angles camera();
        float forward(); float strafe();
        boolean keyDown(int keyCode);
        int selectedSlot(); void selectSlot(int slot);
        ItemInfo hotbar(int slot);
        BlockInfo block(int x, int y, int z);
        RayHit ray(double eyeX, double eyeY, double eyeZ, float yaw, float pitch, double reach);
        boolean supportRay(double eyeX, double eyeY, double eyeZ, float yaw, float pitch,
                           int cellX, int cellY, int cellZ, double reach);
        boolean place(Position support, int face, double hitX, double hitY, double hitZ,
                      int slot, boolean clientSwing);
        double eyeHeight(boolean sneaking);
    }

    private static final int RAY_LIMIT = 2048;
    private static final double REACH = ScaffoldGeometry.REACH;

    private static final List<String> EXCLUDED_EXACT = List.of(
            "tile.air", "tile.water", "tile.lava", "tile.fire", "tile.sand", "tile.gravel",
            "tile.snow", "tile.vine", "tile.cake", "tile.pistonbase", "tile.pistonstickybase",
            "tile.soil", "tile.daylightdetector", "tile.cocoa", "tile.dragonegg", "tile.waterlily",
            "tile.reeds", "tile.soulsand", "tile.cauldron", "tile.portal", "tile.endportalframe",
            "tile.bed", "tile.redstonedust", "tile.redstonewire");
    private static final List<String> EXCLUDED_WORDS = List.of(
            "slime", "web", "cactus", "workbench", "noteblock", "jukebox", "leaves", "sapling",
            "bush", "flower", "tallgrass", "doubleplant", "slab", "stairs", "wall", "fence",
            "door", "bars", "pane", "thinglass", "carpet", "tnt", "diode", "repeater",
            "comparator", "torch", "button", "lever", "pressure", "tripwire", "ladder", "rail",
            "sign", "chest", "furnace", "hopper", "dispenser", "dropper", "anvil", "enchant",
            "brewing", "skull", "pot");
    private static final List<String> SUPPORT_EXCLUDES = List.of(
            "chest", "furnace", "hopper", "workbench", "dispenser", "dropper", "enchant",
            "anvil", "brewing");

    private static final class Plan {
        int slot;
        boolean wasGround, landed, allow, skipPlacement, clutch;
        boolean telly, groundSupported, failed, sprint;
        float yaw, pitch;
        Placement target;
        String holdReason;
        boolean placed;
    }

    private static final class Attempt {
        final Placement placement;
        final RayHit hit;
        final int slot, serial;
        ScaffoldPacket packet;
        String error;
        boolean cancelled;

        Attempt(Placement placement, RayHit hit, int slot, int serial) {
            this.placement = placement;
            this.hit = hit;
            this.slot = slot;
            this.serial = serial;
        }
    }

    private static final class PendingPlacement {
        final Cell support;
        final int tick;
        final float yaw, pitch;
        boolean seen;
        boolean lookChecked;

        PendingPlacement(Cell support, int tick, boolean seen, float yaw, float pitch) {
            this.support = support;
            this.tick = tick;
            this.seen = seen;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    public final ScaffoldSettings config;
    private final Host host;
    private final SprintController sprintController;
    private final Random random = new Random();

    private Plan plan;
    private boolean prepared;
    private int preparedFor = -1;
    private int lastPlanTick = -1;
    private int lastCounterTick = -1;
    private boolean landedThisTick;
    private Angles angles;
    private String blockedReason;

    private final Map<Cell, BlockInfo> blocks = new HashMap<>();
    private final Map<FaceKey, RotationHit> faceCache = new HashMap<>();
    private final Map<Cell, PendingPlacement> pending = new HashMap<>();
    private final Map<Cell, Integer> rejectedUntil = new HashMap<>();
    private final Set<Integer> lowStackSwitched = new HashSet<>();

    private int playerId = -1;
    private int groundTicks, airTicks, clutchTicks;
    private boolean wasGrounded, groundedInitialized, jumpedGround, manualJumpActive;
    private boolean placementPrimed;
    private double baseY = Double.NaN;
    private Placement aimTarget;
    private Cell searchSupport;

    private int originalSlot = -1, selectedSlot = -1;
    private int syncedSlot = -1, publishedSlot = -1;

    private boolean serverLookKnown, serverPosKnown, sentSneaking;
    private boolean sprintControl;
    private float serverYaw, serverPitch;
    private double serverX, serverY, serverZ;
    private int movementSerial;
    private int lastMovementTick = -1;
    private int attackTick = -100;
    private int lastAttemptTick = -100;
    private int cooldownUntil = -100;
    private boolean poseValid;

    private Attempt sending;
    private int rayCount;
    private String rayReason;

    public ScaffoldWalkModule(Host host, MarkerHooks hooks, SprintController sprintController) {
        super("scaffold-walk", "ScaffoldWalk", Category.PLAYER);
        this.host = host;
        this.sprintController = sprintController;
        this.config = new ScaffoldSettings(hooks);
    }

    @Override public String hudSuffix() { return ""; }
    @Override public List<? extends SettingValue<?>> settings() { return config.roots(); }
    @Override protected void onEnable() {
        resetState();
        originalSlot = selectedSlot = -1;
        syncedSlot = publishedSlot = -1;
        Angles camera = host.available() ? host.camera() : null;
        angles = camera == null ? null : new Angles(camera.yaw(), camera.pitch());
        playerId = host.playerId();
    }
    @Override protected void onDisable() { stop(true); }
    @Override public void worldChanged() { if (enabled()) stop(true); }

    public String pendingReason() { return enabled() && plan == null ? blockedReason : null; }
    public boolean blocksCombat() { return false; }
    public boolean safeWalk() { return false; }

    /** The planned look, independent of the camera. */
    public Angles angles() {
        if (plan == null) return angles;
        return new Angles(plan.yaw, plan.pitch);
    }

    public boolean correctingMovement() {
        return enabled() && host.available() && config.moveFix.get() && plan != null
                && plan.allow && !plan.failed;
    }

    /** Clutch holds horizontal displacement for the tick, like the Lua freeze. */
    public boolean clutchHorizontal() {
        return enabled() && host.available() && config.placement.get() && plan != null
                && plan.allow && plan.clutch && !plan.failed;
    }

    /** Static helper used by the movement adapter for the silent-move-fix arm. */
    public static float[] fixedMovement(float cameraYaw, float rotationYaw, float forward, float strafe) {
        return ScaffoldGeometry.fixedInput(cameraYaw, rotationYaw, forward, strafe);
    }

    // ---------------------------------------------------------------- planning

    /** Runs once per client tick, before the player update. */
    public void tick() {
        if (!enabled()) return;
        if (!host.available()) {
            suspend(false);
            blockedReason = "Waiting for player/world";
            return;
        }
        int tick = host.ticksExisted();
        if (playerId != host.playerId() || tick < lastPlanTick) {
            stop(false);
            playerId = host.playerId();
        }
        if (prepared && preparedFor == tick) return;

        Context context = context();
        if (context == null) {
            suspend(false);
            return;
        }
        if (syncedSlot < 0) syncedSlot = host.selectedSlot();

        prepared = true;
        preparedFor = tick;
        lastPlanTick = tick;
        plan = null;
        blockedReason = null;

        updateCounters(context);
        blocks.clear();
        watchPlacements(context);

        String why = ready();
        if (why != null) {
            suspend(false);
            blockedReason = why;
            host.setSprinting(false);
            return;
        }
        int slot = selectSlot(context);
        if (slot < 0) {
            suspend(false);
            blockedReason = "Cannot select blocks";
            host.setSprinting(false);
            return;
        }

        Plan next = new Plan();
        next.slot = slot;
        next.wasGround = host.grounded();
        next.landed = landedThisTick;
        next.allow = config.placement.get();
        plan = next;
        planRotation(context, next);
        angles = new Angles(next.yaw, next.pitch);
        if (config.tellyShift.get()) host.suppressSneak();
        if (!next.allow) {
            Angles camera = host.camera();
            next.yaw = camera.yaw();
            next.pitch = camera.pitch();
            next.skipPlacement = true;
        }
    }

    /** Ensures the plan exists before the walking update. */
    public void motionBefore() {
        if (!prepared) tick();
    }

    /** Look published in the C03 packet for this walking update. */
    public Angles motionPre() {
        if (!enabled()) return null;
        if (!prepared || plan == null || plan.failed) return null;
        return new Angles(plan.yaw, plan.pitch);
    }

    /** Placement runs from the player's pre-move update, before the walk pose is sent. */
    public void motionPlace() {
        if (!enabled() || plan == null || plan.placed || plan.failed) return;
        Context context = context();
        if (context == null) return;
        plan.placed = placeOne(context, plan);
    }

    /** Sprint and auto-jump are re-evaluated after the live input update. */
    public void afterInput() {
        sprintControl = false;
        if (!enabled() || !host.available()) return;
        // Only telly bridging needs the scaffold's forward-look sprint gate. Non-telly
        // movement keeps the Sprint module/vanilla sprint in control.
        if (plan == null || !plan.allow || !plan.telly) return;
        if (config.tellyShift.get()) host.suppressSneak();
        sprintControl = true;
        boolean sprint = sprintController.update(config.sprint.get(), plan.yaw);
        plan.sprint = sprint;

        float[] input = movement();
        if (plan.telly && config.autoJump.get() && plan.groundSupported
                && (groundTicks >= 2 || plan.landed) && !jumpedGround
                && Math.abs(input[0]) + Math.abs(input[1]) > 0.1F && !host.jumpHeld()
                && !plan.clutch) {
            if (host.jump()) {
                jumpedGround = true;
            }
        }
    }

    /** True while this tick's scaffold plan owns the sprint/telly look decision. */
    public boolean controlsSprint() {
        return enabled() && sprintControl;
    }

    private Context context() {
        if (!host.available()) return null;
        Angles camera = host.camera();
        double x = host.positionX();
        double y = host.feetY();
        double z = host.positionZ();
        if (!finite(x) || !finite(y) || !finite(z) || camera == null) return null;
        double eyeY = y + host.eyeHeight(host.sneaking());
        return new Context(host.ticksExisted(), x, y, z, x, eyeY, z, camera.yaw(), camera.pitch());
    }

    private void updateCounters(Context context) {
        if (lastCounterTick == context.tick()) return;
        lastCounterTick = context.tick();
        boolean grounded = host.grounded();
        boolean landed = grounded && groundedInitialized && !wasGrounded;
        wasGrounded = grounded;
        groundedInitialized = true;
        landedThisTick = landed;
        if (grounded) {
            groundTicks++;
            airTicks = 0;
            if (landed && tellyEnabled()) placementPrimed = false;
        } else {
            groundTicks = 0;
            airTicks++;
            jumpedGround = false;
        }
    }

    private String ready() {
        if (host.inLiquid()) return "In liquid";
        if (host.onLadder() || host.riding()) return "Ladder / riding";
        if (host.inWeb()) return "Cobweb";
        if (!hasAnyBlock()) return "No eligible hotbar blocks";
        if (host.ticksExisted() < cooldownUntil) return "Resynchronising after server correction";
        return null;
    }

    private boolean hasAnyBlock() {
        for (int slot = 0; slot <= 8; slot++) {
            if (eligible(host.hotbar(slot))) return true;
        }
        return false;
    }

    private float[] movement() {
        float forward = host.forward();
        float strafe = host.strafe();
        int keyForward = (host.keyDown(17) ? 1 : 0) - (host.keyDown(31) ? 1 : 0);
        int keyStrafe = (host.keyDown(30) ? 1 : 0) - (host.keyDown(32) ? 1 : 0);
        if (keyForward != 0 || keyStrafe != 0) {
            forward = keyForward;
            strafe = keyStrafe;
        }
        return new float[]{forward, strafe};
    }

    private boolean tellyEnabled() {
        return config.telly.get() && (!config.tellyShift.get() || shiftDown());
    }

    private boolean shiftDown() {
        return host.keyDown(42) || host.keyDown(54);
    }

    // ------------------------------------------------------------- rotation

    private void planRotation(Context c, Plan p) {
        float lastYaw = serverLookKnown ? serverYaw : c.yaw();
        float lastPitch = serverLookKnown ? serverPitch : c.pitch();
        boolean jumpKey = host.jumpHeld();
        boolean requestedTelly = tellyEnabled();
        float[] input = movement();
        float forward = input[0];
        float strafe = input[1];
        boolean moving = Math.abs(forward) + Math.abs(strafe) > 0.1F;

        boolean carryManual = manualJumpActive && !(p.wasGround && requestedTelly && moving);
        boolean manualJump = carryManual || (jumpKey && (!requestedTelly || !moving));
        manualJumpActive = manualJump && (jumpKey || !p.wasGround);
        boolean telly = requestedTelly && !manualJump;
        int placeTick = Math.max(1, Math.min(6, config.placeTick.get()));

        if (Double.isNaN(baseY) || p.wasGround || !telly) baseY = Math.floor(c.y() - 1.0D);
        if (manualJump || (telly && jumpKey)) baseY = Math.floor(c.y()) - 1.0D;
        int ty = (int) Math.floor(baseY);

        rayCount = 0;
        rayReason = null;
        faceCache.clear();
        searchSupport = null;
        RayHit viewed = host.ray(c.eyeX(), c.eyeY(), c.eyeZ(), lastYaw, lastPitch, REACH);
        rayCount++;
        if (viewed != null) searchSupport = new Cell(viewed.x(), viewed.y(), viewed.z());

        float heading = ScaffoldGeometry.direction(c.yaw(), forward, strafe);
        double speed = moving ? 0.2D : Math.min(0.5D, Math.hypot(host.motionX(), host.motionZ()));
        double angle = Math.toRadians(heading);
        double mx = -Math.sin(angle) * speed;
        double mz = Math.cos(angle) * speed;

        Placement target = null;
        if (replaceable(block((int) Math.floor(c.x()), (int) Math.floor(c.y()), (int) Math.floor(c.z())))) {
            if (validTarget(c, aimTarget) && targetOnRoute(c, aimTarget, ty, mx, mz)
                    && closestFace(c, aimTarget, lastYaw, lastPitch) != null) {
                target = aimTarget;
            }
            int tx = (int) Math.floor(c.x());
            int tz = (int) Math.floor(c.z());
            if (target == null) target = searchedPlace(c, tx, ty, tz, lastYaw, lastPitch, true);
            if (target == null && (moving || speed > 0.01D)) {
                Set<Cell> visited = new HashSet<>();
                visited.add(new Cell(tx, ty, tz));
                for (int step = 1; step <= 3; step++) {
                    int aheadX = (int) Math.floor(c.x() + mx * step);
                    int aheadZ = (int) Math.floor(c.z() + mz * step);
                    Cell key = new Cell(aheadX, ty, aheadZ);
                    if (!visited.add(key)) continue;
                    target = searchedPlace(c, aheadX, ty, aheadZ, lastYaw, lastPitch, true);
                    if (target != null) break;
                }
            }
            if (target == null) target = searchedPlace(c, tx, ty, tz, lastYaw, lastPitch, false);
        }
        if ((target == null || !targetOnRoute(c, target, ty, mx, mz))
                && validTarget(c, aimTarget) && closestFace(c, aimTarget, lastYaw, lastPitch) != null) {
            target = aimTarget;
        }
        aimTarget = target;

        double predictedY = c.y();
        double motionY = host.motionY();
        for (int i = 0; i < 2; i++) {
            predictedY += motionY;
            motionY = (motionY - 0.08D) * 0.98D;
        }
        if (config.clutch.get() && target != null && !p.wasGround
                && destinationY(target) > predictedY && clutchTicks <= 8) {
            baseY = c.y() - 1.0D;
            target = searchedPlace(c, (int) Math.floor(c.x()), (int) Math.floor(baseY),
                    (int) Math.floor(c.z()), lastYaw, lastPitch, false);
            aimTarget = target;
            p.clutch = target != null;
            if (p.clutch) clutchTicks++;
            else clutchTicks = 0;
        } else {
            clutchTicks = 0;
        }

        RotationHit strict = target == null ? null : closestFace(c, target, lastYaw, lastPitch);
        boolean jitter = config.sourceJitter.get();
        p.groundSupported = p.wasGround && footprintSupported(c);
        boolean wantsTellyJump = telly && moving && (jumpKey || config.autoJump.get());
        float idleHeading = p.wasGround && wantsTellyJump ? heading : c.yaw();
        float idleYaw = idleHeading - (jitter ? random.nextFloat() : 0.5F);
        float idlePitch = 85.5F - (jitter ? random.nextFloat() : 0.5F);
        float yaw = angles != null ? angles.yaw() : lastYaw;
        float pitch = angles != null ? angles.pitch() : lastPitch;

        boolean watchdog = config.watchdog.get();
        boolean grim = config.grim.get();
        if (manualJump) {
            yaw = strict != null ? strict.yaw() : lastYaw;
            pitch = strict != null ? strict.pitch() : lastPitch;
        } else if (!watchdog && !grim && target != null) {
            yaw = strict != null ? strict.yaw() : lastYaw;
            pitch = strict != null ? strict.pitch() : lastPitch;
        }
        if (!manualJump && (watchdog || grim)) {
            float bestYaw = strict != null ? strict.yaw() : lastYaw;
            float bestPitch = strict != null ? strict.pitch() : lastPitch;
            if (airTicks > 0 || (config.tellyShift.get() && !shiftDown()) || !config.telly.get()) {
                yaw = bestYaw;
                pitch = bestPitch;
            } else if (groundTicks >= 1) {
                if (strict != null || !p.groundSupported || !wantsTellyJump) {
                    yaw = bestYaw;
                    pitch = bestPitch;
                } else {
                    yaw = idleYaw;
                    pitch = idlePitch;
                }
            }
        }

        boolean jumpReady = jumpKey || ((groundTicks >= 2 || p.landed) && !jumpedGround);
        boolean recoveringSprint = wantsTellyJump && p.groundSupported && jumpReady && !p.clutch;
        if (recoveringSprint) {
            yaw = idleYaw;
            pitch = idlePitch;
        }
        yaw = ScaffoldGeometry.near(yaw, lastYaw);
        pitch = ScaffoldGeometry.clamp(pitch, -90.0F, 90.0F);
        if (jitter) {
            float nudged = ScaffoldGeometry.clamp(pitch - (0.002F + random.nextFloat() * 0.004F),
                    -90.0F, 90.0F);
            if (strict == null || exact(c, c.eyeX(), c.eyeY(), c.eyeZ(), yaw, nudged, target) != null) {
                pitch = nudged;
            }
        }

        boolean approachingGap = false;
        if (target != null) {
            Face face = ScaffoldGeometry.byId(target.face());
            int destX = target.support().x() + face.dx();
            int destZ = target.support().z() + face.dz();
            approachingGap = destinationY(target) == ty
                    && ((destX == (int) Math.floor(c.x()) && destZ == (int) Math.floor(c.z()))
                    || (destX == (int) Math.floor(c.x() + mx) && destZ == (int) Math.floor(c.z() + mz)));
        }
        boolean earlyEdge = config.earlyEdgePlace.get() && telly && !p.wasGround && moving
                && strict != null && approachingGap && airTicks > 0 && airTicks < placeTick
                && targetOnRoute(c, target, ty, mx, mz);
        p.skipPlacement = recoveringSprint || (telly && airTicks < placeTick && !earlyEdge);
        p.holdReason = recoveringSprint ? "Telly ground recovery"
                : (telly && airTicks < placeTick && !earlyEdge) ? "Telly Place Tick" : null;

        boolean seekSprint = false;
        if (strict != null && config.preferSprintRay.get() && config.sprint.get()) {
            seekSprint = !sprintController.wouldSprint(lastYaw)
                    && sprintController.wouldSprint(strict.yaw());
        }
        RotationHit published = null;
        if (!telly && (!placementPrimed || !seekSprint) && !p.skipPlacement && target != null) {
            published = exact(c, c.eyeX(), c.eyeY(), c.eyeZ(), lastYaw, lastPitch, target);
        }
        if (published != null) {
            yaw = published.yaw();
            pitch = published.pitch();
        }

        p.yaw = yaw;
        p.pitch = pitch;
        p.target = target;
        p.telly = telly;
    }

    private RotationHit closestFace(Context c, Placement target, float lastYaw, float lastPitch) {
        if (!validTarget(c, target)) return null;
        boolean ascendingTelly = tellyEnabled() && host.jumpHeld();
        float[] input = movement();
        float forward = input[0];
        float strafe = input[1];
        boolean preferSprint = !ascendingTelly && config.preferSprintRay.get() && config.sprint.get()
                && forward > 0.1F;
        Face face = ScaffoldGeometry.byId(target.face());
        if (face == null) return null;
        FaceKey key = new FaceKey(new Cell(target.support().x(), target.support().y(), target.support().z()),
                target.face(), lastYaw, lastPitch, forward, strafe, preferSprint,
                c.eyeX(), c.eyeY(), c.eyeZ());
        RotationHit cached = faceCache.get(key);
        if (cached != null) return cached;

        RotationHit previous = exact(c, c.eyeX(), c.eyeY(), c.eyeZ(), lastYaw, lastPitch, target);
        if (previous != null && (!preferSprint || sprintRay(previous.yaw()))) {
            faceCache.put(key, previous);
            return previous;
        }

        RotationHit best = previous;
        Float bestDiff = previous == null ? null : 0.0F;
        boolean bestSprint = previous != null && preferSprint
                && sprintRay(previous.yaw());
        List<double[]> points = ScaffoldGeometry.facePoints(c.eyeX(), c.eyeY(), c.eyeZ(),
                target.support().x(), target.support().y(), target.support().z(), face, lastYaw);
        for (double[] point : points) {
            if (rayCount >= RAY_LIMIT) break;
            float[] values = ScaffoldGeometry.anglesTo(c.eyeX(), c.eyeY(), c.eyeZ(),
                    point[0], point[1], point[2]);
            float yaw = ScaffoldGeometry.near(values[0], lastYaw);
            float pitch = values[1];
            if (Math.abs(pitch) > 89.9F) yaw = lastYaw;
            pitch = ScaffoldGeometry.clamp(pitch, -90.0F, 90.0F);
            RotationHit hit = exact(c, c.eyeX(), c.eyeY(), c.eyeZ(), yaw, pitch, target);
            if (hit == null) continue;
            float diff = Math.abs(ScaffoldGeometry.wrap(yaw - lastYaw));
            boolean candidateSprint = preferSprint && sprintRay(hit.yaw());
            if (best == null || (candidateSprint && !bestSprint)
                    || (candidateSprint == bestSprint && (diff < bestDiff - 0.05F
                    || (Math.abs(diff - bestDiff) <= 0.05F && hit.margin() > best.margin())))) {
                best = hit;
                bestDiff = diff;
                bestSprint = candidateSprint;
            }
        }
        if (best != null) faceCache.put(key, best);
        return best;
    }

    private boolean sprintRay(float yaw) {
        return sprintController.wouldSprint(yaw);
    }

    private Placement searchedPlace(Context c, int tx, int ty, int tz, float lastYaw,
                                    float lastPitch, boolean directOnly) {
        if (!replaceable(block(tx, ty, tz))) return null;

        List<Placement> neighbours = new ArrayList<>();
        for (Face face : ScaffoldGeometry.neighbourFaces()) {
            Placement target = new Placement(new Position(tx - face.dx(), ty - face.dy(), tz - face.dz()),
                    face.id());
            if (!validTarget(c, target)) continue;
            neighbours.add(target);
        }
        neighbours.sort((a, b) -> {
            int priorityA = searchSupport != null
                    && searchSupport.x() == a.support().x()
                    && searchSupport.y() == a.support().y()
                    && searchSupport.z() == a.support().z() ? 0 : 1;
            int priorityB = searchSupport != null
                    && searchSupport.x() == b.support().x()
                    && searchSupport.y() == b.support().y()
                    && searchSupport.z() == b.support().z() ? 0 : 1;
            if (priorityA != priorityB) return Integer.compare(priorityA, priorityB);
            return Integer.compare(neighbourOrder(a.face()), neighbourOrder(b.face()));
        });
        for (Placement candidate : neighbours) {
            if (closestFace(c, candidate, lastYaw, lastPitch) != null) return candidate;
        }
        if (directOnly) return null;

        int bx = (int) Math.floor(c.x());
        int by = (int) Math.floor(c.y());
        int bz = (int) Math.floor(c.z());
        for (ScaffoldGeometry.Offset offset : ScaffoldGeometry.SEARCH_OFFSETS) {
            if (rayCount >= RAY_LIMIT - 128) break;
            int x = bx + offset.x();
            int y = by + offset.y();
            int z = bz + offset.z();
            double dx = x + 0.5D - c.x();
            double dy = y + 0.5D - c.eyeY();
            double dz = z + 0.5D - c.z();
            if (!supportAt(x, y, z) || dx * dx + dy * dy + dz * dz > (REACH + 1) * (REACH + 1)) {
                continue;
            }
            Placement best = null;
            float bestDiff = 0;
            int bestDistance = Integer.MAX_VALUE;
            for (Face face : new Face[]{ScaffoldGeometry.UP, ScaffoldGeometry.NORTH,
                    ScaffoldGeometry.SOUTH, ScaffoldGeometry.WEST, ScaffoldGeometry.EAST}) {
                if (face.dy() != 0 && !(face.dy() == 1 && y + 1 == ty)) continue;
                Placement target = new Placement(new Position(x, y, z), face.id());
                if (!validTarget(c, target)) continue;
                RotationHit hit = closestFace(c, target, lastYaw, lastPitch);
                if (hit == null) continue;
                float diff = Math.abs(ScaffoldGeometry.wrap(hit.yaw() - lastYaw));
                int distance = (x + face.dx() - tx) * (x + face.dx() - tx)
                        + (y + face.dy() - ty) * (y + face.dy() - ty)
                        + (z + face.dz() - tz) * (z + face.dz() - tz);
                if (best == null || distance < bestDistance
                        || (distance == bestDistance && diff < bestDiff)) {
                    best = target;
                    bestDiff = diff;
                    bestDistance = distance;
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static int neighbourOrder(int faceId) {
        Face[] faces = ScaffoldGeometry.neighbourFaces();
        for (int i = 0; i < faces.length; i++) {
            if (faces[i].id() == faceId) return i;
        }
        return 99;
    }

    private boolean validTarget(Context c, Placement target) {
        if (target == null) return false;
        Face face = ScaffoldGeometry.byId(target.face());
        if (face == null) return false;
        Cell support = new Cell(target.support().x(), target.support().y(), target.support().z());
        Cell destination = new Cell(support.x() + face.dx(), support.y() + face.dy(),
                support.z() + face.dz());
        if (rejected(destination)) return false;
        if (!supportAt(support.x(), support.y(), support.z())) return false;
        if (!replaceable(block(destination.x(), destination.y(), destination.z()))) return false;

        int x = destination.x();
        int y = destination.y();
        int z = destination.z();
        if (x < c.x() + 0.3D && x + 1 > c.x() - 0.3D
                && z < c.z() + 0.3D && z + 1 > c.z() - 0.3D
                && y < c.y() + 1.8D && y + 1 > c.y() + 0.00001D) {
            return false;
        }
        double hx = support.x() + 0.5D + face.dx() * 0.5D;
        double hy = support.y() + 0.5D + face.dy() * 0.5D;
        double hz = support.z() + 0.5D + face.dz() * 0.5D;
        double dx = hx - c.x();
        double dy = hy - c.eyeY();
        double dz = hz - c.z();
        return dx * dx + dy * dy + dz * dz <= REACH * REACH + 1.0D;
    }

    private boolean targetOnRoute(Context c, Placement target, int ty, double mx, double mz) {
        if (!validTarget(c, target)) return false;
        Face face = ScaffoldGeometry.byId(target.face());
        if (face == null) return false;
        if (target.support().y() + face.dy() != ty) return false;
        double destX = target.support().x() + face.dx();
        double destZ = target.support().z() + face.dz();
        double startX = c.x();
        double startZ = c.z();
        double deltaX = mx * 3.0D;
        double deltaZ = mz * 3.0D;
        double entry = 0;
        double exit = 1;
        for (int axis = 0; axis < 2; axis++) {
            double low = (axis == 0 ? destX : destZ) - 0.3D;
            double high = (axis == 0 ? destX : destZ) + 1.3D;
            double start = axis == 0 ? startX : startZ;
            double delta = axis == 0 ? deltaX : deltaZ;
            if (Math.abs(delta) < 1e-12) {
                if (start < low || start > high) return false;
            } else {
                double a = (low - start) / delta;
                double b = (high - start) / delta;
                entry = Math.max(entry, Math.min(a, b));
                exit = Math.min(exit, Math.max(a, b));
            }
        }
        return entry <= exit;
    }

    private boolean footprintSupported(Context c) {
        int y = (int) Math.floor(c.y() - 0.0001D);
        for (int x = (int) Math.floor(c.x() - 0.29999D);
             x <= (int) Math.floor(c.x() + 0.29999D); x++) {
            for (int z = (int) Math.floor(c.z() - 0.29999D);
                 z <= (int) Math.floor(c.z() + 0.29999D); z++) {
                if (supportAt(x, y, z)) return true;
            }
        }
        return false;
    }

    private int destinationY(Placement target) {
        Face face = ScaffoldGeometry.byId(target.face());
        return target.support().y() + (face == null ? 0 : face.dy());
    }

    private Cell destination(Placement target) {
        Face face = ScaffoldGeometry.byId(target.face());
        return new Cell(target.support().x() + (face == null ? 0 : face.dx()),
                target.support().y() + (face == null ? 0 : face.dy()),
                target.support().z() + (face == null ? 0 : face.dz()));
    }

    // ------------------------------------------------------------ placement

    private boolean placeOne(Context c, Plan p) {
        if (!p.allow || !config.placement.get()) return false;
        if (p.skipPlacement) return delay(p.holdReason != null ? p.holdReason : "Placement hold");
        if (p.placed || lastAttemptTick == c.tick()) return false;
        if (attackTick == c.tick()) return delay("An attack was already sent this tick");
        if (!poseValid || !serverLookKnown || !serverPosKnown) {
            return delay("Waiting for a preceding normal movement pose");
        }
        if (c.tick() - lastMovementTick > 2 || c.tick() < lastMovementTick) {
            return delay("Waiting for the current walking pose");
        }
        if (Math.abs(serverPitch) > 90.0F) return delay("Published pitch is outside bounds");
        if (sentSneaking != host.sneaking()) {
            return delay("Sneak eye height changed; wait for the next ordinary pose");
        }
        if (!p.telly && publishedSlot != p.slot) {
            return delay("Publish the selected block slot before the first placement");
        }
        double positionX = c.x() - serverX;
        double positionY = c.y() - serverY;
        double positionZ = c.z() - serverZ;
        if (positionX * positionX + positionY * positionY + positionZ * positionZ
                > 0.0009D + 1e-12D) {
            return delay("Previous movement position changed");
        }

        blocks.clear();
        faceCache.clear();
        rayCount = 0;
        Placement target = p.target;
        if (!validTarget(c, target)) return delay("No valid support / destination");
        RotationHit hit = exact(c, c.eyeX(), c.eyeY(), c.eyeZ(), p.yaw, p.pitch, target);
        if (hit == null) return delay(rayReason != null ? rayReason : "Applied rotation misses the face");

        double eyeY = serverY + host.eyeHeight(sentSneaking);
        RotationHit serverHit = exact(c, serverX, eyeY, serverZ, p.yaw, p.pitch, target);
        if (serverHit == null) {
            return delay("Ray differs at the preceding server position");
        }
        boolean precedingRequired = !p.telly || config.raycast.get();
        if (precedingRequired
                && !host.supportRay(serverX, eyeY, serverZ, serverYaw, serverPitch,
                target.support().x(), target.support().y(), target.support().z(), REACH)) {
            return delay("Publish a look at this support block before placing");
        }
        if (config.raycast.get()) {
            if (exact(c, serverX, eyeY, serverZ, serverYaw, serverPitch, target) == null) {
                return delay("Strict RayCast: preceding look must hit the same face");
            }
            if (!p.telly && (Math.abs(ScaffoldGeometry.wrap(serverYaw - p.yaw)) > 0.001F
                    || Math.abs(serverPitch - p.pitch) > 0.001F)) {
                return delay("Strict RayCast: keep the published look");
            }
        }

        ItemInfo stack = host.hotbar(p.slot);
        if (host.selectedSlot() != p.slot || !eligible(stack)) {
            return delay("Prepared block slot changed");
        }
        if (!support(block(target.support().x(), target.support().y(), target.support().z()))
                || !supportAt(target.support().x(), target.support().y(), target.support().z())) {
            return delay("Support changed before the controller click");
        }

        lastAttemptTick = c.tick();
        Attempt attempt = new Attempt(target, hit.hit(), p.slot, movementSerial);
        sending = attempt;
        boolean placed;
        try {
            placed = host.place(target.support(), target.face(),
                    hit.hit().hx(), hit.hit().hy(), hit.hit().hz(), p.slot, !config.noSwing.get());
        } finally {
            sending = null;
        }
        Cell destination = destination(target);
        boolean packetSent = attempt.packet != null;
        if (packetSent && !attempt.cancelled) {
            blocks.clear();
            BlockInfo placedBlock = block(destination.x(), destination.y(), destination.z());
            boolean seen = placedBlock != null && !replaceable(placedBlock);
            pending.put(destination, new PendingPlacement(
                    new Cell(target.support().x(), target.support().y(), target.support().z()),
                    c.tick(), seen, p.yaw, p.pitch));
        }
        if (attempt.error != null || attempt.cancelled) {
            reject(destination, c.tick());
            blockedReason = attempt.error != null ? attempt.error : "Own C08 cancelled";
            return false;
        }
        if (placed && packetSent) {
            placementPrimed = true;
            blockedReason = null;
            return true;
        }
        blockedReason = packetSent ? "Placement refused" : "Placement API failed";
        cooldownUntil = Math.max(cooldownUntil, c.tick() + 2);
        return false;
    }

    private RotationHit exact(Context c, double eyeX, double eyeY, double eyeZ,
                              float yaw, float pitch, Placement target) {
        if (target == null) {
            rayReason = "No target";
            return null;
        }
        if (rayCount >= RAY_LIMIT) {
            rayReason = "Ray budget reached";
            return null;
        }
        rayCount++;
        RayHit hit = host.ray(eyeX, eyeY, eyeZ, yaw, pitch, REACH);
        if (hit == null || hit.face() < 0) {
            rayReason = "Ray did not hit a block";
            return null;
        }
        if (hit.x() != target.support().x() || hit.y() != target.support().y()
                || hit.z() != target.support().z() || hit.face() != target.face()) {
            rayReason = "Support / face differs";
            return null;
        }
        Face face = ScaffoldGeometry.byId(target.face());
        double margin = ScaffoldGeometry.faceMargin(hit.hx(), hit.hy(), hit.hz(),
                target.support().x(), target.support().y(), target.support().z(), face);
        if (margin < 0) {
            rayReason = "Hit lies near a face edge";
            return null;
        }
        rayReason = null;
        return new RotationHit(yaw, pitch, hit, margin);
    }

    private String validatePlacement(Attempt attempt, ScaffoldPacket packet) {
        Placement target = attempt.placement;
        if (packet.placeX() != target.support().x()
                || packet.placeY() != target.support().y()
                || packet.placeZ() != target.support().z()) {
            return "C08 support differs from the validated ray";
        }
        if (packet.placeFace() != target.face()) {
            return "C08 face differs from the validated ray";
        }
        double[] expected = {attempt.hit.hx() - target.support().x(),
                attempt.hit.hy() - target.support().y(),
                attempt.hit.hz() - target.support().z()};
        float[] actual = {packet.offsetX(), packet.offsetY(), packet.offsetZ()};
        for (int axis = 0; axis < 3; axis++) {
            float value = actual[axis];
            if (!finite(value) || value < 0 || value > 1
                    || ScaffoldGeometry.wire(value) != ScaffoldGeometry.wire(expected[axis])) {
                return "C08 relative hit coordinate differs from the validated ray";
            }
        }
        return null;
    }

    private boolean delay(String reason) {
        blockedReason = reason;
        return false;
    }

    private void reject(Cell destination, int tick) {
        rejectedUntil.put(destination, tick + 40);
        cooldownUntil = Math.max(cooldownUntil, tick + 5);
        pending.remove(destination);
        aimTarget = null;
        blocks.clear();
    }

    private void watchPlacements(Context c) {
        if (pending.isEmpty()) {
            rejectedUntil.entrySet().removeIf(entry -> entry.getValue() <= c.tick());
            return;
        }
        Set<Cell> invalid = new HashSet<>();
        Iterator<Map.Entry<Cell, PendingPlacement>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Cell, PendingPlacement> entry = iterator.next();
            Cell destination = entry.getKey();
            PendingPlacement record = entry.getValue();
            BlockInfo block = block(destination.x(), destination.y(), destination.z());
            boolean present = block != null && !replaceable(block);
            boolean changed = false;
            if (record.seen && !present) {
                changed = true;
            } else if (!record.lookChecked && c.tick() - record.tick >= 3) {
                changed = true;
            } else if (!record.seen && c.tick() - record.tick >= 3) {
                changed = true;
            } else if (c.tick() - record.tick >= 40) {
                iterator.remove();
                continue;
            } else if (present) {
                record.seen = true;
            }
            if (changed) {
                iterator.remove();
                invalid.add(destination);
                rejectedUntil.put(destination, c.tick() + 40);
                cooldownUntil = Math.max(cooldownUntil, c.tick() + 5);
            }
        }
        boolean cascade = true;
        while (cascade) {
            cascade = false;
            Iterator<Map.Entry<Cell, PendingPlacement>> entries = pending.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<Cell, PendingPlacement> entry = entries.next();
                Cell support = entry.getValue().support;
                if (invalid.contains(support)) {
                    entries.remove();
                    invalid.add(entry.getKey());
                    rejectedUntil.put(entry.getKey(), c.tick() + 40);
                    cascade = true;
                }
            }
        }
        if (!invalid.isEmpty()) {
            aimTarget = null;
            blocks.clear();
        }
        rejectedUntil.entrySet().removeIf(entry -> entry.getValue() <= c.tick());
    }

    // -------------------------------------------------------------- packets

    /** Called for every outbound packet before it reaches the network manager. */
    public boolean outgoing(ScaffoldPacket packet) {
        if (!enabled() || packet == null) return false;
        switch (packet.id()) {
            case 0x02 -> attackTick = host.ticksExisted();
            case 0x03, 0x04, 0x05, 0x06 -> movementSent(packet);
            case 0x08 -> placementSent(packet);
            case 0x09 -> slotSent(packet);
            default -> { }
        }
        return false;
    }

    private void movementSent(ScaffoldPacket packet) {
        if (packet.cancelled() || packet.excluded()) return;
        movementSerial++;
        lastMovementTick = host.ticksExisted();
        if (packet.hasLook()) {
            serverYaw = packet.yaw();
            serverPitch = packet.pitch();
            serverLookKnown = true;
        }
        if (packet.hasPosition()) {
            serverX = packet.x();
            serverY = packet.y();
            serverZ = packet.z();
            serverPosKnown = true;
        }
        sentSneaking = host.sneaking();
        poseValid = plan != null && !plan.failed;
        int held = host.selectedSlot();
        if (syncedSlot < 0) syncedSlot = held;
        publishedSlot = syncedSlot == held ? held : -1;
        verifyPlacementLook();
    }

    /** The Lua required the movement after a C08 to publish that placement's look. */
    private void verifyPlacementLook() {
        if (pending.isEmpty() || !serverLookKnown) return;
        Iterator<Map.Entry<Cell, PendingPlacement>> entries = pending.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Cell, PendingPlacement> entry = entries.next();
            PendingPlacement record = entry.getValue();
            if (record.lookChecked || record.tick != host.ticksExisted()) continue;
            record.lookChecked = true;
            if (Math.abs(ScaffoldGeometry.wrap(serverYaw - record.yaw)) > 0.001F
                    || Math.abs(serverPitch - record.pitch) > 0.001F) {
                entries.remove();
                rejectedUntil.put(entry.getKey(), host.ticksExisted() + 40);
                cooldownUntil = Math.max(cooldownUntil, host.ticksExisted() + 5);
                aimTarget = null;
            }
        }
    }

    private void slotSent(ScaffoldPacket packet) {
        publishedSlot = -1;
        placementPrimed = false;
        int reported = packet.slot();
        if (reported < 0) reported = host.selectedSlot();
        syncedSlot = packet.cancelled() ? -1 : reported;
    }

    private void placementSent(ScaffoldPacket packet) {
        Attempt attempt = sending;
        if (attempt == null) return;
        attempt.packet = packet;
        if (packet.cancelled()) {
            attempt.cancelled = true;
            return;
        }
        if (attempt.serial != movementSerial) {
            attempt.error = "A new movement packet preceded this placement";
            return;
        }
        if (!packet.alignPlacement(attempt.placement,
                attempt.hit.hx(), attempt.hit.hy(), attempt.hit.hz())) {
            attempt.error = "C08 packet not available";
            return;
        }
        String error = validatePlacement(attempt, packet);
        if (attempt.error == null) attempt.error = error;
    }

    /** Inbound packet hook, called on the client thread before processing. */
    public void packetReceive(Object packet) {
        if (!enabled() || packet == null) return;
        if (packet instanceof net.minecraft.network.play.server.S12PacketEntityVelocity velocity) {
            if (velocity.getEntityID() == host.playerId()) {
                placementPrimed = false;
                poseValid = false;
            }
            return;
        }
        if (packet instanceof net.minecraft.network.play.server.S27PacketExplosion) {
            placementPrimed = false;
            poseValid = false;
            return;
        }
        if (packet instanceof net.minecraft.network.play.server.S01PacketJoinGame
                || packet instanceof net.minecraft.network.play.server.S07PacketRespawn
                || packet instanceof net.minecraft.network.play.server.S40PacketDisconnect) {
            stop(false);
            return;
        }
        if (packet instanceof net.minecraft.network.play.server.S08PacketPlayerPosLook) {
            stop(false);
            cooldownUntil = Math.max(cooldownUntil, host.ticksExisted() + 10);
            aimTarget = null;
            placementPrimed = false;
        }
    }

    // ------------------------------------------------------------- inventory

    private int selectSlot(Context c) {
        int current = host.selectedSlot();
        if (current < 0 || current > 8) return -1;
        boolean earlySwitch = config.hypixelBlockSwitch.get();
        if (!earlySwitch) lowStackSwitched.clear();
        for (int slot = 0; slot <= 8; slot++) {
            ItemInfo stack = host.hotbar(slot);
            if (!eligible(stack) || stack.count() > 4) lowStackSwitched.remove(slot);
        }
        ItemInfo held = host.hotbar(current);
        boolean switchingLow = earlySwitch && eligible(held) && held.count() <= 4
                && !lowStackSwitched.contains(current);
        int chosen = -1;
        int count = -1;
        if (switchingLow) {
            for (int offset = 1; offset <= 8; offset++) {
                int slot = (current + offset) % 9;
                ItemInfo stack = host.hotbar(slot);
                if (!eligible(stack) || lowStackSwitched.contains(slot)) continue;
                if (chosen < 0 || (config.mostBlocks.get() && stack.count() > count)) {
                    chosen = slot;
                    count = stack.count();
                }
            }
            if (chosen < 0) chosen = current;
        }
        if (chosen < 0 && eligible(held)) chosen = current;
        if (chosen < 0) {
            for (int slot = 0; slot <= 8; slot++) {
                ItemInfo stack = host.hotbar(slot);
                if (!eligible(stack)) continue;
                if (chosen < 0 || (config.mostBlocks.get() && stack.count() > count)) {
                    chosen = slot;
                    count = stack.count();
                }
            }
        }
        if (chosen < 0) return -1;
        int before = host.selectedSlot();
        if (selectedSlot < 0) originalSlot = current;
        else if (current != selectedSlot) originalSlot = current;
        host.selectSlot(chosen);
        if (host.selectedSlot() != chosen) return -1;
        if (before != chosen) {
            if (switchingLow) lowStackSwitched.add(current);
            publishedSlot = -1;
            placementPrimed = false;
        }
        selectedSlot = chosen;
        return chosen;
    }

    private void restoreSlot() {
        if (selectedSlot >= 0 && originalSlot >= 0 && host.selectedSlot() == selectedSlot) {
            host.selectSlot(originalSlot);
        }
        selectedSlot = -1;
        originalSlot = -1;
        syncedSlot = -1;
        publishedSlot = -1;
        placementPrimed = false;
    }

    private static boolean eligible(ItemInfo item) {
        if (item == null || !item.block() || item.count() <= 0) return false;
        String name = item.name() == null ? "" : item.name().toLowerCase(Locale.ROOT);
        if (!name.startsWith("tile.")) return false;
        if (EXCLUDED_EXACT.contains(name)) return false;
        for (String word : EXCLUDED_WORDS) {
            if (name.indexOf(word, 5) >= 0) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- blocks

    private BlockInfo block(int x, int y, int z) {
        Cell key = new Cell(x, y, z);
        if (blocks.containsKey(key)) return blocks.get(key);
        BlockInfo info = host.block(x, y, z);
        blocks.put(key, info);
        return info;
    }

    private static boolean replaceable(BlockInfo block) {
        return block != null && block.replaceable();
    }

    private boolean support(BlockInfo block) {
        if (block == null || block.replaceable() || !block.blocksMovement()) return false;
        if (!block.fullCube() && !block.glass()) return false;
        String name = block.name() == null ? "" : block.name().toLowerCase(Locale.ROOT);
        for (String excluded : SUPPORT_EXCLUDES) {
            if (name.contains(excluded)) return false;
        }
        return true;
    }

    private boolean supportAt(int x, int y, int z) {
        if (rejected(new Cell(x, y, z))) return false;
        return support(block(x, y, z));
    }

    private boolean rejected(Cell cell) {
        Integer until = rejectedUntil.get(cell);
        if (until != null && until > host.ticksExisted()) return true;
        PendingPlacement record = pending.get(cell);
        return record != null && !record.seen;
    }

    // --------------------------------------------------------------- control

    private void suspend(boolean restoreSelection) {
        if (restoreSelection) restoreSlot();
        plan = null;
        prepared = false;
        preparedFor = -1;
        placementPrimed = false;
        aimTarget = null;
        blocks.clear();
        faceCache.clear();
    }

    private void stop(boolean restoreSelection) {
        suspend(restoreSelection);
        if (restoreSelection) {
            selectedSlot = originalSlot = -1;
            syncedSlot = publishedSlot = -1;
        }
        if (host.available()) host.setSprinting(false);
        resetState();
    }

    private void resetState() {
        plan = null;
        prepared = false;
        preparedFor = -1;
        lastPlanTick = -1;
        lastCounterTick = -1;
        landedThisTick = false;
        angles = null;
        blockedReason = null;
        blocks.clear();
        faceCache.clear();
        pending.clear();
        rejectedUntil.clear();
        lowStackSwitched.clear();
        playerId = -1;
        groundTicks = airTicks = clutchTicks = 0;
        wasGrounded = groundedInitialized = jumpedGround = manualJumpActive = false;
        placementPrimed = false;
        baseY = Double.NaN;
        aimTarget = null;
        searchSupport = null;
        serverLookKnown = serverPosKnown = sentSneaking = false;
        serverYaw = serverPitch = 0;
        serverX = serverY = serverZ = 0;
        movementSerial = 0;
        lastMovementTick = -1;
        attackTick = -100;
        lastAttemptTick = -100;
        cooldownUntil = -100;
        poseValid = false;
        sending = null;
        rayCount = 0;
        rayReason = null;
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }
}
