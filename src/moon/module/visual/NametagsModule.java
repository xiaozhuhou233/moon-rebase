package moon.module.visual;

import java.util.List;
import moon.visual.gui.BooleanSetting;
import moon.visual.gui.DisplayNamed;
import moon.visual.gui.MultiChoiceSetting;
import moon.visual.gui.SettingGroup;
import moon.visual.gui.SettingValue;
import moon.visual.gui.ModuleRowWidget;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;

/** Nightly Nametags settings and entity filter. Vanilla label placement remains host-owned. */
public final class NametagsModule implements ModuleRowWidget.Module {
    public enum Target implements DisplayNamed { ANIMAL, MOB, INVISIBLE, PLAYER;
        public String displayName() { return switch (this) {
            case ANIMAL -> "Animals"; case MOB -> "Mobs"; case INVISIBLE -> "Invisible"; case PLAYER -> "Players";
        }; }
    }

    private final Minecraft mc;
    private final List<SettingValue<?>> settings;
    public final MultiChoiceSetting<Target> targets;
    public final BooleanSetting health, distance, separateInformation, roundedBackground;
    private boolean enabled;

    public NametagsModule(Minecraft mc, MarkerHooks hooks) {
        this.mc = mc;
        targets = MultiChoiceSetting.builder("targets", Target.class, hooks).configure("Targets", Target.PLAYER).build();
        health = BooleanSetting.builder("health", hooks).configure("Health", true).build();
        distance = BooleanSetting.builder("distance", hooks).configure("Distance", true).build();
        separateInformation = BooleanSetting.builder("separate-information", hooks).configure("Separate information", true).build();
        roundedBackground = BooleanSetting.builder("round", hooks).configure("Rounded background", true).build();
        SettingGroup information = SettingGroup.builder(hooks).configure("Information", health, distance, separateInformation, roundedBackground).build();
        settings = List.of(targets, information);
    }

    public String displayName() { return "Nametags"; }
    public String hudSuffix() { return ""; }
    public boolean enabled() { return enabled; }
    public void toggle() { setEnabled(!enabled); }
    public void setEnabled(boolean value) { enabled = value; }
    public List<? extends SettingValue<?>> settings() { return settings; }

    public boolean applies(Entity entity) {
        if (!enabled || entity == null) return false;
        if (entity.isInvisible() && targets.isSelected(Target.INVISIBLE)) return true;
        if (entity instanceof EntityPlayer) return targets.isSelected(Target.PLAYER);
        if (entity instanceof EntityAnimal) return targets.isSelected(Target.ANIMAL);
        return entity instanceof EntityLivingBase && targets.isSelected(Target.MOB);
    }

    public String information(Entity entity) {
        if (!applies(entity) || !(entity instanceof EntityLivingBase living)) return null;
        StringBuilder text = new StringBuilder();
        if (health.get()) text.append(Math.round(living.getHealth())).append(" HP");
        if (distance.get() && mc.thePlayer != null) {
            if (text.length() > 0) text.append("  ");
            text.append(Math.round(entity.getDistanceToEntity(mc.thePlayer))).append("m");
        }
        return text.length() == 0 ? null : text.toString();
    }
}
