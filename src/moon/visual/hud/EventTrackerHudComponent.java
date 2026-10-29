package moon.visual.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import moon.visual.render.DrawTask;
import moon.visual.render.MsdfFontBase;

/** fX's normal tp.ag()==false drawing and original asynchronous refresh flow.
 * Numeric constants use the supplied fX runtime seed. EventTrackerEntry.parse
 * restores f3's array parser from its separately recovered runtime dump.
 * HTTP/executor adapters belong to the destination; constructing this class
 * does not itself submit a request. See EVENT_TRACKER_EVIDENCE.json.
 */
public final class EventTrackerHudComponent extends DraggableHudComponent {
    public interface Config {
        boolean enabled();boolean hideTitle();boolean fixedWidth();boolean majorOnly();int eventLimit();
    }
    /** Original f3.a(): live mutable list, without a snapshot or null repair. */
    public interface EventData {List<EventTrackerEntry> events();}
    public interface Response {int statusCode();String body();}
    public interface Client {
        Object buildGetRequest(String url);
        Response send(Object request)throws IOException,InterruptedException;
    }
    public interface Host {
        MsdfFontBase selectedFont();long currentTimeMillis();
        Client createClient();void submit(Runnable action);
        EventData parseOriginalEventData(JsonElement json);
        void error(String message,Exception exception);
        boolean originalNormalFlag(); // tp.af(); tp.ag() is its negation
        Object originalRoundingMarker(); // DR.b(), read on every Dq.b call
        String originalStringMarker(); // r2.h()
        void setOriginalStringMarker(String value); // r2.b(String)
        void setOriginalNormalFlag(boolean value); // tp.l(boolean)
    }

    private final DraggableComponentProperties properties=DraggableComponentProperties.builder().title("Event Tracker").build();
    private final HashMap<String,String> colors=new HashMap<>();
    private final Host host;
    private final Config config;
    private final Client client;
    // Keep original unsynchronized publication and assignment/failure order.
    private long lastRefresh;
    private EventData allEvents,majorEvents;

    public EventTrackerHudComponent(String id,HudRenderPipeline hud,Config config,Host host) {
        super(id,hud);this.config=config;this.host=host;client=host.createClient();
        colors.put("Quick Maths","\u00a75");colors.put("2x Rewards","\u00a72");
        colors.put("Rage Pit","\u00a7c\u00a7l");colors.put("Giant Cake","\u00a7d");
        colors.put("KOTL","\u00a7e");colors.put("KOTH","\u00a73");colors.put("All bounty","\u00a76");
        colors.put("Blockhead","\u00a79\u00a7l");colors.put("Raffle","\u00a7e\u00a7l");
        colors.put("Care Package","\u00a76");colors.put("Team Deathmatch","\u00a7d\u00a7l");
        boolean abnormal=!host.originalNormalFlag();
        colors.put("Robbery","\u00a7e\u00a7l");colors.put("Squads","\u00a7b\u00a7l");
        colors.put("Spire","\u00a7d\u00a7l");colors.put("Auction","\u00a77");
        colors.put("Beast","\u00a7a\u00a7l");colors.put("Pizza","\u00a7c\u00a7l");
        if(abnormal)host.setOriginalStringMarker("vbcKhb");
    }
    @Override public DraggableComponentProperties properties(){return properties;}
    @Override public boolean isEnabled(){return config.enabled();}
    public Map<String,String> getColors(){return colors;}
    public long getLastRefresh(){return lastRefresh;}
    public EventData getAllEvents(){return allEvents;}
    public EventData getMajorEvents(){return majorEvents;}
    @Override public void prepareRectangles(DrawTask task){properties.setTitleDisabled(config.hideTitle());}
    @Override public void prepareBackground(DrawTask task){if(host.originalNormalFlag()&&config.fixedWidth())setWidth(150F);}
    @Override public void prepareMask(DrawTask task){}

