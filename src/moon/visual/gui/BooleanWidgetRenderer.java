package moon.visual.gui;

import java.util.function.Supplier;
import moon.visual.render.*;

/** ke task factory; the unresolved original hx estimate is an explicit migration input. */
public final class BooleanWidgetRenderer implements BooleanSettingWidget.Drawing {
    private final DrawServices services;
    private final SettingWidgetRenderer base;
    private final Supplier<ShaderDispatcher> outline;
    private final int outlineEstimate;
    public BooleanWidgetRenderer(DrawServices services,SettingWidgetRenderer base,Supplier<ShaderDispatcher> outline,int outlineEstimate) {
        this.services=services;this.base=base;this.outline=outline;this.outlineEstimate=outlineEstimate;
    }
    public DrawTask msdfTask(MsdfFontBase font) { return base.msdfTask(font); }
    public DrawTask roundedTask() { return base.roundedTask(); }
    public DrawTask outlineTask() { return DrawTask.builder(services).dispatcher(outline.get()).build(); }
    public DrawTaskState.Command clip(float x,float y,float width,float height) { return base.clip(x,y,width,height); }
    public DrawTaskState.Geometry rounded(float x,float y,float width,float height,float radius,int color) { return base.rounded(x,y,width,height,radius,color); }
    public DrawTaskState.Geometry outline(float x,float y,float width,float height,float radius,float lineWidth,int color) {
        return new UiGeometry.OutlineRectangle(x,y,width,height,radius,lineWidth,color,outlineEstimate);
    }
}
