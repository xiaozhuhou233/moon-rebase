package moon.visual.notification;

import java.util.ArrayList;
import moon.visual.render.*;

/** ge normal null-marker path. Original worker/fB scheduling and encrypted
 * blend/recent-change constants are host inputs. close() is an explicit resource
 * ownership extension; original task order and no-finally failure behavior stay. */
public final class NotificationRenderPipeline implements AutoCloseable {
    public interface Host extends Notification.Host {
        DrawServices drawing();ShaderDispatcher roundedDispatcher();ShaderDispatcher msdfDispatcher();
        boolean capabilityEnabled(int capability);void enableBlend();void disableBlend();
        void requestShadowUpdate();void requestBlurUpdate();
    }
    private final Host host;private final NotificationQueue queue;private final int blendCapability;
    private final long recentChangeWindow;
    private boolean dirty=true;private long lastAnimationChange;
    private DrawTask background,icons,text,mask;
    public NotificationRenderPipeline(Host host,NotificationQueue queue,int blendCapability,long recentChangeWindow){
        this.host=host;this.queue=queue;this.blendCapability=blendCapability;this.recentChangeWindow=recentChangeWindow;
    }
    private void requireNormal(){if(Notification.marker()!=null)throw new UnsupportedOperationException("ge nonnull gc marker path not restored");}
    public void requestUpdate(){dirty=true;}
    private DrawTask roundedTask(){return DrawTask.builder(host.drawing()).dispatcher(host.roundedDispatcher()).retained().ownedBuffers().build();}
    private DrawTask textTask(MsdfFontBase font){return DrawTask.builder(host.drawing()).dispatcher(host.msdfDispatcher()).texture(font::bind).retained().ownedBuffers().build();}
    private static void release(DrawTask task){if(task!=null)task.getState().release();}
    public void prepare(){
        requireNormal();if(!dirty)return;
        release(background);release(icons);release(text);release(mask);
        background=roundedTask();icons=textTask(host.iconFont());text=textTask(host.textFont());mask=roundedTask();
        var entries=queue.entries();var removals=new ArrayList<Notification>();float offset=0F;
        for(Notification entry:entries){
            if(entry.expirationTime()<host.clock().getAsLong()){
                if(!entry.expired())entry.animation().resetTo(entry.animation().get());
                entry.expired(true);
            }
            if(entry.finished())removals.add(entry);
            entry.prepare(background,icons,text,mask,offset); // Finished entries still draw once before removal.
            if(!entry.animation().isFinished())lastAnimationChange=host.clock().getAsLong();
            offset-=entry.animatedHeight()+2F;
        }
        for(Notification entry:removals)queue.remove(entry);
        if(host.clock().getAsLong()-lastAnimationChange<recentChangeWindow){host.requestShadowUpdate();host.requestBlurUpdate();}
        dirty=false;
    }
    public void drawMask(){mask.draw();} // Original dm has no null guard and ignores its flag.
    public void draw(){
        requireNormal();boolean blend=host.capabilityEnabled(blendCapability);
        if(!blend)host.enableBlend();background.draw();icons.draw();text.draw();if(!blend)host.disableBlend();
    }
    public boolean dirty(){return dirty;}public long lastAnimationChange(){return lastAnimationChange;}
    public DrawTask background(){return background;}public DrawTask icons(){return icons;}
    public DrawTask text(){return text;}public DrawTask mask(){return mask;}
    @Override public void close(){release(background);release(icons);release(text);release(mask);background=icons=text=mask=null;}
}
