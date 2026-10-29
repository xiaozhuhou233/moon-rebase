package moon.visual.render;

/** Original fI gw.a().c(): each task owns a VAO/VBO and retains its native
 * writer/buffers across render(), releasing them at the next update(). */
public final class EntityEspTaskFactory implements EntityEspRenderer.Tasks {
    private final DrawServices services;
    private final ShaderDispatcher rectangles,gradients,msdf,bitmap;
    public EntityEspTaskFactory(DrawServices services,ShaderDispatcher rectangles,ShaderDispatcher gradients,
            ShaderDispatcher msdf,ShaderDispatcher bitmap) {
        this.services=services;this.rectangles=rectangles;this.gradients=gradients;this.msdf=msdf;this.bitmap=bitmap;
    }
    private DrawTaskBuilder task(ShaderDispatcher dispatcher){return DrawTask.builder(services).dispatcher(dispatcher).ownedBuffers().retained();}
    public DrawTask rectangles(){return task(rectangles).build();}
    public DrawTask gradients(){return task(gradients).build();}
    public DrawTask msdf(MsdfFontBase font){return task(msdf).texture(font::bind).build();}
    public DrawTask bitmap(BitmapFontFamily font){return task(bitmap).transform(font).build();}
}
