package moon.port.myau.event.events.callables;

import moon.port.myau.event.events.Cancellable;
import moon.port.myau.event.events.Event;

public abstract class EventCancellable implements Event, Cancellable {
    private boolean cancelled;
    protected EventCancellable() {
    }
    @Override
    public boolean isCancelled() {
        return cancelled;
    }
    @Override
    public void setCancelled(boolean state) {
        cancelled = state;
    }
}
