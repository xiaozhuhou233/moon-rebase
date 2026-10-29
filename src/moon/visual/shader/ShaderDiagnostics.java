package moon.visual.shader;

/** Logging boundary; templates and argument order follow the original engine. */
public interface ShaderDiagnostics {
    void info(String template, Object... arguments);
    void error(String template, Object... arguments);
}
