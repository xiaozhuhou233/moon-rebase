package moon.visual.render;

import org.lwjgl.opengl.*;

/** Owned g3 migration FBO. Allows original 1x1 construction and retains RGBA16
 * seed precision. Resize explicitly deletes old handles instead of g3's leak.
 * StateAccess must use the destination client's GL state cache when attached.
 */
public final class LwjglSeedFramebuffer extends SeedFramebufferLifecycle implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private boolean closed;
    public LwjglSeedFramebuffer(int width, int height, boolean depth) {
        this(width, height, depth, new LwjglPostProcessBackend.StateAccess() { });
    }
    public LwjglSeedFramebuffer(int width, int height, boolean depth, LwjglPostProcessBackend.StateAccess state) {
        super(depth, new Gl(state));
        if (width < 1 || height < 1) throw new IllegalArgumentException("FBO dimensions must be positive");
        try { createBindFramebuffer(width, height); }
        catch (RuntimeException | Error failure) { delete(); throw failure; }
    }
    private void requireOpen() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Render thread required");
        if (closed) throw new IllegalStateException("FBO closed");
    }
    @Override public boolean resizeIfChanged(int width, int height) {
        requireOpen();
        if (width <= 1 || height <= 1 || width == this.width && height == this.height) return false;
        delete(); createRaw(width, height); return true;
    }
    @Override public void bind(boolean viewport) { requireOpen(); super.bind(viewport); }
    @Override public void bindTexture() { requireOpen(); super.bindTexture(); }
    @Override public void clearBound() { requireOpen(); super.clearBound(); }
    @Override public void setFilter(int value) { requireOpen(); super.setFilter(value); }
    @Override public void setWrap(int value) { requireOpen(); super.setWrap(value); }
    @Override public void createBindFramebuffer(int width, int height) { requireOpen(); super.createBindFramebuffer(width, height); }
    @Override public void createRaw(int width, int height) { requireOpen(); super.createRaw(width, height); }
    @Override public void delete() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Render thread required");
        super.delete();
    }
    @Override public void close() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Render thread required");
        if (!closed) { delete(); closed = true; }
    }
    private static final class Gl implements Backend {
        private final LwjglPostProcessBackend.StateAccess state;
        Gl(LwjglPostProcessBackend.StateAccess state) { this.state = state; }
        public boolean supported() { return true; }
        public void enableDepth() { state.enableDepth(); }
        public int createFramebuffer() { return GL30.glGenFramebuffers(); }
        public int createTexture() { return GL11.glGenTextures(); }
        public int createRenderbuffer() { return GL30.glGenRenderbuffers(); }
        public void bindFramebuffer(int id) { GL30.glBindFramebuffer(36160, id); }
        public void bindTexture(int id) { state.bindTexture(id); }
        public void bindRenderbuffer(int id) { GL30.glBindRenderbuffer(36161, id); }
        public void textureStorage(int width, int height) { GL11.glTexImage2D(3553, 0, INTERNAL_FORMAT, width, height, 0, 6408, 5121, 0L); }
        public void depthStorage(int width, int height) { GL30.glRenderbufferStorage(36161, 33190, width, height); }
        public void attachTexture(int id) { GL32.glFramebufferTexture(36160, 36064, id, 0); }
        public void attachDepth(int id) { GL30.glFramebufferRenderbuffer(36160, 36096, 36161, id); }
        public void checkComplete() {
            int status = GL30.glCheckFramebufferStatus(36160);
            if (status != 36053) throw new IllegalStateException("Incomplete seed FBO: " + status);
        }
        public void textureParameter(int parameter, int value) { GL11.glTexParameterf(3553, parameter, value); }
        public void viewport(int width, int height) { state.viewport(width, height); }
        public void clearColor(float r, float g, float b, float a) { state.clearColor(r, g, b, a); }
        public void clearDepth(double depth) { state.clearDepth(depth); }
        public void clear(int mask) { state.clear(mask); }
        public void deleteTexture(int id) { state.deleteTexture(id); }
        public void deleteRenderbuffer(int id) { GL30.glDeleteRenderbuffers(id); }
        public void deleteFramebuffer(int id) { GL30.glDeleteFramebuffers(id); }
    }
}
