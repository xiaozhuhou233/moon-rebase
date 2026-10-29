package moon.visual.render;

import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL11;

/** Actual compatibility texture-environment calls. Pass the destination's
 * OpenGlHelper.setLightmapTextureCoords and shader-color bridge; both maintain
 * game-side state in addition to issuing GL calls. No shader hook is discarded. */
public final class LwjglGlowMaskColorBackend implements GlowMaskColorEnvironment.Backend {
    public interface Lightmap { void set(int unit,float x,float y); }
    public interface Shaders { boolean enabled();void color(float r,float g,float b,float a); }
    private final LwjglPostProcessBackend.StateAccess state;private final Lightmap lightmap;private final Shaders shaders;
    public LwjglGlowMaskColorBackend(LwjglPostProcessBackend.StateAccess state,Lightmap lightmap,Shaders shaders){
        this.state=state;this.lightmap=lightmap;this.shaders=shaders;
    }
    public void lightmap(int unit,float x,float y){lightmap.set(unit,x,y);}
    public void activeTexture(int unit){state.activeTexture(unit);}
    public void enableTexture2D(){state.enableTexture2D();}public void disableTexture2D(){state.disableTexture2D();}
    public void texEnvi(int target,int parameter,int value){GL11.glTexEnvi(target,parameter,value);}
    public void texEnv(int target,int parameter,FloatBuffer color){GL11.glTexEnvfv(target,parameter,color);}
    public boolean shadersEnabled(){return shaders.enabled();}public void shaderColor(float r,float g,float b,float a){shaders.color(r,g,b,a);}
    public void color(float r,float g,float b,float a){state.color(r,g,b,a);}
}
