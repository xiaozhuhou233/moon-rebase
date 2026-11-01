package moon.port.myau;
import java.util.*;
import moon.port.myau.module.Module;
import moon.port.myau.module.modules.MyauPeers.*;
import moon.port.myau.management.*;
public final class Myau {
 public static final String clientName="Moon";
 public static final Registry moduleManager=new Registry();
 public static final RotationManager rotationManager=new RotationManager();
 public static final BlinkManager blinkManager=new BlinkManager();
 public static final PlayerStateManager playerStateManager=new PlayerStateManager();
 public static final DelayManager delayManager=new DelayManager();
 public static final Names friendManager=new Names(),targetManager=new Names();
 public static final class Names {public final Set<String> names=new HashSet<>();public boolean isFriend(String name){return names.contains(name.toLowerCase(Locale.ROOT));}}
 public static final class Registry {
  public final Map<Class<?>,Module> modules=new HashMap<>();
  public Registry(){for(Module m:List.of(new AutoHeal(),new BedNuker(),new LongJump(),new BlockIn(),new NoSlow(),new Speed(),new Fly(),new HitSelect(),new KeepSprint(),new TargetStrafe(),new AntiBot(),new HUD()))modules.put(m.getClass(),m);}
 }
}
