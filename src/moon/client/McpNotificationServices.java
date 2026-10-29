package moon.client;

import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import moon.visual.notification.*;
import moon.visual.render.*;
import moon.visual.gui.SettingValue;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Destination ownership/event adapter, not a reconstruction of ge's worker.
 * All queue publication and GL work belong to the client/render thread.
 * Active notifications rebuild before POST effects, including one final empty
 * rebuild to invalidate cached masks. Unresolved constants live in the profile.
 */
public final class McpNotificationServices implements NotificationRenderPipeline.Host,AutoCloseable {
    private final McpVisualServices visual;
    private final NotificationQueue queue=new NotificationQueue();
    public final ShadowStyle shadowColor;
    final NotificationRenderPipeline pipeline;
    private boolean prepared,hadEntries,drawEntries,closed;
    McpNotificationServices(McpVisualServices visual){
        this.visual=visual;shadowColor=new ShadowStyle("shadow-color","Shadow Color",visual.hooks);
        pipeline=new NotificationRenderPipeline(this,queue,visual.profile.notificationBlendCapability,
            visual.profile.notificationRecentChangeWindow);
    }
    private void check(){visual.backend.requireOpen();if(closed)throw new IllegalStateException("Notifications closed");}
    public void publish(NotificationType type,String title,String message,long duration){
        check();Objects.requireNonNull(type);Objects.requireNonNull(title);Objects.requireNonNull(message);
        queue.add(Notification.builder(this,visual.profile.notification).type(type).title(title).message(message).duration(duration).build());
    }
    public List<Notification> entries(){check();return List.copyOf(queue.entries());}
    public List<? extends SettingValue<?>> settings(){return List.of(shadowColor.group);}
    void prepare(){
        check();boolean active=!queue.entries().isEmpty();
        if(!active&&!hadEntries)return;
        pipeline.requestUpdate();pipeline.prepare();prepared=true;drawEntries=active;hadEntries=active;
        // The original worker is deliberately replaced by render-thread scheduling.
        // Also invalidates the final empty frame and stationary live theme changes.
        visual.post.requestShadowUpdate();visual.post.requestBlurUpdate();
    }
    void draw(){check();if(prepared&&drawEntries)render(false);}
    void mask(boolean shadow){check();if(prepared&&drawEntries)render(true);}
    private void render(boolean mask){
        // Preserve the current FBO: this callback also runs inside effect inputs.
        // Target state isolation is outside the original no-finally pipeline.
        try(McpLuaRenderer.DrawState state=new McpLuaRenderer.DrawState()){
            GlStateManager.disableDepth();GlStateManager.disableAlpha();GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1,1,1,1);
            if(mask)pipeline.drawMask();else pipeline.draw();
        }
    }
    public MsdfFontBase textFont(){return visual.font("Roboto-Medium");}
    public MsdfFontBase iconFont(){return visual.font("MaterialIcons-Regular");}
    public float iconAdvance(char c){return visual.font("MaterialIcons-Regular").getLoader().getProvider().glyph(c).advance();}
    public float iconPlaneHeight(char c){return visual.font("MaterialIcons-Regular").getLoader().getProvider().glyph(c).planeHeight();}
    public int scaledWidth(){return visual.scaledWidth();}public int scaledHeight(){return visual.scaledHeight();}
    public Notification.ThemeSource resolveClickGui(){
        // gc resolves jK once at initialize, then reads its existing Scheme at
        // each prepare. Rebuilding HCT here loses the original Scheme identity
        // and repeats full palette solving for every notification/frame.
        var clickGui=visual.clickSettings.module();
        return ()->{
            McpVisualProfile.Palette palette=(McpVisualProfile.Palette)clickGui.getUiColor();
            return new Notification.Theme(){public int accent(){return palette.accent();}public int onAccent(){return palette.onAccent();}};
        };
    }
    public LongSupplier clock(){return System::currentTimeMillis;}public DrawServices.MarkerHooks markers(){return visual.hooks;}
    public DrawServices drawing(){return visual.draw;}public ShaderDispatcher roundedDispatcher(){return visual.rounded;}
    public ShaderDispatcher msdfDispatcher(){return visual.msdf;}
    public boolean capabilityEnabled(int c){return GL11.glIsEnabled(c);}public void enableBlend(){GlStateManager.enableBlend();}
    public void disableBlend(){GlStateManager.disableBlend();}
    public void requestShadowUpdate(){visual.post.requestShadowUpdate();}public void requestBlurUpdate(){visual.post.requestBlurUpdate();}
    @Override public void close(){
        if(closed)return;check();closed=true;
        try{pipeline.close();}finally{queue.entries().clear();prepared=hadEntries=drawEntries=false;}
    }
}
