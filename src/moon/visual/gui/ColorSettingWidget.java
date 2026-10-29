package moon.visual.gui;

import java.awt.Color;
import moon.visual.render.*;

/** kg rendering adapted for independent live controls: only visible expanded bodies receive
 * input, and dragging is captured by the control which consumed the press. */
public final class ColorSettingWidget extends SettingWidget {
    /** Verified com.v field roles. uiColor must supply the original dark Scheme factory. */
    public interface Scheme extends Theme {
        int onPrimary();int primaryContainer();int onPrimaryContainer();
        int secondary();int onSecondary();int secondaryContainer();int onSecondaryContainer();
    }
    public interface Drawing extends SettingWidget.Drawing {
        DrawTask rectangleTask();DrawTask gradientTask();DrawTask outlineTask();
        DrawTaskState.Geometry rectangle(float x,float y,float width,float height,int color);
        DrawTaskState.Geometry gradient(float x,float y,float width,float height,int from,int to,boolean vertical);
        DrawTaskState.Geometry outline(float x,float y,float width,float height,float radius,float lineWidth,int color);
    }
    public interface Host extends SettingWidget.Host {
        @Override Scheme uiColor(int argb);
        @Override Drawing drawing();
        String readClipboard() throws Exception;
        void writeClipboard(String text);
    }
    private static final String[] ACTIONS={"Paste","Copy"};
    private final FrameAnimation headerAnimation,bodyAnimation;
    private final ColorSetting setting;
    private final Host colorHost;
    private boolean expanded,draggingHue,draggingPlane;
    private float hue,saturation,brightness;
    private Scheme selectedScheme;
    private int observedColor;
    public ColorSettingWidget(Host host,ColorSetting setting,float x,float y,float width,float height) {
        super(host);colorHost=host;headerAnimation=newAnimation();bodyAnimation=newAnimation();
        this.setting=setting;this.x=x;targetY=y;this.width=width;this.height=height;
        int capturedSentinel=getComponentSentinel();
        float[] hsb=hsb(setting.get());float nearest=0,distance=2.14748365E9F;
        for(int row=0;row<67;row++) {
            if(capturedSentinel!=0) return;
            float candidate=row/67F;
            if(Math.abs(candidate-hsb[0])<distance) { distance=Math.abs(candidate-hsb[0]);nearest=candidate; }
        }
        hue=nearest;saturation=hsb[1];brightness=hsb[2];selectedScheme=host.uiColor(setting.get());observedColor=setting.get();
    }
    private static float[] hsb(int argb) {
        Color color=new Color(argb);return Color.RGBtoHSB(color.getRed(),color.getGreen(),color.getBlue(),new float[3]);
    }
    private Scheme scheme() { return (Scheme)theme; }
    private Drawing drawing() { return colorHost.drawing(); }
    private DrawTaskState.Command parentClip() { return drawing().clip(clipX,clipY,clipWidth,clipHeight); }
    private DrawTaskState.Command bodyClip() {
        float available=clipHeight-(clipY+clipHeight-(getY()+height)+3)-(expanded?fullHeight-height:0);
        return drawing().clip(clipX,clipY,clipWidth,Math.min(available,clipHeight));
    }
    private int[] presetColors() {
        Scheme global=scheme(),selected=selectedScheme;
        return new int[]{global.accent(),global.onPrimary(),selected.accent(),selected.secondary(),
            selected.onPrimary(),selected.onSecondary(),selected.primaryContainer(),selected.onPrimaryContainer()};
    }
    @Override public void update(int mouseX,int mouseY) {
        if(!expanded||!isVisible()){draggingHue=draggingPlane=false;}
        if(!draggingHue&&!draggingPlane&&setting.get()!=observedColor)syncColor();
        ensureConfig();drawBackground();drawHeader();
        if(expanded){drawPresets();drawClipboardButtons();}
        int capturedSentinel=getComponentSentinel();
        // Original bodyAnimation is constructed but never advanced.
        if(capturedSentinel==0&&(expanded||bodyAnimation.get()>19.1D)) drawHue(mouseY);
        if(expanded)drawPlane(mouseX,mouseY);
        super.update(mouseX,mouseY);
    }
    private void syncColor(){
        float[] values=hsb(setting.get());hue=values[0];saturation=values[1];brightness=values[2];
        selectedScheme=colorHost.uiColor(setting.get());observedColor=setting.get();
    }
    @Override public boolean mouseClicked(int mouseX,int mouseY,int button) {
        if(button!=0||!isVisible()||!hitTest(mouseX,mouseY,clipX,clipY,clipWidth,clipHeight))return false;
        if(hitTest(mouseX,mouseY,x+3,getY()+3,width-6,16)){
            expanded=!expanded;draggingHue=draggingPlane=false;return true;
        }
        if(!expanded)return false;
        float buttonX=x+padding+5,buttonY=getY()+102;
        for(String action:ACTIONS){
            float actionWidth=font.width(action,6.565F)+4;
            if(hitTest(mouseX,mouseY,buttonX,buttonY+.25F,actionWidth+4,font.height(6.565F)+4)){
                if(action.equals("Copy"))colorHost.writeClipboard("#"+Integer.toHexString(setting.get()));
                else try{
                    String value=colorHost.readClipboard();
                    if(value.startsWith("#")){setting.set((int)Long.parseLong(value.substring(1),16));syncColor();}
                }catch(Exception ignored){}
                return true;
            }
            buttonX+=actionWidth+6;
        }
        int[] presets=presetColors();
        for(int i=0;i<presets.length;i++)if(hitTest(mouseX,mouseY,x+padding+5+i*11,getY()+90.25F,9,9)){
            setting.set(presets[i]);syncColor();return true;
        }
        float planeX=x+padding+6,planeWidth=width-25-padding*2;
        if(hitTest(mouseX,mouseY,x+width-padding-11.5F,getY()+19,4,67)){
            draggingHue=true;return true;
        }
        if(hitTest(mouseX,mouseY,planeX,getY()+19,planeWidth,67)){
            draggingPlane=true;return true;
        }
        return false;
    }
    @Override public void mouseReleased(int mouseX,int mouseY,int button) {
        if(button==0)draggingHue=draggingPlane=false;
        super.mouseReleased(mouseX,mouseY,button);
    }
    private void drawBackground() {
        DrawTaskState.Command clip=parentClip();DrawTask task=drawing().roundedTask();
        task.add(drawing().rounded(x+padding,getY(),width-padding*2,height,6,theme.track()));task.add(clip);task.draw();
        fullHeight=19+(expanded?100:0);
    }
    private void drawHeader() {
        DrawTaskState.Command clip=parentClip();headerAnimation.update(expanded?0:1,0.30000001192092896D);
        DrawTask preview=drawing().roundedTask();
        preview.add(drawing().rounded(getX()+getWidth()-padding-19.5F+1,getY()+3,13,13,5.5F,setting.get()));
        preview.add(clip);preview.draw();
        DrawTask title=drawing().msdfTask(font);
        font.drawSinglePass(title,setting.getDisplayName(),8.039999F,getX()+padding+6,getY()+5,theme.text());
        title.add(clip);title.draw();
    }
    public void drawPresets() {
        int capturedSentinel=getComponentSentinel();DrawTaskState.Command clip=bodyClip();int[] colors=presetColors();
        DrawTask task=drawing().roundedTask();
        for(int index=0;index<colors.length;index++) {
            task.add(drawing().rounded(x+padding+5+index*11,getY()+90+.25F,9,9,3.5F,colors[index]));
            if(capturedSentinel!=0) break;
        }
        if(capturedSentinel==0) { task.add(clip);task.draw(); }
        else task.getState().release();
        if(host.markers().getMarker()!=null) setComponentSentinel(getComponentSentinel()+1);
    }
    public void drawClipboardButtons() {
        int capturedSentinel=inverseComponentSentinel();
        DrawTaskState.Command clip=bodyClip();float buttonX=getX()+padding+5,buttonY=getY()+102;
        DrawTask backgrounds=drawing().roundedTask();
        int index=0;
        for(String action:ACTIONS) {
            float actionWidth=font.width(action,6.565F)+4;
            backgrounds.add(drawing().rounded(buttonX,buttonY+.25F,actionWidth+4,font.height(7.065F)+4,5,scheme().secondary()));
            buttonX+=actionWidth+6;
            if(capturedSentinel==0&&index++==0) break;
        }
        if(capturedSentinel==0) backgrounds.getState().release();
        else { backgrounds.add(clip);backgrounds.draw(); }
        DrawTask text=drawing().msdfTask(font);buttonX=getX()+padding+5;
        index=0;
        for(String action:ACTIONS) {
            float actionWidth=font.width(action,6.565F)+4;
            font.draw(text,action,6.565F,buttonX+4,buttonY+3,scheme().onPrimary(),false,false,.01F);buttonX+=actionWidth+6;
            if(capturedSentinel==0&&index++==0) break;
        }
        if(capturedSentinel==0) text.getState().release();
        else { text.add(clip);text.draw(); }
    }
    private void drawHue(int mouseY) {
        int capturedSentinel=getComponentSentinel();
        float hueX=x+getWidth()-padding-(5+2.5F)-4,hueY=getY()+19;
        DrawTaskState.Command clip=bodyClip();DrawTask strips=drawing().rectangleTask();
        for(int row=0;row<67;row++) {
            float value=row/67F;strips.add(drawing().rectangle(hueX,hueY+row,4,1,Color.HSBtoRGB(value,1,1)));
            if(capturedSentinel==0&&value==hue) strips.add(drawing().rectangle(hueX-1,hueY+row,6,1,scheme().onSecondaryContainer()));
            if(capturedSentinel==0&&draggingHue&&mouseY==Math.round(hueY)+row) {
                hue=value;
                // Preserve saturation and brightness while editing hue.
                setting.set(Color.HSBtoRGB(hue,saturation,brightness));
                observedColor=setting.get();selectedScheme=colorHost.uiColor(observedColor);
            }
        }
        strips.add(clip);strips.draw();
        DrawTask outlines=drawing().outlineTask();
        outlines.add(drawing().outline(hueX-2,hueY-2,8,71,2,.5F,theme.track()));
        outlines.add(drawing().outline(hueX-2.5F,hueY-2.5F,9,72,2,.5F,scheme().onSecondaryContainer()));
        outlines.add(clip);outlines.draw();
    }
    private void drawPlane(int mouseX,int mouseY) {
        int capturedSentinel=getComponentSentinel();
        DrawTaskState.Command clip=bodyClip();float planeWidth=getWidth()-25-padding*2;
        float planeX=getX()+padding+(5+1),planeY=getY()+19;
        DrawTask gradients=drawing().gradientTask();
        gradients.add(drawing().gradient(planeX,planeY,planeWidth,67,-1,Color.HSBtoRGB(hue,1,1),false));
        gradients.add(drawing().gradient(planeX,planeY,planeWidth,67,0,Color.BLACK.getRGB(),true));
        gradients.add(clip);gradients.draw();
        DrawTask outlines=drawing().outlineTask();
        outlines.add(drawing().outline(planeX-3,planeY-3,planeWidth+6,73,5,1.25F,theme.track()));
        outlines.add(drawing().outline(planeX-2,planeY-2,planeWidth+4,71,5,.5F,scheme().onSecondaryContainer()));
        float indicatorX=planeX+planeWidth*saturation,indicatorY=planeY+67*(1-brightness);
        outlines.add(drawing().outline(indicatorX-4,indicatorY-4,8,8,2,.75F,scheme().onSecondaryContainer()));
        outlines.add(clip);outlines.draw();
        if(draggingPlane&&(capturedSentinel!=0||hitTest(mouseX,mouseY,planeX,planeY,planeWidth,67))) {
            double newSaturation=NumericSettingMath.clamp((double)((mouseX-planeX)/planeWidth),0D,1D);
            double invertedBrightness=NumericSettingMath.clamp((double)((mouseY-planeY)/67F),0D,1D);
            setting.set(Color.HSBtoRGB(hue,(float)newSaturation,(float)(1-invertedBrightness)));
            selectedScheme=colorHost.uiColor(setting.get());
            saturation=(float)newSaturation;brightness=(float)(1-invertedBrightness);observedColor=setting.get();
        }
    }
    @Override public boolean isVisible() { return setting.isVisible(); }
    public ColorSetting getSetting() { return setting; }
    public boolean isExpanded() { return expanded; }
    public boolean isDraggingHue() { return draggingHue; }
    public boolean isDraggingPlane() { return draggingPlane; }
    public float getHue() { return hue; }
    public float getSaturation() { return saturation; }
    public float getBrightness() { return brightness; }
    public Scheme getSelectedScheme() { return selectedScheme; }
    public FrameAnimation getHeaderAnimation() { return headerAnimation; }
    public FrameAnimation getBodyAnimation() { return bodyAnimation; }
}
