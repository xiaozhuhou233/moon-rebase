package moon.client;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import moon.module.NativeModule;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import moon.module.combat.killaura.KillAuraRotations.Angles;
import moon.port.myau.Myau;
import moon.port.myau.event.EventManager;
import moon.port.myau.event.events.callables.EventCancellable;
import moon.port.myau.event.types.EventType;
import moon.port.myau.events.*;
import moon.port.myau.management.*;
import moon.port.myau.module.Module;
import moon.port.myau.module.modules.KillAura;
import moon.port.myau.module.modules.Scaffold;
import moon.port.myau.module.modules.Velocity;
import moon.port.myau.property.Property;
import moon.port.myau.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.util.*;

/** Moon lifecycle/event boundary. Requested algorithms live in the attributed source files. */
public final class McpMyauModules implements AutoCloseable {
    private final Minecraft mc;
    public final KillAura auraLogic=new KillAura();
    public final Scaffold scaffoldLogic=new Scaffold();
    public final Velocity velocityLogic=new Velocity();
    public final NativeModule killAura,scaffold,velocity;
    private final MovementFix movementFix=new MovementFix();
    private final moon.port.myau.util.MovementTicks movementTicks=new moon.port.myau.util.MovementTicks();
    private final SlotComponent slot=new SlotComponent();
    private final Field reportYaw=field(EntityPlayerSP.class,"lastReportedYaw");
    private final Field reportPitch=field(EntityPlayerSP.class,"lastReportedPitch");
    private final Method scaffoldStopsSprint=method(Scaffold.class,"shouldStopSprint");
    private UpdateEvent plan;
    private Object lastWorld;
    private int preparedTick=-1;
    public McpMyauModules(Minecraft mc,MarkerHooks hooks){
        this.mc=mc;
        Myau.moduleManager.modules.put(KillAura.class,auraLogic);
        Myau.moduleManager.modules.put(Scaffold.class,scaffoldLogic);
        Myau.moduleManager.modules.put(Velocity.class,velocityLogic);
        killAura=new Adapter("myau-killaura","KillAura",NativeModule.Category.COMBAT,auraLogic,hooks);
        scaffold=new Adapter("myau-scaffold","Scaffold",NativeModule.Category.PLAYER,scaffoldLogic,hooks);
        velocity=new Adapter("myau-velocity","Velocity",NativeModule.Category.COMBAT,velocityLogic,hooks);
        for(Object listener:listeners())EventManager.register(listener);
    }
    private List<Object> listeners(){return List.of(auraLogic,scaffoldLogic,velocityLogic,movementFix,movementTicks,slot,Myau.rotationManager,Myau.blinkManager,Myau.delayManager);}
    private static Field field(Class<?> type,String name){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static Method method(Class<?> type,String name){try{Method m=type.getDeclaredMethod(name);m.setAccessible(true);return m;}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    public boolean allowsSprint(){if(!scaffold.enabled())return true;try{return !(Boolean)scaffoldStopsSprint.invoke(scaffoldLogic);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    public boolean active(){return killAura.enabled()||scaffold.enabled()||velocity.enabled();}
    private boolean ready(){return active()&&mc.thePlayer!=null&&mc.theWorld!=null&&mc.thePlayer.isEntityAlive();}
    public void tick(boolean pre){
        if(mc.thePlayer==null||mc.theWorld==null)return;
        if(pre&&lastWorld!=mc.theWorld){worldChanged();lastWorld=mc.theWorld;}
        EventManager.call(new TickEvent(pre?EventType.PRE:EventType.POST));
    }
    public void beforeUpdate(){
        if(lastWorld!=mc.theWorld){worldChanged();lastWorld=mc.theWorld;}
        if(!ready()){plan=null;if(mc.thePlayer!=null)RotationState.applyState(false,mc.thePlayer.rotationYaw,mc.thePlayer.rotationPitch,mc.thePlayer.rotationYaw,-1);return;}
        if(preparedTick==mc.thePlayer.ticksExisted)return;
        preparedTick=mc.thePlayer.ticksExisted;
        try{plan=new UpdateEvent(EventType.PRE,reportYaw.getFloat(mc.thePlayer),reportPitch.getFloat(mc.thePlayer),mc.thePlayer.rotationYaw,mc.thePlayer.rotationPitch);}
        catch(IllegalAccessException e){throw new IllegalStateException(e);}
        EventManager.call(plan);
        RotationState.applyState(plan.isRotated()&&!mc.thePlayer.isRiding(),plan.getNewYaw(),plan.getNewPitch(),plan.getPreYaw(),plan.isRotating());
    }
    public void afterInput(){if(ready())EventManager.call(new MoveInputEvent());}
    public void beforeLiving(){if(ready())EventManager.call(new LivingUpdateEvent());}
    public void beforeWalking(){if(ready())EventManager.call(new PlayerUpdateEvent());}
    public Angles angles(){return ready()&&plan!=null&&plan.isRotated()?new Angles(plan.getNewYaw(),plan.getNewPitch()):null;}
    public NativeModule rotationSource(){return scaffold.enabled()?scaffold:killAura;}
    public float physicsYaw(float fallback){return ready()&&RotationState.isActived()?RotationState.getSmoothedYaw():fallback;}
    public float[] strafe(float strafe,float forward,float friction){
        if(!ready())return new float[]{strafe,forward,friction};
        StrafeEvent event=new StrafeEvent(strafe,forward,friction);EventManager.call(event);
        return new float[]{event.getStrafe(),event.getForward(),event.getFriction()};
    }
    public float jumpYaw(float fallback){if(!ready())return fallback;JumpEvent event=new JumpEvent(physicsYaw(fallback));EventManager.call(event);return event.getYaw();}
    public void postUpdate(){
        if(!ready())return;
        EventManager.call(new UpdateEvent(EventType.POST,plan==null?mc.thePlayer.rotationYaw:plan.getNewYaw(),plan==null?mc.thePlayer.rotationPitch:plan.getNewPitch(),mc.thePlayer.rotationYaw,mc.thePlayer.rotationPitch));
    }
    public boolean safeWalk(){if(!ready())return false;SafeWalkEvent event=new SafeWalkEvent(false);EventManager.call(event);return event.isSafeWalk();}
    public boolean packet(Packet<?> packet,boolean inbound){
        if(!ready())return false;
        PacketEvent event=new PacketEvent(inbound?EventType.RECEIVE:EventType.SEND,packet);EventManager.call(event);
        if(!inbound&&!event.isCancelled()){
            Myau.playerStateManager.handlePacket(packet);
            return Myau.blinkManager.isBlinking()&&Myau.blinkManager.offerPacket(packet);
        }
        return event.isCancelled();
    }
    @SuppressWarnings("unchecked") public boolean delayIncoming(Packet<?> packet){return Myau.delayManager.shouldDelay((Packet<net.minecraft.network.play.INetHandlerPlayClient>)packet);}
    public double[] knockback(double x,double y,double z){
        if(!ready())return new double[]{x,y,z};
        KnockbackEvent event=new KnockbackEvent(x,y,z);EventManager.call(event);
        return new double[]{event.getX(),event.getY(),event.getZ()};
    }
    public boolean click(int button){if(!ready())return false;EventCancellable event=button==0?new LeftClickMouseEvent():new RightClickMouseEvent();EventManager.call(event);return event.isCancelled();}
    public boolean hitBlock(){if(!ready())return false;HitBlockEvent event=new HitBlockEvent();EventManager.call(event);return event.isCancelled();}
    public boolean cancelUse(){if(!ready())return false;CancelUseEvent event=new CancelUseEvent();EventManager.call(event);return event.isCancelled();}
    public boolean swap(int slot,int offset){if(!ready())return false;SwapItemEvent event=new SwapItemEvent(slot,offset);EventManager.call(event);return event.isCancelled();}
    public void render2D(float ticks){if(ready())EventManager.call(new Render2DEvent(ticks));}
    public void render3D(float ticks){if(ready())EventManager.call(new Render3DEvent(ticks));}
    public EntityLivingBase target(){return killAura.enabled()?auraLogic.getTarget():null;}
    public boolean blockPose(){return mc.thePlayer!=null&&killAura.enabled()&&(auraLogic.isBlocking()||auraLogic.isPlayerBlocking());}
    public int displayedSlot(){return scaffold.enabled()&&scaffoldLogic.itemSpoof.getValue()?scaffoldLogic.getSlot():-1;}
    public static final class RenderSlot implements AutoCloseable {
        private final EntityPlayerSP player;private final int original;
        public RenderSlot(EntityPlayerSP player,int slot){this.player=player;original=player==null?-1:player.inventory.currentItem;if(player!=null&&slot>=0&&slot<9)player.inventory.currentItem=slot;}
        public void close(){if(player!=null)player.inventory.currentItem=original;}
    }
    public void worldChanged(){
        Myau.delayManager.delayedPacket.clear();Myau.delayManager.delay(moon.port.myau.enums.DelayModules.NONE);moon.port.myau.util.MovementTicks.reset();
        EventManager.call(new LoadWorldEvent());
        plan=null;preparedTick=-1;SlotComponent.reset();LateRotation.clear();Myau.rotationManager.clear();moon.port.myau.util.RiseRotation.reset();
        for(Module module:List.of(auraLogic,scaffoldLogic,velocityLogic))if(module.isEnabled()){module.setEnabled(false);module.setEnabled(true);}
    }
    @Override public void close(){killAura.close();scaffold.close();velocity.close();for(Object o:listeners())EventManager.unregister(o);Myau.blinkManager.setBlinkState(false,Myau.blinkManager.getBlinkingModule());}

    private final class Adapter extends NativeModule {
        private final Module original;
        private final List<SettingValue<?>> settings;
        Adapter(String key,String name,Category category,Module original,MarkerHooks hooks){
            super(key,name,category);this.original=original;
            Map<String,List<SettingValue<?>>> groups=new LinkedHashMap<>();
            for(Field f:original.getClass().getFields())try{
                if(f.get(original) instanceof Property<?> property){property.setOwner(original);groups.computeIfAbsent(group(original,property.getName()),x->new ArrayList<>()).add(adapt(property,hooks));}
            }catch(IllegalAccessException e){throw new IllegalStateException(e);}
            List<SettingValue<?>> roots=new ArrayList<>();
            groups.forEach((title,children)->roots.add(SettingGroup.builder(hooks).configure(title,children.toArray(SettingValue[]::new)).build()));
            settings=List.copyOf(roots);
        }
        public List<? extends SettingValue<?>> settings(){return settings;}
        public String hudSuffix(){return String.join(" ",original.getSuffix());}
        protected void onEnable(){original.setEnabled(true);preparedTick=-1;}
        protected void onDisable(){original.setEnabled(false);if(original==velocityLogic){if(Myau.delayManager.getDelayModule()==moon.port.myau.enums.DelayModules.VELOCITY)Myau.delayManager.setDelayState(false,moon.port.myau.enums.DelayModules.VELOCITY);moon.port.myau.util.KeyBindUtil.updateKeyState(mc.gameSettings.keyBindJump.getKeyCode());}plan=null;preparedTick=-1;if(!auraLogic.isEnabled()&&!scaffoldLogic.isEnabled())Myau.rotationManager.clear();}
        public void worldChanged(){McpMyauModules.this.worldChanged();}
    }
    private static String title(String key){StringBuilder b=new StringBuilder();for(String word:key.split("-")){if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));}return b.toString();}
    private static String group(Module module,String key){
        if(module instanceof Velocity){
            if(key.startsWith("mw-"))return "Watchdog";
            if(key.startsWith("delay-"))return "Delay";
            if(key.startsWith("explosions-"))return "Explosions";
            if(key.startsWith("jump-")||key.startsWith("require-"))return "Jump Reset";
            if(Set.of("chance","horizontal","vertical","fake-check").contains(key))return "Knockback";
        }
        return group(key);
    }
    private static String group(String key){
        if(key.contains("rotation")||key.contains("move-fix")||key.contains("smoothing")||key.contains("multi-point")||key.contains("angle-step")||key.startsWith("watchdog-yaw"))return "Rotations / MoveFix";
        if(key.contains("auto-block")||key.contains("watchdog-short")||key.contains("watchdog-block"))return "Auto Block";
        if(key.contains("keep-y")||key.equals("tower"))return "Tower / Keep Y";
        if(key.contains("motion")||key.equals("sprint")||key.equals("safe-walk"))return "Movement";
        if(Set.of("players","bosses","mobs","animals","golems","silverfish","teams","through-walls","fov").contains(key))return "Targets";
        if(key.equals("show-target")||key.equals("debug")||key.equals("debug-log")||key.equals("block-counter"))return "Display";
        return "Main";
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static SettingValue<?> adapt(Property p,MarkerHooks hooks){
        String key=p.getName(),title=title(key);Object initial=p.getValue();SettingValue setting;
        boolean mode=p instanceof ModeProperty;
        if(mode){ModeProperty m=(ModeProperty)p;Class c=MODE_TYPES[m.getModeCount()];
            setting=EnumSetting.builder(key,c,hooks).configure(title,(Enum)c.getEnumConstants()[(Integer)initial]).labels(v->m.getModes()[((Enum)v).ordinal()]).build();
        }else if(p instanceof BooleanProperty)setting=BooleanSetting.builder(key,hooks).configure(title,(Boolean)initial).build();
        else if(p instanceof FloatProperty f)setting=FloatSetting.builder(key,hooks).configure(title,((Number)initial).floatValue(),f.getMinimum(),f.getMaximum(),f.getStep()>0?f.getStep():.01F).build();
        else if(p instanceof IntProperty i)setting=IntegerSetting.builder(key,hooks).configure(title,((Number)initial).intValue(),i.getMinimum(),i.getMaximum(),1).build();
        else if(p instanceof PercentProperty)setting=IntegerSetting.builder(key,hooks).configure(title,((Number)initial).intValue(),0,100,1).build();
        else throw new IllegalArgumentException("Unmapped Myau property "+p.getClass()+" / "+key);
        setting.setVisibility(p::isVisible);SettingValue bound=setting;boolean[] inside={false};
        p.bind(null,v->{if(!inside[0]){inside[0]=true;try{bound.set(mode?boundEnum(bound,((Number)v).intValue()):v);}finally{inside[0]=false;}}});
        setting.setNormalizer(v->{if(inside[0])return v;inside[0]=true;try{p.setValue(mode?((Enum)v).ordinal():v);return mode?boundEnum(bound,((Number)p.getValue()).intValue()):p.getValue();}finally{inside[0]=false;}});
        return setting;
    }
    private static Object boundEnum(SettingValue<?> setting,int index){return ((EnumSetting<?>)setting).values()[index];}
    // Each menu has exactly its upstream option count; labels come from ModeProperty.
    private enum Mode1 implements DisplayNamed {V0;public String displayName(){return name();}}
    private enum Mode2 implements DisplayNamed {V0,V1;public String displayName(){return name();}}
    private enum Mode3 implements DisplayNamed {V0,V1,V2;public String displayName(){return name();}}
    private enum Mode4 implements DisplayNamed {V0,V1,V2,V3;public String displayName(){return name();}}
    private enum Mode5 implements DisplayNamed {V0,V1,V2,V3,V4;public String displayName(){return name();}}
    private enum Mode6 implements DisplayNamed {V0,V1,V2,V3,V4,V5;public String displayName(){return name();}}
    private enum Mode7 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6;public String displayName(){return name();}}
    private enum Mode8 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7;public String displayName(){return name();}}
    private enum Mode9 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8;public String displayName(){return name();}}
    private enum Mode10 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9;public String displayName(){return name();}}
    private enum Mode11 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10;public String displayName(){return name();}}
    private enum Mode12 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11;public String displayName(){return name();}}
    private enum Mode13 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12;public String displayName(){return name();}}
    private enum Mode14 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13;public String displayName(){return name();}}
    private enum Mode15 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14;public String displayName(){return name();}}
    private enum Mode16 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15;public String displayName(){return name();}}
    private enum Mode17 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15,V16;public String displayName(){return name();}}
    private enum Mode18 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15,V16,V17;public String displayName(){return name();}}
    private enum Mode19 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15,V16,V17,V18;public String displayName(){return name();}}
    private enum Mode20 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15,V16,V17,V18,V19;public String displayName(){return name();}}
    private enum Mode21 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15,V16,V17,V18,V19,V20;public String displayName(){return name();}}
    private enum Mode22 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15,V16,V17,V18,V19,V20,V21;public String displayName(){return name();}}
    private enum Mode23 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15,V16,V17,V18,V19,V20,V21,V22;public String displayName(){return name();}}
    private enum Mode24 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15,V16,V17,V18,V19,V20,V21,V22,V23;public String displayName(){return name();}}
    private enum Mode25 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15,V16,V17,V18,V19,V20,V21,V22,V23,V24;public String displayName(){return name();}}
    private enum Mode26 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15,V16,V17,V18,V19,V20,V21,V22,V23,V24,V25;public String displayName(){return name();}}
    private enum Mode27 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15,V16,V17,V18,V19,V20,V21,V22,V23,V24,V25,V26;public String displayName(){return name();}}
    private enum Mode28 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15,V16,V17,V18,V19,V20,V21,V22,V23,V24,V25,V26,V27;public String displayName(){return name();}}
    private enum Mode29 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15,V16,V17,V18,V19,V20,V21,V22,V23,V24,V25,V26,V27,V28;public String displayName(){return name();}}
    private enum Mode30 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15,V16,V17,V18,V19,V20,V21,V22,V23,V24,V25,V26,V27,V28,V29;public String displayName(){return name();}}
    private enum Mode31 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15,V16,V17,V18,V19,V20,V21,V22,V23,V24,V25,V26,V27,V28,V29,V30;public String displayName(){return name();}}
    private enum Mode32 implements DisplayNamed {V0,V1,V2,V3,V4,V5,V6,V7,V8,V9,V10,V11,V12,V13,V14,V15,V16,V17,V18,V19,V20,V21,V22,V23,V24,V25,V26,V27,V28,V29,V30,V31;public String displayName(){return name();}}
    private static final Class<?>[] MODE_TYPES={null,Mode1.class,Mode2.class,Mode3.class,Mode4.class,Mode5.class,Mode6.class,Mode7.class,Mode8.class,Mode9.class,Mode10.class,Mode11.class,Mode12.class,Mode13.class,Mode14.class,Mode15.class,Mode16.class,Mode17.class,Mode18.class,Mode19.class,Mode20.class,Mode21.class,Mode22.class,Mode23.class,Mode24.class,Mode25.class,Mode26.class,Mode27.class,Mode28.class,Mode29.class,Mode30.class,Mode31.class,Mode32.class};
}
