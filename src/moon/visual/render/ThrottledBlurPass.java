package moon.visual.render;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import moon.module.visual.EffectsModule;
import moon.visual.render.PostProcessBackend.Framebuffer;

/** Recovered fz render/control flow for both captured guard values.
 * API and FBO ownership remain with ManagedPostProcessor. Constants use
 * POSTPROCESS_CONSTANTS finite candidate policy, not runtime confirmation.
 * Original constructor/global-marker/infinite-worker lifecycle remains
 * an explicit migration boundary; no worker is started here.
 */
public final class ThrottledBlurPass implements EffectsModule.Pipeline {
    public interface FrameLimiter { void sync(int framesPerSecond); }
    private static final int[] DIVISORS = {2,4,8,16,32,64,128,256,512,1024,2084,4096};
    private final Framebuffer input;
    private final Framebuffer[] pyramid;
    private final Framebuffer cachedOutput;
    private final PostProcessBackend backend;
    private final BooleanSupplier guardIsNull;
    private final LongSupplier clock;
    private final KawaseStageExecutor stages;
    private int iterations, offset;
    private int updatesInInterval, updatesLastInterval;
    private long intervalStart;
    private boolean dirty;

    public ThrottledBlurPass(Framebuffer input, Framebuffer[] pyramid, Framebuffer cachedOutput,
            int iterations, int offset, PostProcessBackend backend,
            BooleanSupplier guardIsNull, LongSupplier clock) {
        this.input = input;
        this.pyramid = pyramid;
        this.cachedOutput = cachedOutput;
        this.iterations = iterations;
        this.offset = offset;
        this.backend = backend;
        this.guardIsNull = guardIsNull;
        this.clock = clock;
        stages = new KawaseStageExecutor(backend, guardIsNull);
        intervalStart = clock.getAsLong();
    }

    @Override public void render() {
        boolean normal = guardIsNull.getAsBoolean(); // Original offset8 before visibility.
        boolean visible = backend.isWindowVisible();
        if (!normal) {
            // Offsets16->49->93->138->605 carry visibility on the operand stack.
            // They bypass resize30..46, clock81..92 and dirty read134..135.
            composite(visible ? 1 : 0);
            return;
        }
        if (!visible) return;
        if (cachedOutput.resizeIfChanged(backend.displayWidth(), backend.displayHeight())) {
            input.setClearColor(0,0,0,0); // fz updates input, not cachedOutput.
        }
        if (clock.getAsLong() - intervalStart >= 1000L) {
            updatesLastInterval = updatesInInterval;
            updatesInInterval = 0;
            intervalStart = clock.getAsLong();
        }
        if (dirty) {
            updatesInInterval++;
            cachedOutput.clearAndUnbind();
            if (input.resizeIfChanged(backend.displayWidth(), backend.displayHeight())) {
                input.setClearColor(0,0,0,0);
            }
            for (int i = 0; i < pyramid.length; i++) {
                Framebuffer level = pyramid[i];
                if (level.resizeIfChanged(backend.displayWidth() / DIVISORS[i],
                        backend.displayHeight() / DIVISORS[i])) {
                    level.setFilter(9729);
                    level.setWrap(33648);
                }
            }
            input.clearAndUnbind();
            input.bind(false);
            backend.blendFuncSeparate(1,0,1,0);
            backend.publishMaskEvent(false);
            for (int i = 0; i < iterations; i++) {
                int texture = i == 0 ? backend.mainFramebuffer().texture() : pyramid[i].texture();
                stages.down(false, texture, pyramid[i + 1], (float) offset);
            }
            for (int i = iterations; i > 0; i--) {
                stages.up(false, pyramid[i].texture(), i == 1 ? cachedOutput : pyramid[i - 1],
                        input, cachedOutput, (float) offset);
            }
            dirty = false;
        }
        backend.enableBlend();
        backend.enableAlpha();
        backend.enableTexture2D();
        backend.alphaFunc(516,0.0F);
        composite(770);
    }

    private void composite(int sourceFactor) {
        backend.blendFuncSeparate(sourceFactor,771,1,0);
        double width = backend.scaledWidth(), height = backend.scaledHeight();
        cachedOutput.bindTexture();
        backend.mainFramebuffer().bind(true);
        backend.drawCompositeQuad(7,width,height);
        backend.bindTexture(0);
        backend.disableTexture2D();
    }

    public void runOriginalWorker(FrameLimiter limiter) {
        while (true) {
            dirty = true;
            limiter.sync(60);
        }
    }
    public ThrottledBlurPass setDirty(boolean value) { dirty = value; return this; }
    @Override public ThrottledBlurPass setIterations(int value) { iterations = value; return this; }
    @Override public ThrottledBlurPass setOffset(int value) { offset = value; return this; }
    public int getUpdatesLastInterval() { return updatesLastInterval; }
    public boolean isDirty() { return dirty; }
}
