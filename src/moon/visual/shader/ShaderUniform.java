package moon.visual.shader;

import java.nio.FloatBuffer;
import java.util.function.Supplier;

/** com.moon.ia; uploads affect the currently bound program, with no implicit use(). */
public final class ShaderUniform {
    public interface Backend {
        int getUniformLocation(int program, String name);
        int getError();
        void uniformMatrix4(int location, boolean transpose, FloatBuffer values);
        void uniform1fv(int location, float[] values);
        void uniform4f(int location, float x, float y, float z, float w);
        void uniform2f(int location, float x, float y);
        void uniform1f(int location, float value);
        void uniform1i(int location, int value);
    }

    /** Original external moom.r2 calls, supplied by the host instead of recreating it. */
    public interface MarkerHooks {
        Object getMarker();
        void setMarker(String value);
    }

    // Original r2[] element type is opaque here; nullness, identity and length are retained.
    private static Object[] sentinel = new Object[2];
    private final String name;
    private final Supplier<ShaderProgram> shader;
    private int location;
    private final Backend backend;
    private final ShaderDiagnostics diagnostics;
    private final MarkerHooks markers;

    public ShaderUniform(String name, Supplier<ShaderProgram> shader, int location,
                         Backend backend, ShaderDiagnostics diagnostics, MarkerHooks markers) {
        this.name = name;
        this.shader = shader;
        this.location = location;
        this.backend = backend;
        this.diagnostics = diagnostics;
        this.markers = markers;
    }

    public void initializeLocation() {
        if (shader.get() == null) {
            diagnostics.error("Failed to link uniform location, Shader is null! (Did the shader compile successfully?)");
            return;
        }
        // Deliberately calls supplier twice; do not cache the first result.
        location = backend.getUniformLocation(shader.get().getProgram(), name);
        int error = backend.getError();
        if (error == 0) {
            diagnostics.info("Successfully linked uniform location with name: {}", name);
        } else if (error == 1281) {
            diagnostics.error("Failed to link uniform location as the associated shader program is not known by OpenGL");
        } else if (error == 1282) {
            diagnostics.error("Failed to link uniform location as the associated shader program does not contain this uniform or the program did not compile successfully!");
        }
    }

    public void setMatrix4(boolean transpose, FloatBuffer values) { backend.uniformMatrix4(location, transpose, values); }
    public void setFloatArray(float[] values) { backend.uniform1fv(location, values); }
    public void setVector4(float x, float y, float z, float w) {
        getSentinel();
        backend.uniform4f(location, x, y, z, w);
        if (markers.getMarker() != null) setSentinel(new Object[4]);
    }
    public void setVector2(float x, float y) { backend.uniform2f(location, x, y); }
    public void setFloat(float value) { backend.uniform1f(location, value); }
    public void setInt(int value) { backend.uniform1i(location, value); }
    public void setBoolean(boolean value) {
        Object[] captured = getSentinel();
        backend.uniform1i(location, value ? 1 : 0);
        if (captured == null) markers.setMarker("rjghL");
    }

    public String getName() { return name; }
    public Supplier<ShaderProgram> getShader() { return shader; }
    public int getLocation() { return location; }
    public ShaderUniform setLocation(int location) { this.location = location; return this; }
    public static void setSentinel(Object[] value) { sentinel = value; }
    public static Object[] getSentinel() { return sentinel; }
    public static ShaderUniformBuilder builder(Backend backend, ShaderDiagnostics diagnostics, MarkerHooks markers) {
        return new ShaderUniformBuilder(backend, diagnostics, markers);
    }
}
