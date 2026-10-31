package moon.client;

import java.util.Locale;
import moon.module.combat.killaura.KillAuraRotations.Angles;
import moon.module.player.ScaffoldWalkModule;
import moon.module.player.ScaffoldWalkModule.BlockInfo;
import moon.module.player.ScaffoldWalkModule.ItemInfo;
import moon.module.player.ScaffoldWalkModule.Placement;
import moon.module.player.ScaffoldWalkModule.Position;
import moon.module.player.ScaffoldWalkModule.RayHit;
import moon.module.player.ScaffoldWalkModule.ScaffoldPacket;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjglx.input.Keyboard;

/**
 * MCP boundary for the native ScaffoldWalk controller. Geometry, faces and
 * placement validation live in the module; this adapter exposes the live world,
 * inventory, rays and packet objects.
 */
final class McpScaffoldHost implements ScaffoldWalkModule.Host {
    private final Minecraft mc;

    McpScaffoldHost(Minecraft mc) { this.mc = mc; }

    // ------------------------------------------------------------- lifecycle

    public boolean available() {
        return mc.thePlayer != null && mc.theWorld != null && mc.thePlayer.isEntityAlive()
                && mc.currentScreen == null && mc.thePlayer.sendQueue != null
                && mc.playerController != null;
    }
    public boolean inLiquid() {
        return mc.thePlayer != null && (mc.thePlayer.isInWater() || mc.thePlayer.isInLava());
    }
    public boolean onLadder() { return mc.thePlayer != null && mc.thePlayer.isOnLadder(); }
    public boolean riding() { return mc.thePlayer != null && mc.thePlayer.isRiding(); }
    public boolean inWeb() { return mc.thePlayer != null && mc.thePlayer.luaIsInWeb(); }

    // --------------------------------------------------------------- player

    public double feetY() { return mc.thePlayer.getEntityBoundingBox().minY; }
    public double positionX() { return mc.thePlayer.posX; }
    public double positionZ() { return mc.thePlayer.posZ; }
    public double motionX() { return mc.thePlayer.motionX; }
    public double motionY() { return mc.thePlayer.motionY; }
    public double motionZ() { return mc.thePlayer.motionZ; }
    public boolean grounded() { return mc.thePlayer.onGround; }
    public boolean sneaking() { return mc.thePlayer.isSneaking(); }
    public void setSprinting(boolean value) { mc.thePlayer.setSprinting(value); }
    public boolean jump() {
        try {
            mc.thePlayer.jump();
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }
    public boolean jumpHeld() { return keyDown(57); }
    public boolean shiftHeld() { return keyDown(42) || keyDown(54); }
    public void suppressSneak() {
        if (mc.thePlayer == null) return;
        if (mc.thePlayer.movementInput != null) mc.thePlayer.movementInput.sneak = false;
        mc.thePlayer.setSneaking(false);
    }
    public int playerId() { return mc.thePlayer == null ? -1 : mc.thePlayer.getEntityId(); }
    public int ticksExisted() { return mc.thePlayer == null ? -1 : mc.thePlayer.ticksExisted; }
    public int foodLevel() { return mc.thePlayer == null ? 20 : mc.thePlayer.getFoodStats().getFoodLevel(); }
    public Angles camera() {
        return new Angles(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
    }
    public float forward() { return mc.thePlayer.moveForward; }
    public float strafe() { return mc.thePlayer.moveStrafing; }
    public boolean keyDown(int keyCode) { return Keyboard.isKeyDown(keyCode); }
    public double eyeHeight(boolean sneaking) {
        return sneaking ? 1.5399999618530273D : 1.6200000047683716D;
    }

    // ------------------------------------------------------------ inventory

    public int selectedSlot() { return mc.thePlayer == null ? -1 : mc.thePlayer.inventory.currentItem; }
    public void selectSlot(int slot) {
        if (mc.thePlayer == null || slot < 0 || slot > 8) return;
        mc.thePlayer.inventory.currentItem = slot;
        if (mc.playerController != null) mc.playerController.moonSyncCurrentPlayItem();
    }
    public ItemInfo hotbar(int slot) {
        if (mc.thePlayer == null || slot < 0 || slot > 8) return null;
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
        if (stack == null || stack.stackSize <= 0) return null;
        if (stack.getItem() instanceof ItemBlock item) {
            return new ItemInfo(item.getBlock().getUnlocalizedName().toLowerCase(Locale.ROOT),
                    stack.stackSize, true);
        }
        String name = stack.getItem() == null ? "" : stack.getItem().getUnlocalizedName();
        return new ItemInfo(name.toLowerCase(Locale.ROOT), stack.stackSize, false);
    }

    // ---------------------------------------------------------------- world

    public BlockInfo block(int x, int y, int z) {
        if (mc.theWorld == null || y < 0 || y > 255) return null;
        BlockPos pos = new BlockPos(x, y, z);
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        boolean glass = block instanceof BlockGlass || block instanceof BlockStainedGlass;
        return new BlockInfo(block.getUnlocalizedName().toLowerCase(Locale.ROOT),
                block.isFullCube(), glass, replaceable(block, pos),
                block.getMaterial().blocksMovement());
    }

    /**
     * Placement replacement follows World.canBlockBePlaced (material replaceable)
     * plus the block override; Scaffold.lua allowed air, plants, snow, vine, fire,
     * water and lava as destination cells.
     */
    private boolean replaceable(Block block, BlockPos pos) {
        return block.isReplaceable(mc.theWorld, pos) || block.getMaterial().isReplaceable();
    }

    public RayHit ray(double eyeX, double eyeY, double eyeZ, float yaw, float pitch, double reach) {
        if (mc.theWorld == null) return null;
        Vec3 eye = new Vec3(eyeX, eyeY, eyeZ);
        Vec3 look = lookVector(yaw, pitch);
        Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, end, false, true, false);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || hit.sideHit == null || hit.hitVec == null) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        return new RayHit(pos.getX(), pos.getY(), pos.getZ(), hit.sideHit.getIndex(),
                hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord);
    }

