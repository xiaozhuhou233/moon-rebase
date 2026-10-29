package moon.visual.hud;

import com.google.gson.annotations.SerializedName;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** f2 carrier plus f3's recovered normal-path event-array parser.
 * Reflective f2 aliases differ from f3's explicit event/timestamp/type keys. */
public class EventTrackerEntry {
    @SerializedName("a") private String name;
    @SerializedName("b") private long timestamp;
    @SerializedName("c") private String type;
    private static int[] sentinel=new int[1];

    public String getName(){return name;}
    public long getTimestamp(){return timestamp;}
    public String getType(){return type;}
    public EventTrackerEntry setName(String value){name=value;return this;}
    public EventTrackerEntry setTimestamp(long value){timestamp=value;return this;}
    public EventTrackerEntry setType(String value){type=value;return this;}
    public static int[] getSentinel(){return sentinel;}
    public static void setSentinel(int[] value){sentinel=value;}

    /** f3: skip non-objects; retain object rows even with missing fields.
     * Gson conversions and their exceptions are deliberately left unchanged.
     * Original timestamp is already milliseconds; do not rescale or sort it.
     */
    public static EventTrackerHudComponent.EventData parse(JsonElement json){
        ParsedEvents result=new ParsedEvents();
        if(json.isJsonArray())for(JsonElement element:json.getAsJsonArray()){
            if(!element.isJsonObject())continue;
            JsonObject object=element.getAsJsonObject();
            EventTrackerEntry event=new EventTrackerEntry();
            if(object.has("event"))event.setName(object.get("event").getAsString());
            if(object.has("timestamp"))event.setTimestamp(object.get("timestamp").getAsLong());
            if(object.has("type"))event.setType(object.get("type").getAsString());
            result.events.add(event);
        }
        return result;
    }
    private static final class ParsedEvents implements EventTrackerHudComponent.EventData {
        private final List<EventTrackerEntry> events=new ArrayList<>();
        public List<EventTrackerEntry> events(){return events;}
    }
}
