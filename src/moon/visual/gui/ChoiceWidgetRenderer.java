package moon.visual.gui;

import moon.visual.render.*;

/** Actual rounded-row corner masks and half-pixel inset through original hz/dispatcher/shader. */
public final class ChoiceWidgetRenderer implements ChoiceSettingWidget.Drawing {
    private final DrawServices services;
    private final SettingWidgetRenderer base;
    public ChoiceWidgetRenderer(DrawServices services,SettingWidgetRenderer base) { this.services=services;this.base=base; }
    public DrawTask msdfTask(MsdfFontBase font) { return base.msdfTask(font); }
    public DrawTask roundedTask() { return base.roundedTask(); }
    public DrawTaskState.Command clip(float x,float y,float width,float height) { return base.clip(x,y,width,height); }
    public DrawTaskState.Geometry rounded(float x,float y,float width,float height,float radius,int color) { return base.rounded(x,y,width,height,radius,color); }
    public DrawTaskState.Geometry rounded(float x,float y,float width,float height,float radius,int color,
            boolean g,boolean h,boolean i,boolean j,boolean smooth) {
        return new UiGeometry.RoundedRectangle(x,y,width,height,radius,color,services.markers)
            .cornerG(g).cornerH(h).cornerI(i).cornerJ(j).smooth(smooth);
    }
}
