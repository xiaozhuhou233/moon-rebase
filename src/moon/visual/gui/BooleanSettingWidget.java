package moon.visual.gui;

import java.util.function.LongSupplier;
import moon.visual.render.*;

/** ke's captured ko guard: skip toggle work but retain parent update; guarded input returns hit.
 * Durations/track height are injected; production uses the separately documented numeric profile.
 * The fixed 25x13 left-button input rectangle is literal in the original bytecode.
 */
public final class BooleanSettingWidget extends SettingWidget {
    public interface Scheme extends ChoiceSettingWidget.Scheme { int outline(); }
    public interface Drawing extends SettingWidget.Drawing {
        DrawTask outlineTask();
        DrawTaskState.Geometry outline(float x,float y,float width,float height,float radius,float lineWidth,int color);
    }
    public interface Host extends SettingWidget.Host {
        MsdfFontBase materialIcons();
        @Override Scheme uiColor(int argb);
        @Override Drawing drawing();
    }
    public static final class Parameters {
        public final long colorDuration,motionDuration;
        public final int trackHeight;
        public Parameters(long colorDuration,long motionDuration,int trackHeight) {
            this.colorDuration=colorDuration;this.motionDuration=motionDuration;this.trackHeight=trackHeight;
        }
    }
    private final BooleanSetting setting;
    private final TimedAnimation colorAnimation,positionAnimation,sizeAnimation;
    private final Host booleanHost;
    private final Parameters parameters;
    private boolean displayedState;
    public BooleanSettingWidget(Host host,BooleanSetting setting,float x,float y,float width,float height,
            Parameters parameters,LongSupplier clock) {
        super(host);booleanHost=host;this.parameters=parameters;
        // bs.LINEAR has no k_ assignment: its switch result is the zero/default arm.
        colorAnimation=new TimedAnimation(parameters.colorDuration,0,clock,host.markers());
        positionAnimation=new TimedAnimation(parameters.motionDuration,0,clock,host.markers());
        sizeAnimation=new TimedAnimation(parameters.motionDuration,0,clock,host.markers());
        this.setting=setting;this.x=x;targetY=y;this.width=width;this.height=height;
    }
    @Override public void update(int mouseX,int mouseY) {
        int captured=inverseComponentSentinel();
        if(captured!=0) {
            if(displayedState!=setting.get()) {
                sizeAnimation.resetTo(displayedState?sizeAnimation.get():0);
                positionAnimation.resetTo(displayedState?sizeAnimation.get():0);
                colorAnimation.resetTo(displayedState?sizeAnimation.get():0);
                displayedState=setting.get();
            }
            ensureConfig();drawToggle();drawTitle();fullHeight=15;
        }
        super.update(mouseX,mouseY);
    }
    @Override public boolean mouseClicked(int mouseX,int mouseY,int button) {
        float w=25,h=13;int captured=inverseComponentSentinel();
        float left=getX()+getWidth()-w-padding,top=getY()+getHeight()/2-h/2+.5F;
        boolean hit=hitTest(mouseX,mouseY,left,top,w,h);
        if(captured==0) return hit;
        if(hit&&button==0) setting.set(!setting.get());
        return super.mouseClicked(mouseX,mouseY,button);
    }
    private DrawTaskState.Command parentClip() { return booleanHost.drawing().clip(clipX,clipY,clipWidth,clipHeight); }
    private void drawTitle() {
        DrawTaskState.Command clip=parentClip();DrawTask task=booleanHost.drawing().msdfTask(font);
        font.drawSinglePass(task,setting.getDisplayName(),8.039999F,getX()+padding+1,getY()+3.5F,theme.text());
        task.add(clip);task.draw();
    }
    private void drawToggle() {
        Drawing drawing=booleanHost.drawing();Scheme scheme=(Scheme)theme;
        sizeAnimation.update(0,1,displayedState);DrawTaskState.Command clip=parentClip();
        int trackHeight=parameters.trackHeight;float trackWidth=trackHeight*1.56F;
        float thumb=8.5F+sizeAnimation.get()*4;
        positionAnimation.update(0,1,displayedState);colorAnimation.update(0,1,displayedState);
        float left=getX()+getWidth()-trackWidth-padding-1,top=getY()+7.5F-trackHeight/2F;
        float thumbX=left+4-.5F+positionAnimation.get()*(trackWidth-5-thumb),thumbY=top+trackHeight/2F-thumb/2;
        DrawTask rounded=drawing.roundedTask();
        rounded.add(drawing.rounded(left-.5F,top-.5F,trackWidth+1,trackHeight+1F,trackHeight/2F-1,scheme.track()));
        rounded.add(drawing.rounded(left-.5F,top-.5F,trackWidth+1,trackHeight+1F,trackHeight/2F-.5F,new ArgbColor(scheme.accent()).withAlpha(colorAnimation.get()).getRGB()));
        rounded.add(drawing.rounded(thumbX,thumbY,thumb,thumb,thumb/2-1,scheme.outline()));
        rounded.add(drawing.rounded(thumbX,thumbY,thumb,thumb,thumb/2-1,new ArgbColor(scheme.onPrimary()).withAlpha(colorAnimation.get()).getRGB()));
        rounded.add(clip);rounded.draw();
        DrawTask outline=drawing.outlineTask();
        outline.add(drawing.outline(left-1,top-1,trackWidth+2,trackHeight+2F,trackHeight/2F-.5F,.45F,new ArgbColor(scheme.outline()).withAlpha(1-colorAnimation.get()).getRGB()));
        outline.add(clip);outline.draw();
        MsdfFontBase icons=booleanHost.materialIcons();DrawTask check=drawing.msdfTask(icons);
        icons.drawSinglePass(check,"\ue5ca",6.5F,thumbX+3,thumbY+thumb/2-2,new ArgbColor(scheme.accent()).withAlpha(colorAnimation.get()).getRGB());
        check.add(clip);check.draw();
    }
    @Override public boolean isVisible() { return setting.isVisible(); }
    public BooleanSetting getSetting() { return setting; }
    public boolean getDisplayedState() { return displayedState; }
    public TimedAnimation getColorAnimation() { return colorAnimation; }
    public TimedAnimation getPositionAnimation() { return positionAnimation; }
    public TimedAnimation getSizeAnimation() { return sizeAnimation; }
}
