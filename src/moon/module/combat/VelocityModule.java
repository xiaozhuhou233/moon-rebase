package moon.module.combat;

import java.util.ArrayDeque;
import java.util.List;
import moon.module.NativeModule;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.DamageSource;

/** jf/ax/d3-d6: original four velocity modes, isolated from the old bootstrap.
 * c1 explosion is additive; its axis-cancel flags preserve existing motion.
 * Numeric evidence: moon-deobf/deliverables/velocity/CONSTANTS.json.
 */
public final class VelocityModule extends NativeModule {
    public enum Mode implements DisplayNamed {
        NORMAL,DELAYED,JUMP,GRIM;
        public String displayName(){return switch(this){
            case NORMAL->"Normal";case DELAYED->"Delayed";case JUMP->"Jump";case GRIM->"Grim (Flag)";
        };}
    }
    private record Pending(double x,double y,double z,boolean explosion,int tick) {}
    private final Minecraft mc;
    public final EnumSetting<Mode> mode;
    public final IntegerSetting horizontal,vertical,delay,duration,timeout;
    public final BooleanSetting reverseHorizontal,reverseVertical,allowCancel,stack,modifyExplosions;
    
    private final List<SettingValue<?>> settings;
    private final ArrayDeque<Pending> pending=new ArrayDeque<>();
    private boolean normalPending,jumpPending,grimPending;
    private Object world;

