package moon.visual.render;

import org.lwjgl.opengl.*;

/** Owned migration FBO: fixes pz resize leak, validates completeness, render-thread close is idempotent. */
public final class LwjglFramebuffer extends FramebufferLifecycle implements AutoCloseable {
    private final Thread owner=Thread.currentThread();
    private boolean closed;
    public LwjglFramebuffer(int width,int height,boolean depth){
        this(width,height,depth,new LwjglPostProcessBackend.StateAccess(){});
    }
    public LwjglFramebuffer(int width,int height,boolean depth,LwjglPostProcessBackend.StateAccess state){
        super(depth,new Gl(state));
        if(width<=1||height<=1)throw new IllegalArgumentException("FBO dimensions must exceed 1");
        try{createBindFramebuffer(width,height);}catch(RuntimeException|Error e){delete();throw e;}
    }
    private void requireOpen(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Render thread required");if(closed)throw new IllegalStateException("FBO closed");}
    @Override public boolean resizeIfChanged(int w,int h){
        requireOpen();if(w<=1||h<=1||w==width&&h==height)return false;
        createBindFramebuffer(w,h);return true;
    }
    @Override public void bind(boolean viewport){requireOpen();super.bind(viewport);}
    @Override public void bindTexture(){requireOpen();super.bindTexture();}
    @Override public void clearBound(){requireOpen();super.clearBound();}
    @Override public void setFilter(int value){requireOpen();super.setFilter(value);}
    @Override public void setWrap(int value){requireOpen();super.setWrap(value);}
    @Override public void setClearColor(float r,float g,float b,float a){requireOpen();super.setClearColor(r,g,b,a);}
    @Override public void createBindFramebuffer(int w,int h){requireOpen();super.createBindFramebuffer(w,h);}
    @Override public void createRaw(int w,int h){requireOpen();super.createRaw(w,h);}
    @Override public void delete(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Render thread required");super.delete();}
    @Override public void close(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Render thread required");if(!closed){delete();closed=true;}}
    private static final class Gl implements Backend {
        private final LwjglPostProcessBackend.StateAccess state;
        Gl(LwjglPostProcessBackend.StateAccess state){this.state=state;}
        public boolean supported(){return true;}public void enableDepth(){state.enableDepth();}
        public int createFramebuffer(){return GL30.glGenFramebuffers();}public int createTexture(){return GL11.glGenTextures();}
        public int createRenderbuffer(){return GL30.glGenRenderbuffers();}
        public void bindFramebuffer(int id){GL30.glBindFramebuffer(36160,id);}public void bindTexture(int id){state.bindTexture(id);}
        public void bindRenderbuffer(int id){GL30.glBindRenderbuffer(36161,id);}
        public void textureStorage(int w,int h){GL11.glTexImage2D(3553,0,32856,w,h,0,6408,5121,0L);}
        public void depthStorage(int w,int h){GL30.glRenderbufferStorage(36161,33190,w,h);}
        public void attachTexture(int id){GL30.glFramebufferTexture2D(36160,36064,3553,id,0);}
        public void attachDepth(int id){GL30.glFramebufferRenderbuffer(36160,36096,36161,id);}
        public void checkComplete(){int status=GL30.glCheckFramebufferStatus(36160);if(status!=36053)throw new IllegalStateException("Incomplete FBO: "+status);}
        public void textureParameter(int p,int v){GL11.glTexParameteri(3553,p,v);}
        public void viewport(int w,int h){state.viewport(w,h);}
        public void clearColor(float r,float g,float b,float a){state.clearColor(r,g,b,a);}
        public void clearDepth(double d){state.clearDepth(d);}public void clear(int mask){state.clear(mask);}
        public void deleteTexture(int id){state.deleteTexture(id);}public void deleteRenderbuffer(int id){GL30.glDeleteRenderbuffers(id);}
        public void deleteFramebuffer(int id){GL30.glDeleteFramebuffers(id);}
    }
}
