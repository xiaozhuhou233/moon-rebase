package moon.visual.render;

import java.util.Arrays;
import java.util.function.Supplier;
import moon.visual.render.PostProcessBackend.Framebuffer;

/** fH's independent entity outline path. It borrows fF's mask and uses he/hg/hf,
 * but does not use fK's marker policy, live Bezier curve or fixed color values.
 * Encrypted integers and the unused fB constructor/getter remain explicit inputs.
 * There is deliberately no failure cleanup around the original render methods. */
public final class EntityOutlinePass implements AutoCloseable {
    public interface Host extends GlowOutlinePass.Host {
        /** Original GL13 call, bypassing the game's active-texture cache. */
        void rawActiveTexture(int textureEnum);
        Object externalMarker();
        void setSentinel(String[] value);
    }
    public interface Config {
        Integer radius(); Float brightness(); Float contrast(); Float saturation(); Boolean enabled();
    }
    public static final class Constants {
        public final int sampleCount, seedFilter, outputFilter, seedTexture, seedGuardTexture;
        public final int floodTexture, stencilTexture, stencilSampler, cleanupFirst, cleanupSecond;
        public final GlowCompositePass.Constants composite;
        /** No inferred GL enum, sample count or blend mode is an implicit default. */
        public Constants(int sampleCount,int seedFilter,int outputFilter,int seedTexture,int seedGuardTexture,
                int floodTexture,int stencilTexture,int stencilSampler,int cleanupFirst,int cleanupSecond,
                GlowCompositePass.Constants composite) {
            this.sampleCount=sampleCount;this.seedFilter=seedFilter;this.outputFilter=outputFilter;
            this.seedTexture=seedTexture;this.seedGuardTexture=seedGuardTexture;this.floodTexture=floodTexture;
            this.stencilTexture=stencilTexture;this.stencilSampler=stencilSampler;
            this.cleanupFirst=cleanupFirst;this.cleanupSecond=cleanupSecond;this.composite=composite;
        }
    }
    private final Host host;
    private final GlowCompositePass.Host composite;
    private final Config config;
    private final Constants constants;
    private final Supplier<Framebuffer> mask;
    private final Framebuffer output;
    private Framebuffer seeds,scratch;
    private final float[] samples;
    private final Object unusedLimiter;
    private int width,height;
    private DrawTask floodTask,outlineTask,baseTask;
    private boolean closed;

