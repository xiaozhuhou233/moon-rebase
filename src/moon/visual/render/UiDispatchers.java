package moon.visual.render;
import java.util.function.IntSupplier;
import moon.visual.shader.*;
/** hj/hh/hk/hi/hn/hl/hm/g7 dispatchers. Original shared sentinel state is kept per original class. */
public final class UiDispatchers {
    private UiDispatchers() {
    }
    private static int roundedSentinel;
    private static String textureSentinel,msdfSentinel;
    public static int getRoundedSentinel() {
        return roundedSentinel;
    }
    public static void setRoundedSentinel(int v) {
        roundedSentinel=v;
    }
    public static int roundedInverse() {
        return roundedSentinel==0?43:0;
    }
    public static String getTextureSentinel() {
        return textureSentinel;
    }
    public static void setTextureSentinel(String v) {
        textureSentinel=v;
    }
    public static String getMsdfSentinel() {
        return msdfSentinel;
    }
    public static void setMsdfSentinel(String v) {
        msdfSentinel=v;
    }
    private abstract static class Base extends ShaderDispatcher {
        private final ShaderDescriptor descriptor;
        Base(DrawServices s,Class<?> allowed,String attributes,String shader) {
            super(s);
            descriptor=ShaderDescriptor.builder(new ShaderDescriptorBuilder.MarkerHooks() {
                public int[] getDispatcherSentinel() {
                    return ShaderDispatcher.getSentinel();
                }
                public Object getMarker() {
                    return s.markers.getMarker();
                }
                public void setDispatcherSentinel(int[] v) {
                    ShaderDispatcher.setSentinel(v);
                }
            }
            ).allowed(allowed).attributes(attributes).vertex(shader).fragment(shader).build();
        }
        public ShaderDescriptor descriptor() {
            return descriptor;
        }
        protected void setupUniforms() {
        }
        protected void beforeDraw() {
        }
        final void textureUnit() {
            int location=services.backend.getUniformLocation(program.getProgram(),"tex");
            services.backend.uniform1i(location,0);
        }
    }
    public static final class Rectangle extends Base {
        public Rectangle(DrawServices s) {
            super(s,UiGeometry.Rectangle.class,"rectangle","rectangle");
        }
    }
    public static final class Gradient extends Base {
        public Gradient(DrawServices s) {
            super(s,UiGeometry.GradientRectangle.class,"gradient","gradient");
        }
    }
    public static final class RoundedRectangle extends Base {
        private final ShaderUniform smoothness;
        private final IntSupplier scale;
        public RoundedRectangle(DrawServices s,IntSupplier scale) {
            super(s,UiGeometry.RoundedRectangle.class,"rounded_rectangle","rounded_rectangle");
            this.scale=scale;
            smoothness=s.uniform("smoothness",()->program);
        }
        protected void setupUniforms() {
            smoothness.initializeLocation();
        }
        protected void beforeDraw() {
            int captured=getRoundedSentinel();
            smoothness.setFloat(.7F/scale.getAsInt());
            if(services.markers.getMarker()!=null)setRoundedSentinel(captured+1);
        }
        public ShaderUniform getSmoothness() {
            return smoothness;
        }
    }
    public static final class OutlineRectangle extends Base {
        private final ShaderUniform smoothness;
        private final IntSupplier scale;
        public OutlineRectangle(DrawServices s,IntSupplier scale) {
            super(s,UiGeometry.OutlineRectangle.class,"outline_rounded_rectangle","outline_rounded_rectangle");
            this.scale=scale;
            smoothness=s.uniform("smoothness",()->program);
        }
        protected void setupUniforms() {
            smoothness.initializeLocation();
        }
        protected void beforeDraw() {
            int captured=roundedInverse();
            smoothness.setFloat(.7F/scale.getAsInt());
            if(captured==0)services.markers.setMarker("VHPjcc");
        }
        public ShaderUniform getSmoothness() {
            return smoothness;
        }
    }
    public static final class Texture extends Base {
        public Texture(DrawServices s) {
            super(s,UiGeometry.ColoredTextureQuad.class,"texture","texture");
        }
        protected void setupUniforms() {
            int location=services.backend.getUniformLocation(program.getProgram(),"tex");
            getTextureSentinel();
            services.backend.uniform1i(location,0);
            if(services.markers.getMarker()!=null)setTextureSentinel("gyU07");
        }
    }
    public static final class ColoredTexture extends Base {
        public ColoredTexture(DrawServices s) {
            super(s,UiGeometry.ColoredTextureQuad.class,"texture","texture_col");
        }
        protected void setupUniforms() {
            textureUnit();
        }
    }
    public static final class RoundedTexture extends Base {
        private final ShaderUniform smoothness;
        private final IntSupplier scale;
        public RoundedTexture(DrawServices s,IntSupplier scale) {
            super(s,UiGeometry.RoundedTextureQuad.class,"rounded_texture","rounded_texture");
            this.scale=scale;
            smoothness=s.uniform("smoothness",()->program);
        }
        protected void setupUniforms() {
            smoothness.initializeLocation();
            String captured=getTextureSentinel();
            textureUnit();
            if(captured!=null)services.markers.setMarker("aYhZh");
        }
        protected void beforeDraw() {
            smoothness.setFloat(.7F/scale.getAsInt());
        }
        public ShaderUniform getSmoothness() {
            return smoothness;
        }
    }
    public static final class Msdf extends Base {
        public Msdf(DrawServices s) {
            super(s,UiGeometry.MsdfGlyph.class,"msdf","msdf");
        }
        protected void setupUniforms() {
            int tex=services.backend.getUniformLocation(program.getProgram(),"tex"),weight=services.backend.getUniformLocation(program.getProgram(),"fontWeight"),
            size=services.backend.getUniformLocation(program.getProgram(),"textureSize"),smoothness=services.backend.getUniformLocation(program.getProgram(),"smoothness");
            String captured=getMsdfSentinel();
            services.backend.uniform1i(tex,0);
            services.backend.uniform1f(smoothness,.5F);
            services.backend.uniform1f(weight,1);
            services.backend.uniform2f(size,2964,2964);
            if(captured!=null)services.markers.setMarker("k4zZsb");
        }
    }
}
