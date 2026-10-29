package moon.visual.render;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.lwjgl.opengl.GL13;
import moon.visual.render.PostProcessBackend.Framebuffer;

/** fH host: borrow the established shader/FBO owner, but keep sJ separate from
 * fK's gb and bypass the state cache for original GL13.glActiveTexture calls.
 * Pair with LwjglGlowCompositeBackend and LwjglMcpGlowBatchFactory in MCP. */
public final class LwjglEntityOutlineBackend implements EntityOutlinePass.Host {
    private final LwjglGlowBackend owner;
    private final Supplier<Object> sentinel,externalMarker;
    private final Consumer<String[]> sentinelSetter;
    public LwjglEntityOutlineBackend(LwjglGlowBackend owner,Supplier<Object> sentinel,
            Consumer<String[]> sentinelSetter,Supplier<Object> externalMarker){
        this.owner=owner;this.sentinel=sentinel;this.sentinelSetter=sentinelSetter;this.externalMarker=externalMarker;
    }
    public Object marker(){return sentinel.get();}
    public Object externalMarker(){return externalMarker.get();}
    public void setSentinel(String[] value){sentinelSetter.accept(value);}
    public int displayWidth(){return owner.displayWidth();}public int displayHeight(){return owner.displayHeight();}
    public Framebuffer createFramebuffer(int width,int height,boolean depth){return owner.createFramebuffer(width,height,depth);}
    public DrawTask createTask(GlowOutlinePass.Kind kind){return owner.createTask(kind);}
    public void enableTexture2D(){owner.enableTexture2D();}
    public void activeTexture(int textureEnum){owner.activeTexture(textureEnum);}
    public void rawActiveTexture(int textureEnum){GL13.glActiveTexture(textureEnum);}
    public void bindTexture(int texture){owner.bindTexture(texture);}
    public void blendFuncSeparate(int sr,int dr,int sa,int da){owner.blendFuncSeparate(sr,dr,sa,da);}
    public void releaseFramebuffer(Framebuffer framebuffer){owner.releaseFramebuffer(framebuffer);}
}
