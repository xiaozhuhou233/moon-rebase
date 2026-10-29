package moon.visual.shader;

/** com.moon.hr. Its toString() also has an original external sentinel side effect. */
public final class ShaderDescriptorBuilder {
    public interface MarkerHooks {
        int[] getDispatcherSentinel();
        Object getMarker();
        void setDispatcherSentinel(int[] value);
    }
    private Class<?> allowed;
    private String attributes;
    private String fragment;
    private String vertex;
    private int mode;
    private boolean optionalBuffer;
    private final MarkerHooks markers;

    ShaderDescriptorBuilder(MarkerHooks markers) { this.markers = markers; }
    public ShaderDescriptorBuilder allowed(Class<?> value) { allowed = value; return this; }
    public ShaderDescriptorBuilder attributes(String value) { attributes = value; return this; }
    public ShaderDescriptorBuilder fragment(String value) { fragment = value; return this; }
    public ShaderDescriptorBuilder vertex(String value) { vertex = value; return this; }
    public ShaderDescriptorBuilder mode(int value) { mode = value; return this; }
    public ShaderDescriptorBuilder optionalBuffer(boolean value) { optionalBuffer = value; return this; }
    public ShaderDescriptor build() { return new ShaderDescriptor(allowed, attributes, fragment, vertex, mode, optionalBuffer); }
    @Override public String toString() {
        markers.getDispatcherSentinel();
        String result = "TaskDispatcherConfiguration.TaskDispatcherConfigurationBuilder(allowed=" + allowed
                + ", attributes=" + attributes + ", fragment=" + fragment + ", vertex=" + vertex
                + ", mode=" + mode + ", optionalBuffer=" + optionalBuffer + ")";
        if (markers.getMarker() != null) markers.setDispatcherSentinel(new int[5]);
        return result;
    }
}
