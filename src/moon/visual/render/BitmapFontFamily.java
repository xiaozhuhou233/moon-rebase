package moon.visual.render;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import org.lwjglx.util.vector.Matrix4f;
import moon.visual.shader.ShaderUniform.MarkerHooks;
/** hI: five bitmap variants (scale 1..5), with a LIVE scale lookup at each original call site.
 * Factory corresponds to hJ.create(...).create(type); use BitmapFontBuilder.factory for hH.
 */
public final class BitmapFontFamily implements DrawTaskState.Transform {
    public interface Factory {
        BitmapFontFace create(String name,float size,int scale);
    }
    /** External hL sentinel calls remain explicit, without claiming hL itself is restored. */
    public interface FontHooks extends MarkerHooks {
        String[] getFontSentinel();
        void setFontSentinel(String[] value);
    }
    private final Map<Integer,BitmapFontFace> fonts=new HashMap<>();
    private final IntSupplier scale;
    private final FontHooks hooks;
    public BitmapFontFamily(String name,float size,Factory factory,IntSupplier scale,FontHooks hooks) {
        this.scale=scale;
        this.hooks=hooks;
        String[] captured=hooks.getFontSentinel();
        for(int i=1;i<6;i++) {
            fonts.put(i,factory.create(name,size,i));
            if(captured!=null)break;
        }
    }
    private BitmapFontFace font() {
        return fonts.get(scale.getAsInt());
    }
    public void draw(DrawTask task,String text,float x,float y,int color) {
        font().draw(task,text,x,y,color,false);
    }
    public void drawShadow(DrawTask task,String text,float x,float y,int color) {
        hooks.getFontSentinel();
        font().draw(task,text,x+.5F,y+.5F,0x80000000,true);
        font().draw(task,text,x,y,color,false);
        if(hooks.getMarker()!=null)hooks.setFontSentinel(new String[5]);
    }
    public void drawOutline(DrawTask task,String text,float x,float y,float offset,int color) {
        offset/=scale.getAsInt();
        BitmapFontFace face=font();
        face.draw(task,text,x-offset,y-offset,0xff000000,true);
        face.draw(task,text,x+offset,y+offset,0xff000000,true);
        face.draw(task,text,x+offset,y-offset,0xff000000,true);
        String[] captured=hooks.getFontSentinel();
        face.draw(task,text,x-offset,y+offset,0xff000000,true);
        face.draw(task,text,x,y-offset,0xff000000,true);
        face.draw(task,text,x,y+offset,0xff000000,true);
        face.draw(task,text,x-offset,y,0xff000000,true);
        face.draw(task,text,x+offset,y,0xff000000,true);
        face.draw(task,text,x,y,color,false);
        if(captured!=null)hooks.setMarker("SeXT3b");
    }
    public void apply(Matrix4f matrix) {
        font().apply(matrix);
    }
    public void unapply(Matrix4f matrix) {
        font().unapply(matrix);
    }
    public float width(String text) {
        return font().width(text)/scale.getAsInt();
    }
    public float height() {
        return font().height()/scale.getAsInt();
    }
}
