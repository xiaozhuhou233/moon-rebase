package moon.port.myau.event.events;

public interface Cancellable {
    boolean isCancelled();
    void setCancelled(boolean state);
}
