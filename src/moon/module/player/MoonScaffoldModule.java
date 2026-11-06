package moon.module.player;

import java.util.*;
import moon.module.NativeModule;
import moon.module.combat.killaura.KillAuraRotations;
import moon.module.combat.killaura.KillAuraRotations.Angles;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.*;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import org.lwjglx.input.Keyboard;

/** Native Nightly lK, gA/gC/gD/gy/gE bridge. No Lua or Myau scaffold dependency. */
public final class MoonScaffoldModule extends NativeModule {
    public enum Rotation implements DisplayNamed { CALCULATED, BRUTEFORCE, BACKWARDS, STATIC, ECONOMIC; public String displayName(){return name();} }
    public enum Edge implements DisplayNamed { SIMPLE, BOX; public String displayName(){return name();} }
    public enum Sprint implements DisplayNamed { NORMAL, HYPIXEL; public String displayName(){return name();} }
    public enum SafeWalk implements DisplayNamed { NONE, NORMAL, JUMP; public String displayName(){return name();} }
    public enum Tower implements DisplayNamed { HYPIXEL; public String displayName(){return name();} }
    public enum Sneak implements DisplayNamed { EDGE, DISTANCE, DELAY; public String displayName(){return name();} }
    public final EnumSetting<Rotation> rotation;
    public final EnumSetting<Edge> edge;
    public final EnumSetting<Sprint> sprintMode;
    public final EnumSetting<SafeWalk> safeWalkMode;
    public final EnumSetting<Tower> towerMode;
    public final MultiChoiceSetting<Sneak> sneak;
    public final BooleanSetting switchBlock,silent,tower,sprint,slowSprint,unsprint,keepY,breezly,correctMovement,limitSpeed,
        swing,raytrace,rotate,backOnStart,keepRotation,onlyRequired,middlePoint,sensitivity;
    public final FloatSetting edgeBox,radius,speedLimit,sneakDistance;
    public final IntegerSetting breezlyDelay,minSneak,maxSneak,sneakDelay,missChance,doubleChance,rotationSpeed;
    private final Minecraft mc;
    private final List<SettingValue<?>> settings;
    private static final List<float[]> SPIRAL=spiral();
    private final Random random=new Random();
    private int originalSlot=-1,selectedSlot=-1,plannedTick=-1,sneakTicks,breezlyTicks,sprintStep,correctionTicks;
    private boolean placed,breezlyLeft,draining,towerPlaced;
    private double level,distance,towerFloor;
    private float yaw,pitch,desiredYaw,desiredPitch,towerYaw;
    private BlockPos support;
    private EnumFacing face;
    private long placements;
    private Object world,connection;
    private final List<Packet<?>> sprintPackets=new ArrayList<>();

