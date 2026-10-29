package moon.visual.render;

import java.util.*;
import org.lwjglx.util.vector.Matrix4f;
import moon.visual.shader.ShaderProgram;

/** gx. Geometry/font/command implementations remain explicit host boundaries. */
public final class DrawTaskState {
    public interface Geometry { int estimatedBytes(); int write(NativeVertexWriter writer,float width,float height); }
    public interface Command { void setProgram(ShaderProgram program);void before(float width,float height);void after(); }
    public interface Transform { void apply(Matrix4f matrix);void unapply(Matrix4f matrix); }
    public interface Texture { void bind(); }
    private final DrawServices services;
    private final NativeVertexWriter writer;
    private final Matrix4f localMatrix=MatrixTransforms.scale(1,1,1);
    private final List<Geometry> geometry=new ArrayList<>();
    private final List<Command> commands=new ArrayList<>();
    private Transform transform;private Texture texture;
    private VertexBuffer buffer;private VertexArray array;
    private Matrix4f modelview,projection;
    private int first,vertexCount,primitive=7;
    private float width,height;
    private boolean compiled,geometryBuilt,retained;
    public DrawTaskState(DrawServices services) {
        this.services=services;writer=new NativeVertexWriter(2048,services.backend,services.markers);
        width=(float)services.scaledWidth.getAsDouble();height=(float)services.scaledHeight.getAsDouble();
    }
    public void add(Geometry value){geometry.add(value);} public void add(Command value){commands.add(value);}
    /** Original counts each added scalar, not each interleaved vertex. */
    public void append(float value){writer.append(value);vertexCount++;}
    public void initializeWithDispatcher(ShaderDispatcher value) { /* gx.a(g5) is empty. */ }
    public void applyTransform(){transform.apply(localMatrix);} public void unapplyTransform(){transform.unapply(localMatrix);}
    public void bindTexture(){texture.bind();}
    public void beforeCommands(ShaderProgram p){
        Object[] captured=DrawTask.getSentinel();
        for(Command c:commands){c.setProgram(p);c.before(width,height);if(captured!=null)break;}
    }
    public void afterCommands(){Object[] captured=DrawTask.getSentinel();for(Command c:commands){c.after();if(captured!=null)break;}}
    public void buildVertices(){
        Object[] captured=DrawTask.getSentinel();int bytes=0;
        for(Geometry g:geometry){bytes+=g.estimatedBytes();if(captured!=null)break;}
        if(captured==null||geometry.isEmpty())writer.reserveExtra(bytes);
        for(Geometry g:geometry){vertexCount+=g.write(writer,width,height);if(captured!=null)return;}
        geometryBuilt=true;
    }
    public void compileBuffers(){
        Object[] captured=DrawTask.getSentinel();boolean own=hasOwnBuffers();
        if(captured==null&&!own){services.diagnostics.error("Illegal task compilation call, no buffers are being overridden");return;}
        if(captured!=null?own:compiled){services.diagnostics.error("Illegal task compilation call, task is already compiled");return;}
        buffer.bind(34962);buffer.allocate(34962,(long)writer.capacityBytes()*4,35048);
        buffer.upload(34962,0,writer);compiled=true;
    }
    public void release(){
        if(DrawTask.getSentinel()==null&&hasOwnBuffers()){buffer.delete();array.delete();}
        writer.free();
    }
    public boolean hasOwnBuffers(){return DrawTask.getSentinel()!=null?buffer!=null:array!=null&&buffer!=null;}
    public boolean hasCustomMatrices(){return DrawTask.getSentinel()!=null?projection!=null:modelview!=null&&projection!=null;}
    public boolean hasTransform(){return transform!=null;} public boolean hasTexture(){return texture!=null;}
    public boolean hasCommands(){return DrawTask.getSentinel()!=null?commands.isEmpty():!commands.isEmpty();}
    public NativeVertexWriter getWriter(){return writer;} public Matrix4f getLocalMatrix(){return localMatrix;}
    public List<Geometry> getGeometry(){return geometry;} public List<Command> getCommands(){return commands;}
    public Transform getTransform(){return transform;} public DrawTaskState setTransform(Transform v){transform=v;return this;}
    public Texture getTexture(){return texture;} public DrawTaskState setTexture(Texture v){texture=v;return this;}
    public VertexBuffer getBuffer(){return buffer;} public DrawTaskState setBuffer(VertexBuffer v){buffer=v;return this;}
    public VertexArray getArray(){return array;} public DrawTaskState setArray(VertexArray v){array=v;return this;}
    public Matrix4f getModelview(){return modelview;} public DrawTaskState setModelview(Matrix4f v){modelview=v;return this;}
    public Matrix4f getProjection(){return projection;} public DrawTaskState setProjection(Matrix4f v){projection=v;return this;}
    public int getFirst(){return first;} public DrawTaskState setFirst(int v){first=v;return this;}
    public int getVertexCount(){return vertexCount;} public DrawTaskState setVertexCount(int v){vertexCount=v;return this;}
    public int getPrimitive(){return primitive;} public DrawTaskState setPrimitive(int v){primitive=v;return this;}
    public float getWidth(){return width;} public DrawTaskState setWidth(float v){width=v;return this;}
    public float getHeight(){return height;} public DrawTaskState setHeight(float v){height=v;return this;}
    public boolean isCompiled(){return compiled;} public DrawTaskState setCompiled(boolean v){compiled=v;return this;}
    public boolean isGeometryBuilt(){return geometryBuilt;} public DrawTaskState setGeometryBuilt(boolean v){geometryBuilt=v;return this;}
    public boolean isRetained(){return retained;} public DrawTaskState setRetained(boolean v){retained=v;return this;}
}
