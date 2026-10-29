package moon.visual.render;
import moon.visual.shader.ShaderProgram;
/** h7. setProgram maps the existing void host interface; shader() preserves fluent use. */
public abstract class DrawCommand implements DrawTaskState.Command {
    public ShaderProgram program;
    private static boolean sentinel=true;
    public void setProgram(ShaderProgram value) {
        program=value;
    }
    public DrawCommand shader(ShaderProgram value) {
        setProgram(value);
        return this;
    }
    public static boolean getSentinel() {
        return sentinel;
    }
    public static void setSentinel(boolean value) {
        sentinel=value;
    }
    public static boolean inverse() {
        return !getSentinel();
    }
}
