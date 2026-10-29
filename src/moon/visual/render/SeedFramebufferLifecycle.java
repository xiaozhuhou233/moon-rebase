package moon.visual.render;

/** g3 normal g4 marker path. Backend must allocate GL_RGBA16 normalized storage.
 * Unlike pz, raw creation clears the currently bound target without setting the
 * viewport, checks completeness, and leaves the framebuffer bound. Original
 * raw resize overwrites old handles; the owned LWJGL adapter fixes that leak.
 */
public class SeedFramebufferLifecycle extends FramebufferLifecycle {
    public static final int INTERNAL_FORMAT = 32859;
    public SeedFramebufferLifecycle(boolean depth, Backend backend) { super(depth, backend); }

    @Override public void createRaw(int width, int height) {
        this.width = width; this.height = height; textureWidth = width; textureHeight = height;
        if (!backend.supported()) { clearBound(); return; }
        framebuffer = backend.createFramebuffer(); texture = backend.createTexture();
        if (depth) renderbuffer = backend.createRenderbuffer();
        setFilter(9728); backend.bindTexture(texture); backend.textureStorage(width, height);
        backend.bindFramebuffer(framebuffer); backend.bindTexture(texture); backend.attachTexture(texture);
        if (depth) {
            backend.bindRenderbuffer(renderbuffer); backend.depthStorage(width, height);
            backend.attachDepth(renderbuffer);
        }
        clearBound(); backend.bindTexture(0); backend.checkComplete();
    }

    /** g3.e ignores any pz-style configurable clear color. Does not bind a target. */
    @Override public void clearBound() {
        backend.clearColor(0, 0, 0, 0);
        int mask = 16384;
        if (depth) { backend.clearDepth(1); mask |= 256; }
        backend.clear(mask);
    }
}
