package moon.visual.gui;

import java.util.function.Consumer;
import moon.visual.render.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** kw with bq/kx roles. The static kw.a label is unresolved; the host owns one shared close button.
 * Button hit testing accepts float coordinates and preserves the bq null-sentinel comparison bit.
 */
public final class ModalButton {
    public enum Type { SUCCESS,ERROR,NORMAL }
    public interface Host {
        MsdfFontBase robotoMedium();
        MarkerHooks markers();
        ModalConfiguration.Screen activeScreen();
    }
    private final MsdfFontBase font;
    private final String label;
    private final Type type;
    private final Consumer<Void> callback;
    private final MarkerHooks markers;
    private float x,y,width,height;
    private static int[] typeSentinel=new int[1];
    public ModalButton(Host host,String label,Type type,Consumer<Void> callback) {
        font=host.robotoMedium();this.label=label;this.type=type;this.callback=callback;markers=host.markers();
    }
    /** Construct once per host, then reuse in every descriptor, mirroring kw.a's shared identity.
     * activeScreen is deliberately looked up when clicked, rather than captured at construction.
     */
    public static ModalButton sharedClose(Host host,String explicitLabel) {
        return new ModalButton(host,explicitLabel,Type.ERROR,ignored->host.activeScreen().setModal(null));
    }
    public void initialize() {
        width=font.width(label,8F)+6F;getTypeSentinel();height=font.height(8F)+6F;
        if(markers.getMarker()!=null) setTypeSentinel(new int[3]);
    }
    public void draw(DrawTask background,DrawTask text,ModalOverlay.Scheme scheme,ModalOverlay.Drawing drawing) {
        int[] captured=getTypeSentinel();int fill,ink;
        switch(type) {
            case ERROR:fill=scheme.error();break;
            case SUCCESS:fill=scheme.success();break;
            case NORMAL:fill=scheme.accent();break;
            default:throw new IncompatibleClassChangeError();
        }
        switch(type) {
            case ERROR:ink=scheme.onError();break;
            case SUCCESS:ink=scheme.onSuccess();break;
            case NORMAL:ink=scheme.onAccent();break;
            default:throw new IncompatibleClassChangeError();
        }
        background.add(drawing.rounded(x,y,width,height,3F,fill));
        font.drawSinglePass(text,label,8F,x+3F,y+3.5F,ink);
        if(captured==null) markers.setMarker("KUeQJb");
    }
    public boolean hit(float mouseX,float mouseY) {
        if(getTypeSentinel()==null) {
            int comparison=Float.isNaN(mouseX)||Float.isNaN(x)?-1:mouseX>x?1:mouseX==x?0:-1;
            return (comparison&1)!=0;
        }
        return mouseX>=x&&mouseX<=x+width&&mouseY>=y&&mouseY<=y+height;
    }
    public MsdfFontBase getFont() { return font; }
    public String getLabel() { return label; }
    public Type getType() { return type; }
    public Consumer<Void> getCallback() { return callback; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public ModalButton setX(float value) { x=value;return this; }
    public ModalButton setY(float value) { y=value;return this; }
    public ModalButton setWidth(float value) { width=value;return this; }
    public ModalButton setHeight(float value) { height=value;return this; }
    public static int[] getTypeSentinel() { return typeSentinel; }
    public static void setTypeSentinel(int[] value) { typeSentinel=value; }
}
