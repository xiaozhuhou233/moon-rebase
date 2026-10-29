package moon.module.player.scaffoldwalk;

import java.util.List;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** jE runtime keys/defaults; enum order from aU/aV/aW. */
public final class ScaffoldSettings {
    public enum Rotation implements DisplayNamed { CALCULATED, BRUTEFORCE, BACKWARDS, STATIC; public String displayName(){return name();} }
    public enum Sprint implements DisplayNamed { NORMAL, HYPIXEL; public String displayName(){return name();} }
    public enum Tower implements DisplayNamed { HYPIXEL; public String displayName(){return name();} }
    public final FloatSetting radius,speedLimit;
    public final IntegerSetting rotationSpeed;
    public final BooleanSetting switchBlock,silent,tower,sprint,slowSprint,unsprint,keepY,safeWalk,correctMovement,limitSpeed,swing,realClick,checkBlock,rotate,keepRotation;
    public final EnumSetting<Rotation> rotation;
    public final EnumSetting<Sprint> sprintMode;
    public final EnumSetting<Tower> towerMode;
    private final List<SettingValue<?>> roots;
    public ScaffoldSettings(MarkerHooks h){
        radius=FloatSetting.builder("search-radius",h).configure("Search radius",3.5F,1,6,.5F).build();
        switchBlock=b(h,"switch-to-block",true);silent=b(h,"switch-silently",false);
        tower=b(h,"tower",false);towerMode=EnumSetting.builder("tower-mode",Tower.class,h).configure("Tower mode",Tower.HYPIXEL).build();
        sprint=b(h,"allow-sprinting",false);sprintMode=EnumSetting.builder("sprint-mode",Sprint.class,h).configure("Sprint mode",Sprint.NORMAL).build();
        slowSprint=b(h,"hypixel-slow-sprint",true);unsprint=b(h,"un-sprint-on-place",false);
        keepY=b(h,"keep-y",false);safeWalk=b(h,"safe-walk",false);correctMovement=b(h,"correct-movement",true);limitSpeed=b(h,"limit-movement-speed",false);
        speedLimit=FloatSetting.builder("speed-limit",h).configure("Speed limit",.2F,.01F,.5F,.001F).build();
        swing=b(h,"swing-after-place",false);realClick=b(h,"real-click",true);checkBlock=b(h,"check-block",true);
        rotate=b(h,"rotate",true);keepRotation=b(h,"keep-rotation",true);
        rotationSpeed=IntegerSetting.builder("rotation-speed",h).configure("Rotation speed",180,1,180,1).build();
        rotation=EnumSetting.builder("rotation-mode",Rotation.class,h).configure("Rotation mode",Rotation.BRUTEFORCE).build();
        roots=List.of(SettingGroup.builder(h).configure("Blocks",radius,switchBlock,silent).build(),
            SettingGroup.builder(h).configure("Movement",tower,towerMode,sprint,sprintMode,slowSprint,unsprint,keepY,safeWalk,correctMovement,limitSpeed,speedLimit).build(),
            SettingGroup.builder(h).configure("Placement",swing,realClick,checkBlock).build(),
            SettingGroup.builder(h).configure("Rotation",rotate,keepRotation,rotationSpeed,rotation).build());
    }
    private static BooleanSetting b(MarkerHooks h,String key,boolean value){return BooleanSetting.builder(key,h).configure(key.replace('-', ' '),value).build();}
    public List<SettingValue<?>> roots(){return roots;}
}
