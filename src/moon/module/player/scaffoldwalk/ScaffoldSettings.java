package moon.module.player.scaffoldwalk;

import java.util.List;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/**
 * Native GUI/config view over the original Scaffold.lua settings. Keys and
 * defaults follow the script; {@code allow-sprinting} is kept as the persisted
 * key for the Lua {@code sprint} setting so existing module configs survive.
 */
public final class ScaffoldSettings {
    public final BooleanSetting placement,watchdog,grim,telly,tellyShift,earlyEdgePlace,autoJump,moveFix,sprint,
        preferSprintRay,clutch,raycast,mostBlocks,hypixelBlockSwitch,noSwing,sourceJitter;
    public final IntegerSetting placeTick;
    private final List<SettingValue<?>> roots;

    public ScaffoldSettings(MarkerHooks hooks){
        placement=b(hooks,"placement","Allow Placement",true);
        watchdog=b(hooks,"watchdog","Watchdog Rotation",true);
        grim=b(hooks,"grim","Grim Rotation",false);
        telly=b(hooks,"telly","Telly",true);
        tellyShift=b(hooks,"telly-shift","Telly On Shift",false);
        placeTick=IntegerSetting.builder("place-tick",hooks).configure("Place Tick",4,1,6,1).build();
        earlyEdgePlace=b(hooks,"early-edge-place","Early Edge Placement",true);
        autoJump=b(hooks,"auto-jump","Telly Auto Jump",true);
        moveFix=b(hooks,"move-fix","Silent Move Fix",true);
        sprint=BooleanSetting.builder("allow-sprinting",hooks).configure("Allow Sprint",true).build();
        preferSprintRay=b(hooks,"prefer-sprint-ray","Prefer Sprint Rays",true);
        clutch=b(hooks,"clutch","Clutch",false);
        raycast=b(hooks,"raycast","Strict Preceding RayCast",false);
        mostBlocks=b(hooks,"most-blocks","Most Blocks Slot",false);
        hypixelBlockSwitch=b(hooks,"hypixel-block-switch","Hypixel Block Switch",false);
        noSwing=b(hooks,"no-swing","No Client Swing",false);
        sourceJitter=b(hooks,"source-jitter","Source Rotation Jitter",false);
        SettingGroup placementGroup=SettingGroup.builder(hooks)
                .configure("Placement",placement,earlyEdgePlace,placeTick,noSwing).build();
        SettingGroup rotationGroup=SettingGroup.builder(hooks)
                .configure("Rotation",watchdog,grim,telly,tellyShift,sourceJitter,preferSprintRay,raycast).build();
        SettingGroup movementGroup=SettingGroup.builder(hooks)
                .configure("Movement",autoJump,moveFix,sprint).build();
        SettingGroup inventoryGroup=SettingGroup.builder(hooks)
                .configure("Inventory",mostBlocks,hypixelBlockSwitch).build();
        roots=List.of(placementGroup,rotationGroup,movementGroup,inventoryGroup,
                SettingGroup.builder(hooks).configure("Clutch",clutch).build());
    }

    private static BooleanSetting b(MarkerHooks hooks,String key,String name,boolean initial){
        return BooleanSetting.builder(key,hooks).configure(name,initial).build();
    }

    public List<SettingValue<?>> roots(){return roots;}
}
