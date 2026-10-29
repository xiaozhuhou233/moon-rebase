package moon.visual.render;

import java.awt.Color;
import java.nio.*;

/** kR: interpolate constant and previous RGB with texture-environment alpha.
 * fF supplies opaque Color(int), producing flat RGB while preserving texture
 * alpha. Shared buffer, shader hooks and nondefault Y branches remain. */
public final class GlowMaskColorEnvironment {
    public interface Backend {
        void lightmap(int unit,float x,float y);void activeTexture(int unit);
        void enableTexture2D();void disableTexture2D();
        void texEnvi(int target,int parameter,int value);void texEnv(int target,int parameter,FloatBuffer color);
        boolean shadersEnabled();void shaderColor(float r,float g,float b,float a);void color(float r,float g,float b,float a);
    }
    public static final class Parameters {
        public final int defaultUnit,lightmapUnit,target,mode,color,combine,combineRgb,interpolate,source0,constant;
        public final int modulate,operand0,operand1,sourceColor,texture,source1,previous;
        public Parameters(int defaultUnit,int lightmapUnit,int target,int mode,int color,int combine,int combineRgb,int interpolate,
                int source0,int constant,int modulate,int operand0,int operand1,int sourceColor,int texture,int source1,int previous){
            this.defaultUnit=defaultUnit;this.lightmapUnit=lightmapUnit;this.target=target;this.mode=mode;this.color=color;
            this.combine=combine;this.combineRgb=combineRgb;this.interpolate=interpolate;this.source0=source0;this.constant=constant;
            this.modulate=modulate;this.operand0=operand0;this.operand1=operand1;this.sourceColor=sourceColor;
            this.texture=texture;this.source1=source1;this.previous=previous;
        }
        /** kR15 calls and ln fields decoded offline; neither seed is runtime supplied. */
        public static Parameters freshStateDecodeCandidate(){
            return new Parameters(33984,33985,8960,8704,8705,34160,34161,34165,34176,34166,8448,34192,34193,768,5890,34177,34168);
        }
    }
    private static final FloatBuffer COLOR=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final Backend backend;private final Parameters p;
    public GlowMaskColorEnvironment(Backend backend,Parameters parameters){this.backend=backend;this.p=parameters;}
    public void apply(Color color){
        int marker=ArgbColor.inverseSentinel();
        backend.lightmap(p.lightmapUnit,1,1);backend.activeTexture(p.lightmapUnit);backend.enableTexture2D();
        backend.texEnvi(p.target,p.combineRgb,p.interpolate);backend.texEnvi(p.target,p.source0,p.constant);
        COLOR.position(0);COLOR.put(color.getRed()/255F).put(color.getGreen()/255F).put(color.getBlue()/255F).put(color.getAlpha()/255F);
        boolean shaders=backend.shadersEnabled();
        if(marker!=0){backend.activeTexture(shaders?1:0);return;} // Raw BT.ap() Z becomes kY.g(int).
        if(shaders)backend.shaderColor(color.getRed()/255F,color.getGreen()/255F,color.getBlue()/255F,color.getAlpha()/255F);
        COLOR.flip();backend.texEnv(p.target,p.color,COLOR);backend.activeTexture(p.defaultUnit);
    }
    public void reset(){
        backend.enableTexture2D();backend.activeTexture(p.lightmapUnit);backend.texEnvi(p.target,p.mode,p.combine);
        int marker=ArgbColor.getSentinel();
        backend.texEnvi(p.target,p.combineRgb,p.modulate);backend.texEnvi(p.target,p.operand0,p.sourceColor);
        backend.texEnvi(p.target,p.operand1,p.sourceColor);backend.texEnvi(p.target,p.source0,p.texture);
        backend.texEnvi(p.target,p.source1,p.previous);
        if(marker==0){backend.shaderColor(1,1,1,1);return;}
        backend.color(1,1,1,1);backend.disableTexture2D();backend.activeTexture(p.defaultUnit);
        if(backend.shadersEnabled())backend.shaderColor(0,0,0,0);
    }
}
