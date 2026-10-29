package moon.client;

import com.google.gson.JsonElement;
import java.net.URI;
import java.util.Objects;
import java.util.function.Function;
import moon.visual.hud.*;
import moon.visual.render.DrawTask;

/** Destination owner for fX with recovered f3 parsing and optional mirror/parser APIs.
 * Register/close on the client thread. The existing HUD registry owns its wrapper
 * and closes the HTTP host on unregister, failed registration, and game shutdown.
 */
public final class McpEventTrackerAddon implements AutoCloseable {
    private final McpVisualServices visual;
    private final JavaHttpEventTrackerHost host;
    private final OwnedComponent component;

    /** Built-in component. The HUD registry owns its HTTP and layout lifecycle.
     * A JVM endpoint override supports mirrors and isolated loopback validation.
     */
    public static McpEventTrackerAddon registerBuiltin(McpVisualServices visual){
        URI endpoint=URI.create(System.getProperty("moon.eventTracker.endpoint",JavaHttpEventTrackerHost.ORIGINAL_ENDPOINT.toString()));
        return new McpEventTrackerAddon(visual,"pit-event-tracker",EventTrackerEntry::parse,endpoint,8F,60F);
    }
    public static McpEventTrackerAddon register(McpVisualServices visual,String id,
            Function<JsonElement,EventTrackerHudComponent.EventData> parser) {
        return register(visual,id,parser,JavaHttpEventTrackerHost.ORIGINAL_ENDPOINT);
    }
    /** Explicit mirror/loopback override. Caller owns verification of its parser. */
    public static McpEventTrackerAddon register(McpVisualServices visual,String id,
            Function<JsonElement,EventTrackerHudComponent.EventData> parser,URI endpoint) {
        return new McpEventTrackerAddon(visual,id,parser,endpoint,0F,0F);
    }
    private McpEventTrackerAddon(McpVisualServices visual,String id,
            Function<JsonElement,EventTrackerHudComponent.EventData> parser,URI endpoint,float initialX,float initialY) {
        this.visual=Objects.requireNonNull(visual);Objects.requireNonNull(id);
        visual.backend.requireOpen();
        host=new JavaHttpEventTrackerHost(visual::hudFont,System::currentTimeMillis,parser,
            action->visual.mc.addScheduledTask(action),()->{
                visual.hud.pipeline.setDirty(true);visual.post.requestShadowUpdate();visual.post.requestBlurUpdate();
            },(message,failure)->visual.diagnostics.error(message,failure),endpoint);
        try{component=visual.registerHudComponent(pipeline->{
            OwnedComponent created=new OwnedComponent(id,pipeline,visual.hudSettings.trackerConfig(),host);
            created.setX(initialX).setY(initialY);return created;
        });}
        catch(RuntimeException|Error failure){host.close();throw failure;}
    }
    public DraggableHudComponent component(){return component;}
    /** Read on the client thread, after the scheduled completion/invalidation. */
    public EventTrackerHudComponent.EventData allEvents(){visual.backend.requireOpen();return component.delegate.getAllEvents();}
    public EventTrackerHudComponent.EventData majorEvents(){visual.backend.requireOpen();return component.delegate.getMajorEvents();}
    public boolean isClosed(){return host.isClosed();}
    public boolean isTerminated(){return host.isTerminated();}
    @Override public void close(){
        if(host.isClosed())return;
        // On save failure the registry retains ownership, including its live host.
        visual.unregisterHudComponent(component);
    }

    private static final class OwnedComponent extends DraggableHudComponent implements AutoCloseable {
        private final EventTrackerHudComponent delegate;
        private final JavaHttpEventTrackerHost host;
        OwnedComponent(String id,HudRenderPipeline pipeline,EventTrackerHudComponent.Config config,JavaHttpEventTrackerHost host){
            super(id,pipeline);this.host=host;delegate=new EventTrackerHudComponent(id,pipeline,config,host);
        }
        public DraggableComponentProperties properties(){return delegate.properties();}
        public boolean isEnabled(){return !host.isClosed()&&delegate.isEnabled();}
        private void before(){
            delegate.setX(getX()).setY(getY()).setWidth(getWidth()).setHeight(getHeight())
                .setContentX(getContentX()).setContentY(getContentY());
        }
        private void after(){
            setX(delegate.getX()).setY(delegate.getY()).setWidth(delegate.getWidth()).setHeight(delegate.getHeight())
                .setContentX(delegate.getContentX()).setContentY(delegate.getContentY());
        }
        private void draw(DrawTask task,java.util.function.Consumer<DrawTask> callback){
            before();try{callback.accept(task);}finally{after();}
        }
        public void prepareRectangles(DrawTask task){draw(task,delegate::prepareRectangles);}
        public void prepareMask(DrawTask task){draw(task,delegate::prepareMask);}
        public void prepareBackground(DrawTask task){draw(task,delegate::prepareBackground);}
        public void prepareText(DrawTask task){draw(task,delegate::prepareText);}
        public void renderAfterTasks(){delegate.renderAfterTasks();}
        public void close(){host.close();}
    }
}
