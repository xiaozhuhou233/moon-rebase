package moon.module.combat.killaura;

import java.util.*;
import moon.module.combat.KillAuraModule;
import moon.module.combat.killaura.KillAuraRotations.Angles;
import moon.module.player.scaffold.OpenB4Support.*;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.util.*;
import org.lwjglx.input.Mouse;

/** OpenB4-u20 rotation and AutoBlock state machines, hosted by Moon's target/attack owner. */
public final class OpenB4Aura {
    public enum Rotation implements DisplayNamed { Silent,Lock_view,None; public String displayName(){return name().replace('_',' ');} }
    public enum AutoBlock implements DisplayNamed {
        Manual(false,false,false),Vanilla(false,false,false),Damage(false,false,false),Fake(false,false,false),
        Interact_A(true,true,true),Interact_B(true,true,true),Interact_C(true,true,true),Interact_D(true,true,true),Swap(false,true,true),Dual(true,true,false);
        public final boolean interacts,packet,lags;
        AutoBlock(boolean interacts,boolean packet,boolean lags){this.interacts=interacts;this.packet=packet;this.lags=lags;}
        public String displayName(){return name().replace('_',' ');}
    }
    public final EnumSetting<Rotation> rotation;
    public final EnumSetting<AutoBlock> autoBlock;
    public final FloatSetting smoothing,blockAps,blockLag,blockRange,closestPoint;
    public final BooleanSetting manualBlock,silentSwing,throughWalls;
    private final List<SettingValue<?>> settings;
    private final Minecraft mc=Minecraft.getMinecraft();
    private final KillAuraModule owner;
    private final ArrayDeque<Packet<?>> held=new ArrayDeque<>();
    private Object world;
    private NetHandlerPlayClient connection;
    private Angles angles;
    private boolean returning,keyOwned,serverBlocking,fakeBlocking,lagging,sending,releasing,slowed;
    private int blockState=-1,lagAccumulator,dualSwaps,badSwapTicks;
    private float clientYaw,clientPitch;
    private double dualAccumulator;
    private long damageBlockAt,lastAttack;
    private AutoBlock activeMode;
    private Rotation activeRotation;

