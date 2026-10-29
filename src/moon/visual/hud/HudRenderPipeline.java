package moon.visual.hud;
import moon.module.visual.hud.HudSettings;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import moon.visual.event.HudOverlayPhaseEvent;
import moon.visual.render.*;

/** jP's normal ba.b()==null render/cache path; HudSettings contains its setting tree.
 * Original startup registration and nondefault paths remain separate work.
 * Register PRE and POST at original LOWEST priority; Effects POST runs before this.
 * Original fw switch assignments remain required inputs. Background/blend ints
 * are decoded offline with jP's supplied runtime seed (HUD_PIPELINE_EVIDENCE.json).
 */
public final class HudRenderPipeline {
    public static final int BACKGROUND_ARGB=0x80000000,BLEND_SOURCE=770,BLEND_DESTINATION=771;
    // Exact ba/bd names and declaration order, not inferred fw switch assignments.
    public enum TextColorMode { STATIC,GRADIENT,PULSING,INDIVIDUAL }
    public enum SidebarColorMode { SYNC,STATIC,GRADIENT,PULSING }
    public interface Config extends HudColors.Config {
        float scale();
        TextColorMode textMode();SidebarColorMode sidebarMode();
        int textColorOne();int textColorTwo();int sidebarColorOne();int sidebarColorTwo();
    }
    public interface Host {
        boolean originalGuardIsNull();
        default ShadowStyle shadowStyle(Object source){return null;}
        MsdfFontBase selectedFont(); // aq.get().supplier().get(), even on clean PRE
        DrawServices drawing();
        ShaderDispatcher rectangleDispatcher();ShaderDispatcher roundedDispatcher();ShaderDispatcher msdfDispatcher();
        void bindMainFramebuffer(boolean viewport);
        void disableDepth();void enableDepth();void enableBlend();
        void blendFuncSeparate(int sourceRgb,int destinationRgb,int sourceAlpha,int destinationAlpha);
        void fixedFunctionScale(float x,float y,float z);
    }
    public static final class Parameters {
        public final int backgroundArgb,blendSource,blendDestination;
        public final ToIntFunction<TextColorMode> textSwitch;
        public final ToIntFunction<SidebarColorMode> sidebarSwitch;
        public Parameters(ToIntFunction<TextColorMode> textSwitch,ToIntFunction<SidebarColorMode> sidebarSwitch) {
            this(BACKGROUND_ARGB,BLEND_SOURCE,BLEND_DESTINATION,textSwitch,sidebarSwitch);
        }
        public Parameters(int backgroundArgb,int blendSource,int blendDestination,
                          ToIntFunction<TextColorMode> textSwitch,ToIntFunction<SidebarColorMode> sidebarSwitch) {
            this.backgroundArgb=backgroundArgb;this.blendSource=blendSource;this.blendDestination=blendDestination;
            this.textSwitch=textSwitch;this.sidebarSwitch=sidebarSwitch;
        }
    }
    private final Host host;
    private final Config config;
    private final Parameters parameters;
    private boolean dirty=true;
    private DrawTask rectangles,mask,background,rendererText,componentText,sidebar;
    private record ShadowMask(DrawTask task,ShadowStyle style){}
    private final List<ShadowMask> shadowMasks=new ArrayList<>();
    private final List<DraggableHudComponent> components=new ArrayList<>();
    private final List<HudRenderer> renderers=new ArrayList<>();

