package moon.visual.gui;
import moon.module.visual.clickgui.ClickGuiSettings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import moon.module.visual.EffectsModule;
import moon.visual.render.*;

/** kA both captured kz sentinel paths. Actual module rows and setting factory, not placeholder children.
 * Previous-frame heights, distinct 4/5 gaps, post-draw scrolling/drag and no-finally order are intentional.
 * Five encrypted integers remain explicit inputs; this original class stays PARTIAL.
 */
public final class CategoryPanel extends ClickGuiWidget implements ModuleRowWidget.Category {
    public interface Category { String displayName();String icon(); }
    public interface ScaledScreen { int height(); }
    /** Live jK A setting; ClickGuiSettings implements this alongside l9 Config. */
    public interface StyleConfig extends ClickGuiWidget.Config { boolean roundedStyle(); }
    public interface Host extends ClickGuiWidget.Host,ModuleRowWidget.Host {
        @Override SettingWidgetFactory.Drawing drawing();
        @Override SettingWidget.Theme uiColor(int argb);
        List<? extends ModuleRowWidget.Module> modules(Category category);
        default boolean roundedStyle(Config config) { return ((StyleConfig)config).roundedStyle(); }
        ScaledScreen scaledScreen();
        int pollWheel();
        MsdfFontBase materialIcons();
        /** com.v.q() and com.v.x(), respectively. */
        int categoryBackground(Object theme);
        int categoryMask(Object theme);
        StencilRenderUtil stencil();
        /** Original moom.kY.l() boundary at the end of EACH traversal. */
        void enableBlend();
        EffectsModule effectsModule();
    }
    public static final class Parameters {
        public final int overflowMargin,unusedBackgroundValue,layoutMargin,negativeWheelStep,positiveWheelStep;
        public Parameters(int overflowMargin,int unusedBackgroundValue,int layoutMargin,int negativeWheelStep,int positiveWheelStep) {
            this.overflowMargin=overflowMargin;this.unusedBackgroundValue=unusedBackgroundValue;this.layoutMargin=layoutMargin;
            this.negativeWheelStep=negativeWheelStep;this.positiveWheelStep=positiveWheelStep;
        }
    }
    private static EffectsModule cachedEffects;
    private final Host panelHost;
    private final Category category;
    private final List<? extends ModuleRowWidget.Module> modules;
    private final List<ModuleRowWidget> rows=new ArrayList<>();
    private final FrameAnimation scrollAnimation;
    private final GroupSettingWidget.Factory factory;
    private final Parameters parameters;
    private int scrollTarget;