    public OpenB4Aura(KillAuraModule owner,MarkerHooks h){
        this.owner=owner;
        rotation=EnumSetting.builder("openb4-rotation",Rotation.class,h).configure("Rotation",Rotation.Silent).build();
        smoothing=number(h,"rotation-smoothing","Smoothing",0,0,8,.1F);
        closestPoint=number(h,"aim-closest-point","Aim closest point",0,0,100,1);
        throughWalls=flag(h,"hit-through-blocks","Hit through blocks",false);
        autoBlock=EnumSetting.builder("openb4-auto-block",AutoBlock.class,h).configure("AutoBlock",AutoBlock.Manual).build();
        blockAps=number(h,"auto-block-aps","AutoBlock APS",10,1,20,.5F);
        blockLag=number(h,"auto-block-lag","AutoBlock lag",100,0,100,2);
        blockRange=number(h,"block-range","Range (block)",6,3,10,.1F);
        manualBlock=flag(h,"manual-block","Manual block",true);
        silentSwing=flag(h,"silent-swing-blocking","Silent swing while blocking",false);
        settings=List.of(SettingGroup.builder(h).configure("Rotation",rotation,smoothing,closestPoint,throughWalls).build(),
            SettingGroup.builder(h).configure("AutoBlock",autoBlock,blockAps,blockLag,blockRange,manualBlock,silentSwing).build());
    }
    private static FloatSetting number(MarkerHooks h,String k,String title,float v,float min,float max,float step){return FloatSetting.builder(k,h).configure(title,v,min,max,step).build();}
    private static BooleanSetting flag(MarkerHooks h,String k,String title,boolean v){return BooleanSetting.builder(k,h).configure(title,v).build();}
    public List<SettingValue<?>> settings(){return settings;}
    public boolean rotates(){return true;}
    public boolean blocks(){return true;}
    public Angles angles(){return rotates()&&owner.config.rotate.get()&&rotation.get()!=Rotation.None?angles:null;}
    public boolean canAttackTarget(){Entity e=entity();return e!=null&&(throughWalls.get()||aimPoint(e)!=null);}
    public boolean rayHitsTarget(float range){
        Entity e=entity();Angles a=angles();if(e==null||a==null)return false;
        Vec3 eyes=mc.thePlayer.getPositionEyes(1),v=RotationUtils.getVectorForRotation(a.yaw(),a.pitch());
        float border=e.getCollisionBorderSize();var box=e.getEntityBoundingBox().expand(border,border,border);
        return box.isVecInside(eyes)||box.calculateIntercept(eyes,eyes.addVector(v.xCoord*range,v.yCoord*range,v.zCoord*range))!=null;
    }
    private Entity entity(){return owner.target()==null||mc.theWorld==null?null:mc.theWorld.getEntityByID(owner.target().id());}
    private boolean sword(){return mc.thePlayer!=null&&isSword(mc.thePlayer.getHeldItem());}
    private static boolean isSword(ItemStack stack){return stack!=null&&stack.getItem() instanceof ItemSword;}
    private boolean allowed(){Entity e=entity();return blocks()&&owner.config.autoBlock.get()&&sword()&&e!=null
        &&mc.currentScreen==null&&mc.thePlayer.getDistanceSqToEntity(e)<=blockRange.get()*blockRange.get()&&(!manualBlock.get()||Mouse.isButtonDown(1));}
    private boolean sameConnection(){return world==mc.theWorld&&connection==mc.getNetHandler();}
    private void bindConnection(){world=mc.theWorld;connection=mc.getNetHandler();}
    public void update(){
        if(!sameConnection()){discard();bindConnection();RotationManager.reset();}
        if(activeMode!=autoBlock.get()||!blocks()||!owner.config.autoBlock.get()){
            finish(true);activeMode=autoBlock.get();
        }
        if(activeRotation!=rotation.get()||!rotates()||!owner.config.rotate.get()){
            angles=null;returning=false;activeRotation=rotation.get();
        }
        if(badSwapTicks>0)--badSwapTicks;
        if(mc.thePlayer==null||!rotates()||!owner.config.rotate.get()||rotation.get()==Rotation.None){angles=null;return;}
        Entity e=entity();
        if(e!=null&&mc.thePlayer.getDistanceToEntity(e)<=owner.config.aimReach.get()){
            Vec3 point=aimPoint(e);
            if(point==null){angles=null;return;}
            Vec3 eyes=mc.thePlayer.getPositionEyes(1);float[] r=RotationUtils.fromDelta(point.xCoord-eyes.xCoord,point.yCoord-eyes.yCoord,point.zCoord-eyes.zCoord);
            r[0]=RotationManager.lastYaw+MathHelper.wrapAngleTo180_float(r[0]-RotationManager.lastYaw);
            smooth(r,RotationManager.lastYaw,RotationManager.lastPitch,smoothing.get(),0);
            r=RotationUtils.fixSensitivity(r[0],r[1],RotationManager.lastYaw,RotationManager.lastPitch,false);
            angles=new Angles(r[0],r[1]);clientYaw=mc.thePlayer.rotationYaw;clientPitch=mc.thePlayer.rotationPitch;returning=smoothing.get()>0;
        }else if(!returning||!smoothReturn()){angles=null;returning=false;}
    }
    private Vec3 aimPoint(Entity e){
        Vec3 eyes=mc.thePlayer.getPositionEyes(1);float border=e.getCollisionBorderSize();AxisAlignedBB b=e.getEntityBoundingBox().expand(border,border,border);
        double x=(b.minX+b.maxX)/2,y=b.minY+(b.maxY-b.minY)*.825,z=(b.minZ+b.maxZ)/2,t=closestPoint.get()/100D;
        Vec3 point=new Vec3(x+(MathHelper.clamp_double(eyes.xCoord,b.minX+.03,b.maxX-.03)-x)*t,
            y+(MathHelper.clamp_double(eyes.yCoord,b.minY+.03,b.maxY-.03)-y)*t,z+(MathHelper.clamp_double(eyes.zCoord,b.minZ+.03,b.maxZ-.03)-z)*t);
        if(throughWalls.get()||b.isVecInside(eyes)||clear(eyes,point))return point;
        double eye=e.getEyeHeight(),o=.28;
        double[][] candidates={{0,eye+.2,0},{0,eye-.6,0},{0,eye-.9,0},{0,eye-1.2,0},{0,eye-1.5,0},
            {o,eye-.2,o},{o,eye-.2,-o},{-o,eye-.2,o},{-o,eye-.2,-o},{o,eye-.9,o},{o,eye-.9,-o},{-o,eye-.9,o},{-o,eye-.9,-o},
            {o,eye-1.5,o},{o,eye-1.5,-o},{-o,eye-1.5,o},{-o,eye-1.5,-o}};
        for(double[] c:candidates){point=new Vec3(e.posX+c[0],e.posY+c[1],e.posZ+c[2]);if(clear(eyes,point))return point;}return null;
    }
    private boolean clear(Vec3 from,Vec3 to){return mc.theWorld.rayTraceBlocks(from,to,false,true,false)==null;}
    public static void smooth(float[] r,float lastYaw,float lastPitch,float smoothing,float extra){
        float keep=(float)(1-Math.min(9.9,smoothing+extra)/10),dy=MathHelper.wrapAngleTo180_float(r[0]-lastYaw),dp=r[1]-lastPitch;
        if(Math.abs(dy)>7)dy=Math.signum(dy)*((Math.abs(dy)-7)*keep+7);
        if(Math.abs(dp)>3)dp=Math.signum(dp)*((Math.abs(dp)-3)*keep+3);
        r[0]=lastYaw+dy;r[1]=lastPitch+dp;
    }
    private boolean smoothReturn(){
        if(mc.objectMouseOver!=null&&mc.objectMouseOver.typeOfHit!=MovingObjectPosition.MovingObjectType.MISS)return false;
        ItemStack stack=mc.thePlayer.getHeldItem();Item item=stack==null?null:stack.getItem();
        if(item==Items.fishing_rod||item==Items.bow||item==Items.egg||item==Items.snowball||item==Items.ender_pearl)return false;
        float yaw=mc.thePlayer.rotationYaw,pitch=mc.thePlayer.rotationPitch;
        float dy=Math.abs(MathHelper.wrapAngleTo180_float(yaw-clientYaw)),dp=Math.abs(pitch-clientPitch);clientYaw=yaw;clientPitch=pitch;
        if(dy>20||dp>8)return false;
        float[] r={yaw,pitch};smooth(r,RotationManager.lastYaw,RotationManager.lastPitch,smoothing.get(),1.5F);
        boolean still=Math.abs(MathHelper.wrapAngleTo180_float(r[0]-yaw))>3||Math.abs(r[1]-pitch)>3;
        r=RotationUtils.fixSensitivity(r[0],r[1],RotationManager.lastYaw,RotationManager.lastPitch,false);angles=new Angles(r[0],r[1]);return still;
    }
    /** One FSM step per walking update; attacks retain Moon's selection/cancellation callbacks. */
    public boolean pre(){
        if(!blocks())return true;
        if(!allowed()){finish(true);return true;}
        AutoBlock mode=autoBlock.get();
        if(!mode.packet)return true;
        if(blockState==0)key(false);
        if(mode==AutoBlock.Swap){
            if(blockState==0){setBlocking(true);blockState=1;}
            else{rollLag();blockState=0;swapAway();serverBlocking=false;if(!lagging)fakeBlocking=false;}
            return true;
        }
        if(mode==AutoBlock.Dual){return prepareDual(false);}
        if(blockState==0){return true;}
        rollLag();
        if(blockState==1||PacketUtils.lastUse==1){
            if(mode==AutoBlock.Interact_B||mode==AutoBlock.Interact_C){
                slot(freeSlot());if(mode==AutoBlock.Interact_C){setBlocking(false);if(lagging)fakeBlocking=true;}
                else{serverBlocking=false;if(!lagging)fakeBlocking=false;}
            }else if(serverBlocking||PacketUtils.lastUse==1){
                if(mode==AutoBlock.Interact_D)swapAway();setBlocking(false);if(lagging)fakeBlocking=true;
            }
        }
        blockState=0;return false;
    }
    public boolean beforeAttack(){
        if(!blocks()||!allowed()||!autoBlock.get().packet)return true;
        AutoBlock mode=autoBlock.get();
        if(mode==AutoBlock.Dual)return prepareDual(true);
        if(mode.interacts){
            blockState=1;
            if((mode==AutoBlock.Interact_B||mode==AutoBlock.Interact_C)&&PacketUtils.serverSlot!=mc.thePlayer.inventory.currentItem)slot(mc.thePlayer.inventory.currentItem);
        }
        return true;
    }
    public void afterAttack(){
        if(!allowed())return;
        AutoBlock mode=autoBlock.get();lastAttack=System.currentTimeMillis();
        if(mode.packet&&mode.interacts&&blockState==1){
            Entity e=entity();Angles a=owner.angles();if(a==null)a=new Angles(mc.thePlayer.rotationYaw,mc.thePlayer.rotationPitch);
            Vec3 eyes=mc.thePlayer.getPositionEyes(1),v=RotationUtils.getVectorForRotation(a.yaw(),a.pitch());
            Vec3 end=eyes.addVector(v.xCoord*(owner.config.reach.get()+.3),v.yCoord*(owner.config.reach.get()+.3),v.zCoord*(owner.config.reach.get()+.3));
            float border=e.getCollisionBorderSize();MovingObjectPosition hit=e.getEntityBoundingBox().expand(border,border,border).calculateIntercept(eyes,end);
            if(hit!=null){send(new C02PacketUseEntity(e,hit.hitVec.subtract(new Vec3(e.posX,e.posY,e.posZ))));send(new C02PacketUseEntity(e,C02PacketUseEntity.Action.INTERACT));}
            else if(mc.thePlayer.getDistanceToEntity(e)<=owner.config.reach.get())return;
            setBlocking(true);
        }
    }
    private boolean prepareDual(boolean attack){
        int best=-1,second=-1;double bd=-1,sd=-1;
        for(int i=0;i<9;i++){ItemStack s=mc.thePlayer.inventory.getStackInSlot(i);if(!isSword(s))continue;double d=((ItemSword)s.getItem()).getDamageVsEntity()+EnchantmentHelper.getModifierForCreature(s,EnumCreatureAttribute.UNDEFINED);
            if(d>bd){second=best;sd=bd;best=i;bd=d;}else if(d>sd){second=i;sd=d;}}
        if(second<0){if(serverBlocking)setBlocking(false);key(false);blockState=-2;return true;}
        if(attack){blockState=1;int slot=(dualSwaps++&1)==0?best:second;if(slot==PacketUtils.serverSlot)slot=slot==best?second:best;slot(slot);}
        else blockState=0;
        return true;
    }
    public boolean useBlockScheduler(){return blocks()&&allowed();}
    public boolean attackDue(){
        if(autoBlock.get()==AutoBlock.Dual){if(dualSwaps==0&&dualAccumulator==0)dualAccumulator=20-blockAps.get();dualAccumulator+=blockAps.get();if(dualAccumulator<20)return false;dualAccumulator-=20;return true;}
        if(autoBlock.get()==AutoBlock.Swap&&blockState==0&&(lastAttack==0||blockAps.get()>=10))return true;
        if(autoBlock.get().interacts&&autoBlock.get()!=AutoBlock.Dual&&blockState==0&&blockAps.get()>=10)return true;
        return System.currentTimeMillis()-lastAttack>=1000D/blockAps.get();
    }
    public void post(){
        if(rotates()&&rotation.get()==Rotation.Lock_view&&angles()!=null&&entity()!=null){mc.thePlayer.rotationYaw=angles.yaw();mc.thePlayer.rotationPitch=angles.pitch();}
        if(!allowed())return;
        switch(autoBlock.get()){
            case Vanilla->key(true);
            case Damage->{if(System.currentTimeMillis()-damageBlockAt<800)key(true);else if(!Mouse.isButtonDown(1))key(false);}
            case Fake->fakeBlocking=true;
            default->{}
        }
    }
    public void afterInput(){
        if(!blocks()||!owner.enabled()||mc.thePlayer==null)return;
        if(autoBlock.get().packet&&serverBlocking&&!mc.thePlayer.isUsingItem()
            &&!(autoBlock.get()==AutoBlock.Dual&&blockState>=0&&dualAccumulator+blockAps.get()>=20)){
            mc.thePlayer.movementInput.moveForward*=.2F;mc.thePlayer.movementInput.moveStrafe*=.2F;
        }
        slowed=false;
    }
    public boolean pose(){return blocks()&&owner.config.autoBlock.get()&&(fakeBlocking||serverBlocking||keyOwned);}
    public boolean silentSwing(){return blocks()&&silentSwing.get()&&pose();}
    private void rollLag(){lagAccumulator+=Math.round(blockLag.get());lagging=lagAccumulator>=100;if(lagging)lagAccumulator-=100;}
    private void key(boolean on){KeyBinding.setKeyBindStateUnchecked(mc.gameSettings.keyBindUseItem.getKeyCode(),on||(!autoBlock.get().packet&&Mouse.isButtonDown(1)));keyOwned=on;}
    private void setBlocking(boolean on){
        int server=PacketUtils.serverSlot<0?mc.thePlayer.inventory.currentItem:PacketUtils.serverSlot;
        if(on&&!isSword(mc.thePlayer.inventory.getStackInSlot(server)))return;
        if(on)send(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
        else{releasing=true;try{send(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,BlockPos.ORIGIN,EnumFacing.DOWN));}finally{releasing=false;}}
        serverBlocking=fakeBlocking=on;
    }
    private int freeSlot(){
        int current=mc.thePlayer.inventory.currentItem;
        for(int i=0;i<9;i++)if(i!=current&&mc.thePlayer.inventory.getStackInSlot(i)==null)return i;
        for(int i=0;i<9;i++){if(i==current)continue;Item item=mc.thePlayer.inventory.getStackInSlot(i).getItem();if(item!=Items.bow&&item!=Items.ender_pearl&&item!=Items.fishing_rod)return i;}
        return(current+1)%9;
    }
    private void slot(int n){send(new C09PacketHeldItemChange(n));serverBlocking=false;}
    private void swapAway(){slot(freeSlot());slot(mc.thePlayer.inventory.currentItem);}
    private void send(Packet<?> p){boolean was=sending;sending=true;try{PacketUtils.send(p);}finally{sending=was;}}
    /** true means consume. Replay goes straight to the same NetworkManager once. */
    public boolean outgoing(Packet<?> p){
        if(!sameConnection()){discard();bindConnection();return false;}
        if(!owner.enabled()||!blocks()||!owner.config.autoBlock.get())return false;
        AutoBlock mode=activeMode==null?autoBlock.get():activeMode;
        if(!sending&&mode==AutoBlock.Manual&&allowed()&&p instanceof C08PacketPlayerBlockPlacement b&&b.getPlacedBlockDirection()==255&&isSword(b.getStack())){
            if(PacketUtils.sentAttack)return true;
            Entity target=entity();Angles a=angles();
            if(a!=null&&mc.thePlayer.getDistanceToEntity(target)<=owner.config.reach.get()){
                boolean accepted=owner.manualBlockAttack();
                if(accepted){interact(target,a);}
            }
        }
        if(mode.packet&&!sending&&allowed()){
            if(p instanceof C07PacketPlayerDigging d&&d.getStatus()==C07PacketPlayerDigging.Action.RELEASE_USE_ITEM&&!releasing)return true;
            if(p instanceof C09PacketHeldItemChange){badSwapTicks=1;}
            if(p instanceof C08PacketPlayerBlockPlacement b&&isSword(b.getStack())&&badSwapTicks>0)return true;
            if(p instanceof C02PacketUseEntity e&&e.getAction()!=C02PacketUseEntity.Action.ATTACK&&!mode.interacts)return true;
        }
        if(!mode.lags||mc.isSingleplayer()||(!lagging&&held.isEmpty())||(blockState!=-1&&p instanceof C08PacketPlayerBlockPlacement&&!sending))return false;
        if(blockState==-1){
            if(p instanceof C03PacketPlayer){drain(true);lagging=false;return false;}
            if(!held.isEmpty()){held.add(p);return true;}return false;
        }
        PacketUtils.observe(p);held.add(p);
        if(p instanceof C03PacketPlayer){if(blockState==1)drain(false);else if(!lagging)drain(true);}
        // Do not let a disconnected/misbehaving server create an unbounded client queue.
        if(held.size()>512){drain(true);lagging=false;}
        return true;
    }
    private void drain(boolean dropAttacks){
        if(!sameConnection()||connection==null||!connection.getNetworkManager().isChannelOpen()){held.clear();return;}
        while(!held.isEmpty()){
            Packet<?> p=held.removeFirst();
            if(dropAttacks&&(p instanceof C0APacketAnimation||p instanceof C02PacketUseEntity||p instanceof C07PacketPlayerDigging d&&d.getStatus()==C07PacketPlayerDigging.Action.START_DESTROY_BLOCK))continue;
            connection.getNetworkManager().sendPacket(p);
        }
    }
    private void finish(boolean dropAttacks){
        boolean had=blockState!=-1||serverBlocking||keyOwned;
        blockState=-1;lagging=false;
        if(sameConnection()&&mc.thePlayer!=null){
            drain(dropAttacks);
            if(had){
                int current=mc.thePlayer.inventory.currentItem;
                if(PacketUtils.serverSlot>=0&&PacketUtils.serverSlot!=current)slot(current);
                if(serverBlocking)setBlocking(false);
            }
        }else held.clear();
        if(keyOwned||had){key(false);KeyBinding.setKeyBindStateUnchecked(mc.gameSettings.keyBindUseItem.getKeyCode(),mc.currentScreen==null&&Mouse.isButtonDown(1));}
        serverBlocking=fakeBlocking=keyOwned=false;dualAccumulator=0;dualSwaps=0;lastAttack=0;
    }
    private void interact(Entity e,Angles a){
        Vec3 eyes=mc.thePlayer.getPositionEyes(1),v=RotationUtils.getVectorForRotation(a.yaw(),a.pitch());double range=owner.config.reach.get()+.3;
        float border=e.getCollisionBorderSize();var hit=e.getEntityBoundingBox().expand(border,border,border).calculateIntercept(eyes,eyes.addVector(v.xCoord*range,v.yCoord*range,v.zCoord*range));
        if(hit!=null){send(new C02PacketUseEntity(e,hit.hitVec.subtract(new Vec3(e.posX,e.posY,e.posZ))));send(new C02PacketUseEntity(e,C02PacketUseEntity.Action.INTERACT));}
    }
    public boolean cancelMouse(int button,boolean pressed){
        if(!blocks()||!owner.config.autoBlock.get()||!owner.enabled()||mc.currentScreen!=null)return false;
        if(button==0&&pressed&&entity()!=null&&(allowed()&&autoBlock.get().packet||mc.objectMouseOver!=null&&mc.objectMouseOver.entityHit!=null))return true;
        if(button!=1||!autoBlock.get().packet||!sword())return false;
        if(entity()==null&&mc.objectMouseOver!=null){
            BlockPos p=mc.objectMouseOver.getBlockPos();if(p!=null&&BlockUtils.isInteractable(BlockUtils.getBlock(p)))return false;
        }
        fakeBlocking=pressed;return true;
    }
    public boolean stop(){boolean had=blockState!=-1||serverBlocking||keyOwned;finish(true);return had;}
    public void damage(){damageBlockAt=System.currentTimeMillis();}
    public void correction(){finish(true);angles=null;returning=false;}
    public void reset(){finish(true);discard();bindConnection();}
    private void discard(){held.clear();blockState=-1;serverBlocking=fakeBlocking=keyOwned=lagging=returning=slowed=false;angles=null;lagAccumulator=dualSwaps=badSwapTicks=0;dualAccumulator=0;lastAttack=damageBlockAt=0;}
}
