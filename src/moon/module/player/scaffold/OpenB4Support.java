package moon.module.player.scaffold;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.block.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.entity.*;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** OpenB4 behavior dependencies adapted to MCP's native input/packet lifecycle. */
public final class OpenB4Support {
    private static final Minecraft mc=Minecraft.getMinecraft();
    public static String label(String s){return s.replace("___","-").replace("__",".").replace('_',' ').trim();}
    public static boolean stopAuraBlocking(){var s=moon.client.McpModuleServices.current();return s!=null&&s.killAura.stopForScaffold();}
    public enum FakeRotation implements DisplayNamed {None,Smooth,Forward,Spin;public String displayName(){return name();}}
    public enum SwapSlot {Scaffold}
    public static final class MotionEvent {
        public float yaw,pitch;public double y;public boolean onGround;
        public MotionEvent(float yaw,float pitch,double y,boolean onGround){this.yaw=yaw;this.pitch=pitch;this.y=y;this.onGround=onGround;}
    }
    /** Keep donor accessors local; underlying values are native, persisted ClickGUI settings. */
    public static final class SliderSetting {
        public final SettingValue<?> value;
        public SliderSetting(SettingValue<?> value){this.value=value;}
        public double getInput(){Object v=value.get();return v instanceof Enum<?> e?e.ordinal():((Number)v).doubleValue();}
        public boolean isAtMin(){return getInput()==(value instanceof FloatSetting f?f.getMinimum():0);}
        public String getDisplay(boolean ignored){Object v=value.get();return v instanceof DisplayNamed n?n.displayName():v.toString();}
        public static <E extends Enum<E>&DisplayNamed> SliderSetting mode(String key,String title,Class<E> type,E initial,MarkerHooks h){return new SliderSetting(EnumSetting.builder(key,type,h).configure(title,initial).build());}
    }
    public static final class ButtonSetting {
        public final BooleanSetting value;
        public ButtonSetting(BooleanSetting value){this.value=value;}
        public boolean isToggled(){return value.get();}
    }
    public static final class KeyUtils {
        public static boolean isDown(KeyBinding key){int code=key.getKeyCode();return code<0?org.lwjglx.input.Mouse.isButtonDown(code+100):code>0&&org.lwjglx.input.Keyboard.isKeyDown(code);}
        public static void release(KeyBinding key){KeyBinding.setKeyBindStateUnchecked(key.getKeyCode(),false);}
    }
    public static final class RotationManager {
        public static float lastYaw,lastPitch,playerYawAtSend;
        public static Float smoothYaw;
        public static float movementYaw(){return mc.thePlayer.rotationYaw;}
        public static void reset(){if(mc.thePlayer!=null){lastYaw=playerYawAtSend=mc.thePlayer.rotationYaw;lastPitch=mc.thePlayer.rotationPitch;}smoothYaw=null;}
    }
    public static final class NoRotateModule {
        public static long correctedAt;
        public static boolean sinceSetback(long ms){return System.currentTimeMillis()-correctedAt<ms;}
    }
    public static final class Hypixel {
        public static boolean onHypixel(){return mc.getCurrentServerData()!=null&&mc.getCurrentServerData().serverIP.toLowerCase(java.util.Locale.ROOT).matches("(?:.*\\.)?hypixel\\.net(?::[0-9]+)?");}
    }
    public static final class MathUtils {
        public static double randomBetween(double a,double b){return a+Math.random()*(b-a);}
    }
    public static final class PacketUtils {
        public static boolean sentCloseWindow,sentUseEntity,sentAnyDigging,sentAttack,sentAnyPlacement;
        public static int serverSlot=-1,lastUse=-1;
        public static void reset(){sentCloseWindow=sentUseEntity=sentAnyDigging=sentAttack=sentAnyPlacement=false;serverSlot=lastUse=-1;}
        public static void observe(Packet<?> p){
            if(p instanceof C03PacketPlayer){sentCloseWindow=sentUseEntity=sentAnyDigging=sentAttack=sentAnyPlacement=false;}
            else if(p instanceof C0DPacketCloseWindow)sentCloseWindow=true;
            else if(p instanceof C02PacketUseEntity e){sentUseEntity=true;if(e.getAction()==C02PacketUseEntity.Action.ATTACK)sentAttack=true;}
            else if(p instanceof C07PacketPlayerDigging d){sentAnyDigging=true;if(d.getStatus()==C07PacketPlayerDigging.Action.RELEASE_USE_ITEM)lastUse=2;}
            else if(p instanceof C08PacketPlayerBlockPlacement b){sentAnyPlacement=true;if(b.getPlacedBlockDirection()==255)lastUse=1;}
            else if(p instanceof C09PacketHeldItemChange c){serverSlot=c.getSlotId();lastUse=3;}
        }
        public static boolean noUseOrDig(){return !sentUseEntity&&!sentAnyDigging;}
        public static boolean noAttackPlaceOrDig(){return !sentAttack&&!sentAnyPlacement&&!sentAnyDigging;}
        public static void send(Packet<?> p){if(mc.getNetHandler()!=null)mc.getNetHandler().addToSendQueue(p);}
    }
    public static final class AutoSwapModule {
        private static int remembered=-1,selected=-1;
        public static void remember(SwapSlot kind){if(mc.thePlayer!=null&&remembered<0)remembered=mc.thePlayer.inventory.currentItem;}
        public static void forget(){remembered=selected=-1;}
        public static int countBlocks(){int count=0;if(mc.thePlayer!=null)for(int i=0;i<9;i++){ItemStack s=mc.thePlayer.inventory.getStackInSlot(i);if(ItemUtils.isScaffoldBlock(s))count+=s.stackSize;}return count;}
        public static void swap(SwapSlot kind){
            remember(kind);int best=-1,n=0;
            for(int i=0;i<9;i++){ItemStack s=mc.thePlayer.inventory.getStackInSlot(i);if(ItemUtils.isScaffoldBlock(s)&&s.stackSize>n){best=i;n=s.stackSize;}}
            if(best>=0){if(selected>=0&&mc.thePlayer.inventory.currentItem!=selected)remembered=mc.thePlayer.inventory.currentItem;mc.thePlayer.inventory.currentItem=selected=best;PlayerUtils.syncCurrentPlayItem();}
        }
        public static void restore(SwapSlot kind,boolean restore){if(restore&&remembered>=0&&mc.thePlayer!=null&&mc.thePlayer.inventory.currentItem==selected){mc.thePlayer.inventory.currentItem=remembered;PlayerUtils.syncCurrentPlayItem();}forget();}
    }
    public static final class ItemUtils {
        public static boolean isScaffoldBlock(ItemStack s){return s!=null&&s.stackSize>0&&s.getItem() instanceof ItemBlock b&&BlockUtils.isPlaceable(b.getBlock())&&b.getBlock()!=Blocks.pumpkin;}
    }
    public static final class PlayerUtils {
        public static long groundTicks,airTicks;
        public static void trackGround(){if(mc.thePlayer.onGround){groundTicks++;airTicks=0;}else{airTicks++;groundTicks=0;}}
        public static void syncCurrentPlayItem(){mc.playerController.moonSyncCurrentPlayItem();}
        public static void swing(boolean silent,boolean reset){if(silent)PacketUtils.send(new C0APacketAnimation());else mc.thePlayer.swingItem();if(reset)mc.getItemRenderer().resetEquippedProgress();}
        public static void place(MovingObjectPosition hit,ItemStack stack){mc.playerController.onPlayerRightClick(mc.thePlayer,mc.theWorld,stack,hit.getBlockPos(),hit.sideHit,hit.hitVec);}
    public static boolean isAirBelow(double x, double y, double z) {
        BlockPos pos = new BlockPos(x, y - (double) (y % 1.0 == 0.0 ? 1 : 0), z);
        return mc.theWorld.isAirBlock(pos);
    }

