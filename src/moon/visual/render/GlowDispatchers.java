package moon.visual.render;

import moon.visual.shader.ShaderDescriptor;
import moon.visual.shader.ShaderDescriptorBuilder;
import moon.visual.shader.ShaderUniform;

/** he/hf/hg. Original shader names, uniform order, quad and marker effects. */
public final class GlowDispatchers {
    private GlowDispatchers() { }
    private static String[] floodSentinel = new String[2];
    public static String[] getFloodSentinel() { return floodSentinel; }
    public static void setFloodSentinel(String[] value) { floodSentinel = value; }

    private static ShaderDescriptor descriptor(DrawServices services, String name) {
        return ShaderDescriptor.builder(new ShaderDescriptorBuilder.MarkerHooks() {
            public int[] getDispatcherSentinel() { return ShaderDispatcher.getSentinel(); }
            public Object getMarker() { return services.markers.getMarker(); }
            public void setDispatcherSentinel(int[] value) { ShaderDispatcher.setSentinel(value); }
        }).allowed(UiGeometry.TextureQuad.class).attributes(name).vertex(name).fragment(name).build();
    }

    /** he: samples accepts the original 101 entries without resampling. */
    public static final class Outline extends ShaderDispatcher {
        public final ShaderUniform texture, stencilTexture, samples, texelSize, direction, radius;
        public final ShaderUniform brightness, contrast, saturation;
        private final ShaderDescriptor descriptor;
        public Outline(DrawServices services) {
            super(services);
            texture = services.uniform("tex", () -> program);
            stencilTexture = services.uniform("stencilTex", () -> program);
            samples = services.uniform("bezierSamples", () -> program);
            texelSize = services.uniform("outline.texelSize", () -> program);
            direction = services.uniform("outline.direction", () -> program);
            radius = services.uniform("outline.radius", () -> program);
            brightness = services.uniform("brightness", () -> program);
            contrast = services.uniform("contrast", () -> program);
            saturation = services.uniform("saturation", () -> program);
            descriptor = GlowDispatchers.descriptor(services, "entity_outline");
        }
        public ShaderDescriptor descriptor() { return descriptor; }
        protected void setupUniforms() {
            texture.initializeLocation(); stencilTexture.initializeLocation(); samples.initializeLocation();
            texelSize.initializeLocation(); direction.initializeLocation(); radius.initializeLocation();
            brightness.initializeLocation(); contrast.initializeLocation(); saturation.initializeLocation();
        }
        protected void beforeDraw() { }
        public void draw(DrawTaskState state) {
            NativeVertexWriter writer = state.getWriter();
            writer.append(1, 0).append(1, 1); getFloodSentinel();
            writer.append(0, 0).append(0, 1);
            writer.append(0, 1).append(0, 0); writer.append(1, 1).append(1, 0);
            state.setVertexCount(4); super.draw(state);
            if (services.markers.getMarker() != null) setFloodSentinel(new String[2]);
        }
    }

    /** hf. fK's original instanceof hg check does not match this dispatcher. */
    public static final class Base extends ShaderDispatcher {
        public final ShaderUniform texture;
        private final ShaderDescriptor descriptor;
        public Base(DrawServices services) {
            super(services); descriptor = GlowDispatchers.descriptor(services, "flood_base");
            texture = services.uniform("tex", () -> program);
        }
        public ShaderDescriptor descriptor() { return descriptor; }
        protected void setupUniforms() { texture.initializeLocation(); }
        protected void beforeDraw() { }
        public void draw(DrawTaskState state) {
            NativeVertexWriter writer = state.getWriter();
            writer.append(1, 0).append(1, 1); String[] captured = getFloodSentinel();
            writer.append(0, 0).append(0, 1);
            writer.append(0, 1).append(0, 0); writer.append(1, 1).append(1, 0);
            state.setVertexCount(4); super.draw(state);
            if (captured == null) services.markers.setMarker("aZoYnb");
        }
    }

    /** hg. Keep inactive uniforms and original initialization order. */
    public static final class Flood extends ShaderDispatcher {
        public final ShaderUniform texture, magicTexture, jumpDistance, flag, texelSize;
        private final ShaderDescriptor descriptor;
        public Flood(DrawServices services) {
            super(services); descriptor = GlowDispatchers.descriptor(services, "flood");
            texture = services.uniform("tex", () -> program);
            magicTexture = services.uniform("magicTex", () -> program);
            jumpDistance = services.uniform("u_jumpDistance", () -> program);
            flag = services.uniform("fuck", () -> program);
            texelSize = services.uniform("texelSize", () -> program);
        }
        public ShaderDescriptor descriptor() { return descriptor; }
        protected void setupUniforms() {
            texture.initializeLocation(); jumpDistance.initializeLocation(); flag.initializeLocation();
            magicTexture.initializeLocation(); texelSize.initializeLocation();
        }
        protected void beforeDraw() { }
        public void draw(DrawTaskState state) {
            NativeVertexWriter writer = state.getWriter();
            writer.append(1, 0).append(1, 1); writer.append(0, 0).append(0, 1);
            writer.append(0, 1).append(0, 0); writer.append(1, 1).append(1, 0);
            state.setVertexCount(4); super.draw(state);
        }
    }
}
