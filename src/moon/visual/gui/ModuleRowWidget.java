package moon.visual.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import moon.visual.render.*;

/** kc with per-method captured kb guards, constructor marker and stack-carry paths. Module and category are actual game integration boundaries.
 * Header rendering/input intentionally uses raw targetY, while inherited animatedY still advances.
 * The category owns outer clipping; kc adds no header shader clip of its own.
 */
public final class ModuleRowWidget extends SettingWidget {
    public interface Module {
        List<? extends SettingValue<?>> settings();
        String displayName();
        default String hudSuffix(){return "";}
        default String bindId(){return displayName().toLowerCase(java.util.Locale.ROOT);}
        boolean enabled();
        void toggle();
    }
    public interface Category { float getY();float getHeight(); }
    public interface Host extends SettingWidget.Host {
        @Override ChoiceSettingWidget.Drawing drawing();
        /** com.v.z(), distinct from setting track u(). */
        int moduleBackground(Theme theme);
        /** com.v.b(), distinct from regular text v(). */
        int enabledText(Theme theme);
        float scaledScreenHeight();
        /** Capture l9.a(false) once per traversal, then call k9.a() before and after each child. */
        BooleanSupplier childAnimationState();
    }
    private final Host rowHost;
    private final Module module;
    private final Category category;
    private final GroupSettingWidget.Factory factory;
    private final MsdfFontBase titleFont;
    private final FrameAnimation enabledAnimation,heightAnimation;
    private final List<SettingWidget> children=new ArrayList<>();
    private boolean expanded,positionChanging,snapChildren,roundedStyle;
    private float targetHeight;
    private int originalUnusedIndex;

