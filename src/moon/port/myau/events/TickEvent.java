package moon.port.myau.events;

import moon.port.myau.event.events.Event;
import moon.port.myau.event.types.EventType;

public class TickEvent implements Event {
    private final EventType type;

    public TickEvent(EventType type) {
        this.type = type;
    }

    public EventType getType() {
        return this.type;
    }
}
