package moon.visual.render;

import java.nio.FloatBuffer;
import java.util.function.*;

/** gw. hs/bl registry and kH matrices are host inputs; builder reuses one eager gv instance. */
public final class DrawTaskBuilder {
    private final DrawServices services;
    private final DrawTask task;
    public DrawTaskBuilder(DrawServices services){this.services=services;task=new DrawTask(services);}
    public <K> DrawTaskBuilder dispatcher(K kind,Function<K,ShaderDispatcher> registry){task.setDispatcher(registry.apply(kind));return this;}
    public DrawTaskBuilder dispatcher(ShaderDispatcher dispatcher){task.setDispatcher(dispatcher);return this;}
    public DrawTaskBuilder transform(DrawTaskState.Transform transform){task.getState().setTransform(transform);return this;}
    public DrawTaskBuilder texture(DrawTaskState.Texture texture){task.getState().setTexture(texture);return this;}
    public DrawTaskBuilder ownedBuffers(){
        task.getState().setBuffer(new VertexBuffer(services.backend,services.markers));
        task.getState().setArray(new VertexArray(services.backend));return this;
    }
    public DrawTaskBuilder captureMatrices(Supplier<FloatBuffer> modelview,Supplier<FloatBuffer> projection){
        task.getState().setModelview(MatrixTransforms.read(modelview.get().duplicate()));
        task.getState().setProjection(MatrixTransforms.read(projection.get().duplicate()));return this;
    }
    public DrawTaskBuilder retained(){task.getState().setRetained(true);return this;}
    public DrawTaskBuilder primitive(int value){task.getState().setPrimitive(value);return this;}
    public DrawTask build(){task.setup();return task;}
}
