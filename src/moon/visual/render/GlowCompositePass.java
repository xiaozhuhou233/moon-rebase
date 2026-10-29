package moon.visual.render;

import java.util.function.Supplier;
import moon.visual.render.PostProcessBackend.Framebuffer;

/** fG's update/composite methods. fD entity filtering and fF mask capture remain
 * separate host responsibilities; this class does not claim to restore them.
 * The five encrypted integers are required inputs, with no guessed defaults. */
public final class GlowCompositePass {
    public interface Config { Boolean blur(); Boolean enabled(); }
    public interface Resolution { double width(); double height(); }
    /** CPU batch construction must precede texture binding and GPU submission. */
    public interface Batch {
        void vertex(double x,double y,double z,double u,double v);
        void draw();
    }
    public interface Host {
        Object marker(); void setExternalMarker(String value);
        Resolution scaledResolution(); Framebuffer mainFramebuffer();
        void enableBlend(); void enableAlpha(); void enableTexture2D();
        void alphaFunc(int function,float reference);
        void blendFuncSeparate(int sr,int dr,int sa,int da);
        Batch beginPositionTexture(int primitive); void bindTexture(int texture);
        int clientFps(); int desktopFrequency();
    }
    public static final class Constants {
        public final int alphaStart,sourceRgb,destinationRgb,primitive,alphaRestore;
        public Constants(int alphaStart,int sourceRgb,int destinationRgb,int primitive,int alphaRestore) {
            this.alphaStart=alphaStart;this.sourceRgb=sourceRgb;this.destinationRgb=destinationRgb;
            this.primitive=primitive;this.alphaRestore=alphaRestore;
        }
    }
    private final Host host;
    private final Config config;
    private final Constants constants;
    private final Supplier<Framebuffer> mask;
    private final GlowOutlinePass outline;
    private final GlowBlurPass blur;
    /** Passes and mask are borrowed. Their creator owns resize/close ordering. */
    public GlowCompositePass(Host host,Config config,Constants constants,Supplier<Framebuffer> mask,
            GlowOutlinePass outline,GlowBlurPass blur) {
        this.host=host;this.config=config;this.constants=constants;this.mask=mask;this.outline=outline;this.blur=blur;
    }
    public void update() {
        Object marker=host.marker();
        outline.render(mask.get());
        if(marker==null||config.blur().booleanValue())blur.render(mask.get(),outline.output());
    }
    public void render() {
        Object marker=host.marker();
        Framebuffer source=marker!=null&&config.blur().booleanValue()?blur.output():outline.output();
        Resolution resolution=host.scaledResolution();double width=resolution.width(),height=resolution.height();
        host.enableBlend();host.enableAlpha();host.enableTexture2D();
        host.alphaFunc(constants.alphaStart,0);
        host.blendFuncSeparate(constants.sourceRgb,constants.destinationRgb,1,0);
        host.mainFramebuffer().bind(true);
        Batch batch=host.beginPositionTexture(constants.primitive);
        batch.vertex(0,height,0,0,0);batch.vertex(width,height,0,1,0);
        batch.vertex(width,0,0,1,1);batch.vertex(0,0,0,0,1);
        source.bindTexture();batch.draw();host.bindTexture(0);
        host.alphaFunc(constants.alphaRestore,.1F);
        if(marker==null)host.setExternalMarker("VGnCW");
    }
    public void afterRender(){ /* fG.c() is empty. */ }
    public boolean enabled(){return config.enabled().booleanValue();}
    public int updateFrequency(){return Math.max(host.clientFps()/5,host.desktopFrequency());}
}
