package moon.visual.gui;

import java.util.function.IntConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import moon.visual.render.*;

/** kv task factory through original gv/gx/g5. GL state calls stay on the host render thread. */
public final class BezierEditorRenderer implements BezierEditor.Drawing {
    private final DrawServices services;
    private final Supplier<ShaderDispatcher> rounded,rectangle;
    private final IntConsumer enable;
    private final Consumer<Float> pointSize;
    public BezierEditorRenderer(DrawServices services,Supplier<ShaderDispatcher> rounded,Supplier<ShaderDispatcher> rectangle,IntConsumer enable,Consumer<Float> pointSize) {
        this.services=services;this.rounded=rounded;this.rectangle=rectangle;this.enable=enable;this.pointSize=pointSize;
    }
    public DrawTask roundedTask() { return DrawTask.builder(services).dispatcher(rounded.get()).build(); }
    public DrawTask rectangleTask(int primitive) { return DrawTask.builder(services).dispatcher(rectangle.get()).primitive(primitive).build(); }
    public DrawTaskState.Geometry rounded(float x,float y,float width,float height,float radius,int color) {
        return new UiGeometry.RoundedRectangle(x,y,width,height,radius,color,services.markers);
    }
    public void enable(int capability) { enable.accept(capability); }
    public void pointSize(float value) { pointSize.accept(value); }
}
