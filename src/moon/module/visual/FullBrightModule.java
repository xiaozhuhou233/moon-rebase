package moon.module.visual;

import java.util.List;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

/** jO: refresh night vision at every c0 motion phase; d() removes it on disable.
 * DATA-decoded duration/amplifier are 16350/1336. No gamma preference is changed.
 */
public final class FullBrightModule implements ModuleRowWidget.Module,AutoCloseable {
    private final Minecraft mc;
    
    private final List<SettingValue<?>> settings;
    private boolean enabled;
    public FullBrightModule(Minecraft mc,MarkerHooks hooks){
        this.mc=mc;
        settings=List.of();
    }
    public String displayName(){return "FullBright";}
    public boolean enabled(){return enabled;}
    public void toggle(){setEnabled(!enabled);}
    public List<? extends SettingValue<?>> settings(){return settings;}
    public void setEnabled(boolean value){
        if(enabled==value)return;
        enabled=value;
        if(!value&&mc.thePlayer!=null&&mc.thePlayer.isPotionActive(Potion.nightVision))
            mc.thePlayer.removePotionEffect(Potion.nightVision.id);
    }
    public void motionPhase(){
        if(enabled&&mc.theWorld!=null&&mc.thePlayer!=null)
            mc.thePlayer.addPotionEffect(new PotionEffect(Potion.nightVision.id,16350,1336));
    }
    @Override public void close(){setEnabled(false);}
}
