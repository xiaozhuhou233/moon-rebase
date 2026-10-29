package moon.client;

import moon.visual.render.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL30;

/** Owns only k5's otherwise discarded packed renderbuffer. Main FBO/texture stay borrowed.
 * Vanilla recreates its main FBO before resize notification; delete the old attachment there.
 */
final class McpStencilOwner extends LwjglStencilBackend implements AutoCloseable {
    private final Minecraft mc;
    private int packed,framebuffer;
    McpStencilOwner(Minecraft mc,McpMainFramebufferView main,LwjglPostProcessBackend.StateAccess state){
        super(()->main,()->mc.displayWidth,()->mc.displayHeight,state);this.mc=mc;
    }
    @Override public int generateRenderbuffer(){
        if(packed!=0)throw new IllegalStateException("Packed stencil must be released before replacement");
        framebuffer=mc.getFramebuffer().framebufferObject;
        return packed=super.generateRenderbuffer();
    }
    void resized(){if(packed!=0){GL30.glDeleteRenderbuffers(packed);packed=0;}}
    int liveAttachments(){return packed==0?0:1;}
    @Override public void close(){
        if(packed==0)return;
        int previous=org.lwjgl.opengl.GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        if(GL30.glIsFramebuffer(framebuffer)){
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_STENCIL_ATTACHMENT,GL30.GL_RENDERBUFFER,0);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_RENDERBUFFER,0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,previous);
        }
        resized();
    }
}
