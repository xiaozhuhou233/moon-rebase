package moon.visual.gui;

import java.util.Collections;
import moon.visual.render.*;

/** kd: any-button Open area, title uses animatedY, button and center suppliers use targetY.
 * The l9 field assignment/init and shared kw.a identity are explicit screen/host boundaries.
 */
public final class BezierSettingWidget extends SettingWidget {
    public interface Scheme extends Theme { int onAccent(); }
    public interface Host extends SettingWidget.Host {
        @Override Scheme uiColor(int argb);
        ModalConfiguration.Screen screen();
        BezierEditor createEditor(BezierSetting setting);
        ModalOverlay createOverlay(ModalConfiguration configuration);
        ModalButton sharedCloseButton();
    }
    private final BezierSetting setting;
    private final Host curveHost;
    private int clickState;
    public BezierSettingWidget(Host host,BezierSetting setting,float x,float y,float width,float height) {
        super(host);curveHost=host;this.setting=setting;this.x=x;targetY=y;this.width=width;this.height=height;
    }
    @Override public void update(int mouseX,int mouseY) {
        ensureConfig();int captured=inverseComponentSentinel();Drawing drawing=host.drawing();
        DrawTaskState.Command clip=drawing.clip(clipX,clipY,clipWidth,clipHeight);DrawTask text=drawing.msdfTask(font);
        font.drawSinglePass(text,setting.getDisplayName(),8.039999F,getX()+padding+1F,getY()+height/2F-font.height(8.039999F)/2F+1F,theme.text());
        String label="Open";float bx=x+width-padding-font.width(label,7.7F)-6F,bh=font.height(7.7F)+6F;
        float by=targetY+height/2F-bh/2F,bw=font.width(label,7.7F)+6F;DrawTask background=drawing.roundedTask();
        background.add(drawing.rounded(bx,by,bw,bh,4F,theme.accent()));
        font.drawSinglePass(text,label,7.7F,bx+3F,by+3.5F,((Scheme)theme).onAccent());
        background.add(clip);text.add(clip);background.draw();text.draw();fullHeight=14F;super.update(mouseX,mouseY);
        if(captured==0) host.markers().setMarker("SheUIc");
    }
    @Override public boolean mouseClicked(int mouseX,int mouseY,int button) {
        String label="Open";float bx=x+width-padding-font.width(label,7.7F)-6F,bh=font.height(7.7F)+6F;
        int captured=inverseComponentSentinel();float by=targetY+height/2F-bh/2F,bw=font.width(label,7.7F)+6F;
        boolean hit=hitTest(mouseX,mouseY,bx,by,bw,bh);
        if(captured==0) return (clickState&1)!=0;
        if(hit) {
            ModalConfiguration.Screen screen=curveHost.screen();
            ModalConfiguration configuration=ModalConfiguration.builder(host.markers()).title("Bezier Editor")
                .description("Click and drag the points to edit the curve").x(()->x+width/2F).y(()->targetY+height/2F)
                .screen(curveHost.screen()).content(curveHost.createEditor(setting)).buttons(Collections.singletonList(curveHost.sharedCloseButton())).build();
            screen.setModal(curveHost.createOverlay(configuration));
        }
        return clickState!=-1;
    }
    @Override public void mouseReleased(int mouseX,int mouseY,int button) { clickState=-1; }
    @Override public boolean isVisible() { return setting.isVisible(); }
    public BezierSetting getSetting() { return setting; }
    public int getClickState() { return clickState; }
}
