package moon.visual.gui;

import moon.visual.render.*;

/** kf. Raw input ignores button/clip; callback(null,null), then kb returns false.
 * Roboto color pairs are com.v e/f underneath a/b hover overlays.
 */
public final class ActionSettingWidget extends SettingWidget {
    public interface Scheme extends Theme { int success();int onSuccess();int onAccent(); }
    public interface Host extends SettingWidget.Host {
        MsdfFontBase robotoMedium();
        @Override Scheme uiColor(int argb);
    }
    private final ActionSetting setting;
    private final FrameAnimation hover;
    private final Host actionHost;
    public ActionSettingWidget(Host host,ActionSetting setting,float x,float y,float width,float height) {
        super(host);actionHost=host;hover=newAnimation();this.setting=setting;
        this.x=x;targetY=y;this.width=width;this.height=height;
    }
    @Override public void update(int mouseX,int mouseY) {
        ensureConfig();draw(mouseX,mouseY);fullHeight=25F;super.update(mouseX,mouseY);
    }
    private void draw(int mouseX,int mouseY) {
        Drawing drawing=host.drawing();DrawTaskState.Command clip=drawing.clip(clipX,clipY,clipWidth,clipHeight);
        MsdfFontBase face=actionHost.robotoMedium();DrawTask text=drawing.msdfTask(face),background=drawing.roundedTask();
        float bx=getX()+padding,by=targetY+1F,bw=getWidth()-padding*2F,bh=getHeight()-2F;
        float tw=face.width(setting.getDisplayName(),9.8F),th=face.secondMetric(setting.getDisplayName(),9.8F);
        hover.update(hitTest(mouseX,mouseY,bx,by,bw,bh)?1F:0F,0.4000000059604645D);float amount=hover.get();
        Scheme colors=(Scheme)theme;
        background.add(drawing.rounded(bx,by,bw,bh,8F,colors.success()));
        background.add(drawing.rounded(bx,by,bw,bh,8F,new ArgbColor(colors.accent()).withAlpha(amount).getRGB()));
        float tx=bx+bw/2F-tw/2F,ty=by+bh/2F-th/2F+.5F;
        face.drawSinglePass(text,setting.getDisplayName(),9.8F,tx,ty,colors.onSuccess());
        face.drawSinglePass(text,setting.getDisplayName(),9.8F,tx,ty,new ArgbColor(colors.onAccent()).withAlpha(amount).getRGB());
        background.add(clip);background.draw();text.add(clip);text.draw();
    }
    @Override public boolean mouseClicked(int mouseX,int mouseY,int button) {
        int captured=inverseComponentSentinel();
        boolean hit=hitTest(mouseX,mouseY,getX()+padding,targetY+1F,getWidth()-padding*2F,getHeight()-2F);
        if(captured==0) return hit;
        if(hit) setting.getCallback().accept(null,null);
        return super.mouseClicked(mouseX,mouseY,button);
    }
    @Override public boolean isVisible() { return setting.isVisible(); }
    public ActionSetting getSetting() { return setting; }
    public FrameAnimation getHoverAnimation() { return hover; }
}
