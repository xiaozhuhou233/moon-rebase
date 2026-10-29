package moon.visual.render;

import java.io.IOException;
import java.util.*;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.Consumer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import moon.visual.render.PostProcessBackend.Framebuffer;

/** LWJGL3 host for fK/fJ. Programs/writers belong to the supplied draw owner;
 * framebuffers belong to this adapter. Pass 7 for original compatibility quads,
 * or explicitly select another destination topology. The original GL_CLAMP
 * and fixed-function state require a compatibility context, as in the base's
 * Display.create(PixelFormat) path. Topology alone does not enable core support.
 */
public final class LwjglGlowBackend implements GlowOutlinePass.Host, GlowBlurPass.Host, AutoCloseable {
    private final DrawServices services;
    private final LwjglDrawBackend owner;
    private final LwjglPostProcessBackend.StateAccess state;
    private final IntSupplier width,height;
    private final Supplier<Object> marker;
    private final Consumer<Object[]> markerSetter;
    private final int primitive;
    private final Map<GlowOutlinePass.Kind,ShaderDispatcher> dispatchers=new EnumMap<>(GlowOutlinePass.Kind.class);
    private final KawaseDispatcher glowDown,glowUp;
    private final Set<LwjglSeedFramebuffer> framebuffers=Collections.newSetFromMap(new IdentityHashMap<LwjglSeedFramebuffer,Boolean>());
    private boolean closed;
    public LwjglGlowBackend(DrawServices services,LwjglDrawBackend owner,LwjglPostProcessBackend.StateAccess state,
            IntSupplier width,IntSupplier height,Supplier<Object> marker,int primitive) throws IOException {
        this(services,owner,state,width,height,marker,value->{throw new UnsupportedOperationException("gb marker setter is required for the nonnull r2 branch");},primitive);
    }
    public LwjglGlowBackend(DrawServices services,LwjglDrawBackend owner,LwjglPostProcessBackend.StateAccess state,
            IntSupplier width,IntSupplier height,Supplier<Object> marker,Consumer<Object[]> markerSetter,int primitive) throws IOException {
        this.services=services; this.owner=owner; this.state=state;
        this.width=width; this.height=height; this.marker=marker; this.markerSetter=markerSetter; this.primitive=primitive;
        dispatchers.put(GlowOutlinePass.Kind.OUTLINE,new GlowDispatchers.Outline(services));
        dispatchers.put(GlowOutlinePass.Kind.FLOOD,new GlowDispatchers.Flood(services));
        dispatchers.put(GlowOutlinePass.Kind.BASE,new GlowDispatchers.Base(services));
        for(ShaderDispatcher dispatcher:dispatchers.values()) {
            dispatcher.initialize(); int program=dispatcher.getProgram().getProgram();
            if(program==0 || GL20.glGetProgrami(program,35714)!=1) throw new IOException("Glow shader failed: "+dispatcher.descriptor().getFragment());
        }
        glowDown=KawaseDispatcher.glowDown(services);glowUp=KawaseDispatcher.glowUp(services);
        for(KawaseDispatcher dispatcher:new KawaseDispatcher[]{glowDown,glowUp}) {
            dispatcher.initialize();int program=dispatcher.getProgram().getProgram();
            if(program==0||GL20.glGetProgrami(program,35714)!=1)throw new IOException("Glow blur shader failed: "+dispatcher.descriptor().getFragment());
        }
    }
    private void requireOpen() { owner.requireOpen(); if(closed) throw new IllegalStateException("Glow backend closed"); }
    public Object marker() { return marker.get(); }
    public Object externalMarker(){return services.markers.getMarker();}
    public void setMarker(Object[] value){markerSetter.accept(value);}
    public int displayWidth() { return width.getAsInt(); }
    public int displayHeight() { return height.getAsInt(); }
    public Framebuffer createFramebuffer(int width,int height,boolean depth) {
        requireOpen(); LwjglSeedFramebuffer framebuffer=new LwjglSeedFramebuffer(width,height,depth,state);
        framebuffers.add(framebuffer); return framebuffer;
    }
    public DrawTask createTask(GlowOutlinePass.Kind kind) {
        requireOpen(); return DrawTask.builder(services).dispatcher(dispatchers.get(kind)).retained().ownedBuffers().primitive(primitive).build();
    }
    public DrawTask createBlurTask(boolean up){
        requireOpen();return DrawTask.builder(services).dispatcher(up?glowUp:glowDown).retained().ownedBuffers().primitive(primitive).build();
    }
    public void rawClearColor(float r,float g,float b,float a){GL11.glClearColor(r,g,b,a);}
    public void enableTexture2D() { state.enableTexture2D(); }
    public void activeTexture(int textureEnum) { state.activeTexture(textureEnum); }
    public void bindTexture(int texture) { state.bindTexture(texture); }
    public void blendFuncSeparate(int sr,int dr,int sa,int da) { state.blendFuncSeparate(sr,dr,sa,da); }
    public void releaseFramebuffer(Framebuffer framebuffer) {
        requireOpen(); LwjglSeedFramebuffer seed=(LwjglSeedFramebuffer)framebuffer;
        if(!framebuffers.contains(seed)) throw new IllegalArgumentException("Foreign or already released Glow FBO");
        seed.close(); framebuffers.remove(seed);
    }
    public int liveFramebuffers() { return framebuffers.size(); }
    public void close() {
        owner.requireOpen(); if(closed) return;
        for(LwjglSeedFramebuffer framebuffer:framebuffers) framebuffer.close();
        framebuffers.clear(); closed=true;
    }
}
