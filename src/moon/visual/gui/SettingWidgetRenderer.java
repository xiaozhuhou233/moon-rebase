package moon.visual.gui;

import java.util.function.Supplier;
import moon.visual.render.*;

/** Migration task factory: uses the restored gv/gx/g5 pipeline and h9 shader clipping.
 * Registry suppliers remain live, so a host can initialize dispatchers after GL creation.
 */
public final class SettingWidgetRenderer implements SettingWidget.Drawing {
    private final DrawServices services;
    private final Supplier<ShaderDispatcher> msdf,rounded;
    public SettingWidgetRenderer(DrawServices services,Supplier<ShaderDispatcher> msdf,Supplier<ShaderDispatcher> rounded) {
        this.services=services;this.msdf=msdf;this.rounded=rounded;
    }
    public DrawTask msdfTask(MsdfFontBase font) {
        return DrawTask.builder(services).dispatcher(msdf.get()).texture(font::bind).build();
    }
    public DrawTask roundedTask() { return DrawTask.builder(services).dispatcher(rounded.get()).build(); }
    public DrawTaskState.Command clip(float x,float y,float width,float height) {
        return new ShaderClipCommand(x,y,width,height,services);
    }
    public DrawTaskState.Geometry rounded(float x,float y,float width,float height,float radius,int color) {
        return new UiGeometry.RoundedRectangle(x,y,width,height,radius,color,services.markers);
    }
}
