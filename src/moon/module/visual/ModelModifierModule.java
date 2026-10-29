package moon.module.visual;

import java.util.List;
import moon.visual.gui.DisplayNamed;
import moon.visual.gui.MultiChoiceSetting;
import moon.visual.gui.SettingValue;
import moon.visual.gui.ModuleRowWidget;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.entity.Entity;

/** Nightly self/other player model flags, exposed through the native visual registry. */
public final class ModelModifierModule implements ModuleRowWidget.Module {
    public enum Flag implements DisplayNamed { BABY, RIDING, HIDE_ARMOR;
        public String displayName(){return switch(this){case BABY->"Baby";case RIDING->"Riding";case HIDE_ARMOR->"Hide Armor";};}
    }
    public final MultiChoiceSetting<Flag> selfModifiers,otherModifiers;
    private final net.minecraft.client.Minecraft mc;
    private final List<SettingValue<?>> settings;
    private boolean enabled;
    public ModelModifierModule(net.minecraft.client.Minecraft mc,MarkerHooks hooks){
        this.mc=mc;
        selfModifiers=MultiChoiceSetting.builder("self-modifiers",Flag.class,hooks).configure("Self Modifiers",Flag.BABY).build();
        otherModifiers=MultiChoiceSetting.builder("other-modifiers",Flag.class,hooks).configure("Other Modifiers",Flag.BABY).build();
        settings=List.of(selfModifiers,otherModifiers);
    }
    private MultiChoiceSetting<Flag> flags(Entity entity){return entity==mc.thePlayer?selfModifiers:otherModifiers;}
    public boolean flag(Entity entity,Flag flag){return enabled&&entity instanceof net.minecraft.entity.player.EntityPlayer&&flags(entity).isSelected(flag);}
    public String displayName(){return "ModelModifier";} public String hudSuffix(){return "";}
    public boolean enabled(){return enabled;} public void toggle(){enabled=!enabled;} public void setEnabled(boolean value){enabled=value;}
    public List<? extends SettingValue<?>> settings(){return settings;}
}
