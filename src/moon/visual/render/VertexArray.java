package moon.visual.render;

/** com.moon.ic. Construction allocates immediately; delete is neither guarded nor automatic. */
public final class VertexArray {
    public interface Backend {
        int generateVertexArray();
        void bindVertexArray(int array);
        void deleteVertexArray(int array);
    }
    private final Backend backend;
    private final int array;
    public VertexArray(Backend backend) { this.backend = backend; this.array = backend.generateVertexArray(); }
    public void bind() { backend.bindVertexArray(array); }
    public void delete() { backend.deleteVertexArray(array); }
}
