package moon.visual.gui;

import moon.visual.render.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** kb, the actual setting-component base; kz is the separate draggable window base.
 * Theme exposes com.v's u/a/v calls without inventing unreviewed palette formulas.
 */
public class SettingWidget {
    public interface Theme { int track();int accent();int text(); }
    public interface Drawing {
        DrawTask msdfTask(MsdfFontBase font);
        DrawTask roundedTask();
        DrawTaskState.Command clip(float x,float y,float width,float height);
        DrawTaskState.Geometry rounded(float x,float y,float width,float height,float radius,int color);
    }
    public interface Host {
        MsdfFontBase interMedium();
        ClickGuiWidget.Config clickGuiConfig();
        Theme uiColor(int argb);
        int framesPerSecond();
        int animationFpsFloor();
        MarkerHooks markers();
        Drawing drawing();
    }
    protected MsdfFontBase font;
    protected ClickGuiWidget.Config config;
    private final FrameAnimation animatedY;
    protected final FrameAnimation visibilityAnimation;
    protected float x,targetY,width,height,fullHeight,padding=3;
    protected float clipX,clipY,clipWidth,clipHeight;
    protected Theme theme;
    private boolean initialVisibility;
    private static String sentinel="kiRzw";
    // ko's shared static integer; its inverse returns 126 for zero, otherwise zero.
    private static int componentSentinel;
    protected final Host host;
    public SettingWidget(Host host) {
        this.host=host;font=host.interMedium();config=host.clickGuiConfig();
        animatedY=newAnimation();visibilityAnimation=newAnimation();
    }
    protected final FrameAnimation newAnimation() { return new FrameAnimation(host::framesPerSecond,host.animationFpsFloor()); }
    public void initialize() {
        ensureConfig();theme=host.uiColor(config.colorArgb());
        getSentinel(); // invokestatic kb.b():String, not refreshColor().
        initialVisibility=isVisible();fullHeight=height;
        visibilityAnimation.set(isVisible()?1:0);animatedY.set(targetY);
        if(host.markers().getMarker()!=null) setSentinel("Oyo3H");
    }
    public void refreshColor() { theme=host.uiColor(config.colorArgb()); }
    public void update(int mouseX,int mouseY) {
        String captured=getSentinel();
        animatedY.update(targetY,0.30000001192092896D);
        if(captured==null||usesVisibilityAnimation()) {
            visibilityAnimation.update(isVisible()?1:0,0.30000001192092896D);
            height=fullHeight*visibilityAnimation.get();
        }
    }
    public void overlay(int mouseX,int mouseY) { }
    public boolean mouseClicked(int mouseX,int mouseY,int button) { return false; }
    public void mouseReleased(int mouseX,int mouseY,int button) { }
    public void keyTyped(char character,int key) { }
    protected boolean hitTest(int mouseX,int mouseY,float x,float y,float width,float height) {
        if(getSentinel()==null) {
            // fcmpl returns -1 for NaN. JVM ireturn boolean narrowing keeps bit zero.
            int comparison=Float.isNaN(x)?-1:mouseX>x?1:mouseX==x?0:-1;
            return (comparison&1)!=0;
        }
        // kb uses fcmpl for lower edges and fcmpg for upper edges: reject NaN.
        return mouseX>=x&&mouseX<=x+width&&mouseY>=y&&mouseY<=y+height;
    }
    public boolean isVisible() { return false; }
    public boolean usesVisibilityAnimation() { return true; }
    protected void ensureConfig() {
        String captured=getSentinel();
        if(captured!=null) {
            if(config!=null) return;
            config=host.clickGuiConfig();
        }
        theme=host.uiColor(config.colorArgb());
    }
    public MsdfFontBase getFont() { return font; }
    public ClickGuiWidget.Config getConfig() { return config; }
    public FrameAnimation getYAnimation() { return animatedY; }
    public FrameAnimation getVisibilityAnimation() { return visibilityAnimation; }
    public float getY() { return animatedY.get(); }
    public float getX() { return x; }
    public float getTargetY() { return targetY; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public float getFullHeight() { return fullHeight; }
    public float getPadding() { return padding; }
    public float getClipX() { return clipX; }
    public float getClipY() { return clipY; }
    public float getClipWidth() { return clipWidth; }
    public float getClipHeight() { return clipHeight; }
    public Theme getTheme() { return theme; }
    public boolean getInitialVisibility() { return initialVisibility; }
    public SettingWidget setFont(MsdfFontBase value) { font=value;return this; }
    public SettingWidget setConfig(ClickGuiWidget.Config value) { config=value;return this; }
    public SettingWidget setX(float value) { x=value;return this; }
    public SettingWidget setTargetY(float value) { targetY=value;return this; }
    public SettingWidget setWidth(float value) { width=value;return this; }
    public SettingWidget setHeight(float value) { height=value;return this; }
    public SettingWidget setFullHeight(float value) { fullHeight=value;return this; }
    public SettingWidget setClipX(float value) { clipX=value;return this; }
    public SettingWidget setClipY(float value) { clipY=value;return this; }
    public SettingWidget setClipWidth(float value) { clipWidth=value;return this; }
    public SettingWidget setClipHeight(float value) { clipHeight=value;return this; }
    public SettingWidget setTheme(Theme value) { theme=value;return this; }
    public SettingWidget setInitialVisibility(boolean value) { initialVisibility=value;return this; }
    public SettingWidget setPadding(float value) { padding=value;return this; }
    public static void setSentinel(String value) { sentinel=value; }
    public static String getSentinel() { return sentinel; }
    public static void setComponentSentinel(int value) { componentSentinel=value; }
    public static int getComponentSentinel() { return componentSentinel; }
    public static int inverseComponentSentinel() { return getComponentSentinel()==0?126:0; }
}
