package moon.visual.gui;

import moon.visual.render.*;

/** Shared original rounded/MSDF task factory, with no extra clipping added to kr/kw. */
public final class ModalRenderer implements ModalOverlay.Drawing {
    private final SettingWidget.Drawing drawing;
    public ModalRenderer(SettingWidget.Drawing drawing) { this.drawing=drawing; }
    public DrawTask roundedTask() { return drawing.roundedTask(); }
    public DrawTask msdfTask(MsdfFontBase font) { return drawing.msdfTask(font); }
    public DrawTaskState.Geometry rounded(float x,float y,float width,float height,float radius,int color) { return drawing.rounded(x,y,width,height,radius,color); }
}
