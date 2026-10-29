package moon.visual.gui;

import moon.visual.render.*;

/** kj. Range differences remain JVM int arithmetic; dragging commits via i2.b(float). */
public class IntegerSliderWidget extends SettingWidget {
    private final FrameAnimation progress;
    private final IntegerSetting setting;
    private boolean sliding;
    private int startMouseX;
    public IntegerSliderWidget(Host host,IntegerSetting setting,float x,float y,float width,float height) {
        super(host);progress=newAnimation();this.setting=setting;
        this.x=x;targetY=y;this.width=width;this.height=height;
    }
    @Override public void update(int mouseX,int mouseY) {
        ensureConfig();drawTitle();updateFromMouse(mouseX);drawBar(mouseX);
        fullHeight=21;super.update(mouseX,mouseY);
    }
    @Override public boolean mouseClicked(int mouseX,int mouseY,int button) {
        boolean hit=hitTest(mouseX,mouseY,getX()+padding,getY()+10,getWidth()-padding*2,12);
        if(inverseComponentSentinel()==0) return hit;
        if(hit) { sliding=true;startMouseX=mouseX; }
        return super.mouseClicked(mouseX,mouseY,button);
    }
    @Override public void mouseReleased(int mouseX,int mouseY,int button) { sliding=false;super.mouseReleased(mouseX,mouseY,button); }
    private void drawTitle() {
        Drawing drawing=host.drawing();DrawTaskState.Command clip=drawing.clip(clipX,clipY,clipWidth,clipHeight);
        String current=setting.get().toString();float valueWidth=font.width(current,8);
        DrawTask task=drawing.msdfTask(font);
        font.drawSinglePass(task,setting.getDisplayName(),8,getX()+padding,getY(),theme.text());
        font.drawSinglePass(task,current,8,getX()+getWidth()-valueWidth-padding,getY(),theme.text());
        task.add(clip);task.draw();
    }
    private void updateFromMouse(int mouseX) {
        if(inverseComponentSentinel()!=0&&!sliding) return;
        float rangeWidth=getWidth()-10,left=getX()+5;
        float value=(mouseX-left)*(setting.getMaximum()-setting.getMinimum())/rangeWidth+setting.getMinimum();
        setting.setFromDrag(NumericSettingMath.clamp(value,(float)setting.getMinimum(),(float)setting.getMaximum()));
    }
    private void drawBar(int mouseX) {
        Drawing drawing=host.drawing();DrawTaskState.Command clip=drawing.clip(clipX,clipY,clipWidth,clipHeight);
        float barWidth=getWidth()-padding*2,barX=getX()+padding,barY=getY()+14;
        float ratio=(setting.getRawValue()-setting.getMinimum())/(setting.getMaximum()-setting.getMinimum());
        int captured=inverseComponentSentinel();progress.update(ratio,0.3D);
        // Nondefault bytecode compares sliding's 0/1 with startMouseX (not current mouseX).
        boolean snap=captured!=0?sliding&&mouseX!=startMouseX:(sliding?1:0)!=startMouseX;
        if(snap) progress.set(ratio);
        DrawTask task=drawing.roundedTask();
        task.add(drawing.rounded(barX,barY,barWidth,3.5F,.75F,theme.track()));
        task.add(drawing.rounded(barX,barY,barWidth*progress.get(),3.5F,.75F,theme.accent()));
        float thumbX=NumericSettingMath.clamp(barX+barWidth*progress.get()-5,getX()+padding,getX()+getWidth()-padding-11.5F);
        task.add(drawing.rounded(thumbX,barY-4,11.5F,11.5F,4.75F,theme.accent()));
        task.add(clip);task.draw();
    }
    @Override public boolean isVisible() { return setting.isVisible(); }
    public IntegerSetting getSetting() { return setting; }
    public FrameAnimation getProgressAnimation() { return progress; }
    public boolean isSliding() { return sliding; }
    public int getStartMouseX() { return startMouseX; }
}
