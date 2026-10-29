package moon.visual.gui;

import java.util.ArrayList;
import java.util.List;
import moon.visual.render.*;

/** kn with per-method captured ko guards and original first-child/stack-carry behavior. Child construction is shared with the screen through Factory.
 * Layout intentionally precedes child updates; repeated initialize appends rather than clears.
 * Original w/x have no nonzero assignments. The unused header animation remains externally live.
 */
public final class GroupSettingWidget extends SettingWidget {
    public interface Host extends SettingWidget.Host {
        float scaledScreenHeight();
        /** l9.a(false).a(): sample once for each child update/overlay traversal. */
        boolean childAnimationsActive();
    }
    public interface Factory {
        SettingWidget create(SettingValue<?> setting,float x,float y,float width,float initialHeight);
    }
    private final SettingGroup setting;
    private final Host groupHost;
    private final Factory factory;
    private final FrameAnimation headerFade,heightAnimation;
    private final List<SettingWidget> children=new ArrayList<>();
    private boolean expanded,positionChanging,snapChildren;
    private int originalUnusedIndex;

    public GroupSettingWidget(Host host,SettingGroup setting,float x,float y,float width,float height,Factory factory) {
        super(host);groupHost=host;this.setting=setting;this.factory=factory;
        headerFade=newAnimation();heightAnimation=newAnimation();
        this.x=x;targetY=y;this.width=width;this.height=height;
    }
    /** kn.a() constructor descriptors, not guesses from current animated widget heights. */
    public static float originalInitialHeight(SettingValue.Type type) {
        switch(type) {
            case BOOLEAN:return 15F;
            case COLOR:case ENUM:case MULTIPLE_CHOICE:case MODE:case BUTTON:case MODULAR_LIST:return 25F;
            case FLOAT:case INTEGER:return 21F;
            case STRING:return 32F;
            case GROUP:return 23F;
            case BEZIER:return 14F;
            default:throw new IncompatibleClassChangeError();
        }
    }
    @Override public void initialize() {
        List<SettingValue<?>> values=setting.get();int captured=getComponentSentinel();float childY=getTargetY()+20F;
        for(SettingValue<?> value:values) {
            if(captured!=0) return;
            SettingWidget child=factory.create(value,getX(),childY,getWidth(),originalInitialHeight(value.getType()));
            children.add(child);child.initialize();childY+=child.getHeight()+3F;
        }
        super.initialize();
    }
    @Override public void refreshColor() {
        children.forEach(SettingWidget::refreshColor);super.refreshColor();
    }
    @Override public void update(int mouseX,int mouseY) {
        int captured=inverseComponentSentinel();ensureConfig();drawHeaderBackground();drawHeaderText();layoutChildren();
        if(captured==0||expanded||heightAnimation.get()>20.5D) visitChildren(mouseX,mouseY,false);
        super.update(mouseX,mouseY);
    }
    @Override public void overlay(int mouseX,int mouseY) {
        int captured=getComponentSentinel();if(captured!=0||expanded||heightAnimation.get()>20.5D) visitChildren(mouseX,mouseY,true);
        super.overlay(mouseX,mouseY);
    }
    private void drawHeaderBackground() {
        Drawing drawing=host.drawing();DrawTaskState.Command clip=drawing.clip(clipX,clipY,clipWidth,clipHeight);
        DrawTask task=drawing.roundedTask();
        task.add(drawing.rounded(x+padding,getY(),width-padding*2F,20F,5F,theme.track()));
        task.add(clip);task.draw();
    }
    private void drawHeaderText() {
        Drawing drawing=host.drawing();DrawTaskState.Command clip=drawing.clip(clipX,clipY,clipWidth,clipHeight);
        String count=""+setting.get().size();float countWidth=font.width(count,8.039999F);
        DrawTask task=drawing.msdfTask(font);
        int color=new ArgbColor(theme.text()).withAlpha(1F-headerFade.get()).getRGB();
        font.draw(task,setting.getDisplayName(),8.039999F,x+padding+5F,getY()+6F,color,false,false,.015F);
        font.draw(task,count,8.039999F,x+width-padding-countWidth-5F,getY()+6F,
            new ArgbColor(theme.text()).withAlpha(1F-headerFade.get()).getRGB(),false,false,.015F);
        task.add(clip);task.draw();
    }
    private void layoutChildren() {
        float childX=getX(),childY=getY()+24.5F;int captured=inverseComponentSentinel();float total=0F;
        for(SettingWidget child:children) {
            child.setX(childX+padding);child.setTargetY(childY);child.setWidth(width-padding*2F);
            // The nondefault branch carries this, so it snaps the GROUP animation.
            if(captured==0) getYAnimation().set(childY);
            else if(!checkPositionChanging()||snapChildren) child.getYAnimation().set(childY);
            child.setClipX(clipX);child.setClipY(clipY);child.setClipWidth(clipWidth);
            if(captured==0) break;
            if(child instanceof GroupSettingWidget) ((GroupSettingWidget)child).setSnapChildren(snapChildren);
            childY+=child.getHeight()+5F*child.getVisibilityAnimation().get();
            total+=child.getHeight()+5F*child.getVisibilityAnimation().get();
        }
        fullHeight=expanded?20F+total:20F;
        visibilityAnimation.update(isVisible()?1F:0F,.4000000059604645D);
        heightAnimation.update(fullHeight,.4000000059604645D);
        if(captured!=0) {
            if(hasChangingChildHeight()&&expanded) {
                setHeight(Math.max(fullHeight,20F)*visibilityAnimation.get());
                heightAnimation.set(Math.max(fullHeight,20F));
            } else setHeight(Math.max(heightAnimation.get(),20F)*visibilityAnimation.get());
        }
        float bottom=Math.min(clipHeight-(clipY+clipHeight-(getY()+getHeight())),clipHeight);
        for(SettingWidget child:children) {
            child.setClipHeight(Math.max(bottom,20F));
            if(captured==0) break;
        }
    }
    /** kn.d(): return original w, not the locally detected motion flag. It also snaps aligned/hidden children. */
    private boolean checkPositionChanging() {
        int captured=inverseComponentSentinel();boolean moving=false;
        for(SettingWidget child:children) {
            if(captured==0) {child.getYAnimation().set(child.getTargetY());return false;}
            if(roundOne(child.getY())!=roundOne(child.getTargetY())&&child.isVisible()) moving=true;
            else child.getYAnimation().set(child.getTargetY());
        }
        if(captured==0) return false;
        if(!moving) positionChanging=false;
        return positionChanging;
    }
    public boolean hasChangingChildHeight() {
        int captured=getComponentSentinel();boolean changing=false;
        for(SettingWidget child:children) {
            double actual=roundOne(child.getHeight()),target=roundOne(child.getFullHeight());
            int comparison=actual>target?1:actual==target?0:-1; // JVM dcmpl.
            if(captured!=0) return (comparison&1)!=0;
            if(comparison!=0&&(child.isVisible()||child.getHeight()>.1D)) changing=true;
        }
        return changing;
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
        int captured=inverseComponentSentinel();boolean hit=hitTest(mouseX,mouseY,x,targetY,width,20F);
        if(captured==0) return hit;
        if(hit) expanded=!expanded;
        if(expanded) {
            for(SettingWidget child:children) {
                if((hitTest(mouseX,mouseY,x,targetY,width,height)||child instanceof StringSettingWidget||child instanceof BezierSettingWidget)
                    &&isExpanded()&&child.mouseClicked(mouseX,mouseY,button)) return true;
            }
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }
    @Override public void mouseReleased(int mouseX,int mouseY,int button) {
        int captured=inverseComponentSentinel();
        for(SettingWidget child:children) {child.mouseReleased(mouseX,mouseY,button);if(captured==0) return;}
        super.mouseReleased(mouseX,mouseY,button);
    }
    @Override public void keyTyped(char character,int key) {
        int captured=inverseComponentSentinel();
        for(SettingWidget child:children) {child.keyTyped(character,key);if(captured==0) return;}
        super.keyTyped(character,key);
    }
    @Override public boolean usesVisibilityAnimation() { return false; }
    @Override public boolean isVisible() { return setting.isVisible(); }
    public SettingGroup getSetting() { return setting; }
    public List<SettingWidget> getChildren() { return children; }
    public FrameAnimation getHeaderFadeAnimation() { return headerFade; }
    public FrameAnimation getHeightAnimation() { return heightAnimation; }
    public boolean isExpanded() { return expanded; }
    public boolean isPositionChanging() { return positionChanging; }
    public int getOriginalUnusedIndex() { return originalUnusedIndex; }
    public boolean isSnapChildren() { return snapChildren; }
    public GroupSettingWidget setSnapChildren(boolean value) { snapChildren=value;return this; }
}
