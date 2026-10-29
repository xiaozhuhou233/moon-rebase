package moon.visual.render;
import java.util.HashMap;
import moon.visual.shader.*;
/** h9: shader clipping, not GL scissor or stencil-buffer clipping.
 * Cache entries retain the FIRST command's live program supplier, just as the classfile does.
 * Static caches intentionally have no ownership/deletion fix in this faithful layer.
 */
public final class ShaderClipCommand extends DrawCommand {
    private static final HashMap<ShaderProgram,ShaderUniform> areas=new HashMap<>(),required=new HashMap<>();
    private static Object[] sentinel=new Object[4];
    private final float x,y,width,height;
    private final DrawServices services;
    public ShaderClipCommand(float x,float y,float width,float height,DrawServices services) {
        this.x=x;
        this.y=y;
        this.width=width;
        this.height=height;
        this.services=services;
    }
    private ShaderUniform create(String name) {
        ShaderUniform u=services.uniform(name,()->program);
        u.initializeLocation();
        return u;
    }
    public void before(float sw,float sh) {
        areas.computeIfAbsent(program,p->create("stencil.area"));
        required.computeIfAbsent(program,p->create("stencil.required"));
        float left=x/sw,right=(x+width)/sw,top=y/sh,bottom=(y+height)/sh;
        getSentinelArray();
        ShaderUniform area=areas.get(program),enabled=required.get(program);
        area.setVector4(left,top,right,bottom);
        enabled.setInt(1);
        if(services.markers.getMarker()!=null)setSentinelArray(new Object[3]);
    }
    public void after() {
        ShaderUniform enabled=required.get(program);
        program.use();
        enabled.setInt(0);
        services.backend.useProgram(0);
    }
    public static Object[] getSentinelArray() {
        return sentinel;
    }
    public static void setSentinelArray(Object[] value) {
        sentinel=value;
    }
}
