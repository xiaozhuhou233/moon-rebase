package moon.visual.shader;

import java.io.IOException;

/** com.moon.h_: bytecode-reviewed construction, compilation and use lifecycle. */
public final class ShaderProgram {
    public interface Backend {
        int createShader(int type);
        void shaderSource(int shader, String source);
        void compileShader(int shader);
        int getShaderParameter(int shader, int parameter);
        String getShaderInfoLog(int shader, int maxLength);
        int createProgram();
        void attachShader(int program, int shader);
        void bindFragmentOutput(int program, int location, String name);
        void linkProgram(int program);
        void useProgram(int program);
    }

    private final Backend backend;
    private final ShaderDiagnostics diagnostics;
    private int program;

    public ShaderProgram(String vertexPath, String fragmentPath, boolean optionalBuffer,
                         ShaderSourceLoader sources, Backend backend,
                         ShaderDiagnostics diagnostics) throws IOException {
        this.backend = backend;
        this.diagnostics = diagnostics;
        int vertex = compile(35633, sources.readResource(vertexPath));
        int fragment = compile(35632, sources.readResource(fragmentPath));
        // Both stages are attempted, even when vertex compilation returned -1.
        if (vertex == -1 || fragment == -1) return;
        program = backend.createProgram();
        backend.attachShader(program, vertex);
        backend.attachShader(program, fragment);
        backend.bindFragmentOutput(program, 0, "fragColor");
        if (optionalBuffer) {
            diagnostics.info("Enabling optional / secondary color for " + vertexPath + " and " + fragmentPath);
            backend.bindFragmentOutput(program, 1, "optionalColor");
        }
        backend.linkProgram(program);
        // Original logs success without querying link status. Ownership/cleanup is external.
        diagnostics.info("Successfully linked shaders with vertex path: {} and fragment path: {}",
                         vertexPath, fragmentPath);
    }

    private int compile(int type, String source) {
        int shader = backend.createShader(type);
        backend.shaderSource(shader, source);
        backend.compileShader(shader);
        if (backend.getShaderParameter(shader, 35713) == 1) return shader;
        String log = backend.getShaderInfoLog(shader, 2048);
        diagnostics.error("Failed to compile shader: {}", log);
        return -1;
    }

    public void use() { backend.useProgram(program); }
    public int getProgram() { return program; }
}
