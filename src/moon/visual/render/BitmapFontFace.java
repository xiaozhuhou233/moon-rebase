package moon.visual.render;
import org.lwjglx.util.vector.Matrix4f;
/** hG contract. BitmapAtlasFont supplies the restored hH default-path implementation. */
public abstract class BitmapFontFace implements DrawTaskState.Transform {
    public abstract void draw(DrawTask task,String text,float x,float y,int color,boolean shadowPass);
    public abstract void apply(Matrix4f matrix);
    public abstract void unapply(Matrix4f matrix);
    public abstract float width(String text);
    public abstract float height();
}
