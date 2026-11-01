package moon.port.myau.module.modules;
import moon.port.myau.module.Module;
import moon.port.myau.property.properties.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import java.awt.Color;
/** Optional upstream modules are absent and disabled. They are not GUI modules. */
public final class MyauPeers {
 public static class Absent extends Module { public Absent(){super("Not installed",false);} }
 public static class AutoHeal extends Absent {public boolean isSwitching(){return false;}}
 public static class BedNuker extends Absent {public boolean isReady(){return false;}}
 public static class LongJump extends Absent {public boolean isAutoMode(){return false;}public boolean isJumping(){return false;}public boolean canStartJump(){return false;}}
 public static class Speed extends Absent {}
 public static class Fly extends Absent {}
 public static class BlockIn extends Absent {public boolean isPlacing(){return false;}}
 public static class NoSlow extends Absent {public boolean isSwordActive(){return false;}public boolean isWatchdogReleaseTick(){return false;}}
 public static class HitSelect extends Absent {public boolean shouldBlockAttack(EntityLivingBase e){return false;}public void confirmHit(EntityLivingBase e){} }
 public static class KeepSprint extends Absent {
  public final PercentProperty slowdown=new PercentProperty("slowdown",0);
  public boolean shouldDeferAttack(EntityLivingBase e){return false;} public boolean shouldKeepSprint(){return false;}
  public boolean shouldDropSprintAfterHit(){return false;} public void confirmAttack(){}
 }
 public static class TargetStrafe extends Absent {public float getTargetYaw(){return Float.NaN;}}
 public static class AntiBot extends Absent {public static boolean isBot(EntityPlayer p){return false;}}
 public static class HUD extends Absent {
  public final FloatProperty scale=new FloatProperty("scale",1F,.5F,3F);
  public final BooleanProperty shadow=new BooleanProperty("shadow",true);
  public Color getColor(long time){return new Color(Color.HSBtoRGB((time%5000)/5000F,.6F,1));}
 }
}
