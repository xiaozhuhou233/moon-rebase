package moon.client;

import java.util.*;
import java.util.function.*;
import moon.visual.hud.DraggableHudComponent;

/** Destination input policy; no recovered original drag-screen behavior is claimed.
 * Screen coordinates are GUI units. Component positions are unscaled HUD units.
 */
final class McpHudDragController {
    private final Supplier<List<DraggableHudComponent>> components;
    private final BooleanSupplier editable;
    private final DoubleSupplier scale,width,height;
    private final Runnable changed,committed;
    private DraggableHudComponent captured;
    private float grabX,grabY;
    private double capturedScale,capturedWidth,capturedHeight;
    McpHudDragController(Supplier<List<DraggableHudComponent>> components,BooleanSupplier editable,
            DoubleSupplier scale,DoubleSupplier width,DoubleSupplier height,Runnable changed,Runnable committed){
        this.components=components;this.editable=editable;this.scale=scale;this.width=width;this.height=height;
        this.changed=changed;this.committed=committed;
    }
    DraggableHudComponent captured(){return captured;}
    void opened(){finish();if(editable.getAsBoolean())for(var component:components.get())if(component.isEnabled())constrain(component);}
    void validate(){
        if(captured!=null&&(!editable.getAsBoolean()||!captured.isEnabled()||!bounds(captured)||!components.get().contains(captured)||
                scale.getAsDouble()!=capturedScale||width.getAsDouble()!=capturedWidth||height.getAsDouble()!=capturedHeight))finish();
    }
    boolean press(float screenX,float screenY,int button){
        validate();if(button!=0||!editable.getAsBoolean()||!validViewport())return false;finish();
        var list=components.get();float x=(float)(screenX/scale.getAsDouble()),y=(float)(screenY/scale.getAsDouble());
        for(int i=list.size()-1;i>=0;i--){
            var component=list.get(i);if(!component.isEnabled()||!bounds(component))continue;
            if(x>=component.getX()&&x<=component.getX()+component.getWidth()&&y>=component.getY()&&y<=component.getY()+component.getHeight()){
                captured=component;grabX=x-component.getX();grabY=y-component.getY();
                capturedScale=scale.getAsDouble();capturedWidth=width.getAsDouble();capturedHeight=height.getAsDouble();return true;
            }
        }return false;
    }
    boolean move(float screenX,float screenY,int button){
        validate();if(captured==null||button!=0||!Float.isFinite(screenX)||!Float.isFinite(screenY))return false;
        moveTo(captured,(float)(screenX/capturedScale)-grabX,(float)(screenY/capturedScale)-grabY);return true;
    }
    boolean release(int button){if(button!=0||captured==null)return false;finish();return true;}
    void finish(){if(captured==null)return;captured=null;committed.run();}
    void constrain(DraggableHudComponent component){if(validViewport()&&bounds(component))moveTo(component,component.getX(),component.getY());}
    private void moveTo(DraggableHudComponent component,float x,float y){
        x=Math.max(0,Math.min(x,(float)Math.max(0,width.getAsDouble()/scale.getAsDouble()-component.getWidth())));
        y=Math.max(0,Math.min(y,(float)Math.max(0,height.getAsDouble()/scale.getAsDouble()-component.getHeight())));
        if(Float.compare(component.getX(),x)!=0||Float.compare(component.getY(),y)!=0){component.setX(x);component.setY(y);changed.run();}
    }
    private boolean validViewport(){return Double.isFinite(scale.getAsDouble())&&scale.getAsDouble()>0&&Double.isFinite(width.getAsDouble())&&width.getAsDouble()>0&&Double.isFinite(height.getAsDouble())&&height.getAsDouble()>0;}
    private static boolean bounds(DraggableHudComponent component){return Float.isFinite(component.getX())&&Float.isFinite(component.getY())&&Float.isFinite(component.getWidth())&&Float.isFinite(component.getHeight())&&component.getWidth()>0&&component.getHeight()>0;}
}