    public EntityOutlinePass(Host host,GlowCompositePass.Host composite,Config config,Constants constants,
            Supplier<?> unusedLimiterFactory,Supplier<Framebuffer> mask) {
        this.host=host;this.composite=composite;this.config=config;this.constants=constants;
        output=host.createFramebuffer(1,1,false);
        seeds=host.createFramebuffer(1,1,false);
        scratch=host.createFramebuffer(1,1,false);
        samples=new float[constants.sampleCount];
        unusedLimiter=unusedLimiterFactory.get();
        this.mask=mask;
        Arrays.fill(samples,1F);
    }
    private void requireOpen(){if(closed)throw new IllegalStateException("Entity outline pass closed");}
    private void createTasks(){
        outlineTask=host.createTask(GlowOutlinePass.Kind.OUTLINE);
        floodTask=host.createTask(GlowOutlinePass.Kind.FLOOD);
        baseTask=host.createTask(GlowOutlinePass.Kind.BASE);
    }
    /** fH.b(): fetch the shared mask exactly once. */
    public void update(){requireOpen();prepare(mask.get());}
    /** fH.a(g3). Rebuild even at the same dimensions when either task exists. */
    public void prepare(Framebuffer input){
        requireOpen();Object marker=host.marker();int width=host.displayWidth(),height=host.displayHeight();
        if(marker==null){
            if(this.width!=width||this.height!=height||floodTask!=null||outlineTask!=null){
                this.width=width;this.height=height;
                if(floodTask!=null)floodTask.getState().release();
                if(outlineTask!=null)outlineTask.getState().release();
                if(baseTask!=null)baseTask.getState().release();
                createTasks();
            }
            if(seeds.resizeIfChanged(host.displayWidth(),host.displayHeight()))seeds.setFilter(constants.seedFilter);
            if(scratch.resizeIfChanged(host.displayWidth(),host.displayHeight()))scratch.setFilter(constants.outputFilter);
            if(output.resizeIfChanged(host.displayWidth(),host.displayHeight()))output.setFilter(constants.outputFilter);
            seed(input);
        }else{
            // Bytecode150 ->189 ->237: test l, then release n, leaving l/m alive.
            if(floodTask!=null)baseTask.getState().release();
            createTasks();
            //342 ->403 ->476: e.resize result controls d.filter. Skip seed at512.
            if(seeds.resizeIfChanged(host.displayWidth(),host.displayHeight()))output.setFilter(constants.outputFilter);
        }
        expand(input);
    }
    /** fH.b(g3): the hf base dispatcher normally fails the original hg guard. */
    public void seed(Framebuffer input){
        requireOpen();host.enableTexture2D();Object marker=host.marker();
        host.rawActiveTexture(constants.seedTexture);
        input.bindTexture();seeds.bind(false);seeds.clearBound();
        ShaderDispatcher dispatcher=baseTask.getDispatcher();
        if(marker!=null)return;
        if(dispatcher instanceof GlowDispatchers.Flood){
            GlowDispatchers.Flood flood=(GlowDispatchers.Flood)dispatcher;
            flood.getProgram().use();host.rawActiveTexture(constants.seedGuardTexture);
            host.bindTexture(input.texture());flood.texture.setInt(0);
        }
        baseTask.draw();
    }
    /** fH.c(g3): marker precedes radius, and field exchange precedes every draw. */
    public void expand(Framebuffer input){
        requireOpen();Object marker=host.marker();float radius=config.radius().intValue();
        float jump=(float)(radius-Math.log(radius))/1.6F;
        int loops=Math.min(Math.max((int)Math.ceil(Math.log(radius)),1),5);
        for(int i=loops;i>=0;i--){
            scratch.bind(false);ShaderDispatcher dispatcher=floodTask.getDispatcher();
            if(marker!=null||dispatcher instanceof GlowDispatchers.Flood){
                GlowDispatchers.Flood flood=(GlowDispatchers.Flood)dispatcher;
                flood.getProgram().use();host.rawActiveTexture(constants.floodTexture);
                if(marker==null){
                    host.bindTexture(seeds.texture());flood.texture.setInt(0);
                    flood.jumpDistance.setVector4(jump,0,0,0);
                    flood.texelSize.setVector2(host.displayWidth(),host.displayHeight());
                    jump/=2F;if(jump<1F)jump=1F; // fcmpg preserves NaN.
                }
            }
            Framebuffer previous=seeds;seeds=scratch;scratch=previous;
            floodTask.draw();if(marker!=null)break;
        }
        host.blendFuncSeparate(1,0,1,0);output.bind(false);output.clearBound();
        ShaderDispatcher dispatcher=outlineTask.getDispatcher();
        boolean isOutline=dispatcher instanceof GlowDispatchers.Outline;
        if(marker!=null){
            // At303 the instanceof boolean remains on the stack until bind596.
            host.bindTexture(isOutline?1:0);
        }else{
            if(isOutline){
                GlowDispatchers.Outline outline=(GlowDispatchers.Outline)dispatcher;
                outline.getProgram().use();host.rawActiveTexture(constants.stencilTexture);
                outline.stencilTexture.setInt(constants.stencilSampler);
                host.bindTexture(seeds.texture());host.rawActiveTexture(constants.floodTexture);
                outline.texture.setInt(0);host.bindTexture(input.texture());
                outline.texelSize.setVector2(1F/host.displayWidth(),1F/host.displayHeight());
                outline.radius.setFloat(radius);outline.samples.setFloatArray(samples);
                outline.brightness.setFloat(config.brightness().floatValue());
                outline.contrast.setFloat(config.contrast().floatValue());
                outline.saturation.setFloat(config.saturation().floatValue());
            }
            outlineTask.draw();
            host.rawActiveTexture(constants.cleanupFirst);host.bindTexture(0);
            host.rawActiveTexture(constants.cleanupSecond);host.bindTexture(0);
            host.rawActiveTexture(constants.floodTexture);host.bindTexture(0);
        }
        if(host.externalMarker()!=null)host.setSentinel(new String[1]);
    }
    /** fH.a(): resolution and both dimensions precede the sJ marker read. */
    public void render(){
        requireOpen();GlowCompositePass.Resolution resolution=composite.scaledResolution();
        double width=resolution.width(),height=resolution.height();Object marker=host.marker();
        composite.enableBlend();composite.enableAlpha();composite.enableTexture2D();
        GlowCompositePass.Constants c=constants.composite;
        composite.alphaFunc(c.alphaStart,0);composite.blendFuncSeparate(c.sourceRgb,c.destinationRgb,1,0);
        composite.mainFramebuffer().bind(true);
        GlowCompositePass.Batch batch=composite.beginPositionTexture(c.primitive);
        batch.vertex(0,height,0,0,0);batch.vertex(width,height,0,1,0);
        batch.vertex(width,0,0,1,1);batch.vertex(0,0,0,0,1);
        output.bindTexture();batch.draw();composite.bindTexture(0);composite.alphaFunc(c.alphaRestore,.1F);
        if(marker!=null)composite.setExternalMarker("cosmjb");
    }
    public void afterRender(){ /* Original fH.c() is empty. */ }
    public boolean enabled(){return config.enabled().booleanValue();}
    public Framebuffer output(){return output;}public Framebuffer seeds(){return seeds;}public Framebuffer scratch(){return scratch;}
    public int width(){return width;}public int height(){return height;}
    /** Preserve the original mutable array getter, although initialized to all ones. */
    public float[] samples(){return samples;}public Object unusedLimiter(){return unusedLimiter;}
    public DrawTask floodTask(){return floodTask;}public DrawTask outlineTask(){return outlineTask;}public DrawTask baseTask(){return baseTask;}
    /** Added ownership boundary. The input mask, composite and unused limiter are borrowed. */
    public void close(){
        if(closed)return;
        if(floodTask!=null)floodTask.getState().release();
        if(outlineTask!=null)outlineTask.getState().release();
        if(baseTask!=null)baseTask.getState().release();
        host.releaseFramebuffer(output);host.releaseFramebuffer(seeds);host.releaseFramebuffer(scratch);closed=true;
    }
}
