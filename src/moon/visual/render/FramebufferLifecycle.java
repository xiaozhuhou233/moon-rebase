package moon.visual.render;

/** moom.pz allocation/resize/clear/delete subset. Fixed-function screen rendering remains outside this class. */
public class FramebufferLifecycle implements PostProcessBackend.Framebuffer {
    public interface Backend {
        boolean supported(); void enableDepth();
        int createFramebuffer();int createTexture();int createRenderbuffer();
        void bindFramebuffer(int id);void bindTexture(int id);void bindRenderbuffer(int id);
        void textureStorage(int width,int height);void depthStorage(int width,int height);
        void attachTexture(int texture);void attachDepth(int renderbuffer);void checkComplete();
        void textureParameter(int parameter,int value);void viewport(int width,int height);
        void clearColor(float r,float g,float b,float a);void clearDepth(double depth);void clear(int mask);
        void deleteTexture(int id);void deleteRenderbuffer(int id);void deleteFramebuffer(int id);
    }
    protected final Backend backend;
    protected final boolean depth;
    protected int framebuffer=-1,texture=-1,renderbuffer=-1,width,height,textureWidth,textureHeight,filter;
    private final float[] clearColor={1,1,1,0};
    public FramebufferLifecycle(boolean depth,Backend backend){this.depth=depth;this.backend=backend;}
    public void createBindFramebuffer(int w,int h){
        if(!backend.supported()){width=w;height=h;return;}
        backend.enableDepth();if(framebuffer>=0)delete();createRaw(w,h);
        backend.checkComplete();backend.bindFramebuffer(0);
    }
    /** Original raw creation overwrites old handles; does not delete. */
    public void createRaw(int w,int h){
        width=w;height=h;textureWidth=w;textureHeight=h;
        if(!backend.supported()){clearAndUnbind();return;}
        framebuffer=backend.createFramebuffer();texture=backend.createTexture();
        if(depth)renderbuffer=backend.createRenderbuffer();
        setFilter(9728);backend.bindTexture(texture);backend.textureStorage(w,h);
        backend.bindFramebuffer(framebuffer);backend.attachTexture(texture);
        if(depth){backend.bindRenderbuffer(renderbuffer);backend.depthStorage(w,h);backend.attachDepth(renderbuffer);}
        clearAndUnbind();backend.bindTexture(0);
    }
    /** Faithful pz resize leaks old handles. Production adapter overrides this explicitly. */
    public boolean resizeIfChanged(int w,int h){
        if(w<=1||h<=1)return false;
        if(w!=width||h!=height){createRaw(w,h);return true;}return false;
    }
    public void setClearColor(float r,float g,float b,float a){clearColor[0]=r;clearColor[1]=g;clearColor[2]=b;clearColor[3]=a;}
    public void setFilter(int value){
        if(!backend.supported())return;filter=value;
        backend.bindTexture(texture);backend.textureParameter(10241,value);backend.textureParameter(10240,value);
        backend.textureParameter(10242,10496);backend.textureParameter(10243,10496);backend.bindTexture(0);
    }
    public void setWrap(int value){if(!backend.supported())return;backend.bindTexture(texture);backend.textureParameter(10242,value);backend.textureParameter(10243,value);backend.bindTexture(0);}
    public void bind(boolean updateViewport){if(!backend.supported())return;backend.bindFramebuffer(framebuffer);if(updateViewport)backend.viewport(width,height);}
    public void bindTexture(){if(backend.supported())backend.bindTexture(texture);}
    public void clearAndUnbind(){bind(true);clearBound();if(backend.supported())backend.bindFramebuffer(0);}
    public void clearBound(){backend.clearColor(clearColor[0],clearColor[1],clearColor[2],clearColor[3]);int mask=16384;if(depth){backend.clearDepth(1);mask|=256;}backend.clear(mask);}
    public void delete(){
        if(!backend.supported())return;backend.bindTexture(0);backend.bindFramebuffer(0);
        if(renderbuffer>-1){backend.deleteRenderbuffer(renderbuffer);renderbuffer=-1;}
        if(texture>-1){backend.deleteTexture(texture);texture=-1;}
        if(framebuffer>-1){backend.bindFramebuffer(0);backend.deleteFramebuffer(framebuffer);framebuffer=-1;}
    }
    public int width(){return width;}public int height(){return height;}public int texture(){return texture;}
    public int getFramebuffer(){return framebuffer;}public int getDepthRenderbuffer(){return renderbuffer;}
    public int getTextureWidth(){return textureWidth;}public int getTextureHeight(){return textureHeight;}
    public int getFilter(){return filter;}
}