    public MoonScaffoldModule(Minecraft mc,MarkerHooks h){
        super("scaffoldwalk","ScaffoldWalk",Category.PLAYER);this.mc=mc;
        edge=EnumSetting.builder("edge-detection",Edge.class,h).configure("Edge Detection",Edge.SIMPLE).build();
        edgeBox=FloatSetting.builder("edge-box-size",h).configure("Edge Box Size",.25F,.05F,.3F,.025F).visibility(()->edge.get()==Edge.BOX).build();
        radius=f(h,"search-radius","Search Radius",3.5F,1,6,.5F);
        switchBlock=b(h,"switch-to-block","Switch To Block",true);silent=b(h,"switch-silently","Switch Silently",false);
        tower=b(h,"tower","Tower",false);towerMode=EnumSetting.builder("tower-mode",Tower.class,h).configure("Tower Mode",Tower.HYPIXEL).visibility(tower::get).build();
        sprint=b(h,"allow-sprinting","Allow Sprinting",false);sprintMode=EnumSetting.builder("sprint-mode",Sprint.class,h).configure("Sprint Mode",Sprint.NORMAL).visibility(sprint::get).build();
        slowSprint=BooleanSetting.builder("hypixel-slow-sprint",h).configure("Hypixel Slow Sprint",true).visibility(()->sprint.get()&&sprintMode.get()==Sprint.HYPIXEL).build();
        unsprint=b(h,"un-sprint-on-place","Unsprint On Place",false);keepY=b(h,"keep-y","Keep Y",false);
        breezly=b(h,"breezly-pattern","Breezly Pattern",false);breezlyDelay=IntegerSetting.builder("breezlyDelay",h).configure("Breezly Delay",2,1,5,1).visibility(breezly::get).build();
        safeWalkMode=EnumSetting.builder("safe-walk-mode",SafeWalk.class,h).configure("Safe Walk Mode",SafeWalk.NORMAL).build();
        correctMovement=b(h,"correct-movement","Correct Movement",true);limitSpeed=b(h,"limit-movement-speed","Limit Movement Speed",false);
        speedLimit=FloatSetting.builder("speed-limit",h).configure("Speed Limit",.2F,.01F,.5F,.001F).visibility(limitSpeed::get).build();
        sneak=MultiChoiceSetting.builder("sneak-features",Sneak.class,h).configure("Sneak Features",Sneak.EDGE).build();
        minSneak=i(h,"min-sneak-duration","Min Sneak Duration",1,1,20);maxSneak=i(h,"max-sneak-duration","Max Sneak Duration",1,1,20);
        sneakDelay=i(h,"sneak-activation-delay","Sneak Activation Delay",1,1,20);sneakDistance=f(h,"sneak-activation-distance","Sneak Activation Distance",1.5F,.075F,10,.075F);
        swing=b(h,"swing-after-place","Swing After Place",false);raytrace=b(h,"raytrace","Raytrace",true);
        missChance=i(h,"miss-click-chance","Miss Click Chance",0,0,100);doubleChance=i(h,"double-click-chance","Double Click Chance",10,1,100);
        rotate=b(h,"rotate","Rotate",true);backOnStart=b(h,"back-on-start","Back On Start",true);keepRotation=b(h,"keep-rotation","Keep Rotation",true);
        onlyRequired=b(h,"only-rotate-if-required","Only Rotate If Required",false);middlePoint=b(h,"middle-point","Middle Point",false);sensitivity=b(h,"use-sensitivity","Use Sensitivity",false);
        rotationSpeed=i(h,"rotation-speed","Rotation Speed",180,1,180);
        rotation=EnumSetting.builder("rotation-mode",Rotation.class,h).configure("Rotation Mode",Rotation.BRUTEFORCE).build();
        settings=List.of(group(h,"Placement",edge,edgeBox,radius,switchBlock,silent,swing,raytrace,missChance,doubleChance),
            group(h,"Movement",sprint,sprintMode,slowSprint,unsprint,keepY,breezly,breezlyDelay,safeWalkMode,correctMovement,limitSpeed,speedLimit),
            group(h,"Tower",tower,towerMode),group(h,"Sneak",sneak,minSneak,maxSneak,sneakDelay,sneakDistance),
            group(h,"Rotations",rotate,backOnStart,keepRotation,onlyRequired,middlePoint,sensitivity,rotation,rotationSpeed));
    }
    private static BooleanSetting b(MarkerHooks h,String key,String name,boolean value){return BooleanSetting.builder(key,h).configure(name,value).build();}
    private static FloatSetting f(MarkerHooks h,String key,String name,float value,float min,float max,float step){return FloatSetting.builder(key,h).configure(name,value,min,max,step).build();}
    private static IntegerSetting i(MarkerHooks h,String key,String name,int value,int min,int max){return IntegerSetting.builder(key,h).configure(name,value,min,max,1).build();}
    private static SettingGroup group(MarkerHooks h,String title,SettingValue<?>... values){return SettingGroup.builder(h).configure(title,values).build();}
    public List<? extends SettingValue<?>> settings(){return settings;}
    public String hudSuffix(){return rotation.get().displayName();}
    public long placements(){return placements;}
    public Angles angles(){return new Angles(yaw,pitch);}
    public BlockPos support(){return support;}
    public EnumFacing face(){return face;}
    public boolean active(){return enabled()&&mc.thePlayer!=null&&mc.theWorld!=null;}
    public boolean blocksCombat(){return active();}
    public boolean controlsSprint(){return active();}
    public boolean safeWalk(){return active()&&mc.thePlayer.onGround&&safeWalkMode.get()==SafeWalk.NORMAL;}
    public boolean correctingMovement(){return active()&&correctMovement.get()&&rotate.get()&&(placed||keepRotation.get());}
    public int renderSlot(int vanilla){return active()&&silent.get()&&originalSlot>=0?originalSlot:vanilla;}
    protected void onEnable(){reset();begin();}
    protected void onDisable(){flush();restore();reset();}
    public void worldChanged(){restore();reset();}
    private void reset(){support=null;face=null;originalSlot=selectedSlot=plannedTick=-1;placed=towerPlaced=breezlyLeft=false;sneakTicks=breezlyTicks=sprintStep=correctionTicks=0;distance=0;sprintPackets.clear();world=null;connection=null;}
    private void begin(){if(mc.thePlayer==null||mc.theWorld==null)return;var p=mc.thePlayer;world=mc.theWorld;connection=mc.getNetHandler();originalSlot=p.inventory.currentItem;level=p.getEntityBoundingBox().minY;yaw=desiredYaw=p.rotationYaw-(backOnStart.get()?180:0);pitch=desiredPitch=backOnStart.get()?84:p.rotationPitch;}
    private void restore(){
        if(mc.thePlayer!=null&&world==mc.theWorld&&connection==mc.getNetHandler()&&originalSlot>=0&&selectedSlot>=0&&mc.thePlayer.inventory.currentItem==selectedSlot){mc.thePlayer.inventory.currentItem=originalSlot;mc.playerController.moonSyncCurrentPlayItem();}
        restoreKey(mc.gameSettings.keyBindLeft);restoreKey(mc.gameSettings.keyBindRight);restoreKey(mc.gameSettings.keyBindSneak);
    }
    private void restoreKey(KeyBinding key){KeyBinding.setKeyBindState(key.getKeyCode(),mc.currentScreen==null&&physical(key));}
    private boolean physical(KeyBinding key){int code=key.getKeyCode();return code>0&&Keyboard.isKeyDown(code);}
    private boolean moving(){return mc.thePlayer.moveForward!=0||mc.thePlayer.moveStrafing!=0;}
    private boolean towerActive(){return tower.get()&&mc.gameSettings.keyBindJump.isKeyDown()&&!(keepY.get()&&moving());}
    private void stopSprint(){if(mc.thePlayer.isSprinting())mc.thePlayer.setSprinting(false);}
    private void selectBlock(){
        var p=mc.thePlayer;if(selectedSlot>=0&&p.inventory.currentItem!=selectedSlot)originalSlot=p.inventory.currentItem;
        int slot=p.inventory.currentItem;
        if(switchBlock.get()&&!valid(p.inventory.getStackInSlot(slot))){slot=-1;int count=0;for(int j=0;j<9;j++){ItemStack stack=p.inventory.getStackInSlot(j);if(valid(stack)&&stack.stackSize>count){slot=j;count=stack.stackSize;}}}
        if(slot>=0&&valid(p.inventory.getStackInSlot(slot))){p.inventory.currentItem=slot;selectedSlot=slot;mc.playerController.moonSyncCurrentPlayItem();}else selectedSlot=-1;
    }
    private boolean valid(ItemStack item){if(item==null||item.stackSize<=0||!(item.getItem() instanceof ItemBlock ib))return false;Block block=ib.getBlock();return block.getMaterial().blocksMovement()&&!(block instanceof BlockContainer)&&!(block instanceof BlockFalling);}
    private BlockPos below(){return new BlockPos(mc.thePlayer.posX,level-.5,mc.thePlayer.posZ);}
    private boolean supported(){
        var p=mc.thePlayer;double fall=-.1;AxisAlignedBB box=new AxisAlignedBB(p.posX,p.posY,p.posZ,p.posX,p.posY,p.posZ).expand(edgeBox.get(),0,edgeBox.get());
        for(AxisAlignedBB hit:mc.theWorld.getCollidingBoundingBoxes(p,box.addCoord(0,fall,0)))fall=hit.calculateYOffset(box,fall);
        return fall==0;
    }
    private boolean simpleEdge(){BlockPos at=below();return mc.theWorld.getBlockState(at).getBlock().isReplaceable(mc.theWorld,at);}
    private boolean shouldSearch(){return simpleEdge()||!supported();}
    private boolean needsRotation(){return edge.get()==Edge.SIMPLE?simpleEdge():!supported();}
    private void search(){
        BlockPos origin=below(),best=null;double nearest=2147483647D;
        for(int x=-5;x<=5;x++)for(int y=0;y>-3;y--)for(int z=-5;z<=5;z++){
            BlockPos at=origin.add(x,y,z);Block block=mc.theWorld.getBlockState(at).getBlock();
            if(block instanceof BlockContainer||block instanceof BlockFalling||!block.getMaterial().blocksMovement())continue;
            Vec3 center=new Vec3((float)at.getX()+.5F,(float)at.getY()+.5F,(float)at.getZ()+.5F);
            double d=mc.thePlayer.getPositionVector().distanceTo(center);if(d<=radius.get()&&d<nearest){nearest=d;best=at;}
        }
        support=best;face=best==null?null:EnumFacing.getFacingFromVector(origin.getX()-best.getX(),origin.getY()-best.getY(),origin.getZ()-best.getZ());
    }
    public void tick(){
        if(!active())return;if(world!=mc.theWorld||connection!=mc.getNetHandler()){reset();begin();}if(originalSlot<0)begin();
        var p=mc.thePlayer;if(plannedTick==p.ticksExisted)return;plannedTick=p.ticksExisted;selectBlock();
        if(p.onGround||(tower.get()&&mc.gameSettings.keyBindJump.isKeyDown()&&!moving())||!(keepY.get()&&moving()))level=Math.floor(p.getEntityBoundingBox().minY);
        if(shouldSearch()){search();if(needsRotation()&&support!=null)solve();}else{support=null;face=null;}
        float oldYaw=yaw,oldPitch=pitch;yaw=KillAuraRotations.approach(yaw,desiredYaw,rotationSpeed.get());pitch=KillAuraRotations.approach(pitch,desiredPitch,rotationSpeed.get());
        if(sensitivity.get()){float step=(float)(Math.pow(mc.gameSettings.mouseSensitivity*.6F+.2F,3)*8*.15);yaw=oldYaw+Math.round((yaw-oldYaw)/step)*step;pitch=oldPitch+Math.round((pitch-oldPitch)/step)*step;}
        pitch=MathHelper.clamp_float(pitch,-90,90);
    }
    private MovingObjectPosition ray(float y,float p){
        Vec3 eye=mc.thePlayer.getPositionEyes(1);double yr=Math.toRadians(y),pr=Math.toRadians(p);
        return mc.theWorld.rayTraceBlocks(eye,eye.addVector(-Math.sin(yr)*Math.cos(pr)*4.5,-Math.sin(pr)*4.5,Math.cos(yr)*Math.cos(pr)*4.5),false,false,true);
    }
    private boolean hits(float y,float p){MovingObjectPosition hit=ray(y,p);return hit!=null&&hit.typeOfHit==MovingObjectPosition.MovingObjectType.BLOCK&&support.equals(hit.getBlockPos())&&face==hit.sideHit;}
    private void solve(){
        var p=mc.thePlayer;Vec3 eye=p.getPositionEyes(1);Vec3 point=new Vec3(support).addVector(.5+face.getFrontOffsetX()*.5,.5+face.getFrontOffsetY()*.5,.5+face.getFrontOffsetZ()*.5);
        if(middlePoint.get()){if(face.getAxis()==EnumFacing.Axis.X&&Math.abs(point.zCoord-eye.zCoord)<=.5)eye=new Vec3(eye.xCoord,eye.yCoord,point.zCoord);if(face.getAxis()==EnumFacing.Axis.Z&&Math.abs(point.xCoord-eye.xCoord)<=.5)eye=new Vec3(point.xCoord,eye.yCoord,eye.zCoord);}
        double dx=(float)point.xCoord-(float)eye.xCoord,dy=(float)point.yCoord-(float)eye.yCoord,dz=(float)point.zCoord-(float)eye.zCoord;
        desiredYaw=(float)Math.toDegrees(Math.atan2(dz,dx))-90;desiredPitch=-(float)Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)));
        switch(rotation.get()){
            case CALCULATED -> { }
            case BRUTEFORCE -> {for(float[] offset:SPIRAL){float y=yaw+offset[0],v=MathHelper.clamp_float(pitch+offset[1],-90,90);if(hits(y,v)){desiredYaw=y;desiredPitch=v;break;}}}
            case BACKWARDS, STATIC -> {
                desiredYaw=p.rotationYaw-180;
                if(rotation.get()==Rotation.STATIC){float forward=(mc.gameSettings.keyBindForward.isKeyDown()?1:0)-(mc.gameSettings.keyBindBack.isKeyDown()?1:0),side=(mc.gameSettings.keyBindLeft.isKeyDown()?1:0)-(mc.gameSettings.keyBindRight.isKeyDown()?1:0);float requested=p.rotationYaw-180*(forward==0?1:Math.max(forward,0))+90*side/(forward!=0?2*forward:1);desiredYaw=p.rotationYaw+MathHelper.wrapAngleTo180_float(p.rotationYaw%360-requested);}
                for(float v=65;v<=84;v=(float)(v+.005)){if(hits(desiredYaw,v)){desiredPitch=v;break;}}
            }
            case ECONOMIC -> {
                float best=Float.MAX_VALUE,chosen=yaw;boolean found=false;
                for(int pass=0;pass<2;pass++){
                    if(pass==1&&best<=5)break;
                    for(float[] offset:SPIRAL){float y=yaw+offset[0],v=MathHelper.clamp_float(pitch+offset[1],-90,90);if(!hits(y,v))continue;float d=Math.abs(MathHelper.wrapAngleTo180_float(y-(pass==0?p.rotationYaw-180:yaw)));if(d<best){best=d;chosen=y;found=true;if(d<=1)break;}}
                }
                if(found){best=Float.MAX_VALUE;float chosenPitch=pitch;boolean foundPitch=false;for(float[] offset:SPIRAL){float v=MathHelper.clamp_float(pitch+offset[1],-90,90);if(hits(chosen,v)&&v<best){foundPitch=true;best=v;chosenPitch=v;if(v<=81)break;}}if(foundPitch){desiredYaw=chosen;desiredPitch=chosenPitch;}}
            }
        }
        if(onlyRequired.get()&&hits(yaw,pitch)){desiredYaw=yaw;desiredPitch=pitch;}
    }
    private static List<float[]> spiral(){List<float[]> values=new ArrayList<>();float i=0,r=0;while(r<90){float a=(float)(1.61803398875*Math.PI*i);values.add(new float[]{MathHelper.sin(a)*r,MathHelper.cos(a)*r});r=MathHelper.sqrt_float(++i);}return List.copyOf(values);}
    public Angles motionPre(){
        if(!active())return null;tick();var p=mc.thePlayer;
        if(!sprint.get())stopSprint();
        if(limitSpeed.get()&&p.onGround&&moving()&&Math.hypot(p.motionX,p.motionZ)>speedLimit.get())speed(speedLimit.get());
        if(towerActive()&&moving()){
            if(p.onGround)towerYaw=p.rotationYaw;
            if(towerPlaced&&Math.round(p.posY*100)/100D==Math.round(p.posY)){p.motionY=0;towerPlaced=false;}
        }
        Angles result=rotate.get()&&(placed||keepRotation.get())?angles():null;placed=false;return result;
    }
    public void place(){
        if(!active())return;var p=mc.thePlayer;
        if(shouldSearch()&&support!=null&&selectedSlot>=0){
            for(int attempt=0;attempt<3;attempt++){
                if(raytrace.get()&&!hits(yaw,pitch))break;MovingObjectPosition hit=ray(yaw,pitch);Vec3 point=hit==null?new Vec3(support):hit.hitVec;
                ItemStack held=p.getHeldItem();if(!valid(held))break;
                if(mc.playerController.onPlayerRightClick(p,mc.theWorld,held,support,face,point)){placements++;placed=true;towerPlaced=true;if(swing.get())p.swingItem();else p.sendQueue.addToSendQueue(new C0APacketAnimation());}
                if(supported())break;search();if(support==null)break;
            }
            if(p.onGround&&!supported()&&safeWalkMode.get()==SafeWalk.JUMP)p.jump();
            if(sneak.isSelected(Sneak.EDGE))requestSneak();if(unsprint.get())stopSprint();
        }else if(mc.objectMouseOver!=null&&random.nextInt(100)+1<=missChance.get()){
            if(mc.objectMouseOver.getBlockPos()!=null&&p.getHeldItem()!=null){mc.playerController.sendUseItem(p,mc.theWorld,p.getHeldItem());if(random.nextInt(100)+1<=doubleChance.get())mc.playerController.sendUseItem(p,mc.theWorld,p.getHeldItem());}
        }
        if(sneak.isSelected(Sneak.DISTANCE)&&distance>=sneakDistance.get())requestSneak();
        if(sneak.isSelected(Sneak.DELAY)&&p.ticksExisted%sneakDelay.get()==0)requestSneak();
        boolean sneaking=mc.currentScreen==null&&physical(mc.gameSettings.keyBindSneak)||sneakTicks-->0;
        distance=sneaking?0:distance+p.distanceWalkedModified;KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),sneaking);
    }
    private void requestSneak(){int min=minSneak.get(),max=Math.max(min,maxSneak.get());sneakTicks=Math.max(sneakTicks,min+random.nextInt(max-min+1));}
    public void afterInput(){
        if(!active())return;var p=mc.thePlayer;if(!sprint.get())stopSprint();
        if(breezly.get()&&p.onGround&&p.moveForward>0&&mc.currentScreen==null){if(breezlyTicks++>=breezlyDelay.get()){breezlyLeft=!breezlyLeft;breezlyTicks=0;}p.movementInput.moveStrafe=breezlyLeft?1:-1;}
        if(sprint.get()&&sprintMode.get()==Sprint.HYPIXEL){
            if(mc.gameSettings.keyBindJump.isKeyDown()&&moving())stopSprint();int amplifier=p.isPotionActive(Potion.moveSpeed)?p.getActivePotionEffect(Potion.moveSpeed).getAmplifier():-1;
            if(Math.hypot(p.motionX,p.motionZ)>.216&&moving()&&p.onGround&&amplifier>=0)speed(slowSprint.get()?.108:.126);else if(moving()&&slowSprint.get())speed(.122);
        }else flush();
    }
    private void speed(double value){var p=mc.thePlayer;float y=p.rotationYaw,forward=p.moveForward,side=p.moveStrafing;int turn=forward<0?-45:forward==0?90:45;if(forward<0)y+=180;if(side<0)y+=turn;if(side>0)y-=turn;double a=Math.toRadians(y);p.motionX=-Math.sin(a)*value;p.motionZ=Math.cos(a)*value;}
    public void beforeMove(){if(active()&&towerActive()&&moving()&&mc.thePlayer.onGround){towerYaw=mc.thePlayer.rotationYaw;mc.thePlayer.motionY=.41988;towerFloor=mc.thePlayer.posY;towerPlaced=true;}}
    public void move(moon.module.movement.SpeedModule.Move event){if(active()&&towerActive()&&moving()){event.yaw=towerYaw;event.forward=1;event.strafe=0;event.speed(.19);}}
    private void flush(){if(sprintPackets.isEmpty())return;var queue=new ArrayList<>(sprintPackets);sprintPackets.clear();if(world!=mc.theWorld||connection!=mc.getNetHandler()||mc.thePlayer==null)return;draining=true;try{for(Packet<?> packet:queue)mc.thePlayer.sendQueue.addToSendQueue(packet);}finally{draining=false;}}
    public boolean outgoing(Packet<?> packet){
        if(!active()||draining)return false;
        if(!(sprint.get()&&sprintMode.get()==Sprint.HYPIXEL)){flush();return false;}
        if(mc.gameSettings.keyBindJump.isKeyDown()&&moving()||correctionTicks>0){if(correctionTicks>0)correctionTicks--;stopSprint();flush();sprintStep=0;return false;}
        if(packet instanceof C08PacketPlayerBlockPlacement||packet instanceof C0APacketAnimation){sprintPackets.add(packet);return true;}
        if(packet instanceof C03PacketPlayer c&&!c.moonScaffoldExcluded()){
            if(c.isMoving()&&mc.thePlayer.onGround){
                var p=mc.thePlayer;double distance=Math.hypot(p.posX-p.prevPosX,p.posZ-p.prevPosZ);double offset=slowSprint.get()?Math.abs(distance-(p.isPotionActive(Potion.moveSpeed)?.16+random.nextDouble()*.02:.08+random.nextDouble()*.02)):Math.max(distance-(.12+random.nextDouble()*.005),0);
                if(++sprintStep==1){float y=p.rotationYaw,f=p.moveForward,s=p.moveStrafing;int turn=f<0?-45:f==0?90:45;if(f<0)y+=180;if(s<0)y+=turn;if(s>0)y-=turn;c.moonHorizontalPosition(c.getPositionX()+Math.sin(Math.toRadians(y))*offset,c.getPositionZ()-Math.cos(Math.toRadians(y))*offset);}
                else{flush();sprintStep=0;}
            }else{flush();sprintStep=0;}
        }
        return false;
    }
    public void receive(Packet<?> packet){if(active()&&packet instanceof S08PacketPlayerPosLook)correctionTicks=20;}
}