    public static boolean isAirBelow(Entity entity) {
        return isAirBelow(entity.posX, entity.posY, entity.posZ);
    }

    public static boolean isAirBelow() {
        return isAirBelow(mc.thePlayer);
    }

    public static boolean nearEdge(double margin) {
        int x = MathHelper.floor_double(mc.thePlayer.posX);
        int y = MathHelper.floor_double(mc.thePlayer.posY - 0.01);
        int z = MathHelper.floor_double(mc.thePlayer.posZ);
        BlockPos pos = new BlockPos(x, y, z);
        if (mc.theWorld.isAirBlock(pos)) {
            return true;
        }
        double fx = mc.thePlayer.posX - (double) x;
        double fz = mc.thePlayer.posZ - (double) z;
        return fx < margin && mc.theWorld.isAirBlock(new BlockPos(x - 1, y, z)) || fx > 1.0 - margin && mc.theWorld.isAirBlock(new BlockPos(x + 1, y, z)) || fz < margin && mc.theWorld.isAirBlock(new BlockPos(x, y, z - 1)) || fz > 1.0 - margin && mc.theWorld.isAirBlock(new BlockPos(x, y, z + 1));
    }

    public static boolean inLiquid() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava();
    }

    public static Vec3 eyes(double x, double y, double z) {
        return new Vec3(x, y + (double) mc.thePlayer.getEyeHeight(), z);
    }

