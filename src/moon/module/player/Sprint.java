package moon.module.player;

import java.util.List;
import moon.module.NativeModule;
import moon.module.player.sprint.SprintController;
import moon.visual.gui.SettingValue;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;

/**
 * Automatic sprint, rebuilt around the shared {@link SprintController}.
 *
 * <p>The controller owns the sprint state while this module is enabled and leaves a
 * recovery window after sprint attacks so KillAura's C02 attacks keep the
 * vanilla slowdown.</p>
 */
public final class Sprint extends NativeModule {
    private final Minecraft mc;
    private final SprintController controller;

    public Sprint(Minecraft mc, MarkerHooks hooks, SprintController controller) {
        super("sprint", "Sprint", Category.PLAYER);
        this.mc = mc;
        this.controller = controller;
    }

    @Override public List<? extends SettingValue<?>> settings() { return List.of(); }
    @Override public void worldChanged() { controller.reset(); }

    /** Called after the live movement input update, before movement. */
    public void afterInput() {
        if (!enabled() || mc.thePlayer == null) return;
        controller.update(true, mc.thePlayer.rotationYaw);
    }

    @Override protected void onDisable() {
        controller.releaseControl();
    }
}
