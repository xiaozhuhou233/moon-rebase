package moon.visual.render;

import org.lwjglx.util.vector.Vector4f;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** fM: four separately assigned retained tasks and the original .5 spacing.
 * The entity and module identities stay in the destination game/framework.
 * Its eager Minecraft singleton field is represented by the injected Host. */
public abstract class EntityEspComponent<E,S> {
    public interface Host<E> extends MarkerHooks {
        Vector4f projection(E entity);
        /** moom.Cz.c(), independently captured from the fI/q0 scheduler marker. */
        int componentMarker();
        /** moom.Cz.b(), used by armor's inverse marker branch. */
        int inverseComponentMarker();
        void setInverseComponentMarker(int value);
    }
    private final S settings;
    protected final Host<E> host;
    protected DrawTask rectangles,msdf,gradients,bitmap;
    protected float spacing=.5F;
    protected EntityEspComponent(S settings,Host<E> host) { this.settings=settings;this.host=host; }
    public abstract void draw(E entity);
    public abstract boolean accepts(E entity);
    public EntityEspComponent<E,S> setRectangles(DrawTask task) { rectangles=task;return this; }
    public EntityEspComponent<E,S> setMsdf(DrawTask task) { msdf=task;return this; }
    public EntityEspComponent<E,S> setGradients(DrawTask task) { gradients=task;return this; }
    public EntityEspComponent<E,S> setBitmap(DrawTask task) { bitmap=task;return this; }
    public EntityEspComponent<E,S> setSpacing(float value) { spacing=value;return this; }
    public S getSettings() { return settings; }
    public DrawTask getRectangles() { return rectangles; }
    public DrawTask getMsdf() { return msdf; }
    public DrawTask getGradients() { return gradients; }
    public DrawTask getBitmap() { return bitmap; }
    public float getSpacing() { return spacing; }
}
