package moon.visual.render;

import java.io.*;
import java.util.*;
import java.util.function.*;
import moon.visual.shader.*;
import moon.module.visual.EffectsModule;

/** Migration lifecycle, not original fz's endless worker. Construct/render/close on the GL thread. */
public final class ManagedPostProcessor implements AutoCloseable {
    private final LwjglDrawBackend resources;
    private final List<LwjglFramebuffer> framebuffers=new ArrayList<>();
    private LwjglPostProcessBackend backend;
    private CachedShadowPass shadow;private ThrottledBlurPass blur;
    private LwjglFramebuffer shadowInput,shadowOutput,blurInput,blurOutput;
    private final LongSupplier clock;
    private final LwjglPostProcessBackend.StateAccess state;
    private final int maxIterations;
    private double nextBlurUpdate;
    private boolean closed;
    /** Original jM callbacks change only one pass's field, without requesting
     * a shadow rebuild. Tick/requestUpdate and the blur scheduler own that work.
     * The stable views enforce this migration owner's GL-thread/close boundary.
     */
    private final EffectsModule.Pipeline blurView=new EffectsModule.Pipeline(){
        public void render(){renderBlur();}
        public EffectsModule.Pipeline setIterations(int value){requireOpen();checkCapacity(value);blur.setIterations(value);return this;}
        public EffectsModule.Pipeline setOffset(int value){requireOpen();blur.setOffset(value);return this;}
    };
    private final EffectsModule.ShadowPipeline shadowView=new EffectsModule.ShadowPipeline(){
        public void render(){renderShadow();}
        public void requestUpdate(){requestShadowUpdate();}
        public EffectsModule.Pipeline setIterations(int value){requireOpen();checkCapacity(value);shadow.setIterations(value);return this;}
        public EffectsModule.Pipeline setOffset(int value){requireOpen();shadow.setOffset(value);return this;}
    };
    public ManagedPostProcessor(LwjglPostProcessBackend.Host host,LwjglPostProcessBackend.StateAccess state,
            ShaderSourceLoader.ResourceAccess resourceAccess,ShaderDiagnostics diagnostics,int iterations,int offset,
            LongSupplier clock) throws IOException {
        this(host,state,resourceAccess,diagnostics,iterations,offset,iterations,offset,clock);
    }
    public ManagedPostProcessor(LwjglPostProcessBackend.Host host,LwjglPostProcessBackend.StateAccess state,
            ShaderSourceLoader.ResourceAccess resourceAccess,ShaderDiagnostics diagnostics,
            int blurIterations,int blurOffset,int shadowIterations,int shadowOffset,LongSupplier clock) throws IOException {
        this(host,state,resourceAccess,diagnostics,blurIterations,blurOffset,shadowIterations,shadowOffset,clock,false);
    }
    /** Original jM storage: shared input and six levels, distinct output caches.
     * Requests1x1 in the original map to this owner's explicit2x2 minimum.
     * Existing public constructors retain their separate1..11 storage contract.
     * The render-thread scheduler remains a migration policy, not an old worker.
     */
    public static ManagedPostProcessor originalEffects(LwjglPostProcessBackend.Host host,LwjglPostProcessBackend.StateAccess state,
            ShaderSourceLoader.ResourceAccess resourceAccess,ShaderDiagnostics diagnostics,int iterations,int offset,LongSupplier clock) throws IOException {
        return new ManagedPostProcessor(host,state,resourceAccess,diagnostics,iterations,offset,iterations,offset,clock,true);
    }
    private ManagedPostProcessor(LwjglPostProcessBackend.Host host,LwjglPostProcessBackend.StateAccess state,
            ShaderSourceLoader.ResourceAccess resourceAccess,ShaderDiagnostics diagnostics,
            int blurIterations,int blurOffset,int shadowIterations,int shadowOffset,LongSupplier clock,boolean sharedEffects) throws IOException {
        maxIterations=sharedEffects?EffectsModule.MAX_ITERATIONS:11;
        checkCapacity(blurIterations);checkCapacity(shadowIterations);
        this.clock=clock;this.state=state;resources=new LwjglDrawBackend(true,state);
        try {
            DrawServices.MarkerHooks markers=new DrawServices.MarkerHooks(){public Object getMarker(){return null;}public void setMarker(String v){ }};
            // h_ deliberately leaves streams open. Close real host streams at this migration boundary.
            ShaderSourceLoader.ResourceAccess detached=path->{
                try(InputStream input=resourceAccess.open(path)){
                    if(input==null)return null;ByteArrayOutputStream data=new ByteArrayOutputStream();
                    byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)data.write(buffer,0,n);
                    return new ByteArrayInputStream(data.toByteArray());
                }
            };
            DrawServices services=new DrawServices(resources,diagnostics,markers,detached,new EngineAttributeParser(),host::scaledWidth,host::scaledHeight);
            backend=new LwjglPostProcessBackend(host,state,services,resources);
            if(sharedEffects){
                shadowInput=blurInput=create();
                LwjglFramebuffer[] shared=pyramid(EffectsModule.FRAMEBUFFER_COUNT);
                blurOutput=create();
                blur=new ThrottledBlurPass(blurInput,shared,blurOutput,blurIterations,blurOffset,backend,()->true,clock);
                shadowOutput=create();
                shadow=new CachedShadowPass(shadowInput,shared,shadowOutput,shadowIterations,shadowOffset,backend,()->true,clock);
            }else{
                shadowInput=create();shadowOutput=create();blurInput=create();blurOutput=create();
                LwjglFramebuffer[] sp=pyramid(12),bp=pyramid(12);
                shadow=new CachedShadowPass(shadowInput,sp,shadowOutput,shadowIterations,shadowOffset,backend,()->true,clock);
                blur=new ThrottledBlurPass(blurInput,bp,blurOutput,blurIterations,blurOffset,backend,()->true,clock);
            }
            shadow.requestUpdate();blur.setDirty(true);
        }catch(IOException|RuntimeException|Error e){close();throw e;}
    }
    private LwjglFramebuffer create(){LwjglFramebuffer f=new LwjglFramebuffer(2,2,false,state);framebuffers.add(f);f.setClearColor(0,0,0,0);return f;}
    private LwjglFramebuffer[] pyramid(int count){LwjglFramebuffer[] p=new LwjglFramebuffer[count];for(int i=0;i<p.length;i++){p[i]=create();p[i].setFilter(9729);p[i].setWrap(33648);}return p;}
    private void requireOpen(){resources.requireOpen();if(closed)throw new IllegalStateException("Postprocessor closed");}
    private void checkCapacity(int value){if(value<1||value>maxIterations)throw new IllegalArgumentException("Iterations must be 1.."+maxIterations);}
    public EffectsModule.Pipeline blurPipeline(){requireOpen();return blurView;}
    public EffectsModule.ShadowPipeline shadowPipeline(){requireOpen();return shadowView;}
    public void renderShadow(){requireOpen();shadow.render();}
    /** The render thread requests an update at 60Hz. No background thread touches dirty flags or GL. */
    public void renderBlur(){requireOpen();long now=clock.getAsLong();if(now>=nextBlurUpdate){blur.setDirty(true);nextBlurUpdate=now+1000.0/60.0;}blur.render();}
    public void requestShadowUpdate(){requireOpen();shadow.requestUpdate();}
    public void requestBlurUpdate(){requireOpen();blur.setDirty(true);}
    /** Explicit migration reconfiguration with immediate invalidation. Original
     * jM callbacks use the views above and retain the original dirty timing. */
    public void configure(int iterations,int offset){requireOpen();checkCapacity(iterations);configureShadow(iterations,offset);configureBlur(iterations,offset);}
    public void configureShadow(int iterations,int offset){requireOpen();checkCapacity(iterations);shadow.setIterations(iterations).setOffset(offset).requestUpdate();}
    public void configureBlur(int iterations,int offset){requireOpen();checkCapacity(iterations);blur.setIterations(iterations).setOffset(offset).setDirty(true);}
    public void setShadowMaskCallback(Consumer<Boolean> callback){requireOpen();shadow.setMaskCallback(callback);}
    public PostProcessBackend.Framebuffer getShadowInput(){return shadowInput;}public PostProcessBackend.Framebuffer getShadowOutput(){return shadowOutput;}
    public PostProcessBackend.Framebuffer getBlurInput(){return blurInput;}public PostProcessBackend.Framebuffer getBlurOutput(){return blurOutput;}
    public int liveDrawResources(){return resources.liveResources();}public int liveNativeAllocations(){return resources.liveNativeAllocations();}
    public int liveFramebuffers(){int n=0;for(LwjglFramebuffer f:framebuffers)if(f.getFramebuffer()!=-1)n++;return n;}
    @Override public void close(){
        if(closed){resources.close();return;}
        // requireOpen enforces context-thread ownership before any destructive operation.
        resources.requireOpen();for(int i=framebuffers.size()-1;i>=0;i--)framebuffers.get(i).close();resources.close();closed=true;
    }
}
