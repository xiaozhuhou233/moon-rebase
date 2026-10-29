package moon.visual.render;

import java.io.IOException;
import java.util.*;
import org.lwjgl.opengl.*;

/** LWJGL3 compatibility-context bridge to restored fA/fz/g5/gx/gv. */
public final class LwjglPostProcessBackend implements PostProcessBackend {
    public interface Host {
        boolean isWindowVisible();int displayWidth();int displayHeight();double scaledWidth();double scaledHeight();
        /** Borrowed; this backend never deletes the host framebuffer or its texture. */
        Framebuffer mainFramebuffer();
        void publishMaskEvent(boolean shadow);
        /** Host establishes its GUI projection/modelview and color before immediate-mode composite. */
        void prepareComposite();
        default int[] glowColors(){return FlowingGlow.palette(0xff8b6cff,0xff51d8ef);}
    }
    /** Override with MCP GlStateManager/kY equivalents to keep the game's GL cache synchronized. */
    public interface StateAccess {
        default void enableDepth(){GL11.glEnable(2929);}
        default void enableBlend(){GL11.glEnable(3042);}default void enableAlpha(){GL11.glEnable(3008);}
        default void enableTexture2D(){GL11.glEnable(3553);}default void disableTexture2D(){GL11.glDisable(3553);}
        default void alphaFunc(int f,float r){GL11.glAlphaFunc(f,r);}
        default void blendFunc(int s,int d){GL11.glBlendFunc(s,d);}
        default void blendFuncSeparate(int sr,int dr,int sa,int da){GL14.glBlendFuncSeparate(sr,dr,sa,da);}
        default void activeTexture(int t){GL13.glActiveTexture(t);}default void bindTexture(int t){GL11.glBindTexture(3553,t);}
        default void clearColor(float r,float g,float b,float a){GL11.glClearColor(r,g,b,a);}
        default void viewport(int w,int h){GL11.glViewport(0,0,w,h);}
        default void clearDepth(double d){GL11.glClearDepth(d);}default void clear(int mask){GL11.glClear(mask);}
        default void colorMask(boolean r,boolean g,boolean b,boolean a){GL11.glColorMask(r,g,b,a);}
        default void color(float r,float g,float b,float a){GL11.glColor4f(r,g,b,a);}
        default void deleteTexture(int texture){GL11.glDeleteTextures(texture);}
    }
    private FlowingGlow flowing;
    private final Host host;private final StateAccess state;private final DrawServices services;
    private final LwjglDrawBackend owner;
    private final Map<Kind,KawaseDispatcher> dispatchers=new EnumMap<>(Kind.class);
    public LwjglPostProcessBackend(Host host,StateAccess state,DrawServices services,LwjglDrawBackend owner) throws IOException {
        this.host=host;this.state=state;this.services=services;this.owner=owner;
        for(Kind k:Kind.values()){KawaseDispatcher d=new KawaseDispatcher(services,k);d.initialize();
            if(d.getProgram().getProgram()==0||GL20.glGetProgrami(d.getProgram().getProgram(),35714)!=1)
                throw new IOException("Postprocess program failed: "+k);
            dispatchers.put(k,d);
        }
    }
    public Stage acquireStage(Kind k){owner.requireOpen();KawaseDispatcher d=dispatchers.get(k);
        // Acquisition owns no native allocation. An abandoned Stage cannot leak a gx writer.
        return new Stage(){
            public boolean hasExpectedUniformLayout(){return true;}
            public void useProgram(){d.getProgram().use();}
            public void setTextureSampler(int u){d.texture.setInt(u);}public void setStencilSampler(int u){d.stencil.setInt(u);}
            public void setTexelSize(float x,float y){d.texelSize.setVector2(x,y);}public void setOffset(float x,float y){d.offset.setVector2(x,y);}
            public void setAlpha(float a){d.alpha.setFloat(a);}public void setUseStencil(boolean b){d.useStencil.setBoolean(b);}
            public void draw(){
                owner.requireOpen();DrawTask t=new DrawTask(services).setDispatcher(d);
                try{t.setup();t.draw();}finally{
                    // Explicit migration ownership guard. Original g5/gv have no exception cleanup.
                    if(owner.ownsMemory(t.getState().getWriter().address()))t.getState().getWriter().free();
                }
            }
        };
    }
    public boolean isWindowVisible(){return host.isWindowVisible();}public int displayWidth(){return host.displayWidth();}
    public int displayHeight(){return host.displayHeight();}public double scaledWidth(){return host.scaledWidth();}
    public double scaledHeight(){return host.scaledHeight();}public Framebuffer mainFramebuffer(){return host.mainFramebuffer();}
    public void publishMaskEvent(boolean shadow){host.publishMaskEvent(shadow);}
    public void enableBlend(){state.enableBlend();}public void enableAlpha(){state.enableAlpha();}
    public void enableTexture2D(){state.enableTexture2D();}public void disableTexture2D(){state.disableTexture2D();}
    public void alphaFunc(int f,float r){state.alphaFunc(f,r);}public void blendFunc(int s,int d){state.blendFunc(s,d);}
    public void blendFuncSeparate(int sr,int dr,int sa,int da){state.blendFuncSeparate(sr,dr,sa,da);}
    public void activeTexture(int t){state.activeTexture(t);}public void bindTexture(int t){state.bindTexture(t);}
    public void clearColor(float r,float g,float b,float a){state.clearColor(r,g,b,a);}
    public void drawGlowCompositeQuad(int primitive,double w,double h){
        owner.requireOpen();
        host.prepareComposite();quad(primitive,w,h);
    }
    public void drawCompositeQuad(int primitive,double w,double h){host.prepareComposite();quad(primitive,w,h);}
    private void quad(int primitive,double w,double h){
        GL11.glBegin(primitive);
        GL11.glTexCoord2d(0,0);GL11.glVertex2d(0,h);GL11.glTexCoord2d(1,0);GL11.glVertex2d(w,h);
        GL11.glTexCoord2d(1,1);GL11.glVertex2d(w,0);GL11.glTexCoord2d(0,1);GL11.glVertex2d(0,0);GL11.glEnd();
    }
}
