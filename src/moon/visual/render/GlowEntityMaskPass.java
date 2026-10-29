package moon.visual.render;

import java.awt.Color;
import java.util.Iterator;
import java.util.function.Predicate;
import moon.visual.render.PostProcessBackend.Framebuffer;
import moon.visual.render.GlowEntityFilter.Type;

/** fF. Entity capture, original state order and marker/exception residue. The
 * filter integer and patched nv/kR game hooks are explicit migration inputs. */
public final class GlowEntityMaskPass<E> implements AutoCloseable {
    public interface Config {
        Boolean primaryEnabled();Boolean secondaryEnabled();
        Integer hurtColor();Integer mobColor();Integer animalColor();Integer playerColor();
    }
    /** Original nv.a/b/c/d/e setters: capture-color path, glint, post-render
     * shadow/fire, living names and the jJ capture bypass. These are patched
     * renderer policies; vanilla renderOutlines is a separate original flag. */
    public interface Renderer<E> {
        void flagA(boolean value);void flagB(boolean value);void flagC(boolean value);
        void flagD(boolean value);void flagE(boolean value);boolean render(E entity,float partialTicks);
    }
    public interface Host<E> extends GlowEntityFilter.Entities<E> {
        Object marker();Object externalMarker();void setMarker(String value);
        Framebuffer createFramebuffer(int width,int height,boolean depth);void releaseFramebuffer(Framebuffer value);
        int displayWidth();int displayHeight();float partialTicks();
        void enableBlend();void disableDepth();void enableDepth();void setupCamera(float partialTicks,int pass);
        /** pv.aj is proved by the original entityShadows settings save record. */
        void lightmap(float x,float y);boolean entityShadows();void entityShadows(boolean value);
        Renderer<E> renderer();Iterable<E> loadedEntities();boolean inFrustum(E entity);int hurtTime(E entity);
        void resetColorEnvironment();void colorEnvironment(Color color);void disableItemLighting();void setupOverlay();
        Framebuffer mainFramebuffer();
    }
    private final Host<E> host;private final Config config;private final int filter;
    private final Framebuffer output;private final Predicate<E> predicate;private boolean closed;
    /** fF.g raw-long candidate does not yield a valid GL filter. No default. */
    public GlowEntityMaskPass(Host<E> host,Config config,int filter,Predicate<E> predicate){
        this.host=host;this.config=config;this.filter=filter;
        host.marker();output=host.createFramebuffer(1,1,false);this.predicate=predicate;
        if(host.externalMarker()!=null)host.setMarker("kGPZbb");
    }
    private void requireOpen(){if(closed)throw new IllegalStateException("Glow entity mask closed");}
    public void render(){ /* Original a() is empty. */ }
    public void update(){ /* Original b() is empty. */ }
    public void afterRender(){capture();}
    public boolean enabled(){
        Object marker=host.marker();boolean primary=config.primaryEnabled().booleanValue();
        return marker!=null?primary:primary||config.secondaryEnabled().booleanValue();
    }
    public void capture(){
        requireOpen();host.enableBlend();Object marker=host.marker();host.disableDepth();
        host.setupCamera(host.partialTicks(),0);host.lightmap(240,240);
        boolean previous=host.entityShadows();host.entityShadows(false);
        host.renderer().flagA(true);host.renderer().flagB(false);host.renderer().flagC(false);
        host.renderer().flagD(false);host.renderer().flagE(true);
        int width=host.displayWidth();
        if(marker==null){
            if(output.resizeIfChanged(width,host.displayHeight()))output.setFilter(filter);
            output.bind(false);
        }else output.bind(width!=0); // Nonnull marker passes displayWidth as raw Z.
        output.clearBound();Color last=null;host.resetColorEnvironment();
        Iterator<E> entities=host.loadedEntities().iterator();
        while(entities.hasNext()){
            E entity=entities.next();
            if(marker!=null)return; // Offset233 ->457 skips restoration on first entity.
            if(!predicate.test(entity)||!host.inFrustum(entity))continue;
            Color color=new Color(color(entity)); // Color(int), intentionally opaque.
            if(!color.equals(last)){host.colorEnvironment(color);last=color;}
            host.renderer().render(entity,host.partialTicks());
        }
        host.resetColorEnvironment();host.enableDepth();host.disableItemLighting();host.setupOverlay();
        host.renderer().flagA(false);host.renderer().flagB(true);host.renderer().flagC(true);
        host.renderer().flagD(true);host.renderer().flagE(false);
        host.entityShadows(previous);host.mainFramebuffer().bind(false);
    }
    public int color(E entity){
        Object marker=host.marker();boolean living=host.is(entity,Type.LIVING);
        if(marker!=null)return living?1:0;
        if(living&&host.hurtTime(entity)>0)return config.hurtColor().intValue();
        if(host.is(entity,Type.MOB))return config.mobColor().intValue();
        if(host.is(entity,Type.ANIMAL_A))return config.animalColor().intValue();
        if(host.is(entity,Type.PLAYER))return config.playerColor().intValue();
        return -1;
    }
    public Framebuffer output(){return output;}public Predicate<E> predicate(){return predicate;}
    /** Added migration ownership endpoint; capture exceptions keep original state. */
    public void close(){if(!closed){host.releaseFramebuffer(output);closed=true;}}
}
