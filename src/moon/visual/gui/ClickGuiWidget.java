package moon.visual.gui;

import moon.visual.render.MsdfFontBase;

/** kz. Actual base widget: lazy config, theme refresh, dragging, inclusive hit testing and hooks.
 * Host replaces bw/bo.INTER_SEMI_BOLD, module lookup and com.v color construction.
 * Subclasses still supply the category, setting and modal drawing behaviors.
 */
public class ClickGuiWidget {
    public interface Config { int colorArgb(); }
    public interface Host {
        MsdfFontBase interSemiBold();
        Config clickGuiConfig();
        Object uiColor(int argb);
    }
    protected MsdfFontBase font;
    protected Config config;
    protected float x,y,width,height;
    private boolean dragging;
    private float dragOffsetX,dragOffsetY;
    protected Object themeColor;
    private static boolean sentinel;
    private final Host host;

    public ClickGuiWidget(Host host) { this.host=host; font=host.interSemiBold(); }
    public void initialize() { ensureConfig(); themeColor=host.uiColor(config.colorArgb()); }
    public void refreshColor() { themeColor=host.uiColor(config.colorArgb()); }
    public void update(int mouseX,int mouseY) {
        boolean abnormal=inverseSentinel();
        if(!abnormal) {
            if(!dragging) return;
            x=dragOffsetX+mouseX;
        }
        y=dragOffsetY+mouseY;
    }
    public void mouseClicked(int mouseX,int mouseY,int button) { }
    public void mouseReleased(int mouseX,int mouseY,int button) { }
    public void keyTyped(char character,int key) { }
    public void onOriginalDm(Object event,float partialTicks) { }
    public void tick() { }

    protected boolean hitTest(int mouseX,int mouseY,float x,float y,float width,float height) {
        boolean abnormal=inverseSentinel();
        int comparison=fcmpl(mouseX,x);
        // The nondefault bytecode forwards the first comparison directly to ireturn.
        if(abnormal) return comparison!=0;
        if(comparison<0) return false;
        if(fcmpg(mouseX,x+width)>0) return false;
        if(fcmpl(mouseY,y)<0) return false;
        return fcmpg(mouseY,y+height)<=0;
    }
    private static int fcmpl(float a,float b) {
        return Float.isNaN(a)||Float.isNaN(b)?-1:a>b?1:a==b?0:-1;
    }
    private static int fcmpg(float a,float b) {
        return Float.isNaN(a)||Float.isNaN(b)?1:a>b?1:a==b?0:-1;
    }
    protected void ensureConfig() {
        if(!inverseSentinel()) {
            if(config!=null) return;
            config=host.clickGuiConfig();
        }
        themeColor=host.uiColor(config.colorArgb());
    }
    public MsdfFontBase getFont() { return font; }
    public Config getConfig() { return config; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public boolean isDragging() { return dragging; }
    public float getDragOffsetX() { return dragOffsetX; }
    public float getDragOffsetY() { return dragOffsetY; }
    public Object getThemeColor() { return themeColor; }
    public ClickGuiWidget setFont(MsdfFontBase value) { font=value; return this; }
    public ClickGuiWidget setConfig(Config value) { config=value; return this; }
    public ClickGuiWidget setX(float value) { x=value; return this; }
    public ClickGuiWidget setY(float value) { y=value; return this; }
    public ClickGuiWidget setWidth(float value) { width=value; return this; }
    public ClickGuiWidget setHeight(float value) { height=value; return this; }
    public ClickGuiWidget setDragging(boolean value) { dragging=value; return this; }
    public ClickGuiWidget setDragOffsetX(float value) { dragOffsetX=value; return this; }
    public ClickGuiWidget setDragOffsetY(float value) { dragOffsetY=value; return this; }
    public ClickGuiWidget setThemeColor(Object value) { themeColor=value; return this; }
    public static void setSentinel(boolean value) { sentinel=value; }
    public static boolean getSentinel() { return sentinel; }
    public static boolean inverseSentinel() { return !getSentinel(); }
    static { if(!getSentinel()) setSentinel(true); }
}
