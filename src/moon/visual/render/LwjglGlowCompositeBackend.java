package moon.visual.render;

import moon.visual.render.PostProcessBackend.Framebuffer;
import org.lwjgl.opengl.GL11;

/** Compatibility-context bridge for fG. The host supplies its existing GUI
 * matrices/color before render, as the original caller does. StateAccess must
 * match the game's state cache. An MCP batch factory can replace immediate draw. */
public final class LwjglGlowCompositeBackend implements GlowCompositePass.Host {
    public interface GameAccess {
        Object marker();void setExternalMarker(String value);
        GlowCompositePass.Resolution scaledResolution();Framebuffer mainFramebuffer();
        int clientFps();int desktopFrequency();
    }
    public interface BatchFactory { GlowCompositePass.Batch begin(int primitive); }
    private final GameAccess game;
    private final LwjglPostProcessBackend.StateAccess state;
    private final BatchFactory batches;
    public LwjglGlowCompositeBackend(GameAccess game,LwjglPostProcessBackend.StateAccess state) {
        this(game,state,primitive->new GlowCompositePass.Batch(){
            private final double[] data=new double[20];private int size;
            public void vertex(double x,double y,double z,double u,double v) {
                data[size++]=x;data[size++]=y;data[size++]=z;data[size++]=u;data[size++]=v;
            }
            public void draw(){
                GL11.glBegin(primitive);
                for(int i=0;i<size;i+=5){GL11.glTexCoord2d(data[i+3],data[i+4]);GL11.glVertex3d(data[i],data[i+1],data[i+2]);}
                GL11.glEnd();
            }
        });
    }
    public LwjglGlowCompositeBackend(GameAccess game,LwjglPostProcessBackend.StateAccess state,BatchFactory batches) {
        this.game=game;this.state=state;this.batches=batches;
    }
    public Object marker(){return game.marker();}public void setExternalMarker(String value){game.setExternalMarker(value);}
    public GlowCompositePass.Resolution scaledResolution(){return game.scaledResolution();}
    public Framebuffer mainFramebuffer(){return game.mainFramebuffer();}
    public int clientFps(){return game.clientFps();}public int desktopFrequency(){return game.desktopFrequency();}
    public void enableBlend(){state.enableBlend();}public void enableAlpha(){state.enableAlpha();}
    public void enableTexture2D(){state.enableTexture2D();}public void alphaFunc(int function,float reference){state.alphaFunc(function,reference);}
    public void blendFuncSeparate(int sr,int dr,int sa,int da){state.blendFuncSeparate(sr,dr,sa,da);}
    public GlowCompositePass.Batch beginPositionTexture(int primitive){return batches.begin(primitive);}
    public void bindTexture(int texture){state.bindTexture(texture);}
}
