package moon.visual.shader;

import java.util.function.Supplier;

/** com.moon.ib. Default location is 0, including before any GL lookup. */
public final class ShaderUniformBuilder {
    private String name;
    private Supplier<ShaderProgram> shader;
    private int location;
    private final ShaderUniform.Backend backend;
    private final ShaderDiagnostics diagnostics;
    private final ShaderUniform.MarkerHooks markers;

    ShaderUniformBuilder(ShaderUniform.Backend backend, ShaderDiagnostics diagnostics, ShaderUniform.MarkerHooks markers) {
        this.backend = backend;
        this.diagnostics = diagnostics;
        this.markers = markers;
    }
    public ShaderUniformBuilder name(String value) { name = value; return this; }
    public ShaderUniformBuilder shader(Supplier<ShaderProgram> value) { shader = value; return this; }
    public ShaderUniformBuilder location(int value) { location = value; return this; }
    public ShaderUniform build() { return new ShaderUniform(name, shader, location, backend, diagnostics, markers); }
    @Override public String toString() {
        return "Uniform.UniformBuilder(name=" + name + ", shader=" + shader + ", location=" + location + ")";
    }
}
