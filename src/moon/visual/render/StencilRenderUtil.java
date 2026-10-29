package moon.visual.render;

import java.util.function.IntSupplier;

/**
 * com.moon.k5, bytecode-reviewed public behavior with all 20 numeric table slots
 * decoded (STENCIL_CONSTANTS.json). Injected calls isolate MCP and GL caches.
 * Preserves BOTH Y sentinel branches, state write order, and original ownership
 * defects. No implicit FBO binding, cleanup, state snapshot or exception recovery.
 */
public final class StencilRenderUtil {
    public static final int STENCIL_BUFFER_BIT = 1024;
    public static final int STENCIL_TEST = 2960;
    public static final int GEQUAL = 518, LEQUAL = 515, NOTEQUAL = 517;
    public static final int KEEP = 7680, REPLACE = 7681;
    public static final int SRC_ALPHA = 770, ONE_MINUS_SRC_ALPHA = 771;
    public static final int FRAMEBUFFER = 36160, RENDERBUFFER = 36161;
    public static final int DEPTH_STENCIL = 34041;
    public static final int STENCIL_ATTACHMENT = 36128, DEPTH_ATTACHMENT = 36096;

    public interface FramebufferAccess {
        int getDepthBuffer();
        void setDepthBuffer(int handle);
    }
    public interface Backend {
        void enableBlend();
        void blendFunc(int source, int destination);
        FramebufferAccess getMainFramebuffer();
        int getDisplayWidth();
        int getDisplayHeight();
        void clearStencil(int value);
        void clear(int mask);
        void enable(int capability);
        void disable(int capability);
        void stencilFunc(int function, int reference, int mask);
        void stencilOp(int stencilFail, int depthFail, int depthPass);
        void colorMask(boolean red, boolean green, boolean blue, boolean alpha);
        void deleteRenderbuffer(int handle);
        int generateRenderbuffer();
        void bindRenderbuffer(int target, int handle);
        void renderbufferStorage(int target, int format, int width, int height);
        void framebufferRenderbuffer(int target, int attachment, int renderbufferTarget, int handle);
    }

    private final Backend gl;
    private final IntSupplier originalYState; // original Y.b(); default static value 98

    public StencilRenderUtil(Backend gl, IntSupplier originalYState) {
        this.gl = gl;
        this.originalYState = originalYState;
    }

    /** Original a(boolean). Its stencil function is GEQUAL, not ALWAYS. */
    public void beginWrite(boolean redChannelFlag) {
        int state = originalYState.getAsInt();
        ensureMainFramebufferStencil();
        gl.clearStencil(0);
        gl.clear(STENCIL_BUFFER_BIT);
        gl.enable(STENCIL_TEST);
        gl.stencilFunc(GEQUAL, 1, 1);
        gl.stencilOp(KEEP, KEEP, REPLACE);
        if (state != 0 && redChannelFlag) return;
        gl.colorMask(state == 0 && redChannelFlag, false, false, false);
    }

    /** Original b(boolean); Y.c() is 105 when Y.b()==0, otherwise zero. */
    public void beginRead(boolean useLessOrEqual) {
        int derivedState = originalYState.getAsInt() == 0 ? 105 : 0;
        gl.enableBlend();
        int function = derivedState != 0 ? (useLessOrEqual ? 1 : 0)
                : (useLessOrEqual ? LEQUAL : NOTEQUAL);
        gl.stencilFunc(function, 1, 1);
        gl.stencilOp(KEEP, KEEP, KEEP);
        gl.colorMask(true, true, true, true);
    }

    /** Original a(); does not restore color mask or disable blending. */
    public void end() {
        gl.enableBlend();
        gl.blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA);
        gl.disable(STENCIL_TEST);
    }

    /** Original failed method b(), recovered from javap offsets 8..101. */
    public void ensureMainFramebufferStencil() {
        gl.enableBlend();
        int state = originalYState.getAsInt();
        gl.blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA);
        FramebufferAccess framebuffer = gl.getMainFramebuffer();
        if (state != 0) {
            if (framebuffer == null || framebuffer.getDepthBuffer() <= -1) return;
            replaceDepthWithPackedStencil(framebuffer);
        }
        // For the zero sentinel path original skips replacement and dereferences
        // the framebuffer directly, including throwing if it is null.
        framebuffer.setDepthBuffer(-1);
    }

    /** Original a(pz): attaches to CURRENT FBO and loses generated handle. */
    public void replaceDepthWithPackedStencil(FramebufferAccess framebuffer) {
        gl.enableBlend();
        gl.blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA);
        gl.deleteRenderbuffer(framebuffer.getDepthBuffer());
        int handle = gl.generateRenderbuffer();
        gl.bindRenderbuffer(RENDERBUFFER, handle);
        gl.renderbufferStorage(RENDERBUFFER, DEPTH_STENCIL,
                gl.getDisplayWidth(), gl.getDisplayHeight());
        gl.framebufferRenderbuffer(FRAMEBUFFER, STENCIL_ATTACHMENT, RENDERBUFFER, handle);
        gl.framebufferRenderbuffer(FRAMEBUFFER, DEPTH_ATTACHMENT, RENDERBUFFER, handle);
    }
}
