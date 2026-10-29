package moon.module.visual;

import java.util.List;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;

/** jT: dH is Minecraft's PRE/POST client tick, confirmed at fL.x call sites.
 * Cancel the entire S03 packet, including its totalWorldTime update, like c7.
 */
public final class TimeChangerModule implements ModuleRowWidget.Module,AutoCloseable {
    private final Minecraft mc;
    public final IntegerSetting time;
    
    private final List<SettingValue<?>> settings;
    private boolean enabled;
    public TimeChangerModule(Minecraft mc,MarkerHooks hooks){
        this.mc=mc;
        time=IntegerSetting.builder("time",hooks).configure("Time",8000,0,24000,500).build();
        settings=List.of(time);
    }
    public String hudSuffix(){return String.valueOf(time.get());}
    public String displayName(){return "TimeChanger";}
    public boolean enabled(){return enabled;}
    public void toggle(){setEnabled(!enabled);}
    public List<? extends SettingValue<?>> settings(){return settings;}
    public void setEnabled(boolean value){enabled=value;}
    public boolean cancelsTimePacket(){return enabled;}
    public void tick(){if(enabled&&mc.theWorld!=null)mc.theWorld.setWorldTime(time.get());}
    @Override public void close(){setEnabled(false);}
}