    @Override public void prepareText(DrawTask task) {
        boolean abnormal=!host.originalNormalFlag();
        MsdfFontBase font=host.selectedFont();
        if(abnormal)throw new UnsupportedOperationException("fX tp.ag nonnormal drawing not restored");
        if(allEvents==null) {
            font.draw(task,"\u00a7eLoading...",8F,getContentX(),getContentY(),-1,true);
            setHeight(font.height(12F)+font.height(8F)+12F);
        } else {
            float width=0F;
            List<EventTrackerEntry> events=config.majorOnly()?majorEvents.events():allEvents.events();
            int count=Math.min(config.eventLimit(),events.size());
            for(int i=0;i<count;i++) {
                EventTrackerEntry event=events.get(i);
                String color=colors.getOrDefault(event.getName(),"\u00a77");
                long timestamp=event.getTimestamp();
                String time=timestamp<=host.currentTimeMillis()?"Active":"active in "+formatTimeRemaining(timestamp-host.currentTimeMillis());
                float y=getContentY()+font.height(8F)*i;
                String name=color+event.getName();
                if(!config.fixedWidth())name=name+" \u00a77("+time+")";
                else font.draw(task,"\u00a77"+time,8F,getContentX()+getWidth()-font.width(time,8F)-8F,y,-1);
                font.draw(task,name,8F,getContentX(),y,-1);
                float candidate=font.width(name,8F)+8F;
                // fcmpg/ifge skips the assignment on NaN.
                if(width<candidate)width=candidate;
            }
            width=Math.max(width,font.width("Event Tracker",12F)+8F);
            if(!config.fixedWidth())setWidth(width);
            setHeight((properties.isTitleDisabled()?0F:font.height(12F))+font.height(8F)*count+
                (properties.isTitleDisabled()?7F:12F));
        }
        if(host.originalStringMarker()!=null)host.setOriginalNormalFlag(!abnormal);
    }

    /** Dq.b's actual d2i/comparison/marker behavior, including int overflow. */
    private int originalInteger(double value) {
        boolean marker=host.originalRoundingMarker()!=null;
        int integer=(int)value;
        // With a null marker, the dcmpg result remains on the operand stack
        // through goto/ireturn: return -1/0/1 (NaN -> 1), not the integer.
        if(!marker)return value<integer?-1:value==integer?0:1;
        return value<integer?integer-1:integer;
    }
    public String formatTimeRemaining(long millis) {
        if(!host.originalNormalFlag())throw new UnsupportedOperationException("fX nonnormal time formatting not restored");
        if(millis<=60000L)return originalInteger((double)(millis*100L))/100000+"s";
        if(millis<=3600000L)return originalInteger((double)((float)millis/60000F))+"m"+
            originalInteger((double)((float)millis/1000F))%60+"s";
        return originalInteger((double)((float)millis/3600000F))+"h"+
            originalInteger((double)((float)millis/60000F))%60+"m";
    }
    @Override public void renderAfterTasks() {
        if(host.currentTimeMillis()-lastRefresh>10000L)host.submit(this::requestEvents);
    }
    private void requestEvents() {
        boolean normal=host.originalNormalFlag();
        Object request=client.buildGetRequest("https://events.mcpqndq.dev/"); // outside original try
        try {
            Response response=client.send(request);
            if(normal) {
                if(response.statusCode()==200) {
                    String body=response.body();
                    JsonElement json=new JsonParser().parse(body);
                    allEvents=host.parseOriginalEventData(json);
                    majorEvents=host.parseOriginalEventData(json);
                    majorEvents.events().removeIf(event->removeFromMajor(event));
                }
                lastRefresh=host.currentTimeMillis();
            }
        } catch(IOException|InterruptedException exception) {
            host.error("Failed to create pit tracking request",exception);
        }
    }
    private boolean removeFromMajor(EventTrackerEntry event) {
        boolean abnormal=!host.originalNormalFlag();
        boolean major=event.getType().equals("major");
        return abnormal?major:!major;
    }
}
