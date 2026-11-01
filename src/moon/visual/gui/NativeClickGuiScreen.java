package moon.visual.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import moon.module.visual.ClickGuiModule;
import moon.module.visual.EffectsModule;
import moon.visual.render.RenderTransformState;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** Material controller for l9/DEFAULT pj; pk screen policy uses the same live setting adapters.
 * Modern screen/category geometry follows pk/ns/nr; module setting widgets remain destination adapters.
 * MCP GuiScreen super calls, input codes and module lookup remain explicit host boundaries.
 * Original animation durations and blend integers are mandatory parameters, not guessed constants.
 */
public final class NativeClickGuiScreen implements ClickGuiModule.NativeScreen,ModalConfiguration.Screen {
    /** Only the bs entries actually used by mo/mp; k_ literal arms establish 0/4/3. */
    public enum Easing {
        LINEAR(0),EASE_OUT_BACK(4),EASE_IN_BACK(3);
        final int arm;Easing(int arm){this.arm=arm;}
    }
    public interface Config extends ClickGuiWidget.Config {
        boolean openingAnimation();boolean closingAnimation();
        Easing openingEasing();Easing closingEasing();
        default float modernScale(){return 1F;}
        default boolean modernCenter(){return false;}
        default boolean modernPostProcessing(){return false;}
    }
    /** Renderer/module services shared by the plain controller and MCP GuiScreen adapter. */
    public interface Services {
        /** Actual kz/kA host, sharing the same config and module/setting tree. */
        CategoryPanel.Host categoryHost();
        default int guiWidth(){return 800;}
        default void renderModernBlur(Runnable mask){}
        List<? extends CategoryPanel.Category> categories();
        boolean isScriptsCategory(CategoryPanel.Category category);
        GroupSettingWidget.Factory settingFactory();
        CategoryPanel.Parameters categoryParameters();
        MarkerHooks markers();
        ModalOverlay.Scheme modalScheme();
        EffectsModule effectsModule();
        boolean effectsModuleEnabled(EffectsModule module);
        boolean isOriginalDmShadow(Object event);
        void enableBlend();void blendFunc(int source,int destination);
        /** Destination input bridge; controller still consumes sign-only wheel via categoryHost. */
        default void mouseWheel(int delta){}
        /** Destination-only ownership boundary for synchronous temporary GUI draws. */
        default void renderFrame(Runnable drawing){drawing.run();}
    }
    public interface Host extends Services {
        void enableRepeatEvents(boolean enabled);
        void displayNoScreen();Object currentScreen();void focusGame();
        void parentInitialize();void parentDraw(int x,int y,float partialTicks);
        void parentMouseClicked(int x,int y,int button);void parentMouseReleased(int x,int y,int button);
        void parentKeyTyped(char character,int legacyKey);
    }
    public static final class Parameters {
        public final long fadeLinear,fadeOutBack,fadeInBack,scaleLinear,scaleOutBack,scaleInBack;
        public final int blendSource,blendDestination;
        public Parameters(long fadeLinear,long fadeOutBack,long fadeInBack,long scaleLinear,long scaleOutBack,long scaleInBack,
                          int blendSource,int blendDestination) {
            this.fadeLinear=fadeLinear;this.fadeOutBack=fadeOutBack;this.fadeInBack=fadeInBack;
            this.scaleLinear=scaleLinear;this.scaleOutBack=scaleOutBack;this.scaleInBack=scaleInBack;
            this.blendSource=blendSource;this.blendDestination=blendDestination;
        }
    }
    /** mT is shared outside l9. The default adapter keeps the normal nonnull
     * policy; a runtime host can supply its actual shared array sentinel. */
    public interface Guard {
        boolean isNonNull();
        void setNonNull(int length);
    }
    private static final Guard NORMAL_GUARD=new Guard() {
        public boolean isNonNull(){return true;}
        public void setNonNull(int length){}
    };
    private final Guard guard;
    private final Host host;
    private final Config config;
    private final Parameters parameters;
    private final List<ClickGuiWidget> widgets=new ArrayList<>();
    private final Map<Easing,TimedAnimation> fadeAnimations=new HashMap<>(),scaleAnimations=new HashMap<>();
    private ModalOverlay modal;
    /** nw is exactly a deferred nt.a(mouseX, mouseY) call, not a second modal. */
    public record DeferredOverlay(java.util.function.BiConsumer<Integer,Integer> renderer,int mouseX,int mouseY){
        public void draw(){renderer.accept(mouseX,mouseY);}
    }
    private DeferredOverlay deferredOverlay;
    public void deferOverlay(java.util.function.BiConsumer<Integer,Integer> renderer,int mouseX,int mouseY){
        deferredOverlay=new DeferredOverlay(java.util.Objects.requireNonNull(renderer),mouseX,mouseY);
    }
    private boolean closing;
    private boolean modern;
    private EffectsModule effects;
    private CategoryPanel scriptsPanel;

