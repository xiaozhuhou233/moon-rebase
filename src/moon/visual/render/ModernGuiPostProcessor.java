package moon.visual.render;

import java.io.IOException;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.renderer.GlStateManager;

/** Modern background blur with owned buffers and live Effects diffusion parameters.
 * Uses the destination Kawase shaders, with explicit render-thread FBO ownership.
 * Never submits a shadow mask or changes the shared HUD Effects settings.
 */
public final class ModernGuiPostProcessor implements AutoCloseable {
    private final LwjglPostProcessBackend backend;
    private final KawaseStageExecutor stages;
    private final LwjglFramebuffer[] buffers=new LwjglFramebuffer[7];
    private boolean closed;

    public ModernGuiPostProcessor(LwjglPostProcessBackend.Host host,LwjglPostProcessBackend.StateAccess state,
            DrawServices services,LwjglDrawBackend owner)throws IOException {
        backend=new LwjglPostProcessBackend(host,state,services,owner);stages=new KawaseStageExecutor(backend,()->true);
        try{
            for(int i=0;i<buffers.length;i++){
                buffers[i]=new LwjglFramebuffer(2,2,false,state);
                buffers[i].setFilter(9729);buffers[i].setWrap(33071);buffers[i].setClearColor(0,0,0,0);
            }
        }catch(RuntimeException|Error failure){close();throw failure;}
    }
    public void render(Runnable drawMask){
        render(drawMask,2,1.5F);
    }
    private LwjglFramebuffer level(int index){return buffers[index<2?index+1:index+2];}
    public void render(Runnable drawMask,int iterations,float offset){
        if(closed)throw new IllegalStateException("Modern blur closed");
        iterations=Math.max(1,Math.min(5,iterations));offset=Math.max(0F,Math.min(10F,offset));
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        float sx=RenderTransformState.getScaleX(),sy=RenderTransformState.getScaleY(),sz=RenderTransformState.getScaleZ();
        float tx=RenderTransformState.getTranslateX(),ty=RenderTransformState.getTranslateY(),tz=RenderTransformState.getTranslateZ(),alpha=RenderTransformState.getAlpha();
        try{
            int w=backend.displayWidth(),h=backend.displayHeight();
            for(int i=0;i<buffers.length;i++){
                int divisor=i==0||i==3?1:1<<(i<3?i:i-1);
                if(buffers[i].resizeIfChanged(Math.max(2,w/divisor),Math.max(2,h/divisor))){
                    buffers[i].setFilter(9729);buffers[i].setWrap(33071);
                }
            }
            // Framebuffer allocation enables depth. No fullscreen stage may test or write it.
            GlStateManager.disableDepth();GlStateManager.depthMask(false);
            LwjglFramebuffer mask=buffers[0],half=buffers[1],quarter=buffers[2],output=buffers[3];
            mask.clearAndUnbind();mask.bind(true);backend.enableBlend();backend.blendFuncSeparate(1,0,1,0);drawMask.run();
            RenderTransformState.resetScale();RenderTransformState.resetTranslation();RenderTransformState.resetAlpha();
            backend.blendFuncSeparate(1,0,1,0);
            int source=backend.mainFramebuffer().texture();
            for(int i=0;i<iterations;i++){stages.down(false,source,level(i),offset);source=level(i).texture();}
            for(int i=iterations-2;i>=0;i--)stages.up(false,level(i+1).texture(),level(i),mask,output,offset);
            // Transparent mask fragments may be rejected by the compatibility alpha test.
            // Clear the previous animated footprint before composing the current one.
            output.clearAndUnbind();
            stages.up(false,level(0).texture(),output,mask,output,offset);
            backend.mainFramebuffer().bind(true);backend.enableBlend();backend.enableAlpha();backend.alphaFunc(516,0F);
            backend.enableTexture2D();backend.blendFuncSeparate(770,771,1,0);
            output.bindTexture();backend.drawCompositeQuad(7,backend.scaledWidth(),backend.scaledHeight());backend.bindTexture(0);
        }finally{
            backend.mainFramebuffer().bind(true);
            RenderTransformState.setScale(sx,sy,sz);RenderTransformState.setTranslation(tx,ty,tz);RenderTransformState.setAlpha(alpha);
            backend.blendFuncSeparate(770,771,1,0);
            GlStateManager.depthMask(depthWrite);
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
        }
    }
    public int liveFramebuffers(){int n=0;for(LwjglFramebuffer f:buffers)if(f!=null&&f.getFramebuffer()!=-1)n++;return n;}
    @Override public void close(){if(closed)return;closed=true;for(LwjglFramebuffer f:buffers)if(f!=null)f.close();}
}
