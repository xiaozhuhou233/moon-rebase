package moon.module.movement;

import java.util.List;
import moon.module.NativeModule;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;

/** Alpha jr / aN, with the eight original mode implementations kept in this file. */
public final class SpeedModule extends NativeModule {
    public enum Mode implements DisplayNamed {
        VANILLA("Vanilla"), NCP("NCP"), WATCHDOG("Watchdog"), LEGIT("Legit"),
        VANILLA_BHOP("Vanilla Bhop"), VULCAN("Vulcan"), VERUS("Verus"), STRAFE("Strafe");
        private final String title;
        Mode(String title) { this.title=title; }
        public String displayName() { return title; }
    }
    public enum VerusMode implements DisplayNamed {
        GROUND, HOP;
        public String displayName() { return this==GROUND?"Ground":"Hop"; }
    }
    public final EnumSetting<Mode> mode;
    public final BooleanSetting damageBoost, fast;
    public final FloatSetting damageBoostAmount;
    public final EnumSetting<VerusMode> verusMode;
    private final Minecraft mc;
    private final List<SettingValue<?>> settings;
    private Mode activeMode;
    private VerusMode activeVerus;
    private boolean ncpJumped;
    private double ncpSpeed, lastDistance, strafeDistance, vulcanSpeed;
    private int strafeStage, vulcanTicks, damageTick=-1;
    private double verusAirTicks, verusMoveTicks, verusHopTicks, cameraY=Double.NaN;
    private boolean packetGround;

    public SpeedModule(Minecraft mc, MarkerHooks hooks) {
        super("speed","Speed",Category.PLAYER);
        this.mc=mc;
        mode=EnumSetting.builder("mode",Mode.class,hooks).configure("Mode",Mode.VANILLA).build();
        damageBoost=BooleanSetting.builder("wd-damage-boost",hooks).configure("Damage Boost",false)
            .visibility(()->mode.get()==Mode.WATCHDOG).build();
        damageBoostAmount=FloatSetting.builder("wd-damage-boost-amount",hooks).configure("Damage Boost Amount",2.5F,1F,3F,.25F)
            .visibility(()->mode.get()==Mode.WATCHDOG&&damageBoost.get()).build();
        verusMode=EnumSetting.builder("verus-mode",VerusMode.class,hooks).configure("Verus Mode",VerusMode.HOP)
            .visibility(()->mode.get()==Mode.VERUS).build();
        fast=BooleanSetting.builder("strafe-fast",hooks).configure("Fast",true)
            .visibility(()->mode.get()==Mode.STRAFE).build();
        settings=List.of(mode,damageBoost,damageBoostAmount,verusMode,fast);
    }
    public List<? extends SettingValue<?>> settings() { return settings; }
    public String hudSuffix() { return mode.get().displayName(); }
    protected void onEnable() { reset(); }
    protected void onDisable() { leaveMode();reset(); }
    public void worldChanged() { reset(); }
    private void reset() {
        activeMode=null;activeVerus=null;ncpJumped=false;ncpSpeed=lastDistance=strafeDistance=vulcanSpeed=0;
        strafeStage=vulcanTicks=0;damageTick=-1;verusAirTicks=verusMoveTicks=verusHopTicks=0;
        cameraY=mc.thePlayer==null?Double.NaN:mc.thePlayer.posY;packetGround=false;
    }
    private void leaveMode() {
        if(activeMode==Mode.VULCAN&&mc.thePlayer!=null) {
            if(mc.thePlayer.onGround)mc.thePlayer.motionY=.05F;
            mc.thePlayer.motionX=mc.thePlayer.motionZ=0;
        }
    }
    private boolean ready() {
        if(!enabled()||mc.thePlayer==null||mc.theWorld==null||!mc.thePlayer.isEntityAlive())return false;
        if(activeMode!=mode.get()||activeVerus!=verusMode.get()) {
            leaveMode();reset();activeMode=mode.get();activeVerus=verusMode.get();
        }
        return true;
    }
    /** tp.ae(): original checks the four physical movement keys, not a target's presence. */
    public boolean moving() {
        return mc.gameSettings.keyBindForward.isKeyDown()||mc.gameSettings.keyBindBack.isKeyDown()
            ||mc.gameSettings.keyBindLeft.isKeyDown()||mc.gameSettings.keyBindRight.isKeyDown();
    }
    /** c0 PRE is sent from onUpdateWalkingPlayer, after this tick's collision movement. */
    public void motionPre() {
        if(!ready())return;
        EntityPlayerSP p=mc.thePlayer;
        lastDistance=Math.hypot(p.posX-p.prevPosX,p.posZ-p.prevPosZ);
        packetGround=false;
        switch(activeMode) {
            case WATCHDOG -> {
                if(moving()&&p.onGround) {
                    boolean boost=!p.isInWater();p.jump();
                    if(boost) {
                        double multiplier=1.65;
                        if(p.isPotionActive(Potion.moveSpeed))multiplier*=1+.07*amplifier(Potion.moveSpeed);
                        setMotion(baseSpeed(.2,false)*multiplier);
                    }
                }
            }
            case VANILLA_BHOP -> { if(p.onGround&&moving())p.jump(); }
            case VULCAN -> {
                vulcanTicks++;
                if(p.onGround&&moving()){p.jump();p.motionY=.05F;vulcanTicks=0;}
            }
            case STRAFE -> {
                if(!fast.get()&&moving()) {
                    if(p.onGround)p.jump();else setMotion(Math.hypot(p.motionX,p.motionZ));
                }
                strafeDistance=lastDistance;
            }
            case VERUS -> {
                if(activeVerus==VerusMode.GROUND) {
                    if(p.posY%.015625==0)cameraY=p.posY;
                    if(moving()) {
                        verusMoveTicks++;
                        if(p.onGround){p.motionY=.42F;verusAirTicks=1;}
                        else if(++verusAirTicks<=10){p.motionY=0;packetGround=true;}
                    } else verusMoveTicks=0;
                } else {
                    if(p.onGround&&moving()){p.motionY=.42F;verusHopTicks=0;}
                    if(!p.onGround&&verusHopTicks==0){p.motionY=-.0784000015258789;verusHopTicks++;}
                }
            }
            default -> { }
        }
    }
    /** et listens to dH PRE (client tick), separately from c0 motion PRE. */
    public void tickPre() {
        if(ready()&&activeMode==Mode.LEGIT&&mc.thePlayer.onGround&&moving())mc.thePlayer.jump();
    }
    public boolean suppressVanillaJump() {
        return ready()&&(activeMode==Mode.LEGIT||activeMode==Mode.WATCHDOG);
    }
    public boolean packetGround(boolean original) { return ready()&&packetGround||original; }

