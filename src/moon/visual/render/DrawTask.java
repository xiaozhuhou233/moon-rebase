package moon.visual.render;

/** gv, including null/sentinel control flow and the original exception-sticky rendering flag. */
public final class DrawTask {
    private static Object[] sentinel;
    private final DrawTaskState state;
    private final DrawServices services;
    private ShaderDispatcher dispatcher;
    private boolean rendering;
    public DrawTask(DrawServices services){this.services=services;state=new DrawTaskState(services);}
    public void setup(){
        Object[] captured=getSentinel();
        if(captured==null&&dispatcher==null){services.diagnostics.error("Illegal task setup call (dispatcher == NULL)");return;}
        int mode=dispatcher.descriptor().getMode();
        if(captured!=null){if(mode!=0)state.initializeWithDispatcher(dispatcher);return;}
        if(mode!=0)state.setPrimitive(dispatcher.descriptor().getMode());
        if(state.hasOwnBuffers())state.initializeWithDispatcher(dispatcher);
    }
    public void add(DrawTaskState.Geometry v){state.add(v);} public void add(DrawTaskState.Command v){state.add(v);}
    public void draw(){
        Object[] captured=getSentinel();
        if(captured==null){
            if(rendering){services.diagnostics.error("Cannot render a task twice at once!");return;}
            if(dispatcher==null){services.diagnostics.error("Cannot render a task (dispatcher == NULL)");return;}
            rendering=true;
        }
        dispatcher.draw(state);rendering=false; // No finally in the original.
    }
    public DrawTaskState getState(){return state;} public ShaderDispatcher getDispatcher(){return dispatcher;}
    public DrawTask setDispatcher(ShaderDispatcher v){dispatcher=v;return this;}
    public boolean isRendering(){return rendering;} public DrawTask setRendering(boolean v){rendering=v;return this;}
    public static DrawTaskBuilder builder(DrawServices services){return new DrawTaskBuilder(services);}
    public static Object[] getSentinel(){return sentinel;} public static void setSentinel(Object[] v){sentinel=v;}
}