    public static int idOffset() {
        int id = Math.abs(mc.thePlayer.getEntityId());
        return id > 999 ? id + 213 : id * 99;
    }

    public static int decimals(double d) {
        return (int) Math.round(d % 1.0 * 10000.0);
    }

    public static int posYDecimals() {
        return decimals(mc.thePlayer.posY);
    }

    public static int motionYDecimals() {
        return (int) Math.round(mc.thePlayer.motionY % 10.0 * 10000.0);
    }

    public static float forwardInput() {
        float f = 0.0f;
        if (mc.gameSettings.keyBindForward.isKeyDown()) {
            f += 1.0f;
        }
        if (mc.gameSettings.keyBindBack.isKeyDown()) {
            f -= 1.0f;
        }
        return f;
    }

    public static float strafeInput() {
        float f = 0.0f;
        if (mc.gameSettings.keyBindLeft.isKeyDown()) {
            f += 1.0f;
        }
        if (mc.gameSettings.keyBindRight.isKeyDown()) {
            f -= 1.0f;
        }
        return f;
    }

    public static boolean isMoving() {
        return mc.thePlayer.movementInput.moveForward != 0.0f || mc.thePlayer.movementInput.moveStrafe != 0.0f;
    }

    public static boolean jumpKeyDown() {
        return KeyUtils.isDown(mc.gameSettings.keyBindJump);
    }

    public static void setJump(boolean jump) {
        mc.thePlayer.movementInput.jump = jump;
    }

    public static void setSneak(boolean sneak) {
        boolean was = mc.thePlayer.movementInput.sneak;
        mc.thePlayer.movementInput.sneak = sneak;
        if (!was && sneak) {
            setForward((float) ((double) getForward() * 0.3));
            setStrafe((float) ((double) getStrafe() * 0.3));
        }
    }

    public static float getForward() {
        return mc.thePlayer.movementInput.moveForward;
    }

    public static float getStrafe() {
        return mc.thePlayer.movementInput.moveStrafe;
    }

    public static void setForward(float f) {
        mc.thePlayer.movementInput.moveForward = f;
    }

    public static void setStrafe(float f) {
        mc.thePlayer.movementInput.moveStrafe = f;
    }

    public static MoveDirection direction(double forward, double strafe) {
        MoveDirection d = null;
        if (forward == 0.0) {
            if (strafe == 0.0) {
                d = MoveDirection.NONE;
            } else if (strafe > 0.0) {
                d = MoveDirection.LEFT;
            } else if (strafe < 0.0) {
                d = MoveDirection.RIGHT;
            }
        } else if (forward > 0.0) {
            if (strafe == 0.0) {
                d = MoveDirection.FORWARD;
            } else if (strafe > 0.0) {
                d = MoveDirection.FORWARD_LEFT;
            } else if (strafe < 0.0) {
                d = MoveDirection.FORWARD_RIGHT;
            }
        } else if (forward < 0.0) {
            if (strafe == 0.0) {
                d = MoveDirection.BACK;
            } else if (strafe > 0.0) {
                d = MoveDirection.BACK_LEFT;
            } else if (strafe < 0.0) {
                d = MoveDirection.BACK_RIGHT;
            }
        }
        return d;
    }

    public static MoveDirection direction() {
        return direction(forwardInput(), strafeInput());
    }

    public static MoveDirection direction(float yaw) {
        float moveYaw = RotationManager.movementYaw();
        if (yaw == moveYaw) {
            return direction();
        }
        double rad = Math.toRadians(yaw - moveYaw);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double forward = mc.thePlayer.movementInput.moveForward;
        double strafe = mc.thePlayer.movementInput.moveStrafe;
        double f = Math.round(forward * cos - strafe * sin);
        double s = Math.round(forward * sin + strafe * cos);
        return direction(f, s);
    }

    public static MoveDirection direction(MotionEvent e) {
        return direction(e.yaw);
    }

    public static boolean diagonalYaw() {
        float f = Math.abs(mc.thePlayer.rotationYaw % 90.0f);
        return f > 10.0f && f < 80.0f;
    }

    public static boolean isDiagonal() {
        return diagonalYaw() || direction().diagonal;
    }

