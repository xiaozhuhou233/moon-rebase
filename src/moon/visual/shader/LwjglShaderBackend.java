package moon.visual.shader;

import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** LWJGL3 adapter. All calls require a current context on the calling thread. */
public class LwjglShaderBackend implements ShaderProgram.Backend, ShaderUniform.Backend {
    private final boolean convertLegacyTextureCalls;

    /** Faithful source upload; no shader source rewriting. */
    public LwjglShaderBackend() { this(false); }
    /** Explicit migration option, also used by the independent GL3.3 verifier. */
    public LwjglShaderBackend(boolean convertLegacyTextureCalls) {
        this.convertLegacyTextureCalls = convertLegacyTextureCalls;
    }
    @Override public int createShader(int type) { return GL20.glCreateShader(type); }
    @Override public void shaderSource(int shader, String source) {
        GL20.glShaderSource(shader, convertLegacyTextureCalls && source != null
                ? source.replaceAll("\\btexture2D\\s*\\(", "texture(") : source);
    }
    @Override public void compileShader(int shader) { GL20.glCompileShader(shader); }
    @Override public int getShaderParameter(int shader, int parameter) { return GL20.glGetShaderi(shader, parameter); }
    @Override public String getShaderInfoLog(int shader, int maxLength) { return GL20.glGetShaderInfoLog(shader, maxLength); }
    @Override public int createProgram() { return GL20.glCreateProgram(); }
    @Override public void attachShader(int program, int shader) { GL20.glAttachShader(program, shader); }
    @Override public void bindFragmentOutput(int program, int location, String name) { GL30.glBindFragDataLocation(program, location, name); }
    @Override public void linkProgram(int program) { GL20.glLinkProgram(program); }
    @Override public void useProgram(int program) { GL20.glUseProgram(program); }
    @Override public int getUniformLocation(int program, String name) { return GL20.glGetUniformLocation(program, name); }
    @Override public int getError() { return GL11.glGetError(); }
    @Override public void uniformMatrix4(int location, boolean transpose, FloatBuffer values) { GL20.glUniformMatrix4fv(location, transpose, values); }
    @Override public void uniform1fv(int location, float[] values) { GL20.glUniform1fv(location, values); }
    @Override public void uniform4f(int location, float x, float y, float z, float w) { GL20.glUniform4f(location, x, y, z, w); }
    @Override public void uniform2f(int location, float x, float y) { GL20.glUniform2f(location, x, y); }
    @Override public void uniform1f(int location, float value) { GL20.glUniform1f(location, value); }
    @Override public void uniform1i(int location, int value) { GL20.glUniform1i(location, value); }
}
