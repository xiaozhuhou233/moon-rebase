package moon.visual.render;

import java.util.function.Predicate;

/** kS; one retained frustum, positioned from the current render-view entity for
 * every test. Its Y.b()==0 path ignores the entity's bypass flag. */
public final class GlowEntityFrustum<E,B> implements Predicate<E> {
    public interface Host<E,B> {
        int marker();B bounds(E entity);E renderViewEntity();
        double x(E entity);double y(E entity);double z(E entity);
        void position(double x,double y,double z);boolean contains(B bounds);boolean ignoreFrustum(E entity);
    }
    private final Host<E,B> host;
    public GlowEntityFrustum(Host<E,B> host){this.host=host;}
    public boolean test(E entity){
        int marker=host.marker();boolean inside=testBounds(host.bounds(entity));
        return marker==0?inside:inside||host.ignoreFrustum(entity);
    }
    public boolean testBounds(B bounds){
        E view=host.renderViewEntity();host.position(host.x(view),host.y(view),host.z(view));
        return host.contains(bounds);
    }
}
