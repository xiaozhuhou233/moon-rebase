package moon.visual.render;

import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.lwjgl.opengl.*;

/** LWJGL3 bridge for original k5. Operates on the CURRENT FBO; never binds a replacement.
 * Does not repair k5's discarded packed-renderbuffer handle. Its host owns attachment lifetime.
 * StateAccess synchronizes blend, clear and color-mask state with the destination MCP cache.
 */
public class LwjglStencilBackend implements StencilRenderUtil.Backend {
    private final Supplier<StencilRenderUtil.FramebufferAccess> main;
    private final IntSupplier width,height;
    private final LwjglPostProcessBackend.StateAccess state;
    public LwjglStencilBackend(Supplier<StencilRenderUtil.FramebufferAccess> main,IntSupplier width,IntSupplier height,
                               LwjglPostProcessBackend.StateAccess state) {
        this.main=main;this.width=width;this.height=height;this.state=state;
    }
    public void enableBlend(){state.enableBlend();}
    public void blendFunc(int source,int destination){state.blendFunc(source,destination);}
    public StencilRenderUtil.FramebufferAccess getMainFramebuffer(){return main.get();}
    public int getDisplayWidth(){return width.getAsInt();}public int getDisplayHeight(){return height.getAsInt();}
    public void clearStencil(int value){GL11.glClearStencil(value);}public void clear(int mask){state.clear(mask);}
    public void enable(int capability){GL11.glEnable(capability);}public void disable(int capability){GL11.glDisable(capability);}
    public void stencilFunc(int function,int reference,int mask){GL11.glStencilFunc(function,reference,mask);}
    public void stencilOp(int stencilFail,int depthFail,int depthPass){GL11.glStencilOp(stencilFail,depthFail,depthPass);}
    public void colorMask(boolean r,boolean g,boolean b,boolean a){state.colorMask(r,g,b,a);}
    public void deleteRenderbuffer(int handle){GL30.glDeleteRenderbuffers(handle);}public int generateRenderbuffer(){return GL30.glGenRenderbuffers();}
    public void bindRenderbuffer(int target,int handle){GL30.glBindRenderbuffer(target,handle);}
    public void renderbufferStorage(int target,int format,int width,int height){GL30.glRenderbufferStorage(target,format,width,height);}
    public void framebufferRenderbuffer(int target,int attachment,int renderbufferTarget,int handle){GL30.glFramebufferRenderbuffer(target,attachment,renderbufferTarget,handle);}
}
