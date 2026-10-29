package moon.visual.shader;

/** com.moon.hq, a descriptor only. Vertex writer/ht semantics remain outside this class. */
public final class ShaderDescriptor {
    private final Class<?> allowed;
    private final String attributes;
    private final String fragment;
    private final String vertex;
    private final int mode;
    private final boolean optionalBuffer;

    ShaderDescriptor(Class<?> allowed, String attributes, String fragment, String vertex, int mode, boolean optionalBuffer) {
        this.allowed = allowed;
        this.attributes = attributes;
        this.fragment = fragment;
        this.vertex = vertex;
        this.mode = mode;
        this.optionalBuffer = optionalBuffer;
    }
    public static ShaderDescriptorBuilder builder(ShaderDescriptorBuilder.MarkerHooks markers) {
        return new ShaderDescriptorBuilder(markers);
    }
    /** Original Class<? extends ht>; erased boundary until ht/g4 are restored. */
    public Class<?> getAllowed() { return allowed; }
    public String getAttributes() { return attributes; }
    public String getFragment() { return fragment; }
    public String getVertex() { return vertex; }
    public int getMode() { return mode; }
    public boolean hasOptionalBuffer() { return optionalBuffer; }
}
