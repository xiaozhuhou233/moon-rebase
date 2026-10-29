package moon.visual.gui;

import java.util.function.Supplier;
import moon.visual.render.*;

/** Combined drawing services for every setting type; original geometry/task implementations
 * are reused. Unrecovered hw/hx estimates are explicit constructor inputs, not guessed defaults.
 */
public final class SettingWidgetTreeRenderer implements SettingWidgetFactory.Drawing {
    private final ColorWidgetRenderer shapes;
    private final ChoiceWidgetRenderer rows;
    public SettingWidgetTreeRenderer(DrawServices services,SettingWidgetRenderer base,
            Supplier<ShaderDispatcher> rectangle,Supplier<ShaderDispatcher> gradient,Supplier<ShaderDispatcher> outline,
            int gradientEstimate,int outlineEstimate) {
        shapes=new ColorWidgetRenderer(services,base,rectangle,gradient,outline,gradientEstimate,outlineEstimate);
        rows=new ChoiceWidgetRenderer(services,base);
    }
    public DrawTask rectangleTask(){return shapes.rectangleTask();}
    public DrawTask gradientTask(){return shapes.gradientTask();}
    public DrawTask outlineTask(){return shapes.outlineTask();}
    public DrawTask msdfTask(MsdfFontBase font){return shapes.msdfTask(font);}
    public DrawTask roundedTask(){return shapes.roundedTask();}
    public DrawTaskState.Command clip(float x,float y,float w,float h){return shapes.clip(x,y,w,h);}
    public DrawTaskState.Geometry rounded(float x,float y,float w,float h,float r,int color){return shapes.rounded(x,y,w,h,r,color);}
    public DrawTaskState.Geometry rounded(float x,float y,float w,float h,float r,int color,boolean g,boolean b,boolean i,boolean j,boolean smooth){return rows.rounded(x,y,w,h,r,color,g,b,i,j,smooth);}
    public DrawTaskState.Geometry rectangle(float x,float y,float w,float h,int color){return shapes.rectangle(x,y,w,h,color);}
    public DrawTaskState.Geometry gradient(float x,float y,float w,float h,int from,int to,boolean vertical){return shapes.gradient(x,y,w,h,from,to,vertical);}
    public DrawTaskState.Geometry outline(float x,float y,float w,float h,float r,float line,int color){return shapes.outline(x,y,w,h,r,line,color);}
}
