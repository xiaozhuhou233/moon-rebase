package moon.visual.render;

import java.util.function.Supplier;
import moon.visual.render.PostProcessBackend.Framebuffer;

/** fK: seed -> jump flood -> curve outline. Full fG/fJ composition is separate.
 * Eight encrypted constants remain explicit: no inferred GL enums are defaults.
 * Original task rebuilds, marker branches, getter order and failure residue stay.
 */
public final class GlowOutlinePass implements AutoCloseable {
    public enum Kind { OUTLINE, FLOOD, BASE }
    public interface Host {
        Object marker(); int displayWidth(); int displayHeight();
        Framebuffer createFramebuffer(int width, int height, boolean depth);
        DrawTask createTask(Kind kind);
        void enableTexture2D(); void activeTexture(int textureEnum); void bindTexture(int texture);
        void blendFuncSeparate(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha);
        void releaseFramebuffer(Framebuffer framebuffer);
    }
    public static final class Constants {
        public final int seedFilter, outputFilter, sourceTexture, floodTexture;
        public final int stencilTexture, stencilSampler, cleanupFirst, cleanupSecond;
        /** Values must come from evidence or an explicitly chosen destination configuration. */
        public Constants(int seedFilter, int outputFilter, int sourceTexture, int floodTexture,
                int stencilTexture, int stencilSampler, int cleanupFirst, int cleanupSecond) {
            this.seedFilter=seedFilter; this.outputFilter=outputFilter;
            this.sourceTexture=sourceTexture; this.floodTexture=floodTexture;
            this.stencilTexture=stencilTexture; this.stencilSampler=stencilSampler;
            this.cleanupFirst=cleanupFirst; this.cleanupSecond=cleanupSecond;
        }
    }
    private final Host host;
    private final Constants constants;
    private final Supplier<Integer> radius;
    private final Supplier<float[]> curve;
    private final Framebuffer output;
    private Framebuffer seeds, scratch;
    private int width, height;
    private DrawTask floodTask, outlineTask, baseTask;
    private boolean closed;

