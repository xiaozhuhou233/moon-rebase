package moon.module.visual;
import moon.module.visual.effects.EffectsSettings;

import java.util.function.BooleanSupplier;
import moon.visual.event.HudOverlayPhaseEvent;

/**
 * PARTIAL reconstruction of com.moon.jM's observable rendering path.
 * Bytecode evidence and field mapping: dossiers/Effects.md.
 * EffectsSettings supplies the original normal constructor's setting tree.
 * Framebuffer allocation, pipeline internals and event registration belong to
 * explicit adapters and are NOT claimed implemented by this controller.
 */
public final class EffectsModule {
    // Decoded from jM's numeric constant table without executing Moon classes.
    public static final int FRAMEBUFFER_COUNT = 6;
    public static final int DEFAULT_ITERATIONS = 3;
    public static final int MIN_ITERATIONS = 1;
    public static final int MAX_ITERATIONS = 5;
    public static final int DEFAULT_OFFSET = 3;
    public static final int MIN_OFFSET = 0;
    public static final int MAX_OFFSET = 10;
    public interface Pipeline {
        void render();
        Pipeline setIterations(int iterations);
        Pipeline setOffset(int offset);
    }
    public interface ShadowPipeline extends Pipeline {
        void requestUpdate();
        default ShadowPipeline setAlpha(float alpha) { return this; }
    }
    public interface RenderHooks {
        void publishScreenEvent(); // original new dl; publish before depth change
        void disableDepth();       // moom.kY.i() -> depth capability .a()
        void enableDepth();        // moom.kY.j() -> depth capability .b()
    }

    private final Pipeline blurPipeline;          // original u : fz
    private final ShadowPipeline shadowPipeline; // original z : fA
    private final BooleanSupplier blurEnabled;   // original v setting value
    private final BooleanSupplier shadowEnabled; // original A setting value
    private final BooleanSupplier originalGuardIsNull; // ba.b() == null
    private final RenderHooks hooks;

    public EffectsModule(Pipeline blurPipeline, ShadowPipeline shadowPipeline,
                         BooleanSupplier blurEnabled, BooleanSupplier shadowEnabled,
                         BooleanSupplier originalGuardIsNull, RenderHooks hooks) {
        this.blurPipeline = blurPipeline;
        this.shadowPipeline = shadowPipeline;
        this.blurEnabled = blurEnabled;
        this.shadowEnabled = shadowEnabled;
        this.originalGuardIsNull = originalGuardIsNull;
        this.hooks = hooks;
    }

    /** Original onRenderWorld(dH): unconditional z.b(); dH is NOT a world draw hook. */
    public void requestShadowUpdate() { shadowPipeline.requestUpdate(); }
    /** jM.b():iW then iW.a():Object, read live by l9's animation scheduling. */
    public boolean isShadowEnabled() { return shadowEnabled.getAsBoolean(); }

    /** Original onAttack(c9): observed POST-overlay effects, HIGH priority. */
    public void onHudOverlay(HudOverlayPhaseEvent event) {
        // In bytecode the guard value is fetched before the phase check.
        boolean guardIsNull = originalGuardIsNull.getAsBoolean();
        if (event.getPhase() != HudOverlayPhaseEvent.Phase.POST) return;
        hooks.publishScreenEvent();
        hooks.disableDepth();
        boolean blurFlag = blurEnabled.getAsBoolean();
        if (guardIsNull && blurFlag) blurPipeline.render();
        // Nonnull ba:61->113 retains the blur boolean as the shadow decision;
        // it bypasses the blur draw and the shadow-setting read at94..106.
        boolean shadowFlag = guardIsNull ? shadowEnabled.getAsBoolean() : blurFlag;
        if (shadowFlag) shadowPipeline.render();
        // No try/finally in original: exceptions skip the final depth enable.
        hooks.enableDepth();
    }

    // Four original lambda$new$N callbacks, old value is intentionally unused.
    public void onBlurIterationsChanged(Integer oldValue, Integer newValue) {
        blurPipeline.setIterations(newValue);
    }
    public void onBlurOffsetChanged(Integer oldValue, Integer newValue) {
        blurPipeline.setOffset(newValue);
    }
    public void onShadowIterationsChanged(Integer oldValue, Integer newValue) {
        shadowPipeline.setIterations(newValue);
    }
    public void onShadowOffsetChanged(Integer oldValue, Integer newValue) {
        shadowPipeline.setOffset(newValue);
    }
}
