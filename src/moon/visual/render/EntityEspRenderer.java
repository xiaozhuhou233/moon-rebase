package moon.visual.render;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import org.lwjglx.util.vector.Vector4f;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** fI: retained 2D ESP scheduler and alpha-only held-item silhouettes.
 * q0 belongs to the scheduler, independently of each component's Cz marker.
 * Original encrypted integers are required inputs, never migration defaults. */
public final class EntityEspRenderer<E,I> implements AutoCloseable {
    public interface Settings {
        boolean enabled();boolean itemsEnabled();boolean itemIconsEnabled();
        boolean itemNamesEnabled();boolean armorEnabled();int itemColor();
    }
    public interface Host<E,I> extends MarkerHooks {
        int updateMarker();int renderMarker();void setRenderMarker(int value);
        MsdfFontBase verdana();BitmapFontFamily bitmapFont();Iterable<E> entities();
        boolean inFrustum(E entity);Vector4f projection(E entity);
        boolean isPlayer(E entity);I heldItem(E player);void renderItem(I item,float x,float y);
        void bindMainFramebuffer(boolean viewport);void bindBlockAtlas();
        void blockAtlasBlurMipmap(boolean blur,boolean mipmap);void restoreBlockAtlasBlurMipmap();
        void setupOverlay();void enableGuiItemLighting();void disableItemLighting();
    }
    public interface Tasks {
        DrawTask rectangles();DrawTask gradients();DrawTask msdf(MsdfFontBase font);DrawTask bitmap(BitmapFontFamily font);
    }
    public interface State {
        /** Original Gui.drawRect(0,0,0,0,0), including all state/vertex side effects. */
        void zeroAreaGuiRectangle();
        void disableDepth();void enableRescaleNormal();void disableRescaleNormal();
        void disableAlpha();void enableAlpha();void alphaFunc(int function,float reference);
        void enableBlend();void blendFuncSeparate(int sr,int dr,int sa,int da);void blendFunc(int source,int destination);
        void color(float r,float g,float b,float a);void scale(float x,float y,float z);void bindTexture(int texture);
        void colorMask(boolean r,boolean g,boolean b,boolean a);
        /** GL11C call, deliberately bypassing the Minecraft depth-function cache. */
        void rawDepthFunc(int function);
        /** Actual k3 rectangle, not an ordinary colored item draw. */
        void itemColorRectangle(float x,float y,float width,float height,int color);
    }
    public static final class Constants {
        public final int alphaFunction,sourceRgb,destinationRgb,shadowColor,restoreSource,restoreDestination,maskDepth,restoreDepth;
        /** Eight distinct fI call sites, in bytecode order; no inferred GL enums. */
        public Constants(int alphaFunction,int sourceRgb,int destinationRgb,int shadowColor,
                int restoreSource,int restoreDestination,int maskDepth,int restoreDepth) {
            this.alphaFunction=alphaFunction;this.sourceRgb=sourceRgb;this.destinationRgb=destinationRgb;this.shadowColor=shadowColor;
            this.restoreSource=restoreSource;this.restoreDestination=restoreDestination;this.maskDepth=maskDepth;this.restoreDepth=restoreDepth;
        }
    }
    private final Host<E,I> host;private final Settings settings;private final Tasks tasks;private final State state;
    private final Constants constants;private final Predicate<E> predicate;
    private final List<EntityEspComponent<E,?>> components;
    private DrawTask rectangles,msdf,gradients,bitmap;private boolean closed;
    /** Arguments follow the original eager construction order fO/fP/fN/fQ/fS/fR. */
    public EntityEspRenderer(Host<E,I> host,Settings settings,Tasks tasks,State state,Constants constants,Predicate<E> predicate,
            EntityEspComponent<E,?> box,EntityEspComponent<E,?> health,EntityEspComponent<E,?> armor,
            EntityEspComponent<E,?> status,EntityEspComponent<E,?> name,EntityEspComponent<E,?> heldItem) {
        this.host=host;this.settings=settings;this.tasks=tasks;this.state=state;this.constants=constants;this.predicate=predicate;
        components=Collections.unmodifiableList(Arrays.asList(box,health,armor,status,name,heldItem));
    }
    private void requireOpen(){if(closed)throw new IllegalStateException("ESP renderer closed");}
    public boolean enabled(){return settings.enabled();}
    public void afterRender(){ /* Original c() is empty. */ }
    private static void release(DrawTask task){if(task!=null)task.getState().release();}
    public void update(){
        requireOpen();MsdfFontBase face=host.verdana();int marker=host.updateMarker();BitmapFontFamily pixel=host.bitmapFont();
        if(marker!=0){release(rectangles);release(msdf);release(gradients);release(bitmap);}
        else if(rectangles!=null)bitmap.getState().release(); // Old d tests old g; null g intentionally throws.
        rectangles=tasks.rectangles();gradients=tasks.gradients();msdf=tasks.msdf(face);bitmap=tasks.bitmap(pixel);
        Iterator<E> entities=host.entities().iterator();
        while(entities.hasNext()){
            E entity=entities.next();
            if(predicate.test(entity)&&(marker==0||host.inFrustum(entity))&&host.projection(entity)!=null){
                for(EntityEspComponent<E,?> component:components){
                    if(marker==0){component.draw(entity);host.setMarker("H2e6ub");return;}
                    if(component.accepts(entity)){
                        component.setRectangles(rectangles);component.setMsdf(msdf);
                        component.setGradients(gradients);component.setBitmap(bitmap);component.draw(entity);
                    }
                }
            }
            if(marker==0)return;
        }
    }
    public void render(){
        requireOpen();int marker=host.renderMarker();
        if(marker==0){
            if(settings.itemsEnabled()&&settings.itemIconsEnabled()){
                BitmapFontFamily font=host.bitmapFont();state.zeroAreaGuiRectangle();host.bindMainFramebuffer(false);
                host.bindBlockAtlas();host.blockAtlasBlurMipmap(false,false);host.setupOverlay();host.enableGuiItemLighting();
                state.disableDepth();state.enableRescaleNormal();state.disableAlpha();state.alphaFunc(constants.alphaFunction,.1F);
                state.enableBlend();state.blendFuncSeparate(constants.sourceRgb,constants.destinationRgb,1,0);
                state.color(1,1,1,1);state.scale(.5F,.5F,1);
                for(E entity:host.entities()){
                    Vector4f bounds=host.projection(entity);
                    if(bounds==null||!predicate.test(entity)||!host.inFrustum(entity)||!host.isPlayer(entity)||host.heldItem(entity)==null)continue;
                    I item=host.heldItem(entity); // Separate original getter; do not reuse the null-check value.
                    float width=bounds.z-bounds.x,height=bounds.w-bounds.y;
                    float x=(bounds.x+width/2-4)*2;
                    float y=(bounds.y+height+2+(settings.itemNamesEnabled()?font.height()+1:0))*2+(settings.armorEnabled()?2.5F:0);
                    itemMask(item,x+1,y+1,constants.shadowColor);itemMask(item,x,y,settings.itemColor());
                }
                state.blendFunc(constants.restoreSource,constants.restoreDestination);state.scale(2,2,1);
                state.enableAlpha();state.disableRescaleNormal();state.disableDepth();host.disableItemLighting();
                host.restoreBlockAtlasBlurMipmap();state.bindTexture(0);
            }
            state.enableBlend();
            if(rectangles!=null)rectangles.draw();if(msdf!=null)msdf.draw();if(gradients!=null)gradients.draw();if(bitmap!=null)bitmap.draw();
        }else rectangles.draw(); // Carried d across 721/753/785 -> unconditional draw810; null intentionally throws.
        if(host.getMarker()!=null)host.setRenderMarker(marker+1);
    }
    public void itemMask(I item,float x,float y,int color){
        state.colorMask(false,false,false,true);host.renderItem(item,x,y);state.colorMask(true,true,true,true);
        state.rawDepthFunc(constants.maskDepth);state.itemColorRectangle(x-25,y-25,50,50,color);state.rawDepthFunc(constants.restoreDepth);
        // No finally: the original leaves color-mask/depth/matrix/atlas state on failure.
    }
    public List<EntityEspComponent<E,?>> components(){return components;}
    public DrawTask rectangles(){return rectangles;}public DrawTask msdf(){return msdf;}
    public DrawTask gradients(){return gradients;}public DrawTask bitmap(){return bitmap;}
    /** Added ownership endpoint for destination unload. Original afterRender() remains empty. */
    public void close(){if(!closed){release(rectangles);release(msdf);release(gradients);release(bitmap);closed=true;}}
}
