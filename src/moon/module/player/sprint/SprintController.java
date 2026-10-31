package moon.module.player.sprint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.potion.Potion;
import moon.module.player.scaffoldwalk.ScaffoldGeometry;

/**
 * Single sprint authority for the Sprint module and ScaffoldWalk.
 *
 * <p>Uses current movement input without synthesizing sprint-key presses.
 * A sprint attack keeps
 * the vanilla slowdown (C02 runs before {@code attackTargetEntityWithCurrentItem},
 * so the attack itself applies motion * 0.6 and clears sprint); the controller
 * waits a few ticks before starting sprint again.</p>
 */
public final class SprintController {
    /** Human reaction before Ctrl is pressed again after a sprint attack. */
    public static final int ATTACK_RECOVERY_TICKS = 5;

    private final Minecraft mc;
    private int resumeAt = -100;
    private int lastTicks = -1;
    private boolean forcedSprint;
    private boolean owned;

    public SprintController(Minecraft mc) { this.mc = mc; }

    /** Death/respawn/rejoin gives a new player entity with a reset tick counter.
     * A stale attack-recovery tick would otherwise block sprint until the old
     * tick count is reached again. */
    private void observeTimeline(EntityPlayerSP player) {
        if (lastTicks >= 0 && player.ticksExisted < lastTicks) reset();
        lastTicks = player.ticksExisted;
    }

    public void reset() {
        releaseControl();
        resumeAt = -100;
        lastTicks = -1;
    }

    /**
     * Called for a C02 before the client attack runs. Do not clear sprint here:
     * the vanilla sprint attack must still see it to apply the normal slowdown.
     */
    public void onAttack() {
        EntityPlayerSP player = mc.thePlayer;
        if (!owned || player == null || !player.isSprinting()) return;
        resumeAt = Math.max(resumeAt, player.ticksExisted + ATTACK_RECOVERY_TICKS);
    }

    /**
     * @param allow module/configuration allows sprinting
     * @param lookYaw the rotation that will be sent to the server
     * @return whether sprint is held after this update
     */
    public boolean update(boolean allow, float lookYaw) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return false;
        observeTimeline(player);
        owned = true;
        float heading = ScaffoldGeometry.direction(player.rotationYaw,
                player.movementInput == null ? 0 : player.movementInput.moveForward,
                player.movementInput == null ? 0 : player.movementInput.moveStrafe);
        float error = Math.abs(ScaffoldGeometry.wrap(heading - lookYaw));
        float limit = player.isSprinting() ? 55.0F : 45.0F;
        boolean desired = allow && error <= limit && canSprint(player)
                && player.ticksExisted >= resumeAt;
        apply(player, desired);
        return desired;
    }

    /** Candidate check for ScaffoldWalk's prefer-sprint-ray search. */
    public boolean wouldSprint(float lookYaw) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || player.movementInput == null) return false;
        observeTimeline(player);
        if (player.ticksExisted < resumeAt) return false;
        float heading = ScaffoldGeometry.direction(player.rotationYaw,
                player.movementInput.moveForward, player.movementInput.moveStrafe);
        if (Math.abs(ScaffoldGeometry.wrap(heading - lookYaw)) > 45.0F) return false;
        return canSprint(player);
    }

    private boolean canSprint(EntityPlayerSP player) {
        if (player.movementInput == null) return false;
        // movementInput is the live update; moveForward retains the previous tick.
        float forward = player.movementInput.moveForward;
        float strafe = player.movementInput.moveStrafe;
        if (Math.abs(forward) + Math.abs(strafe) < 0.0001F) return false;
        if (forward < 0.8F) return false;
        if (player.isCollidedHorizontally || player.isSneaking()) return false;
        if (!player.capabilities.allowFlying && player.getFoodStats().getFoodLevel() <= 6) return false;
        return !player.isUsingItem() && !player.isPotionActive(Potion.blindness)
                && !player.isPotionActive(Potion.moveSlowdown);
    }

    private void apply(EntityPlayerSP player, boolean sprint) {
        if (player.isSprinting() != sprint) player.setSprinting(sprint);
        forcedSprint = sprint;
    }

    /** No module owns sprint: hand the key and sprint state back to the player. */
    public void releaseControl() {
        if (!owned && !forcedSprint) return;
        owned = false;
        EntityPlayerSP player = mc.thePlayer;
        if (forcedSprint && player != null && player.isSprinting()
                && !mc.gameSettings.keyBindSprint.isKeyDown()) {
            player.setSprinting(false);
        }
        forcedSprint = false;
    }
}
