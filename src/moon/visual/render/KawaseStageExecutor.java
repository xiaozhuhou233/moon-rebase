package moon.visual.render;

import java.util.function.BooleanSupplier;
import moon.visual.render.PostProcessBackend.Framebuffer;
import moon.visual.render.PostProcessBackend.Kind;
import moon.visual.render.PostProcessBackend.Stage;

/** Recovered fA/fz down/up, including captured nonnull guard paths.
 * fA down acquires the stage before the guard; all other stages read guard first.
 * Numeric constants retain the explicit POSTPROCESS_CONSTANTS candidate policy. */
public final class KawaseStageExecutor {
    public static final int TEXTURE0 = 33984;
    public static final int SOURCE_UNIT = 21, STENCIL_UNIT = 22;
    private final PostProcessBackend backend;
    private final BooleanSupplier originalGuardIsNull;

    public KawaseStageExecutor(PostProcessBackend backend, BooleanSupplier originalGuardIsNull) {
        this.backend = backend;
        this.originalGuardIsNull = originalGuardIsNull;
    }

    public void down(boolean shadow, int sourceTexture, Framebuffer target, float offset) {
        Stage stage;
        boolean normal;
        if (shadow) {
            stage = backend.acquireStage(Kind.SHADOW_DOWN);
            normal = originalGuardIsNull.getAsBoolean();
        } else {
            normal = originalGuardIsNull.getAsBoolean();
            stage = backend.acquireStage(Kind.KAWASE_DOWN);
        }
        boolean layout = stage.hasExpectedUniformLayout();
        if (!normal) {
            backend.bindTexture(layout ? 1 : 0);
            return;
        }
        if (layout) {
            stage.useProgram();
            target.bind(true);
            if (shadow) {
                backend.clearColor(0, 0, 0, 0);
                target.clearBound();
            }
            stage.setTextureSampler(SOURCE_UNIT);
            backend.activeTexture(TEXTURE0 + SOURCE_UNIT);
            backend.bindTexture(sourceTexture);
            stage.setTexelSize(1.0F / target.width(), 1.0F / target.height());
            stage.setOffset(offset, offset);
            stage.setAlpha(1.0F);
        }
        stage.draw();
        backend.activeTexture(TEXTURE0 + SOURCE_UNIT);
        backend.bindTexture(0);
        backend.activeTexture(TEXTURE0);
        backend.bindTexture(0);
    }

    public void up(boolean shadow, int sourceTexture, Framebuffer target,
                   Framebuffer mask, Framebuffer cachedOutput, float offset) {
        up(shadow, sourceTexture, target, mask, cachedOutput, offset, 1.0F);
    }

    public void up(boolean shadow, int sourceTexture, Framebuffer target,
                   Framebuffer mask, Framebuffer cachedOutput, float offset, float alpha) {
        boolean normal = originalGuardIsNull.getAsBoolean();
        Stage stage = backend.acquireStage(shadow ? Kind.SHADOW_UP : Kind.KAWASE_UP);
        boolean layout = stage.hasExpectedUniformLayout();
        if (!normal) {
            backend.bindTexture(layout ? 1 : 0);
            return;
        }
        if (layout) {
            stage.useProgram();
            target.bind(true);
            if (shadow) {
                backend.clearColor(0, 0, 0, 0);
                target.clearBound();
            }
            stage.setStencilSampler(STENCIL_UNIT);
            backend.activeTexture(TEXTURE0 + STENCIL_UNIT);
            backend.bindTexture(mask.texture());
            stage.setTextureSampler(SOURCE_UNIT);
            backend.activeTexture(TEXTURE0 + SOURCE_UNIT);
            backend.bindTexture(sourceTexture);
            stage.setTexelSize(1.0F / target.width(), 1.0F / target.height());
            stage.setOffset(offset, offset);
            stage.setAlpha(shadow && target == cachedOutput ? alpha : 1.0F);
            stage.setUseStencil(target == cachedOutput);
        }
        stage.draw();
        backend.activeTexture(TEXTURE0 + STENCIL_UNIT);
        backend.bindTexture(0);
        backend.activeTexture(TEXTURE0 + SOURCE_UNIT);
        backend.bindTexture(0);
        backend.activeTexture(TEXTURE0);
        backend.bindTexture(0);
    }

}
