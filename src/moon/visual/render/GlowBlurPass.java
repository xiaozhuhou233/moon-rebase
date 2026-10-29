package moon.visual.render;

import moon.visual.render.PostProcessBackend.Framebuffer;

/** fJ. One down draw, then the original count-controlled up chain; not a
 * conventional down pyramid. Explicit parameters distinguish an offline table
 * decode from a supplied runtime seed. Requires normalized RGBA16 framebuffers.
 */
public final class GlowBlurPass implements AutoCloseable {
    public interface Config {
        Integer iterations(); Float offset(); Float alpha(); Boolean stencil();
    }
    public interface Host {
        Object marker(); Object externalMarker(); void setMarker(Object[] value);
        int displayWidth(); int displayHeight();
        Framebuffer createFramebuffer(int width,int height,boolean depth);
        DrawTask createBlurTask(boolean up);
        void blendFuncSeparate(int sr,int dr,int sa,int da);
        void rawClearColor(float r,float g,float b,float a);
        void activeTexture(int textureEnum); void bindTexture(int texture);
        void releaseFramebuffer(Framebuffer framebuffer);
    }
    public static final class Parameters {
        public final int filter,wrap,levelCount,texture0,sourceTexture,sourceSampler,stencilTexture,stencilSampler;
        private final int[] divisors;
        public Parameters(int filter,int wrap,int levelCount,int[] divisors,int texture0,
                int sourceTexture,int sourceSampler,int stencilTexture,int stencilSampler) {
            this.filter=filter; this.wrap=wrap; this.levelCount=levelCount; this.divisors=divisors.clone();
            this.texture0=texture0; this.sourceTexture=sourceTexture; this.sourceSampler=sourceSampler;
            this.stencilTexture=stencilTexture; this.stencilSampler=stencilSampler;
        }
        /** All33 calls decoded offline. Seed was NOT supplied by runtime capture. */
        public static Parameters freshStateDecodeCandidate() {
            return new Parameters(9729,33648,6,new int[]{1,2,4,8,16,32,64,128,256,512,1024,2084,4096},33984,34005,21,34006,22);
        }
    }
    private final Host host;
    private final Parameters parameters;
    private Config config;
    private final Framebuffer output,half;
    private final Framebuffer[] levels;
    private int width,height;
    private DrawTask downTask,upTask;
    private boolean closed;
    public GlowBlurPass(Host host,Parameters parameters,Config config) {
        this.host=host; this.parameters=parameters;
        Object marker=host.marker();
        output=host.createFramebuffer(1,1,false); half=host.createFramebuffer(1,1,false);
        levels=new Framebuffer[parameters.levelCount];
        for(int i=0;i<levels.length;i++) {
            levels[i]=host.createFramebuffer(1,1,false);
            if(marker==null) return; // Original constructor leaves config and later entries null.
        }
        this.config=config;
    }
    private void requireOpen(){if(closed)throw new IllegalStateException("Glow blur pass closed");}
    private void createTasks(){downTask=host.createBlurTask(false); upTask=host.createBlurTask(true);}
    public void render(Framebuffer mask,Framebuffer outline) {
        requireOpen(); int iterations=config.iterations().intValue(); float offset=config.offset().floatValue();
        int width=host.displayWidth(); Object marker=host.marker(); int height=host.displayHeight();
        if(marker!=null) {
            if(this.width!=width || this.height!=height || downTask==null || upTask==null) {
                this.width=width; this.height=height;
                if(downTask!=null)downTask.getState().release();
                if(upTask!=null)upTask.getState().release(); createTasks();
            }
            if(output.resizeIfChanged(host.displayWidth(),host.displayHeight()))output.setFilter(parameters.filter);
            if(half.resizeIfChanged(host.displayWidth()/2,host.displayHeight()/2))half.setFilter(parameters.filter);
            for(int i=0;i<levels.length;i++) {
                int divisor=parameters.divisors[i]; Framebuffer target=levels[i];
                if(target.resizeIfChanged(host.displayWidth()/divisor,host.displayHeight()/divisor)) {
                    target.setFilter(parameters.filter); target.setWrap(parameters.wrap);
                }
            }
        } else {
            // Offsets186->237 test i but release j; width/height bookkeeping skipped.
            if(downTask!=null)upTask.getState().release(); createTasks();
            output.resizeIfChanged(host.displayWidth(),host.displayHeight());
        }
        host.blendFuncSeparate(1,0,1,0); down(outline,half,offset);
        for(int i=iterations;i>0;i--) {
            // Null marker passes loop index as texture id (offset591 ->620).
            int source=marker==null?i:i==iterations?half.texture():levels[i].texture();
            Framebuffer target=i==1?output:levels[i-1]; up(mask,source,target,offset);
            if(marker==null)break;
        }
        if(host.externalMarker()!=null)host.setMarker(new Object[1]);
    }
    public void down(Framebuffer source,Framebuffer target,float offset) {
        requireOpen(); Object marker=host.marker(); ShaderDispatcher dispatcher=downTask.getDispatcher();
        if(marker==null){host.bindTexture(0);return;}
        if(dispatcher instanceof KawaseDispatcher && "glow_down".equals(dispatcher.descriptor().getFragment())) {
            KawaseDispatcher down=(KawaseDispatcher)dispatcher;
            down.getProgram().use(); target.bind(true); host.rawClearColor(0,0,0,0); target.clearBound();
            host.activeTexture(parameters.texture0); source.bindTexture(); down.texture.setInt(0);
            down.texelSize.setVector2(1F/target.width(),1F/target.height());
            down.offset.setVector2(offset,offset); down.alpha.setFloat(1);
        }
        downTask.draw(); host.activeTexture(parameters.sourceTexture); host.bindTexture(0);
        host.activeTexture(parameters.texture0); host.bindTexture(0);
    }
    public void up(Framebuffer mask,int sourceTexture,Framebuffer target,float offset) {
        requireOpen(); Object marker=host.marker(); ShaderDispatcher dispatcher=upTask.getDispatcher();
        if(marker==null){host.bindTexture(0);return;}
        if(dispatcher instanceof KawaseDispatcher && "glow_up".equals(dispatcher.descriptor().getFragment())) {
            KawaseDispatcher up=(KawaseDispatcher)dispatcher;
            up.getProgram().use(); target.bind(true); host.rawClearColor(0,0,0,0); target.clearBound();
            up.stencil.setInt(parameters.stencilSampler); host.activeTexture(parameters.stencilTexture);
            host.bindTexture(mask.texture()); up.texture.setInt(parameters.sourceSampler);
            host.activeTexture(parameters.sourceTexture); host.bindTexture(sourceTexture);
            up.texelSize.setVector2(1F/target.width(),1F/target.height()); up.offset.setVector2(offset,offset);
            up.alpha.setFloat(target==output?config.alpha().floatValue():1F);
            up.useStencil.setBoolean(target==output&&config.stencil().booleanValue());
        }
        upTask.draw(); host.activeTexture(parameters.stencilTexture); host.bindTexture(0);
        host.activeTexture(parameters.sourceTexture); host.bindTexture(0);
        host.activeTexture(parameters.texture0); host.bindTexture(0);
    }
    public Framebuffer output(){return output;} public Framebuffer half(){return half;}
    /** Original getter exposes the live array; retain index/null failures on mutation. */
    public Framebuffer[] levels(){return levels;}
    public int width(){return width;}public int height(){return height;}
    public DrawTask downTask(){return downTask;}public DrawTask upTask(){return upTask;}
    /** Added migration cleanup; null constructor entries are owned only if allocated. */
    public void close(){
        if(closed)return;
        if(downTask!=null)downTask.getState().release();if(upTask!=null)upTask.getState().release();
        host.releaseFramebuffer(output);host.releaseFramebuffer(half);
        for(Framebuffer framebuffer:levels)if(framebuffer!=null)host.releaseFramebuffer(framebuffer);
        closed=true;
    }
}
