package moon.visual.gui;

import java.util.function.Supplier;
import moon.visual.render.*;

/** Migration task factory for kg. Unrecovered hw/hx estimates stay explicit inputs;
 * 272/224 actual layout sizes are a migration policy, not decoded original constants.
 */
public final class ColorWidgetRenderer implements ColorSettingWidget.Drawing {
    private final SettingWidgetRenderer base;
    private final DrawServices services;
    private final Supplier<ShaderDispatcher> rectangle,gradient,outline;
    private final int gradientEstimate,outlineEstimate;
    public ColorWidgetRenderer(DrawServices services,SettingWidgetRenderer base,Supplier<ShaderDispatcher> rectangle,
            Supplier<ShaderDispatcher> gradient,Supplier<ShaderDispatcher> outline,int gradientEstimate,int outlineEstimate) {
        this.services=services;this.base=base;this.rectangle=rectangle;this.gradient=gradient;this.outline=outline;
        this.gradientEstimate=gradientEstimate;this.outlineEstimate=outlineEstimate;
    }
    private DrawTask task(Supplier<ShaderDispatcher> dispatcher) { return DrawTask.builder(services).dispatcher(dispatcher.get()).build(); }
    public DrawTask rectangleTask() { return task(rectangle); }
    public DrawTask gradientTask() { return task(gradient); }
    public DrawTask outlineTask() { return task(outline); }
    public DrawTask msdfTask(MsdfFontBase font) { return base.msdfTask(font); }
    public DrawTask roundedTask() { return base.roundedTask(); }
    public DrawTaskState.Command clip(float x,float y,float width,float height) { return base.clip(x,y,width,height); }
    public DrawTaskState.Geometry rounded(float x,float y,float width,float height,float radius,int color) { return base.rounded(x,y,width,height,radius,color); }
    public DrawTaskState.Geometry rectangle(float x,float y,float width,float height,int color) { return new UiGeometry.Rectangle(x,y,width,height,color); }
    public DrawTaskState.Geometry gradient(float x,float y,float width,float height,int from,int to,boolean vertical) {
        return new UiGeometry.GradientRectangle(x,y,width,height,from,to,vertical,gradientEstimate,services.markers);
    }
    public DrawTaskState.Geometry outline(float x,float y,float width,float height,float radius,float lineWidth,int color) {
        return new UiGeometry.OutlineRectangle(x,y,width,height,radius,lineWidth,color,outlineEstimate);
    }
}
