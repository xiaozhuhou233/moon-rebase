package moon.visual.gui;

import java.util.HashMap;
import java.util.Map;
import moon.visual.render.*;

/** kh/kk/km drawing/input with per-class, per-method ko capture timing.
 * Host fonts, geometry, animation and settings remain explicit component boundaries.
 */
public abstract class ChoiceSettingWidget<T extends Enum<T> & DisplayNamed> extends SettingWidget {
    public interface Scheme extends Theme { int onPrimary(); }
    public interface Drawing extends SettingWidget.Drawing {
        DrawTaskState.Geometry rounded(float x,float y,float width,float height,float radius,int color,
            boolean cornerG,boolean cornerH,boolean cornerI,boolean cornerJ,boolean smooth);
    }
    public interface Host extends SettingWidget.Host {
        MsdfFontBase materialIcons();
        @Override Scheme uiColor(int argb);
        @Override Drawing drawing();
    }
    private static final String CHECK="\ue5ca";
    private final Map<T,FrameAnimation> selectionAnimations;
    private final FrameAnimation heightAnimation;
    private final Host choiceHost;
    private boolean expanded;
    protected enum Kind { ENUM, MODE, MULTI }
    private final Kind kind;
    private final int constructorGuard;
    protected ChoiceSettingWidget(Host host,float x,float y,float width,float height) {
        this(host,x,y,width,height,Kind.MODE,0);
    }
    protected ChoiceSettingWidget(Host host,float x,float y,float width,float height,Kind kind,int beforeSuperGuard) {
        super(host);choiceHost=host;this.kind=kind;heightAnimation=newAnimation();selectionAnimations=new HashMap<>();
        this.x=x;targetY=y;this.width=width;
        // km captures raw ko after width, before height/options. kh supplied its pre-super inverse.
        constructorGuard=kind==Kind.MULTI?getComponentSentinel():beforeSuperGuard;
        this.height=height;
    }
    protected final void initializeOptions() {
        T[] initialOptions=values();
        int captured=kind==Kind.MODE?getComponentSentinel():constructorGuard;
        for(T option:initialOptions) {
            selectionAnimations.put(option,newAnimation());
            if(kind==Kind.ENUM?captured==0:captured!=0) break;
        }
    }
    protected abstract T[] values();
    protected abstract boolean selected(T option);
    protected abstract void select(T option);
    protected abstract String title();
    protected String optionLabel(T option) { return option.displayName(); }
    protected String headerStatus() { return null; }
    private Drawing drawing() { return choiceHost.drawing(); }
    private DrawTaskState.Command parentClip() { return drawing().clip(clipX,clipY,clipWidth,clipHeight); }
    private DrawTaskState.Command rowsClip() {
        return drawing().clip(clipX,clipY,clipWidth,Math.min(clipHeight-(clipY+clipHeight-(getY()+height)),clipHeight));
    }
    @Override public void update(int mouseX,int mouseY) {
        int captured;
        if(kind==Kind.ENUM) {ensureConfig();captured=getComponentSentinel();}
        else {captured=getComponentSentinel();ensureConfig();}
        drawBackground();drawTitle();
        if(captured!=0||expanded||heightAnimation.get()>19.1D) drawRows();
        super.update(mouseX,mouseY);
    }
    @Override public boolean mouseClicked(int mouseX,int mouseY,int button) {
        int captured=kind==Kind.MODE?getComponentSentinel():0;
        float rowX=x+padding;
        if(kind==Kind.MULTI) captured=inverseComponentSentinel();
        float rowWidth=width-padding*2;
        if(kind==Kind.ENUM) captured=getComponentSentinel();
        float headerEnd=getY()+3+23;
        boolean hit=hitTest(mouseX,mouseY,rowX,headerEnd-23-6,rowWidth,23);
        if(kind==Kind.MULTI?captured==0:captured!=0) return hit;
        if(hit) expanded=!expanded;
        else if(expanded) {
            // The original reads row geometry again after the header test.
            float optionX=x+padding,optionY=getY()+19+3-1,optionWidth=width-padding*2;
            DisplayNamed[] options=(DisplayNamed[])values();
            for(DisplayNamed option:options) {
                if(hitTest(mouseX,mouseY,optionX,optionY,optionWidth,21)) select(asOption(option));
                optionY+=21+1.5F-1;
            }
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }
    @SuppressWarnings("unchecked") private T asOption(DisplayNamed option) { return (T)option; }
    /** Retained protected API for callers of the old readable wrapping helper. */
    protected void measureWrapping() { measureBackgroundX(); }
    private float measureBackgroundX() {
        // kh captures after values/length; kk captures after height but before values.
        float measureX=x+padding+5;
        int unusedWrapHeight=0;float textHeight=0;
        if(kind!=Kind.ENUM) textHeight=font.height(7.7F);
        int captured=kind==Kind.MODE?getComponentSentinel():0;
        T[] options=values();int length=options.length;
        if(kind==Kind.ENUM) captured=getComponentSentinel();
        for(int index=0;index<length;index++) {
            String label=optionLabel(options[index]);float labelWidth=font.width(label,7.7F);
            float rightBoundary=x+width-(padding*2+10);
            if(captured!=0) return (measureX+labelWidth)+rightBoundary;
            if(measureX+labelWidth>rightBoundary) {
                measureX=x+padding+5;
                if(kind!=Kind.ENUM) unusedWrapHeight=(int)(unusedWrapHeight+(textHeight+13));
            }
            measureX+=labelWidth+15;
        }
        return x+padding;
    }
    private float measureMultiBackgroundX(int captured) {
        float measureX=x+padding+5;int unusedWrapHeight=0;float textHeight=font.height(7.7F);
        T[] options=values();
        for(T option:options) {
            String label=optionLabel(option);float labelWidth=font.width(label,7.7F);
            float rightBoundary=x+width-(padding*2+10);
            if(captured==0) return (measureX+labelWidth)+rightBoundary;
            if(measureX+labelWidth>rightBoundary) {
                measureX=x+padding+5;unusedWrapHeight=(int)(unusedWrapHeight+(textHeight+13));
            }
            measureX+=labelWidth+15;
        }
        return x+padding;
    }
    private void drawBackground() {
        int captured=kind==Kind.MULTI?inverseComponentSentinel():0;
        DrawTaskState.Command clip=parentClip();
        float backgroundX=kind==Kind.MULTI?measureMultiBackgroundX(captured):measureBackgroundX();
        float backgroundY=getY(),backgroundWidth=width-padding*2;
        // Original w is loaded into an unused local here; it supplies no color or size.
        DrawTask task=drawing().roundedTask();
        task.add(drawing().rounded(backgroundX,backgroundY,backgroundWidth,19,5,theme.track(),true,true,true,true,false));
        task.add(clip);task.draw();
        fullHeight=expanded?21.5F*values().length+20:19;
        visibilityAnimation.update(isVisible()?1:0,0.30000001192092896D);
        heightAnimation.update(fullHeight*visibilityAnimation.get(),0.30000001192092896D);setHeight(heightAnimation.get());
    }
    private void drawTitle() {
        DrawTaskState.Command clip=parentClip();DrawTask task=drawing().msdfTask(font);
        String status=headerStatus();float statusWidth=status==null?0:font.width(status,8.039999F);
        font.draw(task,title(),8.039999F,getX()+padding+5,getY()+5.5F,theme.text(),false,false,.01F);
        if(status!=null) font.draw(task,status,8.039999F,getX()+getWidth()-padding-statusWidth-5,getY()+5.5F,theme.text(),false,false,.01F);
        task.add(clip);task.draw();
    }
    private void drawRows() {
        DrawTaskState.Command clip=rowsClip();
        int captured=kind==Kind.MODE?inverseComponentSentinel():0;
        MsdfFontBase icons=choiceHost.materialIcons();
        if(kind==Kind.ENUM) captured=inverseComponentSentinel();
        DrawTask backgrounds=drawing().roundedTask(),text=drawing().msdfTask(font);
        if(kind==Kind.MULTI) captured=getComponentSentinel();
        DrawTask checks=drawing().msdfTask(icons);
        float rowX=x+padding,rowY=getY()+19+3-1,rowWidth=width-padding*2,rowHeight=21;
        DisplayNamed[] options=(DisplayNamed[])values();
        for(int index=0;index<options.length;index++) {
            DisplayNamed item=options[index];
            if(kind!=Kind.MULTI&&captured==0) return;
            T option=asOption(item);boolean checked=selected(option);FrameAnimation animation=selectionAnimations.get(option);
            if(kind==Kind.MULTI&&captured!=0) return;
            animation.update(checked?1:0,0.4000000059604645D);
            int trackColor=theme.track(),fillColor=new ArgbColor(theme.accent()).withAlpha(animation.get()).getRGB();
            boolean g=true,h=true,i=true,j=true;
            if(options.length>1) {
                if(index==0) { i=false;j=false; }
                else if(index<options.length-1) { g=false;h=false;i=false;j=false; }
                else { h=false;g=false; }
            }
            DrawTaskState.Geometry track=drawing().rounded(rowX,rowY,rowWidth,rowHeight,5,trackColor,g,h,i,j,false);
            DrawTaskState.Geometry fill=drawing().rounded(rowX,rowY,rowWidth,rowHeight,5,fillColor,g,h,i,j,false);
            backgrounds.add(track);backgrounds.add(fill);
            font.draw(text,optionLabel(option),8,rowX+5,rowY+rowHeight/2-font.height(8)/2+1,
                new ArgbColor(theme.text()).withAlpha(1-animation.get()).getRGB(),false);
            font.draw(text,optionLabel(option),8,rowX+5,rowY+rowHeight/2-font.height(8)/2+1,
                new ArgbColor(((Scheme)theme).onPrimary()).withAlpha(animation.get()).getRGB(),false);
            if(checked) icons.draw(checks,CHECK,8.5F,rowX+rowWidth-5-icons.width(CHECK,8.5F),
                rowY+rowHeight/2-icons.height(8.5F)/2+1,new ArgbColor(((Scheme)theme).onPrimary()).withAlpha(animation.get()).getRGB(),false);
            rowY+=rowHeight+1.5F-1;
        }
        backgrounds.add(clip);text.add(clip);checks.add(clip);backgrounds.draw();text.draw();checks.draw();
    }
    @Override public boolean usesVisibilityAnimation() { return false; }
    public boolean isExpanded() { return expanded; }
    public FrameAnimation getHeightAnimation() { return heightAnimation; }
    public FrameAnimation getSelectionAnimation(T option) { return selectionAnimations.get(option); }
}
