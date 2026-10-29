package moon.visual.render;

import moon.visual.shader.*;

/** hp, bl.WORLD. Uses the recovered position3/color4 shader and matrix uniforms. */
public final class WorldDispatcher extends ShaderDispatcher {
    private final ShaderDescriptor descriptor;
    private static int sentinel=119;

    public WorldDispatcher(DrawServices services) {
        super(services);
        descriptor=ShaderDescriptor.builder(new ShaderDescriptorBuilder.MarkerHooks() {
            public int[] getDispatcherSentinel(){return ShaderDispatcher.getSentinel();}
            public Object getMarker(){return services.markers.getMarker();}
            public void setDispatcherSentinel(int[] value){ShaderDispatcher.setSentinel(value);}
        }).allowed(UiGeometry.Rectangle.class).attributes("world").vertex("world").fragment("world").build();
    }

    public ShaderDescriptor descriptor(){return descriptor;}
    protected void setupUniforms(){}
    protected void beforeDraw(){}
    public static int getWorldSentinel(){return sentinel;}
    public static void setWorldSentinel(int value){sentinel=value;}
    public static int inverseWorldSentinel(){return sentinel==0?84:0;}
}
