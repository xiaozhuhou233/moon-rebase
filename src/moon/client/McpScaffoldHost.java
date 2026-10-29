package moon.client;
import moon.module.combat.killaura.KillAuraRotations;
import moon.module.player.scaffoldwalk.ScaffoldSettings;

import moon.module.player.ScaffoldWalkModule.*;
import moon.module.player.scaffoldwalk.ScaffoldSettings.Rotation;
import moon.module.combat.killaura.KillAuraRotations.Angles;
import net.minecraft.client.Minecraft;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.util.*;

/** Vanilla MCP boundary for jE/fh/ff/fd. No old bootstrap classes are executed. */
final class McpScaffoldHost implements moon.module.player.ScaffoldWalkModule.Host {
    private final Minecraft mc;
    McpScaffoldHost(Minecraft mc){this.mc=mc;}
    public boolean available(){return mc.thePlayer!=null&&mc.theWorld!=null&&mc.thePlayer.isEntityAlive()&&mc.currentScreen==null;}
    public double feetY(){return mc.thePlayer.getEntityBoundingBox().minY;}
    public boolean grounded(){return mc.thePlayer.onGround;}
    public boolean jumpHeld(){return mc.gameSettings.keyBindJump.isKeyDown();}
    public boolean movementKeyHeld(){return mc.gameSettings.keyBindForward.isKeyDown()||mc.gameSettings.keyBindBack.isKeyDown()
        ||mc.gameSettings.keyBindLeft.isKeyDown()||mc.gameSettings.keyBindRight.isKeyDown();}
    public double positionY(){return mc.thePlayer.posY;}
    public void verticalMotion(double value){mc.thePlayer.motionY=value;}
    public boolean connected(){return mc.thePlayer!=null&&mc.theWorld!=null&&mc.thePlayer.sendQueue!=null;}
    public Object worldIdentity(){return mc.theWorld;}
    public Object connectionIdentity(){return mc.thePlayer==null?null:mc.thePlayer.sendQueue;}
    public boolean sprinting(){return mc.thePlayer.isSprinting();}
    public int speedAmplifier(){var effect=mc.thePlayer.getActivePotionEffect(net.minecraft.potion.Potion.moveSpeed);return effect==null?-1:effect.getAmplifier();}
    public float forward(){return mc.thePlayer.moveForward;}
    public float strafe(){return mc.thePlayer.moveStrafing;}
    public double positionX(){return mc.thePlayer.posX;}
    public double positionZ(){return mc.thePlayer.posZ;}
    public double previousX(){return mc.thePlayer.prevPosX;}
    public double previousZ(){return mc.thePlayer.prevPosZ;}
    public void horizontalMotion(double x,double z){mc.thePlayer.motionX=x;mc.thePlayer.motionZ=z;}
    public void releaseSprintKey(){net.minecraft.client.settings.KeyBinding.setKeyBindStateUnchecked(mc.gameSettings.keyBindSprint.getKeyCode(),false);}
    public void sendSprintPacket(SprintPacket packet){mc.thePlayer.sendQueue.getNetworkManager().sendPacket(((SprintPacketView)packet).packet());}
    record SprintPacketView(net.minecraft.network.Packet<?> packet) implements SprintPacket {
        public SprintPacketKind kind(){
            if(packet instanceof net.minecraft.network.play.client.C08PacketPlayerBlockPlacement)return SprintPacketKind.PLACE;
            if(packet instanceof net.minecraft.network.play.client.C0APacketAnimation)return SprintPacketKind.ANIMATION;
            if(packet instanceof net.minecraft.network.play.client.C03PacketPlayer)return SprintPacketKind.MOVEMENT;
            if(packet instanceof net.minecraft.network.play.server.S08PacketPlayerPosLook)return SprintPacketKind.CORRECTION;
            return SprintPacketKind.OTHER;
        }
        public boolean moving(){return packet instanceof net.minecraft.network.play.client.C03PacketPlayer p&&p.isMoving();}
        public boolean excluded(){return packet instanceof net.minecraft.network.play.client.C03PacketPlayer p&&p.moonScaffoldExcluded();}
        public double x(){return ((net.minecraft.network.play.client.C03PacketPlayer)packet).getPositionX();}
        public double z(){return ((net.minecraft.network.play.client.C03PacketPlayer)packet).getPositionZ();}
        public void horizontalPosition(double x,double z){((net.minecraft.network.play.client.C03PacketPlayer)packet).moonHorizontalPosition(x,z);}
    }
    public Angles camera(){return new Angles(mc.thePlayer.rotationYaw,mc.thePlayer.rotationPitch);}
    public int selectedSlot(){return mc.thePlayer==null?-1:mc.thePlayer.inventory.currentItem;}
    public void selectSlot(int slot){if(mc.thePlayer!=null&&slot>=0&&slot<9){mc.thePlayer.inventory.currentItem=slot;mc.playerController.updateController();}}
    static boolean buildingBlock(ItemStack stack){
        if(stack==null||stack.stackSize<=0||!(stack.getItem() instanceof ItemBlock item))return false;
        Block block=item.getBlock();
        return block.getMaterial().isSolid()&&block.isFullCube()&&!(block instanceof BlockFalling)&&!(block instanceof BlockContainer);
    }
    public int blockSlot(boolean allowSwitch){
        int current=selectedSlot();if(current<0)return -1;
        if(buildingBlock(mc.thePlayer.inventory.getStackInSlot(current)))return current;
        if(allowSwitch)for(int i=0;i<9;i++)if(buildingBlock(mc.thePlayer.inventory.getStackInSlot(i)))return i;
        return -1;
    }
    private boolean replaceable(BlockPos p){return mc.theWorld.getBlockState(p).getBlock().isReplaceable(mc.theWorld,p);}
    // The destination's ENUM_FACING_FIX returns null for diagonal vectors.
    // Scaffold needs vanilla's greatest-dot-product rule, including enum-order ties.
    static EnumFacing supportFace(int x,int y,int z){
        EnumFacing best=EnumFacing.NORTH;float maximum=Float.MIN_VALUE;
        for(EnumFacing face:EnumFacing.values()){
            float dot=(float)x*face.getFrontOffsetX()+(float)y*face.getFrontOffsetY()+(float)z*face.getFrontOffsetZ();
            if(dot>maximum){maximum=dot;best=face;}
        }
        return best;
    }
    public Placement find(double level,double radius){
        // Destination MathHelper floors through float; original DS uses double floor.
        BlockPos below=new BlockPos((int)Math.floor(mc.thePlayer.posX),(int)Math.floor(level-.5),(int)Math.floor(mc.thePlayer.posZ));
        if(!replaceable(below))return null;
        BlockPos best=null;double distance=2147483647.0;
        Vec3 position=mc.thePlayer.getPositionVector();
        // fh's 243 offsets: X/Z -4..4, Y 0,-1,-2, in X/Y/Z order.
        // Select first on ties; compute the face only after choosing the nearest support.
        for(int x=-4;x<=4;x++)for(int y=0;y>-3;y--)for(int z=-4;z<=4;z++){
            BlockPos support=below.add(x,y,z);Block block=mc.theWorld.getBlockState(support).getBlock();
            if(block instanceof BlockContainer||block instanceof BlockFalling||!block.getMaterial().blocksMovement())continue;
            // Preserve I2F/FADD/F2D centers and original (float)Math.sqrt distance.
            // Destination Vec3 delegates to an approximate sqrt, so keep this local.
            double dx=(double)((float)support.getX()+.5F)-position.xCoord;
            double dy=(double)((float)support.getY()+.5F)-position.yCoord;
            double dz=(double)((float)support.getZ()+.5F)-position.zCoord;
            double d=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(d<=radius&&d<distance){best=support;distance=d;}
        }
        if(best==null)return null;
        EnumFacing face=supportFace(below.getX()-best.getX(),below.getY()-best.getY(),below.getZ()-best.getZ());
        Vec3 hit=new Vec3(best).addVector(.5+face.getFrontOffsetX()*.5,.5+face.getFrontOffsetY()*.5,.5+face.getFrontOffsetZ()*.5);
        return new Placement(new Position(best.getX(),best.getY(),best.getZ()),face.getIndex(),hit.xCoord,hit.yCoord,hit.zCoord);
    }
    private BlockPos block(Placement p){return new BlockPos(p.support().x(),p.support().y(),p.support().z());}
    private MovingObjectPosition ray(Angles a){
        Vec3 eye=mc.thePlayer.getPositionEyes(1);double yaw=Math.toRadians(a.yaw()),pitch=Math.toRadians(a.pitch()),reach=mc.playerController.getBlockReachDistance();
        return mc.theWorld.rayTraceBlocks(eye,eye.addVector(-Math.sin(yaw)*Math.cos(pitch)*reach,-Math.sin(pitch)*reach,Math.cos(yaw)*Math.cos(pitch)*reach),false,true,false);
    }
    private boolean matches(MovingObjectPosition hit,Placement p){return hit!=null&&hit.typeOfHit==MovingObjectPosition.MovingObjectType.BLOCK&&hit.getBlockPos().equals(block(p))&&hit.sideHit.getIndex()==p.face();}
    public Angles aim(Placement p,Angles current,Rotation mode){
        Vec3 eye=mc.thePlayer.getPositionEyes(1);double dx=p.x()-eye.xCoord,dy=p.y()-eye.yCoord,dz=p.z()-eye.zCoord;
        Angles target=new Angles((float)Math.toDegrees(Math.atan2(dz,dx))-90,(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));
        if(mode==Rotation.BRUTEFORCE){
            // ff's golden-angle spiral, sqrt(n) radius, first exact block-and-face hit.
            for(int n=0;n<8100;n++){double radius=Math.sqrt(n),phase=1.61803398875*Math.PI*n;
                Angles candidate=new Angles(current.yaw()+(float)(Math.sin(phase)*radius),Math.max(-90,Math.min(90,current.pitch()+(float)(Math.cos(phase)*radius))));
                if(matches(ray(candidate),p))return candidate;
            }
        }else if(mode==Rotation.BACKWARDS)return new Angles(mc.thePlayer.rotationYaw-180,target.pitch());
        else if(mode==Rotation.STATIC){
            float forward=mc.thePlayer.movementInput.moveForward,strafe=mc.thePlayer.movementInput.moveStrafe;
            float yaw=mc.thePlayer.rotationYaw+(forward<0?180:0);
            if(strafe!=0)yaw+=(strafe>0?-90:90)*(forward==0?1:forward>0?.5F:-.5F);
            return new Angles(yaw-180,target.pitch());
        }
        return target;
    }
    public boolean place(Placement p,Angles angles,int slot,boolean silent,boolean realClick,boolean checkBlock,boolean swing){
        if(!available()||slot<0||!buildingBlock(mc.thePlayer.inventory.getStackInSlot(slot))||!replaceable(block(p).offset(EnumFacing.getFront(p.face()))))return false;
        MovingObjectPosition hit=ray(angles);
        if(realClick&&(!matches(hit,p)&&checkBlock||hit==null||hit.typeOfHit!=MovingObjectPosition.MovingObjectType.BLOCK))return false;
        if(!realClick)hit=new MovingObjectPosition(new Vec3(p.x(),p.y(),p.z()),EnumFacing.getFront(p.face()),block(p));
        final int before=selectedSlot();
        try{
            selectSlot(slot);
            ItemStack stack=mc.thePlayer.inventory.getStackInSlot(slot);
            boolean placed=realClick?mc.moonRightClickBlock(hit):mc.playerController.onPlayerRightClick(mc.thePlayer,mc.theWorld,stack,hit.getBlockPos(),hit.sideHit,hit.hitVec);
            if(placed&&!realClick&&swing)mc.thePlayer.swingItem();
            if(stack.stackSize<=0)mc.thePlayer.inventory.setInventorySlotContents(slot,null);
            return placed;
        }finally{if(silent)selectSlot(before);}
    }
    public void stopSprint(){mc.thePlayer.setSprinting(false);}
    public void limitSpeed(double maximum){double speed=Math.hypot(mc.thePlayer.motionX,mc.thePlayer.motionZ);if(speed>maximum){mc.thePlayer.motionX*=maximum/speed;mc.thePlayer.motionZ*=maximum/speed;}}
}