    /**
     * The Lua's publishedSupportRay: is the support cell reachable along the look
     * before any full cube (glass included) blocks the path?
     */
    public boolean supportRay(double eyeX, double eyeY, double eyeZ, float yaw, float pitch,
                              int cellX, int cellY, int cellZ, double reach) {
        if (mc.theWorld == null) return false;
        Vec3 look = lookVector(yaw, pitch);
        double entry = 0;
        double exit = reach;
        for (int axis = 0; axis < 3; axis++) {
            double origin = axis == 0 ? eyeX : axis == 1 ? eyeY : eyeZ;
            double delta = axis == 0 ? look.xCoord : axis == 1 ? look.yCoord : look.zCoord;
            double cell = axis == 0 ? cellX : axis == 1 ? cellY : cellZ;
            if (Math.abs(delta) < 1e-12) {
                if (origin < cell || origin > cell + 1) return false;
            } else {
                double a = (cell - origin) / delta;
                double b = (cell + 1 - origin) / delta;
                entry = Math.max(entry, Math.min(a, b));
                exit = Math.min(exit, Math.max(a, b));
            }
        }
        if (entry > exit || entry > reach) return false;
        double hitX = eyeX + look.xCoord * entry;
        double hitY = eyeY + look.yCoord * entry;
        double hitZ = eyeZ + look.zCoord * entry;
        double dirX = hitX - eyeX;
        double dirY = hitY - eyeY;
        double dirZ = hitZ - eyeZ;
        int minX = MathHelper.floor_double(Math.min(eyeX, hitX));
        int maxX = MathHelper.floor_double(Math.max(eyeX, hitX));
        int minY = MathHelper.floor_double(Math.min(eyeY, hitY));
        int maxY = MathHelper.floor_double(Math.max(eyeY, hitY));
        int minZ = MathHelper.floor_double(Math.min(eyeZ, hitZ));
        int maxZ = MathHelper.floor_double(Math.max(eyeZ, hitZ));
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x == cellX && y == cellY && z == cellZ) continue;
                    Block block = mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
                    boolean glass = block instanceof BlockGlass || block instanceof BlockStainedGlass;
                    if (!block.isFullCube() && !glass) continue;
                    double cellEntry = 0;
                    double cellExit = 1;
                    for (int axis = 0; axis < 3; axis++) {
                        double origin = axis == 0 ? eyeX : axis == 1 ? eyeY : eyeZ;
                        double direction = axis == 0 ? dirX : axis == 1 ? dirY : dirZ;
                        double low = axis == 0 ? x : axis == 1 ? y : z;
                        if (Math.abs(direction) < 1e-12) {
                            if (origin < low || origin > low + 1) {
                                cellEntry = 1;
                                cellExit = 0;
                                break;
                            }
                        } else {
                            double a = (low - origin) / direction;
                            double b = (low + 1 - origin) / direction;
                            cellEntry = Math.max(cellEntry, Math.min(a, b));
                            cellExit = Math.min(cellExit, Math.max(a, b));
                        }
                    }
                    if (cellEntry <= cellExit && cellEntry < 1 - 0.000001 && cellExit > 0.000001) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static Vec3 lookVector(float yaw, float pitch) {
        float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(f1 * f2, f3, f * f2);
    }