    public CategoryPanel(Host host,Category category,float x,float y,float width,float height,
                         GroupSettingWidget.Factory factory,Parameters parameters) {
        super(host);panelHost=host;
        boolean captured=getSentinel();
        scrollAnimation=new FrameAnimation(host::framesPerSecond,host.animationFpsFloor());
        this.category=category;modules=host.modules(category);this.factory=factory;this.parameters=parameters;
        this.x=x;this.y=y;this.width=width;this.height=height;
        if(host.markers().getMarker()!=null) setSentinel(!captured);
    }
    @Override public void initialize() {
        boolean abnormal=inverseSentinel();ensureConfig();rows.clear();float childY=y+30F;
        for(ModuleRowWidget.Module module:modules) {
            ModuleRowWidget row=new ModuleRowWidget(panelHost,module,this,x+5F,childY,width-10F,22F,factory);
            row.setConfig(config);
            int color=config.colorArgb();
            if(!abnormal) { row.setTheme(panelHost.uiColor(color));rows.add(row); }
            // Nondefault 125->192 carries the color integer into the boolean field.
            row.setRoundedStyle(abnormal?(color&1)!=0:panelHost.roundedStyle(config));
            row.initialize();childY+=row.getHeight();
            if(abnormal) break;
        }
        rows.sort(Comparator.comparingDouble(row->row.getModule().displayName().charAt(0)));
    }
    @Override public void update(int mouseX,int mouseY) {
        ensureConfig();ScaledScreen captured=panelHost.scaledScreen();
        layoutRows();drawBackground(true);drawHeader();
        boolean normal=getSentinel();
        panelHost.stencil().beginWrite(false);drawBackground(false);panelHost.stencil().beginRead(true);
        visitRows(mouseX,mouseY,false);panelHost.stencil().end();drawOutline();visitRows(mouseX,mouseY,true);
        boolean hit=hitTest(mouseX,mouseY,x,y+25F,width,height-50F);
        int selector=hit?1:0;
        if(normal) {
            if(hit) pollWheel();
            selector=contentHeight()+25F>captured.height()-parameters.overflowMargin?1:0;
        }
        int lower=Math.min((int)(-contentHeight()+height-25F),0);
        scrollTarget=scrollTarget<lower?lower:Math.min(scrollTarget,0);
        // Nondefault branch carries the hit result rather than the scroll offset.
        scrollAnimation.update(normal?(selector!=0?scrollTarget:0):(float)selector,.4000000059604645D);
        super.update(mouseX,mouseY);
        if(!normal) panelHost.markers().setMarker("omCWh");
    }
    private void layoutRows() {
        boolean normal=getSentinel();
        ScaledScreen screen=panelHost.scaledScreen();float childX=x+3F,childY=y+25F+scrollAnimation.get(),total=0;
        boolean rounded=panelHost.roundedStyle(config);int gap=normal?(rounded?4:0):(rounded?1:0);
        if(!rounded) childX-=3F;
        for(ModuleRowWidget row:rows) {
            row.setX(childX);
            // 154:ifeq200 carries childY into setWidth, then continues at207.
            if(normal) row.setTargetY(childY);
            row.setWidth(normal?(rounded?width-6F:width):childY);row.setRoundedStyle(rounded);
            row.setSnapChildren(Math.round((double)scrollAnimation.get()*100D)/100D!=scrollTarget);
            childY+=row.getHeight()+gap;total+=row.getHeight()+gap;
            if(!normal) break;
        }
        height=Math.min(26F+total,screen.height()-parameters.layoutMargin);
    }
    public float contentHeight() {
        boolean abnormal=inverseSentinel();ensureConfig();float total=0;
        boolean rounded=panelHost.roundedStyle(config);
        for(ModuleRowWidget row:rows) {
            if(abnormal) return total;
            total+=row.getHeight()+(rounded?5:0);
        }
        return total;
    }
    private void pollWheel() {
        boolean normal=getSentinel();int wheel=panelHost.pollWheel();
        if(normal&&wheel==0) return;
        // Nondefault 35->50 and 64->90 carry the raw wheel as the increment.
        scrollTarget+=normal?(wheel>0?parameters.positiveWheelStep:parameters.negativeWheelStep):wheel;
    }
    private void visitRows(int mouseX,int mouseY,boolean overlay) {
        boolean abnormal=inverseSentinel();
        for(ModuleRowWidget row:rows) {
            if(abnormal) return;
            if(overlay) row.overlay(mouseX,mouseY);else row.update(mouseX,mouseY);
        }
        panelHost.enableBlend();
    }
    private void drawBackground(boolean full) {
        ensureConfig();int unused=parameters.unusedBackgroundValue;
        DrawTask task=panelHost.drawing().roundedTask();
        if(full) task.add(panelHost.drawing().rounded(x,y,width,height+.5F,10F,panelHost.categoryBackground(themeColor)));
        task.add(panelHost.drawing().rounded(x,y+25F,width,height-25F,10F,panelHost.categoryBackground(themeColor),false,false,true,true,false));
        task.draw();
    }
    private void drawHeader() {
        MsdfFontBase icons=panelHost.materialIcons();DrawTask title=panelHost.drawing().msdfTask(font),icon=panelHost.drawing().msdfTask(icons);
        icons.draw(icon,category.icon(),14F,x+width-icons.width(category.icon(),14F)-7F,y+8F,-1,false);
        font.draw(title,category.displayName(),12F,x+8F,y+7F,-1,false);title.draw();icon.draw();
    }
    private void drawOutline() {
        DrawTask task=panelHost.drawing().outlineTask();
        task.add(panelHost.drawing().outline(x-1.5F,y-1.5F,width+3F,height+3F,10F,1F,panelHost.categoryBackground(themeColor)));task.draw();
    }
    @Override public void onOriginalDm(Object event,float partialTicks) {
        ensureConfig();DrawTask task=panelHost.drawing().roundedTask();
        task.add(panelHost.drawing().rounded(x,y,width,height+.5F,10F,new ArgbColor(panelHost.categoryMask(themeColor)).withAlpha(.75F).getRGB()));task.draw();
    }
    @Override public void tick() {
        ensureConfig();boolean abnormal=inverseSentinel(),changing=false;
        for(ModuleRowWidget row:rows) {
            boolean current=row.hasChangingOverallHeight();
            if(abnormal) { if(current) cachedEffects.requestShadowUpdate();return; }
            if(current) changing=true;
        }
        if(abnormal) return; // Empty list leaves cachedEffects and dragging untouched.
        if(cachedEffects==null) cachedEffects=panelHost.effectsModule();
        if(changing||isDragging()) cachedEffects.requestShadowUpdate();
    }
    @Override public void mouseClicked(int mouseX,int mouseY,int button) {
        boolean normal=getSentinel();
        for(ModuleRowWidget row:rows) { row.mouseClicked(mouseX,mouseY,button);if(!normal) return; }
        if(normal&&hitTest(mouseX,mouseY,x,y,width,25F)) { setDragging(true);setDragOffsetX(x-mouseX);setDragOffsetY(y-mouseY); }
        super.mouseClicked(mouseX,mouseY,button);
    }
    @Override public void mouseReleased(int mouseX,int mouseY,int button) {
        boolean abnormal=inverseSentinel();
        for(ModuleRowWidget row:rows) { row.mouseReleased(mouseX,mouseY,button);if(abnormal) return; }
        setDragging(false);super.mouseReleased(mouseX,mouseY,button);
    }
    @Override public void keyTyped(char character,int key) {
        boolean normal=getSentinel();
        for(ModuleRowWidget row:rows) { row.keyTyped(character,key);if(!normal) break; }
    }
    @Override public void refreshColor() { rows.forEach(ModuleRowWidget::refreshColor);super.refreshColor(); }
    public Category getCategory() { return category; }
    public List<? extends ModuleRowWidget.Module> getModules() { return modules; }
    public List<ModuleRowWidget> getRows() { return rows; }
    public FrameAnimation getScrollAnimation() { return scrollAnimation; }
    public int getScrollTarget() { return scrollTarget; }
}
