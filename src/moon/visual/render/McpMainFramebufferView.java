package moon.visual.render;

import java.util.function.Supplier;
import net.minecraft.client.shader.Framebuffer;

/** Borrowed live view, e.g. new McpMainFramebufferView(mc::getFramebuffer). Never owns host handles. */
public final class McpMainFramebufferView implements PostProcessBackend.Framebuffer,StencilRenderUtil.FramebufferAccess {
    private final Supplier<Framebuffer> current;
    public McpMainFramebufferView(Supplier<Framebuffer> current){this.current=current;}
    public int width(){return current.get().framebufferWidth;}public int height(){return current.get().framebufferHeight;}
    public int texture(){return current.get().framebufferTexture;}
    public int getDepthBuffer(){return current.get().depthBuffer;}
    public void setDepthBuffer(int handle){current.get().depthBuffer=handle;}
    public void bind(boolean viewport){current.get().bindFramebuffer(viewport);}public void bindTexture(){current.get().bindFramebufferTexture();}
    private UnsupportedOperationException hostOwned(){return new UnsupportedOperationException("Main framebuffer lifecycle belongs to MCP");}
    public boolean resizeIfChanged(int w,int h){throw hostOwned();}
    public void setClearColor(float r,float g,float b,float a){throw hostOwned();}
    public void setFilter(int filter){throw hostOwned();}public void setWrap(int wrap){throw hostOwned();}
    public void clearAndUnbind(){throw hostOwned();}public void clearBound(){throw hostOwned();}
}
