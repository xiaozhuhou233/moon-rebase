package moon.visual.hud;

import com.google.gson.JsonElement;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import moon.visual.render.MsdfFontBase;

/** Java 24 HTTP/thread adapter for the restored fX/f3 parser boundary.
 * No request is made by construction. Each eligible original submission gets
 * its own worker; a supplied endpoint is an explicit destination override.
 */
public final class JavaHttpEventTrackerHost implements EventTrackerHudComponent.Host,AutoCloseable {
    public static final URI ORIGINAL_ENDPOINT=URI.create("https://events.mcpqndq.dev/");
    private final Supplier<MsdfFontBase> font;
    private final LongSupplier clock;
    private final Function<JsonElement,EventTrackerHudComponent.EventData> parser;
    private final Executor renderThread;
    private final Runnable invalidate;
    private final BiConsumer<String,Exception> errors;
    private final URI endpoint;
    private final HttpClient http;
    private final ExecutorService workers;
    private final AtomicBoolean closed=new AtomicBoolean();
    private final Object roundingMarker=new Object();

    public JavaHttpEventTrackerHost(Supplier<MsdfFontBase> font,LongSupplier clock,
            Function<JsonElement,EventTrackerHudComponent.EventData> parser,
            Executor renderThread,Runnable invalidate,BiConsumer<String,Exception> errors,URI endpoint) {
        this.font=Objects.requireNonNull(font);this.clock=Objects.requireNonNull(clock);
        this.parser=Objects.requireNonNull(parser);this.renderThread=Objects.requireNonNull(renderThread);
        this.invalidate=Objects.requireNonNull(invalidate);this.errors=Objects.requireNonNull(errors);
        this.endpoint=Objects.requireNonNull(endpoint);
        if(!endpoint.isAbsolute()||endpoint.getHost()==null||
                !("https".equalsIgnoreCase(endpoint.getScheme())||"http".equalsIgnoreCase(endpoint.getScheme())))
            throw new IllegalArgumentException("Absolute HTTP(S) endpoint required");
        // fX also uses newBuilder().build(), with no added timeout or redirect policy.
        http=HttpClient.newBuilder().build();
        workers=Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("moon-event-tracker-",0).factory());
    }
    public MsdfFontBase selectedFont(){return font.get();}
    public long currentTimeMillis(){return clock.getAsLong();}
    public EventTrackerHudComponent.EventData parseOriginalEventData(JsonElement json){return parser.apply(json);}
    public void error(String message,Exception failure){errors.accept(message,failure);}
    public EventTrackerHudComponent.Client createClient(){
        return new EventTrackerHudComponent.Client(){
            public Object buildGetRequest(String url){
                if(!ORIGINAL_ENDPOINT.toString().equals(url))throw new IllegalArgumentException("Unexpected original tracker endpoint");
                return HttpRequest.newBuilder().uri(endpoint).GET().build();
            }
            public EventTrackerHudComponent.Response send(Object request)throws java.io.IOException,InterruptedException {
                HttpResponse<String> response=http.send((HttpRequest)request,HttpResponse.BodyHandlers.ofString());
                return new EventTrackerHudComponent.Response(){
                    public int statusCode(){return response.statusCode();}
                    public String body(){return response.body();}
                };
            }
        };
    }
    public void submit(Runnable action){
        Objects.requireNonNull(action);
        if(closed.get())return;
        try{workers.execute(()->{
            if(closed.get())return;
            try{action.run();}finally{
                // Worker writes precede queue submission. Minecraft's scheduled
                // task queue publishes them before the next retained HUD rebuild.
                // This is destination synchronization; fX itself stays unchanged.
                if(!closed.get())renderThread.execute(()->{if(!closed.get())invalidate.run();});
            }
        });}catch(RejectedExecutionException failure){if(!closed.get())throw failure;}
    }
    // Explicit normal-path migration policy, not recovered global marker state.
    public boolean originalNormalFlag(){return true;}
    public Object originalRoundingMarker(){return roundingMarker;}
    public String originalStringMarker(){return null;}
    public void setOriginalStringMarker(String value){throw new UnsupportedOperationException("Normal-path adapter only");}
    public void setOriginalNormalFlag(boolean value){if(!value)throw new UnsupportedOperationException("Normal-path adapter only");}
    public URI endpoint(){return endpoint;}
    public boolean isClosed(){return closed.get();}
    public boolean isTerminated(){return workers.isTerminated()&&http.isTerminated();}
    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;
        // Never wait for HTTP or worker termination on the client/render thread.
        try{workers.shutdownNow();}finally{http.shutdownNow();}
    }
}
