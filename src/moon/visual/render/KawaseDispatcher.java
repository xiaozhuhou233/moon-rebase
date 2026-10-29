package moon.visual.render;

import moon.visual.shader.*;
import moon.visual.render.PostProcessBackend.Kind;

/** g8 plus ha/hb/hc/hd and g9/g_, consolidated by shader; exact quad/uniform order. */
public final class KawaseDispatcher extends ShaderDispatcher {
    private static String kawaseSentinel;
    private final ShaderDescriptor descriptor;
    public final ShaderUniform texture,offset,texelSize,alpha,stencil,useStencil;
    public KawaseDispatcher(DrawServices services,Kind kind){
        this(services,kind.name().toLowerCase(java.util.Locale.ROOT),kind==Kind.KAWASE_UP||kind==Kind.SHADOW_UP);
    }
    /** Explicit Glow variants, without adding Glow stages to the fA/fz registry. */
    public static KawaseDispatcher glowDown(DrawServices services){return new KawaseDispatcher(services,"glow_down",false);}
    public static KawaseDispatcher glowUp(DrawServices services){return new KawaseDispatcher(services,"glow_up",true);}
    private KawaseDispatcher(DrawServices services,String name,boolean up){
        super(services);
        texture=services.uniform("kawase.texture",()->program);offset=services.uniform("kawase.offset",()->program);
        texelSize=services.uniform("kawase.texelSize",()->program);alpha=services.uniform("kawase.alpha",()->program);
        stencil=up?services.uniform("kawase.stencil",()->program):null;useStencil=up?services.uniform("kawase.doStencil",()->program):null;
        descriptor=ShaderDescriptor.builder(new ShaderDescriptorBuilder.MarkerHooks(){
            public int[] getDispatcherSentinel(){return ShaderDispatcher.getSentinel();}
            public Object getMarker(){return services.markers.getMarker();}
            public void setDispatcherSentinel(int[] v){ShaderDispatcher.setSentinel(v);}
        }).attributes("kawase").vertex(name).fragment(name).build();
    }
    @Override public ShaderDescriptor descriptor(){return descriptor;}
    @Override protected void setupUniforms(){
        if(stencil!=null){stencil.initializeLocation();useStencil.initializeLocation();}
        getKawaseSentinel();texture.initializeLocation();offset.initializeLocation();texelSize.initializeLocation();alpha.initializeLocation();
        if(services.markers.getMarker()!=null)setKawaseSentinel("QBBKQc");
    }
    @Override protected void beforeDraw() { }
    @Override public void draw(DrawTaskState s){
        String captured=getKawaseSentinel();NativeVertexWriter w=s.getWriter();
        w.append(1,0).append(1,1);w.append(0,0).append(0,1);w.append(0,1).append(0,0);w.append(1,1).append(1,0);
        s.setVertexCount(4);super.draw(s);if(captured!=null)services.markers.setMarker("sx7qvb");
    }
    public static String getKawaseSentinel(){return kawaseSentinel;}
    public static void setKawaseSentinel(String v){kawaseSentinel=v;}
}
