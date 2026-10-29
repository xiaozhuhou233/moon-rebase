package moon.visual.render;

import java.io.IOException;
import java.nio.FloatBuffer;
import org.lwjglx.util.vector.Matrix4f;
import org.lwjglx.util.vector.Vector3f;
import moon.visual.shader.*;

/** g5. Original state order, both sentinel paths, capacity upload and failure residue retained. */
public abstract class ShaderDispatcher {
    private static int[] sentinel=new int[5];
    protected final DrawServices services;
    private final FloatBuffer modelviewBuffer=DrawServices.floatBuffer(16),projectionBuffer=DrawServices.floatBuffer(16);
    protected VertexArray array;protected VertexBuffer buffer;protected ShaderAttributeSet attributes;
    protected ShaderProgram program;
    private final ShaderUniform modelview,projection;
    protected ShaderDispatcher(DrawServices services){
        this.services=services;
        modelview=services.uniform("matrix.modelview",()->program);projection=services.uniform("matrix.projection",()->program);
    }
    public abstract ShaderDescriptor descriptor();
    protected abstract void setupUniforms();
    protected abstract void beforeDraw();
    public void initialize() throws IOException {
        ShaderDescriptor d=descriptor();program=services.program(d.getVertex()+".vert",d.getFragment()+".frag",d.hasOptionalBuffer());
        createResources();program.use();attributes=new ShaderAttributeSet(d.getAttributes(),null,program,services);
        setupUniforms();attributes.link();attributes.specifyPointers();projection.initializeLocation();modelview.initializeLocation();
        services.backend.bindBuffer(34962,0);services.backend.bindVertexArray(0);services.backend.useProgram(0);
        services.diagnostics.info("Setup task dispatcher successful for {}",getClass());
    }
    protected void createResources(){
        array=new VertexArray(services.backend);buffer=new VertexBuffer(services.backend,services.markers);
        array.bind();buffer.bind(34962);buffer.allocate(34962,2048,35048);
    }
    private void uploadDefault(DrawTaskState s){
        buffer.bind(34962);buffer.allocate(34962,(long)s.getWriter().capacityBytes()*4,35048);buffer.upload(34962,0,s.getWriter());
    }
    public void draw(DrawTaskState s){
        services.backend.alphaFunc(516,0);int[] captured=getSentinel();program.use();array.bind();
        if(captured!=null){
            if(!s.isGeometryBuilt())s.buildVertices();
            if(s.hasOwnBuffers()) { if(!s.isCompiled())s.compileBuffers();else s.getBuffer().bind(34962); }
            else uploadDefault(s);
            if(s.hasTransform())s.applyTransform();if(s.hasTexture())s.bindTexture();
        } else {
            // These jumps are unusual but present in the classfile, not a portable fast path.
            if(!s.isGeometryBuilt()){s.compileBuffers();services.markers.setMarker("zcXTHb");}
            s.getBuffer().bind(34962);uploadDefault(s);
            if(s.hasTransform())s.bindTexture();
        }
        float sx=RenderTransformState.getScaleX(),sy=RenderTransformState.getScaleY(),sz=RenderTransformState.getScaleZ();
        float tx=RenderTransformState.getTranslateX(),ty=RenderTransformState.getTranslateY(),tz=RenderTransformState.getTranslateZ();
        float angle=RenderTransformState.getAngle(),ax=RenderTransformState.getAxisX(),ay=RenderTransformState.getAxisY(),az=RenderTransformState.getAxisZ();
        boolean hasScale=RenderTransformState.hasScale();
        Matrix4f local=s.getLocalMatrix();
        if(captured!=null){
            if(hasScale)local.scale(new Vector3f(sx,sy,sz));
            if(RenderTransformState.hasTranslation())local.translate(new Vector3f(tx,ty,tz));
            if(RenderTransformState.hasRotation())local.rotate(angle,new Vector3f(ax,ay,az));
            if(s.hasCommands())s.beforeCommands(program);
            attributes.specifyPointers();beforeDraw();modelviewBuffer.clear();projectionBuffer.clear();
        }
        MatrixTransforms.write((captured!=null?s.hasCustomMatrices():hasScale)?s.getModelview():local,modelviewBuffer,services.markers);
        modelview.setMatrix4(false,modelviewBuffer);
        Matrix4f ortho=MatrixTransforms.orthographic(0,1,1,0,0,1,services.markers);
        MatrixTransforms.write(captured==null||s.hasCustomMatrices()?s.getProjection():ortho,projectionBuffer,services.markers);
        projection.setMatrix4(false,projectionBuffer);
        int shadowColor=ShadowStyle.apply(services,program);
        try{services.backend.drawArrays(s.getPrimitive(),0,s.getVertexCount());}
        finally{if(shadowColor>=0)services.backend.uniform1i(shadowColor,0);}
        boolean retained=s.isRetained();
        if(captured==null){services.backend.alphaFunc(retained?1:0,.1F);return;}
        if(!retained)s.release();
        services.backend.bindBuffer(34962,0);services.backend.bindVertexArray(0);services.backend.useProgram(0);
        if(s.hasTransform())s.unapplyTransform();if(s.hasTexture())services.backend.bindTexture(0);
        // Original inverse operations are in the SAME order and query live flags again.
        if(RenderTransformState.hasScale())local.scale(new Vector3f(1/sx,1/sy,1/sz));
        if(RenderTransformState.hasTranslation())local.translate(new Vector3f(-tx,-ty,-tz));
        if(RenderTransformState.hasRotation())local.rotate(-angle,new Vector3f(ax,ay,az));
        if(s.hasCommands())s.afterCommands();services.backend.alphaFunc(516,.1F);
    }
    public FloatBuffer getModelviewBuffer(){return modelviewBuffer;} public FloatBuffer getProjectionBuffer(){return projectionBuffer;}
    public VertexArray getArray(){return array;} public VertexBuffer getBuffer(){return buffer;}
    public ShaderAttributeSet getAttributes(){return attributes;} public ShaderProgram getProgram(){return program;}
    public ShaderUniform getModelviewUniform(){return modelview;} public ShaderUniform getProjectionUniform(){return projection;}
    public static int[] getSentinel(){return sentinel;} public static void setSentinel(int[] v){sentinel=v;}
}
