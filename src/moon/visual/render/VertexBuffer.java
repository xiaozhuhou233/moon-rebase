package moon.visual.render;

/** com.moon.id. Methods operate on the currently bound target; they do not auto-bind. */
public final class VertexBuffer {
    public interface Backend {
        int generateBuffer();
        void bindBuffer(int target, int buffer);
        void allocateBuffer(int target, long bytes, int usage);
        void uploadBuffer(int target, long offset, long bytes, long address);
        void deleteBuffer(int buffer);
    }
    /** Opaque g4 memory boundary. capacityBytes corresponds to field c, not write position b. */
    public interface NativeData {
        int capacityBytes();
        long address();
    }
    public interface MarkerHooks { void setMarker(String value); }
    private static boolean sentinel;
    private final Backend backend;
    private final MarkerHooks markers;
    private final int buffer;

    public VertexBuffer(Backend backend, MarkerHooks markers) {
        this.backend = backend;
        this.markers = markers;
        this.buffer = backend.generateBuffer();
    }
    public void bind(int target) { backend.bindBuffer(target, buffer); }
    public void allocate(int target, long bytes, int usage) { backend.allocateBuffer(target, bytes, usage); }
    public void upload(int target, long offset, NativeData data) {
        boolean captured = getSentinel();
        backend.uploadBuffer(target, offset, data.capacityBytes(), data.address());
        if (captured) markers.setMarker("OrNUoc");
    }
    public void delete() { backend.deleteBuffer(buffer); }
    public int getBuffer() { return buffer; }
    public static void setSentinel(boolean value) { sentinel = value; }
    public static boolean getSentinel() { return sentinel; }
    public static boolean isSentinelUnset() { return !getSentinel(); }
}