    public VelocityModule(Minecraft mc,MarkerHooks hooks){
        super("velocity","Velocity",Category.COMBAT);this.mc=mc;
        mode=EnumSetting.builder("mode",Mode.class,hooks).configure("Mode",Mode.NORMAL)
            .callback((old,next)->worldChanged()).build();
        horizontal=integer("horizontal-mod","Horizontal modifier",50,0,100,hooks,Mode.NORMAL);
        vertical=integer("vertical-mod","Vertical modifier",50,0,100,hooks,Mode.NORMAL);
        delay=integer("vertical-delay","Delay",0,0,10,hooks,Mode.NORMAL);
        // The original constructor supplies 0 with minimum 1; i2 clamps this to 1.
        duration=integer("modifier-duration","Modifier duration",1,1,10,hooks,Mode.NORMAL);
        reverseHorizontal=bool("reverse-horizontal","Reverse horizontal",false,hooks,Mode.NORMAL);
        reverseVertical=bool("reverse-vertical","Reverse vertical",false,hooks,Mode.NORMAL);
        allowCancel=bool("allow-cancel","Allow cancel",false,hooks,Mode.NORMAL);
        stack=bool("delayed-stack-velocities","Stack velocities",true,hooks,Mode.DELAYED);
        timeout=integer("delayed-timeout-ticks","Time-out",12,1,60,hooks,Mode.DELAYED);
        modifyExplosions=BooleanSetting.builder("modify-explosions",hooks).configure("Modify explosions",true).build();
        settings=List.of(mode,horizontal,vertical,delay,duration,reverseHorizontal,reverseVertical,allowCancel,stack,timeout,modifyExplosions);
    }
    private IntegerSetting integer(String key,String label,int value,int min,int max,MarkerHooks hooks,Mode visible){
        return IntegerSetting.builder(key,hooks).configure(label,value,min,max,1).visibility(()->mode.get()==visible).build();
    }
    private BooleanSetting bool(String key,String label,boolean value,MarkerHooks hooks,Mode visible){
        return BooleanSetting.builder(key,hooks).configure(label,value).visibility(()->mode.get()==visible).build();
    }
    public List<? extends SettingValue<?>> settings(){return settings;}
    @Override public String hudSuffix(){return mode.get()==Mode.NORMAL?horizontal.get()+"% "+vertical.get()+"%":mode.get().displayName();}
    public int queuedVelocities(){return pending.size();}
    @Override protected void onDisable(){worldChanged();}
    @Override public void worldChanged(){pending.clear();normalPending=jumpPending=grimPending=false;world=mc.theWorld;}
    private boolean ready(){
        if(world!=mc.theWorld)worldChanged();
        return enabled()&&mc.theWorld!=null&&mc.thePlayer!=null;
    }
    /** Returns true when this module has consumed or deferred the vanilla application. */
    public boolean receive(Entity entity,double x,double y,double z,boolean explosion){
        if(!ready()||entity!=mc.thePlayer)return false;
        // Original NetHandler publishes explosions only if at least one component is positive.
        if(explosion&&!(above(x,0)||above(y,0)||above(z,0)))return false;
        EntityPlayerSP player=mc.thePlayer;
        switch(mode.get()){
            case NORMAL -> {
                if(explosion&&!modifyExplosions.get())return false;
                normalPending=above(Math.sqrt(x*x+y*y+z*z),.05);
                if(delay.get()!=0)return false;
                double[] motion=modified(x,y,z);
                if(explosion){
                    if(!allowCancel.get()||horizontal.get()!=0){player.motionX+=motion[0];player.motionZ+=motion[2];}
                    if(!allowCancel.get()||vertical.get()!=0)player.motionY+=motion[1];
                }else{
                    player.setVelocity(allowCancel.get()&&horizontal.get()==0?player.motionX:motion[0],
                        allowCancel.get()&&vertical.get()==0?player.motionY:motion[1],
                        allowCancel.get()&&horizontal.get()==0?player.motionZ:motion[2]);
                }
                normalPending=false;return true;
            }
            case DELAYED -> {
                if(explosion&&!modifyExplosions.get())return false;
                int tick=player.ticksExisted;
                // d3 preserves the oldest timestamp but replaces its vector when stacking.
                if(stack.get()){
                    Pending first=pending.peek();if(first!=null)tick=first.tick;
                    pending.clear();
                }
                pending.add(new Pending(x,y,z,explosion,tick));return true;
            }
            case JUMP,GRIM -> {
                if(jumpPending){jumpPending=false;return false;}
                if(above(Math.hypot(x,z),.05)&&!explosion&&player.onGround){
                    jumpPending=true;if(mode.get()==Mode.GRIM)grimPending=true;
                }
                return false;
            }
        }
        return false;
    }
    /** JVM dcmpl/fcmpl put NaN above a finite threshold in the original branches. */
    private static boolean above(double value,double threshold){return !(value<=threshold);}
    private double[] modified(double x,double y,double z){
        double h=horizontal.get()/100D,v=vertical.get()/100D;
        // Original reversal gates on current player Y, not the incoming vector's Y.
        if(reverseVertical.get()&&above(mc.thePlayer.motionY,0))v=-v;
        if(reverseHorizontal.get())h=-h;
        return new double[]{x*h,y*v,z*h};
    }
    /** aj.BEFORE: runs before vanilla living movement, including the riding path. */
    public void beforeUpdate(){
        if(!ready()||(mode.get()!=Mode.JUMP&&mode.get()!=Mode.GRIM))return;
        EntityPlayerSP player=mc.thePlayer;
        if(player.ticksExisted<=2)jumpPending=false;
        if(player.onGround&&jumpPending){player.jump();jumpPending=false;}
        if(mode.get()!=Mode.GRIM)return;
        if(player.hurtTime<=0){if(player.hurtTime==0)grimPending=false;return;}
        if(!grimPending)return;
        EntityPlayer target=null;
        for(Entity entity:mc.theWorld.loadedEntityList){
            if(entity instanceof EntityPlayer candidate&&candidate!=player&&candidate.getEntityId()!=-84
                    &&!((float)player.getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue()<=0F)){target=candidate;break;}
        }
        boolean moving=mc.gameSettings.keyBindForward.isKeyDown()||mc.gameSettings.keyBindBack.isKeyDown()
            ||mc.gameSettings.keyBindLeft.isKeyDown()||mc.gameSettings.keyBindRight.isKeyDown();
        if(target!=null&&player.hurtTime<10&&moving&&player.isSprinting()){
            player.sendQueue.addToSendQueue(new C02PacketUseEntity(target,C02PacketUseEntity.Action.ATTACK));
            if(target.attackEntityFrom(DamageSource.causePlayerDamage(player),1F)){player.motionX*=.6;player.motionZ*=.6;}
            player.setSprinting(true);
        }
    }
    /** aj.PRE: same stage as the original on-foot walking packet preparation. */
    public void motionPre(){
        if(!ready())return;
        EntityPlayerSP player=mc.thePlayer;
        if(mode.get()==Mode.NORMAL&&normalPending){
            int high=20-delay.get(),low=high-(duration.get()-1);
            if(player.hurtResistantTime>=low&&player.hurtResistantTime<=high){
                double[] motion=modified(player.motionX,player.motionY,player.motionZ);
                player.motionX=motion[0];player.motionY=motion[1];player.motionZ=motion[2];
            }
        }else if(mode.get()==Mode.DELAYED){
            Pending next;
            while((next=pending.peek())!=null){
                if(player.ticksExisted-next.tick<timeout.get())break;
                if(next.explosion){player.motionX+=next.x;player.motionY+=next.y;player.motionZ+=next.z;}
                else {player.motionX=next.x;player.motionY=next.y;player.motionZ=next.z;}
                pending.remove();
            }
        }
    }
}
