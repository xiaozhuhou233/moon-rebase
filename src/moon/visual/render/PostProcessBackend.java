package moon.visual.render;

/** Named boundary for the exact moom.pz / kY / gv calls used by fA and fz. */
public interface PostProcessBackend {
    interface Framebuffer {
        int width();
        int height();
        int texture();
        /** Original pz.a(II)Z: rejects either dimension <=1; no clamp. */
        boolean resizeIfChanged(int width, int height);
        void setClearColor(float red, float green, float blue, float alpha);
        void setFilter(int filter);
        void setWrap(int wrap);
        void bind(boolean updateViewport);
        void bindTexture();
        /** Original pz.f(): bind with viewport, clear, then unbind FBO. */
        void clearAndUnbind();
        /** Original pz.g(): clear current FBO; does not bind or unbind. */
        void clearBound();
    }
    interface Stage {
        /** Mirrors original instanceof ha/hb/hc/hd guard. */
        boolean hasExpectedUniformLayout();
        /** Original h_.a(): glUseProgram, with no projection update here. */
        void useProgram();
        void setTextureSampler(int unit);
        void setStencilSampler(int unit);
        void setTexelSize(float x, float y);
        void setOffset(float x, float y);
        void setAlpha(float alpha);
        void setUseStencil(boolean enabled);
        /** Original gv.b() runs even if uniform-layout check fails. */
        void draw();
    }
    enum Kind { KAWASE_DOWN, KAWASE_UP, SHADOW_DOWN, SHADOW_UP }

    boolean isWindowVisible();
    int displayWidth();
    int displayHeight();
    double scaledWidth();
    double scaledHeight();
    Framebuffer mainFramebuffer();
    Stage acquireStage(Kind kind);
    void publishMaskEvent(boolean shadow);
    void enableBlend();
    /** Original kY.d(): k0 alpha state enable, NOT a lighting operation. */
    void enableAlpha();
    void enableTexture2D();
    void disableTexture2D();
    void alphaFunc(int function, float reference);
    void blendFunc(int source, int destination);
    void blendFuncSeparate(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha);
    void activeTexture(int glTextureEnum);
    void bindTexture(int texture);
    void clearColor(float red, float green, float blue, float alpha);
    /** Four vertex quads, original order: (0,h,0,0),(w,h,1,0),(w,0,1,1),(0,0,0,1). */
    void drawCompositeQuad(int primitive, double scaledWidth, double scaledHeight);
    default void drawGlowCompositeQuad(int primitive,double width,double height){drawCompositeQuad(primitive,width,height);}
}
