package moon.visual.render;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.function.DoubleSupplier;
import moon.visual.shader.*;

/** Host boundaries for resource lookup, scaled resolution and obfuscation marker calls. */
public final class DrawServices {
    public interface MarkerHooks extends ShaderUniform.MarkerHooks,VertexBuffer.MarkerHooks { }
    public final DrawBackend backend;
    public final ShaderDiagnostics diagnostics;
    public final MarkerHooks markers;
    public final ShaderSourceLoader.ResourceAccess resources;
    public final ShaderAttributeSet.Parser attributeParser;
    public final DoubleSupplier scaledWidth,scaledHeight;
    public DrawServices(DrawBackend backend,ShaderDiagnostics diagnostics,MarkerHooks markers,
            ShaderSourceLoader.ResourceAccess resources,ShaderAttributeSet.Parser attributeParser,
            DoubleSupplier scaledWidth,DoubleSupplier scaledHeight) {
        this.backend=backend;this.diagnostics=diagnostics;this.markers=markers;this.resources=resources;
        this.attributeParser=attributeParser;this.scaledWidth=scaledWidth;this.scaledHeight=scaledHeight;
    }
    public ShaderProgram program(String vertex,String fragment,boolean optional) throws IOException {
        return new ShaderProgram(vertex,fragment,optional,new ShaderSourceLoader(resources,diagnostics),backend,diagnostics);
    }
    public ShaderUniform uniform(String name,java.util.function.Supplier<ShaderProgram> program) {
        return ShaderUniform.builder(backend,diagnostics,markers).name(name).shader(program).build();
    }
    public static FloatBuffer floatBuffer(int elements) {
        return ByteBuffer.allocateDirect(elements*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }
}