    public boolean place(Position support, int face, double hitX, double hitY, double hitZ,
                         int slot, boolean clientSwing) {
        if (!available() || slot < 0 || slot > 8 || mc.thePlayer.inventory.currentItem != slot) {
            return false;
        }
        EnumFacing side = EnumFacing.getFront(face);
        if (side == null) return false;
        BlockPos pos = new BlockPos(support.x(), support.y(), support.z());
        BlockPos destination = pos.offset(side);
        Block destinationBlock = mc.theWorld.getBlockState(destination).getBlock();
        if (!replaceable(destinationBlock, destination)) return false;
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
        if (stack == null || stack.stackSize <= 0) return false;
        Vec3 hit = new Vec3(hitX, hitY, hitZ);
        boolean placed;
        try {
            placed = mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack,
                    pos, side, hit);
        } catch (RuntimeException failure) {
            return false;
        }
        if (placed) {
            if (clientSwing) mc.thePlayer.swingItem();
            else mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
        }
        if (stack.stackSize <= 0) mc.thePlayer.inventory.setInventorySlotContents(slot, null);
        return placed;
    }

    // -------------------------------------------------------------- packets

    /** MCP adapter over the concrete packet instance. */
    record View(Packet<?> packet) implements ScaffoldPacket {
        public int id() {
            if (packet instanceof C02PacketUseEntity) return 0x02;
            if (packet instanceof C08PacketPlayerBlockPlacement) return 0x08;
            if (packet instanceof C09PacketHeldItemChange) return 0x09;
            if (packet instanceof C0APacketAnimation) return 0x0A;
            if (packet instanceof C03PacketPlayer player) {
                if (player instanceof C03PacketPlayer.C06PacketPlayerPosLook) return 0x06;
                if (player instanceof C03PacketPlayer.C04PacketPlayerPosition) return 0x04;
                if (player instanceof C03PacketPlayer.C05PacketPlayerLook) return 0x05;
                return 0x03;
            }
            return -1;
        }
        public boolean excluded() {
            return packet instanceof C03PacketPlayer player && player.moonScaffoldExcluded();
        }
        public boolean hasPosition() {
            return packet instanceof C03PacketPlayer player && player.isMoving();
        }
        public boolean hasLook() {
            return packet instanceof C03PacketPlayer player && player.getRotating();
        }
        public double x() { return ((C03PacketPlayer) packet).getPositionX(); }
        public double y() { return ((C03PacketPlayer) packet).getPositionY(); }
        public double z() { return ((C03PacketPlayer) packet).getPositionZ(); }
        public float yaw() { return ((C03PacketPlayer) packet).getYaw(); }
        public float pitch() { return ((C03PacketPlayer) packet).getPitch(); }
        public boolean onGround() { return ((C03PacketPlayer) packet).isOnGround(); }
        public int slot() {
            return packet instanceof C09PacketHeldItemChange change ? change.getSlotId() : -1;
        }
        public int placeX() {
            return packet instanceof C08PacketPlayerBlockPlacement place ? place.getPosition().getX() : 0;
        }
        public int placeY() {
            return packet instanceof C08PacketPlayerBlockPlacement place ? place.getPosition().getY() : 0;
        }
        public int placeZ() {
            return packet instanceof C08PacketPlayerBlockPlacement place ? place.getPosition().getZ() : 0;
        }
        public int placeFace() {
            return packet instanceof C08PacketPlayerBlockPlacement place ? place.getPlacedBlockDirection() : -1;
        }
        public float offsetX() {
            return packet instanceof C08PacketPlayerBlockPlacement place ? place.getPlacedBlockOffsetX() : 0;
        }
        public float offsetY() {
            return packet instanceof C08PacketPlayerBlockPlacement place ? place.getPlacedBlockOffsetY() : 0;
        }
        public float offsetZ() {
            return packet instanceof C08PacketPlayerBlockPlacement place ? place.getPlacedBlockOffsetZ() : 0;
        }
        public boolean alignPlacement(Placement placement, double hitX, double hitY, double hitZ) {
            if (!(packet instanceof C08PacketPlayerBlockPlacement place)) return false;
            Position support = placement.support();
            float x = (float) moon.module.player.scaffoldwalk.ScaffoldGeometry.wire(
                    Math.max(0, Math.min(1, hitX - support.x())));
            float y = (float) moon.module.player.scaffoldwalk.ScaffoldGeometry.wire(
                    Math.max(0, Math.min(1, hitY - support.y())));
            float z = (float) moon.module.player.scaffoldwalk.ScaffoldGeometry.wire(
                    Math.max(0, Math.min(1, hitZ - support.z())));
            place.moonAlignPlacement(new BlockPos(support.x(), support.y(), support.z()),
                    placement.face(), x, y, z);
            return true;
        }
    }
}
