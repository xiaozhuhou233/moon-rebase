package moon.module.player;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import moon.module.NativeModule;
import moon.visual.gui.SettingValue;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.potion.Potion;

/** Render-independent automatic sprint, adapted from the old PlayerMoveEvent module. */
public final class Sprint extends NativeModule {
    private final Minecraft mc;
    private final BooleanSupplier scaffoldAllowsSprint,keepSprintWhileUsingItem;

    public Sprint(Minecraft mc,MarkerHooks hooks,BooleanSupplier scaffoldAllowsSprint,
                  BooleanSupplier keepSprintWhileUsingItem){
        super("sprint","Sprint",Category.PLAYER);
        this.mc=mc;this.scaffoldAllowsSprint=scaffoldAllowsSprint;this.keepSprintWhileUsingItem=keepSprintWhileUsingItem;
    }
    @Override public List<? extends SettingValue<?>> settings(){return List.of();}
    @Override public void worldChanged(){}
    public boolean canSprint(){
        EntityPlayerSP player=mc.thePlayer;if(player==null||mc.theWorld==null)return false;
        boolean hypixel=false;ServerData server=mc.getCurrentServerData();
        if(server!=null&&server.serverIP!=null)hypixel=server.serverIP.toLowerCase(Locale.ROOT).contains("hypixel");
        boolean moving=player.movementInput!=null&&(player.movementInput.moveForward!=0||player.movementInput.moveStrafe!=0);
        boolean enoughMovement=player.moveForward>=.8F||hypixel&&moving;
        return enoughMovement&&!player.isCollidedHorizontally
            &&(player.getFoodStats().getFoodLevel()>6||player.capabilities.allowFlying)
            &&scaffoldAllowsSprint.getAsBoolean()&&!player.isSneaking()
            &&(!player.isUsingItem()||keepSprintWhileUsingItem.getAsBoolean())
            &&!player.isPotionActive(Potion.blindness)&&!player.isPotionActive(Potion.moveSlowdown);
    }
    public void updateSprint(){
        EntityPlayerSP player=mc.thePlayer;if(player!=null)player.setSprinting(enabled()&&canSprint());
    }
    @Override protected void onDisable(){
        EntityPlayerSP player=mc.thePlayer;
        if(player!=null)player.setSprinting(mc.gameSettings.keyBindSprint.isKeyDown());
    }
}
