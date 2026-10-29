package moon.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;
import javax.imageio.ImageIO;
import moon.module.player.SessionInformationModule;
import moon.visual.render.*;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjglx.util.vector.Matrix4f;
import org.lwjglx.util.vector.Vector3f;

/** jF/fq: original server icon and 126.5-high three-row panel, shared dm masks.
 * Rebuilt on the render thread instead of the original desktop-refresh worker.
 * There is no entry/exit animation in jF; retained geometry refresh is not an animation.
 */
final class McpSessionHud implements AutoCloseable {
    static final float HEIGHT=126.5F,ROW=20.833334F;
    static final String TITLE="\u00a7lSession Information";
    private static final String[] ICONS={"\uf889","\uf89a","\ue8b5"};
    private static final ResourceLocation UNKNOWN=new ResourceLocation("textures/misc/unknown_server.png");
    record Frame(float left,float top,float width,float scale,String server,List<String> rows,
                 int textColor,int accent,int primary,int onPrimary,boolean colorful,ResourceLocation icon){}
    private final McpVisualServices visual;
    private String iconSource;
    private ResourceLocation ownedIcon;
    private boolean dragging;
    private float dragX,dragY;
    private Frame frame;

    McpSessionHud(McpVisualServices visual){this.visual=visual;}
    Frame frame(){return frame;}
    int liveTextures(){return ownedIcon==null?0:1;}
    void prepare(){
        SessionInformationModule s=visual.nativeModules==null?null:visual.nativeModules.session;
        if(s==null||!s.enabled()||visual.mc.theWorld==null||visual.mc.thePlayer==null){
            if(frame!=null)invalidate();frame=null;dragging=false;releaseIcon();return;
        }
        ServerData data=visual.mc.getCurrentServerData();
        String server=data==null?"Singleplayer":data.serverIP;
        updateIcon(data);
        MsdfFont font=visual.font("Roboto-Medium");
        float width=Math.max(150,Math.max(font.width(TITLE,12),font.width(server,8)));
        int accent=s.syncColors.get()?visual.hudSettings.textColorOne.get():s.color.get();
        var palette=visual.uiColor(accent);
        Frame next=new Frame(s.x.get(),s.y.get(),width,s.scale.get(),server,List.of(s.kills()+" kills",s.deaths()+" deaths",s.elapsed()),
            s.lowAlpha.get()?0xbbffffff:-1,accent,palette.accent(),palette.onPrimary(),s.colorful.get(),ownedIcon==null?UNKNOWN:ownedIcon);
        if(!next.equals(frame))invalidate();frame=next;
        if(!(visual.mc.currentScreen instanceof GuiChat))dragging=false;
    }
    private void invalidate(){visual.post.requestShadowUpdate();visual.post.requestBlurUpdate();}
    private void updateIcon(ServerData data){
        String source=data==null?null:data.getBase64EncodedIconData();
        if(Objects.equals(source,iconSource))return;
        releaseIcon();iconSource=source;if(source==null)return;
        try{
            BufferedImage image=ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(source)));
            // The destination ServerData contract uses vanilla 64x64 icons; fq also validates both dimensions.
            if(image==null||image.getWidth()!=64||image.getHeight()!=64)return;
            DynamicTexture texture=new DynamicTexture(image);
            try{ownedIcon=visual.mc.getTextureManager().getDynamicTextureLocation("moon-session-server",texture);}
            catch(RuntimeException|Error failure){texture.deleteGlTexture();throw failure;}
        }catch(java.io.IOException|IllegalArgumentException failure){/* Invalid data selects vanilla's unknown-server image. */}
    }
    private void releaseIcon(){
        if(ownedIcon!=null)visual.mc.getTextureManager().deleteTexture(ownedIcon);
        ownedIcon=null;iconSource=null;
    }
    static void draw(McpVisualServices visual){if(visual.sessionHud!=null)visual.sessionHud.draw();}
    void draw(){if(frame!=null)McpLuaRenderer.renderHud(()->render(false));}
    void mask(boolean shadow){if(frame!=null)McpLuaRenderer.renderHud(()->render(true));}
    /** Original dm does not branch on blur/shadow: both receive the same full-color K panel. */
    private void render(boolean mask){
        Frame f=frame;float x=f.left()/f.scale(),y=f.top()/f.scale();
        DrawTask shape=task(visual.rounded,null,f.scale());
        try{
            if(mask){rectangle(shape,x,y,f.width(),HEIGHT,8,f.colorful()?f.accent():0xff000000);shape.draw();return;}
            rectangle(shape,x,y,f.width(),HEIGHT,8,0x66000000);
            rectangle(shape,x,y,f.width(),HEIGHT,8,new ArgbColor(f.colorful()?f.accent():0xff000000).withAlpha(.2F).getRGB());
            float rowY=y+30.5F+ROW+2;
            for(int i=0;i<3;i++){rectangle(shape,x+6.5F,rowY,ROW,ROW,6,f.primary());rowY+=ROW+2;}
            shape.draw();
        }finally{shape.getState().release();}
        MsdfFont text=visual.font("Roboto-Medium"),icons=visual.font("MaterialSymbolsOutlined-Regular");
        DrawTask letters=task(visual.msdf,text::bind,f.scale()),symbols=task(visual.msdf,icons::bind,f.scale());
        try{
            text.drawSinglePass(letters,TITLE,12,x+f.width()/2-text.width(TITLE,12)/2,y+9.5F,f.textColor());
            text.drawSinglePass(letters,f.server(),8,x+ROW+10,y+38.5F,f.textColor());
            float rowY=y+30.5F+ROW+2;
            for(int i=0;i<3;i++){
                text.drawSinglePass(letters,f.rows().get(i),8,x+ROW+10,rowY+6,f.textColor());
                MsdfGlyphShape glyph=icons.getLoader().getProvider().glyph(ICONS[i].charAt(0));
                if(glyph!=null){
                    // Center the actual glyph quad, including its left bearing and baseline.
                    var bounds=glyph.getGlyph().getPlaneBounds();
                    float gx=x+6.5F+(ROW-glyph.getPlaneWidth()*13)/2-(bounds==null?0:bounds.getLeft()*13);
                    float gy=rowY+(ROW-glyph.planeHeight()*13)/2
                        -glyph.getMetrics().baseline(visual.hooks)*13+glyph.getBearingTop()*13;
                    icons.drawSinglePass(symbols,ICONS[i],13,gx,gy,f.onPrimary());
                }
                rowY+=ROW+2;
            }
            letters.draw();symbols.draw();
        }finally{letters.getState().release();symbols.getState().release();}
        DrawTask image=task(visual.roundedTexture,()->visual.mc.getTextureManager().bindTexture(f.icon()),f.scale());
        try{image.add(new UiGeometry.RoundedTextureQuad(x+6.5F,y+30.5F,x+6.5F+ROW,y+30.5F+ROW,0,0,1,1,6,-1,visual.hooks));image.draw();}
        finally{image.getState().release();}
    }
    private DrawTask task(ShaderDispatcher dispatcher,DrawTaskState.Texture texture,float scale){
        DrawTask task=DrawTask.builder(visual.draw).dispatcher(dispatcher).retained().build();
        task.getState().setTexture(texture).setTransform(new DrawTaskState.Transform(){
            public void apply(Matrix4f matrix){matrix.scale(new Vector3f(scale,scale,1));}
            public void unapply(Matrix4f matrix){matrix.setIdentity();}
        });return task;
    }
    private void rectangle(DrawTask task,float x,float y,float width,float height,float radius,int color){
        task.add(new UiGeometry.RoundedRectangle(x,y,width,height,radius,color,visual.hooks));
    }
    boolean press(int x,int y,int button){
        Frame f=frame;
        if(button!=0||f==null||!(visual.mc.currentScreen instanceof GuiChat)||x<f.left()||x>f.left()+f.width()*f.scale()||y<f.top()||y>f.top()+HEIGHT*f.scale())return false;
        dragging=true;dragX=f.left()-x;dragY=f.top()-y;return true;
    }
    boolean move(int x,int y,int button){
        if(!dragging)return false;
        if(!(visual.mc.currentScreen instanceof GuiChat)){dragging=false;return false;}
        visual.nativeModules.session.x.set(Math.max(0,x+dragX));visual.nativeModules.session.y.set(Math.max(0,y+dragY));
        prepare();invalidate();return true;
    }
    boolean release(int button){boolean active=dragging;if(button==0)dragging=false;return button==0&&active;}
    public void close(){frame=null;dragging=false;releaseIcon();}
}
