package moon.client;
import moon.module.combat.killaura.KillAuraSettings;
import moon.module.combat.killaura.KillAuraRotations;
import moon.module.combat.killaura.KillAuraTargetSelector;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.*;
import moon.module.NativeModule;
import moon.module.combat.*;
import moon.module.player.ScaffoldWalkModule;
import moon.module.combat.killaura.KillAuraSettings.TargetKind;
import moon.module.combat.killaura.KillAuraTargetSelector.*;
import moon.module.combat.killaura.KillAuraRotations.Angles;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.*;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.passive.*;
import net.minecraft.item.*;
import net.minecraft.init.Items;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.*;
import org.apache.logging.log4j.*;

/** Native module owner for the Java24 MCP. No dependency on the old module registry/native init. */
public final class McpModuleServices implements AutoCloseable {
    private static final Logger LOG=LogManager.getLogger("MoonModules");
    private static volatile McpModuleServices instance;
    private final Minecraft mc;
    private final Thread thread=Thread.currentThread();
    private final McpVisualConfigStore config;
    private final List<NativeModule> modules;
    public final KillAuraModule killAura;
    public final VelocityModule velocity;
    public final moon.module.exploit.BacktrackModule backtrack;
    public final moon.module.player.FastPlaceModule fastPlace;
    public final moon.module.player.ScaffoldWalkModule scaffold;
    public final moon.module.player.Sprint sprint;
    public final moon.module.player.sprint.SprintController sprintController;
    public final McpMyauModules myau;
    public final moon.module.player.SessionInformationModule session;
    public final moon.module.player.InventoryManagerModule inventory;
    public final moon.module.player.ChestStealerModule chest;
    private final List<Predicate<EntityLivingBase>> attackCancellations=new CopyOnWriteArrayList<>();
    private final List<BooleanSupplier> combatCancellations=new CopyOnWriteArrayList<>();
    private Predicate<EntityLivingBase> rejected=entity -> false,preferred=entity -> false;
    private Object world;
    private volatile boolean closed;
    private String lastPending,lastPlayerPending;
    private final java.util.ArrayDeque<Long> leftClicks=new java.util.ArrayDeque<>(),rightClicks=new java.util.ArrayDeque<>();
    private long packetWindowStart=System.currentTimeMillis();
    private int packetsIn,packetsOut,lastPacketsIn,lastPacketsOut;
    private EntityPlayerSP visualPlayer;
    private NativeModule visualSource;
    private Motion visualPrevious,visualCurrent;
    private int visualTick=-1;

    private void publishVisualMotion(EntityPlayerSP player,Angles angles,NativeModule source){
        if(angles==null){visualPlayer=null;visualSource=null;visualCurrent=visualPrevious=null;visualTick=-1;return;}
        Motion next=new Motion(angles.yaw(),angles.pitch());
        if(visualPlayer!=player||visualCurrent==null||visualTick<player.ticksExisted-1){
            visualPrevious=new Motion(player.rotationYaw,player.rotationPitch);
        }else if(visualTick!=player.ticksExisted)visualPrevious=visualCurrent;
        visualPlayer=player;visualSource=source;visualCurrent=next;visualTick=player.ticksExisted;
    }
    /** Model-only silent rotations. The camera, movement input and packet angles stay independent. */
    public static Motion visualMotion(EntityLivingBase entity,float partialTicks){
        McpModuleServices s=instance;
        if(s==null||s.closed||entity!=s.mc.thePlayer||entity!=s.visualPlayer||s.visualCurrent==null
            ||s.mc.theWorld!=entity.worldObj||s.mc.currentScreen!=null||s.visualSource==null||!s.visualSource.enabled()
            ||entity.ticksExisted-s.visualTick>1||entity.ticksExisted<s.visualTick)return null;
        float ticks=Math.max(0,Math.min(1,partialTicks));
        float yaw=s.visualPrevious.yaw()+KillAuraRotations.wrap(s.visualCurrent.yaw()-s.visualPrevious.yaw())*ticks;
        float pitch=s.visualPrevious.pitch()+(s.visualCurrent.pitch()-s.visualPrevious.pitch())*ticks;
        return new Motion(yaw,pitch);
    }

