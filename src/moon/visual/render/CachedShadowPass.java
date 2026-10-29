package moon.visual.render;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import moon.module.visual.EffectsModule;
import moon.visual.render.PostProcessBackend.Framebuffer;

/** Recovered fA render/control flow for both captured guard values.
 * API and FBO ownership remain with ManagedPostProcessor. Constants use
 * POSTPROCESS_CONSTANTS finite candidate policy, not runtime confirmation.
 * Original constructor/global-marker/infinite-worker lifecycle remains
 * an explicit migration boundary; no worker is started here.
 */
public final class CachedShadowPass implements EffectsModule.ShadowPipeline {
    private static final int[] DIVISORS = {2,4,8,16,32,64,128,256,512,1024,2084,4096};
    private final Framebuffer input, cachedOutput;
    private final Framebuffer[] pyramid;
    private final PostProcessBackend backend;
    private final BooleanSupplier guardIsNull;
    private final LongSupplier clock;
    private final KawaseStageExecutor stages;
    private int iterations, offset, updatesInInterval, updatesLastInterval;
    private float alpha = 1.0F;
    private long intervalStart;
    private boolean dirty;
    private Consumer<Boolean> maskCallback;

    public CachedShadowPass(Framebuffer input, Framebuffer[] pyramid, Framebuffer cachedOutput,
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
        boolean normal = guardIsNull.getAsBoolean(); // fA render8, captured once.
        boolean visible = backend.isWindowVisible();
        if (!normal) {
            renderNonnull(visible ? 1 : 0);
            return;
        }
        if (!visible) return;
        if (cachedOutput.resizeIfChanged(backend.displayWidth(), backend.displayHeight())) {
            cachedOutput.setClearColor(0,0,0,0);
            requestUpdate();
        }
        if (input.resizeIfChanged(backend.displayWidth(), backend.displayHeight())) {
            input.setClearColor(0,0,0,0);
            requestUpdate();
        }
        if (clock.getAsLong() - intervalStart >= 1000L) {
            updatesLastInterval = updatesInInterval;
            updatesInInterval = 0;
            intervalStart = clock.getAsLong();
        }
        backend.enableBlend();
        if (dirty) {
            updatesInInterval++;
            for (int i = 0; i < pyramid.length; i++) {
                Framebuffer level = pyramid[i];
                if (level.resizeIfChanged(backend.displayWidth() / DIVISORS[i],
                        backend.displayHeight() / DIVISORS[i])) {
                    level.bindTexture();
                    level.setClearColor(0,0,0,0);
                    level.setFilter(9729);
                    level.setWrap(33648);
                }
                level.clearAndUnbind();
            }
            input.clearAndUnbind();
            input.bind(true);
            if (maskCallback != null) maskCallback.accept(true);
            else backend.publishMaskEvent(true);
            backend.blendFunc(1,0);
            for (int i = 0; i < iterations; i++) {
                int texture = i == 0 ? input.texture() : pyramid[i].texture();
                stages.down(true, texture, pyramid[i + 1], (float) offset);
            }
            for (int i = iterations; i > 0; i--) {
                stages.up(true, pyramid[i].texture(), i == 1 ? cachedOutput : pyramid[i - 1],
                        input, cachedOutput, (float) offset, alpha);
            }
            dirty = false;
        }
        composite();
    }

    private void renderNonnull(int firstLevel) {
        // 16->49->104->152->200->232: visibility becomes initial pyramid index.
        // No resize, clock, dirty condition or metric increment on this path.
        if (firstLevel < pyramid.length) {
            int unusedDivisor = DIVISORS[firstLevel]; // Original244..250 reads before level.
            Framebuffer level = pyramid[firstLevel];
            level.clearAndUnbind(); // 264->368; no resize/filter/wrap, one level only.
        }
        input.clearAndUnbind();
        input.bind(true);
        // Nonnull branch398->422 bypasses the callback null check and fallback.
        // A null/throwing callback leaves partial clears and preserves dirty.
        maskCallback.accept(true);
        backend.publishMaskEvent(true); // 432 falls through435 even with callback.
        backend.blendFunc(1,0);
        // A positive down-loop count jumps480->631 with index0 as false.
        // A nonpositive count reaches629->631; neither path executes a stage.
        dirty = false;
        composite();
    }

    private void composite() {
        double width = backend.scaledWidth(), height = backend.scaledHeight();
        backend.enableBlend();
        backend.enableAlpha();
        backend.enableTexture2D();
        backend.alphaFunc(516,0.0F);
        backend.blendFuncSeparate(770,771,1,0);
        cachedOutput.bindTexture();
        backend.mainFramebuffer().bind(true);
        backend.drawGlowCompositeQuad(7,width,height);
        backend.bindTexture(0);
        backend.alphaFunc(516,0.1F);
    }

    @Override public void requestUpdate() { dirty = true; }
    @Override public CachedShadowPass setIterations(int value) { iterations = value; return this; }
    @Override public CachedShadowPass setOffset(int value) { offset = value; return this; }
    @Override public CachedShadowPass setAlpha(float value) { alpha = Math.max(0.0F, Math.min(1.0F, value)); return this; }
    public CachedShadowPass setMaskCallback(Consumer<Boolean> value) { maskCallback = value; return this; }
    public Consumer<Boolean> getMaskCallback() { return maskCallback; }
    public int getUpdatesLastInterval() { return updatesLastInterval; }
    public boolean isDirty() { return dirty; }
}
