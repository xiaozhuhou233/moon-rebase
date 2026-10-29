package moon.module.player;

import java.util.List;
import moon.module.NativeModule;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;

/** Original jy: PRE client tick clears the right-click cooldown, optionally for blocks only. */
public final class FastPlaceModule extends NativeModule {
    private final Minecraft mc;
    public final BooleanSetting blockOnly;
    
    public FastPlaceModule(Minecraft mc,MarkerHooks hooks){
        super("fastplace","FastPlace",Category.PLAYER);this.mc=mc;
        blockOnly=BooleanSetting.builder("block-only",hooks).configure("Block only",true).build();
    }
    public List<? extends SettingValue<?>> settings(){return List.of(blockOnly);}
    public boolean clearsDelay(){
        if(!enabled()||mc.thePlayer==null)return false;
        var stack=mc.thePlayer.getHeldItem();
        return !blockOnly.get()||(stack!=null&&stack.getItem() instanceof ItemBlock);
    }
    public void worldChanged(){}
}