    public McpModuleServices(Minecraft mc,moon.visual.render.DrawServices drawing,MarkerHooks hooks)throws IOException {
        if(instance!=null)throw new IllegalStateException("Native module services already installed");
        this.mc=mc;
        sprintController=new moon.module.player.sprint.SprintController(mc);
        killAura=new KillAuraModule(new AuraHost(),hooks,new Random());
        velocity=new VelocityModule(mc,hooks);
        backtrack=new moon.module.exploit.BacktrackModule(mc,drawing,hooks);
        fastPlace=new moon.module.player.FastPlaceModule(mc,hooks);
        scaffold=new moon.module.player.ScaffoldWalkModule(new McpScaffoldHost(mc),hooks,sprintController);
        sprint=new moon.module.player.Sprint(mc,hooks,sprintController);
        myau=new McpMyauModules(mc,hooks);
        
        inventory=new moon.module.player.InventoryManagerModule(mc,()->myau.target()!=null||killAura.target()!=null,hooks);
        chest=new moon.module.player.ChestStealerModule(mc,inventory,hooks);
        session=new moon.module.player.SessionInformationModule(new moon.module.player.SessionInformationModule.Host(){
            public String server(){return mc.getCurrentServerData()==null?null:mc.getCurrentServerData().serverIP;}
            public String playerName(){return mc.thePlayer==null?"":mc.thePlayer.getName();}
            public boolean inWorld(){return mc.thePlayer!=null&&mc.theWorld!=null;}
        },mc.mcDataDir.toPath().resolve("moon/session-information"),hooks);
        combatCancellations.add(chest::silentActive);
        combatCancellations.add(scaffold::blocksCombat);
        combatCancellations.add(inventory::usingPotion);
        // The previous implementations remain available as source backups only.
        modules=List.of(myau.killAura,myau.velocity,backtrack,fastPlace,sprint,myau.scaffold,session,chest,inventory);
        config=new McpVisualConfigStore(mc.mcDataDir.toPath().resolve("moon/module-config.json"));
        for(NativeModule module:modules)config.bind(module.key(),module.settings(),module::enabled,module::setEnabled);
        instance=this;
        try { config.load(); } catch(IOException failure) { LOG.error("Module config not loaded; original file preserved",failure); }
    }
    public static McpModuleServices current() { return instance; }
    /** Raw mouse button edge for the informative watermark CPS L/R row. */
    public static void mouseButton(int button,boolean pressed){
        McpModuleServices s=instance;
        if(s==null||s.closed||!pressed)return;
        s.click(button);
    }
    private synchronized void click(int button){
        if(button!=0&&button!=1)return;
        java.util.ArrayDeque<Long> queue=button==0?leftClicks:rightClicks;
        long now=System.currentTimeMillis();
        queue.addLast(now);trimClicks(queue,now);
    }
    private static void trimClicks(java.util.ArrayDeque<Long> queue,long now){
        while(!queue.isEmpty()&&now-queue.peekFirst()>=1000L)queue.removeFirst();
    }
    public synchronized int leftCps(){
        long now=System.currentTimeMillis();trimClicks(leftClicks,now);return leftClicks.size();
    }
    public synchronized int rightCps(){
        long now=System.currentTimeMillis();trimClicks(rightClicks,now);return rightClicks.size();
    }
    private synchronized void countPacket(boolean inbound){
        if(inbound)packetsIn++;else packetsOut++;
        rollPacketWindow(System.currentTimeMillis());
    }
    private void rollPacketWindow(long now){
        if(now-packetWindowStart<1000L)return;
        lastPacketsIn=packetsIn;lastPacketsOut=packetsOut;
        packetsIn=packetsOut=0;packetWindowStart=now;
    }
    /** Completed one-second packet rates: {in,out}. */
    public synchronized int[] packetRates(){
        rollPacketWindow(System.currentTimeMillis());
        return new int[]{lastPacketsIn,lastPacketsOut};
    }
    public static boolean renderAuraBlockPose() {
        McpModuleServices services=instance;
        return services!=null&&!services.closed&&(services.myau.blockPose()||services.killAura.rendersBlockPose());
    }
    public List<NativeModule> modules() { return modules; }
    McpVisualConfigStore configuration(){return config;}
    public List<NativeModule> modules(NativeModule.Category category) { return modules.stream().filter(m -> m.category()==category).toList(); }
    /** AntiBot/Teams/friends integrations supply their own original policies when migrated. */
    public void targetPolicy(Predicate<EntityLivingBase> rejected,Predicate<EntityLivingBase> preferred) {
        check();this.rejected=Objects.requireNonNull(rejected);this.preferred=Objects.requireNonNull(preferred);
    }
    public AutoCloseable cancelAttacksWhen(Predicate<EntityLivingBase> listener) {
        check();attackCancellations.add(Objects.requireNonNull(listener));return () -> attackCancellations.remove(listener);
    }
    /** Destination counterpart of jc.f() publishing cR; Scaffold will bind here. */
    public AutoCloseable cancelCombatWhen(BooleanSupplier listener) {
        check();combatCancellations.add(Objects.requireNonNull(listener));return () -> combatCancellations.remove(listener);
    }
    public void tick() {
        check();
        scaffold.tick();
        if(world!=mc.theWorld) { for(NativeModule module:modules)module.worldChanged();world=mc.theWorld;publishVisualMotion(null,null,null); }
        if(!available())killAura.worldChanged();
        killAura.updateTarget();
        if(!sprintOwned())updateAuraSprint();
        session.tick();
        try { config.poll(System.currentTimeMillis()); } catch(IOException|RuntimeException failure) { LOG.error("Could not save module settings",failure); }
        String pending=killAura.pendingReason();
        if(pending!=null&&!pending.equals(lastPending))LOG.warn("KillAura selection is pending migration: {}",pending);
        lastPending=pending;
        String playerPending=String.valueOf(scaffold.pendingReason())+"; "+String.valueOf(chest.pendingReason());
        if(!playerPending.equals("null; null")&&!playerPending.equals(lastPlayerPending))LOG.warn("Player module migration limitations: {}",playerPending);
        lastPlayerPending=playerPending;
    }
    public record Motion(float yaw,float pitch,double y,boolean onGround) {
        public Motion(float yaw,float pitch){this(yaw,pitch,Double.NaN,false);}
    }
    public static Motion walkingPre(EntityPlayerSP player) {
        McpModuleServices services=instance;
        if(services==null||services.closed||player!=services.mc.thePlayer)return new Motion(player.rotationYaw,player.rotationPitch,player.getEntityBoundingBox().minY,player.onGround);
        services.check();services.backtrack.motionPre();services.velocity.motionPre();Angles scaffoldAngles=services.scaffold.motionPre();
        services.chest.motionPre();Angles inventoryAngles=services.inventory.motionPre();
        Angles angles=services.killAura.motionPre();NativeModule source=services.killAura;
        if(inventoryAngles!=null){angles=inventoryAngles;source=services.inventory;}
        if(scaffoldAngles!=null){angles=scaffoldAngles;source=services.scaffold;}
        services.myau.beforeWalking();
        Angles myauAngles=services.myau.angles();
        if(myauAngles!=null){angles=myauAngles;source=services.myau.rotationSource();}
        services.publishVisualMotion(player,angles,source);
        Motion result=angles==null?new Motion(player.rotationYaw,player.rotationPitch,player.getEntityBoundingBox().minY,player.onGround):new Motion(angles.yaw(),angles.pitch(),player.getEntityBoundingBox().minY,player.onGround);
        moon.module.player.scaffold.OpenB4Support.RotationManager.lastYaw=result.yaw();
        moon.module.player.scaffold.OpenB4Support.RotationManager.lastPitch=result.pitch();
        moon.module.player.scaffold.OpenB4Support.RotationManager.playerYawAtSend=player.rotationYaw;
        return result;
    }
    /** Sprint module, or an actively placing Scaffold, owns the vanilla sprint block. */
    public static boolean moonOwnsSprint(EntityPlayerSP player){
        McpModuleServices s=instance;
        return s!=null&&!s.closed&&player==s.mc.thePlayer
                &&(s.sprint.enabled()||(s.scaffold.enabled()&&s.scaffold.controlsSprint()));
    }
    private boolean sprintOwned(){return scaffold.enabled()||sprint.enabled();}
    public static void walkingPost(EntityPlayerSP player) {
        McpModuleServices services=instance;
        if(services!=null&&!services.closed&&player==services.mc.thePlayer){services.inventory.motionPost();services.killAura.motionPost();}
    }
    public static void myauTick(boolean pre){McpModuleServices s=instance;if(s!=null&&!s.closed)s.myau.tick(pre);}
    public static void playerAfterUpdate(EntityPlayerSP player){McpModuleServices s=instance;if(s!=null&&!s.closed&&player==s.mc.thePlayer)s.myau.postUpdate();}
    public static Motion ridingMotion(EntityPlayerSP player){McpModuleServices s=instance;Angles a=s==null||s.closed||player!=s.mc.thePlayer?null:s.myau.angles();return a==null?new Motion(player.rotationYaw,player.rotationPitch):new Motion(a.yaw(),a.pitch());}
    /** Scaffold places from the pre-move delta, before the walk pose publishes the look. */
    public static void scaffoldPlace(EntityPlayerSP player){
        McpModuleServices s=instance;
        if(s!=null&&!s.closed&&player==s.mc.thePlayer)s.scaffold.motionPlace();
    }
    public record Movement(float yaw,float strafe,float forward) { }
    public static Movement movement(EntityPlayerSP player,float strafe,float forward) {
        McpModuleServices s=instance;
        if(s!=null&&!s.closed&&player==s.mc.thePlayer&&s.myau.active()){
            float[] input=s.myau.strafe(strafe,forward,0);
            return new Movement(s.myau.physicsYaw(player.rotationYaw),input[0],input[1]);
        }
        if(s!=null&&!s.closed&&player==s.mc.thePlayer&&s.scaffold.correctingMovement()){
            float yaw=s.scaffold.angles().yaw();float[] fixed=ScaffoldWalkModule.fixedMovement(player.rotationYaw,yaw,forward,strafe);
            return new Movement(yaw,fixed[1],fixed[0]);
        }
        if(s==null||s.closed||player!=s.mc.thePlayer||!s.available()||!s.killAura.correctingMovement())
            return new Movement(player.rotationYaw,strafe,forward);
        float yaw=s.killAura.angles().yaw();
        if(s.killAura.config.silentlyCorrect.get()) {
            if(strafe*strafe+forward*forward<=1.0E-6F)return new Movement(player.rotationYaw,strafe,forward);
            var direction=moon.module.combat.KillAuraModule.correctedDirection(yaw,movementYaw(player));
            float scale=.98F;
            if(player.isUsingItem())scale*=.2F;
            if(player.isSneaking())scale*=.3F;
            return new Movement(yaw,direction.strafe()*scale,direction.forward()*scale);
        }
        return new Movement(yaw,strafe,forward);
    }
    /** Original kQ.a(): movement direction comes from live living-entity input fields. */
    private static float movementYaw(EntityPlayerSP player) {
        float angle=player.rotationYaw*((float)Math.PI/180F),sin=MathHelper.sin(angle),cos=MathHelper.cos(angle);
        float x=player.moveStrafing*cos-player.moveForward*sin;
        float z=player.moveForward*cos+player.moveStrafing*sin;
        return (float)MathHelper.atan2(z,x)*(180F/(float)Math.PI)-90F;
    }
    private void updateAuraSprint() {
        if(!available()||!killAura.hasRotation())return;
        var p=mc.thePlayer;
        var direction=KillAuraModule.correctedDirection(killAura.angles().yaw(),movementYaw(p));
        float scale=.98F*(p.isUsingItem()?.2F:1F)*(p.isSneaking()?.3F:1F);
        killAura.updateSprintEligibility(direction.forward()*scale,p.isSprinting(),p.isCollidedHorizontally,
                p.getFoodStats().getFoodLevel()>6||p.capabilities.allowFlying,p.isUsingItem(),p.isPotionActive(net.minecraft.potion.Potion.blindness),()->{
                    int key=mc.gameSettings.keyBindSprint.getKeyCode();
                    KeyBinding.onTick(key);p.setSprinting(false);KeyBinding.setKeyBindStateUnchecked(key,false);
                });
    }
    /** py.a(IZ)/jc.c4: cancellation applies to both press and release state events. */
    public static boolean cancelKeyState(int code) {
        McpModuleServices s=instance;
        if(s==null||s.closed||s.sprintOwned())return false;
        return s.killAura.enabled()&&!s.killAura.allowsSprintKey()
                &&code==s.mc.gameSettings.keyBindSprint.getKeyCode();
    }
    public static float jumpYaw(EntityPlayerSP player) {
        McpModuleServices s=instance;
        if(s!=null&&!s.closed&&player==s.mc.thePlayer&&s.myau.active())return s.myau.jumpYaw(player.rotationYaw);
        if(s!=null&&!s.closed&&player==s.mc.thePlayer&&s.scaffold.correctingMovement())return s.scaffold.angles().yaw();
        return s!=null&&!s.closed&&player==s.mc.thePlayer&&s.available()&&s.killAura.hasRotation()
            ?s.killAura.angles().yaw():player.rotationYaw;
    }
    public static boolean safeWalk(Entity entity){McpModuleServices s=instance;return s!=null&&!s.closed&&entity==s.mc.thePlayer&&(s.myau.safeWalk()||s.scaffold.safeWalk());}
    public static void playerBeforeUpdate(EntityPlayerSP player){
        McpModuleServices s=instance;if(s!=null&&!s.closed&&player==s.mc.thePlayer){s.check();s.velocity.beforeUpdate();s.myau.beforeUpdate();s.scaffold.motionBefore();}
    }
    public static void playerAfterInput(EntityPlayerSP player){
        McpModuleServices s=instance;
        if(s!=null&&!s.closed&&player==s.mc.thePlayer){
            s.myau.afterInput();
            if(s.sprint.enabled()&&s.killAura.enabled()&&s.killAura.target()!=null
                    &&s.killAura.config.stopSprintOnAttack.get()){
                s.sprintController.update(false,player.rotationYaw);
            }else{
                // Select one owner before applying sprint; Sprint must not start
                // it just before a scaffold plan stops it in the same tick.
                s.scaffold.afterInput();
                if(!s.scaffold.controlsSprint()){
                    if(s.sprint.enabled())s.sprintController.update(s.myau.allowsSprint(),player.rotationYaw);
                    else s.sprintController.releaseControl();
                }
            }
            s.killAura.afterInput();
        }
    }
    public static void playerBeforeLiving(EntityPlayerSP player){McpModuleServices s=instance;if(s!=null&&!s.closed&&player==s.mc.thePlayer)s.myau.beforeLiving();}
    public static void renderScaffold(float ticks){McpModuleServices s=instance;if(s!=null&&!s.closed)s.myau.render3D(ticks);}
    public static void renderScaffoldCount(){McpModuleServices s=instance;if(s!=null&&!s.closed)s.myau.render2D(1);}
    public static McpMyauModules.RenderSlot spoofedRenderSlot(){McpModuleServices s=instance;return new McpMyauModules.RenderSlot(s==null?null:s.mc.thePlayer,s==null||s.closed?-1:s.myau.displayedSlot());}
    public static int displayedSlot(int fallback){McpModuleServices s=instance;int slot=s==null||s.closed?-1:s.myau.displayedSlot();return slot>=0?slot:fallback;}
    public static boolean cancelMouse(int button,boolean pressed){
        McpModuleServices s=instance;if(s==null||s.closed||s.mc.currentScreen!=null)return false;
        return s.killAura.openB4.cancelMouse(button,pressed);
    }
    public static boolean cancelModuleClick(int button){McpModuleServices s=instance;return s!=null&&!s.closed&&s.mc.currentScreen==null&&s.myau.click(button);}
    public static boolean cancelBlockHit(){McpModuleServices s=instance;return s!=null&&!s.closed&&s.myau.hitBlock();}
    public static boolean cancelItemUseRelease(){McpModuleServices s=instance;return s!=null&&!s.closed&&s.myau.cancelUse();}
    public static boolean cancelHotbarSwap(int slot,int offset){McpModuleServices s=instance;return s!=null&&!s.closed&&s.myau.swap(slot,offset);}
    /** Clutch freezes horizontal displacement for the placing tick, like the Lua MoveEvent freeze. */
    public static Vec3 scaffoldMove(EntityPlayerSP player,double y){
        McpModuleServices s=instance;
        if(s==null||s.closed||player!=s.mc.thePlayer)return null;
        return s.scaffold.clutchHorizontal()?new Vec3(0,y,0):null;
    }
    public static boolean receiveVelocity(Entity entity,double x,double y,double z,boolean explosion){
        McpModuleServices s=instance;if(s==null||s.closed)return false;
        s.check();
        // The recovered Velocity module edits the vanilla setVelocity path through
        // KnockbackEvent. Keep this hook for the existing damage bookkeeping and
        // let EntityPlayerSP.setVelocity dispatch the copied event exactly once.
        if(entity==s.mc.thePlayer)s.killAura.openB4.damage();
        return false;
    }
    public static double[] knockback(EntityPlayerSP player,double x,double y,double z){McpModuleServices s=instance;return s==null||s.closed||player!=s.mc.thePlayer?new double[]{x,y,z}:s.myau.knockback(x,y,z);}
    /** Serialize packet state with movement on the client thread. Direct drain uses
     * NetworkManager, bypassing NetHandler's normal observer exactly once. */
    public static boolean scaffoldSend(net.minecraft.client.network.NetHandlerPlayClient handler,net.minecraft.network.Packet<?> packet){
        McpModuleServices s=instance;
        if(s==null||s.closed||s.mc.thePlayer==null||handler!=s.mc.thePlayer.sendQueue)return false;
        if(Thread.currentThread()!=s.thread){
            s.mc.addScheduledTask(()->{
                if(!s.closed&&s.mc.thePlayer!=null&&handler==s.mc.thePlayer.sendQueue
                    &&!scaffoldSend(handler,packet))handler.getNetworkManager().sendPacket(packet);
            });
            return true;
        }
        McpScaffoldHost.View view=new McpScaffoldHost.View(packet);
        s.countPacket(false);
        if(s.myau.packet(packet,false))return true;
        s.scaffold.outgoing(view);
        // C02 attacks run before the client attack applies the vanilla sprint slowdown.
        // INTERACT packets (mount/villager/etc.) must not start a sprint recovery.
        if (packet instanceof net.minecraft.network.play.client.C02PacketUseEntity use
                && use.getAction()==net.minecraft.network.play.client.C02PacketUseEntity.Action.ATTACK) {
            s.sprintController.onAttack();
        }
        moon.module.player.scaffold.OpenB4Support.PacketUtils.observe(packet);
        return s.killAura.outgoing(packet);
    }
    /** Route PLAY traffic to one client-thread queue before applying or delaying packets. */
    @SuppressWarnings({"rawtypes","unchecked"})
    public static boolean receivePlayPacket(net.minecraft.network.NetworkManager manager,net.minecraft.network.INetHandler listener,net.minecraft.network.Packet<?> packet){
        McpModuleServices s=instance;
        if(s==null||s.closed||!(listener instanceof net.minecraft.client.network.NetHandlerPlayClient handler))return false;
        s.countPacket(true);
        Runnable receive=()->{
            if(s.closed||!manager.isChannelOpen()||handler.getNetworkManager()!=manager)return;
            if(s.mc.getNetHandler()!=null&&s.mc.getNetHandler()!=handler)return;
            if(s.myau.packet(packet,true))return;
            if(s.myau.delayIncoming(packet))return;
            if(packet instanceof net.minecraft.network.play.server.S08PacketPlayerPosLook){
                moon.module.player.scaffold.OpenB4Support.NoRotateModule.correctedAt=System.currentTimeMillis();s.killAura.correction();
            }
            scaffoldReceive(manager,packet);
            if(!s.backtrack.receive(handler,packet))((net.minecraft.network.Packet)packet).processPacket(handler);
        };
        if(Thread.currentThread()==s.thread)receive.run();else s.mc.addScheduledTask(receive);
        return true;
    }
    public static void observeSent(net.minecraft.network.NetworkManager manager,net.minecraft.network.Packet<?> packet){
        McpModuleServices s=instance;
        if(s==null||s.closed||!(packet instanceof net.minecraft.network.play.client.C02PacketUseEntity))return;
        Runnable observe=()->{
            if(!s.closed&&s.mc.getNetHandler()!=null&&s.mc.getNetHandler().getNetworkManager()==manager)s.backtrack.sent(packet);
        };
        if(Thread.currentThread()==s.thread)observe.run();else s.mc.addScheduledTask(observe);
    }
    public static boolean fastPlaceClearsDelay(){McpModuleServices s=instance;return s!=null&&!s.closed&&s.fastPlace.clearsDelay();}
    public static void backtrackFramePost(){McpModuleServices s=instance;if(s!=null&&!s.closed)s.backtrack.framePost();}
    public static void scaffoldReceive(net.minecraft.network.NetworkManager manager,net.minecraft.network.Packet<?> packet){
        McpModuleServices s=instance;
        if(s==null||s.closed||s.mc.thePlayer==null||manager!=s.mc.thePlayer.sendQueue.getNetworkManager())return;
        Runnable event=()->{
            if(!s.closed&&s.mc.thePlayer!=null&&manager==s.mc.thePlayer.sendQueue.getNetworkManager())
                s.scaffold.packetReceive(packet);
        };
        if(Thread.currentThread()==s.thread)event.run();else s.mc.addScheduledTask(event);
    }
    public static void chat(String message){McpModuleServices s=instance;if(s!=null&&!s.closed)s.session.chat(message);}
    private boolean available() { return !closed&&mc.theWorld!=null&&mc.thePlayer!=null&&mc.thePlayer.isEntityAlive()&&mc.currentScreen==null; }
    private void check() { if(closed||Thread.currentThread()!=thread)throw new IllegalStateException("Modules require their live client thread"); }
    @Override public void close() {
        if(closed)return;check();
        try { config.flush(); } catch(IOException|RuntimeException failure) { LOG.error("Could not save module settings on shutdown",failure); }
        try { for(NativeModule module:modules)module.close();myau.close();killAura.close();scaffold.close();velocity.close(); }
        finally { closed=true;attackCancellations.clear();combatCancellations.clear();if(instance==this)instance=null; }
    }
    /** Original cV: Ignore blocking allows queued attacks through the item-use branch. */
    public static boolean attackWhileUsingItem() {
        McpModuleServices s=instance;
        return s!=null&&!s.closed&&s.killAura.enabled()&&s.killAura.config.ignoreBlocking.get()&&s.killAura.target()!=null;
    }
    /** Original cS cancellation is evaluated at actual dispatch, before the swing. */
    public static boolean leftClickCancelled(Entity entity) {
        McpModuleServices s=instance;
        return s!=null&&!s.closed&&entity instanceof EntityLivingBase living
                &&s.attackCancellations.stream().anyMatch(listener->listener.test(living));
    }
    public static void leftClickAccepted() {
        McpModuleServices s=instance;if(s!=null&&!s.closed)s.killAura.realClickAttackAccepted();
    }
    /** Receiver for original ea's ds publication after a time-bank burst finishes. */
    public static void tickbaseBurstFinished() {
        McpModuleServices s=instance;if(s!=null&&!s.closed){s.check();s.killAura.forceClickOnTickbase();}
    }
    /** dy/dG rotations and dF reach, applied to the live ray at input consumption.
     * Preserve the camera's objectMouseOver; do not keep a stale hit from pulse generation.
     */
    public static MovingObjectPosition leftClickHit(MovingObjectPosition vanilla) {
        McpModuleServices s=instance;
        if(s==null||!s.available()||!s.killAura.enabled()||!s.killAura.config.realClick.get())return vanilla;
        Angles angles=s.killAura.hasRotation()?s.killAura.angles():null;
        if(angles==null)angles=new Angles(s.mc.thePlayer.rotationYaw,s.mc.thePlayer.rotationPitch);
        return s.clickRay(angles,s.killAura.config.reach.get(),true);
    }
    private MovingObjectPosition clickRay(Angles angles,float range) {
        return clickRay(angles,range,false);
    }
    private MovingObjectPosition clickRay(Angles angles,float range,boolean picking) {
        Vec3 eye=mc.thePlayer.getPositionEyes(1);
        double yaw=Math.toRadians(angles.yaw()),pitch=Math.toRadians(angles.pitch());
        Vec3 look=new Vec3(-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch));
        double blockRange=picking?mc.playerController.getBlockReachDistance():range;
        Vec3 blockEnd=eye.addVector(look.xCoord*blockRange,look.yCoord*blockRange,look.zCoord*blockRange);
        MovingObjectPosition wall=mc.theWorld.rayTraceBlocks(eye,blockEnd,false,false,true);
        double scan=blockRange,nearest=blockRange;
        boolean gate=false;
        if(picking) {
            if(mc.playerController.extendedReach())scan=nearest=6;
            else gate=scan>3;
            // Original kU publishes dF here; extending entities does not extend the block ray.
            if(range>4.5)scan=6;
        }
        if(wall!=null)nearest=eye.distanceTo(wall.hitVec);
        Vec3 end=eye.addVector(look.xCoord*scan,look.yCoord*scan,look.zCoord*scan);
        Entity hit=null;Vec3 point=null;
        double distance=nearest;
        for(Entity entity:mc.theWorld.getEntitiesInAABBexcluding(mc.thePlayer,
                mc.thePlayer.getEntityBoundingBox().addCoord(look.xCoord*scan,look.yCoord*scan,look.zCoord*scan).expand(1,1,1),
                e->e.canBeCollidedWith()&&EntitySelectors.NOT_SPECTATING.apply(e))) {
            float border=entity.getCollisionBorderSize();AxisAlignedBB box=entity.getEntityBoundingBox().expand(border,border,border);
            MovingObjectPosition intercept=box.calculateIntercept(eye,end);
            if(box.isVecInside(eye)) {
                if(distance>=0){hit=entity;point=intercept==null?eye:intercept.hitVec;distance=0;}
            } else if(intercept!=null) {
                double candidate=eye.distanceTo(intercept.hitVec);
                if(candidate<distance||distance==0) {
                    if(entity==mc.thePlayer.ridingEntity){if(distance==0){hit=entity;point=intercept.hitVec;}}
                    else {hit=entity;point=intercept.hitVec;distance=candidate;}
                }
            }
        }
        if(hit!=null&&gate&&eye.distanceTo(point)>range)
            return new MovingObjectPosition(MovingObjectPosition.MovingObjectType.MISS,point,null,new BlockPos(point));
        if(hit!=null&&(distance<nearest||wall==null))return new MovingObjectPosition(hit,point);
        return wall!=null?wall:new MovingObjectPosition(MovingObjectPosition.MovingObjectType.MISS,end,null,new BlockPos(end));
    }
    /** eZ LATEST item table, checked through original e0 enum mapping and item subclasses. */
    public static double auraAttackSpeed(Item item) {
        if(item instanceof ItemSword)return 1.6;
        if(item instanceof ItemSpade)return 1;
        if(item instanceof ItemPickaxe)return 1.2;
        if(item instanceof ItemAxe axe)return switch(axe.getToolMaterial()) {
            case STONE,WOOD -> .8;case IRON -> .9;default -> 1;
        };
        if(item instanceof ItemHoe hoe)return switch(hoe.getMaterialName()) {
            case "WOOD","GOLD" -> 1;case "STONE" -> 2;case "IRON" -> 3;default -> 4;
        };
        return 4;
    }
    private final class AuraHost implements KillAuraModule.Host {
        public Player player() {
            EntityPlayerSP p=mc.thePlayer;
            return p==null?null:new Player(p.posX,p.posY,p.posZ,p.getEyeHeight(),p.rotationYaw,p.rotationPitch);
        }
        public Collection<Target> targets() {
            if(mc.theWorld==null)return List.of();
            List<Target> targets=new ArrayList<>();
            for(Entity e:mc.theWorld.loadedEntityList) {
                if(!(e instanceof EntityLivingBase living)||e==mc.thePlayer||e instanceof EntityArmorStand)continue;
                TargetKind kind=e instanceof EntityPlayer?TargetKind.PLAYERS:
                    e instanceof EntityAnimal||e instanceof EntityAmbientCreature||e instanceof EntityWaterMob?TargetKind.ANIMALS:TargetKind.MOBS;
                targets.add(new Target(e.getEntityId(),kind,e.posX,e.posY,e.posZ,e.height,living.getHealth(),e.isInvisible(),
                    !e.isEntityAlive()||e instanceof EntityPlayer p&&p.isSpectator()||rejected.test(living),preferred.test(living)));
            }
            return targets;
        }
        public boolean available() { return McpModuleServices.this.available(); }
        public long currentTimeMillis() { return System.currentTimeMillis(); }
        public int hurtResistantTime() { return mc.thePlayer==null?0:mc.thePlayer.hurtResistantTime; }
        public int targetHurtResistantTime(int entityId) {
            Entity e=mc.theWorld==null?null:mc.theWorld.getEntityByID(entityId);return e==null?0:e.hurtResistantTime;
        }
        public double heldItemAttackSpeed() {
            ItemStack stack=mc.thePlayer==null?null:mc.thePlayer.getHeldItem();return auraAttackSpeed(stack==null?null:stack.getItem());
        }
        public int selectedSlot() { return mc.thePlayer.inventory.currentItem; }
        public void sendHeldItemChange(int slot) { mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(slot)); }
        public void sendUseItemPacket() { mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem())); }
        public void sendWatchdogPlacement() {
            mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getPosition().down(),EnumFacing.UP.getIndex(),new ItemStack(Items.bed),0,1,0));
        }
        public void releaseUseItemPacket() {
            if(mc.thePlayer!=null&&mc.thePlayer.sendQueue!=null)
                mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,BlockPos.ORIGIN,EnumFacing.DOWN));
        }
        public boolean cancelledByOtherModule() { return combatCancellations.stream().anyMatch(BooleanSupplier::getAsBoolean); }
        public boolean swordHeld() { return mc.thePlayer!=null&&mc.thePlayer.getHeldItem()!=null&&mc.thePlayer.getHeldItem().getItem() instanceof ItemSword; }
        public void block() { if(swordHeld())mc.playerController.sendUseItem(mc.thePlayer,mc.theWorld,mc.thePlayer.getHeldItem()); }
        public void useKey(boolean pressed) { KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(),pressed); }
        public void unblock() {
            if(mc.thePlayer!=null&&mc.thePlayer.sendQueue!=null) {
                releaseUseItemPacket();
                mc.thePlayer.stopUsingItem();
            }
        }
        public boolean rayHits(int entityId,Angles angles,float range) {
            if(mc.thePlayer==null||mc.theWorld==null)return false;
            MovingObjectPosition hit=clickRay(angles,range);
            return hit!=null&&hit.typeOfHit==MovingObjectPosition.MovingObjectType.ENTITY&&hit.entityHit.getEntityId()==entityId;
        }
        public void swingMiss() { if(available())mc.thePlayer.swingItem(); }
        public void stopSprintOnAttack() {
            if(!available())return;
            // Schedule the post-attack Ctrl recovery; do not clear sprint here or the
            // vanilla sprint-attack slowdown (motion * 0.6 + sprint reset) would be skipped.
            sprintController.onAttack();
        }
        public boolean attack(int entityId,boolean realClick,boolean ignoreBlocking) {
            if(!available())return false;
            if(realClick) {
                // eZ.d: both onTick calls enqueue presses, including the release edge.
                int key=mc.gameSettings.keyBindAttack.getKeyCode();
                KeyBinding.setKeyBindState(key,true);KeyBinding.onTick(key);
                KeyBinding.setKeyBindState(key,false);KeyBinding.onTick(key);
                return false;
            }
            Entity e=mc.theWorld.getEntityByID(entityId);
            if(!(e instanceof EntityLivingBase living)||!living.isEntityAlive())return false;

            if(attackCancellations.stream().anyMatch(listener -> listener.test(living)))return false;
            if(killAura.openB4.silentSwing())mc.thePlayer.sendQueue.addToSendQueue(new net.minecraft.network.play.client.C0APacketAnimation());else mc.thePlayer.swingItem();mc.playerController.attackEntity(mc.thePlayer,living);return true;
        }
    }
}
