package moon.module.movement;

import java.util.List;
import java.util.function.BooleanSupplier;
import moon.client.McpModuleServices.Motion;
import moon.module.NativeModule;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;

/** Pulsive NoSlowDown: input slowdown cancellation plus Watchdog / NCP motion handling. */
public final class NoSlowModule extends NativeModule {
    public enum Mode implements DisplayNamed {
        WATCHDOG, NCP;
        public String displayName() { return this == WATCHDOG ? "Watchdog" : "NCP"; }
    }
    public final EnumSetting<Mode> mode;
    private final Minecraft mc;
    private final BooleanSupplier auraBusy, speedEnabled;
    private EntityPlayerSP usePlayer;
    private ItemStack useItem;
    private int useSlot = -1;
    private Mode useMode;
    private boolean blocking;

    public NoSlowModule(Minecraft mc, MarkerHooks hooks, BooleanSupplier auraBusy, BooleanSupplier speedEnabled) {
        super("noslow", "NoSlow", Category.PLAYER);
        this.mc = mc; this.auraBusy = auraBusy; this.speedEnabled = speedEnabled;
        mode = EnumSetting.builder("mode", Mode.class, hooks).configure("Mode", Mode.WATCHDOG).build();
    }
    public List<? extends SettingValue<?>> settings() { return List.of(mode); }
    public String hudSuffix() { return mode.get().displayName(); }
    protected void onEnable() { reset(); }
    protected void onDisable() { reset(); }
    public void worldChanged() { reset(); }
    private void reset() { usePlayer = null; useItem = null; useSlot = -1; useMode = null; blocking = false; }

    public boolean cancelsSlowdown(EntityPlayerSP player) {
        return enabled() && player != null && player == mc.thePlayer && mc.theWorld != null;
    }
    public Motion motion(Motion original, boolean pre) {
        EntityPlayerSP p = mc.thePlayer;
        if (!cancelsSlowdown(p) || !p.isUsingItem()) { reset(); return original; }
        if (usePlayer != p || useItem != p.getItemInUse() || useSlot != p.inventory.currentItem || useMode != mode.get()) {
            reset(); usePlayer = p; useItem = p.getItemInUse(); useSlot = p.inventory.currentItem; useMode = mode.get();
        }
        // Aura owns its own blocking packets. Slowdown cancellation remains independent.
        if (auraBusy.getAsBoolean() || (p.moveForward == 0 && p.moveStrafing == 0)) return original;
        if (mode.get() == Mode.WATCHDOG) {
            if (pre && p.ticksExisted % 2 == 0 && !speedEnabled.getAsBoolean() && p.posY % 0.015625 == 0) {
                return new Motion(original.yaw(), original.pitch(), original.y() + 0.05, false);
            }
        } else if (!blocking && p.sendQueue != null) {
            p.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 255, null, 0F, 0F, 0F));
            // Reset per item-use cycle; the donor accidentally kept this flag forever.
            blocking = true;
        }
        return original;
    }
}