    public HudRenderPipeline(Host host,Config config,Parameters parameters){this.host=host;this.config=config;this.parameters=parameters;}
    public void onClientTick(){setDirty(true);} // dH, not a world draw
    public void onScaleChanged(Float previous,Float current){setDirty(true);}
    public void onHudOverlay(HudOverlayPhaseEvent event) {
        if(!host.originalGuardIsNull())throw new UnsupportedOperationException("jP nonnull ba sentinel not restored");
        if(event.getPhase()==HudOverlayPhaseEvent.Phase.PRE)prepare();
        else if(event.getPhase()==HudOverlayPhaseEvent.Phase.POST)render();
    }
    private DrawTask task(ShaderDispatcher dispatcher) {
        return DrawTask.builder(host.drawing()).dispatcher(dispatcher).ownedBuffers().retained().build();
    }
    private DrawTask textTask(MsdfFontBase font) {
        return DrawTask.builder(host.drawing()).dispatcher(host.msdfDispatcher()).ownedBuffers().retained().texture(font::bind).build();
    }
    private static void release(DrawTask task){if(task!=null)task.getState().release();}
    private void prepare() {
        MsdfFontBase font=host.selectedFont();
        if(!dirty)return;
        dirty=false; // no rollback when release/build/callback fails
        for(ShadowMask shadow:shadowMasks)release(shadow.task());shadowMasks.clear();
        release(rectangles);release(mask);release(background);release(rendererText);release(sidebar);release(componentText);
        rectangles=task(host.rectangleDispatcher());
        background=task(host.roundedDispatcher());
        rendererText=textTask(font);
        mask=task(host.roundedDispatcher());
        sidebar=task(host.roundedDispatcher());
        componentText=textTask(font);
        for(HudRenderer renderer:renderers) {
            renderer.prepareRectangles(rectangles);renderer.prepareMask(mask);
            DrawTask coloredMask=task(host.roundedDispatcher());renderer.prepareMask(coloredMask);
            shadowMasks.add(new ShadowMask(coloredMask,host.shadowStyle(renderer)));
            renderer.prepareBackground(background);renderer.prepareText(rendererText);
        }
        for(DraggableHudComponent component:components) {
            if(!component.isEnabled())continue;
            DraggableComponentProperties properties=component.properties();
            DrawTask coloredMask=task(host.roundedDispatcher());
            shadowMasks.add(new ShadowMask(coloredMask,host.shadowStyle(component)));
            if(!properties.isTitleDisabled()) {
                float titleWidth=font.width(properties.getTitle(),12F);
                // Original centers using the old width, then grows the component.
                font.draw(componentText,properties.getTitle(),12F,
                    component.getX()+component.getWidth()/2F-titleWidth/2F,component.getY()+4F,-1);
                component.setWidth(Math.max(component.getWidth(),titleWidth+8F));
            }
            component.setHeight(Math.max(component.getHeight(),properties.isTitleDisabled()?4F:8F+font.height(12F)));
            if(!properties.isBackgroundDisabled()) {
                background.add(new UiGeometry.RoundedRectangle(component.getX()-1F,component.getY()-1F,
                    component.getWidth()+2F,component.getHeight()+2F,6F,parameters.backgroundArgb,host.drawing().markers));
                mask.add(new UiGeometry.RoundedRectangle(component.getX()-1F,component.getY()-1F,
                    component.getWidth()+2F,component.getHeight()+2F,6F,Color.BLACK.getRGB(),host.drawing().markers));
                coloredMask.add(new UiGeometry.RoundedRectangle(component.getX()-1F,component.getY()-1F,
                    component.getWidth()+2F,component.getHeight()+2F,6F,-1,host.drawing().markers));
            }
            component.setContentX(component.getX()+4F);
            component.setContentY(component.getY()+(properties.isTitleDisabled()?4F:8F+font.height(12F)));
            component.prepareRectangles(rectangles);component.prepareMask(mask);component.prepareMask(coloredMask);
            component.prepareBackground(background);component.prepareText(componentText);
        }
        int first=-1,second=-1;
        if(config.textMode()!=TextColorMode.STATIC&&config.textMode()!=TextColorMode.INDIVIDUAL) {
            switch(parameters.textSwitch.applyAsInt(config.textMode())) {
                case 1:first=config.textColorOne();second=config.textColorTwo();break;
                case 2:first=config.textColorOne();second=new Color(config.textColorOne()).darker().darker().getRGB();break;
                default:break;
            }
            rendererText.add(new AnimatedGradientCommand(first,second,1F,0F,false,host.drawing()));
        }
        if(config.sidebarMode()==SidebarColorMode.SYNC&&config.textMode()!=TextColorMode.STATIC&&config.textMode()!=TextColorMode.INDIVIDUAL) {
            sidebar.add(new AnimatedGradientCommand(first,second,1F,0F,false,host.drawing()));
        } else if(config.sidebarMode()!=SidebarColorMode.SYNC&&config.sidebarMode()!=SidebarColorMode.STATIC&&config.textMode()!=TextColorMode.INDIVIDUAL) {
            // Original guard reads TEXT INDIVIDUAL here. Preserve it.
            switch(parameters.sidebarSwitch.applyAsInt(config.sidebarMode())) {
                case 1:first=config.sidebarColorOne();second=config.sidebarColorTwo();break;
                case 2:first=config.sidebarColorOne();second=new Color(config.sidebarColorOne()).darker().darker().getRGB();break;
                default:break;
            }
            sidebar.add(new AnimatedGradientCommand(first,second,1F,0F,false,host.drawing()));
        }
    }
    private void render() {
        host.bindMainFramebuffer(true);host.disableDepth();host.enableBlend();
        host.blendFuncSeparate(parameters.blendSource,parameters.blendDestination,1,0);
        RenderTransformState.setScale(config.scale(),config.scale(),1F);
        rectangles.draw();background.draw();sidebar.draw();componentText.draw();rendererText.draw();
        RenderTransformState.resetScale();
        host.enableBlend();host.fixedFunctionScale(config.scale(),config.scale(),1F);
        for(HudRenderer renderer:renderers)renderer.renderAfterTasks();
        for(DraggableHudComponent component:components)if(component.isEnabled())component.renderAfterTasks();
        host.fixedFunctionScale(1F/config.scale(),1F/config.scale(),1F);host.enableDepth();
    }
    /** dm's flag is deliberately ignored: blur and shadow draw the same at task. */
    public void onOriginalDm(Object event) {
        if(!host.originalGuardIsNull())throw new UnsupportedOperationException("jP.dm nonnull ba sentinel not restored");
        if(mask!=null) {
            RenderTransformState.setScale(config.scale(),config.scale(),1F);
            try{
                if(Boolean.TRUE.equals(event))for(ShadowMask shadow:shadowMasks){if(shadow.style()==null)shadow.task().draw();else shadow.style().render(shadow.task()::draw);}
                else mask.draw();
            }finally{RenderTransformState.resetScale();}
        }
    }
    public void releaseShadowMasks(){for(ShadowMask shadow:shadowMasks)release(shadow.task());shadowMasks.clear();}
    public boolean isDirty(){return dirty;}
    public HudRenderPipeline setDirty(boolean value){dirty=value;return this;}
    public List<DraggableHudComponent> getComponents(){return components;}
    public List<HudRenderer> getRenderers(){return renderers;}
    public DrawTask getRectangles(){return rectangles;}public DrawTask getMask(){return mask;}
    public DrawTask getBackground(){return background;}public DrawTask getRendererText(){return rendererText;}
    public DrawTask getComponentText(){return componentText;}public DrawTask getSidebar(){return sidebar;}
}
