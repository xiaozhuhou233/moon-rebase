package moon.visual.render;

/** com.moon.g4. Native address, byte write position and byte capacity are distinct. */
public final class NativeVertexWriter implements VertexBuffer.NativeData {
    public interface Memory {
        long allocateMemory(long bytes);
        long reallocateMemory(long address, long bytes);
        void freeMemory(long address);
        void putFloat(long address, float value);
    }
    private static String sentinel;
    private final Memory memory;
    private final VertexBuffer.MarkerHooks markers;
    private long address;
    private int writtenBytes, capacityBytes;

    public NativeVertexWriter(int initialCapacity, Memory memory, VertexBuffer.MarkerHooks markers) {
        this.memory=memory; this.markers=markers;
        this.address=memory.allocateMemory(initialCapacity); this.capacityBytes=initialCapacity;
    }
    private void grow() {
        int size=(int)(capacityBytes*1.5F);
        long replacement=memory.reallocateMemory(address,size);
        capacityBytes=size; address=replacement;
    }
    public void reserveExtra(int bytes) {
        int size=capacityBytes+bytes;
        long replacement=memory.reallocateMemory(address,size);
        capacityBytes=size; address=replacement;
    }
    /** Original free leaves fields intact; no automatic ownership or repeated-free guard. */
    public void free() { memory.freeMemory(address); }
    public NativeVertexWriter append(float value) {
        String captured=getSentinel();
        while(capacityBytes-writtenBytes<4) {
            if(captured!=null)return this;
            grow();
        }
        memory.putFloat(address+writtenBytes,value); writtenBytes+=4; return this;
    }
    public NativeVertexWriter append(float x,float y) { return append(x).append(y); }
    public NativeVertexWriter append(float x,float y,float z) { return append(x).append(y).append(z); }
    public NativeVertexWriter append(double x,double y,double z) { return append((float)x).append((float)y).append((float)z); }
    @Deprecated public NativeVertexWriter appendNormalized(float x,float y,float width,float height) { return append(x/width).append(y/height); }
    public NativeVertexWriter appendColor(int argb) {
        float alpha=RenderTransformState.getAlpha(); String captured=getSentinel();
        float r=(argb>>16&255)/255.0F, g=(argb>>8&255)/255.0F, b=(argb&255)/255.0F;
        float a=(argb>>24&255)/255.0F*alpha;
        append(r).append(g).append(b).append(a);
        if(captured!=null)markers.setMarker("ej1ZDc");
        return this;
    }
    public NativeVertexWriter append(float x,float y,float z,float w) { return append(x).append(y).append(z).append(w); }
    @Override public long address() { return address; }
    public int writtenBytes() { return writtenBytes; }
    @Override public int capacityBytes() { return capacityBytes; }
    public NativeVertexWriter setAddress(long value) { address=value; return this; }
    public NativeVertexWriter setWrittenBytes(int value) { writtenBytes=value; return this; }
    public NativeVertexWriter setCapacityBytes(int value) { capacityBytes=value; return this; }
    public static void setSentinel(String value) { sentinel=value; }
    public static String getSentinel() { return sentinel; }
}