    public NativeClickGuiScreen(Host host,Config config,Parameters parameters,LongSupplier clock) {
        this(host,config,parameters,clock,NORMAL_GUARD);
    }
    public NativeClickGuiScreen(Host host,Config config,Parameters parameters,LongSupplier clock,boolean modern) {
        this(host,config,parameters,clock,NORMAL_GUARD);this.modern=modern;
        if(modern){
            // pk.L candidate 95673785565422 decodes its nY duration to 150 ms.
            fadeAnimations.put(Easing.LINEAR,new TimedAnimation(150L,0,clock,host.markers()));
            scaleAnimations.put(Easing.LINEAR,new TimedAnimation(150L,0,clock,host.markers()));
        }
    }
    public NativeClickGuiScreen(Host host,Config config,Parameters parameters,LongSupplier clock,Guard guard) {
        this.host=host;this.parameters=parameters;this.guard=guard;
        addAnimations(fadeAnimations,parameters.fadeLinear,parameters.fadeOutBack,parameters.fadeInBack,clock);
        guard.isNonNull(); // original read35 precedes mp allocation; result is unused
        addAnimations(scaleAnimations,parameters.scaleLinear,parameters.scaleOutBack,parameters.scaleInBack,clock);
        this.config=config;
        if(host.markers().getMarker()!=null) guard.setNonNull(2);
    }
    private void addAnimations(Map<Easing,TimedAnimation> map,long linear,long outBack,long inBack,LongSupplier clock) {
        map.put(Easing.LINEAR,new TimedAnimation(linear,0,clock,host.markers()));
        map.put(Easing.EASE_OUT_BACK,new TimedAnimation(outBack,4,clock,host.markers()));
        map.put(Easing.EASE_IN_BACK,new TimedAnimation(inBack,3,clock,host.markers()));
    }
    /** Original a(): one-time tree construction, called by jK BEFORE display/GuiScreen init.
     * Repeated calls rebuild without duplicating categories.
     */
    @Override public void initialize() {
        widgets.clear();scriptsPanel=null;
        float x=modern?5F:10F,y=x;boolean normal=guard.isNonNull();
        for(CategoryPanel.Category category:host.categories()) {
            float initialHeight=modern?Math.max(26F,host.categoryHost().scaledScreen().height()-100F):350F;
            CategoryPanel panel=new CategoryPanel(host.categoryHost(),category,x,y,modern?140F:153F,initialHeight,host.settingFactory(),host.categoryParameters());
            panel.setModern(modern);
            panel.setConfig(config);panel.setThemeColor(host.categoryHost().uiColor(config.colorArgb()));widgets.add(panel);
            panel.initialize();x+=modern?145F:157F;
            if(!normal) return;
            if(host.isScriptsCategory(category)) scriptsPanel=panel;
        }
    }
    /** Original c(): GuiScreen init/reopen/resize lifecycle, distinct from a(). Does not clear modal. */
    public void initializeScreen() {
        host.parentInitialize();boolean normal=guard.isNonNull();closing=false;
        onColorSettingChanged();
        if(!normal) { reset(fadeAnimations.values(),1F,true);return; }
        if(modern||config.openingAnimation()) { reset(scaleAnimations.values(),modern?0F:.5F,false);reset(fadeAnimations.values(),0F,false); }
        else { reset(scaleAnimations.values(),1F,true);reset(fadeAnimations.values(),1F,true); }
    }
    private void reset(Collection<TimedAnimation> animations,float value,boolean blocked) {
        boolean normal=guard.isNonNull();
        for(TimedAnimation animation:animations) {
            if(!normal) { animation.resetTo(value);return; }
            if(blocked) { animation.set(value);animation.block(); }
            else animation.resetTo(value);
        }
    }
    private void advance(Collection<TimedAnimation> animations,boolean forward) {
        boolean normal=guard.isNonNull();
        for(TimedAnimation animation:animations) {
            animation.update(0F,1F,forward);
            if(!normal) return;
        }
    }
    private void advanceAnimations(boolean normal) {
        advance(scaleAnimations.values(),normal?!closing:closing);
        advance(fadeAnimations.values(),normal?!closing:closing);
    }
    public TimedAnimation animation(boolean fade) {
        boolean normal=guard.isNonNull();
        if(!normal) return closing?fadeAnimations.get(config.openingEasing()):scaleAnimations.get(config.openingEasing());
        // Map receiver precedes the setting callback in the original.
        Map<Easing,TimedAnimation> map=fade?fadeAnimations:scaleAnimations;
        Easing key=modern?Easing.LINEAR:closing?config.closingEasing():config.openingEasing();
        return map.get(key);
    }
    private EffectsModule effects() { if(effects==null) effects=host.effectsModule();return effects; }
    public void drawScreen(int mouseX,int mouseY,float partialTicks) {
        boolean normal=guard.isNonNull();
        // null mT carries C straight to the Boolean cast80, before lookup.
        if(!normal) { ((Boolean)(Object)effects).booleanValue();return; }
        // ClickGUI is excluded from Effects masks: drawing owns animation updates.
        advanceAnimations(normal);
        float scale=animation(false).get(),alpha=animation(true).get();
        int inputX=logicalX(mouseX),inputY=logicalY(mouseY);
        if(modern){
            float spacing=145F,total=widgets.size()*140F+(widgets.size()-1)*5F;
            float start=config.modernCenter()?host.guiWidth()/config.modernScale()/2F-total/2F:5F;
            for(int i=0;i<widgets.size();i++){
                CategoryPanel panel=(CategoryPanel)widgets.get(i);panel.setX(start+i*spacing);panel.setY(5F);
                panel.setInteractionBlocked(modal!=null);
                panel.setViewportHeight(host.categoryHost().scaledScreen().height()/config.modernScale());panel.prepareLayout();
            }
        }
        applyTransform(scale,alpha);
        if(modern&&config.modernPostProcessing())host.renderModernBlur(()->{
            for(ClickGuiWidget widget:widgets)((CategoryPanel)widget).drawModernMask();
        });
        for(ClickGuiWidget widget:widgets) {
            host.enableBlend();host.blendFunc(parameters.blendSource,parameters.blendDestination);widget.update(inputX,inputY);
        }
        if(modern&&deferredOverlay!=null){
            DeferredOverlay pending=deferredOverlay;deferredOverlay=null;
            try{pending.draw();}finally{resetTransform();}
        }else resetTransform();
        // Original dcmpg treats NaN as below the threshold.
        if(!(animation(false).get()>=.5D)&&closing) closeGameScreen();
        if(modal!=null) modal.update(mouseX,mouseY);
        host.parentDraw(mouseX,mouseY,partialTicks);
    }
    private void applyTransform(float scale,float alpha) {
        float translate=(1F-scale)/2F;
        float zoom=modern?config.modernScale():1F;
        RenderTransformState.setScale(scale*zoom,scale*zoom,1F);RenderTransformState.setTranslation(translate,translate,0F);RenderTransformState.setAlpha(alpha);
    }
    private static void resetTransform() {
        RenderTransformState.resetAlpha();RenderTransformState.resetScale();RenderTransformState.resetTranslation();
    }
    private void closeGameScreen() { host.displayNoScreen();if(host.currentScreen()==null) host.focusGame(); }
    public void mouseClicked(int mouseX,int mouseY,int button) {
        boolean normal=guard.isNonNull();
        if(normal&&modal!=null) { modal.mouseClicked(mouseX,mouseY,button);return; }
        for(ClickGuiWidget widget:widgets) {
            widget.mouseClicked(logicalX(mouseX),logicalY(mouseY),button);if(!normal) return;
        }
        host.parentMouseClicked(mouseX,mouseY,button);
    }
    public void mouseReleased(int mouseX,int mouseY,int button) {
        boolean normal=guard.isNonNull();
        if(normal&&modal!=null) { modal.mouseReleased(mouseX,mouseY,button);return; }
        for(ClickGuiWidget widget:widgets) {
            widget.mouseReleased(logicalX(mouseX),logicalY(mouseY),button);if(!normal) return;
        }
        host.parentMouseReleased(mouseX,mouseY,button);
    }
    public void keyTyped(char character,int legacyKey) {
        boolean normal=guard.isNonNull();
        if(legacyKey==1) {
            if(!normal) { ((Boolean)(Object)modal).booleanValue();return; }
            if(modal!=null) { setModal(null);return; }
            if(modern||config.closingAnimation()) {
                if(!closing) {
                    closing=true;reset(fadeAnimations.values(),1F,false);reset(scaleAnimations.values(),1F,false);host.enableRepeatEvents(false);
                }
            } else { closeGameScreen();host.enableRepeatEvents(false); }
            return;
        }
        if(normal&&modal!=null) { modal.keyTyped(character,legacyKey);return; }
        for(ClickGuiWidget widget:widgets) {
            widget.keyTyped(character,legacyKey);if(!normal) return;
        }
        host.parentKeyTyped(character,legacyKey);
    }
    /** Inverse of the visible shader transform, including animation. Modals remain in screen coordinates. */
    public int logicalX(int x){return modern?(int)((x-(1F-animation(false).get())*.5F*host.guiWidth())/Math.max(.001F,animation(false).get()*config.modernScale())):x;}
    public int logicalY(int y){return modern?(int)((y-(1F-animation(false).get())*.5F*host.categoryHost().scaledScreen().height())/Math.max(.001F,animation(false).get()*config.modernScale())):y;}
    public boolean isModern(){return modern;}
    public boolean doesGuiPauseGame() { return false; }
    @Override public void onOriginalDl() {
        boolean normal=guard.isNonNull();
        if(!normal) effects.requestShadowUpdate();
        else if(!animation(true).isFinished()||!animation(false).isFinished()) effects().requestShadowUpdate();
        for(ClickGuiWidget widget:widgets) { widget.tick();if(!normal) return; }
    }
    @Override public void onOriginalDm(Object event) {
        // User policy: no ClickGUI shadow, independently of HUD Effects.
    }
    @Override public void onColorSettingChanged() { widgets.forEach(ClickGuiWidget::refreshColor); }
    @Override public ModalOverlay.Scheme modalScheme() { return host.modalScheme(); }
    @Override public void setModal(ModalOverlay overlay) {
        boolean normal=guard.isNonNull();modal=overlay;
        if(overlay!=null)overlay.setModern(modern);
        if(!normal||overlay!=null) overlay.initialize();
    }
    public ModalOverlay getModal() { return modal; }
    public List<ClickGuiWidget> getWidgets() { return widgets; }
    public Map<Easing,TimedAnimation> getFadeAnimations() { return fadeAnimations; }
    public Map<Easing,TimedAnimation> getScaleAnimations() { return scaleAnimations; }
    public CategoryPanel getScriptsPanel() { return scriptsPanel; }
    public Config getConfig() { return config; }
    public boolean isClosing() { return closing; }
    public EffectsModule getCachedEffects() { return effects; }
}