    public GlowOutlinePass(Host host, Constants constants, Supplier<Integer> radius, Supplier<float[]> curve) {
        this.host=host; this.constants=constants;
        output=host.createFramebuffer(1,1,false);
        seeds=host.createFramebuffer(1,1,false);
        scratch=host.createFramebuffer(1,1,false);
        this.radius=radius; this.curve=curve;
    }
    private void requireOpen() { if(closed) throw new IllegalStateException("Glow outline pass closed"); }
    private void createTasks() {
        outlineTask=host.createTask(Kind.OUTLINE);
        floodTask=host.createTask(Kind.FLOOD);
        baseTask=host.createTask(Kind.BASE);
    }
    /** fK.a. Same-sized nonnull flood task deliberately triggers a rebuild. */
    public void render(Framebuffer input) {
        requireOpen(); Object marker=host.marker();
        int width=host.displayWidth(), height=host.displayHeight();
        if(marker!=null) {
            if(this.width!=width || this.height!=height || floodTask!=null || outlineTask!=null) {
                this.width=width; this.height=height;
                if(floodTask!=null) floodTask.getState().release();
                if(outlineTask!=null) outlineTask.getState().release();
                if(baseTask!=null) baseTask.getState().release();
                createTasks();
            }
            if(seeds.resizeIfChanged(host.displayWidth(),host.displayHeight())) seeds.setFilter(constants.seedFilter);
            if(scratch.resizeIfChanged(host.displayWidth(),host.displayHeight())) scratch.setFilter(constants.outputFilter);
            if(output.resizeIfChanged(host.displayWidth(),host.displayHeight())) output.setFilter(constants.outputFilter);
        } else {
            // Offsets150 ->189 ->237 use h, not i/j. The old h/i are not released.
            if(floodTask!=null) baseTask.getState().release();
            createTasks();
            // Offset342 ->403 ->476 skips e/c resize and tests d's resize result.
            if(seeds.resizeIfChanged(host.displayWidth(),host.displayHeight())) output.setFilter(constants.outputFilter);
        }
        seed(input); expand(input);
    }
    /** fK.b: retain the original mismatched hg guard for the hf base task. */
    public void seed(Framebuffer input) {
        requireOpen(); Object marker=host.marker();
        host.enableTexture2D(); host.activeTexture(constants.sourceTexture);
        input.bindTexture(); seeds.bind(false); seeds.clearBound();
        ShaderDispatcher dispatcher=baseTask.getDispatcher();
        if(marker==null) return;
        if(dispatcher instanceof GlowDispatchers.Flood) {
            GlowDispatchers.Flood flood=(GlowDispatchers.Flood)dispatcher;
            flood.getProgram().use(); host.activeTexture(constants.sourceTexture);
            host.bindTexture(input.texture()); flood.texture.setInt(0);
        }
        baseTask.draw();
    }
    /** fK.c. The original loop is inclusive of zero and swaps fields before draw. */
    public void expand(Framebuffer input) {
        requireOpen(); float radius=this.radius.get().intValue(); Object marker=host.marker();
        float jump=(float)(radius-Math.log(radius))/1.6F;
        int loops=Math.min(Math.max((int)Math.ceil(Math.log(radius)),1),5);
        for(int i=loops;i>=0;i--) {
            scratch.bind(false); ShaderDispatcher dispatcher=floodTask.getDispatcher();
            if(marker==null || dispatcher instanceof GlowDispatchers.Flood) {
                GlowDispatchers.Flood flood=(GlowDispatchers.Flood)dispatcher;
                flood.getProgram().use(); host.activeTexture(constants.floodTexture);
                if(marker!=null) {
                    host.bindTexture(seeds.texture()); flood.texture.setInt(0);
                    flood.jumpDistance.setVector4(jump,0,0,0);
                    flood.texelSize.setVector2(host.displayWidth(),host.displayHeight());
                    jump/=2F; if(jump<1F) jump=1F; // fcmpg leaves NaN unchanged.
                }
            }
            Framebuffer previous=seeds; seeds=scratch; scratch=previous;
            floodTask.draw(); if(marker==null) break;
        }
        host.blendFuncSeparate(1,0,1,0); output.bind(false); output.clearBound();
        ShaderDispatcher dispatcher=outlineTask.getDispatcher();
        if(marker==null) { host.bindTexture(0); return; }
        if(dispatcher instanceof GlowDispatchers.Outline) {
            GlowDispatchers.Outline outline=(GlowDispatchers.Outline)dispatcher;
            outline.getProgram().use(); host.activeTexture(constants.stencilTexture);
            outline.stencilTexture.setInt(constants.stencilSampler);
            host.bindTexture(seeds.texture()); host.activeTexture(constants.sourceTexture);
            outline.texture.setInt(0); host.bindTexture(input.texture());
            outline.texelSize.setVector2(1F/host.displayWidth(),1F/host.displayHeight());
            outline.radius.setFloat(radius); outline.samples.setFloatArray(curve.get());
            outline.brightness.setFloat(0); outline.contrast.setFloat(1); outline.saturation.setFloat(1);
        }
        outlineTask.draw();
        host.activeTexture(constants.cleanupFirst); host.bindTexture(0);
        host.activeTexture(constants.cleanupSecond); host.bindTexture(0);
        host.activeTexture(constants.sourceTexture); host.bindTexture(0);
    }
    public Framebuffer output() { return output; }
    public Framebuffer seeds() { return seeds; }
    public Framebuffer scratch() { return scratch; }
    public int width() { return width; }
    public int height() { return height; }
    public DrawTask floodTask() { return floodTask; }
    public DrawTask outlineTask() { return outlineTask; }
    public DrawTask baseTask() { return baseTask; }
    /** Added migration ownership boundary; original fK has no close method. */
    public void close() {
        if(closed) return;
        if(floodTask!=null) floodTask.getState().release();
        if(outlineTask!=null) outlineTask.getState().release();
        if(baseTask!=null) baseTask.getState().release();
        host.releaseFramebuffer(output); host.releaseFramebuffer(seeds); host.releaseFramebuffer(scratch);
        closed=true;
    }
}