    public static float movementAngle() {
        return mc.thePlayer.rotationYaw + (float) RotationUtils.inputAngle();
    }

    public static void jump() {
        mc.thePlayer.jump();
    }

    public static void jump(double speed) {
        MoveUtils.strafe(speed);
        jump();
    }

    public static void multiplyMotion(double d) {
        mc.thePlayer.motionX *= d;
        mc.thePlayer.motionZ *= d;
    }

    public static int potionLevel(Potion potion) {
        PotionEffect effect = mc.thePlayer.getActivePotionEffect(potion);
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    public static int speedLevel() {
        return potionLevel(Potion.moveSpeed);
    }

    public static double perSpeedLevel(double d) {
        return (double) speedLevel() * d;
    }

    public static boolean isAdventure() {
        return !mc.thePlayer.capabilities.allowEdit;
    }

    public static EnumFacing facing() {
        return mc.thePlayer.getHorizontalFacing();
    }

    public static MovingObjectPosition rayTraceFrom(double range, double x, double y, double z, float yaw, float pitch) {
        Vec3 from = eyes(x, y, z);
        Vec3 dir = RotationUtils.getVectorForRotation(yaw, pitch);
        Vec3 to = from.addVector(dir.xCoord * range, dir.yCoord * range, dir.zCoord * range);
        return mc.theWorld.rayTraceBlocks(from, to, false, false, false);
    }

    public static MovingObjectPosition rayTraceBlocks(double range, float yaw, float pitch) {
        return rayTraceFrom(range, mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, yaw, pitch);
    }

    public static MovingObjectPosition rayTraceBlock(BlockPos pos, EnumFacing facing, double range, float yaw, float pitch) {
        MovingObjectPosition hit = rayTraceBlocks(range, yaw, pitch);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }
        if (!BlockUtils.samePos(hit.getBlockPos(), pos) || facing != null && hit.sideHit != facing) {
            return null;
        }
        return hit;
    }
    }
    public static final class BlockUtils {
        private static final BlockPos.MutableBlockPos scratch=new BlockPos.MutableBlockPos();
    public static IBlockState getState(BlockPos pos) {
        return mc.theWorld.getBlockState(pos);
    }

    public static Block getBlock(BlockPos pos) {
        return mc.theWorld.getBlockState(pos).getBlock();
    }

    public static BlockPos pos(double x, double y, double z) {
        return new BlockPos(x, y, z);
    }

    public static Block getBlock(double x, double y, double z) {
        return getBlock(pos(x, y, z));
    }

    public static BlockPos below(double d) {
        return new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - d, mc.thePlayer.posZ);
    }

    public static boolean isReplaceable(BlockPos pos) {
        Block block = getBlock(pos);
        return block == Blocks.air || block == Blocks.fire || block instanceof BlockLiquid || block.getMaterial().isReplaceable();
    }

    public static boolean isPlaceable(Block block) {
        return block == Blocks.glass || block.isFullBlock() && block != Blocks.gravel && block != Blocks.sand && block != Blocks.soul_sand && block != Blocks.tnt && block != Blocks.crafting_table && block != Blocks.furnace && block != Blocks.dispenser && block != Blocks.dropper && block != Blocks.noteblock && block != Blocks.command_block;
    }

    public static boolean isContainer(Block block) {
        return block instanceof BlockContainer || block == Blocks.crafting_table || block == Blocks.anvil;
    }

    public static boolean isInteractable(Block block) {
        if (isContainer(block) || block == Blocks.bed || block == Blocks.lever) {
            return true;
        }
        if (block instanceof BlockTrapDoor || block instanceof BlockFence || block instanceof BlockFenceGate) {
            return true;
        }
        if (!(block instanceof BlockDoor)) {
            return false;
        }
        return block != Blocks.iron_door;
    }

    public static BlockPos placedPos(MovingObjectPosition hit) {
        return hit.getBlockPos().offset(hit.sideHit);
    }

    public static boolean samePos(BlockPos a, BlockPos b) {
        return a == b || a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ();
    }

    public static Vec3 hitVec(BlockPos pos, net.minecraft.util.EnumFacing facing) {
        double lo = 0.1, hi = 0.9, ylo = 0.05, yhi = 0.95, top = 1.0;
        double x = (double) pos.getX() + MathUtils.randomBetween(lo, hi);
        double y = (double) pos.getY() + MathUtils.randomBetween(ylo, yhi);
        double z = (double) pos.getZ() + MathUtils.randomBetween(lo, hi);
        IBlockState state = getState(pos);
        Block block = state.getBlock();
        if (!block.isFullBlock() && block instanceof net.minecraft.block.BlockSlab) {
            if (state.getValue(net.minecraft.block.BlockSlab.HALF) == net.minecraft.block.BlockSlab.EnumBlockHalf.TOP) {
                ylo = 0.505;
            } else if (facing == net.minecraft.util.EnumFacing.UP) {
                top = 0.5;
            } else {
                yhi = 0.495;
            }
            y = (double) pos.getY() + MathUtils.randomBetween(ylo, yhi);
        }
        switch (facing) {
            case EAST: x = pos.getX() + 1; break;
            case WEST: x = pos.getX(); break;
            case NORTH: z = pos.getZ(); break;
            case SOUTH: z = pos.getZ() + 1; break;
            case UP: y = (double) pos.getY() + top; break;
            case DOWN: y = pos.getY(); break;
        }
        return new Vec3(x, y, z);
    }
    }
    public static final class MoveUtils {
    public static float moveYaw(float yaw, float forward, float strafe) {
        float f4 = 1.0f;
        if (forward < 0.0f) {
            yaw += 180.0f;
            f4 = -0.5f;
        } else if (forward > 0.0f) {
            f4 = 0.5f;
        }
        if (strafe > 0.0f) {
            yaw -= 90.0f * f4;
        } else if (strafe < 0.0f) {
            yaw += 90.0f * f4;
        }
        return yaw;
    }

    public static float moveYaw(float yaw) {
        return moveYaw(yaw, mc.thePlayer.movementInput.moveForward, mc.thePlayer.movementInput.moveStrafe);
    }

    public static void setSpeed(double speed, float yaw) {
        if (speed == 0.0) {
            mc.thePlayer.motionZ = 0.0;
            mc.thePlayer.motionX = 0.0;
            return;
        }
        mc.thePlayer.motionX = -Math.sin(yaw *= (float) Math.PI / 180) * speed;
        mc.thePlayer.motionZ = Math.cos(yaw) * speed;
    }

    public static void setSpeed(double speed) {
        float yaw = mc.thePlayer.rotationYaw;
        if (mc.thePlayer.movementInput.moveForward < 0.0f) {
            yaw += 180.0f;
        }
        setSpeed(speed, yaw);
    }

    public static void strafe(double speed) {
        setSpeed(speed, moveYaw(RotationManager.movementYaw()));
    }

    public static double speed() {
        return Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
    }
    }
    public static final class RotationUtils {
        public static float lastYawStep,lastPitchStep;
        public static float[] currentRotation(){return new float[]{RotationManager.lastYaw,RotationManager.lastPitch};}
    public enum Precision implements DisplayNamed {
        Low(40.0f),
        Medium(20.0f),
        High(5.0f),
        Very_high(1.5f);

        public String displayName(){return label(name());}
        public final float offsetYaw;

        Precision(float offsetYaw) {
            this.offsetYaw = offsetYaw;
        }

        public float getOffsetYaw() {
            return this.offsetYaw;
        }

        public float getOffsetPitch(float pitch) {
            if (pitch > 75.0f) {
                return 1.0f;
            }
            if (pitch > 60.0f) {
                return 2.0f;
            }
            return 5.0f;
        }
    }

    public static float clampPitch(float pitch) {
        return MathHelper.clamp_float(pitch, -90.0f, 90.0f);
    }

    public static float[] fixSensitivity(float yaw, float pitch, float lastYaw, float lastPitch, boolean jitter) {
        int n;
        float f5 = yaw - lastYaw;
        float f6 = Math.abs(f5);
        float f7 = pitch - lastPitch;
        float f8 = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
        double d = (double) (f8 * f8 * f8) * 1.2;
        float f9 = lastYawStep = (float) ((double) Math.round((double) f5 / d) * d);
        float f10 = lastPitchStep = (float) ((double) Math.round((double) f7 / d) * d);
        yaw = lastYaw + f9;
        pitch = lastPitch + f10;
        return new float[]{yaw, clampPitch(pitch)};
    }

    public static float[] fixSensitivity(float yaw, float pitch, float lastYaw, float lastPitch) {
        return fixSensitivity(yaw, pitch, lastYaw, lastPitch, true);
    }

    public static float randomOffset() {
        return (float) (Math.random() - 0.5);
    }

    public static double inputAngle() {
        double d = Math.toDegrees(Math.atan2(PlayerUtils.strafeInput(), -PlayerUtils.forwardInput()));
        d = (d + 360.0) % 360.0;
        return d;
    }

    public static double inputAngleSigned() {
        double d = Math.toDegrees(Math.atan2(-PlayerUtils.strafeInput(), PlayerUtils.forwardInput()));
        return d == 0.0 ? 0.0 : d;
    }

    public static float[] fromDelta(double dx, double dy, double dz) {
        double d4 = Math.sqrt(dx * dx + dz * dz);
        float f = (float) (Math.atan2(dz, dx) * (double) 57.29578f) - 90.0f;
        float f2 = (float) (-(Math.atan2(dy, d4) * (double) 57.29578f));
        float[] current = currentRotation();
        f = current[0] + MathHelper.wrapAngleTo180_float(f - current[0]);
        f2 = clampPitch(current[1] + MathHelper.wrapAngleTo180_float(f2 - current[1]));
        return new float[]{f, f2};
    }

    public static Vec3 getVectorForRotation(float yaw, float pitch) {
        float f3 = MathHelper.cos((yaw *= -((float) Math.PI / 180)) - (float) Math.PI);
        float f4 = MathHelper.sin(yaw - (float) Math.PI);
        float f5 = -MathHelper.cos(pitch *= -((float) Math.PI / 180));
        float f6 = MathHelper.sin(pitch);
        return new Vec3(f4 * f5, f6, f3 * f5);
    }

    public static float[] searchBlockRotation(BlockPos pos, EnumFacing facing, float offset, Precision precision, float airYaw, boolean playerYaw) {
        float bestYaw = 0.0f;
        float bestPitch = 0.0f;
        float base = playerYaw ? mc.thePlayer.rotationYaw : (mc.thePlayer.onGround ? PlayerUtils.movementAngle() : airYaw);
        float from = base + offset;
        float to = base + 170.0f;
        float reach = mc.playerController.getBlockReachDistance();
        float best = 360.0f;
        for (float yaw = from; yaw <= to; yaw += precision.getOffsetYaw()) {
            float y = (float) ((double) yaw + (Math.random() - 0.5));
            for (float pitch = 90.0f; pitch > 30.0f; pitch -= precision.getOffsetPitch(pitch)) {
                float diff;
                BlockPos hitPos;
                float p = MathHelper.clamp_float((float) ((double) pitch + (Math.random() - 0.5)), -90.0f, 90.0f);
                MovingObjectPosition hit = WorldUtils.rayTraceBlocks(reach, y, p);
                if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || facing != null && hit.sideHit != facing || !BlockUtils.samePos(hitPos = hit.getBlockPos(), pos) || !awayFromEdge((float) (hit.hitVec.yCoord - (double) hitPos.getY())) || !awayFromEdge((float) (hit.hitVec.xCoord - (double) hitPos.getX())) || !awayFromEdge((float) (hit.hitVec.zCoord - (double) hitPos.getZ())) || !((diff = Math.abs(base - y)) < best)) continue;
                best = diff;
                bestYaw = y;
                bestPitch = p;
            }
        }
        if (best == 360.0f) {
            return null;
        }
        if (playerYaw && best > (float) 70) {
            return null;
        }
        return new float[]{bestYaw, bestPitch};
    }

    public static boolean awayFromEdge(float f) {
        return f == 0.0f || f == 1.0f || (double) f > 0.06 && (double) f < 0.94;
    }
    }
    public static final class WorldUtils {
    public static MovingObjectPosition rayTraceBlocks(double range, float yaw, float pitch) {
        Vec3 eyes = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        float f3 = MathHelper.cos((yaw *= -((float) Math.PI / 180)) - (float) Math.PI);
        float f4 = MathHelper.sin(yaw - (float) Math.PI);
        float f5 = -MathHelper.cos(pitch *= -((float) Math.PI / 180));
        float f6 = MathHelper.sin(pitch);
        Vec3 dir = new Vec3(f4 * f5, f6, f3 * f5);
        Vec3 end = eyes.addVector(dir.xCoord * range, dir.yCoord * range, dir.zCoord * range);
        return mc.theWorld.rayTraceBlocks(eyes, end, false, false, false);
    }

    public static double distanceSq(double x, double y, double z, Vec3 p) {
        double d4 = x - p.xCoord;
        double d5 = y - p.yCoord;
        double d6 = z - p.zCoord;
        return d4 * d4 + d5 * d5 + d6 * d6;
    }

    public static double behind(double x, double z, double dirX, double dirZ, Vec3 p) {
        double d5 = p.xCoord - x;
        double d6 = p.zCoord - z;
        return -(d5 * dirX + d6 * dirZ);
    }
    }
    public enum MoveDirection {
    NONE(false, false, false),
    FORWARD(true, false, false),
    FORWARD_LEFT(true, true, false),
    FORWARD_RIGHT(true, true, false),
    BACK(false, false, true),
    BACK_LEFT(false, true, true),
    BACK_RIGHT(false, true, true),
    LEFT(false, false, false),
    RIGHT(false, false, false);

    public final boolean forward;
    public final boolean diagonal;
    public final boolean backward;

    MoveDirection(boolean forward, boolean diagonal, boolean backward) {
        this.forward = forward;
        this.diagonal = diagonal;
        this.backward = backward;
    }
}

    public enum Theme implements DisplayNamed {
    DEFAULT("Default", true, false),
    RAINBOW("Rainbow", false, true),
    AURORA("Aurora", new java.awt.Color(120, 0, 200), new java.awt.Color(0, 255, 180)),
    CHERRY("Cherry", new java.awt.Color(255, 200, 200), new java.awt.Color(243, 58, 106)),
    COTTON_CANDY("Cotton candy", new java.awt.Color(99, 249, 255), new java.awt.Color(255, 104, 204)),
    FLARE("Flare", new java.awt.Color(241, 39, 17), new java.awt.Color(245, 175, 25)),
    FLOWER("Flower", new java.awt.Color(215, 166, 231), new java.awt.Color(211, 90, 232)),
    FOREST("Forest", new java.awt.Color(34, 139, 34), new java.awt.Color(154, 205, 50)),
    FROST("Frost", new java.awt.Color(253, 253, 253), new java.awt.Color(210, 225, 230)),
    GOLD("Gold", new java.awt.Color(255, 250, 0), new java.awt.Color(255, 253, 195)),
    GRAY("Grayscale", new java.awt.Color(240, 240, 240), new java.awt.Color(110, 110, 110)),
    INFERNO("Inferno", new java.awt.Color(255, 51, 0), new java.awt.Color(50, 0, 0)),
    ROYAL("Royal", new java.awt.Color(125, 204, 241), new java.awt.Color(30, 71, 170)),
    SANDSTORM("Sandstorm", new java.awt.Color(194, 178, 128), new java.awt.Color(255, 236, 179)),
    SKY("Sky", new java.awt.Color(140, 254, 250), new java.awt.Color(14, 210, 247)),
    VINE("Vine", new java.awt.Color(0, 255, 25), new java.awt.Color(180, 255, 180));

    public static SliderSetting timeMultiplier;

    public static Theme defaultTheme = FROST;

    public static final Theme[] SELECTABLE = Arrays.stream(values()).filter(t -> t != DEFAULT).toArray(Theme[]::new);
    public static final String[] NAMES = Arrays.stream(values()).map(t -> t.name).toArray(String[]::new);
    public static final String[] SELECTABLE_NAMES = Arrays.stream(SELECTABLE).map(t -> t.name).toArray(String[]::new);
    public static final Map<String, Theme> BY_NAME = new LinkedHashMap<>();

    static {
        for (Theme t : values()) BY_NAME.put(t.name, t);
    }

    public String displayName(){return name;}
    public final String name;
    public final boolean followsDefault;
    public final boolean rainbow;
    public final java.awt.Color first;
    public final java.awt.Color second;
    private final int[] rgbArr;

    Theme(String name, boolean followsDefault, boolean rainbow) {
        this.name = name;
        this.followsDefault = followsDefault;
        this.rainbow = rainbow;
        this.first = null;
        this.second = null;
        this.rgbArr = null;
    }

    Theme(String name, java.awt.Color first, java.awt.Color second) {
        this.name = name;
        this.followsDefault = false;
        this.rainbow = false;
        this.first = first;
        this.second = second;
        this.rgbArr = new int[]{first.getRGB(), second.getRGB()};
    }

    public int getRGB(double d) {
        if (this.followsDefault) return defaultTheme.getRGB(d);
        return gradient(this.rainbow, this.first, this.second, d);
    }

    public int[] getRGBArr() {
        if (this.followsDefault) return defaultTheme.getRGBArr();
        if (this.rainbow) {
            int n = RenderUtils.rainbow(2L);
            return new int[]{n, n};
        }
        return this.rgbArr;
    }

    public static int gradient(boolean rainbow, java.awt.Color color, java.awt.Color color2, double d) {
        double mult = timeMultiplier == null ? 0.5 : timeMultiplier.getInput();
        if (rainbow) {
            long l = (long) Math.max(2.0 * mult, 1.0);
            return RenderUtils.rainbow(l, (long) d);
        }
        double d3 = (double) System.currentTimeMillis() / 1.0E8 * mult * 400000.0;
        double d4 = (Math.sin(d3 + d * (double) 0.55f) + 1.0) * 0.5;
        return RenderUtils.blend(color, color2, d4);
    }
}

    public static final class RenderUtils {
        public static int withAlpha(int c,int a){return(c&0xffffff)|(clampAlpha(a)<<24);}
        public static int clampAlpha(int a){return Math.max(0,Math.min(255,a));}
        public static int rainbow(long speed){return rainbow(speed,0);}
        public static int rainbow(long speed,long offset){return java.awt.Color.HSBtoRGB(((System.currentTimeMillis()+offset)/(float)(speed*1000))%1, .8F,1);}
        public static int blend(java.awt.Color a,java.awt.Color b,double d){return new java.awt.Color((int)(a.getRed()*(1-d)+b.getRed()*d),(int)(a.getGreen()*(1-d)+b.getGreen()*d),(int)(a.getBlue()*(1-d)+b.getBlue()*d)).getRGB();}
    }
    public static final class Render3D {
        public static void block(BlockPos p,int color,boolean outline,boolean shade){
            var rm=mc.getRenderManager();
            boolean blend=org.lwjgl.opengl.GL11.glIsEnabled(3042),texture=org.lwjgl.opengl.GL11.glIsEnabled(3553),depth=org.lwjgl.opengl.GL11.glIsEnabled(2929),write=org.lwjgl.opengl.GL11.glGetBoolean(2930);
            int src=org.lwjgl.opengl.GL11.glGetInteger(32969),dst=org.lwjgl.opengl.GL11.glGetInteger(32968),sa=org.lwjgl.opengl.GL11.glGetInteger(32971),da=org.lwjgl.opengl.GL11.glGetInteger(32970);
            float line=org.lwjgl.opengl.GL11.glGetFloat(2849);
            net.minecraft.client.renderer.GlStateManager.pushMatrix();
            try{
            net.minecraft.client.renderer.GlStateManager.enableBlend();
            net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            net.minecraft.client.renderer.GlStateManager.disableTexture2D();
            net.minecraft.client.renderer.GlStateManager.disableDepth();
            net.minecraft.client.renderer.GlStateManager.depthMask(false);
            float r=(color>>16&255)/255F,g=(color>>8&255)/255F,b=(color&255)/255F,a=(color>>>24)/255F;
            double x=p.getX()-rm.viewerPosX,y=p.getY()-rm.viewerPosY,z=p.getZ()-rm.viewerPosZ;
            var t=net.minecraft.client.renderer.Tessellator.getInstance();var w=t.getWorldRenderer();
            double[][] v={{x,y,z},{x+1,y,z},{x+1,y+1,z},{x,y+1,z},{x,y,z+1},{x+1,y,z+1},{x+1,y+1,z+1},{x,y+1,z+1}};
            if(outline){
                org.lwjgl.opengl.GL11.glLineWidth(2);
                int[] edges={0,1,1,2,2,3,3,0,4,5,5,6,6,7,7,4,0,4,1,5,2,6,3,7};
                w.begin(1,net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
                for(int i:edges)w.pos(v[i][0],v[i][1],v[i][2]).color(r,g,b,a).endVertex();t.draw();
            }
            if(shade){
                int[] faces={0,1,2,3,5,4,7,6,4,0,3,7,1,5,6,2,3,2,6,7,4,5,1,0};
                w.begin(7,net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
                for(int i:faces)w.pos(v[i][0],v[i][1],v[i][2]).color(r,g,b,a/3).endVertex();t.draw();
            }
            }finally{
            net.minecraft.client.renderer.GlStateManager.depthMask(write);
            if(depth)net.minecraft.client.renderer.GlStateManager.enableDepth();else net.minecraft.client.renderer.GlStateManager.disableDepth();
            if(texture)net.minecraft.client.renderer.GlStateManager.enableTexture2D();else net.minecraft.client.renderer.GlStateManager.disableTexture2D();
            if(blend)net.minecraft.client.renderer.GlStateManager.enableBlend();else net.minecraft.client.renderer.GlStateManager.disableBlend();
            net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(src,dst,sa,da);org.lwjgl.opengl.GL11.glLineWidth(line);
            net.minecraft.client.renderer.GlStateManager.popMatrix();
            }
        }
    }
}