    /** dz: TargetStrafe edits forward/strafe/yaw before the selected mode sets the speed. */
    public void move(Move move) {
        if(!ready())return;
        EntityPlayerSP p=mc.thePlayer;
        switch(activeMode) {
            case VANILLA, VANILLA_BHOP -> { if(moving())move.speed(1); }
            case NCP -> {
                if(!moving())break;
                double base=baseSpeed(.2,false);boolean potion=p.isPotionActive(Potion.moveSpeed);
                if(p.onGround&&!ncpJumped) {
                    ncpSpeed=base*(potion?2.08:2.14);
                    p.motionY=.4000000059604645;
                    if(p.isPotionActive(Potion.jump))p.motionY+=(amplifier(Potion.jump)+1)*.101F;
                    move.y=p.motionY;ncpJumped=true;
                } else if(ncpJumped) {
                    ncpJumped=false;ncpSpeed=lastDistance-(potion?.597:.78)*(lastDistance-base);
                } else ncpSpeed=ncpSpeed*.9100000262260437+.026000000536441803;
                move.speed(ncpSpeed);
            }
            case WATCHDOG -> {
                int elapsed=p.ticksExisted-damageTick;
                if(damageTick>=0&&elapsed>0&&elapsed<DAMAGE_BOOST_TICKS&&damageBoost.get()
                    &&!p.isBurning()&&!p.isInLava()&&!p.isInWater())
                    move.speed(baseSpeed(.2,false)*Math.max(damageBoostAmount.get()-elapsed*.08F,1F));
            }
            case VULCAN -> {
                if(!moving()){move.speed(0);break;}
                if(vulcanTicks==0)vulcanSpeed=baseSpeed(.45,false)*(double)1.2F;
                move.speed(Math.max(vulcanSpeed,vulcanTicks==0?baseSpeed(.23,false)*(double)2.16F:baseSpeed(.46,false)*1.13));
                vulcanSpeed*=(double).96F;
            }
            case VERUS -> {
                if(activeVerus==VerusMode.GROUND) {
                    if(verusMoveTicks>10) {
                        double speed=verusAirTicks<=8&&verusAirTicks>0?baseSpeed(.2,false)*.9+verusAirTicks*.15:baseSpeed(.2,false)*(double)1.3F;
                        if(!p.isSprinting())speed*=.8;
                        move.speed(speed);
                    }
                } else if(moving()) {
                    if(++verusMoveTicks>=2)move.speed(baseSpeed(.2,false)*(p.onGround?(double)2.38F:(double)1.4F));
                } else verusMoveTicks=0;
            }
            case STRAFE -> {
                if(!fast.get()||!moving())break;
                ++strafeStage;
                double speed=strafeDistance*.9100000262260437+.025999998673796654;
                if(strafeStage==1) {
                    if(p.onGround) {
                        float jump=.42F;
                        if(p.isPotionActive(Potion.jump))jump+=(amplifier(Potion.jump)+1)*.1F;
                        p.motionY=jump;move.y=p.motionY;
                    }
                } else if(strafeStage!=2&&(!mc.theWorld.getCollidingBoundingBoxes(p,p.getEntityBoundingBox().offset(0,p.motionY,0)).isEmpty()||p.isCollidedVertically))strafeStage=0;
                move.speed(Math.max(speed,baseSpeed(.2,true)));
            }
            default -> { }
        }
    }
    // eA uses (int)m, not the full decrypted long. Offline data-table candidate: 10 ticks.
    private static final int DAMAGE_BOOST_TICKS=10;
    public void velocity(double x,double z,boolean explosion) {
        if(ready()&&activeMode==Mode.WATCHDOG&&!explosion&&Math.hypot(x,z)>.1)damageTick=mc.thePlayer.ticksExisted;
    }
    private int amplifier(Potion potion) { return mc.thePlayer.getActivePotionEffect(potion).getAmplifier(); }
    private double baseSpeed(double potionFactor,boolean forceSprint) {
        double speed=mc.thePlayer.isSprinting()||forceSprint?.2873:.216;
        if(mc.thePlayer.isPotionActive(Potion.moveSpeed))speed*=1+potionFactor*amplifier(Potion.moveSpeed);
        return speed;
    }
    private void setMotion(double speed) {
        EntityPlayerSP p=mc.thePlayer;
        float angle=direction(p.rotationYaw,p.moveForward,p.moveStrafing);
        p.motionX=-Math.sin(angle)*speed;p.motionZ=Math.cos(angle)*speed;
    }
    public static float direction(float yaw,float forward,float strafe) {
        float turn=forward<0?-45:forward==0?90:45;
        if(forward<0)yaw+=180;
        if(strafe<0)yaw+=turn;else if(strafe>0)yaw-=turn;
        return (float)(yaw*(Math.PI/180));
    }
    public static final class Move {
        public double x,y,z;
        public float forward,strafe,yaw;
        public Move(double x,double y,double z,float forward,float strafe,float yaw) {
            this.x=x;this.y=y;this.z=z;this.forward=forward;this.strafe=strafe;this.yaw=yaw;
        }
        public void speed(double speed) {
            float angle=direction(yaw,forward,strafe);
            x=-MathHelper.sin(angle)*speed;z=MathHelper.cos(angle)*speed;
        }
    }
    /** ey/dQ affects the render-view position only, never outgoing movement packet Y. */
    public CameraPosition cameraPosition() {
        Entity view=mc.getRenderViewEntity();
        boolean apply=ready()&&activeMode==Mode.VERUS&&activeVerus==VerusMode.GROUND
            &&view==mc.thePlayer&&Double.isFinite(cameraY);
        return new CameraPosition(apply?view:null,cameraY);
    }
    public static final class CameraPosition implements AutoCloseable {
        private final Entity view;private final double original;
        public CameraPosition(Entity view,double y) { this.view=view;original=view==null?0:view.posY;if(view!=null)view.posY=y; }
        public void close() { if(view!=null)view.posY=original; }
    }
}