    public ModuleRowWidget(Host host,Module module,Category category,float x,float y,float width,float height,
                           GroupSettingWidget.Factory factory) {
        this(getSentinel(),host,module,category,x,y,width,height,factory);
    }
    // Delegation evaluates the guard before SettingWidget's constructor callbacks.
    private ModuleRowWidget(String captured,Host host,Module module,Category category,float x,float y,float width,float height,
                            GroupSettingWidget.Factory factory) {
        super(host);rowHost=host;titleFont=host.interMedium();
        enabledAnimation=newAnimation();heightAnimation=newAnimation();
        this.module=module;this.category=category;this.factory=factory;
        this.x=x;targetY=y;this.width=width;this.height=height;
        if(captured==null) host.markers().setMarker("pGclLb");
    }
    @Override public void initialize() {
        List<? extends SettingValue<?>> values=module.settings();String captured=getSentinel();float childY=getTargetY()+20F;
        for(SettingValue<?> value:values) {
            if(captured==null) return;
            if(value.isGrouped()) continue;
            SettingWidget child=factory.create(value,getX(),childY,getWidth(),GroupSettingWidget.originalInitialHeight(value.getType()));
            child.setConfig(config);child.setTheme(host.uiColor(config.colorArgb()));
            children.add(child);child.initialize();childY+=child.getHeight()+3F;
        }
        super.initialize();
    }
    @Override public void refreshColor() { children.forEach(SettingWidget::refreshColor);super.refreshColor(); }
    @Override public void update(int mouseX,int mouseY) {
        String captured=getSentinel();drawBackground();drawTitle();layoutChildren();
        if(captured==null||shouldVisitChildren()) visitChildren(mouseX,mouseY,false);
        super.update(mouseX,mouseY);
    }
    @Override public void overlay(int mouseX,int mouseY) {
        String captured=getSentinel();if(captured==null||shouldVisitChildren()) visitChildren(mouseX,mouseY,true);
        super.overlay(mouseX,mouseY);
    }
    private boolean shouldVisitChildren() { return expanded||heightAnimation.get()>(roundedStyle?22.5F:24.5F); }
    private void drawBackground() {
        String captured=getSentinel();
        enabledAnimation.update(module.enabled()?1F:0F,.30000001192092896D);
        DrawTask task=rowHost.drawing().roundedTask();
        boolean rounded=captured!=null&&roundedStyle;
        float bx=rounded?x+1.5F:x,bw=rounded?width-3F:width,bh=rounded?22F:25F,r=rounded?4.5F:0F;
        // Rounded style leaves default smooth=true, flat style explicitly disables it.
        task.add(rowHost.drawing().rounded(bx,targetY,bw,bh,r,rowHost.moduleBackground(theme),true,true,true,true,rounded));
        task.add(rowHost.drawing().rounded(bx,targetY,bw,bh,r,new ArgbColor(theme.accent()).withAlpha(enabledAnimation.get()).getRGB(),true,true,true,true,rounded));
        task.draw();
    }
    private void drawTitle() {
        String captured=getSentinel();
        DrawTask task=rowHost.drawing().msdfTask(titleFont);
        float textWidth=titleFont.width(module.displayName(),9.3F),textY=targetY+(roundedStyle?6.25F:7.75F);
        if(captured!=null&&enabledAnimation.get()<1F) titleFont.draw(task,module.displayName(),9.3F,x+width/2F-textWidth/2F,textY,
            new ArgbColor(theme.text()).withAlpha(1F-enabledAnimation.get()).getRGB(),false,false,.015F);
        titleFont.draw(task,module.displayName(),9.3F,x+width/2F-textWidth/2F,textY,
            new ArgbColor(rowHost.enabledText(theme)).withAlpha(enabledAnimation.get()).getRGB(),false,false,.015F);
        task.draw();
    }
    private void layoutChildren() {
        String captured=getSentinel();
        float childX=getX(),childY=getTargetY()+(roundedStyle?26.5F:29F),total=0F,minimum=roundedStyle?22F:24F;
        int visible=0;
        for(SettingWidget child:children) {
            child.setX(childX);child.setTargetY(childY);
            // kc:125 carries child,width to setPadding; no child width write.
            if(captured==null) child.setPadding(width);
            else {child.setWidth(width);child.setPadding(roundedStyle?3F:6F);}
            // kc:172 carries the MODULE row, not its child, to the animation getter.
            if(captured==null) getYAnimation().set(childY);
            else if(!checkPositionChanging()||snapChildren) child.getYAnimation().set(childY);
            child.setClipX(x);child.setClipY(targetY);child.setClipWidth(width);
            if(captured!=null&&child instanceof GroupSettingWidget) ((GroupSettingWidget)child).setSnapChildren(snapChildren);
            float v=child.getVisibilityAnimation().get();
            childY+=child.getHeight()+5F*v;total+=child.getHeight()+5F*v;
            if(child.isVisible()) visible++;
            if(captured==null) break;
        }
        if(captured!=null&&!roundedStyle&&expanded&&visible>0) total+=4.5F*visibilityAnimation.get();
        targetHeight=expanded?minimum+total:minimum;
        heightAnimation.update(targetHeight,.4000000059604645D);
        if(captured!=null) {
            if(hasChangingChildHeight()&&expanded) {
                setHeight(Math.max(targetHeight,minimum));heightAnimation.set(Math.max(targetHeight,minimum));
            } else setHeight(Math.max(heightAnimation.get(),minimum));
        }
        for(SettingWidget child:children) {
            child.setClipHeight(Math.max(captured!=null&&hasChangingChildHeight()&&expanded?targetHeight:heightAnimation.get(),20F));
            if(captured==null) break;
        }
    }
    /** kc.d(): local motion never commits original z. Aligned or invisible children still snap. */
    public boolean checkPositionChanging() {
        String captured=getSentinel();
        for(SettingWidget child:children) {
            if(captured==null) {child.getYAnimation().set(child.getTargetY());break;}
            if(roundOne(child.getY())!=roundOne(child.getTargetY())&&child.isVisible()) continue;
            child.getYAnimation().set(child.getTargetY());
        }
        return positionChanging;
    }
    public boolean hasChangingChildHeight() {
        String captured=getSentinel();
        for(SettingWidget child:children) {
            if(captured==null) return child instanceof GroupSettingWidget;
            if(child instanceof GroupSettingWidget&&((GroupSettingWidget)child).hasChangingChildHeight()) return true;
            if(roundOne(child.getHeight())!=roundOne(child.getFullHeight())&&(child.isVisible()||child.getHeight()>.1D)) return true;
        }
        return false;
    }
    /** kc.f(): separate category query; fixed22 even in flat style, visible children only, overwrites y. */
    public boolean hasChangingOverallHeight() {
        String captured=getSentinel();float total=0F;
        for(SettingWidget child:children) {
            if(captured==null) break;
            if(child.isVisible()) total+=child.getHeight()+5F*child.getVisibilityAnimation().get();
        }
        targetHeight=expanded?22F+total:22F;
        return roundOne(height)!=roundOne(targetHeight);
    }
    private static double roundOne(float value) { return Math.round((double)value*10D)/10D; }
    /** Expand within the parent layout. Never scale or move children from the screen origin. */
    private void visitChildren(int mouseX,int mouseY,boolean overlay) {
        for(SettingWidget child:children) {
            if(child.getHeight()<=.1F&&!child.isVisible())continue;
            float alpha=RenderTransformState.getAlpha();
            float clipY=child.getClipY(),clipHeight=child.getClipHeight();
            float progress=Math.max(0,Math.min(1,child.getVisibilityAnimation().get()));
            try{
                RenderTransformState.setAlpha(alpha*progress);
                // Reveal new controls downwards inside the space they currently occupy.
                if(!overlay&&progress<.999F){
                    float top=Math.max(clipY,child.getY());
                    float bottom=Math.min(clipY+clipHeight,child.getY()+child.getHeight());
                    child.setClipY(top);child.setClipHeight(Math.max(0,bottom-top));
                }
                if(overlay)child.overlay(mouseX,mouseY);else child.update(mouseX,mouseY);
            }finally{
                child.setClipY(clipY);child.setClipHeight(clipHeight);
                RenderTransformState.setAlpha(alpha);
            }
        }
    }
    @Override public boolean mouseClicked(int mouseX,int mouseY,int button) {
        String captured=getSentinel();
        if(captured==null) return expanded;
        if(expanded) for(SettingWidget child:children) {
            if((hitTest(mouseX,mouseY,x,targetY,width,height)||child instanceof StringSettingWidget||child instanceof BezierSettingWidget)
                &&isExpanded()&&child.mouseClicked(mouseX,mouseY,button)) return true;
        }
        if(hitTest(mouseX,mouseY,x,targetY,width,20F)
            &&hitTest(mouseX,mouseY,x,category.getY()+25F,width,category.getHeight()-25F)) {
            if(button==0) module.toggle();
            else {
                boolean any=children.stream().anyMatch(SettingWidget::isVisible);
                if(!expanded&&!any) return super.mouseClicked(mouseX,mouseY,button);
                expanded=!expanded;
            }
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }
    @Override public void mouseReleased(int mouseX,int mouseY,int button) {
        String captured=getSentinel();for(SettingWidget child:children) {child.mouseReleased(mouseX,mouseY,button);if(captured==null) return;}super.mouseReleased(mouseX,mouseY,button);
    }
    @Override public void keyTyped(char character,int key) {
        String captured=getSentinel();for(SettingWidget child:children) {child.keyTyped(character,key);if(captured==null) return;}super.keyTyped(character,key);
    }
    @Override public boolean usesVisibilityAnimation() { return false; }
    @Override public boolean isVisible() { return true; }
    @Override public MsdfFontBase getFont() { return titleFont; }
    public Module getModule() { return module; }
    public Category getCategory() { return category; }
    public List<SettingWidget> getChildren() { return children; }
    public FrameAnimation getEnabledAnimation() { return enabledAnimation; }
    public FrameAnimation getHeightAnimation() { return heightAnimation; }
    public float getTargetHeight() { return targetHeight; }
    public boolean isExpanded() { return expanded; }
    public boolean isPositionChanging() { return positionChanging; }
    public int getOriginalUnusedIndex() { return originalUnusedIndex; }
    public boolean isSnapChildren() { return snapChildren; }
    public boolean isRoundedStyle() { return roundedStyle; }
    public ModuleRowWidget setSnapChildren(boolean value) { snapChildren=value;return this; }
    public ModuleRowWidget setRoundedStyle(boolean value) { roundedStyle=value;return this; }
}
